package de.ajsch.villagerai.model;

import java.util.List;

public record VillageRecord(
        String villageId,
        String villageName,
        long registeredAt,
        List<Anchor> knownAnchors) {

    public VillageRecord {
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        if (villageName == null || villageName.isBlank()) {
            throw new IllegalArgumentException("villageName must not be blank");
        }
        if (registeredAt <= 0) {
            throw new IllegalArgumentException("registeredAt must be positive");
        }
        if (knownAnchors == null) {
            knownAnchors = List.of();
        } else {
            knownAnchors = List.copyOf(knownAnchors);
        }
    }
}