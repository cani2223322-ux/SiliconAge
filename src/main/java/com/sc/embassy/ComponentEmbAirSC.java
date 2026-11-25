package com.sc.embassy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §4.3): the abstract base of the airfield's pieces (package P's
 * ComponentEmbRunwaySC, ...ApronSC, ...TerminalSC, ...HangarSC, ...HelipadSC, ...FunnelSC, ...AirDefenceSC) - a frame piece
 * (ComponentEmbFrameSC: the core's column, the dome's radius, the main gate's way) that also keeps the field's decision (NBT "EmbAir"
 * {kind, side, ils, version}) and gives its EmbassyAirfieldSC.Field - the same numbers the generator, the protection and the API use.
 * The field's frame is (a, q): q = p x side, towards the field. Never registered itself; every block goes in through
 * ComponentSettlementSC.put (the window only, flag 2, light by hand: idempotent for the «Достройка»). Written whole by the coordinator.
 */
public abstract class ComponentEmbAirSC extends ComponentEmbFrameSC {

    /** The field's decision (EmbassyAirfieldSC: kind, side, the ILS ends, version). */
    protected int kind, side = 1, ils, version = EmbassyAirfieldSC.VERSION;
    private EmbassyAirfieldSC.Field field;

    public ComponentEmbAirSC() {
    }

    /** A piece on `lot` (one of p.field.lots()) of embassy `id`'s plan `p` (its field set), from `dyMin` to `dyMax` over the floor. */
    protected ComponentEmbAirSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot, int dyMin, int dyMax) {
        super(id, p, lot, dyMin, dyMax);
        EmbassyAirfieldSC.Field f = p.field;
        if (f != null) {
            kind = f.kind;
            side = f.side;
            ils = f.ils;
            version = f.version;
            field = f;
        }
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("EmbAir", new int[]{kind, side, ils, version});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = ints(nbt, "EmbAir", 4);
        kind = a[0];
        side = a[1] < 0 ? -1 : 1;
        ils = a[2];
        version = Math.max(1, a[3]);
        field = null;
    }

    /** The piece's field (made again from the NBT after a load; null: a bad kind - the piece then builds nothing). */
    protected EmbassyAirfieldSC.Field field() {
        if (field == null) {
            field = EmbassyAirfieldSC.Field.of(coreX, coreZ, radius, floorY, gateDir, kind, side, ils, version);
        }
        return field;
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        EmbassyAirfieldSC.Field f = field();
        if (f != null) {
            buildField(w, win, f);
        }
    }

    /** The piece's part of the window (package P's: every subclass). */
    protected abstract void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f);

    // ------------------------------------------------------------------ the field's frame (a, q)

    /** A block at (a, floor + dy, q) of the field's frame (the trim's look-alikes as ComponentEmbFrameSC.at). @return placed */
    protected boolean atq(World w, StructureBoundingBox win, int a, int dy, int q, Block b, int meta) {
        return at(w, win, a, dy, q * side, b, meta);
    }

    /** Air at (a, floor + dy, q) (only where something stands). */
    protected void airq(World w, StructureBoundingBox win, int a, int dy, int q) {
        air(w, win, a, dy, q * side);
    }

    /** The airfield's surface (EmbassyBlocksSC.airfield, BlockAirfieldSC meta) at (a, floor + dy, q). */
    protected boolean surface(World w, StructureBoundingBox win, int a, int dy, int q, int meta) {
        return EmbassyBlocksSC.airfield != null && atq(w, win, a, dy, q, EmbassyBlocksSC.airfield, meta);
    }

    /** A BlockShowcase2SC machine (the airfield's metas: TOWER_CONSOLE, SERVICE_COLUMN, AIRFIELD_BEACON, AA_POST) at (a, floor + dy, q), its front towards (da, dq). */
    protected void machineq(World w, StructureBoundingBox win, int a, int dy, int q, int meta, int da, int dq) {
        machine2(w, win, a, dy, q * side, meta, da, dq * side);
    }

    /** The part of the field's rectangle r {a0, q0, a1, q1} the window holds, in (a, q) (null: none) - a piece walks only that. */
    protected int[] clipq(int[] r, StructureBoundingBox win) {
        int[] c = clip(EmbassyAirfieldSC.rect(r[0], r[1] * side, r[2], r[3] * side), win);
        return c == null ? null : EmbassyAirfieldSC.rect(c[0], c[1] * side, c[2], c[3] * side);
    }

    /** The world direction (NORTH..WEST) of a step (da, dq) of the field's frame. */
    protected int dirOfq(int da, int dq) {
        return dirOf(da, dq * side);
    }

    // ------------------------------------------------------------------ the floor's plan (the self-test's)

    /** floors(): a cell one piece itself puts two different metas in. */
    public static final int FLOOR_CLASH = -2;

    /**
     * The self-test's (SelfTestAirfieldSC): the floor (dy 0, EmbassyBlocksSC.airfield) the airfield's pieces put on field `f` - one map
     * a piece, in build order (the runway, the apron, the hangar, the helipad; null - the field has none), {ComponentEmbAirKitSC.key(a, q)
     * -> BlockAirfieldSC meta}, the very cells and metas of their buildField (the same pure helpers); FLOOR_CLASH - the piece puts two in
     * one cell. Two pieces must agree on every cell they share: else each «Достройка» puts one, then the other. Pure.
     */
    public static List<Map<Long, Integer>> floors(EmbassyAirfieldSC.Field f) {
        List<Map<Long, Integer>> out = new ArrayList<Map<Long, Integer>>();
        Map<Long, Integer> m = null;
        if (f.runway != null) {
            m = new HashMap<Long, Integer>();
            Map<Long, Integer> lights = ComponentEmbAirKitSC.lights(f);
            int[] r = ComponentEmbRunwaySC.paved(f);
            for (int a = r[0]; a <= r[2]; a++) {
                for (int q = r[1]; q <= r[3]; q++) {
                    int meta = ComponentEmbRunwaySC.meta(f, lights, a, q);
                    if (meta >= 0) {
                        floor(m, a, q, meta);
                    }
                }
            }
            if (f.windsock != null) {
                for (int da = -1; da <= 1; da++) {
                    for (int dq = -1; dq <= 1; dq++) {
                        floor(m, f.windsock[0] + da, f.windsock[1] + dq, ComponentEmbRunwaySC.windsockMeta(da, dq));
                    }
                }
            }
        }
        out.add(m);
        m = null;
        if (f.apron != null) {
            m = new HashMap<Long, Integer>();
            for (int a = f.apron[0]; a <= f.apron[2]; a++) {
                for (int q = f.apron[1]; q <= f.apron[3]; q++) {
                    floor(m, a, q, ComponentEmbApronSC.meta(f, a, q));
                }
            }
        }
        out.add(m);
        m = null;
        if (f.hangar != null) {
            m = new HashMap<Long, Integer>();
            for (int a = f.hangar[0]; a <= f.hangar[2]; a++) {
                for (int q = f.hangar[1]; q <= f.hangar[3]; q++) {
                    floor(m, a, q, ComponentEmbHangarSC.meta(f.hangar, a, q));
                }
            }
        }
        out.add(m);
        m = null;
        if (f.helipad != null) {
            m = new HashMap<Long, Integer>();
            int[] h = f.helipad, col = f.columns[f.columns.length - 1];
            for (int a = h[0]; a <= h[2]; a++) {
                for (int q = h[1]; q <= h[3]; q++) {
                    floor(m, a, q, ComponentEmbHelipadSC.meta(h, a, q));
                }
            }
            for (int da = -1; da <= 1; da++) {
                floor(m, col[0] + da, col[1], ComponentEmbHelipadSC.columnMeta(da));
            }
        }
        out.add(m);
        return out;
    }

    private static void floor(Map<Long, Integer> m, int a, int q, int meta) {
        Long k = Long.valueOf(ComponentEmbAirKitSC.key(a, q));
        Integer was = m.put(k, Integer.valueOf(meta));
        if (was != null && was.intValue() != meta) {
            m.put(k, Integer.valueOf(FLOOR_CLASH));
        }
    }

    /** The settlements' unbreakable trim (BlockSettlementDecorSC) is there to build with (a test world without it: nothing). */
    protected static boolean trimReady() {
        return SettlementBlocksSC.decor != null && SettlementBlocksSC.showcase2 != null;
    }
}
