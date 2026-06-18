package de.ajsch.villagerai.service;

import de.ajsch.villagerai.event.ReputationChangedEvent;
import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.ReputationScope;
import de.ajsch.villagerai.service.ChiefVisualService;
import de.ajsch.villagerai.storage.ChiefRepository;
import java.util.Optional;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Prüft, ob die LEGENDARY-Stufe für einen Chief freigeschaltet werden kann.
 *
 * Bedingungen:
 *  1. Dorf-Ruf ≥ 100
 *  2. Villager-Ruf ≥ 100
 *  3. Enderdrache wurde mindestens einmal getötet
 *
 * Freischaltung ist pro Spieler/Chief permanent (nicht revertierbar).
 */
public class LegendaryUnlockService implements Listener {

    private final ChiefVisualService chiefVisualService;
    private final ChiefService chiefService;
    private final ChiefRepository chiefRepository;
    private final ReputationService reputationService;
    private final Logger logger;

    public LegendaryUnlockService(
            @NotNull ChiefVisualService chiefVisualService,
            @NotNull ChiefService chiefService,
            @NotNull ChiefRepository chiefRepository,
            @NotNull ReputationService reputationService,
            @NotNull Logger logger) {
        this.chiefVisualService = chiefVisualService;
        this.chiefService = chiefService;
        this.chiefRepository = chiefRepository;
        this.reputationService = reputationService;
        this.logger = logger;
    }

    @EventHandler
    public void onReputationChanged(ReputationChangedEvent event) {
        // Nur Speaker-Ruf-Änderungen triggern die Prüfung
        if (event.getScope() != ReputationScope.SPEAKER) {
            return;
        }
        // Früher Exit: Ruf muss mindestens 100 sein, damit sich die Prüfung lohnt
        if (event.getNewReputation() < 100) {
            return;
        }
        checkUnlock(event.getPlayer(), event.getVillageId(), event.getSpeakerId());
    }

    /**
     * Prüft alle drei Bedingungen und schaltet bei Erfolg die Legendary-Stufe frei.
     */
    public void checkUnlock(@NotNull Player player, @NotNull String villageId, @NotNull String speakerId) {
        int villageRep = reputationService.getVillageScore(player.getUniqueId(), villageId);
        int speakerRep = reputationService.getSpeakerScore(player.getUniqueId(), speakerId);

        if (villageRep < 100 || speakerRep < 100) {
            logger.fine("LegendaryUnlock: Ruf zu niedrig für " + speakerId
                    + " (village=" + villageRep + ", speaker=" + speakerRep + ")");
            return;
        }

        if (!checkWorldProgress(player)) {
            logger.info("LegendaryUnlock: Welt-Fortschritt fehlt für Spieler " + player.getName()
                    + " (speakerId=" + speakerId + ")");
            return;
        }

        unlockLegendary(villageId, speakerId);
    }

    /**
     * Prüft, ob der Spieler den Enderdrachen mindestens einmal getötet hat.
     */
    public boolean checkWorldProgress(@NotNull Player player) {
        try {
            int dragonKills = player.getStatistic(Statistic.KILL_ENTITY, EntityType.ENDER_DRAGON);
            return dragonKills > 0;
        } catch (Exception e) {
            logger.warning("LegendaryUnlock: Konnte Statistic.KILL_ENTITY(ENDER_DRAGON) nicht abrufen: "
                    + e.getMessage() + " – erlaube Freischaltung als Fallback");
            return true; // Fallback bei API-Problemen
        }
    }

    /**
     * Setzt {@code legendaryUnlocked = true} für den aktiven Chief des Dorfes.
     */
    public void unlockLegendary(@NotNull String villageId, @NotNull String speakerId) {
        Optional<ChiefAttributes> attrsOpt = chiefRepository.findActiveByVillageId(villageId);
        if (attrsOpt.isEmpty()) {
            logger.warning("LegendaryUnlock: Kein aktiver Chief in Dorf " + villageId + " gefunden");
            return;
        }

        ChiefAttributes attrs = attrsOpt.get();
        if (attrs.legendaryUnlocked()) {
            logger.fine("LegendaryUnlock: Bereits freigeschaltet für " + speakerId);
            return;
        }

        long now = System.currentTimeMillis();
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
                true,  // legendaryUnlocked
                now);  // legendaryLastActivated
        chiefRepository.save(updated);

        // Broadcast an alle Spieler
        Component broadcast = Component.text(
                "Der Häuptling " + speakerId + " von " + villageId + " ist zur Legende aufgestiegen!",
                NamedTextColor.GOLD);
        Bukkit.broadcast(broadcast);

        // Legendary-Partikel starten, falls der Villager lebt
        Entity entity = Bukkit.getEntity(updated.entityUuid());
        if (entity instanceof Villager villager && villager.isValid() && !villager.isDead()) {
            chiefVisualService.startLegendaryParticles(villager);
        }

        logger.info("LegendaryUnlock: Chief " + speakerId + " in Dorf " + villageId
                + " zur Legende aufgestiegen (legendaryLastActivated=" + now + ")");
    }
}