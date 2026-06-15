package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shows red dust particles on dark blocks inside a village-light SECURE quest
 * sub-area, only for the quest owner. Renders every 20 ticks, but only when
 * the player has an active village-light quest with remaining progress.
 *
 * <p>Controlled by {@code debug.village-light-particle-marker} in {@code config.yml}.
 * This is a quality-of-life debug feature, enabled by default.</p>
 */
public final class VillageLightParticleMarkerService {

    private final Logger logger = Logger.getLogger(VillageLightParticleMarkerService.class.getName());
    private final QuestService questService;
    private final LightLevelScanner lightLevelScanner;
    private volatile boolean enabled;
    private final Map<UUID, MarkerTask> activeMarkers = new ConcurrentHashMap<>();
    private final BukkitTask tickTask;
    private int tickCounter = 0;

    public VillageLightParticleMarkerService(
            VillageChiefPlugin plugin,
            QuestService questService,
            LightLevelScanner lightLevelScanner,
            boolean enabled) {
        this.questService = questService;
        this.lightLevelScanner = lightLevelScanner;
        this.enabled = enabled;
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        logger.info("VillageLightParticleMarkerService created. enabled=" + enabled + " tickInterval=20L");
    }

    public void reloadEnabled(boolean enabled) {
        boolean old = this.enabled;
        this.enabled = enabled;
        logger.info("reloadEnabled: " + old + " -> " + enabled);
        if (!enabled) {
            activeMarkers.clear();
        }
    }

    public void shutdown() {
        logger.info("shutdown() – activeMarkers.size=" + activeMarkers.size());
        tickTask.cancel();
        activeMarkers.clear();
    }

    private void tick() {
        tickCounter++;
        if (!enabled) {
            if (tickCounter <= 5 || tickCounter % 200 == 0) {
                logger.fine("tick() #" + tickCounter + " – disabled, skipping");
            }
            return;
        }

        int questPlayers = 0;
        int renderedPlayers = 0;
        int noDarkBlocks = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();
            Quest activeQuest = questService.findActiveQuest(playerUuid).orElse(null);
            boolean relevant = activeQuest != null
                    && activeQuest.type() == QuestType.SECURE
                    && questService.isVillageLightSecureQuest(activeQuest)
                    && activeQuest.progress() < activeQuest.goal();

            if (!relevant) {
                if (activeMarkers.remove(playerUuid) != null) {
                    logger.fine("tick() – removed marker for " + player.getName() + " (quest no longer relevant)");
                }
                continue;
            }

            questPlayers++;

            MarkerTask task = activeMarkers.computeIfAbsent(playerUuid,
                    ignored -> {
                        logger.info("tick() – created MarkerTask for " + player.getName()
                                + " (questId=" + activeQuest.questId() + ")");
                        return new MarkerTask();
                    });
            int result = task.render(player, activeQuest);
            if (result > 0) {
                renderedPlayers++;
            } else if (result == 0) {
                noDarkBlocks++;
            }
        }

        activeMarkers.keySet().removeIf(uuid -> {
            if (Bukkit.getPlayer(uuid) == null) {
                logger.fine("tick() – cleaned up offline player marker: " + uuid);
                return true;
            }
            return false;
        });

        // Summary log every 60 ticks (3 seconds)
        if (tickCounter % 60 == 0) {
            logger.info("tick() #" + tickCounter
                    + " | enabled=" + enabled
                    + " | onlinePlayers=" + Bukkit.getOnlinePlayers().size()
                    + " | questPlayers=" + questPlayers
                    + " | renderedPlayers=" + renderedPlayers
                    + " | noDarkBlocks=" + noDarkBlocks
                    + " | activeMarkers=" + activeMarkers.size());
        }
    }

    private class MarkerTask {
        private int[] lastSubCenter = null;
        private int lastSubSize = 0;
        private List<DarkBlockCache.BlockPos> darkBlocks = List.of();
        private int scanTickCounter = 0;

        /**
         * Returns the number of dark blocks that were marked (0 means no dark blocks found).
         */
        int render(Player player, Quest quest) {
            scanTickCounter++;
            int[] subCenter = questService.extractVillageLightSubCenter(quest);
            int subSize = questService.extractVillageLightAreaSize(quest);
            if (subCenter == null) {
                if (scanTickCounter <= 3) {
                    logger.warning("render() for " + player.getName()
                            + " – subCenter is null! Quest targetKey=" + quest.targetKey());
                }
                return -1;
            }

            boolean subChanged = !Arrays.equals(subCenter, lastSubCenter)
                    || subSize != lastSubSize;
            if (darkBlocks.isEmpty() || scanTickCounter % 2 == 0 || subChanged) {
                World world = player.getWorld();
                darkBlocks = lightLevelScanner.darkBlocksInSubArea(
                        world, subCenter[0], subCenter[2], subSize);
                lastSubCenter = subCenter;
                lastSubSize = subSize;

                if (scanTickCounter <= 3 || subChanged || scanTickCounter % 20 == 0) {
                    logger.info("render() scan for " + player.getName()
                            + " subCenter=(" + subCenter[0] + "," + subCenter[1] + "," + subCenter[2] + ")"
                            + " subSize=" + subSize
                            + " darkBlocks.size=" + darkBlocks.size()
                            + " subChanged=" + subChanged
                            + " scanTick=" + scanTickCounter);
                }
            }

            if (darkBlocks.isEmpty()) {
                if (scanTickCounter <= 5 || scanTickCounter % 200 == 0) {
                    logger.fine("render() for " + player.getName()
                            + " – no dark blocks to mark (scanTick=" + scanTickCounter + ")");
                }
                return 0;
            }

            DustOptions dust = new DustOptions(Color.fromRGB(220, 30, 30), 0.9f);
            World world = player.getWorld();
            int marked = 0;
            for (DarkBlockCache.BlockPos pos : darkBlocks) {
                // Temporarily use FLAME for maximum visibility during debugging.
                // FLAME particles are large, bright, and visible from far away.
                player.spawnParticle(Particle.FLAME,
                        pos.x() + 0.5, pos.y() + 0.2, pos.z() + 0.5,
                        3, 0.15, 0.05, 0.15, 0.02, null);
                marked++;
            }

            if (scanTickCounter <= 3 || scanTickCounter % 20 == 0) {
                var first = darkBlocks.get(0);
                double dist = player.getLocation().distance(
                        new org.bukkit.Location(world, first.x(), first.y(), first.z()));
                logger.info("render() for " + player.getName()
                        + " – first dark block at (" + first.x() + "," + first.y() + "," + first.z() + ")"
                        + " player distance=" + String.format("%.1f", dist)
                        + " blocks");
            }

            if (scanTickCounter <= 3 || scanTickCounter % 40 == 0) {
                logger.info("render() for " + player.getName()
                        + " – marked " + marked + " dark blocks with red dust particles"
                        + " (scanTick=" + scanTickCounter + ")");
            }

            return marked;
        }
    }
}