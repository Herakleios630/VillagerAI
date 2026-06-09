package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.VillageIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

public final class VillageIdentityService {

    public VillageIdentity resolve(Villager villager) {
        Location anchor = resolveAnchor(villager);
        String villageName = buildVillageName(anchor);
        return new VillageIdentity(
                buildVillageId(anchor),
                villageName,
                buildVillageDescription(anchor, villageName),
            buildVillageAttributes(villager, anchor),
            formatBiome(anchor.getBlock().getBiome()),
            estimateVillagePopulation(villager, anchor),
            buildVillageEventSummary(villager, anchor));
    }

    private Location resolveAnchor(Villager villager) {
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

    private boolean hasWorld(Location location) {
        return location != null && location.getWorld() != null;
    }

    private String buildVillageId(Location anchor) {
        String worldName = anchor.getWorld().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return worldName + ":v:" + anchor.getBlockX() + ":" + anchor.getBlockZ();
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

        if (attributes.isEmpty()) {
            attributes.add("wenig erkennbare Besonderheiten");
        }

        return String.join(", ", attributes);
    }

    private int estimateVillagePopulation(Villager villager, Location anchor) {
        String villageId = buildVillageId(anchor);
        int population = 0;
        for (org.bukkit.entity.Entity entity : anchor.getWorld().getNearbyEntities(anchor, 96.0D, 48.0D, 96.0D)) {
            if (!(entity instanceof Villager nearbyVillager)) {
                continue;
            }

            Location nearbyAnchor = resolveAnchor(nearbyVillager);
            if (!hasWorld(nearbyAnchor)) {
                continue;
            }

            if (villageId.equals(buildVillageId(nearbyAnchor))) {
                population++;
            }
        }

        return Math.max(1, population);
    }

    private String buildVillageEventSummary(Villager villager, Location anchor) {
        if (anchor.getWorld().isThundering()) {
            return "Ein Gewitter drueckt auf die Stimmung und haelt viele Dorfbewohner eher in Deckung.";
        }
        if (anchor.getWorld().hasStorm()) {
            return "Regen bestimmt gerade den Dorfalltag und bremst die Arbeit im Freien.";
        }

        long time = anchor.getWorld().getTime();
        if (time >= 12300L && time < 23850L) {
            return "Es ist Nacht, und das Dorf wirkt vorsichtig und zurueckgezogen.";
        }
        if (hasWorld(villager.getMemory(MemoryKey.JOB_SITE)) || hasWorld(villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE))) {
            return "Es ist ein ruhiger Arbeitstag, und die Dorfbewohner gehen ihren ueblichen Aufgaben nach.";
        }
        if (hasWorld(villager.getMemory(MemoryKey.MEETING_POINT))) {
            return "Im Dorf ist gerade ein ruhiger, geordneter Alltag ohne auffaellige Zwischenfaelle spuerbar.";
        }
        return "Im Dorf gibt es aktuell kein herausstechendes Ereignis; alles wirkt eher ruhig und gewoehnlich.";
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