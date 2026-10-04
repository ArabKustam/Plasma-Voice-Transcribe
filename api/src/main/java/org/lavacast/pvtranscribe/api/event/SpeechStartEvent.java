package org.lavacast.pvtranscribe.api.event;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * A player started talking in voice chat.
 */
public record SpeechStartEvent(
        @NotNull UUID speakerId,
        @NotNull String speakerName,
        long sessionId,
        @NotNull String voiceSource,
        @NotNull String voiceChannel,
        @NotNull Instant timestamp
) {
}
