package org.lavacast.pvtranscribe.paper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lavacast.pvtranscribe.core.config.ConfigSection;

import java.util.Set;

/**
 * Exposes Bukkit's YAML configuration to the platform-independent core.
 */
public final class BukkitConfigSection implements ConfigSection {

    private final ConfigurationSection section;

    public BukkitConfigSection(@NotNull ConfigurationSection section) {
        this.section = section;
    }

    @Override
    public @Nullable Object get(@NotNull String path) {
        Object value = section.get(path);
        return value instanceof ConfigurationSection child ? new BukkitConfigSection(child) : value;
    }

    @Override
    public @Nullable ConfigSection getSection(@NotNull String path) {
        ConfigurationSection child = section.getConfigurationSection(path);
        return child == null ? null : new BukkitConfigSection(child);
    }

    @Override
    public @NotNull Set<String> getKeys() {
        return section.getKeys(false);
    }
}
