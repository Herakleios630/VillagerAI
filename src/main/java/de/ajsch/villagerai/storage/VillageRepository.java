package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.VillageRecord;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.Location;

public interface VillageRepository {

    Optional<VillageRecord> findByAnchor(Location anchor, int maxDistance);

    Optional<VillageRecord> findByVillageId(String villageId);

    void save(VillageRecord record);

    Collection<VillageRecord> findAll();
}