package com.sc.mob.ai;

import com.sc.mob.EntitySentryDroneSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.7, rules ПС-4, ПС-9): the feral sentry drone's Ion Cutter. On a target within
 * EntitySentryDroneSC.RANGE that it can see (checked once every SIGHT_CHECK ticks), its searchlight goes on
 * (EntitySentryDroneSC.setLight: the DataWatcher's target, the telegraph ACT_SEARCHLIGHT) for SEARCHLIGHT_TICKS - the cue to
 * duck behind cover - then the beam: BEAM_DAMAGE as the drone's own blow, only if it still sees the target at that very
 * moment; then the light is off for the rest of RELOAD_TICKS. Losing sight puts the light out and starts the aim again. The
 * beam's sparks are vanilla's reddust in the cutter's ion blue, as the mobs' guns (EntityAIBeamAttackSC). Look only (mutex 2):
 * the flight is EntityAISentryDroneFlySC's.
 */
public class EntityAISentryDroneBeamSC extends EntityAIBase {

    /** How often it looks for a clear line (ticks, the AI rule). */
    public static final int SIGHT_CHECK = 10;
    /** The beam's colour (the cutter's ion blue), as reddust's «velocity». */
    private static final double R = 0.35D, G = 0.85D, B = 1.0D;

    private final EntitySentryDroneSC drone;
    private EntityLivingBase target;
    private boolean seen;
    private int sightIn, aim, reload;

    public EntityAISentryDroneBeamSC(EntitySentryDroneSC drone) {
        this.drone = drone;
        setMutexBits(2);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = drone.getAttackTarget();
        double r = EntitySentryDroneSC.RANGE * 1.5D;
        if (t == null || !t.isEntityAlive() || drone.getDistanceSqToEntity(t) > r * r) {
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
    }

    @Override
    public void resetTask() {
        target = null;
        aim = 0;
        seen = false;
        drone.setLight(null);
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        drone.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        face();
        if (--sightIn <= 0) {
            seen = drone.getEntitySenses().canSee(target);
            sightIn = SIGHT_CHECK;
        }
        if (reload > 0) {
            reload--;
            return;
        }
        double r = EntitySentryDroneSC.RANGE;
        if (!seen || drone.getDistanceSqToEntity(target) > r * r) {
            aim = 0;
            drone.setLight(null);
            return;
        }
        if (aim++ == 0) {
            drone.setLight(target);
            SoundsSC.play(drone.worldObj, drone.posX, drone.posY, drone.posZ, "mob.beam.aim", 0.8F, 1.3F);
        }
        if (aim < EntitySentryDroneSC.SEARCHLIGHT_TICKS) {
            return;
        }
        aim = 0;
        reload = EntitySentryDroneSC.RELOAD_TICKS - EntitySentryDroneSC.SEARCHLIGHT_TICKS;
        drone.setLight(null);
        if (!drone.canEntityBeSeen(target)) {                   // stepped behind cover in the last moment
            return;
        }
        line();
        SoundsSC.play(drone.worldObj, drone.posX, drone.posY, drone.posZ, "mob.beam.shot", 1F, 1.2F);
        target.attackEntityFrom(DamageSource.causeMobDamage(drone), EntitySentryDroneSC.BEAM_DAMAGE);
    }

    /** The body turns to the target (a flyer has no legs to lag behind its head). */
    private void face() {
        double dx = target.posX - drone.posX, dz = target.posZ - drone.posZ;
        drone.rotationYaw = drone.renderYawOffset = drone.rotationYawHead = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
    }

    /** The beam: sparks from the drone's lens to the target's chest, to the players within 16 blocks of each. */
    private void line() {
        if (!(drone.worldObj instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) drone.worldObj;
        double x0 = drone.posX, y0 = drone.posY + drone.height * 0.4D, z0 = drone.posZ;
        double x1 = target.posX, y1 = target.posY + target.height * 0.6D, z1 = target.posZ;
        int n = 12;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) (n + 1);
            ws.func_147487_a("reddust", x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, z0 + (z1 - z0) * k, 0, R, G, B, 1.0D);
        }
        ws.func_147487_a("fireworksSpark", x1, y1, z1, 6, 0.1D, 0.1D, 0.1D, 0.05D);
    }
}
