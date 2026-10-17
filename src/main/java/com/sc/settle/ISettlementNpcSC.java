package com.sc.settle;

/**
 * Marker (docs/idea-dimension-substrate.md §5 «Защита жителей», plan-substrate-tech-f2.md rule ПЖ-2): a Tuner. Players'
 * attacks on it are cancelled inside its dome and the 24-block belt round it (SettlementProtectSC: AttackEntityEvent,
 * LivingAttackEvent with a player source - absolute damage and shots too), right-clicks are never touched (trading).
 * Not EntityVillager (other mods' trades, golems, VillageCollection), not EntityAgeable (guardsEntity would cancel the
 * right-click), not IMob (the core's FILTER_HOSTILE would push it out). Shared core.
 */
public interface ISettlementNpcSC extends ISettlementMemberSC {
}
