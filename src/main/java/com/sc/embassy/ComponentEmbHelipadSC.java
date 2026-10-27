package com.sc.embassy;

import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbHelipad" - the helipad «Н» 11 x 11 (Field.helipad, every kind: APRON paving, the white «Н» and circle MARK_WHITE, LIGHT_TAXI round its
 * edge), its service column (SERVICE_COLUMN at exactly the helipad's Field.columnBlock - the last).
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy -1 .. H_COLUMN + 1). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: concrete; a blue taxi light on every other cell of the edge (the corners lit - 20 lamps, the field's only lights off
 * Field.lights); the white circle (radius 4) round the white «Н» (its strokes along a, 5 long, 4 apart); the column on its own
 * concrete step past the edge, its front to the pad, a yellow cell under it.
 */
public class ComponentEmbHelipadSC extends ComponentEmbAirSC {

    /** The circle's squared radius band (cells 4 out) and the «Н»'s half size (its strokes HALF off the middle, HALF long either way). */
    private static final int RING_IN2 = 12, RING_OUT2 = 20, H_HALF = 2;

    public ComponentEmbHelipadSC() {
    }

    public ComponentEmbHelipadSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyAirfieldSC.H_COLUMN + 1);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        int[] h = f.helipad;
        if (in == null || h == null || EmbassyBlocksSC.airfield == null) {
            return;
        }
        int[] c = clipq(h, in);
        if (c != null) {
            for (int a = c[0]; a <= c[2]; a++) {
                for (int q = c[1]; q <= c[3]; q++) {
                    surface(w, in, a, 0, q, meta(h, a, q));
                }
            }
        }
        int[] col = f.columns[f.columns.length - 1];
        for (int da = -1; da <= 1; da++) {
            surface(w, in, col[0] + da, 0, col[1], da == 0 ? BlockAirfieldSC.MARK_YELLOW : BlockAirfieldSC.APRON);
        }
        int dq = ComponentEmbAirKitSC.toward(col[1], Math.max(h[1], Math.min(h[3], col[1])));
        int da = dq != 0 ? 0 : ComponentEmbAirKitSC.toward(col[0], Math.max(h[0], Math.min(h[2], col[0])));
        machineq(w, in, col[0], EmbassyAirfieldSC.COLUMN_DY, col[1], BlockShowcase2SC.SERVICE_COLUMN, da, dq);
    }

    /** The helipad's meta at (a, q) of `h`: the edge's lights, the circle, the «Н», concrete. Pure. */
    static int meta(int[] h, int a, int q) {
        int u = a - h[0], v = q - h[1], n = h[2] - h[0], du = u - n / 2, dv = v - (h[3] - h[1]) / 2;
        if (u == 0 || v == 0 || u == n || v == h[3] - h[1]) {
            return (u + v) % 2 == 0 ? BlockAirfieldSC.LIGHT_TAXI : BlockAirfieldSC.APRON;
        }
        int d2 = du * du + dv * dv;
        boolean ring = d2 > RING_IN2 && d2 <= RING_OUT2;
        boolean letter = Math.abs(dv) == H_HALF && Math.abs(du) <= H_HALF || du == 0 && Math.abs(dv) < H_HALF;
        return ring || letter ? BlockAirfieldSC.MARK_WHITE : BlockAirfieldSC.APRON;
    }
}
