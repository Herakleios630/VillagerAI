package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.SpeakerStatus;
import de.ajsch.villagerai.model.VillageIdentity;
import de.ajsch.villagerai.storage.SpeakerRepository;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Extrahiert ALLE Speaker-Methoden aus dem alten ChiefService.
 * Verwaltet normale Dorfbewohner (keine Chiefs).
 * Arbeitet mit SpeakerRepository und VillageIdentityService.
 * Namenspools werden in eigener name-pools.yml verwaltet.
 */
public final class SpeakerService {

    private final JavaPlugin plugin;
    private final SpeakerRepository speakerRepository;
    private final VillageIdentityService villageIdentityService;
    private final Logger logger;
    private volatile Map<String, List<String>> namePools;
    private final File namePoolsFile;

    // ─── fixed defaults ───

    private record ProfessionProfile(String role, String basePersonality, String greetingTemplate) {}

    private record CharacterArchetype(
            String personalitySuffix, String tone, String behaviorHint, String greetingTemplate) {
        String buildPersonality(String basePersonality) {
            return basePersonality + ", " + personalitySuffix;
        }
        String buildGreeting(String professionGreetingTemplate, String role, String villageName) {
            String template = (professionGreetingTemplate == null || professionGreetingTemplate.isBlank())
                    ? greetingTemplate
                    : professionGreetingTemplate;
            return template.replace("{role}", role).replace("{village}", villageName);
        }
    }

    private static final Map<String, ProfessionProfile> PROFESSION_PROFILES = buildProfessionProfiles();
    private static final List<CharacterArchetype> ARCHETYPES = buildArchetypes();

    // ─── constructor ───

    public SpeakerService(
            JavaPlugin plugin,
            SpeakerRepository speakerRepository,
            VillageIdentityService villageIdentityService,
            Logger logger) {
        this.plugin = plugin;
        this.speakerRepository = speakerRepository;
        this.villageIdentityService = villageIdentityService;
        this.logger = logger;
        this.namePoolsFile = new File(plugin.getDataFolder(), "name-pools.yml");
        this.namePools = loadNamePools();
    }

    // ─── public API ───

    public Optional<Speaker> getSpeaker(Villager villager) {
        Optional<Speaker> stored = speakerRepository.findByEntityUuid(villager.getUniqueId());
        if (stored.isPresent()) {
            return stored;
        }
        return Optional.of(createOrRefreshProfile(villager));
    }

    public Speaker createOrRefreshProfile(Villager villager) {
        String professionKey = normalizeProfessionKey(villager.getProfession());
        VillageIdentity identity = villageIdentityService.resolve(villager);
        String speakerId = createVillagerSpeakerId(villager);
        String displayName = resolveDisplayName(villager, professionKey);

        ProfessionProfile profProfile = resolveProfessionProfile(professionKey);
        CharacterArchetype archetype = resolveArchetype(villager);

        Speaker speaker = new Speaker(
                villager.getUniqueId(),
                speakerId,
                identity.villageId(),
                identity.villageName(),
                displayName,
                profProfile.role(),
                archetype.buildPersonality(profProfile.basePersonality()),
                archetype.tone(),
                archetype.behaviorHint(),
                archetype.buildGreeting(profProfile.greetingTemplate(), profProfile.role(), identity.villageName()),
                professionKey,
                villager.getWorld().getName(),
                villager.getLocation().getX(),
                villager.getLocation().getY(),
                villager.getLocation().getZ(),
                SpeakerStatus.NORMALER_DORFBEWOHNER);

        speakerRepository.save(speaker);
        return speaker;
    }

    public void refreshLoadedVillagerProfiles(Iterable<Villager> villagers) {
        if (villagers == null) {
            return;
        }
        for (Villager villager : villagers) {
            if (villager == null) {
                continue;
            }
            createOrRefreshProfile(villager);
        }
    }

    // ─── Chief-Status-Mutationen (von ChiefService delegiert) ───

    public Speaker promoteToChief(Villager villager) {
        Speaker existing = speakerRepository.findByEntityUuid(villager.getUniqueId()).orElse(null);
        Speaker speaker;
        if (existing != null) {
            speaker = new Speaker(
                    existing.entityUuid(), existing.speakerId(), existing.villageId(),
                    existing.villageName(), existing.displayName(), "Häuptling",
                    existing.personality(), existing.speechTone(), existing.behaviorHint(),
                    existing.greeting(), existing.profession(), existing.world(),
                    existing.x(), existing.y(), existing.z(),
                    SpeakerStatus.AKTIV_CHIEF);
        } else {
            speaker = createOrRefreshProfile(villager);
            speaker = new Speaker(
                    speaker.entityUuid(), speaker.speakerId(), speaker.villageId(),
                    speaker.villageName(), speaker.displayName(), "Häuptling",
                    speaker.personality(), speaker.speechTone(), speaker.behaviorHint(),
                    speaker.greeting(), speaker.profession(), speaker.world(),
                    speaker.x(), speaker.y(), speaker.z(),
                    SpeakerStatus.AKTIV_CHIEF);
        }
        speakerRepository.save(speaker);
        return speaker;
    }

    public void demoteFromChief(Villager villager) {
        speakerRepository.findByEntityUuid(villager.getUniqueId()).ifPresent(existing -> {
            Speaker demoted = new Speaker(
                    existing.entityUuid(), existing.speakerId(), existing.villageId(),
                    existing.villageName(), existing.displayName(), existing.role(),
                    existing.personality(), existing.speechTone(), existing.behaviorHint(),
                    existing.greeting(), existing.profession(), existing.world(),
                    existing.x(), existing.y(), existing.z(),
                    SpeakerStatus.NORMALER_DORFBEWOHNER);
            speakerRepository.save(demoted);
        });
    }

    public void markAsFormerChief(Villager villager) {
        speakerRepository.findByEntityUuid(villager.getUniqueId()).ifPresent(existing -> {
            Speaker mourned = new Speaker(
                    existing.entityUuid(), existing.speakerId(), existing.villageId(),
                    existing.villageName(), existing.displayName(), existing.role(),
                    existing.personality(), existing.speechTone(), existing.behaviorHint(),
                    existing.greeting(), existing.profession(), existing.world(),
                    existing.x(), existing.y(), existing.z(),
                    SpeakerStatus.GEWESENER_CHIEF);
            speakerRepository.save(mourned);
        });
        if (speakerRepository.findByEntityUuid(villager.getUniqueId()).isEmpty()) {
            Speaker fresh = createOrRefreshProfile(villager);
            Speaker mourned = new Speaker(
                    fresh.entityUuid(), fresh.speakerId(), fresh.villageId(),
                    fresh.villageName(), fresh.displayName(), fresh.role(),
                    fresh.personality(), fresh.speechTone(), fresh.behaviorHint(),
                    fresh.greeting(), fresh.profession(), fresh.world(),
                    fresh.x(), fresh.y(), fresh.z(),
                    SpeakerStatus.GEWESENER_CHIEF);
            speakerRepository.save(mourned);
        }
    }

    public Optional<Speaker> findActiveChiefByVillageId(String villageId) {
        return speakerRepository.findByVillageId(villageId).stream()
                .filter(s -> s.speakerStatus() == SpeakerStatus.AKTIV_CHIEF)
                .findFirst();
    }

    // ─── name resolution ───

    public String resolveDisplayName(Villager villager, String professionKey) {
        String customName = villager.getCustomName();
        if (customName != null && !customName.isBlank()) {
            return customName;
        }
        String villageId = villageIdentityService.resolve(villager).villageId();
        String generatedName = pickNameFromPool(professionKey, villageId, villager.getUniqueId());
        villager.setCustomName(generatedName);
        villager.setCustomNameVisible(false);
        return generatedName;
    }

    public void reloadNamePools() {
        this.namePools = loadNamePools();
    }

    private String pickNameFromPool(String poolKey, String villageId, UUID uuid) {
        Map<String, List<String>> pools = namePools;
        List<String> pool = pools.getOrDefault(poolKey.toUpperCase(Locale.ROOT),
                pools.getOrDefault("default", Collections.emptyList()));
        if (pool.isEmpty()) {
            return poolKey.substring(0, 1).toUpperCase(Locale.ROOT)
                    + poolKey.substring(1).toLowerCase(Locale.ROOT)
                    + "-" + uuid.toString().substring(0, 6);
        }

        Set<String> usedNames = new HashSet<>();
        for (Speaker speaker : speakerRepository.findByVillageId(villageId)) {
            if (speaker.displayName() != null) {
                usedNames.add(speaker.displayName());
            }
        }

        int offset = Math.floorMod(uuid.hashCode(), pool.size());
        for (int i = 0; i < pool.size(); i++) {
            String candidate = pool.get((offset + i) % pool.size());
            if (!usedNames.contains(candidate)) {
                return candidate;
            }
        }
        return pool.get(offset);
    }

    private Map<String, List<String>> loadNamePools() {
        if (!namePoolsFile.exists()) {
            Map<String, List<String>> defaults = emergencyNamePools();
            saveNamePools(defaults);
            return defaults;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(namePoolsFile);
        Map<String, List<String>> pools = new HashMap<>();
        for (String key : config.getKeys(false)) {
            List<String> names = config.getStringList(key);
            if (names != null && !names.isEmpty()) {
                pools.put(key.toUpperCase(Locale.ROOT), names);
            }
        }
        Map<String, List<String>> emergency = emergencyNamePools();
        for (Map.Entry<String, List<String>> entry : emergency.entrySet()) {
            pools.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return pools;
    }

    private void saveNamePools(Map<String, List<String>> pools) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            logger.warning("Konnte Plugin-Data-Folder nicht erstellen");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, List<String>> entry : pools.entrySet()) {
            config.set(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        try {
            config.save(namePoolsFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Konnte name-pools.yml nicht speichern", e);
        }
    }

    // ─── helpers ───

    private String normalizeProfessionKey(Profession profession) {
        if (profession == null) {
            return "NONE";
        }
        return profession.name().toUpperCase(Locale.ROOT);
    }

    private String createVillagerSpeakerId(Villager villager) {
        return "villager-" + villager.getUniqueId().toString().substring(0, 8);
    }

    private ProfessionProfile resolveProfessionProfile(String professionKey) {
        return PROFESSION_PROFILES.getOrDefault(professionKey, PROFESSION_PROFILES.get("DEFAULT"));
    }

    private CharacterArchetype resolveArchetype(Villager villager) {
        int variant = Math.floorMod(villager.getUniqueId().hashCode(), ARCHETYPES.size());
        return ARCHETYPES.get(variant);
    }

    // ─── static initializers ───

    private static Map<String, ProfessionProfile> buildProfessionProfiles() {
        Map<String, ProfessionProfile> profiles = new HashMap<>();
        profiles.put("DEFAULT", new ProfessionProfile("Dorfbewohner", "einfach und gutmuetig", null));
        profiles.put("ARMORER", new ProfessionProfile("Ruestungsschmied", "praktisch und direkt", null));
        profiles.put("BUTCHER", new ProfessionProfile("Fleischer", "bodenstaendig und freundlich", "Frisches Fleisch gibt es spaeter. Erstmal: Ich bin der {role} aus {village}."));
        profiles.put("CARTOGRAPHER", new ProfessionProfile("Kartograph", "neugierig und aufmerksam", null));
        profiles.put("CLERIC", new ProfessionProfile("Kleriker", "ruhig und bedaechtig", null));
        profiles.put("FARMER", new ProfessionProfile("Bauer", "bodenstaendig und freundlich", null));
        profiles.put("FISHERMAN", new ProfessionProfile("Fischer", "bodenstaendig und freundlich", null));
        profiles.put("FLETCHER", new ProfessionProfile("Pfeilmacher", "geschaeftig und gewissenhaft", null));
        profiles.put("LEATHERWORKER", new ProfessionProfile("Lederarbeiter", "geschaeftig und gewissenhaft", null));
        profiles.put("LIBRARIAN", new ProfessionProfile("Bibliothekar", "neugierig und aufmerksam", null));
        profiles.put("MASON", new ProfessionProfile("Steinmetz", "geschaeftig und gewissenhaft", null));
        profiles.put("SHEPHERD", new ProfessionProfile("Schaefer", "bodenstaendig und freundlich", null));
        profiles.put("TOOLSMITH", new ProfessionProfile("Werkzeugschmied", "praktisch und direkt", null));
        profiles.put("WEAPONSMITH", new ProfessionProfile("Waffenschmied", "praktisch und direkt", null));
        return Collections.unmodifiableMap(profiles);
    }

    private static List<CharacterArchetype> buildArchetypes() {
        return List.of(
                new CharacterArchetype("eher herzlich und redselig", "warm und zugeneigt",
                        "spricht gern offen und persoenlich",
                        "Guten Tag. Ich bin der {role} aus {village}."),
                new CharacterArchetype("eher reserviert und trocken", "trocken und knapp",
                        "antwortet knapp und manchmal spruehde",
                        "Ja? Ich bin der {role} aus {village}."),
                new CharacterArchetype("mit rauem Humor und spitzer Zunge", "sarkastisch und rau",
                        "macht bissige Witze, ohne die Grenze zu verbotenen Inhalten zu ueberschreiten",
                        "Na schoen. Der {role} aus {village} hoert zu."),
                new CharacterArchetype("wachsam und schnell misstrauisch", "vorsichtig und wachsam",
                        "bleibt hoeflich, aber prueft Fremde genau",
                        "Halt erst einmal an. Ich bin der {role} aus {village}."));
    }

    private static Map<String, List<String>> emergencyNamePools() {
        Map<String, List<String>> pools = new HashMap<>();
        pools.put("chief", List.of("Adelar", "Alarich", "Aldric", "Baldomar", "Bernulf", "Branko"));
        pools.put("default", List.of("Ari", "Mila", "Toma", "Lina", "Bela", "Jaro", "Borin", "Selma", "Maren", "Ivo"));
        pools.put("LIBRARIAN", List.of("Alda", "Borin", "Selma", "Tovin", "Maren", "Ivo"));
        pools.put("CARTOGRAPHER", List.of("Niko", "Elsa", "Rurik", "Lena", "Taro", "Mila"));
        pools.put("FARMER", List.of("Edda", "Hanno", "Greta", "Falk", "Tilda", "Jorin"));
        pools.put("CLERIC", List.of("Mira", "Simeon", "Alva", "Noam", "Rina", "Tobit"));
        pools.put("BUTCHER", List.of("Torv", "Brina", "Kuno", "Rika", "Bram", "Hela"));
        pools.put("ARMORER", List.of("Raska", "Doran", "Hedda", "Kjell", "Marta", "Olek"));
        pools.put("TOOLSMITH", List.of("Raska", "Doran", "Hedda", "Kjell", "Marta", "Olek"));
        pools.put("WEAPONSMITH", List.of("Raska", "Doran", "Hedda", "Kjell", "Marta", "Olek"));
        pools.put("FISHERMAN", List.of("Janne", "Piet", "Svala", "Enno", "Kora", "Nils"));
        pools.put("SHEPHERD", List.of("Liese", "Fenja", "Oda", "Miro", "Jara", "Pavel"));
        pools.put("FLETCHER", List.of("Yaro", "Tessa", "Rena", "Corin", "Mika", "Leif"));
        pools.put("LEATHERWORKER", List.of("Bela", "Rumo", "Tjara", "Eske", "Hilda", "Vero"));
        pools.put("MASON", List.of("Arno", "Petra", "Kara", "Merten", "Sora", "Iven"));
        pools.put("NITWIT", List.of("Alberich", "Balduin", "Bimbo", "Bodo", "Dussel", "Eumel"));
        pools.put("NONE", List.of("Ari", "Bela", "Borin", "Bran", "Brecht", "Corin"));
        return pools;
    }
}