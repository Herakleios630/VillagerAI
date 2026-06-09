package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.QuestDifficultyPreference;
import de.ajsch.villagerai.storage.QuestDifficultyPreferenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class QuestDifficultyService {

    private final QuestDifficultyPreferenceRepository repository;
    private volatile Settings settings;

    public QuestDifficultyService(QuestDifficultyPreferenceRepository repository, Settings settings) {
        this.repository = repository;
        this.settings = settings == null ? Settings.defaults() : settings;
    }

    public void reloadSettings(Settings settings) {
        this.settings = settings == null ? Settings.defaults() : settings;
    }

    public QuestDifficultyPreference getPreference(UUID playerUuid, String chiefId) {
        return repository.findByPlayerAndChief(playerUuid, chiefId)
                .map(this::normalizePreference)
                .orElseGet(() -> new QuestDifficultyPreference(playerUuid, chiefId, 0, 0, 0L, 0L));
    }

    public QuestDifficultyPreference setPreferredDifficultyTier(UUID playerUuid, String chiefId, int tier) {
        long now = System.currentTimeMillis();
        QuestDifficultyPreference updated = getPreference(playerUuid, chiefId)
                .withPreferredDifficultyTier(clampTier(tier), now);
        repository.savePreference(updated);
        return updated;
    }

    public QuestDifficultyPreference recordSuggestedTier(UUID playerUuid, String chiefId, int tier) {
        long now = System.currentTimeMillis();
        QuestDifficultyPreference updated = getPreference(playerUuid, chiefId)
                .withSuggestion(clampTier(tier), now, now);
        repository.savePreference(updated);
        return updated;
    }

    public int resolveUnlockedTier(int villageReputationScore) {
        if (!settings.enabled()) {
            return 0;
        }

        int unlockedTier = 0;
        for (int unlockScore : settings.unlockScores()) {
            if (villageReputationScore >= unlockScore) {
                unlockedTier++;
            }
        }
        return clampTier(unlockedTier);
    }

    public int clampTier(int tier) {
        return Math.max(0, Math.min(tier, settings.maxTier()));
    }

    public String describeTier(int tier) {
        int normalizedTier = clampTier(tier);
        if (normalizedTier <= 0) {
            return "normal";
        }
        return "stufe-" + normalizedTier;
    }

    public int maxTier() {
        return settings.maxTier();
    }

    public boolean challengeOffersEnabled() {
        return settings.challengeOffersEnabled();
    }

    public double challengeOfferChance() {
        return settings.challengeOfferChance();
    }

    public long challengeOfferCooldownMillis() {
        return Math.max(0L, settings.challengeOfferCooldownSeconds()) * 1000L;
    }

    public boolean legendaryEnabled() {
        return settings.legendaryEnabled();
    }

    public int legendaryTier() {
        int configuredTier = settings.legendaryTier();
        if (configuredTier <= 0) {
            return settings.maxTier();
        }
        return clampTier(configuredTier);
    }

    public boolean requiresNetherAccessForLegendary() {
        return settings.requireNetherAccess();
    }

    public boolean requiresEndAccessForLegendary() {
        return settings.requireEndAccess();
    }

    public boolean requiresDragonKillForLegendary() {
        return settings.requireDragonKill();
    }

    public long legendaryCooldownMillis() {
        return Math.max(0L, settings.legendaryCooldownSeconds()) * 1000L;
    }

    private QuestDifficultyPreference normalizePreference(QuestDifficultyPreference preference) {
        int preferredTier = clampTier(preference.preferredDifficultyTier());
        int lastSuggestedTier = clampTier(preference.lastSuggestedTier());
        if (preferredTier == preference.preferredDifficultyTier() && lastSuggestedTier == preference.lastSuggestedTier()) {
            return preference;
        }
        return new QuestDifficultyPreference(
                preference.playerUuid(),
                preference.chiefId(),
                preferredTier,
                lastSuggestedTier,
                preference.lastSuggestedAtEpochMillis(),
                preference.updatedAtEpochMillis());
    }

    public record Settings(
            boolean enabled,
            List<Integer> unlockScores,
            boolean challengeOffersEnabled,
            double challengeOfferChance,
            long challengeOfferCooldownSeconds,
            boolean legendaryEnabled,
            int legendaryTier,
            boolean requireNetherAccess,
            boolean requireEndAccess,
            boolean requireDragonKill,
            long legendaryCooldownSeconds) {

        public Settings {
            List<Integer> normalizedScores = new ArrayList<>();
            if (unlockScores != null) {
                for (Integer unlockScore : unlockScores) {
                    if (unlockScore != null) {
                        normalizedScores.add(unlockScore);
                    }
                }
            }
            if (normalizedScores.isEmpty()) {
                normalizedScores = List.of(25, 50, 75, 100);
            } else {
                normalizedScores = List.copyOf(normalizedScores);
            }
            unlockScores = normalizedScores;
            challengeOfferChance = Math.max(0.0D, Math.min(1.0D, challengeOfferChance));
            challengeOfferCooldownSeconds = Math.max(0L, challengeOfferCooldownSeconds);
            legendaryTier = Math.max(0, legendaryTier);
            legendaryCooldownSeconds = Math.max(0L, legendaryCooldownSeconds);
        }

        public static Settings defaults() {
            return new Settings(
                    true,
                    List.of(25, 50, 75, 100),
                    true,
                    0.2D,
                    900L,
                    true,
                    4,
                    true,
                    true,
                    true,
                    7200L);
        }

        public int maxTier() {
            return unlockScores.size();
        }
    }
}