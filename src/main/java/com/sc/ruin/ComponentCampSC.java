package com.sc.ruin;

import com.sc.dim.SubstrateTerrainSC;
import com.sc.mob.RareRegistrySC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.2, rule ПЗ-6): a piece of a Renegades' camp - "SCSi14CmpPart" is this base piece
 * (it builds nothing; MapGenStructureIO wants a class per name). It keeps the camp's place (NBT "Camp": its canyon segment - axis,
 * line, seg -, the middle's column, the levelled floor's Y, the canyon's half width there, the residents' count and whether an
 * Executioner guards it) and the camp's fixed spots in local coordinates - `a` along the canyon, `c` across it (CampLayoutSC.Site:
 * AXIS_X runs along X) - all pure, so RuinWorldSC and the world test find the same blocks the pieces set:
 * <ul>
 * <li>the Stolen Field Generator in the middle (0, 0), its dome CampLayoutSC.FIELD_RADIUS;</li>
 * <li>the safe under the tarp by the +c wall (SAFE_A, hw) - inside the dome;</li>
 * <li>the PANELS stolen panels at (+-PANEL_A, +-PANEL_C) - outside the dome, within its feed range, under the open sky;</li>
 * <li>the stolen charge pad (PAD_A, -(hw - 1)) - outside the dome, the Executioner's end of the camp (only when it has one).</li>
 * </ul>
 * Everything stands one block over the levelled floor (floorY + 1). As every ruin piece: built only inside the populate window,
 * by the pure heights, from its own seed (ComponentRuinSC).
 */
public class ComponentCampSC extends ComponentRuinSC {

    /** The spots (local a, c - see the class comment). TODO(balance) */
    public static final int SAFE_A = 6, PANEL_A = 19, PANEL_C = 2, PAD_A = 13;
    /** The tarps: their two stretches along each wall (a from .. to), their height over the floor, how far they reach from the wall. */
    public static final int[][] TARP_A = {{-9, -3}, {3, 9}};
    public static final int TARP_H = 4, TARP_DEPTH = 3;
    /** The box's reach under and over the floor (the levelling fills and clears inside it). */
    public static final int BELOW = 3, ABOVE = TARP_H + 2;

    /** The camp's segment, middle, floor, the canyon's half width there, residents. */
    protected int axis, line, seg, mx, mz, floorY, hw, marauders;
    protected boolean executioner;

    public ComponentCampSC() {
    }

    protected ComponentCampSC(CampLayoutSC.Site s, int hw) {
        super(s.seed, box(s));
        axis = s.axis;
        line = s.line;
        seg = s.seg;
        mx = s.x;
        mz = s.z;
        floorY = s.floorY;
        this.hw = hw;
        marauders = s.marauders;
        executioner = s.executioner;
    }

    /** The piece's box: the camp's (CampLayoutSC.Site.box), BELOW under its floor to ABOVE over it. */
    public static StructureBoundingBox box(CampLayoutSC.Site s) {
        int[] b = s.box();
        return new StructureBoundingBox(b[0], s.floorY - BELOW, b[1], b[2], s.floorY + ABOVE, b[3]);
    }

    /** Pure: the canyon's half width at camp `s` (the canyon along X is the line z = line·256 - SubstrateTerrainSC's hz). */
    public static int halfWidth(long worldSeed, CampLayoutSC.Site s) {
        return s.axis == CampLayoutSC.AXIS_X ? SubstrateTerrainSC.canyonHalfWidth(~worldSeed, s.line)
                : SubstrateTerrainSC.canyonHalfWidth(worldSeed, s.line);
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Camp", new int[]{axis, line, seg, mx, mz, floorY, hw, marauders, executioner ? 1 : 0});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Camp");
        if (a.length == 9) {
            axis = a[0];
            line = a[1];
            seg = a[2];
            mx = a[3];
            mz = a[4];
            floorY = a[5];
            hw = a[6];
            marauders = a[7];
            executioner = a[8] != 0;
        }
    }

    /** The camp's registry key (RareRegistrySC.campKey: its Warlord's record). */
    public String key() {
        return RareRegistrySC.campKey(axis, line, seg);
    }

    // ------------------------------------------------------------------ local -> world

    /** World X of the local spot (a along, c across). */
    protected int wx(int a, int c) {
        return axis == CampLayoutSC.AXIS_X ? mx + a : mx + c;
    }

    /** World Z of the local spot. */
    protected int wz(int a, int c) {
        return axis == CampLayoutSC.AXIS_X ? mz + c : mz + a;
    }

    /** Local `a` (along) of a world column. */
    protected int la(int x, int z) {
        return axis == CampLayoutSC.AXIS_X ? x - mx : z - mz;
    }

    /** Local `c` (across) of a world column. */
    protected int lc(int x, int z) {
        return axis == CampLayoutSC.AXIS_X ? z - mz : x - mx;
    }

    /** The world spot {x, y, z} of local (a, c), standing on the floor. */
    protected int[] at(int a, int c) {
        return new int[]{wx(a, c), floorY + 1, wz(a, c)};
    }

    // ------------------------------------------------------------------ the camp's spots

    /** The Stolen Field Generator. */
    public int[] field() {
        return at(0, 0);
    }

    /** The safe. */
    public int[] safe() {
        return at(SAFE_A, hw);
    }

    /** The PANELS stolen panels, {x, y, z} each. */
    public int[][] panels() {
        return new int[][]{at(PANEL_A, PANEL_C), at(PANEL_A, -PANEL_C), at(-PANEL_A, PANEL_C), at(-PANEL_A, -PANEL_C)};
    }

    /** The stolen charge pad, or null (no Executioner here). */
    public int[] pad() {
        return executioner ? at(PAD_A, -(hw - 1)) : null;
    }

    /** The middle on the floor {x, floorY + 1, z} - the residents' home. */
    public int[] middle() {
        return new int[]{mx, floorY + 1, mz};
    }

    public int floorY() {
        return floorY;
    }

    public int halfWidth() {
        return hw;
    }
}
