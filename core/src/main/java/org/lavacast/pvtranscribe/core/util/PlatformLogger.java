package org.lavacast.pvtranscribe.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal logger implemented by each platform (Bukkit logger, SLF4J on Fabric, ...).
 */
public interface PlatformLogger {

    void info(@NotNull String message);

    void warn(@NotNull String message, @Nullable Throwable error);

    void error(@NotNull String message, @Nullable Throwable error);

    default void warn(@NotNull String message) {
        warn(message, null);
    }

    default void error(@NotNull String message) {
        error(message, null);
    }

    default void debug(@NotNull String message) {
    }
}
