package de.ajsch.villagerai.util;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.RayTraceResult;

public final class EntityTargetingUtil {

    private EntityTargetingUtil() {
    }

    public static Villager findTargetedVillager(Player player, double range) {
        RayTraceResult rayTraceResult = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof Villager);

        if (rayTraceResult == null || !(rayTraceResult.getHitEntity() instanceof Villager villager)) {
            return null;
        }

        return villager;
    }
}