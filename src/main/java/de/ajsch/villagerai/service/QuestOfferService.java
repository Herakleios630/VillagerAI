package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Chief;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.model.VillagerContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionType;

public final class QuestOfferService {

    private final Logger logger;
    private final QuestService questService;
    private final QuestDifficultyService questDifficultyService;
    private volatile Map<String, List<OfferTemplate>> templatesByProfession;
    private volatile List<OfferTemplate> defaultTemplates;

    public QuestOfferService(
            Logger logger,
            QuestService questService,
            QuestDifficultyService questDifficultyService,
            ConfigurationSection templatesSection) {
        this.logger = logger;
        this.questService = questService;
        this.questDifficultyService = questDifficultyService;
        reloadTemplates(templatesSection);
    }

    public synchronized void reloadTemplates(ConfigurationSection templatesSection) {
        QuestOfferTemplateConfig config = loadTemplates(templatesSection);
        this.templatesByProfession = config.templatesByProfession();
        this.defaultTemplates = config.defaultTemplates();
    }

    public QuestOffer createOffer(UUID playerUuid, Chief chief, VillagerContext villagerContext, int villageReputationScore) {
        String profession = villagerContext.profession() == null
                ? "NONE"
                : villagerContext.profession().toUpperCase(Locale.ROOT);
        List<OfferTemplate> templates = templatesByProfession.getOrDefault(profession, defaultTemplates);
        int preferredTier = questDifficultyService.getPreference(playerUuid, chief.chiefId()).preferredDifficultyTier();
        int unlockedTier = questDifficultyService.resolveUnlockedTier(villageReputationScore);
        int selectedTier = Math.min(preferredTier, unlockedTier);
        List<OfferTemplate> selectedTemplates = selectTemplatesForTier(templates, selectedTier);
        int variant = Math.floorMod(playerUuid.hashCode() ^ chief.chiefId().hashCode(), selectedTemplates.size());
        questDifficultyService.recordSuggestedTier(playerUuid, chief.chiefId(), selectedTierForTemplates(selectedTemplates));
        return selectedTemplates.get(variant).toOffer(chief, this);
    }

    public QuestOffer createOfferForTier(UUID playerUuid, Chief chief, VillagerContext villagerContext, int desiredTier) {
        String profession = villagerContext.profession() == null
                ? "NONE"
                : villagerContext.profession().toUpperCase(Locale.ROOT);
        List<OfferTemplate> templates = templatesByProfession.getOrDefault(profession, defaultTemplates);
        List<OfferTemplate> selectedTemplates = selectTemplatesForTier(templates, questDifficultyService.clampTier(desiredTier));
        int variant = Math.floorMod(playerUuid.hashCode() ^ chief.chiefId().hashCode(), selectedTemplates.size());
        questDifficultyService.recordSuggestedTier(playerUuid, chief.chiefId(), selectedTierForTemplates(selectedTemplates));
        return selectedTemplates.get(variant).toOffer(chief, this);
    }

    private List<OfferTemplate> selectTemplatesForTier(List<OfferTemplate> templates, int preferredTier) {
        int selectedTier = -1;
        for (OfferTemplate template : templates) {
            if (template.difficultyTier() <= preferredTier) {
                selectedTier = Math.max(selectedTier, template.difficultyTier());
            }
        }
        if (selectedTier < 0) {
            selectedTier = 0;
        }

        List<OfferTemplate> matchingTemplates = new ArrayList<>();
        for (OfferTemplate template : templates) {
            if (template.difficultyTier() == selectedTier) {
                matchingTemplates.add(template);
            }
        }

        return matchingTemplates.isEmpty() ? List.copyOf(templates) : matchingTemplates;
    }

    private int selectedTierForTemplates(List<OfferTemplate> templates) {
        return templates.stream().map(OfferTemplate::difficultyTier).max(Comparator.naturalOrder()).orElse(0);
    }

    public Quest acceptOffer(Player player, Chief chief, QuestOffer offer) {
        return switch (offer.type()) {
            case BREW -> questService.activateBrewQuest(player.getUniqueId(), chief, offer.potionType(), offer.amount(), offer.difficultyTier());
            case FETCH -> questService.activateFetchQuest(player, chief, offer.material(), offer.amount(), offer.difficultyTier());
            case REPAIR -> questService.activateRepairQuest(player.getUniqueId(), chief, offer.material(), offer.amount(), offer.difficultyTier());
            case BUILD -> questService.activateBuildQuest(player.getUniqueId(), chief, offer.material(), offer.amount(), offer.difficultyTier());
            case BREED -> questService.activateBreedQuest(player.getUniqueId(), chief, offer.entityType(), offer.amount(), offer.difficultyTier());
            case KILL -> questService.activateKillQuest(player.getUniqueId(), chief, offer.entityType(), offer.amount(), offer.difficultyTier());
            case VISIT -> questService.activateVisitQuest(
                    player.getUniqueId(),
                    chief,
                    offer.worldName(),
                    offer.targetX(),
                    offer.targetZ(),
                    offer.radius(),
                    offer.difficultyTier());
            case DELIVER, TALK -> questService.activateTalkQuest(player.getUniqueId(), chief, offer.title(), offer.description());
        };
    }

    private QuestOffer repairOffer(Material material, int amount, String intro, Chief chief, int difficultyTier) {
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.REPAIR,
                "Repariere mit " + amount + " " + materialName,
                "Bringe " + amount + " " + materialName + " zu " + chief.chatName() + ", damit wir Reparaturen schaffen.",
                intro + " Bring mir " + amount + " " + materialName + " fuer die Reparaturen. Uebernimmst du das?",
                "Gut. Mit dem Material reparieren wir das Dorf schnell.",
                material,
                amount,
                null,
                null,
                0,
                0,
                0,
                null,
                difficultyTier);
    }

    private QuestOffer buildOffer(Material material, int amount, String intro, Chief chief, int difficultyTier) {
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.BUILD,
                "Baue " + amount + " " + materialName,
                "Platziere " + amount + " " + materialName + " und melde dich danach wieder bei " + chief.chatName() + ".",
                intro + " Platziere " + amount + " " + materialName + " fuer unser Dorf. Bist du dabei?",
                "Abgemacht. Bau die Bloecke und melde dich danach wieder.",
                material,
                amount,
                null,
                null,
                0,
                0,
                0,
                null,
                difficultyTier);
    }

    private QuestOffer breedOffer(EntityType entityType, int amount, String intro, Chief chief, int difficultyTier) {
        String entityName = formatEntityType(entityType);
        return new QuestOffer(
                QuestType.BREED,
                "Zuechte " + amount + " " + entityName,
                "Zuechte " + amount + " " + entityName + " fuer " + chief.chatName() + ".",
                intro + " Zuechte " + amount + " " + entityName + " fuer unser Dorf. Uebernimmst du das?",
                "Gut. Sorge fuer Nachwuchs und berichte dann wieder.",
                null,
                amount,
                entityType,
                null,
                0,
                0,
                0,
                null,
                difficultyTier);
    }

    private QuestOffer brewOffer(PotionType potionType, int amount, String intro, Chief chief, int difficultyTier) {
        String potionName = formatPotionType(potionType);
        return new QuestOffer(
                QuestType.BREW,
                "Braue " + amount + " " + potionName,
                "Bringe " + amount + " " + potionName + " zu " + chief.chatName() + ".",
                intro + " Braue mir " + amount + " " + potionName + " und bring sie dann zu mir. Willst du das uebernehmen?",
                "Gut. Dann braue " + amount + " " + potionName + " und bring sie mir danach.",
                null,
                amount,
                null,
                null,
                0,
                0,
                0,
                potionType,
                difficultyTier);
    }

    private QuestOffer fetchOffer(Material material, int amount, String intro, Chief chief, int difficultyTier) {
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.FETCH,
                "Sammle " + amount + " " + materialName,
                "Besorge " + amount + " " + materialName + " und melde dich danach wieder bei " + chief.chatName() + ".",
                intro + " Besorge mir " + amount + " " + materialName + ". Willst du das uebernehmen?",
                "Gut. Dann bring mir " + amount + " " + materialName + " und melde dich danach wieder.",
                material,
                amount,
                null,
                null,
                0,
                0,
                0,
                null,
                difficultyTier);
    }

            private QuestOffer killOffer(EntityType entityType, int amount, String intro, Chief chief, int difficultyTier) {
        String entityName = formatEntityType(entityType);
        return new QuestOffer(
                QuestType.KILL,
                "Toete " + amount + " " + entityName,
                "Besiege " + amount + " " + entityName + " fuer " + chief.chatName() + ".",
                intro + " Besiege " + amount + " " + entityName + " und komm dann zu mir zurueck. Willst du das uebernehmen?",
                "Abgemacht. Erledige " + amount + " " + entityName + " und berichte mir danach.",
                null,
                amount,
                entityType,
                null,
                0,
                0,
                0,
                null,
                difficultyTier);
    }

            private QuestOffer visitOffer(Chief chief, int distance, int radius, int difficultyTier) {
        int baseX = (int) Math.round(chief.x());
        int baseZ = (int) Math.round(chief.z());
        int targetX = baseX + distance;
        int targetZ = baseZ - distance;
        return new QuestOffer(
                QuestType.VISIT,
                "Reise nach X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach wieder bei "
                        + chief.chatName() + ".",
                "Ich will wissen, was bei X " + targetX + " / Z " + targetZ + " los ist. Sieh dort nach und komm dann zu mir zurueck. Willst du das uebernehmen?",
                "Gut. Sieh dich dort um und komm danach wieder zu mir.",
                null,
                1,
                null,
                chief.world(),
                targetX,
                targetZ,
                radius,
                null,
                difficultyTier);
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatEntityType(EntityType entityType) {
        return entityType.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatPotionType(PotionType potionType) {
        return potionType.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private QuestOfferTemplateConfig loadTemplates(ConfigurationSection templatesSection) {
        Map<String, List<OfferTemplate>> templatesByProfession = new HashMap<>();
        List<OfferTemplate> configuredDefaultTemplates = new ArrayList<>();
        if (templatesSection == null) {
            logger.warning("quest-offers.yml fehlt oder enthaelt keine 'offer-templates'-Sektion. Nutze Notfall-Default fuer Questangebote.");
            return emergencyTemplateConfig();
        }

        for (String key : templatesSection.getKeys(false)) {
            List<OfferTemplate> templates = loadTemplateList(key, templatesSection.getMapList(key));
            if (templates.isEmpty()) {
                logger.warning("Quest-Angebote fuer '" + key + "' wurden ignoriert, weil keine gueltigen Templates gefunden wurden.");
                continue;
            }

            String normalizedKey = key.toUpperCase(Locale.ROOT);
            if ("DEFAULT".equals(normalizedKey)) {
                configuredDefaultTemplates = templates;
            } else {
                templatesByProfession.put(normalizedKey, templates);
            }
        }

        if (configuredDefaultTemplates.isEmpty()) {
            logger.warning("quest-offers.yml enthaelt keine gueltigen DEFAULT-Angebote. Nutze einen schmalen Notfall-Default.");
            configuredDefaultTemplates = emergencyDefaultTemplates();
        }

        return new QuestOfferTemplateConfig(templatesByProfession, configuredDefaultTemplates);
    }

    private List<OfferTemplate> loadTemplateList(String scopeName, List<Map<?, ?>> rawEntries) {
        List<OfferTemplate> templates = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawEntries) {
            OfferTemplate template = parseTemplate(scopeName, rawEntry);
            if (template != null) {
                templates.add(template);
            }
        }
        return templates;
    }

    private OfferTemplate parseTemplate(String scopeName, Map<?, ?> rawEntry) {
        if (rawEntry == null || rawEntry.isEmpty()) {
            logger.warning("Leeres Quest-Template in '" + scopeName + "' ignoriert.");
            return null;
        }

        String typeName = stringValue(rawEntry.get("type"));
        String intro = stringValue(rawEntry.get("intro"));
        if (typeName == null || intro == null) {
            logger.warning("Quest-Template in '" + scopeName + "' ignoriert: 'type' und 'intro' sind Pflichtfelder.");
            return null;
        }

        QuestType type;
        try {
            type = QuestType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warning("Quest-Template in '" + scopeName + "' ignoriert: unbekannter Typ '" + typeName + "'.");
            return null;
        }

        return switch (type) {
            case FETCH -> {
                Material material = Material.matchMaterial(stringValue(rawEntry.get("material")));
                int amount = intValue(rawEntry.get("amount"), 1);
                if (material == null || material.isAir() || amount <= 0) {
                    logger.warning("FETCH-Template in '" + scopeName + "' ignoriert: gueltiges Material und amount > 0 sind Pflicht.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, material, null, amount, 0, 0, null, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)));
            }
            case BREW -> {
                PotionType potionType = parsePotionType(stringValue(rawEntry.get("potion-type")));
                int amount = intValue(rawEntry.get("amount"), 1);
                if (potionType == null || amount <= 0) {
                    logger.warning("BREW-Template in '" + scopeName + "' ignoriert: gueltiger potion-type und amount > 0 sind Pflicht.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, null, null, amount, 0, 0, potionType, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)));
            }
            case KILL -> {
                EntityType entityType = parseEntityType(stringValue(rawEntry.get("entity-type")));
                int amount = intValue(rawEntry.get("amount"), 1);
                if (entityType == null || amount <= 0) {
                    logger.warning("KILL-Template in '" + scopeName + "' ignoriert: gueltiger entity-type und amount > 0 sind Pflicht.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, null, entityType, amount, 0, 0, null, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)));
            }
            case VISIT -> {
                int distance = intValue(rawEntry.get("distance"), 96);
                int radius = intValue(rawEntry.get("radius"), 5);
                if (distance <= 0 || radius <= 0) {
                    logger.warning("VISIT-Template in '" + scopeName + "' ignoriert: distance und radius muessen > 0 sein.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, null, null, 1, distance, radius, null, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)));
            }
            case REPAIR, BUILD -> {
                Material material = Material.matchMaterial(stringValue(rawEntry.get("material")));
                int amount = intValue(rawEntry.get("amount"), 1);
                if (material == null || material.isAir() || amount <= 0) {
                    logger.warning(type.name() + "-Template in '" + scopeName + "' ignoriert: gueltiges material und amount > 0 sind Pflicht.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, material, null, amount, 0, 0, null, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)));
            }
            case BREED -> {
                EntityType entityType = parseEntityType(stringValue(rawEntry.get("entity-type")));
                int amount = intValue(rawEntry.get("amount"), 1);
                if (entityType == null || amount <= 0) {
                    logger.warning("BREED-Template in '" + scopeName + "' ignoriert: gueltiger entity-type und amount > 0 sind Pflicht.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, null, entityType, amount, 0, 0, null, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)));
            }
            default -> null;
        };
    }

    private PotionType parsePotionType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PotionType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private EntityType parseEntityType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            EntityType entityType = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            return entityType.isAlive() ? entityType : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value).trim();
        return string.isEmpty() ? null : string;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

        private QuestOfferTemplateConfig emergencyTemplateConfig() {
        return new QuestOfferTemplateConfig(Map.of(), emergencyDefaultTemplates());
    }

        private List<OfferTemplate> emergencyDefaultTemplates() {
        return List.of(
            new OfferTemplate(QuestType.FETCH, "Ein wenig Proviant waere hilfreich.", Material.BREAD, null, 8, 0, 0, null, 0));
    }

    private record QuestOfferTemplateConfig(
            Map<String, List<OfferTemplate>> templatesByProfession,
            List<OfferTemplate> defaultTemplates) {
    }

    private record OfferTemplate(
            QuestType type,
            String intro,
            Material material,
            EntityType entityType,
            int amount,
            int distance,
            int radius,
            PotionType potionType,
            int difficultyTier) {

        private QuestOffer toOffer(Chief chief, QuestOfferService service) {
            return switch (type) {
                case BREW -> service.brewOffer(potionType, amount, intro, chief, difficultyTier);
                case FETCH -> service.fetchOffer(material, amount, intro, chief, difficultyTier);
                case REPAIR -> service.repairOffer(material, amount, intro, chief, difficultyTier);
                case BUILD -> service.buildOffer(material, amount, intro, chief, difficultyTier);
                case BREED -> service.breedOffer(entityType, amount, intro, chief, difficultyTier);
                case KILL -> service.killOffer(entityType, amount, intro, chief, difficultyTier);
                case VISIT -> service.visitOffer(chief, distance, radius, difficultyTier);
                default -> service.fetchOffer(Material.BREAD, 8, intro, chief, difficultyTier);
            };
        }
    }

    public record QuestOffer(
            QuestType type,
            String title,
            String description,
            String promptText,
            String acceptedReplyText,
            Material material,
            int amount,
            EntityType entityType,
            String worldName,
            int targetX,
            int targetZ,
            int radius,
            PotionType potionType,
            int difficultyTier) {
    }
}