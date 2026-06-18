package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.service.MourningService;
import de.ajsch.villagerai.storage.ChiefRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public final class ChiefAutoAssignmentService implements Listener {

    private final ChiefService chiefService;
    private final ChiefRepository chiefRepository;
    private final VillageIdentityService villageIdentityService;
    private final MourningService mourningService;
    private final ChiefMeetingObserver chiefMeetingObserver;
    private final Logger logger;

    /**
     * In-Memory-Guard: Verhindert, dass innerhalb einer Session dieselbe
     * villageId mehrfach durch assignChiefIfMissing prozessiert wird.
     * Wird bei initialScan befüllt und bei jedem assignChiefIfMissing-Erfolg erweitert.
     */
    private final Set<String> assignedThisSession = ConcurrentHashMap.newKeySet();

    public ChiefAutoAssignmentService(
            ChiefService chiefService,
            ChiefRepository chiefRepository,
            VillageIdentityService villageIdentityService,
            MourningService mourningService,
            ChiefMeetingObserver chiefMeetingObserver,
            Logger logger) {
        this.chiefService = chiefService;
        this.chiefRepository = chiefRepository;
        this.villageIdentityService = villageIdentityService;
        this.mourningService = mourningService;
        this.chiefMeetingObserver = chiefMeetingObserver;
        this.logger = logger;
    }

    /**
     * Scannt alle geladenen Villager in allen Welten und weist Chiefs zu,
     * wo noch keiner existiert. Wird einmalig bei Server-Start aufgerufen.
     */
    public void initialScan() {
        int assigned = 0;
        Set<String> assignedVillageIds = new HashSet<>();

        // Pre-load all persisted active chiefs so we don't re-assign for
        // villages whose chief's chunk is currently unloaded.
        chiefRepository.findAll().stream()
                .filter(ChiefAttributes::isActive)
                .map(ChiefAttributes::villageId)
                .forEach(assignedVillageIds::add);
        logger.info("ChiefAutoAssignment: pre-loaded " + assignedVillageIds.size()
                + " persisted active chief village(s)");

        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                String vid = resolveVillageIdQuietly(villager);
                if (vid == null || assignedVillageIds.contains(vid)) {
                    continue;
                }
                try {
                    if (assignChiefIfMissing(villager)) {
                        assignedVillageIds.add(vid);
                        assigned++;
                    }
                } catch (Exception e) {
                    logger.warning("ChiefAutoAssignment: initialScan failed for villager "
                            + villager.getUniqueId() + ": " + e.getMessage());
                }
            }
        }
        logger.info("ChiefAutoAssignment: initialScan assigned " + assigned + " new chief(s)");
    }

    /**
     * Bei jedem Chunk-Load prüfen: Gibt es Villager im Chunk ohne Chief?
     * Wenn ja, weise einen Chief für das Dorf zu.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() && event.getChunk().getEntities().length == 0) {
            return;
        }

        // Sammle alle eindeutigen villageIds im Chunk, um pro Dorf nur
        // EINMAL assignChiefIfMissing aufzurufen (statt pro Villager).
        Set<String> villageIdsInChunk = new HashSet<>();
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            String vid = resolveVillageIdQuietly(villager);
            if (vid != null) {
                villageIdsInChunk.add(vid);
            }
        }

        for (String villageId : villageIdsInChunk) {
            // Einen beliebigen Villager dieser villageId im Chunk finden
            Villager representative = null;
            for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
                if (!(entity instanceof Villager villager)) {
                    continue;
                }
                String vid = resolveVillageIdQuietly(villager);
                if (villageId.equals(vid)) {
                    representative = villager;
                    break;
                }
            }
            if (representative == null) {
                continue;
            }

            try {
                assignChiefIfMissing(representative);
            } catch (Exception e) {
                logger.warning("ChiefAutoAssignment: onChunkLoad failed for village "
                        + villageId + " in chunk " + event.getChunk().getX()
                        + "/" + event.getChunk().getZ() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Prüft für das Dorf des gegebenen Villagers, ob bereits ein Chief existiert.
     * Falls nicht, wird der Villager mit der niedrigsten Entity-UUID im Dorf
     * zum Chief ernannt.
     *
     * @param villager ein beliebiger Villager des zu prüfenden Dorfes
     * @return true wenn ein neuer Chief zugewiesen wurde
     */
    public boolean assignChiefIfMissing(Villager villager) {
        String villageId;
        try {
            villageId = villageIdentityService.resolveOrRegisterVillageId(villager);
            if (villageId == null) {
                return false;
            }
        } catch (Exception e) {
            logger.fine("ChiefAutoAssignment: cannot resolve villageId for "
                    + villager.getUniqueId() + ": " + e.getMessage());
            return false;
        }

        // 0) In-Memory-Guard: bereits in dieser Session zugewiesen?
        if (assignedThisSession.contains(villageId)) {
            logger.fine("ChiefAutoAssignment: village " + villageId
                    + " already assigned this session, skipping.");
            return false;
        }

        // 1) Village in mourning? No auto-assignment.
        if (mourningService != null && mourningService.isVillageInMourning(villageId)) {
            logger.fine("ChiefAutoAssignment: village " + villageId
                    + " is in mourning, skipping auto-assignment.");
            return false;
        }

        // YAML-only Deduplication: check persisted chiefs (chiefs.yml).
        Optional<ChiefAttributes> persistedAttrs = chiefRepository.findActiveByVillageId(villageId);
        if (persistedAttrs.isPresent()) {
            logger.fine("ChiefAutoAssignment: village " + villageId
                    + " already has persisted chief " + persistedAttrs.get().speakerId());
            return false;
        }

        // Kein Chief gefunden -> niedrigste UUID wählen
        Optional<Villager> candidate = villager.getWorld()
                .getEntitiesByClass(Villager.class)
                .stream()
                .filter(v -> villageId.equals(resolveVillageIdQuietly(v)))
                .min(Comparator.comparing(v -> v.getUniqueId().toString()));

        if (candidate.isEmpty()) {
            return false;
        }

        Villager chosen = candidate.get();
        Speaker speaker = chiefService.markChief(chosen, villageId);
        assignedThisSession.add(villageId);
        logger.info("ChiefAutoAssignment: auto-assigned chief " + speaker.speakerId()
                + " (" + speaker.displayName() + ") to village " + villageId
                + " (villager " + chosen.getUniqueId() + ")");

        // Coronation observer (nur bei natürlicher Auto-Assignment)
        ChiefMeetingObserver observer = this.chiefMeetingObserver;
        if (observer != null) {
            observer.observeCoronation(speaker);
        }
        return true;
    }

    private String resolveVillageIdQuietly(Villager villager) {
        try {
            return villageIdentityService.resolveOrRegisterVillageId(villager);
        } catch (Exception e) {
            return null;
        }
    }
}