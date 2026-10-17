package com.sc.mob.ai;

import com.sc.mob.EntityTrooperSC;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.2, rule ПС-10): the Trooper's Plasma Rifle - EntityAIBeamAttackSC's beam with
 * its own numbers: a red laser aim of AIM_TICKS (1.5 s, IMobActionSC.ACT_AIM), a beam of 7 (MobCombatSC.BEAM), a shot
 * every SHOT_EVERY_MIN..MAX ticks (2..3 s), RIFLE_RANGE blocks. Walks up like the Ф1 gunners; a target right next to it
 * gets the fist.
 */
public class EntityAITrooperRifleSC extends EntityAIBeamAttackSC {

    public EntityAITrooperRifleSC(EntityTrooperSC mob, double speed) {
        super(mob, speed, EntityTrooperSC.AIM_TICKS, 1, 1, EntityTrooperSC.RIFLE_RANGE, EntityTrooperSC.RIFLE_COLOR);
    }

    /** From a shot to the next aim: the rest of SHOT_EVERY_MIN..MAX after the aim. */
    @Override
    protected int reloadTicks() {
        int every = EntityTrooperSC.SHOT_EVERY_MIN + mob.getRNG().nextInt(EntityTrooperSC.SHOT_EVERY_MAX - EntityTrooperSC.SHOT_EVERY_MIN + 1);
        return Math.max(0, every - EntityTrooperSC.AIM_TICKS);
    }
}
