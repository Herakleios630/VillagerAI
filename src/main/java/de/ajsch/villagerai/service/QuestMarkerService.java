package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
    private volatile boolean debugEnabled;
    private int tickCounter = 0;
    private int debugLogInterval = 200; // alle 10 Sekunden bei 20 Ticks/s
    private final Map<UUID, UUID> playerMarkerIds = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public QuestMarkerService(
            VillageChiefPlugin plugin,
            QuestService questService,
            QuestGiverLocatorService questGiverLocatorService,
            boolean enabled,
            String activeSymbol,
            String readySymbol,
            double heightAboveHead) {
        this.plugin = plugin;
        this.questService = questService;
        this.questGiverLocatorService = questGiverLocatorService;
        this.debugEnabled = plugin.getConfig().getBoolean("quests.markers.debug", false);
        reloadSettings(enabled, activeSymbol, readySymbol, heightAboveHead);
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] Konstruktor: enabled=" + enabled + ", activeSymbol=" + activeSymbol + ", readySymbol=" + readySymbol);
        }
    }

    public void reloadSettings(boolean enabled, String activeSymbol, String readySymbol, double heightAboveHead) {
        this.enabled = enabled;
        this.activeSymbol = activeSymbol == null || activeSymbol.isBlank() ? "§l§7?" : activeSymbol;
        this.readySymbol = readySymbol == null || readySymbol.isBlank() ? "§l§e?" : readySymbol;
        this.heightAboveHead = Math.max(0.2, heightAboveHead);
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] reloadSettings: enabled=" + enabled + ", height=" + heightAboveHead);
        }
    }

    public void start() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            return;
        }
        plugin.getLogger().info("[QuestMarker] start() AUFGERUFEN - erstelle Scheduler...");
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllOnlinePlayers, 20L, 20L);
        plugin.getLogger().info("[QuestMarker] Scheduler erstellt, taskId=" + refreshTask.getTaskId());
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] Debug-Modus EIN");
        }
    }

    public void shutdown() {
        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] shutdown() – aktive Marker: " + playerMarkerIds.size());
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        removeAllMarkers();
    }

    public void clear(UUID playerUuid) {
        removePlayerMarker(playerUuid);
    }

    private void refreshAllOnlinePlayers() {
        if (!enabled) return;
        tickCounter++;
        
        boolean logNow = debugEnabled && (tickCounter % debugLogInterval == 0);
        if (logNow) {
            plugin.getLogger().info("[QuestMarker] Tick=" + tickCounter + ", aktive Marker=" + playerMarkerIds.size()
                + ", Online-Spieler=" + Bukkit.getOnlinePlayers().size());
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
            return;
        }

        Optional<Quest> activeQuest = questService.findActiveQuest(player.getUniqueId());
        if (activeQuest.isEmpty()) {
            if (playerMarkerIds.containsKey(player.getUniqueId()) && verbose) {
                plugin.getLogger().info("[QuestMarker] Keine aktive Quest für " + player.getName() + ", entferne Marker");
            }
            removePlayerMarker(player.getUniqueId());
            return;
        }

        Quest quest = activeQuest.get();
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
        TextDisplay display = resolveDisplay(markerId);

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
                plugin.getLogger().info("[QuestMarker] NEUER Marker für " + player.getName()
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

    private void removePlayerMarker(UUID playerUuid) {
        UUID markerId = playerMarkerIds.remove(playerUuid);
        if (markerId == null) return;

        if (debugEnabled) {
            plugin.getLogger().info("[QuestMarker] Entferne Marker " + markerId + " für Spieler " + playerUuid);
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

    private void removeAllMarkers() {
        List<UUID> playersToRemove = List.copyOf(playerMarkerIds.keySet());
        if (debugEnabled && !playersToRemove.isEmpty()) {
            plugin.getLogger().info("[QuestMarker] Entferne alle " + playersToRemove.size() + " Marker");
        }
        for (UUID playerUuid : playersToRemove) {
            removePlayerMarker(playerUuid);
        }
    }

    private TextDisplay resolveDisplay(UUID entityId) {
        if (entityId == null) return null;
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity instanceof TextDisplay td && td.isValid()) {
                return td;
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
