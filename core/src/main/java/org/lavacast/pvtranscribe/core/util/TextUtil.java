package org.lavacast.pvtranscribe.core.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern ENGINE_MARKERS = Pattern.compile("\\[(unk|noise|laughter|music)]|<unk>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final String COLOR_CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private TextUtil() {
    }

    /**
     * Removes engine markers such as {@code [unk]} and collapses whitespace.
     */
    public static @NotNull String cleanEngineText(@NotNull String raw) {
        String text = ENGINE_MARKERS.matcher(raw).replaceAll(" ");
        return SPACES.matcher(text).replaceAll(" ").trim();
    }

    public static @NotNull String capitalize(@NotNull String text) {
        if (text.isEmpty()) return text;
        int first = text.codePointAt(0);
        return new StringBuilder(text.length())
                .appendCodePoint(Character.toUpperCase(first))
                .append(text, Character.charCount(first), text.length())
                .toString();
    }

    /**
     * Translates {@code &}-color codes (and {@code &#RRGGBB}) to section-sign codes.
     */
    public static @NotNull String colorize(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '#' && i + 7 < text.length() && isHex(text, i + 2, i + 8)) {
                    out.append('§').append('x');
                    for (int j = i + 2; j < i + 8; j++) {
                        out.append('§').append(Character.toLowerCase(text.charAt(j)));
                    }
                    i += 7;
                    continue;
                }
                if (COLOR_CODES.indexOf(next) >= 0) {
                    out.append('§').append(Character.toLowerCase(next));
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Removes formatting characters from user/engine text so it cannot inject colors.
     */
    public static @NotNull String stripFormatting(@NotNull String text) {
        return text.indexOf('§') < 0 ? text : text.replace('§', ' ');
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    /**
     * Word-wraps text to lines of at most {@code maxLineLength} characters and keeps only the
     * <b>last</b> {@code maxLines} lines, like TV subtitles that always show the newest words.
     * If something was cut off at the beginning, the first kept line starts with an ellipsis.
     */
    public static @NotNull List<String> wrapTail(@NotNull String text, int maxLineLength, int maxLines) {
        if (text.isEmpty()) return Collections.emptyList();
        maxLineLength = Math.max(8, maxLineLength);
        maxLines = Math.max(1, maxLines);

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            while (word.length() > maxLineLength) {
                // very long "word": hard split
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, maxLineLength));
                word = word.substring(maxLineLength);
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxLineLength) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());

        if (lines.size() <= maxLines) return lines;
        List<String> tail = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
        tail.set(0, "…" + tail.get(0));
        return tail;
    }
}
