package com.sc.mob.ai;

import java.util.List;

import com.sc.mob.BreakdownDamageSC;
import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.mob.MobCombatSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.5, rules ПБ-15, ПС-9, ПС-10): the Exo Executioner's «Рассечение». A target in
 * sight within CLEAVE_REACH, its chestplate not suppressed (a ПЭ-1 Point in the chest buys about 9 s), the cooldown over:
 * <ol>
 * <li>the wind-up - WINDUP_TICKS standing, IMobActionSC.ACT_WINDUP (red glints over the arc on every client), its way
 * locked towards where the target stood when it began: the cue to step back or aside. A ПЭ-1 Point that reaches it
 * (EntityExecutionerSC.empHit -> breakOff) or its chestplate suppressed meanwhile breaks it off - sparks and power.off;</li>
 * <li>the blow - every living thing in the cone (CLEAVE_REACH deep from its centre, CLEAVE_HALF_ANGLE to each side, about
 * its height, in its sight) but the Substrate's own (ISubstrateNativeSC: its hostiles, the settlements' Tuners and wardens)
 * takes CLEAVE_DAMAGE of «Пробой» (BreakdownDamageSC.of: past the
 * armour and every energy shield, the documented exception ПБ-16), times its damageMul(). A sweep of crits shows the arc.</li>
 * </ol>
 * The next one CLEAVE_COOLDOWN after the blow (or the break). Movement and look its own (mutex 3), above the melee; it can't
 * be interrupted once it began (the swim task needs only bit 4).
 */
public class EntityAICleaveSC extends EntityAIBase {

    private final EntityExecutionerSC mob;
    private EntityLivingBase target;
    private int ticks, readyAt;
    private boolean running;
    private float yaw;

    public EntityAICleaveSC(EntityExecutionerSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < readyAt || mob.mobArmor().suppressed(1)) {
            return false;
        }
        EntityLivingBase t = mob.getAttackTarget();
        if (t == null || !t.isEntityAlive() || t instanceof ISubstrateNativeSC || Math.abs(t.posY - mob.posY) > 2D
                || mob.getDistanceSqToEntity(t) > EntityExecutionerSC.CLEAVE_REACH * EntityExecutionerSC.CLEAVE_REACH
                || !mob.getEntitySenses().canSee(t)) {
            return false;
        }
        target = t;
        return true;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        running = true;
        yaw = MobCombatSC.yawTo(mob.posX, mob.posZ, target.posX, target.posZ);
        mob.getNavigator().clearPathEntity();
        mob.setMobAction(IMobActionSC.ACT_WINDUP);
        mob.playSound("mob.irongolem.throw", 1.4F, 0.5F);                  // the blade goes up
    }

    @Override
    public boolean continueExecuting() {
        return running && mob.isEntityAlive();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void resetTask() {
        if (mob.mobAction() == IMobActionSC.ACT_WINDUP) {
            mob.setMobAction(IMobActionSC.ACT_NONE);
        }
        running = false;
        target = null;
    }

    @Override
    public void updateTask() {
        if (!running) {
            return;
        }
        if (mob.mobArmor().suppressed(1)) {
            breakOff();                                                     // the chestplate went dark: no «Рассечение»
            return;
        }
        mob.getNavigator().clearPathEntity();
        mob.rotationYaw = yaw;
        mob.rotationYawHead = yaw;
        mob.renderYawOffset = yaw;
        if (++ticks < EntityExecutionerSC.WINDUP_TICKS) {
            return;
        }
        strike();
        running = false;
        readyAt = mob.ticksExisted + EntityExecutionerSC.CLEAVE_COOLDOWN;
    }

    /** Server: a ПЭ-1 Point reached it (EntityExecutionerSC.empHit) - a wind-up in progress breaks off. */
    public void breakOff() {
        if (!running || mob.worldObj.isRemote) {
            return;
        }
        running = false;
        readyAt = mob.ticksExisted + EntityExecutionerSC.CLEAVE_COOLDOWN;
        mob.setMobAction(IMobActionSC.ACT_NONE);
        if (mob.worldObj instanceof WorldServer) {
            ((WorldServer) mob.worldObj).func_147487_a("fireworksSpark", mob.posX, mob.posY + 1.5D, mob.posZ, 12, 0.3D, 0.3D, 0.3D, 0.06D);
        }
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1.2D, mob.posZ, "power.off", 0.9F, 0.8F);
    }

    /** Server: the blow. @return how many it hit */
    private int strike() {
        mob.setMobAction(IMobActionSC.ACT_NONE);
        mob.swingItem();
        double reach = EntityExecutionerSC.CLEAVE_REACH;
        List<?> near = mob.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, mob.boundingBox.expand(reach + 1D, 1.5D, reach + 1D));
        DamageSource src = BreakdownDamageSC.of(mob);
        float damage = EntityExecutionerSC.CLEAVE_DAMAGE * mob.damageMul();
        int hit = 0;
        for (Object o : near) {
            EntityLivingBase e = (EntityLivingBase) o;
            if (e == mob || !e.isEntityAlive() || e instanceof ISubstrateNativeSC) {        // §4.5: no native - a Tuner, a warden spared too
                continue;
            }
            double dx = e.posX - mob.posX, dz = e.posZ - mob.posZ;
            double r = reach + e.width * 0.5D;
            if (dx * dx + dz * dz > r * r || Math.abs(e.posY - mob.posY) > 2D
                    || !MobCombatSC.inCone(mob.posX, mob.posZ, yaw, e.posX, e.posZ, EntityExecutionerSC.CLEAVE_HALF_ANGLE)
                    || !mob.canEntityBeSeen(e)) {
                continue;
            }
            if (e.attackEntityFrom(src, damage)) {
                hit++;
            }
        }
        if (mob.worldObj instanceof WorldServer) {                         // the arc: a sweep of crits in front
            WorldServer ws = (WorldServer) mob.worldObj;
            for (int i = -3; i <= 3; i++) {
                double a = Math.toRadians(yaw + i * EntityExecutionerSC.CLEAVE_HALF_ANGLE / 3D);
                double x = mob.posX - Math.sin(a) * reach * 0.8D, z = mob.posZ + Math.cos(a) * reach * 0.8D;
                ws.func_147487_a("magicCrit", x, mob.posY + 1.0D, z, 3, 0.1D, 0.2D, 0.1D, 0.1D);
            }
        }
        mob.playSound("mob.irongolem.hit", 1.6F, 0.6F);
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1D, mob.posZ, "field.zap", 1F, 0.7F);
        return hit;
    }
}
