package org.lavacast.pvtranscribe.core.voice;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Who hears a speaker through one voice channel at a moment in time.
 *
 * @param channel   voice channel, e.g. "proximity", "groups", "broadcast"
 * @param type      how the channel routes audio
 * @param listeners players the voice chat sends the audio to
 * @param radius    for {@link Type#PROXIMITY}: voice distance in blocks; otherwise -1
 * @param timestamp {@link System#currentTimeMillis()} when computed
 */
public record Audience(
        @NotNull String channel,
        @NotNull Type type,
        @NotNull Set<UUID> listeners,
        double radius,
        long timestamp
) {

    public enum Type {
        /** Audio is heard around the speaker within a radius. */
        PROXIMITY,
        /** Audio is sent to an explicit list of players (groups, broadcast, direct). */
        DIRECT
    }
}
