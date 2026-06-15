package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.VillagePerimeter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Scans the entire village perimeter once and caches a {@code List<BlockPos>}
 * of all dark spawnable surface blocks. Used only during the quest OFFER phase
 * – not on every {@code BlockPlaceEvent} / {@code BlockBreakEvent}.
 *
 * <h3>What is considered "dark"?</h3>
 * <ul>
 *   <li>Surface only – highest solid block with air directly above; no caves.</li>
 *   <li>Solid, opaque block ({@link Material#isSolid()} and {@link Material#isOccluding()}).</li>
 *   <li>Block-Light ({@link Block#getLightFromBlocks()}) == 0; Sky-Light is ignored
 *       because it changes with the day/night cycle and would hide dark blocks during daytime.</li>
 *   <li>No water, lava, glass, slabs, stairs, carpets, leaves, ice, beds.</li>
 * </ul>
 *
 * <h3>Caching</h3>
 * The dark-block list is cached per {@code villageId} with a configurable TTL
 * (default 30 s). After expiry the next query triggers a fresh perimeter scan.
 */
public final class DarkBlockCache {

    private final Duration ttl;
    private final Random random = new Random();
    private final Map<String, CachedDarkList> cache = new ConcurrentHashMap<>();
    private final Logger logger = Logger.getLogger(DarkBlockCache.class.getName());
    private volatile boolean debugLogging = false;

    public DarkBlockCache(Duration ttl) {
        this.ttl = ttl;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    private void debug(String msg, Object... args) {
        if (debugLogging) {
            logger.log(Level.INFO, String.format("[DarkBlockCache] " + msg, args));
        }
    }

    // ---- public API ----

    /**
     * Returns the (possibly cached) list of dark block positions within the given perimeter.
     * If the cached entry is expired or missing, a fresh full-perimeter scan is performed.
     */
    public List<BlockPos> getDarkBlocks(VillagePerimeter perimeter) {
        CachedDarkList cached = cache.get(perimeter.villageId());
        if (cached != null && !cached.isExpired(ttl.toMillis())) {
            return cached.positions;
        }

        List<BlockPos> darkBlocks = scanPerimeter(perimeter);
        cache.put(perimeter.villageId(), new CachedDarkList(darkBlocks, System.currentTimeMillis()));
        return darkBlocks;
    }

    /**
     * Counts how many dark blocks fall inside a square sub-area defined by its
     * center (x, z) and edge length (e.g. 20 for 20×20).
     */
    public int darkCountInSubArea(VillagePerimeter perimeter, int centerX, int centerZ, int subAreaSize) {
        List<BlockPos> darkBlocks = getDarkBlocks(perimeter);
        int half = subAreaSize / 2;
        int minX = centerX - half;
        int maxX = centerX + half - 1;
        int minZ = centerZ - half;
        int maxZ = centerZ + half - 1;

        int count = 0;
        for (BlockPos pos : darkBlocks) {
            if (pos.x >= minX && pos.x <= maxX && pos.z >= minZ && pos.z <= maxZ) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether the perimeter contains at least one sub-area with
     * {@code darkCount >= minDarkBlocks} at the given size.
     */
    public boolean hasValidSubArea(VillagePerimeter perimeter, int subAreaSize, int minDarkBlocks) {
        return pickRandomSubArea(perimeter, subAreaSize, minDarkBlocks).isPresent();
    }

    /**
     * Picks a random valid sub-area center within the perimeter.
     * A sub-area is valid if it contains at least {@code minDarkBlocks} dark positions.
     * Returns {@link Optional#empty()} when no such sub-area exists.
     */
    public Optional<SubAreaResult> pickRandomSubArea(VillagePerimeter perimeter, int subAreaSize, int minDarkBlocks) {
        List<BlockPos> darkBlocks = getDarkBlocks(perimeter);
        if (darkBlocks.isEmpty()) {
            return Optional.empty();
        }

        int half = subAreaSize / 2;
        int startX = perimeter.minX() + half;
        int endX = perimeter.maxX() - half + 1;
        int startZ = perimeter.minZ() + half;
        int endZ = perimeter.maxZ() - half + 1;

        if (startX > endX || startZ > endZ) {
            return Optional.empty();
        }

        List<BlockPos> validCenters = new ArrayList<>();
        for (int cx = startX; cx <= endX; cx++) {
            int boxMinX = cx - half;
            int boxMaxX = cx + half - 1;
            for (int cz = startZ; cz <= endZ; cz++) {
                int boxMinZ = cz - half;
                int boxMaxZ = cz + half - 1;

                int darkCount = 0;
                for (BlockPos pos : darkBlocks) {
                    if (pos.x >= boxMinX && pos.x <= boxMaxX
                            && pos.z >= boxMinZ && pos.z <= boxMaxZ) {
                        darkCount++;
                        if (darkCount >= minDarkBlocks) {
                            break; // early exit – we only need to know it reaches the threshold
                        }
                    }
                }

                if (darkCount >= minDarkBlocks) {
                    validCenters.add(new BlockPos(cx, 0, cz));
                }
            }
        }

        if (validCenters.isEmpty()) {
            return Optional.empty();
        }

        BlockPos chosen = validCenters.get(random.nextInt(validCenters.size()));
        // We need the actual Y for the initial dark count.
        // Use the world's heightmap to get a representative surface Y for the center.
        World world = perimeter.world();
        int surfaceY = world != null ? findSurfaceY(world, chosen.x, chosen.z) : 64;
        BlockPos centerWithY = new BlockPos(chosen.x, surfaceY, chosen.z);
        int initialDarkCount = darkCountInSubArea(perimeter, chosen.x, chosen.z, subAreaSize);
        return Optional.of(new SubAreaResult(centerWithY, initialDarkCount));
    }

    /** Invalidate the cached dark-block list for a single village. */
    public void invalidate(String villageId) {
        cache.remove(villageId);
    }

    /** Invalidate all cached dark-block lists. */
    public void invalidateAll() {
        cache.clear();
    }

    /** Number of currently cached villages. */
    public int cacheSize() {
        return cache.size();
    }

    // ---- internal scanning ----

    private List<BlockPos> scanPerimeter(VillagePerimeter perimeter) {
        World world = perimeter.world();
        if (world == null) {
            return List.of();
        }

        int totalColumns = 0;
        int surfaceFound = 0;
        int spawnableSurface = 0;
        int noSurface = 0;
        int notSpawnable = 0;
        int aboveSolid = 0;
        int liquidAbove = 0;
        int excludedMaterial = 0;
        int hasLight = 0;
        int darkCount = 0;

        // Sampling: first 5 examples per rejection reason for detailed diagnostics
        int sampleMax = 5;
        List<String> sampleNoSurface = new ArrayList<>();
        List<String> sampleNotSpawnable = new ArrayList<>();
        List<String> sampleAboveSolid = new ArrayList<>();
        List<String> sampleLiquidAbove = new ArrayList<>();
        List<String> sampleExcluded = new ArrayList<>();
        List<String> sampleHasLight = new ArrayList<>();
        List<String> sampleDark = new ArrayList<>();

        List<BlockPos> darkBlocks = new ArrayList<>();
        for (int x = perimeter.minX(); x <= perimeter.maxX(); x++) {
            for (int z = perimeter.minZ(); z <= perimeter.maxZ(); z++) {
                totalColumns++;
                Block surface = findSurfaceBlock(world, x, z);
                if (surface == null) {
                    noSurface++;
                    if (sampleNoSurface.size() < sampleMax) {
                        sampleNoSurface.add(String.format("(%d,%d) no surface", x, z));
                    }
                    continue;
                }
                surfaceFound++;

                SpawnCheckResult result = checkSpawnableDetailed(surface);
                switch (result) {
                    case SPAWNABLE:
                        spawnableSurface++;
                        break;
                    case NOT_SOLID_OR_NOT_OCCLUDING:
                        notSpawnable++;
                        if (sampleNotSpawnable.size() < sampleMax) {
                            Block above = surface.getRelative(BlockFace.UP);
                            sampleNotSpawnable.add(String.format("(%d,%d,%d) surface=%s solid=%s occl=%s above=%s",
                                    x, surface.getY(), z,
                                    surface.getType().name(),
                                    surface.getType().isSolid(),
                                    surface.getType().isOccluding(),
                                    above.getType().name()));
                        }
                        continue;
                    case ABOVE_SOLID:
                        aboveSolid++;
                        if (sampleAboveSolid.size() < sampleMax) {
                            Block above = surface.getRelative(BlockFace.UP);
                            sampleAboveSolid.add(String.format("(%d,%d,%d) surface=%s above=%s",
                                    x, surface.getY(), z,
                                    surface.getType().name(),
                                    above.getType().name()));
                        }
                        continue;
                    case LIQUID_ABOVE:
                        liquidAbove++;
                        if (sampleLiquidAbove.size() < sampleMax) {
                            Block above = surface.getRelative(BlockFace.UP);
                            sampleLiquidAbove.add(String.format("(%d,%d,%d) surface=%s above=%s",
                                    x, surface.getY(), z,
                                    surface.getType().name(),
                                    above.getType().name()));
                        }
                        continue;
                    case EXCLUDED_MATERIAL:
                        excludedMaterial++;
                        if (sampleExcluded.size() < sampleMax) {
                            Block above = surface.getRelative(BlockFace.UP);
                            sampleExcluded.add(String.format("(%d,%d,%d) surface=%s above=%s",
                                    x, surface.getY(), z,
                                    surface.getType().name(),
                                    above.getType().name()));
                        }
                        continue;
                }

                // Mob spawning checks light in the block ABOVE the surface, not the surface itself.
                // Only blockLight matters for quest purposes – skyLight changes with day/night
                // cycle and would prevent quest offering during daytime.
                Block above = surface.getRelative(BlockFace.UP);
                int blockLight = above.getLightFromBlocks();
                if (blockLight == 0) {
                    darkBlocks.add(new BlockPos(x, above.getY(), z));
                    darkCount++;
                    if (sampleDark.size() < sampleMax) {
                        sampleDark.add(String.format("(%d,%d,%d) surface=%s above=%s blockLight=%d",
                                x, above.getY(), z,
                                surface.getType().name(),
                                above.getType().name(),
                                blockLight));
                    }
                } else {
                    hasLight++;
                    if (sampleHasLight.size() < sampleMax) {
                        sampleHasLight.add(String.format("(%d,%d,%d) surface=%s above=%s blockLight=%d",
                                x, above.getY(), z,
                                surface.getType().name(),
                                above.getType().name(),
                                blockLight));
                    }
                }
            }
        }

        debug("scanPerimeter for %s: totalColumns=%d surfaceFound=%d spawnableSurface=%d darkCount=%d noSurface=%d notSpawnable=%d aboveSolid=%d liquidAbove=%d excludedMaterial=%d hasLight=%d",
                perimeter.villageId(), totalColumns, surfaceFound, spawnableSurface, darkCount, noSurface, notSpawnable, aboveSolid, liquidAbove, excludedMaterial, hasLight);
        if (debugLogging) {
            if (!sampleNoSurface.isEmpty()) debug("  [noSurface sample] %s", String.join(" | ", sampleNoSurface));
            if (!sampleNotSpawnable.isEmpty()) debug("  [notSpawnable sample] %s", String.join(" | ", sampleNotSpawnable));
            if (!sampleAboveSolid.isEmpty()) debug("  [aboveSolid sample] %s", String.join(" | ", sampleAboveSolid));
            if (!sampleLiquidAbove.isEmpty()) debug("  [liquidAbove sample] %s", String.join(" | ", sampleLiquidAbove));
            if (!sampleExcluded.isEmpty()) debug("  [excludedMaterial sample] %s", String.join(" | ", sampleExcluded));
            if (!sampleHasLight.isEmpty()) debug("  [hasLight sample] %s", String.join(" | ", sampleHasLight));
            if (!sampleDark.isEmpty()) debug("  [dark sample] %s", String.join(" | ", sampleDark));
        }
        return darkBlocks;
    }

    /**
     * Same checks as {@link #isSpawnableSurface(Block)} but returns a detailed
     * enum to support per-reason statistics in {@link #scanPerimeter(VillagePerimeter)}.
     */
    private static SpawnCheckResult checkSpawnableDetailed(Block block) {
        Material type = block.getType();

        // Must be solid AND occluding – rules out slabs, stairs, glass, leaves, etc.
        if (!type.isSolid() || !type.isOccluding()) {
            return SpawnCheckResult.NOT_SOLID_OR_NOT_OCCLUDING;
        }

        // Above must be passable (air, grass, flowers, etc.), NOT another solid block
        Block above = block.getRelative(BlockFace.UP);
        if (above.getType().isSolid()) {
            return SpawnCheckResult.ABOVE_SOLID;
        }

        // Mobs cannot spawn underwater or in lava – exclude water/lava above the surface
        String aboveName = above.getType().name();
        if (aboveName.contains("WATER") || aboveName.contains("LAVA")) {
            return SpawnCheckResult.LIQUID_ABOVE;
        }

        // Explicitly exclude materials mobs cannot spawn on,
        // even if they are technically solid+occluding
        String name = type.name();
        if (name.contains("ICE") || name.contains("LEAVES") || name.contains("GLASS")
                || name.contains("BED") || name.contains("CARPET") || name.contains("SNOW")) {
            return SpawnCheckResult.EXCLUDED_MATERIAL;
        }

        return SpawnCheckResult.SPAWNABLE;
    }

    // ---- shared scanning helpers (also used by LightLevelScanner) ----

    /**
     * Finds the highest solid block at (x,z) that mobs could spawn on.
     * Uses the MOTION_BLOCKING heightmap to skip grass, flowers, etc.
     * Returns {@code null} when no such surface exists (e.g. void).
     */
    static Block findSurfaceBlock(World world, int x, int z) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        // MOTION_BLOCKING heightmap returns the highest block that blocks movement
        // or is a fluid. This IS already the solid surface block itself (e.g. grass,
        // stone), NOT the block above it. Do NOT subtract 1.
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);

        // Clamp to world bounds
        if (y < minY) {
            y = minY;
        }
        if (y > maxY) {
            y = maxY;
        }

        // Walk downward at most 5 blocks to find the first solid block
        int walkDown = 0;
        int searchY = y;
        while (searchY >= minY && walkDown < 5) {
            Block block = world.getBlockAt(x, searchY, z);
            if (block.getType().isSolid()) {
                return block;
            }
            searchY--;
            walkDown++;
        }

        // Fallback: try up to 2 blocks above the heightmap value
        // (handles edge cases where the heightmap returns a non-solid like a fluid)
        searchY = y + 1;
        int walkUp = 0;
        while (searchY <= maxY && walkUp < 2) {
            Block block = world.getBlockAt(x, searchY, z);
            if (block.getType().isSolid()) {
                return block;
            }
            searchY++;
            walkUp++;
        }

        return null;
    }

    /**
     * Returns the surface Y coordinate at (x,z), or the world's minimum height
     * if no surface can be determined.
     */
    static int findSurfaceY(World world, int x, int z) {
        Block surface = findSurfaceBlock(world, x, z);
        return surface != null ? surface.getY() : world.getMinHeight();
    }

    /**
     * Whether the given block is a valid spawnable surface for mob spawning.
     * Delegates to {@link #checkSpawnableDetailed(Block)} – the single authority
     * for spawn-surface rules.
     */
    static boolean isSpawnableSurface(Block block) {
        return checkSpawnableDetailed(block) == SpawnCheckResult.SPAWNABLE;
    }

    // ---- detailed spawn check result ----

    enum SpawnCheckResult {
        SPAWNABLE,
        NOT_SOLID_OR_NOT_OCCLUDING,
        ABOVE_SOLID,
        LIQUID_ABOVE,
        EXCLUDED_MATERIAL
    }

    // ---- internal types ----

    private record CachedDarkList(List<BlockPos> positions, long cachedAtEpochMillis) {
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - cachedAtEpochMillis > ttlMillis;
        }
    }

    /** Immutable block position in the X/Z plane with a surface Y. */
    public record BlockPos(int x, int y, int z) {
    }

    /** Result of picking a random valid sub-area for a village-light quest. */
    public record SubAreaResult(BlockPos center, int initialDarkCount) {
    }
}