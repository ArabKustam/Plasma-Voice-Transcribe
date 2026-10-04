package org.lavacast.pvtranscribe.core.engine;

import org.jetbrains.annotations.NotNull;

/**
 * Result of feeding audio to a {@link SpeechStream}.
 *
 * @param type       what happened
 * @param text       raw text from the engine (empty for {@link Type#NONE})
 * @param confidence 0..1, or -1 if unknown
 * @param language   detected language, or null to use the engine language
 */
public record EngineResult(@NotNull Type type, @NotNull String text, double confidence, String language) {

    public static final EngineResult NONE = new EngineResult(Type.NONE, "", -1, null);

    public enum Type {
        NONE,
        /** Intermediate hypothesis of the current phrase. */
        PARTIAL,
        /** Final text of a phrase. */
        FINAL
    }

    public static EngineResult partial(@NotNull String text) {
        return new EngineResult(Type.PARTIAL, text, -1, null);
    }

    public static EngineResult endpoint(@NotNull String text, double confidence) {
        return new EngineResult(Type.FINAL, text, confidence, null);
    }
}
