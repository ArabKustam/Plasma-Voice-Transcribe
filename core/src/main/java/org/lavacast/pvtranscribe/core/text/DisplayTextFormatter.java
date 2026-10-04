package org.lavacast.pvtranscribe.core.text;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns spoken numbers and arithmetic into symbols for display:
 * "сколько будет два плюс два" -> "сколько будет 2 + 2",
 * "двадцать пять тысяч триста" -> "25300".
 * <p>
 * Only nominative Russian number words are converted (that is what the recognizer outputs for spoken
 * numbers). A lone "один/одна" stays a word, because it is usually not a number ("я один").
 * Operator words become symbols only next to a number, so "в этом есть плюс" is left alone.
 */
public final class DisplayTextFormatter {

    private static final Map<String, Integer> UNITS = new HashMap<>();
    private static final Map<String, Integer> TEENS = new HashMap<>();
    private static final Map<String, Integer> TENS = new HashMap<>();
    private static final Map<String, Integer> HUNDREDS = new HashMap<>();
    private static final Map<String, Long> SCALES = new HashMap<>();
    private static final Map<String, String> OPERATORS = new HashMap<>();
    /** Two-word operators: first word -> (second word -> symbol). */
    private static final Map<String, Map<String, String>> OPERATORS_2 = new HashMap<>();

    static {
        String[] units = {"ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"};
        for (int i = 0; i < units.length; i++) UNITS.put(units[i], i);
        UNITS.put("одна", 1);
        UNITS.put("одно", 1);
        UNITS.put("две", 2);
        String[] teens = {"десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать",
                "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"};
        for (int i = 0; i < teens.length; i++) TEENS.put(teens[i], 10 + i);
        String[] tens = {"двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто"};
        for (int i = 0; i < tens.length; i++) TENS.put(tens[i], 20 + 10 * i);
        String[] hundreds = {"сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот"};
        for (int i = 0; i < hundreds.length; i++) HUNDREDS.put(hundreds[i], 100 * (i + 1));
        for (String w : new String[]{"тысяча", "тысячи", "тысяч"}) SCALES.put(w, 1_000L);
        for (String w : new String[]{"миллион", "миллиона", "миллионов"}) SCALES.put(w, 1_000_000L);
        for (String w : new String[]{"миллиард", "миллиарда", "миллиардов"}) SCALES.put(w, 1_000_000_000L);

        OPERATORS.put("плюс", "+");
        OPERATORS.put("plus", "+");
        OPERATORS.put("минус", "-");
        OPERATORS.put("minus", "-");
        OPERATORS.put("равно", "=");
        OPERATORS.put("equals", "=");
        OPERATORS.put("процент", "%");
        OPERATORS.put("процента", "%");
        OPERATORS.put("процентов", "%");
        OPERATORS.put("percent", "%");
        OPERATORS.put("times", "×");
        OPERATORS_2.put("умножить", Map.of("на", "×"));
        OPERATORS_2.put("умноженное", Map.of("на", "×"));
        OPERATORS_2.put("разделить", Map.of("на", "/"));
        OPERATORS_2.put("делить", Map.of("на", "/"));
        OPERATORS_2.put("поделить", Map.of("на", "/"));
        OPERATORS_2.put("divided", Map.of("by", "/"));
        OPERATORS_2.put("multiplied", Map.of("by", "×"));
        OPERATORS_2.put("в", Map.of("квадрате", "²", "кубе", "³"));
    }

    private DisplayTextFormatter() {
    }

    public static @NotNull String format(@NotNull String text, boolean numbers, boolean math) {
        if (!numbers && !math) return text;
        String[] words = text.split(" ");
        List<String> out = new ArrayList<>(words.length);
        List<Boolean> numeric = new ArrayList<>(words.length);

        int i = 0;
        while (i < words.length) {
            if (numbers) {
                int[] consumed = new int[1];
                Long value = parseNumber(words, i, consumed);
                if (value != null) {
                    out.add(String.valueOf(value));
                    numeric.add(true);
                    i += consumed[0];
                    continue;
                }
            }
            out.add(words[i]);
            numeric.add(isDigits(words[i]));
            i++;
        }
        if (!math) return String.join(" ", out);

        List<String> result = new ArrayList<>(out.size());
        for (int j = 0; j < out.size(); j++) {
            String lower = strip(out.get(j));
            boolean prevNum = j > 0 && numeric.get(j - 1);
            String symbol = OPERATORS.get(lower);
            Map<String, String> two = OPERATORS_2.get(lower);
            if (two != null && j + 1 < out.size()) {
                String second = two.get(strip(out.get(j + 1)));
                boolean nextNum = j + 2 < out.size() && numeric.get(j + 2);
                if (second != null && prevNum && (nextNum || second.equals("²") || second.equals("³"))) {
                    if (second.equals("²") || second.equals("³")) {
                        // "пять в квадрате" -> "5²"
                        result.set(result.size() - 1, result.get(result.size() - 1) + second);
                    } else {
                        result.add(second);
                    }
                    j++;
                    continue;
                }
            }
            boolean nextNum = j + 1 < out.size() && numeric.get(j + 1);
            if (symbol != null && symbol.equals("%")) {
                if (prevNum) {
                    result.set(result.size() - 1, result.get(result.size() - 1) + "%");
                    continue;
                }
            } else if (symbol != null && (prevNum || nextNum)) {
                result.add(symbol);
                continue;
            }
            result.add(out.get(j));
        }
        return String.join(" ", result);
    }

    /**
     * Parses a Russian number starting at {@code start}. Returns null if there is none (or just a lone "один").
     */
    private static Long parseNumber(String[] words, int start, int[] consumed) {
        long total = 0;
        long group = 0;
        int lastClass = 5; // 4 = hundreds, 3 = tens, 2 = teens, 1 = units; must strictly decrease inside a group
        long lastScale = Long.MAX_VALUE;
        int count = 0;
        boolean onlyOne = true;

        int i = start;
        for (; i < words.length; i++) {
            String w = strip(words[i]);
            Integer v;
            int cls;
            if ((v = HUNDREDS.get(w)) != null) cls = 4;
            else if ((v = TENS.get(w)) != null) cls = 3;
            else if ((v = TEENS.get(w)) != null) cls = 2;
            else if ((v = UNITS.get(w)) != null) cls = 1;
            else {
                Long scale = SCALES.get(w);
                if (scale != null && scale < lastScale && (count > 0 || i == start)) {
                    group = group == 0 ? 1 : group;
                    total += group * scale;
                    group = 0;
                    lastScale = scale;
                    lastClass = 5;
                    count++;
                    onlyOne = false;
                    continue;
                }
                break;
            }
            // "двадцать пять" ok, "двадцать десять" or "два три" -> stop
            if (cls >= lastClass || (cls == 1 && lastClass == 2) || (cls == 2 && lastClass == 3)) break;
            if (v == 0 && count > 0) break;
            group += v;
            lastClass = cls;
            count++;
            if (v != 1 || cls != 1) onlyOne = false;
        }
        if (count == 0) return null;
        if (onlyOne && count == 1) return null;
        consumed[0] = i - start;
        return total + group;
    }

    private static boolean isDigits(String word) {
        if (word.isEmpty()) return false;
        for (int i = 0; i < word.length(); i++) {
            if (!Character.isDigit(word.charAt(i))) return false;
        }
        return true;
    }

    private static String strip(String word) {
        return word.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
