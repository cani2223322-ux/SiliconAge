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
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        int[] c = in == null ? null : clipq(f.funnels[variant], in);
        if (c == null) {
            return;
        }
        boolean low = variant == 0;
        for (int a = c[0]; a <= c[2]; a++) {
            int dist = low ? f.thresholdLow - a : a - f.thresholdHigh;
            int y0 = Math.max(in.minY, floorY + Math.max(0, dist) / EmbassyAirfieldSC.FUNNEL_SLOPE + 1);
            for (int q = c[1]; q <= c[3]; q++) {
                int x = f.x(a, q), z = f.z(a, q);
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
