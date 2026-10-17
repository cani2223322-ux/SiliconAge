package com.sc.mob;

import net.minecraft.entity.Entity;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §8.3): the factions of the proposal's §8 - «Отступники» (living renegades in
 * stolen armour, crimson light, the camps), «Оболочки» (the Lithographers' empty armour, cold light, the Dead Nodes and the
 * Vaults) and the rest («Шум и одичавшая техника», no faction). Pure. Shared core: only the coordinator edits this file.
 */
public final class FactionSC {

    public static final String RENEGADES = "renegades", SHELLS = "shells", NONE = "";

    private FactionSC() {
    }

    /** The faction of `e`: its own answer (IFactionSC), else NONE. */
    public static String of(Entity e) {
        if (e instanceof IFactionSC) {
            String f = ((IFactionSC) e).faction();
            return f == null ? NONE : f;
        }
        return NONE;
    }

    /** `e` is of faction `f` (NONE matches nothing - the noise has no side to spare). */
    public static boolean is(Entity e, String f) {
        return f != null && !f.isEmpty() && f.equals(of(e));
    }

    /**
     * The faction of the Ф1..Ф3 classes by their class (they don't say it themselves): the Marauder, the Trooper, the Jammer and
     * the Executioner are Renegades; the Lineman, the Hollow Shell and its repair drone are Shells; anything else NONE. The Ф4
     * classes answer IFactionSC.faction() on their own.
     */
    public static String defaultOf(Entity e) {
        if (e instanceof EntityMarauderSC || e instanceof EntityTrooperSC || e instanceof EntityJammerSC || e instanceof EntityExecutionerSC) {
            return RENEGADES;
        }
        if (e instanceof EntityLinemanSC || e instanceof EntityHollowShellSC || e instanceof EntityRepairDroneSC) {
            return SHELLS;
        }
        return NONE;
    }
}
