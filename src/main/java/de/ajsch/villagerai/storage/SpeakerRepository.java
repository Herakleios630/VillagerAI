package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Speaker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository fuer alle gespraechsfaehigen Dorfbewohner (Chiefs und normale Villager).
 * Speichert ausschliesslich Speaker-Daten – keine Dorf-Identitaetsfelder,
 * keine Chief-spezifischen Attribute.
 */
public interface SpeakerRepository {

    Optional<Speaker> findByEntityUuid(UUID entityUuid);

    Optional<Speaker> findBySpeakerId(String speakerId);

    List<Speaker> findByVillageId(String villageId);

    List<Speaker> findAllActiveChiefs();

    void save(Speaker speaker);

    void deleteByEntityUuid(UUID entityUuid);

    void deleteBySpeakerId(String speakerId);
}