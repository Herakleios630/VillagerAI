package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.storage.ChiefRepository;
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
import org.joml.Vector3f;

public final class ChiefVisualService implements Listener {

    private final VillageChiefPlugin plugin;
    private final ChiefRepository chiefRepository;
    private final Logger logger;

    private final Map<UUID, ItemDisplay> bannerDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, CrownEntry> crownEntries = new ConcurrentHashMap<>();
    private BukkitTask tickTask;

    // Configurable values (loaded from config.yml, defaults match current tuning)
    private double backOffset = 0.6;
    private double heightOffset = 0.9;
    private float bannerScale = 0.8f;
    private float tiltDegrees = 12f;
    private int interpolationTicks = 3;
    private int tickInterval = 3;

    public ChiefVisualService(VillageChiefPlugin plugin, ChiefRepository chiefRepository,
            Logger logger) {
        this.plugin = plugin;
        this.chiefRepository = chiefRepository;
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
        spawnBannerAttributes(attrs.entityUuid(), attrs.speakerId(), attrs.bannerPattern(), villager);
    }

    private void spawnBannerAttributes(UUID entityUuid, String speakerId, String bannerPattern, Villager villager) {
        if (bannerDisplays.containsKey(entityUuid)) {
            return;
        }

        ItemDisplay display = (ItemDisplay) villager.getWorld().spawnEntity(
                computeBackLocation(villager), EntityType.ITEM_DISPLAY);

        display.setItemStack(createBannerItem(bannerPattern));
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
        bannerDisplays.clear();
        crownEntries.clear();
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

    public static List<Pattern> buildBannerPatterns(String seed) {
        if (seed == null || seed.isBlank()) {
            return List.of();
        }

        int hash = seed.hashCode();
        int absHash = Math.abs(hash);
        DyeColor[] dyes = DyeColor.values();
        PatternType[] types = PatternType.values();

        int layerCount = 3 + (absHash % 4);
        List<Pattern> patterns = new ArrayList<>(layerCount);

        int workingHash = absHash;
        for (int i = 0; i < layerCount; i++) {
            DyeColor color = dyes[workingHash % dyes.length];
            workingHash = Math.abs(workingHash >> 4);
            PatternType type = types[workingHash % types.length];
            workingHash = Math.abs(workingHash >> 4);
            if (workingHash == 0) {
                workingHash = absHash + i * 31;
            }
            patterns.add(new Pattern(color, type));
        }

        return patterns;
    }

    private ItemStack createBannerItem(String bannerPattern) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            for (Pattern pattern : buildBannerPatterns(bannerPattern)) {
                meta.addPattern(pattern);
            }
            banner.setItemMeta(meta);
        }
        return banner;
    }

    private record CrownEntry(BukkitTask task, long startedAt) {}
}