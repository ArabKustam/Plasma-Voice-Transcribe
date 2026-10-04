package org.lavacast.pvtranscribe.engine.tone;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.audio.Downsampler;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;

/**
 * Streaming recognition of one speaker with T-one.
 * <p>
 * Audio is downsampled to 8 kHz and fed in 300 ms chunks together with the model's hidden state from the
 * previous chunk. Each chunk yields 10 frames of letter probabilities; greedy CTC decoding turns all frames
 * of the current phrase into text, which grows while the player speaks (partial results). A pause of
 * {@code silence-ms} ends the phrase, like the phrase splitter of the reference implementation.
 */
final class ToneStream implements SpeechStream {

    static final int SAMPLE_RATE = 8_000;
    static final int CHUNK_SAMPLES = 2_400;
    static final int FRAME_MS = 30;
    private static final int STATE_SIZE = 219_729;
    private static final int VOCAB = 35;
    private static final int SPACE = 33;
    private static final int BLANK = 34;
    private static final String LABELS = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя ";
    /** A frame is silence if space + blank probability is above this (reference: 0.9). */
    private static final double SILENCE_THRESHOLD = 0.9;
    /** Silent frames kept before speech, so the first letter is not cut. */
    private static final int SPEECH_EXPAND = 3;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final Downsampler downsampler;
    private final int silenceFrames;

    private final int[] chunk = new int[CHUNK_SAMPLES];
    private int filled;
    /** Hidden state of the model (float16 values as raw bytes), carried from chunk to chunk. */
    private ByteBuffer state = newState();
    private boolean primed;

    // greedy tokens of the current phrase
    private int[] tokens = new int[256];
    private int tokenCount;
    private boolean hadSpeech;
    private int trailingSilence;
    private String lastText = "";

    ToneStream(OrtEnvironment env, OrtSession session, int inputSampleRate, int silenceFrames) {
        this.env = env;
        this.session = session;
        this.downsampler = new Downsampler(inputSampleRate, SAMPLE_RATE);
        this.silenceFrames = silenceFrames;
    }

    @Override
    public @NotNull EngineResult accept(short @NotNull [] pcm) throws Exception {
        if (!primed) {
            // the model was trained with 300 ms of silence before speech
            runChunk(new int[CHUNK_SAMPLES]);
            primed = true;
        }
        int[] samples = downsampler.process(pcm);
        EngineResult result = EngineResult.NONE;
        for (int sample : samples) {
            chunk[filled++] = sample;
            if (filled == CHUNK_SAMPLES) {
                filled = 0;
                runChunk(chunk);
                if (hadSpeech && trailingSilence >= silenceFrames) {
                    result = EngineResult.endpoint(takePhrase(), -1);
                } else if (result.type() != EngineResult.Type.FINAL) {
                    String text = decode();
                    if (!text.equals(lastText)) {
                        lastText = text;
                        result = EngineResult.partial(text);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public @NotNull EngineResult finish() throws Exception {
        if (primed) {
            // flush: the rest of the current chunk plus 300 ms of silence (right context)
            if (filled > 0) {
                java.util.Arrays.fill(chunk, filled, CHUNK_SAMPLES, 0);
                filled = 0;
                runChunk(chunk);
            }
            runChunk(new int[CHUNK_SAMPLES]);
        }
        return EngineResult.endpoint(takePhrase(), -1);
    }

    @Override
    public void reset() {
        state = newState();
        primed = false;
        filled = 0;
        downsampler.reset();
        clearPhrase();
    }

    @Override
    public void close() {
        // the session is shared and owned by the engine; nothing native per stream
    }

    private static ByteBuffer newState() {
        return ByteBuffer.allocateDirect(STATE_SIZE * 2).order(ByteOrder.nativeOrder());
    }

    private void runChunk(int[] audio) throws OrtException {
        try (OnnxTensor signal = OnnxTensor.createTensor(env, IntBuffer.wrap(audio), new long[]{1, CHUNK_SAMPLES, 1});
             OnnxTensor stateIn = OnnxTensor.createTensor(env, state.rewind(), new long[]{1, STATE_SIZE}, OnnxJavaType.FLOAT16);
             OrtSession.Result out = session.run(Map.of("signal", signal, "state", stateIn))) {

            OnnxTensor logprobs = (OnnxTensor) out.get("logprobs").orElseThrow();
            OnnxTensor stateOut = (OnnxTensor) out.get("state_next").orElseThrow();

            ByteBuffer next = stateOut.getByteBuffer();
            state.clear();
            state.put(next);
            state.flip();

            FloatBuffer lp = logprobs.getFloatBuffer();
            int frames = lp.remaining() / VOCAB;
            float[] frame = new float[VOCAB];
            for (int f = 0; f < frames; f++) {
                lp.get(frame);
                onFrame(frame);
            }
        }
    }

    private void onFrame(float[] logprobs) {
        int best = 0;
        for (int i = 1; i < VOCAB; i++) {
            if (logprobs[i] > logprobs[best]) best = i;
        }
        double silence = Math.exp(logprobs[SPACE]) + Math.exp(logprobs[BLANK]);
        boolean speech = silence <= SILENCE_THRESHOLD;

        if (!hadSpeech && !speech) {
            // still before the phrase: keep only a few frames of lead-in
            push(best);
            if (tokenCount > SPEECH_EXPAND) {
                System.arraycopy(tokens, tokenCount - SPEECH_EXPAND, tokens, 0, SPEECH_EXPAND);
                tokenCount = SPEECH_EXPAND;
            }
            return;
        }
        push(best);
        if (speech) {
            hadSpeech = true;
            trailingSilence = 0;
        } else {
            trailingSilence++;
        }
    }

    private void push(int token) {
        if (tokenCount == tokens.length) tokens = java.util.Arrays.copyOf(tokens, tokens.length * 2);
        tokens[tokenCount++] = token;
    }

    /**
     * Greedy CTC: collapse repeated tokens, drop blanks.
     */
    private String decode() {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        for (int i = 0; i < tokenCount; i++) {
            int t = tokens[i];
            if (t != prev && t < LABELS.length()) {
                char c = LABELS.charAt(t);
                if (c != ' ' || (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ')) sb.append(c);
            }
            prev = t;
        }
        return sb.toString().trim();
    }

    private String takePhrase() {
        String text = decode();
        clearPhrase();
        return text;
    }

    private void clearPhrase() {
        tokenCount = 0;
        hadSpeech = false;
        trailingSilence = 0;
        lastText = "";
    }
}
