package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.SpeakerStatus;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlSpeakerRepository implements SpeakerRepository {

    private final JavaPlugin plugin;
    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlSpeakerRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "speakers.yml");
        this.configuration = loadConfiguration();
    }

    @Override
    public Optional<Speaker> findByEntityUuid(UUID entityUuid) {
        synchronized (lock) {
            return readSpeaker(entityUuid.toString());
        }
    }

    @Override
    public Optional<Speaker> findBySpeakerId(String speakerId) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("speakers");
            if (section == null) {
                return Optional.empty();
            }
            for (String entityUuidKey : section.getKeys(false)) {
                Optional<Speaker> speaker = readSpeaker(entityUuidKey);
                if (speaker.isPresent() && speaker.get().speakerId().equals(speakerId)) {
                    return speaker;
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public List<Speaker> findByVillageId(String villageId) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("speakers");
            if (section == null) {
                return List.of();
            }
            List<Speaker> result = new ArrayList<>();
            for (String entityUuidKey : section.getKeys(false)) {
                readSpeaker(entityUuidKey).ifPresent(speaker -> {
                    if (speaker.villageId().equals(villageId)) {
                        result.add(speaker);
                    }
                });
            }
            return List.copyOf(result);
        }
    }

    @Override
    public List<Speaker> findAllActiveChiefs() {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("speakers");
            if (section == null) {
                return List.of();
            }
            List<Speaker> result = new ArrayList<>();
            for (String entityUuidKey : section.getKeys(false)) {
                readSpeaker(entityUuidKey).ifPresent(speaker -> {
                    if (speaker.speakerStatus() == SpeakerStatus.AKTIV_CHIEF) {
                        result.add(speaker);
                    }
                });
            }
            return List.copyOf(result);
        }
    }

    @Override
    public void save(Speaker speaker) {
        synchronized (lock) {
            String path = "speakers." + speaker.entityUuid();
            configuration.set(path + ".entity-uuid", speaker.entityUuid().toString());
            configuration.set(path + ".speaker-id", speaker.speakerId());
            configuration.set(path + ".village-id", speaker.villageId());
            configuration.set(path + ".village-name", speaker.villageName());
            configuration.set(path + ".display-name", speaker.displayName());
            configuration.set(path + ".role", speaker.role());
            configuration.set(path + ".personality", speaker.personality());
            configuration.set(path + ".speech-tone", speaker.speechTone());
            configuration.set(path + ".behavior-hint", speaker.behaviorHint());
            configuration.set(path + ".greeting", speaker.greeting());
            configuration.set(path + ".profession", speaker.profession());
            configuration.set(path + ".world", speaker.world());
            configuration.set(path + ".x", speaker.x());
            configuration.set(path + ".y", speaker.y());
            configuration.set(path + ".z", speaker.z());
            configuration.set(path + ".speaker-status", speaker.speakerStatus().name());
            saveConfiguration();
        }
    }

    @Override
    public void deleteByEntityUuid(UUID entityUuid) {
        synchronized (lock) {
            configuration.set("speakers." + entityUuid, null);
            saveConfiguration();
        }
    }

    @Override
    public void deleteBySpeakerId(String speakerId) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("speakers");
            if (section == null) {
                return;
            }
            for (String entityUuidKey : section.getKeys(false)) {
                Optional<Speaker> speaker = readSpeaker(entityUuidKey);
                if (speaker.isPresent() && speaker.get().speakerId().equals(speakerId)) {
                    configuration.set("speakers." + entityUuidKey, null);
                    saveConfiguration();
                    return;
                }
            }
        }
    }

    private Optional<Speaker> readSpeaker(String entityUuid) {
        ConfigurationSection section = configuration.getConfigurationSection("speakers." + entityUuid);
        if (section == null) {
            return Optional.empty();
        }

        String speakerId = section.getString("speaker-id");
        String villageId = section.getString("village-id");
        if (speakerId == null || villageId == null) {
            return Optional.empty();
        }

        String statusRaw = section.getString("speaker-status", "NORMAL");
        SpeakerStatus speakerStatus;
        try {
            speakerStatus = SpeakerStatus.valueOf(statusRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            speakerStatus = SpeakerStatus.NORMALER_DORFBEWOHNER;
        }

        return Optional.of(new Speaker(
            UUID.fromString(entityUuid),
            speakerId,
            villageId,
            section.getString("village-name", "unser Dorf"),
            section.getString("display-name", "Dorfbewohner"),
            section.getString("role", "Dorfbewohner"),
            section.getString("personality", "normal"),
            section.getString("speech-tone", "freundlich"),
            section.getString("behavior-hint", "spricht wie ein ueblicher Dorfbewohner"),
            section.getString("greeting", "Hallo!"),
            section.getString("profession", "NONE"),
            section.getString("world", "unknown"),
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            speakerStatus
        ));
    }

    private YamlConfiguration loadConfiguration() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        if (!file.exists()) {
            plugin.saveResource("speakers.yml", false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save speakers.yml", exception);
        }
    }
}