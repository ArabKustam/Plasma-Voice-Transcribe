package org.lavacast.pvtranscribe.engine.tone;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lavacast.pvtranscribe.core.config.MapConfigSection;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

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
 * Real recognition with the real T-one model:
 * {@code gradlew :engine-tone:test -Dtone.model=<model.onnx> -Dtone.wav=<16-bit mono wav>}
 */
@EnabledIfSystemProperty(named = "tone.model", matches = ".+")
class ToneEngineIT {

    @Test
    void recognisesSpeechWithPartials() throws Exception {
        Path model = Path.of(System.getProperty("tone.model"));
        byte[] wav = Files.readAllBytes(Path.of(System.getProperty("tone.wav")));
        int sampleRate = ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        ByteBuffer data = ByteBuffer.wrap(wav, 44, wav.length - 44).order(ByteOrder.LITTLE_ENDIAN);
        short[] pcm = new short[data.remaining() / 2];
        data.asShortBuffer().get(pcm);

        SpeechEngine engine = new ToneEngineFactory().create(
                new SpeechEngineFactory.Context(model.getParent(), "ru", false, new StdoutLogger()),
                new MapConfigSection(Map.of("model-file", model.getFileName().toString(), "auto-download", false)));
        engine.start();

        List<String> partials = new ArrayList<>();
        List<String> finals = new ArrayList<>();
        int frame = sampleRate / 50;
        long start = System.nanoTime();
        SpeechStream stream = engine.createStream(sampleRate);
        for (int i = 0; i + frame <= pcm.length; i += frame) {
            EngineResult r = stream.accept(Arrays.copyOfRange(pcm, i, i + frame));
            if (r.type() == EngineResult.Type.PARTIAL && !r.text().isEmpty()) {
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
        System.out.println("Processed " + pcm.length * 1000L / sampleRate + " ms of audio in "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
        stream.close();
        engine.close();

        assertFalse(partials.isEmpty(), "expected partial results while speaking");
        assertTrue(String.join(" ", finals).contains("шахт"), String.join(" | ", finals));
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
