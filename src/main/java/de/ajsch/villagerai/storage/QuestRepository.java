package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Quest;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface QuestRepository {

    Optional<Quest> findByQuestId(String questId);

    Collection<Quest> findByPlayerUuid(UUID playerUuid);

    Collection<Quest> findAll();

    void saveQuest(Quest quest);

    void removeQuest(String questId);
}