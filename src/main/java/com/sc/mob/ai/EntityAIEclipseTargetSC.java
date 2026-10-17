package com.sc.mob.ai;

import com.sc.item.ItemArmorSC;
import com.sc.mob.EntityEclipseSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.4, rule ПС-34: «охотится за игроком с самой сильной бронёй»): «Затмение» goes for
 * the player in the strongest armour - of the players it may attack (vanilla's isSuitableTarget: alive, not in creative, in
 * sight) within HUNT_RANGE, the one with the highest armourScore (the armour points, and the best suit of this mod worn: a
 * Singular set before an Exo one of the same points), the nearer of two equal. Looked at again every RETARGET_TICKS while it
 * fights; nothing while it flees (EntityEclipseSC.fleeing). A target task (mutex 1) above vanilla's HurtByTarget.
 */
public class EntityAIEclipseTargetSC extends EntityAITarget {

    private final EntityEclipseSC eclipse;
    private int wait;
    private EntityLivingBase pick;

    public EntityAIEclipseTargetSC(EntityEclipseSC mob) {
        super(mob, true);
        this.eclipse = mob;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (eclipse.fleeing() || --wait > 0) {
            return false;
        }
        wait = EntityEclipseSC.RETARGET_TICKS / 2 + taskOwner.getRNG().nextInt(EntityEclipseSC.RETARGET_TICKS / 2 + 1);
        pick = strongest();
        return pick != null;
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(pick);
        wait = EntityEclipseSC.RETARGET_TICKS;
        super.startExecuting();
    }

    @Override
    public boolean continueExecuting() {
        if (eclipse.fleeing()) {
            return false;
        }
        if (--wait <= 0) {
            wait = EntityEclipseSC.RETARGET_TICKS;
            EntityLivingBase s = strongest();
            if (s != null && s != taskOwner.getAttackTarget()) {
                taskOwner.setAttackTarget(s);
            }
        }
        return super.continueExecuting();
    }

    @Override
    public void resetTask() {
        pick = null;
        super.resetTask();
    }

    @Override
    protected double getTargetDistance() {
        return EntityEclipseSC.HUNT_RANGE;
    }

    /** The visible player it may attack with the highest armourScore within HUNT_RANGE, null none. */
    private EntityLivingBase strongest() {
        double range = getTargetDistance();
        EntityPlayer best = null;
        int bestScore = -1;
        double bestDist = Double.MAX_VALUE;
        for (Object o : taskOwner.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double d = taskOwner.getDistanceSqToEntity(p);
            if (d > range * range || !isSuitableTarget(p, false)) {
                continue;
            }
            int score = armourScore(p);
            if (score > bestScore || score == bestScore && d < bestDist) {
                best = p;
                bestScore = score;
                bestDist = d;
            }
        }
        return best;
    }

    /** How strong a player's armour is: its armour points x 8 + the best suit of this mod worn (Nano 1 .. Singular 4). */
    public static int armourScore(EntityPlayer p) {
        int suit = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.inventory.armorInventory[i];
            if (s != null && s.getItem() instanceof ItemArmorSC) {
                suit = Math.max(suit, ((ItemArmorSC) s.getItem()).getSuit().ordinal() + 1);
            }
        }
        return p.getTotalArmorValue() * 8 + suit;
    }
}
