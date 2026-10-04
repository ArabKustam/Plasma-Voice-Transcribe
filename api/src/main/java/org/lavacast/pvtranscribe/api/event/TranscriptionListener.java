package org.lavacast.pvtranscribe.api.event;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Transcript;

/**
 * Receives speech events of players. Override only what you need.
 * <p>
 * Order for one player: {@code onSpeechStart}, then for each phrase any number of
 * {@code onPartialTranscript} followed by one {@code onFinalTranscript}, then {@code onSpeechEnd}.
 */
public interface TranscriptionListener {

    /**
     * The player started talking.
     */
    default void onSpeechStart(@NotNull SpeechStartEvent event) {
    }

    /**
     * Intermediate result: the player is still speaking and the text may still change.
     */
    default void onPartialTranscript(@NotNull Transcript transcript) {
    }

    /**
     * Final result of a phrase. Delivered once per phrase, only when some text was recognised.
     */
    default void onFinalTranscript(@NotNull Transcript transcript) {
    }

    /**
     * Called for both partial and final results, after the specific method.
     * Use {@link Transcript#isFinal()} to tell them apart.
     */
    default void onTranscript(@NotNull Transcript transcript) {
    }

    /**
     * The player stopped talking.
     */
    default void onSpeechEnd(@NotNull SpeechEndEvent event) {
    }
}
