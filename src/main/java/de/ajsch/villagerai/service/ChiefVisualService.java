package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.event.ReputationChangedEvent;
import de.ajsch.villagerai.model.BiomeFamily;
import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.ChiefVisualTier;
import de.ajsch.villagerai.model.ReputationScope;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.storage.SpeakerRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public final class ChiefVisualService implements Listener {

    private final VillageChiefPlugin plugin;
    private final ChiefRepository chiefRepository;
    private final SpeakerRepository speakerRepository;
    private final Logger logger;

    private final Map<UUID, ItemDisplay> bannerDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, CrownEntry> crownEntries = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> legendaryParticleTasks = new ConcurrentHashMap<>();
    private BukkitTask tickTask;

    // Configurable values (loaded from config.yml, defaults match current tuning)
    private double backOffset = 0.6;
    private double heightOffset = 0.9;
    private float bannerScale = 0.8f;
    private float tiltDegrees = 12f;
    private int interpolationTicks = 3;
    private int tickInterval = 3;

    public ChiefVisualService(VillageChiefPlugin plugin, ChiefRepository chiefRepository,
            SpeakerRepository speakerRepository, Logger logger) {
        this.plugin = plugin;
        this.chiefRepository = chiefRepository;
        this.speakerRepository = speakerRepository;
        this.logger = logger;
        reloadConfig();
    }

    /**
     * (Re)load visual parameters from {@code config.yml} and apply them to all
     * existing banner displays immediately – no server restart required.
     */
    public void reloadConfig() {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("visuals.chief-banner");
        if (section != null) {
            backOffset = section.getDouble("back-offset", backOffset);
            heightOffset = section.getDouble("height-offset", heightOffset);
            bannerScale = (float) section.getDouble("scale", bannerScale);
            tiltDegrees = (float) section.getDouble("tilt-degrees", tiltDegrees);
            interpolationTicks = section.getInt("interpolation-ticks", interpolationTicks);
            tickInterval = section.getInt("tick-interval", tickInterval);
        }

        // Apply to every already-spawned display
        for (ItemDisplay display : bannerDisplays.values()) {
            if (display == null || !display.isValid()) continue;
            applyTransformation(display);
            display.setInterpolationDuration(interpolationTicks);
        }

        // Restart tick task if interval changed
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        start();
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, tickInterval);
    }

    public void spawnBanner(ChiefAttributes attrs, Villager villager) {
        ChiefVisualTier tier = resolveTier(attrs.visualTier());
        spawnBannerAttributes(attrs.entityUuid(), attrs.speakerId(), attrs.bannerPattern(), tier, villager);
    }

    /**
     * Refreshes the banner for a chief whose visual tier changed.
     * Removes the old banner display and spawns a new one with the updated tier.
     */
    public void refreshBanner(ChiefAttributes attrs, Villager villager) {
        removeBanner(attrs.entityUuid());
        spawnBanner(attrs, villager);
    }

    private void spawnBannerAttributes(UUID entityUuid, String speakerId, String bannerPattern,
                                       ChiefVisualTier tier, Villager villager) {
        if (bannerDisplays.containsKey(entityUuid)) {
            return;
        }

        ItemDisplay display = (ItemDisplay) villager.getWorld().spawnEntity(
                computeBackLocation(villager), EntityType.ITEM_DISPLAY);

        display.setItemStack(createBannerItem(bannerPattern, tier));
        display.setBillboard(Display.Billboard.FIXED);
        display.setInterpolationDuration(interpolationTicks);
        display.setPersistent(false);
        display.setRotation(computeBodyYaw(villager), 0);
        applyTransformation(display);

        bannerDisplays.put(entityUuid, display);
        logger.fine("Banner spawned for chief " + speakerId + " (entity " + entityUuid + ")");
    }

    private void applyTransformation(ItemDisplay display) {
        float halfHeight = bannerScale * 0.5f;
        Transformation transform = display.getTransformation();
        transform.getLeftRotation().set(new Quaternionf()
                .rotateY((float) Math.toRadians(180))
                .rotateX((float) Math.toRadians(-tiltDegrees)));
        transform.getTranslation().set(0, halfHeight, 0);
        transform.getScale().set(bannerScale, bannerScale, bannerScale);
        display.setTransformation(transform);
    }

    public void removeBanner(UUID entityUuid) {
        ItemDisplay display = bannerDisplays.remove(entityUuid);
        if (display != null && display.isValid()) {
            display.remove();
            logger.fine("Banner removed for entity " + entityUuid);
        }
    }

    public void startCrownParticles(Villager villager) {
        UUID entityUuid = villager.getUniqueId();
        stopCrownParticles(entityUuid);

        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;
            private static final int MAX_TICKS = 20 * 60 * 20;

            @Override
            public void run() {
                if (!villager.isValid() || villager.isDead()) {
                    stopCrownParticles(entityUuid);
                    return;
                }
                if (tick >= MAX_TICKS) {
                    stopCrownParticles(entityUuid);
                    return;
                }
                World world = villager.getWorld();
                double x = villager.getLocation().getX();
                double y = villager.getLocation().getY();
                double z = villager.getLocation().getZ();
                Color gold = Color.fromRGB(255, 215, 0);
                world.spawnParticle(Particle.DUST, x, y + 2.3, z, 1, 0.3, 0.1, 0.3, 1.0,
                        new Particle.DustOptions(gold, 1.0f));
                if (tick % 5 == 0) {
                    Color white = Color.fromRGB(255, 255, 255);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 2.0, z, 1, 0.5, 0.2, 0.5, 2.0,
                            new Particle.DustTransition(gold, white, 2.0f));
                }
                tick++;
            }
        }, 0L, 1L);

        crownEntries.put(entityUuid, new CrownEntry(particleTask, System.currentTimeMillis()));
        logger.fine("Crown particles started for entity " + entityUuid);
    }

    public void stopCrownParticles(UUID entityUuid) {
        CrownEntry entry = crownEntries.remove(entityUuid);
        if (entry != null && entry.task != null) {
            entry.task.cancel();
        }
    }

    /**
     * Startet permanente Leucht-Partikel für einen Chief mit LEGENDARY-Tier.
     * Goldene Dust-Partikel + weiße END_ROD-Partikel, zufällig um den Kopf verteilt.
     */
    public void startLegendaryParticles(Villager villager) {
        UUID entityUuid = villager.getUniqueId();
        if (legendaryParticleTasks.containsKey(entityUuid)) {
            return; // bereits aktiv
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!villager.isValid() || villager.isDead()) {
                    stopLegendaryParticles(entityUuid);
                    return;
                }

                World world = villager.getWorld();
                Location base = villager.getLocation();
                double x = base.getX() + (Math.random() - 0.5) * 1.0;
                double y = base.getY() + 1.8 + (Math.random() * 0.7);
                double z = base.getZ() + (Math.random() - 0.5) * 1.0;

                // Goldener Dust-Partikel (jeden Lauf, alle 5 Ticks)
                Color gold = Color.fromRGB(255, 215, 0);
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 1.0,
                        new Particle.DustOptions(gold, 1.0f));

                // Weißer END_ROD-Partikel (alle 10 Ticks)
                if (tick % 2 == 0) {
                    double x2 = base.getX() + (Math.random() - 0.5) * 1.0;
                    double y2 = base.getY() + 1.8 + (Math.random() * 0.7);
                    double z2 = base.getZ() + (Math.random() - 0.5) * 1.0;
                    world.spawnParticle(Particle.END_ROD, x2, y2, z2, 1, 0, 0, 0, 0);
                }

                tick++;
            }
        }, 0L, 5L);

        legendaryParticleTasks.put(entityUuid, task);
        logger.fine("Legendary particles started for entity " + entityUuid);
    }

    public void stopLegendaryParticles(UUID entityUuid) {
        BukkitTask task = legendaryParticleTasks.remove(entityUuid);
        if (task != null) {
            task.cancel();
            logger.fine("Legendary particles stopped for entity " + entityUuid);
        }
    }

    public void restoreAllBanners(ChiefService chiefService) {
        int restored = 0;

        for (ChiefAttributes attrs : chiefRepository.findAll()) {
            if (!attrs.isActive()) continue;
            Entity entity = Bukkit.getEntity(attrs.entityUuid());
            if (entity instanceof Villager villager && villager.isValid() && !villager.isDead()) {
                spawnBanner(attrs, villager);
                restored++;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (bannerDisplays.containsKey(villager.getUniqueId())) continue;
                Optional<ChiefAttributes> attrs = chiefRepository.findByEntityUuid(villager.getUniqueId());
                if (attrs.isEmpty() || !attrs.get().isActive()) continue;
                spawnBanner(attrs.get(), villager);
                restored++;
            }
        }

        if (restored > 0) {
            logger.info("Restored " + restored + " chief banner(s) on server start.");
        }
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (UUID uuid : bannerDisplays.keySet()) {
            removeBanner(uuid);
        }
        for (UUID uuid : crownEntries.keySet()) {
            stopCrownParticles(uuid);
        }
        for (UUID uuid : legendaryParticleTasks.keySet()) {
            stopLegendaryParticles(uuid);
        }
        bannerDisplays.clear();
        crownEntries.clear();
        legendaryParticleTasks.clear();
        logger.fine("ChiefVisualService shut down.");
    }

    private void tick() {
        Iterator<Map.Entry<UUID, ItemDisplay>> it = bannerDisplays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ItemDisplay> entry = it.next();
            UUID uuid = entry.getKey();
            ItemDisplay display = entry.getValue();

            if (display == null || !display.isValid()) {
                it.remove();
                continue;
            }

            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Villager villager) || !villager.isValid() || villager.isDead()) {
                display.remove();
                it.remove();
                continue;
            }

            Location backLoc = computeBackLocation(villager);
            display.teleport(backLoc);
            display.setRotation(backLoc.getYaw(), 0);
        }
    }

    private Location computeBackLocation(Villager villager) {
        Location loc = villager.getBoundingBox().getCenter().toLocation(villager.getWorld());
        double bodyYaw = computeBodyYaw(villager);
        loc.setYaw((float) bodyYaw);
        loc.setPitch(0);
        Vector dir = loc.getDirection().normalize();
        Vector backVec = dir.clone().multiply(-backOffset);
        return loc.clone().add(backVec).add(0, heightOffset, 0);
    }

    private float computeBodyYaw(Villager villager) {
        return villager.getBodyYaw();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            removeBanner(villager.getUniqueId());
            stopCrownParticles(villager.getUniqueId());
            stopLegendaryParticles(villager.getUniqueId());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity ent : event.getChunk().getEntities()) {
            if (!(ent instanceof Villager villager) || !villager.isValid() || villager.isDead()) {
                continue;
            }
            Optional<ChiefAttributes> attrs = chiefRepository.findByEntityUuid(villager.getUniqueId());
            if (attrs.isPresent() && attrs.get().isActive()) {
                spawnBanner(attrs.get(), villager);
                logger.info("Banner-restored for chief " + attrs.get().speakerId()
                        + " on chunk load");
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity ent : event.getChunk().getEntities()) {
            if (ent instanceof Villager) {
                removeBanner(ent.getUniqueId());
                stopCrownParticles(ent.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onReputationChanged(ReputationChangedEvent event) {
        String speakerId = event.getSpeakerId();
        if (speakerId == null) {
            return;
        }

        Optional<Speaker> speakerOpt = speakerRepository.findBySpeakerId(speakerId);
        if (speakerOpt.isEmpty()) {
            return;
        }
        Speaker speaker = speakerOpt.get();

        Optional<ChiefAttributes> chiefOpt = chiefRepository.findByEntityUuid(speaker.entityUuid());
        if (chiefOpt.isEmpty() || !chiefOpt.get().isActive()) {
            return;
        }
        ChiefAttributes chief = chiefOpt.get();

        Entity entity = Bukkit.getEntity(chief.entityUuid());
        if (!(entity instanceof Villager villager) || !villager.isValid() || villager.isDead()) {
            return;
        }

        ChiefVisualTier newTier = ChiefVisualTier.fromReputation(
                event.getNewReputation(), chief.legendaryUnlocked());
        ChiefVisualTier oldTier = resolveTier(chief.visualTier());

        if (newTier != oldTier) {
            refreshBanner(chief, villager);

            ChiefAttributes updated = new ChiefAttributes(
                    chief.entityUuid(), chief.speakerId(), chief.villageId(),
                    chief.crownedAt(), chief.mournedAt(), chief.isActive(),
                    newTier.name(), chief.biomeStyle(), chief.bannerPattern(),
                    chief.legendaryUnlocked(), chief.legendaryLastActivated());
            chiefRepository.save(updated);

            World world = villager.getWorld();
            Location loc = villager.getLocation();
            for (int i = 0; i < 5; i++) {
                world.spawnParticle(Particle.HAPPY_VILLAGER,
                        loc.getX() + (Math.random() - 0.5) * 0.6,
                        loc.getY() + 2.0 + (Math.random() * 0.5),
                        loc.getZ() + (Math.random() - 0.5) * 0.6,
                        1, 0, 0, 0, 0);
            }

            logger.info(String.format(
                    "ChiefVisualService: tier changed for %s: %s -> %s (reputation %d)",
                    chief.speakerId(), oldTier.name(), newTier.name(),
                    event.getNewReputation()));
        }

        // Legendary-Partikel starten/stoppen basierend auf aktuellem Tier
        if (newTier == ChiefVisualTier.LEGENDARY && chief.legendaryUnlocked()) {
            startLegendaryParticles(villager);
        } else {
            stopLegendaryParticles(chief.entityUuid());
        }
    }

    // ── Tier-spezifische Konstanten ──

    private static final DyeColor[] COLORS_TIER0 = {DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY};
    private static final DyeColor[] COLORS_ACCENT = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW};
    private static final DyeColor[] COLORS_LEGENDARY = {DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.WHITE, DyeColor.RED};

    private static final PatternType[] PATTERNS_TIER0 = {
        PatternType.BASE, PatternType.STRIPE_BOTTOM, PatternType.CROSS
    };
    private static final PatternType[] PATTERNS_TIER1_EXTRA = {
        PatternType.STRIPE_LEFT, PatternType.TRIANGLE_BOTTOM, PatternType.HALF_VERTICAL
    };
    private static final PatternType[] PATTERNS_TIER2_EXTRA = {
        PatternType.CREEPER, PatternType.FLOWER, PatternType.SKULL
    };
    private static final PatternType[] PATTERNS_TIER3_EXTRA = {
        PatternType.GRADIENT, PatternType.CURLY_BORDER, PatternType.MOJANG
    };
    private static final PatternType[] PATTERNS_LEGENDARY = {
        PatternType.STRAIGHT_CROSS, PatternType.GRADIENT, PatternType.GLOBE,
        PatternType.RHOMBUS, PatternType.CREEPER, PatternType.SKULL
    };

    // ── Alte Signatur – delegiert an TIER_1 als Default ──

    /**
     * @deprecated Use {@link #buildBannerPatterns(String, ChiefVisualTier)} to make tier explicit.
     */
    @Deprecated
    public static List<Pattern> buildBannerPatterns(String seed) {
        return buildBannerPatterns(seed, ChiefVisualTier.TIER_1);
    }

    // ── Tier-abhängige Pattern-Generierung ──

    /**
     * Baut eine deterministische Banner-Pattern-Liste, abhängig von einem Seed-String
     * und dem visuellen Rang des Chiefs. Gleiche seed + gleicher tier = gleiches Banner.
     */
    public static List<Pattern> buildBannerPatterns(String seed, ChiefVisualTier tier) {
        if (seed == null || seed.isBlank()) {
            return List.of();
        }
        if (tier == null) {
            tier = ChiefVisualTier.TIER_1;
        }

        int hash = seed.hashCode();
        int absHash = Math.abs(hash);

        // Layer-Anzahl pro Tier
        int layerCount;
        switch (tier) {
            case TIER_0:     layerCount = absHash % 2 == 0 ? 2 : 3; break;
            case TIER_1:     layerCount = 3 + (absHash % 2); break;  // 3-4
            case TIER_2:     layerCount = 5; break;
            case TIER_3:     layerCount = 6; break;
            case LEGENDARY:  layerCount = 6; break;
            default:         layerCount = 3 + (absHash % 4); break;
        }

        // Farbpalette pro Tier
        DyeColor[] palette;
        switch (tier) {
            case TIER_0:
                palette = COLORS_TIER0;
                break;
            case TIER_1:
                palette = mergePalettes(COLORS_TIER0, pickAccent(absHash, 1));
                break;
            case TIER_2:
                palette = mergePalettes(COLORS_TIER0, pickAccent(absHash >> 4, 2));
                break;
            case TIER_3:
                palette = mergePalettes(COLORS_TIER0, pickAccent(absHash >> 8, 3));
                break;
            case LEGENDARY:
                palette = COLORS_LEGENDARY;
                break;
            default:
                palette = DyeColor.values();
        }

        // Pattern-Pool pro Tier
        PatternType[] pool;
        switch (tier) {
            case TIER_0:
                pool = PATTERNS_TIER0;
                break;
            case TIER_1:
                pool = mergePatterns(PATTERNS_TIER0, PATTERNS_TIER1_EXTRA);
                break;
            case TIER_2:
                pool = mergePatterns(
                        mergePatterns(PATTERNS_TIER0, PATTERNS_TIER1_EXTRA),
                        PATTERNS_TIER2_EXTRA);
                break;
            case TIER_3:
                pool = mergePatterns(
                        mergePatterns(
                                mergePatterns(PATTERNS_TIER0, PATTERNS_TIER1_EXTRA),
                                PATTERNS_TIER2_EXTRA),
                        PATTERNS_TIER3_EXTRA);
                break;
            case LEGENDARY:
                pool = PATTERNS_LEGENDARY;
                break;
            default:
                pool = PatternType.values();
        }

        List<Pattern> patterns = new ArrayList<>(layerCount);
        int workingHash = absHash;
        for (int i = 0; i < layerCount; i++) {
            DyeColor color = palette[workingHash % palette.length];
            workingHash = Math.abs(workingHash >> 4);
            PatternType type = pool[workingHash % pool.length];
            workingHash = Math.abs(workingHash >> 4);
            if (workingHash == 0) {
                workingHash = absHash + i * 31;
            }
            patterns.add(new Pattern(color, type));
        }

        return patterns;
    }

    // ── Hilfsmethoden für Paletten/Pool-Kombination ──

    private static DyeColor[] mergePalettes(DyeColor[] base, DyeColor[] accent) {
        DyeColor[] merged = new DyeColor[base.length + accent.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(accent, 0, merged, base.length, accent.length);
        return merged;
    }

    private static DyeColor[] pickAccent(int hash, int count) {
        DyeColor[] result = new DyeColor[count];
        int h = Math.abs(hash);
        for (int i = 0; i < count; i++) {
            result[i] = COLORS_ACCENT[h % COLORS_ACCENT.length];
            h = Math.abs(h >> 4);
            if (h == 0) h = Math.abs(hash) + i * 7;
        }
        return result;
    }

    private static PatternType[] mergePatterns(PatternType[] a, PatternType[] b) {
        PatternType[] merged = new PatternType[a.length + b.length];
        System.arraycopy(a, 0, merged, 0, a.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }

    // ── Banner-Item-Erzeugung (tier-aware) ──

    private ItemStack createBannerItem(String bannerPattern, ChiefVisualTier tier) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            for (Pattern pattern : buildBannerPatterns(bannerPattern, tier)) {
                meta.addPattern(pattern);
            }
            banner.setItemMeta(meta);
        }
        return banner;
    }

/**
     * Liefert die abstrakte Brustplatten-Farbe für einen Chief, basierend auf
     * seinem aktuellen {@link ChiefVisualTier}. Kein ItemDisplay wird gespawned;
     * die Farbe ist für eine spätere Client-Mod vorgesehen.
     *
     * @param attrs die Chief-Attribute (darf nicht null sein)
     * @return die Farbe gemäß visueller Stufe, oder null bei unbekanntem Tier
     */
    public static Color getChestplateColor(ChiefAttributes attrs) {
        if (attrs == null) return null;
        ChiefVisualTier tier = resolveTier(attrs.visualTier());
        return tier.getChestplateColor();
    }

    /**
     * Mischt die Tier-Brustplattenfarbe (70%) mit der Biom-Primärfarbe (30%).
     * Reine Datenfunktion – kein Rendering, kein ItemDisplay.
     * Dient als API-Einstiegspunkt für eine spätere Client-Mod.
     *
     * @param attrs die Chief-Attribute (darf nicht null sein, muss biomeStyle haben)
     * @return die gemischte Brustplatten-Farbe, oder null falls attrs null ist,
     *         oder reine Tier-Farbe falls biomeStyle nicht auflösbar ist
     */
    public static Color getBlendedChestplateColor(ChiefAttributes attrs) {
        if (attrs == null) return null;
        ChiefVisualTier tier = resolveTier(attrs.visualTier());
        Color tierColor = tier.getChestplateColor();
        if (tierColor == null) return null;

        BiomeFamily family = null;
        if (attrs.biomeStyle() != null && !attrs.biomeStyle().isBlank()) {
            try {
                family = BiomeFamily.valueOf(attrs.biomeStyle());
            } catch (IllegalArgumentException ignored) {
                // Fall through
            }
        }
        if (family == null) {
            return tierColor; // kein Biome-Style vorhanden oder unbekannt → reine Tier-Farbe
        }

        Color biomeColor = family.getPrimaryBukkitColor();
        return blendColors(tierColor, biomeColor, 0.3f);
    }

    /**
     * Mischt zwei Bukkit-Farben im RGB-Raum.
     * @param factor Gewichtung der zweiten Farbe (0.0 = nur base, 1.0 = nur blend)
     */
    private static Color blendColors(Color base, Color blend, float factor) {
        int r = Math.round(base.getRed()   * (1f - factor) + blend.getRed()   * factor);
        int g = Math.round(base.getGreen() * (1f - factor) + blend.getGreen() * factor);
        int b = Math.round(base.getBlue()  * (1f - factor) + blend.getBlue()  * factor);
        return Color.fromRGB(
                Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)),
                Math.min(255, Math.max(0, b)));
    }

    /**
     * Resolves a visual tier string (from ChiefAttributes or config)
     * to a {@link ChiefVisualTier}, defaulting to TIER_1.
     */
    public static ChiefVisualTier resolveTier(String visualTierStr) {
        if (visualTierStr != null && !visualTierStr.isBlank()) {
            try {
                return ChiefVisualTier.valueOf(visualTierStr);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return ChiefVisualTier.TIER_1;
    }

    private record CrownEntry(BukkitTask task, long startedAt) {}
}