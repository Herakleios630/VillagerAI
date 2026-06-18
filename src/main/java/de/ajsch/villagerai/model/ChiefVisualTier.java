package de.ajsch.villagerai.model;

import org.bukkit.Color;

/**
 * Visual rank tiers for a Chief, derived from combined reputation.
 * Does NOT contain any rendering details (colors, items, banner layers).
 * Those belong to {@link de.ajsch.villagerai.service.ChiefVisualService}
 * and future mod render layers.
 */
public enum ChiefVisualTier {
    TIER_0(0, "Schlicht", "Basis-Rang, keine besonderen visuellen Akzente",
            Color.fromRGB(210, 180, 140)),
    TIER_1(25, "Kleine Akzente", "Erste leichte visuelle Verbesserungen",
            Color.fromRGB(160, 82, 45)),
    TIER_2(50, "Markant", "Deutlich sichtbare Rang-Abzeichen",
            Color.fromRGB(70, 130, 180)),
    TIER_3(75, "Elite", "Hochrangige visuelle Ausstattung",
            Color.fromRGB(138, 43, 226)),
    LEGENDARY(100, "Legendär", "Legendäre Erscheinung (nur bei freigeschaltetem Status)",
            Color.fromRGB(255, 215, 0));

    private final int minScore;
    private final String displayName;
    private final String description;
    private final Color chestplateColor;

    ChiefVisualTier(int minScore, String displayName, String description, Color chestplateColor) {
        this.minScore = minScore;
        this.displayName = displayName;
        this.description = description;
        this.chestplateColor = chestplateColor;
    }

    public int minScore() {
        return minScore;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * Returns the abstract chestplate color for this tier.
     * Intended for use by a future client-side mod – no ItemDisplay is spawned.
     */
    public Color getChestplateColor() {
        return chestplateColor;
    }

    /**
     * Ermittelt den visuellen Rang aus dem kombinierten Reputations-Score.
     *
     * @param combinedScore    0–100 kombinierter Ruf (Dorf + Villager)
     * @param legendaryUnlocked ob der Legendär-Status freigeschaltet ist
     * @return der passende {@link ChiefVisualTier}
     */
    public static ChiefVisualTier fromReputation(int combinedScore, boolean legendaryUnlocked) {
        if (legendaryUnlocked && combinedScore >= 100) {
            return LEGENDARY;
        }
        if (combinedScore >= 75) {
            return TIER_3;
        }
        if (combinedScore >= 50) {
            return TIER_2;
        }
        if (combinedScore >= 25) {
            return TIER_1;
        }
        return TIER_0;
    }
}