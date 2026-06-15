package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.ChiefAttributes;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistiert ausschliesslich Chief-spezifische Attribute (visualTier,
 * banner, legendary, etc.).  Die Village-Zuordnung und die Speaker-Daten
 * verwaltet {@link SpeakerRepository}.
 */
public interface ChiefRepository {

    Optional<ChiefAttributes> findByEntityUuid(UUID entityUuid);

    Optional<ChiefAttributes> findActiveByVillageId(String villageId);

    List<ChiefAttributes> findAll();

    void save(ChiefAttributes attributes);

    void deleteByEntityUuid(UUID entityUuid);

    /**
     * Uebergangs-Bruecke: Loescht alle ChiefAttributes zu dieser Entity-UUID.
     * Wird nur von ChiefService.unmarkChief() verwendet, das spaeter
     * auf Speaker+ChiefAttributes umgestellt wird.
     */
    default void deleteChiefAttributes(UUID entityUuid) {
        deleteByEntityUuid(entityUuid);
    }
}