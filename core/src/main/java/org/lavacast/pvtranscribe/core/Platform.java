package org.lavacast.pvtranscribe.core;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * What the core needs from a server platform (Paper, Fabric, ...).
 */
public interface Platform {

    @NotNull PlatformLogger logger();

    /**
     * Runs tasks on the server main thread.
     */
    @NotNull Executor syncExecutor();

    @NotNull Path dataFolder();

    @NotNull String version();

    /**
     * Whether speech of this player may be transcribed right now: online, has permission, world not
     * disabled, not disabled by an admin. Called from worker and network threads, so implementations must
     * answer from a thread-safe cache instead of touching the game state.
     */
    boolean canTranscribe(@NotNull UUID playerId);

    /**
     * Persists the admin per-player transcription toggle.
     */
    void setTranscriptionEnabled(@NotNull UUID playerId, boolean enabled);

    boolean isTranscriptionEnabled(@NotNull UUID playerId);

    /**
     * Names of online players (thread-safe snapshot), used to recognise nicknames in speech.
     */
    default @NotNull java.util.Collection<String> onlinePlayerNames() {
        return java.util.List.of();
    }
}
