package com.sc.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.embassy.EmbassyAirDefenceSC;
import com.sc.embassy.EmbassyAirRulesSC;
import com.sc.embassy.EmbassyAirServiceSC;
import com.sc.embassy.EmbassyAirfieldSC;
import com.sc.embassy.EmbassyBlocksSC;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyPiecesSC;
import com.sc.embassy.EmbassyQuartersSC;
import com.sc.embassy.EmbassySC;
import com.sc.embassy.EmbassySiteSC;
import com.sc.embassy.api.AirfieldInfoSC;
import com.sc.embassy.api.EmbassyAirApiSC;
import com.sc.manual.BookContent;
import com.sc.manual.BookEntry;
import com.sc.manual.Lang;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.trade.TradeRulesSC;

import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;

/**
 * PACKAGE D (docs/plan-embassy-airfield-core.md §11.1): the self-tests of «Лётное поле посольства» - pure logic only (no world): the
 * geometry (EmbassyAirfieldSC.problem over the 12 seeds x radii 88..96 x sides x kinds, the frame's round trip, the level shape and
 * the zone, the lights' places, the ICAO and the runway numbers), the decision and the record's "X" (writeDecision / decisionOf, no "X"
 * - no field), the services' rules (EmbassyAirRulesSC: refusal's order, price, units, the caps), the air defence's pure rules (admitted,
 * passAvailable), the «Лётная книжка» (logbookChips, EmbassyAirServiceSC.landed), the pieces' names (EmbassyPiecesSC.NAMES appended,
 * registered), the metas (BlockAirfieldSC.NAMES / LIGHT / runway, BlockShowcase2SC 10..13), the lang keys of every package (ru and en,
 * no « », no 128..255 of the font table) and the API's constants (API_VERSION 1, the PAY_ / PASS_ / AD_ codes = the rules').
 * SelfTestSC.run calls run() after SelfTestEmbassySC (the coordinator's). Output "[SC-TEST] PASS / FAIL / INFO airfield: ...".
 * <p>
 * Each of the 13 sections by itself (an unfinished package body fails only its own): (1) problem() on 648 plans and the quarters'
 * problem() with the field's posts (package G), (2) the frame, (3) the shape and the zone, (4) the lights' counts and places, (5) the
 * decision and the record, (6) EmbassyAirRulesSC, (7) the API's constants and AirfieldInfoSC, (8) the pieces, (9) the metas, (10) the
 * names, (11) the registry (reach, inBelt, overlapping on a test registry), (12) the texts and the handbook, (13) package G's pure choose
 * helpers (EmbassyAirSiteSC, asked by name: decide, fullFits, fullIls, flags / tally / count, levelProblem / approachProblem, the tallies
 * on a synthetic WorldChunkManager). It runs at preInit (SCMod): the structures are registered at init - "registered" is the world
 * test's then.
 */
final class SelfTestAirfieldSC {

    private static final String[] SECTIONS = {"geometry", "frame", "shape and zone", "lights", "decision and record", "services' rules", "API",
            "pieces", "metas", "names", "registry", "texts", "package G"};
    /** The 12 world seeds of docs/plan-substrate-tech-f6.md §5.1 («Российская империя» as the game hashes a word). */
    static final long[] SEEDS = {-4043695398149670526L, -1234567890123L, -77777L, 12345L, 31337L, 987654321L, "Российская империя".hashCode(), 1L,
            42L, 2024L, 8675309L, 555555L};
    /** The dome's radii (SettlementSizeSC.EMBASSY: 88..96) and the kinds with a field. */
    private static final int R_MIN = 88, R_MAX = 96;
    private static final int[] KINDS = {EmbassyAirfieldSC.HELI, EmbassyAirfieldSC.SHORT, EmbassyAirfieldSC.FULL};
    /** Core keys of §9 and the arguments their callers pass. */
    private static final String[][] CORE_ARGS = {{"sc.airfield.ad.warn", "4"}, {"sc.airfield.unknown", "0"}, {"sc.tooltip.airfield.howto", "0"},
            {"sc.tooltip.embassyAirfield.details", "0"}, {"sc.nei.obtain.airfield", "0"}, {"sc.airfield.logbook.first", "3"}};
    /** Key prefixes of the airfield's texts: each in both languages with as many arguments, English without Cyrillic, no « » anywhere. */
    private static final String[] PREFIXES = {"sc.airfield.", "sc.tooltip.airfield.", "sc.tooltip.embassyAirfield.", "tile.siliconage.embassyAirfield",
            "tile.siliconage.showcase2.towerConsole", "tile.siliconage.showcase2.serviceColumn", "tile.siliconage.showcase2.airfieldBeacon",
            "tile.siliconage.showcase2.aaPost", "sc.manual.world.embassyCity.air", "sc.nei.obtain.airfield"};
    private static final String SITE_CLASS = "com.sc.embassy.EmbassyAirSiteSC";

    private SelfTestAirfieldSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "airfield: " + what);
    }

    static void run() {
        for (int i = 0; i < SECTIONS.length; i++) {
            try {
                section(i);
            } catch (Throwable t) {
                check(false, SECTIONS[i] + " - exception " + t);
                t.printStackTrace();
            }
        }
    }

    private static void section(int i) throws Exception {
        switch (i) {
            case 0: geometry(); break;
            case 1: frame(); break;
            case 2: shapeAndZone(); break;
            case 3: lights(); break;
            case 4: decision(); break;
            case 5: rules(); break;
            case 6: api(); break;
            case 7: pieces(); break;
            case 8: metas(); break;
            case 9: names(); break;
            case 10: registry(); break;
            case 11: texts(); break;
            default: packageG(); break;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The test site of world seed `ws` with dome `r` (its own site seed by its place, the plains' floor). */
    static EmbassySiteSC.Site site(long ws, int r) {
        int x = 1792 + (int) (ws & 1023L), z = -1280 - (int) ((ws >>> 10) & 1023L);
        return new EmbassySiteSC.Site(x, z, r, EmbassyLayoutSC.floorY(BiomeGenBase.plains.rootHeight), EmbassySiteSC.siteSeed(ws, x, z), false);
    }

    /** The decision {kind, side, both ILS ends, VERSION}. */
    static int[] air(int kind, int side) {
        return new int[]{kind, side, EmbassyAirfieldSC.ILS_LOW | EmbassyAirfieldSC.ILS_HIGH, EmbassyAirfieldSC.VERSION};
    }

    /** A bare field at (1000, -2000), floor 64 (no plan: the geometry alone). */
    static EmbassyAirfieldSC.Field field(int gateDir, int kind, int side, int r) {
        return EmbassyAirfieldSC.Field.of(1000, -2000, r, 64, gateDir, kind, side, EmbassyAirfieldSC.ILS_LOW | EmbassyAirfieldSC.ILS_HIGH,
                EmbassyAirfieldSC.VERSION);
    }

    private static void note(StringBuilder bad, String what) {
        if (bad.length() < 600) {
            bad.append(' ').append(what);
        }
    }

    // ------------------------------------------------------------------ 1. problem() on 648 plans (§3.1), the quarters' with the field's posts

    private static void geometry() {
        StringBuilder bad = new StringBuilder(), quarters = new StringBuilder();
        int plans = 0, clean = 0, qClean = 0;
        int[] gates = new int[4];
        for (long ws : SEEDS) {
            for (int r = R_MIN; r <= R_MAX; r++) {
                EmbassySiteSC.Site s = site(ws, r);
                List<NpcKindSC> roster = EmbassyLayoutSC.roster(s);
                for (int kind : KINDS) {
                    for (int side = -1; side <= 1; side += 2) {
                        EmbassyLayoutSC.Plan p = EmbassyLayoutSC.plan(ws, s, s.floorY, air(kind, side));
                        plans++;
                        gates[p.gateDir & 3]++;
                        String at = "[" + ws + " r" + r + " " + EmbassyAirfieldSC.KIND_KEYS[kind] + " " + side + ": ";
                        String why = p.field == null ? "no field made" : EmbassyAirfieldSC.problem(p);
                        if (why == null) {
                            clean++;
                        } else {
                            note(bad, at + why + "]");
                        }
                        String q = EmbassyQuartersSC.problem(p, roster);
                        if (q == null) {
                            qClean++;
                        } else {
                            note(quarters, at + q + "]");
                        }
                    }
                }
            }
        }
        check(plans == 648 && clean == plans, "problem() (§3.1, ЛП-1..ЛП-3): " + clean + " of " + plans + " plans (648: 12 seeds x r " + R_MIN + ".." + R_MAX
                + " x HELI / SHORT / FULL x both sides) clean - outside the dome + 3, inside the square, on the level ground, off the Vokzal, its zone and"
                + " the city's lots, the stands 14 from the axis, the buildings under 1:4 and 1:20; main gates by way " + Arrays.toString(gates) + bad);
        check(qClean == plans, "the quarters' problem() with the field (§4.5, ЛП-18, package G): " + qClean + " of " + plans + " plans clean (the"
                + " Sentries #5, #6 and the second Gas Engineer on the field's posts)" + quarters);
    }

    // ------------------------------------------------------------------ 2. the frame (§3: a, q = p x side)

    private static void frame() {
        StringBuilder bad = new StringBuilder();
        int cells = 0;
        for (int g = 0; g < 4; g++) {
            for (int side = -1; side <= 1; side += 2) {
                EmbassyAirfieldSC.Field f = field(g, EmbassyAirfieldSC.FULL, side, 92);
                for (int a = -160; a <= 160; a += 23) {
                    for (int q = -140; q <= 140; q += 17) {
                        cells++;
                        int[] back = f.frame(f.x(a, q), f.z(a, q));
                        if (back[0] != a || back[1] != q) {
                            note(bad, "g" + g + " s" + side + " (" + a + "," + q + ")->(" + back[0] + "," + back[1] + ")");
                        }
                    }
                }
                // world(rect): the frame's rectangle as the world's box - its corners in it, the box's corners back on the rectangle's, as many cells
                for (int[] r : new int[][]{f.runway, f.apron, f.helipad, f.hangar, EmbassyAirfieldSC.rect(-7, 3, 11, 29)}) {
                    int[] w = f.world(r);
                    int[] c0 = f.frame(w[0], w[1]), c1 = f.frame(w[2], w[3]);
                    boolean ok = EmbassyAirfieldSC.in(r, c0[0], c0[1]) && EmbassyAirfieldSC.in(r, c1[0], c1[1])
                            && (long) (w[2] - w[0] + 1) * (w[3] - w[1] + 1) == (long) (r[2] - r[0] + 1) * (r[3] - r[1] + 1);
                    for (int[] c : new int[][]{{r[0], r[1]}, {r[0], r[3]}, {r[2], r[1]}, {r[2], r[3]}}) {
                        int x = f.x(c[0], c[1]), z = f.z(c[0], c[1]);
                        ok &= x >= w[0] && x <= w[2] && z >= w[1] && z <= w[3];
                    }
                    if (!ok) {
                        note(bad, "g" + g + " s" + side + " world(" + Arrays.toString(r) + ")=" + Arrays.toString(w));
                    }
                }
                // q is towards the field: the runway's axis past the dome on the field's side
                int[] mid = f.frame(f.x(0, f.axis), f.z(0, f.axis));
                if (mid[1] != f.axis || f.axis != f.radius + EmbassyAirfieldSC.AXIS) {
                    note(bad, "g" + g + " s" + side + " axis " + f.axis);
                }
            }
        }
        check(bad.length() == 0, "frame (§3): frame(x(a, q), z(a, q)) = (a, q) on " + cells + " cells of the 4 main gates x 2 sides, world(rect) the same"
                + " rectangle, the runway's axis at q = r + " + EmbassyAirfieldSC.AXIS + bad);
    }

    // ------------------------------------------------------------------ 3. the level shape and the zone (ЛП-2, ЛП-6, ЛП-8, ЛП-9)

    private static void shapeAndZone() {
        StringBuilder bad = new StringBuilder();
        for (int r : new int[]{R_MIN, 92, R_MAX}) {
            for (int g = 0; g < 4; g++) {
                for (int side = -1; side <= 1; side += 2) {
                    EmbassyAirfieldSC.Field f = field(g, EmbassyAirfieldSC.FULL, side, r);
                    String at = "r" + r + " g" + g + " s" + side + ": ";
                    int sq = r + EmbassyLayoutSC.LEVEL_OUT + EmbassyLayoutSC.BLEND;
                    // on the shape: the strip, the apron, the pads, the taxiway, the hangar, the terminal, the tower (corners and middles)
                    for (int[] q : new int[][]{f.strip, f.apron, f.padLow, f.padHigh, f.taxi, f.hangar, f.terminal, f.tower}) {
                        for (int[] c : new int[][]{{q[0], q[1]}, {q[0], q[3]}, {q[2], q[1]}, {q[2], q[3]}, {(q[0] + q[2]) / 2, (q[1] + q[3]) / 2}}) {
                            if (f.levelPast(f.x(c[0], c[1]), f.z(c[0], c[1])) > 0D) {
                                note(bad, at + "off the shape at " + c[0] + "," + c[1]);
                            }
                        }
                    }
                    // past the square: the blend is over (the land its own height again)
                    for (int a = -(sq + 1); a <= sq + 1; a += 7) {
                        for (int[] c : new int[][]{{a, sq + 1}, {a, -(sq + 1)}, {sq + 1, a}, {-(sq + 1), a}}) {
                            if (f.levelPast(f.x(c[0], c[1]), f.z(c[0], c[1])) < EmbassyLayoutSC.BLEND) {
                                note(bad, at + "the blend past the square at " + c[0] + "," + c[1]);
                            }
                        }
                    }
                    for (int[] lv : f.level) {
                        int far = Math.max(Math.max(Math.abs(lv[0]), Math.abs(lv[2])), Math.max(Math.abs(lv[1]), Math.abs(lv[3])));
                        if (far + EmbassyLayoutSC.BLEND > sq) {
                            note(bad, at + "a level rectangle + BLEND past +-" + sq + " " + Arrays.toString(lv));
                        }
                    }
                    // the disc: never less level than before (levelPast at most the disc's)
                    for (int dx = -sq; dx <= sq; dx += 9) {
                        for (int dz = -sq; dz <= sq; dz += 9) {
                            double disc = EmbassyLayoutSC.levelPast(dx, dz, r + EmbassyLayoutSC.LEVEL_OUT);
                            if (f.levelPast(f.coreX + dx, f.coreZ + dz) > disc + 1e-9) {
                                note(bad, at + "less level than the disc at " + dx + "," + dz);
                            }
                        }
                    }
                    // the zone: the runway, the apron, the helipad, the emplacements, the windsock in it; the core and the far side not
                    int[][] in = {{0, f.axis}, {(f.apron[0] + f.apron[2]) / 2, (f.apron[1] + f.apron[3]) / 2}, {f.helipad[0] + 5, f.helipad[1] + 5},
                            f.aaPosts[0], f.aaPosts[2], f.aaPosts[4], f.windsock};
                    for (int[] c : in) {
                        if (!f.inZone(f.x(c[0], c[1]), f.z(c[0], c[1]))) {
                            note(bad, at + "not in the zone " + c[0] + "," + c[1]);
                        }
                    }
                    if (f.inZone(f.coreX, f.coreZ) || f.inZone(f.x(0, -(r + 20)), f.z(0, -(r + 20))) || f.inZone(f.x(0, -r / 2), f.z(0, -r / 2))) {
                        note(bad, at + "the zone in the dome or on the far side");
                    }
                    int[] box = f.zoneBox();
                    for (int[] z : f.zone) {
                        int[] w = f.world(z);
                        if (w[0] < box[0] || w[1] < box[1] || w[2] > box[2] || w[3] > box[3]) {
                            note(bad, at + "zoneBox misses " + Arrays.toString(z));
                        }
                    }
                    // the neutral sky: the disc, the zone, the approach corridors (300 past each threshold); the AD zone: 256 round the core
                    boolean neutral = f.neutral(f.coreX, f.coreZ) && f.neutral(f.x(f.thresholdLow - 250, f.axis), f.z(f.thresholdLow - 250, f.axis))
                            && f.neutral(f.x(f.thresholdHigh + 250, f.axis + 15), f.z(f.thresholdHigh + 250, f.axis + 15))
                            && !f.neutral(f.x(0, -(r + 60)), f.z(0, -(r + 60))) && !f.neutral(f.x(f.thresholdHigh + 320, f.axis), f.z(f.thresholdHigh + 320, f.axis));
                    double cx = f.coreX + 0.5D, cz = f.coreZ + 0.5D;
                    boolean ad = f.inAdZone(cx + 255D, cz) && f.inAdZone(cx, cz - 255D) && !f.inAdZone(cx + 257D, cz) && !f.inAdZone(cx + 182D, cz + 182D);
                    if (!neutral || !ad) {
                        note(bad, at + "neutral " + neutral + " AD " + ad);
                    }
                }
            }
        }
        check(bad.length() == 0, "shape and zone (ЛП-2, ЛП-6, ЛП-8, ЛП-9): FULL at r " + R_MIN + " / 92 / " + R_MAX + ", 4 gates x 2 sides - the strip, apron,"
                + " pads, taxiway, hangar, terminal and tower on the level shape, its blend over inside +-(r + 39), the disc never less level than before;"
                + " the zone holds the runway, apron, helipad, emplacements, windsock (not the dome), zoneBox all of it; the neutral sky over the disc and"
                + " the corridors, the AD zone " + EmbassyAirRulesSC.AD_RADIUS + bad);
    }

    // ------------------------------------------------------------------ 4. the lights (§3.1 «Огни») and the fixed blocks

    private static void lights() {
        StringBuilder bad = new StringBuilder();
        int[] metas = {BlockAirfieldSC.LIGHT_EDGE, BlockAirfieldSC.LIGHT_THRESHOLD, BlockAirfieldSC.LIGHT_END, BlockAirfieldSC.LIGHT_TAXI};
        int[][] want = {{64, 18, 18, 10}, {28, 14, 14, 0}};
        String counts = "";
        for (int k = 0; k < 2; k++) {
            int kind = k == 0 ? EmbassyAirfieldSC.FULL : EmbassyAirfieldSC.SHORT;
            for (int r = R_MIN; r <= R_MAX; r++) {
                for (int g = 0; g < 4; g++) {
                    for (int side = -1; side <= 1; side += 2) {
                        EmbassyAirfieldSC.Field f = field(g, kind, side, r);
                        String at = EmbassyAirfieldSC.KIND_KEYS[kind] + " r" + r + " g" + g + " s" + side + ": ";
                        for (int i = 0; i < metas.length; i++) {
                            List<int[]> l = f.lights(metas[i]);
                            // the taxiway's: every LIGHT_STEP along «А» (it grows with the dome: at r 88 the plan's 10)
                            int n = metas[i] == BlockAirfieldSC.LIGHT_TAXI && f.taxi != null
                                    ? 2 * ((f.taxi[2] - f.taxi[0]) / EmbassyAirfieldSC.LIGHT_STEP + 1) : want[k][i];
                            if (l.size() != n || r == R_MIN && l.size() != want[k][i]) {
                                note(bad, at + BlockAirfieldSC.NAMES[metas[i]] + " " + l.size() + " (" + n + ")");
                            }
                            if (r == R_MIN && g == 0 && side == 1) {
                                counts += (i == 0 ? (k == 0 ? "FULL " : ", SHORT ") : " / ") + l.size();
                            }
                            for (int[] c : l) {
                                boolean on = metas[i] == BlockAirfieldSC.LIGHT_TAXI ? EmbassyAirfieldSC.in(f.taxi, c[0], c[1])
                                        : EmbassyAirfieldSC.in(f.runway, c[0], c[1]);
                                if (!on || !f.inZone(f.x(c[0], c[1]), f.z(c[0], c[1]))) {
                                    note(bad, at + BlockAirfieldSC.NAMES[metas[i]] + " off its paving / the zone at " + c[0] + "," + c[1]);
                                    break;
                                }
                            }
                        }
                        // the fixed blocks: the columns, the guns, the windsock in the zone; the desk and the beacon over the tower
                        for (int i = 0; i < f.columns.length; i++) {
                            int[] b = f.columnBlock(i);
                            if (!f.inZone(b[0], b[2]) || b[1] != f.floorY + EmbassyAirfieldSC.COLUMN_DY) {
                                note(bad, at + "column " + i);
                            }
                        }
                        for (int i = 0; i < f.aaPosts.length; i++) {
                            int[] b = f.aaBlock(i);
                            if (!f.inZone(b[0], b[2]) || b[1] != f.floorY + EmbassyAirfieldSC.AA_GUN_DY) {
                                note(bad, at + "gun " + i);
                            }
                        }
                        if (f.windsock != null && !f.inZone(f.windsockBlock()[0], f.windsockBlock()[2])) {
                            note(bad, at + "windsock");
                        }
                        boolean tower = kind == EmbassyAirfieldSC.FULL;
                        int[] desk = f.towerDesk(), beacon = f.beaconBlock();
                        if (tower != (desk != null) || tower != (beacon != null)) {
                            note(bad, at + "desk / beacon " + (desk != null) + " / " + (beacon != null));
                        } else if (tower) {
                            int[] d = f.frame(desk[0], desk[2]), b = f.frame(beacon[0], beacon[2]);
                            if (!EmbassyAirfieldSC.in(f.tower, d[0], d[1]) || !EmbassyAirfieldSC.in(f.tower, b[0], b[1])
                                    || beacon[1] - f.floorY != EmbassyAirfieldSC.BEACON_DY || desk[1] - f.floorY != EmbassyAirfieldSC.TOWER_DESK_DY) {
                                note(bad, at + "desk / beacon off the tower");
                            }
                        }
                    }
                }
            }
        }
        check(bad.length() == 0, "lights (§3.1): " + counts + " (edge / threshold / end / taxi at r " + R_MIN + "; the plan's 64 / 18 / 18 / 10 and"
                + " 28 / 14 / 14 / 0), every one on its paving and in the zone; the columns, the guns, the windsock in the zone, the desk and the beacon"
                + " over the tower - at every r " + R_MIN + ".." + R_MAX + ", gate and side" + bad);
    }

    // ------------------------------------------------------------------ 5. the decision and the record (ЛП-1, ЛП-19)

    private static void decision() {
        StringBuilder bad = new StringBuilder();
        EmbassySiteSC.Site s = site(SEEDS[3], 92);
        // writeDecision / decisionOf round trip; no "X" - no field; NONE - no field
        SettlementRegistrySC.Settlement rec = EmbassyLayoutSC.record(EmbassyLayoutSC.plan(SEEDS[3], s, s.floorY, null), 0, 0L);
        boolean none = EmbassyAirfieldSC.decisionOf(rec) == null && EmbassyAirfieldSC.fieldOf(rec) == null && !rec.extra.hasKey(EmbassyAirfieldSC.EXTRA);
        int[] d = {EmbassyAirfieldSC.FULL, -1, EmbassyAirfieldSC.ILS_HIGH, EmbassyAirfieldSC.VERSION};
        EmbassyAirfieldSC.writeDecision(rec, d);
        EmbassyAirfieldSC.Field rf = EmbassyAirfieldSC.fieldOf(rec);
        boolean trip = Arrays.equals(EmbassyAirfieldSC.decisionOf(rec), d) && rf != null && rf.kind == EmbassyAirfieldSC.FULL && rf.side == -1
                && rf.ils == EmbassyAirfieldSC.ILS_HIGH && EmbassyAirfieldSC.fieldOf(rec) == rf;
        EmbassyAirfieldSC.writeDecision(rec, new int[]{EmbassyAirfieldSC.NONE, 1, 0, EmbassyAirfieldSC.VERSION});
        boolean noneKind = EmbassyAirfieldSC.decisionOf(rec) != null && EmbassyAirfieldSC.fieldOf(rec) == null;
        // fill without air keeps the record's "X"; a record of a plan with air has it, planOf reads it back
        EmbassyAirfieldSC.writeDecision(rec, d);
        NBTTagCompound x = rec.extra.getCompoundTag(EmbassyAirfieldSC.EXTRA);
        EmbassyLayoutSC.fill(rec, EmbassyLayoutSC.plan(SEEDS[3], s, s.floorY, null));
        boolean kept = rec.extra.getCompoundTag(EmbassyAirfieldSC.EXTRA) == x && Arrays.equals(EmbassyAirfieldSC.decisionOf(rec), d);
        EmbassyLayoutSC.Plan withAir = EmbassyLayoutSC.plan(SEEDS[3], s, s.floorY, air(EmbassyAirfieldSC.SHORT, 1));
        SettlementRegistrySC.Settlement made = EmbassyLayoutSC.record(withAir, 0, 0L);
        EmbassyLayoutSC.Plan again = EmbassyLayoutSC.planOf(made, SEEDS[3]);
        boolean written = Arrays.equals(EmbassyAirfieldSC.decisionOf(made), air(EmbassyAirfieldSC.SHORT, 1)) && again.field != null
                && again.field.kind == EmbassyAirfieldSC.SHORT && again.field.axis == withAir.field.axis && EmbassyAirfieldSC.fieldOf(made) != null
                && Arrays.equals(EmbassyAirfieldSC.fieldOf(made).zoneBox(), withAir.field.zoneBox());
        // plan(seed, site, floor) is plan(..., null): the posts and the lots as before the airfield
        int same = 0;
        for (long ws : SEEDS) {
            EmbassySiteSC.Site t = site(ws, R_MIN + (int) ((ws & 0x7FFFFFFFL) % (R_MAX - R_MIN + 1)));
            EmbassyLayoutSC.Plan a = EmbassyLayoutSC.plan(ws, t, t.floorY), b = EmbassyLayoutSC.plan(ws, t, t.floorY, null);
            if (a.field == null && b.field == null && a.air == null && b.air == null && plansAlike(a, b)) {
                same++;
            } else {
                note(bad, "plan(" + ws + ") differs");
            }
        }
        // the mode (embassy.airfield): NEVER never, ALWAYS always, AUTO with the add-on only; sideOf +-1
        int was = SubstrateConfigSC.embassyAirfield();
        boolean never, always, auto;
        try {
            SubstrateConfigSC.setEmbassyAirfield(SubstrateConfigSC.AIRFIELD_NEVER);
            never = !EmbassyAirfieldSC.buildsNow() && EmbassyAirfieldSC.decideFor(null, s) == null;
            SubstrateConfigSC.setEmbassyAirfield(SubstrateConfigSC.AIRFIELD_ALWAYS);
            always = EmbassyAirfieldSC.buildsNow();
            SubstrateConfigSC.setEmbassyAirfield(SubstrateConfigSC.AIRFIELD_AUTO);
            auto = EmbassyAirfieldSC.buildsNow() == Loader.isModLoaded(EmbassyAirfieldSC.ADDON);
        } finally {
            SubstrateConfigSC.setEmbassyAirfield(was);
        }
        int plus = 0;
        for (long ws : SEEDS) {
            int side = EmbassyAirfieldSC.sideOf(ws);
            plus += side == 1 ? 1 : 0;
            if (side != 1 && side != -1) {
                note(bad, "sideOf " + side);
            }
        }
        check(none && trip && noneKind && kept && written && same == SEEDS.length && never && always && auto && bad.length() == 0, "decision and record"
                + " (ЛП-1, ЛП-19): no \"X\" - no decision, no field " + none + "; writeDecision -> decisionOf the same, the field FULL side -1 ILS high"
                + " (cached) " + trip + "; NONE - no field " + noneKind + "; fill without air keeps \"X\" " + kept + "; record(plan with air) writes it, planOf"
                + " reads it back (the same field) " + written + "; plan(seed, site, floor) = plan(..., null) on " + same + " of " + SEEDS.length + " sites; the"
                + " mode: NEVER " + never + ", ALWAYS " + always + ", AUTO = the add-on loaded (" + Loader.isModLoaded(EmbassyAirfieldSC.ADDON) + ") " + auto
                + "; sideOf +1 on " + plus + " of " + SEEDS.length + " seeds" + bad);
    }

    /** Two plans have the same posts (kind, places), lots (kind, box, facing, variant, height), points and footprint. */
    static boolean plansAlike(EmbassyLayoutSC.Plan a, EmbassyLayoutSC.Plan b) {
        if (a.posts.size() != b.posts.size() || a.lots.size() != b.lots.size() || !Arrays.equals(a.footprint, b.footprint)
                || a.points.size() != b.points.size() || a.gateDir != b.gateDir || a.floorY != b.floorY) {
            return false;
        }
        for (int i = 0; i < a.posts.size(); i++) {
            SettlementLayoutSC.Post p = a.posts.get(i), q = b.posts.get(i);
            if (p.kind != q.kind || p.x != q.x || p.y != q.y || p.z != q.z || p.homeX != q.homeX || p.homeY != q.homeY || p.homeZ != q.homeZ) {
                return false;
            }
        }
        for (int i = 0; i < a.lots.size(); i++) {
            SettlementLayoutSC.Lot p = a.lots.get(i), q = b.lots.get(i);
            if (p.kind != q.kind || p.x0 != q.x0 || p.z0 != q.z0 || p.x1 != q.x1 || p.z1 != q.z1 || p.facing != q.facing || p.variant != q.variant
                    || p.height != q.height) {
                return false;
            }
        }
        for (int i = 0; i < a.points.size(); i++) {
            if (!Arrays.equals(a.points.get(i), b.points.get(i)) || !a.pointNames.get(i).equals(b.pointNames.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 6. EmbassyAirRulesSC (§7.1, ЛП-10..ЛП-16)

    private static void rules() {
        StringBuilder bad = new StringBuilder();
        int n = EmbassyAirRulesSC.SERVICE_KEYS.length;
        boolean lengths = n == 9 && EmbassyAirRulesSC.MIN_LEVEL.length == n && EmbassyAirRulesSC.MAX_LEVEL.length == n && EmbassyAirRulesSC.BASE.length == n
                && EmbassyAirRulesSC.UNIT.length == n && EmbassyAirRulesSC.PER_SERVICE.length == n && EmbassyAirRulesSC.PER_DAY.length == n
                && EmbassyAirRulesSC.PAY_KEYS.length == EmbassyAirRulesSC.PAY_UNKNOWN + 1;
        for (int s = 0; s < n; s++) {
            if (EmbassyAirRulesSC.service(EmbassyAirRulesSC.SERVICE_KEYS[s]) != s) {
                note(bad, "service(" + EmbassyAirRulesSC.SERVICE_KEYS[s] + ")");
            }
        }
        boolean keys = EmbassyAirRulesSC.service("nope") == -1 && EmbassyAirRulesSC.service(null) == -1
                && "sc.airfield.pay.limit".equals(EmbassyAirRulesSC.payKey(EmbassyAirRulesSC.PAY_LIMIT))
                && "sc.airfield.pay.unknown".equals(EmbassyAirRulesSC.payKey(99)) && "sc.airfield.pay.unknown".equals(EmbassyAirRulesSC.payKey(-1));
        // refusal's order: unknown, «Враг», the level, the caps
        int ch = EmbassyAirRulesSC.S_CHARGE, cap = EmbassyAirRulesSC.PER_SERVICE[ch], day = EmbassyAirRulesSC.PER_DAY[ch];
        int em = EmbassyAirRulesSC.S_EMERGENCY, gas = EmbassyAirRulesSC.S_ARMOUR_GAS, h2 = EmbassyAirRulesSC.S_HYDROGEN;
        int ok = EmbassyAirRulesSC.PAY_OK, enemy = EmbassyAirRulesSC.PAY_ENEMY, level = EmbassyAirRulesSC.PAY_LEVEL, limit = EmbassyAirRulesSC.PAY_LIMIT;
        int[][] cases = {
                {EmbassyAirRulesSC.refusal(-1, ReputationSC.PARTNER, false, 1, 0), EmbassyAirRulesSC.PAY_UNKNOWN},
                {EmbassyAirRulesSC.refusal(n, ReputationSC.PARTNER, false, 1, 0), EmbassyAirRulesSC.PAY_UNKNOWN},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.PARTNER, false, -1, 0), EmbassyAirRulesSC.PAY_UNKNOWN},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.HONOURED, true, cap * 10L, 0), enemy},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.ENEMY, false, 1, 0), enemy},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.STRANGER, false, cap * 10L, 0), level},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.ACQUAINTANCE, false, cap + 1L, 0), limit},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.ACQUAINTANCE, false, cap, day - cap), ok},
                {EmbassyAirRulesSC.refusal(ch, ReputationSC.ACQUAINTANCE, false, cap, day - cap + 1L), limit},
                {EmbassyAirRulesSC.refusal(em, ReputationSC.STRANGER, false, 1, 0), ok},
                {EmbassyAirRulesSC.refusal(em, ReputationSC.VIOLATOR, false, 1, 0), ok},
                {EmbassyAirRulesSC.refusal(em, ReputationSC.ACQUAINTANCE, false, 1, 0), level},
                {EmbassyAirRulesSC.refusal(em, ReputationSC.STRANGER, false, 1, 1), limit},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_PARK, ReputationSC.STRANGER, false, 3, 0), ok},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_PARK, ReputationSC.VIOLATOR, false, 1, 0), level},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_TOW, ReputationSC.VIOLATOR, false, 1, 0), ok},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_TOW, ReputationSC.ENEMY, false, 1, 0), enemy},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_CHART, ReputationSC.STRANGER, false, 1, 0), ok},
                {EmbassyAirRulesSC.refusal(gas, ReputationSC.ACQUAINTANCE, false, 16000, 0), ok},
                {EmbassyAirRulesSC.refusal(gas, ReputationSC.ACQUAINTANCE, false, 4001, 12000), limit},
                {EmbassyAirRulesSC.refusal(gas, ReputationSC.STRANGER, false, 1000, 0), level},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_REPAIR, ReputationSC.OWN, false, 60, 0), ok},
                {EmbassyAirRulesSC.refusal(EmbassyAirRulesSC.S_REPAIR, ReputationSC.OWN, false, 61, 0), limit},
                {EmbassyAirRulesSC.refusal(h2, ReputationSC.PARTNER, false, 48000, 16000), ok},
                {EmbassyAirRulesSC.refusal(h2, ReputationSC.PARTNER, false, 48000, 16001), limit}};
        for (int i = 0; i < cases.length; i++) {
            if (cases[i][0] != cases[i][1]) {
                note(bad, "case " + i + ": " + cases[i][0] + " not " + cases[i][1]);
            }
        }
        // the units (rounded up) and the prices at every level (base x units x PRICE x the spread, up; free - 0)
        boolean units = EmbassyAirRulesSC.units(ch, 0) == 0 && EmbassyAirRulesSC.units(ch, 1) == 1 && EmbassyAirRulesSC.units(ch, 2000000) == 1
                && EmbassyAirRulesSC.units(ch, 2000001) == 2 && EmbassyAirRulesSC.units(h2, 48000) == 6 && EmbassyAirRulesSC.units(-1, 5) == 0
                && EmbassyAirRulesSC.armourUnits(4001) == 2 && EmbassyAirRulesSC.armourUnits(16000) == 4;
        long seed = 0x5EEDA1F1EL;
        for (int s = 0; s < n; s++) {
            long amount = 2L * EmbassyAirRulesSC.UNIT[s] + 1L;                  // three units of every measure
            int u = EmbassyAirRulesSC.units(s, amount);
            int pct = TradeRulesSC.seedPct(seed, EmbassyAirRulesSC.COUNT_PREFIX + EmbassyAirRulesSC.SERVICE_KEYS[s]);
            for (int lv = ReputationSC.VIOLATOR; lv <= ReputationSC.HONOURED; lv++) {
                long hundredths = Math.round(ReputationSC.PRICE[lv] * 100F);
                long want = Math.max(1L, ((long) EmbassyAirRulesSC.BASE[s] * u * hundredths * (100 + pct) + 9999L) / 10000L);
                int got = EmbassyAirRulesSC.price(s, lv, seed, amount, false);
                if (u != 3 || got != want || EmbassyAirRulesSC.price(s, lv, seed, amount, true) != 0) {
                    note(bad, EmbassyAirRulesSC.SERVICE_KEYS[s] + "@" + lv + " " + got + " not " + want + " (units " + u + ")");
                }
            }
        }
        // the air defence's pure rules and the «Лётная книжка»
        boolean admit = EmbassyAirRulesSC.admitted(ReputationSC.ACQUAINTANCE, false) && EmbassyAirRulesSC.admitted(ReputationSC.HONOURED, false)
                && !EmbassyAirRulesSC.admitted(ReputationSC.STRANGER, false) && !EmbassyAirRulesSC.admitted(ReputationSC.VIOLATOR, false)
                && !EmbassyAirRulesSC.admitted(ReputationSC.ENEMY, false) && !EmbassyAirRulesSC.admitted(ReputationSC.PARTNER, true)
                && EmbassyAirRulesSC.ADMIT_LEVEL == ReputationSC.ACQUAINTANCE;
        boolean pass = EmbassyAirRulesSC.passAvailable(-1L, 0L) && EmbassyAirRulesSC.passAvailable(4L, 5L) && !EmbassyAirRulesSC.passAvailable(5L, 5L)
                && !EmbassyAirRulesSC.passAvailable(6L, 5L);
        // the review's: one warning a day (the same day's end kept - no new seconds), a huge amount never wraps to one chip
        long ws = EmbassyAirRulesSC.WARN_SECONDS * 20L;
        boolean warn = EmbassyAirRulesSC.warnEnd(-1L, 0L, 5L, 1000L) == 1000L + ws && EmbassyAirRulesSC.warnEnd(4L, 900L, 5L, 1000L) == 1000L + ws
                && EmbassyAirRulesSC.warnEnd(5L, 900L, 5L, 5000L) == 900L && !EmbassyAirRulesSC.WARN_DAY.equals(EmbassyAirRulesSC.PASS_DAY)
                && !EmbassyAirRulesSC.WARN_END.equals(EmbassyAirRulesSC.PASS_UNTIL) && !EmbassyAirRulesSC.WARN_DAY.equals(EmbassyAirServiceSC.CHARTS)
                && !EmbassyAirRulesSC.WARN_END.equals(EmbassyAirServiceSC.CHARTS);
        int huge = EmbassyAirRulesSC.price(EmbassyAirRulesSC.S_ELECTROLYTE, ReputationSC.ACQUAINTANCE, seed, (1L << 31) + 5L, false);
        boolean wrap = huge > 1000000 && EmbassyAirRulesSC.price(EmbassyAirRulesSC.S_TOW, ReputationSC.ACQUAINTANCE, seed, Long.MAX_VALUE, false) > 1000000
                && EmbassyAirRulesSC.units(ch, Long.MAX_VALUE) == Integer.MAX_VALUE && EmbassyAirRulesSC.price(gas, ReputationSC.ACQUAINTANCE, seed,
                Long.MAX_VALUE, false) > 1000000 && EmbassyAirRulesSC.refusal(gas, ReputationSC.ACQUAINTANCE, false, Long.MAX_VALUE, 1) == limit;
        pass &= warn && wrap;
        boolean book = EmbassyAirRulesSC.logbookChips(0) == 2 && EmbassyAirRulesSC.logbookChips(62) == 2 && EmbassyAirRulesSC.logbookChips(63) == 1
                && EmbassyAirRulesSC.logbookChips(TradeRulesSC.CHIPS_PER_DAY) == 0 && EmbassyAirRulesSC.logbookChips(99) == 0
                && EmbassyAirRulesSC.LOGBOOK_CHIPS == 2 && EmbassyAirRulesSC.LOGBOOK_TRUST == 5;
        SettlementRegistrySC.PlayerRec pr = new SettlementRegistrySC.PlayerRec();
        boolean landedNone = !EmbassyAirServiceSC.landed(pr, 7) && !EmbassyAirServiceSC.landed(null, 7);
        NBTTagCompound lb = new NBTTagCompound();
        lb.setIntArray(EmbassyAirRulesSC.LANDED, new int[]{3, 7});
        pr.extra.setTag(EmbassyAirRulesSC.LOGBOOK, lb);
        boolean landed = landedNone && EmbassyAirServiceSC.landed(pr, 7) && EmbassyAirServiceSC.landed(pr, 3) && !EmbassyAirServiceSC.landed(pr, 5);
        boolean numbers = EmbassyAirRulesSC.AD_RADIUS == 256 && EmbassyAirRulesSC.WARN_SECONDS == 10 && EmbassyAirRulesSC.PASS_SECONDS == 60
                && EmbassyAirRulesSC.ESCORT_RANGE == 64 && EmbassyAirRulesSC.PER_DAY[gas] == 16000 && EmbassyAirRulesSC.UNIT[gas] == 4000
                && EmbassyAirRulesSC.COLUMN_GASES.length == 2;
        check(lengths && keys && bad.length() == 0 && units && admit && pass && book && landed && numbers, "services' rules (§7.1, ЛП-10..ЛП-16): the"
                + " tables of " + n + " services " + lengths + ", keys " + keys + "; " + cases.length + " refusals in order (unknown, «Враг», the level, the"
                + " caps; the emergency one only «Чужак» / «Нарушитель», once a day), the units rounded up " + units + ", the prices at every level"
                + " (base x units x PRICE x the spread, up; free 0); admitted from «Знакомый», never the «Враг» " + admit + "; one 7700 pass a day, one"
                + " warning a day (" + warn + "), no price wrapped past 2^31 (" + huge + ") " + pass
                + "; the logbook's 2 chips under the day's 64, +5 " + book + "; landed " + landed + "; AD 256 / 10 s / 60 s / 64, gases 16 000 mB " + numbers + bad);
    }

    // ------------------------------------------------------------------ 7. the API (§5)

    private static void api() throws Exception {
        boolean codes = EmbassyAirApiSC.API_VERSION == 1 && EmbassyAirApiSC.PAY_OK == EmbassyAirRulesSC.PAY_OK
                && EmbassyAirApiSC.PAY_ENEMY == EmbassyAirRulesSC.PAY_ENEMY && EmbassyAirApiSC.PAY_LEVEL == EmbassyAirRulesSC.PAY_LEVEL
                && EmbassyAirApiSC.PAY_LIMIT == EmbassyAirRulesSC.PAY_LIMIT && EmbassyAirApiSC.PAY_CHIPS == EmbassyAirRulesSC.PAY_CHIPS
                && EmbassyAirApiSC.PAY_NO_FIELD == EmbassyAirRulesSC.PAY_NO_FIELD && EmbassyAirApiSC.PAY_UNKNOWN == EmbassyAirRulesSC.PAY_UNKNOWN
                && EmbassyAirApiSC.PASS_GRANTED == EmbassyAirDefenceSC.PASS_GRANTED && EmbassyAirApiSC.PASS_RUNNING == EmbassyAirDefenceSC.PASS_RUNNING
                && EmbassyAirApiSC.PASS_USED == EmbassyAirDefenceSC.PASS_USED && EmbassyAirApiSC.PASS_NO_FIELD == EmbassyAirDefenceSC.PASS_NO_FIELD
                && EmbassyAirApiSC.PASS_ADMITTED == EmbassyAirDefenceSC.PASS_ADMITTED && EmbassyAirApiSC.AD_WARN == EmbassyAirDefenceSC.AD_WARN
                && EmbassyAirApiSC.AD_FIRE == EmbassyAirDefenceSC.AD_FIRE && EmbassyAirApiSC.AD_PASS == EmbassyAirDefenceSC.AD_PASS
                && EmbassyAirApiSC.AD_PASS_USED == EmbassyAirDefenceSC.AD_PASS_USED && EmbassyAirApiSC.AD_PASS_OVER == EmbassyAirDefenceSC.AD_PASS_OVER
                && EmbassyAirApiSC.AD_LEFT == EmbassyAirDefenceSC.AD_LEFT && EmbassyAirDefenceSC.AD_KEYS.length == EmbassyAirDefenceSC.AD_LEFT + 1
                && EmbassyAirApiSC.adRadius() == EmbassyAirRulesSC.AD_RADIUS && EmbassyAirApiSC.warnSeconds() == EmbassyAirRulesSC.WARN_SECONDS
                && EmbassyAirfieldSC.ADDON.equals(EmbassyAirApiSC.ADDON_MODID) && "SiliconAgeVehicles".equals(EmbassyAirApiSC.ADDON_MODID)
                && AirfieldInfoSC.FULL == EmbassyAirfieldSC.FULL && AirfieldInfoSC.SHORT == EmbassyAirfieldSC.SHORT && AirfieldInfoSC.HELI == EmbassyAirfieldSC.HELI;
        // the runway's surface: metas 0, 3, 5, 6, 7 of the airfield's block; not 1, 2, 4, 8, nor another block
        String surface = "skipped (embassyAirfieldSC not registered yet)";
        boolean surfaceOk = true;
        Block af = EmbassyBlocksSC.airfield;
        if (af != null) {
            StringBuilder s = new StringBuilder();
            for (int m = 0; m < 16; m++) {
                boolean want = m == BlockAirfieldSC.RUNWAY || m == BlockAirfieldSC.MARK_WHITE || m == BlockAirfieldSC.LIGHT_EDGE
                        || m == BlockAirfieldSC.LIGHT_THRESHOLD || m == BlockAirfieldSC.LIGHT_END;
                if (EmbassyAirApiSC.isRunwaySurface(af, m) != want) {
                    s.append(' ').append(m);
                }
            }
            surfaceOk = s.length() == 0 && !EmbassyAirApiSC.isRunwaySurface(Blocks.stone, 0) && !EmbassyAirApiSC.isRunwaySurface(null, 0)
                    && EmbassyAirApiSC.isEmbassyBlock(af) && !EmbassyAirApiSC.isEmbassyBlock(Blocks.stone) && !EmbassyAirApiSC.isEmbassyBlock(null)
                    && (SettlementBlocksSC.showcase2 == null || EmbassyAirApiSC.isEmbassyBlock(SettlementBlocksSC.showcase2))
                    && (EmbassyBlocksSC.ring == null || EmbassyAirApiSC.isEmbassyBlock(EmbassyBlocksSC.ring));
            surface = surfaceOk ? "ok" : "BAD" + s;
        }
        // AirfieldInfoSC of a test record (its constructor is the API's own: asked by reflection), a FULL and a SHORT field
        StringBuilder info = new StringBuilder();
        Constructor<AirfieldInfoSC> c = AirfieldInfoSC.class.getDeclaredConstructor(SettlementRegistrySC.Settlement.class, EmbassyAirfieldSC.Field.class);
        c.setAccessible(true);
        for (int k = 0; k < 2; k++) {
            int kind = k == 0 ? EmbassyAirfieldSC.FULL : EmbassyAirfieldSC.SHORT;
            SettlementRegistrySC.Settlement s = SelfTestEmbassySC.record(SettlementSizeSC.EMBASSY, 0, 3000 + 4000 * k, 5000);
            s.id = 77;
            s.nameElement = 0;
            s.nameNumber = 1;
            EmbassyAirfieldSC.writeDecision(s, air(kind, 1));
            EmbassyAirfieldSC.Field f = EmbassyAirfieldSC.fieldOf(s);
            AirfieldInfoSC i = c.newInstance(s, f);
            boolean full = kind == EmbassyAirfieldSC.FULL;
            boolean good = i.id == 77 && i.kind == kind && "XTAA".equals(i.icao) && i.width == (full ? 9 : 7) && i.length == (full ? 201 : 91)
                    && i.landingDistance(true) == (full ? 181 : 91) && i.landingDistance(false) == (full ? 181 : 91) && i.ilsLow == full && i.ilsHigh == full
                    && i.adRadius == EmbassyAirRulesSC.AD_RADIUS && i.spots() == f.spots.length && i.columns().length == f.columns.length
                    && i.adPosts().length == EmbassyAirfieldSC.AA_POSTS
                    && i.lights(BlockAirfieldSC.LIGHT_EDGE).length == f.lights(BlockAirfieldSC.LIGHT_EDGE).size()
                    && (i.towerDesk() != null) == full && (i.beacon() != null) == full && i.funnelBoxes().length == (full ? 2 : 0)
                    && i.approachBoxes().length == 2 && i.spotKind(i.spots() - 1) == AirfieldInfoSC.SPOT_HELI
                    && "sc.airfield.spot.heli".equals(i.spotKey(i.spots() - 1)) && (!full || "sc.airfield.spot.ms1".equals(i.spotKey(0)))
                    && i.onRunway(i.threshold(true)[0], i.threshold(true)[2]) && i.inDome(i.coreX + 0.5D, i.coreZ + 0.5D)
                    && !i.inDome(i.end(true)[0], i.end(true)[2]);
            // the numbers by the main gate (north: landings 36 over the low end, 18 over the high one), along X for east / west
            good &= i.gateDir == EmbassyLayoutSC.gateDir(s.seed) && i.numberLow.equals(EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(i.gateDir, true)))
                    && i.numberHigh.equals(EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(i.gateDir, false)))
                    && i.alongX == (i.gateDir == 1 || i.gateDir == 3) && i.threshold(true)[1] == s.floorY;
            good &= Arrays.equals(i.thresholdKey(true), f.block(f.thresholdRow(true), f.axis, 0));
            if (!good) {
                note(info, EmbassyAirfieldSC.KIND_KEYS[kind] + " gate " + i.gateDir + ": " + i.icao + " " + i.width + "x" + i.length + " landing "
                        + i.landingDistance(true) + " numbers " + i.numberLow + "/" + i.numberHigh);
            }
        }
        check(codes && surfaceOk && info.length() == 0, "API (§5): API_VERSION 1, PAY_ / PASS_ / AD_ the rules' numbers, adRadius / warnSeconds, the add-on's"
                + " mod id " + codes + "; isRunwaySurface for metas 0, 3, 5, 6, 7 only, isEmbassyBlock: " + surface + "; AirfieldInfoSC of a FULL and a SHORT"
                + " record - XTAA, 9 x 201 / 7 x 91, landing 181 / 91, the ILS ends, stands, columns, guns, lights, the tower, the funnels, the threshold's"
                + " key, the dome, the numbers" + (info.length() == 0 ? " ok" : " - BAD:" + info));
    }

    // ------------------------------------------------------------------ 8. the pieces (§2.2, ЛП-5)

    @SuppressWarnings("unchecked")
    private static void pieces() {
        String[] old = {"Heart", "Street", "Gate", "Wall", "Tower", "Workshop", "Depot", "Greenhouse", "House", "Vokzal", "Ring", "Energy"};
        String[] added = {"Runway", "Apron", "Terminal", "Hangar", "Helipad", "Funnel", "AirDefence"};
        String[] names = EmbassyPiecesSC.NAMES;
        boolean order = names.length == old.length + added.length;
        for (int i = 0; order && i < names.length; i++) {
            order = names[i].equals(i < old.length ? old[i] : added[i - old.length]);
        }
        // the new pieces' classes: StructureComponents with the no-argument constructor MapGenStructureIO loads them by
        StringBuilder bad = new StringBuilder();
        for (String n : added) {
            try {
                Class<?> k = Class.forName("com.sc.embassy.ComponentEmb" + n + "SC");
                if (!StructureComponent.class.isAssignableFrom(k) || Modifier.isAbstract(k.getModifiers())) {
                    note(bad, n + " not a StructureComponent");
                }
                k.getConstructor();
            } catch (Throwable t) {
                note(bad, n + " " + t);
            }
        }
        // registered: every name in MapGenStructureIO's maps (init registers them - at preInit only if something did already)
        java.util.Set<String> io = new java.util.HashSet<String>();
        for (java.lang.reflect.Field fd : MapGenStructureIO.class.getDeclaredFields()) {
            if (!Modifier.isStatic(fd.getModifiers()) || !Map.class.isAssignableFrom(fd.getType())) {
                continue;
            }
            try {
                fd.setAccessible(true);
                for (Map.Entry<Object, Object> e : ((Map<Object, Object>) fd.get(null)).entrySet()) {
                    for (Object o : new Object[]{e.getKey(), e.getValue()}) {
                        if (o instanceof String) {
                            io.add((String) o);
                        }
                    }
                }
            } catch (Exception ex) {
                note(bad, "MapGenStructureIO " + ex);
            }
        }
        String registered;
        if (io.contains(EmbassySC.START_NAME)) {
            StringBuilder miss = new StringBuilder();
            for (String n : names) {
                if (!io.contains(EmbassySC.PIECE_PREFIX + n)) {
                    miss.append(' ').append(n);
                }
            }
            registered = miss.length() == 0 ? "all " + names.length + " registered" : "NOT registered:" + miss;
            if (miss.length() > 0) {
                note(bad, registered);
            }
        } else {
            registered = "not registered yet (init: the world test checks them)";
        }
        // the field's lots: kinds LOT_RUNWAY..LOT_AIR_DEFENCE, never in Plan.lots; HELI 6, SHORT 7, FULL 12; one piece each after the city's
        boolean lotKinds = EmbassyLayoutSC.LOT_RUNWAY == 14 && EmbassyLayoutSC.LOT_APRON == 15 && EmbassyLayoutSC.LOT_TERMINAL == 16
                && EmbassyLayoutSC.LOT_HANGAR == 17 && EmbassyLayoutSC.LOT_HELIPAD == 18 && EmbassyLayoutSC.LOT_FUNNEL == 19
                && EmbassyLayoutSC.LOT_AIR_DEFENCE == 20;
        int[] want = {6, 7, 12};
        String counts = "";
        EmbassySiteSC.Site s = site(SEEDS[0], 90);
        int without = -1;
        try {
            without = EmbassyPiecesSC.pieces(5, EmbassyLayoutSC.plan(SEEDS[0], s, s.floorY, null)).size();
        } catch (Throwable t) {
            note(bad, "pieces() without a field " + t);
        }
        for (int k = 0; k < KINDS.length; k++) {
            EmbassyLayoutSC.Plan p = EmbassyLayoutSC.plan(SEEDS[0], s, s.floorY, air(KINDS[k], 1));
            List<SettlementLayoutSC.Lot> lots = p.field.lots();
            counts += (k == 0 ? "" : " / ") + lots.size();
            for (SettlementLayoutSC.Lot l : lots) {
                if (l.kind < EmbassyLayoutSC.LOT_RUNWAY || l.kind > EmbassyLayoutSC.LOT_AIR_DEFENCE) {
                    note(bad, "a field lot of kind " + l.kind);
                }
            }
            for (SettlementLayoutSC.Lot l : p.lots) {
                if (l.kind >= EmbassyLayoutSC.LOT_RUNWAY) {
                    note(bad, "a field lot in Plan.lots");
                }
            }
            if (lots.size() != want[k]) {
                note(bad, EmbassyAirfieldSC.KIND_KEYS[KINDS[k]] + " " + lots.size() + " lots");
            }
            try {
                int with = EmbassyPiecesSC.pieces(5, p).size();
                if (without < 0 || with - without != lots.size()) {
                    note(bad, EmbassyAirfieldSC.KIND_KEYS[KINDS[k]] + " pieces " + with + " - " + without + " not " + lots.size());
                }
            } catch (Throwable t) {
                note(bad, "pieces() " + EmbassyAirfieldSC.KIND_KEYS[KINDS[k]] + " " + t);
            }
        }
        check(order && lotKinds && bad.length() == 0, "pieces (§2.2, ЛП-5): EmbassyPiecesSC.NAMES - the 12 of Ф6, then Runway .. AirDefence " + order
                + "; their classes (StructureComponent, no-argument constructor); " + registered + "; LOT_RUNWAY 14 .. LOT_AIR_DEFENCE 20 " + lotKinds
                + "; the field's lots HELI / SHORT / FULL " + counts + " (6 / 7 / 12), never in Plan.lots, one piece each after the city's" + bad);
    }

    // ------------------------------------------------------------------ 9. the metas (§2.2)

    private static void metas() {
        String[] showcase = {"energyStorage", "transformer", "windGenerator", "solarPanel", "crate", "scrapPile", "rack", "gravityCoil", "cryoTank",
                "dispatchConsole", "towerConsole", "serviceColumn", "airfieldBeacon", "aaPost"};
        boolean show = Arrays.equals(BlockShowcase2SC.NAMES, showcase) && BlockShowcase2SC.LIGHT.length == showcase.length
                && BlockShowcase2SC.TOWER_CONSOLE == 10 && BlockShowcase2SC.SERVICE_COLUMN == 11 && BlockShowcase2SC.AIRFIELD_BEACON == 12
                && BlockShowcase2SC.AA_POST == 13 && BlockShowcase2SC.LIGHT[BlockShowcase2SC.AIRFIELD_BEACON] > 0;
        String[] surface = {"runway", "shoulder", "apron", "markWhite", "markYellow", "lightEdge", "lightThreshold", "lightEnd", "lightTaxi"};
        boolean field = Arrays.equals(BlockAirfieldSC.NAMES, surface) && Arrays.equals(BlockAirfieldSC.LIGHT, new int[]{0, 0, 0, 0, 0, 10, 10, 8, 6})
                && BlockAirfieldSC.RUNWAY == 0 && BlockAirfieldSC.LIGHT_TAXI == 8;
        StringBuilder runway = new StringBuilder();
        for (int m = -1; m <= 9; m++) {
            boolean want = m == 0 || m == 3 || m == 5 || m == 6 || m == 7;
            if (BlockAirfieldSC.runway(m) != want) {
                runway.append(' ').append(m);
            }
        }
        String block = "not registered yet";
        boolean blockOk = true;
        Block af = EmbassyBlocksSC.airfield;
        if (af != null) {
            Object reg = Block.blockRegistry.getObject("SiliconAge:embassyAirfieldSC");
            boolean names = true;
            for (int m = 0; m < surface.length; m++) {
                ItemStack st = new ItemStack(af, 1, m);
                names &= st.getItem() != null && ("tile.siliconage.embassyAirfield." + surface[m]).equals(st.getItem().getUnlocalizedName(st));
            }
            blockOk = reg == af && SettlementBlocksSC.unbreakable(af) && !af.isOpaqueCube() && af.renderAsNormalBlock() && names
                    && af.getMaterial().isSolid() && af.getBlockHardness(null, 0, 0, 0) < 0F;
            block = (blockOk ? "ok" : "BAD") + " (SiliconAge:embassyAirfieldSC registered " + (reg == af) + ", unbreakable " + SettlementBlocksSC.unbreakable(af)
                    + ", not an opaque cube " + !af.isOpaqueCube() + ", the items' names " + names + ")";
        }
        check(show && field && runway.length() == 0 && blockOk, "metas (§2.2): showcase2SC's NAMES 14 (10 towerConsole, 11 serviceColumn, 12 airfieldBeacon,"
                + " 13 aaPost after Ф6's 7..9), LIGHT as long " + show + "; embassyAirfieldSC's 9 names and LIGHT {0,0,0,0,0,10,10,8,6} " + field + ", runway()"
                + " for 0, 3, 5, 6, 7 only" + (runway.length() == 0 ? "" : " - NOT:" + runway) + "; the block " + block);
    }

    // ------------------------------------------------------------------ 10. the names (§3.3, the user's choice 10)

    private static void names() {
        boolean icao = "XTAA".equals(EmbassyAirfieldSC.icao(0, 1)) && "XWOA".equals(EmbassyAirfieldSC.icao(6, 1)) && "XVAA".equals(EmbassyAirfieldSC.icao(17, 1))
                && "XYTA".equals(EmbassyAirfieldSC.icao(18, 1)) && "XTAA".equals(EmbassyAirfieldSC.icao(0, 27)) && "XTAZ".equals(EmbassyAirfieldSC.icao(0, 26))
                && "XNBB".equals(EmbassyAirfieldSC.icao(1, 2)) && EmbassyAirfieldSC.icao(0, 1).startsWith(EmbassyAirfieldSC.ICAO_PREFIX);
        StringBuilder table = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < EmbassyAirfieldSC.ICAO_ELEMENT.length; i++) {
            String e = EmbassyAirfieldSC.ICAO_ELEMENT[i];
            if (!e.matches("[A-Z]{2}") || !seen.add(e)) {
                table.append(' ').append(i).append(':').append(e);
            }
        }
        boolean length = EmbassyAirfieldSC.ICAO_ELEMENT.length == NpcNamesSC.ELEMENTS.length;
        String[][] numbers = {{"36", "18"}, {"09", "27"}, {"18", "36"}, {"27", "09"}};
        boolean rwy = true;
        for (int g = 0; g < 4; g++) {
            rwy &= numbers[g][0].equals(EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(g, true)))
                    && numbers[g][1].equals(EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(g, false)));
        }
        rwy &= EmbassyAirfieldSC.heading(0, true) == 360 && EmbassyAirfieldSC.heading(0, false) == 180 && EmbassyAirfieldSC.heading(1, true) == 90
                && "36".equals(EmbassyAirfieldSC.number(0)) && "05".equals(EmbassyAirfieldSC.number(50));
        check(icao && table.length() == 0 && length && rwy, "names (§3.3): icao(0, 1) XTAA, (6, 1) XWOA, (17, 1) XVAA, (18, 1) XYTA, (0, 27) XTAA again "
                + icao + "; " + EmbassyAirfieldSC.ICAO_ELEMENT.length + " element codes of two letters, none twice, one an element of NpcNamesSC ("
                + NpcNamesSC.ELEMENTS.length + ") " + length + (table.length() == 0 ? "" : " - BAD:" + table) + "; the runway's numbers by the main gate"
                + " (north 36/18, east 09/27, south 18/36, west 27/09) " + rwy);
    }

    // ------------------------------------------------------------------ 11. the registry (§0.1: reach, inBelt, overlapping)

    private static void registry() {
        SettlementRegistrySC r = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        EmbassySiteSC.Site s = site(SEEDS[4], 94);
        EmbassyLayoutSC.Plan p = EmbassyLayoutSC.plan(SEEDS[4], s, s.floorY, air(EmbassyAirfieldSC.FULL, 1));
        SettlementRegistrySC.Settlement emb = r.add(EmbassyLayoutSC.record(p, 0, 0L));
        // the same embassy without a field 20000 away (an old record: made before, or without the add-on)
        int off = 20000;
        EmbassySiteSC.Site o = new EmbassySiteSC.Site(s.x + off, s.z, s.radius, s.floorY, s.seed, false);
        SettlementRegistrySC.Settlement old = r.add(EmbassyLayoutSC.record(EmbassyLayoutSC.plan(SEEDS[4], o, o.floorY, null), 0, 0L));
        EmbassyAirfieldSC.Field f = EmbassyAirfieldSC.fieldOf(emb);
        if (f == null) {
            check(false, "registry: the test record has no field");
            return;
        }
        int[] reach = emb.reach(), zone = f.zoneBox(), base = old.reach();
        boolean covers = reach[0] <= zone[0] && reach[1] <= zone[1] && reach[2] >= zone[2] && reach[3] >= zone[3];
        // the old one's reach: the belt and the dock's zone only, as before
        int rr = old.radius + SettlementSC.BELT + 1, d = old.dockZone + 1;
        int[] want = {Math.min(old.x - rr, old.dockX - d), Math.min(old.z - rr, old.dockZ - d), Math.max(old.x + rr, old.dockX + d),
                Math.max(old.z + rr, old.dockZ + d)};
        boolean oldReach = EmbassyAirfieldSC.fieldOf(old) == null && old.airfieldBox() == null && old.hasDock && Arrays.equals(base, want);
        // inBelt / inAirfield on the field (the runway 60 along, past the belt; the helipad), not past the far end; the old one's same cells - not
        int rx = f.x(60, f.axis), rz = f.z(60, f.axis), hx = f.x(f.helipad[0] + 5, f.helipad[1] + 5), hz = f.z(f.helipad[0] + 5, f.helipad[1] + 5);
        int fx = f.x(f.endHigh + 40, f.axis), fz = f.z(f.endHigh + 40, f.axis);
        boolean inBelt = emb.inBelt(rx + 0.5D, rz + 0.5D) && emb.inAirfield(rx + 0.5D, rz + 0.5D) && emb.inBelt(hx + 0.5D, hz + 0.5D)
                && !emb.inBelt(fx + 0.5D, fz + 0.5D) && !old.inBelt(rx + off + 0.5D, rz + 0.5D) && !old.inAirfield(rx + off + 0.5D, rz + 0.5D);
        // the region index finds it at the zone's corners; overlapping: a player's field box over the runway - this embassy, over the old one's - none
        boolean index = r.candidates(0, rx, rz).contains(emb) && r.candidates(0, zone[0], zone[1]).contains(emb) && r.candidates(0, zone[2], zone[3]).contains(emb);
        AxisAlignedBB over = AxisAlignedBB.getBoundingBox(rx - 3, s.floorY, rz - 3, rx + 4, s.floorY + 8, rz + 4);
        AxisAlignedBB overOld = AxisAlignedBB.getBoundingBox(rx + off - 3, s.floorY, rz - 3, rx + off + 4, s.floorY + 8, rz + 4);
        boolean overlap = r.overlapping(0, over) == emb && r.overlapping(0, overOld) == null && emb.airfieldBox() != null
                && emb.airfieldBox().minY == s.floorY - SettlementSC.FOUNDATION;
        // the review's: a field box inside the zone's box but on none of its rectangles (out past the dome's square, by no emplacement) - free
        int gx = f.x(-(f.radius + 10), -(f.radius + 10)), gz = f.z(-(f.radius + 10), -(f.radius + 10));
        AxisAlignedBB gap = AxisAlignedBB.getBoundingBox(gx - 2, s.floorY, gz - 2, gx + 3, s.floorY + 8, gz + 3);
        boolean inBox = gx - 2 >= zone[0] && gx + 2 <= zone[2] && gz - 2 >= zone[1] && gz + 2 <= zone[3];
        overlap &= inBox && emb.airfieldBox().intersectsWith(gap) && !emb.airfieldMeets(gap) && r.overlapping(0, gap) == null
                && emb.airfieldMeets(over);
        // ЛП-18 (the review's): only the field's Sentries get the long leash; the census's reach holds every slot's post (the second Gas
        // Engineer's by the columns and OUTPOST_HOME past it); the old record's leashes and reaches - the belt's
        int belt = emb.radius + SettlementSC.BELT, fieldSentries = 0, leashBad = 0, reachBad = 0, wide = 0;
        for (int i = 0; i < emb.slots.size(); i++) {
            SettlementRegistrySC.Slot sl = emb.slots.get(i);
            double dd = Math.sqrt(emb.distSq(sl.postX + 0.5D, sl.postZ + 0.5D));
            int leash = com.sc.settle.npc.ai.NpcLinkSC.leash(emb, i), census = com.sc.settle.RosterSC.reach(emb, i);
            boolean fs = com.sc.settle.npc.ai.NpcLinkSC.fieldSentry(emb, i), out = !sl.kindOf().guard && dd > emb.radius - 2;
            fieldSentries += fs ? 1 : 0;
            leashBad += sl.kindOf().guard && (fs ? leash < dd + com.sc.settle.npc.ai.NpcLinkSC.FIELD_LEASH - 1 : leash != belt) ? 1 : 0;
            reachBad += census < belt || census < (sl.kindOf().guard ? leash : dd + (out ? com.sc.settle.npc.ai.NpcLinkSC.OUTPOST_HOME : 0)) ? 1 : 0;
            wide = Math.max(wide, census);
        }
        for (int i = 0; i < old.slots.size(); i++) {
            reachBad += com.sc.settle.RosterSC.reach(old, i) != old.radius + SettlementSC.BELT ? 1 : 0;
            leashBad += com.sc.settle.npc.ai.NpcLinkSC.fieldSentry(old, i) || com.sc.settle.npc.ai.NpcLinkSC.leash(old, i) != old.radius + SettlementSC.BELT
                    ? 1 : 0;
        }
        boolean roster = fieldSentries == 2 && leashBad == 0 && reachBad == 0 && wide > belt;
        // the review's: the funnels' windows (EmbassyRepairSC.settle) - every funnel cell in one, none twice, at most 64; none without a field
        List<int[]> fw = com.sc.embassy.EmbassyRepairSC.funnelWindows(emb);
        int outside = 0, twice = 0;
        for (int i = 0; i < fw.size(); i++) {
            for (int j = i + 1; j < fw.size(); j++) {
                twice += fw.get(i)[0] == fw.get(j)[0] && fw.get(i)[1] == fw.get(j)[1] ? 1 : 0;
            }
        }
        for (int[] q : f.funnels) {
            for (int a = q[0]; a <= q[2]; a++) {
                for (int b = q[1]; b <= q[3]; b++) {
                    int x = f.x(a, b), z = f.z(a, b);
                    boolean in = false;
                    for (int[] win : fw) {
                        in |= x >= win[0] && x <= win[0] + 15 && z >= win[1] && z <= win[1] + 15;
                    }
                    outside += in ? 0 : 1;
                }
            }
        }
        boolean funnels = !fw.isEmpty() && fw.size() <= 64 && outside == 0 && twice == 0 && com.sc.embassy.EmbassyRepairSC.funnelWindows(old).isEmpty();
        check(covers && oldReach && inBelt && index && overlap && roster && funnels, "registry (§0.1): a FULL record's reach() covers its zoneBox "
                + Arrays.toString(zone) + " " + covers + "; a record without \"X\" - no field, no airfield box, its reach the belt and the dock's zone as"
                + " before " + oldReach + "; inBelt / inAirfield on the runway and the helipad, not past the far end nor at the old one's same cells "
                + inBelt + "; the region index finds it at the zone's corners " + index + "; overlapping: a field box over the runway - this embassy, over"
                + " the old one's - none, in the zone's box off its rectangles - none " + overlap + "; ЛП-18: the long leash only the field's 2 Sentries'"
                + " (" + fieldSentries + "), leashes off " + leashBad + ", the census's reach short of a post " + reachBad + " (the widest " + wide + ", the"
                + " belt " + belt + ") " + roster + "; the funnels' " + fw.size() + " windows to settle (every funnel cell in one: off " + outside
                + ", twice " + twice + "; none for the old one) " + funnels);
    }

    // ------------------------------------------------------------------ 12. the texts (§9) and the handbook

    @SuppressWarnings("unchecked")
    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>();
        keys.add("tile.siliconage.embassyAirfield.name");
        for (String n : BlockAirfieldSC.NAMES) {
            keys.add("tile.siliconage.embassyAirfield." + n + ".name");
            keys.add("sc.tooltip.embassyAirfield." + n);
        }
        for (int m = BlockShowcase2SC.TOWER_CONSOLE; m < BlockShowcase2SC.NAMES.length; m++) {
            String n = BlockShowcase2SC.NAMES[m];
            keys.add("tile.siliconage.showcase2." + n + ".name");
            keys.add("sc.tooltip.airfield." + n);
            keys.add("sc.tooltip.airfield." + n + ".details");
        }
        for (String k : EmbassyAirDefenceSC.AD_KEYS) {
            keys.add("sc.airfield.ad." + k);
        }
        for (int c = 0; c < EmbassyAirRulesSC.PAY_KEYS.length; c++) {
            keys.add(EmbassyAirRulesSC.payKey(c));
        }
        for (String k : EmbassyAirRulesSC.SERVICE_KEYS) {
            keys.add("sc.airfield.service." + k);
        }
        for (String k : EmbassyAirfieldSC.SPOT_KEYS) {
            keys.add("sc.airfield.spot." + k);
        }
        for (String k : EmbassyAirfieldSC.KIND_KEYS) {
            keys.add("sc.airfield.kind." + k);
        }
        StringBuilder missing = new StringBuilder(), args = new StringBuilder();
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        for (String[] m : CORE_ARGS) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                String v = lang.get(m[0]);
                if (v == null) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(m[0]);
                } else if (SelfTestEmbassySC.positional(v) != Integer.parseInt(m[1])) {
                    args.append(' ').append(m[0]).append('(').append(SelfTestEmbassySC.positional(v)).append(')');
                }
            }
        }
        // the AD lines take %1$s the embassy, %2$s the callsign, %3$s the seconds, %4$s the level: never a fifth
        for (String k : EmbassyAirDefenceSC.AD_KEYS) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                String v = lang.get("sc.airfield.ad." + k);
                if (v != null && (SelfTestEmbassySC.positional(v) > 4 || v.contains("%5$") || !v.contains("%2$s"))) {
                    args.append(lang == en ? " en " : " ru ").append("ad.").append(k);
                }
            }
        }
        // every airfield key of one file in the other with as many arguments; no « » anywhere, English is English; none of the font table
        StringBuilder diff = new StringBuilder();
        int all = 0;
        for (Map<String, String> from : new Map[]{en, ru}) {
            Map<String, String> to = from == en ? ru : en;
            for (Map.Entry<String, String> e : from.entrySet()) {
                if (!ours(e.getKey())) {
                    continue;
                }
                all += from == en ? 1 : 0;
                String v = e.getValue();
                if (!to.containsKey(e.getKey())) {
                    diff.append(' ').append(from == en ? "only en_US:" : "only ru_RU:").append(e.getKey());
                } else if (from == en && SelfTestEmbassySC.positional(v) != SelfTestEmbassySC.positional(to.get(e.getKey()))) {
                    diff.append(" args differ:").append(e.getKey());
                } else if (from == en && v.matches(".*[\\u0400-\\u04FF«»„].*")) {
                    diff.append(" Cyrillic / «» / „ in en_US:").append(e.getKey());
                } else if (from == ru && v.matches(".*[«»”].*")) {
                    diff.append(" «» / ” in ru_RU:").append(e.getKey());
                }
                for (int i = 0; i < v.length(); i++) {
                    if (SelfTestSC.fontPackUnsafe(v.charAt(i))) {
                        diff.append(" font table:").append(e.getKey());
                        break;
                    }
                }
            }
        }
        check(missing.length() == 0 && args.length() == 0 && diff.length() == 0, "texts (§9): " + keys.size() + " core keys (the surface's names and"
                + " tooltips, the showcases 10..13, the AD lines, the payments, the services, the stands, the kinds) and " + CORE_ARGS.length + " with their"
                + " callers' arguments in en_US and ru_RU" + missing + args + "; " + all + " airfield keys with the same arguments in both, no Cyrillic in"
                + " English, no « » anywhere, none of the font table's 128..255" + diff);

        // the handbook: «Город посольства» has the section «Лётное поле», no raw key; G on the airfield's stacks opens it
        BookContent.invalidate();
        StringBuilder book = new StringBuilder();
        BookEntry city = BookContent.byId("world.embassyCity");
        String text = city == null ? "" : city.searchText();
        for (String h : new String[]{"airhead", "air.layouthead", "air.serviceshead", "air.adhead", "air.bookhead"}) {
            String line = Lang.tr("sc.manual.world.embassyCity." + h).toLowerCase(java.util.Locale.ROOT);
            if (!text.contains(line)) {
                book.append(" lacks '").append(line).append('\'');
            }
        }
        for (String raw : new String[]{"sc.manual.", "sc.airfield.", "sc.tooltip.", "tile.siliconage.", "%s", "%d", "format error"}) {
            if (text.contains(raw)) {
                book.append(" raw '").append(raw).append('\'');
            }
        }
        int mapped = 0;
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        for (int m = 0; EmbassyBlocksSC.airfield != null && m < BlockAirfieldSC.NAMES.length; m++) {
            stacks.add(new ItemStack(EmbassyBlocksSC.airfield, 1, m));
        }
        for (int m = BlockShowcase2SC.TOWER_CONSOLE; SettlementBlocksSC.showcase2 != null && m < BlockShowcase2SC.NAMES.length; m++) {
            stacks.add(new ItemStack(SettlementBlocksSC.showcase2, 1, m));
        }
        for (ItemStack st : stacks) {
            BookEntry got = BookContent.entryFor(st);
            if (got != null && "world.embassyCity".equals(got.id)) {
                mapped++;
            } else {
                book.append(" G ").append(st.getItemDamage()).append("->").append(got == null ? "nothing" : got.id);
            }
        }
        BookContent.invalidate();
        check(city != null && book.length() == 0 && mapped == stacks.size() && !stacks.isEmpty(), "handbook (§9): «Город посольства» has the section"
                + " «Лётное поле» (the layout, the services, the air defence, the logbook) with no raw key; G on " + mapped + " of " + stacks.size()
                + " airfield stacks opens it" + book);
    }

    private static boolean ours(String k) {
        for (String p : PREFIXES) {
            if (k.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 13. package G's pure choose helpers (§3.2, EmbassyAirSiteSC by name)

    private static void packageG() throws Exception {
        Class<?> g = cls(SITE_CLASS);
        Method decide = method(g, "decide", int.class, boolean[].class, boolean[][].class, boolean[][].class);
        Method fits = method(g, "fullFits", boolean.class, boolean.class, boolean.class);
        Method ils = method(g, "fullIls", boolean.class, boolean.class, boolean.class, boolean.class);
        Method flags = method(g, "flags", boolean.class, boolean.class, boolean.class, boolean.class, float.class, float.class);
        Method count = method(g, "count", int[].class, int.class);
        Method tally = method(g, "tally");
        Method level = method(g, "levelProblem", int[].class);
        Method approach = method(g, "approachProblem", int[].class);
        Method levelTally = method(g, "levelTally", WorldChunkManager.class, EmbassyAirfieldSC.Field.class);
        Method approachTally = method(g, "approachTally", WorldChunkManager.class, EmbassyAirfieldSC.Field.class, int.class);
        String[] what = {"decide", "fullFits", "fullIls", "flags", "count", "tally", "levelProblem", "approachProblem", "levelTally", "approachTally"};
        Method[] all = {decide, fits, ils, flags, count, tally, level, approach, levelTally, approachTally};
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == null) {
                missing.append(' ').append(what[i]);
            }
        }
        if (missing.length() > 0) {
            check(false, "package G (§3.2): EmbassyAirSiteSC's pure helpers not found:" + missing);
            return;
        }
        int full = EmbassyAirfieldSC.FULL, shortK = EmbassyAirfieldSC.SHORT, lo = EmbassyAirfieldSC.ILS_LOW, hi = EmbassyAirfieldSC.ILS_HIGH;
        boolean[] both = {true, true};
        boolean[][] good = {{true, true}, {true, true}}, none = {{false, false}, {false, false}};
        StringBuilder bad = new StringBuilder();
        Object[][] cases = {
                {Integer.valueOf(1), both, good, none, new int[]{full, 1, lo | hi}},
                {Integer.valueOf(-1), both, good, none, new int[]{full, -1, lo | hi}},
                {Integer.valueOf(1), both, new boolean[][]{{false, true}, {true, true}}, none, new int[]{full, 1, hi}},           // a bad approach: its ILS off
                {Integer.valueOf(1), both, good, new boolean[][]{{true, false}, {false, false}}, new int[]{full, 1, hi}},         // its funnel on disk: off
                {Integer.valueOf(1), both, new boolean[][]{{false, false}, {true, true}}, none, new int[]{full, -1, lo | hi}},    // both bad: the other side
                {Integer.valueOf(1), new boolean[]{false, true}, good, none, new int[]{full, -1, lo | hi}},                      // the ground: the other side
                {Integer.valueOf(-1), new boolean[]{false, true}, new boolean[][]{{true, true}, {true, false}}, none, new int[]{full, 1, lo}},
                {Integer.valueOf(1), new boolean[]{false, false}, good, none, new int[]{shortK, 1, 0}},                         // no FULL: SHORT
                {Integer.valueOf(-1), both, none, none, new int[]{shortK, -1, 0}}};
        for (int i = 0; i < cases.length; i++) {
            int[] got = (int[]) decide.invoke(null, cases[i][0], cases[i][1], cases[i][2], cases[i][3]);
            if (!Arrays.equals(got, (int[]) cases[i][4])) {
                note(bad, "decide case " + i + ": " + Arrays.toString(got) + " not " + Arrays.toString((int[]) cases[i][4]));
            }
        }
        boolean simple = Boolean.TRUE.equals(fits.invoke(null, true, false, true)) && Boolean.FALSE.equals(fits.invoke(null, true, false, false))
                && Boolean.FALSE.equals(fits.invoke(null, false, true, true)) && Integer.valueOf(lo | hi).equals(ils.invoke(null, true, true, false, false))
                && Integer.valueOf(hi).equals(ils.invoke(null, false, true, false, false)) && Integer.valueOf(lo).equals(ils.invoke(null, true, true, false, true))
                && Integer.valueOf(0).equals(ils.invoke(null, true, true, true, true));
        // the tallies' rules on synthetic samples: plains fit; one water sample - not the level ground (an approach may cross water: the
        // review's); hills over EDGE_PCT - not the level ground, over 20% - not an approach; forest over 30% - only an approach
        int plain = ((Integer) flags.invoke(null, false, false, false, false, 0.1F, 0.05F)).intValue();
        int water = ((Integer) flags.invoke(null, true, false, false, false, -1F, 0.1F)).intValue();
        int hilly = ((Integer) flags.invoke(null, false, false, true, false, 0.45F, 0.3F)).intValue();
        int forest = ((Integer) flags.invoke(null, false, false, false, true, 0.1F, 0.2F)).intValue();
        int[] t = (int[]) tally.invoke(null);
        for (int i = 0; i < 100; i++) {
            count.invoke(null, t, Integer.valueOf(plain));
        }
        boolean rules = level.invoke(null, t) == null && approach.invoke(null, t) == null;
        int[] wt = t.clone();
        count.invoke(null, wt, Integer.valueOf(water));
        rules &= level.invoke(null, wt) != null && approach.invoke(null, wt) == null;
        int[] ht = (int[]) tally.invoke(null), ft = (int[]) tally.invoke(null), h2 = (int[]) tally.invoke(null);
        for (int i = 0; i < 100; i++) {
            count.invoke(null, ht, Integer.valueOf(i < 11 ? hilly : plain));
            count.invoke(null, ft, Integer.valueOf(i < 31 ? forest : plain));
            count.invoke(null, h2, Integer.valueOf(i < 21 ? hilly : plain));
        }
        rules &= level.invoke(null, ht) != null && approach.invoke(null, ht) == null && approach.invoke(null, h2) != null
                && level.invoke(null, ft) == null && approach.invoke(null, ft) != null;
        // the tallies on a synthetic WorldChunkManager (no world, no chunk): all plains - FULL's ground and approaches fit; all ocean - not the
        // ground, an approach yes (water under it); all mountains - neither
        EmbassyAirfieldSC.Field f = field(1, full, 1, 92);
        int[] lp = (int[]) levelTally.invoke(null, new Synthetic(BiomeGenBase.plains), f), lw = (int[]) levelTally.invoke(null, new Synthetic(BiomeGenBase.ocean), f);
        int[] ap = (int[]) approachTally.invoke(null, new Synthetic(BiomeGenBase.plains), f, Integer.valueOf(0));
        int[] aw = (int[]) approachTally.invoke(null, new Synthetic(BiomeGenBase.ocean), f, Integer.valueOf(1));
        int[] am = (int[]) approachTally.invoke(null, new Synthetic(BiomeGenBase.extremeHills), f, Integer.valueOf(1));
        boolean synthetic = lp[0] > 0 && level.invoke(null, lp) == null && level.invoke(null, lw) != null && ap[0] > 0 && approach.invoke(null, ap) == null
                && approach.invoke(null, aw) == null && approach.invoke(null, am) != null;
        check(bad.length() == 0 && simple && rules && synthetic, "package G (§3.2): decide on " + cases.length + " synthetic answers (FULL on the seed's"
                + " side, the other one, SHORT; an end's ILS bit off for its bad approach or its funnel on disk)" + bad + "; fullFits / fullIls " + simple
                + "; the tallies' rules (water - the ground only, hills > " + EmbassySiteSC.EDGE_PCT + "% on the ground / > 20% in an approach, forest > 30%"
                + " in an approach) " + rules + "; on a synthetic WorldChunkManager - plains fit, ocean only under an approach, mountains nowhere (" + lp[0]
                + " / " + ap[0] + " samples) " + synthetic);
        try {
            int[] m = fullShare();
            System.out.println("[SC-TEST] INFO airfield: the FULL share (TODO(balance), the plan's target >= 60 %) on the " + SEEDS.length + " seeds'"
                    + " biome layers, " + m[0] + " sites - these rules (the ground's hills <= " + EmbassySiteSC.EDGE_PCT + " %, an approach's <= 20 %,"
                    + " water under it) " + pct(m[1], m[0]) + " (both ILS ends " + pct(m[2], m[0]) + "); the plan's first rules (10 %, no water) "
                    + pct(m[3], m[0]) + "; the ground's hills <= 20 % too " + pct(m[4], m[0]) + "; no FULL on the seed's side - why: " + WHY);
        } catch (Throwable ex) {
            System.out.println("[SC-TEST] INFO airfield: the FULL share not measured - " + ex);
        }
    }

    private static String pct(int n, int of) {
        return n + " (" + (of == 0 ? 0 : n * 100 / of) + " %)";
    }

    /**
     * The review's measure of the FULL share: on each of SEEDS' biome layers (a WorldChunkManager of its own - no world, no chunk), the
     * first candidate of a region whose land fits (EmbassySiteSC.biomeProblem - the villages, the temples and the spawn's distance left
     * out), SITES_A_SEED sites a seed: EmbassyAirSiteSC.choose there (no funnel on disk), and the same tallies by the plan's first rules
     * and by a looser ground. @return {sites, FULL, FULL with both ILS ends, FULL by the first rules, FULL with the ground's hills 20 %}
     */
    static int[] fullShare() {
        int[] out = new int[5];
        WHY.clear();
        int grid = EmbassySiteSC.grid();
        for (long ws : SEEDS) {
            WorldChunkManager m = new WorldChunkManager(ws, net.minecraft.world.WorldType.DEFAULT);
            int found = 0;
            for (int k = 0; k < SHARE_REGIONS && found < SITES_A_SEED; k++) {
                int rx = 1 + k % 8, rz = k / 8 - 4;
                for (int[] c : EmbassySiteSC.candidates(ws, rx, rz, grid)) {
                    long seed = EmbassySiteSC.siteSeed(ws, c[0], c[1]);
                    int r = EmbassySiteSC.radius(seed);
                    float[] root = new float[1];
                    if (EmbassySiteSC.biomeProblem(m, c[0], c[1], r, root) != null) {
                        continue;
                    }
                    EmbassySiteSC.Site s = new EmbassySiteSC.Site(c[0], c[1], r, EmbassyLayoutSC.floorY(root[0]), seed, false);
                    int[] d = com.sc.embassy.EmbassyAirSiteSC.choose(m, null, s);
                    found++;
                    out[0]++;
                    out[1] += d[0] == EmbassyAirfieldSC.FULL ? 1 : 0;
                    out[2] += d[0] == EmbassyAirfieldSC.FULL && d[2] == (EmbassyAirfieldSC.ILS_LOW | EmbassyAirfieldSC.ILS_HIGH) ? 1 : 0;
                    out[3] += fullBy(m, s, EmbassySiteSC.EDGE_PCT, EmbassySiteSC.EDGE_PCT, false) ? 1 : 0;
                    out[4] += fullBy(m, s, 20, 20, true) ? 1 : 0;
                    if (d[0] != EmbassyAirfieldSC.FULL) {
                        EmbassyAirfieldSC.Field f = com.sc.embassy.EmbassyAirSiteSC.fullField(s, EmbassyAirfieldSC.sideOf(seed));
                        String why = com.sc.embassy.EmbassyAirSiteSC.levelProblem(com.sc.embassy.EmbassyAirSiteSC.levelTally(m, f));
                        why = why != null ? "ground " + why : "approach " + com.sc.embassy.EmbassyAirSiteSC.approachProblem(
                                com.sc.embassy.EmbassyAirSiteSC.approachTally(m, f, 0));
                        String key = why.replaceAll(" x?[0-9]+.*", "");
                        Integer n = WHY.get(key);
                        WHY.put(key, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
                    }
                    break;
                }
            }
        }
        return out;
    }

    /** FULL fits `s` on either side by these numbers: the ground's hills, an approach's hills, water under an approach. */
    private static boolean fullBy(WorldChunkManager m, EmbassySiteSC.Site s, int levelHills, int approachHills, boolean water) {
        for (int side = -1; side <= 1; side += 2) {
            EmbassyAirfieldSC.Field f = com.sc.embassy.EmbassyAirSiteSC.fullField(s, side);
            if (!fits(com.sc.embassy.EmbassyAirSiteSC.levelTally(m, f), false, levelHills, -1)) {
                continue;
            }
            int forest = com.sc.embassy.EmbassyAirSiteSC.APPROACH_FOREST_PCT;
            if (fits(com.sc.embassy.EmbassyAirSiteSC.approachTally(m, f, 0), water, approachHills, forest)
                    || fits(com.sc.embassy.EmbassyAirSiteSC.approachTally(m, f, 1), water, approachHills, forest)) {
                return true;
            }
        }
        return false;
    }

    /** A tally against EmbassyAirSiteSC's rules with these numbers (its problem()'s order). */
    private static boolean fits(int[] t, boolean water, int hills, int forest) {
        int n = t[com.sc.embassy.EmbassyAirSiteSC.T_SAMPLES];
        return n > 0 && (water || t[com.sc.embassy.EmbassyAirSiteSC.T_WATER] == 0) && t[com.sc.embassy.EmbassyAirSiteSC.T_MOUNTAIN] == 0
                && t[com.sc.embassy.EmbassyAirSiteSC.T_HIGH] == 0 && t[com.sc.embassy.EmbassyAirSiteSC.T_SHATTERED] == 0
                && t[com.sc.embassy.EmbassyAirSiteSC.T_HILLY] * 100 <= hills * n
                && (forest < 0 || t[com.sc.embassy.EmbassyAirSiteSC.T_FOREST] * 100 <= forest * n);
    }

    /** fullShare's reasons of the sites without FULL (the seed's side: its ground's first problem, else its -a approach's). */
    private static final Map<String, Integer> WHY = new java.util.TreeMap<String, Integer>();

    /** fullShare: sites a seed, regions looked at most (8 x 8 east of the spawn). */
    private static final int SITES_A_SEED = 12, SHARE_REGIONS = 64;

    /** A WorldChunkManager of one biome everywhere (package G's biome reads without a world). */
    static final class Synthetic extends WorldChunkManager {
        private final BiomeGenBase biome;

        Synthetic(BiomeGenBase biome) {
            this.biome = biome;
        }

        @Override
        public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] a, int x, int z, int w, int h) {
            BiomeGenBase[] out = a == null || a.length < w * h ? new BiomeGenBase[w * h] : a;
            Arrays.fill(out, biome);
            return out;
        }

        @Override
        public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] a, int x, int z, int w, int h) {
            return getBiomesForGeneration(a, x, z, w, h);
        }

        @Override
        public BiomeGenBase getBiomeGenAt(int x, int z) {
            return biome;
        }
    }

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name, true, SelfTestAirfieldSC.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** A public static method of `c` by its name and exact parameters, or null. */
    private static Method method(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c == null ? null : c.getMethod(name, params);
            return m != null && Modifier.isStatic(m.getModifiers()) ? m : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
