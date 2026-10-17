package com.sc.settle.gen;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.SettlementSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.5): a settlement's ground - "SCSi14SetGround", always the first piece of
 * its start (the buildings of the same window come after it). Its box is the plan's footprint. Per column of the
 * window, by the pure terrain (SubstrateTerrainSC.terraceY - the footprint is all plateau):
 * <ul>
 * <li>on the level ground (the dome's disc and the pad out past the dock, SettlementLayoutSC.levelDistance): the floor's
 * Y; on the ramp round it (RAMP blocks): the terrain, kept within a block of height per block of the level ground's
 * edge - a terrace further off than that ends in a wall;</li>
 * <li>above it: air up to CLEAR over the old terrace (a druse, a trace, a pond go too); at it: Quartz Turf; below it:
 * filled down to the old terrace and at least SettlementSC.FOUNDATION deep (a cave under the floor is closed) - the
 * crust's bands near the top, Crystallite under them. Never Blocks.stone.</li>
 * </ul>
 */
public class ComponentSetGroundSC extends ComponentSettlementSC {

    /** Air this far over the old terrace (a druse is at most 9 high). */
    public static final int CLEAR = 10;
    /** Under the turf: this many blocks of the crust's bands, then Crystallite. */
    public static final int CRUST = 3;

    private int coreX, coreZ, radius, gateDir;
    private boolean dock;

    public ComponentSetGroundSC() {
    }

    public ComponentSetGroundSC(int id, SettlementLayoutSC.Plan p) {
        super(id, p.floorY, new SettlementLayoutSC.Lot(-1, p.footprint[0], p.footprint[1], p.footprint[2], p.footprint[3], p.gateDir, 0, 0),
                -255, 255);
        coreX = p.coreX;
        coreZ = p.coreZ;
        radius = p.radius;
        gateDir = p.gateDir;
        dock = p.dock;
        boundingBox.minY = 1;
        boundingBox.maxY = 255;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Core", new int[]{coreX, coreZ, radius, gateDir & 3});
        nbt.setBoolean("Dock", dock);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] c = ints(nbt, "Core", 4);
        coreX = c[0];
        coreZ = c[1];
        radius = Math.max(1, c[2]);
        gateDir = c[3] & 3;
        dock = nbt.getBoolean("Dock");
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        long seed = w.getSeed();
        int ax = SettlementLayoutSC.DIR_X[gateDir], az = SettlementLayoutSC.DIR_Z[gateDir], px = -az, pz = ax;
        int x0 = Math.max(boundingBox.minX, win.minX), x1 = Math.min(boundingBox.maxX, win.maxX);
        int z0 = Math.max(boundingBox.minZ, win.minZ), z1 = Math.min(boundingBox.maxZ, win.maxZ);
        Block rock = SubstrateBlocksSC.rock;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - coreX, dz = z - coreZ;
                double e = SettlementLayoutSC.levelDistance(dx * ax + dz * az, dx * px + dz * pz, radius, dock);
                if (e > SettlementLayoutSC.RAMP) {
                    continue;
                }
                int natural = SubstrateTerrainSC.terraceY(seed, x, z), target = floorY;
                if (e > 0) {                                     // the ramp: at most a block of height a block from the edge
                    int k = (int) Math.ceil(e);
                    target = Math.max(floorY - k, Math.min(floorY + k, natural));
                }
                for (int y = Math.min(255, Math.max(natural, target) + CLEAR); y > target; y--) {
                    if (!w.isAirBlock(x, y, z)) {
                        w.setBlock(x, y, z, Blocks.air, 0, 2);
                    }
                }
                int bottom = Math.max(1, Math.min(natural, target - SettlementSC.FOUNDATION));
                for (int y = target; y >= bottom; y--) {
                    Block b = w.getBlock(x, y, z);
                    if (y == target) {
                        put(w, win, x, y, z, rock, SubstrateBlocksSC.QUARTZ_TURF);
                    } else if (!solid(b) || b == rock && w.getBlockMetadata(x, y, z) == SubstrateBlocksSC.QUARTZ_TURF) {
                        put(w, win, x, y, z, rock, y >= target - CRUST && y >= SubstrateTerrainSC.OXIDE_FROM
                                ? SubstrateBlocksSC.OXIDE_VIOLET + SubstrateTerrainSC.oxideBand(y) : SubstrateBlocksSC.CRYSTALLITE);
                    }                                            // (the old turf under a fill turns to rock too)
                }
            }
        }
    }

    /** Ground to keep: anything but air, liquids and what a block replaces. */
    private static boolean solid(Block b) {
        Material m = b.getMaterial();
        return m != Material.air && !m.isLiquid() && !m.isReplaceable();
    }
}
