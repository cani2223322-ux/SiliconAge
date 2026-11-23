package com.sc.embassy;

import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockSettlementDecorSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbHangar" - the «Ангар» 27 x 21, H_HANGAR (5) high (Field.hangar: its walls of the trim's metal, a flat roof, its gate the whole +a face -
 * towards the apron, 21 wide and 4 high; a lamp row inside). Never higher than H_HANGAR (the transitional surface).
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy -1 .. H_HANGAR + 1). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: a concrete floor (APRON) with a yellow line in from the gate to a stop bar by the back wall; the back and the two sides steel
 * (the trim's) on titanium posts every POST_STEP (and at the gate's edges), a lead glass band at WINDOW_DY between the posts; the +a
 * face open its whole width, H_HANGAR - 1 high; a flat steel roof on top (H_HANGAR) - a row of lit nodes down its middle, more over
 * the gate.
 */
public class ComponentEmbHangarSC extends ComponentEmbAirSC {

    /** The side walls' posts every this many cells (the back's every BACK_STEP); the windows' row; the stop bar this far in from the back; its half width. */
    private static final int POST_STEP = 6, BACK_STEP = 5, WINDOW_DY = 3, STOP_U = 3, STOP_HALF = 3;
    /** The roof's lamps: down the middle every LAMP_STEP (from LAMP_FROM), over the gate every GATE_LAMP_STEP. */
    private static final int LAMP_STEP = 5, LAMP_FROM = 3, GATE_LAMP_STEP = 4;

    public ComponentEmbHangarSC() {
    }

    public ComponentEmbHangarSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyAirfieldSC.H_HANGAR + 1);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        int[] h = f.hangar;
        if (in == null || h == null || !trimReady() || EmbassyBlocksSC.airfield == null) {
            return;
        }
        int[] c = clipq(h, in);
        if (c == null) {
            return;
        }
        Block decor = SettlementBlocksSC.decor;
        int len = h[2] - h[0], wid = h[3] - h[1], mid = wid / 2, roof = EmbassyAirfieldSC.H_HANGAR;
        for (int a = c[0]; a <= c[2]; a++) {
            for (int q = c[1]; q <= c[3]; q++) {
                int u = a - h[0], v = q - h[1];
                boolean gate = u == len, side = v == 0 || v == wid, back = u == 0;
                surface(w, in, a, 0, q, meta(h, a, q));
                for (int dy = 1; dy < roof; dy++) {
                    if (gate || !side && !back) {
                        airq(w, in, a, dy, q);
                    } else {
                        boolean post = side && (u == 0 || u == len - 1 || u % POST_STEP == 0) || back && v % BACK_STEP == 0;
                        if (post) {
                            atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.TITANIUM);
                        } else if (dy == WINDOW_DY) {
                            atq(w, in, a, dy, q, SettlementBlocksSC.glass, 0);
                        } else {
                            atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.STEEL);
                        }
                    }
                }
                boolean lamp = v == mid && u >= LAMP_FROM && (u - LAMP_FROM) % LAMP_STEP == 0 || gate && v % GATE_LAMP_STEP == 2;
                atq(w, in, a, roof, q, decor, lamp ? BlockSettlementDecorSC.NODE : BlockSettlementDecorSC.STEEL);
            }
        }
    }

    /** The hangar `h`'s floor at (a, q): the yellow line in from the gate to the stop bar, concrete elsewhere. Pure. */
    static int meta(int[] h, int a, int q) {
        int u = a - h[0], v = q - h[1], mid = (h[3] - h[1]) / 2;
        boolean line = v == mid && u > STOP_U || u == STOP_U && Math.abs(v - mid) <= STOP_HALF;
        return line ? BlockAirfieldSC.MARK_YELLOW : BlockAirfieldSC.APRON;
    }
}
