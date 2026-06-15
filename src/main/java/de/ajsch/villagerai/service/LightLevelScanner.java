package de.ajsch.villagerai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Scans a single sub-area (e.g. 20×20) block-by-block to count how many dark
 * spawnable surface blocks remain. Designed to complete in {@code <5 ms} for
 * a 20×20 area, running synchronously on the main thread.
 *
 * <h3>Scan rules (identical to {@link DarkBlockCache})</h3>
 * <ul>
 *   <li>Surface only – highest solid block with air directly above.</li>
 *   <li>Solid and occluding ({@code isSolid()} and {@code isOccluding()}).</li>
 *   <li>Block-Light ({@code getLightFromBlocks()}) == 0 → dark. Sky-Light IS ignored
 *       because it changes with day/night and would hide dark blocks during daytime.</li>
 *   <li>No water, lava, glass, slabs, stairs, carpets, ice, leaves, beds, snow.</li>
 * </ul>
 *
 * <h3>Trigger policy</h3>
 * Called on: {@code BlockPlaceEvent}, {@code BlockBreakEvent} (when a light
 * source is placed/removed inside the sub-area), quest-giver interaction,
 * and quest acceptance (initial scan). No timer or continuous scanning.
 */
public final class LightLevelScanner {

    private final Logger logger = Logger.getLogger(LightLevelScanner.class.getName());
    private volatile boolean debugLogging = false;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    private void debug(String msg, Object... args) {
        if (debugLogging) {
            logger.log(Level.INFO, String.format("[LightLevelScanner] " + msg, args));
        }
    }

    /**
     * Scans every X/Z column in the sub-area and counts the dark surface blocks.
     *
     * @param world       the world to scan in
     * @param centerX     center X of the sub-area
     * @param centerZ     center Z of the sub-area
     * @param subAreaSize edge length (e.g. 20 for a 20×20 area)
     * @return dark count (0 when the sub-area is fully lit)
     */
    public int scanSubArea(World world, int centerX, int centerZ, int subAreaSize) {
        int half = subAreaSize / 2;
        int minX = centerX - half;
        int maxX = centerX + half - 1;
        int minZ = centerZ - half;
        int maxZ = centerZ + half - 1;

        int totalColumns = 0;
        int surfaceFound = 0;
        int spawnableSurface = 0;
        int noSurface = 0;
        int notSpawnable = 0;
        int hasLight = 0;
        int darkCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                totalColumns++;
                Block surface = DarkBlockCache.findSurfaceBlock(world, x, z);
                if (surface == null) {
                    noSurface++;
                    continue;
                }
                surfaceFound++;

                if (!DarkBlockCache.isSpawnableSurface(surface)) {
                    notSpawnable++;
                    continue;
                }
                spawnableSurface++;

                // Mob spawning checks light in the block ABOVE the surface, not the surface itself.
                // Only blockLight matters for quest purposes – skyLight changes with day/night cycle.
                Block above = surface.getRelative(BlockFace.UP);
                int blockLight = above.getLightFromBlocks();
                if (blockLight == 0) {
                    darkCount++;
                } else {
                    hasLight++;
                }
            }
        }

        debug("scanSubArea center=(%d,%d) size=%d: totalColumns=%d surfaceFound=%d spawnableSurface=%d darkCount=%d noSurface=%d notSpawnable=%d hasLight=%d",
                centerX, centerZ, subAreaSize, totalColumns, surfaceFound, spawnableSurface, darkCount, noSurface, notSpawnable, hasLight);

        return darkCount;
    }

    /**
     * Returns the list of dark block positions within the sub-area.
     * Useful for debug overlays or optional glow effects.
     */
    public List<DarkBlockCache.BlockPos> darkBlocksInSubArea(World world, int centerX, int centerZ, int subAreaSize) {
        int half = subAreaSize / 2;
        int minX = centerX - half;
        int maxX = centerX + half - 1;
        int minZ = centerZ - half;
        int maxZ = centerZ + half - 1;

        List<DarkBlockCache.BlockPos> darkBlocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block surface = DarkBlockCache.findSurfaceBlock(world, x, z);
                if (surface == null || !DarkBlockCache.isSpawnableSurface(surface)) {
                    continue;
                }
                // Light check must use the block ABOVE the surface (the air block),
                // consistent with scanSubArea() and DarkBlockCache.scanPerimeter().
                // Only blockLight matters – skyLight changes with day/night cycle.
                Block above = surface.getRelative(org.bukkit.block.BlockFace.UP);
                if (above.getLightFromBlocks() == 0) {
                    // Store above.getY() so particles hover at air block height,
                    // matching DarkBlockCache.scanPerimeter() behaviour.
                    darkBlocks.add(new DarkBlockCache.BlockPos(x, above.getY(), z));
                }
            }
        }
        return darkBlocks;
    }
}