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
    }

    /** The names by pool id (the spawn egg's meta; the bestiary). Append with the pool. */
    public static final String[] NAMES = {EntityLinemanSC.NAME, EntityMarauderSC.NAME, com.sc.settle.npc.EntityTunerSC.NAME,
            com.sc.settle.npc.EntityWardenSC.NAME, EntityTrooperSC.NAME, EntityJammerSC.NAME, EntityHollowShellSC.NAME, EntityRepairDroneSC.NAME,
            EntityExecutionerSC.NAME, EntityNaniteSC.NAME, EntitySentryDroneSC.NAME, EntityMimicSC.NAME};
    /**
     * The spawn egg's colours by pool id: {shell, spots}. Lineman grey-blue / its diode's yellow, Marauder mud / orange,
     * Tuner faded turquoise / its diodes' green, guard steel / the dome's turquoise; Ф3: Trooper dark red / crimson, Jammer
     * night blue / its screen's blue, Shell pale steel / cold white, Repair Drone olive / yellow, Executioner gunmetal / frost,
     * nanite black / green, sentry drone rust / orange, mimic machine grey / violet.
     */
    public static final int[][] EGG_COLORS = {{0x8A9BA8, 0xFFE040}, {0x5A4A3A, 0xFF9A30}, {0x6FA8A2, 0x7CFF9A}, {0x4A5560, 0x3CD2C8},
            {0x3A2026, 0xFF4040}, {0x2A3348, 0x4A7CFF}, {0xB8C4CC, 0xFFFFFF}, {0x8C8C7A, 0xFFE040}, {0x4A4F5A, 0x40E0FF}, {0x30343A, 0x50FF70},
            {0x6A5A4A, 0xFF9A30}, {0x777777, 0xB060FF}};

    /** The entity name of pool id `id` ("lineman"), null for an unknown id. */
    public static String nameOf(int id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : null;
    }
}
