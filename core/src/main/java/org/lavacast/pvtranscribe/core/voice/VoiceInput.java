package org.lavacast.pvtranscribe.core.voice;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Sink that voice adapters push data into. All methods are cheap and non-blocking, so they can be
 * called from network threads of the voice chat.
 */
public interface VoiceInput {

    void onAudioFrame(@NotNull VoiceSourceAdapter source, @NotNull SpeakerInfo speaker, @NotNull VoiceFrame frame);

    void onSpeakingStopped(@NotNull VoiceSourceAdapter source, @NotNull UUID speakerId);

    /**
     * Reports who currently hears the speaker through one voice source/channel.
     */
    void onAudience(@NotNull VoiceSourceAdapter source, @NotNull UUID speakerId, @NotNull Audience audience);
}
