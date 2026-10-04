package org.lavacast.pvtranscribe.engine.cloud;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Engine backed by a cloud service. Holds the API key and one shared HTTP client; each speaker gets a
 * {@link CloudStream} with its own WebSocket.
 */
final class CloudEngine implements SpeechEngine {

    private final String name;
    private final String apiKey;
    private final String keySetting;
    private final SpeechEngineFactory.Context context;
    private final EngineCapabilities capabilities;
    private final Function<Integer, CloudStream> streams;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** After a connection failure, new connections are refused until this time (shared by all speakers). */
    volatile long blockedUntil;
    volatile String lastError = "";

    CloudEngine(String name, String apiKey, String keySetting, SpeechEngineFactory.Context context,
                EngineCapabilities capabilities, StreamFactory streams) {
        this.name = name;
        this.apiKey = apiKey;
        this.keySetting = keySetting;
        this.context = context;
        this.capabilities = capabilities;
        this.streams = rate -> streams.create(this, rate);
    }

    @FunctionalInterface
    interface StreamFactory {
        CloudStream create(CloudEngine engine, int sampleRate);
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull EngineCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public void start() {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("No API key: set " + keySetting + " in config.yml. "
                    + "Note: with a cloud engine players' voice is sent to " + name + ".");
        }
        context.logger().info("Using cloud speech recognition '" + name + "'. Players' voice is sent to this service "
                + "while they speak; it is billed per minute of audio.");
    }

    @Override
    public @NotNull SpeechStream createStream(int sampleRate) {
        return streams.apply(sampleRate);
    }

    @Override
    public @NotNull String language() {
        return context.autoDetect() ? "auto" : context.language();
    }

    @Override
    public void close() {
        // WebSockets are closed by their streams
    }

    String apiKey() {
        return apiKey;
    }

    SpeechEngineFactory.Context context() {
        return context;
    }

    /**
     * Names of online players, to help the service spell them.
     */
    List<String> vocabulary(int limit) {
        Collection<String> names = context.vocabulary().get();
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (out.size() >= limit) break;
            if (n.length() >= 3) out.add(n);
        }
        return out;
    }
}
