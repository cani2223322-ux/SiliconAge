package com.sc.embassy;

import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockSettlementDecorSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbApron" - the apron (LOT_APRON, FULL): the paving (APRON), the stands «МС-1..4» (Field.spots: the yellow outline and number, MARK_YELLOW),
 * the service columns (BlockShowcase2SC.SERVICE_COLUMN at exactly Field.columnBlock(i) of the apron's stands, their front to the
 * stand), the monument «Последний челнок Литографов» (Field.monument: a shuttle's wreck of the trim and quartz, H_MONUMENT high at
 * most), the apron's fence (its outer edge, a gate to the terminal).
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy -1 .. H_MONUMENT + 1). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: the stands are entered from the lane (heading +a, the nose to the columns): each has its yellow outline, a lead-in line along
 * its middle to a stop bar STOP_IN short of the far edge, and its number (1..4, a 3 x 5 glyph read heading in) left of the line by
 * the entry; a column's cell is yellow under it. The fence (iron bars, H_FENCE high, a lit trim node at each end and either side of a
 * gate): along the outer edge (a = the apron's far end - the columns stand in its line) and along the landside edge facing the
 * terminal (q = the apron's near edge, from the monument on), its gate in front of the terminal's apron door; never on a guard's post
 * (Field.posts: the Sentry's by the monument stays free). The monument: the Lithographers' last shuttle frozen in its take-off on a
 * quartz plinth - delta wings flat on it, the titanium hull climbing a block a block towards +a and the sky, its engines' glow (a
 * lit node) at the tail, a twin fin, the lead glass canopy under the steel nose - all within the 6 x 6 and H_MONUMENT.
 */
public class ComponentEmbApronSC extends ComponentEmbAirSC {

    /** A stand's stop bar this far in from its far edge (half its width either side of the line); its number this far in from the entry, this far left of the line. */
    private static final int STOP_IN = 3, STOP_HALF = 2, NUMBER_IN = 2, NUMBER_OFF = 3;
    /** The gate in the landside fence: its half width (the terminal's apron door is 3 wide). */
    private static final int GATE_HALF = 1;
    /**
     * The monument by layer (dy 1..H_MONUMENT), each by q (v from the apron's near edge) a row along a (u): c - chiseled quartz, q -
     * quartz, t - titanium, s - steel, l - aluminium, g - lead glass, n - a lit trace node, '.' - air (all the trim's looks).
     */
    private static final String[][] MONUMENT = {
            {"cccccc", "cqqqqc", "cqqqqc", "cqqqqc", "cqqqqc", "cccccc"},
            {"ll....", "lll...", "ntt...", "ntt...", "lll...", "ll...."},
            {"......", "......", "s.tt..", "s.tt..", "......", "......"},
            {"......", "......", "s..tt.", "s..tt.", "......", "......"},
            {"......", "......", "....tg", "....tg", "......", "......"},
            {"......", "......", ".....s", ".....s", "......", "......"}};

    public ComponentEmbApronSC() {
    }

    public ComponentEmbApronSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyAirfieldSC.H_MONUMENT + 1);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        if (in == null || f.apron == null || EmbassyBlocksSC.airfield == null) {
            return;
        }
        int[] c = clipq(f.apron, in);
        if (c != null) {
            for (int a = c[0]; a <= c[2]; a++) {
                for (int q = c[1]; q <= c[3]; q++) {
                    surface(w, in, a, 0, q, meta(f, a, q));
                }
            }
        }
        for (int i = 0; i < f.columns.length; i++) {
            if (f.spotKinds[i] == EmbassyAirfieldSC.SPOT_HELI) {
                continue;                                       // the helipad's: its own piece
            }
            int[] col = f.columns[i], s = f.spots[i];
            int da = ComponentEmbAirKitSC.toward(col[0], clamp(col[0], s[0], s[2])), dq = ComponentEmbAirKitSC.toward(col[1], clamp(col[1], s[1], s[3]));
            if (da != 0 && dq != 0) {
                da = 0;
            }
            machineq(w, in, col[0], EmbassyAirfieldSC.COLUMN_DY, col[1], BlockShowcase2SC.SERVICE_COLUMN, da, dq);
        }
        if (trimReady()) {
            fence(w, in, f);
            monument(w, in, f);
        }
    }

    /** The apron's paving at (a, q): a stand's yellow marks, a column's yellow cell, plain concrete elsewhere. Pure. */
    static int meta(EmbassyAirfieldSC.Field f, int a, int q) {
        for (int i = 0; i < f.spots.length; i++) {
            if (f.spotKinds[i] != EmbassyAirfieldSC.SPOT_HELI && EmbassyAirfieldSC.in(f.spots[i], a, q)) {
                return stand(f, f.spots[i], i + 1, a, q) ? BlockAirfieldSC.MARK_YELLOW : BlockAirfieldSC.APRON;
            }
        }
        return column(f, a, q) ? BlockAirfieldSC.MARK_YELLOW : BlockAirfieldSC.APRON;
    }

    /** Stand `s` (number `n`)'s yellow at (a, q): the outline, the lead-in line, the stop bar, the number (see the class comment). */
    private static boolean stand(EmbassyAirfieldSC.Field f, int[] s, int n, int a, int q) {
        if (a == s[0] || a == s[2] || q == s[1] || q == s[3]) {
            return true;
        }
        int mid = (s[1] + s[3]) / 2, stop = s[2] - STOP_IN;
        if (q == mid && a < stop || a == stop && Math.abs(q - mid) <= STOP_HALF) {
            return true;
        }
        String t = String.valueOf(n);
        int base = s[0] + NUMBER_IN, nq = mid - NUMBER_OFF * f.side;
        return a >= base && a < base + ComponentEmbAirKitSC.GLYPH_H
                && ComponentEmbAirKitSC.pixel(t, ComponentEmbAirKitSC.width(t) / 2 + (q - nq) * f.side, ComponentEmbAirKitSC.GLYPH_H - 1 - (a - base));
    }

    /** A column of the apron's stands stands at (a, q). */
    private static boolean column(EmbassyAirfieldSC.Field f, int a, int q) {
        for (int i = 0; i < f.columns.length; i++) {
            if (f.spotKinds[i] != EmbassyAirfieldSC.SPOT_HELI && f.columns[i][0] == a && f.columns[i][1] == q) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** The apron's fence: its outer edge (the columns in its line) and its landside edge from the monument on, the gate to the terminal. */
    private void fence(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f) {
        int[] ap = f.apron;
        int outer = ap[2], land = ap[1], from = f.monument != null ? f.monument[2] + 1 : ap[0];
        int gate = f.terminal != null ? (f.terminal[0] + f.terminal[2]) / 2 : Integer.MIN_VALUE;
        int[] c = clipq(EmbassyAirfieldSC.rect(outer, land, outer, ap[3]), in);
        if (c != null) {
            for (int q = c[1]; q <= c[3]; q++) {
                fencePost(w, in, f, outer, q, q == land || q == ap[3]);
            }
        }
        c = clipq(EmbassyAirfieldSC.rect(from, land, outer - 1, land), in);
        if (c != null) {
            for (int a = c[0]; a <= c[2]; a++) {
                int d = Math.abs(a - gate);
                if (d > GATE_HALF) {
                    fencePost(w, in, f, a, land, a == from || d == GATE_HALF + 1);
                }
            }
        }
    }

    /** One cell of the fence: a lit node (`post`) or the bars; nothing on a column's or a guard's cell. */
    private void fencePost(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f, int a, int q, boolean post) {
        if (column(f, a, q) || ComponentEmbAirKitSC.post(f, a, q)) {
            return;
        }
        if (post) {
            atq(w, in, a, EmbassyAirfieldSC.H_FENCE, q, SettlementBlocksSC.decor, BlockSettlementDecorSC.NODE);
        } else {
            atq(w, in, a, EmbassyAirfieldSC.H_FENCE, q, Blocks.iron_bars, 0);
        }
    }

    /** «Последний челнок Литографов» on Field.monument (MONUMENT's layers). */
    private void monument(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f) {
        int[] m = f.monument;
        if (m == null) {
            return;
        }
        int nu = Math.min(m[2] - m[0] + 1, MONUMENT[0][0].length()), nv = Math.min(m[3] - m[1] + 1, MONUMENT[0].length);
        for (int dy = 1; dy <= Math.min(MONUMENT.length, EmbassyAirfieldSC.H_MONUMENT); dy++) {
            String[] layer = MONUMENT[dy - 1];
            for (int v = 0; v < nv; v++) {
                for (int u = 0; u < nu; u++) {
                    char ch = layer[v].charAt(u);
                    int a = m[0] + u, q = m[1] + v;
                    if (ch == '.') {
                        airq(w, in, a, dy, q);
                    } else if (ch == 'g') {
                        atq(w, in, a, dy, q, SettlementBlocksSC.glass, 0);
                    } else {
                        atq(w, in, a, dy, q, SettlementBlocksSC.decor, trim(ch));
                    }
                }
            }
        }
    }

    /** The trim's meta of a monument's letter. */
    private static int trim(char ch) {
        switch (ch) {
            case 'c':
                return BlockSettlementDecorSC.QUARTZ_CHISELED;
            case 'q':
                return BlockSettlementDecorSC.QUARTZ;
            case 't':
                return BlockSettlementDecorSC.TITANIUM;
            case 'l':
                return BlockSettlementDecorSC.ALUMINIUM;
            case 'n':
                return BlockSettlementDecorSC.NODE;
            default:
                return BlockSettlementDecorSC.STEEL;
        }
    }
}
