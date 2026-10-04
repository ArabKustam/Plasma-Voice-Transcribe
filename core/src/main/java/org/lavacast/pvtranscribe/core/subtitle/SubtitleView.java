package org.lavacast.pvtranscribe.core.subtitle;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a platform renderer should show above one speaker right now.
 *
 * @param bubbles  speech bubbles, the newest (closest to the head) first
 * @param audience who hears the speaker according to the voice chat
 */
public record SubtitleView(
        @NotNull UUID speakerId,
        @NotNull String speakerName,
        @NotNull List<Bubble> bubbles,
        @NotNull AudienceSnapshot audience
) {

    /**
     * One speech bubble.
     *
     * @param key     stable id of the bubble while it lives (phrase + part)
     * @param text    formatted text (section-sign color codes, lines separated by {@code \n})
     * @param lines   number of text lines
     * @param version changes whenever the text changes
     * @param opacity 1 = fully visible, falls to 0 while fading out
     */
    public record Bubble(@NotNull String key, @NotNull String text, int lines, boolean isFinal, long version, float opacity) {
    }

    /**
     * @param known           whether the voice chat reported any audience for this speaker
     * @param listeners       union of listeners of all channels the speaker used recently
     * @param proximityRadius largest proximity voice distance in use, or -1 if no proximity channel
     */
    public record AudienceSnapshot(boolean known, @NotNull Set<UUID> listeners, double proximityRadius) {
        public static final AudienceSnapshot UNKNOWN = new AudienceSnapshot(false, Set.of(), -1);
    }
}
