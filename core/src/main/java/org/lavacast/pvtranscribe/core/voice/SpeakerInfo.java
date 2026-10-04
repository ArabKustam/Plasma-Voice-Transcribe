package org.lavacast.pvtranscribe.core.voice;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SpeakerInfo(@NotNull UUID id, @NotNull String name) {
}
