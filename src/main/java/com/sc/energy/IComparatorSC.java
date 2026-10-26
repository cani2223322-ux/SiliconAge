package com.sc.energy;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md, «Редстоун для машин и реакторов», ★ A; the coordinator's - ОМ-5 and ОМ-7 implement it): a tile
 * that gives a comparator its own reading - a machine its progress, a generator its buffer, a reactor its plasma's stability, the
 * Singular Reactor the black hole's mass, a quarry its progress / buffer, the Energy Bank its charge. Its block answers
 * hasComparatorInputOverride true and getComparatorInputOverride with comparatorLevel(); the tile calls
 * World.func_147453_f(x, y, z, block) whenever the level it answers changes (ComparatorSC.changed keeps the last one sent).
 */
public interface IComparatorSC {

    /** The comparator's level now, 0..15 (server). */
    int comparatorLevel();
}
