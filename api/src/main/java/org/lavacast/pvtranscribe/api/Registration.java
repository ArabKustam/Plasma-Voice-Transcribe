package org.lavacast.pvtranscribe.api;

/**
 * Handle returned when registering something in the API. Call {@link #unregister()} (or close it)
 * when your plugin disables.
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

    void unregister();

    @Override
    default void close() {
        unregister();
    }
}
