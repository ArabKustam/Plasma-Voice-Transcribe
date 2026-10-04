package org.lavacast.pvtranscribe.api;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Static entry point to the PV-Transcribe API.
 *
 * <pre>{@code
 * PVTranscribeApi api = PVTranscribe.get();
 * api.addListener(new TranscriptionListener() {
 *     public void onFinalTranscript(Transcript transcript) {
 *         System.out.println(transcript.getSpeakerName() + ": " + transcript.getText());
 *     }
 * });
 * }</pre>
 *
 * On Bukkit the same instance is also registered in the {@code ServicesManager}.
 */
public final class PVTranscribe {

    private static volatile PVTranscribeApi instance;

    private PVTranscribe() {
    }

    /**
     * @return the running API instance
     * @throws IllegalStateException if PV-Transcribe is not loaded yet
     */
    public static @NotNull PVTranscribeApi get() {
        PVTranscribeApi api = instance;
        if (api == null) {
            throw new IllegalStateException("PV-Transcribe is not loaded yet");
        }
        return api;
    }

    public static @NotNull Optional<PVTranscribeApi> getIfLoaded() {
        return Optional.ofNullable(instance);
    }

    /**
     * Internal: called by the PV-Transcribe platform implementation.
     */
    public static void register(@NotNull PVTranscribeApi api) {
        instance = api;
    }

    /**
     * Internal: called by the PV-Transcribe platform implementation on shutdown.
     */
    public static void unregister(@NotNull PVTranscribeApi api) {
        if (instance == api) {
            instance = null;
        }
    }
}
