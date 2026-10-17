package com.sc.mob.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sc.mob.EntityHollowShellSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4): a Hollow Shell patrol walks its Dead Node's zone in line («идёт строем»)
 * while none of it has a target. Once every REGROUP ticks each member finds the others of its patrol id within REACH and
 * sorts them by UUID - the first leads, the rest keep abreast of it, SPACING blocks to its right and left by rank. The
 * leader walks to random points of the zone (the crossing +- ZONE - 4) with a pause at each; a follower re-paths to its
 * slot every FOLLOW ticks (faster when it fell behind). The walking only (mutex 1); a Shell without a patrol (an egg's)
 * wanders as vanilla's.
 */
public class EntityAIShellPatrolSC extends EntityAIBase {

    public static final int REGROUP = 40, FOLLOW = 10, PAUSE_MIN = 60, PAUSE_MAX = 160;
    private static final double REACH = 24D;

    private static final Comparator<EntityHollowShellSC> BY_UUID = new Comparator<EntityHollowShellSC>() {
        @Override
        public int compare(EntityHollowShellSC a, EntityHollowShellSC b) {
            return a.getUniqueID().compareTo(b.getUniqueID());
        }
    };

    private final EntityHollowShellSC mob;
    private final double speed;
    private EntityHollowShellSC leader;
    private int rank, regroupIn, followIn, pause;
    private boolean walking;
    private double wx, wz;

    public EntityAIShellPatrolSC(EntityHollowShellSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return mob.patrol() != 0L && mob.hasNode() && mob.getAttackTarget() == null && !mob.rebooting();
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        regroupIn = 0;
        followIn = 0;
        walking = false;
        pause = mob.getRNG().nextInt(PAUSE_MIN);
    }

    @Override
    public void resetTask() {
        leader = null;
        walking = false;
    }

    @Override
    public void updateTask() {
        if (--regroupIn <= 0) {
            regroupIn = REGROUP;
            regroup();
        }
        if (leader == null || leader == mob || !leader.isEntityAlive()) {
            lead();
        } else {
            follow();
        }
    }

    private void regroup() {
        List<?> near = mob.worldObj.getEntitiesWithinAABB(EntityHollowShellSC.class, mob.boundingBox.expand(REACH, 8D, REACH));
        List<EntityHollowShellSC> mine = new ArrayList<EntityHollowShellSC>();
        for (Object o : near) {
            EntityHollowShellSC s = (EntityHollowShellSC) o;
            if (s.isEntityAlive() && s.patrol() == mob.patrol()) {
                mine.add(s);
            }
        }
        if (!mine.contains(mob)) {
            mine.add(mob);
        }
        Collections.sort(mine, BY_UUID);
        leader = mine.get(0);
        rank = mine.indexOf(mob);
    }

    /** The leader: random points of the zone, a pause at each. */
    private void lead() {
        if (walking) {
            double dx = wx - mob.posX, dz = wz - mob.posZ;
            if (mob.getNavigator().noPath() || dx * dx + dz * dz < 2.25D) {
                walking = false;
                pause = PAUSE_MIN + mob.getRNG().nextInt(PAUSE_MAX - PAUSE_MIN + 1);
            }
            return;
        }
        if (--pause > 0) {
            return;
        }
        int half = Math.max(2, EntityHollowShellSC.ZONE - 4);
        wx = mob.homeX() + 0.5D + mob.getRNG().nextInt(2 * half + 1) - half;
        wz = mob.homeZ() + 0.5D + mob.getRNG().nextInt(2 * half + 1) - half;
        walking = mob.getNavigator().tryMoveToXYZ(wx, groundY(mob.worldObj, wx, wz), wz, speed);
        if (!walking) {
            pause = 20;                                             // a wall or a drop there: another point soon
        }
    }

    /** A follower: abreast of the leader, by rank to its right (odd) or left (even). */
    private void follow() {
        if (--followIn > 0) {
            return;
        }
        followIn = FOLLOW;
        double yaw = Math.toRadians(leader.renderYawOffset);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        int side = rank % 2 == 1 ? 1 : -1;
        double off = EntityHollowShellSC.SPACING * ((rank + 1) / 2);
        double sx = leader.posX + rx * side * off, sz = leader.posZ + rz * side * off;
        double dx = sx - mob.posX, dz = sz - mob.posZ, d2 = dx * dx + dz * dz;
        if (d2 < 2.25D) {
            mob.getNavigator().clearPathEntity();
            return;
        }
        mob.getNavigator().tryMoveToXYZ(sx, leader.posY, sz, d2 > 36D ? speed * 1.3D : speed);
    }

    /** The column's top (a loaded column; else the home's height). */
    private double groundY(World w, double x, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        if (!w.blockExists(bx, 64, bz)) {
            return mob.homeY();
        }
        int top = w.getHeightValue(bx, bz);
        return Math.abs(top - mob.posY) <= 6D ? top : mob.homeY();
    }
}
