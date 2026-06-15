package de.ajsch.villagerai.model;

public record Anchor(
        AnchorType type,
        String world,
        int x,
        int y,
        int z) {

    public Anchor {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
    }

    public String posKey() {
        return type.name() + ";" + world + ";" + x + ";" + y + ";" + z;
    }

    public enum AnchorType {
        MEETING_POINT,
        HOME,
        JOB_SITE,
        POTENTIAL_JOB_SITE,
        VILLAGER_POSITION
    }
}