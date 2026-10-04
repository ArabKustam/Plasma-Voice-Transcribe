package org.lavacast.pvtranscribe.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-player switches stored in {@code players.yml}: transcription disabled by an admin,
 * and subtitles hidden by the player for themself. Thread-safe reads.
 */
final class PlayerData {

    private final File file;
    private final Logger logger;
    private final Set<UUID> transcriptionDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> subtitlesHidden = ConcurrentHashMap.newKeySet();

    PlayerData(@NotNull File file, @NotNull Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void load() {
        transcriptionDisabled.clear();
        subtitlesHidden.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        read(yaml, "transcription-disabled", transcriptionDisabled);
        read(yaml, "subtitles-hidden", subtitlesHidden);
    }

    synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("transcription-disabled", transcriptionDisabled.stream().map(UUID::toString).sorted().toList());
        yaml.set("subtitles-hidden", subtitlesHidden.stream().map(UUID::toString).sorted().toList());
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file.getName(), e);
        }
    }

    boolean isTranscriptionEnabled(UUID id) {
        return !transcriptionDisabled.contains(id);
    }

    void setTranscriptionEnabled(UUID id, boolean enabled) {
        boolean changed = enabled ? transcriptionDisabled.remove(id) : transcriptionDisabled.add(id);
        if (changed) save();
    }

    boolean isSubtitlesHidden(UUID id) {
        return subtitlesHidden.contains(id);
    }

    void setSubtitlesHidden(UUID id, boolean hidden) {
        boolean changed = hidden ? subtitlesHidden.add(id) : subtitlesHidden.remove(id);
        if (changed) save();
    }

    private void read(YamlConfiguration yaml, String key, Set<UUID> target) {
        for (String raw : new ArrayList<>(yaml.getStringList(key))) {
            try {
                target.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException e) {
                logger.warning("Ignoring invalid UUID '" + raw + "' in " + file.getName());
            }
        }
    }
}
