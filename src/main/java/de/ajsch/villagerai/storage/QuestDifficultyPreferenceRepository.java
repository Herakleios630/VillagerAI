package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.QuestDifficultyPreference;
import java.util.Optional;
import java.util.UUID;

public interface QuestDifficultyPreferenceRepository {

    Optional<QuestDifficultyPreference> findByPlayerAndChief(UUID playerUuid, String chiefId);

    void savePreference(QuestDifficultyPreference preference);
}