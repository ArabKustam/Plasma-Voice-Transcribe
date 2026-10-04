package org.lavacast.pvtranscribe.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A continuous period of a player talking in voice chat: from the first audio packet until the
 * player releases push-to-talk, voice activation stops, or silence times out.
 * One session can contain several phrases (utterances).
 */
public interface SpeechSession {

    @NotNull UUID getSpeakerId();

    @NotNull String getSpeakerName();

    /**
     * Unique (per server run) id of this session.
     */
    long getSessionId();

    /**
     * Voice chat the audio came from, for example {@code "plasmovoice"}.
     */
    @NotNull String getVoiceSource();

    /**
     * Voice channel the player last spoke into, for example {@code "proximity"}, {@code "groups"},
     * {@code "broadcast"}. {@code "unknown"} if the voice chat does not report it.
     */
    @NotNull String getVoiceChannel();

    @NotNull Instant getStartedAt();

    /**
     * @return the latest transcript (partial or final) of the current phrase, if any text was recognised yet
     */
    @NotNull Optional<Transcript> getCurrentTranscript();
}
