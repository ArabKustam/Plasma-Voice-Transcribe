package org.lavacast.pvtranscribe.paper.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.event.SpeechEndEvent;

/**
 * A player stopped talking. Fired on the main thread (not fired if the player already left).
 */
public final class PlayerSpeechEndEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final SpeechEndEvent speech;

    public PlayerSpeechEndEvent(@NotNull Player player, @NotNull SpeechEndEvent speech) {
        super(player);
        this.speech = speech;
    }

    public @NotNull SpeechEndEvent getSpeech() {
        return speech;
    }

    public @NotNull SpeechEndEvent.Reason getReason() {
        return speech.reason();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
