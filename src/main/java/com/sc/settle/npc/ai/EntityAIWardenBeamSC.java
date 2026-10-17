package com.sc.settle.npc.ai;

import com.sc.mob.MobCombatSC;
import com.sc.mob.ai.EntityAIBeamAttackSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.util.WeaponType;

import net.minecraft.entity.EntityCreature;

/**
 * PACKAGE N (rules ПГ-2, ПГ-4): the guards' beams - Ф1's EntityAIBeamAttackSC as it is (an instant beam, the aim of
 * 1 s everyone sees, only along a clear line; MobCombatSC.beamDamage: the Ion Cutter 4, the Plasma Rifle 7), the
 * Senior's Plasma Rifle firing every 2..3 s: up to EntityWardenSC.RIFLE_RELOAD_SPREAD ticks more after each shot.
 */
public class EntityAIWardenBeamSC extends EntityAIBeamAttackSC {

    private final EntityCreature mob;

    public EntityAIWardenBeamSC(EntityCreature mob, double speed) {
        super(mob, speed);
        this.mob = mob;
    }

    @Override
    protected int reloadTicks() {
        int t = super.reloadTicks();
        return MobCombatSC.gun(mob) == WeaponType.PLASMA_RIFLE ? t + mob.getRNG().nextInt(EntityWardenSC.RIFLE_RELOAD_SPREAD + 1) : t;
    }
}
