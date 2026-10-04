package org.lavacast.pvtranscribe.api;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.event.TranscriptionListener;
import org.lavacast.pvtranscribe.api.phrase.PhraseRegistry;
import org.lavacast.pvtranscribe.api.subtitle.SubtitleProcessor;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * The PV-Transcribe API.
 * <p>
 * Everything here is independent of the voice chat (Plasmo Voice, Simple Voice Chat, ...)
 * and of the speech-to-text engine in use.
 * <p>
 * Threading: listener and phrase callbacks run on PV-Transcribe worker threads unless an executor
 * is given (see {@link #syncExecutor()} to run on the server main thread). Never block inside them.
 */
public interface PVTranscribeApi {

    @NotNull String getVersion();

    /**
     * Subscribes to speech events of all players.
     *
     * @return handle to unsubscribe
     */
    @NotNull Registration addListener(@NotNull TranscriptionListener listener);

    /**
     * Subscribes to speech events, delivering them through the given executor
     * (for example {@link #syncExecutor()}).
     */
    @NotNull Registration addListener(@NotNull TranscriptionListener listener, @NotNull Executor executor);

    /**
     * Registry of key phrases ("fireball", "open the gate", ...) that other plugins react to.
     */
    @NotNull PhraseRegistry phrases();

    /**
     * Adds a processor that may change or hide subtitle text before it is shown above a player's head
     * (censoring, translation, ...). It does not affect transcripts delivered to listeners.
     */
    @NotNull Registration addSubtitleProcessor(@NotNull SubtitleProcessor processor);

    /**
     * @return the speech session of a player who is currently speaking
     */
    @NotNull Optional<SpeechSession> getActiveSession(@NotNull UUID playerId);

    @NotNull Collection<SpeechSession> getActiveSessions();

    default boolean isSpeaking(@NotNull UUID playerId) {
        return getActiveSession(playerId).isPresent();
    }

    /**
     * Whether an administrator has allowed transcription for this player
     * (does not check permissions or disabled worlds).
     */
    boolean isTranscriptionEnabled(@NotNull UUID playerId);

    /**
     * Enables or disables transcription for a player. Persisted by the platform.
     */
    void setTranscriptionEnabled(@NotNull UUID playerId, boolean enabled);

    /**
     * @return information about the speech-to-text engine currently in use
     */
    @NotNull EngineInfo getEngineInfo();

    /**
     * @return ids of connected voice chats, for example {@code "plasmovoice"}
     */
    @NotNull Set<String> getVoiceSources();

    /**
     * Executor that runs tasks on the server main thread.
     */
    @NotNull Executor syncExecutor();
}
