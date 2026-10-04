package org.lavacast.pvtranscribe.api.phrase;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Registration;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Lets plugins react to specific words or phrases.
 *
 * <pre>{@code
 * Registration reg = PVTranscribe.get().phrases().register(
 *     PhraseTrigger.builder("myplugin:fireball")
 *         .phrases("огненный шар", "fireball")
 *         .mode(MatchMode.CONTAINS)
 *         .executor(PVTranscribe.get().syncExecutor()) // run on the main thread
 *         .handler(match -> castFireball(match.speakerId()))
 *         .build());
 * }</pre>
 */
public interface PhraseRegistry {

    @NotNull Registration register(@NotNull PhraseTrigger trigger);

    /**
     * Shortcut: whole-word match of any of the phrases in final transcripts, callback on a worker thread.
     */
    default @NotNull Registration onPhrase(@NotNull String id,
                                           @NotNull Collection<String> phrases,
                                           @NotNull Consumer<PhraseMatch> handler) {
        return register(PhraseTrigger.builder(id).phrases(phrases).handler(handler).build());
    }

    @NotNull Collection<PhraseTrigger> getTriggers();
}
