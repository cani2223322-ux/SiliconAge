package com.sc.mob.ai;

import com.sc.mob.BreakdownDamageSC;
import com.sc.mob.EntityEclipseSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobCombatSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.4, rules ПБ-15, ПС-9, ПС-34): «Затмение»'s heavy blow - a target in sight within
 * STRIKE_REACH, its chestplate not suppressed, the cooldown over:
 * <ol>
 * <li>the wind-up - HEAVY_WINDUP (1 s) standing, IMobActionSC.ACT_WINDUP (every client draws it rocking back, crimson motes
 * along the blade), its way locked towards where the target stood: the cue to step back. A ПЭ-1 Point that reaches it
 * (EntityEclipseSC.empHit -> breakOff) or its chestplate suppressed meanwhile breaks it off;</li>
 * <li>the blow - the target, if still within STRIKE_REACH + its half width, in front (STRIKE_HALF_ANGLE to each side) and in
 * sight, takes HEAVY_BLOW of «Пробой» (BreakdownDamageSC.of - past the armour and every energy shield, ПБ-16) x damageMul().</li>
 * </ol>
 * The next one HEAVY_COOLDOWN after the blow (or the break). Movement and look its own (mutex 3), above the melee; not
 * interruptible once begun; never while it flees.
 */
public class EntityAIEclipseStrikeSC extends EntityAIBase {

    private final EntityEclipseSC mob;
    private EntityLivingBase target;
    private int ticks, readyAt;
    private boolean running;
    private float yaw;

    public EntityAIEclipseStrikeSC(EntityEclipseSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < readyAt || mob.fleeing() || mob.mobArmor().suppressed(1)) {
            return false;
        }
        EntityLivingBase t = mob.getAttackTarget();
        double r = EntityEclipseSC.STRIKE_REACH;
        if (t == null || !t.isEntityAlive() || Math.abs(t.posY - mob.posY) > 2D || mob.getDistanceSqToEntity(t) > r * r
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
        mob.playSound("mob.irongolem.throw", 1.4F, 0.4F);
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
        if (mob.mobArmor().suppressed(1) || mob.fleeing()) {
            breakOff();
            return;
        }
        mob.getNavigator().clearPathEntity();
        mob.rotationYaw = yaw;
        mob.rotationYawHead = yaw;
        mob.renderYawOffset = yaw;
        if (++ticks < EntityEclipseSC.HEAVY_WINDUP) {
            return;
        }
        strike();
        running = false;
        readyAt = mob.ticksExisted + EntityEclipseSC.HEAVY_COOLDOWN;
    }

    /** Server: a ПЭ-1 Point reached it (EntityEclipseSC.empHit) - a wind-up in progress breaks off. */
    public void breakOff() {
        if (!running || mob.worldObj.isRemote) {
            return;
        }
        running = false;
        readyAt = mob.ticksExisted + EntityEclipseSC.HEAVY_COOLDOWN;
        mob.setMobAction(IMobActionSC.ACT_NONE);
        if (mob.worldObj instanceof WorldServer) {
            ((WorldServer) mob.worldObj).func_147487_a("fireworksSpark", mob.posX, mob.posY + 1.5D, mob.posZ, 12, 0.3D, 0.3D, 0.3D, 0.06D);
        }
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1.2D, mob.posZ, "power.off", 0.9F, 0.7F);
    }

    private void strike() {
        mob.setMobAction(IMobActionSC.ACT_NONE);
        mob.swingItem();
        EntityLivingBase t = target;
        if (t != null && t.isEntityAlive()) {
            double r = EntityEclipseSC.STRIKE_REACH + t.width * 0.5D, dx = t.posX - mob.posX, dz = t.posZ - mob.posZ;
            if (dx * dx + dz * dz <= r * r && Math.abs(t.posY - mob.posY) <= 2D
                    && MobCombatSC.inCone(mob.posX, mob.posZ, yaw, t.posX, t.posZ, EntityEclipseSC.STRIKE_HALF_ANGLE) && mob.canEntityBeSeen(t)) {
                t.attackEntityFrom(BreakdownDamageSC.of(mob), EntityEclipseSC.HEAVY_BLOW * mob.damageMul());
            }
        }
        if (mob.worldObj instanceof WorldServer) {
            double a = Math.toRadians(yaw);
            ((WorldServer) mob.worldObj).func_147487_a("magicCrit", mob.posX - Math.sin(a) * 1.6D, mob.posY + 1.1D, mob.posZ + Math.cos(a) * 1.6D,
                    14, 0.3D, 0.3D, 0.3D, 0.15D);
        }
        mob.playSound("mob.irongolem.hit", 1.6F, 0.5F);
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1D, mob.posZ, "field.zap", 1F, 0.6F);
    }
}
