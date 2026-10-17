package com.sc.mob;

import com.sc.SCMod;

import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.1): the Substrate's entities, registered from SCMod.init. Only
 * EntityRegistry.registerModEntity under this mod's id - its own pool from 0 (the Vehicles add-on has its own); the
 * global id pool 0..255 that EnderIO, GraviSuite, TransformersMod, Thermal Foundation and Twilight Forest share is
 * never touched, so there's no vanilla spawn egg: ItemSpawnEggSC is ours. The names and pool ids below are what
 * worlds keep - append, never renumber. Entity names: "SiliconAge.<name>", lang entity.SiliconAge.<name>.name.
 */
public final class MobEntitiesSC {

    /** Pool ids (fixed). Ф2 (the coordinator's, docs/plan-substrate-tech-f2.md §2.2): 2 the Tuner, 3 the dome's guard. */
    public static final int ID_LINEMAN = 0, ID_MARAUDER = 1, ID_TUNER = 2, ID_WARDEN = 3;
    /**
     * Ф3 (the coordinator's, docs/plan-substrate-tech-f3.md §2.2): package M's armoured hostiles and the Shell's drone, package X's
     * «noise» - the swarm's particle, the feral sentry drone, the mimic. The City's Captain and Commandant are warden ranks.
     */
    public static final int ID_TROOPER = 4, ID_JAMMER = 5, ID_HOLLOW_SHELL = 6, ID_REPAIR_DRONE = 7, ID_EXECUTIONER = 8, ID_NANITE = 9,
            ID_SENTRY_DRONE = 10, ID_MIMIC = 11;
    /**
     * Ф4 (the coordinator's, docs/plan-substrate-tech-f4.md §2.2): package M's armoured ones - the Overheated, the camps'
     * Warlord, the Vaults' Singular Sentinel, «Затмение»; package N's «noise» - the Induction Phantom, the Current Leech, the
     * Quartzite Colossus, the Arc Strider, the Scrapper Crab - and the peaceful Wafer Crab.
     */
    public static final int ID_OVERHEATED = 12, ID_WARLORD = 13, ID_SENTINEL = 14, ID_ECLIPSE = 15, ID_PHANTOM = 16, ID_LEECH = 17,
            ID_COLOSSUS = 18, ID_STRIDER = 19, ID_SCRAPPER = 20, ID_WAFER_CRAB = 21;
    /**
     * Ф5 (the coordinator's, docs/plan-substrate-tech-f5.md §2.2): package B's boss - the Breakdown Warden - and its four Containment
     * Coils; package K's caravan - its master (a Tuner) and its guards (dome wardens). None of them comes from the spawn director;
     * the coil and the caravan have no spawn egg (hasEgg).
     */
    public static final int ID_BREAKDOWN_WARDEN = 22, ID_CONTAINMENT_COIL = 23, ID_CARAVAN_MASTER = 24, ID_CARAVAN_GUARD = 25;
    /** Tracking: as vanilla's monsters (80 blocks, every 3 ticks, with velocity). */
    public static final int TRACKING = 80, UPDATES = 3;

    private MobEntitiesSC() {
    }

    public static void init() {
        EntityRegistry.registerModEntity(EntityLinemanSC.class, EntityLinemanSC.NAME, ID_LINEMAN, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityMarauderSC.class, EntityMarauderSC.NAME, ID_MARAUDER, SCMod.instance, TRACKING, UPDATES, true);
        // Подложка Ф2: the settlements' creatures (package N's classes)
        EntityRegistry.registerModEntity(com.sc.settle.npc.EntityTunerSC.class, com.sc.settle.npc.EntityTunerSC.NAME, ID_TUNER, SCMod.instance,
                TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(com.sc.settle.npc.EntityWardenSC.class, com.sc.settle.npc.EntityWardenSC.NAME, ID_WARDEN, SCMod.instance,
                TRACKING, UPDATES, true);
        // Подложка Ф3: the armoured hostiles and the Shell's drone (package M), the «noise» (package X)
        EntityRegistry.registerModEntity(EntityTrooperSC.class, EntityTrooperSC.NAME, ID_TROOPER, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityJammerSC.class, EntityJammerSC.NAME, ID_JAMMER, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityHollowShellSC.class, EntityHollowShellSC.NAME, ID_HOLLOW_SHELL, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityRepairDroneSC.class, EntityRepairDroneSC.NAME, ID_REPAIR_DRONE, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityExecutionerSC.class, EntityExecutionerSC.NAME, ID_EXECUTIONER, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityNaniteSC.class, EntityNaniteSC.NAME, ID_NANITE, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntitySentryDroneSC.class, EntitySentryDroneSC.NAME, ID_SENTRY_DRONE, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityMimicSC.class, EntityMimicSC.NAME, ID_MIMIC, SCMod.instance, TRACKING, UPDATES, true);
        // Подложка Ф4: the armoured ones (package M), the noise and the crab (package N)
        EntityRegistry.registerModEntity(EntityOverheatedSC.class, EntityOverheatedSC.NAME, ID_OVERHEATED, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityWarlordSC.class, EntityWarlordSC.NAME, ID_WARLORD, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntitySentinelSC.class, EntitySentinelSC.NAME, ID_SENTINEL, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityEclipseSC.class, EntityEclipseSC.NAME, ID_ECLIPSE, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityPhantomSC.class, EntityPhantomSC.NAME, ID_PHANTOM, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityLeechSC.class, EntityLeechSC.NAME, ID_LEECH, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityColossusSC.class, EntityColossusSC.NAME, ID_COLOSSUS, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityStriderSC.class, EntityStriderSC.NAME, ID_STRIDER, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityScrapperSC.class, EntityScrapperSC.NAME, ID_SCRAPPER, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(EntityWaferCrabSC.class, EntityWaferCrabSC.NAME, ID_WAFER_CRAB, SCMod.instance, TRACKING, UPDATES, true);
        // Подложка Ф5: the boss and its coils (package B; seen from far - the arena is big), the caravan (package K)
        EntityRegistry.registerModEntity(EntityBreakdownWardenSC.class, EntityBreakdownWardenSC.NAME, ID_BREAKDOWN_WARDEN, SCMod.instance, BOSS_TRACKING,
                UPDATES, true);
        EntityRegistry.registerModEntity(EntityContainmentCoilSC.class, EntityContainmentCoilSC.NAME, ID_CONTAINMENT_COIL, SCMod.instance, BOSS_TRACKING,
                20, false);
        EntityRegistry.registerModEntity(com.sc.settle.caravan.EntityCaravanMasterSC.class, com.sc.settle.caravan.EntityCaravanMasterSC.NAME,
                ID_CARAVAN_MASTER, SCMod.instance, TRACKING, UPDATES, true);
        EntityRegistry.registerModEntity(com.sc.settle.caravan.EntityCaravanGuardSC.class, com.sc.settle.caravan.EntityCaravanGuardSC.NAME,
                ID_CARAVAN_GUARD, SCMod.instance, TRACKING, UPDATES, true);
    }

    /** Ф5: the boss and its coils are tracked this far (the arena is 60 across; the coils' beams reach the boss). */
    public static final int BOSS_TRACKING = 128;

    /** Ф5: the pool ids that have a spawn egg (ItemSpawnEggSC) - all but the coil and the caravan's two (meaningless alone). */
    public static boolean hasEgg(int id) {
        return id >= 0 && id < NAMES.length && id != ID_CONTAINMENT_COIL && id != ID_CARAVAN_MASTER && id != ID_CARAVAN_GUARD;
    }

    /** The names by pool id (the spawn egg's meta; the bestiary). Append with the pool. */
    public static final String[] NAMES = {EntityLinemanSC.NAME, EntityMarauderSC.NAME, com.sc.settle.npc.EntityTunerSC.NAME,
            com.sc.settle.npc.EntityWardenSC.NAME, EntityTrooperSC.NAME, EntityJammerSC.NAME, EntityHollowShellSC.NAME, EntityRepairDroneSC.NAME,
            EntityExecutionerSC.NAME, EntityNaniteSC.NAME, EntitySentryDroneSC.NAME, EntityMimicSC.NAME, EntityOverheatedSC.NAME, EntityWarlordSC.NAME,
            EntitySentinelSC.NAME, EntityEclipseSC.NAME, EntityPhantomSC.NAME, EntityLeechSC.NAME, EntityColossusSC.NAME, EntityStriderSC.NAME,
            EntityScrapperSC.NAME, EntityWaferCrabSC.NAME, EntityBreakdownWardenSC.NAME, EntityContainmentCoilSC.NAME,
            com.sc.settle.caravan.EntityCaravanMasterSC.NAME, com.sc.settle.caravan.EntityCaravanGuardSC.NAME};
    /**
     * The spawn egg's colours by pool id: {shell, spots}. Lineman grey-blue / its diode's yellow, Marauder mud / orange,
     * Tuner faded turquoise / its diodes' green, guard steel / the dome's turquoise; Ф3: Trooper dark red / crimson, Jammer
     * night blue / its screen's blue, Shell pale steel / cold white, Repair Drone olive / yellow, Executioner gunmetal / frost,
     * nanite black / green, sentry drone rust / orange, mimic machine grey / violet; Ф4: Overheated red-hot / steam white, Warlord
     * crimson / gold, Sentinel void black / violet, «Затмение» black / crimson, Phantom glass blue / pale, Leech cable black / copper,
     * Colossus quartzite white / grey, Strider gunmetal / arc blue, Scrapper rust / yellow, Wafer Crab teal / iridescent violet;
     * Ф5: the Breakdown Warden void black / the reactor's white-violet, the coil copper / its beam's blue, the caravan's master faded
     * turquoise / gold, its guard steel / the dome's turquoise (the last three have no egg).
     */
    public static final int[][] EGG_COLORS = {{0x8A9BA8, 0xFFE040}, {0x5A4A3A, 0xFF9A30}, {0x6FA8A2, 0x7CFF9A}, {0x4A5560, 0x3CD2C8},
            {0x3A2026, 0xFF4040}, {0x2A3348, 0x4A7CFF}, {0xB8C4CC, 0xFFFFFF}, {0x8C8C7A, 0xFFE040}, {0x4A4F5A, 0x40E0FF}, {0x30343A, 0x50FF70},
            {0x6A5A4A, 0xFF9A30}, {0x777777, 0xB060FF}, {0xC84020, 0xF0F0F0}, {0x7A1020, 0xFFC040}, {0x101018, 0x8040FF}, {0x0A0A0E, 0xFF2040},
            {0x9AC8E8, 0xE8F4FF}, {0x202020, 0xD08040}, {0xE0E0E8, 0x9A9AA8}, {0x4A4F5A, 0x60B0FF}, {0x7A5A3A, 0xFFE040}, {0x3FA8A0, 0xC080FF},
            {0x0C0A14, 0xE0C8FF}, {0xB87333, 0x60C0FF}, {0x6FA8A2, 0xFFC040}, {0x4A5560, 0x3CD2C8}};

    /** The entity name of pool id `id` ("lineman"), null for an unknown id. */
    public static String nameOf(int id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : null;
    }
}
