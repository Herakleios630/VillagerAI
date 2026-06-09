package de.ajsch.villagerai.listener;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.service.ChiefService;
import de.ajsch.villagerai.service.QuestService;
import de.ajsch.villagerai.service.QuestUiService;
import de.ajsch.villagerai.storage.VillagerProfileRepository;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class QuestLifecycleListener implements Listener {

    private final VillageChiefPlugin plugin;
    private final ChiefService chiefService;
    private final QuestService questService;
    private final QuestUiService questUiService;
    private final VillagerProfileRepository villagerProfileRepository;
    private final Map<UUID, Long> lastWrongTargetHintMillis = new ConcurrentHashMap<>();
    private static final long WRONG_TARGET_HINT_COOLDOWN_MILLIS = 15_000L;

    public QuestLifecycleListener(
            VillageChiefPlugin plugin,
            ChiefService chiefService,
            QuestService questService,
            QuestUiService questUiService,
            VillagerProfileRepository villagerProfileRepository) {
        this.plugin = plugin;
        this.chiefService = chiefService;
        this.questService = questService;
        this.questUiService = questUiService;
        this.villagerProfileRepository = villagerProfileRepository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        handleQuestGiverDeath(event);
        handleVillagerDeathAnnouncement(event);
        handlePlayerKillProgress(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }

        Collection<QuestService.BreedQuestUpdate> updates = questService.advanceBreedQuests(player, event.getEntityType());
        for (QuestService.BreedQuestUpdate update : updates) {
            Quest quest = update.quest();
            player.sendMessage(Component.text(
                    "Quest-Fortschritt: " + quest.title() + " (" + quest.progress() + "/" + quest.goal() + ")",
                    NamedTextColor.GREEN));
            if (update.readyToTurnIn()) {
                player.sendMessage(Component.text(
                        "Ziel erreicht: Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                        NamedTextColor.YELLOW));
            }
            questUiService.refresh(player);
        }

        if (updates.isEmpty()) {
            hintWrongBreedTarget(player, event.getEntityType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Collection<QuestService.BuildQuestUpdate> buildUpdates = questService.advanceBuildQuests(player, event.getBlockPlaced().getType());
        for (QuestService.BuildQuestUpdate update : buildUpdates) {
            Quest quest = update.quest();
            player.sendMessage(Component.text(
                    "Quest-Fortschritt: " + quest.title() + " (" + quest.progress() + "/" + quest.goal() + ")",
                    NamedTextColor.GREEN));
            if (update.readyToTurnIn()) {
                player.sendMessage(Component.text(
                        "Ziel erreicht: Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                        NamedTextColor.YELLOW));
            }
            questUiService.refresh(player);
        }

        Collection<QuestService.SecureQuestUpdate> secureUpdates = questService.advanceSecureQuests(player, event.getBlockPlaced().getType(), event.getBlockPlaced().getLocation());
        for (QuestService.SecureQuestUpdate update : secureUpdates) {
            Quest quest = update.quest();
            player.sendMessage(Component.text(
                    "Quest-Fortschritt: " + quest.title() + " (" + quest.progress() + "/" + quest.goal() + ")",
                    NamedTextColor.GREEN));
            if (update.readyToTurnIn()) {
                player.sendMessage(Component.text(
                        "Ziel erreicht: Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                        NamedTextColor.YELLOW));
            }
            questUiService.refresh(player);
        }

        if (buildUpdates.isEmpty() && secureUpdates.isEmpty()) {
            hintWrongBlockTarget(player, event.getBlockPlaced().getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (event.getTransformedEntity() == null || event.getTransformedEntity().getType() != EntityType.ZOMBIE_VILLAGER) {
            return;
        }

        String villagerName = chiefService.getConversationSpeaker(villager).chatName();
        String professionName = formatProfession(villager);
        String message = "Der " + professionName + " " + villagerName + " wurde in einen Zombie verwandelt.";
        broadcastVillagerEvent(message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Collection<QuestService.VisitQuestUpdate> visitUpdates = questService.advanceVisitQuests(event.getPlayer(), to);
        for (QuestService.VisitQuestUpdate update : visitUpdates) {
            QuestService.VisitRequirement requirement = update.requirement();
            event.getPlayer().sendMessage(Component.text(
                    "Ziel erreicht: X " + requirement.targetX() + " / Z " + requirement.targetZ()
                            + ". Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                    NamedTextColor.YELLOW));
            questUiService.refresh(event.getPlayer());
        }

        Collection<QuestService.ExploreQuestUpdate> exploreUpdates = questService.advanceExploreQuests(event.getPlayer(), to);
        for (QuestService.ExploreQuestUpdate update : exploreUpdates) {
            QuestService.VisitRequirement requirement = update.requirement();
            event.getPlayer().sendMessage(Component.text(
                    "Ziel erreicht: X " + requirement.targetX() + " / Z " + requirement.targetZ()
                            + ". Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                    NamedTextColor.YELLOW));
            questUiService.refresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        scheduleFetchSync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        scheduleFetchSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        scheduleFetchSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        scheduleFetchSync(player);
    }

    private void handleQuestGiverDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        String speakerId = chiefService.getChief(villager)
                .map(Chief::chiefId)
                .or(() -> villagerProfileRepository.findByEntityUuid(villager.getUniqueId()).map(profile -> profile.speakerId()))
                .orElse(null);
        if (speakerId == null) {
            return;
        }

        String questGiverName = chiefService.getConversationSpeaker(villager).chatName();
        Collection<Quest> cancelledQuests = questService.cancelActiveQuestsForChief(speakerId);
        for (Quest quest : cancelledQuests) {
            Player player = Bukkit.getPlayer(quest.playerUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            player.sendMessage(Component.text(
                    "Quest abgebrochen: " + quest.title() + ", weil " + questGiverName + " gestorben ist.",
                    NamedTextColor.RED));
            questUiService.refresh(player);
        }
    }

    private void handleVillagerDeathAnnouncement(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        String villagerName = chiefService.getConversationSpeaker(villager).chatName();
        String professionName = formatProfession(villager);
        String cause = event.getEntity().getKiller() == null
                ? "ist gestorben"
                : "ist von einem " + event.getEntity().getKiller().getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " erschlagen worden";
        broadcastVillagerEvent("Der " + professionName + " " + villagerName + " " + cause + ".");
    }

    private String formatProfession(Villager villager) {
        String raw = villager.getProfession().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (raw.isEmpty()) {
            return "Villager";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private void broadcastVillagerEvent(String message) {
        plugin.getLogger().info("[Villager] " + message);
        Component component = Component.text(message, NamedTextColor.RED);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(component);
        }
    }

    private void handlePlayerKillProgress(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        Collection<QuestService.KillQuestUpdate> updates = questService.advanceKillQuests(killer, event.getEntityType());
        for (QuestService.KillQuestUpdate update : updates) {
            Quest quest = update.quest();
            killer.sendMessage(Component.text(
                    "Quest-Fortschritt: " + quest.title() + " (" + quest.progress() + "/" + quest.goal() + ")",
                    NamedTextColor.GREEN));
            if (update.readyToTurnIn()) {
                killer.sendMessage(Component.text(
                        "Ziel erreicht: Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                        NamedTextColor.YELLOW));
            }
            questUiService.refresh(killer);
        }

        if (updates.isEmpty()) {
            hintWrongKillTarget(killer, event.getEntityType());
        }
    }

    private void hintWrongKillTarget(Player player, EntityType killedType) {
        Quest activeQuest = questService.findActiveQuest(player.getUniqueId()).orElse(null);
        if (activeQuest == null || activeQuest.type() != QuestType.KILL) {
            return;
        }
        if (activeQuest.targetKey().equalsIgnoreCase(killedType.name())) {
            return;
        }
        sendThrottledActionBar(player, "Das zaehlt nicht fuer deine aktive Quest. Du brauchst: "
                + formatTargetMob(activeQuest.targetKey()) + " (" + activeQuest.progress() + "/" + activeQuest.goal() + ")");
    }

    private void hintWrongBreedTarget(Player player, EntityType bredType) {
        Quest activeQuest = questService.findActiveQuest(player.getUniqueId()).orElse(null);
        if (activeQuest == null || activeQuest.type() != QuestType.BREED) {
            return;
        }
        if (activeQuest.targetKey().equalsIgnoreCase(bredType.name())) {
            return;
        }
        sendThrottledActionBar(player, "Das zaehlt nicht fuer deine aktive Quest. Du musst zuechten: "
                + formatTargetMob(activeQuest.targetKey()) + " (" + activeQuest.progress() + "/" + activeQuest.goal() + ")");
    }

    private void hintWrongBlockTarget(Player player, org.bukkit.Material placedMaterial) {
        Quest activeQuest = questService.findActiveQuest(player.getUniqueId()).orElse(null);
        if (activeQuest == null) {
            return;
        }
        if (activeQuest.type() == QuestType.BUILD) {
            questService.parseBuildRequirement(activeQuest).ifPresent(requiredMaterial -> {
                if (requiredMaterial != placedMaterial) {
                    sendThrottledActionBar(player, "Das zaehlt nicht. Du musst platzieren: "
                            + formatMaterialName(requiredMaterial) + " (" + activeQuest.progress() + "/" + activeQuest.goal() + ")");
                }
            });
        } else if (activeQuest.type() == QuestType.SECURE) {
            questService.parseSecureRequirement(activeQuest).ifPresent(requirement -> {
                if (requirement.material() != placedMaterial) {
                    sendThrottledActionBar(player, "Das zaehlt nicht. Du musst platzieren: "
                            + formatMaterialName(requirement.material()) + " (" + activeQuest.progress() + "/" + activeQuest.goal() + ")");
                }
            });
        }
    }

    private void sendThrottledActionBar(Player player, String message) {
        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        Long lastHint = lastWrongTargetHintMillis.get(playerUuid);
        if (lastHint != null && now - lastHint < WRONG_TARGET_HINT_COOLDOWN_MILLIS) {
            return;
        }
        lastWrongTargetHintMillis.put(playerUuid, now);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    private String formatTargetMob(String entityTypeName) {
        return entityTypeName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatMaterialName(org.bukkit.Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void scheduleFetchSync(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> handleFetchQuestSync(player));
    }

    private void handleFetchQuestSync(Player player) {
        Collection<QuestService.FetchQuestUpdate> updates = questService.syncActiveFetchQuests(player);
        if (updates.isEmpty()) {
            return;
        }

        for (QuestService.FetchQuestUpdate update : updates) {
            Quest quest = update.quest();
            if (quest.progress() > update.previousProgress()) {
                player.sendMessage(Component.text(
                        "Quest-Fortschritt: " + quest.title() + " (" + quest.progress() + "/" + quest.goal() + ")",
                        NamedTextColor.GREEN));
            }
            if (update.readyToTurnIn() && update.previousProgress() < quest.goal()) {
                player.sendMessage(Component.text(
                        "Ziel erreicht: Kehre zum Questgeber zurueck, um die Quest abzuschliessen.",
                        NamedTextColor.YELLOW));
            }
        }
        questUiService.refresh(player);
    }
}
