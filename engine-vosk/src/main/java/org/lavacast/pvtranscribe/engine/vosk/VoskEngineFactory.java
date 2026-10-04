package org.lavacast.pvtranscribe.engine.vosk;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.config.ConfigSection;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config section {@code engine.vosk}:
 * <pre>
 * models-folder: models
 * auto-download: true
 * download-url: "https://alphacephei.com/vosk/models/{model}.zip"
 * models:
 *   ru: vosk-model-small-ru-0.22
 *   en: vosk-model-small-en-us-0.15
 * </pre>
 */
public final class VoskEngineFactory implements SpeechEngineFactory {

    public static final String TYPE = "vosk";

    /** Small (~40-50 MB) models that run in real time on an ordinary server CPU. */
    private static final Map<String, String> DEFAULT_MODELS = new LinkedHashMap<>();

    static {
        DEFAULT_MODELS.put("ru", "vosk-model-small-ru-0.22");
        DEFAULT_MODELS.put("en", "vosk-model-small-en-us-0.15");
        DEFAULT_MODELS.put("uk", "vosk-model-small-uk-v3-small");
        DEFAULT_MODELS.put("de", "vosk-model-small-de-0.15");
        DEFAULT_MODELS.put("fr", "vosk-model-small-fr-0.22");
        DEFAULT_MODELS.put("es", "vosk-model-small-es-0.42");
        DEFAULT_MODELS.put("pl", "vosk-model-small-pl-0.22");
        DEFAULT_MODELS.put("kz", "vosk-model-small-kz-0.15");
    }

    @Override
    public @NotNull SpeechEngine create(@NotNull Context context, @NotNull ConfigSection settings) {
        Map<String, String> models = new LinkedHashMap<>(DEFAULT_MODELS);
        models.putAll(settings.getStringMap("models"));

        String language = context.language();
        String model = models.get(language);
        if (model == null) {
            throw new IllegalArgumentException("No Vosk model configured for language '" + language
                    + "'. Add it under engine.vosk.models (see https://alphacephei.com/vosk/models).");
        }

        Path folder = context.dataFolder().resolve(settings.getString("models-folder", "models"));
        VoskSettings voskSettings = new VoskSettings(
                language,
                model,
                folder,
                settings.getBoolean("auto-download", true),
                settings.getString("download-url", "https://alphacephei.com/vosk/models/{model}.zip")
        );
        return new VoskEngine(voskSettings, context.logger());
    }

    record VoskSettings(String language, String model, Path modelsFolder, boolean autoDownload, String downloadUrl) {
    }
}
