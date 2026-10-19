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
    /**
     * Ф4 (docs/plan-substrate-tech-f4.md §2.1, ПК-17): the Quartzite Ridges, the Oxide Fields and the Rift - the slots kept for
     * them since Ф1 (242..244); the same rule (a taken id moves to the next free one and is written back).
     */
    private static int biomeIdRidges = 242, biomeIdOxide = 243, biomeIdRift = 244;
    /** ПК-5: the world unloads ~20 s after the last player leaves (the server already keeps ~27 GC / GS worlds). */
    private static boolean keepLoaded = false;
    /**
     * Ф5 (docs/plan-substrate-tech-f5.md §2.1, ПК-22): the Substrate's own sky renderer (stars as a circuit grid, a dim ring on the
     * horizon) - each client's own look, never the server's (so not a rule: a private field, read at start); off by default, and
     * off whatever it says while ShadersModCore is there (its shaders draw the sky themselves).
     */
    private static boolean customSky = false;

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
    /**
     * Ф3, ПБ-17 (docs/plan-substrate-tech-f3.md §2.1): the Substrate's mobs use the ПЭ-1 - one Jammer in 50 carries one, the
     * City's Commandant fires it at the hostiles (off - and with weapons.pe1.enabled or affectsPlayers off - no mob carries a
     * ПЭ-1: a carrier gets the Ion Cutter instead, the Commandant only its blade).
     */
    public static boolean mobsUseEmp = true;
    /**
     * Ф4, ПФ-1 (docs/plan-substrate-tech-f4.md §2.1): the Substrate's own weather - the EMP storm (20% a game day, 3..5 min, a
     * pulse every 20..40 s suppressing a piece of armour of players and mobs under the open sky). Off: no storm ever begins
     * (one running ends at its next check).
     */
    public static boolean empStorms = true;
    /**
     * Ф4, ПН-1: the Renegades raid the settlements at night (a player of «Знакомый» or above near, 10% a night, at most once in
     * 5 days a settlement, three waves). Off: no raid begins.
     */
    public static boolean raids = true;
    /**
     * Ф5 (docs/plan-substrate-tech-f5.md §2.1, ПВ-13): the Substrate shows in a Space Bridge's list, and a bridge opens an end there,
     * only for a player who has used a «Навигационная запись Si-14» (per player, by UUID; each LAN friend his own). Off (★): it is
     * there for everyone, as in Ф1..Ф4.
     */
    public static boolean requireSignal = false;
    /**
     * Ф5, ПН-12: a caravan - a trader with two guards - comes to a settlement every 2 game days when a player is near it; 40% are
     * ambushed on the way. Off: no caravans.
     */
    public static boolean caravans = true;

    // ------------------------------------------------------------------ Ф6: the Tuners' Embassies (section "embassy", rules too)

    /**
     * Ф6 (docs/plan-substrate-tech-f6.md §2.1, ПК-26): the Tuners' Embassies - rare big cities in the Overworld with the Great Ring
     * and flights to the Substrate and back (section CATEGORY_EMBASSY of the same file). Off: no new embassy generates and those made
     * stand without protection, flights or a roster (their records stay). They are rules - the server's, sent to a joining client
     * with the others (its handbook shows the server's numbers).
     */
    public static boolean embassies = true;
    /** ПЕ-1: one embassy at most to an embassyGrid x embassyGrid region (blocks); its point within a quarter of it of the middle. TODO(balance) */
    public static int embassyGrid = 2048;
    /** ПЕ-1: the chance, %, that a region has one (before the biomes and the vanilla structures). TODO(balance): /scembassy list on the user's seed */
    public static int embassyChance = 45;
    /** ПЕ-1: no embassy closer than this to the world's spawn (blocks). */
    public static int embassySpawnMin = 1500;
    /** ПЕ-2: the guaranteed first embassy stands this far from the spawn (blocks), between the two. */
    public static int embassyRingMin = 1300, embassyRingMax = 2500;

    /** The section of the embassies' rules. */
    public static final String CATEGORY_EMBASSY = "embassy";

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
                "Biome id of the Silicon Plateaus; a taken id moves to the next free one and is written here / Id биома „Кремниевые плато“; занятый сдвигается на следующий свободный и записывается сюда");
        biomeIdCanyons = c.getInt("biomeIdCanyons", d, 241, 40, 254,
                "Biome id of the Scribe Canyons (the same rule) / Id биома „Скрайб-каньоны“ (то же правило)");
        biomeIdRidges = c.getInt("biomeIdRidges", d, 242, 40, 254,
                "Biome id of the Quartzite Ridges (the same rule) / Id биома „Кварцитовые гряды“ (то же правило)");
        biomeIdOxide = c.getInt("biomeIdOxide", d, 243, 40, 254,
                "Biome id of the Oxide Fields (the same rule) / Id биома „Окисные поля“ (то же правило)");
        biomeIdRift = c.getInt("biomeIdRift", d, 244, 40, 254,
                "Biome id of the Rift (the same rule) / Id биома „Разлом“ (то же правило)");
        keepLoaded = c.getBoolean("keepLoaded", d, false,
                "Keep the world loaded with nobody there (chunk loaders keep their chunks anyway) / Держать мир загруженным без игроков (загрузчики чанков держат свои чанки в любом случае)");
        customSky = c.getBoolean("customSky", d, false,
                "This client draws the Substrate's own sky - stars as a circuit grid, a dim ring on the horizon (each client's own choice; off by itself"
                + " while ShadersModCore is installed) / Этот клиент рисует своё небо Подложки - звёзды сеткой микросхемы, тусклое кольцо у горизонта"
                + " (у каждого клиента своё; само выключается при ShadersModCore)");
        strictEntry = c.getBoolean("strictEntry", d, true,
                "Only the Space Bridge (and the Vehicles ship's Jump) lets players in; anything else sends them back; the nether portal doesn't work there"
                + " / Войти можно только Космическим мостом (и „Прыжком“ корабля Vehicles); остальное отправляет назад; адский портал там не работает");
        respawnDimension = c.getInt("respawnDimension", d, 0, -100000, 100000,
                "Dying in the Substrate, players wake up in this dimension (an unknown id: the Overworld) / Погибнув в Подложке, игрок возрождается в этом измерении (неизвестное - Верхний мир)");
        bridgeCostMultiplier = num(c, "bridgeCostMultiplier", d, 0.5F, 0.1F, 10F,
                "The Space Bridge's EU and gases to or from the Substrate, times this (on top of bridge.costMultiplier) / Множитель EU и газов Космического моста в Подложку и из неё (поверх bridge.costMultiplier)");
        nativesCrossBridge = c.getBoolean("nativesCrossBridge", d, false,
                "The Substrate's creatures may pass a bridge out of it (with the Mass Compensator) / Существа Подложки проходят через мост (с Компенсатором массы)");
        empMobTargets = c.getBoolean("empMobTargets", d, true,
                "PE-1 Point and Pulse suppress the armour of the Substrate's mobs (off: they only slow them, as before) / ПЭ-1 „Точка“ и „Импульс“ подавляют броню мобов Подложки (выкл.: только замедляют, как раньше)");
        siphonMobs = c.getBoolean("siphonMobs", d, true,
                "PE-1 Siphon drains the armour of the Substrate's mobs / ПЭ-1 „Сифон“ выкачивает броню мобов Подложки");
        gearDropMultiplier = num(c, "gearDropMultiplier", d, 1F, 0F, 10F,
                "Every trophy chance (pieces of armour and weapons off the Substrate's mobs), times this / Множитель шансов трофеев (части брони и оружие с мобов Подложки)");
        villages = c.getBoolean("villages", d, true,
                "Settlements of the Tuners generate in land not explored yet (off: no new ones; those already made stay)"
                + " / Поселения Наладчиков появляются в ещё не открытых землях (выкл.: новых нет; уже созданные остаются)");
        guardsAggroOnViolations = c.getBoolean("guardsAggroOnViolations", d, false,
                "A settlement's guards attack a player after a third violation within 60 s (off: warnings and reputation only; hitting a settler or a guard always makes them attack)"
                + " / Стражи поселения нападают после третьего нарушения за 60 с (выкл.: только предупреждения и репутация; удар по жителю или стражу - нападение всегда)");
        mobsUseEmp = c.getBoolean("mobsUseEmp", d, true,
                "The Substrate's mobs use the PE-1: one Jammer in 50 carries one, a City's Commandant fires it at hostiles (off: no mob ever holds a PE-1)"
                + " / Мобы Подложки применяют ПЭ-1: он есть у 1 из 50 Глушителей, Комендант города стреляет им по враждебным (выкл.: ПЭ-1 ни у одного моба)");
        empStorms = c.getBoolean("empStorms", d, true,
                "EMP storms, the Substrate's own weather: on 20% of the game days for 3-5 minutes; every 20-40 s a pulse suppresses a piece of armour of players"
                + " and mobs under the open sky (off: no storm begins) / ЭМИ-бури, своя погода Подложки: в 20% игровых суток на 3-5 минут; каждые 20-40 с"
                + " импульс подавляет часть брони у игроков и мобов под открытым небом (выкл.: бурь нет)");
        raids = c.getBoolean("raids", d, true,
                "The Renegades raid a settlement at night when a player it knows (\"Acquaintance\" or better) is near: 10% a night, at most once in 5 days,"
                + " three waves (off: no raids) / Отступники ночью нападают на поселение, если рядом игрок с „Доверием“ от „Знакомого“: 10% за ночь, не чаще"
                + " раза в 5 дней, три волны (выкл.: налётов нет)");
        requireSignal = c.getBoolean("requireSignal", d, false,
                "The Substrate shows in a Space Bridge's list (and a bridge opens an end there) only for a player who has used a \"Si-14 Navigation Record\""
                + " (off: for everyone) / Подложка видна в списке Космического моста (и мост открывает туда конец) только игроку, который применил"
                + " „Навигационную запись Si-14“ (выкл.: всем)");
        caravans = c.getBoolean("caravans", d, true,
                "Every 2 game days a caravan - a trader and two guards - comes to a settlement a player is near; 40% are ambushed on the way;"
                + " beating the ambush off gives reputation and a rare deal (off: no caravans) / Раз в 2 игровых дня к поселению, у которого есть"
                + " игрок, идёт караван - торговец и два стража; 40% попадают в засаду; отбитая засада - „Доверие“ и редкая сделка (выкл.: караванов нет)");
        loadEmbassies(c);
    }

    /** Ф6 (docs/plan-substrate-tech-f6.md §2.1): the section "embassy". The placement numbers matter only for land not generated yet. */
    private static void loadEmbassies(Configuration c) {
        String e = CATEGORY_EMBASSY;
        c.setCategoryComment(e, "The Tuners' Embassies: rare big cities of the Tuners in the Overworld with the Great Ring - flights to the Substrate"
                + " and back for Credit Chips. The placement numbers change only land not generated yet."
                + " / Посольства Наладчиков: редкие большие города Наладчиков в Верхнем мире с Большим кольцом - рейсы в Подложку и обратно"
                + " за Кредит-чипы. Числа размещения меняют только ещё не созданную землю.");
        c.setCategoryRequiresWorldRestart(e, true);
        embassies = c.getBoolean("embassies", e, true,
                "Embassies generate in the Overworld (only in land not explored yet) and work: protection, settlers, flights (off: no new ones;"
                + " those made stand empty) / Посольства появляются в Верхнем мире (только в ещё не открытой земле) и работают: защита, жители,"
                + " рейсы (выкл.: новых нет; созданные стоят пустыми)");
        embassyGrid = c.getInt("embassyGrid", e, 2048, 1024, 16384,
                "One embassy at most to a region of this size, blocks (two embassies are at least half of it apart)"
                + " / Не больше одного посольства на регион такого размера, блоки (два посольства - не ближе половины его)");
        embassyChance = c.getInt("embassyChance", e, 45, 0, 100,
                "The chance, %, that a region has an embassy (then the land must suit it: flat, dry and open - plains, savanna, desert)"
                + " / Шанс посольства в регионе, % (затем земля должна подойти: ровная, сухая, открытая - равнины, саванна, пустыня)");
        embassySpawnMin = c.getInt("embassySpawnMin", e, 1500, 0, 1000000,
                "No embassy closer than this to the world's spawn, blocks (the guaranteed first one aside)"
                + " / Посольства не ближе этого к точке появления мира, блоки (кроме гарантированного первого)");
        embassyRingMin = c.getInt("embassyRingMin", e, 1300, 256, 1000000,
                "The guaranteed first embassy stands at least this far from the world's spawn, blocks"
                + " / Гарантированное первое посольство - не ближе этого к точке появления мира, блоки");
        embassyRingMax = c.getInt("embassyRingMax", e, 2500, 512, 1000000,
                "...and at most this far, blocks (at least embassyRingMin + 256) / ...и не дальше этого, блоки (не меньше embassyRingMin + 256)");
        if (embassyRingMax < embassyRingMin + 256) {
            embassyRingMax = embassyRingMin + 256;               // a ring too thin to hold a city: widened, never an error
        }
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

    /** Ф4: the Quartzite Ridges' biome id. */
    public static int biomeIdRidges() {
        return biomeIdRidges;
    }

    /** Ф4: the Oxide Fields' biome id. */
    public static int biomeIdOxide() {
        return biomeIdOxide;
    }

    /** Ф4: the Rift's biome id. */
    public static int biomeIdRift() {
        return biomeIdRift;
    }

    public static boolean keepLoaded() {
        return keepLoaded;
    }

    /** Ф5: this client asked for the Substrate's own sky (SubstrateSkySC also checks ShadersModCore). */
    public static boolean customSky() {
        return customSky;
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
            } else if ("biomeIdRidges".equals(key)) {
                biomeIdRidges = id;
            } else if ("biomeIdOxide".equals(key)) {
                biomeIdOxide = id;
            } else if ("biomeIdRift".equals(key)) {
                biomeIdRift = id;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
