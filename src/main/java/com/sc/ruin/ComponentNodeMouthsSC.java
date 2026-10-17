package com.sc.ruin;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateTerrainSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3, rules ПУ-1, ПУ-2): a Dead Node's canyon mouths and walls -
 * "SCSi14NodeMouths" - the whole zone (+-RuinLayoutSC.NODE_HALF round the crossing) but the plaza:
 * <ul>
 * <li>the four canyons' mouths: rubble on their floors up to the zone's edge - thick by the walls (RUBBLE_WALL_PCT, now and
 * then two high), sparse in the middle (RUBBLE_MID_PCT, one high: the way through stays open);</li>
 * <li>every wall facing a canyon or the plaza inside the zone: burnt-out panels set into the rock PANEL_FROM..PANEL_TO blocks
 * over the floor before it - two rows of four-block panels in Ruin Metal frames, broken off here and there; a Trace Node
 * still glows in the middle seam now and then.</li>
 * </ul>
 * Everything by the pure heights (SubstrateTerrainSC.canyonFloorY - a wall is a rock column beside a canyon one); a panel
 * only replaces the wall's own rock (a cave's mouth stays open), rubble only lies on solid floor.
 */
public class ComponentNodeMouthsSC extends ComponentRuinSC {

    /** The panels' rows over the floor before the wall; the panel's width and its frame; the seams' lamps (one in so many blocks). */
    public static final int PANEL_FROM = 4, PANEL_TO = 8, PANEL_W = 4, LAMP_EVERY = 15, LAMP_PCT = 60;
    /** Rubble (%): by a wall, two high by a wall, in the middle; panels broken off (%). TODO(balance) */
    public static final int RUBBLE_WALL_PCT = 22, RUBBLE_HEAP_PCT = 8, RUBBLE_MID_PCT = 7, PANEL_BROKEN_PCT = 25;

    private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};

    /** The crossing's column, its floor, the plaza's half size (the plaza piece's own). */
    private int cx0, cz0, floorY, half;

    public ComponentNodeMouthsSC() {
    }

    public ComponentNodeMouthsSC(long seed, int x, int z, int floorY, int plazaHalf) {
        super(seed, new StructureBoundingBox(x - RuinLayoutSC.NODE_HALF, Math.max(1, floorY - 2), z - RuinLayoutSC.NODE_HALF,
                x + RuinLayoutSC.NODE_HALF, floorY + PANEL_TO + 3, z + RuinLayoutSC.NODE_HALF));
        this.cx0 = x;
        this.cz0 = z;
        this.floorY = floorY;
        this.half = plazaHalf;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Node", new int[]{cx0, cz0, floorY, half});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Node");
        if (a.length == 4) {
            cx0 = a[0];
            cz0 = a[1];
            floorY = a[2];
            half = a[3];
        }
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        long ws = w.getSeed();
        int x0 = Math.max(boundingBox.minX, win.minX), x1 = Math.min(boundingBox.maxX, win.maxX);
        int z0 = Math.max(boundingBox.minZ, win.minZ), z1 = Math.min(boundingBox.maxZ, win.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int f = SubstrateTerrainSC.canyonFloorY(ws, x, z);
                if (f >= 0) {
                    if (Math.max(Math.abs(x - cx0), Math.abs(z - cz0)) > half) {
                        rubble(w, win, ws, x, z, f);
                    }
                    continue;
                }
                for (int d = 0; d < 4; d++) {                   // a wall: its face towards the first canyon column beside it
                    int nf = SubstrateTerrainSC.canyonFloorY(ws, x + DX[d], z + DZ[d]);
                    if (nf >= 0) {
                        panels(w, win, x, z, nf, DX[d] != 0 ? z : x);
                        break;
                    }
                }
            }
        }
    }

    /** Rubble on a mouth's floor column (its top block at `f`). */
    private void rubble(World w, StructureBoundingBox win, long ws, int x, int z, int f) {
        if (!solid(w, win, x, f, z) || !w.isAirBlock(x, f + 1, z)) {
            return;
        }
        boolean byWall = false;
        for (int d = 0; d < 4 && !byWall; d++) {
            byWall = SubstrateTerrainSC.canyonFloorY(ws, x + DX[d], z + DZ[d]) < 0 || SubstrateTerrainSC.canyonFloorY(ws, x + 2 * DX[d], z + 2 * DZ[d]) < 0;
        }
        int r = roll(x, 0, z, 11);
        if (r >= (byWall ? RUBBLE_WALL_PCT : RUBBLE_MID_PCT)) {
            return;
        }
        rubble(this, w, win, x, f + 1, z, roll(x, 1, z, 12));
        if (byWall && r < RUBBLE_HEAP_PCT) {
            rubble(this, w, win, x, f + 2, z, roll(x, 2, z, 12));
        }
    }

    /** The panels of a wall column whose face looks at a canyon floor `base`; `along` - the wall's own coordinate. */
    private void panels(World w, StructureBoundingBox win, int x, int z, int base, int along) {
        int col = ((along % (PANEL_W + 1)) + PANEL_W + 1) % (PANEL_W + 1);
        for (int k = PANEL_FROM; k <= PANEL_TO; k++) {
            int y = base + k, row = k - PANEL_FROM;
            if (!isRock(w, win, x, y, z) || SubstrateBlocksSC.isTrace(w.getBlock(x, y, z), w.getBlockMetadata(x, y, z))) {
                continue;                                       // a cave's mouth, a crystal, a live trace: left as they are
            }
            if (row == 2 || col == PANEL_W) {                   // the frame: the middle seam and the uprights
                boolean lamp = row == 2 && ((along % LAMP_EVERY) + LAMP_EVERY) % LAMP_EVERY == LAMP_EVERY / 2 && roll(x, y, z, 13) < LAMP_PCT;
                if (lamp) {
                    rock(w, win, x, y, z, SubstrateBlocksSC.TRACE_NODE);
                } else {
                    ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
                }
                continue;
            }
            int r = roll(x, y, z, 14);
            if (r >= PANEL_BROKEN_PCT) {
                ruin(w, win, x, y, z, r < PANEL_BROKEN_PCT + 10 ? BlockRuinSC.RUIN_METAL : BlockRuinSC.BURNT_PANEL);
            }
        }
    }
}
