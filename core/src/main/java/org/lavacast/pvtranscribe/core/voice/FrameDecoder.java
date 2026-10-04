package org.lavacast.pvtranscribe.core.voice;

import org.jetbrains.annotations.NotNull;

/**
 * Decodes frames of one speaker to 16-bit mono PCM.
 */
public interface FrameDecoder extends AutoCloseable {

    short @NotNull [] decode(@NotNull VoiceFrame frame) throws Exception;

    /**
     * Resets codec state, e.g. at the start of a new speech session.
     */
    void reset();

    @Override
    void close();

    /**
     * Interleaved stereo to mono.
     */
    static short[] downmix(short[] stereo) {
        short[] mono = new short[stereo.length / 2];
        for (int i = 0; i < mono.length; i++) {
            mono[i] = (short) ((stereo[2 * i] + stereo[2 * i + 1]) / 2);
        }
        return mono;
    }
}
