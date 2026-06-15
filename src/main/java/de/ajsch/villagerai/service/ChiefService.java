package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.SpeakerStatus;
import de.ajsch.villagerai.model.VillageIdentity;
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
    private final Logger logger;

    public ChiefService(
            Keys keys,
            ChiefRepository chiefRepository,
            VillageIdentityService villageIdentityService,
            ChiefVisualService chiefVisualService,
            SpeakerService speakerService,
            MourningService mourningService,
            Logger logger) {
        this.keys = keys;
        this.chiefRepository = chiefRepository;
        this.villageIdentityService = villageIdentityService;
        this.chiefVisualService = chiefVisualService;
        this.speakerService = speakerService;
        this.mourningService = mourningService;
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
        // Guard: bereits aktiver Chief
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
        ChiefAttributes attrs = new ChiefAttributes(
                villager.getUniqueId(),
                speakerId,
                villageId,
                now,   // crownedAt
                0L,    // mournedAt
                true,  // isActive
                null,  // visualTier (wird später von ReputationService gesetzt)
                null,  // biomeStyle
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
                for (org.bukkit.block.banner.Pattern pattern : ChiefVisualService.buildBannerPatterns(seed)) {
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

    
}