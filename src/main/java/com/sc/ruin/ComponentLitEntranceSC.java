package com.sc.ruin;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateTerrainSC;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3, rule ПУ-5): an abandoned lithograph's way in - "SCSi14LitEntrance":
 * <ul>
 * <li>the hut on the surface: RuinLayoutSC.HUT x HUT of Ruin Metal over the hall's north wall, its floor at the highest
 * terrace under it (a plinth of Crystallite where the ground is lower), walls three high with blocks fallen out, the arch of
 * its door in the south wall, half its roof gone;</li>
 * <li>the shaft: a ladder from the hut's floor down through the rock and the hall's ceiling to its floor, on the north side
 * (the hut's wall, a ring of Ruin Metal round the shaft in the rock, the hall's north wall); one Trace Node half way down still
 * glows.</li>
 * </ul>
 * Built after the hall (the start's order): in the hall's own box it only sets the ladder. By the pure heights
 * (SubstrateTerrainSC.terraceY); the hut's floor is worked out when the start is made (NBT "Hut").
 */
public class ComponentLitEntranceSC extends ComponentRuinSC.Lit {

    /** The hut's walls over its floor; its roof's share left (%), the walls' top rows' blocks fallen out (%). */
    public static final int WALL_H = 3, ROOF_LEFT_PCT = 55, TOP_FALLEN_PCT = 30, MID_FALLEN_PCT = 12;
    /** The ladder's meta: on the north side of its block, held by the block at z - 1 (BlockLadder). */
    private static final int LADDER_NORTH = 3;

    /** The shaft's column, the hall's floor (Y), the hut's floor (Y). */
    private int sx, sz, floorY, hutY;

    public ComponentLitEntranceSC() {
    }

    public ComponentLitEntranceSC(RuinLayoutSC.LithoSite s, long worldSeed) {
        super(s.seed, box(s, hutFloor(s, worldSeed)));
        this.sx = s.shaftX();
        this.sz = s.shaftZ();
        this.floorY = s.floorY;
        this.hutY = hutFloor(s, worldSeed);
    }

    private static StructureBoundingBox box(RuinLayoutSC.LithoSite s, int hutY) {
        int[] b = s.hutBox();
        return new StructureBoundingBox(b[0], s.floorY + 1, b[1], b[2], Math.min(255, hutY + WALL_H + 1), b[3]);
    }

    /** The hut's floor: the highest terrace under its HUT x HUT (pure) - the ground never rises into it. */
    public static int hutFloor(RuinLayoutSC.LithoSite s, long worldSeed) {
        int[] b = s.hutBox();
        int top = 0;
        for (int x = b[0]; x <= b[2]; x++) {
            for (int z = b[1]; z <= b[3]; z++) {
                top = Math.max(top, SubstrateTerrainSC.terraceY(worldSeed, x, z));
            }
        }
        return top;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Hut", new int[]{sx, sz, floorY, hutY});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Hut");
        if (a.length == 4) {
            sx = a[0];
            sz = a[1];
            floorY = a[2];
            hutY = a[3];
        }
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        long ws = w.getSeed();
        int ceiling = floorY + RuinLayoutSC.HALL_H - 1, lamp = (ceiling + hutY) / 2;
        // the hut: a plinth up to its floor, the floor, the walls with the door's arch, what's left of the roof
        int x0 = boundingBox.minX, x1 = boundingBox.maxX, z0 = boundingBox.minZ, z1 = boundingBox.maxZ;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!win.isVecInside(x, hutY, z)) {
                    continue;
                }
                for (int y = SubstrateTerrainSC.terraceY(ws, x, z) + 1; y < hutY; y++) {
                    rock(w, win, x, y, z, SubstrateBlocksSC.CRYSTALLITE);
                }
                if (x != sx || z != sz) {
                    ruin(w, win, x, hutY, z, BlockRuinSC.RUIN_METAL);
                }
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int k = 1; k <= WALL_H + 1; k++) {
                    int y = hutY + k;
                    if (k == WALL_H + 1) {
                        if (roll(x, y, z, 31) < ROOF_LEFT_PCT && (x != sx || z != sz)) {
                            ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
                        } else {
                            air(w, win, x, y, z);
                        }
                    } else if (edge && wallStays(x, y, z, k)) {
                        ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
                    } else if (x != sx || z != sz) {
                        air(w, win, x, y, z);
                    }
                }
            }
        }
        // the shaft: the ladder from the hall's floor to over the hut's floor, the ring round it in the rock, its lamp
        for (int y = floorY + 1; y <= hutY + WALL_H - 1; y++) {
            if (y > ceiling && y < hutY) {
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        if (a == 0 && b == 0) {
                            continue;
                        }
                        if (y == lamp && a == 0 && b == -1) {
                            rock(w, win, sx, y, sz - 1, SubstrateBlocksSC.TRACE_NODE);
                        } else {
                            ruin(w, win, sx + a, y, sz + b, BlockRuinSC.RUIN_METAL);
                        }
                    }
                }
            }
            set(w, win, sx, y, sz, Blocks.ladder, LADDER_NORTH);
        }
    }

    /**
     * The hut's wall block `k` over its floor stays: the door's opening in the south wall's middle (two high) under its
     * arch, the north wall over the shaft whole (the ladder holds on to it), the rest with blocks fallen out of the top rows.
     */
    private boolean wallStays(int x, int y, int z, int k) {
        boolean south = z == boundingBox.maxZ, north = z == boundingBox.minZ;
        if (south && x == sx) {
            return k == WALL_H;                                 // the door, the arch's keystone over it
        }
        if (north && Math.abs(x - sx) <= 1 || south && Math.abs(x - sx) == 1) {
            return true;                                        // the ladder's wall; the arch's sides
        }
        int r = roll(x, y, z, 32);
        return k == WALL_H ? r >= TOP_FALLEN_PCT : k == WALL_H - 1 ? r >= MID_FALLEN_PCT : true;
    }
}
