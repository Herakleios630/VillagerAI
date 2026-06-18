package de.ajsch.villagerai.model;

import java.util.List;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.block.Biome;
import org.bukkit.block.banner.PatternType;

/**
 * Ordnet jedes Bukkit-Biom einer von 14 Biom-Familien zu und liefert
 * die zugehoerige Farb- und Materialwelt fuer Chief-Visuals.
 */
public enum BiomeFamily {

    PLAINS(
        "#4CAF50", "#FFD700", "OAK_WOOD",
        List.of(Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW)
    ),
    TAIGA(
        "#2E7D32", "#CFD8DC", "SPRUCE_WOOD",
        List.of(Biome.TAIGA, Biome.SNOWY_TAIGA,
                Biome.OLD_GROWTH_PINE_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.GROVE)
    ),
    DESERT(
        "#FF9800", "#D7CCC8", "SANDSTONE",
        List.of(Biome.DESERT, Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS)
    ),
    SWAMP(
        "#558B2F", "#F9A825", "OAK_WOOD",
        List.of(Biome.SWAMP, Biome.MANGROVE_SWAMP)
    ),
    SAVANNA(
        "#E65100", "#FFEB3B", "ACACIA_WOOD",
        List.of(Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA)
    ),
    SNOW(
        "#FFFFFF", "#81D4FA", "SPRUCE_WOOD",
        List.of(Biome.SNOWY_PLAINS, Biome.ICE_SPIKES, Biome.FROZEN_PEAKS, Biome.SNOWY_SLOPES)
    ),
    FOREST(
        "#388E3C", "#F48FB1", "BIRCH_WOOD",
        List.of(Biome.FOREST, Biome.FLOWER_FOREST, Biome.BIRCH_FOREST,
                Biome.DARK_FOREST, Biome.CHERRY_GROVE, Biome.OLD_GROWTH_BIRCH_FOREST)
    ),
    JUNGLE(
        "#1B5E20", "#FFD54F", "JUNGLE_WOOD",
        List.of(Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE)
    ),
    OCEAN(
        "#1565C0", "#00BCD4", "PRISMARINE",
        List.of(Biome.OCEAN, Biome.WARM_OCEAN, Biome.LUKEWARM_OCEAN,
                Biome.COLD_OCEAN, Biome.FROZEN_OCEAN,
                Biome.DEEP_OCEAN, Biome.DEEP_LUKEWARM_OCEAN,
                Biome.DEEP_COLD_OCEAN, Biome.DEEP_FROZEN_OCEAN)
    ),
    MOUNTAIN(
        "#9E9E9E", "#607D8B", "STONE",
        List.of(Biome.STONY_PEAKS, Biome.JAGGED_PEAKS,
                Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_FOREST)
    ),
    UNDERGROUND(
        "#212121", "#76FF03", "DEEPSLATE",
        List.of(Biome.DRIPSTONE_CAVES, Biome.LUSH_CAVES, Biome.DEEP_DARK)
    ),
    NETHER(
        "#B71C1C", "#FFD700", "NETHER_BRICKS",
        List.of(Biome.NETHER_WASTES, Biome.CRIMSON_FOREST,
                Biome.WARPED_FOREST, Biome.SOUL_SAND_VALLEY, Biome.BASALT_DELTAS)
    ),
    END(
        "#9C27B0", "#FFEE58", "END_STONE",
        List.of(Biome.THE_END, Biome.END_HIGHLANDS, Biome.END_MIDLANDS, Biome.SMALL_END_ISLANDS)
    ),
    DEFAULT(
        "#8D6E63", "#BDBDBD", "OAK_WOOD",
        List.of()
    );

    private final String primaryColor;
    private final String secondaryColor;
    private final String accentMaterial;
    private final List<Biome> biomes;

    BiomeFamily(String primaryColor, String secondaryColor, String accentMaterial, List<Biome> biomes) {
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.accentMaterial = accentMaterial;
        this.biomes = biomes;
    }

    public String primaryColor()   { return primaryColor; }
    public String secondaryColor() { return secondaryColor; }
    public String accentMaterial() { return accentMaterial; }
    public List<Biome> biomes()    { return biomes; }

    /**
     * Ermittelt die Biom-Familie zu einem Bukkit-Biom.
     * Fallback: {@link #DEFAULT}.
     */
    public static BiomeFamily fromBiome(Biome biome) {
        if (biome == null) return DEFAULT;
        for (BiomeFamily family : values()) {
            if (family.biomes.contains(biome)) return family;
        }
        return DEFAULT;
    }

    /**
     * Ermittelt die Biom-Familie aus dem formatierten Biom-Namen,
     * wie er von {@code VillageIdentityService.formatBiome()} kommt
     * (z.B. "plains", "snowy taiga").
     * Fallback: {@link #DEFAULT}.
     */
    public static BiomeFamily fromBiomeName(String biomeName) {
        if (biomeName == null || biomeName.isBlank()) return DEFAULT;
        try {
            Biome biome = Biome.valueOf(biomeName.toUpperCase(Locale.ROOT).replace(' ', '_'));
            return fromBiome(biome);
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }

    /**
     * Parst den hexadezimalen primaryColor-String in ein Bukkit-{@link Color}-Objekt.
     * Fallback: Weiß (255,255,255).
     */
    public Color getPrimaryBukkitColor() {
        try {
            String hex = primaryColor.replace("#", "");
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            return Color.fromRGB(255, 255, 255);
        }
    }

    /**
     * Liefert die bevorzugten Banner-Farben dieser Biom-Familie.
     * Daten für spätere Mod – wird aktuell NICHT in {@code buildBannerPatterns()} verwendet.
     */
    public List<DyeColor> getBannerColors() {
        // Jede Biom-Familie liefert 2-3 passende DyeColors für Banner
        return switch (this) {
            case PLAINS      -> List.of(DyeColor.WHITE, DyeColor.YELLOW, DyeColor.LIME);
            case TAIGA       -> List.of(DyeColor.GREEN, DyeColor.GRAY, DyeColor.LIGHT_GRAY);
            case DESERT      -> List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.WHITE);
            case SWAMP       -> List.of(DyeColor.GREEN, DyeColor.BROWN, DyeColor.GRAY);
            case SAVANNA     -> List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.BROWN);
            case SNOW        -> List.of(DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.CYAN);
            case FOREST      -> List.of(DyeColor.GREEN, DyeColor.PINK, DyeColor.WHITE);
            case JUNGLE      -> List.of(DyeColor.GREEN, DyeColor.YELLOW, DyeColor.LIME);
            case OCEAN       -> List.of(DyeColor.BLUE, DyeColor.CYAN, DyeColor.LIGHT_BLUE);
            case MOUNTAIN    -> List.of(DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.CYAN);
            case UNDERGROUND -> List.of(DyeColor.BLACK, DyeColor.GRAY, DyeColor.LIME);
            case NETHER      -> List.of(DyeColor.RED, DyeColor.ORANGE, DyeColor.BLACK);
            case END         -> List.of(DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.YELLOW);
            default          -> List.of(DyeColor.WHITE, DyeColor.BROWN, DyeColor.GRAY);
        };
    }

    /**
     * Liefert die bevorzugten Banner-Muster dieser Biom-Familie.
     * Daten für spätere Mod – wird aktuell NICHT in {@code buildBannerPatterns()} verwendet.
     */
    public List<PatternType> getPreferredPatterns() {
        return switch (this) {
            case PLAINS      -> List.of(PatternType.STRIPE_BOTTOM, PatternType.FLOWER, PatternType.CROSS);
            case TAIGA       -> List.of(PatternType.STRIPE_TOP, PatternType.TRIANGLE_BOTTOM, PatternType.GRADIENT);
            case DESERT      -> List.of(PatternType.STRIPE_MIDDLE, PatternType.TRIANGLE_TOP, PatternType.CURLY_BORDER);
            case SWAMP       -> List.of(PatternType.CREEPER, PatternType.FLOWER, PatternType.CROSS);
            case SAVANNA     -> List.of(PatternType.TRIANGLE_BOTTOM, PatternType.STRIPE_BOTTOM, PatternType.HALF_HORIZONTAL);
            case SNOW        -> List.of(PatternType.GRADIENT, PatternType.STRAIGHT_CROSS, PatternType.RHOMBUS);
            case FOREST      -> List.of(PatternType.FLOWER, PatternType.CREEPER, PatternType.STRIPE_LEFT);
            case JUNGLE      -> List.of(PatternType.FLOWER, PatternType.CREEPER, PatternType.GLOBE);
            case OCEAN       -> List.of(PatternType.GRADIENT, PatternType.CURLY_BORDER, PatternType.RHOMBUS);
            case MOUNTAIN    -> List.of(PatternType.TRIANGLE_TOP, PatternType.STRIPE_MIDDLE, PatternType.GRADIENT);
            case UNDERGROUND -> List.of(PatternType.CREEPER, PatternType.SKULL, PatternType.GLOBE);
            case NETHER      -> List.of(PatternType.SKULL, PatternType.STRAIGHT_CROSS, PatternType.GLOBE);
            case END         -> List.of(PatternType.GLOBE, PatternType.RHOMBUS, PatternType.MOJANG);
            default          -> List.of(PatternType.BASE, PatternType.CROSS, PatternType.STRIPE_BOTTOM);
        };
    }
}