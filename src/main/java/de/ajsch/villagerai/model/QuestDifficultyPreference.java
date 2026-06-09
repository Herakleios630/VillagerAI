package de.ajsch.villagerai.model;

import java.util.UUID;

public record QuestDifficultyPreference(
        UUID playerUuid,
        String chiefId,
        int preferredDifficultyTier,
        int lastSuggestedTier,
        long lastSuggestedAtEpochMillis,
        long updatedAtEpochMillis) {

    public QuestDifficultyPreference {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (chiefId == null || chiefId.isBlank()) {
            throw new IllegalArgumentException("chiefId must not be blank");
        }
        if (preferredDifficultyTier < 0) {
            throw new IllegalArgumentException("preferredDifficultyTier must not be negative");
        }
        if (lastSuggestedTier < 0) {
            throw new IllegalArgumentException("lastSuggestedTier must not be negative");
        }
    }

    public QuestDifficultyPreference withPreferredDifficultyTier(int tier, long updatedAt) {
        return new QuestDifficultyPreference(
                playerUuid,
                chiefId,
                tier,
                lastSuggestedTier,
                lastSuggestedAtEpochMillis,
                updatedAt);
    }

    public QuestDifficultyPreference withSuggestion(int tier, long suggestedAt, long updatedAt) {
        return new QuestDifficultyPreference(
                playerUuid,
                chiefId,
                preferredDifficultyTier,
                tier,
                suggestedAt,
                updatedAt);
    }
}