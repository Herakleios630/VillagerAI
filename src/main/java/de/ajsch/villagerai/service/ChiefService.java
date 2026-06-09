package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.VillageIdentity;
import de.ajsch.villagerai.model.VillagerProfile;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.storage.VillagerProfileRepository;
import de.ajsch.villagerai.util.Keys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ChiefService {

    private final Keys keys;
    private final ChiefRepository chiefRepository;
    private final VillagerProfileRepository villagerProfileRepository;
    private final VillageIdentityService villageIdentityService;
    private final Logger logger;
    private volatile ChiefDefaults chiefDefaults;
    private volatile Map<String, ProfessionProfile> professionProfiles;
    private volatile List<CharacterArchetype> archetypes;

    public ChiefService(
            Keys keys,
            ChiefRepository chiefRepository,
            VillagerProfileRepository villagerProfileRepository,
            VillageIdentityService villageIdentityService,
            Logger logger,
            ConfigurationSection profilesSection) {
        this.keys = keys;
        this.chiefRepository = chiefRepository;
        this.villagerProfileRepository = villagerProfileRepository;
        this.villageIdentityService = villageIdentityService;
        this.logger = logger;
        reloadProfiles(profilesSection);
    }

    public synchronized void reloadProfiles(ConfigurationSection profilesSection) {
        this.chiefDefaults = loadChiefDefaults(profilesSection);
        this.professionProfiles = loadProfessionProfiles(profilesSection);
        this.archetypes = loadArchetypes(profilesSection);
    }

    public Chief markChief(Villager villager) {
        return markChief(villager, villageIdentityService.resolve(villager).villageId());
    }

    public Chief markChief(Villager villager, String villageId) {
        String chiefId = "chief-" + UUID.randomUUID().toString().substring(0, 8);
        VillageIdentity identity = villageIdentityService.resolve(villager);
        PersistentDataContainer container = villager.getPersistentDataContainer();
        container.set(keys.chiefFlagKey(), PersistentDataType.BYTE, (byte) 1);
        container.set(keys.chiefIdKey(), PersistentDataType.STRING, chiefId);
        container.set(keys.villageIdKey(), PersistentDataType.STRING, villageId);

        String villageName = identity.villageId().equals(villageId) ? identity.villageName() : identity.villageName();
        ChiefDefaults defaults = chiefDefaults;
        String displayName = defaults.displayName();
        String role = defaults.role();
        String personality = defaults.personality();
        String greeting = applyTemplate(defaults.greetingTemplate(), role, villageName);

        Chief chief = new Chief(
                villager.getUniqueId(),
                chiefId,
                villageId,
            villageName,
            identity.villageDescription(),
            identity.villageAttributes(),
            identity.villageBiome(),
            identity.villagePopulationEstimate(),
            identity.villageEventSummary(),
            displayName,
            role,
            personality,
            defaults.speechTone(),
            defaults.behaviorHint(),
            greeting,
                villager.getWorld().getName(),
                villager.getLocation().getX(),
                villager.getLocation().getY(),
                villager.getLocation().getZ());

        chiefRepository.saveChief(chief);
        return chief;
    }

    public boolean unmarkChief(Villager villager) {
        if (!isChief(villager)) {
            return false;
        }

        PersistentDataContainer container = villager.getPersistentDataContainer();
        container.remove(keys.chiefFlagKey());
        container.remove(keys.chiefIdKey());
        container.remove(keys.villageIdKey());
        chiefRepository.removeChief(villager.getUniqueId());
        villagerProfileRepository.removeProfile(villager.getUniqueId());
        return true;
    }

    public boolean isChief(Entity entity) {
        return getChief(entity).isPresent();
    }

    public Optional<Chief> getChief(Entity entity) {
        if (!(entity instanceof Villager villager)) {
            return Optional.empty();
        }

        PersistentDataContainer container = villager.getPersistentDataContainer();
        Byte flag = container.get(keys.chiefFlagKey(), PersistentDataType.BYTE);
        String chiefId = container.get(keys.chiefIdKey(), PersistentDataType.STRING);
        String villageId = container.get(keys.villageIdKey(), PersistentDataType.STRING);
        if (flag != null && flag == (byte) 1 && chiefId != null && villageId != null) {
            VillageIdentity identity = villageIdentityService.resolve(villager);
            ChiefDefaults defaults = chiefDefaults;
            return chiefRepository.findByEntityUuid(villager.getUniqueId()).or(() -> Optional.of(new Chief(
                    villager.getUniqueId(),
                    chiefId,
                    villageId,
                identity.villageName(),
                identity.villageDescription(),
                identity.villageAttributes(),
                identity.villageBiome(),
                identity.villagePopulationEstimate(),
                identity.villageEventSummary(),
                defaults.displayName(),
                defaults.role(),
                defaults.personality(),
                defaults.speechTone(),
                defaults.behaviorHint(),
                applyTemplate(defaults.greetingTemplate(), defaults.role(), identity.villageName()),
                    villager.getWorld().getName(),
                    villager.getLocation().getX(),
                    villager.getLocation().getY(),
                villager.getLocation().getZ())));
        }

        return chiefRepository.findByEntityUuid(villager.getUniqueId());
    }

    public Optional<Chief> findStoredChief(UUID entityUuid) {
        return chiefRepository.findByEntityUuid(entityUuid);
    }

    public Chief createConversationProfile(Villager villager) {
        VillageIdentity identity = villageIdentityService.resolve(villager);
        return villagerProfileRepository.findByEntityUuid(villager.getUniqueId())
                .map(existing -> updateConversationProfile(villager, existing.speakerId(), villager.getProfession(), identity))
                .orElseGet(() -> updateConversationProfile(
                        villager,
                        createVillagerSpeakerId(villager),
                        villager.getProfession(),
                        identity));
    }

    public void refreshConversationProfile(Villager villager, Profession profession) {
        if (isChief(villager)) {
            return;
        }

        villagerProfileRepository.findByEntityUuid(villager.getUniqueId())
                .ifPresent(existing -> updateConversationProfile(
                        villager,
                        existing.speakerId(),
                        profession,
                        villageIdentityService.resolve(villager)));
    }

    public void refreshLoadedVillagerProfiles(Iterable<Villager> villagers) {
        if (villagers == null) {
            return;
        }

        for (Villager villager : villagers) {
            if (villager == null || isChief(villager)) {
                continue;
            }
            createConversationProfile(villager);
        }
    }

    public Chief getConversationSpeaker(Villager villager) {
        return getChief(villager).orElseGet(() -> createConversationProfile(villager));
    }

    private String createVillagerSpeakerId(Villager villager) {
        return "villager-" + villager.getUniqueId().toString().substring(0, 8);
    }

    private Chief updateConversationProfile(
            Villager villager,
            String speakerId,
            Profession profession,
            VillageIdentity identity) {
        ProfessionProfile professionProfile = resolveProfessionProfile(profession);
        String role = professionProfile.role();
        String displayName = resolveDisplayName(villager, role);
        CharacterArchetype archetype = resolveArchetype(villager);
        VillagerProfile profile = new VillagerProfile(
                villager.getUniqueId(),
                speakerId,
                identity.villageId(),
                identity.villageName(),
                displayName,
                role,
            archetype.buildPersonality(professionProfile.basePersonality()),
            archetype.buildGreeting(professionProfile.greetingTemplate(), role, identity.villageName()),
            normalizeProfessionKey(profession),
                System.currentTimeMillis());
        villagerProfileRepository.saveProfile(profile);

        return new Chief(
                villager.getUniqueId(),
                profile.speakerId(),
                profile.villageId(),
                profile.villageName(),
            identity.villageDescription(),
            identity.villageAttributes(),
            identity.villageBiome(),
            identity.villagePopulationEstimate(),
            identity.villageEventSummary(),
                profile.displayName(),
                profile.role(),
                profile.personality(),
            archetype.tone(),
            archetype.behaviorHint(),
                profile.greeting(),
                villager.getWorld().getName(),
                villager.getLocation().getX(),
                villager.getLocation().getY(),
                villager.getLocation().getZ());
    }

    private String resolveDisplayName(Villager villager, String role) {
        String customName = villager.getCustomName();
        if (customName != null && !customName.isBlank()) {
            return customName;
        }

        String generatedName = generateVillagerName(villager, role);
        villager.setCustomName(generatedName);
        villager.setCustomNameVisible(false);
        return generatedName;
    }

    private String generateVillagerName(Villager villager, String role) {
        String[] firstNames = switch (normalizeProfessionKey(villager.getProfession())) {
            case "LIBRARIAN" -> new String[] {"Alda", "Borin", "Selma", "Tovin", "Maren", "Ivo"};
            case "CARTOGRAPHER" -> new String[] {"Niko", "Elsa", "Rurik", "Lena", "Taro", "Mila"};
            case "FARMER" -> new String[] {"Edda", "Hanno", "Greta", "Falk", "Tilda", "Jorin"};
            case "CLERIC" -> new String[] {"Mira", "Simeon", "Alva", "Noam", "Rina", "Tobit"};
            case "BUTCHER" -> new String[] {"Torv", "Brina", "Kuno", "Rika", "Bram", "Hela"};
            case "ARMORER", "TOOLSMITH", "WEAPONSMITH" -> new String[] {"Raska", "Doran", "Hedda", "Kjell", "Marta", "Olek"};
            case "FISHERMAN" -> new String[] {"Janne", "Piet", "Svala", "Enno", "Kora", "Nils"};
            case "SHEPHERD" -> new String[] {"Liese", "Fenja", "Oda", "Miro", "Jara", "Pavel"};
            case "FLETCHER" -> new String[] {"Yaro", "Tessa", "Rena", "Corin", "Mika", "Leif"};
            case "LEATHERWORKER" -> new String[] {"Bela", "Rumo", "Tjara", "Eske", "Hilda", "Vero"};
            case "MASON" -> new String[] {"Arno", "Petra", "Kara", "Merten", "Sora", "Iven"};
            default -> new String[] {"Ari", "Mila", "Toma", "Lina", "Bela", "Jaro"};
        };
        int index = Math.floorMod(villager.getUniqueId().hashCode(), firstNames.length);
        return firstNames[index];
    }

    private ProfessionProfile resolveProfessionProfile(Profession profession) {
        String professionKey = normalizeProfessionKey(profession);
        ProfessionProfile defaultProfile = professionProfiles.getOrDefault("DEFAULT", emergencyProfessionProfiles().get("DEFAULT"));
        return professionProfiles.getOrDefault(professionKey, defaultProfile);
    }

    private String normalizeProfessionKey(Profession profession) {
        if (profession == null) {
            return "NONE";
        }
        return profession.name().toUpperCase(Locale.ROOT);
    }

    private CharacterArchetype resolveArchetype(Villager villager) {
        List<CharacterArchetype> configuredArchetypes = archetypes;
        int variant = Math.floorMod(villager.getUniqueId().hashCode(), configuredArchetypes.size());
        return configuredArchetypes.get(variant);
    }

    private ChiefDefaults loadChiefDefaults(ConfigurationSection profilesSection) {
        if (profilesSection == null) {
            logger.warning("chief-profiles.yml fehlt. Nutze Notfall-Defaults fuer Chiefs und Villager-Profile.");
            return emergencyChiefDefaults();
        };

        ConfigurationSection chiefSection = profilesSection.getConfigurationSection("chief");
        if (chiefSection == null) {
            logger.warning("chief-profiles.yml enthaelt keine 'chief'-Sektion. Nutze Notfall-Defaults fuer Chiefs.");
            return emergencyChiefDefaults();
        }

        String displayName = readString(chiefSection, "display-name", emergencyChiefDefaults().displayName());
        String role = readString(chiefSection, "role", emergencyChiefDefaults().role());
        String personality = readString(chiefSection, "personality", emergencyChiefDefaults().personality());
        String speechTone = readString(chiefSection, "speech-tone", emergencyChiefDefaults().speechTone());
        String behaviorHint = readString(chiefSection, "behavior-hint", emergencyChiefDefaults().behaviorHint());
        String greetingTemplate = readString(chiefSection, "greeting-template", emergencyChiefDefaults().greetingTemplate());
        return new ChiefDefaults(displayName, role, personality, speechTone, behaviorHint, greetingTemplate);
    }

    private Map<String, ProfessionProfile> loadProfessionProfiles(ConfigurationSection profilesSection) {
        Map<String, ProfessionProfile> profiles = new HashMap<>();
        if (profilesSection != null) {
            ConfigurationSection professionsSection = profilesSection.getConfigurationSection("professions");
            if (professionsSection != null) {
                for (String key : professionsSection.getKeys(false)) {
                    ConfigurationSection professionSection = professionsSection.getConfigurationSection(key);
                    if (professionSection == null) {
                        continue;
                    }

                    String role = readString(professionSection, "role", null);
                    String basePersonality = readString(professionSection, "base-personality", null);
                    String greetingTemplate = readString(professionSection, "greeting-template", null);
                    if (role == null || basePersonality == null) {
                        logger.warning("Berufsprofil '" + key + "' in chief-profiles.yml ignoriert: role und base-personality sind Pflicht.");
                        continue;
                    }
                    profiles.put(key.toUpperCase(Locale.ROOT), new ProfessionProfile(role, basePersonality, greetingTemplate));
                }
            }
        }

        if (!profiles.containsKey("DEFAULT")) {
            logger.warning("chief-profiles.yml enthaelt kein DEFAULT-Berufsprofil. Nutze Notfall-Default.");
            profiles.put("DEFAULT", emergencyProfessionProfiles().get("DEFAULT"));
        }

        for (Map.Entry<String, ProfessionProfile> entry : emergencyProfessionProfiles().entrySet()) {
            profiles.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return profiles;
    }

    private List<CharacterArchetype> loadArchetypes(ConfigurationSection profilesSection) {
        List<CharacterArchetype> configuredArchetypes = new ArrayList<>();
        if (profilesSection != null) {
            for (Map<?, ?> rawEntry : profilesSection.getMapList("archetypes")) {
                CharacterArchetype archetype = parseArchetype(rawEntry);
                if (archetype != null) {
                    configuredArchetypes.add(archetype);
                }
            }
        }

        if (configuredArchetypes.isEmpty()) {
            logger.warning("chief-profiles.yml enthaelt keine gueltigen Archetypen. Nutze Notfall-Archetypen.");
            return emergencyArchetypes();
        }
        return configuredArchetypes;
    }

    private CharacterArchetype parseArchetype(Map<?, ?> rawEntry) {
        if (rawEntry == null || rawEntry.isEmpty()) {
            return null;
        }

        String personalitySuffix = readString(rawEntry.get("personality-suffix"));
        String tone = readString(rawEntry.get("tone"));
        String behaviorHint = readString(rawEntry.get("behavior-hint"));
        String greetingTemplate = readString(rawEntry.get("greeting-template"));
        if (personalitySuffix == null || tone == null || behaviorHint == null || greetingTemplate == null) {
            logger.warning("Archetyp in chief-profiles.yml ignoriert: personality-suffix, tone, behavior-hint und greeting-template sind Pflicht.");
            return null;
        }
        return new CharacterArchetype(personalitySuffix, tone, behaviorHint, greetingTemplate);
    }

    private String applyTemplate(String template, String role, String villageName) {
        return template.replace("{role}", role).replace("{village}", villageName);
    }

    private String readString(ConfigurationSection section, String path, String fallback) {
        String value = section.getString(path);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String readString(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue).trim();
        return value.isBlank() ? null : value;
    }

    private ChiefDefaults emergencyChiefDefaults() {
        return new ChiefDefaults(
                "Haeuptling",
                "Dorfhaeuptling",
                "bedacht",
                "ruhig und wuerdevoll",
                "spricht bedaechtig und mit natuerlicher Autoritaet",
                "Willkommen in {village}.");
    }

    private Map<String, ProfessionProfile> emergencyProfessionProfiles() {
        Map<String, ProfessionProfile> profiles = new HashMap<>();
        profiles.put("DEFAULT", new ProfessionProfile("Dorfbewohner", "einfach und gutmuetig", null));
        profiles.put("ARMORER", new ProfessionProfile("Ruestungsschmied", "praktisch und direkt", null));
        profiles.put("BUTCHER", new ProfessionProfile("Fleischer", "bodenstaendig und freundlich", null));
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
        return profiles;
    }

    private List<CharacterArchetype> emergencyArchetypes() {
        return List.of(
                new CharacterArchetype(
                        "eher herzlich und redselig",
                        "warm und zugeneigt",
                        "spricht gern offen und persoenlich",
                        "Guten Tag. Ich bin der {role} aus {village}."),
                new CharacterArchetype(
                        "eher reserviert und trocken",
                        "trocken und knapp",
                        "antwortet knapp und manchmal spruehde",
                        "Ja? Ich bin der {role} aus {village}."),
                new CharacterArchetype(
                        "mit rauem Humor und spitzer Zunge",
                        "sarkastisch und rau",
                        "macht bissige Witze, ohne die Grenze zu verbotenen Inhalten zu ueberschreiten",
                        "Na schoen. Der {role} aus {village} hoert zu."),
                new CharacterArchetype(
                        "wachsam und schnell misstrauisch",
                        "vorsichtig und wachsam",
                        "bleibt hoeflich, aber prueft Fremde genau",
                        "Halt erst einmal an. Ich bin der {role} aus {village}."));
    }

        private record ChiefDefaults(
            String displayName,
            String role,
            String personality,
            String speechTone,
            String behaviorHint,
            String greetingTemplate) {
    }

    private record ProfessionProfile(String role, String basePersonality, String greetingTemplate) {
    }

    private record CharacterArchetype(String personalitySuffix, String tone, String behaviorHint, String greetingTemplate) {

        private String buildPersonality(String basePersonality) {
            return basePersonality + ", " + personalitySuffix;
        }

        private String buildGreeting(String professionGreetingTemplate, String role, String villageName) {
            String template = professionGreetingTemplate == null || professionGreetingTemplate.isBlank()
                    ? greetingTemplate
                    : professionGreetingTemplate;
            return template.replace("{role}", role).replace("{village}", villageName);
        }
    }
}