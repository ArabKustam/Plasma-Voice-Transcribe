package org.lavacast.pvtranscribe.core.session;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.EngineInfo;
import org.lavacast.pvtranscribe.api.PVTranscribeApi;
import org.lavacast.pvtranscribe.api.Registration;
import org.lavacast.pvtranscribe.api.SpeechSession;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.api.event.SpeechEndEvent;
import org.lavacast.pvtranscribe.api.event.SpeechStartEvent;
import org.lavacast.pvtranscribe.api.event.TranscriptionListener;
import org.lavacast.pvtranscribe.api.phrase.PhraseRegistry;
import org.lavacast.pvtranscribe.api.subtitle.SubtitleProcessor;
import org.lavacast.pvtranscribe.core.Platform;
import org.lavacast.pvtranscribe.core.config.TranscribeConfig;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.EngineRegistry;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechEngineFactory;
import org.lavacast.pvtranscribe.core.phrase.PhraseRegistryImpl;
import org.lavacast.pvtranscribe.core.subtitle.SubtitleManager;
import org.lavacast.pvtranscribe.core.util.NamedThreadFactory;
import org.lavacast.pvtranscribe.core.util.RateLimitedLogger;
import org.lavacast.pvtranscribe.core.util.TextUtil;
import org.lavacast.pvtranscribe.core.voice.Audience;
import org.lavacast.pvtranscribe.core.voice.SpeakerInfo;
import org.lavacast.pvtranscribe.core.voice.VoiceFrame;
import org.lavacast.pvtranscribe.core.voice.VoiceInput;
import org.lavacast.pvtranscribe.core.voice.VoiceSourceAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The single transcription pipeline shared by every voice chat and platform:
 * <pre>
 * voice adapters -> VoiceInput -> per-speaker sessions (worker pool) -> speech engine
 *                -> transcripts -> API listeners / phrase triggers / subtitle state
 * </pre>
 * Audio is only kept in memory while it is queued for recognition; nothing is written to disk.
 */
public final class TranscriptionService implements PVTranscribeApi, VoiceInput {

    private static final long REAPER_PERIOD_MS = 100;
    /** After the voice chat reports the end of speech, wait this long for late UDP frames. */
    private static final long STOP_GRACE_MS = 150;
    private static final int FRAME_MS = 20;

    private final Platform platform;
    private final EngineRegistry engines;
    private final RateLimitedLogger errors;
    private final SubtitleManager subtitles;
    private final PhraseRegistryImpl phrases;
    private final Stats stats = new Stats();

    private final CopyOnWriteArrayList<ListenerEntry> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SubtitleProcessor> processors = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VoiceSourceAdapter> adapters = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<UUID, SpeakerSession> sessions = new ConcurrentHashMap<>();

    private final AtomicLong sessionIds = new AtomicLong();
    private final AtomicLong utteranceIds = new AtomicLong();
    private final AtomicLong engineGenerations = new AtomicLong();
    private final AtomicInteger activeSpeakers = new AtomicInteger();

    private volatile TranscribeConfig config;
    private volatile BiConsumer<Transcript, String> finalPhraseHandler;
    private volatile EngineHolder engine = EngineHolder.stopped("none", 0);
    private volatile ExecutorService workers;
    private ScheduledExecutorService scheduler;
    private int workerThreads;

    public TranscriptionService(@NotNull Platform platform, @NotNull EngineRegistry engines, @NotNull TranscribeConfig config) {
        this.platform = platform;
        this.engines = engines;
        this.config = config;
        this.errors = new RateLimitedLogger(platform.logger(), 60, TimeUnit.SECONDS);
        this.subtitles = new SubtitleManager(config);
        this.phrases = new PhraseRegistryImpl(errors);
    }

    // ================================================================ lifecycle

    public synchronized void start() {
        workerThreads = config.transcription().workerThreads();
        workers = newWorkerPool(workerThreads);
        scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("PV-Transcribe Scheduler", Thread.NORM_PRIORITY));
        scheduler.scheduleWithFixedDelay(this::reap, REAPER_PERIOD_MS, REAPER_PERIOD_MS, TimeUnit.MILLISECONDS);
        if (config.enabled()) loadEngine(config);
    }

    /**
     * Applies a new configuration. The engine is reloaded in the background only if its settings
     * changed; until the new engine is ready the old one keeps working.
     */
    public synchronized void reload(@NotNull TranscribeConfig newConfig) {
        TranscribeConfig old = this.config;
        this.config = newConfig;
        subtitles.applyConfig(newConfig);

        if (newConfig.transcription().workerThreads() != workerThreads) {
            ExecutorService oldPool = workers;
            workerThreads = newConfig.transcription().workerThreads();
            workers = newWorkerPool(workerThreads);
            oldPool.shutdown();
        }

        if (!newConfig.enabled()) {
            sessions.values().forEach(s -> s.submit(() -> s.endSpeaking(SpeechEndEvent.Reason.DISABLED)));
            subtitles.clearAll();
            return;
        }

        boolean engineChanged = !newConfig.engineType().equals(old.engineType())
                || !newConfig.transcription().language().equals(old.transcription().language())
                || newConfig.transcription().autoDetectLanguage() != old.transcription().autoDetectLanguage()
                || !sameSection(newConfig, old)
                || engine.state() == EngineInfo.State.FAILED
                || engine.state() == EngineInfo.State.STOPPED
                || !old.enabled();
        if (engineChanged) loadEngine(newConfig);
    }

    public synchronized void shutdown() {
        for (VoiceSourceAdapter adapter : adapters) {
            try {
                adapter.stop();
            } catch (Throwable t) {
                platform.logger().warn("Failed to stop voice source " + adapter.displayName(), t);
            }
        }
        adapters.clear();

        engineGenerations.incrementAndGet(); // cancels a pending engine load
        if (scheduler != null) scheduler.shutdownNow();

        ExecutorService pool = workers;
        for (SpeakerSession session : sessions.values()) {
            session.submit(() -> session.close(SpeechEndEvent.Reason.DISABLED));
        }
        if (pool != null) {
            pool.shutdown();
            try {
                pool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        sessions.clear();
        subtitles.clearAll();

        SpeechEngine current = engine.engine();
        engine = EngineHolder.stopped(config.engineType(), engineGenerations.get());
        if (current != null) {
            try {
                current.close();
            } catch (Throwable t) {
                platform.logger().warn("Failed to close speech engine", t);
            }
        }
        listeners.clear();
    }

    public void registerVoiceSource(@NotNull VoiceSourceAdapter adapter) throws Exception {
        adapter.start(this);
        adapters.add(adapter);
    }

    // ================================================================ engine

    private void loadEngine(TranscribeConfig cfg) {
        long generation = engineGenerations.incrementAndGet();
        SpeechEngineFactory factory = engines.get(cfg.engineType());
        if (factory == null) {
            platform.logger().error("Unknown speech engine '" + cfg.engineType() + "'. Available: " + engines.types());
            engine = EngineHolder.failed(cfg.engineType(), generation);
            return;
        }
        EngineHolder previous = engine;
        if (previous.engine() == null) {
            engine = EngineHolder.loading(cfg.engineType(), generation);
        }

        Thread loader = new Thread(() -> {
            SpeechEngine created = null;
            try {
                SpeechEngineFactory.Context context = new SpeechEngineFactory.Context(platform.dataFolder(),
                        cfg.transcription().language(), cfg.transcription().autoDetectLanguage(), platform.logger(),
                        platform::onlinePlayerNames);
                created = factory.create(context, cfg.engineSection());
                if (cfg.transcription().autoDetectLanguage() && !created.capabilities().autoLanguage()) {
                    platform.logger().warn("Engine '" + created.name() + "' cannot detect the language automatically; "
                            + "using language '" + cfg.transcription().language() + "'.");
                }
                created.start();
            } catch (Throwable t) {
                if (created != null) created.close();
                if (generation == engineGenerations.get()) {
                    platform.logger().error("Speech engine '" + cfg.engineType() + "' failed to start. "
                            + "Transcription is unavailable until the problem is fixed and the plugin is reloaded.", t);
                    if (engine.engine() == null) engine = EngineHolder.failed(cfg.engineType(), generation);
                }
                return;
            }

            synchronized (TranscriptionService.this) {
                if (generation != engineGenerations.get()) {
                    created.close(); // superseded by a newer reload or shutdown
                    return;
                }
                EngineHolder old = engine;
                engine = EngineHolder.ready(created, generation);
                platform.logger().info("Speech engine '" + created.name() + "' is ready (language: " + created.language() + ").");
                errors.reset("stream-create");
                if (old.engine() != null && old.engine() != created) {
                    // let sessions finish their phrases on the old engine, then close it
                    for (SpeakerSession session : sessions.values()) {
                        session.submit(() -> session.releaseStream(true));
                    }
                    SpeechEngine oldEngine = old.engine();
                    scheduler.schedule(oldEngine::close, 10, TimeUnit.SECONDS);
                }
            }
        }, "PV-Transcribe Engine Loader");
        loader.setDaemon(true);
        loader.start();
    }

    // ================================================================ VoiceInput

    @Override
    public void onAudioFrame(@NotNull VoiceSourceAdapter source, @NotNull SpeakerInfo speaker, @NotNull VoiceFrame frame) {
        TranscribeConfig cfg = config;
        if (!cfg.enabled() || engine.engine() == null) return;
        if (!platform.canTranscribe(speaker.id())) return;

        SpeakerSession session = sessions.computeIfAbsent(speaker.id(), id -> new SpeakerSession(this, id, speaker.name()));
        session.speakerName = speaker.name();
        if (!session.markSequence(frame.sequence())) return; // same audio routed through several sources

        if (!session.isSpeakingNow() && session.pendingFrames.get() == 0
                && activeSpeakers.get() >= cfg.transcription().maxConcurrentSpeakers()) {
            stats.framesDropped.incrementAndGet();
            errors.warn("max-speakers", "Too many players are speaking at once (max-concurrent-speakers: "
                    + cfg.transcription().maxConcurrentSpeakers() + "); some speech is not transcribed.", null);
            return;
        }

        int maxFrames = (int) Math.max(1, cfg.transcription().maxQueueMs() / FRAME_MS);
        if (session.pendingFrames.get() >= maxFrames) {
            stats.framesDropped.incrementAndGet();
            errors.warn("queue-full", "Transcription is falling behind (queue of " + speaker.name()
                    + " exceeded " + cfg.transcription().maxQueueMs() + " ms); dropping audio. "
                    + "Increase transcription.worker-threads or use a smaller model.", null);
            return;
        }

        long now = System.currentTimeMillis();
        session.lastFrameAt = now;
        session.lastActivityAt = now;
        session.stopRequestedAt = 0;
        session.pendingFrames.incrementAndGet();
        session.submit(() -> session.processFrame(source, frame));
    }

    @Override
    public void onSpeakingStopped(@NotNull VoiceSourceAdapter source, @NotNull UUID speakerId) {
        SpeakerSession session = sessions.get(speakerId);
        if (session != null) session.stopRequestedAt = System.currentTimeMillis();
    }

    @Override
    public void onAudience(@NotNull VoiceSourceAdapter source, @NotNull UUID speakerId, @NotNull Audience audience) {
        subtitles.updateAudience(speakerId, source.id() + ":" + audience.channel(), audience);
    }

    /**
     * Must be called by the platform when a player leaves the server.
     */
    public void onPlayerQuit(@NotNull UUID playerId) {
        SpeakerSession session = sessions.remove(playerId);
        if (session != null) {
            session.submit(() -> session.close(SpeechEndEvent.Reason.DISCONNECTED));
        }
        subtitles.remove(playerId);
    }

    /**
     * Demo / preview: shows {@code text} above a player as if they said it, word by word, then as a finished
     * phrase. Only the subtitles are affected; no API events are fired, so other plugins don't react to it.
     * Useful for testing styles and taking screenshots without a microphone.
     */
    public void demo(@NotNull UUID playerId, @NotNull String playerName, @NotNull String text, long wordDelayMs) {
        String[] words = text.trim().split("\\s+");
        long utterance = nextUtteranceId();
        java.time.Instant started = java.time.Instant.now();
        for (int i = 1; i <= words.length; i++) {
            boolean last = i == words.length;
            String partText = String.join(" ", java.util.Arrays.copyOf(words, i));
            long delay = wordDelayMs * i;
            scheduler.schedule(() -> {
                Transcript transcript = new Transcript(playerId, playerName, 0, utterance, partText, partText, false,
                        config.transcription().language(), -1, "demo", "demo", started, java.time.Instant.now());
                subtitles.update(transcript, displayText(transcript));
            }, delay, TimeUnit.MILLISECONDS);
            if (last) {
                scheduler.schedule(() -> {
                    Transcript transcript = new Transcript(playerId, playerName, 0, utterance, partText, partText, true,
                            config.transcription().language(), -1, "demo", "demo", started, java.time.Instant.now());
                    String display = displayText(transcript);
                    subtitles.update(transcript, display);
                    BiConsumer<Transcript, String> handler = finalPhraseHandler;
                    if (handler != null && display != null) platform.syncExecutor().execute(() -> handler.accept(transcript, display));
                }, delay + wordDelayMs * 2, TimeUnit.MILLISECONDS);
            }
        }
    }

    private String displayText(Transcript transcript) {
        TranscribeConfig.DisplayText dt = config.displayText();
        String display = org.lavacast.pvtranscribe.core.text.DisplayTextFormatter.format(
                transcript.getText(), dt.numbersToDigits(), dt.mathSymbols());
        if (dt.matchNicknames()) {
            display = org.lavacast.pvtranscribe.core.text.NicknameMatcher.apply(display, platform.onlinePlayerNames());
        }
        return display;
    }

    /**
     * Ends speech and clears the subtitle of a player, e.g. after death or a world change.
     */
    public void interrupt(@NotNull UUID playerId, @NotNull SpeechEndEvent.Reason reason) {
        SpeakerSession session = sessions.get(playerId);
        if (session != null) session.submit(() -> session.endSpeaking(reason));
        subtitles.remove(playerId);
    }

    private void reap() {
        try {
            long now = System.currentTimeMillis();
            TranscribeConfig cfg = config;
            for (SpeakerSession session : sessions.values()) {
                if (session.isSpeakingNow() || session.pendingFrames.get() > 0) {
                    long silence = now - session.lastFrameAt;
                    boolean stopped = session.stopRequestedAt != 0 && silence >= STOP_GRACE_MS;
                    if ((stopped || silence >= cfg.transcription().silenceTimeoutMs())
                            && session.endQueued.compareAndSet(false, true)) {
                        SpeechEndEvent.Reason reason = stopped ? SpeechEndEvent.Reason.STOPPED_TALKING
                                : SpeechEndEvent.Reason.SILENCE_TIMEOUT;
                        session.submit(() -> session.endSpeaking(reason));
                    }
                } else if (session.hasStream && now - session.lastActivityAt > cfg.transcription().idleRecognizerCloseMs()) {
                    session.hasStream = false;
                    session.submit(() -> session.releaseStream(false));
                }
            }
        } catch (Throwable t) {
            errors.error("reaper", "Error in the PV-Transcribe scheduler", t);
        }
    }

    // ================================================================ called by sessions

    void emitTranscript(Transcript transcript, Set<String> firedTriggers) {
        TranscribeConfig.DisplayText dt = config.displayText();
        String display = org.lavacast.pvtranscribe.core.text.DisplayTextFormatter.format(
                transcript.getText(), dt.numbersToDigits(), dt.mathSymbols());
        if (dt.matchNicknames()) {
            display = org.lavacast.pvtranscribe.core.text.NicknameMatcher.apply(display, platform.onlinePlayerNames());
        }
        for (SubtitleProcessor processor : processors) {
            try {
                display = processor.process(transcript, display);
            } catch (Throwable t) {
                errors.warn("subtitle-processor", "A subtitle processor threw an exception", t);
            }
            if (display == null) break;
        }
        subtitles.update(transcript, display);
        BiConsumer<Transcript, String> phraseHandler = finalPhraseHandler;
        if (transcript.isFinal() && display != null && !display.isBlank() && phraseHandler != null) {
            String shown = display;
            try {
                platform.syncExecutor().execute(() -> phraseHandler.accept(transcript, shown));
            } catch (Throwable t) {
                errors.warn("final-phrase", "Could not deliver a finished phrase", t);
            }
        }

        if (transcript.isFinal() && config.logTranscripts()) {
            platform.logger().info("[voice] " + transcript.getSpeakerName() + ": " + transcript.getText());
        }

        fire(l -> {
            if (transcript.isFinal()) l.onFinalTranscript(transcript);
            else l.onPartialTranscript(transcript);
            l.onTranscript(transcript);
        });
        phrases.evaluate(transcript, firedTriggers);
    }

    /**
     * Platform hook for finished phrases with their display text (numbers as digits, processors applied),
     * e.g. to copy them into the chat. Called on the main thread.
     */
    public void setFinalPhraseHandler(BiConsumer<Transcript, String> handler) {
        this.finalPhraseHandler = handler;
    }

    void fireSpeechStart(SpeakerSession session) {
        SpeechStartEvent event = new SpeechStartEvent(session.getSpeakerId(), session.getSpeakerName(),
                session.getSessionId(), session.getVoiceSource(), session.getVoiceChannel(), session.getStartedAt());
        fire(l -> l.onSpeechStart(event));
    }

    void fireSpeechEnd(SpeechEndEvent event) {
        fire(l -> l.onSpeechEnd(event));
    }

    private void fire(Consumer<TranscriptionListener> call) {
        for (ListenerEntry entry : listeners) {
            Runnable task = () -> {
                try {
                    call.accept(entry.listener());
                } catch (Throwable t) {
                    errors.warn("listener:" + entry.listener().getClass().getName(),
                            "Transcription listener " + entry.listener().getClass().getName() + " threw an exception", t);
                }
            };
            if (entry.executor() == null) {
                task.run();
            } else {
                try {
                    entry.executor().execute(task);
                } catch (Throwable t) {
                    errors.warn("listener-exec", "Could not schedule a transcription listener", t);
                }
            }
        }
    }

    enum DebugMode { OFF, LOG, LOG_AND_SAVE }

    DebugMode debugMode() {
        TranscribeConfig cfg = config;
        if (cfg.saveAudio()) return DebugMode.LOG_AND_SAVE;
        return cfg.debug() ? DebugMode.LOG : DebugMode.OFF;
    }

    /**
     * Diagnostics for one phrase: logs the audio level and, if debug.save-audio is on, writes exactly the
     * audio the engine received to debug-audio/ as a WAV file. Off by default (privacy).
     */
    void debugPhrase(String speaker, long utteranceId, String channel, long frames, long samples, int sampleRate,
                     double dbfs, boolean stereo, String rawText, byte[] pcm) {
        double seconds = sampleRate > 0 ? (double) samples / sampleRate : 0;
        String fileName = null;
        if (pcm != null && pcm.length > 0) {
            fileName = speaker.replaceAll("[^A-Za-z0-9_]", "_") + "-" + utteranceId + ".wav";
            java.nio.file.Path file = platform.dataFolder().resolve("debug-audio").resolve(fileName);
            int rate = sampleRate;
            scheduler.execute(() -> writeWav(file, pcm, rate));
        }
        platform.logger().info(String.format(java.util.Locale.ROOT,
                "[debug] %s phrase #%d (%s): %.1f s, %d frames, %d Hz, level %.1f dBFS%s, engine: '%s'%s",
                speaker, utteranceId, channel, seconds, frames, sampleRate, dbfs, stereo ? ", stereo" : "",
                rawText, fileName != null ? " -> debug-audio/" + fileName : ""));
    }

    private void writeWav(java.nio.file.Path file, byte[] pcm, int sampleRate) {
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                    .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16)
                    .putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2)
                    .putShort((short) 2).putShort((short) 16)
                    .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcm.length);
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(file)) {
                out.write(header.array());
                out.write(pcm);
            }
        } catch (Exception e) {
            errors.warn("debug-audio", "Could not write " + file, e);
        }
    }

    void onSpeakingChanged(boolean started) {
        if (started) activeSpeakers.incrementAndGet();
        else activeSpeakers.decrementAndGet();
    }

    long nextSessionId() {
        return sessionIds.incrementAndGet();
    }

    long nextUtteranceId() {
        return utteranceIds.incrementAndGet();
    }

    String clean(String raw) {
        return TextUtil.cleanEngineText(raw);
    }

    Executor workers() {
        return workers;
    }

    RateLimitedLogger errors() {
        return errors;
    }

    EngineHolder engine() {
        return engine;
    }

    Platform platform() {
        return platform;
    }

    public TranscribeConfig config() {
        return config;
    }

    public SubtitleManager subtitles() {
        return subtitles;
    }

    public Stats stats() {
        return stats;
    }

    public int activeSpeakerCount() {
        return Math.max(0, activeSpeakers.get());
    }

    public boolean hasVoiceClient(@NotNull UUID playerId) {
        for (VoiceSourceAdapter adapter : adapters) {
            try {
                if (adapter.hasVoiceClient(playerId)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // ================================================================ API

    @Override
    public @NotNull String getVersion() {
        return platform.version();
    }

    @Override
    public @NotNull Registration addListener(@NotNull TranscriptionListener listener) {
        ListenerEntry entry = new ListenerEntry(Objects.requireNonNull(listener), null);
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    @Override
    public @NotNull Registration addListener(@NotNull TranscriptionListener listener, @NotNull Executor executor) {
        ListenerEntry entry = new ListenerEntry(Objects.requireNonNull(listener), Objects.requireNonNull(executor));
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    @Override
    public @NotNull PhraseRegistry phrases() {
        return phrases;
    }

    @Override
    public @NotNull Registration addSubtitleProcessor(@NotNull SubtitleProcessor processor) {
        processors.add(Objects.requireNonNull(processor));
        return () -> processors.remove(processor);
    }

    @Override
    public @NotNull Optional<SpeechSession> getActiveSession(@NotNull UUID playerId) {
        SpeakerSession session = sessions.get(playerId);
        return session != null && session.isSpeakingNow() ? Optional.of(session) : Optional.empty();
    }

    @Override
    public @NotNull Collection<SpeechSession> getActiveSessions() {
        List<SpeechSession> list = new ArrayList<>();
        for (SpeakerSession session : sessions.values()) {
            if (session.isSpeakingNow()) list.add(session);
        }
        return list;
    }

    @Override
    public boolean isTranscriptionEnabled(@NotNull UUID playerId) {
        return platform.isTranscriptionEnabled(playerId);
    }

    @Override
    public void setTranscriptionEnabled(@NotNull UUID playerId, boolean enabled) {
        platform.setTranscriptionEnabled(playerId, enabled);
        if (!enabled) interrupt(playerId, SpeechEndEvent.Reason.DISABLED);
    }

    @Override
    public @NotNull EngineInfo getEngineInfo() {
        EngineHolder holder = engine;
        EngineCapabilities caps = holder.engine() != null ? holder.engine().capabilities()
                : new EngineCapabilities(false, false, false);
        String language = holder.engine() != null ? holder.engine().language() : config.transcription().language();
        return new EngineInfo(holder.name(), language, holder.state(), caps.partialResults(), caps.autoLanguage());
    }

    @Override
    public @NotNull Set<String> getVoiceSources() {
        Set<String> ids = new LinkedHashSet<>();
        for (VoiceSourceAdapter adapter : adapters) ids.add(adapter.id());
        return ids;
    }

    @Override
    public @NotNull Executor syncExecutor() {
        return platform.syncExecutor();
    }

    // ================================================================ helpers

    private ExecutorService newWorkerPool(int threads) {
        // slightly below normal priority so recognition never competes with the server tick thread
        return Executors.newFixedThreadPool(threads, new NamedThreadFactory("PV-Transcribe Worker", Thread.NORM_PRIORITY - 1));
    }

    private static boolean sameSection(TranscribeConfig a, TranscribeConfig b) {
        return flatten(a.engineSection(), "").equals(flatten(b.engineSection(), ""));
    }

    private static String flatten(org.lavacast.pvtranscribe.core.config.ConfigSection section, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (String key : new java.util.TreeSet<>(section.getKeys())) {
            Object value = section.get(key);
            if (value instanceof org.lavacast.pvtranscribe.core.config.ConfigSection child) {
                sb.append(flatten(child, prefix + key + "."));
            } else {
                sb.append(prefix).append(key).append('=').append(value).append(';');
            }
        }
        return sb.toString();
    }

    private record ListenerEntry(TranscriptionListener listener, Executor executor) {
    }

    record EngineHolder(SpeechEngine engine, String name, long generation, EngineInfo.State state) {
        static EngineHolder ready(SpeechEngine engine, long generation) {
            return new EngineHolder(engine, engine.name(), generation, EngineInfo.State.READY);
        }

        static EngineHolder loading(String name, long generation) {
            return new EngineHolder(null, name, generation, EngineInfo.State.LOADING);
        }

        static EngineHolder failed(String name, long generation) {
            return new EngineHolder(null, name, generation, EngineInfo.State.FAILED);
        }

        static EngineHolder stopped(String name, long generation) {
            return new EngineHolder(null, name, generation, EngineInfo.State.STOPPED);
        }

        String language() {
            return engine != null ? engine.language() : "unknown";
        }
    }

    /**
     * Counters for the status command.
     */
    public static final class Stats {
        public final AtomicLong framesProcessed = new AtomicLong();
        public final AtomicLong framesDropped = new AtomicLong();
        public final AtomicLong decodeErrors = new AtomicLong();
        public final AtomicLong engineErrors = new AtomicLong();
    }
}
