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
    }

    /** The names by pool id (the spawn egg's meta; the bestiary). Append with the pool. */
    public static final String[] NAMES = {EntityLinemanSC.NAME, EntityMarauderSC.NAME, com.sc.settle.npc.EntityTunerSC.NAME,
            com.sc.settle.npc.EntityWardenSC.NAME};
    /**
     * The spawn egg's colours by pool id: {shell, spots}. Lineman grey-blue / its diode's yellow, Marauder mud / orange,
     * Tuner faded turquoise / its diodes' green, guard steel / the dome's turquoise.
     */
    public static final int[][] EGG_COLORS = {{0x8A9BA8, 0xFFE040}, {0x5A4A3A, 0xFF9A30}, {0x6FA8A2, 0x7CFF9A}, {0x4A5560, 0x3CD2C8}};

    /** The entity name of pool id `id` ("lineman"), null for an unknown id. */
    public static String nameOf(int id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : null;
    }
}
