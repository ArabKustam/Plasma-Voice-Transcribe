package org.lavacast.pvtranscribe.paper.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.event.SpeechStartEvent;

/**
 * A player started talking in voice chat. Fired on the main thread.
 */
public final class PlayerSpeechStartEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final SpeechStartEvent speech;

    public PlayerSpeechStartEvent(@NotNull Player player, @NotNull SpeechStartEvent speech) {
        super(player);
        this.speech = speech;
    }

    public @NotNull SpeechStartEvent getSpeech() {
        return speech;
    }

    public @NotNull String getVoiceChannel() {
        return speech.voiceChannel();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
