package org.lavacast.pvtranscribe.api.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lavacast.pvtranscribe.api.Transcript;

import java.time.Instant;
import java.util.UUID;

/**
 * A player stopped talking.
 *
 * @param lastTranscript final transcript of the last phrase of the session, {@code null} if nothing was recognised
 */
public record SpeechEndEvent(
        @NotNull UUID speakerId,
        @NotNull String speakerName,
        long sessionId,
        @NotNull String voiceSource,
        @NotNull Reason reason,
        @Nullable Transcript lastTranscript,
        @NotNull Instant timestamp
) {

    public enum Reason {
        /** The voice chat reported the end of speech (push-to-talk released, voice activation stopped). */
        STOPPED_TALKING,
        /** No audio arrived for the configured silence timeout. */
        SILENCE_TIMEOUT,
        /** The player left the server. */
        DISCONNECTED,
        /** Transcription was disabled for the player, the world, or the whole plugin. */
        DISABLED
    }
}
