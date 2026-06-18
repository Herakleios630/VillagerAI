package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.Anchor;
import de.ajsch.villagerai.model.VillageIdentity;
import de.ajsch.villagerai.model.VillageRecord;
import de.ajsch.villagerai.storage.ChiefRepository;
import de.ajsch.villagerai.storage.VillageRepository;
import de.ajsch.villagerai.util.Keys;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Player;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.persistence.PersistentDataType;

public final class VillageIdentityService {

    public static final double VILLAGE_RADIUS = 64.0D;

    private final Keys keys;
    private volatile VillageRepository villageRepository;
    private volatile ChiefRepository chiefRepository;
    private volatile Logger logger;
    private volatile MourningService mourningService;
    private final ConcurrentMap<String, Integer> populationCache = new ConcurrentHashMap<>();

    public VillageIdentityService(Keys keys) {
        this.keys = keys;
    }

    public void setVillageRepository(VillageRepository villageRepository) {
        this.villageRepository = villageRepository;
    }

    public void setChiefRepository(ChiefRepository chiefRepository) {
        this.chiefRepository = chiefRepository;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setMourningService(MourningService mourningService) {
        this.mourningService = mourningService;
    }

    /**
     * Einmalig beim Plugin-Start aufrufen: Entfernt alle PDC-Schluessel
     * (village_id, chief_flag, chief_id) von geladenen Villagern, wenn der
     * zugehoerige Eintrag nicht mehr in villages.yml bzw. chiefs.yml existiert.
     * Verhindert Zombie-IDs nach Loeschung der Persistenzdateien.
     */
    public void purgeAllStalePdc() {
        int purged = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!(entity instanceof Villager villager)) {
                    continue;
                }
                org.bukkit.persistence.PersistentDataContainer container = villager.getPersistentDataContainer();

                // village_id pruefen
                String pdcVillageId = container.get(keys.villageIdKey(), PersistentDataType.STRING);
                if (pdcVillageId != null && !pdcVillageId.isBlank()) {
                    if (villageRepository == null
                            || villageRepository.findByVillageId(pdcVillageId).isEmpty()) {
                        container.remove(keys.villageIdKey());
                        purged++;
                    }
                }

                // chief_flag + chief_id pruefen
                Byte pdcChiefFlag = container.get(keys.chiefFlagKey(), PersistentDataType.BYTE);
                String pdcChiefId = container.get(keys.chiefIdKey(), PersistentDataType.STRING);
                boolean hasChiefPdc = pdcChiefFlag != null && pdcChiefFlag == (byte) 1
                        && pdcChiefId != null && !pdcChiefId.isBlank();
                if (hasChiefPdc) {
                    if (chiefRepository == null
                                            || chiefRepository.findAll().stream().noneMatch(a -> a.speakerId().equals(pdcChiefId))) {
                        container.remove(keys.chiefFlagKey());
                        container.remove(keys.chiefIdKey());
                        purged++;
                    }
                }
            }
        }
        if (logger != null && purged > 0) {
            logger.info("[VillageIdentity] PDC-Cleanup: " + purged + " veraltete Schluessel von geladenen Villagern entfernt");
        }
    }

    /** Ermittelt eine stabile villageId per PDC, Repository oder Neuregistrierung. */
    public String resolveOrRegisterVillageId(Villager villager) {
        if (villager == null) {
            if (logger != null) {
                logger.warning("[VillageIdentity] resolveOrRegisterVillageId() mit null villager aufgerufen – gebe null zurueck");
            }
            return null;
        }

        // 1. PDC-Check – bereits gespeichert? Nur gueltig wenn Repository
        //    den Eintrag noch kennt (verhindert Zombie-IDs nach villages.yml-Löschung)
        String pdcId = villager.getPersistentDataContainer().get(keys.villageIdKey(), PersistentDataType.STRING);
        if (pdcId != null && !pdcId.isBlank() && villageRepository != null
                && villageRepository.findByVillageId(pdcId).isPresent()) {
            return pdcId;
        }
        // PDC-Wert existiert, aber nicht mehr im Repository → bereinigen
        if (pdcId != null && !pdcId.isBlank()) {
            villager.getPersistentDataContainer().remove(keys.villageIdKey());
            if (logger != null) {
                logger.fine("[VillageIdentity] Bereinige veraltete PDC-villageId '" + pdcId
                        + "' auf Villager " + villager.getUniqueId() + " – nicht mehr in villages.yml vorhanden");
            }
        }

        // 2. Besten Anchor ermitteln
        Location anchor = resolveAnchor(villager);
        if (anchor == null || anchor.getWorld() == null) {
            return null;
        }

        int population = estimateVillagePopulation(villager, anchor);

        // 3. Repository-Suche (nur wenn VillageRepository gesetzt ist)
        if (villageRepository != null) {
            VillageRecord record = villageRepository.findByAnchor(anchor, 64).orElse(null);
            if (record != null) {
                // Bekanntes Dorf – PDC merken
                villager.getPersistentDataContainer().set(keys.villageIdKey(), PersistentDataType.STRING, record.villageId());
                // ggf. neuen Anchor registrieren
                Anchor newAnchor = buildAnchor(villager, anchor);
                if (newAnchor != null && !record.knownAnchors().contains(newAnchor)) {
                    List<Anchor> updated = new ArrayList<>(record.knownAnchors());
                    updated.add(newAnchor);
                    villageRepository.save(new VillageRecord(
                            record.villageId(), record.villageName(), record.registeredAt(), updated));
                }
                return record.villageId();
            }
        }

        // 4. Neu registrieren, wenn Mindestpopulation erreicht
        Anchor.AnchorType anchorType = resolveAnchorType(villager);
        int minVillagers;
        switch (anchorType) {
            case MEETING_POINT:
            case HOME:
                minVillagers = 1;
                break;
            case JOB_SITE:
            case POTENTIAL_JOB_SITE:
                minVillagers = 2;
                break;
            default:
                minVillagers = 3;
                break;
        }
        if (population < minVillagers) {
            return null; // zu wenige Villager für eigenes Dorf
        }

        String villageId = UUID.randomUUID().toString();
        String villageName = buildVillageName(anchor);
        long now = System.currentTimeMillis();
        Anchor newAnchor = buildAnchor(villager, anchor);
        if (newAnchor == null) {
            return null;
        }
        VillageRecord newRecord = new VillageRecord(villageId, villageName, now, List.of(newAnchor));
        villager.getPersistentDataContainer().set(keys.villageIdKey(), PersistentDataType.STRING, villageId);
        if (villageRepository != null) {
            villageRepository.save(newRecord);
        }
        if (logger != null) {
            logger.info("[VillageIdentity] Neues Dorf registriert: " + villageId + " '" + villageName + "' bei " + newAnchor.posKey());
        }
        return villageId;
    }

    public VillageIdentity resolve(Villager villager) {
        Location anchor = resolveAnchor(villager);
        if (anchor == null || anchor.getWorld() == null) {
            anchor = villager.getLocation();
        }
        String villageId = resolveOrRegisterVillageId(villager);
        if (villageId == null) {
            villageId = "unregistered";
        }
        String villageName;
        VillageRecord record = (villageRepository != null) ? villageRepository.findByVillageId(villageId).orElse(null) : null;
        if (record != null) {
            villageName = record.villageName();
        } else {
            villageName = buildVillageName(anchor);
        }
        int population = estimateVillagePopulation(villager, anchor);
        populationCache.put(villageId, population);
        return new VillageIdentity(
                villageId,
                villageName,
                buildVillageDescription(anchor, villageName),
                buildVillageAttributes(villager, anchor),
                formatBiome(anchor.getBlock().getBiome()),
                population,
                buildVillageEventSummary(villageId, anchor));
    }

    public Location resolveAnchor(Villager villager) {
        Location meetingPoint = villager.getMemory(MemoryKey.MEETING_POINT);
        if (hasWorld(meetingPoint)) {
            return meetingPoint;
        }

        Location home = villager.getMemory(MemoryKey.HOME);
        if (hasWorld(home)) {
            return home;
        }

        Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
        if (hasWorld(jobSite)) {
            return jobSite;
        }

        Location potentialJobSite = villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE);
        if (hasWorld(potentialJobSite)) {
            return potentialJobSite;
        }

        return villager.getLocation();
    }

    /**
     * Ermittelt die villageId fuer einen Spieler, indem das naechste geladene
     * Villager im Umkreis von 64 Bloeken gesucht wird.
     *
     * @return die villageId, oder {@code Optional.empty()} falls kein Villager
     *         in Reichweite ist.
     */
    public Optional<String> resolveVillageIdFromPlayer(Player player) {
        Location playerLocation = player.getLocation();
        double closestDistanceSq = VILLAGE_RADIUS * VILLAGE_RADIUS;
        Villager closest = null;

        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(playerLocation, VILLAGE_RADIUS, VILLAGE_RADIUS, VILLAGE_RADIUS)) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            double distSq = entity.getLocation().distanceSquared(playerLocation);
            if (distSq <= closestDistanceSq) {
                closestDistanceSq = distSq;
                closest = villager;
            }
        }

        if (closest == null) {
            return Optional.empty();
        }

        String villageId = resolveOrRegisterVillageId(closest);
        return (villageId != null) ? Optional.of(villageId) : Optional.empty();
    }

    private boolean hasWorld(Location location) {
        return location != null && location.getWorld() != null;
    }

    private Anchor.AnchorType resolveAnchorType(Villager villager) {
        if (hasWorld(villager.getMemory(MemoryKey.MEETING_POINT))) {
            return Anchor.AnchorType.MEETING_POINT;
        }
        if (hasWorld(villager.getMemory(MemoryKey.HOME))) {
            return Anchor.AnchorType.HOME;
        }
        if (hasWorld(villager.getMemory(MemoryKey.JOB_SITE))) {
            return Anchor.AnchorType.JOB_SITE;
        }
        if (hasWorld(villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE))) {
            return Anchor.AnchorType.POTENTIAL_JOB_SITE;
        }
        return Anchor.AnchorType.VILLAGER_POSITION;
    }

    private Anchor buildAnchor(Villager villager, Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new Anchor(
                resolveAnchorType(villager),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    
    /**
     * Löst eine VillageIdentity nur anhand der villageId auf (ohne Villager-Entity).
     * Nützlich wenn wir nur einen Speaker mit villageId haben, aber keinen Villager.
     */
    public VillageIdentity resolveByVillageId(String villageId) {
        VillageRecord record = (villageRepository != null)
                ? villageRepository.findByVillageId(villageId).orElse(null)
                : null;
        if (record != null) {
            // Versuche einen Registry-Anchor zu finden
            String worldName = record.knownAnchors().isEmpty()
                    ? "world"
                    : record.knownAnchors().get(0).world();
            org.bukkit.Location anchor = record.knownAnchors().isEmpty()
                    ? new org.bukkit.Location(org.bukkit.Bukkit.getWorld(worldName), 0, 64, 0)
                    : new org.bukkit.Location(org.bukkit.Bukkit.getWorld(record.knownAnchors().get(0).world()), record.knownAnchors().get(0).x(), record.knownAnchors().get(0).y(), record.knownAnchors().get(0).z());
            if (anchor == null || anchor.getWorld() == null) {
                anchor = new org.bukkit.Location(
                        org.bukkit.Bukkit.getWorlds().get(0), 0, 64, 0);
            }
            int population = populationCache.getOrDefault(villageId, 1);
            return new VillageIdentity(
                    villageId,
                    record.villageName(),
                    buildVillageDescription(anchor, record.villageName()),
                    buildVillageAttributes(null, anchor),
                    formatBiome(anchor.getBlock().getBiome()),
                    population,
                    buildVillageEventSummary(villageId, anchor));
        }
        // Fallback: generische Identity
        return new VillageIdentity(
                villageId,
                "Unbekanntes Dorf",
                "Ein Dorf, über das wenig bekannt ist.",
                "keine groben Dorfmerkmale bekannt",
                "PLAINS",
                1,
                "kein wichtiges Dorfereignis bekannt");
    }

    private String buildVillageName(Location anchor) {
        String prefix = resolvePrefix(anchor.getBlock().getBiome());
        String suffix = resolveSuffix(anchor);
        return prefix + suffix;
    }

    private String resolvePrefix(Biome biome) {
        String biomeName = biome.toString();
        if (biomeName.contains("CHERRY")) {
            return "Kirsch";
        }
        if (biomeName.contains("BIRCH")) {
            return "Birken";
        }
        if (biomeName.contains("DESERT") || biomeName.contains("BADLANDS")) {
            return "Sand";
        }
        if (biomeName.contains("JUNGLE") || biomeName.contains("BAMBOO")) {
            return "Dschungel";
        }
        if (biomeName.contains("SAVANNA") || biomeName.contains("ACACIA")) {
            return "Akazien";
        }
        if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            return "Moor";
        }
        if (biomeName.contains("SNOW") || biomeName.contains("ICE") || biomeName.contains("FROZEN")) {
            return "Frost";
        }
        if (biomeName.contains("TAIGA") || biomeName.contains("SPRUCE") || biomeName.contains("GROVE")) {
            return "Tannen";
        }
        if (biomeName.contains("FOREST") || biomeName.contains("DARK_FOREST")) {
            return "Eichen";
        }
        if (biomeName.contains("OCEAN") || biomeName.contains("BEACH") || biomeName.contains("RIVER")) {
            return "Kuesten";
        }
        return "Eben";
    }

    private String resolveSuffix(Location anchor) {
        int variant = Math.floorMod(anchor.getBlockX() * 31 + anchor.getBlockZ() * 17, 4);
        return switch (variant) {
            case 0 -> "hain";
            case 1 -> "feld";
            case 2 -> "dorf";
            default -> "grund";
        };
    }

    private String buildVillageDescription(Location anchor, String villageName) {
        String biomeLabel = formatBiome(anchor.getBlock().getBiome());
        String setting = describeSetting(anchor.getBlock().getBiome());
        return villageName + " ist ein " + setting + " im Biom " + biomeLabel + ".";
    }

    private String buildVillageAttributes(Villager villager, Location anchor) {
        List<String> attributes = new ArrayList<>();
        Biome biome = anchor.getBlock().getBiome();
        String biomeName = biome.name();

        if (biomeName.contains("DESERT") || biomeName.contains("BADLANDS") || biomeName.contains("SAVANNA")
                || biomeName.contains("PLAINS") || biomeName.contains("MEADOW")) {
            attributes.add("offene Felder und weite Sicht");
        }
        if (biomeName.contains("FOREST") || biomeName.contains("BIRCH") || biomeName.contains("CHERRY")
                || biomeName.contains("TAIGA") || biomeName.contains("GROVE") || biomeName.contains("JUNGLE")) {
            attributes.add("viel Naehe zu Baeumen und Holz");
        }
        if (biomeName.contains("RIVER") || biomeName.contains("OCEAN") || biomeName.contains("BEACH")
                || biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            attributes.add("Wasser in der Naehe");
        }
        if (biomeName.contains("SNOW") || biomeName.contains("ICE") || biomeName.contains("FROZEN")
                || biomeName.contains("DESERT") || biomeName.contains("BADLANDS")) {
            attributes.add("raues Klima");
        }

        if (villager != null) {
            if (hasWorld(villager.getMemory(MemoryKey.MEETING_POINT))) {
                attributes.add("klarer Dorfmittelpunkt");
            }
            if (hasWorld(villager.getMemory(MemoryKey.HOME))) {
                attributes.add("erkennbare Wohnhaeuser");
            }
            if (hasWorld(villager.getMemory(MemoryKey.JOB_SITE))
                    || hasWorld(villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE))) {
                attributes.add("feste Arbeitsorte");
            }
        }

        if (attributes.isEmpty()) {
            attributes.add("wenig erkennbare Besonderheiten");
        }

        return String.join(", ", attributes);
    }

    /**
     * Zaehlt Villager im Umkreis, die denselben geografischen Dorfmittelpunkt
     * teilen.  Verwendet nur {@link #resolveAnchor(Villager)} (keine
     * Registrierungslogik), um Endlosrekursion mit
     * {@link #resolveOrRegisterVillageId(Villager)} zu vermeiden.
     */
    private int estimateVillagePopulation(Villager villager, Location anchor) {
        int population = 0;
        for (org.bukkit.entity.Entity entity : anchor.getWorld().getNearbyEntities(anchor, VILLAGE_RADIUS, VILLAGE_RADIUS, VILLAGE_RADIUS)) {
            if (!(entity instanceof Villager nearbyVillager)) {
                continue;
            }
            Location nearbyAnchor = resolveAnchor(nearbyVillager);
            if (nearbyAnchor != null && nearbyAnchor.getWorld() != null
                    && nearbyAnchor.getWorld().equals(anchor.getWorld())
                    && nearbyAnchor.distanceSquared(anchor) < 64.0D) { // 8 Blocks Toleranz
                population++;
            }
        }
        return Math.max(1, population);
    }

    private String buildVillageEventSummary(String villageId, Location anchor) {
        // Trauerzustand hat Vorrang vor Wetter/Tageszeit
        if (villageId == null) {
            villageId = "unregistered";
        }
        boolean inMourning = mourningService != null && mourningService.isVillageInMourning(villageId);
        boolean hasChief = chiefRepository != null && chiefRepository.findActiveByVillageId(villageId).isPresent();

        String chiefStatus;
        if (inMourning) {
            chiefStatus = " Das Dorf trauert um seinen gefallenen Haeuptling.";
        } else if (hasChief) {
            chiefStatus = " Der Dorfhaeuptling ist anwesend.";
        } else {
            chiefStatus = " Das Dorf hat derzeit keinen Haeuptling.";
        }

        if (anchor.getWorld().isThundering()) {
            return "Ein Gewitter drueckt auf die Stimmung und haelt viele Dorfbewohner eher in Deckung." + chiefStatus;
        }
        if (anchor.getWorld().hasStorm()) {
            return "Regen bestimmt gerade den Dorfalltag und bremst die Arbeit im Freien." + chiefStatus;
        }

        long time = anchor.getWorld().getTime();
        if (time >= 12300L && time < 23850L) {
            return "Es ist Nacht, und das Dorf wirkt vorsichtig und zurueckgezogen." + chiefStatus;
        }
        return "Im Dorf gibt es aktuell kein herausstechendes Ereignis; alles wirkt eher ruhig und gewoehnlich." + chiefStatus;
    }

    private String formatBiome(Biome biome) {
        return biome.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String describeSetting(Biome biome) {
        String biomeName = biome.name();
        if (biomeName.contains("DESERT") || biomeName.contains("BADLANDS")) {
            return "trockenes Dorf am staubigen Rand der Welt";
        }
        if (biomeName.contains("SNOW") || biomeName.contains("ICE") || biomeName.contains("FROZEN")) {
            return "kuehles Dorf in rauer, frostiger Umgebung";
        }
        if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            return "feuchtes Dorf zwischen Wasser und dichtem Gruen";
        }
        if (biomeName.contains("JUNGLE") || biomeName.contains("BAMBOO")) {
            return "enges Dorf im dichten, wilden Gruen";
        }
        if (biomeName.contains("OCEAN") || biomeName.contains("BEACH") || biomeName.contains("RIVER")) {
            return "offenes Dorf nah am Wasser";
        }
        if (biomeName.contains("TAIGA") || biomeName.contains("SPRUCE") || biomeName.contains("GROVE")) {
            return "ruhiges Dorf zwischen dunklen Nadelbaeumen";
        }
        if (biomeName.contains("FOREST") || biomeName.contains("BIRCH") || biomeName.contains("CHERRY")) {
            return "eingebettetes Dorf zwischen Baeumen und Feldern";
        }
        return "schlichtes Dorf in offener Landschaft";
    }
}
