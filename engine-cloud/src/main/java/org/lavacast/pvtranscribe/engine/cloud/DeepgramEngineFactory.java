package org.lavacast.pvtranscribe.engine.cloud;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.config.ConfigSection;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.util.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Deepgram streaming speech-to-text. Config section {@code engine.deepgram}:
 * <pre>
 * api-key: "..."
 * model: nova-3
 * endpointing-ms: 500
 * </pre>
 * Interim results are shown while the player speaks; Deepgram's own endpointing (or the end of
 * push-to-talk, via "Finalize") ends a phrase.
 */
public final class DeepgramEngineFactory implements SpeechEngineFactory {

    public static final String TYPE = "deepgram";
    private static final int SERVICE_RATE = 16_000;

    @Override
    public @NotNull SpeechEngine create(@NotNull Context context, @NotNull ConfigSection settings) {
        String apiKey = settings.getString("api-key", "").trim();
        String model = settings.getString("model", "nova-3");
        int endpointing = Math.max(10, settings.getInt("endpointing-ms", 500));
        boolean hints = settings.getBoolean("player-names-as-keyterms", true);
        return new CloudEngine(TYPE, apiKey, "engine.deepgram.api-key", context,
                new EngineCapabilities(true, true, true),
                (engine, rate) -> new Stream(engine, rate, model, endpointing, hints));
    }

    private static final class Stream extends CloudStream {
        private final CloudEngine engine;
        private final String model;
        private final int endpointing;
        private final boolean hints;
        private final StringBuilder committed = new StringBuilder();

        Stream(CloudEngine engine, int rate, String model, int endpointing, boolean hints) {
            super(engine, rate, SERVICE_RATE);
            this.engine = engine;
            this.model = model;
            this.endpointing = endpointing;
            this.hints = hints;
        }

        @Override
        URI uri() {
            String language = engine.context().autoDetect() ? "multi" : engine.context().language();
            StringBuilder url = new StringBuilder("wss://api.deepgram.com/v1/listen")
                    .append("?model=").append(enc(model))
                    .append("&language=").append(enc(language))
                    .append("&interim_results=true&punctuate=true&smart_format=true")
                    .append("&encoding=linear16&channels=1&sample_rate=").append(SERVICE_RATE)
                    .append("&endpointing=").append(endpointing);
            if (hints) {
                for (String name : engine.vocabulary(50)) url.append("&keyterm=").append(enc(name));
            }
            return URI.create(url.toString());
        }

        @Override
        Map<String, String> headers() {
            return Map.of("Authorization", "Token " + engine.apiKey());
        }

        @Override
        void configure(WebSocket socket) {
            committed.setLength(0);
        }

        @Override
        void sendAudio(WebSocket socket, byte[] pcm) {
            sendBinary(socket, pcm);
        }

        @Override
        void requestFinal(WebSocket socket) {
            sendText(socket, "{\"type\":\"Finalize\"}");
        }

        @Override
        void onMessage(String message) {
            Object json = Json.parse(message);
            String type = Json.string(json, "type");
            if (type.equals("Error") || Json.get(json, "err_code") != null) {
                fail("Deepgram error: " + message);
                return;
            }
            if (!type.equals("Results")) return;

            String text = Json.string(json, "channel", "alternatives", 0, "transcript").trim();
            boolean isFinal = Json.bool(json, "is_final");
            boolean phraseEnd = Json.bool(json, "speech_final") || Json.bool(json, "from_finalize");
            synchronized (committed) {
                if (isFinal) {
                    if (!text.isEmpty()) {
                        if (committed.length() > 0) committed.append(' ');
                        committed.append(text);
                    }
                    if (phraseEnd) {
                        String phrase = committed.toString();
                        committed.setLength(0);
                        complete(phrase);
                    } else if (committed.length() > 0) {
                        partial(committed.toString());
                    }
                } else if (!text.isEmpty()) {
                    partial(committed.length() > 0 ? committed + " " + text : text);
                }
            }
        }

        private static String enc(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
    }
}
