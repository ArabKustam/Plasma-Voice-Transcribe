package org.lavacast.pvtranscribe.core.engine;

/**
 * @param partialResults     produces intermediate hypotheses while the player speaks
 * @param autoLanguage       can detect the spoken language
 * @param endpointDetection  detects pauses between phrases by itself
 */
public record EngineCapabilities(boolean partialResults, boolean autoLanguage, boolean endpointDetection) {
}
