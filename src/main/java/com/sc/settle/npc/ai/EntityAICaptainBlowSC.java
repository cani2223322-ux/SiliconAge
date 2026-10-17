package com.sc.settle.npc.ai;

import com.sc.mob.IMobActionSC;
import com.sc.mob.MobCombatSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE S (docs/plan-substrate-tech-f3.md §6.4, rule ПГ-11): the Dome Captain's heavy blow. His target within
 * EntityWardenSC.HEAVY_REACH and in sight, his blade in hand, his chestplate not suppressed, the last blow
 * HEAVY_COOLDOWN ticks ago: he stops and winds up for HEAVY_WINDUP ticks - the telegraph IMobActionSC.ACT_WINDUP (DW 27:
 * every client draws the blade charging), a rising hum, sparks at the hand -, then the heavy blow (EntityWardenSC.heavyBlow:
 * HEAVY_BLOW of «Пробой», past armour and every shield) to that one target if it is still within reach and in sight, else
 * the blow cuts the air. A ПЭ-1 Point that hits him meanwhile (EntityWardenSC.empHit) breaks it off; the
 * cooldown runs either way. Over the ordinary blows (mutex 3, priority 2): while he winds up he neither walks nor strikes.
 */
public class EntityAICaptainBlowSC extends EntityAIBase {

    /** The blow lands this much past HEAVY_REACH (the target stepped back during the wind-up). */
    private static final double REACH_SLACK = 0.5D;

    private final EntityWardenSC g;
    private EntityLivingBase target;
    private int windup;
    private long nextAt;

    public EntityAICaptainBlowSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (g.kind() != NpcKindSC.CAPTAIN || g.worldObj.getTotalWorldTime() < nextAt || MobCombatSC.blade(g) == null
                || g.mobArmor().suppressed(1)) {
            return false;
        }
        EntityLivingBase t = g.getAttackTarget();
        double r = EntityWardenSC.HEAVY_REACH;
        return t != null && t.isEntityAlive() && g.getDistanceSqToEntity(t) <= r * r && g.getEntitySenses().canSee(t);
    }

    @Override
    public void startExecuting() {
        target = g.getAttackTarget();
        windup = EntityWardenSC.HEAVY_WINDUP;
        g.takeWindupBroken();                                   // only a Point from now on counts
        g.setMobAction(IMobActionSC.ACT_WINDUP);
        g.getNavigator().clearPathEntity();
        SoundsSC.play(g.worldObj, g.posX, g.posY + 1.2D, g.posZ, "power.on", 1.0F, 0.55F);
    }

    @Override
    public boolean continueExecuting() {
        return windup > 0 && target != null && target.isEntityAlive() && g.isEntityAlive();
    }

    @Override
    public void updateTask() {
        g.getNavigator().clearPathEntity();
        g.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        if (g.takeWindupBroken()) {                             // a Point broke it off: the blade's charge spills
            windup = 0;
            sparks("fireworksSpark", 10);
            SoundsSC.play(g.worldObj, g.posX, g.posY + 1.2D, g.posZ, "power.off", 1.0F, 1.2F);
            return;
        }
        if (windup % 5 == 0) {
            sparks("crit", 4);
        }
        if (--windup <= 0) {
            strike();
        }
    }

    @Override
    public void resetTask() {
        g.setMobAction(IMobActionSC.ACT_NONE);
        nextAt = g.worldObj.getTotalWorldTime() + EntityWardenSC.HEAVY_COOLDOWN;
        windup = 0;
        target = null;
    }

    /** The blow: HEAVY_BLOW of «Пробой» to the target still within reach and in sight. */
    private void strike() {
        g.swingItem();
        double r = EntityWardenSC.HEAVY_REACH + REACH_SLACK;
        if (g.getDistanceSqToEntity(target) <= r * r && g.getEntitySenses().canSee(target)) {
            g.heavyBlow(target);
        } else {
            SoundsSC.play(g.worldObj, g.posX, g.posY + 1.2D, g.posZ, "power.off", 0.8F, 0.9F);
        }
    }

    /** `n` particles of `name` round the blade's hand, every watcher sees them. */
    private void sparks(String name, int n) {
        if (!(g.worldObj instanceof WorldServer)) {
            return;
        }
        double yaw = Math.toRadians(g.renderYawOffset);
        double hx = g.posX - Math.cos(yaw) * 0.4D, hz = g.posZ - Math.sin(yaw) * 0.4D;   // the right hand
        ((WorldServer) g.worldObj).func_147487_a(name, hx, g.posY + 1.1D, hz, n, 0.12D, 0.3D, 0.12D, 0.05D);
    }
}
