package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.ConversationHistory;
import de.ajsch.villagerai.model.ConversationTurn;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ConversationHistoryRepository {

    Optional<ConversationHistory> findHistory(UUID playerUuid, String chiefId);

    Collection<ConversationHistory> findByPlayerUuid(UUID playerUuid);

    void appendTurn(UUID playerUuid, Chief chief, ConversationTurn turn);

    void clearHistory(UUID playerUuid, String chiefId);
}