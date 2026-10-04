package org.lavacast.pvtranscribe.core.config;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parsed, immutable plugin configuration. Values are clamped to sane ranges so a typo in the
 * config cannot freeze the server.
 */
public record TranscribeConfig(
        boolean enabled,
        Transcription transcription,
        String engineType,
        ConfigSection engineSection,
        Subtitles subtitles,
        DisplayText displayText,
        Chat chat,
        boolean logTranscripts,
        boolean debug,
        boolean saveAudio
) {

    public record Transcription(
            String language,
            boolean autoDetectLanguage,
            boolean partialResults,
            long partialIntervalMs,
            long silenceTimeoutMs,
            long maxPhraseMs,
            int workerThreads,
            int maxConcurrentSpeakers,
            long maxQueueMs,
            long idleRecognizerCloseMs,
            Set<String> disabledWorlds
    ) {
    }

    public record Subtitles(
            boolean enabled,
            boolean showPartial,
            boolean showName,
            String nameFormat,
            String partialFormat,
            String finalFormat,
            boolean capitalize,
            int maxLineLength,
            int maxLines,
            long displayMs,
            long displayPerCharMs,
            long maxDisplayMs,
            long fadeOutMs,
            double height,
            double scale,
            int backgroundArgb,
            boolean textShadow,
            boolean seeThrough,
            VisibilityMode visibilityMode,
            double fallbackDistance,
            double maxDistance,
            boolean includePlayersWithoutVoiceMod,
            boolean showToSelf,
            boolean hideWhenSpeakerInvisible,
            boolean bubbleTail,
            int maxBubbles,
            int maxWordsPerBubble,
            Alignment alignment,
            int padding,
            String tailSymbol,
            int tailArgb,
            double bubbleSpacing
    ) {
    }

    public enum Alignment { LEFT, CENTER, RIGHT }

    /**
     * Ready-made looks for the speech bubble; every value can still be overridden in {@code subtitles.style}.
     */
    private record Preset(String background, String partialFormat, String finalFormat, boolean shadow, boolean tail) {
        static Preset of(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "dark" -> new Preset("#C8101010", "&7{text}", "&f{text}", false, true);
                case "glass" -> new Preset("#40000000", "&7{text}", "&f{text}", true, false);
                case "minimal" -> new Preset("#00000000", "&7{text}", "&f{text}", true, false);
                default -> new Preset("#E6FFFFFF", "&8{text}", "&0{text}", false, true); // light / custom
            };
        }
    }

    /**
     * How recognised words are turned into text for subtitles and chat (API transcripts keep the words).
     */
    public record DisplayText(boolean numbersToDigits, boolean mathSymbols, boolean matchNicknames) {
    }

    /**
     * Copy of each finished phrase in the chat of the players who could hear it.
     */
    public record Chat(boolean enabled, String format, boolean sendToSelf) {
    }

    public enum VisibilityMode {
        /** Only players that the voice chat actually sends the audio to (distance, groups, broadcast...). */
        VOICE_CHAT,
        /** Everyone within {@code fallback-distance} blocks. */
        DISTANCE,
        /** Everyone in the same world. */
        WORLD
    }

    public static @NotNull TranscribeConfig parse(@NotNull ConfigSection root) {
        ConfigSection t = orEmpty(root.getSection("transcription"));
        int threads = t.getInt("worker-threads", 0);
        if (threads <= 0) {
            threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        }
        Transcription transcription = new Transcription(
                t.getString("language", "en").trim().toLowerCase(Locale.ROOT),
                t.getBoolean("auto-detect-language", false),
                t.getBoolean("partial-results", true),
                clamp(t.getLong("partial-interval-ms", 150), 0, 5_000),
                clamp(t.getLong("silence-timeout-ms", 900), 200, 10_000),
                clamp((long) (t.getDouble("max-phrase-seconds", 20) * 1000), 2_000, 120_000),
                Math.min(threads, 32),
                (int) clamp(t.getInt("max-concurrent-speakers", 32), 1, 1_000),
                clamp(t.getLong("max-queue-ms", 3_000), 200, 60_000),
                clamp((long) (t.getDouble("idle-recognizer-close-seconds", 60) * 1000), 5_000, 3_600_000),
                t.getStringList("disabled-worlds").stream().map(String::trim).collect(Collectors.toUnmodifiableSet())
        );

        ConfigSection s = orEmpty(root.getSection("subtitles"));
        ConfigSection v = orEmpty(s.getSection("visibility"));
        ConfigSection st = orEmpty(s.getSection("style"));
        ConfigSection tail = orEmpty(st.getSection("tail"));
        Preset preset = Preset.of(st.getString("preset", "light"));
        int background = parseArgb(st.getString("background", preset.background()));
        String tailColor = tail.getString("color", "auto");
        Subtitles subtitles = new Subtitles(
                s.getBoolean("enabled", true),
                s.getBoolean("show-partial", true),
                s.getBoolean("show-name", false),
                s.getString("name-format", "&e{name}"),
                st.getString("partial-format", preset.partialFormat()),
                st.getString("final-format", preset.finalFormat()),
                s.getBoolean("capitalize", true),
                (int) clamp(s.getInt("max-line-length", 32), 8, 200),
                (int) clamp(s.getInt("max-lines", 3), 1, 10),
                clamp((long) (s.getDouble("display-seconds", 3.5) * 1000), 0, 60_000),
                clamp(s.getLong("display-ms-per-character", 40), 0, 1_000),
                clamp((long) (s.getDouble("max-display-seconds", 8) * 1000), 0, 120_000),
                clamp(s.getLong("fade-out-ms", 500), 0, 5_000),
                s.getDouble("height", 0.85),
                Math.max(0.1, Math.min(5.0, st.getDouble("scale", 1.0))),
                background,
                st.getBoolean("text-shadow", preset.shadow()),
                st.getBoolean("see-through", false),
                parseVisibility(v.getString("mode", "voice-chat")),
                Math.max(1, v.getDouble("fallback-distance", 24)),
                Math.max(1, v.getDouble("max-distance", 96)),
                v.getBoolean("include-players-without-voice-mod", true),
                v.getBoolean("show-to-self", false),
                v.getBoolean("hide-when-speaker-invisible", true),
                tail.getBoolean("enabled", preset.tail()),
                (int) clamp(s.getInt("max-bubbles", 3), 1, 6),
                (int) clamp(s.getInt("max-words-per-bubble", 10), 2, 100),
                parseAlignment(st.getString("alignment", "center")),
                (int) clamp(st.getInt("padding", 1), 0, 6),
                tail.getString("symbol", "▼"),
                tailColor.equalsIgnoreCase("auto") ? background : parseArgb(tailColor),
                clamp(s.getDouble("bubble-spacing", 0.07), 0, 2)
        );

        ConfigSection dt = orEmpty(root.getSection("display-text"));
        DisplayText displayText = new DisplayText(
                dt.getBoolean("numbers-to-digits", true),
                dt.getBoolean("math-symbols", true),
                dt.getBoolean("match-nicknames", true)
        );

        ConfigSection c = orEmpty(root.getSection("chat"));
        Chat chat = new Chat(
                c.getBoolean("enabled", true),
                c.getString("format", "&8[&bГолос&8] &f{name}&7: &f{text}"),
                c.getBoolean("send-to-self", true)
        );

        ConfigSection engine = orEmpty(root.getSection("engine"));
        String type = engine.getString("type", "auto").trim().toLowerCase(Locale.ROOT);
        if (type.equals("auto")) {
            if (transcription.autoDetectLanguage() && !engine.getString("openai.api-key", "").isBlank()) {
                type = "openai"; // only cloud engines detect the language
            } else if (transcription.autoDetectLanguage() && !engine.getString("deepgram.api-key", "").isBlank()) {
                type = "deepgram";
            } else {
                // T-one is the most accurate streaming engine for Russian; Vosk covers other languages
                type = transcription.language().equals("ru") ? "t-one" : "vosk";
            }
        }
        ConfigSection engineSection = orEmpty(engine.getSection(type));

        ConfigSection debug = orEmpty(root.getSection("debug"));
        return new TranscribeConfig(
                root.getBoolean("enabled", true),
                transcription,
                type,
                engineSection,
                subtitles,
                displayText,
                chat,
                debug.getBoolean("log-transcripts", false),
                debug.getBoolean("verbose", false),
                debug.getBoolean("save-audio", false)
        );
    }

    private static ConfigSection orEmpty(ConfigSection section) {
        return section == null ? MapConfigSection.empty() : section;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Alignment parseAlignment(String raw) {
        try {
            return Alignment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Alignment.CENTER;
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static VisibilityMode parseVisibility(String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return VisibilityMode.valueOf(key);
        } catch (IllegalArgumentException e) {
            return VisibilityMode.VOICE_CHAT;
        }
    }

    /**
     * Parses {@code #AARRGGBB} or {@code #RRGGBB}.
     */
    static int parseArgb(String raw) {
        String hex = raw.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            if (hex.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
            if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException ignored) {
        }
        return 0x40000000;
    }
}
