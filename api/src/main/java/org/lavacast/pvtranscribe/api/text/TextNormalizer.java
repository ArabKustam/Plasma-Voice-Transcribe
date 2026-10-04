package org.lavacast.pvtranscribe.api.text;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Normalization used for matching phrases: lower case, {@code ё -> е}, punctuation removed,
 * whitespace collapsed. "Огненный шар!" and "огненный  шар" both become "огненный шар".
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static @NotNull String normalize(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == 'ё') c = 'е';
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && out.length() > 0) out.append(' ');
                pendingSpace = false;
                out.append(c);
            } else if (c == '\'' || c == '’' || c == '-') {
                // keep in-word apostrophes and hyphens ("don't", "кто-то")
                if (out.length() > 0 && !pendingSpace && i + 1 < lower.length()
                        && Character.isLetterOrDigit(lower.charAt(i + 1))) {
                    out.append(c == '’' ? '\'' : c);
                } else {
                    pendingSpace = true;
                }
            } else {
                pendingSpace = true;
            }
        }
        return out.toString();
    }

    /**
     * Whole-word containment check on already normalized strings.
     */
    public static boolean containsPhrase(@NotNull String normalizedText, @NotNull String normalizedPhrase) {
        if (normalizedPhrase.isEmpty()) return false;
        int from = 0;
        while (true) {
            int idx = normalizedText.indexOf(normalizedPhrase, from);
            if (idx < 0) return false;
            int end = idx + normalizedPhrase.length();
            boolean startOk = idx == 0 || normalizedText.charAt(idx - 1) == ' ';
            boolean endOk = end == normalizedText.length() || normalizedText.charAt(end) == ' ';
            if (startOk && endOk) return true;
            from = idx + 1;
        }
    }
}
