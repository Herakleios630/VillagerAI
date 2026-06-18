package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.ai.AIService;
import de.ajsch.villagerai.model.AIReply;
import de.ajsch.villagerai.model.AIRequest;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.ConversationRole;
import de.ajsch.villagerai.model.ConversationTurn;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestDifficultyPreference;
import de.ajsch.villagerai.model.QuestRewardResult;
import de.ajsch.villagerai.model.VillagerContext;
import de.ajsch.villagerai.model.VillageIdentity;
import de.ajsch.villagerai.model.ConversationHistory;
import de.ajsch.villagerai.storage.ConversationHistoryRepository;
import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.service.SpeakerService;
import de.ajsch.villagerai.service.VillageIdentityService;
import java.time.Duration;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class ConversationService {

    private final VillageChiefPlugin plugin;
    private final AIService aiService;
    private final QuestService questService;
    private final QuestDifficultyService questDifficultyService;
    private final QuestOfferService questOfferService;
    private final QuestRewardService questRewardService;
    private final QuestUiService questUiService;
    private final MourningService mourningService;
    private final ReputationService reputationService;
    private final ConversationHistoryRepository conversationHistoryRepository;
    private final VillagerContextService villagerContextService;
    private final SpeakerService speakerService;
    private final ChiefRepository chiefRepository;
    private final VillageIdentityService villageIdentityService;
    private volatile Duration timeout;
    private volatile int maxConcurrentRequests;
    private volatile String waitingMessage;
    private volatile String chiefBusyMessage;
    private volatile String queueFullMessage;
    private volatile double spontaneousQuestOfferChance;
    private volatile long spontaneousQuestOfferCooldownMillis;
    private volatile long spontaneousQuestOfferDeclineCooldownMillis;
    private volatile int spontaneousQuestOfferMinCombinedReputation;
    private volatile int recentChiefRepliesLimit;
    private volatile int recentConversationTurnsLimit;
    private volatile int friendlySpontaneousOfferReputationThreshold;
    private volatile long questCooldownMinutesThresholdSeconds;
    private volatile double repeatFallbackLowHealthThreshold;
    private final ConcurrentMap<UUID, ConversationSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> chiefRequestOwners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> spontaneousQuestOfferCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> legendaryOfferCooldowns = new ConcurrentHashMap<>();
    private final AtomicInteger inFlightRequests = new AtomicInteger();
    private final BukkitTask timeoutTask;
    private final BukkitTask engagementTask;

    public ConversationService(
            VillageChiefPlugin plugin,
            AIService aiService,
            QuestService questService,
            QuestDifficultyService questDifficultyService,
            QuestOfferService questOfferService,
            QuestRewardService questRewardService,
            QuestUiService questUiService,
            MourningService mourningService,
            ReputationService reputationService,
            SpeakerService speakerService,
            ChiefRepository chiefRepository,
            VillageIdentityService villageIdentityService,
            ConversationHistoryRepository conversationHistoryRepository,
            VillagerContextService villagerContextService,
            Duration timeout,
            long checkIntervalSeconds,
            int maxConcurrentRequests,
            String waitingMessage,
            String chiefBusyMessage,
            String queueFullMessage,
            double spontaneousQuestOfferChance,
            long spontaneousQuestOfferCooldownSeconds,
            long spontaneousQuestOfferDeclineCooldownSeconds,
            int spontaneousQuestOfferMinCombinedReputation,
            int recentChiefRepliesLimit,
            int recentConversationTurnsLimit,
            int friendlySpontaneousOfferReputationThreshold,
            long questCooldownMinutesThresholdSeconds,
            double repeatFallbackLowHealthThreshold) {
        this.plugin = plugin;
        this.aiService = aiService;
        this.questService = questService;
        this.questDifficultyService = questDifficultyService;
        this.questOfferService = questOfferService;
        this.questRewardService = questRewardService;
        this.questUiService = questUiService;
        this.mourningService = mourningService;
        this.reputationService = reputationService;
        this.conversationHistoryRepository = conversationHistoryRepository;
        this.villagerContextService = villagerContextService;
        this.speakerService = speakerService;
        this.chiefRepository = chiefRepository;
        this.villageIdentityService = villageIdentityService;
        reloadSettings(new RuntimeSettings(
                timeout,
                maxConcurrentRequests,
                waitingMessage,
                chiefBusyMessage,
                queueFullMessage,
                spontaneousQuestOfferChance,
                spontaneousQuestOfferCooldownSeconds,
                spontaneousQuestOfferDeclineCooldownSeconds,
                spontaneousQuestOfferMinCombinedReputation,
                recentChiefRepliesLimit,
                recentConversationTurnsLimit,
                friendlySpontaneousOfferReputationThreshold,
                questCooldownMinutesThresholdSeconds,
                repeatFallbackLowHealthThreshold));
        long intervalTicks = Math.max(20L, checkIntervalSeconds * 20L);
        this.timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::expireTimedOutConversations, intervalTicks, intervalTicks);
            this.engagementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maintainActiveConversationBehavior, 1L, 2L);
    }

    public void reloadSettings(RuntimeSettings settings) {
        this.timeout = settings.timeout() == null ? Duration.ofSeconds(120L) : settings.timeout();
        this.maxConcurrentRequests = Math.max(1, settings.maxConcurrentRequests());
        this.waitingMessage = settings.waitingMessage();
        this.chiefBusyMessage = settings.chiefBusyMessage();
        this.queueFullMessage = settings.queueFullMessage();
        this.spontaneousQuestOfferChance = Math.max(0.0D, Math.min(1.0D, settings.spontaneousQuestOfferChance()));
        this.spontaneousQuestOfferCooldownMillis = Math.max(0L, settings.spontaneousQuestOfferCooldownSeconds()) * 1000L;
        this.spontaneousQuestOfferDeclineCooldownMillis = Math.max(0L, settings.spontaneousQuestOfferDeclineCooldownSeconds()) * 1000L;
        this.spontaneousQuestOfferMinCombinedReputation = settings.spontaneousQuestOfferMinCombinedReputation();
        this.recentChiefRepliesLimit = Math.max(1, settings.recentChiefRepliesLimit());
        this.recentConversationTurnsLimit = Math.max(1, settings.recentConversationTurnsLimit());
        this.friendlySpontaneousOfferReputationThreshold = settings.friendlySpontaneousOfferReputationThreshold();
        this.questCooldownMinutesThresholdSeconds = Math.max(60L, settings.questCooldownMinutesThresholdSeconds());
        this.repeatFallbackLowHealthThreshold = Math.max(0.0D, Math.min(1.0D, settings.repeatFallbackLowHealthThreshold()));
    }

    public void startConversation(Player player, Villager villager, Speaker speaker) {
        if (villager == null || speaker == null) {
            plugin.getLogger().warning("[ConversationService] startConversation() mit null villager oder speaker aufgerufen – abgebrochen");
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text("Dieser Dorfbewohner kann im Moment nicht antworten.", NamedTextColor.RED));
            }
            return;
        }

        // End any previous conversation before starting a new one
        endConversation(player.getUniqueId());

        // VillageIdentity-Cache im Main-Thread befuellen (vermeidet Async-Chunk-Zugriffe)
        villageIdentityService.resolve(villager);

        String defaultVisibility = plugin.getConfig().getString("conversation.visibility.default-mode", "PUBLIC");
        Set<UUID> participants = new HashSet<>();
        participants.add(player.getUniqueId());
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(),
                speaker,
                villager.getUniqueId(),
                villager.hasAI(),
            villagerContextService.resolve(villager, player.getUniqueId()),
            defaultVisibility,
            participants);
        activeSessions.put(player.getUniqueId(), session);
        prepareVillagerForConversation(villager);
        orientVillagerTowardPlayer(villager, player);
        player.sendMessage(Component.text(
                "Du sprichst jetzt mit " + speaker.chatName() + ". Schreibe im Chat.",
                NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Beende das Gespraech mit /chief exit.", NamedTextColor.GRAY));
        if (speaker.greeting() != null && !speaker.greeting().isBlank()) {
            player.sendMessage(Component.text("[" + speaker.chatName() + "] ", NamedTextColor.GOLD)
                    .append(Component.text(speaker.greeting(), NamedTextColor.WHITE)));
        }

        questService.findActiveQuest(player.getUniqueId()).ifPresent(activeQuest -> {
            if (!activeQuest.speakerId().equals(speaker.speakerId())) {
                String otherQuestGiverName = questService.findQuest(activeQuest.questId())
                        .flatMap(q -> Optional.ofNullable(q.speakerId()))
                        .map(id -> "einem anderen Dorfbewohner")
                        .orElse("einem anderen Dorfbewohner");
                player.sendMessage(Component.text(
                        "Hinweis: Du hast noch eine offene Aufgabe bei " + otherQuestGiverName
                                + " ('" + activeQuest.title() + "'). Schliesse sie erst ab oder brich sie ab.",
                        NamedTextColor.GRAY));
            }
        });

        if (!isCloseEnoughForCompletion(player, villager, speaker.speakerId())) {
            return;
        }

        Collection<Quest> completedTalkQuests = questService.completeActiveTalkQuests(player.getUniqueId(), speaker.speakerId());
        for (Quest completedQuest : completedTalkQuests) {
            player.sendMessage(Component.text("Quest abgeschlossen: " + completedQuest.title(), NamedTextColor.GREEN));
            QuestRewardResult rewardResult = questRewardService.grantRewards(
                    player,
                    completedQuest,
                    reputationService.getVillageScore(player.getUniqueId(), completedQuest.villageId()),
                    reputationService.getSpeakerScore(player.getUniqueId(), completedQuest.speakerId()));
            reputationService.applyQuestCompletion(completedQuest);
                if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                    buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        Collection<QuestService.DeliverQuestUpdate> deliverQuestUpdates = questService.completeActiveDeliverQuests(player, speaker.speakerId());
        for (QuestService.DeliverQuestUpdate deliverQuestUpdate : deliverQuestUpdates) {
            Quest updatedQuest = deliverQuestUpdate.quest();
            if (deliverQuestUpdate.handedInAmount() > 0) {
                player.sendMessage(Component.text(
                        "Abgegeben: insgesamt " + updatedQuest.progress() + " / " + updatedQuest.goal()
                                + " fuer " + updatedQuest.title()
                                + " (diesmal " + deliverQuestUpdate.handedInAmount() + ")",
                        NamedTextColor.GREEN));
            }

            if (!deliverQuestUpdate.completed()) {
                continue;
            }

            player.sendMessage(Component.text("Quest abgeschlossen: " + updatedQuest.title(), NamedTextColor.GREEN));
        QuestRewardResult rewardResult = questRewardService.grantRewards(
            player,
            updatedQuest,
            reputationService.getVillageScore(player.getUniqueId(), updatedQuest.villageId()),
            reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.speakerId()));
            reputationService.applyQuestCompletion(updatedQuest);
                if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                    buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        Collection<QuestService.DeliverQuestUpdate> repairQuestUpdates = questService.completeActiveRepairQuests(player, speaker.speakerId());
        for (QuestService.DeliverQuestUpdate repairQuestUpdate : repairQuestUpdates) {
            Quest updatedQuest = repairQuestUpdate.quest();
            if (repairQuestUpdate.handedInAmount() > 0) {
                player.sendMessage(Component.text(
                        "Reparaturmaterial abgegeben: insgesamt " + updatedQuest.progress() + " / " + updatedQuest.goal()
                                + " fuer " + updatedQuest.title()
                                + " (diesmal " + repairQuestUpdate.handedInAmount() + ")",
                        NamedTextColor.GREEN));
            }

            if (!repairQuestUpdate.completed()) {
                continue;
            }

            player.sendMessage(Component.text("Quest abgeschlossen: " + updatedQuest.title(), NamedTextColor.GREEN));
            QuestRewardResult rewardResult = questRewardService.grantRewards(
                    player,
                    updatedQuest,
                    reputationService.getVillageScore(player.getUniqueId(), updatedQuest.villageId()),
                    reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.speakerId()));
            reputationService.applyQuestCompletion(updatedQuest);
            if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                        buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        Collection<QuestService.BrewQuestUpdate> brewQuestUpdates = questService.completeActiveBrewQuests(player, speaker.speakerId());
        for (QuestService.BrewQuestUpdate brewQuestUpdate : brewQuestUpdates) {
            Quest updatedQuest = brewQuestUpdate.quest();
            if (brewQuestUpdate.handedInAmount() > 0) {
                player.sendMessage(Component.text(
                        "Abgegeben: insgesamt " + updatedQuest.progress() + " / " + updatedQuest.goal()
                                + " fuer " + updatedQuest.title()
                                + " (diesmal " + brewQuestUpdate.handedInAmount() + ")",
                        NamedTextColor.GREEN));
            }

            if (!brewQuestUpdate.completed()) {
                continue;
            }

            player.sendMessage(Component.text("Quest abgeschlossen: " + updatedQuest.title(), NamedTextColor.GREEN));
            QuestRewardResult rewardResult = questRewardService.grantRewards(
                    player,
                    updatedQuest,
                    reputationService.getVillageScore(player.getUniqueId(), updatedQuest.villageId()),
                    reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.speakerId()));
            reputationService.applyQuestCompletion(updatedQuest);
            if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                        buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        questService.syncActiveFetchQuests(player);

        //  LEGENDARY quest completions 
        Collection<QuestService.BrewQuestUpdate> legendaryBlazeUpdates = questService.completeLegendaryBlazeQuests(player, speaker.speakerId());
        for (QuestService.BrewQuestUpdate legendaryUpdate : legendaryBlazeUpdates) {
            Quest updatedQuest = legendaryUpdate.quest();
            if (legendaryUpdate.handedInAmount() > 0) {
                player.sendMessage(Component.text(
                        "Lohenruten abgegeben: insgesamt " + updatedQuest.progress() + " / " + updatedQuest.goal()
                                + " fuer " + updatedQuest.title()
                                + " (diesmal " + legendaryUpdate.handedInAmount() + ")",
                        NamedTextColor.GREEN));
            }
            if (legendaryUpdate.completed()) {
                player.sendMessage(Component.text("Legendäre Quest abgeschlossen: " + updatedQuest.title(), NamedTextColor.GREEN));
                QuestRewardResult rewardResult = questRewardService.grantRewards(
                        player, updatedQuest,
                        reputationService.getVillageScore(player.getUniqueId(), updatedQuest.villageId()),
                        reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.speakerId()));
                reputationService.applyQuestCompletion(updatedQuest);
                if (hasVisibleReward(rewardResult)) {
                    player.sendMessage(Component.text(buildRewardSummary(rewardResult), NamedTextColor.GREEN));
                }
            }
        }
        Collection<QuestService.BrewQuestUpdate> legendaryEndUpdates = questService.completeLegendaryEndQuests(player, speaker.speakerId());
        for (QuestService.BrewQuestUpdate legendaryUpdate : legendaryEndUpdates) {
            Quest updatedQuest = legendaryUpdate.quest();
            if (legendaryUpdate.handedInAmount() > 0) {
                player.sendMessage(Component.text(
                        "End-Beute abgegeben: insgesamt " + updatedQuest.progress() + " / " + updatedQuest.goal()
                                + " fuer " + updatedQuest.title()
                                + " (diesmal " + legendaryUpdate.handedInAmount() + ")",
                        NamedTextColor.GREEN));
            }
            if (legendaryUpdate.completed()) {
                player.sendMessage(Component.text("Legendäre Quest abgeschlossen: " + updatedQuest.title(), NamedTextColor.GREEN));
                QuestRewardResult rewardResult = questRewardService.grantRewards(
                        player, updatedQuest,
                        reputationService.getVillageScore(player.getUniqueId(), updatedQuest.villageId()),
                        reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.speakerId()));
                reputationService.applyQuestCompletion(updatedQuest);
                if (hasVisibleReward(rewardResult)) {
                    player.sendMessage(Component.text(buildRewardSummary(rewardResult), NamedTextColor.GREEN));
                }
            }
        }
        Collection<QuestService.BrewQuestUpdate> legendaryNetherUpdates = questService.completeLegendaryNetherQuests(player, speaker.speakerId());
        for (QuestService.BrewQuestUpdate legendaryUpdate : legendaryNetherUpdates) {
            Quest updatedQuest = legendaryUpdate.quest();
            if (legendaryUpdate.handedInAmount() > 0) {
                player.sendMessage(Component.text(
                        "Nether-Beute abgegeben: insgesamt " + updatedQuest.progress() + " / " + updatedQuest.goal()
                                + " fuer " + updatedQuest.title()
                                + " (diesmal " + legendaryUpdate.handedInAmount() + ")",
                        NamedTextColor.GREEN));
            }
            if (legendaryUpdate.completed()) {
                player.sendMessage(Component.text("Legendäre Quest abgeschlossen: " + updatedQuest.title(), NamedTextColor.GREEN));
                QuestRewardResult rewardResult = questRewardService.grantRewards(
                        player, updatedQuest,
                        reputationService.getVillageScore(player.getUniqueId(), updatedQuest.villageId()),
                        reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.speakerId()));
                reputationService.applyQuestCompletion(updatedQuest);
                if (hasVisibleReward(rewardResult)) {
                    player.sendMessage(Component.text(buildRewardSummary(rewardResult), NamedTextColor.GREEN));
                }
            }
        }

        // Re-scan village-light secure quests: a player may have placed lights
        // elsewhere and now returns to the quest giver.
        questService.syncAllVillageLightProgress(player);

        Collection<Quest> completedInteractionQuests = questService.completeReadyInteractionQuests(
                player.getUniqueId(),
                speaker.speakerId());
        for (Quest completedQuest : completedInteractionQuests) {
            player.sendMessage(Component.text("Quest abgeschlossen: " + completedQuest.title(), NamedTextColor.GREEN));
            QuestRewardResult rewardResult = questRewardService.grantRewards(
                player,
                completedQuest,
                reputationService.getVillageScore(player.getUniqueId(), completedQuest.villageId()),
                reputationService.getSpeakerScore(player.getUniqueId(), completedQuest.speakerId()));
            reputationService.applyQuestCompletion(completedQuest);
                if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                    buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }
        questUiService.refresh(player);
    }

    public boolean hasActiveConversation(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public Optional<ConversationSnapshot> getConversation(UUID playerUuid) {
        ConversationSession session = activeSessions.get(playerUuid);
        if (session == null) {
            return Optional.empty();
        }

        return Optional.of(new ConversationSnapshot(
                session.speaker().speakerId(),
                session.speaker().villageId(),
                Duration.ofMillis(System.currentTimeMillis() - session.lastInteractionMillis().get()).toSeconds(),
                session.visibility()));
    }

    public String describeLegendaryBlocker(Player player, String chiefId, int villageReputationScore, int speakerReputationScore) {
        int maxTier = questDifficultyService.maxTier();
        int legendaryTier = questDifficultyService.legendaryTier();
        if (!questDifficultyService.legendaryEnabled() || legendaryTier <= 0 || legendaryTier > maxTier) {
            return "Legendary deaktiviert.";
        }
        if (villageReputationScore < 100 || speakerReputationScore < 100) {
            return "Ruf zu niedrig: Dorf/Villager muessen jeweils 100 haben.";
        }
        if (player == null || !player.isOnline()) {
            return "Spieler ist nicht online.";
        }
        if (questDifficultyService.requiresNetherAccessForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:story/enter_the_nether")) {
            return "Weltfortschritt fehlt: Nether wurde noch nicht betreten.";
        }
        if (questDifficultyService.requiresEndAccessForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:story/enter_the_end", "minecraft:end/root")) {
            return "Weltfortschritt fehlt: End wurde noch nicht betreten.";
        }
        if (questDifficultyService.requiresDragonKillForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:end/kill_dragon", "minecraft:end/dragon_egg")) {
            return "Weltfortschritt fehlt: Enderdrache noch nicht besiegt.";
        }
        String cooldownKey = player.getUniqueId() + ":" + chiefId;
        Long cooldownUntil = legendaryOfferCooldowns.get(cooldownKey);
        long now = System.currentTimeMillis();
        if (cooldownUntil != null && cooldownUntil > now) {
            long remainingSeconds = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            return "Legendary-Cooldown aktiv: noch " + remainingSeconds + "s.";
        }
        return "Legendary verfuegbar.";
    }

    private String buildLegendaryBlockedReply(Player player, String chiefId, int villageReputationScore, int speakerReputationScore) {
        if (!questDifficultyService.legendaryEnabled()) {
            return "";
        }
        int legendaryTier = questDifficultyService.legendaryTier();
        if (legendaryTier <= 0 || legendaryTier > questDifficultyService.maxTier()) {
            return "";
        }
        if (villageReputationScore < 100 || speakerReputationScore < 100) {
            return "Fuer ganz grosse Auftraege brauche ich noch mehr Vertrauen zu dir. "
                    + "Hilf weiter im Dorf, dann reden wir irgendwann darueber.";
        }
        if (questDifficultyService.requiresNetherAccessForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:story/enter_the_nether")) {
            return "Manche Aufgaben setzen voraus, dass du die Welt jenseits des Portals kennst. "
                    + "Komm wieder, wenn du im Nether warst.";
        }
        if (questDifficultyService.requiresEndAccessForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:story/enter_the_end", "minecraft:end/root")) {
            return "Es gibt Dinge, die nur jemand anfassen kann, der das Ende gesehen hat. "
                    + "Komm zurueck, wenn du im End warst.";
        }
        if (questDifficultyService.requiresDragonKillForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:end/kill_dragon", "minecraft:end/dragon_egg")) {
            return "Solange der Drache lebt, stehe ich vor dir und schweige ueber das, was danach kommt. "
                    + "Besiege ihn, dann reden wir ueber grosse Dinge.";
        }
        return "";
    }

    public void handlePlayerChat(UUID playerUuid, String message) {
        ConversationSession session = activeSessions.get(playerUuid);
        if (session == null) {
            return;
        }

        session.lastInteractionMillis().set(System.currentTimeMillis());

        // Chat-Debug: Spieler-Eingabe loggen (NORMAL und VERBOSE)
        plugin.logChatDebug(VillageChiefPlugin.ChatDebugLevel.NORMAL,
                "[INPUT] " + playerUuid + " -> " + session.speaker().chatName() + ": " + message);

        // Broadcast player message based on visibility
        if ("PUBLIC".equalsIgnoreCase(session.visibility())) {
            Villager villager = resolveVillagerFromSession(session);
            if (villager != null) {
                String playerPrefix = plugin.getConfig().getString("conversation.visibility.public-player-prefix", "sagt");
                Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                if (onlinePlayer != null) {
                    Component playerMsg = Component.text("[" + onlinePlayer.getName() + "] ", NamedTextColor.GREEN)
                            .append(Component.text(playerPrefix + " ", NamedTextColor.WHITE))
                            .append(Component.text(message, NamedTextColor.WHITE));
                    broadcastToNearby(session, villager, playerMsg);
                }
            }
        } else {
            String whisperPrefix = plugin.getConfig().getString("conversation.visibility.whisper-player-prefix", "flÃ¼sterst");
            Player onlinePlayer = Bukkit.getPlayer(playerUuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(Component.text("[Du] ", NamedTextColor.GREEN)
                        .append(Component.text(whisperPrefix + " ", NamedTextColor.GRAY))
                        .append(Component.text(message, NamedTextColor.GRAY)));
            }
        }

        if (handleConversationExitRequest(playerUuid, session, message)) {
            return;
        }

        if (session.awaitingReply().get()) {
            queuePlayerMessage(session, message);
            notifyPlayer(playerUuid, waitingMessage + " Deine letzte Nachricht wird direkt danach vorgemerkt.", NamedTextColor.GRAY);
            return;
        }

        if (handlePendingQuestOffer(playerUuid, session, message)) {
            return;
        }

        if (handleQuestCancellationRequest(playerUuid, session, message)) {
            return;
        }

        if (handleQuestRequest(playerUuid, session, message)) {
            return;
        }

        if (!session.awaitingReply().compareAndSet(false, true)) {
            queuePlayerMessage(session, message);
            notifyPlayer(playerUuid, waitingMessage + " Deine letzte Nachricht wird direkt danach vorgemerkt.", NamedTextColor.GRAY);
            return;
        }

        long playerTurnTimestamp = System.currentTimeMillis();
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.speaker(),
                new ConversationTurn(ConversationRole.PLAYER, message, playerTurnTimestamp));

        String chiefId = session.speaker().speakerId();
        UUID currentOwner = chiefRequestOwners.putIfAbsent(chiefId, playerUuid);
        if (currentOwner != null && !currentOwner.equals(playerUuid)) {
            session.awaitingReply().set(false);
            notifyPlayer(playerUuid, chiefBusyMessage, NamedTextColor.GRAY);
            return;
        }

        int currentInFlight = inFlightRequests.incrementAndGet();
        if (currentInFlight > maxConcurrentRequests) {
            inFlightRequests.decrementAndGet();
            chiefRequestOwners.remove(chiefId, playerUuid);
            session.awaitingReply().set(false);
            notifyPlayer(playerUuid, queueFullMessage, NamedTextColor.GRAY);
            return;
        }

        List<ConversationTurn> historyTurns = conversationHistoryRepository.findHistory(playerUuid, session.speaker().speakerId())
            .map(history -> history.turns())
            .orElse(List.of());
        Collection<ConversationHistory> playerHistories = conversationHistoryRepository.findByPlayerUuid(playerUuid);
        String recentConversation = formatRecentConversation(historyTurns);
        String relationshipMemorySummary = buildRelationshipMemorySummary(session.speaker(), historyTurns, playerHistories);
        List<String> recentChiefReplies = findRecentChiefReplies(historyTurns, recentChiefRepliesLimit);
        int villageReputationScore = reputationService.getVillageScore(playerUuid, session.speaker().villageId());
        String villageReputationSummary = reputationService.getVillageSummary(playerUuid, session.speaker().villageId());
        int speakerReputationScore = reputationService.getSpeakerScore(playerUuid, session.speaker().speakerId());
        String speakerReputationSummary = reputationService.getSpeakerSummary(playerUuid, session.speaker().speakerId());
        int combinedReputationScore = reputationService.getCombinedScore(
            playerUuid,
            session.speaker().villageId(),
            session.speaker().speakerId());
        String combinedReputationSummary = reputationService.getCombinedSummary(
            playerUuid,
            session.speaker().villageId(),
            session.speaker().speakerId());

        VillageIdentity villageIdentity = villageIdentityService.resolveByVillageId(session.speaker().villageId());
                boolean isSmalltalk = isCasualSmalltalk(message) && !isTaskSeeking(message);
                AIRequest request = new AIRequest(
                session.speaker().speakerId(),
                session.speaker().villageId(),
            villageIdentity.villageName(),
            villageIdentity.villageDescription(),
            villageIdentity.villageAttributes(),
            villageIdentity.villageBiome(),
            villageIdentity.villagePopulationEstimate(),
            villageIdentity.villageEventSummary(),
            session.speaker().displayName(),
            session.speaker().role(),
            session.speaker().personality(),
            session.speaker().speechTone(),
            session.speaker().behaviorHint(),
            session.speaker().greeting(),
            session.villagerContext().profession(),
            session.villagerContext().villagerType(),
            session.villagerContext().currentBiome(),
            session.villagerContext().worldName(),
            session.villagerContext().isDay(),
            session.villagerContext().isRaining(),
            session.villagerContext().isThundering(),
            session.villagerContext().currentHealth(),
            session.villagerContext().maxHealth(),
            session.villagerContext().healthRatio(),
            session.villagerContext().ateRecently(),
            session.villagerContext().tradeSummary(),
            session.villagerContext().confinementSummary(),
            session.villagerContext().authoritativeWorldFactsSummary(),
            recentConversation,
            relationshipMemorySummary,
            session.villagerContext().homePoi(),
            session.villagerContext().jobSitePoi(),
            session.villagerContext().potentialJobSitePoi(),
            session.villagerContext().meetingPointPoi(),
                session.villagerContext().mcDay(),
                session.villagerContext().mcTime(),
                villageReputationScore,
                villageReputationSummary,
                speakerReputationScore,
                speakerReputationSummary,
                combinedReputationScore,
                combinedReputationSummary,
                combinedReputationScore,
                combinedReputationSummary,
                !mourningService.isVillageInMourning(session.speaker().villageId()),
                mourningService.isVillageInMourning(session.speaker().villageId()),
                buildChiefLocation(session.speaker()),
                session.speaker().speakerStatus().name(),
                buildChiefNarrative(session.speaker()),
                findChiefAttributes(session.speaker()),
                playerUuid,
                message,
                                plugin.getConfig().getBoolean("memory.enabled", false),
                plugin.getConfig().getStringList("memory.trigger-fallback-phrases"),
                isSmalltalk,
                session.visibility());

        // Chat-Debug: Prompt loggen (nur VERBOSE)
        logChatDebugPrompt(request);

        aiService.generateReply(request).whenComplete((reply, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            ConversationSession currentSession = activeSessions.get(playerUuid);
            if (currentSession == null || !currentSession.conversationId().equals(session.conversationId())) {
                releaseRequestSlot(session, playerUuid);
                return;
            }

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                activeSessions.remove(playerUuid, currentSession);
                releaseRequestSlot(session, playerUuid);
                return;
            }

            if (error != null) {
                plugin.getLogger().warning("AI request failed: " + error.getMessage());
                player.sendMessage(Component.text("Der Haeuptling schweigt gerade. Versuch es gleich noch einmal.", NamedTextColor.RED));
                releaseRequestSlot(session, playerUuid);
                return;
            }

            AIReply safeReply = reply == null ? new AIReply("") : reply;
            String replyText = safeReply.replyText() == null || safeReply.replyText().isBlank()
                    ? "Ich habe gerade nichts zu sagen."
                    : safeReply.replyText();
            replyText = avoidRepeatedReply(replyText, recentChiefReplies, message, session, historyTurns.size());

                conversationHistoryRepository.appendTurn(
                    playerUuid,
                    session.speaker(),
                    new ConversationTurn(ConversationRole.NPC, replyText, System.currentTimeMillis()));

            sendChiefMessage(player, session, replyText, NamedTextColor.WHITE);

            // Chat-Debug: Villager-Antwort + Statusblock loggen (NORMAL und VERBOSE)
            logChatDebugReply(playerUuid, session, replyText);

            maybeTriggerSpontaneousQuestOffer(playerUuid, currentSession, player, message);
            releaseRequestSlot(session, playerUuid);
        }));
    }

    public boolean endConversation(UUID playerUuid) {
        return endConversation(playerUuid, Component.text("Das Gespraech mit dem Haeuptling ist beendet.", NamedTextColor.GRAY));
    }

    public boolean endConversation(UUID playerUuid, Component message) {
        ConversationSession removed = activeSessions.remove(playerUuid);
        if (removed == null) {
            return false;
        }

        restoreVillagerAfterConversation(removed);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
        return true;
    }

    public void recoverAllVillagers() {
        int recovered = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (!villager.hasAI()) {
                    boolean isInActiveSession = activeSessions.values().stream()
                            .anyMatch(session -> session.villagerUuid().equals(villager.getUniqueId()));
                    if (!isInActiveSession) {
                        villager.setAI(true);
                        recovered++;
                    }
                }
            }
        }
        if (recovered > 0) {
            plugin.getLogger().info("[ConversationService] Villager-AI fuer " + recovered + " eingefrorene(r) Villager wiederhergestellt.");
        }
    }

    public void shutdown() {
        timeoutTask.cancel();
        engagementTask.cancel();
        for (ConversationSession session : new ArrayList<>(activeSessions.values())) {
            restoreVillagerAfterConversation(session);
        }
        activeSessions.values().forEach(this::restoreVillagerAfterConversation);
        activeSessions.clear();
        chiefRequestOwners.clear();
        legendaryOfferCooldowns.clear();
        inFlightRequests.set(0);
    }

    private void notifyPlayer(UUID playerUuid, String message, NamedTextColor color) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(message, color));
            }
        });
    }

    private boolean hasVisibleReward(QuestRewardResult rewardResult) {
        return rewardResult.experiencePoints() > 0
                || rewardResult.emeraldsGranted() > 0
                || (rewardResult.bonusItemsSummary() != null && !rewardResult.bonusItemsSummary().isBlank());
    }

    private String buildRewardSummary(QuestRewardResult rewardResult) {
        StringBuilder summary = new StringBuilder("Belohnung: ");
        boolean appended = false;
        if (rewardResult.experiencePoints() > 0) {
            summary.append(rewardResult.experiencePoints()).append(" XP");
            appended = true;
        }
        if (rewardResult.emeraldsGranted() > 0) {
            if (appended) {
                summary.append(", ");
            }
            summary.append(rewardResult.emeraldsGranted()).append(" Emeralds");
            appended = true;
        }
        if (rewardResult.bonusItemsSummary() != null && !rewardResult.bonusItemsSummary().isBlank()) {
            if (appended) {
                summary.append(", ");
            }
            summary.append(rewardResult.bonusItemsSummary());
            appended = true;
        }
        if (Math.abs(rewardResult.quantityMultiplier() - 1.0D) > 0.01D) {
            if (appended) {
                summary.append(" | ");
            }
            summary.append("Rufmenge x")
                    .append(String.format(Locale.ROOT, "%.2f", rewardResult.quantityMultiplier()));
        }
        return summary.toString();
    }

    private void releaseRequestSlot(ConversationSession session, UUID playerUuid) {
        chiefRequestOwners.remove(session.speaker().speakerId(), playerUuid);
        session.awaitingReply().set(false);
        inFlightRequests.updateAndGet(current -> Math.max(0, current - 1));
        drainQueuedPlayerMessage(playerUuid, session);
    }

    private void queuePlayerMessage(ConversationSession session, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        session.queuedPlayerMessage().set(message.trim());
    }

    private void drainQueuedPlayerMessage(UUID playerUuid, ConversationSession session) {
        String queuedMessage = session.queuedPlayerMessage().getAndSet(null);
        if (queuedMessage == null || queuedMessage.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handlePlayerChat(playerUuid, queuedMessage));
    }

    private boolean handleConversationExitRequest(UUID playerUuid, ConversationSession session, String message) {
        if (!looksLikeConversationExit(message)) {
            return false;
        }

        appendPlayerTurn(playerUuid, session, message);
        String farewell = buildConversationFarewell(session, message);
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.speaker(),
                new ConversationTurn(ConversationRole.NPC, farewell, System.currentTimeMillis()));

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                sendChiefMessage(player, session, farewell, NamedTextColor.WHITE);
            }
            endConversation(playerUuid, Component.text("Das Gespraech ist beendet.", NamedTextColor.GRAY));
        });
        return true;
    }

    private boolean handlePendingQuestOffer(UUID playerUuid, ConversationSession session, String message) {
        QuestOfferService.QuestOffer pendingOffer = session.pendingQuestOffer().get();
        if (pendingOffer == null) {
            return false;
        }

        appendPlayerTurn(playerUuid, session, message);
        if (isAffirmative(message)) {
            session.pendingQuestOffer().set(null);
            session.pendingQuestOfferWasSpontaneous().set(false);
            Bukkit.getScheduler().runTask(plugin, () -> acceptPendingOffer(playerUuid, session, pendingOffer));
            return true;
        }
        if (isNegative(message)) {
            boolean wasSpontaneous = session.pendingQuestOfferWasSpontaneous().get();
            session.pendingQuestOffer().set(null);
            session.pendingQuestOfferWasSpontaneous().set(false);
            if (wasSpontaneous) {
                setSpontaneousOfferCooldown(playerUuid, session.speaker().speakerId(), spontaneousQuestOfferDeclineCooldownMillis);
            }
            sendGeneratedChiefReply(playerUuid, session, "In Ordnung. Dann vielleicht spaeter.");
            return true;
        }

        sendGeneratedChiefReply(playerUuid, session, "Antworte klar mit ja oder nein, wenn du den Auftrag annehmen willst.");
        return true;
    }

    private boolean handleQuestRequest(UUID playerUuid, ConversationSession session, String message) {
        if (!looksLikeQuestRequest(message)) {
            return false;
        }

        appendPlayerTurn(playerUuid, session, message);
        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                playerUuid,
                session.speaker().speakerId());
        if (!availability.allowed()) {
            sendGeneratedChiefReply(playerUuid, session, buildQuestUnavailableReply(availability));
            return true;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            sendGeneratedChiefReply(playerUuid, session, "Ich kann dich gerade nicht erreichen.");
            return true;
        }

        int villageReputationScore = reputationService.getVillageScore(playerUuid, session.speaker().villageId());
        int speakerReputationScore = reputationService.getSpeakerScore(playerUuid, session.speaker().speakerId());
        int unlockedTier = Math.min(
            questDifficultyService.resolveUnlockedTier(villageReputationScore),
            computeLegendaryAllowedTier(player, session.speaker().speakerId(), villageReputationScore, speakerReputationScore));
        int maxTier = questDifficultyService.maxTier();
        int preferredTier = questDifficultyService.getPreference(playerUuid, session.speaker().speakerId()).preferredDifficultyTier();

        String legendaryHint = "";
        if (unlockedTier < maxTier && preferredTier >= maxTier) {
            legendaryHint = buildLegendaryBlockedReply(player, session.speaker().speakerId(), villageReputationScore, speakerReputationScore);
        }

        QuestOfferService.QuestOffer offer = questOfferService.createOfferForTier(
            playerUuid,
            session.speaker(),
            session.villagerContext(),
            Math.min(preferredTier, unlockedTier));
        if (offer == null) {
            sendGeneratedChiefReply(playerUuid, session,
                    "Unser Dorf ist bereits sicher und hell. Ich habe im Moment keine Aufgabe fuer dich.");
            return true;
        }
        session.pendingQuestOffer().set(offer);
    session.pendingQuestOfferWasSpontaneous().set(false);

        String reply = offer.promptText();
        if (!legendaryHint.isBlank()) {
            reply = reply + " " + legendaryHint;
        }
        sendGeneratedChiefReply(playerUuid, session, reply);
        return true;
    }

    private boolean handleQuestCancellationRequest(UUID playerUuid, ConversationSession session, String message) {
        if (!looksLikeQuestCancellation(message)) {
            return false;
        }

        appendPlayerTurn(playerUuid, session, message);
        Quest activeQuest = questService.findActiveQuest(playerUuid).orElse(null);
        if (activeQuest == null) {
            sendGeneratedChiefReply(playerUuid, session, "Du hast von mir gerade keine Aufgabe offen, die ich streichen koennte.");
            return true;
        }
        if (!activeQuest.speakerId().equals(session.speaker().speakerId())) {
            sendGeneratedChiefReply(
                    playerUuid,
                    session,
                    "Wenn du diese Aufgabe fallen lassen willst, musst du das mit dem Questgeber klaeren, der sie dir gegeben hat.");
            return true;
        }

        Quest cancelledQuest = questService.cancelActiveQuest(playerUuid).orElse(null);
        if (cancelledQuest == null) {
            sendGeneratedChiefReply(playerUuid, session, "Gerade liegt keine Aufgabe vor mir, die ich fuer dich streichen koennte.");
            return true;
        }

        sendGeneratedChiefReply(
                playerUuid,
                session,
                "In Ordnung. Ich streiche dir den Auftrag '" + cancelledQuest.title() + "'. Wenn du spaeter wieder helfen willst, frag einfach neu.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            questUiService.refresh(player);
            player.sendMessage(Component.text("Quest abgebrochen: " + cancelledQuest.title(), NamedTextColor.YELLOW));
        });
        return true;
    }

    private void acceptPendingOffer(UUID playerUuid, ConversationSession session, QuestOfferService.QuestOffer pendingOffer) {
        ConversationSession currentSession = activeSessions.get(playerUuid);
        if (currentSession == null || !currentSession.conversationId().equals(session.conversationId())) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        questService.syncActiveFetchQuests(player);
        Quest quest = questOfferService.acceptOffer(player, session.speaker(), pendingOffer);
        if (pendingOffer.difficultyTier() >= questDifficultyService.legendaryTier()) {
            setLegendaryOfferCooldown(playerUuid, session.speaker().speakerId(), questDifficultyService.legendaryCooldownMillis());
        }
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.speaker(),
                new ConversationTurn(ConversationRole.NPC, pendingOffer.acceptedReplyText(), System.currentTimeMillis()));
        sendChiefMessage(player, session, pendingOffer.acceptedReplyText(), NamedTextColor.WHITE);
        player.sendMessage(Component.text("Quest angenommen: " + quest.title(), NamedTextColor.GREEN));

        // village-light: give a practical hint about the sub-area
        if (questService.isVillageLightSecureQuest(quest)) {
            String[] parts = quest.targetKey().split("\\|");
            if (parts.length >= 9) {
                try {
                    int initialDark = Integer.parseInt(parts[5]);
                    int cx = Integer.parseInt(parts[6]);
                    int cz = Integer.parseInt(parts[8]);
                    int distance = (int) Math.round(player.getLocation().distance(
                            new org.bukkit.Location(player.getWorld(), cx + 0.5D, 64.0D, cz + 0.5D)));
                    player.sendMessage(Component.text(
                            initialDark + " dunkle Stellen im Zielbereich (~" + distance + "m entfernt). "
                                    + "Setze beliebige Lichtquellen (Fackeln, Laternen, Glowstone) – "
                                    + "die Bossbar zeigt deinen Fortschritt.",
                            NamedTextColor.GRAY));
                } catch (NumberFormatException ignored) {
                    player.sendMessage(Component.text(
                            "Setze beliebige Lichtquellen im Zielbereich – die Bossbar zeigt deinen Fortschritt.",
                            NamedTextColor.GRAY));
                }
            } else {
                player.sendMessage(Component.text(
                        "Setze beliebige Lichtquellen im Zielbereich – die Bossbar zeigt deinen Fortschritt.",
                        NamedTextColor.GRAY));
            }
        }

        questUiService.refresh(player);
    }

    private void appendPlayerTurn(UUID playerUuid, ConversationSession session, String message) {
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.speaker(),
                new ConversationTurn(ConversationRole.PLAYER, message, System.currentTimeMillis()));
    }

    private void sendGeneratedChiefReply(UUID playerUuid, ConversationSession session, String replyText) {
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.speaker(),
                new ConversationTurn(ConversationRole.NPC, replyText, System.currentTimeMillis()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            sendChiefMessage(player, session, replyText, NamedTextColor.WHITE);
        });
    }

    private void maybeTriggerSpontaneousQuestOffer(
            UUID playerUuid,
            ConversationSession session,
            Player player,
            String playerMessage) {
        if (spontaneousQuestOfferChance <= 0.0D || session.pendingQuestOffer().get() != null) {
            return;
        }
        if (questService.findActiveQuest(playerUuid).isPresent()) {
            return;
        }
        if (looksLikeQuestRequest(playerMessage)
                || looksLikeQuestCancellation(playerMessage)
                || isAffirmative(playerMessage)
                || isNegative(playerMessage)) {
            return;
        }

        int combinedReputation = reputationService.getCombinedScore(
                playerUuid,
                session.speaker().villageId(),
                session.speaker().speakerId());
        if (combinedReputation < spontaneousQuestOfferMinCombinedReputation) {
            return;
        }

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                playerUuid,
                session.speaker().speakerId());
        if (!availability.allowed()) {
            return;
        }

        String cooldownKey = playerUuid + ":" + session.speaker().speakerId();
        long now = System.currentTimeMillis();
        Long cooldownUntil = spontaneousQuestOfferCooldowns.get(cooldownKey);
        if (cooldownUntil != null && cooldownUntil > now) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= spontaneousQuestOfferChance) {
            return;
        }

        int villageReputationScore = reputationService.getVillageScore(playerUuid, session.speaker().villageId());
        int speakerReputation = reputationService.getSpeakerScore(playerUuid, session.speaker().speakerId());
        QuestDifficultyPreference difficultyPreference = questDifficultyService.getPreference(playerUuid, session.speaker().speakerId());
        int unlockedTier = Math.min(
            questDifficultyService.resolveUnlockedTier(villageReputationScore),
            computeLegendaryAllowedTier(player, session.speaker().speakerId(), villageReputationScore, speakerReputation));
        int preferredTier = questDifficultyService.clampTier(difficultyPreference.preferredDifficultyTier());

        boolean offerChallengeTier = questDifficultyService.challengeOffersEnabled()
            && unlockedTier > preferredTier
            && difficultyPreference.lastSuggestedAtEpochMillis() + questDifficultyService.challengeOfferCooldownMillis() <= now
            && ThreadLocalRandom.current().nextDouble() < questDifficultyService.challengeOfferChance();

        QuestOfferService.QuestOffer offer = offerChallengeTier
            ? questOfferService.createOfferForTier(
                playerUuid,
                session.speaker(),
                session.villagerContext(),
                Math.min(unlockedTier, preferredTier + 1))
            : questOfferService.createOffer(
                playerUuid,
                session.speaker(),
                session.villagerContext(),
                villageReputationScore);
        if (offer == null) {
            return;
        }
        session.pendingQuestOffer().set(offer);
        session.pendingQuestOfferWasSpontaneous().set(true);
        setSpontaneousOfferCooldown(playerUuid, session.speaker().speakerId(), spontaneousQuestOfferCooldownMillis);

        String offerIntro;
        if (offerChallengeTier) {
            offerIntro = "Wenn du willst, haette ich diesmal einen etwas schwierigeren Auftrag fuer dich. ";
        } else {
            offerIntro = combinedReputation >= friendlySpontaneousOfferReputationThreshold
                ? "Warte, ehe du gehst: Vielleicht koenntest du mir sogar helfen. "
                : "Wenn du schon hier bist, haette ich tatsaechlich etwas fuer dich. ";
        }
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.speaker(),
                new ConversationTurn(ConversationRole.NPC, offerIntro + offer.promptText(), System.currentTimeMillis()));
        sendChiefMessage(player, session, offerIntro + offer.promptText(), NamedTextColor.WHITE);
    }

    private int computeLegendaryAllowedTier(Player player, String chiefId, int villageReputationScore, int speakerReputationScore) {
        int maxTier = questDifficultyService.maxTier();
        if (maxTier <= 0) {
            return 0;
        }
        int legendaryTier = questDifficultyService.legendaryTier();
        if (!questDifficultyService.legendaryEnabled() || legendaryTier <= 0 || legendaryTier > maxTier) {
            return maxTier;
        }

        int nonLegendaryTier = Math.max(0, legendaryTier - 1);
        if (villageReputationScore < 100 || speakerReputationScore < 100) {
            return nonLegendaryTier;
        }
        if (player == null || !player.isOnline()) {
            return nonLegendaryTier;
        }
        if (!isLegendaryWorldProgressReady(player)) {
            return nonLegendaryTier;
        }
        if (!isLegendaryCooldownReady(player.getUniqueId(), chiefId, System.currentTimeMillis())) {
            return nonLegendaryTier;
        }
        return maxTier;
    }

    private boolean isLegendaryWorldProgressReady(Player player) {
        if (questDifficultyService.requiresNetherAccessForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:story/enter_the_nether")) {
            return false;
        }
        if (questDifficultyService.requiresEndAccessForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:story/enter_the_end", "minecraft:end/root")) {
            return false;
        }
        if (questDifficultyService.requiresDragonKillForLegendary()
                && !hasAnyCompletedAdvancement(player, "minecraft:end/kill_dragon", "minecraft:end/dragon_egg")) {
            return false;
        }
        return true;
    }

    private boolean hasAnyCompletedAdvancement(Player player, String... advancementKeys) {
        for (String advancementKey : advancementKeys) {
            NamespacedKey key = NamespacedKey.fromString(advancementKey);
            if (key == null) {
                continue;
            }
            Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement != null && player.getAdvancementProgress(advancement).isDone()) {
                return true;
            }
        }
        return false;
    }

    private boolean isLegendaryCooldownReady(UUID playerUuid, String chiefId, long now) {
        String cooldownKey = playerUuid + ":" + chiefId;
        Long cooldownUntil = legendaryOfferCooldowns.get(cooldownKey);
        return cooldownUntil == null || cooldownUntil <= now;
    }

    private void setLegendaryOfferCooldown(UUID playerUuid, String chiefId, long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            return;
        }
        String cooldownKey = playerUuid + ":" + chiefId;
        long targetUntil = System.currentTimeMillis() + cooldownMillis;
        legendaryOfferCooldowns.compute(cooldownKey, (ignored, existingUntil) -> {
            if (existingUntil == null || existingUntil < targetUntil) {
                return targetUntil;
            }
            return existingUntil;
        });
    }

    private void setSpontaneousOfferCooldown(UUID playerUuid, String chiefId, long cooldownMillis) {
        String cooldownKey = playerUuid + ":" + chiefId;
        long now = System.currentTimeMillis();
        spontaneousQuestOfferCooldowns.compute(cooldownKey, (ignored, existingUntil) -> {
            long targetUntil = now + cooldownMillis;
            if (existingUntil == null || existingUntil < targetUntil) {
                return targetUntil;
            }
            return existingUntil;
        });
    }

    private void broadcastToNearby(ConversationSession session, Villager villager, Component message) {
        double radius = plugin.getConfig().getDouble("conversation.visibility.public-radius-blocks", 50);
        Location loc = villager.getLocation();
        if (loc.getWorld() == null) return;
        for (Player nearby : loc.getWorld().getNearbyPlayers(loc, radius)) {
            if (session.participants().contains(nearby.getUniqueId())) {
                nearby.sendMessage(message);
            }
        }
    }

    private void spawnConversationParticles(ConversationSession session) {
        if (!plugin.getConfig().getBoolean("conversation.visibility.particles.enabled", true)) return;

        Villager villager = resolveVillagerFromSession(session);
        if (villager == null || !villager.isValid()) return;

        boolean isPublic = "PUBLIC".equalsIgnoreCase(session.visibility());
        String particleName = isPublic
            ? plugin.getConfig().getString("conversation.visibility.particles.public-particle", "VILLAGER_HAPPY")
            : plugin.getConfig().getString("conversation.visibility.particles.whisper-particle", "SOUL");

        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unbekannter Particle-Typ in Config: " + particleName);
            return;
        }

        Location loc = villager.getEyeLocation().add(0, 0.4, 0);
        int count = plugin.getConfig().getInt("conversation.visibility.particles.particle-count", 4);
        villager.getWorld().spawnParticle(particle, loc, count, 0.2, 0.2, 0.2, 0.02);
    }

    private Villager resolveVillagerFromSession(ConversationSession session) {
        String speakerId = session.speaker().speakerId();
        if (speakerId.startsWith("villager-")) {
            try {
                UUID entityUuid = UUID.fromString(speakerId.substring("villager-".length()));
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
                if (entity instanceof Villager v && v.isValid()) {
                    return v;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private void sendChiefMessage(Player player, ConversationSession session, String replyText, NamedTextColor color) {
        boolean isPublic = "PUBLIC".equalsIgnoreCase(session.visibility());
        String prefix = isPublic
            ? plugin.getConfig().getString("conversation.visibility.public-chief-prefix", "sagt")
            : plugin.getConfig().getString("conversation.visibility.whisper-chief-prefix", "flÃ¼stert");

        Component message = Component.text("[" + session.speaker().chatName() + "] ", NamedTextColor.GOLD)
                .append(Component.text(prefix + " ", isPublic ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                .append(Component.text(replyText, color));

        if (isPublic) {
            Villager villager = resolveVillagerFromSession(session);
            if (villager != null) {
                broadcastToNearby(session, villager, message);
            } else {
                player.sendMessage(message);
            }
        } else {
            player.sendMessage(message);
        }

        spawnConversationParticles(session);
    }

    private boolean looksLikeQuestRequest(String message) {
        String normalized = normalizeForIntent(message);
        return normalized.contains("aufgabe")
                || normalized.contains("auftrag")
                || normalized.contains("quest")
                || normalized.contains("arbeit fuer mich")
                || normalized.contains("was kann ich tun")
                || normalized.contains("kann ich helfen")
                || normalized.contains("brauchst du etwas");
    }

    private boolean looksLikeQuestCancellation(String message) {
        String normalized = normalizeForIntent(message);
        return (normalized.contains("quest") || normalized.contains("auftrag") || normalized.contains("aufgabe"))
                        && (normalized.contains("abbrechen")
                        || normalized.contains("abbruch")
                        || normalized.contains("stornieren")
                        || normalized.contains("fallen lassen"))
                || normalized.contains("ich will abbrechen")
                || normalized.contains("ich moechte abbrechen")
                || normalized.contains("ich möchte abbrechen")
                || normalized.contains("brich die quest ab")
                || normalized.contains("kann ich abbrechen");
    }

    private boolean looksLikeConversationExit(String message) {
        String normalized = normalizeForIntent(message);
        return matchesAny(normalized,
                "tschuss",
                "tschuess",
                "tschuess",
                "tschus",
                "auf wiedersehen",
                "auf wiedersehn",
                "bis bald",
                "bis spater",
                "bis spaeter",
                "bis dann",
                "machs gut",
                "mach s gut",
                "man sieht sich",
                "wir sehen uns",
                "leb wohl",
                "ich gehe dann",
                "ich geh dann",
                "ich bin dann weg",
                "ich muss los",
                "ich muss weiter",
                "ciao",
                "adios",
                "bye",
                "bis zum nachsten mal",
                "bis zum naechsten mal");
    }

    private String buildConversationFarewell(ConversationSession session, String message) {
        String normalized = normalizeForIntent(message);
        String[] variants = normalized.contains("bis bald")
                || normalized.contains("bis dann")
                || normalized.contains("wir sehen uns")
                || normalized.contains("man sieht sich")
                ? new String[] {
                    "Bis bald dann.",
                    "Gut. Dann sehen wir uns spaeter wieder.",
                    "In Ordnung. Komm spaeter wieder vorbei."
                }
                : new String[] {
                    "In Ordnung. Geh deinen Weg.",
                    "Dann leb wohl fuer jetzt.",
                    "Gut. Dann beenden wir es hier."
                };
        return variants[Math.floorMod(session.speaker().speakerId().hashCode() + normalized.hashCode(), variants.length)];
    }

    private boolean isAffirmative(String message) {
        String normalized = normalizeForIntent(message);
        return matchesAny(normalized,
                "ja",
                "jo",
                "jup",
                "jawohl",
                "ok",
                "okay",
                "klar",
                "sicher",
                "gern",
                "gerne",
                "natuerlich",
                "na klar",
                "aber klar",
                "natuerlich mache ich das",
                "ich mache das",
                "ich mach das",
                "mach ich",
                "mache ich",
                "ich uebernehme das",
                "ich ubernehme das",
                "das mache ich",
                "das mach ich",
                "einverstanden",
                "passt");
    }

    private boolean isNegative(String message) {
        String normalized = normalizeForIntent(message);
        return matchesAny(normalized,
                "nein",
                "nee",
                "no",
                "auf keinen fall",
                "auf gar keinen fall",
                "lieber nicht",
                "nicht jetzt",
                "spaeter",
                "spater",
                "kein interesse",
                "ich will nicht",
                "ich moechte nicht",
                "ich möchte nicht",
                "das will ich nicht",
                "eher nicht",
                "auf keinen",
                "kommt nicht in frage");
    }

    private String normalizeForIntent(String message) {
        return Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean matchesAny(String normalized, String... phrases) {
        for (String phrase : phrases) {
            if (normalized.equals(phrase) || normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private void expireTimedOutConversations() {
        long now = System.currentTimeMillis();
        for (UUID playerUuid : activeSessions.keySet()) {
            ConversationSession session = activeSessions.get(playerUuid);
            if (session == null) {
                continue;
            }

            long idleMillis = now - session.lastInteractionMillis().get();
            if (idleMillis >= timeout.toMillis()) {
                endConversation(playerUuid, Component.text(
                        "Das Gespraech mit dem Haeuptling ist wegen Inaktivitaet beendet worden.",
                        NamedTextColor.GRAY));
            }
        }
    }

    private void maintainActiveConversationBehavior() {
        for (UUID playerUuid : activeSessions.keySet()) {
            ConversationSession session = activeSessions.get(playerUuid);
            if (session == null) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                restoreVillagerAfterConversation(session);
                activeSessions.remove(playerUuid, session);
                continue;
            }

            if (!(Bukkit.getEntity(session.villagerUuid()) instanceof Villager villager) || !villager.isValid() || villager.isDead()) {
                endConversation(playerUuid, Component.text(
                        "Das Gespraech ist beendet worden, weil der Dorfbewohner nicht mehr verfuegbar ist.",
                        NamedTextColor.GRAY));
                continue;
            }

            if (!villager.getWorld().equals(player.getWorld())) {
                continue;
            }

            orientVillagerTowardPlayer(villager, player);
            dampenVillagerDrift(villager);
        }
    }

    private void orientVillagerTowardPlayer(Villager villager, Player player) {
        Location villagerEyes = villager.getEyeLocation();
        Location playerEyes = player.getEyeLocation();
        Vector direction = playerEyes.toVector().subtract(villagerEyes.toVector());
        if (direction.lengthSquared() <= 0.0001D) {
            return;
        }

        double horizontalLength = Math.sqrt((direction.getX() * direction.getX()) + (direction.getZ() * direction.getZ()));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.getY(), horizontalLength));
        villager.setRotation(yaw, pitch);
    }

    private boolean isCloseEnoughForCompletion(Player player, Villager villager, String chiefId) {
        double distance = player.getLocation().distance(villager.getLocation());
        if (distance <= 8.0D) {
            return true;
        }

        Optional<Quest> activeQuest = questService.findActiveQuest(player.getUniqueId());
        if (activeQuest.isEmpty() || !activeQuest.get().speakerId().equals(chiefId)) {
            return true;
        }

        player.sendMessage(Component.text(
                "Du bist zu weit vom Questgeber entfernt, um die Quest abzuschliessen. ("
                        + (int) distance + "m, maximal 8m)",
                NamedTextColor.RED));
        return false;
    }

    private void prepareVillagerForConversation(Villager villager) {
        villager.setAI(false);
        villager.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
    }

    private void restoreVillagerAfterConversation(ConversationSession session) {
        if (!(Bukkit.getEntity(session.villagerUuid()) instanceof Villager villager) || !villager.isValid() || villager.isDead()) {
            return;
        }

        villager.setAI(session.hadAiEnabledBeforeConversation());
    }

    private void dampenVillagerDrift(Villager villager) {
        Vector velocity = villager.getVelocity();
        if (Math.abs(velocity.getX()) < 0.01D && Math.abs(velocity.getZ()) < 0.01D) {
            return;
        }

        villager.setVelocity(new Vector(0.0D, velocity.getY(), 0.0D));
    }

    private String formatRecentConversation(List<ConversationTurn> turns) {
        if (turns.isEmpty()) {
            return "noch keine fruehere Unterhaltung mit diesem Spieler";
        }

        List<ConversationTurn> compactTurns = turns.size() <= recentConversationTurnsLimit
                ? turns
            : turns.subList(turns.size() - recentConversationTurnsLimit, turns.size());
        List<String> condensed = new ArrayList<>();
        String previousNormalized = null;
        for (ConversationTurn turn : compactTurns) {
            String line = (turn.role() == ConversationRole.PLAYER ? "Spieler" : turn.role().name()) + ": " + turn.message();
            String normalized = normalizeReplyForRepeatCheck(line);
            if (normalized.equals(previousNormalized)) {
                continue;
            }
            condensed.add(line);
            previousNormalized = normalized;
        }

        return condensed.stream()
                .reduce((left, right) -> left + " | " + right)
                .orElse("noch keine fruehere Unterhaltung mit diesem Spieler");
    }

    private String buildRelationshipMemorySummary(
            Speaker speaker,
            List<ConversationTurn> historyTurns,
            Collection<ConversationHistory> playerHistories) {
        long knownSpeakers = playerHistories == null
                ? 0L
                : playerHistories.stream()
                        .filter(history -> history != null && history.turns() != null && !history.turns().isEmpty())
                        .count();
        if (historyTurns.isEmpty()) {
            if (knownSpeakers > 1L) {
                return "Der Spieler ist im Dorf nicht voellig unbekannt, aber ihr habt selbst noch kaum direkt gesprochen.";
            }
            return "Dieser Spieler ist fuer dich noch weitgehend neu.";
        }

        long playerTurns = historyTurns.stream()
                .filter(turn -> turn.role() == ConversationRole.PLAYER)
                .count();
        long speakerTurns = historyTurns.size() - playerTurns;
        long lastSeenMinutes = Math.max(
                0L,
                (System.currentTimeMillis() - historyTurns.get(historyTurns.size() - 1).timestampEpochMillis()) / 60000L);

        String recency = lastSeenMinutes <= 10L
                ? "Ihr habt erst kuerzlich miteinander gesprochen."
                : lastSeenMinutes <= 180L
                        ? "Euer letztes Gespraech liegt noch nicht lange zurueck."
                        : "Euer letztes Gespraech liegt schon eine Weile zurueck.";

        if (historyTurns.size() < 4) {
            return recency + " Du kennst den Spieler erst oberflaechlich und erinnerst dich nur an wenige direkte Worte.";
        }
        if (historyTurns.size() < 10) {
            return recency + " Du kennst den Spieler inzwischen fluechtig und erkennst seinen Umgangston wieder.";
        }

        String speakerName = speaker == null ? "du" : speaker.chatName();
        return recency + " Zwischen " + speakerName + " und diesem Spieler gibt es schon eine merkbare gemeinsame Gespraechsgeschichte; antworte so, als erkennst du ihn wieder, ohne falsche Details zu erfinden."
                + " Bisherige direkte Wortwechsel: Spieler " + playerTurns + ", Dorfbewohner " + speakerTurns + ".";
    }

    private String buildQuestUnavailableReply(QuestService.TalkQuestAvailability availability) {
        return switch (availability.failureReason()) {
            case ACTIVE_QUEST -> {
                if (availability.failureMessage() != null && !availability.failureMessage().isBlank()) {
                    yield availability.failureMessage();
                }
                String questTitle = availability.activeQuestTitle();
                if (questTitle == null || questTitle.isBlank()) {
                    yield "Du hast noch eine andere Aufgabe offen. Erledige sie erst oder brich sie ab, dann reden wir weiter ueber neue Auftraege.";
                }
                yield "Du hast bereits eine andere Aufgabe offen: '" + questTitle
                        + "'. Bring das erst zu Ende oder brich sie ab, bevor du noch mehr annimmst.";
            }
            case COOLDOWN -> {
                long remainingSeconds = Math.max(1L, availability.remainingSeconds());
                if (remainingSeconds >= questCooldownMinutesThresholdSeconds) {
                    long remainingMinutes = (remainingSeconds + 59L) / 60L;
                    yield "Ich habe im Moment keine neue Aufgabe fuer dich. Komm in etwa "
                            + remainingMinutes + " Minuten noch einmal auf mich zu.";
                }
                yield "Ich habe gerade keine neue Aufgabe fuer dich. Versuch es in "
                        + remainingSeconds + " Sekunden noch einmal.";
            }
            case NONE -> availability.failureMessage() == null || availability.failureMessage().isBlank()
                    ? "Gerade habe ich keine Aufgabe fuer dich."
                    : availability.failureMessage();
        };
    }

    private List<String> findRecentChiefReplies(List<ConversationTurn> turns, int limit) {
        List<String> replies = new ArrayList<>();
        for (int index = turns.size() - 1; index >= 0 && replies.size() < limit; index--) {
            ConversationTurn turn = turns.get(index);
            if (turn.role() != ConversationRole.NPC || turn.message() == null || turn.message().isBlank()) {
                continue;
            }

            String normalized = normalizeReplyForRepeatCheck(turn.message());
            if (!normalized.isBlank()) {
                replies.add(normalized);
            }
        }
        return replies;
    }

    private String avoidRepeatedReply(
            String replyText,
            List<String> recentChiefReplies,
            String playerMessage,
            ConversationSession session,
            int historySize) {
        String normalizedReply = normalizeReplyForRepeatCheck(replyText);
        if (normalizedReply.isBlank() || !recentChiefReplies.contains(normalizedReply)) {
            return replyText;
        }

        return buildRepeatSafeFallback(playerMessage, session, historySize);
    }

    private String buildRepeatSafeFallback(String playerMessage, ConversationSession session, int historySize) {
        String normalizedMessage = normalizeForIntent(playerMessage);
        if (normalizedMessage.contains("wie geht") || normalizedMessage.contains("dein tag") || normalizedMessage.contains("befinden")) {
            if (session.villagerContext().healthRatio() < repeatFallbackLowHealthThreshold) {
                return "Heute bin ich nicht gut beisammen. Lass uns lieber kurz und klar reden.";
            }
            if (!session.villagerContext().ateRecently()) {
                return "Mir fehlt gerade die Ruhe fuer langes Gerede. Komm auf den Punkt.";
            }

            String[] variants = {
                "Es geht schon. Was willst du eigentlich wissen?",
                "Schon besser, wenn man mich nicht im Kreis reden laesst. Was brauchst du?",
                "Ich halte mich auf den Beinen. Sag, worauf du hinauswillst."
            };
            return variants[Math.floorMod(session.speaker().speakerId().hashCode() + historySize, variants.length)];
        }

        String[] variants;
        if (historySize > 10) {
            variants = new String[]{
                "Wir kennen uns schon eine Weile. Du weisst, ich wiederhole mich nicht gern. Komm zur Sache.",
                "Alte Geschichten muessen wir nicht nochmal durchkauen. Was willst du wirklich wissen?",
                "Ich hab's schon gesagt. Also: weiter im Text."
            };
        } else {
            variants = new String[]{
                "Lass mich anders fragen: Was moechtest du als naechstes wissen?",
                "Ich wiederhole mich. Also: Was ist dein naechstes Anliegen?",
                "Reden wir ueber etwas anderes. Was brennt dir auf der Seele?"
            };
        }
        return variants[Math.floorMod(session.speaker().speakerId().hashCode() + historySize, variants.length)];
    }

    private void logChatDebugReply(UUID playerUuid, ConversationSession session, String replyText) {
        plugin.logChatDebug(VillageChiefPlugin.ChatDebugLevel.NORMAL,
                "[OUTPUT] " + session.speaker().chatName() + " -> Spieler " + playerUuid + ": " + replyText);

        // Statusblock bauen
        StringBuilder status = new StringBuilder();
        status.append("[STATUS]");
        status.append(" mourning=").append(mourningService.isVillageInMourning(session.speaker().villageId()));

        // Chief alive/not null check
        boolean chiefExists = false;
        boolean chiefAlive = false;
        if (Bukkit.getEntity(session.villagerUuid()) instanceof org.bukkit.entity.LivingEntity living) {
            chiefExists = true;
            chiefAlive = !living.isDead();
        }
        status.append(" villager-exists=").append(chiefExists);
        status.append(" villager-alive=").append(chiefAlive);

        // Aktive Quests des Spielers
        int activeQuestCount = questService.findPlayerQuests(playerUuid).size();
        status.append(" active-quests=").append(activeQuestCount);

        // Aktueller Quest-Status
        questService.findActiveQuest(playerUuid).ifPresentOrElse(
                quest -> status.append(" current-quest=").append(quest.title())
                        .append(" progress=").append(quest.progress())
                        .append("/").append(quest.goal())
                        .append(" status=").append(quest.status()),
                () -> status.append(" current-quest=none"));

        plugin.logChatDebug(VillageChiefPlugin.ChatDebugLevel.NORMAL, status.toString());
    }

    public void logChatDebugPrompt(AIRequest request) {
        if (request == null) return;
        boolean memoryEnabled = plugin.getConfig().getBoolean("memory.enabled", false);
        String aiProvider = plugin.getConfig().getString("ai.provider", "dummy");
        int messageLength = request.playerMessage() == null ? 0 : request.playerMessage().length();
        int recentConversationLength = request.recentConversation() == null ? 0 : request.recentConversation().length();
        int relationshipMemoryLength = request.relationshipMemorySummary() == null ? 0 : request.relationshipMemorySummary().length();
        boolean likelyMemoryTrigger = looksLikeMemoryRecall(request.playerMessage());

        StringBuilder prompt = new StringBuilder();
        prompt.append("SYSTEM: ").append(plugin.getConfig().getString("ai.http.system-prompt", ""));
        prompt.append("\nchiefId=").append(request.speakerId());
        prompt.append(" villageId=").append(request.villageId());
        prompt.append(" villageName=").append(request.villageName());
        prompt.append("\nvillageDescription=").append(request.villageDescription());
        prompt.append("\nvillageAttributes=").append(request.villageAttributes());
        prompt.append("\nvillageBiome=").append(request.villageBiome());
        prompt.append(" villagePopulation=").append(request.villagePopulationEstimate());
        prompt.append("\nvillageEvent=").append(request.villageEventSummary());
        prompt.append("\nchiefName=").append(request.displayName());
        prompt.append(" role=").append(request.role());
        prompt.append(" personality=").append(request.personality());
        prompt.append(" tone=").append(request.speechTone());
        prompt.append(" behavior=").append(request.behaviorHint());
        prompt.append("\nvillagerProfession=").append(request.villagerProfession());
        prompt.append(" villagerType=").append(request.villagerType());
        prompt.append("\nbiome=").append(request.currentBiome());
        prompt.append(" world=").append(request.worldName());
        prompt.append(" isDay=").append(request.isDay());
        prompt.append(" isRaining=").append(request.isRaining());
        prompt.append(" isThundering=").append(request.isThundering());
        prompt.append("\nhealth=").append(request.currentHealth()).append("/").append(request.maxHealth());
        prompt.append(" healthRatio=").append(request.healthRatio());
        prompt.append(" ateRecently=").append(request.ateRecently());
        prompt.append("\ntradeSummary=").append(request.tradeSummary());
        prompt.append("\nconfinementSummary=").append(request.confinementSummary());
        prompt.append("\nworldFacts=").append(request.authoritativeWorldFactsSummary());
        prompt.append("\nrecentConversation=").append(request.recentConversation());
        prompt.append("\nrelationshipMemory=").append(request.relationshipMemorySummary());
        prompt.append("\nhomePoi=").append(request.homePoi());
        prompt.append(" jobSitePoi=").append(request.jobSitePoi());
        prompt.append(" meetingPointPoi=").append(request.meetingPointPoi());
        prompt.append("\nvillageReputation=").append(request.villageReputationScore())
                .append(" (").append(request.villageReputationSummary()).append(")");
        prompt.append("\nspeakerReputation=").append(request.speakerReputationScore())
                .append(" (").append(request.speakerReputationSummary()).append(")");
        prompt.append("\ncombinedReputation=").append(request.combinedReputationScore())
                .append(" (").append(request.combinedReputationSummary()).append(")");
        prompt.append("\nvillageHasChief=").append(request.villageHasChief());
        prompt.append(" villageMourning=").append(request.villageMourning());
        prompt.append("\nplayerUuid=").append(request.playerUuid());
        prompt.append("\nmemory.enabled=").append(memoryEnabled);
        prompt.append(" ai.provider=").append(aiProvider);
        prompt.append(" likelyMemoryTrigger=").append(likelyMemoryTrigger);
        prompt.append(" messageLength=").append(messageLength);
        prompt.append(" recentConversationLength=").append(recentConversationLength);
        prompt.append(" relationshipMemoryLength=").append(relationshipMemoryLength);
        prompt.append("\nmessagePreview=").append(shortenForChatDebug(request.playerMessage(), 120));
        prompt.append("\nMESSAGE: ").append(request.playerMessage());

        plugin.logChatDebug(VillageChiefPlugin.ChatDebugLevel.VERBOSE,
                "[PROMPT] " + prompt.toString());
    }

        private boolean looksLikeMemoryRecall(String message) {
        String normalized = normalizeForIntent(message);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("name")
                || normalized.contains("erinner")
                || normalized.contains("weisst du noch")
                || normalized.contains("wei t du noch")
                || normalized.contains("fruher")
                || normalized.contains("fruher")
                || normalized.contains("letztes mal")
                || normalized.contains("damals");
    }

    private boolean isCasualSmalltalk(String playerMessage) {
        String normalized = normalizeForIntent(playerMessage);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("unterhalten")
                || normalized.contains("hallo")
                || normalized.contains("hi")
                || normalized.contains("guten tag")
                || normalized.contains("guten morgen")
                || normalized.contains("guten abend")
                || normalized.contains("gruss dich")
                || normalized.contains("gruess dich")
                || normalized.contains("na ")
                || normalized.equals("na")
                || normalized.contains("wie geht")
                || normalized.contains("was machst du")
                || normalized.contains("was gibt es neues")
                || normalized.contains("wie laeuft")
                || normalized.contains("wie läuft")
                || normalized.contains("plaudern")
                || normalized.contains("quatschen")
                || normalized.contains("smalltalk")
                || normalized.contains("einfach reden")
                || normalized.contains("nur reden")
                || normalized.contains("mit dir reden")
                || normalized.contains("mit dir sprechen")
                || normalized.contains("etwas reden");
    }

    private boolean isTaskSeeking(String playerMessage) {
        String normalized = normalizeForIntent(playerMessage);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("auftrag")
                || normalized.contains("aufgabe")
                || normalized.contains("arbeit")
                || normalized.contains("hilfe")
                || normalized.contains("helfen")
                || normalized.contains("quest")
                || normalized.contains("etwas zu tun")
                || normalized.contains("brauchst du etwas")
                || normalized.contains("kann ich etwas tun")
                || normalized.contains("hast du was fuer mich")
                || normalized.contains("hast du etwas fuer mich")
                || normalized.contains("job fuer mich");
    }

    private String shortenForChatDebug(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, Math.max(0, maxLength));
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String normalizeReplyForRepeatCheck(String message) {
        return Normalizer.normalize(message == null ? "" : message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

            /**
     * Baut die chiefLocation fuer den Prompt.
     * Fuer aktive Chiefs: eigene Position.
     * Fuer normale Villager: Position des aktiven Chiefs im Dorf, falls vorhanden.
     */
    private String buildChiefLocation(Speaker speaker) {
        if (speaker.isChief()) {
            return String.format(Locale.ROOT, "Du stehst bei x=%.1f, y=%.1f, z=%.1f in %s",
                    speaker.x(), speaker.y(), speaker.z(), speaker.world());
        }
        return speakerService.findActiveChiefByVillageId(speaker.villageId())
                .map(chief -> String.format(Locale.ROOT,
                        "Der Haeuptling %s (%s) steht bei x=%.1f, y=%.1f, z=%.1f in %s",
                        chief.displayName(), chief.role(),
                        chief.x(), chief.y(), chief.z(), chief.world()))
                .orElse("Das Dorf hat derzeit keinen Haeuptling.");
    }

    /**
     * Baut die chiefNarrative fuer den Prompt.
     * Fuer aktive Chiefs: Bestaetigung, dass sie der Chief sind.
     * Fuer normale Villager: Information ueber den tatsaechlichen Chief.
     */
    private String buildChiefNarrative(Speaker speaker) {
        if (speaker.isChief()) {
            return String.format(
                    "Du BIST der Haeuptling %s, die Fuehrungsperson dieses Dorfes. "
                    + "Die Bewohner respektieren deine Autoritaet.",
                    speaker.displayName());
        }
        return speakerService.findActiveChiefByVillageId(speaker.villageId())
                .map(chief -> String.format(
                        "Der aktuelle Haeuptling dieses Dorfes ist %s (%s). "
                        + "Du bist ein normaler Bewohner und sprichst aus der Perspektive "
                        + "eines einfachen Dorfbewohners.",
                        chief.displayName(), chief.role()))
                .orElse("Das Dorf hat derzeit keinen Haeuptling. "
                        + "Du bist ein normaler Bewohner.");
    }

    /**
     * Sucht die ChiefAttributes zum aktiven Chief des Dorfes dieses Speakers.
     */
    @org.jetbrains.annotations.Nullable
    private de.ajsch.villagerai.model.ChiefAttributes findChiefAttributes(Speaker speaker) {
        return speakerService.findActiveChiefByVillageId(speaker.villageId())
                .flatMap(chief -> chiefRepository.findByEntityUuid(chief.entityUuid()))
                .orElse(null);
    }

        public record RuntimeSettings(
            Duration timeout,
            int maxConcurrentRequests,
            String waitingMessage,
            String chiefBusyMessage,
            String queueFullMessage,
            double spontaneousQuestOfferChance,
            long spontaneousQuestOfferCooldownSeconds,
            long spontaneousQuestOfferDeclineCooldownSeconds,
            int spontaneousQuestOfferMinCombinedReputation,
            int recentChiefRepliesLimit,
            int recentConversationTurnsLimit,
            int friendlySpontaneousOfferReputationThreshold,
            long questCooldownMinutesThresholdSeconds,
            double repeatFallbackLowHealthThreshold) {
        }

    public boolean setVisibility(UUID playerUuid, String visibility) {
        ConversationSession session = activeSessions.get(playerUuid);
        if (session == null) return false;
        ConversationSession updated = new ConversationSession(
            session.conversationId(), session.speaker(), session.villagerUuid(),
            session.hadAiEnabledBeforeConversation(), session.villagerContext(),
            session.awaitingReply(), session.queuedPlayerMessage(),
            session.pendingQuestOffer(), session.pendingQuestOfferWasSpontaneous(),
            session.lastInteractionMillis(),
            visibility,
            session.participants());
        activeSessions.put(playerUuid, updated);
        return true;
    }

    public record ConversationSnapshot(String chiefId, String villageId, long idleSeconds, String visibility) {
    }

    private record ConversationSession(
            UUID conversationId,
            Speaker speaker,
            UUID villagerUuid,
            boolean hadAiEnabledBeforeConversation,
            VillagerContext villagerContext,
            AtomicBoolean awaitingReply,
            AtomicReference<String> queuedPlayerMessage,
            AtomicReference<QuestOfferService.QuestOffer> pendingQuestOffer,
            AtomicBoolean pendingQuestOfferWasSpontaneous,
            AtomicLong lastInteractionMillis,
            String visibility,
            Set<UUID> participants) {

        private ConversationSession(
                UUID conversationId,
                Speaker speaker,
                UUID villagerUuid,
                boolean hadAiEnabledBeforeConversation,
                VillagerContext villagerContext,
                String visibility,
                Set<UUID> participants) {
            this(
                    conversationId,
                    speaker,
                    villagerUuid,
                    hadAiEnabledBeforeConversation,
                    villagerContext,
                    new AtomicBoolean(false),
                    new AtomicReference<>(),
                    new AtomicReference<>(),
                    new AtomicBoolean(false),
                    new AtomicLong(System.currentTimeMillis()),
                    visibility,
                    participants);
        }
    }
}
