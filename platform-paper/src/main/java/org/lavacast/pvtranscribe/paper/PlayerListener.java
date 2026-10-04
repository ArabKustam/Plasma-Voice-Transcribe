package org.lavacast.pvtranscribe.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.lavacast.pvtranscribe.core.session.TranscriptionService;

import java.util.UUID;

/**
 * Keeps the permission cache current and removes subtitles whenever they would be stale:
 * quit, death, respawn, world change.
 */
final class PlayerListener implements Listener {

    private final TranscriptionService service;
    private final BukkitPlatform platform;
    private final SubtitleRenderer renderer;

    PlayerListener(TranscriptionService service, BukkitPlatform platform, SubtitleRenderer renderer) {
        this.service = service;
        this.platform = platform;
        this.renderer = renderer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        platform.refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        service.onPlayerQuit(id);
        renderer.remove(id);
        renderer.forgetViewer(event.getPlayer());
        platform.forget(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        platform.refresh(player);
        clear(player);
        renderer.forgetViewer(player);
    }

    private void clear(Player player) {
        service.subtitles().remove(player.getUniqueId());
        renderer.remove(player.getUniqueId());
    }
}
