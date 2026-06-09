package de.ajsch.villagerai.storage;

import de.ajsch.villagerai.model.Chief;
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

public final class YamlChiefRepository implements ChiefRepository {

    private final JavaPlugin plugin;
    private final File file;
    private final Object lock = new Object();
    private YamlConfiguration configuration;

    public YamlChiefRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chiefs.yml");
        this.configuration = loadConfiguration();
    }

    @Override
    public Optional<Chief> findByEntityUuid(UUID entityUuid) {
        synchronized (lock) {
            return readChief(entityUuid.toString());
        }
    }

    @Override
    public Collection<Chief> findAll() {
        synchronized (lock) {
            ConfigurationSection chiefsSection = configuration.getConfigurationSection("chiefs");
            if (chiefsSection == null) {
                return List.of();
            }

            List<Chief> chiefs = new ArrayList<>();
            for (String entityUuid : chiefsSection.getKeys(false)) {
                readChief(entityUuid).ifPresent(chiefs::add);
            }
            return List.copyOf(chiefs);
        }
    }

    @Override
    public void saveChief(Chief chief) {
        synchronized (lock) {
            String path = "chiefs." + chief.entityUuid();
            configuration.set(path + ".entity-uuid", chief.entityUuid().toString());
            configuration.set(path + ".chief-id", chief.chiefId());
            configuration.set(path + ".village-id", chief.villageId());
            configuration.set(path + ".village-name", chief.villageName());
            configuration.set(path + ".village-description", chief.villageDescription());
            configuration.set(path + ".village-attributes", chief.villageAttributes());
            configuration.set(path + ".village-biome", chief.villageBiome());
            configuration.set(path + ".village-population-estimate", chief.villagePopulationEstimate());
            configuration.set(path + ".village-event-summary", chief.villageEventSummary());
            configuration.set(path + ".display-name", chief.displayName());
            configuration.set(path + ".role", chief.role());
            configuration.set(path + ".personality", chief.personality());
            configuration.set(path + ".speech-tone", chief.speechTone());
            configuration.set(path + ".behavior-hint", chief.behaviorHint());
            configuration.set(path + ".greeting", chief.greeting());
            configuration.set(path + ".world", chief.world());
            configuration.set(path + ".x", chief.x());
            configuration.set(path + ".y", chief.y());
            configuration.set(path + ".z", chief.z());
            saveConfiguration();
        }
    }

    @Override
    public void removeChief(UUID entityUuid) {
        synchronized (lock) {
            configuration.set("chiefs." + entityUuid, null);
            saveConfiguration();
        }
    }

    private Optional<Chief> readChief(String entityUuid) {
        ConfigurationSection section = configuration.getConfigurationSection("chiefs." + entityUuid);
        if (section == null) {
            return Optional.empty();
        }

        String chiefId = section.getString("chief-id");
        String villageId = section.getString("village-id");
        String villageName = section.getString("village-name", "unser Dorf");
        String villageDescription = section.getString("village-description", villageName + " ist ein Dorf in seiner gewohnten Umgebung.");
        String villageAttributes = section.getString("village-attributes", "wenig erkennbare Besonderheiten");
        String villageBiome = section.getString("village-biome", "unknown");
        int villagePopulationEstimate = Math.max(1, section.getInt("village-population-estimate", 1));
        String villageEventSummary = section.getString("village-event-summary", "Im Dorf gibt es aktuell kein herausstechendes Ereignis.");
        String displayName = section.getString("display-name", "Haeuptling");
        String role = section.getString("role", "Dorfhaeuptling");
        String personality = section.getString("personality", "bedacht");
        String speechTone = section.getString("speech-tone", "ruhig und wuerdevoll");
        String behaviorHint = section.getString("behavior-hint", "spricht bedaechtig und mit natuerlicher Autoritaet");
        String greeting = section.getString("greeting", "Willkommen in unserem Dorf.");
        String world = section.getString("world", "unknown");
        if (chiefId == null || villageId == null) {
            return Optional.empty();
        }

        return Optional.of(new Chief(
                UUID.fromString(entityUuid),
                chiefId,
                villageId,
            villageName,
            villageDescription,
            villageAttributes,
            villageBiome,
            villagePopulationEstimate,
            villageEventSummary,
            displayName,
            role,
            personality,
            speechTone,
            behaviorHint,
            greeting,
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")));
    }

    private YamlConfiguration loadConfiguration() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        if (!file.exists()) {
            plugin.saveResource("chiefs.yml", false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveConfiguration() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save chiefs.yml", exception);
        }
    }
}