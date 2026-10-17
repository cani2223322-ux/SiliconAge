package com.sc.settle.npc.ai;

import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE N (rule ПП-10, stage 2 of a block violation): the guard package C picked (GuardAlertSC.watched) walks up to
 * the player, to within CLOSE blocks, and keeps its eyes on him for as long as package C says. Mutex 3. Ф3: the City
 * Commandant (ПГ-12) doesn't leave his plaza for it - he watches from there.
 */
public class EntityAIWardenWatchSC extends EntityAIBase {

    /** It stops this close (blocks across). */
    public static final double CLOSE = 4D;
    private static final int REPATH = 20;

    private final EntityWardenSC g;
    private int ticks;

    public EntityAIWardenWatchSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return g.getAttackTarget() == null && g.warnedPlayer() == null && g.watchedPlayer() != null;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
    }

    @Override
    public void updateTask() {
        EntityPlayer p = g.watchedPlayer();
        if (p == null) {
            return;
        }
        g.getLookHelper().setLookPositionWithEntity(p, 30F, 30F);
        if (g.getDistanceSqToEntity(p) <= CLOSE * CLOSE || g.holdsPlaza() && !EntityWardenSC.nearPlaza(p, g.plaza(), 0D)) {
            g.getNavigator().clearPathEntity();             // Ф3: the Commandant watches from his plaza
        } else if (ticks++ % REPATH == 0) {
            NpcNavSC.walkTo(g, p.posX, p.posY, p.posZ, EntityWardenSC.PATROL);
        }
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
    }
}
