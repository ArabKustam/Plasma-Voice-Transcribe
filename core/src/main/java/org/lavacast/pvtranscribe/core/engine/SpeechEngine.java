package org.lavacast.pvtranscribe.core.engine;

import org.jetbrains.annotations.NotNull;

/**
 * A speech-to-text engine (local or cloud). One engine instance serves all speakers;
 * each speaker gets its own {@link SpeechStream}.
 * <p>
 * To add a new engine: implement this interface and {@link SpeechEngineFactory}, register the factory
 * in {@link EngineRegistry}, and select it with {@code engine.type} in the config.
 */
public interface SpeechEngine extends AutoCloseable {

    @NotNull String name();

    @NotNull EngineCapabilities capabilities();

    /**
     * Loads models, opens connections, etc. Called on a background thread; may take a long time.
     */
    void start() throws Exception;

    /**
     * Creates a recognition stream for one speaker.
     *
     * @param sampleRate sample rate of the mono PCM that will be fed
     */
    @NotNull SpeechStream createStream(int sampleRate) throws Exception;

    /**
     * Language actually used (may differ from the config if a fallback was applied).
     */
    @NotNull String language();

    @Override
    void close();
}
