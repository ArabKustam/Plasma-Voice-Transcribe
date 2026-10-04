package org.lavacast.pvtranscribe.engine.vosk;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Vosk: offline, streaming, gives partial results every few tens of milliseconds and detects pauses
 * between phrases. One model is shared by all speakers, each speaker has its own recognizer.
 */
public final class VoskEngine implements SpeechEngine {

    private static final EngineCapabilities CAPABILITIES = new EngineCapabilities(true, false, true);

    private final VoskEngineFactory.VoskSettings settings;
    private final PlatformLogger logger;
    private volatile Model model;

    VoskEngine(VoskEngineFactory.VoskSettings settings, PlatformLogger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public @NotNull String name() {
        return VoskEngineFactory.TYPE;
    }

    @Override
    public @NotNull EngineCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull String language() {
        return settings.language();
    }

    @Override
    public void start() throws Exception {
        // Vosk returns UTF-8, but JNA decodes native strings with the OS encoding (Cp1251 on Russian Windows),
        // which turns Russian text into garbage. Must be set before JNA's Native class initializes.
        if (System.getProperty("jna.encoding") == null) {
            System.setProperty("jna.encoding", "UTF-8");
        }
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new IllegalStateException("Vosk native library could not be loaded on this system ("
                    + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "). "
                    + "Bundled natives exist for Windows x64, Linux x64 and macOS. "
                    + "On other platforms put libvosk into a folder and start the server with -Djna.library.path=<folder>.", e);
        }

        NativeText.init(logger);
        Path modelDir = new ModelDownloader(settings, logger).ensureModel();
        long start = System.nanoTime();
        logger.info("Loading Vosk model " + modelDir.getFileName() + "...");
        model = new Model(modelDir.toAbsolutePath().toString());
        logger.info("Vosk model loaded in " + (System.nanoTime() - start) / 1_000_000 + " ms.");
    }

    @Override
    public @NotNull SpeechStream createStream(int sampleRate) throws Exception {
        Model current = model;
        if (current == null) throw new IllegalStateException("Vosk engine is not started");
        Recognizer recognizer = new Recognizer(current, sampleRate);
        recognizer.setMaxAlternatives(0);
        recognizer.setWords(false);
        recognizer.setPartialWords(false);
        return new VoskStream(recognizer);
    }

    @Override
    public void close() {
        Model current = model;
        model = null;
        if (current != null) {
            // Vosk models are reference counted: recognizers that are still open keep working.
            current.close();
        }
    }

    static boolean isModelDirectory(Path dir) {
        return Files.isDirectory(dir.resolve("am")) || Files.isRegularFile(dir.resolve("conf").resolve("model.conf"))
                || Files.isDirectory(dir.resolve("graph"));
    }

    private static final class VoskStream implements SpeechStream {
        private final Recognizer recognizer;

        VoskStream(Recognizer recognizer) {
            this.recognizer = recognizer;
        }

        @Override
        public @NotNull EngineResult accept(short @NotNull [] pcm) {
            if (recognizer.acceptWaveForm(pcm, pcm.length)) {
                // Vosk detected a pause: the phrase is complete, the recognizer continues with a new one
                return EngineResult.endpoint(VoskJson.string(NativeText.utf8(recognizer.getResult()), "text"), -1);
            }
            return EngineResult.partial(VoskJson.string(NativeText.utf8(recognizer.getPartialResult()), "partial"));
        }

        @Override
        public @NotNull EngineResult finish() {
            return EngineResult.endpoint(VoskJson.string(NativeText.utf8(recognizer.getFinalResult()), "text"), -1);
        }

        @Override
        public void reset() {
            recognizer.reset();
        }

        @Override
        public void close() {
            recognizer.close();
        }
    }
}
