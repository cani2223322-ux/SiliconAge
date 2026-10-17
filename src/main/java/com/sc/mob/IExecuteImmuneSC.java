package com.sc.mob;

/**
 * Docs/plan-substrate-tech.md §4, rule ПБ-8; Ф3 (docs/plan-substrate-tech-f3.md §5, ПС-24): the Exo blade's «Казнь»
 * (BladeLogicSC.hit - the absolute finishing blow below 20% health) doesn't work on it while executeImmune() says so - an
 * elite (any armoured mob with an elite modifier: a per-mob answer, so it is a method, not a bare marker), the Exo
 * Executioner, later the Singular Sentinel, «Затмение», camp warlords. IBossDisplayData stays the boss's alone (its health
 * bar), so the bosses aren't marked by it. Shared core: only the coordinator edits this file.
 */
public interface IExecuteImmuneSC {

    /** «Казнь» can't finish it now (both sides; the server's answer is what counts). */
    boolean executeImmune();
}
