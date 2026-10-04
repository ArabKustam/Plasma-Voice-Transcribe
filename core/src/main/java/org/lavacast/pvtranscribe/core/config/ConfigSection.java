package org.lavacast.pvtranscribe.core.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of a configuration tree, so the core and engines can read settings without
 * depending on Bukkit's YAML API or any other config library.
 */
public interface ConfigSection {

    @Nullable Object get(@NotNull String path);

    @Nullable ConfigSection getSection(@NotNull String path);

    @NotNull Set<String> getKeys();

    default @NotNull String getString(@NotNull String path, @NotNull String def) {
        Object value = get(path);
        return value == null ? def : String.valueOf(value);
    }

    default boolean getBoolean(@NotNull String path, boolean def) {
        Object value = get(path);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    default int getInt(@NotNull String path, int def) {
        Object value = get(path);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    default long getLong(@NotNull String path, long def) {
        Object value = get(path);
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    default double getDouble(@NotNull String path, double def) {
        Object value = get(path);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    default @NotNull List<String> getStringList(@NotNull String path) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            return list.stream().filter(o -> o != null).map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Section as a flat {@code key -> string} map (first level only).
     */
    default @NotNull Map<String, String> getStringMap(@NotNull String path) {
        ConfigSection section = getSection(path);
        if (section == null) return Map.of();
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys()) {
            Object value = section.get(key);
            if (value != null && !(value instanceof ConfigSection) && !(value instanceof Map)) {
                map.put(key, String.valueOf(value));
            }
        }
        return map;
    }
}
