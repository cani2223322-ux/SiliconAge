package com.sc.mob.ai;

import com.sc.mob.EntityPhantomSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.5, rule ПС-35): the Induction Phantom's strike - with its target's chest within
 * EntityPhantomSC.REACH of its own middle (through rock: it is in the rock), EntityPhantomSC.strike once every STRIKE_EVERY
 * ticks (the helmet out, «Пробой»). The first strike comes STRIKE_EVERY / 2 after it got there - a moment to swing at it.
 * No mutex: the flight goes on (EntityAIPhantomFlySC keeps it hanging at the prey).
 */
public class EntityAIPhantomStrikeSC extends EntityAIBase {

    private final EntityPhantomSC phantom;
    private int cooldown;

    public EntityAIPhantomStrikeSC(EntityPhantomSC phantom) {
        this.phantom = phantom;
        setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = phantom.getAttackTarget();
        return t != null && t.isEntityAlive();
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        cooldown = Math.max(cooldown, EntityPhantomSC.STRIKE_EVERY / 2);
    }

    @Override
    public void updateTask() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        EntityLivingBase t = phantom.getAttackTarget();
        if (t == null) {
            return;
        }
        double dx = t.posX - phantom.posX, dy = t.posY + t.height * 0.5D - (phantom.posY + phantom.height * 0.5D), dz = t.posZ - phantom.posZ;
        double reach = EntityPhantomSC.REACH + t.width * 0.5D;
        if (dx * dx + dy * dy + dz * dz <= reach * reach) {
            phantom.strike(t);
            cooldown = EntityPhantomSC.STRIKE_EVERY;
        }
    }
}
