package de.ajsch.villagerai.model;

import java.util.UUID;

public record VillageReputation(
        UUID playerUuid,
        String villageId,
        int score,
        String lastReason,
        long updatedAtEpochMillis) {

    public VillageReputation {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        if (lastReason == null || lastReason.isBlank()) {
            throw new IllegalArgumentException("lastReason must not be blank");
        }
    }

    public VillageReputation withScore(int newScore, String reason, long updatedAt) {
        return new VillageReputation(playerUuid, villageId, newScore, reason, updatedAt);
    }
}