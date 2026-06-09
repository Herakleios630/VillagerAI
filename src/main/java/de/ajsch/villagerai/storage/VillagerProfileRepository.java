package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.VillagerProfile;
import java.util.Optional;
import java.util.UUID;

public interface VillagerProfileRepository {

    Optional<VillagerProfile> findByEntityUuid(UUID entityUuid);

    void saveProfile(VillagerProfile profile);

    void removeProfile(UUID entityUuid);
}