package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.Quest;
import de.ajsch.villagerai.model.QuestType;
import de.ajsch.villagerai.model.VillagePerimeter;
import de.ajsch.villagerai.model.VillagerContext;
import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.service.ReputationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionType;

public final class QuestOfferService {

    private final Logger logger;
    private final QuestService questService;
    private final QuestDifficultyService questDifficultyService;
    private final VillageIdentityService villageIdentityService;
    private final VillagePerimeterService villagePerimeterService;
    private final DarkBlockCache darkBlockCache;
    private final ChiefRepository chiefRepository;
    private final ReputationService reputationService;
    private volatile int subAreaSize;
    private volatile int minDarkBlocks;
    private volatile Map<String, List<OfferTemplate>> templatesByProfession;
    private volatile List<OfferTemplate> defaultTemplates;
    private volatile List<OfferTemplate> retinueTemplates;
    private volatile List<OfferTemplate> legendaryTemplates;

    public QuestOfferService(
            Logger logger,
            QuestService questService,
            QuestDifficultyService questDifficultyService,
            ConfigurationSection templatesSection,
            VillageIdentityService villageIdentityService,
            VillagePerimeterService villagePerimeterService,
            DarkBlockCache darkBlockCache,
            ChiefRepository chiefRepository,
            ReputationService reputationService,
            int subAreaSize,
            int minDarkBlocks) {
        this.logger = logger;
        this.questService = questService;
        this.questDifficultyService = questDifficultyService;
        this.villageIdentityService = villageIdentityService;
        this.villagePerimeterService = villagePerimeterService;
        this.darkBlockCache = darkBlockCache;
        this.chiefRepository = chiefRepository;
        this.reputationService = reputationService;
        this.subAreaSize = Math.max(10, subAreaSize);
        this.minDarkBlocks = Math.max(1, minDarkBlocks);
        reloadTemplates(templatesSection);
    }

    public synchronized void reloadVillageLightSettings(int subAreaSize, int minDarkBlocks) {
        this.subAreaSize = Math.max(10, subAreaSize);
        this.minDarkBlocks = Math.max(1, minDarkBlocks);
    }

    public synchronized void reloadTemplates(ConfigurationSection templatesSection) {
        QuestOfferTemplateConfig config = loadTemplates(templatesSection);
        this.templatesByProfession = config.templatesByProfession();
        this.defaultTemplates = config.defaultTemplates();
    }

    public QuestOffer createOffer(UUID playerUuid, Speaker chief, VillagerContext villagerContext, int villageReputationScore) {
        String profession = villagerContext.profession() == null
                ? "NONE"
                : villagerContext.profession().toUpperCase(Locale.ROOT);
        List<OfferTemplate> templates = new ArrayList<>(templatesByProfession.getOrDefault(profession, defaultTemplates));

        // RETINUE + LEGENDARY: nur fuer LEGENDARY-Chiefs zusaetzliche Templates anhaengen
        if (isLegendaryChief(chief)) {
            templates.addAll(retinueTemplates);
            if (isLegendaryQuestAvailable(playerUuid, chief)) {
                templates.addAll(legendaryTemplates);
            }
        }

        int preferredTier = questDifficultyService.getPreference(playerUuid, chief.speakerId()).preferredDifficultyTier();
        int unlockedTier = questDifficultyService.resolveUnlockedTier(villageReputationScore);
        int selectedTier = Math.min(preferredTier, unlockedTier);
        List<OfferTemplate> selectedTemplates = selectTemplatesForTier(templates, selectedTier);
        int variant = Math.floorMod(playerUuid.hashCode() ^ chief.speakerId().hashCode(), selectedTemplates.size());
        questDifficultyService.recordSuggestedTier(playerUuid, chief.speakerId(), selectedTierForTemplates(selectedTemplates));
        return selectedTemplates.get(variant).toOffer(chief, this);
    }

    public QuestOffer createOfferForTier(UUID playerUuid, Speaker chief, VillagerContext villagerContext, int desiredTier) {
        String profession = villagerContext.profession() == null
                ? "NONE"
                : villagerContext.profession().toUpperCase(Locale.ROOT);
        List<OfferTemplate> templates = new ArrayList<>(templatesByProfession.getOrDefault(profession, defaultTemplates));

        if (isLegendaryChief(chief)) {
            templates.addAll(retinueTemplates);
            if (isLegendaryQuestAvailable(playerUuid, chief)) {
                templates.addAll(legendaryTemplates);
            }
        }

        List<OfferTemplate> selectedTemplates = selectTemplatesForTier(templates, questDifficultyService.clampTier(desiredTier));
        int variant = Math.floorMod(playerUuid.hashCode() ^ chief.speakerId().hashCode(), selectedTemplates.size());
        questDifficultyService.recordSuggestedTier(playerUuid, chief.speakerId(), selectedTierForTemplates(selectedTemplates));
        return selectedTemplates.get(variant).toOffer(chief, this);
    }

    private boolean isLegendaryChief(Speaker chief) {
        return chiefRepository.findByEntityUuid(chief.entityUuid())
                .map(ChiefAttributes::legendaryUnlocked)
                .orElse(false);
    }

    private boolean isLegendaryQuestAvailable(UUID playerUuid, Speaker chief) {
        Optional<ChiefAttributes> attrsOpt = chiefRepository.findByEntityUuid(chief.entityUuid());
        if (attrsOpt.isEmpty() || !attrsOpt.get().legendaryUnlocked()) {
            return false;
        }
        ChiefAttributes attrs = attrsOpt.get();
        // Prüfe shared Cooldown: 140 Minuten
        long cooldownMillis = 140L * 60L * 1000L;
        if (attrs.legendaryLastActivated() > 0L
                && System.currentTimeMillis() - attrs.legendaryLastActivated() < cooldownMillis) {
            return false;
        }
        int combinedReputation = reputationService.getCombinedScore(
                playerUuid, chief.villageId(), chief.speakerId());
        return combinedReputation >= 100;
    }

    private void updateLegendaryLastActivated(Speaker chief) {
        chiefRepository.findByEntityUuid(chief.entityUuid()).ifPresent(attrs -> {
            ChiefAttributes updated = new ChiefAttributes(
                    attrs.entityUuid(),
                    attrs.speakerId(),
                    attrs.villageId(),
                    attrs.crownedAt(),
                    attrs.mournedAt(),
                    attrs.isActive(),
                    attrs.visualTier(),
                    attrs.biomeStyle(),
                    attrs.bannerPattern(),
                    attrs.legendaryUnlocked(),
                    System.currentTimeMillis());
            chiefRepository.save(updated);
        });
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

    public Quest acceptOffer(Player player, Speaker chief, QuestOffer offer) {
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
            case EXPLORE -> questService.activateExploreQuest(
                    player.getUniqueId(),
                    chief,
                    offer.worldName(),
                    offer.targetX(),
                    offer.targetZ(),
                    offer.radius(),
                    offer.difficultyTier());
            case SECURE -> {
                    if (offer.targetKey() != null && offer.targetKey().contains("|light|")) {
                        yield questService.activateSecureQuestByTargetKey(
                                player.getUniqueId(),
                                chief,
                                offer.material(),
                                0,
                                offer.worldName(),
                                offer.targetX(),
                                offer.targetZ(),
                                offer.radius(),
                                offer.difficultyTier(),
                                offer.targetKey());
                    }
                    yield questService.activateSecureQuest(
                            player.getUniqueId(),
                            chief,
                            offer.material(),
                            offer.amount(),
                            offer.worldName(),
                            offer.targetX(),
                            offer.targetZ(),
                            offer.radius(),
                            offer.difficultyTier());
                }
            case DELIVER, TALK -> questService.activateTalkQuest(player.getUniqueId(), chief, offer.title(), offer.description());
            case RETINUE_GUARD -> questService.activateRetinueGuardQuest(
                    player.getUniqueId(), chief, chief.entityUuid(), offer.amount(), offer.difficultyTier());
            case RETINUE_GOLEM -> questService.activateRetinueGolemQuest(
                    player.getUniqueId(), chief, offer.worldName(), 0, 99999, 0, 99999, offer.difficultyTier());
            case RETINUE_WALL -> questService.activateRetinueWallQuest(
                    player.getUniqueId(), chief, offer.worldName(), 0, 99999, 0, 99999, offer.amount(), offer.difficultyTier());
            case RETINUE_BELL -> questService.activateRetinueBellQuest(
                    player.getUniqueId(), chief,
                    (double) chief.x(), (double) chief.y(), (double) chief.z(),
                    offer.worldName(), offer.difficultyTier());
            case LEGENDARY_DRAGON -> {
                    Quest quest = questService.activateLegendaryDragonQuest(
                            player.getUniqueId(), chief, offer.difficultyTier());
                    updateLegendaryLastActivated(chief);
                    yield quest;
                }
            case LEGENDARY_BLAZE -> {
                    Quest quest = questService.activateLegendaryBlazeQuest(
                            player.getUniqueId(), chief, offer.difficultyTier());
                    updateLegendaryLastActivated(chief);
                    yield quest;
                }
            case LEGENDARY_END -> {
                    Quest quest = questService.activateLegendaryEndQuest(
                            player.getUniqueId(), chief, offer.difficultyTier());
                    updateLegendaryLastActivated(chief);
                    yield quest;
                }
            case LEGENDARY_NETHER -> {
                    Quest quest = questService.activateLegendaryNetherQuest(
                            player.getUniqueId(), chief, offer.difficultyTier());
                    updateLegendaryLastActivated(chief);
                    yield quest;
                }
        };
    }

    private QuestOffer repairOffer(Material material, int amount, String intro, Speaker chief, int difficultyTier) {
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.REPAIR,
                "Repariere mit " + amount + " " + materialName,
                "Bringe " + amount + " " + materialName + " zu " + chief.displayName() + ", damit wir Reparaturen schaffen.",
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
                difficultyTier,
                material.name() + ":" + amount);
    }

    private QuestOffer buildOffer(Material material, int amount, String intro, Speaker chief, int difficultyTier) {
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.BUILD,
                "Baue " + amount + " " + materialName,
                "Platziere " + amount + " " + materialName + " und melde dich danach wieder bei " + chief.displayName() + ".",
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
                difficultyTier,
                material.name());
    }

    private QuestOffer breedOffer(EntityType entityType, int amount, String intro, Speaker chief, int difficultyTier) {
        String entityName = formatEntityType(entityType);
        return new QuestOffer(
                QuestType.BREED,
                "Zuechte " + amount + " " + entityName,
                "Zuechte " + amount + " " + entityName + " fuer " + chief.displayName() + ".",
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
                difficultyTier,
                entityType.name());
    }

    private QuestOffer brewOffer(PotionType potionType, int amount, String intro, Speaker chief, int difficultyTier) {
        String potionName = formatPotionType(potionType);
        return new QuestOffer(
                QuestType.BREW,
                "Braue " + amount + " " + potionName,
                "Bringe " + amount + " " + potionName + " zu " + chief.displayName() + ".",
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
                difficultyTier,
                potionType.name() + ":" + amount);
    }

    private QuestOffer fetchOffer(Material material, int amount, String intro, Speaker chief, int difficultyTier) {
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.FETCH,
                "Sammle " + amount + " " + materialName,
                "Besorge " + amount + " " + materialName + " und melde dich danach wieder bei " + chief.displayName() + ".",
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
                difficultyTier,
                material.name() + ":" + amount);
    }

            private QuestOffer killOffer(EntityType entityType, int amount, String intro, Speaker chief, int difficultyTier) {
        String entityName = formatEntityType(entityType);
        return new QuestOffer(
                QuestType.KILL,
                "Toete " + amount + " " + entityName,
                "Besiege " + amount + " " + entityName + " fuer " + chief.displayName() + ".",
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
                difficultyTier,
                entityType.name());
    }

            private QuestOffer visitOffer(Speaker chief, int distance, int radius, int difficultyTier) {
        int baseX = (int) Math.round(chief.x());
        int baseZ = (int) Math.round(chief.z());
        int targetX = baseX + distance;
        int targetZ = baseZ - distance;
        return new QuestOffer(
                QuestType.VISIT,
                "Reise nach X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach wieder bei "
                        + chief.displayName() + ".",
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
                difficultyTier,
                chief.world() + ":" + targetX + ":" + targetZ + ":" + radius);
    }

    private QuestOffer exploreOffer(Speaker chief, int distance, int radius, int difficultyTier) {
        int baseX = (int) Math.round(chief.x());
        int baseZ = (int) Math.round(chief.z());
        int targetX = baseX + distance;
        int targetZ = baseZ - distance;
        return new QuestOffer(
                QuestType.EXPLORE,
                "Erkunde X " + targetX + " / Z " + targetZ,
                "Erreiche den Ort bei X " + targetX + " / Z " + targetZ + " und melde dich danach wieder bei "
                        + chief.displayName() + ".",
                "Ich brauche einen Blick auf die Umgebung bei X " + targetX + " / Z " + targetZ
                        + ". Sieh dort nach und komm dann zu mir zurueck. Willst du das uebernehmen?",
                "Gut. Sieh dich dort um und komm danach wieder zu mir.",
                null,
                1,
                null,
                chief.world(),
                targetX,
                targetZ,
                radius,
                null,
                difficultyTier,
                chief.world() + ":" + targetX + ":" + targetZ + ":" + radius);
    }

    private QuestOffer secureOffer(Speaker chief, Material material, int amount, int distance, int radius, String intro, int difficultyTier) {
        int baseX = (int) Math.round(chief.x());
        int baseZ = (int) Math.round(chief.z());
        int targetX = baseX + distance;
        int targetZ = baseZ - distance;
        String materialName = formatMaterial(material);
        return new QuestOffer(
                QuestType.SECURE,
                "Sichere mit " + amount + " " + materialName,
                "Platziere " + amount + " " + materialName + " bei X " + targetX + " / Z " + targetZ
                        + " und melde dich danach wieder bei " + chief.displayName() + ".",
                "Ein wenig weiter draussen bei X " + targetX + " / Z " + targetZ + " ist es nicht sicher. "
                        + intro + " Platziere dort " + amount + " " + materialName + ". Bist du dabei?",
                "Gut. Sichere die Stelle bei X " + targetX + " / Z " + targetZ + " mit " + amount + " " + materialName + " und melde dich dann wieder.",
                material,
                amount,
                null,
                chief.world(),
                targetX,
                targetZ,
                radius,
                null,
                difficultyTier,
                material.name() + ":" + chief.world() + ":" + targetX + ":" + targetZ + ":" + radius);
    }

    public QuestOffer villageLightSecureOffer(Speaker chief, Material themeMaterial, String intro, int difficultyTier) {
        // 1) Resolve village perimeter
        // We need a Villager entity to compute the perimeter. The chief may or may not have
        // a loaded Villager. We search the server for a villager with matching speakerId.
        Villager referenceVillager = findVillagerBySpeakerUuid(chief.entityUuid());
        if (referenceVillager == null) {
            logger.fine("village-light offer: could not find loaded villager for chief " + chief.speakerId() + " – no offer");
            return null;
        }

        String villageId = chief.villageId();
        VillagePerimeter perimeter = villagePerimeterService.computePerimeter(referenceVillager, villageId, villageIdentityService);
        if (perimeter == null || perimeter.world() == null) {
            logger.fine("village-light offer: no perimeter for " + villageId + " – no offer");
            return null;
        }

        // 2) Check if there is a valid sub-area with enough dark blocks
        Optional<DarkBlockCache.SubAreaResult> result = darkBlockCache.pickRandomSubArea(
                perimeter, subAreaSize, minDarkBlocks);
        if (result.isEmpty()) {
            logger.fine("village-light offer: no valid subArea in " + villageId + " – no offer");
            return null;
        }

        DarkBlockCache.SubAreaResult subArea = result.get();
        String materialName = formatMaterial(themeMaterial);
        String worldName = perimeter.worldName();
        int cx = subArea.center().x();
        int cy = subArea.center().y();
        int cz = subArea.center().z();

        String title = "Bereich ausleuchten (" + subArea.initialDarkCount() + " dunkle Stellen)";
        String description = "Erhelle einen zufaelligen Sub-Bereich im Dorf " + (chief.villageName() != null ? chief.villageName() : "") + " solange noch kein dunkler Fleck uebrig ist, und melde dich danach wieder bei " + chief.displayName() + ".";
        String promptText = intro + " Wir haben " + subArea.initialDarkCount() + " dunkle Stellen zu vertreiben. Uebernimmst du das?";
        String acceptedReply = "Gut. Mach die Ecke hell und melde dich danach wieder bei mir.";

        // targetKey format for village-light (pipe-separated to avoid colon conflicts with villageId):
        // material|world|villageId|light|goal|initialDark|subCenterX|subCenterY|subCenterZ
        String targetKey = materialName + "|" + worldName + "|" + villageId + "|light|0|"
                + subArea.initialDarkCount() + "|" + cx + "|" + cy + "|" + cz;
        return new QuestOffer(
                QuestType.SECURE,
                title,
                description,
                promptText,
                acceptedReply,
                themeMaterial,
                0,
                null,
                worldName,
                cx,
                cz,
                subAreaSize,
                null,
                difficultyTier,
                targetKey);
    }

    // ── RETINUE offer methods ───────────────────────────────────────

    private QuestOffer retinueGuardOffer(Speaker chief, int durationMinutes, String intro, int difficultyTier, String label) {
        String title = label != null ? label : "Leibwache (" + durationMinutes + " min)";
        return new QuestOffer(
                QuestType.RETINUE_GUARD,
                title,
                "Bleibe " + durationMinutes + " Minuten in der Naehe des Haeuptlings und beschuetze ihn.",
                intro + " Willst du mich bewachen?",
                "Gut. Bleib in meiner Naehe und halte die Augen offen.",
                null,
                durationMinutes,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                32,
                null,
                difficultyTier,
                "RETINUE_GUARD:" + chief.entityUuid() + ":" + durationMinutes);
    }

    private QuestOffer retinueGolemOffer(Speaker chief, String intro, int difficultyTier, String label) {
        String title = label != null ? label : "Golem-Wache";
        return new QuestOffer(
                QuestType.RETINUE_GOLEM,
                title,
                "Erschaffe einen Eisengolem innerhalb des Dorf-Perimeters.",
                intro + " Uebernimmst du das?",
                "Gut. Erschaffe einen Eisengolem im Dorf und melde dich dann wieder.",
                null,
                1,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "RETINUE_GOLEM:" + chief.world());
    }

    private QuestOffer retinueWallOffer(Speaker chief, int amount, String intro, int difficultyTier, String label) {
        String title = label != null ? label : "Mauerbau (" + amount + " Bloecke)";
        return new QuestOffer(
                QuestType.RETINUE_WALL,
                title,
                "Platziere " + amount + " Stein- oder Ziegelbloecke innerhalb des Dorf-Perimeters.",
                intro + " Packst du mit an?",
                "Abgemacht. Bau die Bloecke innerhalb der Dorfgrenzen und melde dich dann.",
                null,
                amount,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "RETINUE_WALL:" + chief.world());
    }

    private QuestOffer retinueBellOffer(Speaker chief, String intro, int difficultyTier, String label) {
        String title = label != null ? label : "Glocken-Stifter";
        return new QuestOffer(
                QuestType.RETINUE_BELL,
                title,
                "Bringe eine Glocke zum Treffpunkt des Dorfes bei " + chief.displayName() + ".",
                intro + " Bringst du uns eine Glocke?",
                "Gut. Bringe eine Glocke her und laeute unseren Bund ein.",
                null,
                1,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "RETINUE_BELL:" + chief.world() + ":" + ((int) Math.round(chief.x())) + ":" + ((int) Math.round(chief.y())) + ":" + ((int) Math.round(chief.z())));
    }

    // ── LEGENDARY offer methods ────────────────────────────────────

    private QuestOffer legendaryDragonOffer(Speaker chief, String intro, int difficultyTier) {
        return new QuestOffer(
                QuestType.LEGENDARY_DRAGON,
                "Drachenjaeger",
                "Toete den Enderdrachen, damit das Dorf endlich aufatmen kann.",
                intro + " Stellst du dich der ultimativen Pruefung?",
                "Der Drache wird fallen wie alle, die unser Dorf bedrohen. Geh und kehre siegreich zurueck.",
                null,
                1,
                EntityType.ENDER_DRAGON,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "LEGENDARY_DRAGON:" + chief.entityUuid());
    }

    private QuestOffer legendaryBlazeOffer(Speaker chief, String intro, int difficultyTier) {
        return new QuestOffer(
                QuestType.LEGENDARY_BLAZE,
                "Lohenfaenger",
                "Bringe 5 Lohenruten aus dem Nether und ueberreiche sie dem Haeuptling.",
                intro + " Wagst du dich in die Festung?",
                "Gut. Besiege die Lohen im Nether und bring mir 5 Ruten als Beweis.",
                Material.BLAZE_ROD,
                5,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "LEGENDARY_BLAZE:" + chief.entityUuid() + ":5");
    }

    private QuestOffer legendaryEndOffer(Speaker chief, String intro, int difficultyTier) {
        return new QuestOffer(
                QuestType.LEGENDARY_END,
                "End-Trophae",
                "Bringe eine Shulker-Schale oder Elytra aus dem End und ueberreiche sie dem Haeuptling.",
                intro + " Kehrst du aus der End-Dimension zurueck?",
                "Gut. Eine Trophae aus dem End wird unser Dorf schmuecken.",
                null,
                1,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "LEGENDARY_END:" + chief.entityUuid());
    }

    private QuestOffer legendaryNetherOffer(Speaker chief, String intro, int difficultyTier) {
        return new QuestOffer(
                QuestType.LEGENDARY_NETHER,
                "Nether-Beute",
                "Bringe einen Nether-Stern oder Wither-Skelett-Schaedel und ueberreiche ihn dem Haeuptling.",
                intro + " Beugst du dich den Tiefen der Hoelle?",
                "Gut. Holt die wertvollste Beute aus den feurigen Tiefen.",
                null,
                1,
                null,
                chief.world(),
                (int) Math.round(chief.x()),
                (int) Math.round(chief.z()),
                0,
                null,
                difficultyTier,
                "LEGENDARY_NETHER:" + chief.entityUuid());
    }

    private Villager findVillagerBySpeakerUuid(UUID entityUuid) {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getUniqueId().equals(entityUuid)) {
                    return villager;
                }
            }
        }
        return null;
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
        List<OfferTemplate> configuredRetinueTemplates = new ArrayList<>();
        List<OfferTemplate> configuredLegendaryTemplates = new ArrayList<>();
        if (templatesSection == null) {
            logger.warning("quest-offers.yml fehlt oder enthaelt keine 'offer-templates'-Sektion. Nutze Notfall-Default fuer Questangebote.");
            this.retinueTemplates = configuredRetinueTemplates;
            this.legendaryTemplates = configuredLegendaryTemplates;
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
            } else if ("RETINUE".equals(normalizedKey)) {
                configuredRetinueTemplates = templates;
            } else if ("LEGENDARY".equals(normalizedKey)) {
                configuredLegendaryTemplates = templates;
            } else {
                templatesByProfession.put(normalizedKey, templates);
            }
        }

        this.retinueTemplates = configuredRetinueTemplates;
        this.legendaryTemplates = configuredLegendaryTemplates;

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
            case VISIT, EXPLORE -> {
                int distance = intValue(rawEntry.get("distance"), 96);
                int radius = intValue(rawEntry.get("radius"), 5);
                if (distance <= 0 || radius <= 0) {
                    logger.warning(type.name() + "-Template in '" + scopeName + "' ignoriert: distance und radius muessen > 0 sein.");
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
            case SECURE -> {
                Material material = Material.matchMaterial(stringValue(rawEntry.get("material")));
                int amount = intValue(rawEntry.get("amount"), 1);
                int distance = intValue(rawEntry.get("distance"), 30);
                int radius = intValue(rawEntry.get("radius"), 5);
                String mode = stringValue(rawEntry.get("mode"));
                if (material == null || material.isAir() || !material.isBlock() || amount <= 0 || distance <= 0 || radius <= 0) {
                    logger.warning("SECURE-Template in '" + scopeName + "' ignoriert: gueltiges Block-Material, amount > 0, distance > 0 und radius > 0 sind Pflicht.");
                    yield null;
                }
                yield new OfferTemplate(type, intro, material, null, amount, distance, radius, null, Math.max(0, intValue(rawEntry.get("difficulty-tier"), 0)), mode, 0, null);
            }
            case RETINUE_GUARD, RETINUE_GOLEM, RETINUE_WALL, RETINUE_BELL -> {
                String mode = stringValue(rawEntry.get("mode"));
                String label = stringValue(rawEntry.get("label"));
                int amount = intValue(rawEntry.get("amount"), 1);
                int guardMinutes = intValue(rawEntry.get("guard-minutes"), 10);
                int cooldownMinutes = intValue(rawEntry.get("cooldown-minutes"), 2880);
                int difficultyTier = Math.max(0, intValue(rawEntry.get("difficulty-tier"), 5));
                yield new OfferTemplate(type, intro, null, null, type == QuestType.RETINUE_GUARD ? guardMinutes : amount, 0, 0, null, difficultyTier, mode, cooldownMinutes, label);
            }
            case LEGENDARY_DRAGON, LEGENDARY_BLAZE, LEGENDARY_END, LEGENDARY_NETHER -> {
                int amount = intValue(rawEntry.get("amount"), 1);
                int cooldownMinutes = intValue(rawEntry.get("cooldown-minutes"), 140);
                int difficultyTier = Math.max(0, intValue(rawEntry.get("difficulty-tier"), 5));
                yield new OfferTemplate(type, intro, null, null, amount, 0, 0, null, difficultyTier, null, cooldownMinutes, null);
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
            int difficultyTier,
            String mode,
            int cooldownMinutes,
            String label) {

        private OfferTemplate(QuestType type, String intro, Material material, EntityType entityType,
                int amount, int distance, int radius, PotionType potionType, int difficultyTier) {
            this(type, intro, material, entityType, amount, distance, radius, potionType, difficultyTier, null, 0, null);
        }

        private QuestOffer toOffer(Speaker chief, QuestOfferService service) {
            return switch (type) {
                case BREW -> service.brewOffer(potionType, amount, intro, chief, difficultyTier);
                case FETCH -> service.fetchOffer(material, amount, intro, chief, difficultyTier);
                case REPAIR -> service.repairOffer(material, amount, intro, chief, difficultyTier);
                case BUILD -> service.buildOffer(material, amount, intro, chief, difficultyTier);
                case BREED -> service.breedOffer(entityType, amount, intro, chief, difficultyTier);
                case KILL -> service.killOffer(entityType, amount, intro, chief, difficultyTier);
                case VISIT -> service.visitOffer(chief, distance, radius, difficultyTier);
                case EXPLORE -> service.exploreOffer(chief, distance, radius, difficultyTier);
                case SECURE -> {
                    if ("village-light".equals(mode)) {
                        yield service.villageLightSecureOffer(chief, material, intro, difficultyTier);
                    }
                    yield service.secureOffer(chief, material, amount, distance, radius, intro, difficultyTier);
                }
                case RETINUE_GUARD -> service.retinueGuardOffer(chief, amount, intro, difficultyTier, label);
                case RETINUE_GOLEM -> service.retinueGolemOffer(chief, intro, difficultyTier, label);
                case RETINUE_WALL -> service.retinueWallOffer(chief, amount, intro, difficultyTier, label);
                case RETINUE_BELL -> service.retinueBellOffer(chief, intro, difficultyTier, label);
                case LEGENDARY_DRAGON -> service.legendaryDragonOffer(chief, intro, difficultyTier);
                case LEGENDARY_BLAZE -> service.legendaryBlazeOffer(chief, intro, difficultyTier);
                case LEGENDARY_END -> service.legendaryEndOffer(chief, intro, difficultyTier);
                case LEGENDARY_NETHER -> service.legendaryNetherOffer(chief, intro, difficultyTier);
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
            int difficultyTier,
            String targetKey) {
    }
}