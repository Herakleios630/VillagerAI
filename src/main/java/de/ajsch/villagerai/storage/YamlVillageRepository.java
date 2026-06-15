package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Anchor;
import de.ajsch.villagerai.model.VillageRecord;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlVillageRepository implements VillageRepository {

    private static final int DEFAULT_MAX_DISTANCE = 64;

    private final JavaPlugin plugin;
    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlVillageRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "villages.yml");
        this.configuration = loadConfiguration();
    }

    @Override
    public Optional<VillageRecord> findByAnchor(Location anchor, int maxDistance) {
        if (anchor == null || anchor.getWorld() == null) {
            return Optional.empty();
        }
        int effectiveMaxDistance = maxDistance > 0 ? maxDistance : DEFAULT_MAX_DISTANCE;
        String anchorWorld = anchor.getWorld().getName();
        int anchorX = anchor.getBlockX();
        int anchorY = anchor.getBlockY();
        int anchorZ = anchor.getBlockZ();

        synchronized (lock) {
            ConfigurationSection villagesSection = configuration.getConfigurationSection("villages");
            if (villagesSection == null) {
                return Optional.empty();
            }
            for (String firstPosKey : villagesSection.getKeys(false)) {
                Optional<VillageRecord> record = readVillage(firstPosKey);
                if (record.isEmpty()) {
                    continue;
                }
                for (Anchor knownAnchor : record.get().knownAnchors()) {
                    if (!anchorWorld.equals(knownAnchor.world())) {
                        continue;
                    }
                    double distance = Math.sqrt(
                            Math.pow(anchorX - knownAnchor.x(), 2)
                                    + Math.pow(anchorY - knownAnchor.y(), 2)
                                    + Math.pow(anchorZ - knownAnchor.z(), 2));
                    if (distance <= effectiveMaxDistance) {
                        return record;
                    }
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public Optional<VillageRecord> findByVillageId(String villageId) {
        if (villageId == null || villageId.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            ConfigurationSection villagesSection = configuration.getConfigurationSection("villages");
            if (villagesSection == null) {
                return Optional.empty();
            }
            for (String firstPosKey : villagesSection.getKeys(false)) {
                Optional<VillageRecord> record = readVillage(firstPosKey);
                if (record.isPresent() && record.get().villageId().equals(villageId)) {
                    return record;
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public void save(VillageRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        if (record.knownAnchors().isEmpty()) {
            throw new IllegalArgumentException("record must have at least one anchor");
        }
        synchronized (lock) {
            String firstPosKey = record.knownAnchors().get(0).posKey();
            String path = "villages." + firstPosKey;
            configuration.set(path + ".village-id", record.villageId());
            configuration.set(path + ".village-name", record.villageName());
            configuration.set(path + ".registered-at", record.registeredAt());
            List<String> anchorKeys = new ArrayList<>();
            for (Anchor anchor : record.knownAnchors()) {
                anchorKeys.add(anchor.posKey());
            }
            configuration.set(path + ".known-anchors", anchorKeys);
            saveConfiguration();
        }
    }

    @Override
    public Collection<VillageRecord> findAll() {
        synchronized (lock) {
            ConfigurationSection villagesSection = configuration.getConfigurationSection("villages");
            if (villagesSection == null) {
                return List.of();
            }
            List<VillageRecord> villages = new ArrayList<>();
            for (String firstPosKey : villagesSection.getKeys(false)) {
                readVillage(firstPosKey).ifPresent(villages::add);
            }
            return List.copyOf(villages);
        }
    }

    private Optional<VillageRecord> readVillage(String firstPosKey) {
        ConfigurationSection section = configuration.getConfigurationSection("villages." + firstPosKey);
        if (section == null) {
            return Optional.empty();
        }
        String villageId = section.getString("village-id");
        String villageName = section.getString("village-name");
        long registeredAt = section.getLong("registered-at");
        if (villageId == null || villageName == null || registeredAt <= 0) {
            return Optional.empty();
        }
        List<String> anchorKeyStrings = section.getStringList("known-anchors");
        List<Anchor> anchors = new ArrayList<>();
        for (String key : anchorKeyStrings) {
            Anchor anchor = parsePosKey(key);
            if (anchor != null) {
                anchors.add(anchor);
            }
        }
        if (anchors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new VillageRecord(villageId, villageName, registeredAt, anchors));
    }

    private static Anchor parsePosKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String[] parts = key.split(";", 5);
        if (parts.length != 5) {
            return null;
        }
        try {
            Anchor.AnchorType type = Anchor.AnchorType.valueOf(parts[0]);
            String world = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int z = Integer.parseInt(parts[4]);
            return new Anchor(type, world, x, y, z);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private YamlConfiguration loadConfiguration() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }
        if (!file.exists()) {
            plugin.saveResource("villages.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save villages.yml", exception);
        }
    }
}
