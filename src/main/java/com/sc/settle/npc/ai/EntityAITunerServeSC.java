package com.sc.settle.npc.ai;

import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE N (rules ПЖ-5, ПО-9): while a player's «Терминал обмена» with this Tuner is open, it stands still and faces
 * him (the nearest, if several trade at once) - its day mustn't walk it out of ContainerTradeSC.REACH and close the
 * window. Mutex 3: no walking, no looking round.
 */
public class EntityAITunerServeSC extends EntityAIBase {

    private final EntityTunerSC t;

    public EntityAITunerServeSC(EntityTunerSC t) {
        this.t = t;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return t.customer() != null;
    }

    @Override
    public void startExecuting() {
        t.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        EntityPlayer p = t.customer();
        if (p != null) {
            t.getLookHelper().setLookPositionWithEntity(p, 10F, (float) t.getVerticalFaceSpeed());
        }
    }
}
