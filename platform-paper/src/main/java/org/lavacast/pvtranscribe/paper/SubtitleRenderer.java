package org.lavacast.pvtranscribe.paper;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.lavacast.pvtranscribe.core.config.TranscribeConfig;
import org.lavacast.pvtranscribe.core.session.TranscriptionService;
import org.lavacast.pvtranscribe.core.subtitle.SubtitleView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shows subtitles as {@link TextDisplay} entities above speakers. Runs every tick on the main thread
 * and only reads the thread-safe subtitle state prepared by worker threads.
 * <p>
 * Each display is hidden by default and shown only to the players allowed to see it, so the visibility
 * follows the voice chat (distance, groups, broadcast). Displays are never saved to the world
 * (non-persistent), follow the speaker every tick and are removed as soon as they are not needed.
 */
final class SubtitleRenderer {

    static final String ENTITY_TAG = "pvtranscribe_subtitle";
    private static final int VIEWER_SYNC_INTERVAL_TICKS = 4;
    /** Moves longer than this (teleports) respawn the display instead of sliding it across the map. */
    private static final double MAX_SLIDE_DISTANCE_SQ = 8 * 8;
    /** Height of one text line of a text display at scale 1 (10 px * 0.025 blocks). */
    private static final double LINE_HEIGHT = 0.25;
    private static final double BUBBLE_PADDING = 0.05;

    private final PVTranscribePlugin plugin;
    private final TranscriptionService service;
    private final BukkitPlatform platform;
    private final Map<UUID, Holder> holders = new HashMap<>();
    private int tick;
    private boolean teleportDurationSupported = true;

    SubtitleRenderer(PVTranscribePlugin plugin, TranscriptionService service, BukkitPlatform platform) {
        this.plugin = plugin;
        this.service = service;
        this.platform = platform;
    }

    void tick() {
        tick++;
        TranscribeConfig.Subtitles cfg = service.config().subtitles();
        if (!cfg.enabled()) {
            removeAll();
            return;
        }

        List<SubtitleView> views = service.subtitles().collect(System.currentTimeMillis());
        Set<UUID> active = new HashSet<>();
        Map<UUID, Boolean> voiceClients = new HashMap<>();

        for (SubtitleView view : views) {
            Player speaker = Bukkit.getPlayer(view.speakerId());
            if (speaker == null || !speaker.isOnline() || speaker.isDead()) continue;
            if (cfg.hideWhenSpeakerInvisible() && isHidden(speaker)) continue;

            active.add(view.speakerId());
            Location anchor = anchor(speaker, cfg);
            Holder holder = holders.computeIfAbsent(view.speakerId(), id -> new Holder());
            if (holder.anchor != null && (holder.anchor.getWorld() != anchor.getWorld()
                    || holder.anchor.distanceSquared(anchor) > MAX_SLIDE_DISTANCE_SQ)) {
                destroy(holder); // teleported: respawn instead of sliding across the map
            }
            boolean moved = holder.anchor == null || holder.anchor.distanceSquared(anchor) > 0.0004;
            holder.anchor = anchor;

            // stack bubbles: the newest right above the head, older ones above it
            double offset = 0;
            Set<String> keys = new HashSet<>();
            boolean spawned = false;
            for (SubtitleView.Bubble bubble : view.bubbles()) {
                keys.add(bubble.key());
                Location target = anchor.clone().add(0, offset, 0);
                BubbleHolder b = holder.bubbles.get(bubble.key());
                if (b == null || !b.display.isValid()) {
                    b = new BubbleHolder(spawn(target, cfg));
                    holder.bubbles.put(bubble.key(), b);
                    spawned = true;
                } else if (moved || b.offset != offset) {
                    b.display.teleport(target);
                }
                b.offset = offset;
                if (b.version != bubble.version()) {
                    b.display.setText(bubble.text());
                    b.version = bubble.version();
                }
                applyOpacity(b, bubble.opacity(), cfg);
                offset += (bubble.lines() * LINE_HEIGHT + BUBBLE_PADDING) * cfg.scale() + cfg.bubbleSpacing();
            }
            for (Iterator<Map.Entry<String, BubbleHolder>> it = holder.bubbles.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, BubbleHolder> e = it.next();
                if (!keys.contains(e.getKey())) {
                    if (e.getValue().display.isValid()) e.getValue().display.remove();
                    it.remove();
                }
            }

            if (cfg.bubbleTail()) {
                Location tailAt = tailAnchor(anchor, cfg);
                if (holder.tail == null || !holder.tail.isValid()) {
                    holder.tail = spawnTail(tailAt, cfg);
                    spawned = true;
                } else if (moved) {
                    holder.tail.teleport(tailAt);
                }
                float newest = view.bubbles().get(0).opacity();
                int alpha = Math.round(((cfg.tailArgb() >>> 24) & 0xFF) * newest);
                if (alpha != holder.tailAlpha) {
                    holder.tailAlpha = alpha;
                    holder.tail.setTextOpacity((byte) Math.max(26, alpha));
                }
            }

            if (spawned) {
                // new entities are hidden by default: show them to everyone who already sees this speaker
                for (UUID id : holder.viewers) {
                    Player viewer = Bukkit.getPlayer(id);
                    if (viewer != null) holder.showTo(plugin, viewer);
                }
            }
            if (spawned || tick % VIEWER_SYNC_INTERVAL_TICKS == 0) {
                syncViewers(holder, speaker, view.audience(), cfg, voiceClients);
            }
        }

        for (Iterator<Map.Entry<UUID, Holder>> it = holders.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Holder> entry = it.next();
            if (!active.contains(entry.getKey())) {
                destroy(entry.getValue());
                it.remove();
            }
        }
    }

    void remove(UUID speakerId) {
        Holder holder = holders.remove(speakerId);
        if (holder != null) destroy(holder);
    }

    /**
     * A viewer left the server or the world: hide everything from them, so the next sync starts clean.
     */
    void forgetViewer(Player viewer) {
        for (Holder holder : holders.values()) {
            if (holder.viewers.remove(viewer.getUniqueId()) && viewer.isOnline()) {
                holder.hideFrom(plugin, viewer);
            }
        }
    }

    void removeAll() {
        for (Holder holder : holders.values()) destroy(holder);
        holders.clear();
    }

    /**
     * Removes subtitle entities left over from a crash or a forced shutdown.
     */
    void cleanupOrphans() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) plugin.getLogger().info("Removed " + removed + " leftover subtitle entities.");
    }

    // ------------------------------------------------------------------ internals

    private void syncViewers(Holder holder, Player speaker, SubtitleView.AudienceSnapshot audience,
                             TranscribeConfig.Subtitles cfg, Map<UUID, Boolean> voiceClients) {
        Set<UUID> target = viewersOf(speaker, audience, cfg, voiceClients);
        for (Iterator<UUID> it = holder.viewers.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!target.contains(id)) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) holder.hideFrom(plugin, viewer);
                it.remove();
            }
        }
        for (UUID id : target) {
            if (holder.viewers.add(id)) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) holder.showTo(plugin, viewer);
            }
        }
    }

    /**
     * Players allowed to see (and read in chat) what the speaker says: follows the voice chat audience.
     */
    Set<UUID> viewersOf(Player speaker, SubtitleView.AudienceSnapshot audience, TranscribeConfig.Subtitles cfg,
                        Map<UUID, Boolean> voiceClients) {
        Set<UUID> target = new HashSet<>();
        Location origin = speaker.getLocation();
        double maxSq = cfg.maxDistance() * cfg.maxDistance();
        double fallbackSq = cfg.fallbackDistance() * cfg.fallbackDistance();

        for (Player viewer : speaker.getWorld().getPlayers()) {
            UUID id = viewer.getUniqueId();
            if (!platform.canSee(id)) continue;
            if (viewer == speaker) {
                if (cfg.showToSelf()) target.add(id);
                continue;
            }
            double distSq = viewer.getLocation().distanceSquared(origin);
            if (distSq > maxSq) continue;

            boolean visible = switch (cfg.visibilityMode()) {
                case WORLD -> true;
                case DISTANCE -> distSq <= fallbackSq;
                case VOICE_CHAT -> {
                    if (!audience.known()) {
                        yield distSq <= fallbackSq;
                    }
                    if (audience.listeners().contains(id)) {
                        yield true;
                    }
                    // Players without the voice mod cannot hear anyone; let them read proximity speech
                    // if they stand within the voice distance.
                    yield cfg.includePlayersWithoutVoiceMod()
                            && audience.proximityRadius() > 0
                            && distSq <= audience.proximityRadius() * audience.proximityRadius()
                            && !voiceClients.computeIfAbsent(id, service::hasVoiceClient);
                }
            };
            if (visible) target.add(id);
        }
        return target;
    }

    private TextDisplay spawn(Location location, TranscribeConfig.Subtitles cfg) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        // all of this happens in the same tick, before the entity is sent to any client
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.addScoreboardTag(ENTITY_TAG);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(switch (cfg.alignment()) {
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
            default -> TextDisplay.TextAlignment.CENTER;
        });
        display.setLineWidth(1000); // we wrap lines ourselves
        display.setShadowed(cfg.textShadow());
        display.setSeeThrough(cfg.seeThrough());
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(cfg.backgroundArgb()));
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange((float) Math.max(0.5, cfg.maxDistance() / 64.0));
        float scale = (float) cfg.scale();
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f()));
        if (teleportDurationSupported) {
            try {
                // client-side interpolation between our per-tick moves (1.20.2+)
                display.setTeleportDuration(3);
            } catch (NoSuchMethodError e) {
                teleportDurationSupported = false;
            }
        }
        return display;
    }

    /**
     * The little triangle under the bubble that points at the speaker: a second text display with a "▼"
     * glyph in the bubble's background color.
     */
    private TextDisplay spawnTail(Location location, TranscribeConfig.Subtitles cfg) {
        TextDisplay tail = location.getWorld().spawn(location, TextDisplay.class);
        tail.setVisibleByDefault(false);
        tail.setPersistent(false);
        tail.addScoreboardTag(ENTITY_TAG);
        tail.setBillboard(Display.Billboard.CENTER);
        tail.setShadowed(false);
        tail.setSeeThrough(cfg.seeThrough());
        tail.setDefaultBackground(false);
        tail.setBackgroundColor(Color.fromARGB(0));
        tail.setBrightness(new Display.Brightness(15, 15));
        tail.setViewRange((float) Math.max(0.5, cfg.maxDistance() / 64.0));
        float scale = (float) cfg.scale();
        tail.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f()));
        tail.setText(tailText(cfg.tailArgb(), cfg.tailSymbol()));
        int alpha = (cfg.tailArgb() >>> 24) & 0xFF;
        tail.setTextOpacity((byte) Math.max(26, alpha));
        if (teleportDurationSupported) {
            try {
                tail.setTeleportDuration(3);
            } catch (NoSuchMethodError e) {
                teleportDurationSupported = false;
            }
        }
        return tail;
    }

    private static String tailText(int argb, String symbol) {
        String hex = String.format("%06x", argb & 0xFFFFFF);
        StringBuilder color = new StringBuilder("§x");
        for (char c : hex.toCharArray()) color.append('§').append(c);
        return color + symbol;
    }

    private static Location tailAnchor(Location bubbleAnchor, TranscribeConfig.Subtitles cfg) {
        // text displays grow upwards from their position, so the tail sits just below the bubble
        return bubbleAnchor.clone().add(0, -0.19 * cfg.scale(), 0);
    }

    private void applyOpacity(BubbleHolder b, float opacity, TranscribeConfig.Subtitles cfg) {
        int step = Math.round(opacity * 20); // 5% steps are smooth enough and avoid needless updates
        if (step == b.opacityStep) return;
        b.opacityStep = step;
        float f = step / 20f;
        // the client renders text with alpha below ~26 as fully opaque, so stop above that
        int textAlpha = Math.max(26, Math.round(255 * f));
        b.display.setTextOpacity((byte) textAlpha);
        int bg = cfg.backgroundArgb();
        int bgAlpha = Math.round(((bg >>> 24) & 0xFF) * f);
        b.display.setBackgroundColor(Color.fromARGB((bgAlpha << 24) | (bg & 0xFFFFFF)));
    }

    private static Location anchor(Player speaker, TranscribeConfig.Subtitles cfg) {
        Location location = speaker.getLocation();
        location.add(0, speaker.getHeight() + cfg.height(), 0);
        location.setYaw(0);
        location.setPitch(0);
        return location;
    }

    private static boolean isHidden(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR
                || player.isInvisible()
                || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void destroy(Holder holder) {
        for (BubbleHolder b : holder.bubbles.values()) {
            if (b.display.isValid()) b.display.remove();
        }
        holder.bubbles.clear();
        if (holder.tail != null && holder.tail.isValid()) holder.tail.remove();
        holder.tail = null;
        holder.tailAlpha = -1;
        holder.anchor = null;
        holder.viewers.clear();
    }

    /** All bubbles and the tail of one speaker, shown to the same viewers. */
    private static final class Holder {
        final Map<String, BubbleHolder> bubbles = new HashMap<>();
        final Set<UUID> viewers = new HashSet<>();
        TextDisplay tail;
        int tailAlpha = -1;
        Location anchor;

        void showTo(org.bukkit.plugin.Plugin plugin, Player viewer) {
            for (BubbleHolder b : bubbles.values()) viewer.showEntity(plugin, b.display);
            if (tail != null) viewer.showEntity(plugin, tail);
        }

        void hideFrom(org.bukkit.plugin.Plugin plugin, Player viewer) {
            for (BubbleHolder b : bubbles.values()) viewer.hideEntity(plugin, b.display);
            if (tail != null) viewer.hideEntity(plugin, tail);
        }
    }

    private static final class BubbleHolder {
        final TextDisplay display;
        long version = -1;
        int opacityStep = 20;
        double offset;

        BubbleHolder(TextDisplay display) {
            this.display = display;
        }
    }
}
