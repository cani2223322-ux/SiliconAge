package com.sc.settle;

/**
 * Marker (docs/idea-dimension-substrate.md §6, plan-substrate-tech-f2.md rules ПГ-1..ПГ-9): a dome's guard. Not
 * under the Tuners' protection - a fight with the guards is fair, and a provocation (SettlementProtectSC.violation).
 * Not EntityMob / IMob (Peaceful would remove it, JourneyMap would draw an enemy, its own core would push it out), not
 * EntityGolem (guardsEntity would refuse the blow). INpc: the blades' sweeps (BladeLogicSC.fair) and the ПЭ-1 Pulse
 * (EmpMobSC) leave it alone. Shared core.
 */
public interface ISettlementGuardSC extends ISettlementMemberSC {
}
