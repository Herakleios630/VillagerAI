package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.VillagePerimeter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

/**
 * Toggled per-player via {@code /chief perimeter}. When active, the village
 * perimeter bounding box (from {@link VillagePerimeterService}) is visualised
 * with coloured particles every 20 ticks.
 *
 * <ul>
 *   <li><b>Corner pillars:</b> yellow flame particles at the 4 corners of the
 *       X/Z bounding box, from min-Y to max-Y of the world.</li>
 *   <li><b>Floor outline:</b> green/gold particles along the perimeter edges
 *       at the surface height.</li>
 * </ul>
 */
public final class VillagePerimeterDisplayService {

    private final VillageChiefPlugin plugin;
    private final SpeakerService speakerService;
    private final VillagePerimeterService villagePerimeterService;
    private final VillageIdentityService villageIdentityService;
    private final Map<UUID, UUID> viewingPlayerToVillager = new ConcurrentHashMap<>();
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final BukkitTask renderTask;

    public VillagePerimeterDisplayService(
            VillageChiefPlugin plugin,
            SpeakerService speakerService,
            VillagePerimeterService villagePerimeterService,
            VillageIdentityService villageIdentityService) {
        this.plugin = plugin;
        this.speakerService = speakerService;
        this.villagePerimeterService = villagePerimeterService;
        this.villageIdentityService = villageIdentityService;
        this.renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 20L);
    }

    /**
     * Toggle the perimeter display for the given player targeting the given villager.
     *
     * @return true if now ON, false if now OFF
     */
    public boolean toggle(Player player, Villager villager) {
        UUID playerUuid = player.getUniqueId();
        if (activePlayers.remove(playerUuid)) {
            viewingPlayerToVillager.remove(playerUuid);
            return false;
        }

        viewingPlayerToVillager.put(playerUuid, villager.getUniqueId());
        activePlayers.add(playerUuid);
        return true;
    }

    public boolean isActive(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public void shutdown() {
        renderTask.cancel();
        activePlayers.clear();
        viewingPlayerToVillager.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID playerUuid : activePlayers) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                activePlayers.remove(playerUuid);
                viewingPlayerToVillager.remove(playerUuid);
                continue;
            }

            UUID villagerUuid = viewingPlayerToVillager.get(playerUuid);
            if (villagerUuid == null || !(Bukkit.getEntity(villagerUuid) instanceof Villager villager)
                    || !villager.isValid() || villager.isDead()) {
                activePlayers.remove(playerUuid);
                viewingPlayerToVillager.remove(playerUuid);
                continue;
            }

            Speaker speaker = speakerService.getSpeaker(villager).orElse(null);
            VillagePerimeter perimeter = villagePerimeterService.computePerimeter(
                    villager, speaker.villageId(), villageIdentityService);
            if (perimeter == null || perimeter.world() == null) {
                continue;
            }

            renderPerimeter(player, perimeter);
        }
    }

    private void renderPerimeter(Player player, VillagePerimeter perimeter) {
        World world = perimeter.world();
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }

        int minX = perimeter.minX();
        int maxX = perimeter.maxX();
        int minZ = perimeter.minZ();
        int maxZ = perimeter.maxZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Surface height for the floor outline – use the world's highest Y at corners
        int surfaceY = Math.max(
                Math.max(world.getHighestBlockYAt(minX, minZ), world.getHighestBlockYAt(maxX, minZ)),
                Math.max(world.getHighestBlockYAt(minX, maxZ), world.getHighestBlockYAt(maxX, maxZ)));

        // ---- Thick corner pillars (flame + end rod) for high visibility ----
        for (int cornerX : new int[] {minX, maxX}) {
            for (int cornerZ : new int[] {minZ, maxZ}) {
                for (int y = minY; y <= maxY; y += 2) {
                    double cx = cornerX + 0.5;
                    double cz = cornerZ + 0.5;
                    double yc = y + 0.5;
                    // 4-point thick column
                    for (double dx : new double[] { -0.3, 0.3 }) {
                        for (double dz : new double[] { -0.3, 0.3 }) {
                            spawnParticle(world, Particle.FLAME, cx + dx, yc, cz + dz, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }
                    // bright white spark at each sample point
                    spawnParticle(world, Particle.END_ROD, cx, yc, cz, 1, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }

        // ---- Floor outline using highly visible end-rod (silver/white) particles ----
        Particle outline = Particle.END_ROD;
        // Horizontal edges (X axis) at minZ and maxZ
        for (int x = minX; x <= maxX; x++) {
            if (x % 2 != 0) continue;
            spawnParticle(world, outline, x + 0.5, surfaceY + 0.5, minZ + 0.5, 1, 0.0, 0.0, 0.0, 0.01);
            spawnParticle(world, outline, x + 0.5, surfaceY + 0.5, maxZ + 0.5, 1, 0.0, 0.0, 0.0, 0.01);
        }

        // Horizontal edges (Z axis) at minX and maxX
        for (int z = minZ; z <= maxZ; z++) {
            if (z % 2 != 0) continue;
            spawnParticle(world, outline, minX + 0.5, surfaceY + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.01);
            spawnParticle(world, outline, maxX + 0.5, surfaceY + 0.5, z + 0.5, 1, 0.0, 0.0, 0.0, 0.01);
        }
    }

    private void spawnParticle(World world, Particle particle, double x, double y, double z,
            int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    private void spawnParticle(World world, Particle particle, double x, double y, double z,
            int count, double offsetX, double offsetY, double offsetZ, double extra,
            Object data) {
        world.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
    }
}