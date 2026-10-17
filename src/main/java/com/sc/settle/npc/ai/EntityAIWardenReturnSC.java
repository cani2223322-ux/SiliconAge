package com.sc.settle.npc.ai;

import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (rule ПГ-6): past its leash (EntityWardenSC.returning, set by the brain) a guard walks back to its core
 * without a target, above everything but swimming (mutex 3) - it doesn't chase anyone out of its dome's reach.
 */
public class EntityAIWardenReturnSC extends EntityAIBase {

    private static final int REPATH = 20;

    private final EntityWardenSC g;
    private int ticks;

    public EntityAIWardenReturnSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return g.returning() && g.link().get() != null;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        walk();
    }

    @Override
    public void updateTask() {
        if (++ticks % REPATH == 0 && g.getNavigator().noPath()) {
            walk();
        }
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
    }

    private void walk() {
        SettlementRegistrySC.Settlement s = g.link().get();
        if (s != null) {
            NpcNavSC.walkTo(g, NpcLinkSC.core(s), EntityWardenSC.BACK);
        }
    }
}
