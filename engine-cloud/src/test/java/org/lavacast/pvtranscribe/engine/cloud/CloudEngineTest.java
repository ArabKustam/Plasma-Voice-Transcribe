package org.lavacast.pvtranscribe.engine.cloud;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.lavacast.pvtranscribe.core.config.MapConfigSection;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.util.Json;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEngineTest {

    private static final PlatformLogger LOG = new PlatformLogger() {
        @Override
        public void info(@NotNull String message) {
        }

        @Override
        public void warn(@NotNull String message, Throwable error) {
        }

        @Override
        public void error(@NotNull String message, Throwable error) {
        }
    };

    private static SpeechEngineFactory.Context ctx() {
        return new SpeechEngineFactory.Context(Path.of("build"), "ru", false, LOG, () -> List.of("buletka"));
    }

    @Test
    void missingKeyIsReportedOnStart() throws Exception {
        SpeechEngine engine = new DeepgramEngineFactory().create(ctx(), MapConfigSection.empty());
        IllegalStateException e = assertThrows(IllegalStateException.class, engine::start);
        assertTrue(e.getMessage().contains("engine.deepgram.api-key"));
    }

    @Test
    void jsonRoundTrip() {
        Object json = Json.parse("{\"type\":\"Results\",\"is_final\":true,\"channel\":{\"alternatives\":[{\"transcript\":\"привет \\\"мир\\\"\"}]}}");
        assertEquals("привет \"мир\"", Json.string(json, "channel", "alternatives", 0, "transcript"));
        assertTrue(Json.bool(json, "is_final"));
        String written = Json.write(Map.of("a", List.of(1, "x\n")));
        assertEquals("{\"a\":[1,\"x\\n\"]}", written);
    }

    /**
     * Connects to the real service with an invalid key: must fail cleanly with a readable error, not hang.
     */
    @Test
    void invalidKeyFailsCleanly() throws Exception {
        if (System.getProperty("cloud.online") == null) return;
        for (SpeechEngineFactory factory : List.of(new DeepgramEngineFactory(), new OpenAiEngineFactory())) {
            SpeechEngine engine = factory.create(ctx(), new MapConfigSection(Map.of("api-key", "invalid-key")));
            engine.start();
            SpeechStream stream = engine.createStream(48_000);
            Exception e = assertThrows(Exception.class, () -> {
                for (int i = 0; i < 20; i++) {
                    stream.accept(new short[960]);
                    Thread.sleep(20);
                }
            });
            System.out.println(engine.name() + ": " + e.getMessage());
            stream.close();
        }
    }
}
