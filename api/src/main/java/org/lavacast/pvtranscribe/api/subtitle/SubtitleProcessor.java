package org.lavacast.pvtranscribe.api.subtitle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lavacast.pvtranscribe.api.Transcript;

/**
 * Changes the text shown in subtitles above the speaker's head, for example to censor words.
 * Runs on a worker thread; must be fast and thread-safe.
 */
@FunctionalInterface
public interface SubtitleProcessor {

    /**
     * @param transcript the transcript being displayed
     * @param text       text after previous processors
     * @return new text, or {@code null} to hide this transcript from subtitles
     */
    @Nullable String process(@NotNull Transcript transcript, @NotNull String text);
}
