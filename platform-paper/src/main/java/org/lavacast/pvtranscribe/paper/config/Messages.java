package org.lavacast.pvtranscribe.paper.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.core.util.TextUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Messages {

    private final String prefix;
    private final Map<String, String> messages = new HashMap<>();
    private final List<String> help;

    public Messages(ConfigurationSection section) {
        if (section == null) {
            prefix = "";
            help = List.of();
            return;
        }
        prefix = TextUtil.colorize(section.getString("prefix", ""));
        for (String key : section.getKeys(false)) {
            if (section.isString(key)) messages.put(key, TextUtil.colorize(section.getString(key, key)));
        }
        help = section.getStringList("help").stream().map(TextUtil::colorize).toList();
    }

    public void send(@NotNull CommandSender sender, @NotNull String key, String... placeholders) {
        String message = messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        sender.sendMessage(prefix + message);
    }

    /**
     * Colorized text of a message without the prefix.
     */
    public @NotNull String text(@NotNull String key) {
        return messages.getOrDefault(key, key);
    }

    public void raw(@NotNull CommandSender sender, @NotNull String text) {
        sender.sendMessage(prefix + TextUtil.colorize(text));
    }

    public void help(@NotNull CommandSender sender) {
        for (String line : help) sender.sendMessage(line);
    }
}
