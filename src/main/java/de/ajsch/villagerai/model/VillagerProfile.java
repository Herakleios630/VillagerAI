package de.ajsch.villagerai.model;

import java.util.UUID;

public record VillagerProfile(
        UUID entityUuid,
        String speakerId,
        String villageId,
        String villageName,
        String displayName,
        String role,
        String personality,
        String greeting,
        String profession,
        long updatedAtEpochMillis) {

    public VillagerProfile {
        if (entityUuid == null) {
            throw new IllegalArgumentException("entityUuid must not be null");
        }
        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException("speakerId must not be blank");
        }
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        if (villageName == null || villageName.isBlank()) {
            throw new IllegalArgumentException("villageName must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        if (personality == null || personality.isBlank()) {
            throw new IllegalArgumentException("personality must not be blank");
        }
        if (greeting == null || greeting.isBlank()) {
            throw new IllegalArgumentException("greeting must not be blank");
        }
        if (profession == null || profession.isBlank()) {
            throw new IllegalArgumentException("profession must not be blank");
        }
    }
}