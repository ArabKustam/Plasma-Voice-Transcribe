package org.lavacast.pvtranscribe.engine.vosk;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lavacast.pvtranscribe.core.config.MapConfigSection;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real recognition with a real Vosk model. Runs only when a models folder and a 16-bit mono WAV are given:
 * {@code gradlew :engine-vosk:test -Dvosk.models=<folder> -Dvosk.wav=<file.wav>}
 */
@EnabledIfSystemProperty(named = "vosk.models", matches = ".+")
class VoskEngineIT {

    @Test
    void recognisesSpeechWithPartials() throws Exception {
        Path models = Path.of(System.getProperty("vosk.models"));
        byte[] wav = Files.readAllBytes(Path.of(System.getProperty("vosk.wav")));
        int sampleRate = ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        short[] pcm = pcm(wav);

        SpeechEngine engine = new VoskEngineFactory().create(
                new SpeechEngineFactory.Context(models.getParent(), "ru", false, new StdoutLogger()),
                new MapConfigSection(Map.of("models-folder", models.getFileName().toString(), "auto-download", false)));
        engine.start();

        List<String> partials = new ArrayList<>();
        List<String> finals = new ArrayList<>();
        int frame = sampleRate / 50; // 20 ms, like voice chat packets
        long start = System.nanoTime();
        try (SpeechStream stream = engine.createStream(sampleRate)) {
            for (int i = 0; i + frame <= pcm.length; i += frame) {
                EngineResult r = stream.accept(Arrays.copyOfRange(pcm, i, i + frame));
                if (r.type() == EngineResult.Type.PARTIAL && !r.text().isEmpty()
                        && (partials.isEmpty() || !partials.get(partials.size() - 1).equals(r.text()))) {
                    partials.add(r.text());
                    System.out.println("partial @" + (i * 1000L / sampleRate) + "ms: " + r.text());
                } else if (r.type() == EngineResult.Type.FINAL) {
                    finals.add(r.text());
                    System.out.println("FINAL   @" + (i * 1000L / sampleRate) + "ms: " + r.text());
                }
            }
            EngineResult last = stream.finish();
            finals.add(last.text());
            System.out.println("FINAL (end): " + last.text());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Processed " + pcm.length * 1000L / sampleRate + " ms of audio in " + ms + " ms");
        engine.close();

        assertFalse(partials.isEmpty(), "expected partial results while speaking");
        String all = String.join(" ", finals);
        assertTrue(all.contains("шахт"), all);
    }

    private static short[] pcm(byte[] wav) {
        // find the "data" chunk
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4);
            int size = ByteBuffer.wrap(wav, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (id.equals("data")) {
                ByteBuffer data = ByteBuffer.wrap(wav, pos + 8, Math.min(size, wav.length - pos - 8)).order(ByteOrder.LITTLE_ENDIAN);
                short[] out = new short[data.remaining() / 2];
                data.asShortBuffer().get(out);
                return out;
            }
            pos += 8 + size;
        }
        throw new IllegalArgumentException("no data chunk");
    }

    private static final class StdoutLogger implements PlatformLogger {
        @Override
        public void info(@NotNull String message) {
            System.out.println("[info] " + message);
        }

        @Override
        public void warn(@NotNull String message, Throwable error) {
            System.out.println("[warn] " + message);
        }

        @Override
        public void error(@NotNull String message, Throwable error) {
            System.out.println("[error] " + message);
        }
    }
}
