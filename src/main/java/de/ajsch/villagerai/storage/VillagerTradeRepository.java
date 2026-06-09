package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.VillagerTradeHistory;
import de.ajsch.villagerai.model.VillagerTradeRecord;
import java.util.Optional;
import java.util.UUID;

public interface VillagerTradeRepository {

    Optional<VillagerTradeHistory> findHistory(UUID playerUuid, UUID villagerUuid);

    void appendTrade(UUID playerUuid, UUID villagerUuid, VillagerTradeRecord tradeRecord);
}