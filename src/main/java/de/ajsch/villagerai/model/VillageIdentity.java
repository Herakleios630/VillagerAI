package de.ajsch.villagerai.model;

public record VillageIdentity(
        String villageId,
    String villageName,
    String villageDescription,
    String villageAttributes,
    String villageBiome,
    int villagePopulationEstimate,
    String villageEventSummary) {

    public VillageIdentity {
        if (villageId == null || villageId.isBlank()) {
            throw new IllegalArgumentException("villageId must not be blank");
        }
        if (villageName == null || villageName.isBlank()) {
            throw new IllegalArgumentException("villageName must not be blank");
        }
        if (villageDescription == null || villageDescription.isBlank()) {
            throw new IllegalArgumentException("villageDescription must not be blank");
        }
        if (villageAttributes == null || villageAttributes.isBlank()) {
            throw new IllegalArgumentException("villageAttributes must not be blank");
        }
        if (villageBiome == null || villageBiome.isBlank()) {
            throw new IllegalArgumentException("villageBiome must not be blank");
        }
        if (villagePopulationEstimate < 1) {
            throw new IllegalArgumentException("villagePopulationEstimate must be at least 1");
        }
        if (villageEventSummary == null || villageEventSummary.isBlank()) {
            throw new IllegalArgumentException("villageEventSummary must not be blank");
        }
    }
}