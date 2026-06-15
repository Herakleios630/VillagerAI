package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestStatus;
import de.ajsch.villagerai.model.QuestType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlQuestRepository implements QuestRepository {

    private final JavaPlugin plugin;
    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlQuestRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        this.configuration = loadConfiguration();
    }

    @Override
    public Optional<Quest> findByQuestId(String questId) {
        synchronized (lock) {
            return readQuest(questId);
        }
    }

    @Override
    public Collection<Quest> findByPlayerUuid(UUID playerUuid) {
        synchronized (lock) {
            List<Quest> quests = new ArrayList<>();
            for (Quest quest : findAllInternal()) {
                if (quest.playerUuid().equals(playerUuid)) {
                    quests.add(quest);
                }
            }
            return List.copyOf(quests);
        }
    }

    @Override
    public Collection<Quest> findAll() {
        synchronized (lock) {
            return List.copyOf(findAllInternal());
        }
    }

    @Override
    public void saveQuest(Quest quest) {
        synchronized (lock) {
            String path = "quests." + quest.questId();
            configuration.set(path + ".quest-id", quest.questId());
            configuration.set(path + ".player-uuid", quest.playerUuid().toString());
            configuration.set(path + ".chief-id", quest.speakerId());
            configuration.set(path + ".village-id", quest.villageId());
            configuration.set(path + ".difficulty-tier", quest.difficultyTier());
            configuration.set(path + ".type", quest.type().name().toLowerCase(Locale.ROOT));
            configuration.set(path + ".title", quest.title());
            configuration.set(path + ".description", quest.description());
            configuration.set(path + ".target-key", quest.targetKey());
            configuration.set(path + ".goal", quest.goal());
            configuration.set(path + ".progress", quest.progress());
            configuration.set(path + ".status", quest.status().name().toLowerCase(Locale.ROOT));
            configuration.set(path + ".created-at-epoch-millis", quest.createdAtEpochMillis());
            configuration.set(path + ".updated-at-epoch-millis", quest.updatedAtEpochMillis());
            saveConfiguration();
        }
    }

    @Override
    public void removeQuest(String questId) {
        synchronized (lock) {
            configuration.set("quests." + questId, null);
            saveConfiguration();
        }
    }

    private List<Quest> findAllInternal() {
        ConfigurationSection questsSection = configuration.getConfigurationSection("quests");
        if (questsSection == null) {
            return List.of();
        }

        List<Quest> quests = new ArrayList<>();
        for (String questId : questsSection.getKeys(false)) {
            readQuest(questId).ifPresent(quests::add);
        }
        return quests;
    }

    private Optional<Quest> readQuest(String questId) {
        ConfigurationSection section = configuration.getConfigurationSection("quests." + questId);
        if (section == null) {
            return Optional.empty();
        }

        String playerUuid = section.getString("player-uuid");
        String speakerId = section.getString("chief-id");
        String villageId = section.getString("village-id");
        String typeName = section.getString("type");
        String title = section.getString("title");
        String description = section.getString("description");
        String statusName = section.getString("status");
        if (playerUuid == null || speakerId == null || villageId == null || typeName == null || title == null || description == null || statusName == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new Quest(
                    section.getString("quest-id", questId),
                    UUID.fromString(playerUuid),
                    speakerId,
                    villageId,
                    Math.max(0, section.getInt("difficulty-tier", 0)),
                    QuestType.valueOf(typeName.toUpperCase(Locale.ROOT)),
                    title,
                    description,
                    section.getString("target-key", ""),
                    section.getInt("goal", 1),
                    section.getInt("progress", 0),
                    QuestStatus.valueOf(statusName.toUpperCase(Locale.ROOT)),
                    section.getLong("created-at-epoch-millis", 0L),
                    section.getLong("updated-at-epoch-millis", 0L)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private YamlConfiguration loadConfiguration() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save quests.yml", exception);
        }
    }
}