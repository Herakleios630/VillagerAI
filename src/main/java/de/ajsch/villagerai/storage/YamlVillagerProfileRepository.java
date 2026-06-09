package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.VillagerProfile;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlVillagerProfileRepository implements VillagerProfileRepository {

    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlVillagerProfileRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "villager-profiles.yml");
        this.configuration = loadConfiguration(plugin);
    }

    @Override
    public Optional<VillagerProfile> findByEntityUuid(UUID entityUuid) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("profiles." + entityUuid);
            if (section == null) {
                return Optional.empty();
            }

            String speakerId = section.getString("speaker-id");
            String villageId = section.getString("village-id");
            String villageName = section.getString("village-name");
            String displayName = section.getString("display-name");
            String role = section.getString("role");
            String personality = section.getString("personality");
            String greeting = section.getString("greeting");
            String profession = section.getString("profession");
            if (speakerId == null || villageId == null || villageName == null || displayName == null
                    || role == null || personality == null || greeting == null || profession == null) {
                return Optional.empty();
            }

            return Optional.of(new VillagerProfile(
                    entityUuid,
                    speakerId,
                    villageId,
                    villageName,
                    displayName,
                    role,
                    personality,
                    greeting,
                    profession,
                    section.getLong("updated-at-epoch-millis", 0L)));
        }
    }

    @Override
    public void saveProfile(VillagerProfile profile) {
        synchronized (lock) {
            String path = "profiles." + profile.entityUuid();
            configuration.set(path + ".speaker-id", profile.speakerId());
            configuration.set(path + ".village-id", profile.villageId());
            configuration.set(path + ".village-name", profile.villageName());
            configuration.set(path + ".display-name", profile.displayName());
            configuration.set(path + ".role", profile.role());
            configuration.set(path + ".personality", profile.personality());
            configuration.set(path + ".greeting", profile.greeting());
            configuration.set(path + ".profession", profile.profession());
            configuration.set(path + ".updated-at-epoch-millis", profile.updatedAtEpochMillis());
            saveConfiguration();
        }
    }

    @Override
    public void removeProfile(UUID entityUuid) {
        synchronized (lock) {
            configuration.set("profiles." + entityUuid, null);
            saveConfiguration();
        }
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
                throw new IllegalStateException("Could not create villager-profiles.yml", exception);
            }
        }
        return loaded;
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save villager-profiles.yml", exception);
        }
    }
}