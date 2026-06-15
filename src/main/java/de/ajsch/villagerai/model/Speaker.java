package de.ajsch.villagerai.model;

import java.util.UUID;

/**
 * Zentrale Gespraechsobjekt fuer ConversationService, AIRequest und PromptBuilder.
 * Enthaelt KEINE Dorf-Identitaetsfelder (Runtime-Enrichment via VillageIdentityService)
 * und KEINE Chief-spezifischen Felder (kommen in ChiefAttributes).
 * Nutzt das vorhandene {@link SpeakerStatus}-Enum.
 */
public record Speaker(
    UUID entityUuid,
    String speakerId,
    String villageId,
    String villageName,
    String displayName,
    String role,
    String personality,
    String speechTone,
    String behaviorHint,
    String greeting,
    String profession,
    String world,
    double x,
    double y,
    double z,
    SpeakerStatus speakerStatus
) {
    /** Liefert den Chat-Namen: Fuer den aktiven Chief "Haeuptling", sonst den displayName. */
    public String chatName() {
        return speakerStatus == SpeakerStatus.AKTIV_CHIEF ? "Haeuptling" : displayName;
    }

    /** Convenience-Methode, ob dieser Speaker ein aktiver Chief ist. */
    public boolean isChief() {
        return speakerStatus == SpeakerStatus.AKTIV_CHIEF;
    }
}