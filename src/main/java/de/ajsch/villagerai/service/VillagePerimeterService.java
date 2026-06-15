package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.VillagePerimeter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

/**
 * Calculates a village perimeter based on the POIs (Meeting-Point, Home, Job-Site,
 * Potential-Job-Site) of all villagers with the same {@code villageId}.
 * The perimeter is a bounding box with an added margin, with a minimum size
 * fallback, and is cached with a configurable TTL.
 */
public final class VillagePerimeterService {

    private final int margin;
    private final int minimumSize;
    private final Duration cacheTtl;

    private final Map<String, CachedPerimeter> cache = new HashMap<>();

    public VillagePerimeterService(int margin, int minimumSize, Duration cacheTtl) {
        this.margin = Math.max(0, margin);
        this.minimumSize = Math.max(20, minimumSize);
        this.cacheTtl = cacheTtl;
    }

    /**
     * Compute (or retrieve cached) perimeter for all villagers that share the
     * same {@code villageId} as the reference villager.
     */
    public VillagePerimeter computePerimeter(Villager villager, String villageId, VillageIdentityService villageIdentityService) {
        Location anchor = villageIdentityService.resolveAnchor(villager);
        if (!hasWorld(anchor)) {
            return fallbackPerimeter(villageId, villager.getLocation());
        }

        CachedPerimeter cached = cache.get(villageId);
        if (cached != null && !cached.isExpired(cacheTtl.toMillis())) {
            return cached.perimeter;
        }

        List<Villager> villagersInSameVillage = collectVillageVillagers(anchor, villageId, villageIdentityService);
        if (villagersInSameVillage.isEmpty()) {
            VillagePerimeter fallback = fallbackPerimeter(villageId, villager.getLocation());
            cache.put(villageId, new CachedPerimeter(fallback, System.currentTimeMillis()));
            return fallback;
        }

        List<Location> pois = collectAllPois(villagersInSameVillage);
        if (pois.isEmpty()) {
            VillagePerimeter fallback = fallbackPerimeter(villageId, villager.getLocation());
            cache.put(villageId, new CachedPerimeter(fallback, System.currentTimeMillis()));
            return fallback;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Location poi : pois) {
            int x = poi.getBlockX();
            int z = poi.getBlockZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        // Add margin in each direction
        minX -= margin;
        minZ -= margin;
        maxX += margin;
        maxZ += margin;

        // Ensure minimum size
        int currentWidth = maxX - minX + 1;
        int currentDepth = maxZ - minZ + 1;
        if (currentWidth < minimumSize) {
            int expand = (minimumSize - currentWidth) / 2;
            minX -= expand;
            maxX += expand;
            // Handle odd diffs
            if ((maxX - minX + 1) < minimumSize) {
                maxX += 1;
            }
        }
        if (currentDepth < minimumSize) {
            int expand = (minimumSize - currentDepth) / 2;
            minZ -= expand;
            maxZ += expand;
            if ((maxZ - minZ + 1) < minimumSize) {
                maxZ += 1;
            }
        }

        VillagePerimeter perimeter = new VillagePerimeter(
                villageId,
                anchor.getWorld().getName(),
                minX,
                maxX,
                minZ,
                maxZ,
                villagersInSameVillage.size(),
                System.currentTimeMillis());
        cache.put(villageId, new CachedPerimeter(perimeter, System.currentTimeMillis()));
        return perimeter;
    }

    /**
     * Invalidate cache for a village, e.g. when a villager dies, changes jobs,
     * or a POI is removed.
     */
    public void invalidateCache(String villageId) {
        cache.remove(villageId);
    }

    /**
     * Invalidate cache for all villages.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Get the cached perimeter if present and not expired.
     */
    public Optional<VillagePerimeter> getCachedPerimeter(String villageId) {
        CachedPerimeter cached = cache.get(villageId);
        if (cached != null && !cached.isExpired(cacheTtl.toMillis())) {
            return Optional.of(cached.perimeter);
        }
        return Optional.empty();
    }

    public int cacheSize() {
        return cache.size();
    }

    // ---- internal helpers ----

    private List<Villager> collectVillageVillagers(Location anchor, String villageId, VillageIdentityService villageIdentityService) {
        List<Villager> villagers = new ArrayList<>();
        if (anchor.getWorld() == null) {
            return villagers;
        }

        Collection<org.bukkit.entity.Entity> nearbyEntities = anchor.getWorld().getNearbyEntities(
                anchor, VillageIdentityService.VILLAGE_RADIUS, VillageIdentityService.VILLAGE_RADIUS, VillageIdentityService.VILLAGE_RADIUS);
        for (org.bukkit.entity.Entity entity : nearbyEntities) {
            if (!(entity instanceof Villager nearbyVillager)) {
                continue;
            }
            String nearbyId = villageIdentityService.resolveOrRegisterVillageId(nearbyVillager);
            if (nearbyId != null && nearbyId.equals(villageId)) {
                villagers.add(nearbyVillager);
            }
        }
        return villagers;
    }

    private List<Location> collectAllPois(List<Villager> villagers) {
        List<Location> pois = new ArrayList<>();
        for (Villager villager : villagers) {
            addPoi(pois, villager.getMemory(MemoryKey.MEETING_POINT));
            addPoi(pois, villager.getMemory(MemoryKey.HOME));
            addPoi(pois, villager.getMemory(MemoryKey.JOB_SITE));
            addPoi(pois, villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE));
        }
        return pois;
    }

    private void addPoi(List<Location> pois, Location location) {
        if (hasWorld(location)) {
            pois.add(location);
        }
    }

    private VillagePerimeter fallbackPerimeter(String villageId, Location villagerLocation) {
        if (!hasWorld(villagerLocation)) {
            throw new IllegalArgumentException("Cannot create fallback perimeter – villager location has no world");
        }

        int cx = villagerLocation.getBlockX();
        int cz = villagerLocation.getBlockZ();
        int halfSize = minimumSize / 2;
        int minX = cx - halfSize;
        int maxX = cx + halfSize;
        int minZ = cz - halfSize;
        int maxZ = cz + halfSize;
        // Ensure exact minimumSize
        if ((maxX - minX + 1) < minimumSize) maxX += 1;
        if ((maxZ - minZ + 1) < minimumSize) maxZ += 1;

        return new VillagePerimeter(
                villageId,
                villagerLocation.getWorld().getName(),
                minX,
                maxX,
                minZ,
                maxZ,
                1,
                System.currentTimeMillis());
    }

    private boolean hasWorld(Location location) {
        return location != null && location.getWorld() != null;
    }

    private static final class CachedPerimeter {
        final VillagePerimeter perimeter;
        final long cachedAtEpochMillis;

        CachedPerimeter(VillagePerimeter perimeter, long cachedAtEpochMillis) {
            this.perimeter = perimeter;
            this.cachedAtEpochMillis = cachedAtEpochMillis;
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - cachedAtEpochMillis > ttlMillis;
        }
    }
}