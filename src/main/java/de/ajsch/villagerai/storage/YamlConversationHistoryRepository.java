package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.ConversationHistory;
import de.ajsch.villagerai.model.ConversationRole;
import de.ajsch.villagerai.model.ConversationTurn;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlConversationHistoryRepository implements ConversationHistoryRepository {

    private final JavaPlugin plugin;
    private final File file;
    private final int maxTurnsPerHistory;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlConversationHistoryRepository(JavaPlugin plugin, int maxTurnsPerHistory) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "conversation-history.yml");
        this.maxTurnsPerHistory = Math.max(1, maxTurnsPerHistory);
        this.configuration = loadConfiguration();
    }

    @Override
    public Optional<ConversationHistory> findHistory(UUID playerUuid, String chiefId) {
        synchronized (lock) {
            return readHistory(playerUuid.toString(), chiefId);
        }
    }

    @Override
    public Collection<ConversationHistory> findByPlayerUuid(UUID playerUuid) {
        synchronized (lock) {
            ConfigurationSection playerSection = configuration.getConfigurationSection("histories." + playerUuid);
            if (playerSection == null) {
                return List.of();
            }

            List<ConversationHistory> histories = new ArrayList<>();
            for (String chiefId : playerSection.getKeys(false)) {
                readHistory(playerUuid.toString(), chiefId).ifPresent(histories::add);
            }
            return List.copyOf(histories);
        }
    }

    @Override
    public void appendTurn(UUID playerUuid, Chief chief, ConversationTurn turn) {
        synchronized (lock) {
            ConversationHistory currentHistory = readHistory(playerUuid.toString(), chief.chiefId())
                    .orElseGet(() -> new ConversationHistory(
                            playerUuid,
                            chief.chiefId(),
                            chief.villageId(),
                            List.of(),
                            turn.timestampEpochMillis()));

            List<ConversationTurn> turns = new ArrayList<>(currentHistory.turns());
            turns.add(turn);
            if (turns.size() > maxTurnsPerHistory) {
                turns = new ArrayList<>(turns.subList(turns.size() - maxTurnsPerHistory, turns.size()));
            }

            String basePath = basePath(playerUuid.toString(), chief.chiefId());
            configuration.set(basePath + ".player-uuid", playerUuid.toString());
            configuration.set(basePath + ".chief-id", chief.chiefId());
            configuration.set(basePath + ".village-id", chief.villageId());
            configuration.set(basePath + ".updated-at-epoch-millis", turn.timestampEpochMillis());
            configuration.set(basePath + ".turns", serializeTurns(turns));
            saveConfiguration();
        }
    }

    @Override
    public void clearHistory(UUID playerUuid, String chiefId) {
        synchronized (lock) {
            configuration.set(basePath(playerUuid.toString(), chiefId), null);
            saveConfiguration();
        }
    }

    private Optional<ConversationHistory> readHistory(String playerUuid, String chiefId) {
        ConfigurationSection section = configuration.getConfigurationSection(basePath(playerUuid, chiefId));
        if (section == null) {
            return Optional.empty();
        }

        List<ConversationTurn> turns = new ArrayList<>();
        for (Map<?, ?> serializedTurn : section.getMapList("turns")) {
            Object roleValue = serializedTurn.get("role");
            Object messageValue = serializedTurn.get("message");
            Object timestampValue = serializedTurn.get("timestamp-epoch-millis");
            if (roleValue == null || messageValue == null || timestampValue == null) {
                continue;
            }

            try {
                ConversationRole role = ConversationRole.valueOf(String.valueOf(roleValue).toUpperCase(Locale.ROOT));
                long timestamp = Long.parseLong(String.valueOf(timestampValue));
                turns.add(new ConversationTurn(role, String.valueOf(messageValue), timestamp));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return Optional.of(new ConversationHistory(
                UUID.fromString(section.getString("player-uuid", playerUuid)),
                section.getString("chief-id", chiefId),
                section.getString("village-id", "unknown"),
                turns,
                section.getLong("updated-at-epoch-millis", 0L)));
    }

    private List<Map<String, Object>> serializeTurns(List<ConversationTurn> turns) {
        List<Map<String, Object>> serializedTurns = new ArrayList<>();
        for (ConversationTurn turn : turns) {
            Map<String, Object> serializedTurn = new LinkedHashMap<>();
            serializedTurn.put("role", turn.role().name().toLowerCase(Locale.ROOT));
            serializedTurn.put("message", turn.message());
            serializedTurn.put("timestamp-epoch-millis", turn.timestampEpochMillis());
            serializedTurns.add(serializedTurn);
        }
        return serializedTurns;
    }

    private String basePath(String playerUuid, String chiefId) {
        return "histories." + playerUuid + "." + chiefId;
    }

    private YamlConfiguration loadConfiguration() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        if (!file.exists()) {
            plugin.saveResource("conversation-history.yml", false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save conversation-history.yml", exception);
        }
    }
}