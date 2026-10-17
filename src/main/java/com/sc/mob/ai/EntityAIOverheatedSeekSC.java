package com.sc.mob.ai;

import com.sc.energy.TileEntityEnergyBase;
import com.sc.mob.EntityOverheatedSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.1, rule ПС-31; proposal §8 «Медленно идёт к источникам энергии»): the Overheated
 * drifts to the nearest machine of this mod (TileEntityEnergyBase - generators, machines, storage, a Breakdown Crack) within
 * SEEK_RADIUS, looked for once in SEEK_TICKS over the loaded chunks only (never loads one); it walks there slowly and stands
 * by it, warming its hands on the current. A target to fight comes first (the melee task has the lower priority number), so
 * this is its idle - in place of the wander. Movement only (mutex 1).
 */
public class EntityAIOverheatedSeekSC extends EntityAIBase {

    /** It is close enough (squared blocks); the walk gives up after this many ticks. */
    private static final double NEAR_SQ = 6.25D;
    private static final int GIVE_UP_TICKS = 400;

    private final EntityOverheatedSC mob;
    private final double speed;
    private int nextLook, walked;
    private int tx, ty, tz;

    public EntityAIOverheatedSeekSC(EntityOverheatedSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.getAttackTarget() != null || mob.ticksExisted < nextLook) {
            return false;
        }
        nextLook = mob.ticksExisted + EntityOverheatedSC.SEEK_TICKS;
        TileEntity te = nearest(mob.worldObj, mob.posX, mob.posY, mob.posZ, EntityOverheatedSC.SEEK_RADIUS);
        if (te == null || te.getDistanceFrom(mob.posX, mob.posY, mob.posZ) <= NEAR_SQ) {
            return false;
        }
        tx = te.xCoord;
        ty = te.yCoord;
        tz = te.zCoord;
        return true;
    }

    @Override
    public void startExecuting() {
        walked = 0;
        mob.getNavigator().tryMoveToXYZ(tx + 0.5D, ty, tz + 0.5D, speed);
    }

    @Override
    public boolean continueExecuting() {
        return mob.getAttackTarget() == null && !mob.getNavigator().noPath() && ++walked < GIVE_UP_TICKS
                && mob.getDistanceSq(tx + 0.5D, ty + 0.5D, tz + 0.5D) > NEAR_SQ;
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPathEntity();
    }

    /** The nearest of this mod's energy tiles within `r` of (x, y, z) in the loaded chunks, null none. */
    public static TileEntity nearest(World w, double x, double y, double z, double r) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), ri = (int) Math.ceil(r);
        TileEntity best = null;
        double bestD = r * r;
        for (int cx = (bx - ri) >> 4; cx <= (bx + ri) >> 4; cx++) {
            for (int cz = (bz - ri) >> 4; cz <= (bz + ri) >> 4; cz++) {
                if (!w.getChunkProvider().chunkExists(cx, cz)) {
                    continue;
                }
                Chunk c = w.getChunkFromChunkCoords(cx, cz);
                for (Object o : c.chunkTileEntityMap.values()) {
                    TileEntity te = (TileEntity) o;
                    if (!(te instanceof TileEntityEnergyBase) || te.isInvalid()) {
                        continue;
                    }
                    double d = te.getDistanceFrom(x, y, z);
                    if (d <= bestD) {
                        bestD = d;
                        best = te;
                    }
                }
            }
        }
        return best;
    }
}
