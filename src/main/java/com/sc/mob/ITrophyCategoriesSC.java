package com.sc.mob;

import java.util.Random;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §9.1, rule ПТ-16): an armoured native whose trophy roll has categories of its own -
 * the camp's Warlord (a worn Quantum piece 2%, its Exo chestplate 0.5%, nothing of its weapons), a Sentinel carrier (its ПЭ-1
 * 10%). MobDropsSC builds the standard four - {a worn piece by its suit, the blade in hand, the gun in hand, a ПЭ-1 in hand} -
 * and asks the mob; everyone else keeps the standard path. The one roll, Looting, the elite and storm multipliers, the
 * bad-luck counter (by the mob's level) stay MobDropsSC's. Server.
 */
public interface ITrophyCategoriesSC {

    /**
     * The chances to roll with: `standard` (a copy - the mob may change it in place) or an array of its own; indices 0..3 keep
     * the standard meaning (MobDropsSC picks a worn piece / the held blade / the held gun / the held ПЭ-1), indices 4 and on are
     * the mob's (trophyMeta).
     */
    float[] trophyCategories(float[] standard);

    /** The trophy meta (TrophyTableSC) for its own category `index` (4 and on) - -1: nothing after all. */
    int trophyMeta(int index, Random rand);
}
