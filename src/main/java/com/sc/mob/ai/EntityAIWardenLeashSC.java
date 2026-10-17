package com.sc.mob.ai;

import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.WardenFightSC;
import com.sc.mob.WardenRulesSC;
import com.sc.settle.npc.ai.NpcNavSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.1, §5.5, rule ПС-56): the Breakdown Warden's leash - never further than
 * WardenRulesSC.LEASH (26) from its arena's middle (an egg's: from where it first stood). Past it, it walks back to the middle (a
 * target that stands out of the pit - LEASH + DROP_MARGIN off the middle - is let go: it can still be reached by the ПЭ-1М and the
 * silencing pulse), and fights again once well inside. Checked every CHECK_TICKS; movement its own while it returns (mutex 1),
 * above the hums and the melee. A path every REPATH ticks at most; given up after GIVE_UP_TICKS (WardenFightSC's bookkeeping puts a
 * Warden that is really lost - out of its pit - back in the middle at once).
 */
public class EntityAIWardenLeashSC extends EntityAIBase {

    private static final int CHECK_TICKS = 10, GIVE_UP_TICKS = 400, REPATH = 20, RETRY_TICKS = 60;
    /** A target this far past the leash is let go; back this far inside before it fights again. */
    public static final double DROP_MARGIN = 8D, BACK_INSIDE = 6D;

    private final EntityBreakdownWardenSC mob;
    private final double speed;
    private int nextCheck, walked, repath;

    public EntityAIWardenLeashSC(EntityBreakdownWardenSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < nextCheck || mob.worldObj.isRemote) {
            return false;
        }
        nextCheck = mob.ticksExisted + CHECK_TICKS;
        return mob.fight().ready() && beyond(0D);
    }

    @Override
    public void startExecuting() {
        walked = 0;
        repath = REPATH;
        WardenFightSC f = mob.fight();
        EntityLivingBase t = mob.getAttackTarget();
        double drop = WardenRulesSC.LEASH + DROP_MARGIN;
        if (t != null && f.flatDistSq(t) > drop * drop) {
            mob.setAttackTarget(null);                            // out of the pit: let it go
        }
        NpcNavSC.walkTo(mob, f.cx, f.cy, f.cz, speed);
    }

    @Override
    public boolean continueExecuting() {
        if (++walked > GIVE_UP_TICKS) {
            nextCheck = mob.ticksExisted + RETRY_TICKS;
            return false;
        }
        if (--repath <= 0 && mob.getNavigator().noPath()) {
            repath = REPATH;
            WardenFightSC f = mob.fight();
            NpcNavSC.walkTo(mob, f.cx, f.cy, f.cz, speed);
        }
        return beyond(-BACK_INSIDE);
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPathEntity();
    }

    /** Further than the leash (+ `margin`) from the middle, across. */
    private boolean beyond(double margin) {
        double r = WardenRulesSC.LEASH + margin;
        return mob.fight().flatDistSq(mob) > r * r;
    }
}
