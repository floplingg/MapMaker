package dev.flop.mapmaker.core;

import java.util.List;

/**
 * Fixed, survival-friendly block choice for every placeable map colour.
 * No configuration needed: one solid, non-gravity, easy-to-get block per colour.
 * Water is left out on purpose (its shade depends on depth, not height).
 */
public final class MapColors {

    public record Entry(int id, int r, int g, int b, String block, String props) {}

    /** Map shade multipliers, indexed by shade: 0 = darker (lower than north), 1 = flat, 2 = brighter (higher). */
    public static final int[] SHADE_MUL = {180, 220, 255, 135};

    public static final String NOOBLINE_BLOCK = "minecraft:cobblestone";

    public static final List<Entry> ENTRIES = List.of(
        e(1, 127, 178, 56, "grass_block"),
        e(2, 247, 233, 163, "sandstone"),
        e(3, 199, 199, 199, "mushroom_stem"),
        e(4, 255, 0, 0, "redstone_block"),
        e(5, 160, 160, 255, "packed_ice"),
        e(6, 167, 167, 167, "iron_block"),
        new Entry(7, 0, 124, 0, "minecraft:oak_leaves", "persistent=true"),
        e(8, 255, 255, 255, "white_concrete"),
        e(9, 164, 168, 184, "clay"),
        e(10, 151, 109, 77, "dirt"),
        e(11, 112, 112, 112, "cobblestone"),
        e(13, 143, 119, 72, "oak_planks"),
        e(14, 255, 252, 245, "quartz_block"),
        e(15, 216, 127, 51, "orange_concrete"),
        e(16, 178, 76, 216, "magenta_concrete"),
        e(17, 102, 153, 216, "light_blue_concrete"),
        e(18, 229, 229, 51, "yellow_concrete"),
        e(19, 127, 204, 25, "lime_concrete"),
        e(20, 242, 127, 165, "pink_concrete"),
        e(21, 76, 76, 76, "gray_concrete"),
        e(22, 153, 153, 153, "light_gray_concrete"),
        e(23, 76, 127, 153, "cyan_concrete"),
        e(24, 127, 63, 178, "purple_concrete"),
        e(25, 51, 76, 178, "blue_concrete"),
        e(26, 102, 76, 51, "brown_concrete"),
        e(27, 102, 127, 51, "green_concrete"),
        e(28, 153, 51, 51, "red_concrete"),
        e(29, 25, 25, 25, "black_concrete"),
        e(30, 250, 238, 77, "gold_block"),
        e(31, 92, 219, 213, "diamond_block"),
        e(32, 74, 128, 255, "lapis_block"),
        e(33, 0, 217, 58, "emerald_block"),
        e(34, 129, 86, 49, "spruce_planks"),
        e(35, 112, 2, 0, "netherrack"),
        e(36, 209, 177, 161, "white_terracotta"),
        e(37, 159, 82, 36, "orange_terracotta"),
        e(38, 149, 87, 108, "magenta_terracotta"),
        e(39, 112, 108, 138, "light_blue_terracotta"),
        e(40, 186, 133, 36, "yellow_terracotta"),
        e(41, 103, 117, 53, "lime_terracotta"),
        e(42, 160, 77, 78, "pink_terracotta"),
        e(43, 57, 41, 35, "gray_terracotta"),
        e(44, 135, 107, 98, "light_gray_terracotta"),
        e(45, 87, 92, 92, "cyan_terracotta"),
        e(46, 122, 73, 88, "purple_terracotta"),
        e(47, 76, 62, 92, "blue_terracotta"),
        e(48, 76, 50, 35, "brown_terracotta"),
        e(49, 76, 82, 42, "green_terracotta"),
        e(50, 142, 60, 46, "red_terracotta"),
        e(51, 37, 22, 16, "black_terracotta"),
        e(52, 189, 48, 49, "crimson_nylium"),
        e(53, 148, 63, 97, "crimson_planks"),
        e(54, 92, 25, 29, "crimson_hyphae"),
        e(55, 22, 126, 134, "warped_nylium"),
        e(56, 58, 142, 140, "warped_planks"),
        e(57, 86, 44, 62, "warped_hyphae"),
        e(58, 20, 180, 133, "warped_wart_block"),
        e(59, 100, 100, 100, "deepslate"),
        e(60, 216, 175, 147, "raw_iron_block"),
        e(61, 127, 167, 150, "verdant_froglight")
    );

    /** Carpet mode: the 16 wool carpets (same map colours as concrete), each sitting on a support block. */
    public static final List<Entry> CARPETS = List.of(
        e(8, 255, 255, 255, "white_carpet"),
        e(15, 216, 127, 51, "orange_carpet"),
        e(16, 178, 76, 216, "magenta_carpet"),
        e(17, 102, 153, 216, "light_blue_carpet"),
        e(18, 229, 229, 51, "yellow_carpet"),
        e(19, 127, 204, 25, "lime_carpet"),
        e(20, 242, 127, 165, "pink_carpet"),
        e(21, 76, 76, 76, "gray_carpet"),
        e(22, 153, 153, 153, "light_gray_carpet"),
        e(23, 76, 127, 153, "cyan_carpet"),
        e(24, 127, 63, 178, "purple_carpet"),
        e(25, 51, 76, 178, "blue_carpet"),
        e(26, 102, 76, 51, "brown_carpet"),
        e(27, 102, 127, 51, "green_carpet"),
        e(28, 153, 51, 51, "red_carpet"),
        e(29, 25, 25, 25, "black_carpet")
    );

    public static final String SUPPORT_BLOCK = "minecraft:cobblestone";

    public static List<Entry> palette(boolean carpets) { return carpets ? CARPETS : ENTRIES; }

    private static Entry e(int id, int r, int g, int b, String block) {
        return new Entry(id, r, g, b, "minecraft:" + block, "");
    }

    /** Colour as it actually appears on the map for a given shade. */
    public static int shadedRgb(Entry en, int shade) {
        int m = SHADE_MUL[shade];
        return ((en.r() * m / 255) << 16) | ((en.g() * m / 255) << 8) | (en.b() * m / 255);
    }

    private MapColors() {}
}
