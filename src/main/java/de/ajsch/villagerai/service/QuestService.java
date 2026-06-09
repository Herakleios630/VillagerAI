package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.storage.QuestRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class QuestService {

    private final QuestRepository questRepository;
    private volatile long talkQuestCooldownMillis;

    public QuestService(QuestRepository questRepository, long talkQuestCooldownSeconds) {
        this.questRepository = questRepository;
        reloadTalkQuestCooldown(talkQuestCooldownSeconds);
    }

    public void reloadTalkQuestCooldown(long talkQuestCooldownSeconds) {
        this.talkQuestCooldownMillis = Math.max(0L, talkQuestCooldownSeconds) * 1000L;
    }

    public Quest offerTalkQuest(UUID playerUuid, Chief chief, String title, String description) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                0,
                QuestType.TALK,
                title,
                description,
                chief.chiefId(),
                1,
                0,
                QuestStatus.OFFERED,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateTalkQuest(UUID playerUuid, Chief chief, String title, String description) {
        return acceptQuest(offerTalkQuest(playerUuid, chief, title, description).questId()).orElseThrow();
    }

    public Quest activateDeliverQuest(UUID playerUuid, Chief chief, Material material, int amount) {
        return activateDeliverQuest(playerUuid, chief, material, amount, 0);
    }

    public Quest activateDeliverQuest(UUID playerUuid, Chief chief, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.DELIVER,
                "Liefere " + amount + " " + formatMaterial(material),
                "Bringe " + amount + " " + formatMaterial(material) + " zu " + chief.chatName() + ".",
                material.name() + ":" + amount,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateRepairQuest(UUID playerUuid, Chief chief, Material material, int amount) {
        return activateRepairQuest(playerUuid, chief, material, amount, 0);
    }

    public Quest activateRepairQuest(UUID playerUuid, Chief chief, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.REPAIR,
                "Repariere mit " + amount + " " + formatMaterial(material),
                "Bringe " + amount + " " + formatMaterial(material) + " fuer Reparaturen zu " + chief.chatName() + ".",
                material.name() + ":" + amount,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateBuildQuest(UUID playerUuid, Chief chief, Material material, int amount) {
        return activateBuildQuest(playerUuid, chief, material, amount, 0);
    }

    public Quest activateBuildQuest(UUID playerUuid, Chief chief, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.BUILD,
                "Baue " + amount + " " + formatMaterial(material),
                "Platziere " + amount + " " + formatMaterial(material) + " fuer " + chief.chatName() + ".",
                material.name(),
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateBreedQuest(UUID playerUuid, Chief chief, EntityType entityType, int amount) {
        return activateBreedQuest(playerUuid, chief, entityType, amount, 0);
    }

    public Quest activateBreedQuest(UUID playerUuid, Chief chief, EntityType entityType, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.BREED,
                "Zuechte " + amount + " " + formatEntityType(entityType),
                "Zuechte " + amount + " " + formatEntityType(entityType) + " fuer " + chief.chatName() + ".",
                entityType.name(),
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateBrewQuest(UUID playerUuid, Chief chief, PotionType potionType, int amount) {
        return activateBrewQuest(playerUuid, chief, potionType, amount, 0);
    }

    public Quest activateBrewQuest(UUID playerUuid, Chief chief, PotionType potionType, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        String potionName = formatPotionType(potionType);
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.BREW,
                "Braue " + amount + " " + potionName,
                "Bringe " + amount + " " + potionName + " zu " + chief.chatName() + ".",
                potionType.name() + ":" + amount,
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateFetchQuest(Player player, Chief chief, Material material, int amount) {
        return activateFetchQuest(player, chief, material, amount, 0);
    }

    public Quest activateFetchQuest(Player player, Chief chief, Material material, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        int initialProgress = Math.min(amount, countMaterial(player, material));
        Quest quest = new Quest(
                createQuestId(),
                player.getUniqueId(),
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.FETCH,
                "Sammle " + amount + " " + formatMaterial(material),
                "Besorge " + amount + " " + formatMaterial(material) + " und melde dich danach bei "
                        + chief.chatName() + ".",
                material.name() + ":" + amount,
                amount,
                initialProgress,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateKillQuest(UUID playerUuid, Chief chief, EntityType entityType, int amount) {
        return activateKillQuest(playerUuid, chief, entityType, amount, 0);
    }

    public Quest activateKillQuest(UUID playerUuid, Chief chief, EntityType entityType, int amount, int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.KILL,
                "Toete " + amount + " " + formatEntityType(entityType),
                "Besiege " + amount + " " + formatEntityType(entityType) + " fuer " + chief.chatName() + ".",
                entityType.name(),
                amount,
                0,
                QuestStatus.ACTIVE,
                now,
                now);
        questRepository.saveQuest(quest);
        return quest;
    }

    public Quest activateVisitQuest(UUID playerUuid, Chief chief, String worldName, int targetX, int targetZ, int radius) {
        return activateVisitQuest(playerUuid, chief, worldName, targetX, targetZ, radius, 0);
    }

    public Quest activateVisitQuest(
            UUID playerUuid,
            Chief chief,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.VISIT,
                "Reise nach X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach bei "
                        + chief.chatName() + ".",
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
            Chief chief,
            Material material,
            int amount,
            String worldName,
            int targetX,
            int targetZ,
            int radius) {
        return activateSecureQuest(playerUuid, chief, material, amount, worldName, targetX, targetZ, radius, 0);
    }

    public Quest activateSecureQuest(
            UUID playerUuid,
            Chief chief,
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
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.SECURE,
                "Sichere mit " + amount + " " + formatMaterial(material),
                "Platziere " + amount + " " + formatMaterial(material)
                        + " bei X " + targetX + " / Z " + targetZ
                        + " und melde dich danach bei " + chief.chatName() + ".",
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

    public Optional<Quest> findLatestQuestForChief(UUID playerUuid, String chiefId) {
        return questRepository.findByPlayerUuid(playerUuid).stream()
                .filter(quest -> quest.chiefId().equals(chiefId))
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

    public Collection<Quest> cancelActiveQuestsForChief(String chiefId) {
        long now = System.currentTimeMillis();
        Collection<Quest> cancelledQuests = new ArrayList<>();
        for (Quest quest : questRepository.findAll()) {
            if ((quest.status() != QuestStatus.OFFERED && quest.status() != QuestStatus.ACTIVE)
                    || !quest.chiefId().equals(chiefId)) {
                continue;
            }

            Quest updatedQuest = quest.withStatus(QuestStatus.CANCELLED, now);
            questRepository.saveQuest(updatedQuest);
            cancelledQuests.add(updatedQuest);
        }
        return cancelledQuests;
    }

    public TalkQuestAvailability validateQuestActivation(UUID playerUuid, String chiefId) {
        Optional<Quest> activeQuest = findActiveQuest(playerUuid);
        if (activeQuest.isPresent()) {
            return new TalkQuestAvailability(
                    false,
                    QuestAvailabilityFailureReason.ACTIVE_QUEST,
                    activeQuest.get().title(),
                    0L,
                    "Du hast bereits eine aktive Quest: " + activeQuest.get().title() + ". Brich sie erst ab oder schliesse sie ab.");
        }

        Optional<Quest> latestCompletedQuest = findLatestQuestForChief(playerUuid, chiefId)
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
        Optional<Quest> latestCancelledQuest = findLatestQuestForChief(playerUuid, chiefId)
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

    public TalkQuestAvailability validateTalkQuestActivation(UUID playerUuid, String chiefId) {
        return validateQuestActivation(playerUuid, chiefId);
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

    public Collection<DeliverQuestUpdate> completeActiveDeliverQuests(Player player, String chiefId) {
        long now = System.currentTimeMillis();
        Collection<DeliverQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.DELIVER
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.chiefId().equals(chiefId)) {
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

    public Collection<DeliverQuestUpdate> completeActiveRepairQuests(Player player, String chiefId) {
        long now = System.currentTimeMillis();
        Collection<DeliverQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.REPAIR
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.chiefId().equals(chiefId)) {
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

    public Collection<BrewQuestUpdate> completeActiveBrewQuests(Player player, String chiefId) {
        long now = System.currentTimeMillis();
        Collection<BrewQuestUpdate> updates = new ArrayList<>();
        for (Quest quest : questRepository.findByPlayerUuid(player.getUniqueId())) {
            if (quest.type() != QuestType.BREW
                    || quest.status() != QuestStatus.ACTIVE
                    || !quest.chiefId().equals(chiefId)) {
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

    public Collection<Quest> completeReadyInteractionQuests(UUID playerUuid, String chiefId) {
        long now = System.currentTimeMillis();
        Collection<Quest> completedQuests = new ArrayList<>();
        Optional<Quest> activeQuest = findActiveQuest(playerUuid);
        for (Quest quest : questRepository.findByPlayerUuid(playerUuid)) {
            if (quest.status() != QuestStatus.ACTIVE
                    || !quest.chiefId().equals(chiefId)
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
            Chief chief,
            String worldName,
            int targetX,
            int targetZ,
            int radius) {
        return activateExploreQuest(playerUuid, chief, worldName, targetX, targetZ, radius, 0);
    }

    public Quest activateExploreQuest(
            UUID playerUuid,
            Chief chief,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            int difficultyTier) {
        long now = System.currentTimeMillis();
        Quest quest = new Quest(
                createQuestId(),
                playerUuid,
                chief.chiefId(),
                chief.villageId(),
                Math.max(0, difficultyTier),
                QuestType.EXPLORE,
                "Erkunde X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach bei "
                        + chief.chatName() + ".",
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

        String[] parts = quest.targetKey().split(":", 5);
        if (parts.length != 5) {
            return Optional.empty();
        }

        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new SecureRequirement(
                    material,
                    parts[1],
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
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
