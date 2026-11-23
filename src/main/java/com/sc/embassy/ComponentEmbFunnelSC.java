package com.sc.embassy;

import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbFunnel" - an approach funnel (LOT_FUNNEL, FULL; variant 0 - the -a end's, 1 - the +a end's): at the first build (never the «Достройка»'s
 * job past the square - but once more when its window is settled, EmbassyRepairSC.settle: the review's) it clears growth (logs, leaves, vines, huge mushrooms - ComponentEmbGroundSC.foreign's) over the 1:FUNNEL_SLOPE
 * plane rising from that end's threshold, FUNNEL_HALF either side of the axis, out to +-FUNNEL_A (an ILS end of a LONG_FUNNELS decision:
 * FUNNEL_ILS_OUT past its threshold - ЛП-2 of the audit 2026-11-04) - only in its windows (the start made before them: MapGenEmbassySC.RANGE
 * covers the funnels' reach). It sets no block.
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy 0 .. 64). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: per column of the funnel in the window (and the box), every growth block (ComponentEmbAirKitSC.growth - the tall part of
 * foreign: logs and any wood, leaves, vines, huge mushrooms, cactus; not snow, ice or flowers past the square) whose height over the
 * floor is more than its distance from the threshold / FUNNEL_SLOPE (problem()'s rule for a building) goes - flag 2, as the ground
 * piece clears, up to the chunk's top filled section; the land itself (a hill past the end) stays.
 * <p>
 * The audit 2026-11-04: ЛП-6 - in later chunks only within EmbassyAirfieldSC.FUNNEL_DAYS of the record's making (funnelsOpen: the land may
 * be the players' later); ЛП-7 - the leaves (and vines) over the plane that hang from the cleared columns into a neighbour chunk already
 * built are taken too: OVERHANG round the cleared part, in the funnel's corridor only, only in loaded chunks (nothing is loaded), never
 * over a player's base.
 */
public class ComponentEmbFunnelSC extends ComponentEmbAirSC {

    /** ЛП-7: how far past the cleared part (blocks across) the leaves over the plane go too - a tree's crown off its trunk. */
    public static final int OVERHANG = 6;

    public ComponentEmbFunnelSC() {
    }

    public ComponentEmbFunnelSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, 0, 64);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        if (EmbassyRepairSC.repairing() && !EmbassyRepairSC.settling() || variant < 0 || variant >= f.funnels.length) {
            return;                                             // the first build and the settling only: past the square nobody keeps the land
        }
        if (EmbassyRepairSC.settling() && (f.ils & (variant == 0 ? EmbassyAirfieldSC.ILS_LOW : EmbassyAirfieldSC.ILS_HIGH)) == 0) {
            return;                                             // an end without ILS: its funnel was on disk (or the biome didn't fit) - old land isn't cleared again
        }
        if (!EmbassyAirfieldSC.funnelsOpen(w, settlementId)) {
            return;                                             // ЛП-6: FUNNEL_DAYS past the record's making - the land may be the players'
        }
        clear(w, f, ComponentEmbAirKitSC.inside(win, boundingBox));
    }

    /**
     * The populate of chunk `chunk` (EmbassyWorldGenSC.generate, after the window): the funnel's growth in that chunk's own columns
     * once more - another mod's generator (IC2's rubber trees at cx * 16 + rand(16)) plants there after our window has run. The chunk
     * itself (loaded: it is being populated) and the leaves hanging from it into a loaded neighbour (ЛП-7), nothing cascades.
     */
    void clearChunk(World w, StructureBoundingBox chunk) {
        EmbassyAirfieldSC.Field f = field();
        if (f != null && variant >= 0 && variant < f.funnels.length && EmbassyAirfieldSC.funnelsOpen(w, settlementId)) {
            clear(w, f, ComponentEmbAirKitSC.inside(chunk, boundingBox));
        }
    }

    /**
     * The growth over the 1:FUNNEL_SLOPE plane of this end inside `in` (the piece's box already; null: none) - flag 2; then (ЛП-7) the
     * leaves over the plane OVERHANG round `in`, in the piece's box, in loaded chunks only.
     */
    private void clear(World w, EmbassyAirfieldSC.Field f, StructureBoundingBox in) {
        int[] c = in == null ? null : clipq(f.funnels[variant], in);
        if (c == null) {
            return;
        }
        boolean settling = EmbassyRepairSC.settling();
        for (int a = c[0]; a <= c[2]; a++) {
            int y0 = Math.max(in.minY, planeY(f, a));
            for (int q = c[1]; q <= c[3]; q++) {
                int x = f.x(a, q), z = f.z(a, q);
                if (settling && EmbassyAirfieldSC.playerBaseAt(w, x, z)) {
                    continue;                                   // a player's base: the land is the players'
                }
                int y1 = Math.min(in.maxY, w.getChunkFromBlockCoords(x, z).getTopFilledSegment() + 15);
                for (int y = y0; y <= y1; y++) {
                    Block b = w.getBlock(x, y, z);
                    if (b.getMaterial() != Material.air && ComponentEmbAirKitSC.growth(w, b, x, y, z)) {
                        w.setBlock(x, y, z, Blocks.air, 0, 2);
                    }
                }
            }
        }
        overhang(w, f, in);
    }

    /** The first block over this end's 1:FUNNEL_SLOPE plane at frame row `a`. */
    private int planeY(EmbassyAirfieldSC.Field f, int a) {
        int dist = variant == 0 ? f.thresholdLow - a : a - f.thresholdHigh;
        return floorY + Math.max(0, dist) / EmbassyAirfieldSC.FUNNEL_SLOPE + 1;
    }

    /**
     * ЛП-7: the leaves and vines over the plane in the funnel's columns OVERHANG round `in` (not in it: clear did those) - in the piece's
     * box, only where the column's chunk is loaded, never over a player's base. Flag 2.
     */
    private void overhang(World w, EmbassyAirfieldSC.Field f, StructureBoundingBox in) {
        StructureBoundingBox ring = ComponentEmbAirKitSC.inside(new StructureBoundingBox(in.minX - OVERHANG, in.minY, in.minZ - OVERHANG,
                in.maxX + OVERHANG, in.maxY, in.maxZ + OVERHANG), boundingBox);
        int[] c = ring == null ? null : clipq(f.funnels[variant], ring);
        IChunkProvider cp = w.getChunkProvider();
        if (c == null || cp == null) {
            return;
        }
        for (int a = c[0]; a <= c[2]; a++) {
            int y0 = Math.max(ring.minY, planeY(f, a));
            for (int q = c[1]; q <= c[3]; q++) {
                int x = f.x(a, q), z = f.z(a, q);
                if (x >= in.minX && x <= in.maxX && z >= in.minZ && z <= in.maxZ || !cp.chunkExists(x >> 4, z >> 4)
                        || EmbassyAirfieldSC.playerBaseAt(w, x, z)) {
                    continue;                                   // done already / not loaded (nothing is loaded for it) / a player's base
                }
                int y1 = Math.min(ring.maxY, w.getChunkFromBlockCoords(x, z).getTopFilledSegment() + 15);
                for (int y = y0; y <= y1; y++) {
                    Block b = w.getBlock(x, y, z);
                    Material m = b.getMaterial();
                    if (m != Material.air && (m == Material.leaves || m == Material.vine || b.isLeaves(w, x, y, z))) {
                        w.setBlock(x, y, z, Blocks.air, 0, 2);
                    }
                }
            }
        }
    }
}
