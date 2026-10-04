package org.lavacast.pvtranscribe.engine.cloud;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.audio.Downsampler;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * One speaker's connection to a cloud recognizer.
 * <p>
 * Audio is sent as it arrives; results come back asynchronously on the HTTP client's threads and are
 * collected here, so {@link #accept} can return the newest partial or a finished phrase without blocking.
 * {@link #finish} asks the service to finish the phrase and waits a short time for the final text.
 * The connection is opened lazily and re-opened if the service closed it (idle timeouts).
 */
abstract class CloudStream implements SpeechStream, WebSocket.Listener {

    private static final long FINAL_WAIT_MS = 2_500;

    private static final long RETRY_DELAY_MS = 15_000;

    private final CloudEngine engine;
    private final HttpClient http;
    private final Downsampler downsampler;
    private final ConcurrentLinkedDeque<EngineResult> results = new ConcurrentLinkedDeque<>();
    private final StringBuilder incoming = new StringBuilder();
    private final Object finalSignal = new Object();

    private volatile WebSocket socket;
    private volatile boolean open;
    private volatile String failure;
    private long finalsReceived;
    private long sentSinceFinal;
    private String lastPartial = "";

    CloudStream(CloudEngine engine, int inputSampleRate, int serviceSampleRate) {
        this.engine = engine;
        this.http = engine.http;
        this.downsampler = new Downsampler(inputSampleRate, serviceSampleRate);
    }

    // ------------------------------------------------------------------ provider specifics

    abstract URI uri();

    abstract Map<String, String> headers();

    /** Configure the session right after the connection opened. */
    abstract void configure(WebSocket socket);

    /** Send 16-bit little-endian PCM at the service sample rate. */
    abstract void sendAudio(WebSocket socket, byte[] pcm);

    /** Ask the service to finish the current phrase now. */
    abstract void requestFinal(WebSocket socket);

    /** Handle one text message from the service; call {@link #partial} / {@link #complete} / {@link #fail}. */
    abstract void onMessage(String message);

    // ------------------------------------------------------------------ SpeechStream

    @Override
    public @NotNull EngineResult accept(short @NotNull [] pcm) throws Exception {
        String error = failure;
        if (error != null) {
            failure = null;
            throw new IllegalStateException(error);
        }
        WebSocket ws = ensureOpen();
        int[] samples = downsampler.process(pcm);
        if (samples.length > 0) {
            byte[] bytes = new byte[samples.length * 2];
            for (int i = 0; i < samples.length; i++) {
                bytes[2 * i] = (byte) samples[i];
                bytes[2 * i + 1] = (byte) (samples[i] >> 8);
            }
            try {
                sendAudio(ws, bytes);
            } catch (Exception e) {
                open = false;
                Thread.sleep(200); // let the service's error message / close reason arrive
                String reason = failure;
                failure = null;
                throw blockAndFail(reason != null ? reason : uri().getHost() + " connection lost: " + e, e);
            }
            sentSinceFinal += samples.length;
        }
        return drain();
    }

    @Override
    public @NotNull EngineResult finish() throws Exception {
        WebSocket ws = socket;
        EngineResult pending = drainFinal();
        if (pending != null) return pending;
        if (ws == null || !open || sentSinceFinal == 0) return takeLastPartial();

        long before;
        synchronized (finalSignal) {
            before = finalsReceived;
        }
        requestFinal(ws);
        long deadline = System.currentTimeMillis() + FINAL_WAIT_MS;
        synchronized (finalSignal) {
            while (finalsReceived == before && open) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                finalSignal.wait(left);
            }
        }
        pending = drainFinal();
        return pending != null ? pending : takeLastPartial();
    }

    @Override
    public void reset() {
        results.clear();
        lastPartial = "";
    }

    @Override
    public void close() {
        WebSocket ws = socket;
        socket = null;
        open = false;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "").orTimeout(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                ws.abort();
            }
        }
    }

    // ------------------------------------------------------------------ helpers for subclasses

    void partial(String text) {
        results.add(EngineResult.partial(text));
    }

    void complete(String text) {
        results.add(EngineResult.endpoint(text, -1));
        synchronized (finalSignal) {
            finalsReceived++;
            finalSignal.notifyAll();
        }
    }

    void fail(String message) {
        failure = message;
        synchronized (finalSignal) {
            finalSignal.notifyAll();
        }
    }

    static void sendText(WebSocket ws, String text) {
        ws.sendText(text, true).join(); // sends must not overlap
    }

    static void sendBinary(WebSocket ws, byte[] data) {
        ws.sendBinary(ByteBuffer.wrap(data), true).join();
    }

    // ------------------------------------------------------------------ internals

    private WebSocket ensureOpen() throws Exception {
        WebSocket ws = socket;
        if (ws != null && open) return ws;
        long blocked = engine.blockedUntil;
        if (System.currentTimeMillis() < blocked) {
            // the service failed recently: don't hammer it with a reconnect for every audio frame
            throw new IllegalStateException(engine.name() + " is unavailable, retrying in "
                    + (blocked - System.currentTimeMillis()) / 1000 + " s: " + engine.lastError);
        }
        WebSocket.Builder builder = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
        headers().forEach(builder::header);
        open = true;
        try {
            ws = builder.buildAsync(uri(), this).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            open = false;
            throw blockAndFail("Could not connect to " + uri().getHost() + ": " + rootMessage(e), e);
        }
        socket = ws;
        try {
            configure(ws);
        } catch (Exception e) {
            open = false;
            Thread.sleep(300); // let the service's error message / close reason arrive
            String reason = failure;
            failure = null;
            throw blockAndFail(reason != null ? reason : uri().getHost() + " rejected the session: " + e, e);
        }
        return ws;
    }

    private IllegalStateException blockAndFail(String message, Exception cause) {
        engine.lastError = message;
        engine.blockedUntil = System.currentTimeMillis() + RETRY_DELAY_MS;
        return new IllegalStateException(message, cause);
    }

    private EngineResult drain() {
        EngineResult partial = null;
        EngineResult r;
        while ((r = results.poll()) != null) {
            if (r.type() == EngineResult.Type.FINAL) {
                sentSinceFinal = 0;
                lastPartial = "";
                return r; // later results stay queued for the next call
            }
            partial = r;
        }
        if (partial != null) {
            lastPartial = partial.text();
            return partial;
        }
        return EngineResult.NONE;
    }

    private EngineResult drainFinal() {
        EngineResult r;
        while ((r = results.poll()) != null) {
            if (r.type() == EngineResult.Type.FINAL) {
                sentSinceFinal = 0;
                lastPartial = "";
                return r;
            }
            lastPartial = r.text();
        }
        return null;
    }

    private EngineResult takeLastPartial() {
        String text = lastPartial;
        lastPartial = "";
        sentSinceFinal = 0;
        return EngineResult.endpoint(text, -1);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
    }

    // ------------------------------------------------------------------ WebSocket.Listener

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        incoming.append(data);
        if (last) {
            String message = incoming.toString();
            incoming.setLength(0);
            try {
                onMessage(message);
            } catch (Exception e) {
                fail("Could not read a message from " + uri().getHost() + ": " + e);
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        open = false;
        if (statusCode != WebSocket.NORMAL_CLOSURE && statusCode != 1001 && reason != null && !reason.isBlank()) {
            fail(uri().getHost() + " closed the connection: " + statusCode + " " + reason);
        }
        synchronized (finalSignal) {
            finalSignal.notifyAll();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open = false;
        fail(uri().getHost() + " connection error: " + rootMessage(error));
    }
}
