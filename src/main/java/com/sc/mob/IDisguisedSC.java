package com.sc.mob;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §4.8, rule ПИ-3): a creature that passes for something else - the Mimic
 * Machine asleep as a Crusher or a furnace. WAILA (compat.WailaEntitySC) shows «???» for its name while disguised(), the
 * Singular helmet's analyzer (Ш5) names it «???» too and lists no weaknesses. Both sides (the DataWatcher).
 * Shared core: only the coordinator edits this file.
 */
public interface IDisguisedSC {

    /** It still passes for a machine. */
    boolean disguised();
}
