package de.ajsch.villagerai.model;

import java.util.UUID;

public record Quest(
        String questId,
        UUID playerUuid,
        String chiefId,
        String villageId,
    int difficultyTier,
        QuestType type,
        String title,
        String description,
        String targetKey,
        int goal,
        int progress,
        QuestStatus status,
        long createdAtEpochMillis,
        long updatedAtEpochMillis) {

    public Quest {
        if (questId == null || questId.isBlank()) {
            throw new IllegalArgumentException("questId must not be blank");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (chiefId == null || chiefId.isBlank()) {
            throw new IllegalArgumentException("chiefId must not be blank");
        }
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (difficultyTier < 0) {
            throw new IllegalArgumentException("difficultyTier must not be negative");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (goal < 1) {
            throw new IllegalArgumentException("goal must be at least 1");
        }
        if (progress < 0 || progress > goal) {
            throw new IllegalArgumentException("progress must be between 0 and goal");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    public Quest withProgress(int newProgress, long updatedAt) {
        return new Quest(
                questId,
                playerUuid,
                chiefId,
                villageId,
                difficultyTier,
                type,
                title,
                description,
                targetKey,
                goal,
                newProgress,
                status,
                createdAtEpochMillis,
                updatedAt);
    }

    public Quest withStatus(QuestStatus newStatus, long updatedAt) {
        return new Quest(
                questId,
                playerUuid,
                chiefId,
                villageId,
                difficultyTier,
                type,
                title,
                description,
                targetKey,
                goal,
                progress,
                newStatus,
                createdAtEpochMillis,
                updatedAt);
    }
}