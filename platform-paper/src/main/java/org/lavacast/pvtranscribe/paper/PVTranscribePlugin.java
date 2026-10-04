package org.lavacast.pvtranscribe.paper;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.lavacast.pvtranscribe.api.PVTranscribe;
import org.lavacast.pvtranscribe.api.PVTranscribeApi;
import org.lavacast.pvtranscribe.core.config.TranscribeConfig;
import org.lavacast.pvtranscribe.core.engine.EngineRegistry;
import org.lavacast.pvtranscribe.core.session.TranscriptionService;
import org.lavacast.pvtranscribe.engine.cloud.DeepgramEngineFactory;
import org.lavacast.pvtranscribe.engine.cloud.OpenAiEngineFactory;
import org.lavacast.pvtranscribe.engine.tone.ToneEngineFactory;
import org.lavacast.pvtranscribe.engine.vosk.VoskEngineFactory;
import org.lavacast.pvtranscribe.paper.config.BukkitConfigSection;
import org.lavacast.pvtranscribe.paper.config.Messages;

import org.bukkit.entity.Player;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.core.util.TextUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class PVTranscribePlugin extends JavaPlugin {

    private volatile TranscribeConfig config;
    private Messages messages;
    private PlayerData playerData;
    private BukkitPlatform platform;
    private TranscriptionService service;
    private SubtitleRenderer renderer;
    private EngineRegistry engines;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        config = TranscribeConfig.parse(new BukkitConfigSection(getConfig()));
        messages = loadMessages();

        playerData = new PlayerData(new File(getDataFolder(), "players.yml"), getLogger());
        playerData.load();
        platform = new BukkitPlatform(this, playerData);

        engines = new EngineRegistry();
        engines.register(VoskEngineFactory.TYPE, new VoskEngineFactory());
        engines.register(ToneEngineFactory.TYPE, new ToneEngineFactory());
        engines.register(OpenAiEngineFactory.TYPE, new OpenAiEngineFactory());
        engines.register(DeepgramEngineFactory.TYPE, new DeepgramEngineFactory());

        service = new TranscriptionService(platform, engines, config);
        platform.refreshAll();
        service.start();
        service.addListener(new BukkitEventBridge(), platform.syncExecutor());

        PVTranscribe.register(service);
        getServer().getServicesManager().register(PVTranscribeApi.class, service, this, ServicePriority.Normal);

        renderer = new SubtitleRenderer(this, service, platform);
        renderer.cleanupOrphans();
        service.setFinalPhraseHandler(this::relayToChat);
        getServer().getPluginManager().registerEvents(new PlayerListener(service, platform, renderer), this);
        getServer().getScheduler().runTaskTimer(this, renderer::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, platform::refreshAll, 10L, 10L);

        PVTranscribeCommand command = new PVTranscribeCommand(this);
        PluginCommand pluginCommand = getCommand("pvtranscribe");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        hookVoiceChats();
    }

    @Override
    public void onDisable() {
        if (renderer != null) renderer.removeAll();
        if (service != null) {
            PVTranscribe.unregister(service);
            service.shutdown();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    private void hookVoiceChats() {
        Plugin plasmoVoice = getServer().getPluginManager().getPlugin("PlasmoVoice");
        if (plasmoVoice == null) {
            getLogger().severe("Plasmo Voice is not installed. Install Plasmo Voice 2.1 or newer "
                    + "(https://modrinth.com/plugin/plasmo-voice). PV-Transcribe stays loaded, but no speech will be transcribed.");
            return;
        }
        if (!plasmoVoice.isEnabled()) {
            getLogger().severe("Plasmo Voice is installed but failed to enable; speech will not be transcribed.");
            return;
        }
        try {
            PlasmoVoiceHook.register(service, platform.logger());
        } catch (LinkageError e) {
            getLogger().log(Level.SEVERE, "Plasmo Voice " + plasmoVoice.getDescription().getVersion()
                    + " is not supported by this PV-Transcribe build (API mismatch). Use Plasmo Voice 2.1 or newer.", e);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to connect to Plasmo Voice", t);
        }
    }

    /**
     * Copies a finished phrase into the chat of everyone who could hear it, so it can be read later.
     */
    private void relayToChat(Transcript transcript, String text) {
        TranscribeConfig.Chat chat = config.chat();
        if (!chat.enabled()) return;
        Player speaker = getServer().getPlayer(transcript.getSpeakerId());
        if (speaker == null) return;

        Set<UUID> recipients = renderer.viewersOf(speaker, service.subtitles().audienceOf(speaker.getUniqueId()),
                config.subtitles(), new HashMap<>());
        if (chat.sendToSelf()) recipients.add(speaker.getUniqueId());
        else recipients.remove(speaker.getUniqueId());

        String body = TextUtil.capitalize(TextUtil.stripFormatting(text));
        String message = TextUtil.colorize(chat.format())
                .replace("{name}", speaker.getName())
                .replace("{text}", body);
        for (UUID id : recipients) {
            Player player = getServer().getPlayer(id);
            if (player != null) player.sendMessage(message);
        }
    }

    void reloadPlugin() {
        reloadConfig();
        config = TranscribeConfig.parse(new BukkitConfigSection(getConfig()));
        messages = loadMessages();
        playerData.load();
        service.reload(config);
        platform.refreshAll();
        renderer.removeAll(); // re-create displays with the new look
    }

    EngineRegistry engines() {
        return engines;
    }

    /**
     * Switches the speech engine ("auto", "t-one", "vosk", "openai", "deepgram"), saves it to config.yml and reloads.
     */
    void setEngine(String type) {
        getConfig().set("engine.type", type);
        saveConfig();
        reloadPlugin();
    }

    /**
     * Sets the recognition language, or "auto" for automatic detection (if the engine supports it).
     */
    void setLanguage(String language) {
        if (language.equalsIgnoreCase("auto")) {
            getConfig().set("transcription.auto-detect-language", true);
        } else {
            getConfig().set("transcription.language", language.toLowerCase(java.util.Locale.ROOT));
            getConfig().set("transcription.auto-detect-language", false);
        }
        saveConfig();
        reloadPlugin();
    }

    /**
     * Sets any config.yml option (from /get|set commands), saves the file and reloads.
     */
    void setOption(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        reloadPlugin();
    }

    void setWorldEnabled(String world, boolean enabled) {
        List<String> disabled = new ArrayList<>(getConfig().getStringList("transcription.disabled-worlds"));
        disabled.removeIf(w -> w.equalsIgnoreCase(world));
        if (!enabled) disabled.add(world);
        getConfig().set("transcription.disabled-worlds", disabled);
        saveConfig();
        reloadPlugin();
    }

    /**
     * Messages come from lang/<locale>.yml in the plugin folder (created from the built-in files and
     * editable); missing keys fall back to the built-in English text.
     */
    private Messages loadMessages() {
        for (String locale : new String[]{"en", "ru"}) {
            if (!new File(getDataFolder(), "lang/" + locale + ".yml").exists()) saveResource("lang/" + locale + ".yml", false);
        }
        String locale = getConfig().getString("locale", "en").toLowerCase(java.util.Locale.ROOT);
        File file = new File(getDataFolder(), "lang/" + locale + ".yml");
        if (!file.exists()) {
            getLogger().warning("Message file lang/" + locale + ".yml not found, using English.");
            file = new File(getDataFolder(), "lang/en.yml");
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        java.io.InputStream builtIn = getResource("lang/en.yml");
        if (builtIn != null) {
            yaml.setDefaults(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(builtIn, java.nio.charset.StandardCharsets.UTF_8)));
        }
        yaml.options().copyDefaults(true);
        return new Messages(yaml);
    }

    TranscribeConfig config() {
        return config;
    }

    Messages messages() {
        return messages;
    }

    PlayerData playerData() {
        return playerData;
    }

    BukkitPlatform platform() {
        return platform;
    }

    TranscriptionService service() {
        return service;
    }
}
