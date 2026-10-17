package com.sc.arena;

import com.sc.dim.SubstrateSC;

/**
 * Подложка Ф5 «Финал» (docs/plan-substrate-tech-f5.md): the structures' registration and the finale's small shared questions.
 * <ul>
 * <li>the Breakdown Epicentre - one arena a 4096 x 4096 region at its «main lines'» crossing (ArenaLayoutSC; MapGenArenaSC and its
 * pieces, the Breakdown Console, the coils' pedestals, the columns - package A; the Breakdown Warden and its coils - package B);</li>
 * <li>the Lithographers' bridge wrecks - small ruins on the plateaus (lore.WreckLayoutSC, lore.MapGenWreckSC - package J).</li>
 * </ul>
 * Both are vanilla structures built inside the populate window only (no cascade), named in MapGenStructureIO with the prefix
 * SCSi14 ("SCSi14Arena" / "SCSi14Arn*", "SCSi14Wreck" / "SCSi14Wrk*"); a start on ground explored before is empty. Shared core:
 * only the coordinator edits this file.
 */
public final class ArenaSC {

    private ArenaSC() {
    }

    /** SCMod.init, after RuinSC.init: the structure names - always, the dimension off too (a saved world must load). */
    public static void init() {
        MapGenArenaSC.registerStructures();
        com.sc.lore.MapGenWreckSC.registerStructures();
    }

    /** New arenas and wrecks generate: the world is on (those made stay either way). No config key of their own. */
    public static boolean generates() {
        return SubstrateSC.active();
    }
}
