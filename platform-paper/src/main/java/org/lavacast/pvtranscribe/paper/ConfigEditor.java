package org.lavacast.pvtranscribe.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads and writes any config.yml value from in-game commands ({@code /pvt get|set <path> <value>}).
 * Values are parsed to the type of the current value (or a sensible guess), saved with the comments
 * kept, and then the plugin reloads.
 */
final class ConfigEditor {

    /** Options that are commented out in the default config but can still be set. */
    private static final List<String> EXTRA_PATHS = List.of(
            "subtitles.style.background",
            "subtitles.style.partial-format",
            "subtitles.style.final-format",
            "subtitles.style.text-shadow",
            "subtitles.style.tail.enabled"
    );

    private ConfigEditor() {
    }

    /**
     * @return all settable leaf paths, sorted
     */
    static List<String> paths(FileConfiguration config) {
        Set<String> paths = new TreeSet<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key) && !key.equals("config-version")) paths.add(key);
        }
        paths.addAll(EXTRA_PATHS);
        return new ArrayList<>(paths);
    }

    static boolean isKnown(FileConfiguration config, String path) {
        return paths(config).contains(path);
    }

    /**
     * Value for display; API keys are masked.
     */
    static String show(FileConfiguration config, String path) {
        Object value = config.get(path);
        if (value == null) return "(not set)";
        if (value instanceof ConfigurationSection) return "(section)";
        String text = String.valueOf(value);
        if (path.endsWith("api-key")) {
            return text.isEmpty() ? "(empty)" : text.substring(0, Math.min(4, text.length())) + "…";
        }
        return text;
    }

    /**
     * Parses {@code raw} to the type the option has (boolean, number, list or text).
     *
     * @return parsed value, or null if it doesn't fit the option's type
     */
    static @Nullable Object parse(FileConfiguration config, String path, String raw) {
        Object current = config.get(path);
        String trimmed = raw.trim();
        if (current instanceof Boolean || path.endsWith("enabled") || path.endsWith("text-shadow")) {
            String b = trimmed.toLowerCase(Locale.ROOT);
            if (b.equals("true") || b.equals("on") || b.equals("yes")) return true;
            if (b.equals("false") || b.equals("off") || b.equals("no")) return false;
            return null;
        }
        if (current instanceof Integer || current instanceof Long) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (current instanceof Double || current instanceof Float) {
            try {
                return Double.parseDouble(trimmed.replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (current instanceof List<?>) {
            String body = trimmed.startsWith("[") && trimmed.endsWith("]") ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
            List<String> list = new ArrayList<>();
            for (String part : body.split(",")) {
                if (!part.isBlank()) list.add(part.trim());
            }
            return list;
        }
        // text: strip optional quotes
        if (trimmed.length() >= 2 && (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                || trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Value suggestions for tab completion.
     */
    static List<String> suggestions(FileConfiguration config, String path) {
        Object current = config.get(path);
        if (current instanceof Boolean || path.endsWith("enabled") || path.endsWith("text-shadow")) {
            return List.of("true", "false");
        }
        return switch (path) {
            case "locale" -> List.of("en", "ru");
            case "transcription.language" -> List.of("en", "ru", "de", "fr", "es", "uk", "pl");
            case "engine.type" -> List.of("auto", "vosk", "t-one", "deepgram", "openai");
            case "subtitles.style.preset" -> List.of("light", "dark", "glass", "minimal");
            case "subtitles.style.alignment" -> List.of("left", "center", "right");
            case "subtitles.visibility.mode" -> List.of("voice-chat", "distance", "world");
            case "subtitles.style.tail.symbol" -> List.of("▼", "▾", "◆", "•", "⏷");
            case "subtitles.style.background", "subtitles.style.tail.color" ->
                    List.of("#E6FFFFFF", "#C8101010", "#40000000", "#00000000", "auto");
            case "subtitles.style.partial-format" -> List.of("&8{text}", "&7{text}", "&7&o{text}");
            case "subtitles.style.final-format" -> List.of("&0{text}", "&f{text}", "&f&l{text}");
            default -> current != null && !(current instanceof ConfigurationSection)
                    ? List.of(String.valueOf(current)) : List.of();
        };
    }

    static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }
}
