package de.ajsch.villagerai.model;

import java.util.List;
import java.util.UUID;

public record ConversationHistory(
        UUID playerUuid,
        String chiefId,
        String villageId,
        List<ConversationTurn> turns,
        long updatedAtEpochMillis) {

    public ConversationHistory {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid must not be null");
        }
        if (chiefId == null || chiefId.isBlank()) {
            throw new IllegalArgumentException("chiefId must not be blank");
        }
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        turns = turns == null ? List.of() : List.copyOf(turns);
    }
}