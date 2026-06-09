package de.ajsch.villagerai.service;

import de.ajsch.villagerai.model.VillagerContext;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

public final class VillagerContextService {

    private final VillagerTradeService villagerTradeService;
    private final VillagerConfinementService villagerConfinementService;

    public VillagerContextService(
            VillagerTradeService villagerTradeService,
            VillagerConfinementService villagerConfinementService) {
        this.villagerTradeService = villagerTradeService;
        this.villagerConfinementService = villagerConfinementService;
    }

    public VillagerContext resolve(Villager villager, UUID playerUuid) {
        World world = villager.getWorld();
        Location location = villager.getLocation();
        double currentHealth = villager.getHealth();
        double maxHealth = resolveMaxHealth(villager);
        double healthRatio = maxHealth <= 0.0D ? 0.0D : currentHealth / maxHealth;
        Player player = Bukkit.getPlayer(playerUuid);
        Location home = villager.getMemory(MemoryKey.HOME);
        Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
        Location potentialJobSite = villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE);
        Location meetingPoint = villager.getMemory(MemoryKey.MEETING_POINT);

        return new VillagerContext(
            villager.getProfession().name(),
            villager.getVillagerType().toString(),
            location.getBlock().getBiome().toString(),
                world.getName(),
                isDay(world),
                world.hasStorm(),
                world.isThundering(),
                currentHealth,
                maxHealth,
                healthRatio,
                Boolean.TRUE.equals(villager.getMemory(MemoryKey.ATE_RECENTLY)),
                villagerTradeService.buildSummary(playerUuid, villager.getUniqueId()),
                villagerConfinementService.describe(villager),
                buildAuthoritativeWorldFacts(villager, player, location, meetingPoint, jobSite, home),
                formatLocation(home),
                formatLocation(jobSite),
                formatLocation(potentialJobSite),
                formatLocation(meetingPoint));
    }

    private String buildAuthoritativeWorldFacts(
            Villager villager,
            Player player,
            Location villagerLocation,
            Location meetingPoint,
            Location jobSite,
            Location home) {
        String profession = villager.getProfession().name().toUpperCase(Locale.ROOT);
        return switch (profession) {
            case "CARTOGRAPHER" -> buildSpecialistFacts(
                "Kartograph",
                villagerLocation,
                player,
                meetingPoint,
                jobSite,
                home,
                "Die Dorfglocke",
                "Der Kartographen-Arbeitsplatz",
                "Das Heim des Kartographen");
            case "LIBRARIAN" -> buildSpecialistFacts(
                "Bibliothekar",
                villagerLocation,
                player,
                meetingPoint,
                jobSite,
                home,
                "Die Dorfglocke",
                "Das Lesepult des Bibliothekars",
                "Das Heim des Bibliothekars");
            case "ARMORER" -> buildSpecialistFacts(
                "Ruestungsschmied",
                villagerLocation,
                player,
                meetingPoint,
                jobSite,
                home,
                "Die Dorfglocke",
                "Die Schmiede des Ruestungsschmieds",
                "Das Heim des Ruestungsschmieds");
            case "TOOLSMITH" -> buildSpecialistFacts(
                "Werkzeugschmied",
                villagerLocation,
                player,
                meetingPoint,
                jobSite,
                home,
                "Die Dorfglocke",
                "Die Werkstatt des Werkzeugschmieds",
                "Das Heim des Werkzeugschmieds");
            case "WEAPONSMITH" -> buildSpecialistFacts(
                "Waffenschmied",
                villagerLocation,
                player,
                meetingPoint,
                jobSite,
                home,
                "Die Dorfglocke",
                "Die Schmiede des Waffenschmieds",
                "Das Heim des Waffenschmieds");
            default -> null;
        };
        }

        private String buildSpecialistFacts(
            String specialistLabel,
            Location villagerLocation,
            Player player,
            Location meetingPoint,
            Location jobSite,
            Location home,
            String bellLabel,
            String jobSiteLabel,
            String homeLabel) {
        if (villagerLocation == null || villagerLocation.getWorld() == null) {
            return null;
        }

        StringBuilder facts = new StringBuilder("Pluginseitig bestaetigte Weltfakten fuer ")
            .append(specialistLabel)
            .append(": ");
        facts.append("aktuelles Biom ")
                .append(villagerLocation.getBlock().getBiome().toString().toLowerCase())
                .append(", Welt ")
                .append(villagerLocation.getWorld().getName())
                .append(". ");

        appendPoiFact(facts, player, meetingPoint, bellLabel);
        appendPoiFact(facts, player, jobSite, jobSiteLabel);
        appendPoiFact(facts, player, home, homeLabel);
        return facts.toString().trim();
    }

    private void appendPoiFact(StringBuilder facts, Player player, Location poi, String label) {
        if (poi == null || poi.getWorld() == null) {
            return;
        }
        facts.append(label)
                .append(" liegt bei ")
                .append(poi.getBlockX())
                .append(", ")
                .append(poi.getBlockY())
                .append(", ")
                .append(poi.getBlockZ());
        if (player != null && player.getWorld().equals(poi.getWorld())) {
            facts.append(" und damit ")
                    .append(describeDirection(player.getLocation(), poi));
        }
        facts.append(". ");
    }

    private String describeDirection(Location from, Location to) {
        int dx = to.getBlockX() - from.getBlockX();
        int dz = to.getBlockZ() - from.getBlockZ();
        int distance = (int) Math.round(Math.sqrt((dx * dx) + (dz * dz)));
        String horizontal = dz < -3 ? "nord" : dz > 3 ? "sued" : "";
        String vertical = dx > 3 ? "ost" : dx < -3 ? "west" : "";
        String direction;
        if (!horizontal.isBlank() && !vertical.isBlank()) {
            direction = horizontal + vertical;
        } else if (!horizontal.isBlank()) {
            direction = horizontal;
        } else if (!vertical.isBlank()) {
            direction = vertical;
        } else {
            direction = "ganz in der Naehe";
        }
        if ("ganz in der Naehe".equals(direction)) {
            return "ganz in deiner Naehe ist";
        }
        return "etwa " + distance + " Bloecke " + direction + " von dir entfernt ist";
    }

    private double resolveMaxHealth(Villager villager) {
        if (villager.getAttribute(Attribute.MAX_HEALTH) == null) {
            return villager.getHealth();
        }

        return villager.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private boolean isDay(World world) {
        long time = world.getTime();
        return time >= 0L && time < 12300L;
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.getWorld().getName()
                + " ("
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ()
                + ")";
    }
}