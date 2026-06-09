package de.ajsch.villagerai.model;

public record QuestRewardResult(
        int experiencePoints,
        int emeraldsGranted,
        String bonusItemsSummary,
        double quantityMultiplier,
        int villageReputationScore,
        int speakerReputationScore) {
}