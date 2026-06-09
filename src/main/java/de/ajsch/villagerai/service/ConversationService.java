package de.ajsch.villagerai.service;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.ai.AIService;
import de.ajsch.villagerai.model.AIReply;
import de.ajsch.villagerai.model.AIRequest;
import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.ConversationRole;
import de.ajsch.villagerai.model.ConversationTurn;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestDifficultyPreference;
import de.ajsch.villagerai.model.QuestRewardResult;
import de.ajsch.villagerai.model.VillagerContext;
import de.ajsch.villagerai.model.ConversationHistory;
import de.ajsch.villagerai.storage.ConversationHistoryRepository;
import java.time.Duration;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private final ReputationService reputationService;
    private final ConversationHistoryRepository conversationHistoryRepository;
    private final VillagerContextService villagerContextService;
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
            ReputationService reputationService,
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
        this.reputationService = reputationService;
        this.conversationHistoryRepository = conversationHistoryRepository;
        this.villagerContextService = villagerContextService;
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

    public void startConversation(Player player, Villager villager, Chief chief) {
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(),
                chief,
                villager.getUniqueId(),
                villager.hasAI(),
            villagerContextService.resolve(villager, player.getUniqueId()));
        activeSessions.put(player.getUniqueId(), session);
        prepareVillagerForConversation(villager);
        orientVillagerTowardPlayer(villager, player);
        player.sendMessage(Component.text(
                "Du sprichst jetzt mit " + chief.chatName() + ". Schreibe im Chat.",
                NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Beende das Gespraech mit /chief exit.", NamedTextColor.GRAY));
        if (chief.greeting() != null && !chief.greeting().isBlank()) {
            player.sendMessage(Component.text("[" + chief.chatName() + "] ", NamedTextColor.GOLD)
                    .append(Component.text(chief.greeting(), NamedTextColor.WHITE)));
        }

        questService.findActiveQuest(player.getUniqueId()).ifPresent(activeQuest -> {
            if (!activeQuest.chiefId().equals(chief.chiefId())) {
                String otherQuestGiverName = questService.findQuest(activeQuest.questId())
                        .flatMap(q -> Optional.ofNullable(q.chiefId()))
                        .map(id -> "einem anderen Dorfbewohner")
                        .orElse("einem anderen Dorfbewohner");
                player.sendMessage(Component.text(
                        "Hinweis: Du hast noch eine offene Aufgabe bei " + otherQuestGiverName
                                + " ('" + activeQuest.title() + "'). Schliesse sie erst ab oder brich sie ab.",
                        NamedTextColor.GRAY));
            }
        });

        if (!isCloseEnoughForCompletion(player, villager, chief.chiefId())) {
            return;
        }

        Collection<Quest> completedTalkQuests = questService.completeActiveTalkQuests(player.getUniqueId(), chief.chiefId());
        for (Quest completedQuest : completedTalkQuests) {
            player.sendMessage(Component.text("Quest abgeschlossen: " + completedQuest.title(), NamedTextColor.GREEN));
            QuestRewardResult rewardResult = questRewardService.grantRewards(
                    player,
                    completedQuest,
                    reputationService.getVillageScore(player.getUniqueId(), completedQuest.villageId()),
                    reputationService.getSpeakerScore(player.getUniqueId(), completedQuest.chiefId()));
            reputationService.applyQuestCompletion(completedQuest);
                if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                    buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        Collection<QuestService.DeliverQuestUpdate> deliverQuestUpdates = questService.completeActiveDeliverQuests(player, chief.chiefId());
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
            reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.chiefId()));
            reputationService.applyQuestCompletion(updatedQuest);
                if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                    buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        Collection<QuestService.DeliverQuestUpdate> repairQuestUpdates = questService.completeActiveRepairQuests(player, chief.chiefId());
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
                    reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.chiefId()));
            reputationService.applyQuestCompletion(updatedQuest);
            if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                        buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        Collection<QuestService.BrewQuestUpdate> brewQuestUpdates = questService.completeActiveBrewQuests(player, chief.chiefId());
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
                    reputationService.getSpeakerScore(player.getUniqueId(), updatedQuest.chiefId()));
            reputationService.applyQuestCompletion(updatedQuest);
            if (hasVisibleReward(rewardResult)) {
                player.sendMessage(Component.text(
                        buildRewardSummary(rewardResult),
                        NamedTextColor.GREEN));
            }
        }

        questService.syncActiveFetchQuests(player);

        Collection<Quest> completedInteractionQuests = questService.completeReadyInteractionQuests(
                player.getUniqueId(),
                chief.chiefId());
        for (Quest completedQuest : completedInteractionQuests) {
            player.sendMessage(Component.text("Quest abgeschlossen: " + completedQuest.title(), NamedTextColor.GREEN));
            QuestRewardResult rewardResult = questRewardService.grantRewards(
                player,
                completedQuest,
                reputationService.getVillageScore(player.getUniqueId(), completedQuest.villageId()),
                reputationService.getSpeakerScore(player.getUniqueId(), completedQuest.chiefId()));
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
                session.chief().chiefId(),
                session.chief().villageId(),
                Duration.ofMillis(System.currentTimeMillis() - session.lastInteractionMillis().get()).toSeconds()));
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
                session.chief(),
                new ConversationTurn(ConversationRole.PLAYER, message, playerTurnTimestamp));

        String chiefId = session.chief().chiefId();
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

        List<ConversationTurn> historyTurns = conversationHistoryRepository.findHistory(playerUuid, session.chief().chiefId())
            .map(history -> history.turns())
            .orElse(List.of());
        Collection<ConversationHistory> playerHistories = conversationHistoryRepository.findByPlayerUuid(playerUuid);
        String recentConversation = formatRecentConversation(historyTurns);
        String relationshipMemorySummary = buildRelationshipMemorySummary(session.chief(), historyTurns, playerHistories);
        List<String> recentChiefReplies = findRecentChiefReplies(historyTurns, recentChiefRepliesLimit);
        int villageReputationScore = reputationService.getVillageScore(playerUuid, session.chief().villageId());
        String villageReputationSummary = reputationService.getVillageSummary(playerUuid, session.chief().villageId());
        int speakerReputationScore = reputationService.getSpeakerScore(playerUuid, session.chief().chiefId());
        String speakerReputationSummary = reputationService.getSpeakerSummary(playerUuid, session.chief().chiefId());
        int combinedReputationScore = reputationService.getCombinedScore(
            playerUuid,
            session.chief().villageId(),
            session.chief().chiefId());
        String combinedReputationSummary = reputationService.getCombinedSummary(
            playerUuid,
            session.chief().villageId(),
            session.chief().chiefId());

        AIRequest request = new AIRequest(
                session.chief().chiefId(),
                session.chief().villageId(),
            session.chief().villageName(),
            session.chief().villageDescription(),
            session.chief().villageAttributes(),
            session.chief().villageBiome(),
            session.chief().villagePopulationEstimate(),
            session.chief().villageEventSummary(),
            session.chief().displayName(),
            session.chief().role(),
            session.chief().personality(),
            session.chief().speechTone(),
            session.chief().behaviorHint(),
            session.chief().greeting(),
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
                villageReputationScore,
                villageReputationSummary,
                speakerReputationScore,
                speakerReputationSummary,
                combinedReputationScore,
                combinedReputationSummary,
                combinedReputationScore,
                combinedReputationSummary,
                playerUuid,
                message);

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
                    session.chief(),
                    new ConversationTurn(ConversationRole.CHIEF, replyText, System.currentTimeMillis()));

            sendChiefMessage(player, session, replyText, NamedTextColor.WHITE);
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
        chiefRequestOwners.remove(session.chief().chiefId(), playerUuid);
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
                session.chief(),
                new ConversationTurn(ConversationRole.CHIEF, farewell, System.currentTimeMillis()));

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
                setSpontaneousOfferCooldown(playerUuid, session.chief().chiefId(), spontaneousQuestOfferDeclineCooldownMillis);
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
                session.chief().chiefId());
        if (!availability.allowed()) {
            sendGeneratedChiefReply(playerUuid, session, buildQuestUnavailableReply(availability));
            return true;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            sendGeneratedChiefReply(playerUuid, session, "Ich kann dich gerade nicht erreichen.");
            return true;
        }

        int villageReputationScore = reputationService.getVillageScore(playerUuid, session.chief().villageId());
        int speakerReputationScore = reputationService.getSpeakerScore(playerUuid, session.chief().chiefId());
        int unlockedTier = Math.min(
            questDifficultyService.resolveUnlockedTier(villageReputationScore),
            computeLegendaryAllowedTier(player, session.chief().chiefId(), villageReputationScore, speakerReputationScore));
        int maxTier = questDifficultyService.maxTier();
        int preferredTier = questDifficultyService.getPreference(playerUuid, session.chief().chiefId()).preferredDifficultyTier();

        String legendaryHint = "";
        if (unlockedTier < maxTier && preferredTier >= maxTier) {
            legendaryHint = buildLegendaryBlockedReply(player, session.chief().chiefId(), villageReputationScore, speakerReputationScore);
        }

        QuestOfferService.QuestOffer offer = questOfferService.createOfferForTier(
            playerUuid,
            session.chief(),
            session.villagerContext(),
            Math.min(preferredTier, unlockedTier));
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
        if (!activeQuest.chiefId().equals(session.chief().chiefId())) {
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
        Quest quest = questOfferService.acceptOffer(player, session.chief(), pendingOffer);
        if (pendingOffer.difficultyTier() >= questDifficultyService.legendaryTier()) {
            setLegendaryOfferCooldown(playerUuid, session.chief().chiefId(), questDifficultyService.legendaryCooldownMillis());
        }
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.chief(),
                new ConversationTurn(ConversationRole.CHIEF, pendingOffer.acceptedReplyText(), System.currentTimeMillis()));
        sendChiefMessage(player, session, pendingOffer.acceptedReplyText(), NamedTextColor.WHITE);
        player.sendMessage(Component.text("Quest angenommen: " + quest.title(), NamedTextColor.GREEN));
        questUiService.refresh(player);
    }

    private void appendPlayerTurn(UUID playerUuid, ConversationSession session, String message) {
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.chief(),
                new ConversationTurn(ConversationRole.PLAYER, message, System.currentTimeMillis()));
    }

    private void sendGeneratedChiefReply(UUID playerUuid, ConversationSession session, String replyText) {
        conversationHistoryRepository.appendTurn(
                playerUuid,
                session.chief(),
                new ConversationTurn(ConversationRole.CHIEF, replyText, System.currentTimeMillis()));
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
                session.chief().villageId(),
                session.chief().chiefId());
        if (combinedReputation < spontaneousQuestOfferMinCombinedReputation) {
            return;
        }

        QuestService.TalkQuestAvailability availability = questService.validateQuestActivation(
                playerUuid,
                session.chief().chiefId());
        if (!availability.allowed()) {
            return;
        }

        String cooldownKey = playerUuid + ":" + session.chief().chiefId();
        long now = System.currentTimeMillis();
        Long cooldownUntil = spontaneousQuestOfferCooldowns.get(cooldownKey);
        if (cooldownUntil != null && cooldownUntil > now) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= spontaneousQuestOfferChance) {
            return;
        }

        int villageReputationScore = reputationService.getVillageScore(playerUuid, session.chief().villageId());
        int speakerReputation = reputationService.getSpeakerScore(playerUuid, session.chief().chiefId());
        QuestDifficultyPreference difficultyPreference = questDifficultyService.getPreference(playerUuid, session.chief().chiefId());
        int unlockedTier = Math.min(
            questDifficultyService.resolveUnlockedTier(villageReputationScore),
            computeLegendaryAllowedTier(player, session.chief().chiefId(), villageReputationScore, speakerReputation));
        int preferredTier = questDifficultyService.clampTier(difficultyPreference.preferredDifficultyTier());

        boolean offerChallengeTier = questDifficultyService.challengeOffersEnabled()
            && unlockedTier > preferredTier
            && difficultyPreference.lastSuggestedAtEpochMillis() + questDifficultyService.challengeOfferCooldownMillis() <= now
            && ThreadLocalRandom.current().nextDouble() < questDifficultyService.challengeOfferChance();

        QuestOfferService.QuestOffer offer = offerChallengeTier
            ? questOfferService.createOfferForTier(
                playerUuid,
                session.chief(),
                session.villagerContext(),
                Math.min(unlockedTier, preferredTier + 1))
            : questOfferService.createOffer(
                playerUuid,
                session.chief(),
                session.villagerContext(),
                villageReputationScore);
        session.pendingQuestOffer().set(offer);
        session.pendingQuestOfferWasSpontaneous().set(true);
        setSpontaneousOfferCooldown(playerUuid, session.chief().chiefId(), spontaneousQuestOfferCooldownMillis);

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
                session.chief(),
                new ConversationTurn(ConversationRole.CHIEF, offerIntro + offer.promptText(), System.currentTimeMillis()));
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

    private void sendChiefMessage(Player player, ConversationSession session, String replyText, NamedTextColor color) {
        player.sendMessage(Component.text("[" + session.chief().chatName() + "] ", NamedTextColor.GOLD)
                .append(Component.text(replyText, color)));
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
        return variants[Math.floorMod(session.chief().chiefId().hashCode() + normalized.hashCode(), variants.length)];
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
        if (activeQuest.isEmpty() || !activeQuest.get().chiefId().equals(chiefId)) {
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
            String line = (turn.role() == ConversationRole.PLAYER ? "Spieler" : "Haeuptling") + ": " + turn.message();
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
            Chief chief,
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

        String speakerName = chief == null ? "du" : chief.chatName();
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
            if (turn.role() != ConversationRole.CHIEF || turn.message() == null || turn.message().isBlank()) {
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
            return variants[Math.floorMod(session.chief().chiefId().hashCode() + historySize, variants.length)];
        }

        String[] variants = {
            "Du wiederholst dich nicht. Also sollte ich es auch nicht tun. Sag klar, was du willst.",
            "Darauf habe ich schon genug gesagt. Stell lieber die naechste klare Frage.",
            "Lass uns nicht auf derselben Stelle treten. Komm zur Sache."
        };
        return variants[Math.floorMod(session.chief().chiefId().hashCode() + historySize, variants.length)];
    }

    private String normalizeReplyForRepeatCheck(String message) {
        return Normalizer.normalize(message == null ? "" : message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    public record ConversationSnapshot(String chiefId, String villageId, long idleSeconds) {
    }

    private record ConversationSession(
            UUID conversationId,
            Chief chief,
            UUID villagerUuid,
            boolean hadAiEnabledBeforeConversation,
            VillagerContext villagerContext,
            AtomicBoolean awaitingReply,
            AtomicReference<String> queuedPlayerMessage,
            AtomicReference<QuestOfferService.QuestOffer> pendingQuestOffer,
            AtomicBoolean pendingQuestOfferWasSpontaneous,
            AtomicLong lastInteractionMillis) {

        private ConversationSession(
                UUID conversationId,
                Chief chief,
                UUID villagerUuid,
                boolean hadAiEnabledBeforeConversation,
                VillagerContext villagerContext) {
            this(
                    conversationId,
                    chief,
                    villagerUuid,
                    hadAiEnabledBeforeConversation,
                    villagerContext,
                    new AtomicBoolean(false),
                    new AtomicReference<>(),
                    new AtomicReference<>(),
                    new AtomicBoolean(false),
                    new AtomicLong(System.currentTimeMillis()));
        }
    }
}
