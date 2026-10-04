package org.lavacast.pvtranscribe.core.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();
    private final int priority;

    public NamedThreadFactory(@NotNull String prefix, int priority) {
        this.prefix = prefix;
        this.priority = priority;
    }

    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(priority);
        return thread;
    }
}
