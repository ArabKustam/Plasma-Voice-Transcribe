package org.lavacast.pvtranscribe.core.voice;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Connects a voice chat (Plasmo Voice, Simple Voice Chat, ...) to PV-Transcribe.
 * <p>
 * An adapter only has to: deliver encoded audio frames of speaking players, say when a player stopped
 * talking, report who can hear the speaker, and decode its own frames to PCM. Everything else
 * (sessions, recognition, subtitles, API) is shared.
 */
public interface VoiceSourceAdapter {

    /**
     * Stable id, used in the API as the voice source, e.g. {@code "plasmovoice"}.
     */
    @NotNull String id();

    @NotNull String displayName();

    /**
     * Starts delivering audio to {@code input}. Called once.
     */
    void start(@NotNull VoiceInput input) throws Exception;

    void stop();

    /**
     * Creates a decoder for frames of one speaker. A decoder is used by one thread at a time
     * and closed by PV-Transcribe when the speaker's session ends.
     */
    @NotNull FrameDecoder createDecoder(boolean stereo) throws Exception;

    /**
     * Whether the player has the voice chat client mod connected (so can hear voice at all).
     */
    boolean hasVoiceClient(@NotNull UUID playerId);
}
