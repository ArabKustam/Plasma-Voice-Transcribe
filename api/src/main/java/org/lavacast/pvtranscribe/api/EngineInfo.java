package org.lavacast.pvtranscribe.api;

import org.jetbrains.annotations.NotNull;

/**
 * Describes the speech-to-text engine in use.
 *
 * @param name                    engine id, for example {@code "vosk"}
 * @param language                configured recognition language, for example {@code "ru"}, or {@code "auto"}
 * @param state                   current state of the engine
 * @param supportsPartialResults  whether the engine produces intermediate results while the player speaks
 * @param supportsAutoLanguage    whether the engine can detect the spoken language by itself
 */
public record EngineInfo(
        @NotNull String name,
        @NotNull String language,
        @NotNull State state,
        boolean supportsPartialResults,
        boolean supportsAutoLanguage
) {

    public enum State {
        /** Not started (disabled in config). */
        STOPPED,
        /** Downloading or loading models. */
        LOADING,
        READY,
        /** Failed to start; see the server console. */
        FAILED
    }
}
