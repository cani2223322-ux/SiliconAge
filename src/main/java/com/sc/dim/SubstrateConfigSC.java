package com.sc.dim;

import net.minecraftforge.common.config.Configuration;

/**
 * Подложка (Si-14), section "dimension" of the mod's .cfg (docs/plan-substrate-tech.md §2). Read from
 * ConfigSC.load with the same Configuration.
 * <p>
 * Two kinds of options, kept apart on purpose:
 * <ul>
 * <li><b>ids</b> (dimension, provider, biomes, on/off, keepLoaded) - private, behind getters: read once at start
 * and used to register the world on both sides. They are NOT ConfigSC's public static fields: ConfigSyncSC
 * overwrites every one of those with the server's value after a client joins, and by then the client has long
 * registered its provider and biomes with its own ids. ConfigSyncSC only ever looks at ConfigSC.class, and the
 * ids here aren't public fields at all - nothing can sync them. SubstrateNetSC only compares them with the
 * server's and warns (README: «id в конфигах клиента и сервера должны совпадать»).</li>
 * <li><b>rules</b> - every public static non-final primitive field of this class: what the server decides
 * (entry, bridge price, natives, ПЭ-1 on mobs, trophies). They travel to a joining client by SubstrateNetSC (the
 * same way ConfigSyncSC does it for ConfigSC) and go back to the client's own on leaving, so its handbook and
 * screens show the server's numbers. A new rule is just a new public static field.</li>
 * </ul>
 * Ids change only before the first visit: the world folder DIM&lt;id&gt;, JourneyMap's maps and the biome ids in
 * every saved chunk depend on them.
 */
public final class SubstrateConfigSC {

    public static final String CATEGORY = "dimension";

    // ------------------------------------------------------------------ ids (never synced, never changed in play)

    private static boolean enabled = true;
    /** ПК-1: negative, free in the user's pack (docs/idea-dimension-substrate.md §11): provider id = dimension id. */
    private static int dimensionId = -1414, providerId = -1414;
    /** ПК-3: explicit per biome; a taken id moves to the next free one and is written back here. */
    private static int biomeIdPlateaus = 240, biomeIdCanyons = 241;
    /** ПК-5: the world unloads ~20 s after the last player leaves (the server already keeps ~27 GC / GS worlds). */
    private static boolean keepLoaded = false;

    /** Kept from load() to write back a biome id moved off a taken slot (SubstrateSC.init). */
    private static Configuration config;

    // ------------------------------------------------------------------ rules (the server's travel to clients)

    /** ПВ-3: players who came in by anything but the bridge (or the Vehicles ship's Jump) are sent back. */
    public static boolean strictEntry = true;
    /** ПВ-9: dying in the Substrate, you wake up in this dimension (canRespawnHere = false). */
    public static int respawnDimension = 0;
    /** ПВ-6: the Space Bridge's price to / from the Substrate, on top of bridge.costMultiplier. */
    public static float bridgeCostMultiplier = 0.5F;
    /** ПВ-8: the Substrate's own creatures pass the bridge (even with the Mass Compensator) - off. */
    public static boolean nativesCrossBridge = false;
    /** ПБ-10: ПЭ-1 Point / Pulse work on the Substrate's armoured mobs (off: the old Slowness only). */
    public static boolean empMobTargets = true;
    /** ПБ-11: ПЭ-1 Siphon drains the Substrate's armoured mobs (supersedes docs/plan-pe1.md §4 «мобы не выкачиваются»). */
    public static boolean siphonMobs = true;
    /** ПТ-4: every trophy chance times this (0 = no trophies). */
    public static float gearDropMultiplier = 1F;
    /** Ф2, ПК-12: new settlements generate in land not explored yet (off: no new ones; those made stay, with their cores). */
    public static boolean villages = true;
    /** Ф2, ПГ-7: the guards attack after a third violation within 60 s (off - ★: warnings and «Доверие» only; a blow at a creature always provokes). */
    public static boolean guardsAggroOnViolations = false;

    private SubstrateConfigSC() {
    }

    public static void load(Configuration c) {
        config = c;
        String d = CATEGORY;
        c.setCategoryComment(d, "Si-14 Substrate, the Space Bridge's own dimension. Ids are read at start: change them only before anyone has been there"
                + " (the world folder, maps and every saved chunk keep them); a server and its clients need the same ids."
                + " / Подложка (Si-14) - своё измерение Космического моста. Id читаются при запуске: менять только до первого входа"
                + " (их помнят папка мира, карты и каждый сохранённый чанк); у сервера и клиентов id должны совпадать.");
        c.setCategoryRequiresMcRestart(d, true);
        enabled = c.getBoolean("enabled", d, true,
                "The dimension exists (off: no world, no biomes; its blocks and items stay registered) / Измерение есть (выкл.: ни мира, ни биомов; блоки и предметы остаются)");
        dimensionId = c.getInt("dimensionId", d, -1414, -100000, 100000,
                "Dimension id - negative only (positive ids are handed out to Galacticraft stations and AE2 cells); change only before the first visit"
                + " / Id измерения - только отрицательный (положительные раздаются станциям Galacticraft и ячейкам AE2); менять только до первого входа");
        providerId = c.getInt("providerId", d, -1414, -100000, 100000,
                "World provider type id (as Galacticraft, ExtraUtilities and Thaumcraft do: the same as the dimension id) / Id типа провайдера мира (как у GC, ExU, TC - равен id измерения)");
        biomeIdPlateaus = c.getInt("biomeIdPlateaus", d, 240, 40, 254,
                "Biome id of the Silicon Plateaus; a taken id moves to the next free one and is written here / Id биома «Кремниевые плато»; занятый сдвигается на следующий свободный и записывается сюда");
        biomeIdCanyons = c.getInt("biomeIdCanyons", d, 241, 40, 254,
                "Biome id of the Scribe Canyons (the same rule) / Id биома «Скрайб-каньоны» (то же правило)");
        keepLoaded = c.getBoolean("keepLoaded", d, false,
                "Keep the world loaded with nobody there (chunk loaders keep their chunks anyway) / Держать мир загруженным без игроков (загрузчики чанков держат свои чанки в любом случае)");
        strictEntry = c.getBoolean("strictEntry", d, true,
                "Only the Space Bridge (and the Vehicles ship's Jump) lets players in; anything else sends them back; the nether portal doesn't work there"
                + " / Войти можно только Космическим мостом (и «Прыжком» корабля Vehicles); остальное отправляет назад; адский портал там не работает");
        respawnDimension = c.getInt("respawnDimension", d, 0, -100000, 100000,
                "Dying in the Substrate, players wake up in this dimension (an unknown id: the Overworld) / Погибнув в Подложке, игрок возрождается в этом измерении (неизвестное - Верхний мир)");
        bridgeCostMultiplier = num(c, "bridgeCostMultiplier", d, 0.5F, 0.1F, 10F,
                "The Space Bridge's EU and gases to or from the Substrate, times this (on top of bridge.costMultiplier) / Множитель EU и газов Космического моста в Подложку и из неё (поверх bridge.costMultiplier)");
        nativesCrossBridge = c.getBoolean("nativesCrossBridge", d, false,
                "The Substrate's creatures may pass a bridge out of it (with the Mass Compensator) / Существа Подложки проходят через мост (с Компенсатором массы)");
        empMobTargets = c.getBoolean("empMobTargets", d, true,
                "PE-1 Point and Pulse suppress the armour of the Substrate's mobs (off: they only slow them, as before) / ПЭ-1 «Точка» и «Импульс» подавляют броню мобов Подложки (выкл.: только замедляют, как раньше)");
        siphonMobs = c.getBoolean("siphonMobs", d, true,
                "PE-1 Siphon drains the armour of the Substrate's mobs / ПЭ-1 «Сифон» выкачивает броню мобов Подложки");
        gearDropMultiplier = num(c, "gearDropMultiplier", d, 1F, 0F, 10F,
                "Every trophy chance (pieces of armour and weapons off the Substrate's mobs), times this / Множитель шансов трофеев (части брони и оружие с мобов Подложки)");
        villages = c.getBoolean("villages", d, true,
                "Settlements of the Tuners generate in land not explored yet (off: no new ones; those already made stay)"
                + " / Поселения Наладчиков появляются в ещё не открытых землях (выкл.: новых нет; уже созданные остаются)");
        guardsAggroOnViolations = c.getBoolean("guardsAggroOnViolations", d, false,
                "A settlement's guards attack a player after a third violation within 60 s (off: warnings and reputation only; hitting a settler or a guard always makes them attack)"
                + " / Стражи поселения нападают после третьего нарушения за 60 с (выкл.: только предупреждения и репутация; удар по жителю или стражу - нападение всегда)");
    }

    /** As ConfigSC.num: Forge clamps by comparisons and NaN passes them - NaN / infinity in the file: the default. */
    private static float num(Configuration c, String name, String category, float def, float min, float max, String comment) {
        float v = c.getFloat(name, category, def, min, max, comment);
        return Float.isNaN(v) || Float.isInfinite(v) ? def : v;
    }

    // ------------------------------------------------------------------ ids: read-only

    public static boolean enabled() {
        return enabled;
    }

    public static int dimensionId() {
        return dimensionId;
    }

    public static int providerId() {
        return providerId;
    }

    public static int biomeIdPlateaus() {
        return biomeIdPlateaus;
    }

    public static int biomeIdCanyons() {
        return biomeIdCanyons;
    }

    public static boolean keepLoaded() {
        return keepLoaded;
    }

    /**
     * SubstrateSC.init: a configured biome id was taken - the one actually used is written back (the next start,
     * and every chunk already saved with it, must find the same biome there). @return whether the file was saved
     */
    static boolean writeBiomeId(String key, int id) {
        if (config == null) {
            return false;
        }
        try {
            config.get(CATEGORY, key, id).set(id);
            config.save();
            if ("biomeIdPlateaus".equals(key)) {
                biomeIdPlateaus = id;
            } else if ("biomeIdCanyons".equals(key)) {
                biomeIdCanyons = id;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
