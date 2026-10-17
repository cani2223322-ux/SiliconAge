package com.sc.mob;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §4.9, rules ПС-7, ПС-8): how a native hostile counts in SpawnDirectorSC's
 * census - the caps of docs/idea-dimension-substrate.md §12: at most 8 natives within 64 blocks of a player, of them at most
 * 2 of the Quantum tier and above; at most 1 elite within 128; about 40 in the whole dimension. A nanite swarm counts once
 * (its first particle only), a mimic never (a structure's fixture), the rest as one each. Both bases implement it
 * (EntityArmoredMobSC by its suit and elite flag, EntitySubstrateMobSC as a plain one); a class may answer otherwise.
 * Server; answers from its own fields. Shared core: only the coordinator edits this file.
 */
public interface ICensusSC {

    /** The census tiers: a plain native, the Quantum tier and above (Quantum / Exo / Singular armour, the Executioner). */
    int TIER_PLAIN = 0, TIER_QUANTUM = 1;

    /** It takes a place under the caps at all (false: a swarm's other particles, a mimic). */
    boolean censusCounts();

    /** TIER_PLAIN or TIER_QUANTUM. */
    int censusTier();

    /** An elite for the «1 in 128 blocks» cap: an elite modifier, or an elite of its kind (the Executioner). */
    boolean censusElite();
}
