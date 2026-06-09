package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.SpeakerReputation;
import de.ajsch.villagerai.model.VillageReputation;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ReputationRepository {

    Optional<VillageReputation> findByPlayerAndVillage(UUID playerUuid, String villageId);

    Optional<SpeakerReputation> findByPlayerAndSpeaker(UUID playerUuid, String speakerId);

    Collection<VillageReputation> findByPlayerUuid(UUID playerUuid);

    void saveReputation(VillageReputation reputation);

    void saveReputation(SpeakerReputation reputation);
}