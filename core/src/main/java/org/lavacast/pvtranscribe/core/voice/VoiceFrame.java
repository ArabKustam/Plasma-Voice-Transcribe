package org.lavacast.pvtranscribe.core.voice;

import org.jetbrains.annotations.NotNull;

/**
 * One encoded audio frame (usually 20 ms of Opus) as received from the voice chat.
 * Decoding happens later on a PV-Transcribe worker thread, never on the voice chat's network thread.
 *
 * @param sequence   sequence number from the voice chat, used to drop duplicates
 * @param data       encoded (and possibly encrypted) payload, owned by this frame
 * @param stereo     whether the payload is stereo
 * @param sampleRate sample rate of decoded PCM
 * @param channel    voice channel name, e.g. "proximity", "groups", "broadcast"
 */
public record VoiceFrame(long sequence, byte @NotNull [] data, boolean stereo, int sampleRate, @NotNull String channel) {
}
