package de.ajsch.villagerai.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents the calculated perimeter of a village in the X/Z plane.
 * The Y-coordinate is not bounded – only the surface is relevant for light checks.
 */
public record VillagePerimeter(
        String villageId,
        String worldName,
        int minX,
        int maxX,
        int minZ,
        int maxZ,
        int villagerCount,
        long calculatedAtEpochMillis) {

    public VillagePerimeter {
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        if (villagerCount < 1) {
            throw new IllegalArgumentException("villagerCount must be at least 1");
        }
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("max values must not be smaller than min values");
        }
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public int totalBlocks() {
        return width() * depth();
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public boolean containsBlock(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean containsBlock(Location location) {
        return location != null
                && worldName.equals(location.getWorld().getName())
                && containsBlock(location.getBlockX(), location.getBlockZ());
    }

    /** Center of the perimeter as a Location (Y = 64 as default reference). */
    public Location center() {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                (minX + maxX) / 2.0 + 0.5,
                64.0,
                (minZ + maxZ) / 2.0 + 0.5);
    }
}