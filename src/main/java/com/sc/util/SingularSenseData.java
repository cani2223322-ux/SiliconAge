package com.sc.util;

/**
 * The client's copy of what the Singular helmet's senses sent (stage 2b): Ш1 the gravity scanner's
 * last pulse, Ш2 the mobs that target the player, Ш5 the analyzer's target. Written by the network
 * thread (ArmorNetSC), read by the renderer (client.SingularClientSC); no client classes here, so
 * the messages can be registered on a dedicated server too. Each part is replaced whole, with the
 * time it came (System.currentTimeMillis) - the renderer lets old data go.
 */
public final class SingularSenseData {

    /** Ш1 block kinds. */
    public static final int CHEST = 0, SPAWNER = 1, ORE = 2, MOD_ORE = 3;

    /** Ш1: x, y, z, kind for each block; entity id, hostile (1/0) for each mob. */
    public static volatile int[] scanBlocks = new int[0], scanMobs = new int[0];
    public static volatile long scanAt;
    /** Ш2: entity ids of the mobs whose target is this player. */
    public static volatile int[] threats = new int[0];
    public static volatile long threatAt;
    /** Ш5: what the analyzer looks at (null / kind NONE: nothing). */
    public static volatile Analysis analysis;
    public static volatile long analysisAt;

    private SingularSenseData() {
    }

    /** One analyzer result. */
    public static final class Analysis {
        public static final byte NONE = 0, MOB = 1, MACHINE = 2;
        /** Mob weaknesses (flags). */
        public static final int WEAK_WATER = 1, WEAK_HEAT = 2, UNDEAD = 4, ARTHROPOD = 8, FIRE_IMMUNE = 16, EXPLODES = 32;
        /**
         * Подложка Ф3 (docs/plan-substrate-tech-f3.md §7.2, rule ПИ-2): a Substrate mob's armour in the same int - flags
         * (com.sc.mob.MobAnalysisSC sets them, MobAnalysisClientSC prints them): the ПЭ-1 works on it, the emergency mode,
         * an elite, the shield up, the armour «down», the personal EMP screen up, a ПЭ-1 reacts on it (drone, swarm, mimic),
         * useless (no electronics: Ф4's Colossus); the suit in bits SUIT_SHIFT.. (0 none, ArmorSuit ordinal + 1); the
         * reserve's % in bits RESERVE_SHIFT.. (RESERVE_UNKNOWN: none to tell). No message field is added.
         */
        public static final int WEAK_EMP = 64, EMERGENCY = 128, ELITE = 256, SHIELD = 512, DOWN = 1024, SCREEN = 2048, EMP_REACTS = 4096,
                EMP_USELESS = 8192;
        public static final int SUIT_SHIFT = 16, SUIT_MASK = 7, RESERVE_SHIFT = 19, RESERVE_MASK = 127, RESERVE_UNKNOWN = 127;

        public byte kind;
        // a mob
        public int entityId, armor, flags;
        public float health, maxHealth, attack = -1F;
        // a machine
        public int x, y, z, stored, capacity, output = -1;
        public byte status = -1, progress = -1;
        public boolean powerOn = true;
    }

    public static void setScan(int[] blocks, int[] mobs) {
        scanBlocks = blocks;
        scanMobs = mobs;
        scanAt = System.currentTimeMillis();
    }

    public static void setThreats(int[] ids) {
        threats = ids;
        threatAt = System.currentTimeMillis();
    }

    public static void setAnalysis(Analysis a) {
        analysis = a;
        analysisAt = System.currentTimeMillis();
    }

    /** Another world / server: everything forgotten. */
    public static void clear() {
        scanBlocks = new int[0];
        scanMobs = new int[0];
        threats = new int[0];
        analysis = null;
        scanAt = threatAt = analysisAt = 0;
    }

    /** Pure: data that came at `at` is still good at `now` for `maxAgeMs`. */
    public static boolean fresh(long at, long now, long maxAgeMs) {
        return at > 0 && now - at >= 0 && now - at <= maxAgeMs;
    }
}
