package org.lavacast.pvtranscribe.api.phrase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A set of phrases and what to do when a player says one of them. Build with {@link #builder(String)}.
 */
public final class PhraseTrigger {

    private final String id;
    private final List<String> phrases;
    private final MatchMode mode;
    private final boolean matchPartial;
    private final @Nullable Executor executor;
    private final Consumer<PhraseMatch> handler;

    private PhraseTrigger(Builder builder) {
        this.id = builder.id;
        this.phrases = List.copyOf(builder.phrases);
        this.mode = builder.mode;
        this.matchPartial = builder.matchPartial;
        this.executor = builder.executor;
        this.handler = Objects.requireNonNull(builder.handler, "handler");
        if (phrases.isEmpty()) {
            throw new IllegalArgumentException("Phrase trigger '" + id + "' has no phrases");
        }
    }

    public static @NotNull Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull List<String> getPhrases() {
        return phrases;
    }

    public @NotNull MatchMode getMode() {
        return mode;
    }

    /**
     * If true, the trigger also fires on partial results, as soon as the phrase is heard,
     * instead of waiting for the end of the sentence. It still fires at most once per utterance.
     */
    public boolean isMatchPartial() {
        return matchPartial;
    }

    public @Nullable Executor getExecutor() {
        return executor;
    }

    public @NotNull Consumer<PhraseMatch> getHandler() {
        return handler;
    }

    public static final class Builder {
        private final String id;
        private final List<String> phrases = new ArrayList<>();
        private MatchMode mode = MatchMode.CONTAINS;
        private boolean matchPartial;
        private Executor executor;
        private Consumer<PhraseMatch> handler;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder phrases(@NotNull String... phrases) {
            return phrases(Arrays.asList(phrases));
        }

        public Builder phrases(@NotNull Collection<String> phrases) {
            this.phrases.addAll(phrases);
            return this;
        }

        public Builder mode(@NotNull MatchMode mode) {
            this.mode = Objects.requireNonNull(mode);
            return this;
        }

        public Builder matchPartial(boolean matchPartial) {
            this.matchPartial = matchPartial;
            return this;
        }

        /**
         * Executor for the handler, e.g. {@code PVTranscribe.get().syncExecutor()}.
         * Default: a PV-Transcribe worker thread.
         */
        public Builder executor(@Nullable Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder handler(@NotNull Consumer<PhraseMatch> handler) {
            this.handler = handler;
            return this;
        }

        public PhraseTrigger build() {
            return new PhraseTrigger(this);
        }
    }
}
