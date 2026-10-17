package com.sc.lore;

import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.ruin.ComponentRuinSC;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §6.2, rules ПЛ-3, ПЛ-4; proposal §3 «Кольца из Выгоревших катушек (дроп — Лом), около
 * 30 блоков»): a Lithographers' bridge wreck - "SCSi14WrkPart", the whole wreck in one piece (its box WreckLayoutSC.HALF each way
 * round the middle):
 * <ul>
 * <li>the ring: burnt coils (ruinSC BURNT_COIL) two courses high on the circle of radius `radius` round the middle, a Ruin Metal clamp
 * in the upper course every CLAMP_EVERY blocks of it; tilted about its axis (axis 0: a line along X through the middle - the height
 * runs with Z; 1: along Z) - level with the ground on the axis, one half dipping into the terrace (where it is buried its line shows
 * through the turf), the other raised to `lean` over it at its far side; GAPS_MIN..GAPS_MAX gaps broken out of it (never by the raised
 * side), TOP_MISSING_PCT of the raised half's upper course gone; where the ground dips under the sunk half it rests on a heap;</li>
 * <li>round it: the turf burnt off inside the ring, the crust ploughed up outside the sunk side, the coils fallen out of each gap, burnt
 * panels lying about (a few stuck upright), the bridge frame's struts of Ruin Metal, the bridge's controller - a machine husk 2 x 2
 * and two high with a tank husk by it - inside the ring opposite the raised side;</li>
 * <li>one chest (WreckLootSC) under the raised side's top, facing the middle - one block further out when the terrace rises there and
 * leaves no room under the ring.</li>
 * </ul>
 * By the pure heights (SubstrateTerrainSC.terraceY: a plateau cell's ground is always Ф1's terraces, and no cave, via, pond, trace,
 * druse or flora goes into a built wreck's box - the generator asks MapGenWreckSC.covers); everything from the piece's seed, inside the
 * populate window only (ComponentRuinSC's rules); nothing that gives light; never Blocks.stone. NBT "Wreck": the middle, its terrace,
 * the radius, the axis, the lean.
 */
public class ComponentWreckSC extends ComponentRuinSC {

    /** The ring's gaps and their half length (blocks along it); the raised half's upper course gone (%); a clamp every this many. TODO(balance) */
    public static final int GAPS_MIN = 2, GAPS_MAX = 3, GAP_HALF_MIN = 1, GAP_HALF_MAX = 2, TOP_MISSING_PCT = 15, CLAMP_EVERY = 5;
    /** The buried part's line showing through the turf (%); the burnt turf inside, the ploughed crust outside the sunk side (%). */
    public static final int SHOW_PCT = 70, SCORCH_PCT = 15, PLOUGH_PCT = 40;
    /** Burnt panels (a share stuck upright, %), the frame's struts, the coils fallen out of a gap (each gap). TODO(balance) */
    public static final int PANELS_MIN = 6, PANELS_MAX = 10, UPRIGHT_PCT = 25, STRUTS_MIN = 3, STRUTS_MAX = 5, FALLEN_MIN = 2, FALLEN_MAX = 4;

    /** No gap this near the raised side (radians, each way) - the chest's roof stays whole. */
    private static final double GAP_CLEAR = Math.toRadians(50D);
    /** The box's floor and top (the terraces 64..74, the raised side up to TILT_MAX over them). */
    private static final int BOX_BOTTOM = SubstrateTerrainSC.PLATEAU_MIN - 3, BOX_TOP = SubstrateTerrainSC.PLATEAU_MAX + WreckLayoutSC.TILT_MAX + 6;
    /** The layout's salts (ComponentRuinSC.layout / roll). */
    private static final int L_GAPS = 1, L_PANELS = 2, L_STRUTS = 3, L_HUSK = 4, L_FALLEN = 5;
    private static final int R_TOP = 41, R_SHOW = 42, R_SCORCH = 43, R_PLOUGH = 44, R_PLOUGH_KIND = 45, R_HEAP = 46, R_FALLEN = 47, R_TANK = 48;
    private static final int[] DX8 = {1, 1, 0, -1, -1, -1, 0, 1}, DZ8 = {0, 1, 1, 1, 0, -1, -1, -1};

    /** The middle column and its terrace, the ring's radius, its tilt axis (0: X, 1: Z) and lean. */
    private int mx, mz, my, radius, axis, lean;

    public ComponentWreckSC() {
    }

    public ComponentWreckSC(WreckLayoutSC.Site s) {
        super(WreckLayoutSC.mix(s.seed, s.x, s.z), new StructureBoundingBox(s.x - WreckLayoutSC.HALF, BOX_BOTTOM, s.z - WreckLayoutSC.HALF,
                s.x + WreckLayoutSC.HALF, BOX_TOP, s.z + WreckLayoutSC.HALF));      // its place in the seed: two cells' sites may share s.seed
        this.mx = s.x;
        this.mz = s.z;
        this.my = s.y;
        this.radius = Math.max(2, s.radius);
        this.axis = s.axis;
        this.lean = s.lean;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Wreck", new int[]{mx, mz, my, radius, axis, lean});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Wreck");
        if (a.length == 6) {
            mx = a[0];
            mz = a[1];
            my = a[2];
            radius = Math.max(2, a[3]);
            axis = a[4];
            lean = a[5];
        }
    }

    // ------------------------------------------------------------------ the shape (pure: the fields, the seed)

    /** +1 / -1: the half that rises (the seed's). */
    private int side() {
        return (seed >>> 7 & 1L) == 0L ? 1 : -1;
    }

    /** Blocks from the tilt axis towards the raised side (negative: into the sunk half). */
    private int across(int x, int z) {
        return side() * (axis == 0 ? z - mz : x - mx);
    }

    /** The ring's lower course at column (x, z): level with the middle's terrace on the axis, `lean` up at the raised side. */
    private int ringBottom(int x, int z) {
        double t = Math.max(-1D, Math.min(1D, across(x, z) / (double) radius));
        return my + (int) Math.round(lean * t);
    }

    private double dist(int x, int z) {
        double dx = x - mx, dz = z - mz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The column lies on the ring's circle. */
    private boolean onRing(int x, int z) {
        double r = dist(x, z);
        return r >= radius - 0.5D && r < radius + 0.5D;
    }

    /** The raised side's angle (atan2(z, x) from the middle). */
    private double raisedAngle() {
        if (axis == 0) {
            return side() > 0 ? Math.PI / 2D : -Math.PI / 2D;
        }
        return side() > 0 ? 0D : Math.PI;
    }

    /** The gaps: {angle, half its length in radians} (the layout's - the same list in every window). */
    private double[][] gaps() {
        Random r = layout(L_GAPS);
        int n = GAPS_MIN + r.nextInt(GAPS_MAX - GAPS_MIN + 1);
        double[][] g = new double[n][];
        double up = raisedAngle();
        for (int k = 0; k < n; k++) {
            double a = up + GAP_CLEAR + r.nextDouble() * (2D * Math.PI - 2D * GAP_CLEAR);
            int half = GAP_HALF_MIN + r.nextInt(GAP_HALF_MAX - GAP_HALF_MIN + 1);
            g[k] = new double[]{a, (half + 0.5D) / radius};
        }
        return g;
    }

    private boolean inGap(int x, int z, double[][] gaps) {
        double a = Math.atan2(z - mz, x - mx);
        for (double[] g : gaps) {
            if (Math.abs(Math.IEEEremainder(a - g[0], 2D * Math.PI)) <= g[1]) {
                return true;
            }
        }
        return false;
    }

    /** A clamp of Ruin Metal in the upper course here (every CLAMP_EVERY blocks along the ring). */
    private boolean clamp(int x, int z) {
        double arc = (Math.atan2(z - mz, x - mx) + Math.PI) * radius;
        return (int) Math.floor(arc) % CLAMP_EVERY == 0;
    }

    /**
     * The chest {x, y, z}: under the raised side's top, or a block further out when the terrace leaves no room under the ring (pure;
     * the world test asks it - new ComponentWreckSC(site).chestAt(world seed)).
     */
    public int[] chestAt(long worldSeed) {
        for (int out = 0; out <= 1; out++) {
            int d = side() * (radius + out);
            int x = axis == 0 ? mx : mx + d, z = axis == 0 ? mz + d : mz;
            int g = SubstrateTerrainSC.terraceY(worldSeed, x, z);
            if (out == 1 || g + 1 < ringBottom(x, z)) {
                return new int[]{x, g + 1, z};
            }
        }
        return null;                                            // never: the loop's second pass always answers
    }

    /** The controller's husk: {x, z} of its 2 x 2's corner, {x, z} of its tank - inside the ring, on the sunk half. */
    private int[] huskAt() {
        Random r = layout(L_HUSK);
        double a = raisedAngle() + Math.PI + (r.nextDouble() - 0.5D) * Math.PI;
        double rad = 2D + r.nextDouble() * Math.max(0D, radius - 6D);
        int hx = mx + (int) Math.round(Math.cos(a) * rad), hz = mz + (int) Math.round(Math.sin(a) * rad);
        int t = r.nextInt(4);
        int tx = t == 0 ? hx + 2 : t == 1 ? hx - 1 : hx + (r.nextBoolean() ? 0 : 1);
        int tz = t == 2 ? hz + 2 : t == 3 ? hz - 1 : hz + (r.nextBoolean() ? 0 : 1);
        return new int[]{hx, hz, tx, tz};
    }

    /** A loose piece may lie at (x, z): inside the box's rim, off the ring, clear of the chest and the husk. */
    private boolean free(int x, int z, int[] chest, int[] husk) {
        if (Math.abs(x - mx) > WreckLayoutSC.HALF - 1 || Math.abs(z - mz) > WreckLayoutSC.HALF - 1) {
            return false;
        }
        double r = dist(x, z);
        if (r >= radius - 1D && r < radius + 1D) {
            return false;
        }
        if (Math.max(Math.abs(x - chest[0]), Math.abs(z - chest[2])) <= 1) {
            return false;
        }
        return x < husk[0] - 1 || x > husk[0] + 2 || z < husk[1] - 1 || z > husk[1] + 2;
    }

    // ------------------------------------------------------------------ building

    @Override
    protected void build(World w, StructureBoundingBox win) {
        long ws = w.getSeed();
        int[] chest = chestAt(ws), husk = huskAt();
        double[][] gaps = gaps();
        // the ring, the burnt turf inside it, the ploughed crust outside its sunk side - column by column
        int x0 = Math.max(win.minX, boundingBox.minX), x1 = Math.min(win.maxX, boundingBox.maxX);
        int z0 = Math.max(win.minZ, boundingBox.minZ), z1 = Math.min(win.maxZ, boundingBox.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = SubstrateTerrainSC.terraceY(ws, x, z), a = across(x, z);
                double r = dist(x, z);
                if (r >= radius - 0.5D && r < radius + 0.5D) {
                    if (!inGap(x, z, gaps)) {
                        ring(w, win, x, z, g, a);
                    }
                } else if (r < radius - 0.5D) {
                    if (roll(x, g, z, R_SCORCH) < SCORCH_PCT) {
                        rock(w, win, x, g, z, SubstrateBlocksSC.CRYSTALLITE);
                    }
                } else if (r < radius + 2.5D && a < -radius / 3 && roll(x, g, z, R_PLOUGH) < PLOUGH_PCT) {
                    rock(w, win, x, g + 1, z, roll(x, g + 1, z, R_PLOUGH_KIND) < 60 ? SubstrateBlocksSC.CRYSTALLITE
                            : SubstrateBlocksSC.OXIDE_VIOLET + SubstrateTerrainSC.oxideBand(g + 1));
                }
            }
        }
        fallen(w, win, ws, gaps, chest, husk);
        struts(w, win, ws, chest, husk);
        panels(w, win, ws, chest, husk);
        controller(w, win, ws, husk);
        if (set(w, win, chest[0], chest[1], chest[2], Blocks.chest, facingTo(chest[0], chest[2], mx, mz))) {
            TileEntity te = w.getTileEntity(chest[0], chest[1], chest[2]);
            if (te instanceof IInventory) {
                WreckLootSC.fillWreck((IInventory) te, new Random(WreckLayoutSC.mix(seed, 9, 0xC4E57L)));
            }
        }
    }

    /** One column of the ring (`g` the terrace, `a` across the axis). */
    private void ring(World w, StructureBoundingBox win, int x, int z, int g, int a) {
        int b = ringBottom(x, z), top = b + 1;
        if (top <= g) {                                         // buried: its line shows through the turf
            if (roll(x, g, z, R_SHOW) < SHOW_PCT) {
                ruin(w, win, x, g, z, BlockRuinSC.BURNT_COIL);
            }
            return;
        }
        if (b >= g) {                                           // (b = g - 1: the lower course is under the turf)
            ruin(w, win, x, b, z, BlockRuinSC.BURNT_COIL);
        }
        if (a <= 0 || roll(x, top, z, R_TOP) >= TOP_MISSING_PCT) {
            ruin(w, win, x, top, z, clamp(x, z) ? BlockRuinSC.RUIN_METAL : BlockRuinSC.BURNT_COIL);
        }
        if (a <= 0) {                                           // the sunk half rests on a heap where the ground dips under it
            for (int y = g + 1; y < b; y++) {
                rubble(this, w, win, x, y, z, roll(x, y, z, R_HEAP) * 3 / 4);
            }
        }
    }

    /** The coils fallen out of each gap: lying on the ground by it, inside or outside the ring, some half sunk. */
    private void fallen(World w, StructureBoundingBox win, long ws, double[][] gaps, int[] chest, int[] husk) {
        Random r = layout(L_FALLEN);
        for (double[] g : gaps) {
            int n = FALLEN_MIN + r.nextInt(FALLEN_MAX - FALLEN_MIN + 1);
            for (int k = 0; k < n; k++) {
                double a = g[0] + (r.nextDouble() * 2D - 1D) * g[1] * 1.5D;
                double rad = radius + (r.nextBoolean() ? 1D : -1D) * (1.5D + r.nextDouble());
                int x = mx + (int) Math.round(Math.cos(a) * rad), z = mz + (int) Math.round(Math.sin(a) * rad);
                if (!free(x, z, chest, husk)) {
                    continue;
                }
                int y = SubstrateTerrainSC.terraceY(ws, x, z);
                ruin(w, win, x, roll(x, y, z, R_FALLEN) < 30 ? y : y + 1, z, BlockRuinSC.BURNT_COIL);
            }
        }
    }

    /** The bridge frame's struts: lines of Ruin Metal 2..4 long lying on the ground, any of eight ways. */
    private void struts(World w, StructureBoundingBox win, long ws, int[] chest, int[] husk) {
        Random r = layout(L_STRUTS);
        int n = STRUTS_MIN + r.nextInt(STRUTS_MAX - STRUTS_MIN + 1);
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * 2D * Math.PI, rad = 1D + r.nextDouble() * (WreckLayoutSC.HALF - 2D);
            int dir = r.nextInt(8), len = 2 + r.nextInt(3);
            int sx = mx + (int) Math.round(Math.cos(a) * rad), sz = mz + (int) Math.round(Math.sin(a) * rad);
            for (int t = 0; t < len; t++) {
                int x = sx + DX8[dir] * t, z = sz + DZ8[dir] * t;
                if (free(x, z, chest, husk)) {
                    ruin(w, win, x, SubstrateTerrainSC.terraceY(ws, x, z) + 1, z, BlockRuinSC.RUIN_METAL);
                }
            }
        }
    }

    /** Burnt panels lying about, 1..2 x 1..2; a few stuck upright, two high. */
    private void panels(World w, StructureBoundingBox win, long ws, int[] chest, int[] husk) {
        Random r = layout(L_PANELS);
        int n = PANELS_MIN + r.nextInt(PANELS_MAX - PANELS_MIN + 1);
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * 2D * Math.PI, rad = r.nextDouble() * (WreckLayoutSC.HALF - 1D);
            int x0 = mx + (int) Math.round(Math.cos(a) * rad), z0 = mz + (int) Math.round(Math.sin(a) * rad);
            boolean upright = r.nextInt(100) < UPRIGHT_PCT, alongX = r.nextBoolean();
            int len = 1 + r.nextInt(2), wid = upright ? 1 : 1 + r.nextInt(2);
            for (int i = 0; i < len; i++) {
                for (int j = 0; j < wid; j++) {
                    int x = x0 + (alongX ? i : j), z = z0 + (alongX ? j : i);
                    if (!free(x, z, chest, husk)) {
                        continue;
                    }
                    int y = SubstrateTerrainSC.terraceY(ws, x, z) + 1;
                    ruin(w, win, x, y, z, BlockRuinSC.BURNT_PANEL);
                    if (upright) {
                        ruin(w, win, x, y + 1, z, BlockRuinSC.BURNT_PANEL);
                    }
                }
            }
        }
    }

    /** The bridge's controller: a machine husk 2 x 2, two high (a burnt panel fallen on one corner), a tank husk by it. */
    private void controller(World w, StructureBoundingBox win, long ws, int[] husk) {
        for (int i = 0; i <= 1; i++) {
            for (int j = 0; j <= 1; j++) {
                int x = husk[0] + i, z = husk[1] + j, g = SubstrateTerrainSC.terraceY(ws, x, z);
                ruin(w, win, x, g + 1, z, BlockRuinSC.MACHINE_HUSK);
                ruin(w, win, x, g + 2, z, BlockRuinSC.MACHINE_HUSK);
                if (i == 0 && j == 0) {
                    ruin(w, win, x, g + 3, z, BlockRuinSC.BURNT_PANEL);
                }
            }
        }
        int g = SubstrateTerrainSC.terraceY(ws, husk[2], husk[3]);
        ruin(w, win, husk[2], g + 1, husk[3], BlockRuinSC.TANK_HUSK);
        if (roll(husk[2], g, husk[3], R_TANK) < 50) {
            ruin(w, win, husk[2], g + 2, husk[3], BlockRuinSC.TANK_HUSK);
        }
    }
}
