package com.sc.mob.ai;

import java.util.List;

import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityJammerSC;
import com.sc.mob.MobCombatSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.3): «держится за спинами» - while the Jammer has a target it walks to a spot
 * KEEP_MIN..KEEP_MAX blocks from it: behind the nearest armoured ally within ALLY_RADIUS that stands nearer the target (three
 * blocks past it, away from the target), else straight back off or up to the middle of that band; in the band and in sight
 * it stands. Looked at once every REPATH ticks (no path finding every tick). A target right next to a carrier (no gun to
 * aim - its ПЭ-1 isn't one) gets the fist here; a gunner's fist is EntityAIJammerEmitterSC's.
 * The walking only (mutex 1): its gun task looks and shoots, a carrier's ПЭ-1 task (package A) holds no bits at all.
 */
public class EntityAIJammerKeepBackSC extends EntityAIBase {

    public static final int REPATH = 10;
    /** Behind the ally by this much; the band's middle. */
    private static final double BEHIND = 3D, MIDDLE = (EntityJammerSC.KEEP_MIN + EntityJammerSC.KEEP_MAX) / 2D;
    private static final double FIST_SQ = 4D;

    private final EntityJammerSC mob;
    private final double speed;
    private EntityLivingBase target;
    private int repath, fist;

    public EntityAIJammerKeepBackSC(EntityJammerSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = mob.getAttackTarget();
        if (t == null || !t.isEntityAlive()) {
            return false;
        }
        target = t;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        repath = 0;
    }

    @Override
    public void resetTask() {
        target = null;
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        double d2 = mob.getDistanceSqToEntity(target);
        if (fist > 0) {
            fist--;
        }
        if (d2 <= FIST_SQ && fist <= 0 && MobCombatSC.gun(mob) == null) {    // a gun in hand: its gun task has the fist
            fist = MobCombatSC.MELEE_COOLDOWN;
            mob.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
            mob.swingItem();
            mob.attackEntityAsMob(target);
        }
        if (--repath > 0) {
            return;
        }
        repath = REPATH + mob.getRNG().nextInt(5);
        double d = Math.sqrt(d2);
        boolean seen = mob.getEntitySenses().canSee(target);
        double[] spot = null;
        EntityLivingBase ally = ally(d2);
        if (ally != null) {
            double ux = ally.posX - target.posX, uz = ally.posZ - target.posZ, len = Math.sqrt(ux * ux + uz * uz);
            if (len > 0.5D) {
                double x = ally.posX + ux / len * BEHIND, z = ally.posZ + uz / len * BEHIND;
                spot = band(x, z);
            }
        }
        if (spot == null && (d < EntityJammerSC.KEEP_MIN || d > EntityJammerSC.KEEP_MAX || !seen)) {
            double ux = mob.posX - target.posX, uz = mob.posZ - target.posZ, len = Math.sqrt(ux * ux + uz * uz);
            if (len < 0.5D) {
                double a = mob.getRNG().nextDouble() * Math.PI * 2D;
                ux = Math.cos(a);
                uz = Math.sin(a);
                len = 1D;
            }
            spot = new double[]{target.posX + ux / len * MIDDLE, target.posZ + uz / len * MIDDLE};
        }
        if (spot == null) {
            mob.getNavigator().clearPathEntity();            // in the band, in sight, no one to hide behind: it stands and shoots
            return;
        }
        double sx = spot[0] - mob.posX, sz = spot[1] - mob.posZ;
        if (sx * sx + sz * sz < 2.25D) {
            mob.getNavigator().clearPathEntity();
            return;
        }
        if (!mob.getNavigator().tryMoveToXYZ(spot[0], groundY(mob.worldObj, spot[0], spot[1]), spot[1], speed) && d < EntityJammerSC.KEEP_MIN) {
            Vec3 away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(mob, EntityJammerSC.KEEP_MAX, 4,
                    Vec3.createVectorHelper(target.posX, target.posY, target.posZ));
            if (away != null) {
                mob.getNavigator().tryMoveToXYZ(away.xCoord, away.yCoord, away.zCoord, speed * 1.1D);
            }
        }
    }

    /** (x, z) moved along the line from the target into the KEEP_MIN..KEEP_MAX band. */
    private double[] band(double x, double z) {
        double ux = x - target.posX, uz = z - target.posZ, len = Math.sqrt(ux * ux + uz * uz);
        if (len < 0.5D) {
            return null;
        }
        double r = Math.max(EntityJammerSC.KEEP_MIN + 1D, Math.min(EntityJammerSC.KEEP_MAX - 1D, len));
        return new double[]{target.posX + ux / len * r, target.posZ + uz / len * r};
    }

    /** The nearest armoured native (not a Jammer) within ALLY_RADIUS standing nearer the target than this one; null none. */
    private EntityLivingBase ally(double ownDistSq) {
        double r = EntityJammerSC.ALLY_RADIUS;
        List<?> near = mob.worldObj.getEntitiesWithinAABB(EntityArmoredMobSC.class, mob.boundingBox.expand(r, 6D, r));
        EntityLivingBase best = null;
        double bestD = Double.MAX_VALUE;
        for (Object o : near) {
            EntityArmoredMobSC m = (EntityArmoredMobSC) o;
            if (m == mob || m instanceof EntityJammerSC || !m.isEntityAlive() || m.getDistanceSqToEntity(target) >= ownDistSq) {
                continue;
            }
            double d = mob.getDistanceSqToEntity(m);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    /** The column's top to walk to (a loaded column; else the mob's own height). */
    private double groundY(World w, double x, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        if (!w.blockExists(bx, 64, bz)) {
            return mob.posY;
        }
        int top = w.getHeightValue(bx, bz);
        return Math.abs(top - mob.posY) <= 6D ? top : mob.posY;   // a ledge far above or below: stay on its own level
    }
}
