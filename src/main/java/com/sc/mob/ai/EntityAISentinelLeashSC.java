package com.sc.mob.ai;

import com.sc.mob.EntitySentinelSC;
import com.sc.settle.npc.ai.NpcNavSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.3, rule ПХ-3: «не отходит дальше 24 блоков»): the Sentinel never leaves its Vault -
 * further than its leash from its home (scHome) it lets its target go and walks back, the target out of its reach is ignored
 * meanwhile (vanilla's home area: EntityAITarget asks it). Without a home (an egg's) - nothing. Checked every CHECK_TICKS;
 * movement its own while it returns (mutex 1), above the melee. A path home every REPATH ticks at most (none to be had - a
 * sealed bunker, a pit - is no path search a tick); given up after GIVE_UP_TICKS, tried again RETRY_TICKS later.
 */
public class EntityAISentinelLeashSC extends EntityAIBase {

    private static final int CHECK_TICKS = 10, GIVE_UP_TICKS = 600, REPATH = 20, RETRY_TICKS = 100;

    private final EntitySentinelSC mob;
    private final double speed;
    private int nextCheck, walked, repath;

    public EntityAISentinelLeashSC(EntitySentinelSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < nextCheck || !mob.hasLeash()) {
            return false;
        }
        nextCheck = mob.ticksExisted + CHECK_TICKS;
        return mob.beyondLeash(0D);
    }

    @Override
    public void startExecuting() {
        walked = 0;
        repath = REPATH;
        EntityLivingBase t = mob.getAttackTarget();
        if (t != null && !mob.isWithinHomeDistance((int) Math.floor(t.posX), (int) Math.floor(t.posY), (int) Math.floor(t.posZ))) {
            mob.setAttackTarget(null);                            // out of its Vault's reach: let it go
        }
        NpcNavSC.walkTo(mob, mob.homeX() + 0.5D, mob.homeY(), mob.homeZ() + 0.5D, speed);   // past its follow range (24) - by legs
    }

    @Override
    public boolean continueExecuting() {
        if (++walked > GIVE_UP_TICKS) {
            nextCheck = mob.ticksExisted + RETRY_TICKS;           // no way home: a while before the next try
            return false;
        }
        if (--repath <= 0 && mob.getNavigator().noPath() && mob.beyondLeash(-2D)) {
            repath = REPATH;
            NpcNavSC.walkTo(mob, mob.homeX() + 0.5D, mob.homeY(), mob.homeZ() + 0.5D, speed);
        }
        return mob.beyondLeash(-4D);                              // back well inside before it fights again
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPathEntity();
    }
}
