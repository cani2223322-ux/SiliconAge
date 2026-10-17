package com.sc.mob;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §8.3, rule ПЗ-3; proposal §9 «исключение по фракции»): the side a Substrate
 * creature is on - FactionSC.RENEGADES (the Marauders, Troopers, Jammers, the Executioner, the camps' Warlords, «Затмение»),
 * FactionSC.SHELLS (the Linemen, the Hollow Shells and their drones, the Overheated, the Sentinels), FactionSC.NONE (the
 * «noise» and everything else). A Stolen Field Generator spares its own faction (TileEntityStolenFieldSC: targets() and
 * deflects()). EntityArmoredMobSC and EntitySubstrateMobSC answer FactionSC.defaultOf(this); a class may say its own.
 * Both sides, from the class alone. Shared core: only the coordinator edits this file.
 */
public interface IFactionSC {

    /** FactionSC.RENEGADES / SHELLS / NONE (never null). */
    String faction();
}
