package org.lavacast.pvtranscribe.paper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.EngineInfo;
import org.lavacast.pvtranscribe.core.session.TranscriptionService;
import org.lavacast.pvtranscribe.paper.config.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class PVTranscribeCommand implements TabExecutor {

    private final PVTranscribePlugin plugin;

    PVTranscribeCommand(PVTranscribePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        Messages messages = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "player-only");
                    return true;
                }
                if (!check(sender, "pvtranscribe.command.toggle")) return true;
                boolean hide = !plugin.playerData().isSubtitlesHidden(player.getUniqueId());
                plugin.playerData().setSubtitlesHidden(player.getUniqueId(), hide);
                plugin.platform().refresh(player);
                messages.send(sender, hide ? "subtitles-hidden" : "subtitles-shown");
            }
            case "reload" -> {
                if (!check(sender, "pvtranscribe.admin.reload")) return true;
                plugin.reloadPlugin();
                messages.send(sender, "reloaded");
            }
            case "status" -> {
                if (!check(sender, "pvtranscribe.admin.status")) return true;
                status(sender);
            }
            case "player" -> {
                if (!check(sender, "pvtranscribe.admin.player")) return true;
                if (args.length < 3) {
                    messages.help(sender);
                    return true;
                }
                OfflinePlayer target = findPlayer(args[1]);
                if (target == null) {
                    messages.send(sender, "player-not-found", "player", args[1]);
                    return true;
                }
                boolean enable = parseToggle(args[2]);
                plugin.service().setTranscriptionEnabled(target.getUniqueId(), enable);
                String name = target.getName() != null ? target.getName() : args[1];
                messages.send(sender, enable ? "player-transcription-on" : "player-transcription-off", "player", name);
            }
            case "demo" -> {
                if (!check(sender, "pvtranscribe.admin.demo")) return true;
                Player target;
                int textFrom = 1;
                if (args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null) {
                    target = Bukkit.getPlayerExact(args[1]);
                    textFrom = 2;
                } else if (sender instanceof Player self) {
                    target = self;
                } else {
                    messages.send(sender, "player-only");
                    return true;
                }
                String text = args.length > textFrom ? ConfigEditor.join(args, textFrom) : messages.text("demo-phrase");
                plugin.service().demo(target.getUniqueId(), target.getName(), text, 350);
                messages.send(sender, "demo-started", "player", target.getName());
            }
            case "get", "config" -> {
                if (!check(sender, "pvtranscribe.admin.config")) return true;
                String prefix = args.length >= 2 ? args[1] : "";
                List<String> paths = ConfigEditor.paths(plugin.getConfig()).stream()
                        .filter(p -> p.startsWith(prefix)).toList();
                if (paths.isEmpty()) {
                    messages.send(sender, "config-unknown", "option", prefix);
                    return true;
                }
                if (paths.size() > 40) {
                    // too many to list: show the top-level sections
                    java.util.Set<String> sections = new java.util.TreeSet<>();
                    for (String p : paths) {
                        int dot = p.indexOf('.', prefix.length() + 1);
                        sections.add(dot > 0 ? p.substring(0, dot) : p);
                    }
                    messages.raw(sender, "&7Sections: &f" + String.join("&7, &f", sections)
                            + " &7- use &f/pvt get <section>");
                    return true;
                }
                for (String p : paths) {
                    sender.sendMessage(org.lavacast.pvtranscribe.core.util.TextUtil.colorize("&7" + p + ": &f")
                            + ConfigEditor.show(plugin.getConfig(), p));
                }
            }
            case "set" -> {
                if (!check(sender, "pvtranscribe.admin.config")) return true;
                if (args.length < 3) {
                    messages.send(sender, "config-usage");
                    return true;
                }
                String path = args[1];
                if (!ConfigEditor.isKnown(plugin.getConfig(), path)) {
                    messages.send(sender, "config-unknown", "option", path);
                    return true;
                }
                Object value = ConfigEditor.parse(plugin.getConfig(), path, ConfigEditor.join(args, 2));
                if (value == null) {
                    messages.send(sender, "config-bad-value", "option", path, "value", ConfigEditor.join(args, 2));
                    return true;
                }
                plugin.setOption(path, value);
                messages.send(sender, "config-set", "option", path, "value", ConfigEditor.show(plugin.getConfig(), path));
            }
            case "engine" -> {
                if (!check(sender, "pvtranscribe.admin.engine")) return true;
                if (args.length < 2) {
                    messages.raw(sender, "&7Engine: &f" + plugin.service().getEngineInfo().name()
                            + " &7(" + plugin.service().getEngineInfo().state() + ")&7. Available: &fauto, "
                            + String.join(", ", new java.util.TreeSet<>(plugin.engines().types())));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (!type.equals("auto") && plugin.engines().get(type) == null) {
                    messages.send(sender, "engine-unknown", "engine", args[1]);
                    return true;
                }
                plugin.setEngine(type);
                messages.send(sender, "engine-changed", "engine", type);
            }
            case "language" -> {
                if (!check(sender, "pvtranscribe.admin.engine")) return true;
                if (args.length < 2) {
                    messages.raw(sender, "&7Language: &f" + plugin.service().getEngineInfo().language()
                            + "&7. Usage: &f/pvt language <ru|en|de|...|auto>");
                    return true;
                }
                plugin.setLanguage(args[1]);
                messages.send(sender, "language-changed", "language", args[1].toLowerCase(Locale.ROOT));
            }
            case "world" -> {
                if (!check(sender, "pvtranscribe.admin.world")) return true;
                if (args.length < 3) {
                    messages.help(sender);
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) {
                    messages.send(sender, "world-not-found", "world", args[1]);
                    return true;
                }
                boolean enable = parseToggle(args[2]);
                plugin.setWorldEnabled(world.getName(), enable);
                messages.send(sender, enable ? "world-transcription-on" : "world-transcription-off", "world", world.getName());
            }
            default -> messages.help(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        TranscriptionService service = plugin.service();
        EngineInfo engine = service.getEngineInfo();
        TranscriptionService.Stats stats = service.stats();
        Messages m = plugin.messages();
        m.raw(sender, "&7Engine: &f" + engine.name() + " &7(" + engine.language() + ") &7state: &f" + engine.state());
        m.raw(sender, "&7Voice sources: &f" + (service.getVoiceSources().isEmpty() ? "&cnone" : String.join(", ", service.getVoiceSources())));
        m.raw(sender, "&7Speaking now: &f" + service.activeSpeakerCount()
                + " &7| worker threads: &f" + service.config().transcription().workerThreads());
        m.raw(sender, "&7Frames processed: &f" + stats.framesProcessed.get()
                + " &7dropped: &f" + stats.framesDropped.get()
                + " &7decode errors: &f" + stats.decodeErrors.get()
                + " &7engine errors: &f" + stats.engineErrors.get());
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private static boolean parseToggle(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        return s.equals("on") || s.equals("true") || s.equals("enable") || s.equals("вкл");
    }

    @SuppressWarnings("deprecation")
    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("pvtranscribe.command.toggle")) options.add("toggle");
            if (sender.hasPermission("pvtranscribe.admin.reload")) options.add("reload");
            if (sender.hasPermission("pvtranscribe.admin.status")) options.add("status");
            if (sender.hasPermission("pvtranscribe.admin.player")) options.add("player");
            if (sender.hasPermission("pvtranscribe.admin.world")) options.add("world");
            if (sender.hasPermission("pvtranscribe.admin.demo")) options.add("demo");
            if (sender.hasPermission("pvtranscribe.admin.config")) {
                options.add("get");
                options.add("set");
            }
            if (sender.hasPermission("pvtranscribe.admin.engine")) {
                options.add("engine");
                options.add("language");
            }
            options.add("help");
            return filter(options.stream(), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("player") && sender.hasPermission("pvtranscribe.admin.player")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("world") && sender.hasPermission("pvtranscribe.admin.world")) {
            return filter(Bukkit.getWorlds().stream().map(World::getName), args[1]);
        }
        if (sender.hasPermission("pvtranscribe.admin.config") && args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && (sub.equals("set") || sub.equals("get") || sub.equals("config"))) {
                return filter(ConfigEditor.paths(plugin.getConfig()).stream(), args[1]);
            }
            if (args.length == 3 && sub.equals("set")) {
                return filter(ConfigEditor.suggestions(plugin.getConfig(), args[1]).stream(), args[2]);
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("engine") && sender.hasPermission("pvtranscribe.admin.engine")) {
            List<String> types = new ArrayList<>(plugin.engines().types());
            types.add("auto");
            return filter(types.stream().sorted(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("language") && sender.hasPermission("pvtranscribe.admin.engine")) {
            return filter(Stream.of("auto", "ru", "en", "uk", "de", "fr", "es", "pl"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("world"))) {
            return filter(Stream.of("on", "off"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
