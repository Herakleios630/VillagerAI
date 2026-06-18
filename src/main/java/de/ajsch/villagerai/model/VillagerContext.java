package de.ajsch.villagerai.model;

public record VillagerContext(
        String profession,
        String villagerType,
        String currentBiome,
        String worldName,
        boolean isDay,
        boolean isRaining,
        boolean isThundering,
        double currentHealth,
        double maxHealth,
        double healthRatio,
        boolean ateRecently,
        String tradeSummary,
        String confinementSummary,
        String authoritativeWorldFactsSummary,
        String homePoi,
        String jobSitePoi,
        String potentialJobSitePoi,
        String meetingPointPoi,
        long mcDay,
        long mcTime) {
}