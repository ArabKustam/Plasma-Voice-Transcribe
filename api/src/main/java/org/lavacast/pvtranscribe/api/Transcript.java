package org.lavacast.pvtranscribe.api;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.text.TextNormalizer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Recognised text of one phrase (utterance) of a player. Immutable.
 * <p>
 * While the player is still speaking the phrase, PV-Transcribe delivers several <b>partial</b>
 * transcripts with the same {@link #getUtteranceId()} and growing text. When the phrase ends, exactly
 * one <b>final</b> transcript with that id is delivered. A newer phrase always has a larger utterance id,
 * so a result can never be confused with another phrase.
 */
public final class Transcript {

    private final UUID speakerId;
    private final String speakerName;
    private final long sessionId;
    private final long utteranceId;
    private final String rawText;
    private final String text;
    private final String normalizedText;
    private final boolean isFinal;
    private final String language;
    private final double confidence;
    private final String voiceSource;
    private final String voiceChannel;
    private final Instant startedAt;
    private final Instant timestamp;

    public Transcript(
            @NotNull UUID speakerId,
            @NotNull String speakerName,
            long sessionId,
            long utteranceId,
            @NotNull String rawText,
            @NotNull String text,
            boolean isFinal,
            @NotNull String language,
            double confidence,
            @NotNull String voiceSource,
            @NotNull String voiceChannel,
            @NotNull Instant startedAt,
            @NotNull Instant timestamp
    ) {
        this.speakerId = Objects.requireNonNull(speakerId, "speakerId");
        this.speakerName = Objects.requireNonNull(speakerName, "speakerName");
        this.sessionId = sessionId;
        this.utteranceId = utteranceId;
        this.rawText = Objects.requireNonNull(rawText, "rawText");
        this.text = Objects.requireNonNull(text, "text");
        this.normalizedText = TextNormalizer.normalize(text);
        this.isFinal = isFinal;
        this.language = Objects.requireNonNull(language, "language");
        this.confidence = confidence;
        this.voiceSource = Objects.requireNonNull(voiceSource, "voiceSource");
        this.voiceChannel = Objects.requireNonNull(voiceChannel, "voiceChannel");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public @NotNull UUID getSpeakerId() {
        return speakerId;
    }

    public @NotNull String getSpeakerName() {
        return speakerName;
    }

    /**
     * Id of the {@link SpeechSession} this phrase belongs to.
     */
    public long getSessionId() {
        return sessionId;
    }

    /**
     * Unique (per server run) id of the phrase. All partial results and the final result of one phrase
     * share it; later phrases have larger ids.
     */
    public long getUtteranceId() {
        return utteranceId;
    }

    /**
     * Text exactly as returned by the speech-to-text engine.
     */
    public @NotNull String getRawText() {
        return rawText;
    }

    /**
     * Cleaned up text: engine markers (like {@code [unk]}) removed and whitespace collapsed.
     * Case and punctuation are kept as the engine produced them.
     */
    public @NotNull String getText() {
        return text;
    }

    /**
     * Lower-case text without punctuation, with {@code ё} replaced by {@code е}. Use it for matching
     * words and phrases. See {@link TextNormalizer}.
     */
    public @NotNull String getNormalizedText() {
        return normalizedText;
    }

    /**
     * @return {@code true} for the final result of the phrase, {@code false} for an intermediate one
     */
    public boolean isFinal() {
        return isFinal;
    }

    public boolean isPartial() {
        return !isFinal;
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    /**
     * Language of the recognition (configured or detected), for example {@code "ru"}.
     */
    public @NotNull String getLanguage() {
        return language;
    }

    /**
     * Engine confidence from 0 to 1, or -1 if the engine does not report it.
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Voice chat the audio came from, for example {@code "plasmovoice"}.
     */
    public @NotNull String getVoiceSource() {
        return voiceSource;
    }

    /**
     * Voice channel the phrase was spoken into, for example {@code "proximity"}, {@code "groups"}
     * or {@code "broadcast"}; {@code "unknown"} if the voice chat does not report it.
     */
    public @NotNull String getVoiceChannel() {
        return voiceChannel;
    }

    /**
     * When the player started this phrase.
     */
    public @NotNull Instant getStartedAt() {
        return startedAt;
    }

    /**
     * When this result was produced.
     */
    public @NotNull Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return whether the normalized text contains the given words as a whole-word phrase
     */
    public boolean containsPhrase(@NotNull String phrase) {
        return TextNormalizer.containsPhrase(normalizedText, TextNormalizer.normalize(phrase));
    }

    @Override
    public String toString() {
        return "Transcript{" + speakerName + " #" + utteranceId + (isFinal ? " final" : " partial") + ": '" + text + "'}";
    }
}
