package org.lavacast.pvtranscribe.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.api.event.SpeechEndEvent;
import org.lavacast.pvtranscribe.api.event.SpeechStartEvent;
import org.lavacast.pvtranscribe.api.event.TranscriptionListener;
import org.lavacast.pvtranscribe.api.phrase.MatchMode;
import org.lavacast.pvtranscribe.api.phrase.PhraseMatch;
import org.lavacast.pvtranscribe.api.phrase.PhraseTrigger;
import org.lavacast.pvtranscribe.core.config.MapConfigSection;
import org.lavacast.pvtranscribe.core.config.TranscribeConfig;
import org.lavacast.pvtranscribe.core.engine.EngineCapabilities;
import org.lavacast.pvtranscribe.core.engine.EngineRegistry;
import org.lavacast.pvtranscribe.core.engine.EngineResult;
import org.lavacast.pvtranscribe.core.engine.SpeechEngine;
import org.lavacast.pvtranscribe.core.engine.SpeechStream;
import org.lavacast.pvtranscribe.core.session.TranscriptionService;
import org.lavacast.pvtranscribe.core.subtitle.SubtitleView;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;
import org.lavacast.pvtranscribe.core.voice.FrameDecoder;
import org.lavacast.pvtranscribe.core.voice.SpeakerInfo;
import org.lavacast.pvtranscribe.core.voice.VoiceFrame;
import org.lavacast.pvtranscribe.core.voice.VoiceInput;
import org.lavacast.pvtranscribe.core.voice.VoiceSourceAdapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real pipeline (threads, sessions, subtitles, phrases) with a fake voice chat and a fake engine
 * whose "audio" frames carry words.
 */
class PipelineTest {

    private TranscriptionService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void streamsPartialsThenOneFinalPerPhrase() throws Exception {
        FakeAdapter voice = start();
        Recorder recorder = new Recorder();
        service.addListener(recorder);
        List<PhraseMatch> matches = new CopyOnWriteArrayList<>();
        service.phrases().register(PhraseTrigger.builder("test:mine").phrases("пойдем в шахту")
                .mode(MatchMode.CONTAINS).handler(matches::add).build());

        UUID mark = UUID.randomUUID();
        voice.say(mark, "MARK", "ребята", "давайте", "пойдем", "в", "шахту");
        voice.input.onSpeakingStopped(voice, mark);

        assertTrue(recorder.ended.await(5, TimeUnit.SECONDS), "speech end not delivered");

        assertEquals(1, recorder.starts.size());
        assertEquals(List.of("ребята", "ребята давайте", "ребята давайте пойдем", "ребята давайте пойдем в",
                "ребята давайте пойдем в шахту"), recorder.partials.stream().map(Transcript::getText).toList());
        assertEquals(1, recorder.finals.size());
        Transcript fin = recorder.finals.get(0);
        assertEquals("ребята давайте пойдем в шахту", fin.getText());
        assertEquals("MARK", fin.getSpeakerName());
        assertEquals(recorder.partials.get(0).getUtteranceId(), fin.getUtteranceId());
        assertEquals(SpeechEndEvent.Reason.STOPPED_TALKING, recorder.ends.get(0).reason());

        assertEquals(1, matches.size(), "phrase trigger must fire exactly once");
        assertTrue(service.subtitles().collect(System.currentTimeMillis()).get(0).bubbles().get(0).isFinal());
    }

    @Test
    void enginePauseSplitsPhrasesAndSilenceEndsSpeech() throws Exception {
        FakeAdapter voice = start();
        Recorder recorder = new Recorder();
        service.addListener(recorder);

        UUID id = UUID.randomUUID();
        voice.say(id, "Steve", "открой", "ворота", FakeStream.PAUSE, "огненный", "шар");
        // no "stopped" signal: silence timeout (300 ms in this config) must end the speech
        assertTrue(recorder.ended.await(5, TimeUnit.SECONDS));

        assertEquals(List.of("открой ворота", "огненный шар"), recorder.finals.stream().map(Transcript::getText).toList());
        assertTrue(recorder.finals.get(1).getUtteranceId() > recorder.finals.get(0).getUtteranceId());
        assertEquals(SpeechEndEvent.Reason.SILENCE_TIMEOUT, recorder.ends.get(0).reason());
    }

    @Test
    void speakersAreIndependent() throws Exception {
        FakeAdapter voice = start();
        Recorder recorder = new Recorder(2);
        service.addListener(recorder);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Thread t1 = new Thread(() -> voice.say(a, "A", "привет", "всем"));
        Thread t2 = new Thread(() -> voice.say(b, "B", "идем", "в", "шахту"));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        voice.input.onSpeakingStopped(voice, a);
        voice.input.onSpeakingStopped(voice, b);
        assertTrue(recorder.ended.await(5, TimeUnit.SECONDS));

        Map<UUID, String> finals = new java.util.HashMap<>();
        for (Transcript t : recorder.finals) finals.put(t.getSpeakerId(), t.getText());
        assertEquals("привет всем", finals.get(a));
        assertEquals("идем в шахту", finals.get(b));
        assertEquals(2, service.subtitles().collect(System.currentTimeMillis()).size());
    }

    @Test
    void olderPhraseNeverOverwritesNewerSubtitle() {
        TranscribeConfig config = config();
        var subtitles = new org.lavacast.pvtranscribe.core.subtitle.SubtitleManager(config);
        UUID id = UUID.randomUUID();
        subtitles.update(transcript(id, 6, "новая фраза", false), "новая фраза");
        subtitles.update(transcript(id, 5, "старая фраза", true), "старая фраза");

        List<SubtitleView> views = subtitles.collect(System.currentTimeMillis());
        assertEquals(1, views.size());
        assertEquals(1, views.get(0).bubbles().size());
        assertTrue(views.get(0).bubbles().get(0).text().contains("Новая фраза"));
        assertFalse(views.get(0).bubbles().get(0).isFinal());
    }

    @Test
    void longPhraseIsSplitIntoBubblesAndOldestDropped() {
        TranscribeConfig config = TranscribeConfig.parse(new MapConfigSection(Map.of(
                "subtitles", Map.of("max-bubbles", 3, "max-words-per-bubble", 3))));
        var subtitles = new org.lavacast.pvtranscribe.core.subtitle.SubtitleManager(config);
        UUID id = UUID.randomUUID();
        subtitles.update(transcript(id, 1, "один", false), "раз два");
        subtitles.update(transcript(id, 1, "один", false), "раз два три четыре пять");
        List<SubtitleView.Bubble> bubbles = subtitles.collect(System.currentTimeMillis()).get(0).bubbles();
        assertEquals(2, bubbles.size());
        assertTrue(bubbles.get(0).text().contains("четыре пять"), bubbles.get(0).text()); // newest, at the head
        assertTrue(bubbles.get(1).text().contains("Раз два три"), bubbles.get(1).text());
        assertTrue(bubbles.get(1).isFinal(), "full bubble is frozen");

        subtitles.update(transcript(id, 1, "один", true), "раз два три четыре пять шесть семь");
        subtitles.update(transcript(id, 2, "x", true), "новая фраза");
        bubbles = subtitles.collect(System.currentTimeMillis()).get(0).bubbles();
        assertEquals(3, bubbles.size(), "max 3 bubbles");
        assertTrue(bubbles.get(0).text().contains("Новая фраза"));
        assertTrue(bubbles.get(2).text().contains("четыре пять шесть"), "oldest bubble was dropped");
    }

    // ------------------------------------------------------------------ helpers

    private FakeAdapter start() throws Exception {
        TranscribeConfig config = config();
        EngineRegistry engines = new EngineRegistry();
        engines.register("fake", (context, settings) -> new FakeEngine());
        service = new TranscriptionService(new FakePlatform(), engines, config);
        service.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (service.getEngineInfo().state() != org.lavacast.pvtranscribe.api.EngineInfo.State.READY) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("engine did not start");
            Thread.sleep(10);
        }
        FakeAdapter adapter = new FakeAdapter();
        service.registerVoiceSource(adapter);
        return adapter;
    }

    private static TranscribeConfig config() {
        return TranscribeConfig.parse(new MapConfigSection(Map.of(
                "transcription", Map.of("partial-interval-ms", 0, "silence-timeout-ms", 300, "worker-threads", 2),
                "engine", Map.of("type", "fake")
        )));
    }

    private static Transcript transcript(UUID id, long utterance, String text, boolean isFinal) {
        java.time.Instant now = java.time.Instant.now();
        return new Transcript(id, "P", 1, utterance, text, text, isFinal, "ru", -1, "fake", "proximity", now, now);
    }

    static final class Recorder implements TranscriptionListener {
        final List<SpeechStartEvent> starts = new CopyOnWriteArrayList<>();
        final List<Transcript> partials = new CopyOnWriteArrayList<>();
        final List<Transcript> finals = new CopyOnWriteArrayList<>();
        final List<SpeechEndEvent> ends = new CopyOnWriteArrayList<>();
        final CountDownLatch ended;

        Recorder() {
            this(1);
        }

        Recorder(int expectedEnds) {
            ended = new CountDownLatch(expectedEnds);
        }

        @Override
        public void onSpeechStart(@NotNull SpeechStartEvent event) {
            starts.add(event);
        }

        @Override
        public void onPartialTranscript(@NotNull Transcript transcript) {
            partials.add(transcript);
        }

        @Override
        public void onFinalTranscript(@NotNull Transcript transcript) {
            finals.add(transcript);
        }

        @Override
        public void onSpeechEnd(@NotNull SpeechEndEvent event) {
            ends.add(event);
            ended.countDown();
        }
    }

    /** Frames carry a word as UTF-8 bytes; the decoder turns it into a 1-sample "PCM" index. */
    static final class FakeAdapter implements VoiceSourceAdapter {
        static final List<String> WORDS = Collections.synchronizedList(new ArrayList<>());
        VoiceInput input;
        long sequence;

        void say(UUID id, String name, String... words) {
            for (String word : words) {
                long seq;
                synchronized (this) {
                    seq = ++sequence;
                }
                input.onAudioFrame(this, new SpeakerInfo(id, name),
                        new VoiceFrame(seq, word.getBytes(StandardCharsets.UTF_8), false, 48_000, "proximity"));
            }
        }

        @Override
        public @NotNull String id() {
            return "fake";
        }

        @Override
        public @NotNull String displayName() {
            return "Fake";
        }

        @Override
        public void start(@NotNull VoiceInput input) {
            this.input = input;
        }

        @Override
        public void stop() {
        }

        @Override
        public @NotNull FrameDecoder createDecoder(boolean stereo) {
            return new FrameDecoder() {
                @Override
                public short @NotNull [] decode(@NotNull VoiceFrame frame) {
                    String word = new String(frame.data(), StandardCharsets.UTF_8);
                    synchronized (WORDS) {
                        int idx = WORDS.indexOf(word);
                        if (idx < 0) {
                            WORDS.add(word);
                            idx = WORDS.size() - 1;
                        }
                        return new short[]{(short) idx};
                    }
                }

                @Override
                public void reset() {
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public boolean hasVoiceClient(@NotNull UUID playerId) {
            return true;
        }
    }

    static final class FakeEngine implements SpeechEngine {
        @Override
        public @NotNull String name() {
            return "fake";
        }

        @Override
        public @NotNull EngineCapabilities capabilities() {
            return new EngineCapabilities(true, false, true);
        }

        @Override
        public void start() {
        }

        @Override
        public @NotNull SpeechStream createStream(int sampleRate) {
            return new FakeStream();
        }

        @Override
        public @NotNull String language() {
            return "ru";
        }

        @Override
        public void close() {
        }
    }

    static final class FakeStream implements SpeechStream {
        static final String PAUSE = "<pause>";
        private final StringBuilder text = new StringBuilder();

        @Override
        public @NotNull EngineResult accept(short @NotNull [] pcm) {
            String word = FakeAdapter.WORDS.get(pcm[0]);
            if (word.equals(PAUSE)) {
                String result = text.toString();
                text.setLength(0);
                return EngineResult.endpoint(result, -1);
            }
            if (text.length() > 0) text.append(' ');
            text.append(word);
            return EngineResult.partial(text.toString());
        }

        @Override
        public @NotNull EngineResult finish() {
            String result = text.toString();
            text.setLength(0);
            return EngineResult.endpoint(result, -1);
        }

        @Override
        public void reset() {
            text.setLength(0);
        }

        @Override
        public void close() {
        }
    }

    static final class FakePlatform implements Platform {
        @Override
        public @NotNull PlatformLogger logger() {
            return new PlatformLogger() {
                @Override
                public void info(@NotNull String message) {
                    System.out.println("[info] " + message);
                }

                @Override
                public void warn(@NotNull String message, Throwable error) {
                    System.out.println("[warn] " + message + (error != null ? " " + error : ""));
                }

                @Override
                public void error(@NotNull String message, Throwable error) {
                    System.out.println("[error] " + message + (error != null ? " " + error : ""));
                }
            };
        }

        @Override
        public @NotNull Executor syncExecutor() {
            return Runnable::run;
        }

        @Override
        public @NotNull Path dataFolder() {
            return Path.of("build", "test-data");
        }

        @Override
        public @NotNull String version() {
            return "test";
        }

        @Override
        public boolean canTranscribe(@NotNull UUID playerId) {
            return true;
        }

        @Override
        public void setTranscriptionEnabled(@NotNull UUID playerId, boolean enabled) {
        }

        @Override
        public boolean isTranscriptionEnabled(@NotNull UUID playerId) {
            return true;
        }
    }
}
