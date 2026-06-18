package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.storage.QuestRepository;
import de.ajsch.villagerai.model.VillagePerimeter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class QuestService {

    private final QuestRepository questRepository;
    private final Logger logger;
    private final LightLevelScanner lightLevelScanner;
    private final VillagePerimeterService villagePerimeterService;
    private final VillageIdentityService villageIdentityService;
    private final DarkBlockCache darkBlockCache;
    private volatile long talkQuestCooldownMillis;

    public QuestService(
            QuestRepository questRepository,
            Logger logger,
            LightLevelScanner lightLevelScanner,
            VillagePerimeterService villagePerimeterService,
            VillageIdentityService villageIdentityService,
            DarkBlockCache darkBlockCache,
            long talkQuestCooldownSeconds) {
        this.questRepository = questRepository;
        this.logger = logger;
        this.lightLevelScanner = lightLevelScanner;
        this.villagePerimeterService = villagePerimeterService;
        this.villageIdentityService = villageIdentityService;
        this.darkBlockCache = darkBlockCache;
        reloadTalkQuestCooldown(talkQuestCooldownSeconds);
    }

    public void reloadTalkQuestCooldown(long talkQuestCooldownSeconds) {
        this.talkQuestCooldownMillis = Math.max(0L, talkQuestCooldownSeconds) * 1000L;
    }

    public Quest offerTalkQuest(UUID playerUuid, Speaker speaker, String title, String description) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                0,
                QuestType.TALK,
                title,
                description,
                speaker.speakerId(),
                1,
                0,
                QuestStatus.OFFERED,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateTalkQuest(UUID playerUuid, Speaker speaker, String title, String description) {
        return acceptQuest(offerTalkQuest(playerUuid, speaker, title, description).questId()).orElseThrow();
    }

    public Quest activateDeliverQuest(UUID playerUuid, Speaker speaker, Material material, int amount) {
        return activateDeliverQuest(playerUuid, speaker, material, amount, 0);
    }

    public Quest activateDeliverQuest(UUID playerUuid, Speaker speaker, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.DELIVER,
                "Liefere " + amount + " " + formatMaterial(material),
                "Bringe " + amount + " " + formatMaterial(material) + " zu " + speaker.displayName() + ".",
                material.name() + ":" + amount,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateRepairQuest(UUID playerUuid, Speaker speaker, Material material, int amount) {
        return activateRepairQuest(playerUuid, speaker, material, amount, 0);
    }

    public Quest activateRepairQuest(UUID playerUuid, Speaker speaker, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.REPAIR,
                "Repariere mit " + amount + " " + formatMaterial(material),
                "Bringe " + amount + " " + formatMaterial(material) + " fuer Reparaturen zu " + speaker.displayName() + ".",
                material.name() + ":" + amount,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateBuildQuest(UUID playerUuid, Speaker speaker, Material material, int amount) {
        return activateBuildQuest(playerUuid, speaker, material, amount, 0);
    }

    public Quest activateBuildQuest(UUID playerUuid, Speaker speaker, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.BUILD,
                "Baue " + amount + " " + formatMaterial(material),
                "Platziere " + amount + " " + formatMaterial(material) + " fuer " + speaker.displayName() + ".",
                material.name(),
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateBreedQuest(UUID playerUuid, Speaker speaker, EntityType entityType, int amount) {
        return activateBreedQuest(playerUuid, speaker, entityType, amount, 0);
    }

    public Quest activateBreedQuest(UUID playerUuid, Speaker speaker, EntityType entityType, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.BREED,
                "Zuechte " + amount + " " + formatEntityType(entityType),
                "Zuechte " + amount + " " + formatEntityType(entityType) + " fuer " + speaker.displayName() + ".",
                entityType.name(),
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateBrewQuest(UUID playerUuid, Speaker speaker, PotionType potionType, int amount) {
        return activateBrewQuest(playerUuid, speaker, potionType, amount, 0);
    }

    public Quest activateBrewQuest(UUID playerUuid, Speaker speaker, PotionType potionType, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        String potionName = formatPotionType(potionType);
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.BREW,
                "Braue " + amount + " " + potionName,
                "Bringe " + amount + " " + potionName + " zu " + speaker.displayName() + ".",
                potionType.name() + ":" + amount,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateFetchQuest(Player player, Speaker speaker, Material material, int amount) {
        return activateFetchQuest(player, speaker, material, amount, 0);
    }

    public Quest activateFetchQuest(Player player, Speaker speaker, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        int initialProgress = Math.min(amount, countMaterial(player, material));
        Quest quest = new Quest(
                createQuestId(),
                player.getUniqueId(),
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.FETCH,
                "Sammle " + amount + " " + formatMaterial(material),
                "Besorge " + amount + " " + formatMaterial(material) + " und melde dich danach bei "
                        + speaker.displayName() + ".",
                material.name() + ":" + amount,
                amount,
                initialProgress,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateKillQuest(UUID playerUuid, Speaker speaker, EntityType entityType, int amount) {
        return activateKillQuest(playerUuid, speaker, entityType, amount, 0);
    }

    public Quest activateKillQuest(UUID playerUuid, Speaker speaker, EntityType entityType, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.KILL,
                "Toete " + amount + " " + formatEntityType(entityType),
                "Besiege " + amount + " " + formatEntityType(entityType) + " fuer " + speaker.displayName() + ".",
                entityType.name(),
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateVisitQuest(UUID playerUuid, Speaker speaker, String worldName, int targetX, int targetZ, int radius) {
        return activateVisitQuest(playerUuid, speaker, worldName, targetX, targetZ, radius, 0);
    }

    public Quest activateVisitQuest(
            UUID playerUuid,
            Speaker speaker,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.VISIT,
                "Reise nach X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach bei "
                        + speaker.displayName() + ".",
                worldName + ":" + targetX + ":" + targetZ + ":" + radius,
                1,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateSecureQuest(
            UUID playerUuid,
            Speaker speaker,
            Material material,
            int amount,
            String worldName,
            int targetX,
            int targetZ,
            int radius) {
        return activateSecureQuest(playerUuid, speaker, material, amount, worldName, targetX, targetZ, radius, 0);
    }

    /**
     * Creates a SECURE quest using a raw targetKey string (e.g. village-light format).
     * The {@code goal} is determined from the {@code targetKey} if it contains a goal segment.
     */
    public Quest activateSecureQuestByTargetKey(
            UUID playerUuid,
            Speaker speaker,
            Material material,
            int amount,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            int difficultyTier,
            String targetKey) {
        long now = System.currentTimeMillis();
        int goal = amount > 0 ? amount : 0;
        // For village-light quests, initialDarkCount is the goal (beseitigte dunkle Bloecke),
        // extracted from the targetKey if it's a light mode key.
        if (targetKey != null && targetKey.contains("|light|")) {
            String[] parts = targetKey.split("\\|");
            // format: material|world|villageId|light|goal|initialDark|subCenterX|subCenterY|subCenterZ
            if (parts.length >= 6) {
                try {
                    int initialDark = Integer.parseInt(parts[5]);
                    goal = initialDark;
                } catch (NumberFormatException ignored) {
                    // keep goal as 0 or amount
                }
            }
        }
        if (goal <= 0) {
            goal = 1;
        }
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.SECURE,
                "Bereich ausleuchten (" + goal + " dunkle Stellen)",
                "Erhelle einen Sub-Bereich im Dorf bei X " + targetX + " / Z " + targetZ
                        + " und melde dich danach bei " + speaker.displayName() + ".",
                targetKey != null ? targetKey : (material.name() + ":" + worldName + ":" + targetX + ":" + targetZ + ":" + radius),
                goal,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateSecureQuest(
            UUID playerUuid,
            Speaker speaker,
            Material material,
            int amount,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.SECURE,
                "Sichere mit " + amount + " " + formatMaterial(material),
                "Platziere " + amount + " " + formatMaterial(material)
                        + " bei X " + targetX + " / Z " + targetZ
                        + " und melde dich danach bei " + speaker.displayName() + ".",
                material.name() + ":" + worldName + ":" + targetX + ":" + targetZ + ":" + radius,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Optional<Quest> acceptQuest(String questId) {
        return questRepository.findByQuestId(questId)
                .map(existingQuest -> existingQuest.withStatus(QuestStatus.ACTIVE, System.currentTimeMillis()))
                .map(updatedQuest -> {
                    questRepository.saveQuest(updatedQuest);
                    return updatedQuest;
                });
    }

    public Optional<Quest> completeQuest(String questId) {
        return questRepository.findByQuestId(questId)
                .map(existingQuest -> existingQuest.withProgress(existingQuest.goal(), System.currentTimeMillis()))
                .map(progressQuest -> progressQuest.withStatus(QuestStatus.COMPLETED, System.currentTimeMillis()))
                .map(updatedQuest -> {
                    questRepository.saveQuest(updatedQuest);
                    return updatedQuest;
                });
    }

    public Optional<Quest> findQuest(String questId) {
        return questRepository.findByQuestId(questId);
    }

    public Collection<Quest> findPlayerQuests(UUID playerUuid) {
        return questRepository.findByPlayerUuid(playerUuid);
    }

    public Optional<Quest> findActiveQuest(UUID playerUuid) {
        return questRepository.findByPlayerUuid(playerUuid).stream()
                .filter(quest -> quest.status() == QuestStatus.OFFERED || quest.status() == QuestStatus.ACTIVE)
                .max(Comparator.comparingLong(Quest::updatedAtEpochMillis));
    }

    public Optional<Quest> findLatestQuestForChief(UUID playerUuid, String speakerId) {
        return questRepository.findByPlayerUuid(playerUuid).stream()
                .filter(quest -> quest.speakerId().equals(speakerId))
                .max(Comparator.comparingLong(Quest::updatedAtEpochMillis));
    }

    public Optional<Quest> cancelActiveQuest(UUID playerUuid) {
        long now = System.currentTimeMillis();
        return findActiveQuest(playerUuid)
                .map(quest -> quest.withStatus(QuestStatus.CANCELLED, now))
                .map(updatedQuest -> {
                    questRepository.saveQuest(updatedQuest);
                    return updatedQuest;
                });
    }

    public Collection<Quest> cancelActiveQuestsForChief(String speakerId) {
        long now = System.currentTimeMillis();
        Collection<Quest> cancelledQuests = new ArrayList<>();
        for (Quest quest : questRepository.findAll()) {
            if ((quest.status() != QuestStatus.OFFERED && quest.status() != QuestStatus.ACTIVE)
                    || !quest.speakerId().equals(speakerId)) {
                continue;
            }

            Quest updatedQuest = quest.withStatus(QuestStatus.CANCELLED, now);
            questRepository.saveQuest(updatedQuest);
            cancelledQuests.add(updatedQuest);
        }
        return cancelledQuests;
    }

    public TalkQuestAvailability validateQuestActivation(UUID playerUuid, String speakerId) {
        Optional<Quest> activeQuest = findActiveQuest(playerUuid);
        if (activeQuest.isPresent()) {
            return new TalkQuestAvailability(
                    false,
                    QuestAvailabilityFailureReason.ACTIVE_QUEST,
                    activeQuest.get().title(),
                    0L,
                    "Du hast bereits eine aktive Quest: " + activeQuest.get().title() + ". Brich sie erst ab oder schliesse sie ab.");
        }

        Optional<Quest> latestCompletedQuest = findLatestQuestForChief(playerUuid, speakerId)
                .filter(quest -> quest.status() == QuestStatus.COMPLETED);
        if (latestCompletedQuest.isPresent() && talkQuestCooldownMillis > 0L) {
            long elapsedMillis = System.currentTimeMillis() - latestCompletedQuest.get().updatedAtEpochMillis();
            if (elapsedMillis < talkQuestCooldownMillis) {
                long remainingSeconds = Math.max(1L, (talkQuestCooldownMillis - elapsedMillis + 999L) / 1000L);
                return new TalkQuestAvailability(
                        false,
                    QuestAvailabilityFailureReason.COOLDOWN,
                    null,
                    remainingSeconds,
                        "Dieser Questgeber hat gerade keine neue Aufgabe fuer dich. Versuch es in "
                                + remainingSeconds + " Sekunden noch einmal.");
            }
        }

        long cancelCooldownMillis = talkQuestCooldownMillis / 5L;
        Optional<Quest> latestCancelledQuest = findLatestQuestForChief(playerUuid, speakerId)
                .filter(quest -> quest.status() == QuestStatus.CANCELLED);
        if (latestCancelledQuest.isPresent() && cancelCooldownMillis > 0L) {
            long elapsedMillis = System.currentTimeMillis() - latestCancelledQuest.get().updatedAtEpochMillis();
            if (elapsedMillis < cancelCooldownMillis) {
                long remainingSeconds = Math.max(1L, (cancelCooldownMillis - elapsedMillis + 999L) / 1000L);
                return new TalkQuestAvailability(
                        false,
                    QuestAvailabilityFailureReason.COOLDOWN,
                    null,
                    remainingSeconds,
                        "Du hast gerade erst einen Auftrag abgebrochen. Versuch es in "
                                + remainingSeconds + " Sekunden noch einmal.");
            }
        }

            return new TalkQuestAvailability(true, QuestAvailabilityFailureReason.NONE, null, 0L, null);
    }

    public TalkQuestAvailability validateTalkQuestActivation(UUID playerUuid, String speakerId) {
        return validateQuestActivation(playerUuid, speakerId);
    }

    public Collection<Quest> completeActiveTalkQuests(UUID playerUuid, String targetKey) {
        long now = System.currentTimeMillis();
        Collection<Quest> completedQuests = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(playerUuid)) {
            if (quest.type() != QuestType.TALK
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.targetKey().equals(targetKey)) {
                continue;
            }

            Quest updatedQuest = quest.withProgress(quest.goal(), now).withStatus(QuestStatus.COMPLETED, now);
            questRepository.saveQuest(updatedQuest);
            completedQuests.add(updatedQuest);
        }
        return completedQuests;
    }

    public Collection<DeliverQuestUpdate> completeActiveDeliverQuests(Player player, String speakerId) {
        long now = System.currentTimeMillis();
        Collection<DeliverQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.DELIVER
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.speakerId().equals(speakerId)) {
                continue;
            }

            DeliveryRequirement requirement = parseDeliveryRequirement(quest).orElse(null);
            if (requirement == null) {
                continue;
            }

            int remainingAmount = Math.max(0, quest.goal() - quest.progress());
            if (remainingAmount <= 0) {
                Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
                questRepository.saveQuest(updatedQuest);
                updates.add(new DeliverQuestUpdate(updatedQuest, 0, true));
                continue;
            }

            int handedIn = removeMaterial(player, requirement.material(), remainingAmount);
            if (handedIn <= 0) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + handedIn);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            boolean completed = newProgress >= quest.goal();
            if (completed) {
                updatedQuest = updatedQuest.withStatus(QuestStatus.COMPLETED, now);
            }
            questRepository.saveQuest(updatedQuest);
            updates.add(new DeliverQuestUpdate(updatedQuest, handedIn, completed));
        }
        return updates;
    }

    public Collection<DeliverQuestUpdate> completeActiveRepairQuests(Player player, String speakerId) {
        long now = System.currentTimeMillis();
        Collection<DeliverQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.REPAIR
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.speakerId().equals(speakerId)) {
                continue;
            }

            DeliveryRequirement requirement = parseRepairRequirement(quest).orElse(null);
            if (requirement == null) {
                continue;
            }

            int remainingAmount = Math.max(0, quest.goal() - quest.progress());
            if (remainingAmount <= 0) {
                Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
                questRepository.saveQuest(updatedQuest);
                updates.add(new DeliverQuestUpdate(updatedQuest, 0, true));
                continue;
            }

            int handedIn = removeMaterial(player, requirement.material(), remainingAmount);
            if (handedIn <= 0) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + handedIn);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            boolean completed = newProgress >= quest.goal();
            if (completed) {
                updatedQuest = updatedQuest.withStatus(QuestStatus.COMPLETED, now);
            }
            questRepository.saveQuest(updatedQuest);
            updates.add(new DeliverQuestUpdate(updatedQuest, handedIn, completed));
        }
        return updates;
    }

    public Collection<BrewQuestUpdate> completeActiveBrewQuests(Player player, String speakerId) {
        long now = System.currentTimeMillis();
        Collection<BrewQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.BREW
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.speakerId().equals(speakerId)) {
                continue;
            }

            BrewRequirement requirement = parseBrewRequirement(quest).orElse(null);
            if (requirement == null) {
                continue;
            }

            int remainingAmount = Math.max(0, quest.goal() - quest.progress());
            if (remainingAmount <= 0) {
                Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
                questRepository.saveQuest(updatedQuest);
                updates.add(new BrewQuestUpdate(updatedQuest, 0, true));
                continue;
            }

            int handedIn = removePotion(player, requirement.potionType(), remainingAmount);
            if (handedIn <= 0) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + handedIn);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            boolean completed = newProgress >= quest.goal();
            if (completed) {
                updatedQuest = updatedQuest.withStatus(QuestStatus.COMPLETED, now);
            }
            questRepository.saveQuest(updatedQuest);
            updates.add(new BrewQuestUpdate(updatedQuest, handedIn, completed));
        }
        return updates;
    }

    public Collection<Quest> completeReadyInteractionQuests(UUID playerUuid, String speakerId) {
        long now = System.currentTimeMillis();
        Collection<Quest> completedQuests = new ArrayList<>();
        Optional<Quest> activeQuest = findActiveQuest(playerUuid);
        for (Quest quest : questRepository.findByPlayerUuid(playerUuid)) {
            if (quest.status() != QuestStatus.ACTIVE
                    || !quest.speakerId().equals(speakerId)
                    || quest.progress() < quest.goal()) {
                continue;
            }

            if (quest.type() == QuestType.TALK
                    || quest.type() == QuestType.DELIVER
                    || quest.type() == QuestType.REPAIR
                    || quest.type() == QuestType.BREW) {
                continue;
            }

            if (activeQuest.isEmpty() || !activeQuest.get().questId().equals(quest.questId())) {
                continue;
            }

            Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
            questRepository.saveQuest(updatedQuest);
            completedQuests.add(updatedQuest);
        }
        return completedQuests;
    }

    public Collection<FetchQuestUpdate> syncActiveFetchQuests(Player player) {
        long now = System.currentTimeMillis();
        Collection<FetchQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.FETCH || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }

            FetchRequirement requirement = parseFetchRequirement(quest).orElse(null);
            if (requirement == null) {
                continue;
            }

            int currentProgress = Math.min(quest.goal(), countMaterial(player, requirement.material()));
            if (currentProgress == quest.progress()) {
                continue;
            }

            Quest updatedQuest = quest.withProgress(currentProgress, now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new FetchQuestUpdate(updatedQuest, quest.progress(), currentProgress >= updatedQuest.goal()));
        }
        return updates;
    }

    public Collection<KillQuestUpdate> advanceKillQuests(Player player, EntityType killedType) {
        long now = System.currentTimeMillis();
        Collection<KillQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.KILL
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.targetKey().equalsIgnoreCase(killedType.name())) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + 1);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            boolean readyToTurnIn = newProgress >= quest.goal();
            questRepository.saveQuest(updatedQuest);
            updates.add(new KillQuestUpdate(updatedQuest, readyToTurnIn));
        }
        return updates;
    }

    public Collection<VisitQuestUpdate> advanceVisitQuests(Player player, Location location) {
        long now = System.currentTimeMillis();
        Collection<VisitQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.VISIT || quest.status() != QuestStatus.ACTIVE || quest.progress() >= quest.goal()) {
                continue;
            }

            VisitRequirement requirement = parseVisitRequirement(quest).orElse(null);
            if (requirement == null || !requirement.worldName().equalsIgnoreCase(location.getWorld().getName())) {
                continue;
            }

            int deltaX = location.getBlockX() - requirement.targetX();
            int deltaZ = location.getBlockZ() - requirement.targetZ();
            double horizontalDistanceSquared = (double) deltaX * deltaX + (double) deltaZ * deltaZ;
            if (horizontalDistanceSquared > (double) requirement.radius() * requirement.radius()) {
                continue;
            }

            Quest updatedQuest = quest.withProgress(quest.goal(), now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new VisitQuestUpdate(updatedQuest, requirement));
        }
        return updates;
    }

    public Collection<BuildQuestUpdate> advanceBuildQuests(Player player, Material placedMaterial) {
        long now = System.currentTimeMillis();
        Collection<BuildQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.BUILD || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }
            Material requiredMaterial = parseBuildRequirement(quest).orElse(null);
            if (requiredMaterial == null || requiredMaterial != placedMaterial) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + 1);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new BuildQuestUpdate(updatedQuest, newProgress >= quest.goal()));
        }
        return updates;
    }

    public Collection<BreedQuestUpdate> advanceBreedQuests(Player player, EntityType bredType) {
        long now = System.currentTimeMillis();
        Collection<BreedQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.BREED
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.targetKey().equalsIgnoreCase(bredType.name())) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + 1);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new BreedQuestUpdate(updatedQuest, newProgress >= quest.goal()));
        }
        return updates;
    }

    public Quest activateExploreQuest(
            UUID playerUuid,
            Speaker speaker,
            String worldName,
            int targetX,
            int targetZ,
            int radius) {
        return activateExploreQuest(playerUuid, speaker, worldName, targetX, targetZ, radius, 0);
    }

    public Quest activateExploreQuest(
            UUID playerUuid,
            Speaker speaker,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                speaker.speakerId(),
                speaker.villageId(),
                Math.max(0, difficultyTier),
                QuestType.EXPLORE,
                "Erkunde X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach bei "
                        + speaker.displayName() + ".",
                worldName + ":" + targetX + ":" + targetZ + ":" + radius,
                1,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Collection<ExploreQuestUpdate> advanceExploreQuests(Player player, Location location) {
        long now = System.currentTimeMillis();
        Collection<ExploreQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.EXPLORE || quest.status() != QuestStatus.ACTIVE || quest.progress() >= quest.goal()) {
                continue;
            }

            VisitRequirement requirement = parseExploreRequirement(quest).orElse(null);
            if (requirement == null || !requirement.worldName().equalsIgnoreCase(location.getWorld().getName())) {
                continue;
            }

            int deltaX = location.getBlockX() - requirement.targetX();
            int deltaZ = location.getBlockZ() - requirement.targetZ();
            double horizontalDistanceSquared = (double) deltaX * deltaX + (double) deltaZ * deltaZ;
            if (horizontalDistanceSquared > (double) requirement.radius() * requirement.radius()) {
                continue;
            }

            Quest updatedQuest = quest.withProgress(quest.goal(), now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new ExploreQuestUpdate(updatedQuest, requirement));
        }
        return updates;
    }

    public Optional<VisitRequirement> parseExploreRequirement(Quest quest) {
        if (quest.type() != QuestType.EXPLORE) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }

        try {
            return Optional.of(new VisitRequirement(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Collection<SecureQuestUpdate> advanceSecureQuests(Player player, Material placedMaterial, Location placedLocation) {
        long now = System.currentTimeMillis();
        Collection<SecureQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.SECURE || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }

            // ---- village-light path: handled via delayed scan in QuestLifecycleListener ----
            if (isVillageLightSecureQuest(quest)) {
                continue;
            }

            // ---- block-count path (original) ----
            SecureRequirement requirement = parseSecureRequirement(quest).orElse(null);
            if (requirement == null) {
                continue;
            }

            if (requirement.material() != placedMaterial) {
                continue;
            }

            if (!requirement.worldName().equalsIgnoreCase(placedLocation.getWorld().getName())) {
                continue;
            }

            int deltaX = placedLocation.getBlockX() - requirement.targetX();
            int deltaZ = placedLocation.getBlockZ() - requirement.targetZ();
            double horizontalDistanceSquared = (double) deltaX * deltaX + (double) deltaZ * deltaZ;
            if (horizontalDistanceSquared > (double) requirement.radius() * requirement.radius()) {
                continue;
            }

            int newProgress = Math.min(quest.goal(), quest.progress() + 1);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new SecureQuestUpdate(updatedQuest, newProgress >= quest.goal()));
        }
        return updates;
    }

    /**
     * Re-scans the sub-area of a village-light SECURE quest and returns the updated progress.
     * Used for {@code BlockBreakEvent} (light source removal) and quest-giver interaction checks.
     * May be called without an event – always safe to run synchronously.
     */
    public Optional<SecureQuestUpdate> syncVillageLightProgress(Player player, Quest quest) {
        if (!isVillageLightSecureQuest(quest) || quest.status() != QuestStatus.ACTIVE) {
            return Optional.empty();
        }
        if (!player.getUniqueId().equals(quest.playerUuid())) {
            return Optional.empty();
        }

        String worldName = extractSecureWorldName(quest);
        if (worldName == null || !worldName.equalsIgnoreCase(player.getWorld().getName())) {
            return Optional.empty();
        }
        int[] subCenter = extractVillageLightSubCenter(quest);
        if (subCenter == null) {
            return Optional.empty();
        }

        int darkCount = lightLevelScanner.scanSubArea(
                player.getWorld(), subCenter[0], subCenter[2], extractVillageLightAreaSize(quest));
        int initialDark = quest.goal();
        int removed = Math.max(0, initialDark - darkCount);
        int newProgress = Math.min(quest.goal(), Math.max(0, removed));
        if (newProgress == quest.progress()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        Quest updatedQuest = quest.withProgress(newProgress, now);
        questRepository.saveQuest(updatedQuest);
        return Optional.of(new SecureQuestUpdate(updatedQuest, newProgress >= quest.goal()));
    }

    /**
     * Applies {@link #syncVillageLightProgress} for all active village-light quests of a player.
     */
    public Collection<SecureQuestUpdate> syncAllVillageLightProgress(Player player) {
        Collection<SecureQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            syncVillageLightProgress(player, quest).ifPresent(updates::add);
        }
        return updates;
    }

    private String extractSecureWorldName(Quest quest) {
        if (quest.targetKey() == null) {
            return null;
        }
        // village-light uses pipe separator, block-count uses colon
        if (quest.targetKey().contains("|light|")) {
            String[] parts = quest.targetKey().split("\\|");
            return parts.length >= 2 ? parts[1] : null;
        }
        String[] parts = quest.targetKey().split(":");
        if (parts.length < 2) {
            return null;
        }
        return parts[1];
    }

    public int[] extractVillageLightSubCenter(Quest quest) {
        if (quest.targetKey() == null || !quest.targetKey().contains("|light|")) {
            return null;
        }
        String[] parts = quest.targetKey().split("\\|");
        // format: material|world|villageId|light|goal|initialDark|subCenterX|subCenterY|subCenterZ
        if (parts.length < 9) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(parts[6]),
                Integer.parseInt(parts[7]),
                Integer.parseInt(parts[8])
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public int extractVillageLightAreaSize(Quest quest) {
        // TODO: could be stored in targetKey or read from config.
        // For now a reasonable default that matches SubAreaSelector / DarkBlockCache.
        return 20;
    }

    public Optional<DeliveryRequirement> parseDeliveryRequirement(Quest quest) {
        if (quest.type() != QuestType.DELIVER) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DeliveryRequirement(material, Integer.parseInt(parts[1])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<FetchRequirement> parseFetchRequirement(Quest quest) {
        if (quest.type() != QuestType.FETCH) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new FetchRequirement(material, Integer.parseInt(parts[1])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<DeliveryRequirement> parseRepairRequirement(Quest quest) {
        if (quest.type() != QuestType.REPAIR) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DeliveryRequirement(material, Integer.parseInt(parts[1])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<Material> parseBuildRequirement(Quest quest) {
        if (quest.type() != QuestType.BUILD) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(quest.targetKey());
        return material == null ? Optional.empty() : Optional.of(material);
    }

    public Optional<BrewRequirement> parseBrewRequirement(Quest quest) {
        if (quest.type() != QuestType.BREW) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }

        PotionType potionType = parsePotionType(parts[0]);
        if (potionType == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BrewRequirement(potionType, Integer.parseInt(parts[1])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<VisitRequirement> parseVisitRequirement(Quest quest) {
        if (quest.type() != QuestType.VISIT) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }

        try {
            return Optional.of(new VisitRequirement(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<SecureRequirement> parseSecureRequirement(Quest quest) {
        if (quest.type() != QuestType.SECURE) {
            return Optional.empty();
        }

        String[] parts = quest.targetKey().split(":");
        // Support both old 5-part format (material:world:x:z:radius)
        // and new village-light format (material:world:villageId:light:goal:initialDark:cx:cy:cz)
        if (parts.length < 5) {
            return Optional.empty();
        }

        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return Optional.empty();
        }

        try {
            // Always extract worldName from parts[1], x from parts[2], z from parts[3], radius from parts[4]
            int targetX = Integer.parseInt(parts[2]);
            int targetZ = Integer.parseInt(parts[3]);
            int radius = Integer.parseInt(parts[4]);
            return Optional.of(new SecureRequirement(
                    material,
                    parts[1],
                    targetX,
                    targetZ,
                    radius));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /**
     * Returns true if the given SECURE quest uses the village-light mode.
     */
    public boolean isVillageLightSecureQuest(Quest quest) {
        return quest.type() == QuestType.SECURE && quest.targetKey() != null && quest.targetKey().contains("|light|");
    }

    private String createQuestId() {
        return "quest-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack itemStack = contents[index];
            if (itemStack == null || itemStack.getType() != material) {
                continue;
            }

            int removed = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - removed);
            if (itemStack.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= removed;
        }
        player.getInventory().setContents(contents);
        return amount - remaining;
    }

    private int removePotion(Player player, PotionType potionType, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack itemStack = contents[index];
            if (!matchesPotionRequirement(itemStack, potionType)) {
                continue;
            }

            int removed = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - removed);
            if (itemStack.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= removed;
        }
        player.getInventory().setContents(contents);
        return amount - remaining;
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack != null && itemStack.getType() == material) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    private boolean matchesPotionRequirement(ItemStack itemStack, PotionType potionType) {
        if (itemStack == null) {
            return false;
        }

        Material itemType = itemStack.getType();
        if (itemType != Material.POTION && itemType != Material.SPLASH_POTION && itemType != Material.LINGERING_POTION) {
            return false;
        }

        if (!(itemStack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return false;
        }

        return potionMeta.getBasePotionType() == potionType;
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private String formatEntityType(EntityType entityType) {
        return entityType.name().toLowerCase().replace('_', ' ');
    }

    private PotionType parsePotionType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return PotionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String formatPotionType(PotionType potionType) {
        return potionType.name().toLowerCase().replace('_', ' ');
    }

    public enum QuestAvailabilityFailureReason {
        NONE,
        ACTIVE_QUEST,
        COOLDOWN
    }

    public record TalkQuestAvailability(
            boolean allowed,
            QuestAvailabilityFailureReason failureReason,
            String activeQuestTitle,
            long remainingSeconds,
            String failureMessage) {
    }

    // ── RETINUE quests ──────────────────────────────────────────────

    public Quest activateRetinueGuardQuest(UUID playerUuid, Speaker chief, UUID chiefEntityUuid, int durationMinutes, int difficultyTier) {
        long now = System.currentTimeMillis();
        String targetKey = "RETINUE_GUARD:" + chiefEntityUuid + ":" + durationMinutes;
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.speakerId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.RETINUE_GUARD,
                "Leibwache (" + durationMinutes + " min)",
                "Bleibe " + durationMinutes + " Minuten in der Naehe des Haeuptlings und beschuetze ihn.",
                targetKey,
                durationMinutes,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateRetinueGolemQuest(UUID playerUuid, Speaker chief, String worldName, int perimeterMinX, int perimeterMaxX, int perimeterMinZ, int perimeterMaxZ, int difficultyTier) {
        long now = System.currentTimeMillis();
        String targetKey = "RETINUE_GOLEM:" + worldName + ":" + perimeterMinX + ":" + perimeterMaxX + ":" + perimeterMinZ + ":" + perimeterMaxZ;
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.speakerId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.RETINUE_GOLEM,
                "Golem-Wache",
                "Erschaffe einen Eisengolem innerhalb des Dorf-Perimeters.",
                targetKey,
                1,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateRetinueWallQuest(UUID playerUuid, Speaker chief, String worldName, int perimeterMinX, int perimeterMaxX, int perimeterMinZ, int perimeterMaxZ, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        String targetKey = "RETINUE_WALL:" + worldName + ":" + perimeterMinX + ":" + perimeterMaxX + ":" + perimeterMinZ + ":" + perimeterMaxZ;
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.speakerId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.RETINUE_WALL,
                "Mauerbau (" + amount + " Bloecke)",
                "Platziere " + amount + " Stein- oder Ziegelbloecke innerhalb des Dorf-Perimeters.",
                targetKey,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateRetinueBellQuest(UUID playerUuid, Speaker chief, double targetX, double targetY, double targetZ, String worldName, int difficultyTier) {
        long now = System.currentTimeMillis();
        String targetKey = "RETINUE_BELL:" + worldName + ":" + ((int) targetX) + ":" + ((int) targetY) + ":" + ((int) targetZ);
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.speakerId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.RETINUE_BELL,
                "Glocken-Stifter",
                "Bringe eine Glocke zum Treffpunkt des Dorfes (X=" + ((int) targetX) + ", Z=" + ((int) targetZ) + ").",
                targetKey,
                1,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
                return quest;
            }

            // ── LEGENDARY quests ───────────────────────────────────────────

            public Quest activateLegendaryDragonQuest(UUID playerUuid, Speaker chief, int difficultyTier) {
                long now = System.currentTimeMillis();
                String targetKey = "LEGENDARY_DRAGON:" + chief.entityUuid();
                Quest quest = new Quest(
                        createQuestId(),
                        playerUuid,
                        chief.speakerId(),
                        chief.villageId(),
                        Math.max(0, difficultyTier),
                        QuestType.LEGENDARY_DRAGON,
                        "Drachenjaeger",
                        "Toete den Enderdrachen und kehre zum Haeuptling zurueck.",
                        targetKey,
                        1,
                        0,
                        QuestStatus.ACTIVE,
                        now,
                        now);
                questRepository.saveQuest(quest);
                return quest;
            }

            public Quest activateLegendaryBlazeQuest(UUID playerUuid, Speaker chief, int difficultyTier) {
                long now = System.currentTimeMillis();
                String targetKey = "LEGENDARY_BLAZE:" + chief.entityUuid() + ":5";
                Quest quest = new Quest(
                        createQuestId(),
                        playerUuid,
                        chief.speakerId(),
                        chief.villageId(),
                        Math.max(0, difficultyTier),
                        QuestType.LEGENDARY_BLAZE,
                        "Lohenfaenger",
                        "Bringe 5 Lohenruten aus dem Nether und ueberreiche sie dem Haeuptling.",
                        targetKey,
                        5,
                        0,
                        QuestStatus.ACTIVE,
                        now,
                        now);
                questRepository.saveQuest(quest);
                return quest;
            }

            public Quest activateLegendaryEndQuest(UUID playerUuid, Speaker chief, int difficultyTier) {
                long now = System.currentTimeMillis();
                String targetKey = "LEGENDARY_END:" + chief.entityUuid();
                Quest quest = new Quest(
                        createQuestId(),
                        playerUuid,
                        chief.speakerId(),
                        chief.villageId(),
                        Math.max(0, difficultyTier),
                        QuestType.LEGENDARY_END,
                        "End-Trophae",
                        "Bringe eine Shulker-Schale oder Elytra aus dem End und ueberreiche sie dem Haeuptling.",
                        targetKey,
                        1,
                        0,
                        QuestStatus.ACTIVE,
                        now,
                        now);
                questRepository.saveQuest(quest);
                return quest;
            }

            public Quest activateLegendaryNetherQuest(UUID playerUuid, Speaker chief, int difficultyTier) {
                long now = System.currentTimeMillis();
                String targetKey = "LEGENDARY_NETHER:" + chief.entityUuid();
                Quest quest = new Quest(
                        createQuestId(),
                        playerUuid,
                        chief.speakerId(),
                        chief.villageId(),
                        Math.max(0, difficultyTier),
                        QuestType.LEGENDARY_NETHER,
                        "Nether-Beute",
                        "Bringe einen Nether-Stern oder Wither-Skelett-Schaedel und ueberreiche ihn dem Haeuptling.",
                        targetKey,
                        1,
                        0,
                        QuestStatus.ACTIVE,
                        now,
                        now);
                questRepository.saveQuest(quest);
                        return quest;
                    }

                    // ── LEGENDARY quest completion handlers ───────────────────────

                    public Collection<KillQuestUpdate> advanceLegendaryDragonQuests(Player player, EntityType killedType) {
                        if (killedType != EntityType.ENDER_DRAGON) {
                            return List.of();
                        }
                        long now = System.currentTimeMillis();
                        Collection<KillQuestUpdate> updates = new ArrayList<>();
                        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
                            if (quest.type() != QuestType.LEGENDARY_DRAGON || quest.status() != QuestStatus.ACTIVE) {
                                continue;
                            }
                            Quest updatedQuest = quest.withProgress(quest.goal(), now);
                            questRepository.saveQuest(updatedQuest);
                            updates.add(new KillQuestUpdate(updatedQuest, true));
                        }
                        return updates;
                    }

                    public Collection<BrewQuestUpdate> completeLegendaryBlazeQuests(Player player, String speakerId) {
                        long now = System.currentTimeMillis();
                        Collection<BrewQuestUpdate> updates = new ArrayList<>();
                        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
                            if (quest.type() != QuestType.LEGENDARY_BLAZE
                                    || quest.status() != QuestStatus.ACTIVE
                                    || !quest.speakerId().equals(speakerId)) {
                                continue;
                            }
                            int remainingAmount = Math.max(0, quest.goal() - quest.progress());
                            if (remainingAmount <= 0) {
                                Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
                                questRepository.saveQuest(updatedQuest);
                                updates.add(new BrewQuestUpdate(updatedQuest, 0, true));
                                continue;
                            }
                            int handedIn = removeMaterial(player, Material.BLAZE_ROD, remainingAmount);
                            if (handedIn <= 0) {
                                continue;
                            }
                            int newProgress = Math.min(quest.goal(), quest.progress() + handedIn);
                            Quest updatedQuest = quest.withProgress(newProgress, now);
                            boolean completed = newProgress >= quest.goal();
                            if (completed) {
                                updatedQuest = updatedQuest.withStatus(QuestStatus.COMPLETED, now);
                            }
                            questRepository.saveQuest(updatedQuest);
                            updates.add(new BrewQuestUpdate(updatedQuest, handedIn, completed));
                        }
                        return updates;
                    }

                    public Collection<BrewQuestUpdate> completeLegendaryEndQuests(Player player, String speakerId) {
                        long now = System.currentTimeMillis();
                        Collection<BrewQuestUpdate> updates = new ArrayList<>();
                        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
                            if (quest.type() != QuestType.LEGENDARY_END
                                    || quest.status() != QuestStatus.ACTIVE
                                    || !quest.speakerId().equals(speakerId)) {
                                continue;
                            }
                            int remainingAmount = Math.max(0, quest.goal() - quest.progress());
                            if (remainingAmount <= 0) {
                                Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
                                questRepository.saveQuest(updatedQuest);
                                updates.add(new BrewQuestUpdate(updatedQuest, 0, true));
                                continue;
                            }
                            int handedInShulker = removeMaterial(player, Material.SHULKER_SHELL, remainingAmount);
                            int handedInElytra = removeMaterial(player, Material.ELYTRA, remainingAmount - handedInShulker);
                            int handedIn = handedInShulker + handedInElytra;
                            if (handedIn <= 0) {
                                continue;
                            }
                            int newProgress = Math.min(quest.goal(), quest.progress() + handedIn);
                            Quest updatedQuest = quest.withProgress(newProgress, now);
                            boolean completed = newProgress >= quest.goal();
                            if (completed) {
                                updatedQuest = updatedQuest.withStatus(QuestStatus.COMPLETED, now);
                            }
                            questRepository.saveQuest(updatedQuest);
                            updates.add(new BrewQuestUpdate(updatedQuest, handedIn, completed));
                        }
                        return updates;
                    }

                    public Collection<BrewQuestUpdate> completeLegendaryNetherQuests(Player player, String speakerId) {
                        long now = System.currentTimeMillis();
                        Collection<BrewQuestUpdate> updates = new ArrayList<>();
                        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
                            if (quest.type() != QuestType.LEGENDARY_NETHER
                                    || quest.status() != QuestStatus.ACTIVE
                                    || !quest.speakerId().equals(speakerId)) {
                                continue;
                            }
                            int remainingAmount = Math.max(0, quest.goal() - quest.progress());
                            if (remainingAmount <= 0) {
                                Quest updatedQuest = quest.withStatus(QuestStatus.COMPLETED, now);
                                questRepository.saveQuest(updatedQuest);
                                updates.add(new BrewQuestUpdate(updatedQuest, 0, true));
                                continue;
                            }
                            int handedInStar = removeMaterial(player, Material.NETHER_STAR, remainingAmount);
                            int handedInSkull = removeMaterial(player, Material.WITHER_SKELETON_SKULL, remainingAmount - handedInStar);
                            int handedIn = handedInStar + handedInSkull;
                            if (handedIn <= 0) {
                                continue;
                            }
                            int newProgress = Math.min(quest.goal(), quest.progress() + handedIn);
                            Quest updatedQuest = quest.withProgress(newProgress, now);
                            boolean completed = newProgress >= quest.goal();
                            if (completed) {
                                updatedQuest = updatedQuest.withStatus(QuestStatus.COMPLETED, now);
                            }
                            questRepository.saveQuest(updatedQuest);
                            updates.add(new BrewQuestUpdate(updatedQuest, handedIn, completed));
                        }
                        return updates;
                    }

                    public Collection<RetinueGuardUpdate> advanceRetinueGuardQuests(Player player, UUID chiefEntityUuid) {
        long now = System.currentTimeMillis();
        Collection<RetinueGuardUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.RETINUE_GUARD || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }
            String[] parts = quest.targetKey().split(":");
            if (parts.length < 3 || !parts[1].equals(chiefEntityUuid.toString())) {
                continue;
            }
            int newProgress = Math.min(quest.goal(), quest.progress() + 1);
            Quest updatedQuest = quest.withProgress(newProgress, now);
            questRepository.saveQuest(updatedQuest);
            updates.add(new RetinueGuardUpdate(updatedQuest, newProgress >= quest.goal()));
        }
        return updates;
    }

    public Collection<RetinueGolemUpdate> advanceRetinueGolemQuests(Player player, Location createdLocation) {
        long now = System.currentTimeMillis();
        Collection<RetinueGolemUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.RETINUE_GOLEM || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }
            // targetKey: RETINUE_GOLEM:worldName:minX:maxX:minZ:maxZ
            String[] parts = quest.targetKey().split(":");
            if (parts.length < 6 || !parts[1].equalsIgnoreCase(createdLocation.getWorld().getName())) {
                continue;
            }
            try {
                int minX = Integer.parseInt(parts[2]);
                int maxX = Integer.parseInt(parts[3]);
                int minZ = Integer.parseInt(parts[4]);
                int maxZ = Integer.parseInt(parts[5]);
                int bx = createdLocation.getBlockX();
                int bz = createdLocation.getBlockZ();
                if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) {
                    continue;
                }
                Quest updatedQuest = quest.withProgress(quest.goal(), now);
                questRepository.saveQuest(updatedQuest);
                updates.add(new RetinueGolemUpdate(updatedQuest, true));
            } catch (NumberFormatException ignored) {
            }
        }
        return updates;
    }

    public Collection<RetinueWallUpdate> advanceRetinueWallQuests(Player player, Material placedMaterial, Location placedLocation) {
        long now = System.currentTimeMillis();
        Collection<RetinueWallUpdate> updates = new ArrayList<>();
        // Only count stone-like blocks
        if (!isStoneOrBrick(placedMaterial)) {
            return updates;
        }
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.RETINUE_WALL || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }
            // targetKey: RETINUE_WALL:worldName:minX:maxX:minZ:maxZ
            String[] parts = quest.targetKey().split(":");
            if (parts.length < 6 || !parts[1].equalsIgnoreCase(placedLocation.getWorld().getName())) {
                continue;
            }
            try {
                int minX = Integer.parseInt(parts[2]);
                int maxX = Integer.parseInt(parts[3]);
                int minZ = Integer.parseInt(parts[4]);
                int maxZ = Integer.parseInt(parts[5]);
                int bx = placedLocation.getBlockX();
                int bz = placedLocation.getBlockZ();
                if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) {
                    continue;
                }
                int newProgress = Math.min(quest.goal(), quest.progress() + 1);
                Quest updatedQuest = quest.withProgress(newProgress, now);
                questRepository.saveQuest(updatedQuest);
                updates.add(new RetinueWallUpdate(updatedQuest, newProgress >= quest.goal()));
            } catch (NumberFormatException ignored) {
            }
        }
        return updates;
    }

    private boolean isStoneOrBrick(Material material) {
        String name = material.name();
        return name.contains("STONE") || name.contains("BRICK") || name.contains("COBBLESTONE");
    }

    public Collection<RetinueBellUpdate> advanceRetinueBellQuests(Player player, Material placedMaterial, Location placedLocation) {
        long now = System.currentTimeMillis();
        Collection<RetinueBellUpdate> updates = new ArrayList<>();
        if (placedMaterial != Material.BELL) {
            return updates;
        }
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.RETINUE_BELL || quest.status() != QuestStatus.ACTIVE) {
                continue;
            }
            // targetKey: RETINUE_BELL:worldName:targetX:targetY:targetZ
            String[] parts = quest.targetKey().split(":");
            if (parts.length < 5 || !parts[1].equalsIgnoreCase(placedLocation.getWorld().getName())) {
                continue;
            }
            try {
                int targetX = Integer.parseInt(parts[2]);
                int targetY = Integer.parseInt(parts[3]);
                int targetZ = Integer.parseInt(parts[4]);
                int bx = placedLocation.getBlockX();
                int by = placedLocation.getBlockY();
                int bz = placedLocation.getBlockZ();
                // Within 2 blocks of target
                if (Math.abs(bx - targetX) > 2 || Math.abs(by - targetY) > 2 || Math.abs(bz - targetZ) > 2) {
                    continue;
                }
                Quest updatedQuest = quest.withProgress(quest.goal(), now);
                questRepository.saveQuest(updatedQuest);
                updates.add(new RetinueBellUpdate(updatedQuest, true));
            } catch (NumberFormatException ignored) {
            }
        }
        return updates;
    }

    public record RetinueGuardUpdate(Quest quest, boolean readyToTurnIn) {}

    public record RetinueGolemUpdate(Quest quest, boolean readyToTurnIn) {}

    public record RetinueWallUpdate(Quest quest, boolean readyToTurnIn) {}

    public record RetinueBellUpdate(Quest quest, boolean readyToTurnIn) {}

    public record DeliveryRequirement(Material material, int amount) {
    }

    public record BrewRequirement(PotionType potionType, int amount) {
    }

    public record FetchRequirement(Material material, int amount) {
    }

    public record SecureRequirement(Material material, String worldName, int targetX, int targetZ, int radius) {
    }

    public record DeliverQuestUpdate(Quest quest, int handedInAmount, boolean completed) {
    }

    public record BrewQuestUpdate(Quest quest, int handedInAmount, boolean completed) {
    }

    public record FetchQuestUpdate(Quest quest, int previousProgress, boolean readyToTurnIn) {
    }

    public record KillQuestUpdate(Quest quest, boolean readyToTurnIn) {
    }

    public record BuildQuestUpdate(Quest quest, boolean readyToTurnIn) {
    }

    public record BreedQuestUpdate(Quest quest, boolean readyToTurnIn) {
    }

    public record SecureQuestUpdate(Quest quest, boolean readyToTurnIn) {
    }

    public record ExploreQuestUpdate(Quest quest, VisitRequirement requirement) {
    }

    public record VisitRequirement(String worldName, int targetX, int targetZ, int radius) {
    }

    public record VisitQuestUpdate(Quest quest, VisitRequirement requirement) {
    }
}
