package de.ajsch.villagerai.config;

import de.ajsch.villagerai.VillageChiefPlugin;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.service.ConversationService;
import de.ajsch.villagerai.service.QuestDifficultyService;
import de.ajsch.villagerai.service.QuestRewardService;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PluginDataLoader {

    private final VillageChiefPlugin plugin;

    public PluginDataLoader(VillageChiefPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveBundledResources() {
        plugin.saveDefaultConfig();
        plugin.saveResource("quests.yml", false);
        plugin.saveResource("conversation-history.yml", false);
        plugin.saveResource("chief-profiles.yml", false);
        plugin.saveResource("quest-offers.yml", false);
        plugin.saveResource("quest-rewards.yml", false);
    }

    public int conversationHistoryMaxTurnsPerChief() {
        return plugin.getConfig().getInt("conversation.history.max-turns-per-player-chief", 20);
    }

    public int villagerTradeHistoryLimit() {
        return plugin.getConfig().getInt("villager-insights.trades.max-history-per-player-villager", 12);
    }

    public int villagerTradeSummaryRecentTrades() {
        return plugin.getConfig().getInt("villager-insights.trades.summary-recent-trades", 3);
    }

    public long questTalkCooldownSeconds() {
        return plugin.getConfig().getLong("quests.talk.cooldown-seconds", 300L);
    }

    public boolean questUiEnabled() {
        return plugin.getConfig().getBoolean("quests.ui.boss-bar-enabled", true);
    }

    public boolean questMarkersEnabled() {
        return plugin.getConfig().getBoolean("quests.markers.enabled", true);
    }

    public String questMarkerActiveSymbol() {
        return plugin.getConfig().getString("quests.markers.active-symbol", "§7?");
    }

    public String questMarkerReadySymbol() {
        return plugin.getConfig().getString("quests.markers.ready-symbol", "§e?");
    }

    public double questMarkerHeightAboveHead() {
        return plugin.getConfig().getDouble("quests.markers.height-above-head", 0.6);
    }

    public boolean questWorldMarkersEnabled() {
        return plugin.getConfig().getBoolean("quests.markers.world-markers.enabled", true);
    }

    public Material questWorldMarkerSecureMaterial() {
        String materialName = plugin.getConfig().getString("quests.markers.world-markers.secure-material", "GLOWSTONE");
        Material material = Material.matchMaterial(materialName == null ? "GLOWSTONE" : materialName);
        return material != null ? material : Material.GLOWSTONE;
    }

    public Material questWorldMarkerExploreMaterial() {
        String materialName = plugin.getConfig().getString("quests.markers.world-markers.explore-material", "LODESTONE");
        Material material = Material.matchMaterial(materialName == null ? "LODESTONE" : materialName);
        return material != null ? material : Material.LODESTONE;
    }

    public double questWorldMarkerHeightAboveGround() {
        return plugin.getConfig().getDouble("quests.markers.world-markers.height-above-ground", 3.0);
    }

    public org.bukkit.Particle questWorldMarkerParticle() {
        String particleName = plugin.getConfig().getString("quests.markers.world-markers.particle", "SOUL_FIRE_FLAME");
        if (particleName == null || particleName.isBlank()) {
            return org.bukkit.Particle.SOUL_FIRE_FLAME;
        }
        try {
            return org.bukkit.Particle.valueOf(particleName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unbekannter Particle-Typ '" + particleName + "' in quests.markers.world-markers.particle. Nutze SOUL_FIRE_FLAME.");
            return org.bukkit.Particle.SOUL_FIRE_FLAME;
        }
    }

    public boolean questWorldMarkerLabelDistance() {
        return plugin.getConfig().getBoolean("quests.markers.world-markers.label-distance", true);
    }

    public boolean questWorldMarkerShowBlock() {
        return plugin.getConfig().getBoolean("quests.markers.world-markers.show-block", true);
    }

    public QuestDifficultyService.Settings loadQuestDifficultySettings() {
        return new QuestDifficultyService.Settings(
                plugin.getConfig().getBoolean("quests.difficulty.enabled", true),
                plugin.getConfig().getIntegerList("quests.difficulty.unlock-scores"),
                plugin.getConfig().getBoolean("quests.difficulty.challenge-offers.enabled", true),
                plugin.getConfig().getDouble("quests.difficulty.challenge-offers.chance", 0.2D),
                plugin.getConfig().getLong("quests.difficulty.challenge-offers.cooldown-seconds", 900L),
                plugin.getConfig().getBoolean("quests.difficulty.legendary.enabled", true),
                plugin.getConfig().getInt("quests.difficulty.legendary.tier", 4),
                plugin.getConfig().getBoolean("quests.difficulty.legendary.require-nether-access", true),
                plugin.getConfig().getBoolean("quests.difficulty.legendary.require-end-access", true),
                plugin.getConfig().getBoolean("quests.difficulty.legendary.require-dragon-kill", true),
                plugin.getConfig().getLong("quests.difficulty.legendary.cooldown-seconds", 7200L));
    }

    public ConfinementSettings confinementSettings() {
        return new ConfinementSettings(
                plugin.getConfig().getLong("villager-insights.confinement.scan-interval-seconds", 60L),
                plugin.getConfig().getInt("villager-insights.confinement.stationary-radius-blocks", 4),
                plugin.getConfig().getLong("villager-insights.confinement.stationary-minutes", 20L),
                plugin.getConfig().getLong("villager-insights.confinement.cant-reach-walk-target-minutes", 5L),
                plugin.getConfig().getLong("villager-insights.confinement.stale-sleep-minutes", 30L),
                plugin.getConfig().getLong("villager-insights.confinement.stale-work-minutes", 30L));
    }

    public int questsSecureSubAreaSize() {
        return plugin.getConfig().getInt("quests.secure.village-light.sub-area-size", 20);
    }

    public int questsSecureMinDarkBlocks() {
        return plugin.getConfig().getInt("quests.secure.village-light.min-dark-blocks", 10);
    }

    public int questsSecurePerimeterMargin() {
        return plugin.getConfig().getInt("quests.secure.village-light.perimeter-margin", 16);
    }

    public int questsSecureMinPerimeterSize() {
        return plugin.getConfig().getInt("quests.secure.village-light.min-perimeter-size", 40);
    }

    public long questsSecureDarkListCacheTtlSeconds() {
        return plugin.getConfig().getLong("quests.secure.village-light.dark-list-cache-ttl-seconds", 30L);
    }

    public ConfigurationSection loadChiefProfilesSection() {
        return loadYamlFile("chief-profiles.yml");
    }

    public ConfigurationSection loadQuestOfferTemplatesSection() {
        return loadYamlFile("quest-offers.yml").getConfigurationSection("offer-templates");
    }

    public Map<QuestType, QuestRewardService.RewardDefinition> loadQuestRewardDefinitions() {
        YamlConfiguration rewardsConfig = loadYamlFile("quest-rewards.yml");
        ConfigurationSection rewardsSection = rewardsConfig.getConfigurationSection("rewards");
        if (rewardsSection == null) {
            plugin.getLogger().warning("quest-rewards.yml fehlt oder enthaelt keine 'rewards'-Sektion. Nutze Notfall-Belohnungen.");
            return emergencyRewardDefinitions();
        }

        EnumMap<QuestType, QuestRewardService.RewardDefinition> definitions = new EnumMap<>(QuestType.class);
        Map<QuestType, QuestRewardService.RewardDefinition> emergencyDefinitions = emergencyRewardDefinitions();
        for (QuestType questType : List.of(
                QuestType.TALK,
                QuestType.FETCH,
                QuestType.DELIVER,
                QuestType.REPAIR,
                QuestType.BUILD,
                QuestType.BREED,
                QuestType.BREW,
                QuestType.KILL,
                QuestType.VISIT,
                QuestType.SECURE,
                QuestType.EXPLORE,
                QuestType.RETINUE_GUARD,
                QuestType.RETINUE_GOLEM,
                QuestType.RETINUE_WALL,
                QuestType.RETINUE_BELL,
                QuestType.LEGENDARY_DRAGON,
                QuestType.LEGENDARY_BLAZE,
                QuestType.LEGENDARY_END,
                QuestType.LEGENDARY_NETHER)) {
            // Map Retinue- and Legendary-variants to shared sections
            String sectionName;
            if (questType.name().startsWith("RETINUE_")) {
                sectionName = "retinue";
            } else if (questType.name().startsWith("LEGENDARY_")) {
                sectionName = "legendary";
            } else {
                sectionName = questType.name().toLowerCase(Locale.ROOT);
            }
            ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(sectionName);
            if (rewardSection == null) {
                plugin.getLogger().warning("Belohnungsdefinition fuer '" + questType.name().toLowerCase(Locale.ROOT) + "' fehlt. Nutze Notfall-Default.");
                definitions.put(questType, emergencyDefinitions.get(questType));
                continue;
            }

            definitions.put(questType, new QuestRewardService.RewardDefinition(
                    Math.max(0, rewardSection.getInt("experience-points", 0)),
                    Math.max(0, rewardSection.getInt("emeralds", 0)),
                    loadRewardItems(questType, rewardSection)));
        }
        return definitions;
    }

    public QuestRewardService.RewardScalingSettings loadRewardScalingSettings() {
        return new QuestRewardService.RewardScalingSettings(
                plugin.getConfig().getBoolean("quests.rewards.reputation.enabled", true),
                plugin.getConfig().getInt("quests.rewards.reputation.village-quality-start-score", 20),
                plugin.getConfig().getInt("quests.rewards.reputation.village-quality-max-score", 100),
                plugin.getConfig().getInt("quests.rewards.reputation.village-quality-max-tier-bonus", 2),
                plugin.getConfig().getInt("quests.rewards.reputation.speaker-quantity-min-score", -100),
                plugin.getConfig().getInt("quests.rewards.reputation.speaker-quantity-max-score", 100),
                plugin.getConfig().getDouble("quests.rewards.reputation.speaker-quantity-min-multiplier", 0.5D),
            plugin.getConfig().getDouble("quests.rewards.reputation.speaker-quantity-max-multiplier", 2.0D),
            plugin.getConfig().getBoolean("quests.rewards.difficulty.enabled", true),
            plugin.getConfig().getDouble("quests.rewards.difficulty.per-tier-multiplier", 0.2D),
            plugin.getConfig().getBoolean("quests.rewards.legendary.enabled", true),
            plugin.getConfig().getInt("quests.rewards.legendary.min-tier", 4),
                plugin.getConfig().getDouble("quests.rewards.legendary.multiplier", 2.0D),
                plugin.getConfig().getBoolean("quests.rewards.rarity.enabled", true),
                plugin.getConfig().getIntegerList("quests.rewards.rarity.unlock-scores"),
                toDoubleList(plugin.getConfig().getList("quests.rewards.rarity.quantity-multipliers")),
                plugin.getConfig().getBoolean("quests.rewards.balancing.enabled", true),
                plugin.getConfig().getInt("quests.rewards.balancing.max-experience", 120),
                plugin.getConfig().getInt("quests.rewards.balancing.max-emeralds", 48),
                plugin.getConfig().getInt("quests.rewards.balancing.max-item-amount", 64),
                plugin.getConfig().getBoolean("quests.rewards.enchanted-books.enabled", true),
                plugin.getConfig().getDouble("quests.rewards.enchanted-books.base-chance", 0.05D),
                plugin.getConfig().getDouble("quests.rewards.enchanted-books.per-rarity-chance", 0.05D),
                plugin.getConfig().getInt("quests.rewards.enchanted-books.min-level", 1),
                plugin.getConfig().getInt("quests.rewards.enchanted-books.max-level", 3),
                plugin.getConfig().getInt("quests.rewards.enchanted-books.rarity-level-bonus", 1),
                parseEnchantments(plugin.getConfig().getStringList("quests.rewards.enchanted-books.enchantments")));
    }

    public ConversationService.RuntimeSettings loadConversationRuntimeSettings() {
        return new ConversationService.RuntimeSettings(
                Duration.ofSeconds(plugin.getConfig().getLong("conversation.timeout-seconds", 120L)),
                Math.max(1, plugin.getConfig().getInt("ai.max-concurrent-requests", 2)),
                plugin.getConfig().getString(
                        "conversation.busy.waiting-message",
                        "Der Villager denkt noch ueber deine letzte Frage nach."),
                plugin.getConfig().getString(
                        "conversation.busy.chief-busy-message",
                        "Der Villager ist gerade in ein anderes Gespraech vertieft. Versuch es gleich noch einmal."),
                plugin.getConfig().getString(
                        "conversation.busy.queue-full-message",
                        "Im Dorf ist gerade viel los. Versuch es in einem Moment noch einmal."),
                plugin.getConfig().getDouble("conversation.quest-offers.spontaneous-chance", 0.18D),
                plugin.getConfig().getLong("conversation.quest-offers.spontaneous-cooldown-seconds", 180L),
                plugin.getConfig().getLong("conversation.quest-offers.spontaneous-decline-cooldown-seconds", 600L),
                plugin.getConfig().getInt("conversation.quest-offers.spontaneous-min-combined-reputation", -5),
                plugin.getConfig().getInt("conversation.repeat-guard.recent-chief-replies-limit", 3),
                plugin.getConfig().getInt("conversation.history.context-turns-limit", 8),
                plugin.getConfig().getInt("conversation.quest-offers.friendly-intro-min-combined-reputation", 15),
                plugin.getConfig().getLong("conversation.quest-cooldown-dialog.minutes-threshold-seconds", 120L),
                plugin.getConfig().getDouble("conversation.repeat-guard.low-health-threshold", 0.7D));
    }

    public String getConversationDefaultVisibility() {
        return plugin.getConfig().getString("conversation.visibility.default-mode", "PUBLIC");
    }

    public int getConversationPublicRadiusBlocks() {
        return plugin.getConfig().getInt("conversation.visibility.public-radius-blocks", 50);
    }

    public String getConversationPublicPlayerPrefix() {
        return plugin.getConfig().getString("conversation.visibility.public-player-prefix", "sagt");
    }

    public String getConversationWhisperPlayerPrefix() {
        return plugin.getConfig().getString("conversation.visibility.whisper-player-prefix", "flüsterst");
    }

    public String getConversationPublicChiefPrefix() {
        return plugin.getConfig().getString("conversation.visibility.public-chief-prefix", "sagt");
    }

    public String getConversationWhisperChiefPrefix() {
        return plugin.getConfig().getString("conversation.visibility.whisper-chief-prefix", "flüstert");
    }

    public boolean getConversationParticlesEnabled() {
        return plugin.getConfig().getBoolean("conversation.visibility.particles.enabled", true);
    }

    public String getConversationPublicParticle() {
        return plugin.getConfig().getString("conversation.visibility.particles.public-particle", "VILLAGER_HAPPY");
    }

    public String getConversationWhisperParticle() {
        return plugin.getConfig().getString("conversation.visibility.particles.whisper-particle", "SOUL");
    }

    public int getConversationParticleCount() {
        return plugin.getConfig().getInt("conversation.visibility.particles.particle-count", 4);
    }

    public int getConversationParticleIntervalTicks() {
        return plugin.getConfig().getInt("conversation.visibility.particles.particle-interval-ticks", 8);
    }

    private List<QuestRewardService.RewardItem> loadRewardItems(QuestType questType, ConfigurationSection rewardSection) {
        List<QuestRewardService.RewardItem> rewardItems = new ArrayList<>();
        for (Map<?, ?> rawEntry : rewardSection.getMapList("bonus-items")) {
            if (rawEntry == null || rawEntry.isEmpty()) {
                plugin.getLogger().warning("Leerer Reward-Eintrag fuer '" + questType.name().toLowerCase(Locale.ROOT) + "' ignoriert.");
                continue;
            }

            String materialName = rawEntry.get("material") == null ? null : String.valueOf(rawEntry.get("material"));
            Material material = Material.matchMaterial(materialName == null ? "AIR" : materialName);
            int amount;
            try {
                Object rawAmount = rawEntry.get("amount");
                amount = Math.max(0, Integer.parseInt(String.valueOf(rawAmount == null ? 1 : rawAmount)));
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Reward-Eintrag fuer '" + questType.name().toLowerCase(Locale.ROOT) + "' ignoriert: amount ist keine ganze Zahl.");
                continue;
            }

            String rewardType = rawEntry.get("reward-type") == null
                    ? "ITEM"
                    : String.valueOf(rawEntry.get("reward-type")).trim().toUpperCase(Locale.ROOT);
            if ("RANDOM_ENCHANTED_BOOK".equals(rewardType)) {
                List<Enchantment> enchantments = parseEnchantments(rawEntry.get("enchantments") instanceof List<?> list ? list : List.of());
                if (amount <= 0) {
                    amount = 1;
                }
                rewardItems.add(QuestRewardService.RewardItem.randomEnchantedBook(amount, enchantments));
                continue;
            }

            if (material == null || material.isAir() || amount <= 0) {
                plugin.getLogger().warning("Reward-Eintrag fuer '" + questType.name().toLowerCase(Locale.ROOT) + "' ignoriert: gueltiges Material und amount > 0 sind Pflicht.");
                continue;
            }

            List<Material> qualityTiers = new ArrayList<>();
            Object rawQualityTiers = rawEntry.get("quality-tiers");
            if (rawQualityTiers instanceof List<?> qualityTierList) {
                for (Object rawQualityTier : qualityTierList) {
                    Material qualityTierMaterial = Material.matchMaterial(String.valueOf(rawQualityTier));
                    if (qualityTierMaterial == null || qualityTierMaterial.isAir()) {
                        plugin.getLogger().warning("Reward-Eintrag fuer '" + questType.name().toLowerCase(Locale.ROOT) + "' ignoriert einen ungueltigen quality-tier-Wert: " + rawQualityTier);
                        continue;
                    }
                    qualityTiers.add(qualityTierMaterial);
                }
            }

            rewardItems.add(QuestRewardService.RewardItem.item(material, amount, qualityTiers));
        }
        return rewardItems;
    }

    private Map<QuestType, QuestRewardService.RewardDefinition> emergencyRewardDefinitions() {
        EnumMap<QuestType, QuestRewardService.RewardDefinition> definitions = new EnumMap<>(QuestType.class);
        definitions.put(QuestType.TALK, new QuestRewardService.RewardDefinition(5, 1, List.of(QuestRewardService.RewardItem.item(Material.BREAD, 2, List.of(Material.COOKED_BEEF, Material.GOLDEN_CARROT)))));
        definitions.put(QuestType.FETCH, new QuestRewardService.RewardDefinition(8, 1, List.of(QuestRewardService.RewardItem.item(Material.APPLE, 3, List.of(Material.BREAD, Material.GOLDEN_CARROT)))));
        definitions.put(QuestType.DELIVER, new QuestRewardService.RewardDefinition(10, 2, List.of(QuestRewardService.RewardItem.item(Material.COOKED_BEEF, 2, List.of(Material.GOLDEN_CARROT, Material.RABBIT_STEW)))));
        definitions.put(QuestType.REPAIR, new QuestRewardService.RewardDefinition(10, 2, List.of(QuestRewardService.RewardItem.item(Material.IRON_INGOT, 3, List.of(Material.CHAIN, Material.LANTERN)))));
        definitions.put(QuestType.BUILD, new QuestRewardService.RewardDefinition(11, 2, List.of(QuestRewardService.RewardItem.item(Material.STONE_BRICKS, 8, List.of(Material.COBBLESTONE, Material.BRICKS)))));
        definitions.put(QuestType.BREED, new QuestRewardService.RewardDefinition(11, 2, List.of(QuestRewardService.RewardItem.item(Material.HAY_BLOCK, 3, List.of(Material.WHEAT, Material.CARROT)))));
        definitions.put(QuestType.BREW, new QuestRewardService.RewardDefinition(12, 2, List.of(QuestRewardService.RewardItem.item(Material.GLASS_BOTTLE, 4, List.of(Material.GLOWSTONE_DUST, Material.REDSTONE)))));
        definitions.put(QuestType.KILL, new QuestRewardService.RewardDefinition(12, 2, List.of(QuestRewardService.RewardItem.item(Material.ARROW, 8, List.of(Material.SPECTRAL_ARROW)))));
        definitions.put(QuestType.VISIT, new QuestRewardService.RewardDefinition(8, 1, List.of(QuestRewardService.RewardItem.item(Material.TORCH, 8, List.of(Material.LANTERN, Material.SOUL_LANTERN)))));
        definitions.put(QuestType.SECURE, new QuestRewardService.RewardDefinition(10, 2, List.of(QuestRewardService.RewardItem.item(Material.TORCH, 8, List.of(Material.LANTERN, Material.SOUL_LANTERN)))));
        definitions.put(QuestType.EXPLORE, new QuestRewardService.RewardDefinition(12, 2, List.of(QuestRewardService.RewardItem.item(Material.MAP, 1, List.of(Material.FILLED_MAP, Material.MAP)), QuestRewardService.RewardItem.item(Material.COMPASS, 1, List.of(Material.COMPASS, Material.RECOVERY_COMPASS)))));
        // RETINUE emergency fallback
        QuestRewardService.RewardDefinition retinueEmergency = new QuestRewardService.RewardDefinition(50, 10, List.of(
                QuestRewardService.RewardItem.item(Material.DIAMOND, 3, List.of(Material.EMERALD, Material.NETHERITE_SCRAP)),
                QuestRewardService.RewardItem.item(Material.ENCHANTED_GOLDEN_APPLE, 2, List.of(Material.TOTEM_OF_UNDYING)),
                QuestRewardService.RewardItem.randomEnchantedBook(2, List.of(Enchantment.MENDING, Enchantment.UNBREAKING, Enchantment.PROTECTION, Enchantment.SHARPNESS))));
        definitions.put(QuestType.RETINUE_GUARD, retinueEmergency);
        definitions.put(QuestType.RETINUE_GOLEM, retinueEmergency);
        definitions.put(QuestType.RETINUE_WALL, retinueEmergency);
        definitions.put(QuestType.RETINUE_BELL, retinueEmergency);
        // LEGENDARY emergency fallback
        QuestRewardService.RewardDefinition legendaryEmergency = new QuestRewardService.RewardDefinition(100, 16, List.of(
                QuestRewardService.RewardItem.item(Material.ELYTRA, 1, List.of()),
                QuestRewardService.RewardItem.item(Material.ENCHANTED_GOLDEN_APPLE, 2, List.of(Material.TOTEM_OF_UNDYING)),
                QuestRewardService.RewardItem.item(Material.NETHERITE_SWORD, 1, List.of()),
                QuestRewardService.RewardItem.item(Material.NETHERITE_INGOT, 3, List.of(Material.NETHERITE_BLOCK)),
                QuestRewardService.RewardItem.item(Material.BEACON, 1, List.of()),
                QuestRewardService.RewardItem.randomEnchantedBook(3, List.of(Enchantment.MENDING, Enchantment.UNBREAKING, Enchantment.PROTECTION, Enchantment.SHARPNESS, Enchantment.EFFICIENCY, Enchantment.FORTUNE, Enchantment.LOOTING, Enchantment.SWEEPING_EDGE))));
        definitions.put(QuestType.LEGENDARY_DRAGON, legendaryEmergency);
        definitions.put(QuestType.LEGENDARY_BLAZE, legendaryEmergency);
        definitions.put(QuestType.LEGENDARY_END, legendaryEmergency);
        definitions.put(QuestType.LEGENDARY_NETHER, legendaryEmergency);
        return definitions;
    }

    private List<Double> toDoubleList(List<?> values) {
        List<Double> doubles = new ArrayList<>();
        if (values == null) {
            return doubles;
        }
        for (Object value : values) {
            if (value instanceof Number number) {
                doubles.add(number.doubleValue());
                continue;
            }
            if (value == null) {
                continue;
            }
            try {
                doubles.add(Double.parseDouble(String.valueOf(value).trim()));
            } catch (NumberFormatException ignored) {
                // ignored
            }
        }
        return doubles;
    }

    private List<Enchantment> parseEnchantments(List<?> rawEnchantments) {
        List<Enchantment> enchantments = new ArrayList<>();
        if (rawEnchantments == null) {
            return enchantments;
        }
        for (Object rawEnchantment : rawEnchantments) {
            if (rawEnchantment == null) {
                continue;
            }
            String normalized = String.valueOf(rawEnchantment).trim().toLowerCase(Locale.ROOT)
                    .replace("minecraft:", "")
                    .replace(' ', '_');
            Enchantment matched = null;
            for (Enchantment enchantment : Enchantment.values()) {
                if (enchantment == null || enchantment.getKey() == null) {
                    continue;
                }
                String keyName = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
                if (keyName.equals(normalized)) {
                    matched = enchantment;
                    break;
                }
            }
            if (matched != null) {
                enchantments.add(matched);
            }
        }
        return enchantments;
    }

    private YamlConfiguration loadYamlFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    public boolean debugDarkBlockScanLogging() {
        return plugin.getConfig().getBoolean("debug.dark-block-scan-logging", false);
    }

    public boolean debugVillageLightParticleMarker() {
        return plugin.getConfig().getBoolean("debug.village-light-particle-marker", true);
    }

    public record ConfinementSettings(
            long scanIntervalSeconds,
            int stationaryRadiusBlocks,
            long stationaryMinutes,
            long cantReachWalkTargetMinutes,
            long staleSleepMinutes,
            long staleWorkMinutes) {
    }
}