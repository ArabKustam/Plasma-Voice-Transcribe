package org.lavacast.pvtranscribe.paper.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Transcript;

/**
 * Recognised text of a player's phrase, fired on the main thread for every partial result and once for
 * the final result. Check {@link #isFinal()}.
 *
 * <pre>{@code
 * @EventHandler
 * public void onSpeech(PlayerTranscriptEvent event) {
 *     if (event.isFinal() && event.getTranscript().containsPhrase("огненный шар")) {
 *         event.getPlayer().launchProjectile(Fireball.class);
 *     }
 * }
 * }</pre>
 */
public final class PlayerTranscriptEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Transcript transcript;

    public PlayerTranscriptEvent(@NotNull Player player, @NotNull Transcript transcript) {
        super(player);
        this.transcript = transcript;
    }

    public @NotNull Transcript getTranscript() {
        return transcript;
    }

    public @NotNull String getText() {
        return transcript.getText();
    }

    public boolean isFinal() {
        return transcript.isFinal();
    }

    public boolean isPartial() {
        return transcript.isPartial();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
