package org.lavacast.pvtranscribe.engine.cloud;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.config.ConfigSection;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.util.Json;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Realtime transcription over WebSocket. Config section {@code engine.openai}:
 * <pre>
 * api-key: "sk-..."
 * model: gpt-live-transcribe
 * prompt: ""
 * </pre>
 * The session is configured without server-side turn detection: PV-Transcribe decides where a phrase ends
 * (end of push-to-talk / voice activation, silence) and commits the audio buffer, the service streams text
 * deltas while the player speaks and a completed transcript after the commit.
 */
public final class OpenAiEngineFactory implements SpeechEngineFactory {

    public static final String TYPE = "openai";
    private static final int SERVICE_RATE = 24_000;

    @Override
    public @NotNull SpeechEngine create(@NotNull Context context, @NotNull ConfigSection settings) {
        String apiKey = settings.getString("api-key", "").trim();
        String model = settings.getString("model", "gpt-live-transcribe");
        String url = settings.getString("url", "wss://api.openai.com/v1/realtime?intent=transcription");
        String prompt = settings.getString("prompt", "");
        boolean hints = settings.getBoolean("player-names-as-keywords", true);
        return new CloudEngine(TYPE, apiKey, "engine.openai.api-key", context,
                new EngineCapabilities(true, true, false),
                (engine, rate) -> new Stream(engine, rate, url, model, prompt, hints));
    }

    private static final class Stream extends CloudStream {
        private final CloudEngine engine;
        private final String url;
        private final String model;
        private final String prompt;
        private final boolean hints;
        private final Map<String, StringBuilder> items = new HashMap<>();

        Stream(CloudEngine engine, int rate, String url, String model, String prompt, boolean hints) {
            super(engine, rate, SERVICE_RATE);
            this.engine = engine;
            this.url = url;
            this.model = model;
            this.prompt = prompt;
            this.hints = hints;
        }

        @Override
        URI uri() {
            return URI.create(url);
        }

        @Override
        Map<String, String> headers() {
            return Map.of("Authorization", "Bearer " + engine.apiKey());
        }

        @Override
        void configure(WebSocket socket) {
            Map<String, Object> transcription = new LinkedHashMap<>();
            transcription.put("model", model);
            if (!engine.context().autoDetect()) transcription.put("languages", List.of(engine.context().language()));
            if (!prompt.isBlank()) transcription.put("prompt", prompt);
            if (hints) {
                List<String> names = engine.vocabulary(50);
                if (!names.isEmpty()) transcription.put("keywords", names);
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("format", Map.of("type", "audio/pcm", "rate", SERVICE_RATE));
            input.put("transcription", transcription);
            input.put("turn_detection", null);
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("type", "transcription");
            session.put("audio", Map.of("input", input));
            sendText(socket, Json.write(Map.of("type", "session.update", "session", session)));
        }

        @Override
        void sendAudio(WebSocket socket, byte[] pcm) {
            sendText(socket, Json.write(Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", Base64.getEncoder().encodeToString(pcm))));
        }

        @Override
        void requestFinal(WebSocket socket) {
            sendText(socket, "{\"type\":\"input_audio_buffer.commit\"}");
        }

        @Override
        void onMessage(String message) {
            Object json = Json.parse(message);
            String type = Json.string(json, "type");
            switch (type) {
                case "conversation.item.input_audio_transcription.delta" -> {
                    StringBuilder sb = items.computeIfAbsent(Json.string(json, "item_id"), k -> new StringBuilder());
                    sb.append(Json.string(json, "delta"));
                    partial(sb.toString().trim());
                }
                case "conversation.item.input_audio_transcription.completed" -> {
                    items.remove(Json.string(json, "item_id"));
                    complete(Json.string(json, "transcript").trim());
                }
                case "conversation.item.input_audio_transcription.failed", "error" -> {
                    String msg = Json.string(json, "error", "message");
                    String code = Json.string(json, "error", "code");
                    if (!code.equals("input_audio_buffer_commit_empty")) {
                        fail("OpenAI error: " + (msg.isEmpty() ? message : msg));
                    }
                }
                default -> {
                }
            }
        }
    }
}
