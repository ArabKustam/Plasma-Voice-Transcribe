package org.lavacast.pvtranscribe.engine.tone;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.config.ConfigSection;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;

import java.nio.file.Path;

/**
 * Config section {@code engine.t-one}:
 * <pre>
 * model-file: models/t-one/model.onnx
 * auto-download: true
 * download-url: "https://huggingface.co/t-tech/T-one/resolve/main/model.onnx"
 * silence-ms: 600
 * </pre>
 */
public final class ToneEngineFactory implements SpeechEngineFactory {

    public static final String TYPE = "t-one";

    @Override
    public @NotNull SpeechEngine create(@NotNull Context context, @NotNull ConfigSection settings) {
        if (!context.language().equals("ru")) {
            throw new IllegalArgumentException("T-one recognises only Russian, but transcription.language is '"
                    + context.language() + "'. Use engine.type: vosk for other languages.");
        }
        Path model = context.dataFolder().resolve(settings.getString("model-file", "models/t-one/model.onnx"));
        int silenceMs = Math.max(150, Math.min(5_000, settings.getInt("silence-ms", 600)));
        return new ToneEngine(new Settings(
                model,
                settings.getBoolean("auto-download", true),
                settings.getString("download-url", "https://huggingface.co/t-tech/T-one/resolve/main/model.onnx"),
                silenceMs / ToneStream.FRAME_MS
        ), context.logger());
    }

    record Settings(Path modelFile, boolean autoDownload, String downloadUrl, int silenceFrames) {
    }
}
