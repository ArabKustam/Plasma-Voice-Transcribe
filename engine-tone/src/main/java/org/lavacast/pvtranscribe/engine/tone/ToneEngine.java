package org.lavacast.pvtranscribe.engine.tone;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * T-one acoustic model run with ONNX Runtime. One session is shared by all speakers (ONNX Runtime
 * sessions are thread-safe); every speaker has its own streaming state.
 */
public final class ToneEngine implements SpeechEngine {

    private static final EngineCapabilities CAPABILITIES = new EngineCapabilities(true, false, true);

    private final ToneEngineFactory.Settings settings;
    private final PlatformLogger logger;
    private volatile OrtEnvironment environment;
    private volatile OrtSession session;

    ToneEngine(ToneEngineFactory.Settings settings, PlatformLogger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public @NotNull String name() {
        return ToneEngineFactory.TYPE;
    }

    @Override
    public @NotNull EngineCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull String language() {
        return "ru";
    }

    @Override
    public void start() throws Exception {
        Path model = settings.modelFile();
        if (!Files.isRegularFile(model)) {
            if (!settings.autoDownload()) {
                throw new IllegalStateException("T-one model not found: " + model.toAbsolutePath()
                        + ". Download model.onnx from https://huggingface.co/t-tech/T-one or enable engine.t-one.auto-download.");
            }
            download(settings.downloadUrl(), model);
        }

        long start = System.nanoTime();
        logger.info("Loading T-one model...");
        OrtEnvironment env;
        try {
            env = OrtEnvironment.getEnvironment();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new IllegalStateException("ONNX Runtime could not be loaded on this system ("
                    + System.getProperty("os.name") + " " + System.getProperty("os.arch") + ").", e);
        }
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        // parallelism comes from several speakers being processed at once, not from one inference
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        environment = env;
        session = env.createSession(model.toAbsolutePath().toString(), options);
        logger.info("T-one model loaded in " + (System.nanoTime() - start) / 1_000_000 + " ms.");
    }

    @Override
    public @NotNull SpeechStream createStream(int sampleRate) {
        OrtSession current = session;
        if (current == null) throw new IllegalStateException("T-one engine is not started");
        return new ToneStream(environment, current, sampleRate, settings.silenceFrames());
    }

    @Override
    public void close() {
        OrtSession current = session;
        session = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                logger.warn("Failed to close the T-one model", e);
            }
        }
    }

    private void download(String url, Path target) throws Exception {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        logger.info("T-one model not found, downloading it from " + url + " (~140 MB) ...");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<InputStream> response = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Model download failed: HTTP " + response.statusCode() + " for " + url);
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int lastPercent = -1;
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent / 20 != lastPercent / 20) {
                        lastPercent = percent;
                        logger.info("Downloading T-one model: " + percent + "% (" + done / (1024 * 1024) + " MB)");
                    }
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(part);
            throw e;
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("T-one model saved to " + target.toAbsolutePath());
    }
}
