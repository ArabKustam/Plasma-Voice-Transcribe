package org.lavacast.pvtranscribe.core.audio;

/**
 * Converts voice chat audio (16/24/48 kHz) to the 8 kHz the model expects: windowed-sinc low-pass
 * filter evaluated only at the output positions (integer decimation). Keeps filter history between
 * calls, so 20 ms frames can be fed one by one.
 */
public final class Downsampler {

    private final int factor;
    private final float[] taps;
    private final float[] history;
    private long inputIndex;
    private int phase;

    public Downsampler(int inputRate, int outputRate) {
        if (inputRate % outputRate != 0) {
            throw new IllegalArgumentException("Voice chat sample rate " + inputRate + " Hz is not a multiple of "
                    + outputRate + " Hz");
        }
        this.factor = inputRate / outputRate;
        if (factor == 1) {
            taps = new float[]{1f};
        } else {
            int n = 16 * factor + 1;
            taps = new float[n];
            double cutoff = 0.45 / factor; // just below the new Nyquist frequency, relative to the input rate
            double sum = 0;
            int mid = n / 2;
            for (int i = 0; i < n; i++) {
                int k = i - mid;
                double sinc = k == 0 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * k) / (Math.PI * k);
                double window = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)) + 0.08 * Math.cos(4 * Math.PI * i / (n - 1));
                taps[i] = (float) (sinc * window);
                sum += taps[i];
            }
            for (int i = 0; i < n; i++) taps[i] /= (float) sum;
        }
        history = new float[taps.length];
    }

    /**
     * @return downsampled samples (may be empty)
     */
    public int[] process(short[] input) {
        if (factor == 1) {
            int[] out = new int[input.length];
            for (int i = 0; i < input.length; i++) out[i] = input[i];
            return out;
        }
        int[] out = new int[(input.length + phase) / factor + 1];
        int count = 0;
        int len = history.length;
        for (short sample : input) {
            history[(int) (inputIndex % len)] = sample;
            inputIndex++;
            if (++phase == factor) {
                phase = 0;
                double acc = 0;
                for (int t = 0; t < len; t++) {
                    acc += taps[t] * history[(int) ((inputIndex - 1 - t + len * 1024L) % len)];
                }
                int v = (int) Math.round(acc);
                out[count++] = Math.max(-32768, Math.min(32767, v));
            }
        }
        if (count == out.length) return out;
        int[] trimmed = new int[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    public void reset() {
        java.util.Arrays.fill(history, 0f);
        inputIndex = 0;
        phase = 0;
    }
}
