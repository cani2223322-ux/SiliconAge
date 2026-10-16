package com.sc.dim;

import com.sc.bridge.BridgeSpaceSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE E (docs/plan-substrate-tech.md §6.4): a place to put a player down at - two cells one walks through (air,
 * grass, snow, a torch: BridgeSpaceSC's AIR / PASS, the bridge's own landing words) over a solid one; never in fire, a
 * portal or a web, never on a cactus, never in a bridge vortex, never in someone else's private field. TeleportQueueSC
 * asks it before every move it makes (strictEntry's way back now, the Return Capsule in Ф2).
 * <p>
 * The field check is the quiet one (TileEntityFieldGeneratorSC.privateRefusing): ShieldEventHandler.privateFor tells
 * the player in chat and is no good for trying a few hundred places. The chunks round the point are loaded first
 * (generated if they never were) - server side, a few times per refused arrival, never every tick.
 */
public final class SafeSpotSC {

    /** The first pass looks this far up and down every column (the nearest place wins); the second, the whole column. */
    public static final int NEAR_SPAN = 6;

    private SafeSpotSC() {
    }

    /**
     * The nearest place round (x, y, z) within `radius` columns (rings outwards; in each column up and down from y),
     * or null. @return {x, feet y, z} of the block cells (stand at x + 0.5, y, z + 0.5)
     */
    public static int[] find(WorldServer w, EntityPlayer p, int x, int y, int z, int radius) {
        if (w == null) {
            return null;
        }
        int r = Math.max(0, radius);
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                w.theChunkProviderServer.loadChunk(cx, cz);
            }
        }
        int top = topFeet(w);
        if (top < 1) {
            return null;
        }
        int y0 = Math.max(1, Math.min(top, y));
        BridgeSpaceSC.Cells cells = BridgeSpaceSC.of(w);
        for (int pass = 0; pass < 2; pass++) {
            int span = pass == 0 ? NEAR_SPAN : top;
            for (int ring = 0; ring <= r; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;                          // inside: an earlier ring had it
                        }
                        int fy = column(w, cells, p, x + dx, y0, z + dz, span, top);
                        if (fy >= 0) {
                            return new int[]{x + dx, fy, z + dz};
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Where to start looking at (x, z) of a world's spawn: the top of the column in a world with a sky, `y` (the spawn's
     * own) under a roof - the Nether's top is bedrock, a place on it is no way back. Loads the chunk.
     */
    public static int surfaceY(WorldServer w, int x, int y, int z) {
        int top = topFeet(w);
        if (!w.provider.hasNoSky) {
            w.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
            int t = w.getTopSolidOrLiquidBlock(x, z);
            if (t > 0) {
                return Math.max(1, Math.min(top, t));
            }
        }
        return Math.max(1, Math.min(top, y));
    }

    /** A player fits at feet y of column (x, z) - see the class comment. */
    public static boolean fits(WorldServer w, EntityPlayer p, int x, int y, int z) {
        return y >= 1 && y <= topFeet(w) && fits(w, BridgeSpaceSC.of(w), p, x, y, z);
    }

    private static boolean fits(WorldServer w, BridgeSpaceSC.Cells cells, EntityPlayer p, int x, int y, int z) {
        if (cells.cell(x, y - 1, z) != BridgeSpaceSC.SOLID || w.getBlock(x, y - 1, z).getMaterial() == Material.cactus) {
            return false;
        }
        for (int dy = 0; dy < 2; dy++) {
            int c = cells.cell(x, y + dy, z);
            if ((c != BridgeSpaceSC.AIR && c != BridgeSpaceSC.PASS) || hurts(w.getBlock(x, y + dy, z).getMaterial())) {
                return false;
            }
        }
        // the quiet check: a nameless asker (no player) is refused by every owned private field
        return (p != null ? TileEntityFieldGeneratorSC.privateRefusing(w, p, x + 0.5, y + 0.5, z + 0.5)
                : TileEntityFieldGeneratorSC.privateRefusingName(w, "", x + 0.5, y + 0.5, z + 0.5)) == null;
    }

    /** One walks through these, but nobody should be put down in them: fire, a portal (it would carry them on), a web. */
    private static boolean hurts(Material m) {
        return m == Material.fire || m == Material.portal || m == Material.web;
    }

    /** One column from y0 outwards (up first), at most `span` each way. -1: nothing. */
    private static int column(WorldServer w, BridgeSpaceSC.Cells cells, EntityPlayer p, int x, int y0, int z, int span, int top) {
        for (int d = 0; d <= span; d++) {
            int up = y0 + d, down = y0 - d;
            if (up > top && down < 1) {
                return -1;
            }
            if (up <= top && fits(w, cells, p, x, up, z)) {
                return up;
            }
            if (d > 0 && down >= 1 && fits(w, cells, p, x, down, z)) {
                return down;
            }
        }
        return -1;
    }

    /** The highest feet y: the head's cell still inside the world - under the roof of a world without a sky (128 in the Nether). */
    private static int topFeet(WorldServer w) {
        return w.getActualHeight() - 2;
    }
}
