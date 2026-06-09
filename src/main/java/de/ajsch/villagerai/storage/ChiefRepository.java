package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Chief;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ChiefRepository {

    Optional<Chief> findByEntityUuid(UUID entityUuid);

    Collection<Chief> findAll();

    void saveChief(Chief chief);

    void removeChief(UUID entityUuid);
}