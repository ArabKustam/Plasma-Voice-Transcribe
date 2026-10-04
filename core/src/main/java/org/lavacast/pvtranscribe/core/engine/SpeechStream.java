package org.lavacast.pvtranscribe.core.engine;

import org.jetbrains.annotations.NotNull;

/**
 * Streaming recognition of one speaker. Used by one thread at a time; not thread-safe.
 */
public interface SpeechStream extends AutoCloseable {

    /**
     * Feeds mono 16-bit PCM.
     *
     * @return {@link EngineResult#partial(String)} with the current hypothesis,
     * {@link EngineResult#endpoint(String, double)} if the engine detected the end of a phrase by itself
     * (the stream then continues with a new phrase), or {@link EngineResult#NONE}
     */
    @NotNull EngineResult accept(short @NotNull [] pcm) throws Exception;

    /**
     * Ends the current phrase and returns its final text; the stream is ready for a new phrase afterwards.
     */
    @NotNull EngineResult finish() throws Exception;

    /**
     * Drops the current phrase without producing a result.
     */
    void reset();

    @Override
    void close();
}
