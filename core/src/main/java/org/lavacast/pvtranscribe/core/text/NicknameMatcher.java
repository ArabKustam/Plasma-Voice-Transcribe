package org.lavacast.pvtranscribe.core.text;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Replaces words that sound like the nickname of an online player with the nickname itself:
 * the recognizer writes "булетка", a player called "buletka" is online, so the subtitle says "buletka".
 * <p>
 * Nicknames are transliterated to Cyrillic and compared with the recognised words (and pairs of words,
 * because "гуга бель" may be one nickname) by edit distance.
 */
public final class NicknameMatcher {

    /** Minimal similarity (1 - distance / length) to accept a match. */
    private static final double MIN_SIMILARITY = 0.75;
    private static final int MIN_LENGTH = 4;

    private NicknameMatcher() {
    }

    public static @NotNull String apply(@NotNull String text, @NotNull Collection<String> nicknames) {
        if (nicknames.isEmpty() || text.isEmpty()) return text;
        List<String[]> candidates = new ArrayList<>();
        for (String nick : nicknames) {
            String cyr = toCyrillic(nick);
            if (cyr.length() >= MIN_LENGTH) candidates.add(new String[]{nick, cyr});
        }
        if (candidates.isEmpty()) return text;

        String[] words = text.split(" ");
        List<String> out = new ArrayList<>(words.length);
        for (int i = 0; i < words.length; i++) {
            String single = clean(words[i]);
            if (i + 1 < words.length && single.length() >= 3 && words[i + 1].length() >= 3) {
                String next = clean(words[i + 1]);
                String nick = best(single + next, candidates);
                // a nickname split in two words ("гуга бель"), but neither word is a nickname by itself
                if (nick != null && best(single, candidates) == null && best(next, candidates) == null) {
                    out.add(nick);
                    i++;
                    continue;
                }
            }
            String nick = best(single, candidates);
            out.add(nick != null ? nick : words[i]);
        }
        return String.join(" ", out);
    }

    private static String best(String word, List<String[]> candidates) {
        if (word.length() < MIN_LENGTH) return null;
        String bestNick = null;
        double bestScore = MIN_SIMILARITY;
        for (String[] c : candidates) {
            String cyr = c[1];
            if (Math.abs(cyr.length() - word.length()) > 3) continue;
            int dist = levenshtein(word, cyr);
            double score = 1.0 - (double) dist / Math.max(word.length(), cyr.length());
            if (score >= bestScore) {
                bestScore = score;
                bestNick = c[0];
            }
        }
        return bestNick;
    }

    private static String clean(String word) {
        return word.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    static String toCyrillic(String nick) {
        String s = nick.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-яё]", "");
        String[][] digraphs = {{"sch", "щ"}, {"sh", "ш"}, {"ch", "ч"}, {"zh", "ж"}, {"kh", "х"}, {"ts", "ц"},
                {"ya", "я"}, {"yu", "ю"}, {"yo", "е"}, {"ye", "е"}, {"ee", "и"}, {"oo", "у"}, {"th", "т"},
                {"ph", "ф"}, {"ck", "к"}, {"qu", "кв"}};
        for (String[] d : digraphs) s = s.replace(d[0], d[1]);
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'a' -> out.append('а');
                case 'b' -> out.append('б');
                case 'c', 'k', 'q' -> out.append('к');
                case 'd' -> out.append('д');
                case 'e' -> out.append('е');
                case 'f' -> out.append('ф');
                case 'g' -> out.append('г');
                case 'h' -> out.append('х');
                case 'i', 'y' -> out.append('и');
                case 'j' -> out.append("дж");
                case 'l' -> out.append('л');
                case 'm' -> out.append('м');
                case 'n' -> out.append('н');
                case 'o' -> out.append('о');
                case 'p' -> out.append('п');
                case 'r' -> out.append('р');
                case 's' -> out.append('с');
                case 't' -> out.append('т');
                case 'u' -> out.append('у');
                case 'v', 'w' -> out.append('в');
                case 'x' -> out.append("кс");
                case 'z' -> out.append('з');
                case 'ё' -> out.append('е');
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
