package org.lavacast.pvtranscribe.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs repeated problems once per time window instead of flooding the console.
 * The first occurrence of a key is logged with its stack trace, later ones only as a summary
 * ("... repeated 128 times in the last 60s").
 */
public final class RateLimitedLogger {

    private final PlatformLogger logger;
    private final long windowNanos;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public RateLimitedLogger(@NotNull PlatformLogger logger, long window, @NotNull TimeUnit unit) {
        this.logger = logger;
        this.windowNanos = unit.toNanos(window);
    }

    public void warn(@NotNull String key, @NotNull String message, @Nullable Throwable error) {
        log(key, message, error, false);
    }

    public void error(@NotNull String key, @NotNull String message, @Nullable Throwable error) {
        log(key, message, error, true);
    }

    /**
     * Forget a key, so the next occurrence is logged in full again (e.g. after the problem was fixed).
     */
    public void reset(@NotNull String key) {
        states.remove(key);
    }

    private void log(String key, String message, Throwable error, boolean isError) {
        long now = System.nanoTime();
        State state = states.computeIfAbsent(key, k -> new State());
        int suppressed;
        boolean first;
        synchronized (state) {
            if (state.lastLogged != 0 && now - state.lastLogged < windowNanos) {
                state.suppressed.incrementAndGet();
                return;
            }
            first = state.lastLogged == 0;
            state.lastLogged = now;
            suppressed = state.suppressed.getAndSet(0);
        }

        String text = message;
        if (suppressed > 0) {
            text += " (repeated " + suppressed + " more times in the last "
                    + TimeUnit.NANOSECONDS.toSeconds(windowNanos) + "s)";
        }
        Throwable shown = first ? error : null;
        if (!first && error != null) {
            text += ": " + error;
        }
        if (isError) {
            logger.error(text, shown);
        } else {
            logger.warn(text, shown);
        }
    }

    private static final class State {
        long lastLogged;
        final AtomicInteger suppressed = new AtomicInteger();
    }
}
