package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.VillagerTradeHistory;
import de.ajsch.villagerai.model.VillagerTradeRecord;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlVillagerTradeRepository implements VillagerTradeRepository {

    private final File file;
    private final int maxTradesPerHistory;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlVillagerTradeRepository(JavaPlugin plugin, int maxTradesPerHistory) {
        this.file = new File(plugin.getDataFolder(), "trade-history.yml");
        this.maxTradesPerHistory = Math.max(1, maxTradesPerHistory);
        this.configuration = loadConfiguration(plugin);
    }

    @Override
    public Optional<VillagerTradeHistory> findHistory(UUID playerUuid, UUID villagerUuid) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection(basePath(playerUuid, villagerUuid));
            if (section == null) {
                return Optional.empty();
            }

            List<VillagerTradeRecord> trades = new ArrayList<>();
            for (Map<?, ?> entry : section.getMapList("trades")) {
                trades.add(new VillagerTradeRecord(
                        stringValue(entry, "result-item", "UNKNOWN"),
                        asInt(entry.get("result-amount")),
                        stringValue(entry, "first-ingredient-item", "UNKNOWN"),
                        asInt(entry.get("first-ingredient-amount")),
                        stringOrNull(entry.get("second-ingredient-item")),
                        asInt(entry.get("second-ingredient-amount")),
                        Math.max(1, asInt(entry.get("trade-count"))),
                        asLong(entry.get("timestamp-epoch-millis"))));
            }

            return Optional.of(new VillagerTradeHistory(
                    UUID.fromString(section.getString("player-uuid", playerUuid.toString())),
                    UUID.fromString(section.getString("villager-uuid", villagerUuid.toString())),
                    List.copyOf(trades),
                    section.getLong("updated-at-epoch-millis", 0L)));
        }
    }

    @Override
    public void appendTrade(UUID playerUuid, UUID villagerUuid, VillagerTradeRecord tradeRecord) {
        synchronized (lock) {
            List<VillagerTradeRecord> trades = new ArrayList<>(findHistory(playerUuid, villagerUuid)
                    .map(VillagerTradeHistory::trades)
                    .orElseGet(List::of));
            trades.add(tradeRecord);
            if (trades.size() > maxTradesPerHistory) {
                trades = new ArrayList<>(trades.subList(trades.size() - maxTradesPerHistory, trades.size()));
            }

            String basePath = basePath(playerUuid, villagerUuid);
            configuration.set(basePath + ".player-uuid", playerUuid.toString());
            configuration.set(basePath + ".villager-uuid", villagerUuid.toString());
            configuration.set(basePath + ".updated-at-epoch-millis", tradeRecord.timestampEpochMillis());
            configuration.set(basePath + ".trades", serializeTrades(trades));
            saveConfiguration();
        }
    }

    private List<Map<String, Object>> serializeTrades(List<VillagerTradeRecord> trades) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (VillagerTradeRecord trade : trades) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("result-item", trade.resultItem());
            entry.put("result-amount", trade.resultAmount());
            entry.put("first-ingredient-item", trade.firstIngredientItem());
            entry.put("first-ingredient-amount", trade.firstIngredientAmount());
            entry.put("second-ingredient-item", trade.secondIngredientItem());
            entry.put("second-ingredient-amount", trade.secondIngredientAmount());
            entry.put("trade-count", trade.tradeCount());
            entry.put("timestamp-epoch-millis", trade.timestampEpochMillis());
            serialized.add(entry);
        }
        return serialized;
    }

    private String basePath(UUID playerUuid, UUID villagerUuid) {
        return "histories." + playerUuid + "." + villagerUuid;
    }

    private YamlConfiguration loadConfiguration(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (!file.exists()) {
            try {
                loaded.save(file);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not create trade-history.yml", exception);
            }
        }
        return loaded;
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save trade-history.yml", exception);
        }
    }

    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private String stringValue(Map<?, ?> entry, String key, String fallback) {
        Object value = entry.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}