package org.lavacast.pvtranscribe.api.phrase;

/**
 * How a phrase is compared with the normalized transcript text
 * (see {@link org.lavacast.pvtranscribe.api.text.TextNormalizer}).
 */
public enum MatchMode {
    /** The whole phrase equals the phrase. */
    EXACT,
    /** The phrase appears in the text as whole words. */
    CONTAINS,
    /** The text starts with the phrase (whole words), e.g. voice commands with arguments. */
    STARTS_WITH,
    /** Phrases are Java regular expressions matched against the normalized text ({@code find()}). */
    REGEX
}
