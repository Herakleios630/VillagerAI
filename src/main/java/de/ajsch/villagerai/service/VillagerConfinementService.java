package de.ajsch.villagerai.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class VillagerConfinementService {

    private final int stationaryRadiusBlocks;
    private final long stationaryMinMillis;
    private final long cantReachWalkTargetMinTicks;
    private final long staleSleepMinTicks;
    private final long staleWorkMinTicks;
    private final ConcurrentMap<UUID, Observation> observations = new ConcurrentHashMap<>();
    private final BukkitTask scanTask;

    public VillagerConfinementService(
            JavaPlugin plugin,
            long scanIntervalSeconds,
            int stationaryRadiusBlocks,
            long stationaryMinutes,
            long cantReachWalkTargetMinutes,
            long staleSleepMinutes,
            long staleWorkMinutes) {
        this.stationaryRadiusBlocks = Math.max(1, stationaryRadiusBlocks);
        this.stationaryMinMillis = Duration.ofMinutes(Math.max(1L, stationaryMinutes)).toMillis();
        this.cantReachWalkTargetMinTicks = Duration.ofMinutes(Math.max(1L, cantReachWalkTargetMinutes)).toSeconds() * 20L;
        this.staleSleepMinTicks = Duration.ofMinutes(Math.max(1L, staleSleepMinutes)).toSeconds() * 20L;
        this.staleWorkMinTicks = Duration.ofMinutes(Math.max(1L, staleWorkMinutes)).toSeconds() * 20L;

        long intervalTicks = Math.max(20L, scanIntervalSeconds * 20L);
        this.scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanLoadedVillagers, intervalTicks, intervalTicks);
    }

    public String describe(Villager villager) {
        List<String> clues = new ArrayList<>();
        long now = System.currentTimeMillis();
        World world = villager.getWorld();
        long worldTime = world.getFullTime();

        Observation observation = observations.get(villager.getUniqueId());
        if (observation != null && now - observation.stationarySinceMillis() >= stationaryMinMillis) {
            clues.add("haelt sich seit " + formatRealMinutes(now - observation.stationarySinceMillis())
                    + " nur in einem kleinen Radius von etwa " + stationaryRadiusBlocks + " Bloecken auf");
        }

        Long cantReachSince = villager.getMemory(MemoryKey.CANT_REACH_WALK_TARGET_SINCE);
        if (cantReachSince != null && worldTime >= cantReachSince && worldTime - cantReachSince >= cantReachWalkTargetMinTicks) {
            clues.add("kann seit " + formatMinecraftDuration(worldTime - cantReachSince) + " kein Walk-Target erreichen");
        }

        Long lastSlept = villager.getMemory(MemoryKey.LAST_SLEPT);
        if (villager.getMemory(MemoryKey.HOME) != null && lastSlept != null && worldTime >= lastSlept
                && worldTime - lastSlept >= staleSleepMinTicks) {
            clues.add("hat seit " + formatMinecraftDuration(worldTime - lastSlept) + " kein Bett erreicht");
        }

        Long lastWorked = villager.getMemory(MemoryKey.LAST_WORKED_AT_POI);
        if (villager.getMemory(MemoryKey.JOB_SITE) != null && lastWorked != null && worldTime >= lastWorked
                && worldTime - lastWorked >= staleWorkMinTicks) {
            clues.add("hat seit " + formatMinecraftDuration(worldTime - lastWorked) + " nicht an seiner Arbeitsstelle gearbeitet");
        }

        if (clues.isEmpty()) {
            return null;
        }

        return String.join("; ", clues);
    }

    public void shutdown() {
        scanTask.cancel();
        observations.clear();
    }

    private void scanLoadedVillagers() {
        long now = System.currentTimeMillis();
        Set<UUID> seenVillagers = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                seenVillagers.add(villager.getUniqueId());
                observe(villager, now);
            }
        }

        observations.entrySet().removeIf(entry -> !seenVillagers.contains(entry.getKey())
                && now - entry.getValue().lastSeenMillis() > stationaryMinMillis);
    }

    private void observe(Villager villager, long now) {
        Location location = villager.getLocation();
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        String worldName = villager.getWorld().getName();

        observations.compute(villager.getUniqueId(), (ignored, existing) -> {
            if (existing == null || !existing.worldName().equals(worldName)
                    || !isWithinRadius(existing, blockX, blockZ)) {
                return new Observation(worldName, blockX, blockZ, now, now);
            }

            return existing.withLastSeenMillis(now);
        });
    }

    private boolean isWithinRadius(Observation observation, int blockX, int blockZ) {
        return Math.abs(blockX - observation.anchorBlockX()) <= stationaryRadiusBlocks
                && Math.abs(blockZ - observation.anchorBlockZ()) <= stationaryRadiusBlocks;
    }

    private String formatRealMinutes(long millis) {
        long minutes = Math.max(1L, Math.round(millis / 60000.0D));
        return minutes + (minutes == 1L ? " Minute" : " Minuten");
    }

    private String formatMinecraftDuration(long ticks) {
        double days = ticks / 24000.0D;
        if (days >= 1.0D) {
            return String.format(java.util.Locale.ROOT, "%.1f Minecraft-Tagen", days);
        }

        long minutes = Math.max(1L, Math.round(ticks / 20.0D / 60.0D));
        return minutes + (minutes == 1L ? " Minute" : " Minuten");
    }

    private record Observation(
            String worldName,
            int anchorBlockX,
            int anchorBlockZ,
            long stationarySinceMillis,
            long lastSeenMillis) {

        private Observation withLastSeenMillis(long newLastSeenMillis) {
            return new Observation(worldName, anchorBlockX, anchorBlockZ, stationarySinceMillis, newLastSeenMillis);
        }
    }
}