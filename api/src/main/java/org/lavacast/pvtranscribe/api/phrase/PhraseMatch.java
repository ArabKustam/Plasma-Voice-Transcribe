package org.lavacast.pvtranscribe.api.phrase;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Transcript;

import java.util.UUID;

/**
 * A phrase trigger fired.
 *
 * @param triggerId     id of the {@link PhraseTrigger}
 * @param matchedPhrase the configured phrase (or regex) that matched
 * @param matchedText   the part of the normalized text that matched
 * @param remainder     normalized text after the match (useful for commands with arguments), may be empty
 * @param transcript    transcript that caused the match (partial or final)
 */
public record PhraseMatch(
        @NotNull String triggerId,
        @NotNull String matchedPhrase,
        @NotNull String matchedText,
        @NotNull String remainder,
        @NotNull Transcript transcript
) {

    public @NotNull UUID speakerId() {
        return transcript.getSpeakerId();
    }

    public @NotNull String speakerName() {
        return transcript.getSpeakerName();
    }
}
