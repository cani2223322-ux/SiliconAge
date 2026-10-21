package com.sc.manual;

import java.util.List;
import java.util.Random;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.embassy.CommandEmbassySC;
import com.sc.embassy.EmbassyBeamSC;
import com.sc.embassy.EmbassyBlocksSC;
import com.sc.embassy.EmbassyItemsSC;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyRepairSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.embassy.EmbassySiteSC;
import com.sc.embassy.ItemEmbassyFinderSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.util.ConfigSC;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Подложка Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md §9.2, package D): the six articles BookWorldSC.add puts at the
 * end of the WORLD chapter (BookWorldSC.F6_ARTICLES, in this order) - «Посольства Наладчиков» (the lore of proposal §2: the
 * Lithographers' outer stations, the channel to «the green world with one moon», the crews cut off; where - EmbassySiteSC's grid, chance
 * and land, the guaranteed one in its ring; how to find one - the Direction Finder, the first-login line, the dome, the beam), «Город
 * посольства» (EmbassyLayoutSC: the dome, the floor, the wall, the quarters, the Vokzal; the roster of EmbassyLayoutSC.roster over many
 * seeds; the protection of the Substrate's settlements and what the Overworld has not), «Большое кольцо: рейсы» (EmbassyRulesSC: the
 * outbound rides' prices by level, the window, the boarding, the soft admission, the round trip, the guarantee), «Рейсы домой» (the
 * Dispatcher at a Substrate dock, HOME / BACK / OTHER), «Лавка и Интендант» (what an embassy opens, the Quartermaster's deals and the
 * Ambassador's contracts of package T's tables, the ceiling of «Доверие») and «Правила посольств» (the config section embassy with the
 * server's numbers, the command, the compatibility notes).
 * <p>
 * Every number from the code: the core's classes (EmbassySiteSC, EmbassyLayoutSC, EmbassyRulesSC, ReputationSC, SettlementSizeSC) and the
 * fixed constants of the packages' stubs (ItemEmbassyFinderSC, EmbassyBeamSC, EmbassyRepairSC, CommandEmbassySC) directly; package T's
 * deals and contracts from TradeTableSC / ContractTableSC as they are (the Quartermaster's deals, embassyPool, takeLevel, DISPATCH_DAYS).
 * G on the Direction Finder, the Great Ring's core and the Ф6 showcase metas opens these pages. Plain data: safe
 * on a server (no client class touched).
 */
final class BookEmbassySC {

    /** The roster table is made of this many seeds' rosters (EmbassyLayoutSC.roster is pure and cheap). */
    private static final int ROSTER_SEEDS = 256;
    /** The Ambassador's contract keys package T appends (docs/plan-substrate-tech-f6.md §2.4) - on the Ambassador's board only. */
    static final String EMB_PREFIX = "emb";

    private BookEmbassySC() {
    }

    static void add(List<BookEntry> list, BookChapter c) {
        list.add(embassies(c));
        list.add(embassyCity(c));
        list.add(flights(c));
        list.add(flightsBack(c));
        list.add(embassyTrade(c));
        list.add(embassyRules(c));
    }

    // ------------------------------------------------------------------ «Посольства Наладчиков» (ПЕ-1..ПЕ-4, ПЕ-11, ПЕ-12)

    private static BookEntry embassies(BookChapter c) {
        ItemStack finder = EmbassyItemsSC.finder(1);
        BookEntry e = new BookEntry("world.embassies", c, finder != null ? finder : BookWorldSC.icon(), Lang.tr("sc.manual.world.embassies.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.embassies.title"))).addAll(BookContent.paras("sc.manual.world.embassies"));
        if (!SettlementSC.embassiesGenerate()) {
            e.add(BookEl.warn(Lang.tr(SubstrateConfigSC.embassies ? "sc.manual.world.embassies.offdim" : "sc.manual.world.embassies.off")));
        }

        // where (ПЕ-1..ПЕ-3): the server's numbers of the section embassy, EmbassySiteSC's bands
        int grid = EmbassySiteSC.grid();
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassies.wherehead")));
        e.addAll(BookContent.paras("sc.manual.world.embassies.where", new Object[]{grid, grid, SubstrateConfigSC.embassyChance,
                EmbassySiteSC.CANDIDATES, grid / 4}, new Object[]{grid / 2, SubstrateConfigSC.embassySpawnMin},
                new Object[]{SubstrateConfigSC.embassyRingMin, SubstrateConfigSC.embassyRingMax, EmbassySiteSC.FALLBACK_MIN, EmbassySiteSC.FALLBACK_MAX},
                new Object[]{EmbassySiteSC.GOOD_PCT, EmbassySiteSC.FOREST_PCT, EmbassySiteSC.HILLS_PCT, EmbassySiteSC.BAND_NEAR, EmbassySiteSC.BAND_MID,
                        EmbassySiteSC.BAND_FAR,
                        EmbassySiteSC.VILLAGE_CLEAR},
                new Object[]{Lang.tr("generator.default"), Lang.tr("generator.largeBiomes")}));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.embassies.old")));

        // how to find one (ПЕ-11, ПЕ-12): the Direction Finder and its recipe, the first-login line, the dome, the beam
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassies.findhead"), finder));
        e.add(BookEl.item(finder, finder != null ? finder.getDisplayName() : Lang.tr("item.siliconage.embassyFinder.name"),
                Lang.tr("sc.manual.world.embassies.finder", ItemEmbassyFinderSC.SEARCH, BookWorldSC.sec(ItemEmbassyFinderSC.COOLDOWN))));
        if (finder != null) {
            BookContent.crafts(e, finder);
        }
        e.add(BookEl.item(new ItemStack(net.minecraft.init.Items.paper), Lang.tr("sc.manual.world.embassies.hint"),
                Lang.tr("sc.manual.world.embassies.hint.desc", ItemEmbassyFinderSC.SEARCH)));
        SettlementSizeSC z = SettlementSizeSC.EMBASSY;
        e.add(BookEl.item(BookWorldSC.core(), Lang.tr("sc.manual.world.embassies.dome"), Lang.tr("sc.manual.world.embassies.dome.desc",
                BookWorldSC.span(z.radiusMin, z.radiusMax))));
        e.add(BookEl.item(showcase2(BlockShowcase2SC.GRAVITY_COIL), Lang.tr("sc.manual.world.embassies.beam"), Lang.tr("sc.manual.world.embassies.beam.desc",
                EmbassyBeamSC.BEAM_VIEW, EmbassyBeamSC.SEND_REACH)));
        e.add(BookEl.link("world.embassyCity", Lang.tr("sc.manual.world.embassyCity.title")));
        e.add(BookEl.link("world.flights", Lang.tr("sc.manual.world.flights.title")));
        e.add(BookEl.link("world.flightsBack", Lang.tr("sc.manual.world.flightsBack.title")));
        e.add(BookEl.link("world.embassyTrade", Lang.tr("sc.manual.world.embassyTrade.title")));
        e.add(BookEl.link("world.embassyRules", Lang.tr("sc.manual.world.embassyRules.title")));
        e.add(BookEl.link("world.lithographers", Lang.tr("sc.manual.world.lithographers.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.about(finder);
        return e;
    }

    // ------------------------------------------------------------------ «Город посольства» (ПЕ-5..ПЕ-8, ПЕ-13, ПЖ-13, ПГ-21)

    private static BookEntry embassyCity(BookChapter c) {
        ItemStack core = BookWorldSC.core();
        BookEntry e = new BookEntry("world.embassyCity", c, core != null ? core : BookWorldSC.icon(), Lang.tr("sc.manual.world.embassyCity.title"));
        SettlementSizeSC z = SettlementSizeSC.EMBASSY;
        e.add(BookEl.title(Lang.tr("sc.manual.world.embassyCity.title"))).addAll(BookContent.paras("sc.manual.world.embassyCity",
                new Object[]{BookWorldSC.span(z.radiusMin, z.radiusMax), EmbassyLayoutSC.LEVEL_OUT, EmbassyLayoutSC.BLEND},
                new Object[]{EmbassyLayoutSC.FLOOR_MIN, EmbassyLayoutSC.FLOOR_MAX, EmbassyLayoutSC.floorY(BiomeGenBase.plains.rootHeight)}));
        if (!SubstrateConfigSC.embassies) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.embassyCity.off")));
        }

        // the quarters from the middle out (ПЕ-7, ПЕ-8): EmbassyLayoutSC's sizes
        int cap = 2 * EmbassyLayoutSC.CAP_HALF + 1, square = 2 * EmbassyLayoutSC.SQUARE_HALF + 1, vokzal = 2 * EmbassyLayoutSC.VOKZAL_HALF + 1;
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyCity.quartershead")));
        part(e, core, "heart", cap, cap);
        part(e, showcase(BlockShowcaseSC.ARMOUR_STATION), "square", square, square, EmbassyLayoutSC.PADS);
        part(e, showcase(BlockShowcaseSC.CVD), "workshops", EmbassyLayoutSC.WORKSHOP, EmbassyLayoutSC.WORKSHOP);
        part(e, showcase2(BlockShowcase2SC.CRATE), "depots");
        part(e, new ItemStack(Blocks.leaves), "greenhouse");
        part(e, BookWorldSC.rock(BookWorldSC.BRICK), "cells");
        part(e, null, "wall", EmbassyLayoutSC.WALL_IN, EmbassyLayoutSC.WALL_H, EmbassyLayoutSC.WALL_T, EmbassyLayoutSC.TOWER_H, EmbassyLayoutSC.GATE_W);
        part(e, BookWorldSC.dock(BlockDockSC.BEACON), "vokzal", vokzal, vokzal, EmbassyLayoutSC.VOKZAL_OUT);
        part(e, showcase2(BlockShowcase2SC.CRYO_TANK), "tanks");

        // the settlers and the guards (ПЖ-13, ПГ-21): the counts of EmbassyLayoutSC.roster over many seeds
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyCity.rosterhead"), BookWorldSC.egg(com.sc.mob.MobEntitiesSC.ID_TUNER)));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.who"), Lang.tr("sc.manual.world.t.count"), Lang.tr("sc.manual.world.t.where"));
        int[][] span = rosterSpan();
        NpcKindSC[] order = {NpcKindSC.ELDER, NpcKindSC.DISPATCHER, NpcKindSC.QUARTERMASTER, NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST,
                NpcKindSC.GAS_TECH, NpcKindSC.CARTOGRAPHER, NpcKindSC.SALVAGER, NpcKindSC.POWER_ENGINEER, NpcKindSC.SENTRY, NpcKindSC.SENIOR_WARDEN,
                NpcKindSC.CAPTAIN, NpcKindSC.COMMANDANT};
        for (NpcKindSC k : order) {
            int lo = span[k.ordinal()][0], hi = span[k.ordinal()][1];
            if (hi <= 0) {
                continue;
            }
            t.row(BookWorldSC.kindIcon(k), Lang.tr(k.langKey(true)), BookWorldSC.span(lo, hi), Lang.tr("sc.manual.world.embassyCity.where." + k.key));
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.embassyCity.roster.note", BookWorldSC.span(z.tunersMin, z.tunersMax), z.guards() + 2,
                Lang.tr(NpcKindSC.ARMOURER.langKey()))));
        e.add(BookEl.item(BookWorldSC.kindIcon(NpcKindSC.ELDER), Lang.tr(NpcKindSC.ELDER.langKey(true)), Lang.tr("sc.manual.world.embassyCity.ambassador")));

        // the protection (ПЕ-13): the Substrate's settlements' own, what the Overworld has not
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyCity.protecthead")));
        e.addAll(BookContent.paras("sc.manual.world.embassyCity.protect", new Object[]{SettlementSC.BELT}, new Object[]{EmbassyLayoutSC.DOCK_ZONE}));
        e.add(BookEl.link("world.flights", Lang.tr("sc.manual.world.flights.title")));
        e.add(BookEl.link("world.embassies", Lang.tr("sc.manual.world.embassies.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.about(showcase2(BlockShowcase2SC.CRYO_TANK));
        return e;
    }

    /** One quarter: sc.manual.world.embassyCity.part.<key> and its .desc (with `args`). */
    private static void part(BookEntry e, ItemStack icon, String key, Object... args) {
        String k = "sc.manual.world.embassyCity.part." + key;
        e.add(BookEl.item(icon, Lang.tr(k), args.length == 0 ? Lang.tr(k + ".desc") : Lang.tr(k + ".desc", args)));
    }

    /**
     * {least, most} of every kind (by ordinal) over ROSTER_SEEDS embassies' rosters (EmbassyLayoutSC.roster - pure): the table's
     * counts, the code's own (the extras vary by the seed).
     */
    static int[][] rosterSpan() {
        int kinds = NpcKindSC.values().length;
        int[][] out = new int[kinds][2];
        for (int[] o : out) {
            o[0] = Integer.MAX_VALUE;
        }
        Random r = new Random(0xE3BA55EL);
        SettlementSizeSC z = SettlementSizeSC.EMBASSY;
        for (int i = 0; i < ROSTER_SEEDS; i++) {
            long seed = r.nextLong();
            int[] n = new int[kinds];
            for (NpcKindSC k : EmbassyLayoutSC.roster(new EmbassySiteSC.Site(0, 0, z.radius(seed), EmbassyLayoutSC.FLOOR_MIN, seed, false))) {
                n[k.ordinal()]++;
            }
            for (int k = 0; k < kinds; k++) {
                out[k][0] = Math.min(out[k][0], n[k]);
                out[k][1] = Math.max(out[k][1], n[k]);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ «Большое кольцо: рейсы» (ПЯ-1..ПЯ-7, ПЯ-10..ПЯ-16)

    private static BookEntry flights(BookChapter c) {
        ItemStack ring = ring(), coil = showcase2(BlockShowcase2SC.GRAVITY_COIL), console = showcase2(BlockShowcase2SC.DISPATCH_CONSOLE);
        BookEntry e = new BookEntry("world.flights", c, ring != null ? ring : coil != null ? coil : BookWorldSC.icon(), Lang.tr("sc.manual.world.flights.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.flights.title"))).addAll(BookContent.paras("sc.manual.world.flights", null,
                new Object[]{EmbassyRulesSC.flightsPerDay()}));
        if (!channelOpen()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.flights.off")));
        }

        // the rides out (ПЯ-4..ПЯ-7, ПЯ-10, ПЯ-11): EmbassyRulesSC's bases x the levels' multipliers, without the embassy's spread
        e.add(BookEl.head(Lang.tr("sc.manual.world.flights.rideshead"), console));
        e.add(prices(new int[]{EmbassyRulesSC.RIDE_INTRO, EmbassyRulesSC.RIDE_FIRST, EmbassyRulesSC.RIDE_DOCK, EmbassyRulesSC.RIDE_RESCUE}));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.flights.prices.note", TradeRulesSC.SEED_SPREAD_PCT)));
        ride(e, EmbassyRulesSC.RIDE_INTRO);
        ride(e, EmbassyRulesSC.RIDE_FIRST);
        ride(e, EmbassyRulesSC.RIDE_DOCK);
        ride(e, EmbassyRulesSC.RIDE_RESCUE, EmbassyRulesSC.RESCUE_SCATTER, BookWorldSC.days(EmbassyRulesSC.RESCUE_TICKS));

        // the window and the boarding (ПЯ-3, ПЯ-6)
        e.add(BookEl.head(Lang.tr("sc.manual.world.flights.windowhead"), ring));
        e.addAll(BookContent.paras("sc.manual.world.flights.window", new Object[]{BookWorldSC.sec(EmbassyRulesSC.COUNTDOWN_TICKS),
                BookWorldSC.sec(EmbassyRulesSC.VORTEX_TICKS), BookWorldSC.sec(EmbassyRulesSC.COOL_TICKS), EmbassyRulesSC.PASSENGERS},
                new Object[]{2 * EmbassyLayoutSC.RING_R + 1, 2 * EmbassyLayoutSC.VORTEX_HALF + 1, 2 * EmbassyLayoutSC.VORTEX_HALF + 1}));

        // the round trip (ПЯ-12): (the ride + HOME) x ROUND_TRIP_PCT, up - the example at «Партнёр» without the spread
        int out = table(EmbassyRulesSC.RIDE_FIRST, ReputationSC.PARTNER), home = table(EmbassyRulesSC.RIDE_HOME, ReputationSC.PARTNER);
        e.add(BookEl.head(Lang.tr("sc.manual.world.flights.roundhead")));
        e.addAll(BookContent.paras("sc.manual.world.flights.round", new Object[]{EmbassyRulesSC.ROUND_TRIP_PCT, out, home,
                ((out + home) * EmbassyRulesSC.ROUND_TRIP_PCT + 99) / 100,
                BookWorldSC.days(EmbassyRulesSC.BACK_TICKS)}));

        // the soft admission (ПЯ-14), the guarantee (ПЯ-13), who may not fly (ПЯ-16)
        e.add(BookEl.head(Lang.tr("sc.manual.world.flights.softhead"), SettlementItemsSC.capsules(1)));
        e.addAll(BookContent.paras("sc.manual.world.flights.soft", new Object[]{BookWorldSC.sec(EmbassyRulesSC.CONFIRM_TICKS)}));
        e.add(BookEl.head(Lang.tr("sc.manual.world.flights.guaranteehead")));
        e.addAll(BookContent.paras("sc.manual.world.flights.guarantee", new Object[]{BookWorldSC.levelName(EmbassyRulesSC.GUARANTOR_LEVEL),
                BookWorldSC.levelName(EmbassyRulesSC.FRIEND_LEVEL), EmbassyRulesSC.NEAR_DISPATCHER, EmbassyRulesSC.GUARANTEE_PCT},
                new Object[]{EmbassyRulesSC.GUARANTEE_PER_DAY, BookWorldSC.days(EmbassyRulesSC.GUARANTEE_TICKS), -EmbassyRulesSC.GUARANTEE_PENALTY}));
        e.add(BookEl.head(Lang.tr("sc.manual.world.flights.ruleshead")));
        e.addAll(BookContent.paras("sc.manual.world.flights.rules", new Object[]{BookWorldSC.levelName(ReputationSC.VIOLATOR)}));

        // the ring's blocks
        e.add(BookEl.item(ring, ring != null ? ring.getDisplayName() : Lang.tr("tile.siliconage.embassyRing.name"), Lang.tr("sc.manual.world.flights.ring")));
        e.add(BookEl.item(coil, coil != null ? coil.getDisplayName() : Lang.tr("tile.siliconage.showcase2.gravityCoil.name"),
                Lang.tr("sc.manual.world.flights.coil")));
        e.add(BookEl.item(console, console != null ? console.getDisplayName() : Lang.tr("tile.siliconage.showcase2.dispatchConsole.name"),
                Lang.tr("sc.manual.world.flights.console")));
        e.add(BookEl.link("world.flightsBack", Lang.tr("sc.manual.world.flightsBack.title")));
        e.add(BookEl.link("world.embassyCity", Lang.tr("sc.manual.world.embassyCity.title")));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        e.add(BookEl.link("world.navrecord", Lang.tr("sc.manual.world.navrecord.title")));
        e.about(ring, coil, console);
        return e;
    }

    /** The bridge and the Substrate let flights through (ПЯ-1, ПЯ-16: bridge.enabled, bridge.crossDimension, the dimension on). */
    static boolean channelOpen() {
        return ConfigSC.bridgeEnabled && ConfigSC.bridgeCrossDimension && SubstrateSC.active();
    }

    /** The table price of `ride` at `level` - without a settlement's spread (TradeRulesSC.playerSide with 0%); 0: closed at that level. */
    static int table(int ride, int level) {
        if (!EmbassyRulesSC.valid(ride) || EmbassyRulesSC.base(ride) <= 0 || level < EmbassyRulesSC.levelOf(ride) || level <= ReputationSC.ENEMY) {
            return 0;
        }
        return TradeRulesSC.playerSide(EmbassyRulesSC.base(ride), level, 0);   // ПС-8: with flightPriceMultiplier
    }

    /** The rides' prices by level (Знакомый .. Почётный), «-» where the level is too low, and their limits. */
    private static BookEl prices(int[] rides) {
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.ride"), BookWorldSC.levelName(ReputationSC.ACQUAINTANCE), BookWorldSC.levelName(ReputationSC.PARTNER),
                BookWorldSC.levelName(ReputationSC.OWN), BookWorldSC.levelName(ReputationSC.HONOURED), Lang.tr("sc.manual.world.t.limit"));
        for (int ride : rides) {
            String[] cells = new String[6];
            cells[0] = Lang.tr(EmbassyRulesSC.rideKey(ride));
            for (int lv = ReputationSC.ACQUAINTANCE; lv <= ReputationSC.HONOURED; lv++) {
                int p = table(ride, lv);
                cells[1 + lv - ReputationSC.ACQUAINTANCE] = ride == EmbassyRulesSC.RIDE_BACK ? Lang.tr("sc.manual.world.cell.paid")
                        : p > 0 ? String.valueOf(p) : "-";
            }
            cells[5] = limitOf(ride);
            t.row(null, cells);
        }
        return t;
    }

    /** A ride's limit in words: once a player, the day's shared flights, one a death, the return half's days, one a day. */
    private static String limitOf(int ride) {
        switch (ride) {
            case EmbassyRulesSC.RIDE_INTRO:
                return Lang.tr("sc.manual.world.cell.onceplayer");
            case EmbassyRulesSC.RIDE_RESCUE:
                return Lang.tr("sc.manual.world.cell.perdeath", BookWorldSC.days(EmbassyRulesSC.RESCUE_TICKS));
            case EmbassyRulesSC.RIDE_BACK:
                return Lang.tr("sc.manual.world.cell.days", BookWorldSC.days(EmbassyRulesSC.BACK_TICKS));
            case EmbassyRulesSC.RIDE_OTHER:
                return Lang.tr("sc.manual.world.cell.day", EmbassyRulesSC.OTHER_PER_DAY);
            default:
                return Lang.tr("sc.manual.world.cell.shared", EmbassyRulesSC.flightsPerDay());
        }
    }

    /** One ride's line: sc.flight.ride.<key> and sc.manual.world.flights.ride.<key> (with `args`). */
    private static void ride(BookEntry e, int ride, Object... args) {
        String k = "sc.manual.world.flights.ride." + EmbassyRulesSC.RIDE_KEYS[ride];
        e.add(BookEl.item(null, Lang.tr(EmbassyRulesSC.rideKey(ride)), args.length == 0 ? Lang.tr(k) : Lang.tr(k, args)));
    }

    // ------------------------------------------------------------------ «Рейсы домой» (ПЖ-11, ПР-8, ПЯ-8, ПЯ-9, ПЯ-12)

    private static BookEntry flightsBack(BookChapter c) {
        ItemStack beacon = BookWorldSC.dock(BlockDockSC.BEACON);
        BookEntry e = new BookEntry("world.flightsBack", c, beacon != null ? beacon : BookWorldSC.icon(), Lang.tr("sc.manual.world.flightsBack.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.flightsBack.title"))).addAll(BookContent.paras("sc.manual.world.flightsBack", null,
                new Object[]{EmbassyRulesSC.BOARD_DOCK, BookWorldSC.sec(EmbassyRulesSC.VORTEX_TICKS)}));
        if (!channelOpen()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.flights.off")));
        }
        e.add(BookEl.head(Lang.tr("sc.manual.world.flightsBack.rideshead"), showcase2(BlockShowcase2SC.DISPATCH_CONSOLE)));
        e.add(prices(new int[]{EmbassyRulesSC.RIDE_HOME, EmbassyRulesSC.RIDE_BACK, EmbassyRulesSC.RIDE_OTHER}));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.flights.prices.note", TradeRulesSC.SEED_SPREAD_PCT)));
        e.add(BookEl.item(null, Lang.tr(EmbassyRulesSC.rideKey(EmbassyRulesSC.RIDE_HOME)), Lang.tr("sc.manual.world.flightsBack.ride.home",
                EmbassyRulesSC.flightsPerDay())));
        e.add(BookEl.item(null, Lang.tr(EmbassyRulesSC.rideKey(EmbassyRulesSC.RIDE_BACK)), Lang.tr("sc.manual.world.flightsBack.ride.back",
                BookWorldSC.days(EmbassyRulesSC.BACK_TICKS), BookWorldSC.levelName(ReputationSC.VIOLATOR))));
        e.add(BookEl.item(null, Lang.tr(EmbassyRulesSC.rideKey(EmbassyRulesSC.RIDE_OTHER)), Lang.tr("sc.manual.world.flightsBack.ride.other",
                BookWorldSC.levelName(ReputationSC.OWN), EmbassyRulesSC.OTHER_PER_DAY)));    // «из Города»: the line inflects it itself
        ItemStack capsule = SettlementItemsSC.capsules(1);
        e.add(BookEl.item(capsule, capsule != null ? capsule.getDisplayName() : Lang.tr("item.siliconage.returnCapsule.name"),
                Lang.tr("sc.manual.world.flightsBack.capsule")));
        e.add(BookEl.link("world.flights", Lang.tr("sc.manual.world.flights.title")));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        e.add(BookEl.link("world.embassies", Lang.tr("sc.manual.world.embassies.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Лавка и Интендант» (ПО-23..ПО-27, ПД-7)

    private static BookEntry embassyTrade(BookChapter c) {
        ItemStack chip = SettlementItemsSC.chips(1);
        BookEntry e = new BookEntry("world.embassyTrade", c, chip != null ? chip : BookWorldSC.icon(), Lang.tr("sc.manual.world.embassyTrade.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.embassyTrade.title"))).addAll(BookContent.paras("sc.manual.world.embassyTrade"));

        // the shop (ПО-23, ПО-24): the Cartographer's two deals, the sellers' lines of TradeTableSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyTrade.shophead"), BookWorldSC.kindIcon(NpcKindSC.CARTOGRAPHER)));
        ItemStack capsule = SettlementItemsSC.capsules(1), map = SettlementItemsSC.settlementMap == null ? null : new ItemStack(SettlementItemsSC.settlementMap);
        String capsuleSeller = BookWorldSC.sellerLine(capsule), mapSeller = BookWorldSC.sellerLine(map);
        e.add(BookEl.item(capsule, capsule != null ? capsule.getDisplayName() : Lang.tr("item.siliconage.returnCapsule.name"),
                capsuleSeller != null ? capsuleSeller : Lang.tr("sc.manual.world.embassyTrade.capsule")));
        e.add(BookEl.item(map, map != null ? map.getDisplayName() : Lang.tr("item.siliconage.settlementMap.name"), Lang.tr("sc.manual.world.embassyTrade.map")
                + (mapSeller == null ? "" : " " + mapSeller)));

        // the Quartermaster (ПО-25): package T's deals as TradeTableSC has them
        List<TradeOfferSC> qm = TradeTableSC.offers(NpcKindSC.QUARTERMASTER);
        e.add(BookEl.head(Lang.tr(NpcKindSC.QUARTERMASTER.langKey()), BookWorldSC.kindIcon(NpcKindSC.QUARTERMASTER)));
        if (qm.isEmpty()) {
            e.add(BookEl.dim(Lang.tr("sc.manual.world.trade.none")));
        } else {
            BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.give"), Lang.tr("sc.manual.world.t.get"), Lang.tr("sc.manual.world.t.level"),
                    Lang.tr("sc.manual.world.t.limit"));
            int chips = 0;
            for (TradeOfferSC o : qm) {
                t.row(BookWorldSC.offerIcon(o), BookWorldSC.sides(o.in), BookWorldSC.outText(o), BookWorldSC.levelName(o.minLevel), BookWorldSC.limitText(o));
                chips += o.sell() && o.limit < TradeOfferSC.UNLIMITED ? o.out.count * o.limit : 0;
            }
            e.add(t);
            e.add(BookEl.dim(Lang.tr("sc.manual.world.embassyTrade.qm", chips, TradeRulesSC.CHIPS_PER_DAY, ReputationSC.TRADE)));
        }

        // the Ambassador's contracts (ПО-27): package T's of ContractTableSC (its «emb» keys and the tantalum capacitors)
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyTrade.contractshead"), BookWorldSC.kindIcon(NpcKindSC.ELDER)));
        BookEl ct = BookEl.table(Lang.tr("sc.manual.world.t.contract"), Lang.tr("sc.manual.world.t.goal"), Lang.tr("sc.manual.world.t.reward"));
        int rows = 0;
        for (ContractTableSC.Contract k : ContractTableSC.embassyPool()) {
            rows++;
            boolean dispatch = k.goal == ContractTableSC.DISPATCH;
            ct.row(null, Lang.tr("sc.contract." + k.key), dispatch ? "1" : String.valueOf(k.target), Lang.tr("sc.manual.world.cell.reward", k.chips, k.reputation)
                    + (dispatch ? " " + Lang.tr("sc.manual.world.cell.overceiling") : ""));
        }
        if (rows > 0) {
            e.add(ct);
        }
        e.addAll(BookContent.paras("sc.manual.world.embassyTrade.contracts", new Object[]{BookWorldSC.levelName(ContractTableSC.takeLevel(true)),
                TradeRulesSC.CONTRACTS_PER_DAY}, new Object[]{ContractTableSC.DISPATCH_DAYS}));    // «Старейшине»: the line inflects him itself

        // the ceiling of «Доверие» (ПД-7)
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyTrade.ceilinghead")));
        e.addAll(BookContent.paras("sc.manual.world.embassyTrade.ceiling", new Object[]{ReputationSC.EMBASSY_CEILING, BookWorldSC.levelName(ReputationSC.PARTNER),
                BookWorldSC.levelName(ReputationSC.OWN), BookWorldSC.levelName(ReputationSC.HONOURED)},
                new Object[]{ReputationSC.PASS, ReputationSC.PASS_CHIPS}));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.flights", Lang.tr("sc.manual.world.flights.title")));
        e.add(BookEl.link("world.embassies", Lang.tr("sc.manual.world.embassies.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Правила посольств» (ПК-26, ПК-28, ПЕ-9, ПЕ-10)

    private static BookEntry embassyRules(BookChapter c) {
        BookEntry e = new BookEntry("world.embassyRules", c, new ItemStack(net.minecraft.init.Items.compass), Lang.tr("sc.manual.world.embassyRules.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.embassyRules.title"))).addAll(BookContent.paras("sc.manual.world.embassyRules"));

        // the server's six rules (the section embassy - its client got them with the others)
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyRules.rulehead")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.embassyRules.embassies"),
                Lang.tr(SubstrateConfigSC.embassies ? "sc.manual.world.embassyRules.embassies.on" : "sc.manual.world.embassyRules.embassies.off")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.embassyRules.grid", SubstrateConfigSC.embassyGrid),
                Lang.tr("sc.manual.world.embassyRules.grid.desc", EmbassySiteSC.grid() / 2)));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.embassyRules.chance", SubstrateConfigSC.embassyChance),
                Lang.tr("sc.manual.world.embassyRules.chance.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.embassyRules.spawnMin", SubstrateConfigSC.embassySpawnMin),
                Lang.tr("sc.manual.world.embassyRules.spawnMin.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.embassyRules.ring", SubstrateConfigSC.embassyRingMin, SubstrateConfigSC.embassyRingMax),
                Lang.tr("sc.manual.world.embassyRules.ring.desc", EmbassySiteSC.FALLBACK_MIN, EmbassySiteSC.FALLBACK_MAX)));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.embassyRules.note")));

        // the command (ПЕ-10) and the «Достройка» (ПЕ-9)
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyRules.cmdhead")));
        e.addAll(BookContent.paras("sc.manual.world.embassyRules.cmd", new Object[]{CommandEmbassySC.LIST_DEFAULT, CommandEmbassySC.LIST_MAX}));
        e.add(BookEl.head(Lang.tr("sc.manual.world.embassyRules.modshead")));
        e.addAll(BookContent.paras("sc.manual.world.embassyRules.mods", new Object[]{BookWorldSC.sec(EmbassyRepairSC.WINDOW_TICKS),
                EmbassyRepairSC.PLAYER_CLEAR, BookWorldSC.minutes(EmbassyRepairSC.PASS_PAUSE_TICKS)}));
        e.add(BookEl.link("world.embassies", Lang.tr("sc.manual.world.embassies.title")));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        return e;
    }

    // ------------------------------------------------------------------ stacks

    /** The Great Ring's core (null before it is registered). */
    static ItemStack ring() {
        return EmbassyBlocksSC.ring == null ? null : new ItemStack(EmbassyBlocksSC.ring);
    }

    /** A showcase machine of the second block (BlockShowcase2SC; Ф6's metas 7..9), null before it is registered. */
    static ItemStack showcase2(int meta) {
        return SettlementBlocksSC.showcase2 == null ? null : new ItemStack(SettlementBlocksSC.showcase2, 1, meta);
    }

    private static ItemStack showcase(int meta) {
        return SettlementBlocksSC.showcase == null ? null : new ItemStack(SettlementBlocksSC.showcase, 1, meta);
    }
}
