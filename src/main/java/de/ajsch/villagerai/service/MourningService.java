package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.ChiefAttributes;
import de.ajsch.villagerai.model.Speaker;
import de.ajsch.villagerai.model.VillagePerimeter;
import de.ajsch.villagerai.storage.ChiefRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class MourningService {

    private static final long MOURNING_DURATION_MILLIS = 60L * 60L * 1000L; // 60 Minuten = 3 Ingame-Tage

    private final JavaPlugin plugin;
    private ChiefService chiefService;
    private final ReputationService reputationService;
    private final VillageIdentityService villageIdentityService;
    private final VillagePerimeterService villagePerimeterService;
    private final ChiefRepository chiefRepository;
    private final Logger logger;
    private volatile ChiefMeetingObserver chiefMeetingObserver;

    // Config-Felder (per reloadConfig() aktualisierbar)
    private boolean debugParticles;
    private boolean particlesEnabled;
    private Particle particleType;
    private String dustColor;
    private float dustSize;
    private int countPerTick;
    private int intervalMinTicks;
    private int intervalMaxTicks;
    private boolean dayOnly;

    /**
     * Lädt alle mourning.* Config-Werte neu und startet/stoppt Partikel-Tasks.
     * Wird bei Plugin-Start und bei /chief reload aufgerufen.
     */
    public void reloadConfig() {
        var config = plugin.getConfig();
        debugParticles = config.getBoolean("mourning.debug-particles", false);
        particlesEnabled = config.getBoolean("mourning.particles.enabled", true);

        String particleName = config.getString("mourning.particles.particle", "FLAME");
        try {
            particleType = Particle.valueOf(particleName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("mourning.particles.particle: ungültiger Partikel-Typ '" + particleName + "', verwende FLAME");
            particleType = Particle.FLAME;
        }

        dustColor = config.getString("mourning.particles.dust-color", "30,30,35");
        dustSize = (float) config.getDouble("mourning.particles.dust-size", 1.8);
        countPerTick = config.getInt("mourning.particles.count-per-tick", 50);
        intervalMinTicks = config.getInt("mourning.particles.interval-min-ticks", 10);
        intervalMaxTicks = config.getInt("mourning.particles.interval-max-ticks", 20);
        dayOnly = config.getBoolean("mourning.particles.day-only", true);

        if (!particlesEnabled) {
            // Alle laufenden Partikel-Tasks stoppen
            var villageIds = new java.util.ArrayList<>(particleTaskIds.keySet());
            for (String villageId : villageIds) {
                stopMourningParticles(villageId);
            }
            logger.info("mourning.reloadConfig: particles disabled, all tasks stopped");
            return;
        }

        // Wenn Partikel enabled und es laufende Tasks gibt: neustarten mit neuen Werten
        var villageIds = new java.util.ArrayList<>(particleTaskIds.keySet());
        for (String villageId : villageIds) {
            stopMourningParticles(villageId);
        }
        // Für alle aktuell trauernden Dörfer Partikel neu starten
        for (String villageId : activeMourning.keySet()) {
            startMourningParticles(villageId);
        }

        logger.info("mourning.reloadConfig: debug=" + debugParticles
                + " enabled=" + particlesEnabled + " particle=" + particleType
                + " countPerTick=" + countPerTick + " interval=" + intervalMinTicks + "-" + intervalMaxTicks
                + " dayOnly=" + dayOnly);
    }

    /** villageId -> mournedUntil-EpochMillis */
    private final Map<String, Long> activeMourning = new HashMap<>();

    /** villageId -> BukkitTask-ID für Trauer-Partikel */
    private final Map<String, Integer> particleTaskIds = new HashMap<>();

    /** villageId -> Tick-Counter für Debug */
    private final Map<String, Integer> particleTickCounters = new HashMap<>();

    /** villageId -> letzter bekannter Perimeter (für Rejoin-Szenario) */
    private final Map<String, VillagePerimeter> particlePerimeters = new HashMap<>();

    /** villageId -> Anzahl Wiederholungsversuche für Nachfolger-Auswahl (max. 3) */
    private final Map<String, Integer> successorRetryCounts = new HashMap<>();

    /** Health-Check-Task-ID: stellt sicher, dass Partikel-Tasks bei Rejoin neu starten */
    private Integer healthCheckTaskId = null;

    public MourningService(
            JavaPlugin plugin,
            ReputationService reputationService,
            VillageIdentityService villageIdentityService,
            VillagePerimeterService villagePerimeterService,
            ChiefRepository chiefRepository,
            Logger logger) {
        this.plugin = plugin;
        this.reputationService = reputationService;
        this.villageIdentityService = villageIdentityService;
        this.villagePerimeterService = villagePerimeterService;
        this.chiefRepository = chiefRepository;
        this.logger = logger;
        reloadConfig();
    }

    public void setChiefService(ChiefService chiefService) {
        this.chiefService = chiefService;
    }

    /**
     * Lädt bestehende Trauer-Einträge aus chiefs.yml und plant
     * deren Abschluss ein. Wird bei Server-Start aufgerufen.
     */
    public void loadAndReschedule() {
        for (ChiefAttributes attrs : chiefRepository.findAll()) {
            if (!attrs.isActive() && attrs.mournedAt() > 0L) {
                long mournedUntil = attrs.mournedAt() + MOURNING_DURATION_MILLIS;
                if (mournedUntil > System.currentTimeMillis()) {
                    long remainingMs = mournedUntil - System.currentTimeMillis();
                    long ticks = Math.max(1L, remainingMs / 50L);
                    String villageId = attrs.villageId();
                    activeMourning.put(villageId, mournedUntil);
                    reputationService.beginVillageMourning(villageId);
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> endMourning(villageId), ticks);
                    startMourningParticles(villageId);
                    long remainingMinutes = remainingMs / 60000L;
                    logger.info("Trauerphase für Dorf " + villageId
                            + " wiederhergestellt, endet in ~" + remainingMinutes + " Min.");
                } else {
                    // Trauerzeit bereits abgelaufen, direkt abschließen
                    String villageId = attrs.villageId();
                    logger.info("Abgelaufene Trauerphase für Dorf " + villageId
                            + " gefunden, führe Nachfolger-Zuweisung durch.");
                    endMourning(villageId);
                }
            }
        }
    }

    /**
     * Startet die Trauerphase für ein Dorf.
     *
     * @param villageId ID des Dorfes
     * @param speakerId Speaker-ID des gefallenen/entfernten Chiefs
     */
    public void beginMourning(String villageId, String speakerId) {
        long now = System.currentTimeMillis();
        long mournedUntil = now + MOURNING_DURATION_MILLIS;
        activeMourning.put(villageId, mournedUntil);

        // Speaker-Reputation löschen
        reputationService.resetSpeakerReputation(speakerId, villageId);

        // Dorf in Trauerzustand versetzen (Ruf temporär 0)
        reputationService.beginVillageMourning(villageId);

        // Scheduler für Beendigung der Trauer
        long ticks = MOURNING_DURATION_MILLIS / 50L;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> endMourning(villageId), ticks);

        // Trauer-Partikel starten
        startMourningParticles(villageId);

        logger.info("Trauerphase gestartet für Dorf " + villageId
                + " (speakerId=" + speakerId + "), Dauer 60 Min.");
    }

    /**
     * Beendet die Trauerphase und weist einen neuen Chief zu.
     */
    public void endMourning(String villageId) {
        if (!activeMourning.containsKey(villageId)) {
            logger.fine("endMourning aufgerufen für nicht-trauerndes Dorf " + villageId);
            return;
        }

        activeMourning.remove(villageId);
        stopMourningParticles(villageId);
        particlePerimeters.remove(villageId);
        reputationService.endVillageMourning(villageId);

        logger.info("Trauerphase beendet für Dorf " + villageId
                + ". Starte Nachfolger-Auswahl.");

        // Nachfolger-Auswahl: lebenden Villager mit niedrigster UUID in diesem Dorf finden
        assignSuccessorChief(villageId);
    }

    /**
     * Findet einen lebenden Villager im angegebenen Dorf (niedrigste Entity-UUID)
     * und ernennt ihn zum neuen Chief.
     */
    private void assignSuccessorChief(String villageId) {
        Optional<Villager> candidate = Optional.empty();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                String vid = resolveVillageIdQuietly(villager);
                if (!villageId.equals(vid)) {
                    continue;
                }
                if (candidate.isEmpty()
                        || villager.getUniqueId().compareTo(candidate.get().getUniqueId()) < 0) {
                    candidate = Optional.of(villager);
                }
            }
        }

        if (candidate.isEmpty()) {
            logger.warning("Kein lebender Villager in Dorf " + villageId
                    + " gefunden. Kein neuer Chief.");
            // Maximal 3 Wiederholungsversuche, dann endgültig abbrechen
            int attempt = successorRetryCounts.getOrDefault(villageId, 0);
            if (attempt < 3) {
                successorRetryCounts.put(villageId, attempt + 1);
                logger.info("Nachfolger-Auswahl für Dorf " + villageId
                        + " fehlgeschlagen (Versuch " + (attempt + 1) + "/3). Wiederhole in 5 Minuten.");
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> retrySuccessorAssignment(villageId), 20L * 60L * 5L);
            } else {
                logger.warning("Nachfolger-Auswahl für Dorf " + villageId
                        + " nach 3 Versuchen endgültig abgebrochen. "
                        + "Dorf bleibt ohne Chief, bis ein Villager geladen wird und ChiefAutoAssignmentService greift.");
                successorRetryCounts.remove(villageId);
            }
            return;
        }

        Villager chosen = candidate.get();
        Speaker speaker = chiefService.markChief(chosen, villageId);
        successorRetryCounts.remove(villageId);
        logger.info("Nachfolger-Chief ernannt: " + speaker.speakerId()
                + " (" + speaker.displayName() + ") für Dorf " + villageId
                + " (Entity-UUID: " + chosen.getUniqueId() + ")");

        // Coronation observer (nur bei natürlicher Nachfolge)
        ChiefMeetingObserver observer = this.chiefMeetingObserver;
        if (observer != null) {
            logger.info("CHiefMeetingObserver registriert, starte observeCoronation für " + speaker.speakerId());
            observer.observeCoronation(speaker);
        } else {
            logger.severe("ChiefMeetingObserver ist NULL – kein Feuerwerk möglich für Dorf " + villageId
                    + ". Wurde setChiefMeetingObserver() aufgerufen?");
        }
    }

    /**
     * Wiederholungsversuch: Nachfolger-Chief ernennen, falls bei endMourning
     * kein Villager geladen war.
     */
    private void retrySuccessorAssignment(String villageId) {
        // Prüfen, ob bereits ein Chief existiert (z. B. durch /chief set)
        Optional<ChiefAttributes> existingAttrs = chiefRepository.findActiveByVillageId(villageId);
        if (existingAttrs.isPresent()) {
            logger.info("Dorf " + villageId + " hat bereits einen Chief, kein Nachfolger nötig.");
            successorRetryCounts.remove(villageId);
            return;
        }

        assignSuccessorChief(villageId);
    }

    /**
     * Prüft, ob ein Dorf sich aktuell in Trauer befindet.
     */
    public void setChiefMeetingObserver(ChiefMeetingObserver observer) {
        this.chiefMeetingObserver = observer;
    }

    public boolean isVillageInMourning(String villageId) {
        return activeMourning.containsKey(villageId);
    }

    /**
     * Bricht eine laufende Trauerphase vorzeitig ab (Admin-Override, z. B.
     * wenn per /chief set ein neuer Chief gesetzt wird).
     */
    public void cancelMourning(String villageId) {
        if (activeMourning.containsKey(villageId)) {
            activeMourning.remove(villageId);
            stopMourningParticles(villageId);
            particlePerimeters.remove(villageId);
            successorRetryCounts.remove(villageId);
            reputationService.endVillageMourning(villageId);
            logger.info("Trauerphase für Dorf " + villageId + " vorzeitig abgebrochen (Admin-Override).");
        }
    }

    // ---- Trauer-Flora (Partikel) ----

    /**
     * Startet dezente dunkle Dust-Partikel, die tagsüber im Dorf-Perimeter
     * schweben, solange die Trauerphase dauert.
     */
    private void startMourningParticles(String villageId) {
        // Bereits laufenden Task nicht doppelt starten
        if (particleTaskIds.containsKey(villageId)) {
            return;
        }

        // Perimeter holen: 1) eigener persistenter Cache (überlebt stopMourningParticles),
        // 2) VillagePerimeterService-Cache, 3) aktiv berechnen
        VillagePerimeter resolved = particlePerimeters.get(villageId);
        if (resolved == null) {
            resolved = villagePerimeterService.getCachedPerimeter(villageId).orElse(null);
        }
        if (resolved == null) {
            resolved = computePerimeterForVillage(villageId);
        }
        if (resolved == null) {
            logger.warning("startMourningParticles: kein Perimeter für " + villageId
                    + ", Partikel werden nicht gestartet.");
            return;
        }
        // Perimeter cachen für spätere Revalidierung
        particlePerimeters.put(villageId, resolved);
        final VillagePerimeter perimeter = resolved;

        World world = Bukkit.getWorld(perimeter.worldName());
        if (world == null) {
            logger.fine("startMourningParticles: Welt '" + perimeter.worldName()
                    + "' nicht geladen, keine Partikel für " + villageId);
            return;
        }

        // DEBUG: Perimeter-Info loggen
        logger.info("[MourningParticles] starte für " + villageId
                + " | world=" + perimeter.worldName()
                + " | X=" + perimeter.minX() + ".." + perimeter.maxX()
                + " | Z=" + perimeter.minZ() + ".." + perimeter.maxZ()
                + " | dayTime=" + world.isDayTime());

        // DEBUG: FLAME für maximale Sichtbarkeit, später auf DUST reduzieren
        BukkitRunnable runnable = new BukkitRunnable() {
            private int tickCount = 0;
            @Override
            public void run() {
                tickCount++;
                // Nur während Trauerphase laufen lassen
                if (!isVillageInMourning(villageId)) {
                    if (debugParticles) logger.info("[MourningParticles] " + villageId + " tick#" + tickCount + " – Trauer beendet, cancel");
                    cancel();
                    particleTaskIds.remove(villageId);
                    particleTickCounters.remove(villageId);
                    return;
                }

                // Nur tagsüber spawnen (wenn dayOnly=true)
                if (dayOnly && !world.isDayTime()) {
                    if (debugParticles && tickCount <= 3) logger.info("[MourningParticles] " + villageId + " tick#" + tickCount + " – Nacht, skipping");
                    return;
                }

                // Zufällige Position innerhalb des Perimeters
                int minX = Math.min(perimeter.minX(), perimeter.maxX());
                int maxX = Math.max(perimeter.minX(), perimeter.maxX());
                int minZ = Math.min(perimeter.minZ(), perimeter.maxZ());
                int maxZ = Math.max(perimeter.minZ(), perimeter.maxZ());

                ThreadLocalRandom rng = ThreadLocalRandom.current();
                // DustOptions nur bei DUST-Partikeln vorbereiten
                org.bukkit.Particle.DustOptions dustOptions = null;
                if (particleType == Particle.DUST) {
                    String[] rgb = dustColor.split(",");
                    int r = rgb.length > 0 ? Integer.parseInt(rgb[0].trim()) : 30;
                    int g = rgb.length > 1 ? Integer.parseInt(rgb[1].trim()) : 30;
                    int b = rgb.length > 2 ? Integer.parseInt(rgb[2].trim()) : 35;
                    dustOptions = new org.bukkit.Particle.DustOptions(Color.fromRGB(r, g, b), dustSize);
                }
                for (int i = 0; i < countPerTick; i++) {
                    double x = rng.nextDouble(minX, maxX + 1);
                    double z = rng.nextDouble(minZ, maxZ + 1);
                    int blockY = world.getHighestBlockYAt((int) x, (int) z);
                    double y = blockY + 1.5 + rng.nextDouble(0.0, 2.0);
                    if (debugParticles && tickCount % 10 == 0 && i == 0) {
                        logger.info("[MourningParticles] " + villageId + " tick#" + tickCount
                                + " sample: x=" + String.format("%.1f", x)
                                + " z=" + String.format("%.1f", z)
                                + " blockY=" + blockY + " spawnY=" + String.format("%.1f", y));
                    }
                    world.spawnParticle(particleType, x, y, z, 3, 0.3, 0.1, 0.3, 0.02, dustOptions);
                }

                if (debugParticles && tickCount == 1) {
                    logger.info("[MourningParticles] " + villageId + " tick#1 – " + countPerTick + "x3 " + particleType + " gespawnt");
                }
            }
        };

        int interval = ThreadLocalRandom.current().nextInt(intervalMinTicks, intervalMaxTicks + 1);
        int taskId = runnable.runTaskTimer(plugin, 0L, interval).getTaskId();
        particleTaskIds.put(villageId, taskId);
        particleTickCounters.put(villageId, 0);

        if (debugParticles) logger.info("[MourningParticles] Task gestartet, taskId=" + taskId + " interval=" + interval + " ticks");

        // Health-Check-Task starten (einmalig für das gesamte Plugin)
        if (healthCheckTaskId == null || !Bukkit.getScheduler().isCurrentlyRunning(healthCheckTaskId)) {
            startHealthCheckTask();
        }
    }

    /**
     * Stoppt die Trauer-Partikel für ein Dorf.
     */
    private void stopMourningParticles(String villageId) {
        Integer taskId = particleTaskIds.remove(villageId);
        particleTickCounters.remove(villageId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
            if (debugParticles) logger.info("[MourningParticles] " + villageId + " Task gecancelt, taskId=" + taskId);
        }
        // Perimeter-Cache NICHT leeren – er wird für Rejoin-Szenarien benötigt.
        // Erst bei endMourning/cancelMourning wird er entfernt.
    }

    /**
     * Startet den Health-Check-Task, der alle 600 Ticks (30 Sekunden)
     * prüft, ob für jedes aktive Trauer-Dorf Partikel laufen und diese
     * bei Bedarf neu startet (z. B. nach Chunk-Reload / Rejoin).
     */
    private void startHealthCheckTask() {
        if (healthCheckTaskId != null) {
            Bukkit.getScheduler().cancelTask(healthCheckTaskId);
        }
        healthCheckTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (String villageId : new java.util.ArrayList<>(activeMourning.keySet())) {
                // Prüfen, ob der Partikel-Task noch läuft
                Integer taskId = particleTaskIds.get(villageId);
                if (taskId == null || !Bukkit.getScheduler().isCurrentlyRunning(taskId)) {
                    if (debugParticles) logger.info("[MourningParticles] HealthCheck: " + villageId
                            + " Task fehlt/gestoppt, starte neu");
                    // Perimeter-Cache leeren, damit er neu berechnet wird
                    particlePerimeters.remove(villageId);
                    startMourningParticles(villageId);
                    continue;
                }

                // Prüfen, ob ein Spieler in der Nähe des Perimeters ist
                VillagePerimeter perimeter = particlePerimeters.get(villageId);
                if (perimeter == null) {
                    continue; // kein Perimeter gecached, kann nicht prüfen
                }

                World world = Bukkit.getWorld(perimeter.worldName());
                if (world == null) {
                    continue;
                }

                boolean playerNearby = false;
                double centerX = (perimeter.minX() + perimeter.maxX()) / 2.0;
                double centerZ = (perimeter.minZ() + perimeter.maxZ()) / 2.0;
                double radius = Math.max(
                        Math.abs(perimeter.maxX() - perimeter.minX()),
                        Math.abs(perimeter.maxZ() - perimeter.minZ())) / 2.0 + 64; // Perimeter-Halbachse + 64 Blöcke

                for (org.bukkit.entity.Player player : world.getPlayers()) {
                    if (player.getLocation().getWorld() != world) {
                        continue;
                    }
                    double dx = player.getLocation().getX() - centerX;
                    double dz = player.getLocation().getZ() - centerZ;
                    if (Math.abs(dx) <= radius && Math.abs(dz) <= radius) {
                        playerNearby = true;
                        break;
                    }
                }

                if (!playerNearby) {
                    // Keine Spieler in der Nähe: Partikel-Task stoppen (spart Server-Ressourcen)
                    if (debugParticles) logger.info("[MourningParticles] HealthCheck: " + villageId
                            + " keine Spieler in Reichweite, stoppe Task");
                    stopMourningParticles(villageId);
                    // Perimeter bleibt gecached für späteren Rejoin
                }
                // Falls Spieler in der Nähe und Task läuft: alles okay, nix tun
            }

            // Wenn keine aktiven Trauer-Dörfer mehr: Health-Check stoppen
            if (activeMourning.isEmpty() && healthCheckTaskId != null) {
                Bukkit.getScheduler().cancelTask(healthCheckTaskId);
                healthCheckTaskId = null;
                if (debugParticles) logger.info("[MourningParticles] HealthCheck gestoppt (keine aktiven Trauer-Dörfer)");
            }
        }, 20L * 30L, 20L * 30L).getTaskId();

        if (debugParticles) logger.info("[MourningParticles] HealthCheck-Task gestartet, taskId=" + healthCheckTaskId);
    }

    /**
     * Versucht, den Perimeter für ein Dorf aktiv zu berechnen, indem ein
     * lebender Villager dieser villageId gesucht wird.
     *
     * @return Perimeter oder null, wenn kein Villager gefunden wurde.
     */
    private VillagePerimeter computePerimeterForVillage(String villageId) {
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                String vid = resolveVillageIdQuietly(villager);
                if (!villageId.equals(vid)) {
                    continue;
                }
                VillagePerimeter perimeter = villagePerimeterService.computePerimeter(villager, villageId, villageIdentityService);
                logger.fine("Perimeter für " + villageId + " aktiv berechnet: "
                        + "[" + perimeter.minX() + "," + perimeter.maxX()
                        + "] x [" + perimeter.minZ() + "," + perimeter.maxZ() + "]");
                return perimeter;
            }
        }
        return null;
    }

    private String resolveVillageIdQuietly(Villager villager) {
        try {
            return villageIdentityService.resolve(villager).villageId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wird vom ChunkLoad-Listener aufgerufen. Prüft, ob der geladene Chunk
     * im Perimeter eines trauernden Dorfes liegt, und startet den Partikel-Task
     * neu, falls er nicht mehr läuft und ein Spieler in der Nähe ist.
     */
    public void onChunkLoad(org.bukkit.World world, int chunkX, int chunkZ) {
        // Block-Koordinaten des geladenen Chunks
        int blockMinX = chunkX << 4;
        int blockMaxX = blockMinX + 15;
        int blockMinZ = chunkZ << 4;
        int blockMaxZ = blockMinZ + 15;

        for (var entry : activeMourning.entrySet()) {
            String villageId = entry.getKey();

            // Prüfen, ob der Partikel-Task bereits läuft
            Integer taskId = particleTaskIds.get(villageId);
            if (taskId != null && Bukkit.getScheduler().isCurrentlyRunning(taskId)) {
                continue; // Task läuft noch, kein Handlungsbedarf
            }

            VillagePerimeter perimeter = particlePerimeters.get(villageId);
            if (perimeter == null) {
                continue;
            }

            // Nur Chunks in der richtigen Welt prüfen
            String worldName = perimeter.worldName();
            if (worldName == null || !worldName.equals(world.getName())) {
                continue;
            }

            // Prüfen, ob der geladene Chunk den Perimeter überlappt
            int pMinX = Math.min(perimeter.minX(), perimeter.maxX());
            int pMaxX = Math.max(perimeter.minX(), perimeter.maxX());
            int pMinZ = Math.min(perimeter.minZ(), perimeter.maxZ());
            int pMaxZ = Math.max(perimeter.minZ(), perimeter.maxZ());

            boolean overlaps = !(blockMaxX < pMinX || blockMinX > pMaxX
                    || blockMaxZ < pMinZ || blockMinZ > pMaxZ);

            if (!overlaps) {
                continue;
            }

            // Chunk überlappt den Perimeter: Prüfen, ob ein Spieler in der Nähe ist
            double centerX = (pMinX + pMaxX) / 2.0;
            double centerZ = (pMinZ + pMaxZ) / 2.0;
            double radius = Math.max(pMaxX - pMinX, pMaxZ - pMinZ) / 2.0 + 64;
            boolean playerNearby = false;
            for (org.bukkit.entity.Player player : world.getPlayers()) {
                double dx = player.getLocation().getX() - centerX;
                double dz = player.getLocation().getZ() - centerZ;
                if (Math.abs(dx) <= radius && Math.abs(dz) <= radius) {
                    playerNearby = true;
                    break;
                }
            }

            if (!playerNearby) {
                continue;
            }

            // Spieler in der Nähe + Chunk im Perimeter geladen → Partikel neu starten
            if (debugParticles) logger.info("[MourningParticles] ChunkLoad-Trigger: " + villageId
                    + " chunk=" + chunkX + "," + chunkZ + " → starte Partikel neu");
            startMourningParticles(villageId);
            return; // Nur ein Dorf pro ChunkLoad-Event behandeln
        }
    }
}
