package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.QuestDifficultyPreference;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlQuestDifficultyPreferenceRepository implements QuestDifficultyPreferenceRepository {

    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlQuestDifficultyPreferenceRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "quest-difficulty-preferences.yml");
        this.configuration = loadConfiguration(plugin);
    }

    @Override
    public Optional<QuestDifficultyPreference> findByPlayerAndChief(UUID playerUuid, String speakerId) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection(path(playerUuid, speakerId));
            if (section == null) {
                return Optional.empty();
            }

            return Optional.of(new QuestDifficultyPreference(
                    playerUuid,
                    speakerId,
                    Math.max(0, section.getInt("preferred-difficulty-tier", 0)),
                    Math.max(0, section.getInt("last-suggested-tier", 0)),
                    section.getLong("last-suggested-at-epoch-millis", 0L),
                    section.getLong("updated-at-epoch-millis", 0L)));
        }
    }

    @Override
    public void savePreference(QuestDifficultyPreference preference) {
        synchronized (lock) {
            String path = path(preference.playerUuid(), preference.speakerId());
            configuration.set(path + ".preferred-difficulty-tier", preference.preferredDifficultyTier());
            configuration.set(path + ".last-suggested-tier", preference.lastSuggestedTier());
            configuration.set(path + ".last-suggested-at-epoch-millis", preference.lastSuggestedAtEpochMillis());
            configuration.set(path + ".updated-at-epoch-millis", preference.updatedAtEpochMillis());
            saveConfiguration();
        }
    }

    private String path(UUID playerUuid, String speakerId) {
        return "preferences." + playerUuid + "." + speakerId;
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
                throw new IllegalStateException("Could not create quest-difficulty-preferences.yml", exception);
            }
        }
        return loaded;
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save quest-difficulty-preferences.yml", exception);
        }
    }
}