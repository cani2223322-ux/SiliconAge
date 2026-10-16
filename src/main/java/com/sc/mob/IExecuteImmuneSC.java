package com.sc.mob;

/**
 * Marker (docs/plan-substrate-tech.md §4, rule ПБ-8): the Exo blade's «Казнь» (BladeLogicSC.hit - the absolute
 * finishing blow below 20% health) doesn't work on it. Elites, the Singular Sentinel, «Затмение», camp warlords.
 * IBossDisplayData stays the boss's alone (its health bar), so the bosses aren't marked by it.
 */
public interface IExecuteImmuneSC {
}
