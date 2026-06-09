package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestRewardResult;
import de.ajsch.villagerai.model.QuestType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public final class QuestRewardService {

    private volatile Map<QuestType, RewardDefinition> rewardsByType;
    private volatile RewardScalingSettings rewardScalingSettings;

    public QuestRewardService(Map<QuestType, RewardDefinition> rewardsByType, RewardScalingSettings rewardScalingSettings) {
        reloadRewards(rewardsByType, rewardScalingSettings);
    }

    public synchronized void reloadRewards(Map<QuestType, RewardDefinition> rewardsByType, RewardScalingSettings rewardScalingSettings) {
        EnumMap<QuestType, RewardDefinition> normalizedRewards = new EnumMap<>(QuestType.class);
        for (QuestType questType : QuestType.values()) {
            RewardDefinition rewardDefinition = rewardsByType == null ? null : rewardsByType.get(questType);
            normalizedRewards.put(questType, rewardDefinition == null ? RewardDefinition.none() : rewardDefinition);
        }
        this.rewardsByType = normalizedRewards;
        this.rewardScalingSettings = rewardScalingSettings == null ? RewardScalingSettings.defaults() : rewardScalingSettings;
    }

    public QuestRewardResult grantRewards(Player player, Quest quest, int villageReputationScore, int speakerReputationScore) {
        RewardDefinition rewardDefinition = rewardsByType.getOrDefault(quest.type(), RewardDefinition.none());
        RewardScalingSettings scalingSettings = rewardScalingSettings == null ? RewardScalingSettings.defaults() : rewardScalingSettings;
        double quantityMultiplier = scalingSettings.quantityMultiplierForScore(speakerReputationScore);
        double difficultyRewardMultiplier = scalingSettings.difficultyRewardMultiplierForTier(quest.difficultyTier());
        double legendaryRewardMultiplier = scalingSettings.legendaryRewardMultiplierForTier(quest.difficultyTier());
        int rarityTier = scalingSettings.rarityTierForScore(villageReputationScore);
        double rarityMultiplier = scalingSettings.rarityQuantityMultiplierForScore(villageReputationScore);
        double totalQuantityMultiplier = quantityMultiplier * difficultyRewardMultiplier * legendaryRewardMultiplier * rarityMultiplier;
        int qualityTierBonus = scalingSettings.qualityTierBonusForScore(villageReputationScore);
        int experiencePoints = scalingSettings.clampExperience(scaleAmount(rewardDefinition.experiencePoints(), totalQuantityMultiplier));
        int emeraldsGranted = scalingSettings.clampEmeralds(scaleAmount(rewardDefinition.emeralds(), totalQuantityMultiplier));

        if (experiencePoints > 0) {
            player.giveExp(experiencePoints);
        }
        if (emeraldsGranted > 0) {
            player.getInventory().addItem(new ItemStack(Material.EMERALD, emeraldsGranted));
        }

        List<String> grantedRewardItems = new ArrayList<>();
        for (RewardItem rewardItem : rewardDefinition.bonusItems()) {
            if (!rewardItem.isPresent()) {
                continue;
            }

            RewardItem resolvedRewardItem = rewardItem.applyQualityTier(qualityTierBonus);
            int grantedAmount = scalingSettings.clampItemAmount(scaleAmount(resolvedRewardItem.amount(), totalQuantityMultiplier));
            if (!resolvedRewardItem.isPresent() || grantedAmount <= 0) {
                continue;
            }

            if (resolvedRewardItem.randomEnchantedBookReward()) {
                GrantedBookReward grantedBookReward = grantRandomEnchantedBook(player, grantedAmount, rarityTier, scalingSettings, resolvedRewardItem.enchantmentPool());
                if (grantedBookReward.amount() > 0) {
                    grantedRewardItems.add(grantedBookReward.summary());
                }
                continue;
            }

            player.getInventory().addItem(new ItemStack(resolvedRewardItem.material(), grantedAmount));
            grantedRewardItems.add(grantedAmount + " " + resolvedRewardItem.displayName());
        }

        GrantedBookReward globalBookReward = tryGrantGlobalEnchantedBook(player, rarityTier, scalingSettings);
        if (globalBookReward.amount() > 0) {
            grantedRewardItems.add(globalBookReward.summary());
        }

        return new QuestRewardResult(
                experiencePoints,
                emeraldsGranted,
                String.join(", ", grantedRewardItems),
                totalQuantityMultiplier,
                villageReputationScore,
                speakerReputationScore);
    }

    private int scaleAmount(int baseAmount, double multiplier) {
        if (baseAmount <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(baseAmount * multiplier));
    }

    private GrantedBookReward tryGrantGlobalEnchantedBook(Player player, int rarityTier, RewardScalingSettings scalingSettings) {
        if (!scalingSettings.enchantedBooksEnabled() || scalingSettings.enchantedBookEnchantments().isEmpty()) {
            return GrantedBookReward.none();
        }

        double chance = scalingSettings.enchantedBookBaseChance()
                + (rarityTier * scalingSettings.enchantedBookPerRarityChance());
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return GrantedBookReward.none();
        }

        Enchantment enchantment = randomEnchantment(scalingSettings.enchantedBookEnchantments());
        if (enchantment == null) {
            return GrantedBookReward.none();
        }

        int minLevel = Math.max(1, scalingSettings.enchantedBookMinLevel());
        int maxLevel = Math.max(minLevel, scalingSettings.enchantedBookMaxLevel());
        int rolledLevel = ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
        int level = Math.max(1, Math.min(5, rolledLevel + (rarityTier * scalingSettings.enchantedBookRarityLevelBonus())));
        ItemStack book = createEnchantedBook(enchantment, level);
        player.getInventory().addItem(book);
        return new GrantedBookReward(1, "1 enchanted book (" + enchantmentName(enchantment) + " " + level + ")");
    }

    private GrantedBookReward grantRandomEnchantedBook(
            Player player,
            int amount,
            int rarityTier,
            RewardScalingSettings scalingSettings,
            List<Enchantment> rewardPool) {
        List<Enchantment> pool = rewardPool == null || rewardPool.isEmpty()
                ? scalingSettings.enchantedBookEnchantments()
                : rewardPool;
        if (pool.isEmpty() || amount <= 0) {
            return GrantedBookReward.none();
        }

        int minLevel = Math.max(1, scalingSettings.enchantedBookMinLevel());
        int maxLevel = Math.max(minLevel, scalingSettings.enchantedBookMaxLevel());
        int granted = 0;
        String lastSummary = null;
        for (int index = 0; index < amount; index++) {
            Enchantment enchantment = randomEnchantment(pool);
            if (enchantment == null) {
                continue;
            }
            int rolledLevel = ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
            int level = Math.max(1, Math.min(5, rolledLevel + (rarityTier * scalingSettings.enchantedBookRarityLevelBonus())));
            player.getInventory().addItem(createEnchantedBook(enchantment, level));
            granted++;
            lastSummary = enchantmentName(enchantment) + " " + level;
        }
        if (granted <= 0) {
            return GrantedBookReward.none();
        }
        String summary = granted + " enchanted book" + (granted > 1 ? "s" : "")
                + (lastSummary == null ? "" : " (z. B. " + lastSummary + ")");
        return new GrantedBookReward(granted, summary);
    }

    private ItemStack createEnchantedBook(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(enchantment, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    private Enchantment randomEnchantment(List<Enchantment> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private String enchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey().replace('_', ' ');
        return key.isEmpty() ? "enchantment" : Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    public record RewardDefinition(int experiencePoints, int emeralds, List<RewardItem> bonusItems) {

        public RewardDefinition {
            experiencePoints = Math.max(0, experiencePoints);
            emeralds = Math.max(0, emeralds);
            List<RewardItem> normalizedItems = new ArrayList<>();
            if (bonusItems != null) {
                for (RewardItem bonusItem : bonusItems) {
                    if (bonusItem != null && bonusItem.isPresent()) {
                        normalizedItems.add(bonusItem);
                    }
                }
            }
            bonusItems = List.copyOf(normalizedItems);
        }

        public static RewardDefinition none() {
            return new RewardDefinition(0, 0, List.of());
        }
    }

    public record RewardItem(
            Material material,
            int amount,
            List<Material> qualityTiers,
            boolean randomEnchantedBookReward,
            List<Enchantment> enchantmentPool) {

        public RewardItem {
            if (material == null) {
                material = Material.AIR;
            }
            amount = Math.max(0, amount);
            List<Material> normalizedQualityTiers = new ArrayList<>();
            if (qualityTiers != null) {
                for (Material qualityTier : qualityTiers) {
                    if (qualityTier != null && !qualityTier.isAir()) {
                        normalizedQualityTiers.add(qualityTier);
                    }
                }
            }
            qualityTiers = List.copyOf(normalizedQualityTiers);
            List<Enchantment> normalizedEnchantmentPool = new ArrayList<>();
            if (enchantmentPool != null) {
                for (Enchantment enchantment : enchantmentPool) {
                    if (enchantment != null) {
                        normalizedEnchantmentPool.add(enchantment);
                    }
                }
            }
            enchantmentPool = List.copyOf(normalizedEnchantmentPool);
            if (randomEnchantedBookReward) {
                material = Material.ENCHANTED_BOOK;
            }
        }

        public static RewardItem none() {
            return new RewardItem(Material.AIR, 0, List.of(), false, List.of());
        }

        public static RewardItem item(Material material, int amount, List<Material> qualityTiers) {
            return new RewardItem(material, amount, qualityTiers, false, List.of());
        }

        public static RewardItem randomEnchantedBook(int amount, List<Enchantment> enchantmentPool) {
            return new RewardItem(Material.ENCHANTED_BOOK, amount, List.of(), true, enchantmentPool);
        }

        public boolean isPresent() {
            return material != Material.AIR && amount > 0;
        }

        public String displayName() {
            return material.name().toLowerCase().replace('_', ' ');
        }

        public RewardItem applyQualityTier(int tierBonus) {
            if (randomEnchantedBookReward) {
                return this;
            }
            if (tierBonus <= 0 || qualityTiers.isEmpty()) {
                return this;
            }

            int qualityIndex = Math.min(tierBonus - 1, qualityTiers.size() - 1);
            return new RewardItem(qualityTiers.get(qualityIndex), amount, qualityTiers, false, enchantmentPool);
        }
    }

    public record RewardScalingSettings(
            boolean enabled,
            int villageQualityStartScore,
            int villageQualityMaxScore,
            int villageQualityMaxTierBonus,
            int speakerQuantityMinScore,
            int speakerQuantityMaxScore,
            double speakerQuantityMinMultiplier,
            double speakerQuantityMaxMultiplier,
            boolean difficultyEnabled,
            double difficultyPerTierMultiplier,
            boolean legendaryEnabled,
            int legendaryMinTier,
            double legendaryMultiplier,
            boolean rarityEnabled,
            List<Integer> rarityUnlockScores,
            List<Double> rarityQuantityMultipliers,
            boolean balancingEnabled,
            int maxExperience,
            int maxEmeralds,
            int maxItemAmount,
            boolean enchantedBooksEnabled,
            double enchantedBookBaseChance,
            double enchantedBookPerRarityChance,
            int enchantedBookMinLevel,
            int enchantedBookMaxLevel,
            int enchantedBookRarityLevelBonus,
            List<Enchantment> enchantedBookEnchantments) {

        public RewardScalingSettings {
            int normalizedVillageStart = Math.max(-100, Math.min(100, villageQualityStartScore));
            int normalizedVillageMax = Math.max(normalizedVillageStart, Math.min(100, villageQualityMaxScore));
            int normalizedSpeakerMin = Math.max(-100, Math.min(100, speakerQuantityMinScore));
            int normalizedSpeakerMax = Math.max(normalizedSpeakerMin, Math.min(100, speakerQuantityMaxScore));
            double normalizedMinMultiplier = Math.max(0.1D, speakerQuantityMinMultiplier);
            double normalizedMaxMultiplier = Math.max(normalizedMinMultiplier, speakerQuantityMaxMultiplier);

            villageQualityStartScore = normalizedVillageStart;
            villageQualityMaxScore = normalizedVillageMax;
            villageQualityMaxTierBonus = Math.max(0, villageQualityMaxTierBonus);
            speakerQuantityMinScore = normalizedSpeakerMin;
            speakerQuantityMaxScore = normalizedSpeakerMax;
            speakerQuantityMinMultiplier = normalizedMinMultiplier;
            speakerQuantityMaxMultiplier = normalizedMaxMultiplier;
            difficultyPerTierMultiplier = Math.max(0.0D, difficultyPerTierMultiplier);
            legendaryMinTier = Math.max(1, legendaryMinTier);
            legendaryMultiplier = Math.max(1.0D, legendaryMultiplier);
            List<Integer> normalizedRarityUnlockScores = new ArrayList<>();
            if (rarityUnlockScores != null) {
                for (Integer unlockScore : rarityUnlockScores) {
                    if (unlockScore != null) {
                        normalizedRarityUnlockScores.add(unlockScore);
                    }
                }
            }
            if (normalizedRarityUnlockScores.isEmpty()) {
                normalizedRarityUnlockScores = List.of(20, 50, 80, 100);
            } else {
                normalizedRarityUnlockScores = List.copyOf(normalizedRarityUnlockScores);
            }
            rarityUnlockScores = normalizedRarityUnlockScores;

            List<Double> normalizedRarityMultipliers = new ArrayList<>();
            if (rarityQuantityMultipliers != null) {
                for (Double rarityMultiplier : rarityQuantityMultipliers) {
                    if (rarityMultiplier != null) {
                        normalizedRarityMultipliers.add(Math.max(0.1D, rarityMultiplier));
                    }
                }
            }
            if (normalizedRarityMultipliers.isEmpty()) {
                normalizedRarityMultipliers = List.of(1.0D, 1.1D, 1.25D, 1.45D, 1.7D);
            } else {
                normalizedRarityMultipliers = List.copyOf(normalizedRarityMultipliers);
            }
            rarityQuantityMultipliers = normalizedRarityMultipliers;

            maxExperience = Math.max(1, maxExperience);
            maxEmeralds = Math.max(1, maxEmeralds);
            maxItemAmount = Math.max(1, maxItemAmount);
            enchantedBookBaseChance = Math.max(0.0D, Math.min(1.0D, enchantedBookBaseChance));
            enchantedBookPerRarityChance = Math.max(0.0D, Math.min(1.0D, enchantedBookPerRarityChance));
            enchantedBookMinLevel = Math.max(1, enchantedBookMinLevel);
            enchantedBookMaxLevel = Math.max(enchantedBookMinLevel, enchantedBookMaxLevel);
            enchantedBookRarityLevelBonus = Math.max(0, enchantedBookRarityLevelBonus);
            List<Enchantment> normalizedEnchantedBookEnchantments = new ArrayList<>();
            if (enchantedBookEnchantments != null) {
                for (Enchantment enchantment : enchantedBookEnchantments) {
                    if (enchantment != null) {
                        normalizedEnchantedBookEnchantments.add(enchantment);
                    }
                }
            }
            if (normalizedEnchantedBookEnchantments.isEmpty()) {
                normalizedEnchantedBookEnchantments = List.of(
                        Enchantment.UNBREAKING,
                        Enchantment.SHARPNESS,
                        Enchantment.PROTECTION,
                        Enchantment.EFFICIENCY,
                        Enchantment.FORTUNE,
                        Enchantment.MENDING);
            } else {
                normalizedEnchantedBookEnchantments = List.copyOf(normalizedEnchantedBookEnchantments);
            }
            enchantedBookEnchantments = normalizedEnchantedBookEnchantments;
        }

        public static RewardScalingSettings defaults() {
            return new RewardScalingSettings(
                    true,
                    20,
                    100,
                    2,
                    -100,
                    100,
                    0.5D,
                    2.0D,
                    true,
                    0.2D,
                    true,
                    4,
                    2.0D,
                    true,
                    List.of(20, 50, 80, 100),
                    List.of(1.0D, 1.1D, 1.25D, 1.45D, 1.7D),
                    true,
                    120,
                    48,
                    64,
                    true,
                    0.05D,
                    0.05D,
                    1,
                    3,
                    1,
                    List.of(
                            Enchantment.UNBREAKING,
                            Enchantment.SHARPNESS,
                            Enchantment.PROTECTION,
                            Enchantment.EFFICIENCY,
                            Enchantment.FORTUNE,
                            Enchantment.MENDING));
        }

        public double quantityMultiplierForScore(int speakerScore) {
            if (!enabled) {
                return 1.0D;
            }
            if (speakerQuantityMaxScore <= speakerQuantityMinScore) {
                return speakerQuantityMaxMultiplier;
            }

            double clampedScore = Math.max(speakerQuantityMinScore, Math.min(speakerQuantityMaxScore, speakerScore));
            double progress = (clampedScore - speakerQuantityMinScore)
                    / (double) (speakerQuantityMaxScore - speakerQuantityMinScore);
            return speakerQuantityMinMultiplier
                    + ((speakerQuantityMaxMultiplier - speakerQuantityMinMultiplier) * progress);
        }

        public int qualityTierBonusForScore(int villageScore) {
            if (!enabled || villageQualityMaxTierBonus <= 0 || villageScore <= villageQualityStartScore) {
                return 0;
            }
            if (villageQualityMaxScore <= villageQualityStartScore) {
                return villageQualityMaxTierBonus;
            }

            double clampedScore = Math.max(villageQualityStartScore, Math.min(villageQualityMaxScore, villageScore));
            double progress = (clampedScore - villageQualityStartScore)
                    / (double) (villageQualityMaxScore - villageQualityStartScore);
            return Math.max(0, Math.min(villageQualityMaxTierBonus,
                    (int) Math.round(progress * villageQualityMaxTierBonus)));
        }

        public double difficultyRewardMultiplierForTier(int difficultyTier) {
            if (!difficultyEnabled || difficultyTier <= 0) {
                return 1.0D;
            }
            return 1.0D + (difficultyPerTierMultiplier * difficultyTier);
        }

        public double legendaryRewardMultiplierForTier(int difficultyTier) {
            if (!legendaryEnabled || difficultyTier < legendaryMinTier) {
                return 1.0D;
            }
            return legendaryMultiplier;
        }

        public int rarityTierForScore(int villageScore) {
            if (!rarityEnabled) {
                return 0;
            }
            int tier = 0;
            for (int unlockScore : rarityUnlockScores) {
                if (villageScore >= unlockScore) {
                    tier++;
                }
            }
            return Math.max(0, tier);
        }

        public double rarityQuantityMultiplierForScore(int villageScore) {
            int tier = rarityTierForScore(villageScore);
            int index = Math.max(0, Math.min(tier, rarityQuantityMultipliers.size() - 1));
            return rarityQuantityMultipliers.get(index);
        }

        public int clampExperience(int value) {
            if (!balancingEnabled) {
                return value;
            }
            return Math.max(0, Math.min(maxExperience, value));
        }

        public int clampEmeralds(int value) {
            if (!balancingEnabled) {
                return value;
            }
            return Math.max(0, Math.min(maxEmeralds, value));
        }

        public int clampItemAmount(int value) {
            if (!balancingEnabled) {
                return value;
            }
            return Math.max(0, Math.min(maxItemAmount, value));
        }
    }

    private record GrantedBookReward(int amount, String summary) {
        private static GrantedBookReward none() {
            return new GrantedBookReward(0, "");
        }
    }
}