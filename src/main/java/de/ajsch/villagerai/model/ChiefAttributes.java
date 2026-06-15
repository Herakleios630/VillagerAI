package de.ajsch.villagerai.model;

import java.util.UUID;

public record ChiefAttributes(
    UUID entityUuid,
    String speakerId,
    String villageId,
    long crownedAt,
    long mournedAt,
    boolean isActive,
    String visualTier,
    String biomeStyle,
    String bannerPattern,
    boolean legendaryUnlocked,
    long legendaryLastActivated
) {
    public static ChiefAttributes createNew(UUID entityUuid, String speakerId, String villageId) {
        return new ChiefAttributes(
            entityUuid, speakerId, villageId, System.currentTimeMillis(),
            0L, true, null, null, "default", false, 0L
        );
    }
}