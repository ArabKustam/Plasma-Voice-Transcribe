package org.lavacast.pvtranscribe.voice.plasmo;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;
import org.lavacast.pvtranscribe.core.util.RateLimitedLogger;
import org.lavacast.pvtranscribe.core.voice.Audience;
import org.lavacast.pvtranscribe.core.voice.FrameDecoder;
import org.lavacast.pvtranscribe.core.voice.SpeakerInfo;
import org.lavacast.pvtranscribe.core.voice.VoiceFrame;
import org.lavacast.pvtranscribe.core.voice.VoiceInput;
import org.lavacast.pvtranscribe.core.voice.VoiceSourceAdapter;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.encryption.Encryption;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.PlayerActivationInfo;
import su.plo.voice.api.server.audio.source.BaseServerDirectSource;
import su.plo.voice.api.server.audio.source.ServerAudioSource;
import su.plo.voice.api.server.audio.source.ServerProximitySource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.api.server.socket.UdpConnection;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Plasmo Voice add-on that feeds players' voice into PV-Transcribe.
 * <p>
 * <b>Where the audio is taken from.</b> Every way Plasmo Voice delivers a player's voice - the built-in
 * proximity chat, pv-addon-groups, pv-addon-broadcast and other add-ons - ends with an audio source sending
 * the packet, which fires {@link ServerSourceAudioPacketEvent} with the speaking player attached
 * ({@link PlayerActivationInfo}). Listening there (instead of to the raw microphone packets) gives us both
 * the audio and the exact set of players Plasmo Voice sends it to, i.e. the players who can hear the speaker.
 * Muted players and cancelled packets never reach this event.
 */
@Addon(
        id = "pv-addon-transcribe",
        name = "PV-Transcribe",
        scope = AddonLoaderScope.SERVER,
        version = "1.0.0",
        authors = {"lavacast"}
)
public final class PlasmoVoiceAdapter implements VoiceSourceAdapter, AddonInitializer {

    public static final String ID = "plasmovoice";
    private static final long AUDIENCE_REFRESH_MS = 250;

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private final PlatformLogger logger;
    private final RateLimitedLogger errors;
    private final ConcurrentHashMap<UUID, Long> audienceComputedAt = new ConcurrentHashMap<>();
    private volatile VoiceInput input;
    private volatile boolean initialized;

    public PlasmoVoiceAdapter(@NotNull PlatformLogger logger) {
        this.logger = logger;
        this.errors = new RateLimitedLogger(logger, 60, TimeUnit.SECONDS);
    }

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String displayName() {
        return "Plasmo Voice";
    }

    @Override
    public void start(@NotNull VoiceInput input) {
        this.input = input;
        PlasmoVoiceServer.getAddonsLoader().load(this);
    }

    @Override
    public void stop() {
        initialized = false;
        try {
            PlasmoVoiceServer.getAddonsLoader().unload(this);
        } catch (Throwable t) {
            logger.warn("Failed to unload the Plasmo Voice add-on", t);
        }
    }

    @Override
    public void onAddonInitialize() {
        String version = voiceServer.getVersion();
        if (!isSupportedVersion(version)) {
            logger.error("Plasmo Voice " + version + " is not supported. PV-Transcribe needs Plasmo Voice 2.1.0 or newer; "
                    + "voice transcription is disabled.");
            return;
        }
        initialized = true;
        logger.info("Connected to Plasmo Voice " + version + ".");
    }

    @Override
    public void onAddonShutdown() {
        initialized = false;
        audienceComputedAt.clear();
    }

    // ------------------------------------------------------------------ events

    @EventSubscribe(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSourceAudio(@NotNull ServerSourceAudioPacketEvent event) {
        VoiceInput sink = input;
        if (!initialized || sink == null) return;
        try {
            PlayerActivationInfo activation = event.getActivationInfo();
            if (activation == null) return; // audio not spoken by a player (music bots, sound sources, ...)

            VoicePlayer player = activation.getPlayer();
            UUID playerId = player.getInstance().getUuid();
            PlayerAudioPacket packet = activation.getAudioPacket();
            ServerAudioSource<?> source = event.getSource();
            String channel = source.getLine().getName();

            sink.onAudioFrame(this, new SpeakerInfo(playerId, player.getInstance().getName()),
                    new VoiceFrame(packet.getSequenceNumber(), packet.getData(), packet.isStereo(),
                            voiceServer.getConfig().voice().sampleRate(), channel));

            long now = System.currentTimeMillis();
            Long last = audienceComputedAt.get(source.getId());
            if (last == null || now - last >= AUDIENCE_REFRESH_MS) {
                audienceComputedAt.put(source.getId(), now);
                if (audienceComputedAt.size() > 4096) audienceComputedAt.clear();
                Audience audience = computeAudience(event, source, channel, playerId, now);
                if (audience != null) sink.onAudience(this, playerId, audience);
            }
        } catch (Throwable t) {
            errors.warn("source-audio", "Failed to handle Plasmo Voice audio", t);
        }
    }

    @EventSubscribe(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpeakEnd(@NotNull PlayerSpeakEndEvent event) {
        VoiceInput sink = input;
        if (!initialized || sink == null) return;
        try {
            sink.onSpeakingStopped(this, event.getPlayer().getInstance().getUuid());
        } catch (Throwable t) {
            errors.warn("speak-end", "Failed to handle Plasmo Voice end of speech", t);
        }
    }

    private Audience computeAudience(ServerSourceAudioPacketEvent event, ServerAudioSource<?> source,
                                     String channel, UUID speakerId, long now) {
        Set<UUID> listeners = new HashSet<>();
        try {
            if (event.getResult() == ServerSourceAudioPacketEvent.Result.HANDLED) {
                // another add-on took over delivery of this packet; we don't know to whom
                return null;
            }
            if (source instanceof ServerProximitySource<?> proximity) {
                // same listener set Plasmo Voice uses to deliver this packet (distance + source filters)
                for (UdpConnection connection : proximity.getListeners(event.getDistance())) {
                    listeners.add(connection.getPlayer().getInstance().getUuid());
                }
                listeners.remove(speakerId);
                return new Audience(channel, Audience.Type.PROXIMITY, listeners, event.getDistance(), now);
            }
            if (source instanceof BaseServerDirectSource direct) {
                // groups, broadcast, direct sources: explicit list of receivers
                for (UdpConnection connection : direct.getListeners()) {
                    listeners.add(connection.getPlayer().getInstance().getUuid());
                }
                listeners.remove(speakerId);
                return new Audience(channel, Audience.Type.DIRECT, listeners, -1, now);
            }
        } catch (AbstractMethodError | NoSuchMethodError e) {
            errors.warn("audience-api", "This Plasmo Voice version does not expose source listeners; "
                    + "subtitles fall back to distance-based visibility", null);
        }
        return null;
    }

    // ------------------------------------------------------------------ decoding

    @Override
    public @NotNull FrameDecoder createDecoder(boolean stereo) {
        AudioDecoder decoder = voiceServer.createOpusDecoder(stereo);
        return new PlasmoFrameDecoder(decoder, voiceServer);
    }

    @Override
    public boolean hasVoiceClient(@NotNull UUID playerId) {
        PlasmoVoiceServer server = voiceServer;
        return server != null && initialized && server.getUdpConnectionManager().getConnectionByPlayerId(playerId).isPresent();
    }

    private static final class PlasmoFrameDecoder implements FrameDecoder {
        private final AudioDecoder decoder;
        private final PlasmoVoiceServer voiceServer;

        PlasmoFrameDecoder(AudioDecoder decoder, PlasmoVoiceServer voiceServer) {
            this.decoder = decoder;
            this.voiceServer = voiceServer;
        }

        @Override
        public short @NotNull [] decode(@NotNull VoiceFrame frame) throws Exception {
            if (!decoder.isOpen()) decoder.open();
            byte[] data = frame.data();
            // Plasmo Voice clients always encrypt voice with the server's AES key
            Encryption encryption = voiceServer.getDefaultEncryption();
            if (encryption != null) data = encryption.decrypt(data);
            short[] pcm = decoder.decode(data);
            return frame.stereo() ? FrameDecoder.downmix(pcm) : pcm;
        }

        @Override
        public void reset() {
            if (decoder.isOpen()) decoder.reset();
        }

        @Override
        public void close() {
            decoder.close();
        }
    }

    static boolean isSupportedVersion(String version) {
        if (version == null) return false;
        String[] parts = version.split("[.\\-+]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 2 || (major == 2 && minor >= 1);
        } catch (NumberFormatException e) {
            return true; // unknown format (dev build): try anyway
        }
    }
}
