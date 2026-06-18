package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.BiomeFamily;
import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.ChiefVisualTier;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.SpeakerStatus;
import de.ajsch.villagerai.model.VillageIdentity;
import de.ajsch.villagerai.service.ReputationService;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.util.Keys;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Verwaltet ausschliesslich Chief-Aktionen: Krönung, Abdankung, Tod/Trauer.
 * Speaker-Status-Mutationen werden an {@link SpeakerService} delegiert.
 * Profile/Namen/Conversation-Profile sind NICHT mehr Teil dieser Klasse.
 */
public final class ChiefService {

        private final Keys keys;
    private final ChiefRepository chiefRepository;
    private final VillageIdentityService villageIdentityService;
    private final ChiefVisualService chiefVisualService;
    private final SpeakerService speakerService;
    private final MourningService mourningService;
    private final ReputationService reputationService;
    private final Logger logger;

    public ChiefService(
            Keys keys,
            ChiefRepository chiefRepository,
            VillageIdentityService villageIdentityService,
            ChiefVisualService chiefVisualService,
            SpeakerService speakerService,
            MourningService mourningService,
            ReputationService reputationService,
            Logger logger) {
        this.keys = keys;
        this.chiefRepository = chiefRepository;
        this.villageIdentityService = villageIdentityService;
        this.chiefVisualService = chiefVisualService;
        this.speakerService = speakerService;
        this.mourningService = mourningService;
        this.reputationService = reputationService;
        this.logger = logger;
    }

    // ──────────────────────────────────────────────
    //  markChief
    // ──────────────────────────────────────────────

    public Speaker markChief(Villager villager) {
            return markChief(villager, villageIdentityService.resolve(villager).villageId(), false);
        }

        public Speaker markChief(Villager villager, String villageId) {
            return markChief(villager, villageId, false);
        }

                public Speaker markChief(Villager villager, String villageId, boolean silent) {
        // Guard: existiert bereits ein ANDERER aktiver Chief für dieses Dorf?
        // Wenn ja, diesen zuerst deaktivieren, bevor wir einen neuen krönen.
        deactivateExistingChiefForVillage(villageId, villager.getUniqueId());

        // Guard: ist DIESER Villager selbst bereits aktiver Chief?
        Optional<Speaker> existingSpeaker = speakerService.getSpeaker(villager);
        if (existingSpeaker.isPresent() && existingSpeaker.get().speakerStatus() == SpeakerStatus.AKTIV_CHIEF) {
            // Bereits in chiefs.yml gespeicherten Eintrag restaurieren
            Optional<ChiefAttributes> existingAttrs = chiefRepository.findByEntityUuid(villager.getUniqueId());
            if (existingAttrs.isPresent()) {
                            logger.info("markChief: villager " + villager.getUniqueId() + " ist bereits Chief – stelle Visuals wieder her");
                            ChiefAttributes attrs = existingAttrs.get();
                            chiefVisualService.spawnBanner(attrs, villager);
                            chiefVisualService.startCrownParticles(villager);
                            return existingSpeaker.get();
                        }
        }

        String speakerId = "chief-" + UUID.randomUUID().toString().substring(0, 8);
        VillageIdentity identity = villageIdentityService.resolve(villager);

        // PDC-Flags
        PersistentDataContainer container = villager.getPersistentDataContainer();
        container.set(keys.chiefFlagKey(), PersistentDataType.BYTE, (byte) 1);
        container.set(keys.chiefIdKey(), PersistentDataType.STRING, speakerId);
        container.set(keys.villageIdKey(), PersistentDataType.STRING, villageId);

        // Speaker-Status via SpeakerService
        Speaker speaker = speakerService.promoteToChief(villager);

                // ChiefAttributes persistieren
        long now = System.currentTimeMillis();
        BiomeFamily family = BiomeFamily.fromBiomeName(identity.villageBiome());
        ChiefAttributes attrs = new ChiefAttributes(
                villager.getUniqueId(),
                speakerId,
                villageId,
                now,   // crownedAt
                0L,    // mournedAt
                true,  // isActive
                null,  // visualTier (wird später von ReputationService gesetzt)
                family.name(),  // biomeStyle
                String.valueOf(villageId.hashCode()), // bannerPattern
                false, // legendaryUnlocked
                0L);   // legendaryLastActivated

        chiefRepository.save(attrs);

                // Visuals und Broadcasts
                chiefVisualService.spawnBanner(attrs, villager);
                chiefVisualService.startCrownParticles(villager);

                if (!silent) {
                    broadcastChiefCoronation(attrs, speaker);
                }
                                logger.info("Chief " + speakerId + " gekrönt in Dorf " + villageId + " (Entity " + villager.getUniqueId() + ")");
                return speaker;
    }

    /**
     * Deaktiviert einen bereits existierenden aktiven Chief für dieselbe villageId,
     * falls es sich um einen ANDEREN Villager handelt. Verhindert doppelte Chiefs pro Dorf.
     */
    private void deactivateExistingChiefForVillage(String villageId, UUID newChiefEntityUuid) {
        Optional<ChiefAttributes> existingAttrsOpt = chiefRepository.findActiveByVillageId(villageId);
        if (existingAttrsOpt.isEmpty()) {
            return;
        }

        ChiefAttributes existingAttrs = existingAttrsOpt.get();
        if (existingAttrs.entityUuid().equals(newChiefEntityUuid)) {
            return;
        }

        logger.warning("markChief: village " + villageId
                + " hat bereits Chief " + existingAttrs.speakerId()
                + " (entity " + existingAttrs.entityUuid()
                + "), wird vor neuer Krönung deaktiviert.");

        ChiefAttributes mournedAttrs = new ChiefAttributes(
                existingAttrs.entityUuid(),
                existingAttrs.speakerId(),
                existingAttrs.villageId(),
                existingAttrs.crownedAt(),
                System.currentTimeMillis(),
                false,
                existingAttrs.visualTier(),
                existingAttrs.biomeStyle(),
                existingAttrs.bannerPattern(),
                existingAttrs.legendaryUnlocked(),
                existingAttrs.legendaryLastActivated());
        chiefRepository.save(mournedAttrs);

        org.bukkit.entity.Entity oldEntity = Bukkit.getEntity(existingAttrs.entityUuid());
        if (oldEntity instanceof Villager oldVillager) {
            Optional<Speaker> oldSpeaker = speakerService.getSpeaker(oldVillager);
            oldSpeaker.ifPresent(s -> dropHeirloomBanner(oldVillager, existingAttrs, s));
            chiefVisualService.stopCrownParticles(oldVillager.getUniqueId());
            chiefVisualService.removeBanner(oldVillager.getUniqueId());
            speakerService.demoteFromChief(oldVillager);
            PersistentDataContainer oldContainer = oldVillager.getPersistentDataContainer();
            oldContainer.remove(keys.chiefFlagKey());
            oldContainer.remove(keys.chiefIdKey());
            oldContainer.remove(keys.villageIdKey());
            logger.info("markChief: alter Chief " + existingAttrs.speakerId()
                    + " (entity " + existingAttrs.entityUuid()
                    + ") deaktiviert, Heirloom-Banner gedroppt.");
        } else {
            logger.warning("markChief: alter Chief-Entity " + existingAttrs.entityUuid()
                    + " nicht als Villager gefunden (vielleicht in ungeladenem Chunk), "
                    + "nur ChiefAttributes deaktiviert.");
        }
    }

    // ──────────────────────────────────────────────
    //  unmarkChief
    // ──────────────────────────────────────────────

    public boolean unmarkChief(Villager villager) {
        Optional<Speaker> speakerOpt = speakerService.getSpeaker(villager);
        if (speakerOpt.isEmpty() || speakerOpt.get().speakerStatus() != SpeakerStatus.AKTIV_CHIEF) {
            return false;
        }

        Speaker speaker = speakerOpt.get();
        Optional<ChiefAttributes> attrsOpt = chiefRepository.findByEntityUuid(villager.getUniqueId());
        if (attrsOpt.isEmpty()) {
            return false;
        }

        ChiefAttributes oldAttrs = attrsOpt.get();

        // PDC-Flags entfernen
        PersistentDataContainer container = villager.getPersistentDataContainer();
        container.remove(keys.chiefFlagKey());
        container.remove(keys.chiefIdKey());
        container.remove(keys.villageIdKey());

        // Heirloom-Banner droppen
                dropHeirloomBanner(villager, oldAttrs, speaker);

        // Crown-Partikel stoppen
        chiefVisualService.stopCrownParticles(villager.getUniqueId());

        // ChiefAttributes als inaktiv persistieren
        ChiefAttributes mournedAttrs = new ChiefAttributes(
                oldAttrs.entityUuid(),
                oldAttrs.speakerId(),
                oldAttrs.villageId(),
                oldAttrs.crownedAt(),
                System.currentTimeMillis(),
                false, // isActive
                oldAttrs.visualTier(),
                oldAttrs.biomeStyle(),
                oldAttrs.bannerPattern(),
                oldAttrs.legendaryUnlocked(),
                oldAttrs.legendaryLastActivated());
        chiefRepository.save(mournedAttrs);

        // SpeakerStatus zurücksetzen
        speakerService.demoteFromChief(villager);

        chiefVisualService.removeBanner(villager.getUniqueId());
        logger.info("Chief " + oldAttrs.speakerId() + " (" + speaker.displayName()
                + ") abgesetzt. Heirloom-Banner gedroppt.");
        return true;
    }

    // ──────────────────────────────────────────────
    //  mournChief  (atomar: Tod/Trauer)
    // ──────────────────────────────────────────────

    /**
     * Atomare Trauer-Aktion: Setzt ChiefAttributes.isActive=false und
     * Speaker.speakerStatus=GEWESENER_CHIEF, speichert beide und sendet
     * einen Todes-Broadcast.
     */
    public void mournChief(Villager villager, String killerName) {
        Optional<Speaker> speakerOpt = speakerService.getSpeaker(villager);
        Optional<ChiefAttributes> attrsOpt = chiefRepository.findByEntityUuid(villager.getUniqueId());

        if (speakerOpt.isEmpty() || attrsOpt.isEmpty()) {
            logger.warning("mournChief: kein Speaker/ChiefAttributes fuer Villager " + villager.getUniqueId());
            return;
        }

        Speaker speaker = speakerOpt.get();
        ChiefAttributes attrs = attrsOpt.get();

        // ChiefAttributes als inaktiv + mournedAt setzen
        ChiefAttributes mournedAttrs = new ChiefAttributes(
                attrs.entityUuid(),
                attrs.speakerId(),
                attrs.villageId(),
                attrs.crownedAt(),
                System.currentTimeMillis(),
                false, // isActive
                attrs.visualTier(),
                attrs.biomeStyle(),
                attrs.bannerPattern(),
                attrs.legendaryUnlocked(),
                attrs.legendaryLastActivated());
        chiefRepository.save(mournedAttrs);

        // SpeakerStatus auf GEWESENER_CHIEF setzen
        speakerService.markAsFormerChief(villager);

        // Broadcast
        broadcastChiefDeath(attrs, speaker, killerName);

        logger.info("mournChief: " + attrs.speakerId() + " (" + speaker.displayName()
                + ") aus Dorf " + attrs.villageId() + " als gefallen markiert"
                + (killerName != null ? ", getötet von " + killerName : ""));
    }

    // ──────────────────────────────────────────────
    //  Broadcasts
    // ──────────────────────────────────────────────

    public void broadcastChiefDeath(ChiefAttributes attrs, Speaker speaker, String killerName) {
        net.kyori.adventure.text.Component deathMessage = net.kyori.adventure.text.Component.text(
                "Der Häuptling " + speaker.displayName() + " von " + speaker.villageName() + " ist gefallen...",
                net.kyori.adventure.text.format.NamedTextColor.RED);
        if (killerName != null && !killerName.isBlank()) {
            deathMessage = deathMessage.hoverEvent(
                    net.kyori.adventure.text.Component.text(
                            "Dorf " + speaker.villageName() + ", getötet von " + killerName,
                            net.kyori.adventure.text.format.NamedTextColor.GRAY));
        } else {
            deathMessage = deathMessage.hoverEvent(
                    net.kyori.adventure.text.Component.text(
                            "Dorf " + speaker.villageName(),
                            net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
        Bukkit.broadcast(deathMessage);
        logger.info("Chat-Broadcast: Chief-Tod für " + attrs.speakerId() + " (" + speaker.displayName() + ")");
    }

    public void broadcastChiefCoronation(ChiefAttributes attrs, Speaker speaker) {
        net.kyori.adventure.text.Component coronationMessage = net.kyori.adventure.text.Component.text(
                "Ein neuer Häuptling erhebt sich in " + speaker.villageName() + ": " + speaker.displayName() + "!",
                net.kyori.adventure.text.format.NamedTextColor.GOLD);
        coronationMessage = coronationMessage.hoverEvent(
                net.kyori.adventure.text.Component.text(
                        "Dorf " + speaker.villageName() + " – " + speaker.role(),
                        net.kyori.adventure.text.format.NamedTextColor.GRAY));
        Bukkit.broadcast(coronationMessage);
        logger.info("Chat-Broadcast: Krönung für " + attrs.speakerId() + " (" + speaker.displayName() + ")");
    }

    // ──────────────────────────────────────────────
    //  Delegation / Lookup
    // ──────────────────────────────────────────────

    public Optional<ChiefAttributes> findChiefByVillageId(String villageId) {
        return speakerService.findActiveChiefByVillageId(villageId)
                .flatMap(speaker -> chiefRepository.findByEntityUuid(speaker.entityUuid()));
    }

    public boolean isVillageInMourning(String villageId) {
        return mourningService.isVillageInMourning(villageId);
    }

    // ──────────────────────────────────────────────
    //  Heirloom Banner
    // ──────────────────────────────────────────────

    private void dropHeirloomBanner(Villager villager, ChiefAttributes attrs, Speaker speaker) {
            org.bukkit.Material bannerMaterial = org.bukkit.Material.WHITE_BANNER;
            org.bukkit.inventory.ItemStack banner = new org.bukkit.inventory.ItemStack(bannerMaterial);
            org.bukkit.inventory.meta.BannerMeta meta = (org.bukkit.inventory.meta.BannerMeta) banner.getItemMeta();
            if (meta != null) {
                                String seed = attrs.bannerPattern() != null
                        ? attrs.bannerPattern()
                        : String.valueOf(attrs.villageId().hashCode());
                ChiefVisualTier tier = ChiefVisualService.resolveTier(attrs.visualTier());
                for (org.bukkit.block.banner.Pattern pattern : ChiefVisualService.buildBannerPatterns(seed, tier)) {
                    meta.addPattern(pattern);
                }
                meta.displayName(net.kyori.adventure.text.Component.text(
                        speaker.displayName() + "'s Wappen",
                        net.kyori.adventure.text.format.NamedTextColor.GOLD));
                String tierText = attrs.visualTier() != null && !attrs.visualTier().isBlank()
                        ? "Rang: " + attrs.visualTier()
                        : "Rang: Häuptling";
                java.time.LocalDate deathDate = java.time.LocalDate.now();
                meta.lore(java.util.List.of(
                        net.kyori.adventure.text.Component.text(
                                "Häuptling von " + speaker.villageName(),
                                net.kyori.adventure.text.format.NamedTextColor.WHITE),
                        net.kyori.adventure.text.Component.text(
                                tierText,
                                net.kyori.adventure.text.format.NamedTextColor.GRAY),
                        net.kyori.adventure.text.Component.text(
                                "Gefallen am " + deathDate.toString(),
                                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)));
                banner.setItemMeta(meta);
            }

            org.bukkit.entity.Item dropped = villager.getWorld().dropItem(
                    villager.getLocation().add(0, 0.5, 0), banner);
            if (dropped != null) {
                dropped.setWillAge(false);
                dropped.setUnlimitedLifetime(true);
                logger.fine("Heirloom-Banner gedroppt für Chief " + attrs.speakerId()
                        + " an " + villager.getLocation());
        }
    }

    // ──────────────────────────────────────────────
    //  VisualTier
    // ──────────────────────────────────────────────

    /**
     * Ermittelt den aktuellen {@link ChiefVisualTier} aus dem kombinierten
     * Ruf (Dorf + Speaker) und speichert den Wert im {@link ChiefAttributes}-
     * Record.  Wird von {@link ChiefVisualService} und der Mod-Render-Schicht
     * verwendet, nicht direkt für das Banner.
     */
    public Optional<ChiefAttributes> refreshVisualTier(UUID playerUuid, UUID chiefEntityUuid) {
        Optional<ChiefAttributes> attrsOpt = chiefRepository.findByEntityUuid(chiefEntityUuid);
        if (attrsOpt.isEmpty()) {
                logger.fine("refreshVisualTier: keine ChiefAttributes für " + chiefEntityUuid);
                return Optional.empty();
        }
        ChiefAttributes oldAttrs = attrsOpt.get();
        if (!oldAttrs.isActive()) {
                return Optional.of(oldAttrs);
        }

        int combinedScore = reputationService.getCombinedScore(
                    playerUuid, oldAttrs.villageId(), oldAttrs.speakerId());
        ChiefVisualTier tier = ChiefVisualTier.fromReputation(
                    combinedScore, oldAttrs.legendaryUnlocked());

        if (tier.name().equals(oldAttrs.visualTier())) {
                return Optional.of(oldAttrs); // kein Update nötig
        }

        ChiefAttributes updated = new ChiefAttributes(
                    oldAttrs.entityUuid(),
                    oldAttrs.speakerId(),
                    oldAttrs.villageId(),
                    oldAttrs.crownedAt(),
                    oldAttrs.mournedAt(),
                    oldAttrs.isActive(),
                    tier.name(), // visualTier als Enum-Name-String
                    oldAttrs.biomeStyle(),
                    oldAttrs.bannerPattern(),
                    oldAttrs.legendaryUnlocked(),
                    oldAttrs.legendaryLastActivated());
        chiefRepository.save(updated);

        logger.info("VisualTier updated: " + oldAttrs.speakerId()
                    + " von " + oldAttrs.visualTier() + " auf " + tier.name()
                    + " (combinedScore=" + combinedScore + ")");
        return Optional.of(updated);
    }

    
}