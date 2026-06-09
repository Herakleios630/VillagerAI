package de.ajsch.villagerai.model;

import java.util.UUID;

public record Chief(
        UUID entityUuid,
        String chiefId,
        String villageId,
                String villageName,
                String villageDescription,
                String villageAttributes,
                String villageBiome,
                int villagePopulationEstimate,
                String villageEventSummary,
                String displayName,
                String role,
                String personality,
                String speechTone,
                String behaviorHint,
                String greeting,
        String world,
        double x,
        double y,
        double z) {

        public String chatName() {
                return displayName == null || displayName.isBlank() ? "Haeuptling" : displayName;
        }
}