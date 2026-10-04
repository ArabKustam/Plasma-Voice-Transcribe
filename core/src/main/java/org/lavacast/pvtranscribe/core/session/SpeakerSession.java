package org.lavacast.pvtranscribe.core.session;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.SpeechSession;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.api.event.SpeechEndEvent;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.voice.FrameDecoder;
import org.lavacast.pvtranscribe.core.voice.VoiceFrame;
import org.lavacast.pvtranscribe.core.voice.VoiceSourceAdapter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-speaker actor. All recognition work for one player runs serially through its mailbox on the
 * shared worker pool, so different players are processed in parallel and independently, while one
 * player's audio is always processed in order and never by two threads at once.
 * <p>
 * Fields marked "worker" are touched only from inside the mailbox.
 */
public final class SpeakerSession implements SpeechSession {

    private static final int DEDUPE_WINDOW = 32;

    private final TranscriptionService service;
    final UUID speakerId;
    volatile String speakerName;

    private final ConcurrentLinkedQueue<Runnable> mailbox = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    final AtomicInteger pendingFrames = new AtomicInteger();

    // read by the scheduler thread
    volatile long lastFrameAt;
    volatile long lastActivityAt = System.currentTimeMillis();
    volatile long stopRequestedAt;
    final AtomicBoolean endQueued = new AtomicBoolean();
    volatile boolean hasStream;

    // API snapshot
    private volatile boolean speaking;
    private volatile long sessionId;
    private volatile Instant startedAt = Instant.now();
    private volatile String voiceSource = "unknown";
    private volatile String voiceChannel = "unknown";
    private volatile Transcript current;

    // duplicate detection (guarded by "this")
    private final long[] recentSequences = new long[DEDUPE_WINDOW];
    private int recentCount;
    private int recentIndex;

    // worker
    private VoiceSourceAdapter decoderSource;
    private boolean decoderStereo;
    private FrameDecoder decoder;
    private SpeechStream stream;
    private long streamGeneration = -1;
    private int streamSampleRate;
    private long utteranceId;
    private Instant utteranceStart;
    private long utteranceSamples;
    private String lastPartial = "";
    private long lastPartialEmitAt;
    private Transcript lastFinal;
    private final Set<String> firedTriggers = new HashSet<>();
    private boolean closed;
    // diagnostics of the current phrase (only used when debug.verbose / debug.save-audio are on)
    private long debugFrames;
    private double debugSumSquares;
    private long debugSamples;
    private boolean debugStereo;
    private java.io.ByteArrayOutputStream debugAudio;

    SpeakerSession(TranscriptionService service, UUID speakerId, String speakerName) {
        this.service = service;
        this.speakerId = speakerId;
        this.speakerName = speakerName;
    }

    // ---------------------------------------------------------------- mailbox

    void submit(Runnable task) {
        mailbox.add(task);
        schedule();
    }

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) {
            try {
                service.workers().execute(this::drain);
            } catch (Throwable t) {
                // pool shut down
                scheduled.set(false);
                mailbox.clear();
            }
        }
    }

    private void drain() {
        int budget = 64; // let other speakers run too
        try {
            Runnable task;
            while (budget-- > 0 && (task = mailbox.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    service.errors().error("session-task", "Unexpected error while transcribing " + speakerName, t);
                }
            }
        } finally {
            scheduled.set(false);
            if (!mailbox.isEmpty()) schedule();
        }
    }

    /**
     * @return false if this sequence number was already seen recently (same audio routed twice)
     */
    synchronized boolean markSequence(long sequence) {
        for (int i = 0; i < recentCount; i++) {
            if (recentSequences[i] == sequence) return false;
        }
        recentSequences[recentIndex] = sequence;
        recentIndex = (recentIndex + 1) % DEDUPE_WINDOW;
        recentCount = Math.min(recentCount + 1, DEDUPE_WINDOW);
        return true;
    }

    // ---------------------------------------------------------------- worker logic

    void processFrame(VoiceSourceAdapter source, VoiceFrame frame) {
        pendingFrames.decrementAndGet();
        if (closed) return;

        TranscriptionService.EngineHolder engine = service.engine();
        if (engine.engine() == null) return;
        if (!service.platform().canTranscribe(speakerId)) {
            if (speaking) endSpeaking(SpeechEndEvent.Reason.DISABLED);
            return;
        }

        short[] pcm = decode(source, frame);
        if (pcm == null || pcm.length == 0) return;
        service.stats().framesProcessed.incrementAndGet();

        if (!speaking) beginSpeaking(source);
        voiceChannel = frame.channel();

        SpeechStream s = ensureStream(engine, frame.sampleRate());
        if (s == null) return;

        if (utteranceId == 0) beginUtterance();
        utteranceSamples += pcm.length;
        collectDebug(pcm, frame.stereo());

        EngineResult result;
        try {
            result = s.accept(pcm);
        } catch (Throwable t) {
            onStreamError(t);
            return;
        }

        switch (result.type()) {
            case PARTIAL -> onPartial(result);
            case FINAL -> finishUtterance(result);
            default -> {
            }
        }

        if (utteranceId != 0 && streamSampleRate > 0
                && utteranceSamples * 1000L / streamSampleRate >= service.config().transcription().maxPhraseMs()) {
            finishCurrent();
        }
    }

    void endSpeaking(SpeechEndEvent.Reason reason) {
        endQueued.set(false);
        stopRequestedAt = 0;
        if (closed) return;
        finishCurrent();
        if (!speaking) return;
        speaking = false;
        service.onSpeakingChanged(false);
        if (decoder != null) decoder.reset();
        service.fireSpeechEnd(new SpeechEndEvent(speakerId, speakerName, sessionId, voiceSource, reason,
                lastFinal, Instant.now()));
        current = null;
    }

    /**
     * Ends the current phrase with the stream (if any) and closes the stream.
     */
    void releaseStream(boolean finalizePhrase) {
        if (finalizePhrase) finishCurrent();
        if (stream != null) {
            try {
                stream.close();
            } catch (Throwable t) {
                service.errors().warn("stream-close", "Failed to close a recognizer", t);
            }
        }
        stream = null;
        hasStream = false;
        streamGeneration = -1;
        utteranceId = 0;
    }

    void close(SpeechEndEvent.Reason reason) {
        if (closed) return;
        endSpeaking(reason);
        releaseStream(false);
        if (decoder != null) {
            try {
                decoder.close();
            } catch (Throwable ignored) {
            }
            decoder = null;
        }
        closed = true;
    }

    private void beginSpeaking(VoiceSourceAdapter source) {
        speaking = true;
        service.onSpeakingChanged(true);
        sessionId = service.nextSessionId();
        startedAt = Instant.now();
        voiceSource = source.id();
        lastFinal = null;
        current = null;
        service.fireSpeechStart(this);
    }

    private void beginUtterance() {
        utteranceId = service.nextUtteranceId();
        utteranceStart = Instant.now();
        utteranceSamples = 0;
        lastPartial = "";
        lastPartialEmitAt = 0;
        firedTriggers.clear();
        debugFrames = 0;
        debugSumSquares = 0;
        debugSamples = 0;
        debugStereo = false;
        debugAudio = service.config().saveAudio() ? new java.io.ByteArrayOutputStream() : null;
    }

    private void collectDebug(short[] pcm, boolean stereo) {
        TranscriptionService.DebugMode mode = service.debugMode();
        if (mode == TranscriptionService.DebugMode.OFF) return;
        debugFrames++;
        debugStereo |= stereo;
        for (short sample : pcm) debugSumSquares += (double) sample * sample;
        debugSamples += pcm.length;
        if (debugAudio != null && debugAudio.size() < 60 * 48_000 * 2) {
            for (short sample : pcm) {
                debugAudio.write(sample & 0xFF);
                debugAudio.write((sample >> 8) & 0xFF);
            }
        }
    }

    private void onPartial(EngineResult result) {
        if (!service.config().transcription().partialResults()) return;
        String raw = result.text();
        String text = service.clean(raw);
        if (text.isEmpty() || text.equals(lastPartial)) return;
        long now = System.currentTimeMillis();
        if (now - lastPartialEmitAt < service.config().transcription().partialIntervalMs()) return;
        lastPartial = text;
        lastPartialEmitAt = now;
        Transcript transcript = transcript(raw, text, false, result);
        current = transcript;
        service.emitTranscript(transcript, firedTriggers);
    }

    private void finishCurrent() {
        if (utteranceId == 0 || stream == null) {
            utteranceId = 0;
            return;
        }
        EngineResult result;
        try {
            result = stream.finish();
        } catch (Throwable t) {
            onStreamError(t);
            return;
        }
        finishUtterance(result);
    }

    private void finishUtterance(EngineResult result) {
        if (utteranceId == 0) return;
        String raw = result.text();
        String text = service.clean(raw);
        long id = utteranceId;
        if (service.debugMode() != TranscriptionService.DebugMode.OFF && debugSamples > 0) {
            double rms = Math.sqrt(debugSumSquares / debugSamples);
            double dbfs = rms > 0 ? 20 * Math.log10(rms / 32768.0) : -120;
            service.debugPhrase(speakerName, id, voiceChannel, debugFrames, debugSamples, streamSampleRate,
                    dbfs, debugStereo, raw, debugAudio == null ? null : debugAudio.toByteArray());
            debugAudio = null;
            debugSamples = 0;
        }
        if (text.isEmpty()) {
            service.subtitles().discard(speakerId, id);
            current = null;
        } else {
            Transcript transcript = transcript(raw, text, true, result);
            current = transcript;
            lastFinal = transcript;
            service.emitTranscript(transcript, firedTriggers);
        }
        utteranceId = 0;
    }

    private Transcript transcript(String raw, String text, boolean isFinal, EngineResult result) {
        String language = result.language() != null ? result.language() : service.engine().language();
        return new Transcript(speakerId, speakerName, sessionId, utteranceId, raw, text, isFinal, language,
                result.confidence(), voiceSource, voiceChannel, utteranceStart, Instant.now());
    }

    private short[] decode(VoiceSourceAdapter source, VoiceFrame frame) {
        try {
            if (decoder == null || decoderSource != source || decoderStereo != frame.stereo()) {
                if (decoder != null) decoder.close();
                decoder = source.createDecoder(frame.stereo());
                decoderSource = source;
                decoderStereo = frame.stereo();
            }
            return decoder.decode(frame);
        } catch (Throwable t) {
            service.stats().decodeErrors.incrementAndGet();
            service.errors().warn("decode:" + source.id(), "Failed to decode " + source.displayName()
                    + " audio of " + speakerName, t);
            return null;
        }
    }

    private SpeechStream ensureStream(TranscriptionService.EngineHolder engine, int sampleRate) {
        if (stream != null && (streamGeneration != engine.generation() || streamSampleRate != sampleRate)) {
            // engine was reloaded or audio format changed: finish with the old stream first
            releaseStream(true);
        }
        if (stream == null) {
            try {
                stream = engine.engine().createStream(sampleRate);
                streamGeneration = engine.generation();
                streamSampleRate = sampleRate;
                hasStream = true;
                service.errors().reset("stream-create");
            } catch (Throwable t) {
                service.stats().engineErrors.incrementAndGet();
                service.errors().error("stream-create", "Speech engine could not create a recognizer", t);
                return null;
            }
        }
        return stream;
    }

    private void onStreamError(Throwable t) {
        service.stats().engineErrors.incrementAndGet();
        service.errors().warn("stream-accept", "Speech engine failed while transcribing " + speakerName
                + "; the phrase was dropped", t);
        // a broken recognizer is thrown away and recreated on the next frame
        long id = utteranceId;
        releaseStream(false);
        if (id != 0) service.subtitles().discard(speakerId, id);
    }

    // ---------------------------------------------------------------- SpeechSession

    boolean isSpeakingNow() {
        return speaking;
    }

    @Override
    public @NotNull UUID getSpeakerId() {
        return speakerId;
    }

    @Override
    public @NotNull String getSpeakerName() {
        return speakerName;
    }

    @Override
    public long getSessionId() {
        return sessionId;
    }

    @Override
    public @NotNull String getVoiceSource() {
        return voiceSource;
    }

    @Override
    public @NotNull String getVoiceChannel() {
        return voiceChannel;
    }

    @Override
    public @NotNull Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public @NotNull Optional<Transcript> getCurrentTranscript() {
        return Optional.ofNullable(current);
    }
}
