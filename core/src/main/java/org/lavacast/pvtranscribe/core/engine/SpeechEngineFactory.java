package org.lavacast.pvtranscribe.core.engine;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.config.ConfigSection;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.nio.file.Path;

@FunctionalInterface
public interface SpeechEngineFactory {

    /**
     * Creates (but does not start) an engine.
     *
     * @param settings the {@code engine.<type>} config section
     */
    @NotNull SpeechEngine create(@NotNull Context context, @NotNull ConfigSection settings) throws Exception;

    /**
     * @param dataFolder      plugin/mod data folder (models etc. are stored relative to it)
     * @param language        configured language, e.g. "ru"
     * @param autoDetect      whether automatic language detection was requested
     * @param vocabulary      words the engine should expect, e.g. names of online players (may change over time)
     */
    record Context(@NotNull Path dataFolder, @NotNull String language, boolean autoDetect, @NotNull PlatformLogger logger,
                   @NotNull java.util.function.Supplier<java.util.Collection<String>> vocabulary) {

        public Context(@NotNull Path dataFolder, @NotNull String language, boolean autoDetect, @NotNull PlatformLogger logger) {
            this(dataFolder, language, autoDetect, logger, java.util.List::of);
        }
    }
}
