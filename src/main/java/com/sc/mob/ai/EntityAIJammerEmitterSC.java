package com.sc.mob.ai;

import com.sc.mob.EntityJammerSC;
import com.sc.mob.MobCombatSC;
import com.sc.util.WeaponType;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.3, rule ПС-10): the Jammer's gun - EntityAIBeamAttackSC with the Pulse
 * Emitter's numbers: an aim of AIM_TICKS (IMobActionSC.ACT_AIM, the violet dotted line), then a burst of BURST rays
 * of 3, BURST_GAP_TICKS ticks apart (each needs the sight), RELOAD_TICKS before the next aim; EMITTER_RANGE blocks. With
 * the rule mobsUseEmp off a carrier holds the Ion Cutter - then one ray of 4 on the Ф1 rhythm. It only aims and looks (mutex
 * 2): the walking is EntityAIJammerKeepBackSC's, so it shoots from behind its allies instead of walking up.
 */
public class EntityAIJammerEmitterSC extends EntityAIBeamAttackSC {

    public EntityAIJammerEmitterSC(EntityJammerSC mob) {
        super(mob, 1.0D, EntityJammerSC.AIM_TICKS, EntityJammerSC.BURST, EntityJammerSC.BURST_GAP_TICKS, EntityJammerSC.EMITTER_RANGE,
                EntityJammerSC.EMITTER_COLOR);
        setMutexBits(2);
    }

    private boolean emitter() {
        return MobCombatSC.gun(mob) == WeaponType.PULSE_EMITTER;
    }

    @Override
    protected int burstCount() {
        return emitter() ? super.burstCount() : 1;
    }

    @Override
    protected int reloadTicks() {
        return emitter() ? EntityJammerSC.RELOAD_TICKS : super.reloadTicks();
    }

    /** The keep-back task walks. */
    @Override
    protected void move(double distSq, boolean seen, boolean inRange) {
    }

    @Override
    protected void stop() {
    }
}
