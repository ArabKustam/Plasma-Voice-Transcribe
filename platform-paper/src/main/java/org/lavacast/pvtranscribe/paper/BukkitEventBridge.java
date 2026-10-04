package org.lavacast.pvtranscribe.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.api.event.SpeechEndEvent;
import org.lavacast.pvtranscribe.api.event.SpeechStartEvent;
import org.lavacast.pvtranscribe.api.event.TranscriptionListener;
import org.lavacast.pvtranscribe.paper.event.PlayerSpeechEndEvent;
import org.lavacast.pvtranscribe.paper.event.PlayerSpeechStartEvent;
import org.lavacast.pvtranscribe.paper.event.PlayerTranscriptEvent;

import java.util.UUID;
import java.util.function.Function;

/**
 * Re-fires API events as regular Bukkit events. Registered with the main-thread executor,
 * so these Bukkit events are always fired synchronously.
 */
final class BukkitEventBridge implements TranscriptionListener {

    @Override
    public void onSpeechStart(@NotNull SpeechStartEvent event) {
        call(event.speakerId(), player -> new PlayerSpeechStartEvent(player, event));
    }

    @Override
    public void onTranscript(@NotNull Transcript transcript) {
        call(transcript.getSpeakerId(), player -> new PlayerTranscriptEvent(player, transcript));
    }

    @Override
    public void onSpeechEnd(@NotNull SpeechEndEvent event) {
        call(event.speakerId(), player -> new PlayerSpeechEndEvent(player, event));
    }

    private static void call(UUID playerId, Function<Player, Event> factory) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Bukkit.getPluginManager().callEvent(factory.apply(player));
        }
    }
}
