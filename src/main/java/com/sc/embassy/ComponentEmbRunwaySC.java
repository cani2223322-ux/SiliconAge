package com.sc.embassy;

import java.util.Map;

import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockSettlementDecorSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbRunway" - the runway (LOT_RUNWAY, variant = the kind): the paving (BlockAirfieldSC.RUNWAY over Field.runway), the shoulders (SHOULDER, FULL),
 * the turning pads and taxiway «А» (APRON paving, the yellow centre line MARK_YELLOW), the white marks (MARK_WHITE: the thresholds'
 * «зебра», the displaced thresholds' arrows, the numbers - EmbassyAirfieldSC.number(heading) - as 3 x 5 glyphs at each end, the centre
 * line's dashes), EXACTLY the lights of Field.lights(LIGHT_END / LIGHT_THRESHOLD / LIGHT_EDGE / LIGHT_TAXI) flush at the floor, the
 * windsock's mast (Field.windsockBlock - vanilla fence and a wool cone over it), the low fence on the field's outer edge (q = the level
 * rectangle's edge; the ends open; gates in it). Only within the lot's box.
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy -1 .. H_WINDSOCK + 2). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: every cell of the paved part (the strip, the pads, the taxiway) gets one meta (meta()) - a light of Field.lights first (that
 * exact list: nothing else on the runway and the taxiway lights up), then the white marks on the runway, the yellow line on the pads
 * and the taxiway, the shoulder. The marks read for a pilot landing over that end (a glyph's top away from him, its left on his left):
 * from the end row (red) FULL's two arrows in the displaced part, the threshold row (green), the «зебра» (FULL four stripes, SHORT
 * three), the number (FULL inside the edge rows; SHORT full width - between two edge light rows), FULL's aiming point, then the centre
 * line's dashes up to the other end's number. Taxiway «А»'s yellow line runs its middle and turns at its start across the +a pad to
 * the runway's edge. The windsock's mast: a nether brick fence (not the oak one - no wood on the Tuners' field) on a white ringed pad,
 * an orange wool cone on top (the add-on draws the moving sock). The fence: iron bars H_FENCE high along the outer edge, lit trim nodes
 * at its ends and either side of each gate's gap; a cell off the level ground (SHORT's zone edge leaves the disc a few cells short of
 * the runway's ends - the fence ends there, a post on its last cell) or on a post of the field's guards (Field.posts) gets none.
 */
public class ComponentEmbRunwaySC extends ComponentEmbAirSC {

    /** The threshold's «зебра»: from this far in past the threshold row, this long (FULL / SHORT). */
    private static final int ZEBRA_IN = 2, ZEBRA_FULL = 8, ZEBRA_SHORT = 3;
    /** The number's nearest row this far in from the threshold (FULL / SHORT - SHORT's stands between two edge light rows). */
    private static final int NUMBER_FULL = 13, NUMBER_SHORT = 6;
    /** FULL: the aiming point - two bars 2 wide either side of the axis, this far in, this long. */
    private static final int AIM_IN = 30, AIM_LEN = 6;
    /** The centre line: dashes of 3 every DASH_STEP, kept DASH_GAP clear of the numbers. */
    private static final int DASH_STEP = 6, DASH_GAP = 6;
    /** FULL: the displaced threshold's arrows on the axis - their tips this far out from the threshold row. */
    private static final int[] ARROW_TIPS = {5, 13};
    /** Taxiway «А»'s centre line turns to the runway's edge this far along from its start (on the +a pad). */
    private static final int TAXI_LEAD = 3;
    /** The fence's gates: FULL's middles along a (SHORT: one at a = 0), their half width - a post either side. */
    private static final int[] GATES_FULL = {-60, 0, 60}, GATES_SHORT = {0};
    private static final int GATE_HALF = 1;

    /** The lights' map and the field it was made for (the piece makes its field once). */
    private EmbassyAirfieldSC.Field lightsOf;
    private Map<Long, Integer> lights;

    public ComponentEmbRunwaySC() {
    }

    public ComponentEmbRunwaySC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyAirfieldSC.H_WINDSOCK + 2);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        if (in == null || f.runway == null || EmbassyBlocksSC.airfield == null) {
            return;
        }
        if (lightsOf != f) {
            lights = ComponentEmbAirKitSC.lights(f);
            lightsOf = f;
        }
        int[] c = clipq(paved(f), in);
        if (c != null) {
            for (int a = c[0]; a <= c[2]; a++) {
                for (int q = c[1]; q <= c[3]; q++) {
                    int meta = meta(f, lights, a, q);
                    if (meta >= 0) {
                        surface(w, in, a, 0, q, meta);
                    }
                }
            }
        }
        windsock(w, in, f);
        fence(w, in, f);
    }

    /** The rectangle the paving's walk covers (FULL: the strip, the pads and taxiway «А»; SHORT: the runway) - meta() says which cells are its. */
    static int[] paved(EmbassyAirfieldSC.Field f) {
        return f.kind == EmbassyAirfieldSC.FULL ? EmbassyAirfieldSC.rect(f.strip[0], f.padLow[1], Math.max(f.strip[2], f.taxi[2]), f.strip[3]) : f.runway;
    }

    /**
     * The paving's meta at (a, q) (`lights` - ComponentEmbAirKitSC.lights(f)), -1 - not this piece's (the order: see the class comment).
     * The +a pad's corner inside the apron (a = r + 3 .. RWY_HALF, q = r + 7 .. r + 9 - at r 88..90 the stand «МС-1»'s yellow outline
     * crosses it) is the apron piece's alone: two pieces writing one cell differently re-set it at every «Достройка». Pure.
     */
    static int meta(EmbassyAirfieldSC.Field f, Map<Long, Integer> lights, int a, int q) {
        Integer l = lights.get(Long.valueOf(ComponentEmbAirKitSC.key(a, q)));
        if (l != null) {
            return l.intValue();
        }
        if (EmbassyAirfieldSC.in(f.apron, a, q)) {
            return -1;                                          // the apron's (ComponentEmbApronSC.meta)
        }
        if (EmbassyAirfieldSC.in(f.runway, a, q)) {
            return white(f, a, q) ? BlockAirfieldSC.MARK_WHITE : BlockAirfieldSC.RUNWAY;
        }
        if (EmbassyAirfieldSC.in(f.padLow, a, q) || EmbassyAirfieldSC.in(f.padHigh, a, q) || EmbassyAirfieldSC.in(f.taxi, a, q)) {
            return taxiLine(f, a, q) ? BlockAirfieldSC.MARK_YELLOW : BlockAirfieldSC.APRON;
        }
        if (f.kind == EmbassyAirfieldSC.FULL && EmbassyAirfieldSC.in(f.strip, a, q)) {
            return BlockAirfieldSC.SHOULDER;
        }
        return -1;
    }

    /** Taxiway «А»'s yellow line: its middle from the turn on, and the turn across the +a pad to the runway's edge (FULL). Pure. */
    static boolean taxiLine(EmbassyAirfieldSC.Field f, int a, int q) {
        if (f.taxi == null || f.padHigh == null) {
            return false;
        }
        int mid = f.taxi[1] + EmbassyAirfieldSC.TAXI_W / 2, lead = f.taxi[0] + TAXI_LEAD;
        return q == mid && a >= lead && a <= f.taxi[2] || a == lead && q >= mid && q <= f.padHigh[3];
    }

    /** The runway's white marks at (a, q): each end's own in the half it lies in, the dashes between (see the class comment). Pure. */
    static boolean white(EmbassyAirfieldSC.Field f, int a, int q) {
        boolean full = f.kind == EmbassyAirfieldSC.FULL, low = a < 0;
        int off = q - f.axis, dirA = low ? 1 : -1, tl = f.thresholdRow(true), th = f.thresholdRow(false);
        int d = (a - (low ? tl : th)) * dirA;                       // in from that end's threshold row (< 0: its displaced part)
        if (d < 0) {
            if (!full) {
                return false;
            }
            for (int tip : ARROW_TIPS) {
                if (arrow(-d - tip, off)) {
                    return true;
                }
            }
            return false;
        }
        int zl = full ? ZEBRA_FULL : ZEBRA_SHORT, nb = full ? NUMBER_FULL : NUMBER_SHORT;
        if (d >= ZEBRA_IN && d < ZEBRA_IN + zl) {
            return full ? off == -3 || off == -1 || off == 1 || off == 3 : off == -2 || off == 0 || off == 2;
        }
        if (d >= nb && d < nb + ComponentEmbAirKitSC.GLYPH_H) {
            // the landing over the -a end heads +a - its number; the columns left to right as its pilot sees them (p grows to his right)
            String n = EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(f.gateDir, low));
            int mid = ComponentEmbAirKitSC.width(n) / 2;
            return ComponentEmbAirKitSC.pixel(n, mid + off * dirA * f.side, ComponentEmbAirKitSC.GLYPH_H - 1 - (d - nb));
        }
        if (full && d >= AIM_IN && d < AIM_IN + AIM_LEN) {
            return Math.abs(off) == 2 || Math.abs(off) == 3;
        }
        int limit = Math.min(-tl, th) - nb - ComponentEmbAirKitSC.GLYPH_H + 1 - DASH_GAP;
        int k = Math.round(a / (float) DASH_STEP);
        return off == 0 && Math.abs(a - DASH_STEP * k) <= 1 && DASH_STEP * Math.abs(k) + 1 <= limit;
    }

    /** An arrow on the axis pointing to the threshold: `k` rows out from its tip (0 the tip; 1, 2 its head's wings; 3, 4 the shaft). */
    private static boolean arrow(int k, int off) {
        switch (k) {
            case 0:
            case 3:
            case 4:
                return off == 0;
            case 1:
                return Math.abs(off) <= 1;
            case 2:
                return off == 0 || Math.abs(off) == 2;
            default:
                return false;
        }
    }

    /** The windsock's mast at Field.windsockBlock: a white ringed pad, a nether brick fence up to the cone, the orange wool cone on top. */
    private void windsock(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f) {
        int[] s = f.windsock;
        if (s == null) {
            return;
        }
        for (int da = -1; da <= 1; da++) {
            for (int dq = -1; dq <= 1; dq++) {
                surface(w, in, s[0] + da, 0, s[1] + dq, windsockMeta(da, dq));
            }
        }
        for (int dy = EmbassyAirfieldSC.WINDSOCK_DY; dy < EmbassyAirfieldSC.H_WINDSOCK; dy++) {
            atq(w, in, s[0], dy, s[1], Blocks.nether_brick_fence, 0);
        }
        atq(w, in, s[0], EmbassyAirfieldSC.H_WINDSOCK, s[1], Blocks.wool, 1);
    }

    /** The windsock's pad at (da, dq) off its mast (-1..1 each): the mast's cell concrete, the white ring round it. Pure. */
    static int windsockMeta(int da, int dq) {
        return da == 0 && dq == 0 ? BlockAirfieldSC.APRON : BlockAirfieldSC.MARK_WHITE;
    }

    /** The low fence on the field's outer edge (FULL: the runway's level rectangle's; SHORT: its zone's), the ends open. */
    private void fence(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f) {
        boolean full = f.kind == EmbassyAirfieldSC.FULL;
        int m = EmbassyAirfieldSC.ZONE_MARGIN;
        int fq = full ? f.radius + EmbassyLayoutSC.LEVEL_OUT : f.runway[3] + m;
        int a0 = full ? -fq : f.runway[0] - m, a1 = full ? fq : f.runway[2] + m;
        int[] c = clipq(EmbassyAirfieldSC.rect(a0, fq, a1, fq), in);
        if (c == null) {
            return;
        }
        for (int a = c[0]; a <= c[2]; a++) {
            if (!f.onShape(f.x(a, fq), f.z(a, fq)) || ComponentEmbAirKitSC.post(f, a, fq)) {
                continue;
            }
            int g = gate(full ? GATES_FULL : GATES_SHORT, a);
            boolean end = a == a0 || a == a1 || !f.onShape(f.x(a - 1, fq), f.z(a - 1, fq)) || !f.onShape(f.x(a + 1, fq), f.z(a + 1, fq));
            if (end || g > 0) {
                if (SettlementBlocksSC.decor != null) {
                    atq(w, in, a, EmbassyAirfieldSC.H_FENCE, fq, SettlementBlocksSC.decor, BlockSettlementDecorSC.NODE);
                }
            } else if (g == 0) {
                atq(w, in, a, EmbassyAirfieldSC.H_FENCE, fq, Blocks.iron_bars, 0);
            }
        }
    }

    /** Along a fence with gates at `gates`: 0 - the bars, -1 - a gate's gap, 1 - a gate's post. Pure. */
    static int gate(int[] gates, int a) {
        for (int g : gates) {
            int d = Math.abs(a - g);
            if (d <= GATE_HALF) {
                return -1;
            }
            if (d == GATE_HALF + 1) {
                return 1;
            }
        }
        return 0;
    }
}
