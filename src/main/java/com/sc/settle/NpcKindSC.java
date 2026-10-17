package com.sc.settle;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §2.3): who a settlement slot holds - the Tuners' professions and the
 * guards' ranks. The ordinal is what the registry (SettlementRegistrySC.Slot.kind) and the entities' NBT / DataWatcher
 * keep: append only, never renumber (Ф3 appends the Power Engineer, the Armourer, the Salvager, the Captain and the
 * Commandant after SENIOR_WARDEN). Lang: sc.settle.kind.<key>. Shared core: only the coordinator edits this file.
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
    SENIOR_WARDEN("seniorWarden", true);

    /** A dead Tuner comes back from the Clone Capsule one a game day per settlement (§5 «по одному в игровые сутки»). */
    public static final int TUNER_RESPAWN_TICKS = 24000;
    /** A dead guard comes back after 5 minutes (§6; the Captain and the Commandant of Ф3: 20 minutes). */
    public static final int GUARD_RESPAWN_TICKS = 6000;

    /** The lang / texture name. */
    public final String key;
    /** A guard (EntityWardenSC, ISettlementGuardSC) rather than a Tuner (EntityTunerSC, ISettlementNpcSC). */
    public final boolean guard;

    NpcKindSC(String key, boolean guard) {
        this.key = key;
        this.guard = guard;
    }

    /** Ticks from a death to the Clone Capsule's copy (only while no player is within SettlementSC.RESPAWN_CLEAR of the core). */
    public int respawnTicks() {
        return guard ? GUARD_RESPAWN_TICKS : TUNER_RESPAWN_TICKS;
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
