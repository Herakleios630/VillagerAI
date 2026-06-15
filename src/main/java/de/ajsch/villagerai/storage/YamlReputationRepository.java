package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.SpeakerReputation;
import de.ajsch.villagerai.model.VillageReputation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlReputationRepository implements ReputationRepository {

    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlReputationRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "reputation.yml");
        this.configuration = loadConfiguration(plugin);
    }

    @Override
    public Optional<VillageReputation> findByPlayerAndVillage(UUID playerUuid, String villageId) {
        synchronized (lock) {
            return readReputation(playerUuid, villageId);
        }
    }

    @Override
    public Optional<SpeakerReputation> findByPlayerAndSpeaker(UUID playerUuid, String speakerId) {
        synchronized (lock) {
            String path = "players." + playerUuid + ".speakers." + sanitizeKey(speakerId);
            ConfigurationSection section = configuration.getConfigurationSection(path);
            if (section == null) {
                return Optional.empty();
            }

            String storedSpeakerId = section.getString("speaker-id", speakerId);
            String lastReason = section.getString("last-reason", "initial");
            return Optional.of(new SpeakerReputation(
                    playerUuid,
                    storedSpeakerId,
                    section.getInt("score", 0),
                    lastReason,
                    section.getLong("updated-at-epoch-millis", 0L)));
        }
    }

    @Override
    public Collection<VillageReputation> findByPlayerUuid(UUID playerUuid) {
        synchronized (lock) {
            ConfigurationSection playerSection = configuration.getConfigurationSection("players." + playerUuid);
            if (playerSection == null) {
                return List.of();
            }

            List<VillageReputation> reputations = new ArrayList<>();
            ConfigurationSection villagesSection = playerSection.getConfigurationSection("villages");
            if (villagesSection != null) {
                for (String villageId : villagesSection.getKeys(false)) {
                    readReputation(playerUuid, villageId).ifPresent(reputations::add);
                }
            }

            for (String villageId : playerSection.getKeys(false)) {
                if ("villages".equals(villageId) || "speakers".equals(villageId)) {
                    continue;
                }
                readReputation(playerUuid, villageId).ifPresent(reputations::add);
            }
            return List.copyOf(reputations);
        }
    }

    @Override
    public void saveReputation(VillageReputation reputation) {
        synchronized (lock) {
            String path = "players." + reputation.playerUuid() + ".villages." + sanitizeKey(reputation.villageId());
            configuration.set(path + ".village-id", reputation.villageId());
            configuration.set(path + ".score", reputation.score());
            configuration.set(path + ".last-reason", reputation.lastReason());
            configuration.set(path + ".updated-at-epoch-millis", reputation.updatedAtEpochMillis());
            saveConfiguration();
        }
    }

    @Override
    public void saveReputation(SpeakerReputation reputation) {
        synchronized (lock) {
            String path = "players." + reputation.playerUuid() + ".speakers." + sanitizeKey(reputation.speakerId());
            configuration.set(path + ".speaker-id", reputation.speakerId());
            configuration.set(path + ".score", reputation.score());
            configuration.set(path + ".last-reason", reputation.lastReason());
            configuration.set(path + ".updated-at-epoch-millis", reputation.updatedAtEpochMillis());
            saveConfiguration();
        }
    }

    private Optional<VillageReputation> readReputation(UUID playerUuid, String villageId) {
        String primaryPath = "players." + playerUuid + ".villages." + sanitizeKey(villageId);
        ConfigurationSection section = configuration.getConfigurationSection(primaryPath);
        if (section == null) {
            String legacyPath = "players." + playerUuid + "." + sanitizeKey(villageId);
            section = configuration.getConfigurationSection(legacyPath);
        }
        if (section == null) {
            return Optional.empty();
        }

        String storedVillageId = section.getString("village-id", villageId);
        String lastReason = section.getString("last-reason", "initial");
        return Optional.of(new VillageReputation(
                playerUuid,
                storedVillageId,
                section.getInt("score", 0),
                lastReason,
                section.getLong("updated-at-epoch-millis", 0L)));
    }

    private String sanitizeKey(String key) {
        return key.replace('.', '_');
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
                throw new IllegalStateException("Could not create reputation.yml", exception);
            }
        }
        return loaded;
    }

        @Override
    public void removeAllSpeakerReputation(String speakerId) {
        synchronized (lock) {
            boolean changed = false;
            ConfigurationSection playersSection = configuration.getConfigurationSection("players");
            if (playersSection == null) {
                return;
            }
            for (String playerUuid : playersSection.getKeys(false)) {
                String path = "players." + playerUuid + ".speakers." + sanitizeKey(speakerId);
                if (configuration.getConfigurationSection(path) != null) {
                    configuration.set(path, null);
                    changed = true;
                }
            }
            if (changed) {
                saveConfiguration();
            }
        }
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save reputation.yml", exception);
        }
    }
}