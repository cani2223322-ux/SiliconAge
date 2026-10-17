package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.mob.BornInSubstrateSC;
import com.sc.mob.EntityMimicSC;
import com.sc.mob.EntitySentryDroneSC;

import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3, §8.4, rules ПУ-5, ПУ-6): an abandoned lithograph's hall - "SCSi14LitHall" -
 * the underground shop RuinLayoutSC.HALL x HALL x HALL_H round its middle (the box is the hall's, walls, floor and ceiling
 * included):
 * <ul>
 * <li>the floor: Ruin Metal with Crystallite seams; the walls: a Crystallite course at the foot, Ruin Metal, a band of
 * burnt-out panels under the ceiling, one Trace Node still glowing in each wall (the only light); the ceiling: Crystallite
 * on Ruin Metal beams, its dead lamps burnt panels; every cave that ran through the box is closed by it;</li>
 * <li>ROWS x COLS machine husks (4 x 3), each two blocks long, a tank husk or a panel heaped on some; fallen panels on the
 * floor; CHESTS_MIN..CHESTS_MAX loot chests by the east and west walls (RuinLootSC.fillLitho);</li>
 * <li>ПУ-6, once (NBT "Residents"): in the populate window that holds the hall's middle - every chunk of it is loaded then -
 * MIMICS_MIN..MIMICS_MAX mimic machines in place of husks (a husk's inner block; a Crusher or an Electric Furnace 50/50) and
 * DRONES_MIN..DRONES_MAX feral sentry drones in the air; each marked «born in Si-14», persistent, at home in the hall.</li>
 * </ul>
 * The husk grid leaves at least 2 x 2 of its spots in that window, whatever the middle's place in it, so the mimics always
 * fit. The shaft's column (RuinLayoutSC.SHAFT_DZ) stays clear - its ladder is the entrance piece's. TODO(balance): counts.
 */
public class ComponentLitHallSC extends ComponentRuinSC.Lit {

    /** The husks' columns (X from the middle; each husk reaches one more block outwards) and rows (Z). */
    public static final int[] HUSK_DX = {-7, -2, 2, 7}, HUSK_DZ = {-6, 0, 6};
    public static final int CHESTS_MIN = RuinLootSC.LITHO_CHESTS_MIN, CHESTS_MAX = RuinLootSC.LITHO_CHESTS_MAX;
    /** ПУ-6: the residents (MapGenLithographSC repeats them for the handbook). TODO(balance) */
    public static final int MIMICS_MIN = 2, MIMICS_MAX = 4, DRONES_MIN = 2, DRONES_MAX = 3;
    /** The chests' places by the side walls (Z from the middle). */
    public static final int[] CHEST_DZ = {-9, -3, 3, 9};
    /** Fallen panels on the floor (%); burnt panels in the band under the ceiling (every other block). */
    public static final int FALLEN_PCT = 3;

    /** The middle's column, the floor (Y), the residents made already. */
    private int mx, mz, floorY;
    private boolean residents;

    public ComponentLitHallSC() {
    }

    public ComponentLitHallSC(RuinLayoutSC.LithoSite s) {
        super(s.seed, new StructureBoundingBox(s.x - RuinLayoutSC.HALL_HALF, s.floorY, s.z - RuinLayoutSC.HALL_HALF,
                s.x + RuinLayoutSC.HALL_HALF, s.ceilingY(), s.z + RuinLayoutSC.HALL_HALF));
        this.mx = s.x;
        this.mz = s.z;
        this.floorY = s.floorY;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Hall", new int[]{mx, mz, floorY});
        nbt.setBoolean("Residents", residents);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Hall");
        if (a.length == 3) {
            mx = a[0];
            mz = a[1];
            floorY = a[2];
        }
        residents = nbt.getBoolean("Residents");
    }

    /** The residents of this hall were made (the world test asks). */
    public boolean residentsMade() {
        return residents;
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int h = RuinLayoutSC.HALL_HALF, top = floorY + RuinLayoutSC.HALL_H - 1;
        int x0 = Math.max(boundingBox.minX, win.minX), x1 = Math.min(boundingBox.maxX, win.maxX);
        int z0 = Math.max(boundingBox.minZ, win.minZ), z1 = Math.min(boundingBox.maxZ, win.maxZ);
        List<int[]> mimics = mimicSpots();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - mx, dz = z - mz;
                boolean wall = Math.abs(dx) == h || Math.abs(dz) == h;
                for (int y = floorY; y <= top; y++) {
                    if (y == floorY) {
                        if (((dx + h) % 5 == 0 || (dz + h) % 6 == 0) && !wall) {
                            rock(w, win, x, y, z, SubstrateBlocksSC.CRYSTALLITE);
                        } else {
                            ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
                        }
                    } else if (y == top) {
                        if ((dz + h) % 6 == 0 || wall) {
                            ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
                        } else if ((dx + h) % 6 == 3 && (dz + h) % 6 == 3) {
                            ruin(w, win, x, y, z, BlockRuinSC.BURNT_PANEL);         // a dead lamp
                        } else {
                            rock(w, win, x, y, z, SubstrateBlocksSC.CRYSTALLITE);
                        }
                    } else if (wall) {
                        wallBlock(w, win, x, y, z, dx, dz, y - floorY);
                    } else {
                        air(w, win, x, y, z);
                    }
                }
            }
        }
        husks(w, win, mimics);
        chests(w, win);
        fallen(w, win, x0, x1, z0, z1);
        if (!residents && win.isVecInside(mx, floorY + 1, mz)) {
            residents = true;                                   // once, whatever happens below (the start is saved after this window)
            residents(w, win, mimics);
        }
    }

    /** A wall block `up` over the floor: the Crystallite course, Ruin Metal, the lamp, the band of panels under the ceiling. */
    private void wallBlock(World w, StructureBoundingBox win, int x, int y, int z, int dx, int dz, int up) {
        int along = Math.abs(dx) == RuinLayoutSC.HALL_HALF ? dz : dx;
        boolean north = dz == -RuinLayoutSC.HALL_HALF && Math.abs(dx) < RuinLayoutSC.HALL_HALF;
        if (up == 1) {
            rock(w, win, x, y, z, SubstrateBlocksSC.CRYSTALLITE);
        } else if (up == 3 && along == (north ? 3 : 0)) {
            rock(w, win, x, y, z, SubstrateBlocksSC.TRACE_NODE);                          // north: off the ladder's column
        } else if (up >= RuinLayoutSC.HALL_H - 3 && (along & 1) == 0) {
            ruin(w, win, x, y, z, BlockRuinSC.BURNT_PANEL);
        } else {
            ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
        }
    }

    /** The husks: the inner block (or the mimic's place) and the outer one, a tank husk or a panel heaped on some. */
    private void husks(World w, StructureBoundingBox win, List<int[]> mimics) {
        for (int dx : HUSK_DX) {
            for (int dz : HUSK_DZ) {
                int out = dx + (dx < 0 ? -1 : 1), r = roll(dx, 0, dz, 21);
                if (!isMimic(mimics, dx, dz)) {
                    ruin(w, win, mx + dx, floorY + 1, mz + dz, BlockRuinSC.MACHINE_HUSK);
                    if (r < 40) {
                        ruin(w, win, mx + dx, floorY + 2, mz + dz, BlockRuinSC.TANK_HUSK);
                    }
                }
                ruin(w, win, mx + out, floorY + 1, mz + dz, r % 3 == 0 ? BlockRuinSC.TANK_HUSK : BlockRuinSC.MACHINE_HUSK);
                if (r >= 40 && r < 60) {
                    ruin(w, win, mx + out, floorY + 2, mz + dz, BlockRuinSC.BURNT_PANEL);
                }
            }
        }
    }

    /** The chests by the side walls: their count, sides and rows from the seed; facing into the hall. */
    private void chests(World w, StructureBoundingBox win) {
        Random r = layout(5);
        int n = CHESTS_MIN + r.nextInt(CHESTS_MAX - CHESTS_MIN + 1);
        int[] order = shuffled(r, 2 * CHEST_DZ.length);
        for (int c = 0; c < n; c++) {
            int side = order[c] / CHEST_DZ.length == 0 ? -1 : 1, dz = CHEST_DZ[order[c] % CHEST_DZ.length];
            int x = mx + side * (RuinLayoutSC.HALL_HALF - 1), z = mz + dz;
            chest(w, win, x, floorY + 1, z, side < 0 ? 5 : 4, true, RuinLayoutSC.mix(seed, c, 0xC4E57L));
        }
    }

    /** A few panels fallen from the ceiling - never on a husk's row, a chest's place or the shaft's landing. */
    private void fallen(World w, StructureBoundingBox win, int x0, int x1, int z0, int z1) {
        int h = RuinLayoutSC.HALL_HALF - 2;
        for (int x = Math.max(x0, mx - h); x <= Math.min(x1, mx + h); x++) {
            for (int z = Math.max(z0, mz - h); z <= Math.min(z1, mz + h); z++) {
                int dx = x - mx, dz = z - mz;
                if (Math.abs(dz - RuinLayoutSC.SHAFT_DZ) <= 2 && Math.abs(dx) <= 1 || onHuskRow(dz) && Math.abs(dx) >= 1 && Math.abs(dx) <= 8) {
                    continue;
                }
                if (roll(x, 1, z, 22) < FALLEN_PCT && w.isAirBlock(x, floorY + 1, z)) {
                    ruin(w, win, x, floorY + 1, z, BlockRuinSC.BURNT_PANEL);
                }
            }
        }
    }

    private static boolean onHuskRow(int dz) {
        for (int r : HUSK_DZ) {
            if (Math.abs(dz - r) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * The husk spots ({dx, dz}, inner blocks) the mimics take: MIMICS_MIN..MIMICS_MAX of the ones inside the populate
     * window that holds the hall's middle (pure: that window is the middle's - populate's +8..+23 of chunk (x - 8) >> 4).
     */
    private List<int[]> mimicSpots() {
        int wx0 = ((mx - 8) >> 4 << 4) + 8, wz0 = ((mz - 8) >> 4 << 4) + 8;
        List<int[]> in = new ArrayList<int[]>();
        for (int dx : HUSK_DX) {
            for (int dz : HUSK_DZ) {
                int x = mx + dx, z = mz + dz;
                if (x >= wx0 && x < wx0 + 16 && z >= wz0 && z < wz0 + 16) {
                    in.add(new int[]{dx, dz});
                }
            }
        }
        Random r = layout(7);
        int n = MIMICS_MIN + r.nextInt(MIMICS_MAX - MIMICS_MIN + 1);
        List<int[]> out = new ArrayList<int[]>();
        while (out.size() < n && !in.isEmpty()) {
            out.add(in.remove(r.nextInt(in.size())));
        }
        return out;
    }

    private static boolean isMimic(List<int[]> mimics, int dx, int dz) {
        for (int[] m : mimics) {
            if (m[0] == dx && m[1] == dz) {
                return true;
            }
        }
        return false;
    }

    /** ПУ-6: the mimics in their husks' places, the drones in the air - only inside the window (it is loaded), once. */
    private void residents(World w, StructureBoundingBox win, List<int[]> mimics) {
        Random r = layout(9);
        int hx = mx, hy = floorY + RuinLayoutSC.HALL_H / 2, hz = mz, hr = RuinLayoutSC.HALL_HALF;
        for (int[] m : mimics) {
            int x = mx + m[0], z = mz + m[1];
            if (!win.isVecInside(x, floorY + 1, z)) {
                continue;
            }
            EntityMimicSC e = new EntityMimicSC(w);
            e.setLocationAndAngles(x + 0.5D, floorY + 1, z + 0.5D, m[1] > 0 ? 180F : 0F, 0F);   // facing the aisle
            e.setDisguise(r.nextInt(2));
            spawn(w, e, hx, hy, hz, hr);
        }
        int drones = DRONES_MIN + r.nextInt(DRONES_MAX - DRONES_MIN + 1);
        for (int d = 0, t = 0; d < drones && t < 40; t++) {
            int x = mx + r.nextInt(2 * hr - 3) - (hr - 2), z = mz + r.nextInt(2 * hr - 3) - (hr - 2), y = floorY + 4 + r.nextInt(2);
            if (!win.isVecInside(x, y, z) || !w.isAirBlock(x, y, z) || !w.isAirBlock(x, y + 1, z)) {
                continue;
            }
            EntitySentryDroneSC e = new EntitySentryDroneSC(w);
            e.setLocationAndAngles(x + 0.5D, y, z + 0.5D, r.nextFloat() * 360F, 0F);
            spawn(w, e, hx, hy, hz, hr);
            d++;
        }
    }

    /** One resident: born in Si-14, persistent, at home in the hall. */
    private static void spawn(World w, EntityLiving e, int hx, int hy, int hz, int hr) {
        BornInSubstrateSC.mark(e);
        e.func_110163_bv();                                     // persistent: never despawns
        if (e instanceof EntityMimicSC) {
            ((EntityMimicSC) e).setHome(hx, hy, hz, hr);
        } else if (e instanceof EntitySentryDroneSC) {
            ((EntitySentryDroneSC) e).setHome(hx, hy, hz, hr);
        }
        w.spawnEntityInWorld(e);
    }

    /** 0..n-1 in a random order. */
    private static int[] shuffled(Random r, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int k = r.nextInt(i + 1), t = a[i];
            a[i] = a[k];
            a[k] = t;
        }
        return a;
    }
}
