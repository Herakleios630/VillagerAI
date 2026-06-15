package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.ChiefAttributes;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlChiefRepository implements ChiefRepository {

    private final JavaPlugin plugin;
    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlChiefRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chief-attributes.yml");
        this.configuration = loadConfiguration();
    }

    @Override
    public Optional<ChiefAttributes> findByEntityUuid(UUID entityUuid) {
        synchronized (lock) {
            return readAttributes(entityUuid.toString());
        }
    }

    @Override
    public Optional<ChiefAttributes> findActiveByVillageId(String villageId) {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("chief-attributes");
            if (section == null) {
                return Optional.empty();
            }
            for (String entityUuid : section.getKeys(false)) {
                Optional<ChiefAttributes> attrs = readAttributes(entityUuid);
                if (attrs.isPresent() && attrs.get().villageId().equals(villageId) && attrs.get().isActive()) {
                    return attrs;
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public List<ChiefAttributes> findAll() {
        synchronized (lock) {
            ConfigurationSection section = configuration.getConfigurationSection("chief-attributes");
            if (section == null) {
                return List.of();
            }

            List<ChiefAttributes> attributes = new ArrayList<>();
            for (String entityUuid : section.getKeys(false)) {
                readAttributes(entityUuid).ifPresent(attributes::add);
            }
            return List.copyOf(attributes);
        }
    }

    @Override
    public void save(ChiefAttributes attributes) {
        synchronized (lock) {
            String path = "chief-attributes." + attributes.entityUuid();
            configuration.set(path + ".entity-uuid", attributes.entityUuid().toString());
            configuration.set(path + ".chief-id", attributes.speakerId());
            configuration.set(path + ".village-id", attributes.villageId());
            configuration.set(path + ".crowned-at", attributes.crownedAt());
            configuration.set(path + ".mourned-at", attributes.mournedAt());
            configuration.set(path + ".is-active", attributes.isActive());
            configuration.set(path + ".visual-tier", attributes.visualTier());
            configuration.set(path + ".biome-style", attributes.biomeStyle());
            configuration.set(path + ".banner-pattern", attributes.bannerPattern());
            configuration.set(path + ".legendary-unlocked", attributes.legendaryUnlocked());
            configuration.set(path + ".legendary-last-activated", attributes.legendaryLastActivated());
            saveConfiguration();
        }
    }

    @Override
    public void deleteByEntityUuid(UUID entityUuid) {
        synchronized (lock) {
            configuration.set("chief-attributes." + entityUuid, null);
            saveConfiguration();
        }
    }

    private Optional<ChiefAttributes> readAttributes(String entityUuid) {
        ConfigurationSection section = configuration.getConfigurationSection("chief-attributes." + entityUuid);
        if (section == null) {
            return Optional.empty();
        }

        String speakerId = section.getString("chief-id");
        if (speakerId == null) {
            return Optional.empty();
        }

        return Optional.of(new ChiefAttributes(
                UUID.fromString(entityUuid),
                speakerId,
                section.getString("village-id", "unknown"),
                section.getLong("crowned-at", 0L),
                section.getLong("mourned-at", 0L),
                section.getBoolean("is-active", true),
                section.getString("visual-tier", null),
                section.getString("biome-style", null),
                section.getString("banner-pattern", "default"),
                section.getBoolean("legendary-unlocked", false),
                section.getLong("legendary-last-activated", 0L)));
    }

    private YamlConfiguration loadConfiguration() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        if (!file.exists()) {
            plugin.saveResource("chief-attributes.yml", false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save chief-attributes.yml", exception);
        }
    }
}