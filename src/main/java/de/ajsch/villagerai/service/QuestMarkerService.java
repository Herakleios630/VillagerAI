package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import de.ajsch.villagerai.model.QuestType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

public final class QuestMarkerService {

    private final VillageChiefPlugin plugin;
    private final QuestService questService;
    private final QuestGiverLocatorService questGiverLocatorService;
    private volatile boolean enabled;
    private volatile String activeSymbol;
    private volatile String readySymbol;
    private volatile double heightAboveHead;
    private volatile boolean worldMarkersEnabled;
    private volatile Material secureMaterial;
    private volatile Material exploreMaterial;
    private volatile double worldMarkerHeightAboveGround;
    private volatile Particle worldMarkerParticle;
    private volatile boolean worldMarkerLabelDistance;
    private volatile boolean worldMarkerShowBlock;
    private volatile boolean debugEnabled;
    private int tickCounter = 0;
    private int debugLogInterval = 200;
    private final Map<UUID, UUID> playerMarkerIds = new ConcurrentHashMap<>();
    private final Map<UUID, WorldMarkerData> playerWorldMarkerIds = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    private record WorldMarkerData(UUID blockDisplayId, UUID textDisplayId) {}

    public QuestMarkerService(
            VillageChiefPlugin plugin,
            QuestService questService,
            QuestGiverLocatorService questGiverLocatorService,
            boolean enabled,
            String activeSymbol,
            String readySymbol,
            double heightAboveHead,
            boolean worldMarkersEnabled,
            Material secureMaterial,
            Material exploreMaterial,
            double worldMarkerHeightAboveGround,
            Particle worldMarkerParticle,
            boolean worldMarkerLabelDistance,
            boolean worldMarkerShowBlock) {
        this.plugin = plugin;
        this.questService = questService;
        this.questGiverLocatorService = questGiverLocatorService;
        this.debugEnabled = plugin.getConfig().getBoolean("quests.markers.debug", false);
        reloadSettings(enabled, activeSymbol, readySymbol, heightAboveHead,
                worldMarkersEnabled, secureMaterial, exploreMaterial,
                worldMarkerHeightAboveGround, worldMarkerParticle, worldMarkerLabelDistance, worldMarkerShowBlock);
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] Konstruktor: enabled=" + enabled + ", worldMarkers=" + worldMarkersEnabled);
        }
    }

    public void reloadSettings(boolean enabled, String activeSymbol, String readySymbol, double heightAboveHead,
                               boolean worldMarkersEnabled, Material secureMaterial, Material exploreMaterial,
                               double worldMarkerHeightAboveGround, Particle worldMarkerParticle,
                               boolean worldMarkerLabelDistance,
                               boolean worldMarkerShowBlock) {
        this.enabled = enabled;
        this.activeSymbol = activeSymbol == null || activeSymbol.isBlank() ? "§l§7?" : activeSymbol;
        this.readySymbol = readySymbol == null || readySymbol.isBlank() ? "§l§e?" : readySymbol;
        this.heightAboveHead = Math.max(0.2, heightAboveHead);
        this.worldMarkersEnabled = worldMarkersEnabled;
        this.secureMaterial = secureMaterial != null ? secureMaterial : Material.GLOWSTONE;
        this.exploreMaterial = exploreMaterial != null ? exploreMaterial : Material.LODESTONE;
        this.worldMarkerHeightAboveGround = Math.max(1.0, worldMarkerHeightAboveGround);
        this.worldMarkerParticle = worldMarkerParticle != null ? worldMarkerParticle : Particle.SOUL_FIRE_FLAME;
        this.worldMarkerLabelDistance = worldMarkerLabelDistance;
        this.worldMarkerShowBlock = worldMarkerShowBlock;
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] reloadSettings: enabled=" + enabled + ", worldMarkers=" + worldMarkersEnabled);
        }
    }

    public void start() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            return;
        }
        plugin.getLogger().info("[QuestMarker] start() AUFGERUFEN - erstelle Scheduler...");
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllOnlinePlayers, 20L, 20L);
        plugin.getLogger().info("[QuestMarker] Scheduler erstellt, taskId=" + refreshTask.getTaskId());
    }

    public void shutdown() {
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] shutdown() – aktive Marker: giver=" + playerMarkerIds.size() + ", world=" + playerWorldMarkerIds.size());
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        removeAllMarkers();
    }

    public void clear(UUID playerUuid) {
        removePlayerMarker(playerUuid);
        removePlayerWorldMarker(playerUuid);
    }

    public void clearWorldMarker(UUID playerUuid) {
        removePlayerWorldMarker(playerUuid);
    }

    private void refreshAllOnlinePlayers() {
        if (!enabled) return;
        tickCounter++;

        boolean logNow = debugEnabled && (tickCounter % debugLogInterval == 0);
        if (logNow) {
            plugin.getLogger().info("[QuestMarker] Tick=" + tickCounter
                    + ", giverMarker=" + playerMarkerIds.size()
                    + ", worldMarker=" + playerWorldMarkerIds.size()
                    + ", Online=" + Bukkit.getOnlinePlayers().size());
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                refresh(player, logNow);
            } catch (Exception e) {
                if (debugEnabled) {
                    plugin.getLogger().warning("[QuestMarker] Fehler bei " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void refresh(Player player, boolean verbose) {
        if (!enabled || player == null || !player.isOnline()) {
            removePlayerMarker(player.getUniqueId());
            removePlayerWorldMarker(player.getUniqueId());
            return;
        }

        Optional<Quest> activeQuest = questService.findActiveQuest(player.getUniqueId());
        if (activeQuest.isEmpty()) {
            if (verbose && (playerMarkerIds.containsKey(player.getUniqueId()) || playerWorldMarkerIds.containsKey(player.getUniqueId()))) {
                plugin.getLogger().info("[QuestMarker] Keine aktive Quest für " + player.getName() + ", entferne alle Marker");
            }
            removePlayerMarker(player.getUniqueId());
            removePlayerWorldMarker(player.getUniqueId());
            return;
        }

        Quest quest = activeQuest.get();

        // --- Questgiver-Marker (existierende Logik) ---
        refreshQuestGiverMarker(player, quest, verbose);

        // --- World-Marker für SECURE und EXPLORE ---
        if (worldMarkersEnabled && quest.status() == QuestStatus.ACTIVE && quest.progress() < quest.goal()) {
            if (quest.type() == QuestType.SECURE) {
                refreshSecureWorldMarker(player, quest, verbose);
            } else if (quest.type() == QuestType.EXPLORE) {
                refreshExploreWorldMarker(player, quest, verbose);
            } else {
                removePlayerWorldMarker(player.getUniqueId());
            }
        } else {
            removePlayerWorldMarker(player.getUniqueId());
        }
    }

    private void refreshQuestGiverMarker(Player player, Quest quest, boolean verbose) {
        Optional<Location> questGiverLocation = questGiverLocatorService.findQuestGiverLocation(quest);
        if (questGiverLocation.isEmpty()) {
            if (verbose) {
                plugin.getLogger().warning("[QuestMarker] Kein Questgeber-Standort für Quest " + quest.questId());
            }
            removePlayerMarker(player.getUniqueId());
            return;
        }

        Location loc = questGiverLocation.get();
        if (loc.getWorld() == null) {
            if (verbose) {
                plugin.getLogger().warning("[QuestMarker] Welt ist null für Location von Quest " + quest.questId());
            }
            removePlayerMarker(player.getUniqueId());
            return;
        }

        if (!loc.isChunkLoaded()) {
            if (verbose) {
                plugin.getLogger().info("[QuestMarker] Chunk nicht geladen an " + loc + " für " + player.getName());
            }
            return;
        }

        Villager questGiver = findVillagerNearby(loc);
        if (questGiver == null) {
            if (verbose) {
                plugin.getLogger().warning("[QuestMarker] Kein Villager in der Nähe von " + loc + " gefunden");
            }
            removePlayerMarker(player.getUniqueId());
            return;
        }

        String symbol = quest.status() == QuestStatus.ACTIVE && quest.progress() >= quest.goal()
                ? readySymbol
                : activeSymbol;

        UUID playerUuid = player.getUniqueId();
        UUID markerId = playerMarkerIds.get(playerUuid);
        TextDisplay display = resolveTextDisplay(markerId);

        if (display != null) {
            Location targetLoc = questGiver.getEyeLocation().clone().add(0.0, heightAboveHead, 0.0);
            if (!targetLoc.isChunkLoaded()) return;
            try {
                display.teleport(targetLoc);
                updateDisplayTextSafe(display, symbol);
                if (verbose) {
                    plugin.getLogger().info("[QuestMarker] Teleportiere Marker für " + player.getName()
                            + " zu Villager an " + targetLoc + ", Symbol=" + symbol);
                }
            } catch (Exception e) {
                if (debugEnabled) {
                    plugin.getLogger().warning("[QuestMarker] Teleport fehlgeschlagen: " + e.getMessage());
                }
                removePlayerMarker(playerUuid);
            }
            return;
        }

        Location spawnLoc = questGiver.getEyeLocation().clone().add(0.0, heightAboveHead, 0.0);
        if (!spawnLoc.isChunkLoaded()) return;

        try {
            display = questGiver.getWorld().spawn(spawnLoc, TextDisplay.class, td -> {
                td.setPersistent(false);
                td.setVisibleByDefault(true);
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(false);
                td.setShadowed(true);
                td.setText(symbol);
                td.setInterpolationDuration(0);
                td.setTeleportDuration(0);
            });
            playerMarkerIds.put(playerUuid, display.getUniqueId());
            if (verbose || debugEnabled) {
                plugin.getLogger().info("[QuestMarker] NEUER Giver-Marker für " + player.getName()
                        + ", Entity-ID=" + display.getUniqueId()
                        + ", an " + spawnLoc
                        + ", Symbol=" + symbol
                        + ", Quest=" + quest.title());
            }
        } catch (Exception e) {
            if (debugEnabled) {
                plugin.getLogger().warning("[QuestMarker] Spawn fehlgeschlagen: " + e.getMessage());
            }
        }
    }

    private void refreshSecureWorldMarker(Player player, Quest quest, boolean verbose) {
        // village-light: draw particle border instead of single point marker
        if (questService.isVillageLightSecureQuest(quest)) {
            int[] subCenter = questService.extractVillageLightSubCenter(quest);
            if (subCenter != null) {
                int half = questService.extractVillageLightAreaSize(quest) / 2;
                int minX = subCenter[0] - half;
                int maxX = subCenter[0] + half - 1;
                int minZ = subCenter[2] - half;
                int maxZ = subCenter[2] + half - 1;
                spawnSecureAreaBorder(player, quest, minX, maxX, minZ, maxZ);
            }
            return;
        }

        // block-count: single point marker (existing logic)
        QuestService.SecureRequirement req = questService.parseSecureRequirement(quest).orElse(null);
        if (req == null) {
            removePlayerWorldMarker(player.getUniqueId());
            return;
        }
        refreshWorldMarker(player, quest, req.worldName(), req.targetX(), req.targetZ(), secureMaterial, verbose);
    }

    private void refreshExploreWorldMarker(Player player, Quest quest, boolean verbose) {
        QuestService.VisitRequirement req = questService.parseExploreRequirement(quest).orElse(null);
        if (req == null) {
            removePlayerWorldMarker(player.getUniqueId());
            return;
        }
        refreshWorldMarker(player, quest, req.worldName(), req.targetX(), req.targetZ(), exploreMaterial, verbose);
    }

    private void refreshWorldMarker(Player player, Quest quest, String worldName, int targetX, int targetZ,
                                    Material blockMaterial, boolean verbose) {
        UUID playerUuid = player.getUniqueId();

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (verbose) {
                plugin.getLogger().warning("[QuestMarker] Welt '" + worldName + "' nicht gefunden für World-Marker");
            }
            removePlayerWorldMarker(playerUuid);
            return;
        }

        Location targetLoc = new Location(world, targetX + 0.5, 0, targetZ + 0.5);
        if (!targetLoc.isChunkLoaded()) {
            if (verbose) {
                plugin.getLogger().info("[QuestMarker] World-Marker Chunk nicht geladen an " + targetX + "/" + targetZ + " für " + player.getName());
            }
            return;
        }

        int surfaceY = world.getHighestBlockYAt(targetX, targetZ);
        double markerY = surfaceY + 1.0 + worldMarkerHeightAboveGround;
        Location markerLoc = new Location(world, targetX + 0.5D, markerY, targetZ + 0.5D);

        WorldMarkerData existingData = playerWorldMarkerIds.get(playerUuid);
        BlockDisplay blockDisplay = worldMarkerShowBlock ? resolveBlockDisplay(existingData != null ? existingData.blockDisplayId() : null) : null;
        TextDisplay textDisplay = resolveTextDisplay(existingData != null ? existingData.textDisplayId() : null);

        int distance = (int) Math.round(player.getLocation().distance(markerLoc));

        boolean blockExists = blockDisplay != null;
        boolean textExists = textDisplay != null;
        boolean bothExist = (worldMarkerShowBlock && blockExists && textExists) || (!worldMarkerShowBlock && textExists);

        if (bothExist) {
            // Update existing markers
            try {
                if (worldMarkerShowBlock) {
                    blockDisplay.teleport(markerLoc);
                }
                if (worldMarkerLabelDistance) {
                    textDisplay.teleport(markerLoc.clone().add(0.0, 0.8, 0.0));
                    updateDisplayTextSafe(textDisplay, "§6Zielort: §f" + distance + "m");
                } else {
                    updateDisplayTextSafe(textDisplay, "");
                }
                if (verbose) {
                    plugin.getLogger().info("[QuestMarker] World-Marker aktualisiert für " + player.getName()
                            + " an " + targetX + "/" + targetZ + ", Distanz=" + distance + "m");
                }
                spawnWorldMarkerParticles(player, markerLoc);
            } catch (Exception e) {
                if (debugEnabled) {
                    plugin.getLogger().warning("[QuestMarker] World-Marker Update fehlgeschlagen: " + e.getMessage());
                }
                removePlayerWorldMarker(playerUuid);
            }
            return;
        }

        // Spawn new markers
        try {
            UUID blockId = null;
            if (worldMarkerShowBlock) {
                BlockData blockData = blockMaterial.createBlockData();
                blockDisplay = world.spawn(markerLoc, BlockDisplay.class, bd -> {
                    bd.setPersistent(false);
                    bd.setVisibleByDefault(false);
                    bd.setBlock(blockData);
                    bd.setInterpolationDuration(0);
                    bd.setTeleportDuration(0);
                });
                player.showEntity(plugin, blockDisplay);
                blockId = blockDisplay.getUniqueId();
            }

            String textContent = worldMarkerLabelDistance ? "§6Zielort: §f" + distance + "m" : "";
            Location textLoc = markerLoc.clone().add(0.0, 0.8, 0.0);
            textDisplay = world.spawn(textLoc, TextDisplay.class, td -> {
                td.setPersistent(false);
                td.setVisibleByDefault(false);
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(true);
                td.setText(textContent);
                td.setInterpolationDuration(0);
                td.setTeleportDuration(0);
            });
            player.showEntity(plugin, textDisplay);

            playerWorldMarkerIds.put(playerUuid, new WorldMarkerData(blockId, textDisplay.getUniqueId()));

            if (verbose || debugEnabled) {
                plugin.getLogger().info("[QuestMarker] NEUER World-Marker für " + player.getName()
                        + (worldMarkerShowBlock ? (", Block=" + blockId) : "")
                        + ", Text=" + textDisplay.getUniqueId()
                        + ", an " + targetX + "/" + targetZ
                        + (worldMarkerShowBlock ? (", Material=" + blockMaterial.name()) : "")
                        + ", Distanz=" + distance + "m"
                        + ", Quest=" + quest.title());
            }
            spawnWorldMarkerParticles(player, markerLoc);
        } catch (Exception e) {
            if (debugEnabled) {
                plugin.getLogger().warning("[QuestMarker] World-Marker Spawn fehlgeschlagen: " + e.getMessage());
            }
            removePlayerWorldMarker(playerUuid);
        }
    }

    private void spawnWorldMarkerParticles(Player player, Location markerLoc) {
        if (worldMarkerParticle == null || !markerLoc.isChunkLoaded()) return;
        try {
            player.spawnParticle(worldMarkerParticle,
                    markerLoc.clone().add(0.0, 0.5, 0.0),
                    3, 0.3, 0.3, 0.3, 0.02);
        } catch (Exception ignored) {
            // Particle spawn can fail silently
        }
    }

    /**
     * Draws a particle border outlining the sub-area (minX..maxX, minZ..maxZ) for
     * village-light SECURE quests. The border is drawn at the surface Y of each
     * block along the perimeter edges.
     */
    private void spawnSecureAreaBorder(Player player, Quest quest, int minX, int maxX, int minZ, int maxZ) {
        if (worldMarkerParticle == null) return;
        org.bukkit.World world = player.getWorld();
        // Draw border: top edge (minZ), bottom edge (maxZ), left edge (minX), right edge (maxX)
        for (int x = minX; x <= maxX; x++) {
            drawBorderParticleAt(player, world, x, minZ);
            drawBorderParticleAt(player, world, x, maxZ);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            drawBorderParticleAt(player, world, minX, z);
            drawBorderParticleAt(player, world, maxX, z);
        }
    }

    private void drawBorderParticleAt(Player player, org.bukkit.World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Location loc = new org.bukkit.Location(world, x + 0.5, y + 1.5, z + 0.5);
        try {
            player.spawnParticle(worldMarkerParticle, loc, 1, 0.0, 0.0, 0.0, 0.0);
        } catch (Exception ignored) {
            // Particle spawn can fail silently
        }
    }

    private void removePlayerMarker(UUID playerUuid) {
        UUID markerId = playerMarkerIds.remove(playerUuid);
        if (markerId == null) return;

        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] Entferne Giver-Marker " + markerId + " für Spieler " + playerUuid);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(markerId)) {
                        entity.remove();
                        return;
                    }
                }
            }
        });
    }

    private void removePlayerWorldMarker(UUID playerUuid) {
        WorldMarkerData data = playerWorldMarkerIds.remove(playerUuid);
        if (data == null) return;

        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] Entferne World-Marker block=" + (data.blockDisplayId() != null ? data.blockDisplayId() : "keiner")
                    + " text=" + data.textDisplayId() + " für Spieler " + playerUuid);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    UUID entityId = entity.getUniqueId();
                    if (entityId.equals(data.blockDisplayId()) || entityId.equals(data.textDisplayId())) {
                        entity.remove();
                    }
                }
            }
        });
    }

    private void removeAllMarkers() {
        List<UUID> giverPlayers = List.copyOf(playerMarkerIds.keySet());
        List<UUID> worldPlayers = List.copyOf(playerWorldMarkerIds.keySet());
        if (debugEnabled && (!giverPlayers.isEmpty() || !worldPlayers.isEmpty())) {
            plugin.getLogger().info("[QuestMarker] Entferne alle Marker: giver=" + giverPlayers.size() + ", world=" + worldPlayers.size());
        }
        for (UUID playerUuid : giverPlayers) {
            removePlayerMarker(playerUuid);
        }
        for (UUID playerUuid : worldPlayers) {
            removePlayerWorldMarker(playerUuid);
        }
    }

    private TextDisplay resolveTextDisplay(UUID entityId) {
        if (entityId == null) return null;
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity instanceof TextDisplay td && td.isValid()) {
                return td;
            }
        }
        return null;
    }

    private BlockDisplay resolveBlockDisplay(UUID entityId) {
        if (entityId == null) return null;
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity instanceof BlockDisplay bd && bd.isValid()) {
                return bd;
            }
        }
        return null;
    }

    private Villager findVillagerNearby(Location location) {
        if (location.getWorld() == null) return null;
        for (Entity entity : location.getWorld().getNearbyEntities(location, 5.0, 3.0, 5.0)) {
            if (entity instanceof Villager villager && villager.isValid() && !villager.isDead()) {
                return villager;
            }
        }
        return null;
    }

    private void updateDisplayTextSafe(TextDisplay display, String symbol) {
        if (!display.isValid() || display.isDead()) return;
        display.setText(symbol);
    }
}