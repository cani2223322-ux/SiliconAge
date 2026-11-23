package com.sc.embassy.api;

import java.util.List;

import com.sc.block.BlockAirfieldSC;
import com.sc.embassy.EmbassyAirfieldSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import net.minecraft.util.IChatComponent;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §5.1, docs/idea-embassy-airfield.md §9): one embassy's airfield for the
 * Vehicles add-on - immutable, in WORLD coordinates (blocks; boxes {minX, minZ, maxX, maxZ} inclusive, points {x, y, z}; y of the paving
 * and of the flush lights is floorY). Made by EmbassyAirApiSC from the record and its EmbassyAirfieldSC.Field (the same numbers the
 * generator built it by); every array getter returns a copy. Written whole by the coordinator; fixed for API_VERSION 1 (only appended).
 * <p>
 * The runway lies along X or Z (alongX): the "low" end is the one on the -a side of the embassy's frame (a - along the main street from the
 * core to the main gate) - a landing OVER the low end flies towards +a (headingLow, numberLow is painted at the low end). The add-on keys its
 * virtual records "dim:x:y:z" by the blocks given here (the tower's desk, the thresholds' middle blocks, the virtual ILS points).
 */
public final class AirfieldInfoSC {

    /** The kinds (EmbassyAirfieldSC.HELI, SHORT, FULL). */
    public static final int HELI = EmbassyAirfieldSC.HELI, SHORT = EmbassyAirfieldSC.SHORT, FULL = EmbassyAirfieldSC.FULL;
    /** The stands' kinds (spotKind): the liner's «МС-1», a small machine's «МС-2..4», the helipad «Н». */
    public static final int SPOT_LINER = EmbassyAirfieldSC.SPOT_LINER, SPOT_SMALL = EmbassyAirfieldSC.SPOT_SMALL, SPOT_HELI = EmbassyAirfieldSC.SPOT_HELI;

    /** The embassy's registry id, its dimension (0), the kind, the decision's version, the side (+1 / -1 across the main street). */
    public final int id, dim, kind, version, side;
    /** Its name «<element>-<number>» (NpcNamesSC codes) - name() is the chat's «Посольство „Тантал-1“». */
    public final int nameElement, nameNumber;
    /** The ICAO the embassy proposes (X + the element's two letters + the number's letter: «Тантал-1» - XTAA); the add-on makes it unique. */
    public final String icao;
    /** The core's column, the floor (the paving's y), the dome's radius, the main gate's way (NORTH 0 .. WEST 3). */
    public final int coreX, coreZ, floorY, radius, gateDir;
    /** The runway lies along the X axis (else along Z); false for HELI. */
    public final boolean alongX;
    /** The paved width (FULL 9, SHORT 7; 0 HELI) and the runway's length (FULL 201, SHORT 91; 0 HELI). */
    public final int width, length;
    /** True headings of a landing over the low end / the high end (90..360; 0 HELI) and their painted numbers («36», «18»; "" HELI). */
    public final int headingLow, headingHigh;
    public final String numberLow, numberHigh;
    /** FULL: the ends whose approach passed the land check may have an ILS (the add-on's CAT III only there); SHORT / HELI: false. */
    public final boolean ilsLow, ilsHigh;
    /** The embassy's air defence circle: this far across from the core, any height (EmbassyAirRulesSC.AD_RADIUS; ЛП-1: the warning's). TODO(balance) */
    public final int adRadius;
    /**
     * ЛП-1 (the audit 2026-11-04): the half side of the square across from the core the guns fire over (about 145) - with the approach
     * corridors, inside adRadius (inFireZone; EmbassyAirApiSC.adZoneAt asks it, the players' bases left out).
     */
    public final int adFireHalf;

    private final EmbassyAirfieldSC.Field f;

    AirfieldInfoSC(SettlementRegistrySC.Settlement s, EmbassyAirfieldSC.Field f) {
        this.f = f;
        id = s.id;
        dim = s.dim;
        kind = f.kind;
        version = f.version;
        side = f.side;
        nameElement = s.nameElement;
        nameNumber = s.nameNumber;
        icao = EmbassyAirfieldSC.icao(s.nameElement, s.nameNumber);
        coreX = f.coreX;
        coreZ = f.coreZ;
        floorY = f.floorY;
        radius = f.radius;
        gateDir = f.gateDir;
        boolean rwy = f.runway != null;
        alongX = rwy && (f.gateDir == 1 || f.gateDir == 3);
        width = rwy ? f.runway[3] - f.runway[1] + 1 : 0;
        length = rwy ? f.endHigh - f.endLow + 1 : 0;
        headingLow = rwy ? EmbassyAirfieldSC.heading(f.gateDir, true) : 0;
        headingHigh = rwy ? EmbassyAirfieldSC.heading(f.gateDir, false) : 0;
        numberLow = rwy ? EmbassyAirfieldSC.number(headingLow) : "";
        numberHigh = rwy ? EmbassyAirfieldSC.number(headingHigh) : "";
        ilsLow = kind == FULL && (f.ils & EmbassyAirfieldSC.ILS_LOW) != 0;
        ilsHigh = kind == FULL && (f.ils & EmbassyAirfieldSC.ILS_HIGH) != 0;
        adRadius = EmbassyAirfieldSC.AD_RADIUS;
        adFireHalf = f.fireHalf();
    }

    /** «Посольство „Тантал-1“» - translated by each client. */
    public IChatComponent name() {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.size = com.sc.settle.SettlementSizeSC.EMBASSY.ordinal();
        s.nameElement = nameElement;
        s.nameNumber = nameNumber;
        return SettlementSC.displayName(s);
    }

    // ------------------------------------------------------------------ the runway (null / empty for HELI)

    /** The paved runway's box (null HELI). */
    public int[] runwayBox() {
        return box(f.runway);
    }

    /** The strip's box - the runway and its shoulders (FULL; SHORT: the runway; null HELI). */
    public int[] stripBox() {
        return box(f.strip);
    }

    /** The low end's / the high end's middle block on the axis {x, floorY, z} (null HELI). */
    public int[] end(boolean low) {
        return f.runway == null ? null : f.block(low ? f.endLow : f.endHigh, f.axis, 0);
    }

    /** Where a landing over the low end (`low`) or the high end touches down at the earliest: the threshold's middle block {x, floorY, z} (null HELI). */
    public int[] threshold(boolean low) {
        return f.runway == null ? null : f.block(low ? f.thresholdLow : f.thresholdHigh, f.axis, 0);
    }

    /** The threshold lights' row middle block of that landing {x, floorY, z} - the add-on's runway-end key (null HELI). */
    public int[] thresholdKey(boolean low) {
        return f.runway == null ? null : f.block(f.thresholdRow(low), f.axis, 0);
    }

    /** The landing distance over the low end / the high end: from its threshold to the far end (FULL 181, SHORT 91; 0 HELI). */
    public int landingDistance(boolean low) {
        return f.runway == null ? 0 : low ? f.endHigh - f.thresholdLow + 1 : f.thresholdHigh - f.endLow + 1;
    }

    /** The virtual localizer of a landing over that end {x, floorY, z} (no block there; null HELI). */
    public int[] localizer(boolean low) {
        int[] q = f.localizer(low);
        return q == null ? null : f.block(q[0], q[1], 0);
    }

    /** The virtual glide slope of a landing over that end {x, floorY, z} (no block there; null HELI). */
    public int[] glideSlope(boolean low) {
        int[] q = f.glideSlope(low);
        return q == null ? null : f.block(q[0], q[1], 0);
    }

    /** FULL's turning pads (low end's, high end's), taxiway «А», the apron; null otherwise. */
    public int[] padBox(boolean low) {
        return box(low ? f.padLow : f.padHigh);
    }

    public int[] taxiBox() {
        return box(f.taxi);
    }

    public int[] apronBox() {
        return box(f.apron);
    }

    /**
     * The core's lights of BlockAirfieldSC meta `meta` (LIGHT_EDGE, LIGHT_THRESHOLD, LIGHT_END, LIGHT_TAXI) as blocks {x, floorY, z} - where
     * the add-on's own lights and devices go ("огни рулёжки на местах ламп ядра"); empty for none.
     */
    public int[][] lights(int meta) {
        List<int[]> l = f.lights(meta);
        int[][] out = new int[l.size()][];
        for (int i = 0; i < out.length; i++) {
            out[i] = f.block(l.get(i)[0], l.get(i)[1], 0);
        }
        return out;
    }

    /** The approach funnels FULL cleared of growth over the 1:20 plane (low end's first; empty otherwise). */
    public int[][] funnelBoxes() {
        return boxes(f.funnels);
    }

    /** The neutral sky's approach corridors (EmbassyAirApiSC.neutralAt holds them; low end's first; empty HELI). */
    public int[][] approachBoxes() {
        return boxes(f.approaches);
    }

    // ------------------------------------------------------------------ the buildings, the stands, the services

    /** The tower's desk {x, y, z} - the add-on's tower key (null: no tower - SHORT, HELI: the add-on's virtual tower takes the helipad's column). */
    public int[] towerDesk() {
        return f.towerDesk();
    }

    /** The tower's «Светомаяк» {x, y, z} (null: no tower). */
    public int[] beacon() {
        return f.beaconBlock();
    }

    /** FULL's boxes: the «Аэровокзал», the «Вышка КДП», the «Ангар», the monument; null otherwise. */
    public int[] terminalBox() {
        return box(f.terminal);
    }

    public int[] towerBox() {
        return box(f.tower);
    }

    public int[] hangarBox() {
        return box(f.hangar);
    }

    public int[] monumentBox() {
        return box(f.monument);
    }

    /** The stands, in order: FULL «МС-1» (the liner's), «МС-2..4», then the helipad «Н» (last, every kind). */
    public int spots() {
        return f.spots.length;
    }

    public int[] spotBox(int i) {
        return box(f.spots[i]);
    }

    /** SPOT_LINER, SPOT_SMALL or SPOT_HELI. */
    public int spotKind(int i) {
        return f.spotKinds[i];
    }

    /** The stand's lang key sc.airfield.spot.<key> («МС-1» .. «МС-4», «Н»). */
    public String spotKey(int i) {
        return "sc.airfield.spot." + EmbassyAirfieldSC.SPOT_KEYS[f.spotKinds[i] == SPOT_HELI ? EmbassyAirfieldSC.SPOT_KEYS.length - 1 : i];
    }

    /** The helipad's box (every kind). */
    public int[] helipadBox() {
        return box(f.helipad);
    }

    /** The «Колонки обслуживания» {x, y, z}: one a stand, in spots' order (the helipad's last). */
    public int[][] columns() {
        int[][] out = new int[f.columns.length][];
        for (int i = 0; i < out.length; i++) {
            out[i] = f.columnBlock(i);
        }
        return out;
    }

    /** The windsock mast's foot {x, y, z} - the add-on draws the cone by the wind (null HELI). */
    public int[] windsock() {
        return f.windsockBlock();
    }

    /** The protected zone's box (blocks can't be broken or set there; it is NOT the whole zone - EmbassyAirApiSC.airfieldAt asks the shape). */
    public int[] zoneBox() {
        return f.zoneBox();
    }

    // ------------------------------------------------------------------ the embassy's air defence

    /** The «Зенитный пост Наладчиков» emplacements' guns {x, y, z} (BlockShowcase2SC.AA_POST) - the add-on fires from them. */
    public int[][] adPosts() {
        int[][] out = new int[f.aaPosts.length][];
        for (int i = 0; i < out.length; i++) {
            out[i] = f.aaBlock(i);
        }
        return out;
    }

    /**
     * (x, z) is in the air defence zone's circle (adRadius across from the core, any height) - the bare circle: EmbassyAirApiSC.adZoneAt
     * also leaves out the players' bases (no fire over one) - ask it before the fire.
     */
    public boolean inAdZone(double x, double z) {
        return f.inAdZone(x, z);
    }

    /**
     * ЛП-1: (x, z) is where the guns may fire (any height): inside the circle, over the embassy's square (adFireHalf) or an approach
     * corridor - the bare zone: EmbassyAirApiSC.adZoneAt also leaves out the players' bases. The rest of the circle only warns.
     */
    public boolean inFireZone(double x, double z) {
        return f.inFireZone(x, z);
    }

    // ------------------------------------------------------------------ pure questions of a column

    /** The column is in the field's protected zone. */
    public boolean inZone(int x, int z) {
        return f.inZone(x, z);
    }

    /** The column is under the neutral sky (the level disc, the zone, the approach corridors). */
    public boolean neutral(int x, int z) {
        return f.neutral(x, z);
    }

    /** The column is on the paved runway (the add-on's «на полосе»: its block there is isRunwaySurface too). */
    public boolean onRunway(int x, int z) {
        if (f.runway == null) {
            return false;
        }
        int[] q = f.frame(x, z);
        return EmbassyAirfieldSC.in(f.runway, q[0], q[1]);
    }

    /** The column is under the dome (the cylinder across: the auto-tower's «ПОСАДКА В КУПОЛЕ ЗАПРЕЩЕНА»). */
    public boolean inDome(double x, double z) {
        double dx = x - (coreX + 0.5D), dz = z - (coreZ + 0.5D);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /** BlockAirfieldSC's runway metas (EmbassyAirApiSC.isRunwaySurface). */
    static boolean runwayMeta(int meta) {
        return BlockAirfieldSC.runway(meta);
    }

    private int[] box(int[] q) {
        return q == null ? null : f.world(q);
    }

    private int[][] boxes(int[][] qs) {
        int[][] out = new int[qs.length][];
        for (int i = 0; i < qs.length; i++) {
            out[i] = f.world(qs[i]);
        }
        return out;
    }

    /** The field this was made of (the API's cache checks it is still the record's). */
    EmbassyAirfieldSC.Field field() {
        return f;
    }
}
