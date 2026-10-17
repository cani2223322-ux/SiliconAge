package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateTerrainSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3, rules ПУ-1, ПУ-2): a Dead Node's plaza - "SCSi14NodePlaza" - the
 * crossing's square (half size max(hx, hz) + CROSSING_EXTRA round the crossing, 19..25 across, at the node's floor):
 * <ul>
 * <li>the floor: a ring of Ruin Metal plates RING blocks round the middle (a landing pad, broken here and there), its four
 * corners still-glowing Trace Nodes (LAMP_PCT each), plates scattered over the rest (PLATE_PCT);</li>
 * <li>the node's dead heart in the middle: a machine husk two high under a burnt panel, tank husks on its four corners;</li>
 * <li>HUSKS_MIN..HUSKS_MAX husks round it - a machine, a long machine, a tank column, a pair of tanks - a block apart;</li>
 * <li>ARCHES collapsed arches of Ruin Metal across two of the four canyon mouths at the plaza's edge: one pillar standing,
 * the other broken, a stump of the lintel, the fallen blocks under the gap;</li>
 * <li>CHESTS_MIN..CHESTS_MAX loot chests beside the husks, facing the middle (RuinLootSC.fillNode).</li>
 * </ul>
 * Nothing stands where the floor below is gone (a cave opened it). The layout is the Node's seed's (the same list in every
 * window, ComponentRuinSC). TODO(balance): the counts.
 * <p>
 * Ф4 (package C, docs/plan-substrate-tech-f4.md §8.6, rule ПХ-2): over a Vault (MapGenVaultSC.builtAt) the middle stays free -
 * no dead heart: the Vault's sealed hatch (ComponentVaultShaftSC, built after the Node) takes the plaza's middle 3 x 3.
 */
public class ComponentNodePlazaSC extends ComponentRuinSC {

    public static final int HUSKS_MIN = 5, HUSKS_MAX = 8, CHESTS_MIN = RuinLootSC.NODE_CHESTS_MIN, CHESTS_MAX = RuinLootSC.NODE_CHESTS_MAX, ARCHES = 2;
    /** The pad's ring round the middle (Chebyshev distance); the arches' height; plates and lamps (%). */
    public static final int RING = 4, ARCH_H = 6, PLATE_PCT = 18, RING_BROKEN_PCT = 15, LAMP_PCT = 60;

    /** Husk kinds. */
    private static final int MACHINE = 0, LONG = 1, TANK = 2, PAIR = 3;
    /** The layout's grid cells. */
    private static final int FREE = 0, BLOCKED = 1, HUSK = 2, CHEST = 3;
    private static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};

    /** The crossing's column, the floor, the two canyons' half widths (hx: the canyon along Z), the plaza's half size. */
    private int cx0, cz0, floorY, hx, hz, half;

    public ComponentNodePlazaSC() {
    }

    public ComponentNodePlazaSC(long seed, int x, int z, int floorY, int hx, int hz) {
        super(seed, new StructureBoundingBox(x - half(hx, hz), floorY, z - half(hx, hz), x + half(hx, hz), floorY + ARCH_H + 2, z + half(hx, hz)));
        this.cx0 = x;
        this.cz0 = z;
        this.floorY = floorY;
        this.hx = hx;
        this.hz = hz;
        this.half = half(hx, hz);
    }

    /** The plaza's half size (as SubstrateTerrainSC.canyonFloorY's crossing). */
    public static int half(int hx, int hz) {
        return Math.max(hx, hz) + SubstrateTerrainSC.CROSSING_EXTRA;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Node", new int[]{cx0, cz0, floorY, hx, hz});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Node");
        if (a.length == 5) {
            cx0 = a[0];
            cz0 = a[1];
            floorY = a[2];
            hx = a[3];
            hz = a[4];
        }
        half = half(hx, hz);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        floor(w, win);
        if (!MapGenVaultSC.builtAt(w, SubstrateTerrainSC.lineIndex(cx0), SubstrateTerrainSC.lineIndex(cz0))) {
            heart(w, win);                                      // Ф4: over a Vault the middle is its hatch's
        }
        List<int[]> things = things();
        for (int[] t : things) {
            if (t[0] == HUSK) {
                husk(w, win, t);
            }
        }
        Random r = layout(3);
        for (int a = 0, from = r.nextInt(4); a < ARCHES; a++) {
            arch(w, win, (from + a * (1 + r.nextInt(2))) & 3, r.nextBoolean(), 2 + r.nextInt(2), 2 + r.nextInt(3));
        }
        int n = 0;
        for (int[] t : things) {
            if (t[0] == CHEST) {
                int x = cx0 + t[1], z = cz0 + t[2];
                if (solid(w, win, x, floorY, z)) {
                    chest(w, win, x, floorY + 1, z, facingTo(x, z, cx0, cz0), false, RuinLayoutSC.mix(seed, n, 0xC4E57L));
                }
                n++;
            }
        }
    }

    /** The plates, the pad's ring and its lamps - only over the plaza's own rock. */
    private void floor(World w, StructureBoundingBox win) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = cx0 + dx, z = cz0 + dz;
                if (!isRock(w, win, x, floorY, z)) {
                    continue;
                }
                int d = Math.max(Math.abs(dx), Math.abs(dz)), r = roll(x, floorY, z, 1);
                if (d == RING && Math.abs(dx) == RING && Math.abs(dz) == RING) {
                    if (r < LAMP_PCT) {
                        rock(w, win, x, floorY, z, SubstrateBlocksSC.TRACE_NODE);
                    } else {
                        ruin(w, win, x, floorY, z, BlockRuinSC.RUIN_METAL);
                    }
                } else if (d == RING ? r >= RING_BROKEN_PCT : d <= 1 || r < PLATE_PCT) {
                    ruin(w, win, x, floorY, z, BlockRuinSC.RUIN_METAL);
                }
            }
        }
    }

    /** The node's dead heart: a machine husk two high under a burnt panel, tank husks on its corners. */
    private void heart(World w, StructureBoundingBox win) {
        if (solid(w, win, cx0, floorY, cz0)) {
            ruin(w, win, cx0, floorY + 1, cz0, BlockRuinSC.MACHINE_HUSK);
            ruin(w, win, cx0, floorY + 2, cz0, BlockRuinSC.MACHINE_HUSK);
            ruin(w, win, cx0, floorY + 3, cz0, BlockRuinSC.BURNT_PANEL);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = cx0 + sx, z = cz0 + sz;
                if (solid(w, win, x, floorY, z)) {
                    ruin(w, win, x, floorY + 1, z, BlockRuinSC.TANK_HUSK);
                    if (roll(x, 0, z, 2) < 50) {
                        ruin(w, win, x, floorY + 2, z, BlockRuinSC.TANK_HUSK);
                    }
                }
            }
        }
    }

    /**
     * The husks and the chests of the plaza (pure, from the seed): {HUSK, dx, dz, dx2, dz2, kind, height} and
     * {CHEST, dx, dz}. A husk keeps a block from any other and off the pad (RING + 1); a chest stands by a husk, never next
     * to another chest (vanilla would join them).
     */
    private List<int[]> things() {
        List<int[]> out = new ArrayList<int[]>();
        Random r = layout(1);
        int n = 2 * half + 1, lim = half - 2;
        int[] grid = new int[n * n];
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= RING + 1) {
                    grid[(dx + half) * n + dz + half] = BLOCKED;
                }
            }
        }
        int husks = HUSKS_MIN + r.nextInt(HUSKS_MAX - HUSKS_MIN + 1);
        List<int[]> placed = new ArrayList<int[]>();
        for (int t = 0; t < 80 && placed.size() < husks; t++) {
            int dx = r.nextInt(2 * lim + 1) - lim, dz = r.nextInt(2 * lim + 1) - lim, kind = r.nextInt(4);
            boolean alongX = r.nextBoolean();
            int dx2 = dx, dz2 = dz;
            if (kind == LONG || kind == PAIR) {
                dx2 = alongX ? dx + (dx < 0 ? -1 : 1) : dx;
                dz2 = alongX ? dz : dz + (dz < 0 ? -1 : 1);
            }
            if (Math.abs(dx2) > lim || Math.abs(dz2) > lim || !clear(grid, n, dx, dz) || !clear(grid, n, dx2, dz2)) {
                continue;
            }
            grid[(dx + half) * n + dz + half] = HUSK;
            grid[(dx2 + half) * n + dz2 + half] = HUSK;
            int[] h = {HUSK, dx, dz, dx2, dz2, kind, kind == TANK ? 2 + r.nextInt(2) : kind == PAIR ? 2 : 1};
            placed.add(h);
            out.add(h);
        }
        int chests = CHESTS_MIN + r.nextInt(CHESTS_MAX - CHESTS_MIN + 1);
        for (int c = 0, t = 0; c < chests && t < 40 && !placed.isEmpty(); t++) {
            int[] h = placed.get(r.nextInt(placed.size()));
            int d = r.nextInt(4), dx = h[1] + DX[d], dz = h[2] + DZ[d];
            if (Math.abs(dx) > half - 1 || Math.abs(dz) > half - 1 || grid[(dx + half) * n + dz + half] != FREE || near(grid, n, dx, dz, CHEST)) {
                continue;
            }
            grid[(dx + half) * n + dz + half] = CHEST;
            out.add(new int[]{CHEST, dx, dz});
            c++;
        }
        return out;
    }

    /** The cell is free and no husk is next to it (8 round). */
    private boolean clear(int[] grid, int n, int dx, int dz) {
        return grid[(dx + half) * n + dz + half] == FREE && !near(grid, n, dx, dz, HUSK);
    }

    /** A cell of `what` among the 8 round (dx, dz). */
    private boolean near(int[] grid, int n, int dx, int dz, int what) {
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                int a = dx + i + half, b = dz + k + half;
                if ((i != 0 || k != 0) && a >= 0 && a < n && b >= 0 && b < n && grid[a * n + b] == what) {
                    return true;
                }
            }
        }
        return false;
    }

    private void husk(World w, StructureBoundingBox win, int[] t) {
        int kind = t[5], height = t[6];
        for (int k = 0; k < 2; k++) {
            int x = cx0 + t[1 + 2 * k], z = cz0 + t[2 + 2 * k];
            if (k == 1 && t[3] == t[1] && t[4] == t[2] || !solid(w, win, x, floorY, z)) {
                continue;                                       // a one-block husk / the floor gone under it
            }
            int r = roll(x, 0, z, 3);
            switch (kind) {
                case MACHINE:
                    ruin(w, win, x, floorY + 1, z, BlockRuinSC.MACHINE_HUSK);
                    if (r < 40) {
                        ruin(w, win, x, floorY + 2, z, BlockRuinSC.BURNT_PANEL);
                    }
                    break;
                case LONG:
                    ruin(w, win, x, floorY + 1, z, BlockRuinSC.MACHINE_HUSK);
                    if (k == 0 && r < 30) {
                        ruin(w, win, x, floorY + 2, z, BlockRuinSC.TANK_HUSK);
                    }
                    break;
                default:                                        // TANK, PAIR: tank husks `height` high
                    for (int y = 1; y <= height; y++) {
                        ruin(w, win, x, floorY + y, z, BlockRuinSC.TANK_HUSK);
                    }
                    break;
            }
        }
    }

    /**
     * A collapsed arch across the canyon mouth `dir` (0 north .. 3 west) at the plaza's edge: its pillars by the canyon's
     * walls, the one on the `leftStands` side whole, the other a stump `stump` high, the lintel reaching `reach` blocks from
     * the whole pillar, the fallen blocks under the gap.
     */
    private void arch(World w, StructureBoundingBox win, int dir, boolean leftStands, int stump, int reach) {
        int hw = DX[dir] == 0 ? hx : hz;                        // the mouth's canyon: along Z (north / south) is hx wide
        for (int s = -hw; s <= hw; s++) {
            int dx = DX[dir] == 0 ? s : DX[dir] * half, dz = DX[dir] == 0 ? DZ[dir] * half : s;
            int x = cx0 + dx, z = cz0 + dz;
            boolean pillar = Math.abs(s) == hw, whole = pillar && (s < 0) == leftStands;
            int from = leftStands ? -hw : hw, dist = Math.abs(s - from);
            if (pillar && solid(w, win, x, floorY, z)) {
                for (int y = 1; y < (whole ? ARCH_H : 1 + stump); y++) {
                    ruin(w, win, x, floorY + y, z, BlockRuinSC.RUIN_METAL);
                }
            }
            if (dist <= reach) {
                ruin(w, win, x, floorY + ARCH_H, z, BlockRuinSC.RUIN_METAL);
            } else if (!pillar && roll(x, 0, z, 4) < 45 && solid(w, win, x, floorY, z)) {
                int ix = x - DX[dir] * (roll(x, 1, z, 4) < 50 ? 1 : 0), iz = z - DZ[dir] * (roll(x, 2, z, 4) < 50 ? 1 : 0);
                if (solid(w, win, ix, floorY, iz) && w.isAirBlock(ix, floorY + 1, iz)) {
                    ruin(w, win, ix, floorY + 1, iz, roll(x, 3, z, 4) < 70 ? BlockRuinSC.RUIN_METAL : BlockRuinSC.BURNT_PANEL);
                }
            }
        }
    }
}
