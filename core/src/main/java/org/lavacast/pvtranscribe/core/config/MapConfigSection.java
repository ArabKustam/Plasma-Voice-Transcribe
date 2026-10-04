package org.lavacast.pvtranscribe.core.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link ConfigSection} over nested maps (as produced by SnakeYAML, Gson, ...). Paths use dots.
 */
public final class MapConfigSection implements ConfigSection {

    private final Map<String, Object> values;

    public MapConfigSection(@NotNull Map<?, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((k, v) -> copy.put(String.valueOf(k), v));
        this.values = Collections.unmodifiableMap(copy);
    }

    public static MapConfigSection empty() {
        return new MapConfigSection(Map.of());
    }

    @Override
    public @Nullable Object get(@NotNull String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            Object value = values.get(path);
            return value instanceof Map<?, ?> map ? new MapConfigSection(map) : value;
        }
        ConfigSection child = getSection(path.substring(0, dot));
        return child == null ? null : child.get(path.substring(dot + 1));
    }

    @Override
    public @Nullable ConfigSection getSection(@NotNull String path) {
        int dot = path.indexOf('.');
        String head = dot < 0 ? path : path.substring(0, dot);
        Object value = values.get(head);
        ConfigSection section = null;
        if (value instanceof Map<?, ?> map) section = new MapConfigSection(map);
        else if (value instanceof ConfigSection cs) section = cs;
        if (section == null || dot < 0) return section;
        return section.getSection(path.substring(dot + 1));
    }

    @Override
    public @NotNull Set<String> getKeys() {
        return values.keySet();
    }
}
