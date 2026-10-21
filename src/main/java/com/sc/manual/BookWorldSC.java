package com.sc.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockRuinSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.block.BlockShowcaseSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.EntryRulesSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateOreTableSC;
import com.sc.dim.SubstrateRadiationSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.dim.SubstrateWildGenSC;
import com.sc.dim.SubstrateWildTerrainSC;
import com.sc.dim.WorldProviderSubstrateSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpTooltipSC;
import com.sc.energy.GeneratorType;
import com.sc.hazard.BridgeEchoSC;
import com.sc.hazard.RaidRulesSC;
import com.sc.hazard.StormRulesSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityColossusSC;
import com.sc.mob.EntityEclipseSC;
import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityJammerSC;
import com.sc.mob.EntityLeechSC;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityMimicSC;
import com.sc.mob.EntityNaniteSC;
import com.sc.mob.EntityOverheatedSC;
import com.sc.mob.EntityPhantomSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.EntityScrapperSC;
import com.sc.mob.EntitySentinelSC;
import com.sc.mob.EntitySentryDroneSC;
import com.sc.mob.EntityStriderSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.EntityWaferCrabSC;
import com.sc.mob.EntityWarlordSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobCombatSC;
import com.sc.mob.MobDropsSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.ScrapperSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.mob.TrophyTableSC;
import com.sc.mob.WardenRulesSC;
import com.sc.ruin.CampBlocksSC;
import com.sc.ruin.CampLayoutSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.RuinWorldSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.CrumbLimitSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.ViolationRulesSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.npc.WardenSuitSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.settle.travel.TravelRulesSC;
import com.sc.tileentity.TileEntityArcCrackSC;
import com.sc.tileentity.TileEntityStolenSolarSC;
import com.sc.tileentity.TileEntitySubstrateVentSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;
import com.sc.util.SiliconMaterial;
import com.sc.util.WeaponType;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The handbook's chapter «Подложка» (BookChapter.WORLD, docs/plan-substrate-tech.md §7.2): «Подложка (Si-14)» (the
 * way in, the price, the world's rules, multiplayer), «Мир-микросхема» (the grid, biomes, layers, blocks, ores),
 * «Бронированные враги» (the reserve, the shield, ПЭ-1 on mobs), the bestiary («Обходчик», «Мародёр») and «Трофеи и
 * добыча». Every number comes from the code and the config (the server's rules when playing on one - SubstrateNetSC
 * rebuilds the book with them), never from the text. G on the Substrate's blocks, the trophies, the loot and the
 * spawn eggs opens these pages.
 * <p>
 * Ф2 «Поселения» (docs/plan-substrate-tech-f2.md §10.2) adds at the chapter's end «Поселения Наладчиков» (the sizes of
 * SettlementSizeSC, the plan, the core, what a guest may not do), «Наладчики» (NpcKindSC's professions, the day),
 * «Терминал обмена и КЧ» (TradeTableSC's deals, ReputationSC's multipliers, TradeRulesSC's limits, ContractTableSC's
 * contracts), «Доверие» (ReputationSC, ViolationRulesSC), «Стражи купола» (the ranks, MobCombatSC's caps, the
 * provocation) and «Причалы и возврат» (TravelRulesSC). G on the core, the showcases, the brick, the dock, the chip,
 * the capsule, the map, the Stolen Cargo and both eggs opens them.
 * <p>
 * The bestiary's own numbers (health, reserve, weapons, drop chances) live in package M's classes; they are read by
 * name (stat) so this chapter builds before M's constants exist - the plan's value until then, and the self-test
 * lists every one still on it (unresolved). TODO(D, after M): direct references. Package N's creatures (the Tuner's
 * day, the guards' health and reserve) are read the same way.
 * <p>
 * Ф3 «Броня и ЭМИ» (docs/plan-substrate-tech-f3.md §10.2) adds at the chapter's end «ПЭ-1 в руках врагов и «Пробой»»
 * (EmpMobGunSC's warnings, the personal screen, the reactive machines, BreakdownDamageSC), the bestiary of Ф3
 * («Штурмовик и Глушитель», «Пустые оболочки и дроны», «Экзо-палач», «Рой, дрон-охранник, мимик»), «Элита» (EliteModSC's
 * table), «Мёртвые Узлы и литографии» (RuinLayoutSC, the loot, the ruin blocks) and «Восстановление трофеев»
 * (TrophyRestoreSC, the Armourer's services of TradeTableSC); and extends «Наладчики» (the three professions), «Стражи
 * купола» (the Captain, the Commandant), «Терминал обмена» (the services) and «Трофеи и добыча» (the trophies SHOWN now, the
 * new loot). The numbers of packages M / X / A / S / T that the core doesn't fix are read by name too (stat, weight, member). G on
 * the eggs, the new loot and the ruin blocks opens these pages.
 * <p>
 * Ф4 «Опасный мир» (docs/plan-substrate-tech-f4.md §10.2) adds at the chapter's end «Гряды, Окисные поля, Разлом» (the cells
 * of SubstrateCellsSC, the terrain's numbers of SubstrateWildTerrainSC, «the land of Ф1..Ф3 stays the plateaus»), «Фоновая
 * радиация» (SubstrateRadiationSC, the protection), «Фумаролы, трещины, жеоды, гейзеры» (the vents' tiles, SubstrateWildGenSC),
 * «ЭМИ-буря» (StormRulesSC, the cover), «Мгла и Выброс», «Налёты» (RaidRulesSC's waves and rewards), «Эхо моста»
 * (BridgeEchoSC), «Лагеря Отступников» (CampLayoutSC, the Stolen Field and panels, the Warlord, the safe, RuinWorldSC's return),
 * «Хранилища и Часовой» (VaultLayoutSC, the Sentinel, the Fragments, the Pelengator), the bestiary of Ф4 («Перегретый»,
 * «Затмение», «Наводка, Токоед, Колосс, Ходок», «Утилизатор и Метка гибели», «Подложник») and «Крупицы сингулярности»
 * (CrumbLimitSC, the Sealed Crumbs); and extends «Мир-микросхема» (the three biomes, the monocrystal), «Трофеи и добыча» (the
 * Singular trophies, the storm's multiplier, the new loot), «Терминал обмена» (the raid's closed trade, the Crumbs' cap) and
 * the first article (the three biome ids). The numbers the core fixes (the stubs' constants) are read directly; what packages
 * W / S / M / N / C keep beyond them is read by name (stat, weight) as before. G on every new block, item, egg and loot meta
 * opens these pages.
 * <p>
 * Ф5 «Финал» (docs/plan-substrate-tech-f5.md §11.4) adds at the chapter's end the thirteen articles of BookFinaleSC (F5_ARTICLES:
 * «Литографы», «Эпицентр Пробоя», «Смотритель Пробоя» with its Containment Coils, «Добыча Смотрителя», «Журналы Литографа»,
 * «Обломки моста Литографов», «Сигнал Литографов», «Караваны», «Схемы окраски и купол», «Модуль Литографов», «Навигационная
 * запись Si-14», «Сингулярный прогресс в Подложке», «Небо Подложки») - every number from the core's classes (WardenRulesSC,
 * ArenaLayoutSC, JournalSC, WreckLayoutSC, SignalRulesSC, CaravanRulesSC, FinaleRulesSC), what packages A / B / J / K / P keep
 * beyond them by name; and extends «Подложка (Si-14)» (requireSignal, caravans, customSky), «Трофеи и добыча» (the ПЭ-1М, the
 * Warden's roll), «Терминал обмена» (the finale's deals), «Крупицы» (the Warden's beyond the cap, the Signal's cache), «Элита» (the
 * Arena Coordinator), «Хранилища» (the Journals, the Fragments into a Key) and «Восстановление трофеев» (the ПЭ-1М).
 * <p>
 * Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md §9.2) adds at the chapter's end the six articles of BookEmbassySC
 * (F6_ARTICLES: «Посольства Наладчиков», «Город посольства», «Большое кольцо: рейсы», «Рейсы домой», «Лавка и Интендант», «Правила
 * посольств») - every number from the core's EmbassySiteSC, EmbassyLayoutSC, EmbassyRulesSC; and extends «Подложка (Si-14)» (the
 * section embassy), «Поселения» (the sizes table - the Substrate's three; the Dispatcher at the dock), «Наладчики» (the Dispatcher, the
 * Quartermaster), «Терминал обмена» (the Quartermaster's deals are an embassy's; the Ambassador's contracts have their own article),
 * «Доверие» (the embassies' ceiling 599) and «Причалы» (the Vokzal's zone 13, the Dispatcher). G on the Direction Finder, the Great
 * Ring's core and the showcase metas 7..9 opens these pages.
 */
public final class BookWorldSC {

    /** The rock's metas by name, SubstrateBlocksSC's order (tile.siliconage.substrateRock.<name>.name, sc.tooltip.substrateRock.<name>). */
    public static final String[] ROCK_NAMES = {"crystallite", "oxideViolet", "oxideBlue", "oxideGold", "oxideGreen", "copperTrace", "traceNode",
            "quartzTurf", "settlementBrick", "monocrystal"};
    /** Ф2: the Settlement Brick is the rock's meta 8 (SubstrateBlocksSC.SETTLEMENT_BRICK, package S; docs/plan-substrate-tech-f2.md §2.2). */
    public static final int BRICK = 8;
    /** The combat loot by meta (ItemCombatLootSC.LootKind, appended only): item.siliconage.combatLoot.<name>.name, sc.tooltip.loot.<name>. */
    public static final String[] LOOT_NAMES = {"shellFirmware", "nanoScrap", "stolenCargo", "quantumScrap", "exoScrap", "naniteDust", "shieldCore",
            "boiledLoop", "inductionThread", "plasmaCondensate", "singularScrap", "naturalWafer"};
    /** Ф2: the Stolen Cargo is meta 2 (LootKind.STOLEN_CARGO, package T). */
    public static final int LOOT_FIRMWARE = 0, LOOT_NANO_SCRAP = 1, LOOT_STOLEN_CARGO = 2;
    /** Ф3: the core's LootKind QUANTUM_SCRAP..SHIELD_CORE (docs/plan-substrate-tech-f3.md §2.2). */
    public static final int LOOT_QUANTUM_SCRAP = 3, LOOT_EXO_SCRAP = 4, LOOT_NANITE_DUST = 5, LOOT_SHIELD_CORE = 6;
    /** Ф4: the core's LootKind BOILED_LOOP..NATURAL_WAFER (docs/plan-substrate-tech-f4.md §2.2). */
    public static final int LOOT_BOILED_LOOP = 7, LOOT_INDUCTION_THREAD = 8, LOOT_PLASMA_CONDENSATE = 9, LOOT_SINGULAR_SCRAP = 10, LOOT_NATURAL_WAFER = 11;
    /** Ф4: the ruin block's metas package C appends (BlockRuinSC.NAMES; docs/plan-substrate-tech-f4.md §2.2). */
    public static final int RUIN_VAULT_PLATING = 4, RUIN_VAULT_LAMP = 5, RUIN_CAMP_TARP = 6;
    /** Ф4: their names - BlockRuinSC.nameOf answers them once package C has appended them. */
    static final String[] RUIN_F4_NAMES = {"vaultPlating", "vaultLamp", "campTarp"};
    /** Ф5: the bridge wrecks' Burnt Coil package J appended to the ruin block (BlockRuinSC.NAMES; docs/plan-substrate-tech-f5.md §2.2). */
    public static final int RUIN_BURNT_COIL = BlockRuinSC.BURNT_COIL;
    static final String RUIN_F5_NAME = "burntCoil";
    /** The trophy's lang key by meta (item.siliconage.trophy.<key>.name; the metas of TrophyTableSC). */
    public static final String[] TROPHY_KEYS = trophyKeys();

    /** Package M's classes the bestiary reads its numbers from. */
    private static final String LINEMAN = "com.sc.mob.EntityLinemanSC", MARAUDER = "com.sc.mob.EntityMarauderSC", DROPS = "com.sc.mob.MobDropsSC",
            BEAM = "com.sc.mob.ai.EntityAIBeamAttackSC";
    /** Ф2: package N's creatures (their numbers by name, as package M's: the plan's value until N's constants exist). */
    private static final String TUNER = "com.sc.settle.npc.EntityTunerSC", WARDEN = "com.sc.settle.npc.EntityWardenSC";
    /**
     * Ф3: the classes of packages M (the armoured mobs, the census and weights, the trophy table), X («noise», the ruins), A (the
     * elites' rhythm) and T (the services' output kinds) whose numbers the core doesn't fix - read by name like the above.
     */
    private static final String TROOPER = "com.sc.mob.EntityTrooperSC", JAMMER = "com.sc.mob.EntityJammerSC", SHELL = "com.sc.mob.EntityHollowShellSC",
            REPAIR_DRONE = "com.sc.mob.EntityRepairDroneSC", EXECUTIONER = "com.sc.mob.EntityExecutionerSC", NANITE = "com.sc.mob.EntityNaniteSC",
            SENTRY_DRONE = "com.sc.mob.EntitySentryDroneSC", MIMIC = "com.sc.mob.EntityMimicSC", RULES = "com.sc.mob.SpawnRulesSC",
            TROPHIES = "com.sc.mob.TrophyTableSC", ELITE_MOD = "com.sc.mob.EliteModSC", RUIN_LOOT = "com.sc.ruin.RuinLootSC", LITHO = "com.sc.ruin.MapGenLithographSC",
            RUIN_LAYOUT = "com.sc.ruin.RuinLayoutSC", RUIN_BLOCK = "com.sc.block.BlockRuinSC", OFFER = "com.sc.settle.trade.TradeOfferSC";
    /**
     * Ф4: the classes of packages W / M / N / C whose numbers the core's stubs don't fix (the creatures' weapons and drops, the
     * monocrystal's drop, the camps' and Vaults' chests) - read by name like the above; the stubs' own constants are read directly.
     */
    private static final String OVERHEATED = "com.sc.mob.EntityOverheatedSC", WARLORD = "com.sc.mob.EntityWarlordSC", SENTINEL = "com.sc.mob.EntitySentinelSC",
            ECLIPSE = "com.sc.mob.EntityEclipseSC", PHANTOM = "com.sc.mob.EntityPhantomSC", LEECH = "com.sc.mob.EntityLeechSC",
            COLOSSUS = "com.sc.mob.EntityColossusSC", STRIDER = "com.sc.mob.EntityStriderSC", SCRAPPER = "com.sc.mob.EntityScrapperSC",
            CRAB = "com.sc.mob.EntityWaferCrabSC", ROCK_BLOCK = "com.sc.block.BlockSubstrateRockSC";
    /** Ф3 (docs/plan-substrate-tech-f3.md §2.3): the services' output kinds package T appends to TradeOfferSC (charge a piece, restore a trophy). */
    static final int OUT_CHARGE = (int) stat(OFFER, "OUT_CHARGE", 3F), OUT_RESTORE = (int) stat(OFFER, "OUT_RESTORE", 4F);
    /** A limit at least this big reads «без лимита» (a service without one, TODO(balance)). */
    private static final int NO_LIMIT = 1000;
    /** The trade article's price example: what the table's 16 becomes at each level. */
    private static final int PRICE_EXAMPLE = 16;
    private static final Set<String> UNRESOLVED = new TreeSet<String>();

    private BookWorldSC() {
    }

    static void add(List<BookEntry> list) {
        BookChapter c = BookChapter.WORLD;
        list.add(substrate(c));
        list.add(terrain(c));
        list.add(armoured(c));
        list.add(lineman(c));
        list.add(marauder(c));
        list.add(trophies(c));
        // Ф2 «Поселения» (docs/plan-substrate-tech-f2.md §10.2): at the chapter's end
        list.add(settlements(c));
        list.add(tuners(c));
        list.add(trade(c));
        list.add(trust(c));
        list.add(wardens(c));
        list.add(docks(c));
        // Ф3 «Броня и ЭМИ» (docs/plan-substrate-tech-f3.md §10.2): at the chapter's end
        list.add(mobEmp(c));
        list.add(renegades(c));
        list.add(shells(c));
        list.add(executioner(c));
        list.add(noise(c));
        list.add(elites(c));
        list.add(ruins(c));
        list.add(restore(c));
        // Ф4 «Опасный мир» (docs/plan-substrate-tech-f4.md §10.2): at the chapter's end
        list.add(biomes4(c));
        list.add(radiation(c));
        list.add(resources4(c));
        list.add(storm(c));
        list.add(haze(c));
        list.add(raids(c));
        list.add(echo(c));
        list.add(camps(c));
        list.add(vaults(c));
        list.add(overheated(c));
        list.add(eclipse(c));
        list.add(noise4(c));
        list.add(scrapper(c));
        list.add(fauna(c));
        list.add(crumbs(c));
        // Ф5 «Финал» (docs/plan-substrate-tech-f5.md §11.4): at the chapter's end
        BookFinaleSC.add(list, c);
        // Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md §9.2): at the chapter's end
        BookEmbassySC.add(list, c);
    }

    /** Ф3: the eight new articles, in the chapter's order (the self-test counts and checks them). */
    public static final String[] F3_ARTICLES = {"world.mobemp", "world.renegades", "world.shells", "world.executioner", "world.noise", "world.elites",
            "world.ruins", "world.restore"};
    /** Ф4: the fifteen new articles (§10.2's fourteen and the Overheated's bestiary page), in the chapter's order (SelfTestHazardSC). */
    public static final String[] F4_ARTICLES = {"world.biomes4", "world.radiation", "world.resources4", "world.storm", "world.haze", "world.raids",
            "world.echo", "world.camps", "world.vaults", "world.overheated", "world.eclipse", "world.noise4", "world.scrapper", "world.fauna", "world.crumbs"};
    /** Ф5: the thirteen new articles (§11.4), in the chapter's order (BookFinaleSC; SelfTestFinaleSC counts and checks them). */
    public static final String[] F5_ARTICLES = {"world.lithographers", "world.arena", "world.warden", "world.wardenLoot", "world.journals",
            "world.wrecks", "world.signal", "world.caravans", "world.cosmetics", "world.lithoModule", "world.navrecord", "world.progress", "world.sky"};
    /** Ф6: the six new articles (§9.2), in the chapter's order (BookEmbassySC; SelfTestEmbassySC counts and checks them). */
    public static final String[] F6_ARTICLES = {"world.embassies", "world.embassyCity", "world.flights", "world.flightsBack", "world.embassyTrade",
            "world.embassyRules"};

    /** The chapter's tile: Crystallite while the Substrate's blocks exist, else a compass. */
    static ItemStack icon() {
        ItemStack s = rock(SubstrateBlocksSC.CRYSTALLITE);
        return s != null ? s : new ItemStack(Items.compass);
    }

    // ------------------------------------------------------------------ «Подложка (Si-14)»

    private static BookEntry substrate(BookChapter c) {
        BookEntry e = new BookEntry("world.substrate", c, icon(), Lang.tr("sc.manual.world.substrate.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.substrate.title"))).addAll(BookContent.paras("sc.manual.world.about"));
        if (!SubstrateSC.active()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.off")));
        }

        // the way in (ПВ-1, ПВ-2)
        e.add(BookEl.head(Lang.tr("sc.manual.world.gethead"), com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1)));
        e.addAll(BookContent.paras("sc.manual.world.get", new Object[]{Lang.tr(WorldProviderSubstrateSC.NAME_KEY)}));
        if (!ConfigSC.bridgeCrossDimension) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.nocross")));
        }

        // the price (ПВ-6): the Space Bridge's burst, both multipliers, the shared anchor
        float mul = ConfigSC.bridgeCostMultiplier * SubstrateConfigSC.bridgeCostMultiplier;
        e.add(BookEl.head(Lang.tr("sc.manual.world.pricehead")));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.jump"), "EU");
        t.row(null, Lang.tr("sc.manual.world.t.noanchor"), BookContent.num(eu(BridgeMathSC.SPACE_BURST * BridgeMathSC.NO_ANCHOR_MUL, mul)));
        t.row(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), Lang.tr("sc.manual.world.t.anchor"),
                BookContent.num(eu(BridgeMathSC.SPACE_BURST, mul)));
        e.add(t);
        e.addAll(BookContent.paras("sc.manual.world.price", new Object[]{multiplier(SubstrateConfigSC.bridgeCostMultiplier),
                multiplier(ConfigSC.bridgeCostMultiplier)}, new Object[]{BridgeMathSC.NO_ANCHOR_MUL}));

        // the world's rules (ПВ-3, ПВ-7..ПВ-10)
        e.add(BookEl.head(Lang.tr("sc.manual.world.ruleshead")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.entry"),
                Lang.tr(SubstrateConfigSC.strictEntry ? "sc.manual.world.rule.entry.on" : "sc.manual.world.rule.entry.off")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.buffer"), Lang.tr("sc.manual.world.rule.buffer.desc", Lang.tr("potion.resistance"),
                Lang.trOr("potion.potency." + EntryRulesSC.BUFFER_AMPLIFIER, String.valueOf(EntryRulesSC.BUFFER_AMPLIFIER + 1)), sec(EntryRulesSC.BUFFER_TICKS))));
        e.add(BookEl.item(new ItemStack(Items.bed), Lang.tr("sc.manual.world.rule.bed"), Lang.tr("sc.manual.world.rule.bed.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.death"), Lang.tr("sc.manual.world.rule.death.desc", dimName(SubstrateSC.respawnDimension()))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.natives"),
                Lang.tr(SubstrateConfigSC.nativesCrossBridge ? "sc.manual.world.rule.natives.on" : "sc.manual.world.rule.natives.off")));

        // multiplayer (ПК-4): the ids here
        boolean on = SubstrateSC.active();
        e.add(BookEl.head(Lang.tr("sc.manual.world.mphead")));
        e.addAll(BookContent.paras("sc.manual.world.mp", null, new Object[]{on ? SubstrateSC.dimId() : SubstrateConfigSC.dimensionId(),
                on ? SubstrateSC.plateaus().biomeID : SubstrateConfigSC.biomeIdPlateaus(), on ? SubstrateSC.canyons().biomeID : SubstrateConfigSC.biomeIdCanyons()}));
        // Ф4 (ПК-17): the three new biomes' ids - the same on the server and every client
        e.add(BookEl.dim(Lang.tr("sc.manual.world.mp.f4", biomeId(BiomeSubstrateSC.RIDGES), biomeId(BiomeSubstrateSC.OXIDE), biomeId(BiomeSubstrateSC.RIFT))));
        // Ф4: the world's own dangers (ПФ-1, ПН-1): the server's two rules
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.storms"),
                Lang.tr(SubstrateConfigSC.empStorms ? "sc.manual.world.rule.storms.on" : "sc.manual.world.rule.storms.off")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.raids"),
                Lang.tr(SubstrateConfigSC.raids ? "sc.manual.world.rule.raids.on" : "sc.manual.world.rule.raids.off")));
        // Ф5 (ПВ-13, ПН-12, ПМ-19): the server's two rules, this client's own sky
        e.add(BookEl.item(com.sc.finale.FinaleItemsSC.navRecord(1), Lang.tr("sc.manual.world.rule.signal"),
                Lang.tr(SubstrateConfigSC.requireSignal ? "sc.manual.world.rule.signal.on" : "sc.manual.world.rule.signal.off")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.caravans"),
                Lang.tr(SubstrateConfigSC.caravans ? "sc.manual.world.rule.caravans.on" : "sc.manual.world.rule.caravans.off")));
        e.add(BookEl.dim(Lang.tr(SubstrateConfigSC.customSky() ? "sc.manual.world.rule.sky.on" : "sc.manual.world.rule.sky.off")));
        // Ф6 (ПК-26): the section embassy - the Tuners' Embassies in the Overworld, the server's rule
        e.add(BookEl.item(com.sc.embassy.EmbassyItemsSC.finder(1), Lang.tr("sc.manual.world.rule.embassies"),
                Lang.tr(SubstrateConfigSC.embassies ? "sc.manual.world.rule.embassies.on" : "sc.manual.world.rule.embassies.off")));
        e.add(BookEl.link("world.terrain", Lang.tr("sc.manual.world.terrain.title")));
        e.add(BookEl.link("world.biomes4", Lang.tr("sc.manual.world.biomes4.title")));
        e.add(BookEl.link("world.storm", Lang.tr("sc.manual.world.storm.title")));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.add(BookEl.link("world.lithographers", Lang.tr("sc.manual.world.lithographers.title")));
        e.add(BookEl.link("world.arena", Lang.tr("sc.manual.world.arena.title")));
        e.add(BookEl.link("world.navrecord", Lang.tr("sc.manual.world.navrecord.title")));
        e.add(BookEl.link("world.embassies", Lang.tr("sc.manual.world.embassies.title")));
        e.add(BookEl.link("bridge", Lang.tr("sc.manual.bridge.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Мир-микросхема»

    private static BookEntry terrain(BookChapter c) {
        ItemStack trace = rock(SubstrateBlocksSC.COPPER_TRACE);
        BookEntry e = new BookEntry("world.terrain", c, trace != null ? trace : icon(), Lang.tr("sc.manual.world.terrain.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.terrain.title"))).addAll(BookContent.paras("sc.manual.world.terrain",
                new Object[]{SubstrateTerrainSC.GRID, SubstrateTerrainSC.CANYON_WIDTH_MIN, SubstrateTerrainSC.CANYON_WIDTH_MAX,
                        SubstrateTerrainSC.CANYON_DEPTH_MIN, SubstrateTerrainSC.CANYON_DEPTH_MAX},
                new Object[]{SubstrateTerrainSC.PLATEAU_MIN, SubstrateTerrainSC.PLATEAU_MAX, SubstrateTerrainSC.TERRACE_STEP}));

        e.add(BookEl.head(Lang.tr("sc.manual.world.biomeshead")));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), Lang.tr("sc.dim.biome.plateaus"),
                Lang.tr("sc.manual.world.biome.plateaus", SubstrateTerrainSC.PLATEAU_MIN, SubstrateTerrainSC.PLATEAU_MAX)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.dim.biome.canyons"),
                Lang.tr("sc.manual.world.biome.canyons", SubstrateTerrainSC.CANYON_BELT)));
        // Ф4 (ПМ-10): the three new biomes - whole cells' inner squares, their own article
        e.add(BookEl.item(rock(SubstrateBlocksSC.CRYSTALLITE), Lang.tr("sc.dim.biome.ridges"),
                Lang.tr("sc.manual.world.biome.ridges", SubstrateWildTerrainSC.RIDGE_MAX)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_GOLD), Lang.tr("sc.dim.biome.oxide"),
                Lang.tr("sc.manual.world.biome.oxide", SubstrateWildTerrainSC.DUNE_MIN, SubstrateWildTerrainSC.DUNE_MAX)));
        e.add(BookEl.item(wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.GLASS), Lang.tr("sc.dim.biome.rift"),
                Lang.tr("sc.manual.world.biome.rift", SubstrateCellsSC.RIFT_MIN_DIST)));
        e.add(BookEl.link("world.biomes4", Lang.tr("sc.manual.world.biomes4.title")));

        // the layers (§3.3 of docs/plan-substrate.md), from SubstrateTerrainSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.layershead")));
        layer(e, new ItemStack(Blocks.bedrock), "0", "bedrock");
        layer(e, rock(SubstrateBlocksSC.CRYSTALLITE), "1-" + SubstrateTerrainSC.BASE_TOP, "base");
        layer(e, ore(OreEntry.TANTALITE), SubstrateTerrainSC.BASE_TOP + "-" + SubstrateTerrainSC.METAL_TOP, "metal");
        layer(e, rock(SubstrateBlocksSC.CRYSTALLITE), SubstrateTerrainSC.METAL_TOP + "-" + SubstrateTerrainSC.OXIDE_FROM, "crystallite");
        layer(e, rock(SubstrateBlocksSC.OXIDE_GOLD), SubstrateTerrainSC.OXIDE_FROM + "+", "oxide");

        e.add(BookEl.head(Lang.tr("sc.manual.world.blockshead")));
        for (int m = 0; m < SubstrateBlocksSC.ROCK_METAS && m < ROCK_NAMES.length; m++) {
            e.add(BookEl.item(rock(m), rockName(m), Lang.tr("sc.manual.world.block." + ROCK_NAMES[m])));
        }
        ItemStack druse = druse();
        e.add(BookEl.item(druse, druse != null ? druse.getDisplayName() : Lang.tr("tile.siliconage.crystalDruse.name"), Lang.tr("sc.manual.world.block.druse")));

        // the ores: SubstrateOreTableSC's heights and multipliers on the config's veins
        e.add(BookEl.head(Lang.tr("sc.manual.world.oreshead"), ore(OreEntry.TANTALITE)));
        e.addAll(BookContent.paras("sc.manual.world.ores"));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.ore"), "Y", Lang.tr("sc.manual.world.t.veins"));
        for (OreEntry ore : OreEntry.values()) {
            float mul = SubstrateOreTableSC.veinMultiplier(ore);
            t.row(ore(ore), new ItemStack(ModBlocks.oreSC, 1, ore.meta()).getDisplayName(), SubstrateOreTableSC.minY(ore) + "-" + SubstrateOreTableSC.maxY(ore),
                    Lang.tr("sc.manual.world.cell.veins", n(ConfigSC.settingsFor(ore).veinsPerChunk * mul), multiplier(mul)));
        }
        e.add(t);
        if (SubstrateBlocksSC.rock != null) {
            e.about(new ItemStack(SubstrateBlocksSC.rock, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SubstrateBlocksSC.ore != null) {
            e.about(new ItemStack(SubstrateBlocksSC.ore, 1, OreDictionary.WILDCARD_VALUE));
        }
        e.about(druse);
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        return e;
    }

    private static void layer(BookEntry e, ItemStack icon, String ys, String key) {
        e.add(BookEl.item(icon, "Y " + ys + ": " + Lang.tr("sc.manual.world.layer." + key), Lang.tr("sc.manual.world.layer." + key + ".what")));
    }

    // ------------------------------------------------------------------ «Бронированные враги»

    private static BookEntry armoured(BookChapter c) {
        BookEntry e = new BookEntry("world.armoured", c, nanoPiece(1), Lang.tr("sc.manual.world.armoured.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.armoured.title"))).addAll(BookContent.paras("sc.manual.world.armour",
                new Object[]{MobArmorMathSC.FULL_POINTS}));

        // by suit: the points of a full set, the share of a blow it stops, ПЭ-1's times and the siphon's rate (the player's rules)
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.suit"), Lang.tr("sc.manual.world.t.points"), Lang.tr("sc.manual.world.t.stops"),
                Lang.tr("sc.manual.world.t.emp"), Lang.tr("sc.manual.world.t.siphon"));
        for (ArmorSuit s : ArmorSuit.values()) {
            int pts = suitPoints(s);
            t.row(new ItemStack(ModItems.ARMOR.get(s)[1]), Lang.tr("sc.suit." + s.name().toLowerCase(Locale.ROOT)), String.valueOf(pts),
                    pct(MobArmorMathSC.absorbShare(pts)) + "%", sec(empTicks(s, EmpMode.POINT)) + " / " + sec(empTicks(s, EmpMode.PULSE)),
                    EmpTooltipSC.siphonPct(s, false) + "%");
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.t.note")));

        e.add(BookEl.head(Lang.tr("sc.manual.world.shieldhead")));
        e.addAll(BookContent.paras("sc.manual.world.shield", null, new Object[]{ironPoints()}, new Object[]{n(MobArmorMathSC.TRACE_RECHARGE_PER_SECOND)}));
        e.add(BookEl.head(Lang.tr("sc.manual.world.fighthead")));
        e.addAll(BookContent.paras("sc.manual.world.fight"));

        // ПЭ-1 (ПБ-10..ПБ-12): the server's two switches
        e.add(BookEl.head(Lang.tr("sc.manual.world.emphead"), new ItemStack(ModItems.empGun)));
        e.add(BookEl.para(SubstrateConfigSC.empMobTargets ? Lang.tr("sc.manual.world.emp.on", ConfigSC.empImmunitySeconds, ironPoints())
                : Lang.tr("sc.manual.world.emp.off", sec(EmpMathSC.NO_ARMOR_SLOW_TICKS))));
        e.add(BookEl.para(SubstrateConfigSC.siphonMobs ? Lang.tr("sc.manual.world.siphon.on", BookContent.num(MobArmorMathSC.MOB_SIPHON_STREAM_CAP))
                : Lang.tr("sc.manual.world.siphon.off")));
        // Ф3: the enemies' own ПЭ-1, the personal screen, «Пробой» - their article
        e.add(BookEl.para(Lang.tr("sc.manual.world.emp.f3")));
        e.add(BookEl.link("emp", Lang.tr("sc.manual.emp.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.lineman", mobName(EntityLinemanSC.NAME)));
        e.add(BookEl.link("world.marauder", mobName(EntityMarauderSC.NAME)));
        e.add(BookEl.link("world.renegades", Lang.tr("sc.manual.world.renegades.title")));
        e.add(BookEl.link("world.shells", Lang.tr("sc.manual.world.shells.title")));
        e.add(BookEl.link("world.executioner", mobName(EntityExecutionerSC.NAME)));
        e.add(BookEl.link("world.noise", Lang.tr("sc.manual.world.noise.title")));
        e.add(BookEl.link("world.elites", Lang.tr("sc.manual.world.elites.title")));
        // Ф4's bestiary
        e.add(BookEl.link("world.overheated", mobName(EntityOverheatedSC.NAME)));
        e.add(BookEl.link("world.camps", Lang.tr("sc.manual.world.camps.title")));
        e.add(BookEl.link("world.vaults", Lang.tr("sc.manual.world.vaults.title")));
        e.add(BookEl.link("world.eclipse", mobName(EntityEclipseSC.NAME)));
        e.add(BookEl.link("world.noise4", Lang.tr("sc.manual.world.noise4.title")));
        return e;
    }

    // ------------------------------------------------------------------ the bestiary

    private static BookEntry lineman(BookChapter c) {
        String name = mobName(EntityLinemanSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_LINEMAN);
        BookEntry e = new BookEntry("world.lineman", c, egg != null ? egg : nanoPiece(0), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.lineman"));
        float hp = stat(LINEMAN, "HP", 20F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n(hp), n(hp / 2F))));
        e.add(BookEl.item(nanoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.mob.armour", suitName(ArmorSuit.NANO),
                n(stat(LINEMAN, "PIECES_MIN", 1F)), n(stat(LINEMAN, "PIECES_MAX", 3F)), n(stat(LINEMAN, "RESERVE_MIN", 20F)), n(stat(LINEMAN, "RESERVE_MAX", 30F)))));
        e.add(BookEl.item(weapon(WeaponType.ION_CUTTER), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.lineman.weapon",
                n(stat(LINEMAN, "FIST_MIN", 3F)), n(stat(LINEMAN, "FIST_MAX", 4F)), pct(stat(LINEMAN, "ION_CUTTER_CHANCE", 0.05F)),
                n(stat(LINEMAN, "BEAM_DAMAGE", 4F)), sec((int) stat(BEAM, "RELOAD_TICKS", 40F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.lineman.where",
                SpawnRulesSC.PLATEAU_WEIGHTS[0], SpawnRulesSC.CANYON_WEIGHTS[0])));
        e.add(BookEl.item(loot(LOOT_FIRMWARE), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.lineman.loot", lootName(LOOT_FIRMWARE),
                ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR).getDisplayName(), n(stat(DROPS, "TRANSISTOR_MAX", 2F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.armorMeta(ArmorSuit.NANO, 1)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.lineman.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[0])), pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.ION_CUTTER.ordinal()])))));
        e.addAll(BookContent.paras("sc.manual.world.lineman.tips", new Object[]{sec(empTicks(ArmorSuit.NANO, EmpMode.POINT)), siphonSeconds(ArmorSuit.NANO)}));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(egg);
        return e;
    }

    private static BookEntry marauder(BookChapter c) {
        String name = mobName(EntityMarauderSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_MARAUDER);
        BookEntry e = new BookEntry("world.marauder", c, egg != null ? egg : nanoPiece(1), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.marauder"));
        float hp = stat(MARAUDER, "HP", 24F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n(hp), n(hp / 2F))));
        e.add(BookEl.item(nanoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.mob.armour", suitName(ArmorSuit.NANO),
                n(stat(MARAUDER, "PIECES_MIN", 2F)), n(stat(MARAUDER, "PIECES_MAX", 4F)), n(stat(MARAUDER, "RESERVE_MIN", 20F)), n(stat(MARAUDER, "RESERVE_MAX", 40F)))));
        e.add(BookEl.item(blade(BladeType.NANO), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.marauder.weapon",
                pct(stat(MARAUDER, "BLADE_CHANCE", 0.5F)), n(stat(MARAUDER, "BLADE_DAMAGE", 6F)), pct(stat(MARAUDER, "ION_CUTTER_CHANCE", 0.3F)),
                n(stat(MARAUDER, "BEAM_DAMAGE", 4F)), n(stat(MARAUDER, "CLUB_DAMAGE", 4F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.marauder.where",
                SpawnRulesSC.CANYON_WEIGHTS[1], SpawnRulesSC.PLATEAU_WEIGHTS[1], n(stat(MARAUDER, "GROUP_MIN", 2F)), n(stat(MARAUDER, "GROUP_MAX", 4F)))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.t.bag"), Lang.tr("sc.manual.world.marauder.bag", n(stat(MARAUDER, "BAG_SLOTS", 9F)))));
        e.add(BookEl.item(loot(LOOT_NANO_SCRAP), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.marauder.loot", lootName(LOOT_NANO_SCRAP),
                pct(stat(DROPS, "SCRAP_CHANCE", 0.4F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.bladeMeta(BladeType.NANO)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.marauder.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[0])), pct(trophyChance(TrophyTableSC.BLADE[0])),
                pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.ION_CUTTER.ordinal()])))));
        e.addAll(BookContent.paras("sc.manual.world.marauder.tips", new Object[]{sec(empTicks(ArmorSuit.NANO, EmpMode.POINT))}));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(egg);
        return e;
    }

    // ------------------------------------------------------------------ «Трофеи и добыча»

    private static BookEntry trophies(BookChapter c) {
        ItemStack icon = trophy(TrophyTableSC.armorMeta(ArmorSuit.NANO, 0));
        BookEntry e = new BookEntry("world.trophies", c, icon != null ? icon : nanoPiece(0), Lang.tr("sc.manual.world.trophies.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.trophies.title"))).addAll(BookContent.paras("sc.manual.world.trophies", null,
                new Object[]{pct(stat(DROPS, "KILLER_SHARE", 0.25F)), sec((int) stat(DROPS, "OWNER_TICKS", 300F))},
                new Object[]{pct(TrophyTableSC.LOOTING_STEP), n(TrophyTableSC.LOOTING_CAP), TrophyTableSC.BAD_LUCK_N[ArmorSuit.NANO.ordinal()],
                        2 * TrophyTableSC.BAD_LUCK_N[ArmorSuit.NANO.ordinal()]}));
        if (SubstrateConfigSC.gearDropMultiplier != 1F) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.trophies.mul", n(SubstrateConfigSC.gearDropMultiplier))));
        }

        // what drops in this version (ПТ-7, ПТ-9, Ф3 ПТ-14): every meta of TrophyTableSC.SHOWN, the chances with the server's multiplier
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.trophy"), Lang.tr("sc.manual.world.t.chance"), Lang.tr("sc.manual.world.t.from"));
        for (int meta : TrophyTableSC.SHOWN) {
            ItemStack real = realOfTrophy(meta);
            String from = trophyDroppers(meta), chance = trophyChancePct(meta);
            t.row(trophy(meta), real != null ? real.getDisplayName() : TrophyTableSC.key(meta), chance == null ? "-" : chance + "%",
                    from == null ? "-" : from);
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.trophies.pe1", pct(trophyChance(stat(TROPHIES, "PE1", 0.02F))),
                n(1F / Math.max(1e-6F, stat(JAMMER, "CARRIER_CHANCE", 0.02F))))));
        // Ф4 (ПТ-16, ПТ-20): the Singular trophies - the Sentinel and «Затмение» only; the storm's multiplier
        e.add(BookEl.dim(Lang.tr("sc.manual.world.trophies.singular", mobName(EntitySentinelSC.NAME), mobName(EntityEclipseSC.NAME),
                pct(trophyChance(stat(SENTINEL, "CARRIER_PE1", 0.1F))))));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.trophies.storm", n(TrophyTableSC.STORM_MUL), n(TrophyTableSC.EVENT_CAP))));
        // Ф5 (ПТ-23, ПТ-26): the Breakdown Warden's one roll - the Singular chestplate, the blade, the ПЭ-1М (meta 24)
        e.add(BookEl.dim(Lang.tr("sc.manual.world.trophies.warden", mobName(com.sc.mob.EntityBreakdownWardenSC.NAME), wardenTrophyPct(WardenRulesSC.TROPHY_CHEST),
                wardenTrophyPct(WardenRulesSC.TROPHY_BLADE), wardenTrophyPct(WardenRulesSC.TROPHY_PE1M), TrophyTableSC.SHOWN.length)));

        e.add(BookEl.head(Lang.tr("sc.manual.world.trophyitemshead")));
        e.addAll(BookContent.paras("sc.manual.world.trophyitems"));
        e.add(BookEl.link("world.restore", Lang.tr("sc.manual.world.restore.title")));

        // faction loot (ПТ-1, Ф3 ПТ-13) and its use: the Crusher's recipes, the Salvager (Ф3)
        e.add(BookEl.head(Lang.tr("sc.manual.world.loothead")));
        e.add(BookEl.item(loot(LOOT_FIRMWARE), lootName(LOOT_FIRMWARE), Lang.tr("sc.manual.world.loot.shellFirmware")));
        e.add(BookEl.item(loot(LOOT_NANO_SCRAP), lootName(LOOT_NANO_SCRAP), Lang.tr("sc.manual.world.loot.nanoScrap",
                pct(stat(DROPS, "SCRAP_CHANCE", 0.4F)))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.t.bag"), Lang.tr("sc.manual.world.loot.bag")));
        e.add(BookEl.item(loot(LOOT_STOLEN_CARGO), lootName(LOOT_STOLEN_CARGO), lootLine(LOOT_STOLEN_CARGO)));   // Ф2: back to the Elders
        for (int m = LOOT_QUANTUM_SCRAP; m < LOOT_NAMES.length; m++) {          // Ф3's four and Ф4's five (ПТ-18)
            e.add(BookEl.item(loot(m), lootName(m), lootLine(m)));
        }
        for (MachineRecipe r : lootRecipes()) {
            e.add(BookEl.machine(r));
        }
        e.add(BookEl.link("world.lineman", mobName(EntityLinemanSC.NAME)));
        e.add(BookEl.link("world.marauder", mobName(EntityMarauderSC.NAME)));
        e.add(BookEl.link("world.renegades", Lang.tr("sc.manual.world.renegades.title")));
        e.add(BookEl.link("world.shells", Lang.tr("sc.manual.world.shells.title")));
        e.add(BookEl.link("world.executioner", mobName(EntityExecutionerSC.NAME)));
        e.add(BookEl.link("world.vaults", Lang.tr("sc.manual.world.vaults.title")));
        e.add(BookEl.link("world.eclipse", mobName(EntityEclipseSC.NAME)));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        if (MobItemsSC.trophy != null) {
            e.about(new ItemStack(MobItemsSC.trophy, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (MobItemsSC.combatLoot != null) {
            e.about(new ItemStack(MobItemsSC.combatLoot, 1, OreDictionary.WILDCARD_VALUE));
        }
        return e;
    }

    /** The machine recipes (the Crusher's first) that take the combat loot. */
    private static List<MachineRecipe> lootRecipes() {
        List<MachineRecipe> out = new ArrayList<MachineRecipe>();
        if (MobItemsSC.combatLoot == null) {
            return out;
        }
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                for (ItemStack in : r.inputs) {
                    if (in != null && in.getItem() == MobItemsSC.combatLoot) {
                        out.add(r);
                        break;
                    }
                }
            }
        }
        return out;
    }

    // ================================================================== Ф2 «Поселения» (docs/plan-substrate-tech-f2.md §10.2)

    // ------------------------------------------------------------------ «Поселения Наладчиков»

    private static BookEntry settlements(BookChapter c) {
        ItemStack core = core();
        BookEntry e = new BookEntry("world.settlements", c, core != null ? core : icon(), Lang.tr("sc.manual.world.settlements.title"));
        // the first settlement's middle from the world's spawn (the middle of cell 0, 0): FIRST_CELL cells away
        int first = (int) Math.round(Math.hypot(SettlementLayoutSC.FIRST_CELL_X * SettlementLayoutSC.CELL,
                SettlementLayoutSC.FIRST_CELL_Z * SettlementLayoutSC.CELL));
        e.add(BookEl.title(Lang.tr("sc.manual.world.settlements.title"))).addAll(BookContent.paras("sc.manual.world.settle.about", null,
                new Object[]{SettlementLayoutSC.REGION, SettlementLayoutSC.CHANCE_PCT, SettlementLayoutSC.CELL - 2 * SettlementLayoutSC.JITTER},
                new Object[]{first}));
        if (!SubstrateConfigSC.villages) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.settle.off")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.settle.old")));

        // the sizes (ПП-3): SettlementSizeSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.sizeshead")));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.size"), Lang.tr("sc.manual.world.t.dome"), Lang.tr("sc.manual.world.t.tuners"),
                Lang.tr("sc.manual.world.t.guards"), Lang.tr("sc.manual.world.t.dock"));
        for (int i = 0; i < SettlementSizeSC.SUBSTRATE_SIZES; i++) {        // Ф6: the Substrate's three (EMBASSY - «Город посольства»)
            SettlementSizeSC s = SettlementSizeSC.values()[i];
            t.row(null, Lang.tr(s.langKey()), span(s.radiusMin, s.radiusMax), span(s.tunersMin, s.tunersMax),
                    s.seniors > 0 ? Lang.tr("sc.manual.world.cell.guards", s.sentries, s.seniors) : String.valueOf(s.sentries),
                    Lang.tr(s.dock ? "sc.manual.world.cell.yes" : "sc.manual.world.cell.no"));
        }
        e.add(t);
        int[] w = SettlementLayoutSC.SIZE_WEIGHTS;
        e.add(BookEl.dim(Lang.tr("sc.manual.world.settle.sizes.note", Lang.tr(NpcKindSC.SENTRY.langKey()), Lang.tr(NpcKindSC.SENIOR_WARDEN.langKey()),
                n(w.length > 2 && w[2] > 0 ? w[1] / (float) w[2] : 0F))));

        // the plan from the middle out (ПП-4)
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.layouthead")));
        e.addAll(BookContent.paras("sc.manual.world.settle.layout"));
        part(e, core, "heart");
        part(e, showcase(BlockShowcaseSC.ARMOUR_STATION), "square");
        part(e, showcase(BlockShowcaseSC.CVD), "workshops");
        part(e, rock(BRICK), "cells");
        part(e, null, "towers");
        part(e, showcase(BlockShowcaseSC.SHOWER), "gate");
        part(e, dock(BlockDockSC.BEACON), "dock");
        part(e, showcase2(BlockShowcase2SC.DISPATCH_CONSOLE), "dispatcher");    // Ф6 (ПЖ-11): the flights home

        // the core (ПП-6, ПП-7)
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.corehead"), core));
        e.addAll(BookContent.paras("sc.manual.world.settle.core", null, new Object[]{SettlementSC.FOUNDATION}));

        // what a guest may not do (ПП-7..ПП-11, ПЖ-2)
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.ruleshead")));
        rule(e, new ItemStack(Items.iron_pickaxe), "break");
        rule(e, new ItemStack(Blocks.chest), "use");
        rule(e, new ItemStack(Items.flint_and_steel), "fire");
        rule(e, new ItemStack(Blocks.tnt), "explode");
        rule(e, egg(MobEntitiesSC.ID_MARAUDER), "spawn", SettlementSC.SPAWN_MARGIN);
        rule(e, com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1), "bridge");
        rule(e, new ItemStack(ModBlocks.fieldGeneratorSC), "field");
        rule(e, egg(MobEntitiesSC.ID_TUNER), "tuners", SettlementSC.BELT);
        rule(e, null, "fake");
        e.addAll(BookContent.paras("sc.manual.world.settle.allowed"));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        // Ф6: the Tuners' Embassies in the Overworld - the same core, protection and settlers, their own article
        e.add(BookEl.dim(Lang.tr("sc.manual.world.settle.embassies")));
        e.add(BookEl.link("world.embassies", Lang.tr("sc.manual.world.embassies.title")));
        e.about(core, rock(BRICK));
        if (SettlementBlocksSC.showcase != null) {
            e.about(new ItemStack(SettlementBlocksSC.showcase, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SettlementBlocksSC.showcase2 != null) {                    // Ф3: the Power Engineer's and the Salvager's machines
            e.about(new ItemStack(SettlementBlocksSC.showcase2, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SettlementBlocksSC.decor != null) {
            e.about(new ItemStack(SettlementBlocksSC.decor, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SettlementBlocksSC.glass != null) {
            e.about(new ItemStack(SettlementBlocksSC.glass));
        }
        return e;
    }

    /** One step of the plan: sc.manual.world.settle.part.<key> and its .desc. */
    private static void part(BookEntry e, ItemStack icon, String key) {
        e.add(BookEl.item(icon, Lang.tr("sc.manual.world.settle.part." + key), Lang.tr("sc.manual.world.settle.part." + key + ".desc")));
    }

    /** One thing a guest may not do: sc.manual.world.settle.rule.<key> and its .desc (with `args`). */
    private static void rule(BookEntry e, ItemStack icon, String key, Object... args) {
        String k = "sc.manual.world.settle.rule." + key;
        e.add(BookEl.item(icon, Lang.tr(k), args.length == 0 ? Lang.tr(k + ".desc") : Lang.tr(k + ".desc", args)));
    }

    // ------------------------------------------------------------------ «Наладчики»

    private static BookEntry tuners(BookChapter c) {
        ItemStack egg = egg(MobEntitiesSC.ID_TUNER);
        BookEntry e = new BookEntry("world.tuners", c, egg != null ? egg : icon(), Lang.tr("sc.manual.world.tuners.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.tuners.title"))).addAll(BookContent.paras("sc.manual.world.tuners", null,
                new Object[]{NpcNamesSC.ELEMENTS.length}));
        float hp = stat(TUNER, "HP", 20F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n(hp), n(hp / 2F))));
        e.add(BookEl.item(core(), Lang.tr("sc.manual.world.tuners.safe"), Lang.tr("sc.manual.world.tuners.safe.desc", SettlementSC.BELT)));

        // the professions of Ф2 (ПЖ-3) and Ф3 (ПЖ-8: the Power Engineer, the Armourer, the Salvager): NpcKindSC, the deals' count from TradeTableSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.tuners.kindshead")));
        for (NpcKindSC k : NpcKindSC.values()) {
            if (k.guard) {
                continue;
            }
            int deals = TradeTableSC.offers(k).size();
            String what = Lang.tr("sc.manual.world.tuners.kind." + k.key);
            e.add(BookEl.item(kindIcon(k), Lang.tr(k.langKey()), deals > 0 ? what + " " + Lang.tr("sc.manual.world.tuners.deals", deals) : what));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.tuners.f3")));

        // the day (ПЖ-4), the world's clock: tick 0 is 6:00
        int work = (int) stat(TUNER, "WORK_UNTIL", 11500F), plaza = (int) stat(TUNER, "PLAZA_UNTIL", 13500F);
        e.add(BookEl.head(Lang.tr("sc.manual.world.tuners.dayhead")));
        e.add(BookEl.item(showcase(BlockShowcaseSC.CVD), Lang.tr("sc.manual.world.tuners.day.work"),
                Lang.tr("sc.manual.world.tuners.day.work.desc", clock(0), clock(work))));
        e.add(BookEl.item(core(), Lang.tr("sc.manual.world.tuners.day.plaza"), Lang.tr("sc.manual.world.tuners.day.plaza.desc", clock(work), clock(plaza))));
        e.add(BookEl.item(rock(BRICK), Lang.tr("sc.manual.world.tuners.day.night"), Lang.tr("sc.manual.world.tuners.day.night.desc", clock(plaza))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_MARAUDER), Lang.tr("sc.manual.world.tuners.day.flee"),
                Lang.tr("sc.manual.world.tuners.day.flee.desc", n(stat(TUNER, "FLEE_RADIUS", 12F)))));

        // talking (ПЖ-5), the Clone Capsule (ПП-13)
        e.add(BookEl.head(Lang.tr("sc.manual.world.tuners.talkhead")));
        e.addAll(BookContent.paras("sc.manual.world.tuners.talk"));
        ItemStack clone = showcase(BlockShowcaseSC.CLONE_CAPSULE);
        e.add(BookEl.item(clone, clone != null ? clone.getDisplayName() : Lang.tr("tile.siliconage.showcase.cloneCapsule.name"),
                Lang.tr("sc.manual.world.tuners.clone", SettlementSC.RESPAWN_CLEAR)));
        e.add(BookEl.item(egg, egg != null ? egg.getDisplayName() : mobName(EntityTunerSC.NAME), Lang.tr("sc.manual.world.tuners.egg")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.about(egg);
        return e;
    }

    // ------------------------------------------------------------------ «Терминал обмена и КЧ»

    private static BookEntry trade(BookChapter c) {
        ItemStack chip = SettlementItemsSC.chips(1);
        BookEntry e = new BookEntry("world.trade", c, chip != null ? chip : icon(), Lang.tr("sc.manual.world.trade.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.trade.title"))).addAll(BookContent.paras("sc.manual.world.trade"));
        e.add(BookEl.item(chip, chip != null ? chip.getDisplayName() : Lang.tr("item.siliconage.creditChip.name"),
                Lang.tr("sc.manual.world.trade.chip", ReputationSC.PASS_CHIPS, TradeRulesSC.CHIPS_PER_DAY)));

        // the deals of every profession (ПО-3), the table's base numbers: TradeTableSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.dealshead")));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.trade.deals.note", TradeRulesSC.SEED_SPREAD_PCT)));
        boolean any = false;
        for (NpcKindSC k : NpcKindSC.values()) {
            List<TradeOfferSC> offers = k.guard ? new ArrayList<TradeOfferSC>() : TradeTableSC.offers(k);
            if (offers.isEmpty()) {
                continue;
            }
            any = true;
            e.add(BookEl.head(Lang.tr(k.langKey()), kindIcon(k)));
            BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.give"), Lang.tr("sc.manual.world.t.get"), Lang.tr("sc.manual.world.t.level"),
                    Lang.tr("sc.manual.world.t.limit"));
            for (TradeOfferSC o : offers) {
                t.row(offerIcon(o), sides(o.in), outText(o), levelName(o.minLevel), limitText(o));
            }
            e.add(t);
            if (k == NpcKindSC.QUARTERMASTER) {
                e.add(BookEl.dim(Lang.tr("sc.manual.world.trade.embassyOnly")));     // Ф6 (ПО-25): an embassy's settler
            }
        }
        if (!any) {
            e.add(BookEl.dim(Lang.tr("sc.manual.world.trade.none")));
        }

        // Ф3 (ПО-13): the services - the Power Engineer's charge, the Armourer's restoring; the thing worked on is the player's own
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.serviceshead"), kindIcon(NpcKindSC.POWER_ENGINEER)));
        e.addAll(BookContent.paras("sc.manual.world.trade.services"));
        e.add(BookEl.link("world.restore", Lang.tr("sc.manual.world.restore.title")));

        // the level's multiplier on the player's side (ПО-6): ReputationSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.priceshead")));
        BookEl p = BookEl.table(Lang.tr("sc.manual.world.t.level"), Lang.tr("sc.manual.world.t.mul"), Lang.tr("sc.manual.world.t.example", PRICE_EXAMPLE),
                Lang.tr("sc.manual.world.t.can"));
        for (int lv = ReputationSC.VIOLATOR; lv <= ReputationSC.HONOURED; lv++) {
            float m = ReputationSC.priceMultiplier(lv);
            p.row(null, levelName(lv), "×" + multiplier(m), String.valueOf(Math.max(1, (int) Math.ceil(PRICE_EXAMPLE * (double) m - 1e-6))),
                    Lang.tr(ReputationSC.buys(lv) ? "sc.manual.world.cell.buysells" : "sc.manual.world.cell.sells"));
        }
        e.add(p);
        e.addAll(BookContent.paras("sc.manual.world.trade.rules", new Object[]{TradeRulesSC.SEED_SPREAD_PCT},
                new Object[]{TradeRulesSC.CHIPS_PER_DAY, ReputationSC.TRADE, ReputationSC.TRADE_DAY_CAP}, new Object[]{TradeRulesSC.BUY_OVER_SELL}));

        // the Elder's contracts (ПО-8): ContractTableSC, the one-time anchor contract (§4 «Цена»)
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.contractshead"), kindIcon(NpcKindSC.ELDER)));
        e.addAll(BookContent.paras("sc.manual.world.trade.contracts", new Object[]{ContractTableSC.BOARD, TradeRulesSC.CONTRACTS_PER_DAY}));
        BookEl ct = BookEl.table(Lang.tr("sc.manual.world.t.contract"), Lang.tr("sc.manual.world.t.goal"), Lang.tr("sc.manual.world.t.reward"));
        for (ContractTableSC.Contract k : ContractTableSC.pool()) {
            if (k.key.startsWith(BookEmbassySC.EMB_PREFIX)) {
                continue;                                   // Ф6 (ПО-27): the Ambassador's own - «Лавка и Интендант»
            }
            ct.row(null, Lang.tr("sc.contract." + k.key), String.valueOf(k.target), reward(k.chips, k.reputation)
                    + (k.modulePct > 0 ? " " + Lang.tr("sc.manual.world.cell.module", k.modulePct) : ""));
        }
        ct.row(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), Lang.tr("sc.contract.anchor"), "1",
                reward(ReputationSC.ANCHOR_CHIPS, ReputationSC.ANCHOR) + " " + Lang.tr("sc.manual.world.cell.once"));
        e.add(ct);
        e.add(BookEl.item(SettlementItemsSC.chips(ReputationSC.PENANCE_CHIPS), Lang.tr("sc.manual.world.trade.penance"),
                Lang.tr("sc.manual.world.trade.penance.desc", ReputationSC.PENANCE_CHIPS, ReputationSC.PENANCE)));
        e.add(BookEl.item(loot(LOOT_STOLEN_CARGO), lootName(LOOT_STOLEN_CARGO), lootLine(LOOT_STOLEN_CARGO)));
        // Ф4 (ПО-14..ПО-16): the new loot is bought (the deals above), a lost raid closes the trade, the Elder's Crumb is in the day's cap
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.f4head")));
        e.addAll(BookContent.paras("sc.manual.world.trade.f4", null, new Object[]{days(RaidRulesSC.CLOSED_TICKS)},
                new Object[]{CrumbLimitSC.DAY_CAP}));
        // Ф5 (ПО-17..ПО-22): the Coordinator, the Module, the scheme, the caravan's rare deal - in the tables above
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.f5head")));
        e.addAll(BookContent.paras("sc.manual.world.trade.f5", null, new Object[]{Lang.tr(NpcKindSC.ARMOURER.langKey()),
                com.sc.settle.caravan.CaravanRulesSC.RARE_DAYS}));
        e.add(BookEl.link("world.caravans", Lang.tr("sc.manual.world.caravans.title")));
        e.add(BookEl.link("world.lithoModule", Lang.tr("sc.manual.world.lithoModule.title")));
        e.add(BookEl.link("world.cosmetics", Lang.tr("sc.manual.world.cosmetics.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        e.add(BookEl.link("world.raids", Lang.tr("sc.manual.world.raids.title")));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        e.about(chip, loot(LOOT_STOLEN_CARGO));
        return e;
    }

    private static String reward(int chips, int trust) {
        return Lang.tr("sc.manual.world.cell.reward", chips, trust);
    }

    /** A deal's row icon: what comes out (the map, an item, the chips), else its first side. */
    static ItemStack offerIcon(TradeOfferSC o) {
        if (o.outKind == TradeOfferSC.OUT_MAP && SettlementItemsSC.settlementMap != null) {
            return new ItemStack(SettlementItemsSC.settlementMap);
        }
        ItemStack s = o.outKind == TradeOfferSC.OUT_ITEM && o.out != null ? o.out.display() : null;
        if ((s == null || o.sell()) && o.in.length > 0 && o.in[0] != null) {
            s = o.in[0].display();                      // a sale: the goods it takes
        }
        return s;
    }

    /** «16 × Кремниевая пластина + 6 КЧ». */
    static String sides(TradeOfferSC.Ingredient[] in) {
        return sides(in, 0, false);
    }

    /** The sides from `from` on; `noChips` - the Credit Chips left out (a station's price: the Armourer's components only). */
    private static String sides(TradeOfferSC.Ingredient[] in, int from, boolean noChips) {
        StringBuilder sb = new StringBuilder();
        for (int k = Math.max(0, from); in != null && k < in.length; k++) {
            TradeOfferSC.Ingredient i = in[k];
            if (i != null && !(noChips && i.isChips())) {
                sb.append(sb.length() == 0 ? "" : " + ").append(side(i));
            }
        }
        return sb.toString();
    }

    private static String side(TradeOfferSC.Ingredient i) {
        if (i.isChips()) {
            return Lang.tr("sc.manual.world.cell.chips", i.count);
        }
        String any = matcherText(i);
        if (any != null) {
            return any;                                 // Ф3: a service's «any matching» side - the player's own thing, never one stack
        }
        ItemStack s = i.display();
        return Lang.tr("sc.manual.world.cell.count", i.count, s != null ? s.getDisplayName() : i.ore != null ? i.ore : "?");
    }

    static String outText(TradeOfferSC o) {
        if (o.outKind == TradeOfferSC.OUT_REPUTATION) {
            return Lang.tr("sc.manual.world.cell.trust", o.outAmount);
        }
        if (o.outKind == TradeOfferSC.OUT_MAP) {
            return SettlementItemsSC.settlementMap != null ? new ItemStack(SettlementItemsSC.settlementMap).getDisplayName()
                    : Lang.tr("item.siliconage.settlementMap.name");
        }
        if (o.outKind == OUT_CHARGE) {
            return Lang.tr("sc.manual.world.cell.charged");
        }
        if (o.outKind == OUT_RESTORE) {
            return Lang.tr("sc.manual.world.cell.restored");
        }
        return o.out == null ? "?" : side(o.out);
    }

    /** «8 в сутки», «1 в неделю, глоб.» (ПО-4, ПО-5); Ф3: «без лимита» (a service with none of its own). */
    static String limitText(TradeOfferSC o) {
        if (o.limit >= NO_LIMIT) {
            return Lang.tr("sc.manual.world.cell.nolimit");
        }
        String k = o.global ? (o.periodDays >= 7 ? "sc.manual.world.cell.week.global" : "sc.manual.world.cell.day.global") : "sc.manual.world.cell.day";
        return Lang.tr(k, o.limit);
    }

    /**
     * Ф3 (§6.8): a service's matcher side in words - «часть брони Нано» (TradeOfferSC.Ingredient.suit), «трофейная часть брони
     * Квант» / a trophy's name (Ingredient.metas()) -, null for any other side. Package T's members, read by name: the chapter
     * builds on the core's TradeOfferSC too (no matchers there - null).
     */
    private static String matcherText(TradeOfferSC.Ingredient i) {
        Object suit = member(i, "suit");
        if (suit instanceof ArmorSuit) {
            return Lang.tr("sc.manual.world.cell.piece", suitName((ArmorSuit) suit));
        }
        int[] metas = metasOf(i);
        return metas.length == 0 ? null : trophyGroup(metas);
    }

    /** A trophy matcher's metas (Ingredient.metas(), package T), empty for any other side. */
    private static int[] metasOf(TradeOfferSC.Ingredient i) {
        Object m = member(i, "metas");
        return m instanceof int[] ? (int[]) m : new int[0];
    }

    /** «трофейная часть брони Нано» for a suit's four pieces, else the trophies' names. */
    static String trophyGroup(int[] metas) {
        ArmorSuit level = null;
        boolean pieces = metas.length == 4;
        for (int m : metas) {
            ArmorSuit s = m >= 0 && m < TrophyTableSC.META_BLADE ? TrophyTableSC.level(m) : null;
            pieces &= s != null && (level == null || level == s);
            level = s;
        }
        if (pieces && level != null) {
            return Lang.tr("sc.manual.world.cell.trophypiece", suitName(level));
        }
        StringBuilder sb = new StringBuilder();
        for (int m : metas) {
            ItemStack t = trophy(m);
            sb.append(sb.length() == 0 ? "" : ", ").append(t != null ? t.getDisplayName() : TrophyTableSC.key(m));
        }
        return sb.toString();
    }

    /** A public field or a public no-argument method `name` of `o` (another package's member, by name), null if it has none. */
    private static Object member(Object o, String name) {
        if (o == null) {
            return null;
        }
        try {
            return o.getClass().getField(name).get(o);
        } catch (Throwable t) {
            // not a field (yet)
        }
        try {
            return o.getClass().getMethod(name).invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The chips a deal takes (0: none). */
    private static int chipsOf(TradeOfferSC o) {
        int n = 0;
        for (TradeOfferSC.Ingredient i : o.in) {
            n += i != null && i.isChips() ? i.count : 0;
        }
        return n;
    }

    /** sc.settle.rep.<level> («Чужак»). */
    public static String levelName(int level) {
        return Lang.tr("sc.settle.rep." + ReputationSC.LEVEL_KEYS[Math.max(0, Math.min(ReputationSC.LEVEL_KEYS.length - 1, level))]);
    }

    // ------------------------------------------------------------------ «Доверие»

    private static BookEntry trust(BookChapter c) {
        BookEntry e = new BookEntry("world.trust", c, kindIcon(NpcKindSC.ELDER) != null ? kindIcon(NpcKindSC.ELDER) : icon(),
                Lang.tr("sc.manual.world.trust.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.trust.title"))).addAll(BookContent.paras("sc.manual.world.trust", null,
                new Object[]{ReputationSC.MIN, ReputationSC.MAX}));

        // the levels (ПД-1): ReputationSC
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.level"), Lang.tr("sc.manual.world.t.points"), Lang.tr("sc.manual.world.t.opens"),
                Lang.tr("sc.manual.world.t.prices"));
        for (int lv = ReputationSC.ENEMY; lv <= ReputationSC.HONOURED; lv++) {
            t.row(null, levelName(lv), levelRange(lv), Lang.tr("sc.manual.world.trust.opens." + ReputationSC.LEVEL_KEYS[lv]),
                    lv == ReputationSC.ENEMY ? "-" : "×" + multiplier(ReputationSC.priceMultiplier(lv)));
        }
        e.add(t);

        // what raises it (ПД-2, ПД-4, ПД-5)
        e.add(BookEl.head(Lang.tr("sc.manual.world.trust.uphead")));
        ItemStack chip = SettlementItemsSC.chips(1);
        gain(e, chip, "trade", ReputationSC.TRADE, ReputationSC.TRADE_DAY_CAP);
        int[] k = ReputationSC.KILL_BY_SUIT;
        gain(e, nanoPiece(1), "kill", SettlementSC.KILL_REACH, k[0], k[Math.min(1, k.length - 1)], k[Math.min(2, k.length - 1)], k[k.length - 1],
                ReputationSC.KILL_SMALL_DAY_CAP);
        gain(e, kindIcon(NpcKindSC.ELDER), "contract", ReputationSC.CONTRACT_MIN, ReputationSC.CONTRACT_MAX);
        gain(e, loot(LOOT_STOLEN_CARGO), "cargo", ReputationSC.CARGO);
        gain(e, chip, "pass", ReputationSC.PASS, ReputationSC.PASS_CHIPS);
        gain(e, SettlementItemsSC.chips(ReputationSC.PENANCE_CHIPS), "penance", ReputationSC.PENANCE_CHIPS, ReputationSC.PENANCE);
        gain(e, com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), "anchor", ReputationSC.ANCHOR, ReputationSC.ANCHOR_CHIPS);
        // Ф6 (ПД-7): what an embassy's deeds give stops at the ceiling
        gain(e, com.sc.embassy.EmbassyItemsSC.finder(1), "embassy", ReputationSC.EMBASSY_CEILING, levelName(ReputationSC.PARTNER));

        // what lowers it (ПД-3, ПП-10): ViolationRulesSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.trust.downhead")));
        boolean rule = SubstrateConfigSC.guardsAggroOnViolations;
        BookEl v = BookEl.table(Lang.tr("sc.manual.world.t.violation"), Lang.tr("sc.manual.world.t.trust"), Lang.tr("sc.manual.world.t.guards"));
        for (int kind = 0; kind < ViolationRulesSC.KEYS.length; kind++) {
            String cost, guards;
            if (ViolationRulesSC.blockKind(kind)) {
                cost = ViolationRulesSC.penalty(kind, ViolationRulesSC.STAGE_WARN) + " / " + ViolationRulesSC.penalty(kind, ViolationRulesSC.STAGE_WATCH)
                        + " / " + ViolationRulesSC.penalty(kind, ViolationRulesSC.STAGE_LAST);
            } else if (kind == ViolationRulesSC.KILL_WARDEN) {
                cost = Lang.tr("sc.manual.world.cell.killcost", ViolationRulesSC.penalty(kind, 1), ReputationSC.KILL_WARDEN_PCT);
            } else {
                cost = String.valueOf(ViolationRulesSC.penalty(kind, 1));
            }
            if (kind == ViolationRulesSC.KILL_WARDEN) {
                guards = Lang.tr("sc.manual.world.cell.enemy", days(ReputationSC.ENEMY_TICKS));
            } else if (ViolationRulesSC.provokes(kind, ViolationRulesSC.STAGE_LAST, rule)) {
                guards = Lang.tr("sc.manual.world.cell.aggro", sec(ViolationRulesSC.aggroTicks(kind)));
            } else {
                guards = Lang.tr("sc.manual.world.cell.warn");
            }
            v.row(null, Lang.tr("sc.settle.violation." + ViolationRulesSC.KEYS[kind]), cost, guards);
        }
        e.add(v);
        e.addAll(BookContent.paras("sc.manual.world.trust.down", new Object[]{sec(ViolationRulesSC.WINDOW_TICKS),
                ViolationRulesSC.penalty(ViolationRulesSC.BREAK, ViolationRulesSC.STAGE_WATCH), ViolationRulesSC.penalty(ViolationRulesSC.BREAK, ViolationRulesSC.STAGE_LAST)},
                new Object[]{ReputationSC.ENEMY_BELOW, days(ReputationSC.ENEMY_TICKS)}));
        e.add(BookEl.para(rule ? Lang.tr("sc.manual.world.trust.aggro.on", sec(ViolationRulesSC.WINDOW_TICKS), sec(ViolationRulesSC.VIOLATION_AGGRO_TICKS))
                : Lang.tr("sc.manual.world.trust.aggro.off")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.embassyTrade", Lang.tr("sc.manual.world.embassyTrade.title")));
        return e;
    }

    /** «0-99», «ниже 0», «1000+», the local «Враг» status. */
    private static String levelRange(int lv) {
        switch (lv) {
            case ReputationSC.ENEMY:
                return Lang.tr("sc.manual.world.cell.local");
            case ReputationSC.VIOLATOR:
                return Lang.tr("sc.manual.world.cell.below", ReputationSC.STRANGER_FROM);
            case ReputationSC.STRANGER:
                return span(ReputationSC.STRANGER_FROM, ReputationSC.ACQUAINTANCE_FROM - 1);
            case ReputationSC.ACQUAINTANCE:
                return span(ReputationSC.ACQUAINTANCE_FROM, ReputationSC.PARTNER_FROM - 1);
            case ReputationSC.PARTNER:
                return span(ReputationSC.PARTNER_FROM, ReputationSC.OWN_FROM - 1);
            case ReputationSC.OWN:
                return span(ReputationSC.OWN_FROM, ReputationSC.HONOURED_FROM - 1);
            default:
                return ReputationSC.HONOURED_FROM + "+";
        }
    }

    /** One way up: sc.manual.world.trust.up.<key> and its .desc (with `args`). */
    private static void gain(BookEntry e, ItemStack icon, String key, Object... args) {
        String k = "sc.manual.world.trust.up." + key;
        e.add(BookEl.item(icon, Lang.tr(k), Lang.tr(k + ".desc", args)));
    }

    // ------------------------------------------------------------------ «Стражи купола»

    /**
     * ПГ-15..ПГ-20: the wardens' suit - the plating's share and the piercing, Regeneration and the dome's induction, the heat, the
     * shield and the Shield function, the Singular perks, the reference fight without ПЭ-1 (WardenSuitSC.fightSeconds).
     */
    private static void wardenSuit(BookEntry e) {
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardens.suithead")));
        e.add(BookEl.item(nanoPiece(1), Lang.tr("sc.manual.world.wardens.suit.plating"), Lang.tr("sc.manual.world.wardens.suit.plating.desc",
                pct(MobArmorMathSC.absorbShare(WardenSuitSC.setPoints(ArmorSuit.NANO, false))),
                pct(MobArmorMathSC.absorbShare(WardenSuitSC.setPoints(ArmorSuit.QUANTUM, false))),
                pct(MobArmorMathSC.absorbShare(WardenSuitSC.setPoints(ArmorSuit.EXO, false))),
                pct(MobArmorMathSC.absorbShare(WardenSuitSC.setPoints(ArmorSuit.SINGULAR, false))),
                n(WardenSuitSC.playerSetUnits(ArmorSuit.EXO)), n(WardenSuitSC.playerSetUnits(ArmorSuit.SINGULAR)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.suit.regen"), Lang.tr("sc.manual.world.wardens.suit.regen.desc",
                n(WardenSuitSC.regenHeal(ArmorSuit.NANO)), n(WardenSuitSC.regenHeal(ArmorSuit.QUANTUM)), n(WardenSuitSC.regenHeal(ArmorSuit.EXO)),
                n(WardenSuitSC.regenHeal(ArmorSuit.SINGULAR)), sec(WardenSuitSC.REGEN_DELAY), pct(WardenSuitSC.REGEN_SHARE),
                pct(WardenSuitSC.PAD_SHARE))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.suit.heat"), Lang.tr("sc.manual.world.wardens.suit.heat.desc",
                EmpMathSC.HEAT_PCT_POINT, EmpMathSC.HEAT_PCT_PULSE, EmpMathSC.HEAT_PCT_SIPHON, WardenSuitSC.HEAT_RESUME_PCT)));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.suit.shield"), Lang.tr("sc.manual.world.wardens.suit.shield.desc",
                n((float) WardenSuitSC.REFLECT_RANGE))));
        e.add(BookEl.item(blade(BladeType.QUANTUM), Lang.tr("sc.manual.world.wardens.suit.fight"), Lang.tr("sc.manual.world.wardens.suit.fight.desc",
                n(WardenSuitSC.REF_BLOW), n(1F / WardenSuitSC.REF_HITS_PER_SECOND), fight(NpcKindSC.SENTRY, false), fight(NpcKindSC.SENIOR_WARDEN, false),
                fight(NpcKindSC.CAPTAIN, false), fight(NpcKindSC.COMMANDANT, false))));
    }

    /** The reference fight's seconds (a Quantum blade, WardenSuitSC.REF_*), whole: `pulsed` - the whole set suppressed. */
    private static String fight(NpcKindSC rank, boolean pulsed) {
        return n(Math.round(WardenSuitSC.fightSeconds(rank, WardenSuitSC.REF_BLOW, WardenSuitSC.REF_HITS_PER_SECOND, pulsed)));
    }

    private static BookEntry wardens(BookChapter c) {
        ItemStack egg = egg(MobEntitiesSC.ID_WARDEN);
        BookEntry e = new BookEntry("world.wardens", c, egg != null ? egg : nanoPiece(0), Lang.tr("sc.manual.world.wardens.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.wardens.title"))).addAll(BookContent.paras("sc.manual.world.wardens"));

        // the ranks (ПГ-2): package N's numbers by name, the blades' and beams' caps of MobCombatSC
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.rank"), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.t.reserve"),
                Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.t.where"));
        t.row(nanoPiece(1), Lang.tr(NpcKindSC.SENTRY.langKey()), Lang.tr("sc.manual.world.cell.suit4", suitName(ArmorSuit.NANO)),
                n(EntityWardenSC.reserveOf(NpcKindSC.SENTRY)), n(stat(WARDEN, "SENTRY_HP", 60F)),
                sizesWith(false));
        t.row(new ItemStack(ModItems.ARMOR.get(ArmorSuit.QUANTUM)[1]), Lang.tr(NpcKindSC.SENIOR_WARDEN.langKey()),
                Lang.tr("sc.manual.world.cell.suit4.helium", suitName(ArmorSuit.QUANTUM)),
                n(EntityWardenSC.reserveOf(NpcKindSC.SENIOR_WARDEN)), n(stat(WARDEN, "SENIOR_HP", 70F)),
                sizesWith(true));
        // Ф3 (ПГ-11, ПГ-12): the Captain and the Commandant - package S's numbers by name, where they serve from SettlementSizeSC
        t.row(new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[1]), Lang.tr(NpcKindSC.CAPTAIN.langKey()),
                Lang.tr("sc.manual.world.cell.suit4.helium", suitName(ArmorSuit.EXO)),
                n(EntityWardenSC.reserveOf(NpcKindSC.CAPTAIN)), n(stat(WARDEN, "CAPTAIN_HP", 80F)),
                captainSizes());
        StringBuilder cities = new StringBuilder();
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            if (s.commandant) {
                cities.append(cities.length() == 0 ? "" : ", ").append(Lang.tr(s.langKey()));
            }
        }
        t.row(new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]), Lang.tr(NpcKindSC.COMMANDANT.langKey()),
                Lang.tr("sc.manual.world.cell.suit4", suitName(ArmorSuit.SINGULAR)),
                n(EntityWardenSC.reserveOf(NpcKindSC.COMMANDANT)), n(stat(WARDEN, "COMMANDANT_HP", 120F)), cities.toString());
        e.add(t);
        e.add(BookEl.item(blade(BladeType.NANO), Lang.tr(NpcKindSC.SENTRY.langKey()), Lang.tr("sc.manual.world.wardens.weapon.sentry",
                blade(BladeType.NANO).getDisplayName(), MobCombatSC.BLADE[BladeType.NANO.ordinal()], weapon(WeaponType.ION_CUTTER).getDisplayName(),
                MobCombatSC.BEAM[WeaponType.ION_CUTTER.ordinal()], sec((int) stat(BEAM, "RELOAD_TICKS", 40F)))));
        e.add(BookEl.item(blade(BladeType.QUANTUM), Lang.tr(NpcKindSC.SENIOR_WARDEN.langKey()), Lang.tr("sc.manual.world.wardens.weapon.senior",
                blade(BladeType.QUANTUM).getDisplayName(), MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()], weapon(WeaponType.PLASMA_RIFLE).getDisplayName(),
                MobCombatSC.BEAM[WeaponType.PLASMA_RIFLE.ordinal()], sec((int) stat(WARDEN, "SENIOR_AIM_TICKS", 20F)))));
        // Ф3: the Captain's heavy «Пробой» blow (ПГ-11), the Commandant's Singular Blade and ПЭ-1 Pulse at the hostiles (ПГ-12)
        e.add(BookEl.item(blade(BladeType.EXO), Lang.tr(NpcKindSC.CAPTAIN.langKey()), Lang.tr("sc.manual.world.wardens.weapon.captain",
                blade(BladeType.EXO).getDisplayName(), n(stat(WARDEN, "CAPTAIN_BLADE", 10F)), sec((int) stat(WARDEN, "HEAVY_WINDUP", 20F)),
                n(stat(WARDEN, "HEAVY_BLOW", 5F)), sec((int) stat(WARDEN, "HEAVY_COOLDOWN", 100F)))));
        String commandantGun = EmpMobGunSC.allowed() ? Lang.tr("sc.manual.world.wardens.emp.commandant", n(stat(WARDEN, "COMMANDANT_RANGE_OUT", 8F)))
                : Lang.tr("sc.manual.world.wardens.emp.commandant.off");
        e.add(BookEl.item(blade(BladeType.SINGULAR), Lang.tr(NpcKindSC.COMMANDANT.langKey()), Lang.tr("sc.manual.world.wardens.weapon.commandant",
                blade(BladeType.SINGULAR).getDisplayName(), n(stat(WARDEN, "COMMANDANT_BLADE", 12F)), n(stat(WARDEN, "PLAZA_STAY", 7F)))
                + " " + commandantGun));

        // 2026-10-18 (ПГ-15..ПГ-20): their suit works as the player's - WardenSuitSC's numbers
        wardenSuit(e);

        // their work (ПГ-3, ПГ-5, ПГ-10)
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardens.workhead")));
        e.add(BookEl.item(showcase(BlockShowcaseSC.CHARGE_PAD), Lang.tr("sc.manual.world.wardens.pad"), Lang.tr("sc.manual.world.wardens.pad.desc",
                pct(stat(WARDEN, "RECHARGE_BELOW", 0.3F)), pct(stat(WARDEN, "RECHARGE_UNTIL", 0.9F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.patrol"), Lang.tr("sc.manual.world.wardens.patrol.desc",
                n(stat(WARDEN, "PATROL_OUT", 16F)), n(stat(WARDEN, "HUNT_OUT", 24F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.help"), Lang.tr("sc.manual.world.wardens.help.desc",
                levelName(ReputationSC.PARTNER), n(stat(WARDEN, "HELP_RADIUS", 24F)))));

        // the provocation (ПГ-6..ПГ-8): ViolationRulesSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardens.provokehead")));
        e.addAll(BookContent.paras("sc.manual.world.wardens.provoke", new Object[]{minutes(ViolationRulesSC.AGGRO_TICKS)},
                new Object[]{sec(ViolationRulesSC.WARN_TICKS), ViolationRulesSC.WALK_AWAY},
                new Object[]{ViolationRulesSC.GUARD_REACH, ViolationRulesSC.leash(SettlementSizeSC.OUTPOST.radiusMax),
                        ViolationRulesSC.leash(SettlementSizeSC.SETTLEMENT.radiusMax),
                        span(ViolationRulesSC.leash(SettlementSizeSC.CITY.radiusMin), ViolationRulesSC.leash(SettlementSizeSC.CITY.radiusMax))},
                new Object[]{-ViolationRulesSC.penalty(ViolationRulesSC.HIT_WARDEN, 1)}));

        // death (ПГ-9) and ПЭ-1
        ItemStack clone = showcase(BlockShowcaseSC.CLONE_CAPSULE);
        e.add(BookEl.item(clone, clone != null ? clone.getDisplayName() : Lang.tr("tile.siliconage.showcase.cloneCapsule.name"),
                Lang.tr("sc.manual.world.wardens.clone", minutes(NpcKindSC.GUARD_RESPAWN_TICKS), SettlementSC.RESPAWN_CLEAR)
                        + " " + Lang.tr("sc.manual.world.wardens.clone.chiefs", minutes(NpcKindSC.CAPTAIN.respawnTicks()))));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.wardens.emp"), Lang.tr("sc.manual.world.wardens.emp.desc",
                fight(NpcKindSC.SENTRY, true), fight(NpcKindSC.SENIOR_WARDEN, true), fight(NpcKindSC.CAPTAIN, true), fight(NpcKindSC.COMMANDANT, true))));
        e.add(BookEl.item(egg, egg != null ? egg.getDisplayName() : mobName(EntityWardenSC.NAME), Lang.tr("sc.manual.world.wardens.egg")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.about(egg);
        return e;
    }

    /** The sizes that have guards of a rank, by name («Пост, Посёлок, Город»). */
    private static String sizesWith(boolean seniors) {
        StringBuilder sb = new StringBuilder();
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            if (seniors ? s.seniors > 0 : s.sentries > 0) {
                sb.append(sb.length() == 0 ? "" : ", ").append(Lang.tr(s.langKey()));
            }
        }
        return sb.toString();
    }

    /** Ф3: where a Captain serves - «Город, Посёлок (30%)» (SettlementSizeSC.captainPct). */
    private static String captainSizes() {
        StringBuilder sb = new StringBuilder();
        for (int i = SettlementSizeSC.values().length - 1; i >= 0; i--) {
            SettlementSizeSC s = SettlementSizeSC.values()[i];
            if (s.captainPct > 0) {
                sb.append(sb.length() == 0 ? "" : ", ").append(Lang.tr(s.langKey()))
                        .append(s.captainPct >= 100 ? "" : " (" + s.captainPct + "%)");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ «Причалы и возврат»

    private static BookEntry docks(BookChapter c) {
        ItemStack beacon = dock(BlockDockSC.BEACON);
        BookEntry e = new BookEntry("world.docks", c, beacon != null ? beacon : com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1),
                Lang.tr("sc.manual.world.docks.title"));
        StringBuilder withDock = new StringBuilder();
        for (int i = 0; i < SettlementSizeSC.SUBSTRATE_SIZES; i++) {        // Ф6: the Substrate's (an embassy's dock is its Vokzal - below)
            SettlementSizeSC s = SettlementSizeSC.values()[i];
            if (s.dock) {
                withDock.append(withDock.length() == 0 ? "" : ", ").append(Lang.tr(s.langKey()));
            }
        }
        e.add(BookEl.title(Lang.tr("sc.manual.world.docks.title"))).addAll(BookContent.paras("sc.manual.world.docks",
                new Object[]{withDock.toString()}, new Object[]{SettlementSC.DOCK_ZONE, SettlementSC.DOCK_BELOW, SettlementSC.DOCK_ABOVE}));
        e.add(BookEl.item(dock(BlockDockSC.PLATE), dockName(BlockDockSC.PLATE), Lang.tr("sc.manual.world.docks.plate")));
        e.add(BookEl.item(beacon, dockName(BlockDockSC.BEACON), Lang.tr("sc.manual.world.docks.beacon", TravelRulesSC.DOCK_SNAP, BridgeMathSC.BEACON_DISCOUNT)));
        // Ф6 (ПР-7, ПР-8, ПЖ-11): the Dispatcher inside the gate, an embassy's Vokzal and its zone
        e.add(BookEl.item(showcase2(BlockShowcase2SC.DISPATCH_CONSOLE), Lang.tr(NpcKindSC.DISPATCHER.langKey()),
                Lang.tr("sc.manual.world.docks.dispatcher", com.sc.embassy.EmbassyRulesSC.BOARD_DOCK)));
        e.add(BookEl.item(BookEmbassySC.ring(), Lang.tr("sc.manual.world.docks.vokzal"), Lang.tr("sc.manual.world.docks.vokzal.desc",
                com.sc.embassy.EmbassyLayoutSC.DOCK_ZONE, TravelRulesSC.DOCK_SNAP)));

        // the bridge (ПР-2, ПР-3)
        e.add(BookEl.head(Lang.tr("sc.manual.world.docks.bridgehead"), com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1)));
        e.addAll(BookContent.paras("sc.manual.world.docks.bridge"));

        // the way back (ПР-4..ПР-6)
        e.add(BookEl.head(Lang.tr("sc.manual.world.docks.backhead")));
        ItemStack capsule = SettlementItemsSC.capsules(1);
        String capsuleSeller = sellerLine(capsule);
        e.add(BookEl.item(capsule, capsule != null ? capsule.getDisplayName() : Lang.tr("item.siliconage.returnCapsule.name"),
                Lang.tr("sc.manual.world.docks.capsule", sec(TravelRulesSC.CAPSULE_TICKS)) + (capsuleSeller == null ? "" : " " + capsuleSeller)));
        ItemStack map = SettlementItemsSC.settlementMap == null ? null : new ItemStack(SettlementItemsSC.settlementMap);
        String mapSeller = sellerLine(map);
        e.add(BookEl.item(map, map != null ? map.getDisplayName() : Lang.tr("item.siliconage.settlementMap.name"),
                Lang.tr("sc.manual.world.docks.map", TravelRulesSC.MAP_DOCKS) + (mapSeller == null ? "" : " " + mapSeller)));
        e.add(BookEl.item(new ItemStack(Items.skull, 1, 0), Lang.tr("sc.manual.world.docks.death"),
                Lang.tr("sc.manual.world.docks.death.desc", TravelRulesSC.DEATH_SCATTER, TravelRulesSC.DEATH_CLEAR)));
        e.add(BookEl.link("bridge", Lang.tr("sc.manual.bridge.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("world.flightsBack", Lang.tr("sc.manual.world.flightsBack.title")));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        if (SettlementBlocksSC.dock != null) {
            e.about(new ItemStack(SettlementBlocksSC.dock, 1, OreDictionary.WILDCARD_VALUE));
        }
        e.about(capsule, map);
        return e;
    }

    // ================================================================== Ф3 «Броня и ЭМИ» (docs/plan-substrate-tech-f3.md §10.2)

    // ------------------------------------------------------------------ «ПЭ-1 в руках врагов и «Пробой»»

    private static BookEntry mobEmp(BookChapter c) {
        BookEntry e = new BookEntry("world.mobemp", c, new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.mobemp.title"));
        int carrier = Math.round(1F / Math.max(1e-6F, stat(JAMMER, "CARRIER_CHANCE", 0.02F)));
        e.add(BookEl.title(Lang.tr("sc.manual.world.mobemp.title"))).addAll(BookContent.paras("sc.manual.world.mobemp",
                new Object[]{mobName(EntityJammerSC.NAME), carrier, Lang.tr(NpcKindSC.COMMANDANT.langKey())}, new Object[]{ConfigSC.empImmunitySeconds}));
        if (!EmpMobGunSC.allowed()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.mobemp.off")));
        }

        // the warnings (ПБ-17): EmpMobGunSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.mobemp.warnhead")));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.emp.mode.point"), Lang.tr("sc.manual.world.mobemp.point",
                sec(EmpMobGunSC.POINT_WARN_TICKS), sec(EmpMobGunSC.POINT_EVERY_MIN), sec(EmpMobGunSC.POINT_EVERY_MAX))));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.emp.mode.pulse"), Lang.tr("sc.manual.world.mobemp.pulse",
                sec(EmpMobGunSC.PULSE_CHARGE_TICKS), EmpMobGunSC.PULSE_HUM_RANGE, EmpMobGunSC.PULSE_GROUP_MIN, n((float) EmpMobGunSC.PULSE_GROUP_RADIUS))));
        e.add(BookEl.item(new ItemStack(Blocks.stonebrick), Lang.tr("sc.manual.world.mobemp.cover"), Lang.tr("sc.manual.world.mobemp.cover.desc",
                EmpMobGunSC.RANGE)));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.mobemp.rules"), Lang.tr("sc.manual.world.mobemp.rules.desc")));

        // the personal EMP screen (ПБ-19)
        e.add(BookEl.head(Lang.tr("sc.manual.world.mobemp.screenhead"), egg(MobEntitiesSC.ID_JAMMER)));
        e.addAll(BookContent.paras("sc.manual.world.mobemp.screen", new Object[]{mobName(EntityJammerSC.NAME), Lang.tr(EliteModSC.SHIELDED.langKey()),
                sec(EliteModSC.SCREEN_RECOVER_TICKS)}));

        // the reactive machines (ПБ-20): package M's / X's numbers by name
        e.add(BookEl.head(Lang.tr("sc.manual.world.mobemp.reactivehead")));
        if (!SubstrateConfigSC.empMobTargets) {
            e.add(BookEl.dim(Lang.tr("sc.manual.world.mobemp.reactive.off", sec(EmpMathSC.NO_ARMOR_SLOW_TICKS))));
        }
        e.add(BookEl.item(egg(MobEntitiesSC.ID_REPAIR_DRONE), mobName(EntityRepairDroneSC.NAME) + ", " + mobName(EntitySentryDroneSC.NAME),
                Lang.tr("sc.manual.world.mobemp.drones")));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_NANITE), mobName(EntityNaniteSC.NAME), Lang.tr("sc.manual.world.mobemp.swarm",
                n(stat(NANITE, "PULSE_BURN_RADIUS", 5F)))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_MIMIC), mobName(EntityMimicSC.NAME), Lang.tr("sc.manual.world.mobemp.mimic",
                sec((int) stat(MIMIC, "STUN_TICKS", 60F)))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_HOLLOW_SHELL), mobName(EntityHollowShellSC.NAME), Lang.tr("sc.manual.world.mobemp.reboot",
                sec((int) stat(SHELL, "REBOOT_TICKS", 60F)))));

        // «Пробой» (ПБ-15, ПБ-16): BreakdownDamageSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.mobemp.breakdownhead"), egg(MobEntitiesSC.ID_EXECUTIONER)));
        e.addAll(BookContent.paras("sc.manual.world.mobemp.breakdown", null, new Object[]{mobName(EntityExecutionerSC.NAME),
                n(stat(EXECUTIONER, "CLEAVE_DAMAGE", 6F)), Lang.tr(NpcKindSC.CAPTAIN.langKey()), n(stat(WARDEN, "HEAVY_BLOW", 5F))}));
        e.add(BookEl.link("emp", Lang.tr("sc.manual.emp.title")));
        e.add(BookEl.link("world.renegades", Lang.tr("sc.manual.world.renegades.title")));
        e.add(BookEl.link("world.executioner", mobName(EntityExecutionerSC.NAME)));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Штурмовик и Глушитель»

    private static BookEntry renegades(BookChapter c) {
        ItemStack trooperEgg = egg(MobEntitiesSC.ID_TROOPER), jammerEgg = egg(MobEntitiesSC.ID_JAMMER);
        BookEntry e = new BookEntry("world.renegades", c, trooperEgg != null ? trooperEgg : quantumPiece(1), Lang.tr("sc.manual.world.renegades.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.renegades.title"))).addAll(BookContent.paras("sc.manual.world.renegades"));

        // the Trooper (§4.2)
        String trooper = mobName(EntityTrooperSC.NAME);
        e.add(BookEl.head(trooper, trooperEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityTrooperSC.HP), n((float) EntityTrooperSC.HP / 2F))));
        e.add(BookEl.item(quantumPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.trooper.armour", suitName(ArmorSuit.QUANTUM),
                n(stat(TROOPER, "RESERVE_DAMAGE_MIN", 40F)), n(stat(TROOPER, "RESERVE_DAMAGE_MAX", 70F)), pct(stat(TROOPER, "FAULTY_CHANCE", 0.5F)), ironPoints())));
        e.add(BookEl.item(weapon(WeaponType.PLASMA_RIFLE), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.trooper.weapon",
                blade(BladeType.QUANTUM).getDisplayName(), n(stat(TROOPER, "BLADE_DAMAGE", 8F)), pct(stat(TROOPER, "BLADE_CHANCE", 0.5F)),
                weapon(WeaponType.PLASMA_RIFLE).getDisplayName(), n(stat(TROOPER, "RIFLE_DAMAGE", 7F)), sec((int) stat(TROOPER, "AIM_TICKS", 30F)),
                sec((int) stat(TROOPER, "SHOT_EVERY_MIN", 40F)), sec((int) stat(TROOPER, "SHOT_EVERY_MAX", 60F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.trooper.dash"), Lang.tr("sc.manual.world.trooper.dash.desc", n(stat(TROOPER, "DASH_FROM", 4F)),
                n(stat(TROOPER, "DASH_TO", 8F)), sec((int) stat(TROOPER, "DASH_CUE_TICKS", 10F)), n(stat(TROOPER, "DASH_LENGTH", 6F)),
                sec((int) stat(TROOPER, "RECOVER_TICKS", 30F)), n(stat(TROOPER, "BACKSTAB_MUL", 1.5F)), sec((int) stat(TROOPER, "DASH_COOLDOWN", 100F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.trooper.where",
                weight("CANYON_WEIGHTS", MobEntitiesSC.ID_TROOPER, 30), n(stat(TROOPER, "GROUP_MIN", 1F)), n(stat(TROOPER, "GROUP_MAX", 2F)))));
        e.add(BookEl.item(loot(LOOT_QUANTUM_SCRAP), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.renegade.loot", lootName(LOOT_QUANTUM_SCRAP),
                pct(stat(TROOPER, "SCRAP_CHANCE", 0.5F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.weaponMeta(WeaponType.PLASMA_RIFLE)), Lang.tr("sc.manual.world.t.trophies"),
                Lang.tr("sc.manual.world.trooper.trophies", pct(trophyChance(TrophyTableSC.PIECE[1])), pct(trophyChance(TrophyTableSC.BLADE[1])),
                        pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.PLASMA_RIFLE.ordinal()])))));

        // the Jammer (§4.3)
        String jammer = mobName(EntityJammerSC.NAME);
        int tier = (int) stat(JAMMER, "DEFENSE_TIER", 3F);
        e.add(BookEl.head(jammer, jammerEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityJammerSC.HP), n((float) EntityJammerSC.HP / 2F))));
        e.add(BookEl.item(quantumPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.jammer.armour", suitName(ArmorSuit.QUANTUM),
                defensePct(EmpMathSC.DEFENSE_DURATION_PCT, tier), defensePct(EmpMathSC.DEFENSE_SIPHON_PCT, tier),
                n(stat(JAMMER, "RESERVE_DAMAGE_MIN", 40F)), n(stat(JAMMER, "RESERVE_DAMAGE_MAX", 60F)), sec(EliteModSC.SCREEN_RECOVER_TICKS))));
        e.add(BookEl.item(weapon(WeaponType.PULSE_EMITTER), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.jammer.weapon",
                weapon(WeaponType.PULSE_EMITTER).getDisplayName(), sec((int) stat(JAMMER, "AIM_TICKS", 20F)), n(stat(JAMMER, "BURST", 3F)),
                n(stat(JAMMER, "BURST_DAMAGE", 3F)), sec((int) stat(JAMMER, "BURST_GAP_TICKS", 5F)), sec((int) stat(JAMMER, "RELOAD_TICKS", 60F)))));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.jammer.carrier"), EmpMobGunSC.allowed()
                ? Lang.tr("sc.manual.world.jammer.carrier.on", Math.round(1F / Math.max(1e-6F, stat(JAMMER, "CARRIER_CHANCE", 0.02F))),
                weapon(WeaponType.ION_CUTTER).getDisplayName())
                : Lang.tr("sc.manual.world.jammer.carrier.off", weapon(WeaponType.ION_CUTTER).getDisplayName())));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.jammer.keep"), Lang.tr("sc.manual.world.jammer.keep.desc", n(stat(JAMMER, "KEEP_MIN", 8F)),
                n(stat(JAMMER, "KEEP_MAX", 14F)), pct(stat(JAMMER, "ESCORT_CHANCE", 0.5F)), trooper)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.jammer.where",
                weight("CANYON_WEIGHTS", MobEntitiesSC.ID_JAMMER, 15))));
        e.add(BookEl.item(loot(LOOT_QUANTUM_SCRAP), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.jammer.loot", lootName(LOOT_QUANTUM_SCRAP),
                pct(stat(JAMMER, "SCRAP_CHANCE", 0.4F)), pct(stat(JAMMER, "MODULE_CHANCE", 0.05F)), pct(stat(JAMMER, "CARRIER_MODULE_CHANCE", 0.25F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.weaponMeta(WeaponType.PULSE_EMITTER)), Lang.tr("sc.manual.world.t.trophies"),
                Lang.tr("sc.manual.world.jammer.trophies", pct(trophyChance(TrophyTableSC.PIECE[1])),
                        pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.PULSE_EMITTER.ordinal()])), pct(trophyChance(stat(TROPHIES, "PE1", 0.02F))))));
        e.addAll(BookContent.paras("sc.manual.world.renegades.tips", new Object[]{sec(empTicks(ArmorSuit.QUANTUM, EmpMode.POINT))},
                new Object[]{jammer}));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.elites", Lang.tr("sc.manual.world.elites.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(trooperEgg, jammerEgg, loot(LOOT_QUANTUM_SCRAP));
        return e;
    }

    // ------------------------------------------------------------------ «Пустые оболочки и дроны»

    private static BookEntry shells(BookChapter c) {
        ItemStack shellEgg = egg(MobEntitiesSC.ID_HOLLOW_SHELL), droneEgg = egg(MobEntitiesSC.ID_REPAIR_DRONE);
        BookEntry e = new BookEntry("world.shells", c, shellEgg != null ? shellEgg : exoPiece(1), Lang.tr("sc.manual.world.shells.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.shells.title"))).addAll(BookContent.paras("sc.manual.world.shells", null,
                new Object[]{RuinLayoutSC.NODE_HALF, n(stat(SHELL, "LEASH", 48F))}));

        String shell = mobName(EntityHollowShellSC.NAME);
        e.add(BookEl.head(shell, shellEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityHollowShellSC.HP),
                n((float) EntityHollowShellSC.HP / 2F))));
        e.add(BookEl.item(exoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.shell.armour", suitName(ArmorSuit.EXO),
                n(stat(SHELL, "RESERVE_DAMAGE_MIN", 70F)), n(stat(SHELL, "RESERVE_DAMAGE_MAX", 110F)), pct(stat(SHELL, "RUSTY_CHANCE", 0.3F)), ironPoints())));
        e.add(BookEl.item(blade(BladeType.EXO), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.shell.weapon",
                blade(BladeType.EXO).getDisplayName(), pct(stat(SHELL, "EXO_BLADE_CHANCE", 0.15F)), n(stat(SHELL, "EXO_BLADE_DAMAGE", 10F)),
                blade(BladeType.QUANTUM).getDisplayName(), pct(stat(SHELL, "QUANTUM_BLADE_CHANCE", 0.25F)), n(stat(SHELL, "QUANTUM_BLADE_DAMAGE", 8F)),
                n(stat(SHELL, "FIST_DAMAGE", 6F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.shell.reboot"), Lang.tr("sc.manual.world.shell.reboot.desc", sec((int) stat(SHELL, "REBOOT_TICKS", 60F)))));
        // МБ-4 (audit 2026-10-20): a patrol is as big as ПС-7 lets it be - at most QUANTUM_NEAR_MAX of the Quantum tier near a player
        float shellHi = Math.min(stat(SHELL, "GROUP_MAX", 3F), com.sc.mob.SpawnRulesSC.QUANTUM_NEAR_MAX);
        float shellLo = Math.min(stat(SHELL, "GROUP_MIN", 2F), shellHi);
        e.add(BookEl.item(ruin(BlockRuinSC.MACHINE_HUSK), Lang.tr("sc.manual.world.t.where"), shellLo == shellHi
                ? Lang.tr("sc.manual.world.shell.where.one", weight("NODE_WEIGHTS", MobEntitiesSC.ID_HOLLOW_SHELL, 70), n(shellHi))
                : Lang.tr("sc.manual.world.shell.where", weight("NODE_WEIGHTS", MobEntitiesSC.ID_HOLLOW_SHELL, 70), n(shellLo), n(shellHi))));
        e.add(BookEl.item(loot(LOOT_FIRMWARE), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.shell.loot", lootName(LOOT_FIRMWARE),
                n(stat(SHELL, "FIRMWARE_MIN", 1F)), n(stat(SHELL, "FIRMWARE_MAX", 3F)), pct(stat(SHELL, "HELIUM_CELL_CHANCE", 0.1F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.bladeMeta(BladeType.EXO)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.shell.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[2])), pct(trophyChance(TrophyTableSC.BLADE[2])), pct(trophyChance(TrophyTableSC.BLADE[1])))));

        // the Repair Drone (§4.4)
        String drone = mobName(EntityRepairDroneSC.NAME);
        e.add(BookEl.head(drone, droneEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityRepairDroneSC.HP),
                n((float) EntityRepairDroneSC.HP / 2F))));
        e.add(BookEl.item(droneEgg, Lang.tr("sc.manual.world.drone.repair"), Lang.tr("sc.manual.world.drone.repair.desc",
                pct(stat(RULES, "DRONE_PATROL_CHANCE", 0.3F)), n(stat(REPAIR_DRONE, "REPAIR_UNITS", 3F)), sec((int) stat(REPAIR_DRONE, "REPAIR_EVERY", 20F)),
                sec((int) stat(REPAIR_DRONE, "ORPHAN_TICKS", 600F)))));
        e.addAll(BookContent.paras("sc.manual.world.shells.tips", new Object[]{siphonSeconds(ArmorSuit.EXO)},
                new Object[]{sec(empTicks(ArmorSuit.EXO, EmpMode.POINT)), sec(EmpMathSC.durationTicks(ArmorSuit.EXO, EmpMode.POINT, false, 0, true,
                        ConfigSC.empDurationMultiplier))}));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(shellEgg, droneEgg);
        return e;
    }

    // ------------------------------------------------------------------ «Экзо-палач»

    private static BookEntry executioner(BookChapter c) {
        String name = mobName(EntityExecutionerSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_EXECUTIONER);
        BookEntry e = new BookEntry("world.executioner", c, egg != null ? egg : exoPiece(1), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.executioner"));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityExecutionerSC.HP),
                n((float) EntityExecutionerSC.HP / 2F))));
        e.add(BookEl.item(exoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.executioner.armour", suitName(ArmorSuit.EXO),
                n(stat(EXECUTIONER, "RESERVE_DAMAGE_MIN", 100F)), n(stat(EXECUTIONER, "RESERVE_DAMAGE_MAX", 140F)))));
        e.add(BookEl.item(blade(BladeType.EXO), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.executioner.weapon",
                blade(BladeType.EXO).getDisplayName(), n(stat(EXECUTIONER, "BLADE_DAMAGE", 11F)))));
        int windup = (int) stat(EXECUTIONER, "WINDUP_TICKS", 20F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.executioner.cleave"), Lang.tr("sc.manual.world.executioner.cleave.desc",
                n(stat(EXECUTIONER, "CLEAVE_REACH", 3F)), sec(windup), n(stat(EXECUTIONER, "CLEAVE_HALF_ANGLE", 60F)), n(stat(EXECUTIONER, "CLEAVE_DAMAGE", 6F)),
                sec((int) stat(EXECUTIONER, "CLEAVE_COOLDOWN", 100F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.executioner.elite"), Lang.tr("sc.manual.world.executioner.elite.desc")));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.executioner.where",
                weight("CANYON_WEIGHTS", MobEntitiesSC.ID_EXECUTIONER, 3), n(stat(RULES, "EXECUTIONER_MIN_DIST", 1000F)))));
        e.add(BookEl.item(loot(LOOT_SHIELD_CORE), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.executioner.loot", lootName(LOOT_EXO_SCRAP),
                n(stat(EXECUTIONER, "SCRAP_MIN", 1F)), n(stat(EXECUTIONER, "SCRAP_MAX", 2F)), lootName(LOOT_SHIELD_CORE),
                pct(stat(EXECUTIONER, "CORE_CHANCE", 0.15F)), pct(stat(EXECUTIONER, "HELIUM_CELL_CHANCE", 0.5F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.bladeMeta(BladeType.EXO)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.executioner.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[2])), pct(trophyChance(TrophyTableSC.BLADE[2])))));
        int chestPoint = EmpMathSC.durationTicks(ArmorSuit.EXO, EmpMode.POINT, false, 0, true, ConfigSC.empDurationMultiplier);
        e.addAll(BookContent.paras("sc.manual.world.executioner.tips", new Object[]{sec(windup)}, new Object[]{sec(chestPoint)}));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.elites", Lang.tr("sc.manual.world.elites.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(egg, loot(LOOT_EXO_SCRAP), loot(LOOT_SHIELD_CORE));
        return e;
    }

    // ------------------------------------------------------------------ «Рой, дрон-охранник, мимик»

    private static BookEntry noise(BookChapter c) {
        ItemStack swarmEgg = egg(MobEntitiesSC.ID_NANITE), droneEgg = egg(MobEntitiesSC.ID_SENTRY_DRONE), mimicEgg = egg(MobEntitiesSC.ID_MIMIC);
        BookEntry e = new BookEntry("world.noise", c, swarmEgg != null ? swarmEgg : icon(), Lang.tr("sc.manual.world.noise.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.noise.title"))).addAll(BookContent.paras("sc.manual.world.noise"));

        // the nanite swarm (§4.6)
        e.add(BookEl.head(mobName(EntityNaniteSC.NAME), swarmEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.swarm.hp", n((float) EntityNaniteSC.HP),
                n(stat(NANITE, "SWARM_MIN", 6F)), n(stat(NANITE, "SWARM_MAX", 10F)), n(stat(NANITE, "SWARM_CAP", 12F)))));
        e.add(BookEl.item(quantumPiece(1), Lang.tr("sc.manual.world.swarm.drain"), Lang.tr("sc.manual.world.swarm.drain.desc",
                pct(stat(NANITE, "ARMOUR_DRAIN", 0.02F)), n(stat(NANITE, "ARMOUR_REACH", 2F)), n(stat(NANITE, "MACHINE_REACH", 4F)),
                pct(stat(NANITE, "MACHINE_DRAIN", 0.02F)), BookContent.num((long) stat(NANITE, "MACHINE_DRAIN_CAP", 2000F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.swarm.split"), Lang.tr("sc.manual.world.swarm.split.desc", sec((int) stat(NANITE, "SPLIT_TICKS", 200F)),
                pct(stat(NANITE, "ARROW_MUL", 0.2F)))));
        e.add(BookEl.item(loot(LOOT_NANITE_DUST), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.swarm.loot", lootName(LOOT_NANITE_DUST),
                pct(stat(NANITE, "DUST_CHANCE", 0.5F)), pct(stat(NANITE, "TRANSISTOR_CHANCE", 0.04F)), pct(stat(NANITE, "MEMORY_CHIP_CHANCE", 0.01F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.swarm.where",
                weight("CANYON_WEIGHTS", MobEntitiesSC.ID_NANITE, 5), weight("NODE_WEIGHTS", MobEntitiesSC.ID_NANITE, 10))));

        // the feral sentry drone (§4.7)
        e.add(BookEl.head(mobName(EntitySentryDroneSC.NAME), droneEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.sentry.hp", n((float) EntitySentryDroneSC.HP),
                n(stat(SENTRY_DRONE, "NATURAL_ARMOUR", 6F)))));
        e.add(BookEl.item(weapon(WeaponType.ION_CUTTER), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.sentry.weapon",
                weapon(WeaponType.ION_CUTTER).getDisplayName(), n(stat(SENTRY_DRONE, "BEAM_DAMAGE", 4F)), sec((int) stat(SENTRY_DRONE, "RELOAD_TICKS", 40F)),
                sec((int) stat(SENTRY_DRONE, "SEARCHLIGHT_TICKS", 20F)), n(stat(SENTRY_DRONE, "RANGE", 16F)), n(stat(SENTRY_DRONE, "CALL_RADIUS", 24F)))));
        e.add(BookEl.item(ruin(BlockRuinSC.RUIN_METAL), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.sentry.where",
                weight("NODE_WEIGHTS", MobEntitiesSC.ID_SENTRY_DRONE, 20), n(stat(SENTRY_DRONE, "HOME_MARGIN", 8F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.sentry.loot", pct(stat(SENTRY_DRONE, "SENSOR_CHANCE", 0.05F)),
                pct(stat(SENTRY_DRONE, "LENS_CHANCE", 0.01F)), pct(stat(SENTRY_DRONE, "CONTROLLER_CHANCE", 0.008F)))));

        // the mimic machine (§4.8)
        e.add(BookEl.head(mobName(EntityMimicSC.NAME), mimicEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityMimicSC.HP), n((float) EntityMimicSC.HP / 2F))));
        e.add(BookEl.item(com.sc.block.BlockMachineSC.stackOf(MachineType.CRUSHER, 1), Lang.tr("sc.manual.world.mimic.sleep"),
                Lang.tr("sc.manual.world.mimic.sleep.desc", n(stat(MIMIC, "WAKE_RADIUS", 3F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.mimic.weapon", n(stat(MIMIC, "CHEW_DAMAGE", 6F)),
                sec((int) stat(MIMIC, "CHEW_EVERY", 20F)))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.mimic.loot",
                n(stat(MIMIC, "LOOT_MIN", 1F)), n(stat(MIMIC, "LOOT_MAX", 2F)), pct(stat(MIMIC, "COMPONENT_CHANCE", 0.25F)))));
        e.addAll(BookContent.paras("sc.manual.world.noise.tips"));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.about(swarmEgg, droneEgg, mimicEgg, loot(LOOT_NANITE_DUST));
        return e;
    }

    // ------------------------------------------------------------------ «Элита»

    private static BookEntry elites(BookChapter c) {
        BookEntry e = new BookEntry("world.elites", c, quantumPiece(0), Lang.tr("sc.manual.world.elites.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.elites.title"))).addAll(BookContent.paras("sc.manual.world.elites",
                new Object[]{EliteModSC.CHANCE_PCT, EliteModSC.PER_STEP_PCT, EliteModSC.STEP_BLOCKS, EliteModSC.MAX_EXTRA_PCT, mobName(EntityExecutionerSC.NAME)},
                new Object[]{Lang.tr("sc.elite.name", Lang.tr(EliteModSC.COMMANDER.langKey()), mobName(EntityTrooperSC.NAME)), n(TrophyTableSC.ELITE_MUL),
                        n(TrophyTableSC.EVENT_CAP)},
                new Object[]{n(stat(RULES, "ELITE_MAX", 1F)), n(stat(RULES, "ELITE_RADIUS", 128F))}));

        // the modifiers (ПС-21): EliteModSC's table, package A's rhythm by name
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.modifier"), Lang.tr("sc.manual.world.t.does"));
        for (EliteModSC m : EliteModSC.values()) {
            t.row(null, Lang.tr(m.langKey()), eliteLine(m));
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.elites.pick", mobName(EntityJammerSC.NAME), Lang.tr(EliteModSC.SHIELDED.langKey()),
                Lang.tr(EliteModSC.VETERAN.langKey()))));
        e.addAll(BookContent.paras("sc.manual.world.elites.tips", new Object[]{Lang.tr(EliteModSC.AVENGER.langKey()), sec(EliteModSC.AVENGER_FUSE_TICKS)},
                new Object[]{Lang.tr(EliteModSC.COMMANDER.langKey())}));
        // Ф5 (ПС-23): an elite killed by a player may drop the Arena Coordinator
        ItemStack coordinator = com.sc.finale.FinaleItemsSC.arenaCoordinator(1);
        e.add(BookEl.item(coordinator, coordinator != null ? coordinator.getDisplayName() : Lang.tr("item.siliconage.arenaCoordinator.name"),
                Lang.tr("sc.manual.world.elites.coordinator", pct(MobDropsSC.ARENA_COORDINATOR_CHANCE))));
        e.add(BookEl.link("world.arena", Lang.tr("sc.manual.world.arena.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.add(BookEl.link("world.executioner", mobName(EntityExecutionerSC.NAME)));
        return e;
    }

    /** sc.manual.world.elite.<key> with its numbers (EliteModSC, EmpMathSC; the Commander's order rhythm - package A's numbers of EliteModSC by name). */
    static String eliteLine(EliteModSC m) {
        String k = "sc.manual.world.elite." + m.key;
        switch (m) {
            case COMMANDER:
                return Lang.tr(k, sec((int) stat(ELITE_MOD, "ORDER_EVERY_TICKS", 100F)), sec(EliteModSC.ORDER_TICKS), EliteModSC.COMMANDER_RADIUS,
                        pct(EliteModSC.COMMANDER_BONUS), sec((int) stat(ELITE_MOD, "ORDER_BUFF_TICKS", 200F)), pct(EliteModSC.COMMANDER_BONUS));
            case CHARGED:
                return Lang.tr(k, pct(EliteModSC.CHARGED_BONUS));
            case SHIELDED:
                return Lang.tr(k, sec(EliteModSC.SCREEN_RECOVER_TICKS));
            case VETERAN:
                return Lang.tr(k, pct(1F - EmpMathSC.HELIUM_DURATION_MUL));
            case REPAIRER:
                return Lang.tr(k, n(EliteModSC.REPAIR_UNITS), EliteModSC.REPAIR_RADIUS);
            case AVENGER:
                return Lang.tr(k, sec(EliteModSC.AVENGER_FUSE_TICKS), n(EliteModSC.AVENGER_RADIUS));
            default:
                return Lang.tr(k);
        }
    }

    // ------------------------------------------------------------------ «Мёртвые Узлы и литографии»

    private static BookEntry ruins(BookChapter c) {
        ItemStack metal = ruin(BlockRuinSC.RUIN_METAL);
        BookEntry e = new BookEntry("world.ruins", c, metal != null ? metal : icon(), Lang.tr("sc.manual.world.ruins.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.ruins.title"))).addAll(BookContent.paras("sc.manual.world.ruins",
                new Object[]{SubstrateTerrainSC.GRID, RuinLayoutSC.NODE_HALF},
                new Object[]{RuinLayoutSC.LITHO_CHANCE_PCT, RuinLayoutSC.HALL, RuinLayoutSC.HALL, RuinLayoutSC.HALL_H,
                        n(stat(RUIN_LAYOUT, "FLOOR_MIN", 30F)), n(stat(RUIN_LAYOUT, "FLOOR_MAX", 40F)), RuinLayoutSC.LITHO_JITTER}));
        if (!com.sc.ruin.RuinSC.generates()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.ruins.off")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.ruins.old")));

        // the Dead Node (ПУ-1, ПУ-2, §8.5, §8.6)
        e.add(BookEl.head(Lang.tr("sc.manual.world.ruins.nodehead"), ruin(BlockRuinSC.MACHINE_HUSK)));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_HOLLOW_SHELL), Lang.tr("sc.manual.world.ruins.patrols"), Lang.tr("sc.manual.world.ruins.patrols.desc",
                mobName(EntityHollowShellSC.NAME), weight("NODE_WEIGHTS", MobEntitiesSC.ID_HOLLOW_SHELL, 70), mobName(EntitySentryDroneSC.NAME),
                weight("NODE_WEIGHTS", MobEntitiesSC.ID_SENTRY_DRONE, 20), mobName(EntityNaniteSC.NAME), weight("NODE_WEIGHTS", MobEntitiesSC.ID_NANITE, 10),
                mobName(EntityMarauderSC.NAME), weight("NODE_WEIGHTS", MobEntitiesSC.ID_MARAUDER, 10))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.ruins.chests"), Lang.tr("sc.manual.world.ruins.node.loot",
                n(stat(RUIN_LOOT, "NODE_CHESTS_MIN", 1F)), n(stat(RUIN_LOOT, "NODE_CHESTS_MAX", 3F)), pct(stat(RUIN_LOOT, "NODE_CRUMB_CHANCE", 0.03F)))));

        // the lithograph (ПУ-4..ПУ-6)
        e.add(BookEl.head(Lang.tr("sc.manual.world.ruins.lithohead"), ruin(BlockRuinSC.BURNT_PANEL)));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_MIMIC), Lang.tr("sc.manual.world.ruins.residents"), Lang.tr("sc.manual.world.ruins.residents.desc",
                n(stat(LITHO, "MIMICS_MIN", 2F)), n(stat(LITHO, "MIMICS_MAX", 4F)), mobName(EntityMimicSC.NAME), n(stat(LITHO, "DRONES_MIN", 2F)),
                n(stat(LITHO, "DRONES_MAX", 3F)), mobName(EntitySentryDroneSC.NAME))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.ruins.chests"), Lang.tr("sc.manual.world.ruins.litho.loot",
                n(stat(RUIN_LOOT, "LITHO_CHESTS_MIN", 2F)), n(stat(RUIN_LOOT, "LITHO_CHESTS_MAX", 4F)), pct(stat(RUIN_LOOT, "LITHO_CHIP_CHANCE", 0.05F)),
                pct(stat(RUIN_LOOT, "LITHO_MODULE_CHANCE", 0.02F)))));

        // the ruin blocks (ПУ-2, §8.3)
        e.add(BookEl.head(Lang.tr("sc.manual.world.ruins.blockshead")));
        for (int m = 0; m < BlockRuinSC.NAMES.length; m++) {
            e.add(BookEl.item(ruin(m), ruinName(m), ruinLine(m)));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.ruins.blocks.note")));
        e.add(BookEl.link("world.shells", Lang.tr("sc.manual.world.shells.title")));
        e.add(BookEl.link("world.noise", Lang.tr("sc.manual.world.noise.title")));
        e.add(BookEl.link("world.terrain", Lang.tr("sc.manual.world.terrain.title")));
        if (RuinBlocksSC.ruin != null) {
            e.about(new ItemStack(RuinBlocksSC.ruin, 1, OreDictionary.WILDCARD_VALUE));
        }
        return e;
    }

    // ------------------------------------------------------------------ «Восстановление трофеев»

    private static BookEntry restore(BookChapter c) {
        ItemStack icon = trophy(TrophyTableSC.armorMeta(ArmorSuit.QUANTUM, 1));
        BookEntry e = new BookEntry("world.restore", c, icon != null ? icon : quantumPiece(1), Lang.tr("sc.manual.world.restore.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.restore.title"))).addAll(BookContent.paras("sc.manual.world.restore", null,
                new Object[]{Lang.tr(NpcKindSC.ARMOURER.langKey())},
                new Object[]{sec(TrophyRestoreSC.CONFIRM_TICKS), BookContent.num(TrophyRestoreSC.STATION_EU)},
                new Object[]{clotName(), BookContent.num(TrophyRestoreSC.SINGULAR_STATION_EU)}));

        // the prices (ПТ-12): the Armourer's OUT_RESTORE deals of TradeTableSC, the station's - his components without the chips + the EU
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.trophy"), Lang.tr(NpcKindSC.ARMOURER.langKey()), Lang.tr("sc.manual.world.t.level"),
                Lang.tr("sc.manual.world.t.station"));
        int rows = 0;
        for (TradeOfferSC o : TradeTableSC.offers(NpcKindSC.ARMOURER)) {
            if (o.outKind != OUT_RESTORE || o.in == null || o.in.length == 0 || o.in[0] == null) {
                continue;
            }
            String what = matcherText(o.in[0]);
            t.row(o.in[0].display(), what != null ? what : Lang.tr(o.langKey()), sides(o.in, 1, false), levelName(o.minLevel), stationPrice(o));
            rows++;
        }
        ItemStack singular = trophy(TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 1));
        t.row(singular, Lang.tr("sc.manual.world.restore.singular"), "-", "-", Lang.tr("sc.manual.world.restore.singular.price", clotName(),
                BookContent.num(TrophyRestoreSC.SINGULAR_STATION_EU)));
        // Ф5 (ПТ-26): the Warden's ПЭ-1М - the Singular station, a ПЭ-1М without modules
        t.row(trophy(TrophyTableSC.META_PE1M), Lang.tr("sc.manual.world.restore.pe1m"), "-", "-", Lang.tr("sc.manual.world.restore.pe1m.price", clotName(),
                BookContent.num(TrophyRestoreSC.SINGULAR_STATION_EU)));
        e.add(t);
        if (rows == 0) {
            e.add(BookEl.dim(Lang.tr("sc.manual.world.restore.none")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.restore.note")));

        // the Salvager buys a Nano trophy instead (ПО-12) - its deal, if the table has it
        e.add(BookEl.item(egg(MobEntitiesSC.ID_TUNER), Lang.tr(NpcKindSC.SALVAGER.langKey()), Lang.tr("sc.manual.world.restore.salvager")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        return e;
    }

    // ================================================================== Ф4 «Опасный мир» (docs/plan-substrate-tech-f4.md §10.2)

    // ------------------------------------------------------------------ «Гряды, Окисные поля, Разлом»

    private static BookEntry biomes4(BookChapter c) {
        ItemStack glass = wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.GLASS);
        BookEntry e = new BookEntry("world.biomes4", c, glass != null ? glass : icon(), Lang.tr("sc.manual.world.biomes4.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.biomes4.title"))).addAll(BookContent.paras("sc.manual.world.biomes4",
                new Object[]{SubstrateCellsSC.CELL, SubstrateCellsSC.CELL, SubstrateCellsSC.INNER_MARGIN},
                new Object[]{SubstrateCellsSC.RIFT_PCT, SubstrateCellsSC.RIFT_MIN_DIST, SubstrateCellsSC.RIDGES_PCT, SubstrateCellsSC.OXIDE_PCT}));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.biomes4.old")));                   // ПМ-11: the land of Ф1..Ф3 is never torn
        String relief = Lang.tr("sc.manual.world.biomes4.relief"), riches = Lang.tr("sc.manual.world.biomes4.riches"),
                danger = Lang.tr("sc.manual.world.biomes4.danger"), who = Lang.tr("sc.manual.world.biomes4.who");
        ItemStack dosimeter = new ItemStack(ModItems.dosimeter);

        // the Quartzite Ridges (ПМ-12)
        e.add(BookEl.head(Lang.tr("sc.dim.biome.ridges"), rock(SubstrateBlocksSC.QUARTZ_TURF)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.CRYSTALLITE), relief, Lang.tr("sc.manual.world.biomes4.ridges.relief", SubstrateWildTerrainSC.RIDGE_MAX,
                SubstrateWildTerrainSC.RIDGE_BLEND)));
        e.add(BookEl.item(ore(OreEntry.TANTALITE), riches, Lang.tr("sc.manual.world.biomes4.ridges.riches", multiplier(SubstrateWildTerrainSC.RIDGE_VEIN_MUL))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_COLOSSUS), who, Lang.tr("sc.manual.world.biomes4.ridges.who")));

        // the Oxide Fields (ПМ-13)
        e.add(BookEl.head(Lang.tr("sc.dim.biome.oxide"), rock(SubstrateBlocksSC.OXIDE_GOLD)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), relief, Lang.tr("sc.manual.world.biomes4.oxide.relief", SubstrateWildTerrainSC.DUNE_MIN,
                SubstrateWildTerrainSC.DUNE_MAX)));
        e.add(BookEl.item(wild(SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.FUMAROLE), riches, Lang.tr("sc.manual.world.biomes4.oxide.riches",
                SubstrateWildGenSC.FUMAROLE_CHANCE, SubstrateWildGenSC.RTG_CHANCE)));
        e.add(BookEl.item(dosimeter, danger, Lang.tr("sc.manual.world.biomes4.oxide.danger", n(SubstrateRadiationSC.backgroundLevel(com.sc.dim.BiomeSubstrateSC.OXIDE)))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_OVERHEATED), who, Lang.tr("sc.manual.world.biomes4.oxide.who")));

        // the Rift (ПМ-14)
        e.add(BookEl.head(Lang.tr("sc.dim.biome.rift"), glass));
        e.add(BookEl.item(glass, relief, Lang.tr("sc.manual.world.biomes4.rift.relief", SubstrateWildTerrainSC.RIFT_RIM, SubstrateWildTerrainSC.RIFT_FLOOR,
                SubstrateWildTerrainSC.ISLANDS_MIN, SubstrateWildTerrainSC.ISLANDS_MAX, SubstrateWildTerrainSC.ISLAND_MIN_Y, SubstrateWildTerrainSC.ISLAND_MAX_Y)));
        e.add(BookEl.item(wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.PILLAR_CORE), Lang.tr("sc.manual.world.biomes4.pillar"),
                Lang.tr("sc.manual.world.biomes4.pillar.desc", SubstrateWildTerrainSC.PILLAR_RADIUS, SubstrateWildTerrainSC.PILLAR_TOP)));
        e.add(BookEl.item(dosimeter, danger, Lang.tr("sc.manual.world.biomes4.rift.danger", n(SubstrateRadiationSC.backgroundLevel(com.sc.dim.BiomeSubstrateSC.RIFT)))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_ECLIPSE), who, Lang.tr("sc.manual.world.biomes4.rift.who", EliteModSC.chancePct(0, true))));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.mp.f4", biomeId(BiomeSubstrateSC.RIDGES), biomeId(BiomeSubstrateSC.OXIDE), biomeId(BiomeSubstrateSC.RIFT))));
        e.add(BookEl.link("world.radiation", Lang.tr("sc.manual.world.radiation.title")));
        e.add(BookEl.link("world.resources4", Lang.tr("sc.manual.world.resources4.title")));
        e.add(BookEl.link("world.terrain", Lang.tr("sc.manual.world.terrain.title")));
        e.add(BookEl.link("world.eclipse", mobName(EntityEclipseSC.NAME)));
        if (SubstrateWildBlocksSC.glass != null) {
            e.about(new ItemStack(SubstrateWildBlocksSC.glass, 1, OreDictionary.WILDCARD_VALUE));
        }
        return e;
    }

    // ------------------------------------------------------------------ «Фоновая радиация»

    private static BookEntry radiation(BookChapter c) {
        ItemStack dosimeter = new ItemStack(ModItems.dosimeter);
        BookEntry e = new BookEntry("world.radiation", c, dosimeter, Lang.tr("sc.manual.world.radiation.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.radiation.title"))).addAll(BookContent.paras("sc.manual.world.radiation", null,
                new Object[]{n(StormRulesSC.EMISSION_RADIATION_MUL)}));

        // the background by biome (ПА-1): SubstrateRadiationSC
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.biome"), Lang.tr("sc.manual.world.t.background"));
        for (int k = 0; k < BiomeSubstrateSC.KINDS; k++) {
            float lv = SubstrateRadiationSC.backgroundLevel(k);
            t.row(biomeIcon(k), Lang.tr("sc.dim.biome." + BiomeSubstrateSC.KEYS[k]), lv > 0F ? n(lv) : Lang.tr("sc.manual.world.cell.none"));
        }
        e.add(t);

        // the sources on top (ПА-2)
        e.add(BookEl.head(Lang.tr("sc.manual.world.radiation.sourceshead")));
        e.add(BookEl.item(wild(SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.CRACKED_RTG), wildName(SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.CRACKED_RTG),
                Lang.tr("sc.manual.world.radiation.rtg", n(SubstrateRadiationSC.rtgLevel()), SubstrateRadiationSC.RTG_RADIUS,
                        n(SubstrateRadiationSC.backgroundLevel(com.sc.dim.BiomeSubstrateSC.OXIDE) + SubstrateRadiationSC.rtgLevel()))));
        e.add(BookEl.item(wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.PILLAR_CORE), Lang.tr("sc.manual.world.biomes4.pillar"),
                Lang.tr("sc.manual.world.radiation.pillar", n(SubstrateRadiationSC.pillarLevel()), SubstrateRadiationSC.PILLAR_RADIUS,
                        n(SubstrateRadiationSC.backgroundLevel(com.sc.dim.BiomeSubstrateSC.RIFT) + SubstrateRadiationSC.pillarLevel()))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_OVERHEATED), mobName(EntityOverheatedSC.NAME), Lang.tr("sc.manual.world.radiation.overheated",
                n(stat(OVERHEATED, "RADIATION_LEVEL", 2F)), n(stat(OVERHEATED, "RADIATION_RADIUS", 4F)))));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.radiation.blocks")));

        // the protection (ПА-3): everything the radiation already has
        e.add(BookEl.head(Lang.tr("sc.manual.world.radiation.protecthead")));
        e.add(BookEl.item(new ItemStack(ModItems.leadSuit[1]), Lang.tr("sc.manual.world.radiation.lead"), Lang.tr("sc.manual.world.radiation.lead.desc")));
        e.add(BookEl.item(quantumPiece(2), Lang.tr("sc.manual.world.radiation.suits"), Lang.tr("sc.manual.world.radiation.suits.desc")));
        e.add(BookEl.item(new ItemStack(ModBlocks.fieldGeneratorSC), Lang.tr("sc.manual.world.radiation.field"), Lang.tr("sc.manual.world.radiation.field.desc")));
        e.add(BookEl.item(core(), Lang.tr("sc.manual.world.radiation.dome"), Lang.tr("sc.manual.world.radiation.dome.desc")));
        e.add(BookEl.item(new ItemStack(ModBlocks.shower), Lang.tr("sc.manual.world.radiation.shower"), Lang.tr("sc.manual.world.radiation.shower.desc",
                n(com.sc.tileentity.TileEntityShowerSC.DOSE_PER_SECOND))));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.radiation.exo")));
        e.add(BookEl.link("radiation", Lang.tr("sc.manual.safety.radiationhead")));
        e.add(BookEl.link("world.biomes4", Lang.tr("sc.manual.world.biomes4.title")));
        e.add(BookEl.link("world.haze", Lang.tr("sc.manual.world.haze.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Фумаролы, трещины, жеоды, гейзеры»

    private static BookEntry resources4(BookChapter c) {
        ItemStack fumarole = wild(SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.FUMAROLE);
        BookEntry e = new BookEntry("world.resources4", c, fumarole != null ? fumarole : icon(), Lang.tr("sc.manual.world.resources4.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.resources4.title"))).addAll(BookContent.paras("sc.manual.world.resources4"));

        // the vents (ПА-4..ПА-7): their tiles' numbers
        e.add(BookEl.head(Lang.tr("sc.manual.world.resources4.ventshead"), fumarole));
        for (int m = 0; m < SubstrateWildBlocksSC.VENT_NAMES.length; m++) {
            e.add(BookEl.item(wild(SubstrateWildBlocksSC.vent, m), wildName(SubstrateWildBlocksSC.vent, m), wildLine(SubstrateWildBlocksSC.vent, m)));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.resources4.vents.note")));

        // the monocrystal geodes (ПМ-16)
        e.add(BookEl.head(Lang.tr("sc.manual.world.resources4.geodehead"), rock(SubstrateBlocksSC.MONOCRYSTAL)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.MONOCRYSTAL), rockName(SubstrateBlocksSC.MONOCRYSTAL), Lang.tr("sc.manual.world.resources4.geode",
                SubstrateWildGenSC.GEODE_Y_MIN, SubstrateWildGenSC.GEODE_Y_MAX, SubstrateWildGenSC.GEODE_CHANCE, SubstrateWildGenSC.GEODE_R_MIN,
                SubstrateWildGenSC.GEODE_R_MAX)));
        e.add(BookEl.item(ModItems.siliconMaterial.stackOf(SiliconMaterial.METALLURGICAL_SI), Lang.tr("sc.manual.world.t.loot"),
                Lang.tr("sc.manual.world.resources4.geode.drop", n(1F), n(stat(ROCK_BLOCK, "MONO_DROP_MAX", 3F)))));

        // the flora and the glass (ПМ-15, ПМ-14)
        e.add(BookEl.head(Lang.tr("sc.manual.world.resources4.florahead"), wild(SubstrateWildBlocksSC.flora, SubstrateWildBlocksSC.WIREWEED)));
        for (int m = 0; m < SubstrateWildBlocksSC.FLORA_NAMES.length; m++) {
            e.add(BookEl.item(wild(SubstrateWildBlocksSC.flora, m), wildName(SubstrateWildBlocksSC.flora, m), wildLine(SubstrateWildBlocksSC.flora, m)));
        }
        for (int m = 0; m < SubstrateWildBlocksSC.GLASS_NAMES.length; m++) {
            e.add(BookEl.item(wild(SubstrateWildBlocksSC.glass, m), wildName(SubstrateWildBlocksSC.glass, m), wildLine(SubstrateWildBlocksSC.glass, m)));
        }
        e.add(BookEl.link("world.biomes4", Lang.tr("sc.manual.world.biomes4.title")));
        e.add(BookEl.link("world.radiation", Lang.tr("sc.manual.world.radiation.title")));
        e.add(BookEl.link("world.terrain", Lang.tr("sc.manual.world.terrain.title")));
        if (SubstrateWildBlocksSC.vent != null) {
            e.about(new ItemStack(SubstrateWildBlocksSC.vent, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SubstrateWildBlocksSC.flora != null) {
            e.about(new ItemStack(SubstrateWildBlocksSC.flora, 1, OreDictionary.WILDCARD_VALUE));
        }
        e.about(rock(SubstrateBlocksSC.MONOCRYSTAL));
        return e;
    }

    /**
     * Ф4: what the handbook says of a wild block's meta (SubstrateWildBlocksSC: the glass, the flora, the vents - where it is, what it
     * gives or does, the tiles' and the generator's numbers) - NEI's «Как получить» too. sc.manual.world.wild.<name>.
     */
    public static String wildLine(net.minecraft.block.Block b, int meta) {
        String[] names = SubstrateWildBlocksSC.namesOf(b);
        String name = names.length == 0 ? "singularGlass" : names[meta >= 0 && meta < names.length ? meta : 0];
        String k = "sc.manual.world.wild." + name;
        if (b == SubstrateWildBlocksSC.vent) {
            switch (meta) {
                case SubstrateWildBlocksSC.FUMAROLE:
                    return Lang.tr(k, TileEntitySubstrateVentSC.HELIUM_MB_MIN, TileEntitySubstrateVentSC.HELIUM_MB_MAX, SubstrateWildGenSC.FUMAROLE_CHANCE);
                case SubstrateWildBlocksSC.ARC_CRACK:
                    return Lang.tr(k, TileEntityArcCrackSC.EU_PER_TICK, TileEntityArcCrackSC.HAZARD_RADIUS, n(TileEntityArcCrackSC.DRAIN_TENTHS_PCT / 10F),
                            TileEntityArcCrackSC.HAZARD_DAMAGE, SubstrateWildGenSC.CRACK_CHANCE);
                case SubstrateWildBlocksSC.ION_GEYSER:
                    return Lang.tr(k, SubstrateWildGenSC.GEYSERS_MIN, SubstrateWildGenSC.GEYSERS_MAX, sec(TileEntitySubstrateVentSC.GEYSER_MIN),
                            sec(TileEntitySubstrateVentSC.GEYSER_MAX), TileEntitySubstrateVentSC.GEYSER_RADIUS, TileEntitySubstrateVentSC.GEYSER_DAMAGE,
                            TileEntitySubstrateVentSC.GEYSER_DRAIN_PCT);
                default:
                    return Lang.tr(k, SubstrateWildGenSC.RTG_CHANCE, n(SubstrateRadiationSC.rtgLevel()), SubstrateRadiationSC.RTG_RADIUS);
            }
        }
        if (b == SubstrateWildBlocksSC.flora) {
            return meta == SubstrateWildBlocksSC.DIODE_MOSS ? Lang.tr(k, com.sc.block.BlockSubstrateFloraSC.MOSS_LIGHT, SubstrateWildGenSC.MOSS_MAX) : Lang.tr(k);
        }
        return meta == SubstrateWildBlocksSC.PILLAR_CORE ? Lang.tr(k, com.sc.block.BlockSingularGlassSC.CORE_LIGHT, SubstrateWildTerrainSC.PILLAR_TOP)
                : Lang.tr(k, SubstrateWildTerrainSC.RIFT_FLOOR);
    }

    // ------------------------------------------------------------------ «ЭМИ-буря»

    private static BookEntry storm(BookChapter c) {
        BookEntry e = new BookEntry("world.storm", c, new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.storm.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.storm.title"))).addAll(BookContent.paras("sc.manual.world.storm",
                new Object[]{StormRulesSC.chancePct(), minutes(StormRulesSC.MIN_TICKS), minutes(StormRulesSC.MAX_TICKS), sec(StormRulesSC.WARN_TICKS)},
                new Object[]{sec(StormRulesSC.PULSE_MIN), sec(StormRulesSC.PULSE_MAX), sec(StormRulesSC.SUPPRESS_MIN), sec(StormRulesSC.SUPPRESS_MAX),
                        StormRulesSC.BOIL_MUL, sec(StormRulesSC.BOIL_TICKS)}));
        if (!SubstrateConfigSC.empStorms) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.storm.off")));
        }

        // the cover (ПФ-3)
        e.add(BookEl.head(Lang.tr("sc.manual.world.storm.coverhead")));
        e.add(BookEl.item(new ItemStack(Blocks.stonebrick), Lang.tr("sc.manual.world.storm.roof"), Lang.tr("sc.manual.world.storm.roof.desc",
                StormRulesSC.COVER_BLOCKS, StormRulesSC.COVER_SCAN)));
        e.add(BookEl.item(new ItemStack(ModBlocks.fieldGeneratorSC), Lang.tr("sc.manual.world.storm.field"), Lang.tr("sc.manual.world.storm.field.desc",
                BookContent.num(EmpMathSC.SCREEN_EU_PULSE))));
        e.add(BookEl.item(core(), Lang.tr("sc.manual.world.storm.dome"), Lang.tr("sc.manual.world.storm.dome.desc")));

        // who else (ПФ-2, ПБ-21)
        e.add(BookEl.head(Lang.tr("sc.manual.world.storm.whohead")));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_TROOPER), Lang.tr("sc.manual.world.storm.mobs"), Lang.tr("sc.manual.world.storm.mobs.desc", StormRulesSC.REACH)));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_HOLLOW_SHELL), mobName(EntityHollowShellSC.NAME), Lang.tr("sc.manual.world.storm.shell",
                sec(StormRulesSC.SHELL_REBOOT_TICKS))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_TUNER), Lang.tr("sc.manual.world.storm.spared"), Lang.tr("sc.manual.world.storm.spared.desc")));

        // what else changes (ПФ-5..ПФ-7)
        e.add(BookEl.head(Lang.tr("sc.manual.world.storm.changeshead")));
        e.add(BookEl.item(ModBlocks.generatorStack(GeneratorType.SOLAR_SI, 1), Lang.tr("sc.manual.world.storm.solar"), Lang.tr("sc.manual.world.storm.solar.desc")));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_NANITE), mobName(EntityNaniteSC.NAME), Lang.tr("sc.manual.world.storm.swarm", StormRulesSC.SWARM_MUL)));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_STRIDER), mobName(EntityStriderSC.NAME), Lang.tr("sc.manual.world.storm.strider")));
        e.add(BookEl.item(trophy(TrophyTableSC.armorMeta(ArmorSuit.QUANTUM, 1)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.storm.trophies",
                n(StormRulesSC.TROPHY_MUL), n(TrophyTableSC.EVENT_CAP))));
        e.addAll(BookContent.paras("sc.manual.world.storm.tips"));
        e.add(BookEl.link("world.haze", Lang.tr("sc.manual.world.haze.title")));
        e.add(BookEl.link("world.noise4", Lang.tr("sc.manual.world.noise4.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Мгла и Выброс»

    private static BookEntry haze(BookChapter c) {
        ItemStack sand = ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND);
        BookEntry e = new BookEntry("world.haze", c, sand, Lang.tr("sc.manual.world.haze.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.haze.title"))).addAll(BookContent.paras("sc.manual.world.haze",
                new Object[]{StormRulesSC.hazeChancePct(), minutes(StormRulesSC.HAZE_MIN), minutes(StormRulesSC.HAZE_MAX), StormRulesSC.HAZE_SIGHT},
                new Object[]{StormRulesSC.HAZE_SIGHT}));
        if (!SubstrateConfigSC.empStorms) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.haze.off")));
        }

        // the Singular Emission (ПФ-10)
        e.add(BookEl.head(Lang.tr("sc.manual.world.emissionhead"), wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.PILLAR_CORE)));
        e.addAll(BookContent.paras("sc.manual.world.emission", new Object[]{StormRulesSC.WEEK_DAYS, minutes(StormRulesSC.EMISSION_TICKS)},
                new Object[]{n(StormRulesSC.EMISSION_RADIATION_MUL), n(StormRulesSC.EMISSION_CRUMB_MUL), mobName(EntityEclipseSC.NAME),
                        n(StormRulesSC.EMISSION_ECLIPSE_MUL)}));
        e.add(BookEl.link("world.storm", Lang.tr("sc.manual.world.storm.title")));
        e.add(BookEl.link("world.radiation", Lang.tr("sc.manual.world.radiation.title")));
        e.add(BookEl.link("world.eclipse", mobName(EntityEclipseSC.NAME)));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Налёты»

    private static BookEntry raids(BookChapter c) {
        ItemStack core = core();
        BookEntry e = new BookEntry("world.raids", c, core != null ? core : icon(), Lang.tr("sc.manual.world.raids.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.raids.title"))).addAll(BookContent.paras("sc.manual.world.raids",
                new Object[]{clock(RaidRulesSC.NIGHT_FROM), clock(RaidRulesSC.NIGHT_TO), levelName(ReputationSC.ACQUAINTANCE), RaidRulesSC.reach(0),
                        RaidRulesSC.reach(1), RaidRulesSC.reach(2)},
                new Object[]{RaidRulesSC.CHANCE_PCT, RaidRulesSC.COOLDOWN_DAYS}));
        if (!SubstrateConfigSC.raids) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.raids.off")));
        }

        // the waves (ПН-2): RaidRulesSC.wave by size - the third one's two kinds by a fixed roll
        e.add(BookEl.head(Lang.tr("sc.manual.world.raids.waveshead"), egg(MobEntitiesSC.ID_MARAUDER)));
        SettlementSizeSC[] sizes = new SettlementSizeSC[SettlementSizeSC.SUBSTRATE_SIZES];  // Ф6: the Substrate's (an embassy isn't raided)
        System.arraycopy(SettlementSizeSC.values(), 0, sizes, 0, sizes.length);
        String[] header = new String[sizes.length + 1];
        header[0] = Lang.tr("sc.manual.world.t.wave");
        for (int s = 0; s < sizes.length; s++) {
            header[s + 1] = Lang.tr(sizes[s].langKey());
        }
        BookEl t = BookEl.table(header);
        for (int row = 0; row < RaidRulesSC.WAVES + 1; row++) {
            int wave = Math.min(row, RaidRulesSC.WAVES - 1);
            boolean column = row == RaidRulesSC.WAVES;            // the third wave without its elite: the Shells' column
            String[] cells = new String[sizes.length + 1];
            cells[0] = (wave + 1) + (wave < RaidRulesSC.WAVES - 1 ? "" : ": " + Lang.tr(column ? "sc.manual.world.raids.column" : "sc.manual.world.raids.elite"));
            for (int s = 0; s < sizes.length; s++) {
                cells[s + 1] = waveText(RaidRulesSC.wave(wave, s, new FixedRoll(column ? 0.999F : 0F)));
            }
            t.row(egg(column ? MobEntitiesSC.ID_HOLLOW_SHELL : wave == 0 ? MobEntitiesSC.ID_MARAUDER : MobEntitiesSC.ID_TROOPER), cells);
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.raids.waves.note", pct(RaidRulesSC.ELITE_WAVE_CHANCE))));
        e.addAll(BookContent.paras("sc.manual.world.raids.fight", new Object[]{RaidRulesSC.SPAWN_OUT_MIN, RaidRulesSC.SPAWN_OUT_MAX,
                minutes(RaidRulesSC.WAVE_TICKS)}));

        // won and lost (ПН-3, ПН-4)
        e.add(BookEl.head(Lang.tr("sc.manual.world.raids.endhead"), SettlementItemsSC.chips(1)));
        BookEl r = BookEl.table(Lang.tr("sc.manual.world.t.size"), Lang.tr("sc.manual.world.t.trust"), Lang.tr("sc.manual.world.t.chips"),
                Lang.tr("sc.manual.world.t.elderchest"));
        for (int s = 0; s < sizes.length; s++) {
            r.row(null, Lang.tr(sizes[s].langKey()), "+" + RaidRulesSC.rep(s), String.valueOf(RaidRulesSC.chips(s)),
                    String.valueOf(RaidRulesSC.CHEST_ROLLS[Math.min(s, RaidRulesSC.CHEST_ROLLS.length - 1)]));
        }
        e.add(r);
        e.addAll(BookContent.paras("sc.manual.world.raids.end", null, new Object[]{minutes(RaidRulesSC.RAID_TICKS), minutes(RaidRulesSC.ABANDON_TICKS),
                days(RaidRulesSC.CLOSED_TICKS)}));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        return e;
    }

    /** «Мародёр-отступник ×3, Обходчик ×2» - a wave's {pool id, count} pairs. */
    private static String waveText(int[][] wave) {
        StringBuilder sb = new StringBuilder();
        for (int[] g : wave) {
            String name = MobEntitiesSC.nameOf(g[0]);
            sb.append(sb.length() == 0 ? "" : ", ").append(Lang.tr("sc.manual.world.cell.times", name == null ? "?" : mobName(name), g[1]));
        }
        return sb.toString();
    }

    /** A Random whose nextFloat is fixed: RaidRulesSC.wave's third wave of either kind for the handbook's table. */
    private static final class FixedRoll extends Random {
        private static final long serialVersionUID = 1L;
        private final float roll;

        FixedRoll(float roll) {
            super(0L);
            this.roll = roll;
        }

        @Override
        public float nextFloat() {
            return roll;
        }
    }

    // ------------------------------------------------------------------ «Эхо моста»

    private static BookEntry echo(BookChapter c) {
        ItemStack shield = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.SHIELD, 1);
        BookEntry e = new BookEntry("world.echo", c, shield, Lang.tr("sc.manual.world.echo.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.echo.title"))).addAll(BookContent.paras("sc.manual.world.echo",
                new Object[]{pct(BridgeEchoSC.HOME_CHANCE), BridgeEchoSC.PARTICLES_MIN, BridgeEchoSC.PARTICLES_MAX, sec(BridgeEchoSC.ECHO_LIFE_TICKS)},
                new Object[]{pct(BridgeEchoSC.ARRIVAL_CHANCE), sec(BridgeEchoSC.SQUAD_DELAY), BridgeEchoSC.SQUAD_DIST_MIN, BridgeEchoSC.SQUAD_DIST_MAX,
                        BridgeEchoSC.SQUAD_MIN, BridgeEchoSC.SQUAD_MAX, minutes(BridgeEchoSC.COOLDOWN_TICKS)}));
        e.add(BookEl.item(shield, shield != null ? shield.getDisplayName() : Lang.tr("tile.siliconage.bridge.portalShield.name"),
                Lang.tr("sc.manual.world.echo.shield")));
        e.add(BookEl.item(dock(BlockDockSC.PLATE), Lang.tr("sc.manual.world.echo.dock"), Lang.tr("sc.manual.world.echo.dock.desc")));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_NANITE), mobName(EntityNaniteSC.NAME), Lang.tr("sc.manual.world.echo.swarm")));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_MARAUDER), mobName(EntityMarauderSC.NAME), Lang.tr("sc.manual.world.echo.squad")));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.echo.vehicles")));
        e.add(BookEl.link("bridge", Lang.tr("sc.manual.bridge.title")));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        e.add(BookEl.link("world.noise", Lang.tr("sc.manual.world.noise.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Лагеря Отступников»

    private static BookEntry camps(BookChapter c) {
        ItemStack field = stolenField(), solar = campBlock(CampBlocksSC.STOLEN_SOLAR), safe = campBlock(CampBlocksSC.CAMP_SAFE);
        ItemStack key = RelicItemsSC.relic(ItemRelicSC.CAMP_KEY, 1), warlordEgg = egg(MobEntitiesSC.ID_WARLORD);
        BookEntry e = new BookEntry("world.camps", c, field != null ? field : icon(), Lang.tr("sc.manual.world.camps.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.camps.title"))).addAll(BookContent.paras("sc.manual.world.camps",
                new Object[]{CampLayoutSC.CHANCE_PCT, CampLayoutSC.MIN_SPAWN_DIST, 2 * CampLayoutSC.HALF_LEN + 1, 2 * CampLayoutSC.HALF_ACROSS + 1},
                new Object[]{mobName(EntityMarauderSC.NAME), CampLayoutSC.MARAUDERS_MIN, CampLayoutSC.MARAUDERS_MAX, mobName(EntityWarlordSC.NAME),
                        CampLayoutSC.EXECUTIONER_PCT, CampLayoutSC.EXECUTIONER_MIN_DIST, mobName(EntityExecutionerSC.NAME)}));
        if (!com.sc.ruin.RuinSC.generates()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.ruins.off")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.camps.old")));

        // the Stolen Field Generator (ПЗ-2..ПЗ-5)
        e.add(BookEl.head(Lang.tr("sc.manual.world.camps.fieldhead"), field));
        e.addAll(BookContent.paras("sc.manual.world.camps.field", new Object[]{CampLayoutSC.FIELD_RADIUS}, null));
        e.add(BookEl.item(solar, campName(CampBlocksSC.STOLEN_SOLAR), Lang.tr("sc.manual.world.camp.stolenSolar", TileEntityStolenSolarSC.PANELS,
                TileEntityStolenSolarSC.EU_DAY)));
        e.add(BookEl.item(field, campName(-1), campLine(-1)));
        e.add(BookEl.item(campBlock(CampBlocksSC.STOLEN_PAD), campName(CampBlocksSC.STOLEN_PAD), campLine(CampBlocksSC.STOLEN_PAD)));

        // the Warlord (ПС-32)
        String warlord = mobName(EntityWarlordSC.NAME);
        e.add(BookEl.head(warlord, warlordEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityWarlordSC.HP), n((float) EntityWarlordSC.HP / 2F))));
        e.add(BookEl.item(exoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.warlord.armour", suitName(ArmorSuit.EXO),
                suitName(ArmorSuit.QUANTUM), n(stat(WARLORD, "RESERVE_DAMAGE_MIN", 80F)), n(stat(WARLORD, "RESERVE_DAMAGE_MAX", 100F)))));
        e.add(BookEl.item(blade(BladeType.QUANTUM), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.warlord.weapon",
                blade(BladeType.QUANTUM).getDisplayName(), n(stat(WARLORD, "BLADE_DAMAGE", 8F)), weapon(WeaponType.ION_CUTTER).getDisplayName(),
                n(stat(WARLORD, "BEAM_DAMAGE", 4F)), n(stat(WARLORD, "SWITCH_RANGE", 6F)), n(stat(WARLORD, "BACKSTAB_MUL", 1.5F)))));
        e.add(BookEl.item(key, Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.warlord.loot", relicName(ItemRelicSC.CAMP_KEY),
                lootName(LOOT_QUANTUM_SCRAP), n(stat(WARLORD, "SCRAP", 4F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.armorMeta(ArmorSuit.EXO, 1)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.warlord.trophies",
                pct(trophyChance(stat(WARLORD, "TROPHY_PIECE", 0.02F))), pct(trophyChance(stat(WARLORD, "TROPHY_EXO_CHEST", 0.005F))))));

        // the safe and the camp's return (ПЗ-6, ПЗ-7)
        e.add(BookEl.head(Lang.tr("sc.manual.world.camps.safehead"), safe));
        e.add(BookEl.item(safe, campName(CampBlocksSC.CAMP_SAFE), campLine(CampBlocksSC.CAMP_SAFE)));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.camps.safe.loot"), Lang.tr("sc.manual.world.camps.safe.loot.desc",
                pct(stat(RUIN_LOOT, "CAMP_MODULE_CHANCE", 0.05F)), pct(stat(RUIN_LOOT, "CAMP_CHIP_CHANCE", 0.03F)), pct(stat(RUIN_LOOT, "CAMP_CRUMB_CHANCE", 0.05F)))));
        e.add(BookEl.item(warlordEgg, Lang.tr("sc.manual.world.camps.return"), Lang.tr("sc.manual.world.camps.return.desc", days(RuinWorldSC.CAMP_RESTORE_TICKS),
                RuinWorldSC.RESPAWN_CLEAR)));
        e.addAll(BookContent.paras("sc.manual.world.camps.tips", new Object[]{clock(18000)}));
        e.add(BookEl.link("world.renegades", Lang.tr("sc.manual.world.renegades.title")));
        e.add(BookEl.link("world.executioner", mobName(EntityExecutionerSC.NAME)));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        e.add(BookEl.link("field", Lang.tr("sc.manual.field.title")));
        e.about(field, warlordEgg, key, ruin(RUIN_CAMP_TARP));
        if (CampBlocksSC.camp != null) {
            e.about(new ItemStack(CampBlocksSC.camp, 1, OreDictionary.WILDCARD_VALUE));
        }
        return e;
    }

    /**
     * Ф4: what the handbook says of a camp block (CampBlocksSC: `meta` of campSC, -1 the Stolen Field Generator) - where it stands,
     * what breaking it gives (package C's numbers by name) - NEI's «Как получить» too. sc.manual.world.camp.<name>.
     */
    public static String campLine(int meta) {
        if (meta < 0) {
            return Lang.tr("sc.manual.world.camp.stolenField", n(com.sc.block.BlockStolenFieldSC.HARDNESS));
        }
        String name = CampBlocksSC.CAMP_NAMES[Math.min(CampBlocksSC.CAMP_NAMES.length - 1, meta)];
        if (meta == CampBlocksSC.STOLEN_SOLAR) {
            return Lang.tr("sc.manual.world.camp." + name, TileEntityStolenSolarSC.PANELS, TileEntityStolenSolarSC.EU_DAY);
        }
        if (meta == CampBlocksSC.STOLEN_PAD) {
            return Lang.tr("sc.manual.world.camp." + name, mobName(EntityExecutionerSC.NAME), n(stat(EXECUTIONER, "PAD_UNITS", 1F)));
        }
        return Lang.tr("sc.manual.world.camp." + name);
    }

    // ------------------------------------------------------------------ «Хранилища и Часовой»

    private static BookEntry vaults(BookChapter c) {
        ItemStack plating = ruin(RUIN_VAULT_PLATING), sentinelEgg = egg(MobEntitiesSC.ID_SENTINEL), fragment = RelicItemsSC.relic(ItemRelicSC.KEY_FRAGMENT, 1);
        ItemStack finder = RelicItemsSC.ruinFinder == null ? null : new ItemStack(RelicItemsSC.ruinFinder);
        BookEntry e = new BookEntry("world.vaults", c, plating != null ? plating : icon(), Lang.tr("sc.manual.world.vaults.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.vaults.title"))).addAll(BookContent.paras("sc.manual.world.vaults",
                new Object[]{VaultLayoutSC.BLOCK, VaultLayoutSC.BLOCK, VaultLayoutSC.HALF, VaultLayoutSC.DEPTH},
                new Object[]{n(stat(RUIN_BLOCK, "PLATING_LEVEL", 3F)), n(stat(RUIN_BLOCK, "PLATING_HARDNESS", 30F)), n(stat(RUIN_BLOCK, "PLATING_SCRAP_PCT", 50F))}));
        if (!com.sc.ruin.RuinSC.generates()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.ruins.off")));
        }
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.ruins.chests"), Lang.tr("sc.manual.world.vaults.loot",
                pct(stat(RUIN_LOOT, "VAULT_MODULE_CHANCE", 0.1F)), pct(stat(RUIN_LOOT, "VAULT_CRUMB_CHANCE", 0.1F)))));

        // the Singular Sentinel (ПС-33)
        String sentinel = mobName(EntitySentinelSC.NAME);
        e.add(BookEl.head(sentinel, sentinelEgg));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntitySentinelSC.HP), n((float) EntitySentinelSC.HP / 2F))));
        e.add(BookEl.item(new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.sentinel.armour",
                suitName(ArmorSuit.SINGULAR), pct(stat(SENTINEL, "SENIOR_CHANCE", 0.2F)), n(stat(SENTINEL, "RESERVE_DAMAGE_MIN", 150F)),
                n(stat(SENTINEL, "RESERVE_DAMAGE_MAX", 250F)), n(stat(SENTINEL, "REGEN_PER_SECOND", 4F)), sec((int) stat(SENTINEL, "REGEN_DELAY_TICKS", 100F)))));
        e.add(BookEl.item(blade(BladeType.SINGULAR), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.sentinel.weapon",
                blade(BladeType.SINGULAR).getDisplayName(), pct(stat(SENTINEL, "SINGULAR_BLADE_CHANCE", 0.3F)), n(stat(SENTINEL, "SINGULAR_BLADE_DAMAGE", 14F)),
                blade(BladeType.EXO).getDisplayName(), n(stat(SENTINEL, "EXO_BLADE_DAMAGE", 10F)))));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.jammer.carrier"), EmpMobGunSC.allowed()
                ? Lang.tr("sc.manual.world.sentinel.carrier.on", pct(stat(SENTINEL, "CARRIER_CHANCE", 0.05F)), n(stat(SENTINEL, "EMP_RANGE", 32F)))
                : Lang.tr("sc.manual.world.sentinel.carrier.off", weapon(WeaponType.ION_CUTTER).getDisplayName())));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.sentinel.press"), Lang.tr("sc.manual.world.sentinel.press.desc", sec((int) stat(SENTINEL, "PRESS_EVERY", 600F)),
                sec((int) stat(SENTINEL, "PRESS_HUM_TICKS", 60F)), n(stat(SENTINEL, "PRESS_RADIUS", 6F)), sec((int) stat(SENTINEL, "PRESS_TICKS", 100F)))));
        e.add(BookEl.item(plating, Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.sentinel.where", n(stat(SENTINEL, "LEASH", 24F)))));
        e.add(BookEl.item(fragment, Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.sentinel.loot", lootName(LOOT_FIRMWARE),
                n(stat(SENTINEL, "FIRMWARE_MIN", 3F)), n(stat(SENTINEL, "FIRMWARE_MAX", 5F)), relicName(ItemRelicSC.SEALED_CRUMB), pct(stat(SENTINEL, "CRUMB_CHANCE", 0.25F)),
                n(StormRulesSC.EMISSION_CRUMB_MUL), relicName(ItemRelicSC.KEY_FRAGMENT))));
        e.add(BookEl.item(trophy(TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 1)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.sentinel.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[ArmorSuit.SINGULAR.ordinal()])), pct(trophyChance(TrophyTableSC.BLADE[BladeType.SINGULAR.ordinal()])),
                pct(trophyChance(stat(SENTINEL, "CARRIER_PE1", 0.1F))))));
        e.add(BookEl.item(sentinelEgg, Lang.tr("sc.manual.world.vaults.return"), Lang.tr("sc.manual.world.vaults.return.desc",
                days(RuinWorldSC.SENTINEL_RESPAWN_TICKS), RuinWorldSC.RESPAWN_CLEAR)));

        // the Fragments and the Pelengator (ПХ-3, ПХ-6)
        e.add(BookEl.head(Lang.tr("sc.manual.world.vaults.findhead"), finder));
        e.add(BookEl.item(fragment, relicName(ItemRelicSC.KEY_FRAGMENT), relicLine(ItemRelicSC.KEY_FRAGMENT)));
        e.add(BookEl.item(finder, finder != null ? finder.getDisplayName() : Lang.tr("item.siliconage.ruinFinder.name"), finderLine()));
        // Ф5 (ПЛ-2, ПУ-11): the Journals of the Vaults, the four Fragments made into the Breakdown Key
        ItemStack journal = com.sc.finale.FinaleItemsSC.journal(5), key = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1);
        e.add(BookEl.item(journal, Lang.tr("sc.manual.world.vaults.journals"), Lang.tr("sc.manual.world.vaults.journals.desc",
                journalNumbers(com.sc.lore.JournalSC.SRC_VAULT), pct(com.sc.lore.JournalSC.chance(com.sc.lore.JournalSC.SRC_VAULT)), sentinel,
                pct(com.sc.lore.JournalSC.chance(com.sc.lore.JournalSC.SRC_SENTINEL)))));
        e.add(BookEl.item(key, relicName(ItemRelicSC.BREAKDOWN_KEY), relicLine(ItemRelicSC.BREAKDOWN_KEY)));
        e.addAll(BookContent.paras("sc.manual.world.vaults.tips"));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.add(BookEl.link("world.arena", Lang.tr("sc.manual.world.arena.title")));
        e.add(BookEl.link("world.journals", Lang.tr("sc.manual.world.journals.title")));
        e.about(plating, ruin(RUIN_VAULT_LAMP), sentinelEgg, fragment, finder);
        return e;
    }

    /** Ф4: the Pelengator's line - who sells it (TradeTableSC: «Картограф прокола: 10 КЧ ...»), what it shows, its uses. */
    public static String finderLine() {
        ItemStack finder = RelicItemsSC.ruinFinder == null ? null : new ItemStack(RelicItemsSC.ruinFinder);
        String seller = sellerLine(finder);
        return Lang.tr("sc.manual.world.vaults.finder", ItemRuinFinderSC.USES) + " "
                + (seller != null ? seller : Lang.tr("sc.manual.world.vaults.finder.seller", Lang.tr(NpcKindSC.CARTOGRAPHER.langKey())));
    }

    /**
     * Ф4: what the handbook says of a relic (ItemRelicSC: the camp's key, the Breakdown Key's Fragment, the Sealed Crumb - who drops
     * it, what it opens; package M's / C's chances by name) - NEI's «Как получить» too. sc.manual.world.relic.<name>.
     */
    public static String relicLine(int meta) {
        String k = "sc.manual.world.relic." + ItemRelicSC.nameOf(meta);
        switch (meta) {
            case ItemRelicSC.CAMP_KEY:
                return Lang.tr(k, mobName(EntityWarlordSC.NAME));
            case ItemRelicSC.KEY_FRAGMENT:
                return Lang.tr(k, mobName(EntitySentinelSC.NAME), ItemRelicSC.FRAGMENTS);
            case ItemRelicSC.BREAKDOWN_KEY:                             // Ф5 (ПУ-11): four Fragments, into the Console
                return Lang.tr(k, ItemRelicSC.FRAGMENTS, mobName(EntitySentinelSC.NAME));
            case ItemRelicSC.BREAKDOWN_MATRIX:                          // Ф5 (ПТ-22, ПО-18): the Warden's, the Elder's Module
                return Lang.tr(k, mobName(com.sc.mob.EntityBreakdownWardenSC.NAME), Lang.tr(NpcKindSC.ELDER.langKey()));
            default:
                return Lang.tr(k, mobName(EntitySentinelSC.NAME), pct(stat(SENTINEL, "CRUMB_CHANCE", 0.25F)), mobName(EntityEclipseSC.NAME),
                        pct(stat(ECLIPSE, "CRUMB_CHANCE", 0.3F)), CrumbLimitSC.DAY_CAP);
        }
    }

    // ------------------------------------------------------------------ «Перегретый»

    private static BookEntry overheated(BookChapter c) {
        String name = mobName(EntityOverheatedSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_OVERHEATED);
        BookEntry e = new BookEntry("world.overheated", c, egg != null ? egg : quantumPiece(1), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.overheated"));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityOverheatedSC.HP),
                n((float) EntityOverheatedSC.HP / 2F))));
        e.add(BookEl.item(quantumPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.overheated.armour", suitName(ArmorSuit.QUANTUM),
                n(stat(OVERHEATED, "PIECES_MIN", 2F)), n(stat(OVERHEATED, "PIECES_MAX", 4F)), n(stat(OVERHEATED, "RESERVE_DAMAGE_MIN", 0F)),
                n(stat(OVERHEATED, "RESERVE_DAMAGE_MAX", 15F)), ironPoints())));
        e.add(BookEl.item(new ItemStack(Items.flint_and_steel), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.overheated.weapon",
                n(stat(OVERHEATED, "FIST_MIN", 3F)), n(stat(OVERHEATED, "FIST_MAX", 4F)), n(stat(OVERHEATED, "FIRE_SECONDS", 3F)))));
        e.add(BookEl.item(new ItemStack(ModItems.dosimeter), Lang.tr("sc.manual.world.overheated.radiation"), Lang.tr("sc.manual.world.overheated.radiation.desc",
                n(stat(OVERHEATED, "RADIATION_LEVEL", 2F)), n(stat(OVERHEATED, "RADIATION_RADIUS", 4F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.overheated.steam"), Lang.tr("sc.manual.world.overheated.steam.desc",
                n(stat(OVERHEATED, "STEAM_DAMAGE", 4F)), n(stat(OVERHEATED, "STEAM_RADIUS", 3F)))));
        e.add(BookEl.item(new ItemStack(Items.water_bucket), Lang.tr("sc.manual.world.overheated.water"), Lang.tr("sc.manual.world.overheated.water.desc",
                n(stat(OVERHEATED, "WATER_DAMAGE", 6F)), pct(stat(OVERHEATED, "WATER_ARMOUR_CUT", 0.5F)), sec((int) stat(OVERHEATED, "WATER_TICKS", 200F)))));
        e.add(BookEl.item(ModBlocks.generatorStack(GeneratorType.SOLAR_SI, 1), Lang.tr("sc.manual.world.overheated.energy"),
                Lang.tr("sc.manual.world.overheated.energy.desc", n(stat(OVERHEATED, "SEEK_RADIUS", 16F)))));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.emp.mode.point"), Lang.tr("sc.manual.world.overheated.emp")));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_GOLD), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.overheated.where",
                weight("OXIDE_WEIGHTS", MobEntitiesSC.ID_OVERHEATED, 40), weight("RIFT_WEIGHTS", MobEntitiesSC.ID_OVERHEATED, 10),
                group(MobEntitiesSC.ID_OVERHEATED, 1, 2))));
        e.add(BookEl.item(loot(LOOT_BOILED_LOOP), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.overheated.loot", lootName(LOOT_FIRMWARE),
                n(stat(OVERHEATED, "FIRMWARE", 1F)), lootName(LOOT_BOILED_LOOP), pct(stat(OVERHEATED, "LOOP_CHANCE", 0.2F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.armorMeta(ArmorSuit.QUANTUM, 1)), Lang.tr("sc.manual.world.t.trophies"),
                Lang.tr("sc.manual.world.overheated.trophies", pct(trophyChance(TrophyTableSC.PIECE[ArmorSuit.QUANTUM.ordinal()])))));
        e.addAll(BookContent.paras("sc.manual.world.overheated.tips"));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.radiation", Lang.tr("sc.manual.world.radiation.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(egg, loot(LOOT_BOILED_LOOP));
        return e;
    }

    // ------------------------------------------------------------------ «Затмение»

    private static BookEntry eclipse(BookChapter c) {
        String name = mobName(EntityEclipseSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_ECLIPSE);
        BookEntry e = new BookEntry("world.eclipse", c, egg != null ? egg : new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[0]), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.eclipse"));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityEclipseSC.HP), n((float) EntityEclipseSC.HP / 2F))));
        e.add(BookEl.item(new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.eclipse.armour",
                suitName(ArmorSuit.SINGULAR), n(stat(ECLIPSE, "RESERVE_DAMAGE_MIN", 200F)), n(stat(ECLIPSE, "RESERVE_DAMAGE_MAX", 250F)))));
        e.add(BookEl.item(blade(BladeType.SINGULAR), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.eclipse.weapon",
                blade(BladeType.SINGULAR).getDisplayName(), n(stat(ECLIPSE, "BLADE_DAMAGE", 13F)), sec((int) stat(ECLIPSE, "HEAVY_WINDUP", 20F)),
                n(stat(ECLIPSE, "HEAVY_BLOW", 8F)), sec((int) stat(ECLIPSE, "HEAVY_COOLDOWN", 100F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.eclipse.hunt"), Lang.tr("sc.manual.world.eclipse.hunt.desc", n(stat(ECLIPSE, "HUNT_RANGE", 32F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.eclipse.blink"), Lang.tr("sc.manual.world.eclipse.blink.desc", n(stat(ECLIPSE, "BLINK_DISTANCE", 8F)),
                sec((int) stat(ECLIPSE, "BLINK_MIN", 100F)), sec((int) stat(ECLIPSE, "BLINK_MAX", 200F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.eclipse.flee"), Lang.tr("sc.manual.world.eclipse.flee.desc", pct(stat(ECLIPSE, "FLEE_BELOW", 0.25F)),
                n(stat(ECLIPSE, "FLEE_DISTANCE", 24F)), n(stat(ECLIPSE, "HEAL_PER_SECOND", 1F)), pct(stat(ECLIPSE, "RECHARGE_TO", 0.6F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.eclipse.sound"), Lang.tr("sc.manual.world.eclipse.sound.desc", n(stat(ECLIPSE, "MUFFLE_RADIUS", 16F)))));
        e.add(BookEl.item(wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.GLASS), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.eclipse.where",
                n(stat(RULES, "ECLIPSE_PCT", 2F)), n(StormRulesSC.EMISSION_ECLIPSE_MUL), days((long) stat(RULES, "ECLIPSE_RESPAWN_TICKS", 72000F)))));
        e.add(BookEl.item(loot(LOOT_SINGULAR_SCRAP), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.eclipse.loot", lootName(LOOT_SINGULAR_SCRAP),
                n(stat(ECLIPSE, "SCRAP_MIN", 2F)), n(stat(ECLIPSE, "SCRAP_MAX", 3F)), relicName(ItemRelicSC.SEALED_CRUMB), pct(stat(ECLIPSE, "CRUMB_CHANCE", 0.3F)),
                n(StormRulesSC.EMISSION_CRUMB_MUL))));
        e.add(BookEl.item(trophy(TrophyTableSC.bladeMeta(BladeType.SINGULAR)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.eclipse.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[ArmorSuit.SINGULAR.ordinal()])), pct(trophyChance(TrophyTableSC.BLADE[BladeType.SINGULAR.ordinal()])))));
        e.addAll(BookContent.paras("sc.manual.world.eclipse.tips"));
        e.add(BookEl.link("world.biomes4", Lang.tr("sc.manual.world.biomes4.title")));
        e.add(BookEl.link("world.haze", Lang.tr("sc.manual.world.haze.title")));
        e.add(BookEl.link("world.elites", Lang.tr("sc.manual.world.elites.title")));
        e.about(egg, loot(LOOT_SINGULAR_SCRAP));
        return e;
    }

    // ------------------------------------------------------------------ «Наводка, Токоед, Колосс, Ходок»

    private static BookEntry noise4(BookChapter c) {
        ItemStack phantomEgg = egg(MobEntitiesSC.ID_PHANTOM), leechEgg = egg(MobEntitiesSC.ID_LEECH), colossusEgg = egg(MobEntitiesSC.ID_COLOSSUS),
                striderEgg = egg(MobEntitiesSC.ID_STRIDER);
        BookEntry e = new BookEntry("world.noise4", c, colossusEgg != null ? colossusEgg : icon(), Lang.tr("sc.manual.world.noise4.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.noise4.title"))).addAll(BookContent.paras("sc.manual.world.noise4"));
        String hp = Lang.tr("sc.manual.world.t.hp"), where = Lang.tr("sc.manual.world.t.where"), lootT = Lang.tr("sc.manual.world.t.loot"),
                empT = Lang.tr("sc.manual.world.noise4.emp");
        ItemStack gun = new ItemStack(ModItems.empGun);

        // the Induction Phantom (ПС-35, ПБ-22)
        e.add(BookEl.head(mobName(EntityPhantomSC.NAME), phantomEgg));
        e.add(BookEl.item(null, hp, Lang.tr("sc.manual.world.mob.hp", n((float) EntityPhantomSC.HP), n((float) EntityPhantomSC.HP / 2F))));
        e.add(BookEl.item(new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[0]), Lang.tr("sc.manual.world.phantom.sight"), Lang.tr("sc.manual.world.phantom.sight.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.phantom.weapon", sec((int) stat(PHANTOM, "HELMET_TICKS", 80F)),
                n(stat(PHANTOM, "BREAKDOWN", 3F)), sec((int) stat(PHANTOM, "STRIKE_EVERY", 40F)), pct(stat(PHANTOM, "WEAPON_MUL", 0.5F)))));
        e.add(BookEl.item(gun, empT, Lang.tr("sc.manual.world.phantom.emp")));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), where, Lang.tr("sc.manual.world.phantom.where", weight("CANYON_WEIGHTS", MobEntitiesSC.ID_PHANTOM, 8),
                weight("RIDGES_WEIGHTS", MobEntitiesSC.ID_PHANTOM, 8), weight("RIFT_WEIGHTS", MobEntitiesSC.ID_PHANTOM, 10))));
        e.add(BookEl.item(loot(LOOT_INDUCTION_THREAD), lootT, lootLine(LOOT_INDUCTION_THREAD)));

        // the Current Leech (ПС-36, ПБ-23)
        e.add(BookEl.head(mobName(EntityLeechSC.NAME), leechEgg));
        e.add(BookEl.item(null, hp, Lang.tr("sc.manual.world.mob.hp", n((float) EntityLeechSC.HP), n((float) EntityLeechSC.HP / 2F))));
        e.add(BookEl.item(nanoPiece(1), Lang.tr("sc.manual.world.leech.wrap"), Lang.tr("sc.manual.world.leech.wrap.desc", sec((int) stat(LEECH, "WRAP_TICKS", 100F)),
                pct(stat(LEECH, "DRAIN", 0.01F)), n(stat(LEECH, "BITE", 2F)), sec((int) stat(LEECH, "WRAP_COOLDOWN", 100F)))));
        e.add(BookEl.item(new ItemStack(Items.water_bucket), Lang.tr("sc.manual.world.leech.water"), Lang.tr("sc.manual.world.leech.water.desc",
                n(stat(LEECH, "SHORT_DAMAGE", 10F)), sec((int) stat(LEECH, "STUN_TICKS", 60F)))));
        e.add(BookEl.item(gun, empT, Lang.tr("sc.manual.world.leech.emp", n(stat(LEECH, "BURST_DAMAGE", 6F)), n(stat(LEECH, "BURST_RADIUS", 3F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), where, Lang.tr("sc.manual.world.leech.where", weight("CANYON_WEIGHTS", MobEntitiesSC.ID_LEECH, 10),
                weight("RIDGES_WEIGHTS", MobEntitiesSC.ID_LEECH, 10))));
        e.add(BookEl.item(null, lootT, Lang.tr("sc.manual.world.leech.loot", pct(stat(LEECH, "CELL_CHANCE", 0.4F)), n(stat(LEECH, "COPPER_MIN", 1F)),
                n(stat(LEECH, "COPPER_MAX", 3F)))));

        // the Quartzite Colossus (ПС-37, ПБ-26)
        e.add(BookEl.head(mobName(EntityColossusSC.NAME), colossusEgg));
        e.add(BookEl.item(null, hp, Lang.tr("sc.manual.world.colossus.hp", n((float) EntityColossusSC.HP), n(stat(COLOSSUS, "NATURAL_ARMOUR", 12F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.colossus.temper"), Lang.tr("sc.manual.world.colossus.temper.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.colossus.weapon", sec((int) stat(COLOSSUS, "STOMP_WINDUP", 20F)),
                n(stat(COLOSSUS, "STOMP_DAMAGE", 6F)), n(stat(COLOSSUS, "STOMP_RADIUS", 4F)), sec((int) stat(COLOSSUS, "THROW_AIM", 20F)),
                n(stat(COLOSSUS, "THROW_RANGE", 12F)), n(stat(COLOSSUS, "THROW_DAMAGE", 7F)))));
        e.add(BookEl.item(new ItemStack(Items.arrow), Lang.tr("sc.manual.world.colossus.faces"), Lang.tr("sc.manual.world.colossus.faces.desc",
                n(stat(COLOSSUS, "BEAM_MUL", 0.5F)), n(stat(COLOSSUS, "BLAST_MUL", 1.5F)), n(stat(COLOSSUS, "DRILL_MUL", 3F)), n(stat(COLOSSUS, "FRONT_MUL", 0.7F)))));
        e.add(BookEl.item(gun, empT, Lang.tr("sc.manual.world.colossus.emp")));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), where, Lang.tr("sc.manual.world.colossus.where", weight("PLATEAU_WEIGHTS", MobEntitiesSC.ID_COLOSSUS, 6),
                weight("RIDGES_WEIGHTS", MobEntitiesSC.ID_COLOSSUS, 12))));
        e.add(BookEl.item(null, lootT, Lang.tr("sc.manual.world.colossus.loot", n(stat(COLOSSUS, "QUARTZITE_MIN", 4F)), n(stat(COLOSSUS, "QUARTZITE_MAX", 8F)),
                n(stat(COLOSSUS, "SAND_MIN", 4F)), n(stat(COLOSSUS, "SAND_MAX", 8F)), pct(stat(COLOSSUS, "ORE_CHANCE", 0.5F)))));

        // the Arc Strider (ПС-38, ПБ-24)
        e.add(BookEl.head(mobName(EntityStriderSC.NAME), striderEgg));
        e.add(BookEl.item(null, hp, Lang.tr("sc.manual.world.mob.hp", n((float) EntityStriderSC.HP), n((float) EntityStriderSC.HP / 2F))));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.strider.storm"), Lang.tr("sc.manual.world.strider.storm.desc",
                n(stat(RULES, "STORM_STRIDER_WEIGHT", 12F)), sec((int) stat(STRIDER, "DISPERSE_TICKS", 600F)))));
        e.add(BookEl.item(exoPiece(1), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.strider.weapon", sec((int) stat(STRIDER, "ARC_CHARGE", 20F)),
                n(stat(STRIDER, "ARC_DAMAGE", 5F)), n(stat(STRIDER, "CHAIN_JUMPS", 3F)), n(stat(STRIDER, "CHAIN_STEP", 6F)))));
        e.add(BookEl.item(new ItemStack(Items.arrow), Lang.tr("sc.manual.world.strider.arrows"), Lang.tr("sc.manual.world.strider.arrows.desc")));
        e.add(BookEl.item(gun, empT, Lang.tr("sc.manual.world.strider.emp", sec((int) stat(STRIDER, "COLLAPSE_TICKS", 100F)), n(stat(STRIDER, "COLLAPSED_MUL", 2F)))));
        e.add(BookEl.item(new ItemStack(Items.water_bucket), Lang.tr("sc.manual.world.strider.water"), Lang.tr("sc.manual.world.strider.water.desc",
                n(stat(STRIDER, "WATER_DAMAGE", 8F)))));
        e.add(BookEl.item(loot(LOOT_PLASMA_CONDENSATE), lootT, lootLine(LOOT_PLASMA_CONDENSATE)));
        e.addAll(BookContent.paras("sc.manual.world.noise4.tips"));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.storm", Lang.tr("sc.manual.world.storm.title")));
        e.add(BookEl.link("world.noise", Lang.tr("sc.manual.world.noise.title")));
        e.about(phantomEgg, leechEgg, colossusEgg, striderEgg, loot(LOOT_INDUCTION_THREAD), loot(LOOT_PLASMA_CONDENSATE));
        return e;
    }

    // ------------------------------------------------------------------ «Утилизатор и Метка гибели»

    private static BookEntry scrapper(BookChapter c) {
        String name = mobName(EntityScrapperSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_SCRAPPER);
        BookEntry e = new BookEntry("world.scrapper", c, egg != null ? egg : new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.scrapper.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.scrapper.title"))).addAll(BookContent.paras("sc.manual.world.scrapper", new Object[]{name},
                new Object[]{sec(ScrapperSC.ARRIVE_TICKS), ScrapperSC.SPAWN_MIN, ScrapperSC.SPAWN_MAX, ScrapperSC.TELL_RANGE}));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityScrapperSC.HP), n((float) EntityScrapperSC.HP / 2F))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.scrapper.armour", n(stat(SCRAPPER, "NATURAL_ARMOUR", 10F)),
                n(stat(SCRAPPER, "BACK_MUL", 2F)))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.t.bag"), Lang.tr("sc.manual.world.scrapper.bag", n(stat(SCRAPPER, "COLLECT_RADIUS", 6F)),
                ScrapperSC.BAG_STACKS)));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.emp.mode.point"), Lang.tr("sc.manual.world.scrapper.emp", sec((int) stat(SCRAPPER, "STUN_TICKS", 80F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.scrapper.loot", ScrapperSC.ORES_MIN, ScrapperSC.ORES_MAX)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.scrapper.where",
                weight("PLATEAU_WEIGHTS", MobEntitiesSC.ID_SCRAPPER, 3), weight("CANYON_WEIGHTS", MobEntitiesSC.ID_SCRAPPER, 3),
                weight("OXIDE_WEIGHTS", MobEntitiesSC.ID_SCRAPPER, 4))));
        e.add(BookEl.item(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1), Lang.tr("sc.manual.world.scrapper.mark"),
                Lang.tr("sc.manual.world.scrapper.mark.desc", ScrapperSC.HOME_LEASH)));
        e.addAll(BookContent.paras("sc.manual.world.scrapper.tips"));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.about(egg);
        return e;
    }

    // ------------------------------------------------------------------ «Подложник»

    private static BookEntry fauna(BookChapter c) {
        String name = mobName(EntityWaferCrabSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_WAFER_CRAB);
        BookEntry e = new BookEntry("world.fauna", c, egg != null ? egg : icon(), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.fauna"));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n((float) EntityWaferCrabSC.HP), n((float) EntityWaferCrabSC.HP / 2F))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.fauna.where",
                group(MobEntitiesSC.ID_WAFER_CRAB, 1, 3), n(stat(RULES, "PEACEFUL_NEAR_MAX", 4F)), n(stat(RULES, "PEACEFUL_WORLD_MAX", 12F)))));
        e.add(BookEl.item(loot(LOOT_NATURAL_WAFER), Lang.tr("sc.manual.world.t.loot"), lootLine(LOOT_NATURAL_WAFER)));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.fauna.note")));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.about(egg, loot(LOOT_NATURAL_WAFER));
        return e;
    }

    // ------------------------------------------------------------------ «Крупицы сингулярности»

    private static BookEntry crumbs(BookChapter c) {
        ItemStack sealed = RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1), crumb = new ItemStack(ModItems.singularCrumb);
        BookEntry e = new BookEntry("world.crumbs", c, sealed != null ? sealed : crumb, Lang.tr("sc.manual.world.crumbs.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.crumbs.title"))).addAll(BookContent.paras("sc.manual.world.crumbs",
                new Object[]{CrumbLimitSC.DAY_CAP}, null));

        // where they come from (ПТ-17): every source of the world but the boss gives them sealed
        e.add(BookEl.head(Lang.tr("sc.manual.world.crumbs.sourceshead"), sealed));
        e.add(BookEl.item(ruin(BlockRuinSC.MACHINE_HUSK), Lang.tr("sc.manual.world.crumbs.nodes"), Lang.tr("sc.manual.world.crumbs.chance",
                pct(stat(RUIN_LOOT, "NODE_CRUMB_CHANCE", 0.03F)))));
        e.add(BookEl.item(ruin(RUIN_VAULT_PLATING), Lang.tr("sc.manual.world.crumbs.vaults"), Lang.tr("sc.manual.world.crumbs.chance",
                pct(stat(RUIN_LOOT, "VAULT_CRUMB_CHANCE", 0.1F)))));
        e.add(BookEl.item(campBlock(CampBlocksSC.CAMP_SAFE), Lang.tr("sc.manual.world.crumbs.camps"), Lang.tr("sc.manual.world.crumbs.chance",
                pct(stat(RUIN_LOOT, "CAMP_CRUMB_CHANCE", 0.05F)))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_SENTINEL), mobName(EntitySentinelSC.NAME), Lang.tr("sc.manual.world.crumbs.chance",
                pct(stat(SENTINEL, "CRUMB_CHANCE", 0.25F)))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_ECLIPSE), mobName(EntityEclipseSC.NAME), Lang.tr("sc.manual.world.crumbs.chance",
                pct(stat(ECLIPSE, "CRUMB_CHANCE", 0.3F)))));
        String elder = sellerLine(crumb);
        e.add(BookEl.item(kindIcon(NpcKindSC.ELDER), Lang.tr(NpcKindSC.ELDER.langKey()), (elder != null ? elder + " " : "")
                + Lang.tr("sc.manual.world.crumbs.elder")));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.crumbs.emission", n(StormRulesSC.EMISSION_CRUMB_MUL))));
        // Ф5 (ПЛ-7, ПТ-22): the Signal's cache gives a sealed one; the Breakdown Warden - real ones, beyond the cap
        e.add(BookEl.item(new ItemStack(Blocks.beacon), Lang.tr("sc.manual.world.signal.title"), Lang.tr("sc.manual.world.crumbs.signal")));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_BREAKDOWN_WARDEN), mobName(com.sc.mob.EntityBreakdownWardenSC.NAME), Lang.tr("sc.manual.world.crumbs.warden",
                WardenRulesSC.CRUMBS_MIN, WardenRulesSC.CRUMBS_MAX)));

        // opening one (ПТ-17)
        e.add(BookEl.head(Lang.tr("sc.manual.world.crumbs.openhead"), crumb));
        e.addAll(BookContent.paras("sc.manual.world.crumbs.open", new Object[]{CrumbLimitSC.DAY_CAP}));
        e.add(BookEl.link("world.vaults", Lang.tr("sc.manual.world.vaults.title")));
        e.add(BookEl.link("world.camps", Lang.tr("sc.manual.world.camps.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.add(BookEl.link("world.eclipse", mobName(EntityEclipseSC.NAME)));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        e.about(sealed);
        return e;
    }

    // ------------------------------------------------------------------ Ф4's stacks and numbers

    /** `meta` of one of SubstrateWildBlocksSC's blocks, null before it is registered. */
    static ItemStack wild(net.minecraft.block.Block b, int meta) {
        return b == null ? null : new ItemStack(b, 1, meta);
    }

    /** The wild block's name in the language (tile.siliconage.wild.<name>.name). */
    public static String wildName(net.minecraft.block.Block b, int meta) {
        ItemStack s = wild(b, meta);
        if (s != null) {
            return s.getDisplayName();
        }
        String[] names = SubstrateWildBlocksSC.namesOf(b);
        return Lang.tr("tile.siliconage.wild." + (names.length == 0 ? "singularGlass" : names[Math.max(0, Math.min(names.length - 1, meta))]) + ".name");
    }

    /** The Stolen Field Generator, null before it is registered. */
    static ItemStack stolenField() {
        return CampBlocksSC.stolenField == null ? null : new ItemStack(CampBlocksSC.stolenField);
    }

    /** `meta` of the camp block (campSC), null before it is registered. */
    static ItemStack campBlock(int meta) {
        return CampBlocksSC.camp == null ? null : new ItemStack(CampBlocksSC.camp, 1, meta);
    }

    /** A camp block's name (`meta` of campSC; -1 the Stolen Field Generator). */
    static String campName(int meta) {
        ItemStack s = meta < 0 ? stolenField() : campBlock(meta);
        if (s != null) {
            return s.getDisplayName();
        }
        return Lang.tr("tile.siliconage.camp." + (meta < 0 ? CampBlocksSC.FIELD_NAMES[0] : CampBlocksSC.CAMP_NAMES[Math.min(CampBlocksSC.CAMP_NAMES.length - 1, meta)])
                + ".name");
    }

    /** A relic's name (item.siliconage.relic.<name>.name). */
    static String relicName(int meta) {
        ItemStack s = RelicItemsSC.relic(meta, 1);
        return s != null ? s.getDisplayName() : Lang.tr("item.siliconage.relic." + ItemRelicSC.nameOf(meta) + ".name");
    }

    /** A biome kind's icon (the plateaus' turf, the canyons' violet oxide, Crystallite, the gold oxide, the Singular Glass). */
    private static ItemStack biomeIcon(int kind) {
        switch (kind) {
            case BiomeSubstrateSC.CANYONS:
                return rock(SubstrateBlocksSC.OXIDE_VIOLET);
            case BiomeSubstrateSC.RIDGES:
                return rock(SubstrateBlocksSC.CRYSTALLITE);
            case BiomeSubstrateSC.OXIDE:
                return rock(SubstrateBlocksSC.OXIDE_GOLD);
            case BiomeSubstrateSC.RIFT:
                return wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.GLASS);
            default:
                return rock(SubstrateBlocksSC.QUARTZ_TURF);
        }
    }

    /** ПК-17: the biome id of a BiomeSubstrateSC kind - the registered biome's, the config's while the dimension is off. */
    static int biomeId(int kind) {
        BiomeSubstrateSC b = SubstrateSC.active() ? SubstrateSC.biome(kind) : null;
        if (b != null && b.kind == kind) {
            return b.biomeID;
        }
        switch (kind) {
            case BiomeSubstrateSC.CANYONS:
                return SubstrateConfigSC.biomeIdCanyons();
            case BiomeSubstrateSC.RIDGES:
                return SubstrateConfigSC.biomeIdRidges();
            case BiomeSubstrateSC.OXIDE:
                return SubstrateConfigSC.biomeIdOxide();
            case BiomeSubstrateSC.RIFT:
                return SubstrateConfigSC.biomeIdRift();
            default:
                return SubstrateConfigSC.biomeIdPlateaus();
        }
    }

    /** «1-2»: a pool id's group in SpawnRulesSC.GROUP_MIN / GROUP_MAX (package M's, by pool id), the plan's while the tables are shorter. */
    private static String group(int id, int planMin, int planMax) {
        int lo = id < SpawnRulesSC.GROUP_MIN.length ? SpawnRulesSC.GROUP_MIN[id] : planMin, hi = id < SpawnRulesSC.GROUP_MAX.length ? SpawnRulesSC.GROUP_MAX[id] : planMax;
        return span(Math.max(1, lo), Math.max(1, Math.max(lo, hi)));
    }

    // ------------------------------------------------------------------ Ф3's stacks and numbers

    static ItemStack ruin(int meta) {
        return RuinBlocksSC.ruin == null ? null : new ItemStack(RuinBlocksSC.ruin, 1, meta);
    }

    static String ruinName(int meta) {
        ItemStack s = ruin(meta);
        return s != null ? s.getDisplayName() : Lang.tr("tile.siliconage.ruin." + BlockRuinSC.nameOf(meta) + ".name");
    }

    static ItemStack quantumPiece(int armorType) {
        return new ItemStack(ModItems.ARMOR.get(ArmorSuit.QUANTUM)[armorType]);
    }

    private static ItemStack exoPiece(int armorType) {
        return new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[armorType]);
    }

    /** A Defense chip's cut (EmpMathSC.DEFENSE_*_PCT) at tier 1..3, % (0 for none). */
    private static int defensePct(int[] table, int tier) {
        return tier < 1 || table.length == 0 ? 0 : table[Math.min(table.length, tier) - 1];
    }

    /**
     * Ф3 (§4.9): SpawnDirectorSC's weight of pool id `id` in package M's table `field` of SpawnRulesSC (PLATEAU_WEIGHTS,
     * CANYON_WEIGHTS - by pool id, appended -, NODE_WEIGHTS), the plan's `plan` while the table has no such entry.
     */
    static int weight(String field, int id, int plan) {
        try {
            Object v = SpawnRulesSC.class.getField(field).get(null);
            if (v instanceof int[] && id >= 0 && id < ((int[]) v).length) {
                return ((int[]) v)[id];
            }
        } catch (Throwable t) {
            // not there (yet): the plan's number
        }
        synchronized (UNRESOLVED) {
            UNRESOLVED.add("SpawnRulesSC." + field + "[" + id + "]");
        }
        return plan;
    }

    // ------------------------------------------------------------------ Ф2's stacks and numbers

    static ItemStack core() {
        return SettlementBlocksSC.core == null ? null : new ItemStack(SettlementBlocksSC.core);
    }

    private static ItemStack showcase(int meta) {
        return SettlementBlocksSC.showcase == null ? null : new ItemStack(SettlementBlocksSC.showcase, 1, meta);
    }

    static ItemStack dock(int meta) {
        return SettlementBlocksSC.dock == null ? null : new ItemStack(SettlementBlocksSC.dock, 1, meta);
    }

    private static String dockName(int meta) {
        ItemStack s = dock(meta);
        return s != null ? s.getDisplayName() : Lang.tr("tile.siliconage.dock." + BlockDockSC.nameOf(meta) + ".name");
    }

    /** A profession's workshop machine (the Elder: the core). */
    static ItemStack kindIcon(NpcKindSC k) {
        switch (k) {
            case ELDER:
                return core();
            case TECHNOLOGIST:
                return showcase(BlockShowcaseSC.CVD);
            case METALLURGIST:
                return showcase(BlockShowcaseSC.BLAST_FURNACE);
            case GAS_TECH:
                return showcase(BlockShowcaseSC.AIR_SEPARATOR);
            case CARTOGRAPHER:
                return showcase(BlockShowcaseSC.FOCUSER);
            case POWER_ENGINEER:                        // Ф3: the Power Engineer's workshop, the Salvager's warehouse (BlockShowcase2SC)
                return showcase2(BlockShowcase2SC.ENERGY_STORAGE);
            case SALVAGER:
                return showcase2(BlockShowcase2SC.SCRAP_PILE);
            case CAPTAIN:
                return new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[1]);
            case COMMANDANT:
                return new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]);
            case DISPATCHER:                            // Ф6: the Dispatchers' desk, the Quartermasters' depot (BlockShowcase2SC 9, 4)
                return showcase2(BlockShowcase2SC.DISPATCH_CONSOLE);
            case QUARTERMASTER:
                return showcase2(BlockShowcase2SC.CRATE);
            default:                                    // the guards, the Armourer: the guard square's armour station
                return showcase(BlockShowcaseSC.ARMOUR_STATION);
        }
    }

    /** Ф3: a showcase machine of the second block (BlockShowcase2SC), null before it is registered. */
    static ItemStack showcase2(int meta) {
        return SettlementBlocksSC.showcase2 == null ? null : new ItemStack(SettlementBlocksSC.showcase2, 1, meta);
    }

    /** «3-5», or «24» when both ends agree. */
    static String span(int lo, int hi) {
        return lo == hi ? String.valueOf(lo) : lo + "-" + hi;
    }

    /** A world tick of the day as the clock shows it (tick 0 is 6:00): «17:30». */
    static String clock(int tick) {
        int t = ((tick % 24000) + 24000) % 24000;
        return String.format(Locale.ROOT, "%d:%02d", (6 + t / 1000) % 24, t % 1000 * 60 / 1000);
    }

    /** Ticks as whole game days: «3». */
    static String days(long ticks) {
        return n(ticks / (float) SettlementSC.DAY);
    }

    /** Ticks as minutes: «5». */
    static String minutes(int ticks) {
        return n(ticks / 1200F);
    }

    // ------------------------------------------------------------------ shared with NEI's «Как получить» (ObtainHandlerSC)

    /**
     * Who drops trophy `meta` in this version (the creative tab and NEI show only these, ПТ-7), or null: a later phase. Ф3
     * (ПТ-14): the Quantum pieces - the Trooper and the Jammer, the Exo pieces - the Hollow Shell and the Executioner, the
     * Quantum Blade - the Trooper and the Shell, the Exo Blade - the Shell and the Executioner, the Pulse Emitter - the
     * Jammer, the Plasma Rifle - the Trooper, the ПЭ-1 - a Jammer carrier only (what each holds or wears).
     */
    public static String trophyDroppers(int meta) {
        String both = mobName(EntityLinemanSC.NAME) + ", " + mobName(EntityMarauderSC.NAME);
        if (meta >= TrophyTableSC.armorMeta(ArmorSuit.NANO, 0) && meta <= TrophyTableSC.armorMeta(ArmorSuit.NANO, 3)) {
            return both;
        }
        if (meta == TrophyTableSC.bladeMeta(BladeType.NANO)) {
            return mobName(EntityMarauderSC.NAME);
        }
        if (meta == TrophyTableSC.weaponMeta(WeaponType.ION_CUTTER)) {
            return both;
        }
        if (meta >= TrophyTableSC.armorMeta(ArmorSuit.QUANTUM, 0) && meta <= TrophyTableSC.armorMeta(ArmorSuit.QUANTUM, 3)) {
            return mobName(EntityTrooperSC.NAME) + ", " + mobName(EntityJammerSC.NAME);
        }
        if (meta >= TrophyTableSC.armorMeta(ArmorSuit.EXO, 0) && meta <= TrophyTableSC.armorMeta(ArmorSuit.EXO, 3)) {
            return mobName(EntityHollowShellSC.NAME) + ", " + mobName(EntityExecutionerSC.NAME);
        }
        if (meta == TrophyTableSC.bladeMeta(BladeType.QUANTUM)) {
            return mobName(EntityTrooperSC.NAME) + ", " + mobName(EntityHollowShellSC.NAME);
        }
        if (meta == TrophyTableSC.bladeMeta(BladeType.EXO)) {
            return mobName(EntityHollowShellSC.NAME) + ", " + mobName(EntityExecutionerSC.NAME);
        }
        if (meta == TrophyTableSC.weaponMeta(WeaponType.PULSE_EMITTER)) {
            return mobName(EntityJammerSC.NAME);
        }
        if (meta == TrophyTableSC.weaponMeta(WeaponType.PLASMA_RIFLE)) {
            return mobName(EntityTrooperSC.NAME);
        }
        if (meta == TrophyTableSC.META_PE1) {                      // Ф4 (ПТ-16): a Sentinel carrier's too
            return Lang.tr("sc.manual.world.cell.carrier", mobName(EntityJammerSC.NAME)) + ", "
                    + Lang.tr("sc.manual.world.cell.carrier", mobName(EntitySentinelSC.NAME));
        }
        // Ф4 (ПТ-16): the Singular pieces and blade - the Vaults' Sentinel and «Затмение»; Ф5 (ПТ-23): the chestplate and the blade the Warden's too
        if (meta >= TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 0) && meta <= TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 3)
                || meta == TrophyTableSC.bladeMeta(BladeType.SINGULAR)) {
            boolean warden = meta == TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 1) || meta == TrophyTableSC.bladeMeta(BladeType.SINGULAR);
            return mobName(EntitySentinelSC.NAME) + ", " + mobName(EntityEclipseSC.NAME) + (warden ? ", " + mobName(com.sc.mob.EntityBreakdownWardenSC.NAME) : "");
        }
        if (meta == TrophyTableSC.META_PE1M) {                     // Ф5 (ПТ-23): the Breakdown Warden's alone
            return mobName(com.sc.mob.EntityBreakdownWardenSC.NAME);
        }
        return null;
    }

    /** Trophy `meta`'s chance of one roll as percent text, the server's multiplier included ("2", "1,5"); null outside the tables. */
    public static String trophyChancePct(int meta) {
        if (meta == TrophyTableSC.META_PE1) {
            return pct(trophyChance(stat(TROPHIES, "PE1", 0.02F)));     // Ф3: the carrier's ПЭ-1 (package M's TrophyTableSC.PE1)
        }
        if (meta == TrophyTableSC.META_PE1M) {
            return wardenTrophyPct(WardenRulesSC.TROPHY_PE1M);          // Ф5 (ПТ-23): the Warden's roll
        }
        if (meta >= 0 && meta < TrophyTableSC.META_BLADE) {
            return pct(trophyChance(TrophyTableSC.PIECE[Math.min(TrophyTableSC.PIECE.length - 1, meta / 4)]));
        }
        if (meta >= TrophyTableSC.META_BLADE && meta < TrophyTableSC.META_WEAPON) {
            return pct(trophyChance(TrophyTableSC.BLADE[Math.min(TrophyTableSC.BLADE.length - 1, meta - TrophyTableSC.META_BLADE)]));
        }
        if (meta >= TrophyTableSC.META_WEAPON && meta < TrophyTableSC.META_WEAPON + TrophyTableSC.WEAPON.length) {
            return pct(trophyChance(TrophyTableSC.WEAPON[meta - TrophyTableSC.META_WEAPON]));
        }
        return null;
    }

    /** Ф5 (ПТ-23): one of the Warden's trophy chances as percent text - its share times the server's gearDropMultiplier (WardenRulesSC.trophyMeta). */
    static String wardenTrophyPct(float share) {
        return pct(Math.max(0F, share * SubstrateConfigSC.gearDropMultiplier));
    }

    /** Ф5: where a JournalSC source is - «Сундуки Мёртвых Узлов», the Sentinel's / the Warden's name (the handbook's table, NEI's page). */
    public static String journalSource(int source) {
        if (source == com.sc.lore.JournalSC.SRC_SENTINEL) {
            return mobName(EntitySentinelSC.NAME);
        }
        if (source == com.sc.lore.JournalSC.SRC_WARDEN) {
            return mobName(com.sc.mob.EntityBreakdownWardenSC.NAME);
        }
        return Lang.tr("sc.manual.world.journals.src." + source);
    }

    /** Ф5: a JournalSC source's chance as text - «6%», the Warden's «первая победа». */
    public static String journalChance(int source) {
        return source == com.sc.lore.JournalSC.SRC_WARDEN ? Lang.tr("sc.manual.world.journals.firstkill") : pct(com.sc.lore.JournalSC.chance(source)) + "%";
    }

    /** Ф5, NEI's «Как получить» of journal №`number`: every source whose pool has it, with its chance («Сундуки Хранилищ - 30%; ...»). */
    public static String journalWhere(int number) {
        StringBuilder sb = new StringBuilder();
        for (int src = 0; src < com.sc.lore.JournalSC.POOLS.length; src++) {
            for (int n : com.sc.lore.JournalSC.POOLS[src]) {
                if (n == number) {
                    sb.append(sb.length() == 0 ? "" : "; ").append(journalSource(src)).append(" - ").append(journalChance(src));
                }
            }
        }
        return sb.toString();
    }

    /** Ф5: «№5, №6, №7, №8» - the journals' numbers a JournalSC source gives. */
    static String journalNumbers(int source) {
        StringBuilder sb = new StringBuilder();
        int[] pool = source >= 0 && source < com.sc.lore.JournalSC.POOLS.length ? com.sc.lore.JournalSC.POOLS[source] : new int[0];
        for (int i = 0; i < pool.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(Lang.tr("sc.manual.world.journals.no", pool[i]));
        }
        return sb.toString();
    }

    /** The real item a trophy of `meta` stands for (its armour piece, blade, weapon, ПЭ-1 / ПЭ-1М), or null. */
    public static ItemStack realOfTrophy(int meta) {
        ArmorSuit[] suits = ArmorSuit.values();
        if (meta >= 0 && meta < TrophyTableSC.META_BLADE && meta / 4 < suits.length) {
            return new ItemStack(ModItems.ARMOR.get(suits[meta / 4])[meta % 4]);
        }
        BladeType[] blades = BladeType.values();
        if (meta >= TrophyTableSC.META_BLADE && meta - TrophyTableSC.META_BLADE < blades.length && meta < TrophyTableSC.META_WEAPON) {
            return blade(blades[meta - TrophyTableSC.META_BLADE]);
        }
        WeaponType[] weapons = WeaponType.values();
        if (meta >= TrophyTableSC.META_WEAPON && meta - TrophyTableSC.META_WEAPON < weapons.length && meta < TrophyTableSC.META_PE1) {
            return weapon(weapons[meta - TrophyTableSC.META_WEAPON]);
        }
        if (meta == TrophyTableSC.META_PE1 || meta == TrophyTableSC.META_PE1M) {
            return new ItemStack(ModItems.empGun, 1, meta == TrophyTableSC.META_PE1M ? com.sc.emp.ItemEmpGunSC.META_UPGRADED : com.sc.emp.ItemEmpGunSC.META_BASIC);
        }
        return null;
    }

    /** The NEI line of loot `meta`: who drops it and how often. */
    public static String lootLine(int meta) {
        if (meta == LOOT_NANO_SCRAP) {
            return Lang.tr("sc.manual.world.loot.nanoScrap", pct(stat(DROPS, "SCRAP_CHANCE", 0.4F)));
        }
        if (meta == LOOT_STOLEN_CARGO) {
            return Lang.tr("sc.manual.world.loot.stolenCargo", pct(stat(DROPS, "CARGO_CHANCE", 0.05F)), ReputationSC.CARGO);
        }
        // Ф3 (ПТ-13): package M's / X's chances by name
        if (meta == LOOT_QUANTUM_SCRAP) {
            return Lang.tr("sc.manual.world.loot.quantumScrap", pct(stat(TROOPER, "SCRAP_CHANCE", 0.5F)), pct(stat(JAMMER, "SCRAP_CHANCE", 0.4F)));
        }
        if (meta == LOOT_EXO_SCRAP) {
            return Lang.tr("sc.manual.world.loot.exoScrap", n(stat(EXECUTIONER, "SCRAP_MIN", 1F)), n(stat(EXECUTIONER, "SCRAP_MAX", 2F)));
        }
        if (meta == LOOT_NANITE_DUST) {
            return Lang.tr("sc.manual.world.loot.naniteDust", pct(stat(NANITE, "DUST_CHANCE", 0.5F)));
        }
        if (meta == LOOT_SHIELD_CORE) {
            return Lang.tr("sc.manual.world.loot.shieldCore", pct(stat(EXECUTIONER, "CORE_CHANCE", 0.15F)));
        }
        // Ф4 (ПТ-18): package M's / N's chances by name; who buys them - TradeTableSC (ПО-14)
        if (meta == LOOT_BOILED_LOOP) {
            return Lang.tr("sc.manual.world.loot.boiledLoop", pct(stat(OVERHEATED, "LOOP_CHANCE", 0.2F)))
                    + buyerSuffix(meta);
        }
        if (meta == LOOT_INDUCTION_THREAD) {
            return Lang.tr("sc.manual.world.loot.inductionThread", pct(stat(PHANTOM, "THREAD_CHANCE", 0.3F)))
                    + buyerSuffix(meta);
        }
        if (meta == LOOT_PLASMA_CONDENSATE) {
            return Lang.tr("sc.manual.world.loot.plasmaCondensate", pct(stat(STRIDER, "CONDENSATE_CHANCE", 0.5F)))
                    + buyerSuffix(meta);
        }
        if (meta == LOOT_SINGULAR_SCRAP) {
            return Lang.tr("sc.manual.world.loot.singularScrap", n(stat(ECLIPSE, "SCRAP_MIN", 2F)),
                    n(stat(ECLIPSE, "SCRAP_MAX", 3F))) + buyerSuffix(meta);
        }
        if (meta == LOOT_NATURAL_WAFER) {
            return Lang.tr("sc.manual.world.loot.naturalWafer", pct(stat(CRAB, "WAFER_CHANCE", 0.05F)))
                    + buyerSuffix(meta);
        }
        return Lang.tr("sc.manual.world.loot.shellFirmware");
    }

    /**
     * Ф4 (ПО-14): « Покупает Газовщик: 1 шт. - 3 КЧ.» - the settler whose TradeTableSC deal takes loot `meta` for chips (package C's
     * table), "" while there is none.
     */
    static String buyerSuffix(int meta) {
        ItemStack s = loot(meta);
        if (s == null) {
            return "";
        }
        for (TradeOfferSC o : TradeTableSC.all()) {
            if (o.outKind == TradeOfferSC.OUT_ITEM && o.out != null && o.out.isChips() && o.in != null && o.in.length > 0 && o.in[0] != null
                    && !o.in[0].isChips() && o.in[0].matches(s)) {
                return " " + Lang.tr("sc.manual.world.loot.buyer", Lang.tr(o.kind.langKey()), o.in[0].count, o.out.count);
            }
        }
        return "";
    }

    /** Ф3, NEI's «Как получить» of the loot: the spawn egg of the mob that drops loot `meta` (the Salvager's buyers aside), or null. */
    public static ItemStack lootEgg(int meta) {
        switch (meta) {
            case LOOT_FIRMWARE:
                return egg(MobEntitiesSC.ID_LINEMAN);
            case LOOT_QUANTUM_SCRAP:
                return egg(MobEntitiesSC.ID_TROOPER);
            case LOOT_EXO_SCRAP:
            case LOOT_SHIELD_CORE:
                return egg(MobEntitiesSC.ID_EXECUTIONER);
            case LOOT_NANITE_DUST:
                return egg(MobEntitiesSC.ID_NANITE);
            case LOOT_BOILED_LOOP:                      // Ф4 (ПТ-18): who drops the new loot
                return egg(MobEntitiesSC.ID_OVERHEATED);
            case LOOT_INDUCTION_THREAD:
                return egg(MobEntitiesSC.ID_PHANTOM);
            case LOOT_PLASMA_CONDENSATE:
                return egg(MobEntitiesSC.ID_STRIDER);
            case LOOT_SINGULAR_SCRAP:
                return egg(MobEntitiesSC.ID_ECLIPSE);
            case LOOT_NATURAL_WAFER:
                return egg(MobEntitiesSC.ID_WAFER_CRAB);
            default:
                return egg(MobEntitiesSC.ID_MARAUDER);
        }
    }

    /** Ф3: what the handbook says of ruin block `meta` (BlockRuinSC: where it stands, what it breaks into, package X's chances) - NEI's page too. */
    public static String ruinLine(int meta) {
        String k = "sc.manual.world.ruin." + BlockRuinSC.nameOf(meta);
        switch (meta) {
            case BlockRuinSC.RUIN_METAL:
                return Lang.tr(k, n(stat(RUIN_BLOCK, "METAL_SCRAP_PCT", 30F)));
            case BlockRuinSC.BURNT_PANEL:
                return Lang.tr(k, n(stat(RUIN_BLOCK, "PANEL_FIRMWARE_PCT", 20F)));
            case BlockRuinSC.MACHINE_HUSK:
                return Lang.tr(k, n(stat(RUIN_BLOCK, "HUSK_TRANSISTOR_PCT", 10F)));
            case RUIN_VAULT_PLATING:                    // Ф4 (ПХ-2): package C's numbers by name
                return Lang.tr(k, n(stat(RUIN_BLOCK, "PLATING_LEVEL", 3F)), n(stat(RUIN_BLOCK, "PLATING_SCRAP_PCT", 50F)));
            case RUIN_VAULT_LAMP:
                return Lang.tr(k, n(stat(RUIN_BLOCK, "LAMP_LIGHT", 10F)));
            case RUIN_BURNT_COIL:                       // Ф5 (ПЛ-4): package J's numbers
                return Lang.tr("sc.manual.world.ruin." + RUIN_F5_NAME, BlockRuinSC.COIL_SCRAP_PCT, BlockRuinSC.COIL_FIRMWARE_PCT);
            default:
                return Lang.tr(k);
        }
    }

    /**
     * Ф3 (ПТ-11, ПТ-12), NEI's «Как получить» of a trophy: where and for what it is restored - the Armourer's service
     * (TradeTableSC's OUT_RESTORE deal for its meta: the sides, the level) and one's own station (his components without the
     * chips + TrophyRestoreSC.STATION_EU); a Singular one only at the Singular station; null - it isn't restored (ПЭ-1М, Ф5).
     */
    public static String restoreLine(int meta) {
        if (meta == TrophyTableSC.META_PE1M) {                     // Ф5 (ПТ-26): Singular-level, the Singular station alone
            return Lang.tr("sc.manual.world.restore.line.pe1m", clotName(), BookContent.num(TrophyRestoreSC.SINGULAR_STATION_EU));
        }
        if (TrophyTableSC.level(meta) == ArmorSuit.SINGULAR) {
            return Lang.tr("sc.manual.world.restore.line.singular", clotName(), BookContent.num(TrophyRestoreSC.SINGULAR_STATION_EU));
        }
        TradeOfferSC o = restoreDeal(meta);
        if (o == null) {
            return meta == TrophyTableSC.META_PE1M ? null : Lang.tr("sc.manual.world.restore.line.station", BookContent.num(TrophyRestoreSC.STATION_EU));
        }
        return Lang.tr("sc.manual.world.restore.line", sides(o.in, 1, false), levelName(o.minLevel), stationPrice(o));
    }

    /** The Armourer's OUT_RESTORE deal whose trophy side takes meta `meta`, or null (none - or package T's table not written yet). */
    static TradeOfferSC restoreDeal(int meta) {
        for (TradeOfferSC o : TradeTableSC.offers(NpcKindSC.ARMOURER)) {
            if (o.outKind == OUT_RESTORE && o.in != null && o.in.length > 0) {
                for (int m : metasOf(o.in[0])) {
                    if (m == meta) {
                        return o;
                    }
                }
            }
        }
        return null;
    }

    /** ПТ-11: one's own station's price for what deal `o` restores - its components (no chips) and the station's EU. */
    private static String stationPrice(TradeOfferSC o) {
        String parts = sides(o.in, 1, true);
        String eu = BookContent.num(TrophyRestoreSC.STATION_EU) + " EU";
        return parts.isEmpty() ? eu : parts + " + " + eu;
    }

    static String clotName() {
        return ModItems.singularClot != null ? new ItemStack(ModItems.singularClot).getDisplayName() : Lang.tr("item.siliconage.singularClot.name");
    }

    /**
     * Ф2, NEI's «Как получить» and the docks' article: who sells `s` in the settlements (TradeTableSC) - «Картограф
     * прокола: 8 КЧ, с уровня «Чужак», 2 в сутки» -, or null if no deal gives it.
     */
    public static String sellerLine(ItemStack s) {
        TradeOfferSC o = dealFor(s);
        if (o == null) {
            return null;
        }
        return Lang.tr("sc.manual.world.trade.seller", Lang.tr(o.kind.langKey()), chipsOf(o), levelName(o.minLevel), limitText(o));
    }

    /** The deal whose output is `s` (an item of OUT_ITEM, or the map of OUT_MAP), null none. */
    static TradeOfferSC dealFor(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return null;
        }
        for (TradeOfferSC o : TradeTableSC.all()) {
            if (o.outKind == TradeOfferSC.OUT_MAP ? s.getItem() == SettlementItemsSC.settlementMap
                    : o.outKind == TradeOfferSC.OUT_ITEM && o.out != null && o.out.matches(s)) {
                return o;
            }
        }
        return null;
    }

    /** What the handbook says of rock `meta` (where it lies, what it drops). */
    public static String rockLine(int meta) {
        return Lang.tr("sc.manual.world.block." + ROCK_NAMES[meta >= 0 && meta < ROCK_NAMES.length ? meta : 0]);
    }

    public static String mobName(String name) {
        return Lang.tr("entity.SiliconAge." + name + ".name");
    }

    /** A share as percent text: 0.015 -> "1.5" ("1,5" in Russian), 0.02 -> "2". */
    public static String pct(float share) {
        return BookContent.number(Math.round(share * 1000F) / 10F);
    }

    // ------------------------------------------------------------------ package M's numbers

    /**
     * Public static number `field` of package M's class `cls`, or `plan` (docs/plan-substrate.md §6, §7) while it has
     * none - the page builds before M's constants exist; the self-test prints what is still on the plan's value.
     */
    static float stat(String cls, String field, float plan) {
        try {
            Object v = Class.forName(cls, true, BookWorldSC.class.getClassLoader()).getField(field).get(null);
            if (v instanceof Number) {
                return ((Number) v).floatValue();
            }
        } catch (Throwable t) {
            // not there (yet): the plan's number
        }
        synchronized (UNRESOLVED) {
            UNRESOLVED.add(cls.substring(cls.lastIndexOf('.') + 1) + "." + field);
        }
        return plan;
    }

    /** The self-test: package M's constants the bestiary still takes from the plan (after a build of the book). */
    public static List<String> unresolved() {
        synchronized (UNRESOLVED) {
            return new ArrayList<String>(UNRESOLVED);
        }
    }

    // ------------------------------------------------------------------

    private static String[] trophyKeys() {
        String[] out = new String[TrophyTableSC.METAS];
        String[] pieces = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (ArmorSuit s : ArmorSuit.values()) {
            for (int t = 0; t < 4; t++) {
                int m = TrophyTableSC.armorMeta(s, t);
                if (m >= 0 && m < out.length) {
                    out[m] = s.textureName + pieces[t];
                }
            }
        }
        for (BladeType b : BladeType.values()) {
            int m = TrophyTableSC.bladeMeta(b);
            if (m >= 0 && m < out.length) {
                String suit = b.name().toLowerCase(Locale.ROOT);
                out[m] = "blade" + Character.toUpperCase(suit.charAt(0)) + suit.substring(1);
            }
        }
        for (WeaponType w : WeaponType.values()) {
            int m = TrophyTableSC.weaponMeta(w);
            if (m >= 0 && m < out.length) {
                out[m] = w.textureName;
            }
        }
        out[TrophyTableSC.META_PE1] = "pe1";
        out[TrophyTableSC.META_PE1M] = "pe1m";
        return out;
    }

    static ItemStack rock(int meta) {
        return SubstrateBlocksSC.rock == null ? null : new ItemStack(SubstrateBlocksSC.rock, 1, meta);
    }

    private static String rockName(int meta) {
        ItemStack s = rock(meta);
        return s != null ? s.getDisplayName() : Lang.tr("tile.siliconage.substrateRock." + ROCK_NAMES[meta] + ".name");
    }

    private static ItemStack druse() {
        return SubstrateBlocksSC.druse == null ? null : new ItemStack(SubstrateBlocksSC.druse);
    }

    /** The Substrate's ore of that kind, else the mod's ordinary one (the same meta). */
    private static ItemStack ore(OreEntry ore) {
        return new ItemStack(SubstrateBlocksSC.ore != null ? SubstrateBlocksSC.ore : ModBlocks.oreSC, 1, ore.meta());
    }

    static ItemStack egg(int poolId) {
        return MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, poolId);
    }

    static ItemStack trophy(int meta) {
        return MobItemsSC.trophy == null ? null : new ItemStack(MobItemsSC.trophy, 1, meta);
    }

    static ItemStack loot(int meta) {
        return MobItemsSC.combatLoot == null ? null : new ItemStack(MobItemsSC.combatLoot, 1, meta);
    }

    static String lootName(int meta) {
        ItemStack s = loot(meta);
        return s != null ? s.getDisplayName() : Lang.tr("item.siliconage.combatLoot." + LOOT_NAMES[meta] + ".name");
    }

    static ItemStack nanoPiece(int armorType) {
        return new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[armorType]);
    }

    static ItemStack blade(BladeType t) {
        return new ItemStack(ModItems.BLADES.get(t));
    }

    private static ItemStack weapon(WeaponType t) {
        return new ItemStack(ModItems.WEAPONS.get(t));
    }

    static String suitName(ArmorSuit s) {
        return Lang.tr("sc.suit." + s.name().toLowerCase(Locale.ROOT));
    }

    /** A full set's armour points (Nano 20, Quantum 22, Exo 24, Singular 28). */
    static int suitPoints(ArmorSuit s) {
        int p = 0;
        for (int t = 0; t < 4; t++) {
            p += s.material.getDamageReductionAmount(t);
        }
        return p;
    }

    /** A full set of iron armour's points: what a mob's suppressed / emergency armour gives (15). */
    static int ironPoints() {
        int p = 0;
        for (int t = 0; t < 4; t++) {
            p += MobArmorMathSC.ironPoints(t);
        }
        return p;
    }

    private static int empTicks(ArmorSuit s, EmpMode m) {
        return EmpMathSC.durationTicks(s, m, false, 0, false, ConfigSC.empDurationMultiplier);
    }

    /** Seconds the siphon takes to drain a full reserve of that suit (a share of the full reserve a second). */
    private static String siphonSeconds(ArmorSuit s) {
        double pctPerSecond = EmpMathSC.siphonPctPerSecond(s, false, 0, ConfigSC.empSiphonRateMultiplier);
        return pctPerSecond <= 0 ? "-" : String.valueOf(Math.round(100D / pctPerSecond));
    }

    /** One roll's chance at the base of a category, with the server's gearDropMultiplier (no Looting, no bad luck). */
    static float trophyChance(float base) {
        return TrophyTableSC.chance(base, 0, false, false, SubstrateConfigSC.gearDropMultiplier, 1);
    }

    private static long eu(long base, float mul) {
        return (long) (base * (double) mul);
    }

    private static String dimName(int id) {
        return Lang.trOr("sc.wl.dim." + id, Lang.tr("sc.wl.dim.other", id));
    }

    /** "4", "1.5" ("1,5" in Russian): one decimal at most. */
    static String n(float v) {
        return BookContent.number(Math.round(v * 10F) / 10F);
    }

    /** "0.5" ("0,5"): a multiplier with up to two decimals. */
    static String multiplier(float m) {
        return BookContent.number(Math.round(m * 100F) / 100F);
    }

    /** Ticks as seconds: "16", "8.8" ("8,8" in Russian). */
    static String sec(int ticks) {
        return n(ticks / 20F);
    }
}
