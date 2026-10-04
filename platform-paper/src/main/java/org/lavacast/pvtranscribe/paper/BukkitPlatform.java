package org.lavacast.pvtranscribe.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lavacast.pvtranscribe.core.Platform;
import org.lavacast.pvtranscribe.core.config.TranscribeConfig;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bukkit side of the core {@link Platform}. Permission and world checks are evaluated on the main
 * thread and cached, because the core asks from network and worker threads.
 */
final class BukkitPlatform implements Platform {

    static final String PERM_SEE = "pvtranscribe.see";
    static final String PERM_TRANSCRIBE = "pvtranscribe.transcribe";

    private final PVTranscribePlugin plugin;
    private final PlayerData playerData;
    private final PlatformLogger logger;
    private final Executor syncExecutor;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private volatile java.util.List<String> onlineNames = java.util.List.of();

    BukkitPlatform(PVTranscribePlugin plugin, PlayerData playerData) {
        this.plugin = plugin;
        this.playerData = playerData;
        this.logger = new JulLogger(plugin.getLogger());
        this.syncExecutor = task -> {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        };
    }

    // ------------------------------------------------------------- cache (main thread)

    void refreshAll() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
            names.add(player.getName());
        }
        onlineNames = java.util.List.copyOf(names);
    }

    void refresh(Player player) {
        TranscribeConfig config = plugin.config();
        UUID id = player.getUniqueId();
        boolean transcribe = config.enabled()
                && player.hasPermission(PERM_TRANSCRIBE)
                && !config.transcription().disabledWorlds().contains(player.getWorld().getName())
                && playerData.isTranscriptionEnabled(id);
        boolean see = player.hasPermission(PERM_SEE) && !playerData.isSubtitlesHidden(id);
        states.put(id, new PlayerState(transcribe, see));
    }

    void forget(UUID id) {
        states.remove(id);
    }

    /**
     * Thread-safe: may this player see subtitles.
     */
    boolean canSee(UUID id) {
        PlayerState state = states.get(id);
        return state != null && state.see();
    }

    // ------------------------------------------------------------- Platform

    @Override
    public @NotNull PlatformLogger logger() {
        return logger;
    }

    @Override
    public @NotNull Executor syncExecutor() {
        return syncExecutor;
    }

    @Override
    public @NotNull Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public @NotNull String version() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean canTranscribe(@NotNull UUID playerId) {
        PlayerState state = states.get(playerId);
        return state != null && state.transcribe();
    }

    @Override
    public void setTranscriptionEnabled(@NotNull UUID playerId, boolean enabled) {
        playerData.setTranscriptionEnabled(playerId, enabled);
        syncExecutor.execute(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) refresh(player);
        });
    }

    @Override
    public @NotNull java.util.Collection<String> onlinePlayerNames() {
        return onlineNames;
    }

    @Override
    public boolean isTranscriptionEnabled(@NotNull UUID playerId) {
        return playerData.isTranscriptionEnabled(playerId);
    }

    private record PlayerState(boolean transcribe, boolean see) {
    }

    private static final class JulLogger implements PlatformLogger {
        private final Logger logger;

        JulLogger(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void info(@NotNull String message) {
            logger.info(message);
        }

        @Override
        public void warn(@NotNull String message, @Nullable Throwable error) {
            logger.log(Level.WARNING, message, error);
        }

        @Override
        public void error(@NotNull String message, @Nullable Throwable error) {
            logger.log(Level.SEVERE, message, error);
        }
    }
}
