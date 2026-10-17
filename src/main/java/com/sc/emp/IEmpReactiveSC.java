package com.sc.emp;

import net.minecraft.entity.Entity;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §3.6, rule ПБ-20): a creature without the mod's armour that a ПЭ-1 hit does
 * something of its own to, instead of the old Slowness (EmpSC.applyNoArmour) - a sentry drone falls at once, a nanite swarm
 * burns (a Point: the particle hit; a Pulse: every particle of the swarm round the hit), a mimic wakes up stunned. Asked by
 * EmpGunLogicSC (a player's shot) and EmpMobGunSC (a mob's) after the rules and the EMP screen let the shot through, only
 * while dimension.empMobTargets is on (off: Slowness, as before). Server.
 */
public interface IEmpReactiveSC {

    /**
     * A Point / Pulse from `shooter` (a player or a mob) reached it at (x, y, z). @return true when it reacted (the shooter
     * hears «sc.emp.hit.reactive»), false - nothing to do: the caller gives the Slowness as for any mob
     */
    boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z);
}
