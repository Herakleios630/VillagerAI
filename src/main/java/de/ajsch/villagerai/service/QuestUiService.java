package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import de.ajsch.villagerai.model.QuestType;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class QuestUiService {

    private final VillageChiefPlugin plugin;
    private final QuestService questService;
    private final QuestGiverLocatorService questGiverLocatorService;
    private final QuestMarkerService questMarkerService;
    private volatile boolean enabled;
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final BukkitTask refreshTask;

    public QuestUiService(
            VillageChiefPlugin plugin,
            QuestService questService,
            QuestGiverLocatorService questGiverLocatorService,
            QuestMarkerService questMarkerService,
            boolean enabled) {
        this.plugin = plugin;
        this.questService = questService;
        this.questGiverLocatorService = questGiverLocatorService;
        this.questMarkerService = questMarkerService;
        this.enabled = enabled;
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshVisibleBars, 20L, 20L);
    }

    public void reloadSettings(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            for (UUID playerUuid : activeBars.keySet()) {
                clear(playerUuid);
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            clear(player == null ? null : player.getUniqueId());
            return;
        }

        questService.findActiveQuest(player.getUniqueId()).ifPresentOrElse(
                quest -> showQuest(player, quest),
                () -> clear(player.getUniqueId()));
    }

    public void clear(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        questMarkerService.clear(playerUuid);
        BossBar existingBar = activeBars.remove(playerUuid);
        if (existingBar != null) {
            existingBar.removeAll();
        }
    }

    public void shutdown() {
        refreshTask.cancel();
        for (UUID playerUuid : activeBars.keySet()) {
            clear(playerUuid);
        }
    }

    private void showQuest(Player player, Quest quest) {
        BossBar bar = activeBars.computeIfAbsent(
                player.getUniqueId(),
                ignored -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));
        bar.setTitle(buildTitle(player, quest));
        bar.setProgress(resolveProgress(player, quest));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setVisible(true);
    }

    private String buildTitle(Player player, Quest quest) {
        int visibleProgress = resolveVisibleProgress(player, quest);
        String targetDetail = buildTargetDetail(player, quest);
        return "Quest: " + quest.title() + " (" + visibleProgress + "/" + quest.goal() + ")"
                + targetDetail
                + buildQuestGiverHint(player, quest)
                + buildTurnInHint(quest);
    }

    private String buildTargetDetail(Player player, Quest quest) {
        if (quest.progress() >= quest.goal()) {
            return "";
        }

        if (quest.type() == QuestType.VISIT && quest.progress() < quest.goal()) {
            return buildVisitTargetDetail(player, quest);
        }

        if (quest.type() == QuestType.EXPLORE && quest.progress() < quest.goal()) {
            return buildExploreTargetDetail(player, quest);
        }

        if (quest.type() == QuestType.KILL && quest.progress() < quest.goal()) {
            String mobName = quest.targetKey().toLowerCase(Locale.ROOT).replace('_', ' ');
            return " | Ziel: " + mobName;
        }

        if (quest.type() == QuestType.BREED && quest.progress() < quest.goal()) {
            String mobName = quest.targetKey().toLowerCase(Locale.ROOT).replace('_', ' ');
            return " | Ziel: " + mobName;
        }

        return "";
    }

    private String buildVisitTargetDetail(Player player, Quest quest) {
        QuestService.VisitRequirement requirement = questService.parseVisitRequirement(quest).orElse(null);
        if (requirement == null || !requirement.worldName().equalsIgnoreCase(player.getWorld().getName())) {
            return "";
        }

        double dx = requirement.targetX() + 0.5D - player.getLocation().getX();
        double dz = requirement.targetZ() + 0.5D - player.getLocation().getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return " | Ziel: " + distance + "m entfernt";
    }

    private String buildExploreTargetDetail(Player player, Quest quest) {
        QuestService.VisitRequirement requirement = questService.parseExploreRequirement(quest).orElse(null);
        if (requirement == null || !requirement.worldName().equalsIgnoreCase(player.getWorld().getName())) {
            return "";
        }

        double dx = requirement.targetX() + 0.5D - player.getLocation().getX();
        double dz = requirement.targetZ() + 0.5D - player.getLocation().getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return " | Ziel: " + distance + "m entfernt";
    }

    private double resolveProgress(Player player, Quest quest) {
        if (quest.goal() <= 0) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, (double) resolveVisibleProgress(player, quest) / (double) quest.goal()));
    }

    private int resolveVisibleProgress(Player player, Quest quest) {
        if (quest.type() == QuestType.DELIVER || quest.type() == QuestType.BREW) {
            return quest.progress();
        }

        return quest.progress();
    }

    private void refreshVisibleBars() {
        if (!enabled) {
            return;
        }

        for (UUID playerUuid : activeBars.keySet()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                clear(playerUuid);
                continue;
            }

            questService.findActiveQuest(playerUuid).ifPresentOrElse(
                    quest -> showQuest(player, quest),
                    () -> clear(playerUuid));
        }
    }

    private String buildQuestGiverHint(Player player, Quest quest) {
        if (quest.type() == QuestType.SECURE && quest.progress() < quest.goal()) {
            return buildSecureTargetHint(player, quest);
        }

        if (quest.type() == QuestType.EXPLORE && quest.progress() < quest.goal()) {
            return buildExploreTargetHint(player, quest);
        }

        if (quest.type() == QuestType.VISIT && quest.progress() < quest.goal()) {
            return " | Zielort offen";
        }

        Location questGiverLocation = questGiverLocatorService.findQuestGiverLocation(quest).orElse(null);
        if (questGiverLocation == null || questGiverLocation.getWorld() == null) {
            return " | Questgeber: ?";
        }
        if (!questGiverLocation.getWorld().equals(player.getWorld())) {
            return " | Questgeber: andere Welt";
        }

        int distance = (int) Math.round(player.getLocation().distance(questGiverLocation));
        String direction = describeRelativeDirection(player.getLocation(), questGiverLocation);
        return " | " + questGiverLocatorService.resolveQuestGiverName(quest) + ": " + direction + " " + distance + "m";
    }

    private String buildSecureTargetHint(Player player, Quest quest) {
        // village-light format: material|world|villageId|light|goal|initialDark|cx|cy|cz
        if (questService.isVillageLightSecureQuest(quest)) {
            String[] parts = quest.targetKey().split("\\|");
            if (parts.length < 9) {
                return " | Zielort offen";
            }
            String targetWorldName = parts[1];
            if (!targetWorldName.equalsIgnoreCase(player.getWorld().getName())) {
                return " | Zielort: andere Welt";
            }
            try {
                int cx = Integer.parseInt(parts[6]);
                int cy = Integer.parseInt(parts[7]);
                int cz = Integer.parseInt(parts[8]);
                // Horizontal-only distance: ignore Y so bossbar shows 0 m when player stands in the sub-area.
                double dx = (cx + 0.5D) - player.getLocation().getX();
                double dz = (cz + 0.5D) - player.getLocation().getZ();
                int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                Location targetFlat = new Location(player.getWorld(), cx + 0.5D, player.getLocation().getY(), cz + 0.5D);
                String direction = describeRelativeDirection(player.getLocation(), targetFlat);
                return " | Bereich: " + direction + " " + distance + "m";
            } catch (NumberFormatException exception) {
                return " | Zielort offen";
            }
        }

        // block-count format: material:world:x:z:radius
        String[] parts = quest.targetKey().split(":", 5);
        if (parts.length != 5) {
            return " | Zielort offen";
        }

        String targetWorldName = parts[1];
        int targetX;
        int targetZ;
        int radius;
        try {
            targetX = Integer.parseInt(parts[2]);
            targetZ = Integer.parseInt(parts[3]);
            radius = Integer.parseInt(parts[4]);
        } catch (NumberFormatException exception) {
            return " | Zielort offen";
        }

        if (!targetWorldName.equalsIgnoreCase(player.getWorld().getName())) {
            return " | Zielort: andere Welt";
        }

        double dx = targetX + 0.5D - player.getLocation().getX();
        double dz = targetZ + 0.5D - player.getLocation().getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String direction = describeRelativeDirection(player.getLocation(),
                new Location(player.getWorld(), targetX + 0.5D, player.getLocation().getY(), targetZ + 0.5D));
        return " | Zielort: " + direction + " " + distance + "m (Radius " + radius + ")";
    }

    private String buildExploreTargetHint(Player player, Quest quest) {
        QuestService.VisitRequirement requirement = questService.parseExploreRequirement(quest).orElse(null);
        if (requirement == null) {
            return " | Zielort offen";
        }

        if (!requirement.worldName().equalsIgnoreCase(player.getWorld().getName())) {
            return " | Zielort: andere Welt";
        }

        double dx = requirement.targetX() + 0.5D - player.getLocation().getX();
        double dz = requirement.targetZ() + 0.5D - player.getLocation().getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        Location targetFlat = new Location(player.getWorld(), requirement.targetX() + 0.5D, player.getLocation().getY(), requirement.targetZ() + 0.5D);
        String direction = describeRelativeDirection(player.getLocation(), targetFlat);
        return " | Zielort: " + direction + " " + distance + "m";
    }

    private String buildTurnInHint(Quest quest) {
        if (quest.status() != QuestStatus.ACTIVE || quest.progress() < quest.goal()) {
            return "";
        }
        if (quest.type() == QuestType.TALK
            || quest.type() == QuestType.DELIVER
            || quest.type() == QuestType.REPAIR
            || quest.type() == QuestType.BREW) {
            return "";
        }
        return " | Abgabe: Shift-Rechtsklick";
    }

    private String describeRelativeDirection(Location from, Location target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double playerYaw = normalizeAngle(from.getYaw());
        double delta = normalizeAngle(targetAngle - playerYaw);

        if (delta >= -22.5D && delta < 22.5D) {
            return "vorn";
        }
        if (delta >= 22.5D && delta < 67.5D) {
            return "vorn-rechts";
        }
        if (delta >= 67.5D && delta < 112.5D) {
            return "rechts";
        }
        if (delta >= 112.5D && delta < 157.5D) {
            return "hinten-rechts";
        }
        if (delta >= 157.5D || delta < -157.5D) {
            return "hinten";
        }
        if (delta >= -157.5D && delta < -112.5D) {
            return "hinten-links";
        }
        if (delta >= -112.5D && delta < -67.5D) {
            return "links";
        }
        return "vorn-links";
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % 360.0D;
        if (normalized < -180.0D) {
            normalized += 360.0D;
        }
        if (normalized >= 180.0D) {
            normalized -= 360.0D;
        }
        return normalized;
    }
}
