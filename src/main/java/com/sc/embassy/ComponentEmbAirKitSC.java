package com.sc.embassy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.block.BlockAirfieldSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): what the airfield's pieces share - never registered, no state (pure but for
 * growth's block reads). The window cut to a piece's own box (inside: a piece never sets a block past its lot - its parts in a window
 * its box misses would never be built), the painted glyphs (3 x 5 digits: the runway's numbers, the stands' «МС» numbers), the lights'
 * fixed places by cell (Field.lights as a map), the vanilla stairs' metas by the way they climb, the funnels' growth.
 */
final class ComponentEmbAirKitSC {

    /** The digits 0..9, 3 x 5, rows top to bottom (a bit set: the column's cell is painted). */
    private static final String[] DIGITS = {"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111"};
    /** A glyph is this wide and long; a column of gap between two. */
    static final int GLYPH_W = 3, GLYPH_H = 5;
    /** Vanilla stairs' metas by the way they climb: NORTH, EAST, SOUTH, WEST (BlockStairs: 0 east, 1 west, 2 south, 3 north). */
    private static final int[] STAIR_META = {3, 0, 2, 1};

    private ComponentEmbAirKitSC() {
    }

    /** The part of window `win` inside the piece's box `box` (null: none) - every block of a piece goes in through it. */
    static StructureBoundingBox inside(StructureBoundingBox win, StructureBoundingBox box) {
        int x0 = Math.max(win.minX, box.minX), y0 = Math.max(win.minY, box.minY), z0 = Math.max(win.minZ, box.minZ);
        int x1 = Math.min(win.maxX, box.maxX), y1 = Math.min(win.maxY, box.maxY), z1 = Math.min(win.maxZ, box.maxZ);
        return x0 > x1 || y0 > y1 || z0 > z1 ? null : new StructureBoundingBox(x0, y0, z0, x1, y1, z1);
    }

    /**
     * Text `text`'s (digits; anything else blank) cell at column `c` (0 its left, a gap column between two glyphs) and row `row` (0 its
     * top) is painted. Pure.
     */
    static boolean pixel(String text, int c, int row) {
        if (text == null || c < 0 || row < 0 || row >= GLYPH_H) {
            return false;
        }
        int g = c / (GLYPH_W + 1), col = c % (GLYPH_W + 1);
        if (g >= text.length() || col >= GLYPH_W) {
            return false;
        }
        int d = text.charAt(g) - '0';
        return d >= 0 && d <= 9 && DIGITS[d].charAt(row * GLYPH_W + col) == '1';
    }

    /** The width of `text` in glyph columns (its gaps counted). */
    static int width(String text) {
        return text == null || text.length() == 0 ? 0 : text.length() * (GLYPH_W + 1) - 1;
    }

    /** A frame cell's key (the lights' map). */
    static long key(int a, int q) {
        return ((long) a << 32) ^ (q & 0xFFFFFFFFL);
    }

    /** Field.lights of the runway's and taxiway's four metas as {cell key -> meta} - the exact places the pieces put them. Pure. */
    static Map<Long, Integer> lights(EmbassyAirfieldSC.Field f) {
        Map<Long, Integer> m = new HashMap<Long, Integer>();
        int[] metas = {BlockAirfieldSC.LIGHT_EDGE, BlockAirfieldSC.LIGHT_TAXI, BlockAirfieldSC.LIGHT_THRESHOLD, BlockAirfieldSC.LIGHT_END};
        for (int meta : metas) {
            List<int[]> l = f.lights(meta);
            for (int[] c : l) {
                m.put(Long.valueOf(key(c[0], c[1])), Integer.valueOf(meta));
            }
        }
        return m;
    }

    /** A vanilla stair's meta climbing towards world way `dir` (NORTH..WEST; anything else: north). */
    static int stair(int dir) {
        return STAIR_META[dir >= 0 && dir < 4 ? dir : 0];
    }

    /** The cell (a, q) is one of the field's posts (the Sentries' and the Gas Engineer's - nothing stands over it). */
    static boolean post(EmbassyAirfieldSC.Field f, int a, int q) {
        if (f.posts == null) {
            return false;
        }
        for (int[] p : f.posts) {
            if (p[0] == a && p[1] == q) {
                return true;
            }
        }
        return false;
    }

    /**
     * The funnels' growth (ComponentEmbGroundSC.foreign's tall part): logs (and any wood - IC2's rubber, Thaumcraft's trees), leaves,
     * vines, huge mushrooms, cactus. Not snow, ice or low plants - a lake's ice or a flower past the square stays as it is.
     */
    static boolean growth(World w, Block b, int x, int y, int z) {
        if (b == Blocks.log || b == Blocks.log2 || b == Blocks.brown_mushroom_block || b == Blocks.red_mushroom_block || b.isWood(w, x, y, z)
                || b.isLeaves(w, x, y, z)) {
            return true;
        }
        Material m = b.getMaterial();
        return m == Material.leaves || m == Material.vine || m == Material.cactus;
    }

    /** -1, 0 or 1 - towards `to` from `from`. */
    static int toward(int from, int to) {
        return to > from ? 1 : to < from ? -1 : 0;
    }
}
