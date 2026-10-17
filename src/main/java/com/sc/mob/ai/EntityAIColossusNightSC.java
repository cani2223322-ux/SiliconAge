package com.sc.mob.ai;

import com.sc.hazard.RaidRulesSC;
import com.sc.mob.EntityColossusSC;

import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.7, rule ПС-37): the Quartzite Colossus is neutral by day - vanilla's nearest player
 * in sight (looked for one tick in SEARCH_CHANCE), but only at night (RaidRulesSC.night: the world's clock, the same night the
 * raids keep). A blow by day still makes it fight (EntityAIHurtByTarget, the colossus's own); a fight begun at night goes on
 * past dawn.
 */
public class EntityAIColossusNightSC extends EntityAINearestAttackableTarget {

    /** One tick in this many it looks (vanilla's targetChance: the AI rule - no oftener than every 10 ticks on average). */
    public static final int SEARCH_CHANCE = 10;

    public EntityAIColossusNightSC(EntityColossusSC colossus) {
        super(colossus, EntityPlayer.class, SEARCH_CHANCE, true);
    }

    @Override
    public boolean shouldExecute() {
        return RaidRulesSC.night(taskOwner.worldObj.getWorldTime()) && super.shouldExecute();
    }
}
