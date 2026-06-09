package de.ajsch.villagerai.model;

import java.util.UUID;

public record SpeakerReputation(
        UUID playerUuid,
        String speakerId,
        int score,
        String lastReason,
        long updatedAtEpochMillis) {

    public SpeakerReputation {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException("speakerId must not be blank");
        }
        if (lastReason == null || lastReason.isBlank()) {
            throw new IllegalArgumentException("lastReason must not be blank");
        }
    }

    public SpeakerReputation withScore(int newScore, String reason, long updatedAt) {
        return new SpeakerReputation(playerUuid, speakerId, newScore, reason, updatedAt);
    }
}