package org.lavacast.pvtranscribe.engine.vosk;

import org.jetbrains.annotations.NotNull;

/**
 * Extracts a top-level string value from Vosk's small JSON results without pulling in a JSON library.
 * Example input: {@code { "partial" : "ребята давайте" }}.
 */
final class VoskJson {

    private VoskJson() {
    }

    static @NotNull String string(String json, String key) {
        if (json == null) return "";
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx < 0) return "";
        int colon = json.indexOf(':', keyIdx + needle.length());
        if (colon < 0) return "";
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return "";

        StringBuilder out = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') break;
            if (c == '\\' && i + 1 < json.length()) {
                char e = json.charAt(++i);
                switch (e) {
                    case 'n' -> out.append(' ');
                    case 't' -> out.append(' ');
                    case 'r' -> {
                    }
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            } catch (NumberFormatException ignored) {
                            }
                            i += 4;
                        }
                    }
                    default -> out.append(e);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }
}
