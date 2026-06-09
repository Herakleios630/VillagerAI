package de.ajsch.villagerai.model;

import java.util.List;
import java.util.UUID;

public record VillagerTradeHistory(
        UUID playerUuid,
        UUID villagerUuid,
        List<VillagerTradeRecord> trades,
        long updatedAtEpochMillis) {
}