package com.sc.settle;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §2.3): who a settlement slot holds - the Tuners' professions and the
 * guards' ranks. The ordinal is what the registry (SettlementRegistrySC.Slot.kind) and the entities' NBT / DataWatcher
 * keep: append only, never renumber. Ф3 (docs/plan-substrate-tech-f3.md §6) appended the Power Engineer, the Armourer,
 * the Salvager (Tuners) and the Captain, the Commandant (guards: EntityWardenSC ranks, back from the Clone Capsule after
 * 20 minutes). Lang: sc.settle.kind.<key>. Shared core: only the coordinator edits this file.
 */
public enum NpcKindSC {

    /** «Старейшина купола» - one in every settlement: contracts, reputation, the dear deals. */
    ELDER("elder", false),
    /** «Технолог» - wafers, GaAs, controllers. */
    TECHNOLOGIST("technologist", false),
    /** «Металлург» - tantalum, hafnium. */
    METALLURGIST("metallurgist", false),
    /** «Газовщик» - liquid helium, deuterium. */
    GAS_TECH("gasTech", false),
    /** «Картограф прокола» - the Settlement Map, the Return Capsule. */
    CARTOGRAPHER("cartographer", false),
    /** «Постовой» - Nano 4/4, every settlement. */
    SENTRY("sentry", true),
    /** «Старший страж» - Quantum 4/4 with helium, Settlement and City. */
    SENIOR_WARDEN("seniorWarden", true),
    // ---- Ф3 (docs/plan-substrate-tech-f3.md §6.1, rule ПЖ-8)
    /** «Энергетик» - energy cells, the helium loop, recharging a piece of armour (a service). */
    POWER_ENGINEER("powerEngineer", false),
    /** «Оружейник» - armour chips, ПЭ-1 modules, restoring trophies (a service); works at the guard square's armour station. */
    ARMOURER("armourer", false),
    /** «Скупщик» - buys combat loot, scrap and Nano trophies for Credit Chips; works at the warehouse. */
    SALVAGER("salvager", false),
    /** «Капитан купола» (ПГ-11) - Exo 4/4 with helium and the shield, the Exo Blade, a heavy «Пробой» blow; every City, 30% of Settlements. */
    CAPTAIN("captain", true, NpcKindSC.CHIEF_RESPAWN_TICKS),
    /** «Комендант города» (ПГ-12) - Singular armour, the Singular Blade, a ПЭ-1 Pulse against the Shells near the dome; Cities only. */
    COMMANDANT("commandant", true, NpcKindSC.CHIEF_RESPAWN_TICKS);

    /** A dead Tuner comes back from the Clone Capsule one a game day per settlement (§5 «по одному в игровые сутки»). */
    public static final int TUNER_RESPAWN_TICKS = 24000;
    /** A dead guard comes back after 5 minutes (§6). */
    public static final int GUARD_RESPAWN_TICKS = 6000;
    /** Ф3: the Captain and the Commandant come back after 20 minutes (§6 «Возрождение»). */
    public static final int CHIEF_RESPAWN_TICKS = 24000;

    /** The lang / texture name. */
    public final String key;
    /** A guard (EntityWardenSC, ISettlementGuardSC) rather than a Tuner (EntityTunerSC, ISettlementNpcSC). */
    public final boolean guard;
    /** Its own respawn time, 0: the default of a Tuner / a guard. */
    private final int respawn;

    NpcKindSC(String key, boolean guard) {
        this(key, guard, 0);
    }

    NpcKindSC(String key, boolean guard, int respawn) {
        this.key = key;
        this.guard = guard;
        this.respawn = respawn;
    }

    /** Ticks from a death to the Clone Capsule's copy (only while no player is within SettlementSC.RESPAWN_CLEAR of the core). */
    public int respawnTicks() {
        return respawn > 0 ? respawn : guard ? GUARD_RESPAWN_TICKS : TUNER_RESPAWN_TICKS;
    }

    /** Ф3: one of the City's two chiefs (one slot each; EntityWardenSC dresses them apart). */
    public boolean chief() {
        return this == CAPTAIN || this == COMMANDANT;
    }

    /** The kind of an ordinal (an unknown one - a world from a newer version - is ELDER's, never a crash). */
    public static NpcKindSC of(int ordinal) {
        NpcKindSC[] all = values();
        return all[ordinal >= 0 && ordinal < all.length ? ordinal : 0];
    }

    /** sc.settle.kind.<key>. */
    public String langKey() {
        return "sc.settle.kind." + key;
    }
}
