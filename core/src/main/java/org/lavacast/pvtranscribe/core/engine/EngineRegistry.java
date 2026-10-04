package org.lavacast.pvtranscribe.core.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine types available for {@code engine.type}.
 */
public final class EngineRegistry {

    private final Map<String, SpeechEngineFactory> factories = new ConcurrentHashMap<>();

    public void register(@NotNull String type, @NotNull SpeechEngineFactory factory) {
        factories.put(type.toLowerCase(Locale.ROOT), factory);
    }

    public @Nullable SpeechEngineFactory get(@NotNull String type) {
        return factories.get(type.toLowerCase(Locale.ROOT));
    }

    public @NotNull Set<String> types() {
        return Set.copyOf(factories.keySet());
    }
}
