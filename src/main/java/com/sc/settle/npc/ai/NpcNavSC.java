package com.sc.settle.npc.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * PACKAGE N: walking a settlement creature somewhere far - a City is 120 blocks across, and vanilla's path search only
 * finds a goal within the mob's followRange of it (and builds its ChunkCache that far round the mob: past the 32
 * blocks an updating entity has loaded round it, it would load chunks). So a far goal is reached by legs of STEP
 * blocks: a random reachable block on the way (RandomPositionGenerator, as vanilla's MoveTowardsRestriction), else a
 * point on the straight line at the mob's own height (the search takes the nearest it can reach). The tasks call
 * walkTo again whenever the navigator has run out of path. Within DIRECT the goal is tried straight first, then by a leg too:
 * vanilla's search finds nothing at all once the goal is a step past the mob's followRange (a Scrapper's 16 - it stood still).
 */
public final class NpcNavSC {

    /** Closer than this (across) the path goes straight to the goal. */
    public static final double DIRECT = 18D;
    /** One leg of a long walk, across and up / down. */
    public static final int STEP = 12, STEP_Y = 6;

    private NpcNavSC() {
    }

    /** Walks `mob` toward (x, y, z) at `speed` (a multiple of its movementSpeed). @return a path was set */
    public static boolean walkTo(EntityCreature mob, double x, double y, double z, double speed) {
        double dx = x - mob.posX, dz = z - mob.posZ;
        double d2 = dx * dx + dz * dz;
        if (d2 <= DIRECT * DIRECT && mob.getNavigator().tryMoveToXYZ(x, y, z, speed)) {
            return true;
        }
        Vec3 v = RandomPositionGenerator.findRandomTargetBlockTowards(mob, STEP, STEP_Y, Vec3.createVectorHelper(x, y, z));
        if (v != null && mob.getNavigator().tryMoveToXYZ(v.xCoord, v.yCoord, v.zCoord, speed)) {
            return true;
        }
        double d = Math.sqrt(d2), step = Math.min(STEP, d);
        return d > 0.5D && mob.getNavigator().tryMoveToXYZ(mob.posX + dx / d * step, mob.posY, mob.posZ + dz / d * step, speed);
    }

    /** walkTo a block's centre. */
    public static boolean walkTo(EntityCreature mob, int[] p, double speed) {
        return p != null && walkTo(mob, p[0] + 0.5D, p[1], p[2] + 0.5D, speed);
    }

    /** Where one stands on column (x, z): over its top movement-blocking block; -1 if its chunk isn't loaded (never loads one). */
    public static int groundY(World w, int x, int z) {
        if (!w.blockExists(x, 64, z)) {
            return -1;
        }
        return w.getTopSolidOrLiquidBlock(x, z);
    }
}
