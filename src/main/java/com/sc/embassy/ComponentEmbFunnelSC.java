package com.sc.embassy;

import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbFunnel" - an approach funnel (LOT_FUNNEL, FULL; variant 0 - the -a end's, 1 - the +a end's): at the first build (never the «Достройка»'s
 * job past the square - but once more when its window is settled, EmbassyRepairSC.settle: the review's) it clears growth (logs, leaves, vines, huge mushrooms - ComponentEmbGroundSC.foreign's) over the 1:FUNNEL_SLOPE
 * plane rising from that end's threshold, FUNNEL_HALF either side of the axis, out to +-FUNNEL_A - only in its windows (the start made
 * before them: RANGE 10 covers a = 150). It sets no block.
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy 0 .. 64). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: per column of the funnel in the window (and the box), every growth block (ComponentEmbAirKitSC.growth - the tall part of
 * foreign: logs and any wood, leaves, vines, huge mushrooms, cactus; not snow, ice or flowers past the square) whose height over the
 * floor is more than its distance from the threshold / FUNNEL_SLOPE (problem()'s rule for a building) goes - flag 2, as the ground
 * piece clears, up to the chunk's top filled section; the land itself (a hill past the end) stays.
 */
public class ComponentEmbFunnelSC extends ComponentEmbAirSC {

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
        clear(w, f, ComponentEmbAirKitSC.inside(win, boundingBox));
    }

    /**
     * The populate of chunk `chunk` (EmbassyWorldGenSC.generate, after the window): the funnel's growth in that chunk's own columns
     * once more - another mod's generator (IC2's rubber trees at cx * 16 + rand(16)) plants there after our window has run. The chunk
     * itself only (loaded: it is being populated), nothing cascades.
     */
    void clearChunk(World w, StructureBoundingBox chunk) {
        EmbassyAirfieldSC.Field f = field();
        if (f != null && variant >= 0 && variant < f.funnels.length) {
            clear(w, f, ComponentEmbAirKitSC.inside(chunk, boundingBox));
        }
    }

    /** The growth over the 1:FUNNEL_SLOPE plane of this end inside `in` (the piece's box already; null: none) - flag 2. */
    private void clear(World w, EmbassyAirfieldSC.Field f, StructureBoundingBox in) {
        int[] c = in == null ? null : clipq(f.funnels[variant], in);
        if (c == null) {
            return;
        }
        boolean low = variant == 0, settling = EmbassyRepairSC.settling();
        for (int a = c[0]; a <= c[2]; a++) {
            int dist = low ? f.thresholdLow - a : a - f.thresholdHigh;
            int y0 = Math.max(in.minY, floorY + Math.max(0, dist) / EmbassyAirfieldSC.FUNNEL_SLOPE + 1);
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
    }
}
