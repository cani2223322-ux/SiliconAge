package com.sc.mob.ai;

import com.sc.mob.MobCombatSC;
import com.sc.util.SoundsSC;
import com.sc.util.WeaponType;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.2, rule ПС-4): a mob with a mod gun in hand (Ф1: the Ion Cutter) shoots
 * an instant beam - no projectile entity. It aims AIM_TICKS first: a dotted line of sparks from its eyes to the target
 * that everyone near sees, the cue to step behind cover; then the beam (MobCombatSC.beamDamage, the mob's own damage
 * source) - one every RELOAD_TICKS. Only along a clear line of sight (checked once every SIGHT_CHECK ticks and once
 * more at the shot); losing sight drops the aim. Out of range or out of sight it walks up, like a skeleton; a target right next
 * to it gets the fist (MobCombatSC.meleeDamage via the mob's attackEntityAsMob).
 * <p>
 * Movement and look are its own (mutex 3): it stands in for the melee task while the mob holds a gun.
 */
public class EntityAIBeamAttackSC extends EntityAIBase {

    /**
     * The aim; from one shot to the next (ПС-4: a beam every 2 s, its last second the visible aim); how often it looks for
     * a clear line. Range in blocks. TODO(balance)
     */
    public static final int AIM_TICKS = 20, RELOAD_TICKS = 40, SIGHT_CHECK = 10;
    public static final double RANGE = 12D;
    /** Closer than this the fist (squared blocks, as vanilla's reach for a 0.6-wide mob). */
    private static final double FIST_SQ = 4D;
    /** The beam's colour (the cutter's ion blue), as reddust's «velocity». */
    private static final double R = 0.35D, G = 0.85D, B = 1.0D;

    private final EntityCreature mob;
    private final double speed;
    private EntityLivingBase target;
    private boolean seen;
    private int sightIn, aim, reload, fist, repath;

    public EntityAIBeamAttackSC(EntityCreature mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = mob.getAttackTarget();
        if (t == null || !t.isEntityAlive() || MobCombatSC.gun(mob) == null) {
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
        sightIn = 0;
        aim = 0;
        repath = 0;
    }

    @Override
    public void resetTask() {
        target = null;
        aim = 0;
        seen = false;
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        if (--sightIn <= 0) {
            seen = mob.getEntitySenses().canSee(target);
            sightIn = SIGHT_CHECK;
        }
        double d = mob.getDistanceSqToEntity(target);
        boolean inRange = d <= RANGE * RANGE;
        mob.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        if (seen && inRange) {
            mob.getNavigator().clearPathEntity();
        } else if (--repath <= 0) {
            mob.getNavigator().tryMoveToEntityLiving(target, speed);
            repath = 10;
        }
        if (fist > 0) {
            fist--;
        }
        if (d <= FIST_SQ && fist <= 0) {                     // right next to it: the fist (no aim needed)
            fist = MobCombatSC.MELEE_COOLDOWN;
            mob.swingItem();
            mob.attackEntityAsMob(target);
        }
        if (reload > 0) {
            reload--;
            return;
        }
        if (!seen || !inRange) {
            aim = 0;
            return;
        }
        aim++;
        if (aim == 1) {
            SoundsSC.play(mob.worldObj, mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ, "mob.beam.aim", 0.8F, 1F);
        }
        if (aim % 4 == 1) {
            line(6, false);
        }
        if (aim < AIM_TICKS) {
            return;
        }
        aim = 0;
        reload = reloadTicks();                              // the next aim is the rest of the 2 s
        if (!mob.canEntityBeSeen(target)) {                  // stepped behind cover in the last moment
            return;
        }
        line(12, true);
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ, "mob.beam.shot", 1F, 1F);
        WeaponType gun = MobCombatSC.gun(mob);
        target.attackEntityFrom(DamageSource.causeMobDamage(mob), MobCombatSC.beamDamage(gun));
    }

    /**
     * Ticks from a shot to the next aim: the rest of the 2 s (RELOAD_TICKS - AIM_TICKS). A subclass may make it longer
     * (Ф2: the dome guards' Plasma Rifle fires every 2..3 s - settle.npc.ai.EntityAIWardenBeamSC).
     */
    protected int reloadTicks() {
        return RELOAD_TICKS - AIM_TICKS;
    }

    /** Sparks from the mob's eyes to the target's chest, `n` points (server: sent to the players within 16 blocks of each). */
    private void line(int n, boolean shot) {
        if (!(mob.worldObj instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) mob.worldObj;
        double x0 = mob.posX, y0 = mob.posY + mob.getEyeHeight() - 0.1D, z0 = mob.posZ;
        double x1 = target.posX, y1 = target.posY + target.height * 0.6D, z1 = target.posZ;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) (n + 1);
            ws.func_147487_a("reddust", x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, z0 + (z1 - z0) * k, 0, R, G, B, 1.0D);
        }
        if (shot) {
            ws.func_147487_a("fireworksSpark", x1, y1, z1, 6, 0.1D, 0.1D, 0.1D, 0.05D);
        }
    }
}
