package de.ajsch.villagerai.model;

import java.util.UUID;

public record QuestDifficultyPreference(
        UUID playerUuid,
        String speakerId,
        int preferredDifficultyTier,
        int lastSuggestedTier,
        long lastSuggestedAtEpochMillis,
        long updatedAtEpochMillis) {

    public QuestDifficultyPreference {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException("speakerId must not be blank");
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
                speakerId,
                tier,
                lastSuggestedTier,
                lastSuggestedAtEpochMillis,
                updatedAt);
    }

    public QuestDifficultyPreference withSuggestion(int tier, long suggestedAt, long updatedAt) {
        return new QuestDifficultyPreference(
                playerUuid,
                speakerId,
                preferredDifficultyTier,
                tier,
                suggestedAt,
                updatedAt);
    }
}