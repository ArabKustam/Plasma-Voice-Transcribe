package org.lavacast.pvtranscribe.core.subtitle;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.core.config.TranscribeConfig;
import org.lavacast.pvtranscribe.core.util.TextUtil;
import org.lavacast.pvtranscribe.core.voice.Audience;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe subtitle state per speaker. Worker threads write transcripts into it; the platform's
 * renderer reads {@link #collect(long)} on the main thread and syncs display entities.
 * <p>
 * Each speaker has up to {@code max-bubbles} speech bubbles. A new phrase opens a new bubble next to the
 * head and pushes older ones up. A phrase longer than {@code max-words-per-bubble} is split: the full part
 * is frozen in its own bubble and the speech continues in a new one.
 * <p>
 * Results of an older phrase can never overwrite a newer one: each bubble belongs to one phrase
 * (utterance id), and a phrase older than the current one cannot open bubbles.
 */
public final class SubtitleManager {

    /** Audiences of different channels are merged if they were reported within this window. */
    private static final long AUDIENCE_MERGE_WINDOW_MS = 1_500;
    private static final long AUDIENCE_FORGET_MS = 60_000;

    private final ConcurrentHashMap<UUID, Speaker> speakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<String, Audience>> audiences = new ConcurrentHashMap<>();
    private final AtomicLong versions = new AtomicLong();

    private volatile TranscribeConfig.Subtitles config;
    private volatile long partialStaleMs;

    public SubtitleManager(@NotNull TranscribeConfig config) {
        applyConfig(config);
    }

    public void applyConfig(@NotNull TranscribeConfig config) {
        this.config = config.subtitles();
        // A partial that is not followed by a final (e.g. engine failure) disappears after this time.
        this.partialStaleMs = config.transcription().silenceTimeoutMs() + 4_000;
    }

    /**
     * @param displayText text after display formatting and subtitle processors; {@code null} hides the phrase
     */
    public void update(@NotNull Transcript transcript, String displayText) {
        TranscribeConfig.Subtitles cfg = config;
        if (!cfg.enabled()) return;
        if (transcript.isPartial() && !cfg.showPartial()) return;

        long now = System.currentTimeMillis();
        long utterance = transcript.getUtteranceId();
        Speaker speaker = speakers.computeIfAbsent(transcript.getSpeakerId(), id -> new Speaker());
        synchronized (speaker) {
            speaker.name = transcript.getSpeakerName();

            if (utterance < speaker.utterance) {
                // late result of an older phrase: it may only settle its own bubbles, never open new ones
                if (transcript.isFinal()) speaker.settle(utterance, now + finalDuration(cfg, displayText));
                return;
            }
            if (utterance > speaker.utterance) {
                speaker.utterance = utterance;
                speaker.committedWords = 0;
                speaker.chunk = 0;
            }

            String text = displayText == null ? "" : TextUtil.stripFormatting(displayText).trim();
            String[] words = text.isEmpty() ? new String[0] : text.split(" ");
            if (speaker.committedWords > words.length) speaker.committedWords = words.length;

            // freeze full bubbles of a long phrase
            while (words.length - speaker.committedWords > cfg.maxWordsPerBubble()) {
                String part = join(words, speaker.committedWords, speaker.committedWords + cfg.maxWordsPerBubble());
                speaker.put(cfg, key(utterance, speaker.chunk), part, true, speaker.chunk == 0,
                        now + finalDuration(cfg, part), versions, transcript.getSpeakerName());
                speaker.committedWords += cfg.maxWordsPerBubble();
                speaker.chunk++;
            }

            String rest = join(words, speaker.committedWords, words.length);
            String key = key(utterance, speaker.chunk);
            if (rest.isEmpty()) {
                speaker.bubbles.remove(key);
            } else {
                long expiresAt = transcript.isFinal() ? now + finalDuration(cfg, rest) : now + partialStaleMs;
                speaker.put(cfg, key, rest, transcript.isFinal(), speaker.chunk == 0, expiresAt, versions,
                        transcript.getSpeakerName());
            }

            while (speaker.bubbles.size() > cfg.maxBubbles()) {
                Iterator<String> oldest = speaker.bubbles.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }

    /**
     * Removes the unfinished bubble of a phrase that ended without recognised text.
     */
    public void discard(@NotNull UUID speakerId, long utteranceId) {
        Speaker speaker = speakers.get(speakerId);
        if (speaker == null) return;
        synchronized (speaker) {
            speaker.bubbles.values().removeIf(b -> b.utterance == utteranceId && !b.isFinal);
        }
    }

    /**
     * Immediately removes everything about a speaker (quit, death, world change, ...).
     */
    public void remove(@NotNull UUID speakerId) {
        speakers.remove(speakerId);
        audiences.remove(speakerId);
    }

    public void clearAll() {
        speakers.clear();
        audiences.clear();
    }

    public void updateAudience(@NotNull UUID speakerId, @NotNull String key, @NotNull Audience audience) {
        audiences.computeIfAbsent(speakerId, id -> new ConcurrentHashMap<>()).put(key, audience);
    }

    /**
     * @return subtitles to display now; expired bubbles are dropped
     */
    public @NotNull List<SubtitleView> collect(long now) {
        long fadeMs = config.fadeOutMs();
        List<SubtitleView> views = new ArrayList<>(speakers.size());
        for (Map.Entry<UUID, Speaker> e : speakers.entrySet()) {
            Speaker speaker = e.getValue();
            List<SubtitleView.Bubble> bubbles = new ArrayList<>();
            String name;
            synchronized (speaker) {
                name = speaker.name;
                speaker.bubbles.values().removeIf(b -> now >= b.expiresAt);
                for (BubbleState b : speaker.bubbles.values()) {
                    float opacity = 1f;
                    long left = b.expiresAt - now;
                    if (b.isFinal && fadeMs > 0 && left < fadeMs) opacity = Math.max(0f, (float) left / fadeMs);
                    bubbles.add(new SubtitleView.Bubble(b.key, b.text, b.lines, b.isFinal, b.version, opacity));
                }
            }
            if (bubbles.isEmpty()) continue;
            Collections.reverse(bubbles); // newest first
            views.add(new SubtitleView(e.getKey(), name, bubbles, audienceOf(e.getKey(), now)));
        }
        return views;
    }

    public boolean hasSubtitle(@NotNull UUID speakerId) {
        Speaker speaker = speakers.get(speakerId);
        if (speaker == null) return false;
        synchronized (speaker) {
            return !speaker.bubbles.isEmpty();
        }
    }

    /**
     * Who currently hears the speaker according to the voice chat.
     */
    public @NotNull SubtitleView.AudienceSnapshot audienceOf(@NotNull UUID speakerId) {
        return audienceOf(speakerId, System.currentTimeMillis());
    }

    private SubtitleView.AudienceSnapshot audienceOf(UUID speakerId, long now) {
        Map<String, Audience> byChannel = audiences.get(speakerId);
        if (byChannel == null || byChannel.isEmpty()) return SubtitleView.AudienceSnapshot.UNKNOWN;

        long latest = 0;
        for (Iterator<Audience> it = byChannel.values().iterator(); it.hasNext(); ) {
            Audience a = it.next();
            if (now - a.timestamp() > AUDIENCE_FORGET_MS) {
                it.remove();
            } else {
                latest = Math.max(latest, a.timestamp());
            }
        }
        if (latest == 0) return SubtitleView.AudienceSnapshot.UNKNOWN;

        Set<UUID> listeners = new HashSet<>();
        double radius = -1;
        for (Audience a : byChannel.values()) {
            if (latest - a.timestamp() > AUDIENCE_MERGE_WINDOW_MS) continue;
            listeners.addAll(a.listeners());
            if (a.type() == Audience.Type.PROXIMITY) radius = Math.max(radius, a.radius());
        }
        return new SubtitleView.AudienceSnapshot(true, listeners, radius);
    }

    private static long finalDuration(TranscribeConfig.Subtitles cfg, String text) {
        int chars = text == null ? 0 : text.length();
        long duration = cfg.displayMs() + cfg.displayPerCharMs() * chars;
        return Math.min(duration, Math.max(cfg.displayMs(), cfg.maxDisplayMs()));
    }

    private static String key(long utterance, int chunk) {
        return utterance + ":" + chunk;
    }

    private static String join(String[] words, int from, int to) {
        if (from >= to) return "";
        return String.join(" ", Arrays.copyOfRange(words, from, to));
    }

    private static String format(TranscribeConfig.Subtitles cfg, String text, boolean isFinal, boolean firstPart, String speakerName) {
        String body = cfg.capitalize() && firstPart ? TextUtil.capitalize(text) : text;
        List<String> lines = TextUtil.wrapTail(body, cfg.maxLineLength(), cfg.maxLines());

        String lineFormat = TextUtil.colorize(isFinal ? cfg.finalFormat() : cfg.partialFormat());
        StringBuilder out = new StringBuilder();
        if (cfg.showName()) {
            out.append(TextUtil.colorize(cfg.nameFormat()).replace("{name}", speakerName));
        }
        for (String line : lines) {
            if (out.length() > 0) out.append('\n');
            // spaces on each side give the text some padding inside the bubble background
            String pad = " ".repeat(cfg.padding());
            out.append(lineFormat.replace("{text}", pad + line + pad).replace("{name}", speakerName));
        }
        return out.toString();
    }

    private static final class Speaker {
        String name = "";
        long utterance;
        int committedWords;
        int chunk;
        /** Insertion order = oldest first. */
        final LinkedHashMap<String, BubbleState> bubbles = new LinkedHashMap<>();

        void put(TranscribeConfig.Subtitles cfg, String key, String raw, boolean isFinal, boolean firstPart,
                 long expiresAt, AtomicLong versions, String speakerName) {
            BubbleState current = bubbles.get(key);
            if (current != null && current.isFinal && !isFinal) return; // never downgrade a final bubble
            if (current != null && current.raw.equals(raw) && current.isFinal == isFinal) {
                current.expiresAt = expiresAt;
                return;
            }
            String text = format(cfg, raw, isFinal, firstPart, speakerName);
            int lines = 1;
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
            BubbleState state = new BubbleState(key, utterance(key), raw, text, lines, isFinal, versions.incrementAndGet());
            state.expiresAt = expiresAt;
            bubbles.put(key, state);
        }

        void settle(long utterance, long expiresAt) {
            for (BubbleState b : bubbles.values()) {
                if (b.utterance == utterance && !b.isFinal) b.expiresAt = Math.min(b.expiresAt, expiresAt);
            }
        }

        private static long utterance(String key) {
            return Long.parseLong(key.substring(0, key.indexOf(':')));
        }
    }

    private static final class BubbleState {
        final String key;
        final long utterance;
        final String raw;
        final String text;
        final int lines;
        final boolean isFinal;
        final long version;
        long expiresAt;

        BubbleState(String key, long utterance, String raw, String text, int lines, boolean isFinal, long version) {
            this.key = key;
            this.utterance = utterance;
            this.raw = raw;
            this.text = text;
            this.lines = lines;
            this.isFinal = isFinal;
            this.version = version;
        }
    }
}
