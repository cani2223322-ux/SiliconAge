package com.sc.ruin;

import com.sc.dim.SubstrateSC;

/**
 * Подложка Ф3 «Руины» (docs/plan-substrate-tech-f3.md §8, rules ПУ-1..ПУ-7): the Lithographers' ruins - the Dead Nodes at
 * the Scribe lines' crossings (MapGenDeadNodeSC) and the abandoned lithographs under the plateaus (MapGenLithographSC), their
 * blocks (RuinBlocksSC: the ruin metal, burnt panels, husks of tanks and machines - no recipes, the drop is scrap) and loot.
 * Both are vanilla structures built inside the populate window only (no cascade), named in MapGenStructureIO with the
 * prefix SCSi14. Ф4 (docs/plan-substrate-tech-f4.md §8): the Renegades' camps (MapGenCampSC) and the Vaults under the Dead Nodes
 * (MapGenVaultSC), their blocks (CampBlocksSC) and items (RelicItemsSC). Shared core: only the coordinator edits this file.
 */
public final class RuinSC {

    private RuinSC() {
    }

    /** SCMod.init, after SettlementSC.init: the structure names - always, the dimension off too (a saved world must load). */
    public static void init() {
        MapGenDeadNodeSC.registerStructures();
        MapGenLithographSC.registerStructures();
        MapGenCampSC.registerStructures();                  // Ф4 (package C): the Renegades' camps, the Vaults
        MapGenVaultSC.registerStructures();
    }

    /** New ruins generate: the world is on (the ruins made already stay either way). No config key of their own. */
    public static boolean generates() {
        return SubstrateSC.active();
    }
}
