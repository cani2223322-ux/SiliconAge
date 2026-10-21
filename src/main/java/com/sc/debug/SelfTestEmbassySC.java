package com.sc.debug;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.sc.block.BlockShowcase2SC;
import com.sc.bridge.BridgeMathSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateNetSC;
import com.sc.dim.SubstrateSC;
import com.sc.embassy.EmbassyBlocksSC;
import com.sc.embassy.EmbassyItemsSC;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.embassy.EmbassySiteSC;
import com.sc.embassy.EmbassyTicketSC;
import com.sc.embassy.EmbassyTradeSC;
import com.sc.manual.BookChapter;
import com.sc.manual.BookContent;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.BookWorldSC;
import com.sc.manual.Lang;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.RosterSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeTableSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.config.Configuration;

/**
 * PACKAGE D (docs/plan-substrate-tech-f6.md §10.1): Ф6's self-test (-Dsc.selftest, from SelfTestSC.run after SelfTestFinaleSC) - the
 * pure decisions of the Tuners' Embassies without a world, each section by itself (an unfinished package body fails only its own
 * section): (1) the config (20 rules: the six of the embassy section, their defaults read from an empty Configuration, the guaranteed
 * ring widened), (2) SettlementSizeSC.EMBASSY and NpcKindSC's two new kinds, (3) the dock zone's NBT, inDockZone, reach, embassies(),
 * isEmbassy / inSubstrate, (4) ReputationSC.give's ceiling, (5) RosterSC's Dispatcher at the Substrate's docks, (6) EmbassySiteSC's
 * pure maths (the regions, the share of rolls, the candidates, vanilla's grids, the guaranteed ring's candidates), (7) EmbassyLayoutSC
 * (the floor, the frame, the roster on 200 sites, the Vokzal and the ring on the level ground, the vortex, the blend, the footprint,
 * the record), (8) EmbassyRulesSC (the window, the prices of the proposal's table, the spread, the round trip, the guarantee, the
 * refusals' matrix and order, the rescue, the return half, the soft admission), (9) EmbassyTicketSC's NBT round trip, (10) the
 * packages' pure bodies (G: the column's levelling; S: the quarters on 60 sites; T: EmbassyTradeSC.allowed, the Quartermaster's deals,
 * the Ambassador's contracts, ПО-7), (11) the texts and (12) the handbook with the cross-check of Ф1..Ф6. Output "[SC-TEST] PASS / FAIL
 * / INFO embassy: ...".
 * <p>
 * Built on the core alone: the packages' bodies (G's EmbassyTerrainSC, S's EmbassyQuartersSC, T's tables) are asked for by their fixed
 * names; a body not written yet fails its own section with what is missing, never another's.
 */
final class SelfTestEmbassySC {

    /** The six Ф6 WORLD articles (SelfTestSubstrateSC counts the chapter with them). */
    static final String[] ARTICLES = BookWorldSC.F6_ARTICLES;
    private static final String[] SECTIONS = {"config", "sizes and kinds", "registry", "ceiling", "dispatcher", "site maths", "layout", "flight rules",
            "tickets", "package bodies", "texts", "handbook"};
    /** The seeds the layouts are sampled on. */
    private static final long[] SEEDS = {1234567L, -77L, 0x5C14L};
    /** The six rules of the section embassy and their defaults (docs/plan-substrate-tech-f6.md §2.1). */
    private static final String[] RULES = {"embassies", "embassyGrid", "embassyChance", "embassySpawnMin", "embassyRingMin", "embassyRingMax",
            "flightPriceMultiplier", "flightsPerDay"};   // the last two: ПС-8 of the audit 2026-10-20
    private static final Object[] DEFAULTS = {Boolean.TRUE, 2048, 100, 1500, 1300, 2500, 1F, 2};
    /** A test dimension id for the Substrate's records (the pure questions take it as an argument). */
    private static final int SUB_DIM = -1414;
    private static final UUID P1 = UUID.fromString("5c0e7a1e-0000-4000-8000-0000000000f6"), P2 = UUID.fromString("5c0e7a1e-0000-4000-8000-0000000000f7");
    /**
     * Key prefixes of Ф6's texts: each in both languages with as many arguments, no Cyrillic and no «» / „ in English (the packages'
     * own keys among them: G's sc.cmd.embassy.*, R's sc.flight.*, T's sc.finder.embassy*, sc.embassy.hint*, the Quartermaster's deals
     * and the Ambassador's contracts).
     */
    private static final String[] PREFIXES = {"sc.embassy.", "sc.flight.", "sc.cmd.embassy.", "sc.settle.kind.dispatcher", "sc.settle.kind.quartermaster",
            "sc.settle.kind.ambassador", "sc.settle.size.embassy", "tile.siliconage.showcase2.gravityCoil", "tile.siliconage.showcase2.cryoTank",
            "tile.siliconage.showcase2.dispatchConsole", "tile.siliconage.embassyRing", "item.siliconage.embassyFinder", "sc.tooltip.embassyRing",
            "sc.tooltip.embassyFinder", "sc.manual.world.embass", "sc.manual.world.flights", "sc.manual.world.rule.embassies", "sc.manual.world.settle.part.dispatcher",
            "sc.manual.world.settle.embassies", "sc.manual.world.tuners.kind.dispatcher", "sc.manual.world.tuners.kind.quartermaster",
            "sc.manual.world.trade.embassyOnly", "sc.manual.world.trust.up.embassy", "sc.manual.world.docks.dispatcher", "sc.manual.world.docks.vokzal",
            "sc.nei.obtain.embassy", "sc.finder.embassy", "sc.trade.offer.qm", "sc.contract.emb", "sc.contract.dispatch", "sc.waila.embassy",
            "sc.manual.world.t.who", "sc.manual.world.t.count", "sc.manual.world.t.ride", "sc.manual.world.cell.paid", "sc.manual.world.cell.onceplayer",
            "sc.manual.world.cell.perdeath", "sc.manual.world.cell.days", "sc.manual.world.cell.shared", "sc.manual.world.cell.overceiling",
            "sc.settle.tuner.hint.dispatcher", "sc.settle.tuner.hint.quartermaster", "sc.settle.tuner.hint.ambassador", "sc.trade.result.dispatchLate",
            "sc.trade.result.elsewhere"};
    /** The core's messages and the arguments their callers pass (§9.1; the tooltips' numbers). */
    private static final String[][] MESSAGE_ARGS = {{"sc.embassy.name", "1"}, {"sc.embassy.seen", "1"}, {"sc.tooltip.embassyFinder.details", "1"},
            {"sc.tooltip.embassyFinder", "0"}, {"sc.tooltip.embassyFinder.howto", "0"}, {"sc.tooltip.embassyRing", "0"}, {"sc.tooltip.embassyRing.details", "0"},
            {"sc.tooltip.embassyRing.howto", "0"}, {"sc.cmd.embassy.usage", "0"}, {"sc.nei.obtain.embassyRing", "0"}, {"sc.nei.obtain.embassyFinder", "0"},
            {"sc.nei.obtain.embassy", "0"}, {"sc.tooltip.showcase.embassy.howto", "0"}};
    /**
     * The packages' messages and the arguments their code passes (G's /scembassy, R's flights - the chat and the «Рейсы» tab, T's
     * finder, first-login line, hints, the Quartermaster's deals and the Ambassador's contracts; a contract's .desc gets its target).
     */
    private static final String[][] PACKAGE_ARGS = {
            {"sc.cmd.embassy.none", "0"}, {"sc.cmd.embassy.off", "0"}, {"sc.cmd.embassy.list.head", "5"}, {"sc.cmd.embassy.list.line", "6"},
            {"sc.cmd.embassy.list.lineGuar", "6"}, {"sc.cmd.embassy.list.more", "1"}, {"sc.cmd.embassy.list.empty", "0"},
            {"sc.cmd.embassy.list.share", "5"}, {"sc.cmd.embassy.where.guaranteed", "5"}, {"sc.cmd.embassy.where.noGuaranteed", "0"},
            {"sc.cmd.embassy.where.nearest", "5"}, {"sc.cmd.embassy.where.noNearest", "1"}, {"sc.cmd.embassy.repair.none", "0"},
            {"sc.cmd.embassy.repair.noId", "1"}, {"sc.cmd.embassy.repair.noStart", "1"}, {"sc.cmd.embassy.repair.done", "4"},
            {"sc.cmd.embassy.state.made", "1"}, {"sc.cmd.embassy.state.empty", "0"}, {"sc.cmd.embassy.state.unexplored", "0"},
            {"sc.flight.countdown.ring", "1"}, {"sc.flight.countdown.dock", "1"}, {"sc.flight.open.ring", "1"}, {"sc.flight.open.dock", "1"},
            {"sc.flight.missed", "0"}, {"sc.flight.noTalon", "0"}, {"sc.flight.full", "0"}, {"sc.flight.mounted", "0"},
            {"sc.flight.channelLost", "0"}, {"sc.flight.carried", "1"}, {"sc.flight.refund", "1"}, {"sc.flight.guarantee.given", "1"},
            {"sc.flight.guarantee.penalty", "2"}, {"sc.flight.target.mark", "2"}, {"sc.flight.tab", "0"}, {"sc.flight.buy", "0"},
            {"sc.flight.round", "0"}, {"sc.flight.friend", "0"}, {"sc.flight.go", "0"}, {"sc.flight.useBack", "0"}, {"sc.flight.confirm", "0"},
            {"sc.flight.none", "0"}, {"sc.flight.price", "1"}, {"sc.flight.price.paid", "0"}, {"sc.flight.price.one", "1"},
            {"sc.flight.price.round", "1"}, {"sc.flight.round.tip", "2"}, {"sc.flight.round.none", "0"}, {"sc.flight.buy.tip", "1"},
            {"sc.flight.useBack.tip", "0"}, {"sc.flight.target.at", "2"}, {"sc.flight.target.none", "0"}, {"sc.flight.back", "2"},
            {"sc.flight.left", "2"}, {"sc.flight.talon", "1"}, {"sc.flight.talon.other", "2"}, {"sc.flight.phase.idle", "0"},
            {"sc.flight.phase.countdown", "1"}, {"sc.flight.phase.vortex", "1"}, {"sc.flight.phase.cool", "1"}, {"sc.flight.phase.tip", "2"},
            {"sc.flight.board.ring", "0"}, {"sc.flight.board.dock", "0"}, {"sc.flight.bought.ring", "2"}, {"sc.flight.bought.dock", "2"},
            {"sc.flight.bought.friend", "2"}, {"sc.flight.boarding.ring", "0"}, {"sc.flight.boarding.dock", "0"}, {"sc.flight.warn", "1"},
            {"sc.flight.warn.friend", "2"}, {"sc.flight.warn.both", "0"}, {"sc.flight.warn.armour", "0"}, {"sc.flight.warn.capsule", "0"},
            {"sc.flight.confirm.tip", "0"}, {"sc.flight.friend.none", "0"}, {"sc.flight.friend.pick", "3"}, {"sc.flight.friend.tip", "2"},
            {"sc.flight.friend.next", "0"}, {"sc.flight.friend.used", "0"}, {"sc.flight.friend.embassy", "0"}, {"sc.flight.friend.level", "1"},
            {"sc.flight.go.none", "0"}, {"sc.flight.go.elsewhere", "0"}, {"sc.flight.go.tip", "0"}, {"sc.flight.decline", "0"},
            {"sc.flight.decline.tip", "0"}, {"sc.flight.declined", "0"}, {"sc.flight.guarantee.declined", "1"}, {"sc.finder.embassy", "3"},
            {"sc.finder.embassy.nameless", "2"}, {"sc.finder.embassy.none", "1"}, {"sc.finder.embassy.offworld", "0"}, {"sc.embassy.hint", "3"},
            {"sc.embassy.hint.nameless", "2"}, {"sc.settle.tuner.hint.dispatcher", "0"}, {"sc.settle.tuner.hint.quartermaster", "0"},
            {"sc.settle.tuner.hint.ambassador", "1"}, {"sc.trade.offer.qmBread", "0"}, {"sc.trade.offer.qmLogs", "0"},
            {"sc.trade.offer.qmCoal", "0"}, {"sc.trade.offer.qmIron", "0"}, {"sc.contract.embSupplyBread", "0"},
            {"sc.contract.embSupplyBread.desc", "1"}, {"sc.contract.embSupplyLogs", "0"}, {"sc.contract.embSupplyLogs.desc", "1"},
            {"sc.contract.embSupplyIron", "0"}, {"sc.contract.embSupplyIron.desc", "1"}, {"sc.contract.embDispatch", "0"},
            {"sc.contract.embDispatch.desc", "0"}, {"sc.contract.dispatch.turnIn", "0"}, {"sc.trade.result.dispatchLate", "0"},
            {"sc.trade.result.elsewhere", "0"}};

    private SelfTestEmbassySC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "embassy: " + what);
    }

    private static void info(String what) {
        System.out.println("[SC-TEST] INFO embassy: " + what);
    }

    static void run() {
        for (int i = 0; i < SECTIONS.length; i++) {
            try {
                section(i);
            } catch (Throwable t) {
                check(false, SECTIONS[i] + " - exception " + t);
                t.printStackTrace();
            }
        }
        try {
            auditStar();
        } catch (Throwable t) {
            check(false, "the audit 2026-10-20 (★) - exception " + t);
            t.printStackTrace();
        }
    }

    /** The shortcomings of 2026-10-20, ★ (docs/plan-audit-2026-10-20-star.md): ПС-1, ПС-5, ПС-7, ПС-8 - the pure parts. */
    private static void auditStar() {
        // ПС-1: no hostile spawns on an embassy's whole level ground (radius + LEVEL_OUT, the Vokzal in it) - found by the index
        SettlementRegistrySC r = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        SettlementRegistrySC.Settlement emb = r.add(record(SettlementSizeSC.EMBASSY, 0, 5000, -3000));
        int edge = emb.radius + EmbassyLayoutSC.LEVEL_OUT;
        check(onGround(r, 5000 + edge - 1, -3000) && !onGround(r, 5000 + edge + 1, -3000) && onGround(r, 5000, -3000 + emb.radius + 30)
                        && !onGround(r, 5000, -3000 + emb.radius + 32),
                "embassy ground (ПС-1): radius + 30 on it, radius + 32 off it (the disc radius + " + EmbassyLayoutSC.LEVEL_OUT + ")");
        // ПС-5: the gain under the embassy's ceiling, as give(..., ceiling) applies it
        check(ReputationSC.gainUnder(560, 50, 599) == 39 && ReputationSC.gainUnder(599, 50, 599) == 0 && ReputationSC.gainUnder(700, 50, 599) == 0
                        && ReputationSC.gainUnder(100, -20, 599) == -20 && ReputationSC.gainUnder(560, 50, ReputationSC.MAX) == 50
                        && "ceiling".equals(com.sc.settle.trade.TradeServerSC.RESULT_KEYS[com.sc.settle.trade.TradeServerSC.R_CEILING]),
                "Trust ceiling (ПС-5): 560 + 50 under 599 gives 39, at / over it 0, a loss as it is, no ceiling - all; the penance's refusal key");
        // ПС-7: a dweller taken away by the rule leaves its slot as never filled
        SettlementRegistrySC.Settlement sub = r.add(substrateRecord(SettlementSizeSC.OUTPOST, 3));
        java.util.UUID who = java.util.UUID.randomUUID();
        r.slotAlive(sub.id, 0, who, 100L);
        r.slotNever(sub.id, 0);
        SettlementRegistrySC.Slot sl = r.slot(sub.id, 0);
        check(sl != null && sl.state == SettlementRegistrySC.NEVER && sl.uuidMost == 0L && sl.uuidLeast == 0L && sl.diedAt == 0L,
                "embassies rule (ПС-7): slotNever - NEVER, no UUID, no death time (the roster makes it at once, no Clone Capsule)");
        // ПС-8: the flights' price multiplier and the day's flights
        float mWas = SubstrateConfigSC.flightPriceMultiplier;
        int fWas = SubstrateConfigSC.flightsPerDay;
        boolean same = true, raised;
        int perDay;
        try {
            SubstrateConfigSC.flightPriceMultiplier = 1F;
            SubstrateConfigSC.flightsPerDay = EmbassyRulesSC.FLIGHTS_PER_DAY;
            for (int ride = 0; ride < EmbassyRulesSC.RIDE_BASE.length; ride++) {
                same &= EmbassyRulesSC.base(ride) == EmbassyRulesSC.RIDE_BASE[ride];
            }
            perDay = EmbassyRulesSC.flightsPerDay();
            SubstrateConfigSC.flightPriceMultiplier = 1.5F;
            raised = EmbassyRulesSC.base(EmbassyRulesSC.RIDE_FIRST) == 36 && EmbassyRulesSC.base(EmbassyRulesSC.RIDE_BACK) == 0;
        } finally {
            SubstrateConfigSC.flightPriceMultiplier = mWas;
            SubstrateConfigSC.flightsPerDay = fWas;
        }
        check(same && raised && perDay == 2, "flight config (ПС-8): at x1 every ride's base as RIDE_BASE, at x1.5 the first flight 36 (BACK still 0), "
                + perDay + " flights a day by default");
    }

    /** ПС-1: SettlementSC.embassyGroundAt's look-up on a test registry (the world's own needs a server). */
    private static boolean onGround(SettlementRegistrySC r, int x, int z) {
        for (SettlementRegistrySC.Settlement s : r.near(0, x, z, EmbassyLayoutSC.LEVEL_OUT)) {
            if (com.sc.settle.SettlementSC.isEmbassy(s) && com.sc.settle.SettlementSC.onEmbassyGround(s.distSq(x, z), s.radius)) {
                return true;
            }
        }
        return false;
    }

    private static void section(int i) throws Exception {
        switch (i) {
            case 0: config(); break;
            case 1: sizesAndKinds(); break;
            case 2: registry(); break;
            case 3: ceiling(); break;
            case 4: dispatcher(); break;
            case 5: siteMaths(); break;
            case 6: layout(); break;
            case 7: flightRules(); break;
            case 8: tickets(); break;
            case 9: packageBodies(); break;
            case 10: texts(); break;
            default: handbook(); break;
        }
    }

    // ------------------------------------------------------------------ 1. the config (§2.1, ПК-26)

    private static void config() throws Exception {
        Set<String> sent = new TreeSet<String>();
        for (Field f : SubstrateNetSC.ruleFields()) {
            sent.add(f.getName());
        }
        boolean rules = sent.size() == 28 && sent.containsAll(Arrays.asList(RULES));
        // the defaults as an empty file reads them, the ring widened when it is too thin - loadEmbassies on Configurations in memory
        Object[] was = new Object[RULES.length];
        for (int k = 0; k < RULES.length; k++) {
            was[k] = SubstrateConfigSC.class.getField(RULES[k]).get(null);
        }
        String defaults, widened;
        boolean restart;
        try {
            Method load = SubstrateConfigSC.class.getDeclaredMethod("loadEmbassies", Configuration.class);
            load.setAccessible(true);
            Configuration empty = new Configuration();
            load.invoke(null, empty);
            StringBuilder d = new StringBuilder();
            for (int k = 0; k < RULES.length; k++) {
                Object v = SubstrateConfigSC.class.getField(RULES[k]).get(null);
                if (!DEFAULTS[k].equals(v)) {
                    d.append(' ').append(RULES[k]).append('=').append(v).append(" (").append(DEFAULTS[k]).append(')');
                }
            }
            defaults = d.length() == 0 ? "ok" : "BAD:" + d;
            restart = empty.hasCategory(SubstrateConfigSC.CATEGORY_EMBASSY) && empty.getCategory(SubstrateConfigSC.CATEGORY_EMBASSY).requiresWorldRestart();
            Configuration thin = new Configuration();
            thin.get(SubstrateConfigSC.CATEGORY_EMBASSY, "embassyRingMin", 1300).set(2000);
            thin.get(SubstrateConfigSC.CATEGORY_EMBASSY, "embassyRingMax", 2500).set(2100);
            load.invoke(null, thin);
            widened = SubstrateConfigSC.embassyRingMin == 2000 && SubstrateConfigSC.embassyRingMax == 2256 ? "ok"
                    : "BAD " + SubstrateConfigSC.embassyRingMin + "/" + SubstrateConfigSC.embassyRingMax;
        } finally {
            for (int k = 0; k < RULES.length; k++) {
                SubstrateConfigSC.class.getField(RULES[k]).set(null, was[k]);
            }
        }
        boolean live = SubstrateConfigSC.embassies && SubstrateConfigSC.embassyGrid == 2048 && SubstrateConfigSC.embassyChance == 100
                && SubstrateConfigSC.embassySpawnMin == 1500 && SubstrateConfigSC.embassyRingMin == 1300 && SubstrateConfigSC.embassyRingMax == 2500;
        if (!live) {
            info("the test config's section embassy isn't the defaults (" + SubstrateConfigSC.embassies + ", " + SubstrateConfigSC.embassyGrid + ", "
                    + SubstrateConfigSC.embassyChance + ", " + SubstrateConfigSC.embassySpawnMin + ", " + SubstrateConfigSC.embassyRingMin + ", "
                    + SubstrateConfigSC.embassyRingMax + ") - the other sections read what it says");
        }
        boolean grid = EmbassySiteSC.grid() == Math.max(1024, SubstrateConfigSC.embassyGrid) && "embassy".equals(SubstrateConfigSC.CATEGORY_EMBASSY);
        check(rules && "ok".equals(defaults) && "ok".equals(widened) && restart && grid, "config (§2.1, ПК-26): " + sent.size() + " rules the server sends"
                + " (28), the eight of the section embassy among them " + rules + "; an empty file reads true / 2048 / 100 / 1500 / 1300 / 2500 / 1.0 / 2 " + defaults
                + "; a ring 2000..2100 is widened to 2000..2256 " + widened + "; the section asks a world restart " + restart + "; grid() "
                + EmbassySiteSC.grid() + " (at least 1024) " + grid);
    }

    // ------------------------------------------------------------------ 2. SettlementSizeSC.EMBASSY, NpcKindSC (§2.4, ПЕ-5, ПЖ-11, ПЖ-12, ПЖ-14)

    private static void sizesAndKinds() {
        SettlementSizeSC e = SettlementSizeSC.EMBASSY;
        boolean size = e.ordinal() == 3 && "embassy".equals(e.key) && e.radiusMin == 88 && e.radiusMax == 96 && e.tunersMin == 18 && e.tunersMax == 22
                && e.sentries == 6 && e.seniors == 4 && e.captainPct == 100 && e.commandant && e.dock && !e.substrate()
                && SettlementSizeSC.SUBSTRATE_SIZES == 3 && SettlementSizeSC.OUTPOST.substrate() && SettlementSizeSC.SETTLEMENT.substrate()
                && SettlementSizeSC.CITY.substrate() && SettlementSizeSC.values().length == 4 && "sc.settle.size.embassy".equals(e.langKey())
                && SettlementSizeSC.of(3) == e && e.captain(0L) && e.guards() == 10;
        Random r = new Random(14L);
        boolean bounds = true, lo = false, hi = false;
        for (int i = 0; i < 4000; i++) {
            int rad = EmbassySiteSC.radius(r.nextLong());
            bounds &= rad >= 88 && rad <= 96;
            lo |= rad == 88;
            hi |= rad == 96;
        }
        NpcKindSC d = NpcKindSC.DISPATCHER, q = NpcKindSC.QUARTERMASTER;
        boolean kinds = NpcKindSC.values().length == 14 && d.ordinal() == 12 && "dispatcher".equals(d.key) && !d.guard && q.ordinal() == 13
                && "quartermaster".equals(q.key) && !q.guard && d.respawnTicks() == NpcKindSC.TUNER_RESPAWN_TICKS && q.respawnTicks() == 24000
                && !d.chief() && !q.chief() && NpcKindSC.of(13) == q && NpcKindSC.of(14) == NpcKindSC.ELDER && "sc.settle.kind.dispatcher".equals(d.langKey());
        StringBuilder names = new StringBuilder();
        for (NpcKindSC k : NpcKindSC.values()) {
            String want = k == NpcKindSC.ELDER ? "sc.settle.kind.ambassador" : k.langKey();
            if (!want.equals(k.langKey(true)) || !k.langKey().equals(k.langKey(false))) {
                names.append(' ').append(k.key);
            }
        }
        SettlementRegistrySC.Settlement emb = record(SettlementSizeSC.EMBASSY, 0, 0, 0), city = record(SettlementSizeSC.CITY, SUB_DIM, 0, 0);
        boolean is = SettlementSC.isEmbassy(emb) && !SettlementSC.isEmbassy(city) && !SettlementSC.isEmbassy(null) && SettlementSC.EMBASSY_DIM == 0
                && EmbassySiteSC.DIM == 0 && !SettlementSC.governs(null)
                && SettlementSC.embassiesGenerate() == (SubstrateSC.active() && SubstrateConfigSC.embassies);
        check(size && bounds && lo && hi && kinds && names.length() == 0 && is, "sizes and kinds (ПЕ-5, ПЖ-11..ПЖ-14): EMBASSY ordinal 3 - dome 88-96,"
                + " 18-22 Tuners, 6 Sentries + 4 Senior Wardens, the Captain 100%, the Commandant, the Vokzal's dock, not the Substrate's (SUBSTRATE_SIZES 3) "
                + size + "; radius(seed) within 88..96 over 4000 seeds " + bounds + " (both ends " + (lo && hi) + "); 14 kinds - DISPATCHER 12, QUARTERMASTER 13"
                + " settlers back after a day, of() never throws " + kinds + "; langKey(true): the Elder «Посол», the rest their own"
                + (names.length() == 0 ? "" : " - NOT:" + names) + "; isEmbassy, EMBASSY_DIM 0, governs(null) false, embassiesGenerate " + is);
    }

    // ------------------------------------------------------------------ 3. the registry (§2.5, ПР-7)

    private static void registry() {
        SettlementRegistrySC r = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        SettlementRegistrySC.Settlement emb = record(SettlementSizeSC.EMBASSY, 0, 5000, -3000), city = record(SettlementSizeSC.CITY, SUB_DIM, 100, 200);
        emb.hasDock = true;
        emb.dockX = 5100;
        emb.dockY = 67;
        emb.dockZ = -3000;
        emb.dockZone = EmbassyLayoutSC.DOCK_ZONE;
        city.hasDock = true;
        city.dockX = 170;
        city.dockY = 70;
        city.dockZ = 200;
        r.add(emb);
        r.add(city);
        NBTTagCompound t = new NBTTagCompound();
        r.writeToNBT(t);
        NBTTagList ss = t.getTagList("Settlements", 10);
        boolean keyEmb = false, keyCity = true;
        for (int i = 0; i < ss.tagCount(); i++) {
            NBTTagCompound c = ss.getCompoundTagAt(i);
            if (c.getInteger("Id") == emb.id) {
                keyEmb = c.hasKey("DockZone") && c.getInteger("DockZone") == 13;
            } else {
                keyCity &= !c.hasKey("DockZone");
            }
        }
        SettlementRegistrySC back = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        back.readFromNBT(t);
        SettlementRegistrySC.Settlement e2 = back.byId(emb.id), c2 = back.byId(city.id);
        boolean round = e2 != null && c2 != null && e2.dockZone == 13 && c2.dockZone == SettlementSC.DOCK_ZONE && SettlementSC.isEmbassy(e2) && e2.dim == 0;
        double bx = emb.dockX + 0.5D, bz = emb.dockZ + 0.5D, y = emb.dockY + 1;
        boolean zone = emb.inDockZone(bx + 12.9D, y, bz) && !emb.inDockZone(bx + 13.1D, y, bz) && emb.inDockZone(bx, y, bz - 12.9D)
                && !emb.inDockZone(bx, emb.dockY - SettlementSC.DOCK_BELOW - 1, bz) && city.inDockZone(city.dockX + 0.5D + 4.9D, 71, city.dockZ + 0.5D)
                && !city.inDockZone(city.dockX + 0.5D + 5.1D, 71, city.dockZ + 0.5D);
        int[] reach = emb.reach();
        boolean reaches = reach[2] >= emb.dockX + 14 && reach[0] <= emb.x - emb.radius - SettlementSC.BELT;
        List<SettlementRegistrySC.Settlement> embassies = back.embassies();
        boolean list = embassies.size() == 1 && embassies.get(0).id == emb.id;
        // found where it stands (the index's regions cover the Vokzal's zone 13 too), by its own dimension only
        boolean found = back.dockZoneAt(0, bx + 12D, y, bz) == e2 && back.dockZoneAt(SUB_DIM, bx + 12D, y, bz) == null
                && back.atCentre(0, emb.x, emb.z) == e2 && back.atCentre(SUB_DIM, emb.x, emb.z) == null;
        SettlementRegistrySC.Settlement sub = record(SettlementSizeSC.SETTLEMENT, SubstrateSC.dimId(), 0, 0);
        boolean inSub = SettlementSC.inSubstrate(sub) == SubstrateSC.active() && !SettlementSC.inSubstrate(emb) && !SettlementSC.inSubstrate(null);
        check(keyEmb && keyCity && round && zone && reaches && list && found && inSub, "registry (§2.5, ПР-7): an embassy's DockZone 13 written "
                + keyEmb + ", a dock of 5 writes no key " + keyCity + ", both back from NBT " + round + "; inDockZone at 12.9 / 13.1 of the Vokzal's beacon,"
                + " 4.9 / 5.1 of a Substrate dock " + zone + "; reach() takes the zone + 1 " + reaches + "; embassies() lists the embassy alone " + list
                + "; dockZoneAt / atCentre by the dimension " + found + "; inSubstrate (the dimension " + (SubstrateSC.active() ? "on" : "off") + ") " + inSub);
    }

    // ------------------------------------------------------------------ 4. the ceiling of «Доверие» (ПД-7)

    private static void ceiling() {
        int c = ReputationSC.EMBASSY_CEILING;
        SettlementRegistrySC.PlayerRec p = new SettlementRegistrySC.PlayerRec();
        p.reputation = 590;
        int a = ReputationSC.give(p, 20, ReputationSC.SRC_TRADE, 5L, c), at = p.reputation;
        int b = ReputationSC.give(p, 1, ReputationSC.SRC_CONTRACT, 5L, c), bt = p.reputation;
        p.reputation = 800;
        int over = ReputationSC.give(p, 10, ReputationSC.SRC_CONTRACT, 5L, c), ot = p.reputation;
        p.reputation = 599;
        int fine = ReputationSC.give(p, -5, ReputationSC.SRC_PENALTY, 5L, c), ft = p.reputation;
        p.reputation = 590;
        int free = ReputationSC.give(p, 20, ReputationSC.SRC_CONTRACT, 5L, ReputationSC.NO_CEILING), freeT = p.reputation;
        // the day's cap of deals still holds under the ceiling (15 of 20 already today: +5, not +10)
        SettlementRegistrySC.PlayerRec d = new SettlementRegistrySC.PlayerRec();
        d.reputation = 100;
        d.rollDay(9L);
        d.repTrades = 15;
        int capped = ReputationSC.give(d, 10, ReputationSC.SRC_TRADE, 9L, c);
        boolean values = c == 599 && c == ReputationSC.OWN_FROM - 1 && a == 9 && at == 599 && b == 0 && bt == 599 && over == 0 && ot == 800 && fine == -5
                && ft == 594 && free == 20 && freeT == 610 && capped == 5 && d.repTrades == 20 && d.reputation == 105;
        SettlementRegistrySC.Settlement emb = record(SettlementSizeSC.EMBASSY, 0, 0, 0), city = record(SettlementSizeSC.CITY, SUB_DIM, 0, 0);
        boolean of = ReputationSC.ceiling(emb) == 599 && ReputationSC.ceiling(city) == ReputationSC.NO_CEILING && ReputationSC.ceiling(null) == ReputationSC.NO_CEILING;
        check(values && of, "ceiling (ПД-7): 590 + 20 by a deal -> +" + a + " = " + at + " (599), 599 + 1 -> +" + b + ", 800 + 10 -> +" + over + " (stays "
                + ot + "), a penalty -5 at 599 -> " + fine + " = " + ft + ", no ceiling 590 + 20 -> " + freeT + "; the day's deal cap under it: 15 of 20 + 10 -> +"
                + capped + " (5) " + values + "; ceiling(embassy) 599, ceiling(a Substrate city / null) NO_CEILING " + of);
    }

    // ------------------------------------------------------------------ 5. the Dispatcher at the Substrate's docks (ПЖ-11, ПР-8)

    private static void dispatcher() {
        StringBuilder bad = new StringBuilder();
        SettlementRegistrySC.Settlement settlement = substrateRecord(SettlementSizeSC.SETTLEMENT, 1L), city = substrateRecord(SettlementSizeSC.CITY, 2L),
                first = substrateRecord(SettlementSizeSC.SETTLEMENT, 3L), outpost = substrateRecord(SettlementSizeSC.OUTPOST, 4L),
                embassy = substrateRecord(SettlementSizeSC.EMBASSY, 5L), elsewhere = substrateRecord(SettlementSizeSC.CITY, 6L);
        first.first = true;
        embassy.dim = 0;
        elsewhere.dim = 7;
        boolean due = RosterSC.dispatcherDue(settlement, SUB_DIM) && RosterSC.dispatcherDue(city, SUB_DIM) && RosterSC.dispatcherDue(first, SUB_DIM)
                && !RosterSC.dispatcherDue(outpost, SUB_DIM) && !RosterSC.dispatcherDue(embassy, SUB_DIM) && !RosterSC.dispatcherDue(embassy, 0)
                && !RosterSC.dispatcherDue(elsewhere, SUB_DIM) && !RosterSC.dispatcherDue(null, SUB_DIM);
        for (SettlementRegistrySC.Settlement s : new SettlementRegistrySC.Settlement[]{settlement, city, first}) {
            List<String> before = slotsText(s);
            int at = RosterSC.addDispatcher(s);
            int again = RosterSC.addDispatcher(s);
            boolean kept = s.slots.size() == before.size() + 1 && slotsText(s).subList(0, before.size()).equals(before);
            SettlementRegistrySC.Slot sl = at >= 0 && at < s.slots.size() ? s.slots.get(at) : null;
            Set<Integer> names = new HashSet<Integer>();
            names.add(Integer.valueOf(NpcNamesSC.code(s.nameElement, s.nameNumber)));
            for (int i = 0; i < s.slots.size() - 1; i++) {
                names.add(Integer.valueOf(s.slots.get(i).nameCode()));
            }
            int[] post = RosterSC.dispatcherPost(s);
            boolean slot = sl != null && at == s.slots.size() - 1 && sl.kind == NpcKindSC.DISPATCHER.ordinal() && sl.state == SettlementRegistrySC.NEVER
                    && sl.nameCode() != 0 && !names.contains(Integer.valueOf(sl.nameCode())) && sl.postX == post[0] && sl.postY == post[1] && sl.postZ == post[2]
                    && s.extra.getCompoundTag(RosterSC.EXTRA_S).getBoolean(RosterSC.F6_DISPATCHER);
            if (again != -1 || !kept || !slot || RosterSC.dispatcherDue(s, SUB_DIM)) {
                bad.append(" [").append(s.sizeKind()).append(": index ").append(at).append(", again ").append(again).append(", kept ").append(kept)
                        .append(", slot ").append(slot).append(']');
            }
        }
        // the post: across the street from P_GATE, both axes; P_GATE on the axis - itself; none - the core's column
        SettlementRegistrySC.Settlement s = substrateRecord(SettlementSizeSC.SETTLEMENT, 9L);
        s.points.put(SettlementRegistrySC.P_GATE, new int[]{s.x + 30, s.floorY + 1, s.z + 2});
        int[] alongX = RosterSC.dispatcherPost(s);
        s.points.put(SettlementRegistrySC.P_GATE, new int[]{s.x - 2, s.floorY + 1, s.z - 30});
        int[] alongZ = RosterSC.dispatcherPost(s);
        s.points.put(SettlementRegistrySC.P_GATE, new int[]{s.x, s.floorY + 1, s.z + 30});
        int[] onAxis = RosterSC.dispatcherPost(s);
        s.points.remove(SettlementRegistrySC.P_GATE);
        int[] none = RosterSC.dispatcherPost(s);
        boolean posts = same(alongX, s.x + 30, s.floorY + 1, s.z - 2) && same(alongZ, s.x + 2, s.floorY + 1, s.z - 30) && same(onAxis, s.x, s.floorY + 1, s.z + 30)
                && same(none, s.x, s.floorY + 1, s.z);
        check(due && bad.length() == 0 && posts, "dispatcher (ПЖ-11, ПР-8): due at a Substrate Settlement, City and «Первый причал», not an Outpost, an"
                + " embassy or another world " + due + "; addDispatcher - one slot at the end (DISPATCHER, NEVER, a name new to the record, F6Disp), the old"
                + " slots untouched, a second call -1" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; dispatcherPost across the street from P_GATE (x and"
                + " z axes), on the axis - P_GATE, none - the core's column " + posts);
    }

    private static boolean same(int[] a, int x, int y, int z) {
        return a != null && a.length == 3 && a[0] == x && a[1] == y && a[2] == z;
    }

    // ------------------------------------------------------------------ 6. EmbassySiteSC, pure (§5.1, ПЕ-1..ПЕ-3)

    private static void siteMaths() {
        int g = 2048;
        boolean regions = EmbassySiteSC.region(0, g) == 0 && EmbassySiteSC.region(2047, g) == 0 && EmbassySiteSC.region(2048, g) == 1
                && EmbassySiteSC.region(-1, g) == -1 && EmbassySiteSC.region(-2048, g) == -1 && EmbassySiteSC.region(-2049, g) == -2;
        // the share of the regions that roll (45%) on three seeds, 6724 regions each
        int rolls = 0, all = 0;
        StringBuilder per = new StringBuilder();
        boolean perOk = true;
        for (long seed : SEEDS) {
            int n = 0, yes = 0;
            for (int rx = -41; rx <= 40; rx++) {
                for (int rz = -41; rz <= 40; rz++) {
                    n++;
                    yes += EmbassySiteSC.rolls(seed, rx, rz, 45) ? 1 : 0;
                }
            }
            rolls += yes;
            all += n;
            float pct = 100F * yes / n;
            perOk &= Math.abs(pct - 45F) <= 2.5F;
            per.append(' ').append(Math.round(pct * 10F) / 10F);
        }
        float share = 100F * rolls / all;
        boolean edges = !EmbassySiteSC.rolls(SEEDS[0], 3, 4, 0) && EmbassySiteSC.rolls(SEEDS[0], 3, 4, 100) && EmbassySiteSC.rolls(SEEDS[0], 3, 4, 150);
        // the candidates: CANDIDATES points within grid / 4 of the middle, in their region, the same twice; neighbours' at least grid / 2 apart
        StringBuilder bad = new StringBuilder();
        double closest = Double.MAX_VALUE;
        for (long seed : SEEDS) {
            for (int rx = -6; rx <= 6; rx++) {
                for (int rz = -6; rz <= 6; rz++) {
                    int[][] c = EmbassySiteSC.candidates(seed, rx, rz, g), c2 = EmbassySiteSC.candidates(seed, rx, rz, g);
                    int mx = rx * g + g / 2, mz = rz * g + g / 2;
                    if (c.length != EmbassySiteSC.CANDIDATES) {
                        bad.append(" count ").append(c.length);
                    }
                    for (int k = 0; k < c.length; k++) {
                        if (Math.abs(c[k][0] - mx) > g / 4 || Math.abs(c[k][1] - mz) > g / 4 || c[k][0] != c2[k][0] || c[k][1] != c2[k][1]
                                || EmbassySiteSC.region(c[k][0], g) != rx || EmbassySiteSC.region(c[k][1], g) != rz) {
                            bad.append(" cand ").append(rx).append(',').append(rz).append('#').append(k);
                        }
                    }
                    int[][] right = EmbassySiteSC.candidates(seed, rx + 1, rz, g), down = EmbassySiteSC.candidates(seed, rx, rz + 1, g);
                    for (int[] a : c) {
                        for (int[][] o : new int[][][]{right, down}) {
                            for (int[] b : o) {
                                closest = Math.min(closest, Math.hypot(a[0] - b[0], a[1] - b[1]));
                            }
                        }
                    }
                }
            }
        }
        long s1 = EmbassySiteSC.siteSeed(SEEDS[0], 100, 200), s2 = EmbassySiteSC.siteSeed(SEEDS[0], 100, 200), s3 = EmbassySiteSC.siteSeed(SEEDS[0], 101, 200);
        boolean seeds = s1 == s2 && s1 != s3 && EmbassySiteSC.area(96) == 96 + EmbassyLayoutSC.LEVEL_OUT + EmbassyLayoutSC.BLEND
                && EmbassySiteSC.area(96) <= EmbassyLayoutSC.REACH_MAX && EmbassySiteSC.farFromSpawn(1500, 0, 0, 0, 1500) && !EmbassySiteSC.farFromSpawn(1499, 0, 0, 0, 1500);
        // vanilla's grids: World.setRandomSeed's formula on our own Random, the negative cells
        boolean vanilla = true;
        for (long seed : SEEDS) {
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    vanilla &= Arrays.equals(EmbassySiteSC.villageStart(seed, cx, cz), gridStart(seed, cx, cz, 32, 8, 10387312L))
                            && Arrays.equals(EmbassySiteSC.templeStart(seed, cx, cz), gridStart(seed, cx, cz, 32, 8, 14357617L));
                }
            }
        }
        boolean cells = EmbassySiteSC.gridCell(0, 32) == 0 && EmbassySiteSC.gridCell(31, 32) == 0 && EmbassySiteSC.gridCell(32, 32) == 1
                && EmbassySiteSC.gridCell(-1, 32) == -1 && EmbassySiteSC.gridCell(-32, 32) == -1 && EmbassySiteSC.gridCell(-33, 32) == -2;
        // the guaranteed ring's pure candidates: 64 points between min and max of the spawn (rounding: a block), spread out
        boolean ring = true;
        int[][] gc = EmbassySiteSC.guaranteeCandidates(SEEDS[1], 162, 425, 1300, 2500);
        Set<Long> distinct = new HashSet<Long>();
        for (int[] c : gc) {
            double dist = Math.hypot(c[0] - 162, c[1] - 425);
            ring &= dist >= 1300 - 1.5D && dist <= 2500 + 1.5D;
            distinct.add(Long.valueOf(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL)));
        }
        ring &= gc.length == EmbassySiteSC.GUARANTEE_TRIES && distinct.size() > gc.length / 2;
        boolean ok = regions && Math.abs(share - 45F) <= 2F && perOk && edges && bad.length() == 0 && closest >= g / 2 && seeds && vanilla && cells && ring;
        check(ok, "site maths (ПЕ-1..ПЕ-3): region() on the negatives " + regions + "; " + Math.round(share * 10F) / 10F + "% of " + all + " regions roll"
                + " at 45 (per seed" + per + ") " + perOk + ", 0 / 100 / 150 " + edges + "; " + EmbassySiteSC.CANDIDATES + " candidates within grid/4 of the"
                + " middle, in their region, the same twice" + (bad.length() == 0 ? "" : " - BAD:" + bad.substring(0, Math.min(200, bad.length())))
                + "; neighbours' candidates >= " + Math.round(closest) + " apart (" + g / 2 + "); siteSeed, area <= " + EmbassyLayoutSC.REACH_MAX + ", farFromSpawn "
                + seeds + "; villageStart / templeStart = World.setRandomSeed's formula " + vanilla + "; gridCell's negative rule " + cells + "; "
                + gc.length + " guaranteed candidates 1300..2500 from the spawn, " + distinct.size() + " different " + ring);
    }

    /** World.setRandomSeed's formula and MapGenVillage / MapGenScatteredFeature's pick, written again here. */
    private static int[] gridStart(long worldSeed, int cx, int cz, int spacing, int separation, long salt) {
        Random r = new Random((long) cx * 341873128712L + (long) cz * 132897987541L + worldSeed + salt);
        return new int[]{cx * spacing + r.nextInt(spacing - separation), cz * spacing + r.nextInt(spacing - separation)};
    }

    // ------------------------------------------------------------------ 7. EmbassyLayoutSC (§5.2, ПЕ-5..ПЕ-8, ПЖ-13)

    private static void layout() {
        boolean floors = EmbassyLayoutSC.floorY(0.125F) == 67 && EmbassyLayoutSC.floorY(-2F) == 65 && EmbassyLayoutSC.floorY(2F) == 72
                && EmbassyLayoutSC.floorY(0.1F) == 66;
        // the frame both ways, in all four directions
        boolean frame = true;
        for (int dir = 0; dir < 4; dir++) {
            EmbassyLayoutSC.Plan p = new EmbassyLayoutSC.Plan(new EmbassySiteSC.Site(1000, -2000, 90, 67, 5L, false));
            p.coreX = 1000;
            p.coreZ = -2000;
            p.gateDir = dir;
            for (int a = -60; a <= 60 && frame; a += 7) {
                for (int q = -60; q <= 60 && frame; q += 11) {
                    int[] f = p.frame(p.x(a, q), p.z(a, q));
                    frame = f[0] == a && f[1] == q;
                }
            }
            frame &= p.x(1, 0) - p.coreX == SettlementLayoutSC.DIR_X[dir] && p.z(1, 0) - p.coreZ == SettlementLayoutSC.DIR_Z[dir];
        }
        // 200 sites: the roster and its posts, the Vokzal and the ring on the level ground, the vortex, the record
        StringBuilder bad = new StringBuilder();
        Random r = new Random(0xF6L);
        int checked = 0, tunersLo = 99, tunersHi = 0;
        for (int i = 0; i < 200; i++) {
            long seed = r.nextLong();
            int x = r.nextInt(40000) - 20000, z = r.nextInt(40000) - 20000;
            EmbassySiteSC.Site site = new EmbassySiteSC.Site(x, z, EmbassySiteSC.radius(seed), 65 + r.nextInt(8), seed, false);
            List<NpcKindSC> roster = EmbassyLayoutSC.roster(site);
            String why = EmbassyLayoutSC.rosterProblem(roster);
            int tuners = 0;
            for (NpcKindSC k : roster) {
                tuners += k.guard ? 0 : 1;
                if (k == NpcKindSC.ARMOURER) {
                    why = "an Armourer";
                }
            }
            tunersLo = Math.min(tunersLo, tuners);
            tunersHi = Math.max(tunersHi, tuners);
            EmbassyLayoutSC.Plan p;
            try {
                p = EmbassyLayoutSC.plan(SEEDS[0], site, site.floorY);
            } catch (RuntimeException ex) {
                bad.append(" [plan: ").append(ex).append(']');
                break;
            }
            checked++;
            if (why == null && p.posts.size() != roster.size()) {
                why = p.posts.size() + " posts for " + roster.size();
            }
            for (int k = 0; why == null && k < roster.size(); k++) {
                if (p.posts.get(k).kind != roster.get(k)) {
                    why = "post " + k + " is " + p.posts.get(k).kind + ", the roster's " + roster.get(k);
                }
            }
            int beacon = site.radius + EmbassyLayoutSC.VOKZAL_OUT, h = EmbassyLayoutSC.VOKZAL_HALF, ra = beacon + EmbassyLayoutSC.RING_BACK;
            for (int[] corner : new int[][]{{beacon - h, -h}, {beacon - h, h}, {beacon + h, -h}, {beacon + h, h}, {ra, -EmbassyLayoutSC.RING_R},
                    {ra, EmbassyLayoutSC.RING_R}}) {
                int cx = p.x(corner[0], corner[1]) - p.coreX, cz = p.z(corner[0], corner[1]) - p.coreZ;
                if (why == null && EmbassyLayoutSC.levelPast(cx, cz, p.levelRadius) > 0D) {
                    why = "the Vokzal / ring corner " + corner[0] + "," + corner[1] + " off the level ground";
                }
            }
            double mx = p.ringX + 0.5D, mz = p.ringZ + 0.5D;
            int[] off2 = {p.x(ra + 2, 0), p.z(ra + 2, 0)}, side3 = {p.x(ra, 3), p.z(ra, 3)}, side2 = {p.x(ra, 2), p.z(ra, 2)};
            boolean vortex = EmbassyLayoutSC.inVortex(p, mx, p.floorY + 1, mz) && EmbassyLayoutSC.inVortex(p, mx, p.ringY, mz)
                    && !EmbassyLayoutSC.inVortex(p, off2[0] + 0.5D, p.floorY + 1, off2[1] + 0.5D) && !EmbassyLayoutSC.inVortex(p, mx, p.floorY + 8, mz)
                    && !EmbassyLayoutSC.inVortex(p, mx, p.floorY, mz) && EmbassyLayoutSC.inVortex(p, side2[0] + 0.5D, p.floorY + 2, side2[1] + 0.5D)
                    && !EmbassyLayoutSC.inVortex(p, side3[0] + 0.5D, p.floorY + 2, side3[1] + 0.5D);
            if (why == null && !vortex) {
                why = "the vortex's bounds";
            }
            int e = p.levelRadius + EmbassyLayoutSC.BLEND;
            if (why == null && (p.levelRadius != site.radius + EmbassyLayoutSC.LEVEL_OUT || e != EmbassySiteSC.area(site.radius)
                    || p.footprint[0] != p.coreX - e || p.footprint[3] != p.coreZ + e || e > EmbassyLayoutSC.REACH_MAX)) {
                why = "the footprint " + Arrays.toString(p.footprint);
            }
            if (why == null && (!EmbassyLayoutSC.onVokzal(p, p.dockX, p.dockZ) || EmbassyLayoutSC.onVokzal(p, p.coreX, p.coreZ)
                    || p.dockX != p.x(beacon, 0) || p.dockY != p.floorY || p.coreY != p.floorY + 1 || p.backGateDir != ((p.gateDir + 2) & 3))) {
                why = "the Vokzal's beacon / the core";
            }
            int[] office = p.pointOf(EmbassyLayoutSC.P_OFFICE), ring = p.pointOf(EmbassyLayoutSC.P_RING);
            if (why == null && (office == null || ring == null || Math.hypot(office[0] - p.dockX, office[2] - p.dockZ) > EmbassyLayoutSC.DOCK_ZONE
                    || Math.hypot(ring[0] - p.dockX, ring[2] - p.dockZ) > EmbassyLayoutSC.DOCK_ZONE)) {
                why = "the office / the ring outside the zone";
            }
            // the record and fill
            SettlementRegistrySC.Settlement rec = EmbassyLayoutSC.record(p, 0, 1234L);
            Set<Integer> names = new HashSet<Integer>();
            names.add(Integer.valueOf(NpcNamesSC.code(rec.nameElement, rec.nameNumber)));
            boolean slots = rec.slots.size() == p.posts.size();
            for (SettlementRegistrySC.Slot sl : rec.slots) {
                slots &= sl.state == SettlementRegistrySC.NEVER && names.add(Integer.valueOf(sl.nameCode()));
            }
            if (why == null && (!SettlementSC.isEmbassy(rec) || rec.dockZone != EmbassyLayoutSC.DOCK_ZONE || !rec.hasDock || rec.dockX != p.dockX
                    || rec.dockZ != p.dockZ || rec.dim != 0 || rec.radius != site.radius || rec.seed != site.seed || !slots || rec.point(EmbassyLayoutSC.P_RING) == null)) {
                why = "the record";
            }
            SettlementRegistrySC.Settlement lost = new SettlementRegistrySC.Settlement();
            lost.id = 77;
            lost.nameElement = 3;
            lost.nameNumber = 33;
            EmbassyLayoutSC.fill(lost, p);
            if (why == null && (lost.id != 77 || lost.nameElement != 3 || lost.nameNumber != 33 || lost.slots.size() != p.posts.size()
                    || lost.dockZone != EmbassyLayoutSC.DOCK_ZONE || !SettlementSC.isEmbassy(lost))) {
                why = "fill";
            }
            EmbassyLayoutSC.Plan again = EmbassyLayoutSC.planOf(rec, SEEDS[0]);
            if (why == null && (again.ringX != p.ringX || again.ringZ != p.ringZ || again.dockX != p.dockX || again.gateDir != p.gateDir)) {
                why = "planOf";
            }
            if (why != null && bad.length() < 500) {
                bad.append(" [").append(x).append(',').append(z).append(" r").append(site.radius).append(": ").append(why).append(']');
            }
        }
        // targetY: the floor on the level ground, the land's own past the blend, monotone between
        boolean blend = EmbassyLayoutSC.targetY(90, 67, 0D) == 67 && EmbassyLayoutSC.targetY(90, 67, -5D) == 67 && EmbassyLayoutSC.targetY(90, 67, 8D) == 90
                && EmbassyLayoutSC.targetY(40, 67, 9D) == 40;
        int up = 67, down = 67;
        for (double past = 0D; past <= EmbassyLayoutSC.BLEND; past += 0.25D) {
            int u = EmbassyLayoutSC.targetY(90, 67, past), d = EmbassyLayoutSC.targetY(50, 67, past);
            blend &= u >= up && d <= down;
            up = u;
            down = d;
        }
        boolean ok = floors && frame && bad.length() == 0 && checked == 200 && tunersLo >= 18 && tunersHi <= 22 && blend;
        check(ok, "layout (EmbassyLayoutSC, ПЕ-5..ПЕ-8, ПЖ-13): floorY 0.125 / -2 / 2 -> 67 / 65 / 72 " + floors + "; the frame both ways in 4 directions "
                + frame + "; " + checked + " sites of 200: the roster (the Ambassador first, 18-22 settlers - here " + tunersLo + "-" + tunersHi + ", no"
                + " Armourer, 12 guards), a post for each in its order, the Vokzal's and the ring's corners on the level ground, the vortex (the ring's"
                + " middle, the floor, 2 off the plane / 3 across / 8 up - out), the footprint = area, the beacon on the Vokzal, the office and the ring"
                + " within the zone 13, the record (EMBASSY, zone 13, the beacon, slots NEVER, names unique), fill keeps the id and the name, planOf the"
                + " same" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; targetY: the floor, the land's own past " + EmbassyLayoutSC.BLEND + ", monotone " + blend);
    }

    // ------------------------------------------------------------------ 8. EmbassyRulesSC (§7.1, ПЯ-3..ПЯ-16)

    private static void flightRules() {
        int c = EmbassyRulesSC.COUNTDOWN_TICKS, v = EmbassyRulesSC.VORTEX_TICKS, k = EmbassyRulesSC.COOL_TICKS;
        boolean nums = c == 200 && v == 400 && k == 1200 && v == BridgeMathSC.SPACE_LIFE_S * 20 && k == BridgeMathSC.COOL_S * 20
                && EmbassyRulesSC.PASSENGERS == 4 && EmbassyRulesSC.FLIGHTS_PER_DAY == 2 && EmbassyRulesSC.ROUND_TRIP_PCT == 90
                && EmbassyRulesSC.BACK_TICKS == 3L * SettlementSC.DAY && EmbassyRulesSC.RESCUE_TICKS == 2L * SettlementSC.DAY && EmbassyRulesSC.CONFIRM_TICKS == 100
                && EmbassyRulesSC.GUARANTEE_PCT == 150 && EmbassyRulesSC.GUARANTEE_PENALTY == -30 && EmbassyRulesSC.NEAR_DISPATCHER == 8
                && EmbassyRulesSC.RIDE_KEYS.length == 7 && EmbassyRulesSC.RIDE_BASE.length == 7 && EmbassyRulesSC.RIDE_LEVEL.length == 7
                && EmbassyRulesSC.REFUSE_KEYS.length == 19 && EmbassyRulesSC.R_HAS_BACK == 18 && EmbassyRulesSC.TARGET_FIRST_PURE == -2 && EmbassyRulesSC.TARGET_MARK == -3;
        boolean phases = EmbassyRulesSC.phaseAt(-1) == EmbassyRulesSC.PHASE_IDLE && EmbassyRulesSC.phaseAt(0) == EmbassyRulesSC.PHASE_COUNTDOWN
                && EmbassyRulesSC.phaseAt(199) == EmbassyRulesSC.PHASE_COUNTDOWN && EmbassyRulesSC.phaseAt(200) == EmbassyRulesSC.PHASE_VORTEX
                && EmbassyRulesSC.phaseAt(599) == EmbassyRulesSC.PHASE_VORTEX && EmbassyRulesSC.phaseAt(600) == EmbassyRulesSC.PHASE_COOL
                && EmbassyRulesSC.phaseAt(1799) == EmbassyRulesSC.PHASE_COOL && EmbassyRulesSC.phaseAt(1800) == EmbassyRulesSC.PHASE_IDLE
                && EmbassyRulesSC.phaseTicksLeft(0) == 200 && EmbassyRulesSC.phaseTicksLeft(199) == 1 && EmbassyRulesSC.phaseTicksLeft(200) == 400
                && EmbassyRulesSC.phaseTicksLeft(1799) == 1 && EmbassyRulesSC.phaseTicksLeft(1800) == 0
                && EmbassyRulesSC.joinable(EmbassyRulesSC.PHASE_IDLE, 9) && EmbassyRulesSC.joinable(EmbassyRulesSC.PHASE_VORTEX, 3)
                && !EmbassyRulesSC.joinable(EmbassyRulesSC.PHASE_VORTEX, 4) && !EmbassyRulesSC.joinable(EmbassyRulesSC.PHASE_COOL, 0);
        // the proposal's table (§7.1) at no spread: Знакомый / Партнёр / Свой / Почётный, 0 - closed
        int[][] want = {{15, 12, 11, 11}, {0, 24, 22, 21}, {0, 32, 29, 28}, {25, 20, 18, 17}, {25, 20, 18, 17}, {0, 0, 0, 0}, {0, 0, 36, 34}};
        StringBuilder table = new StringBuilder();
        for (int ride = 0; ride < want.length; ride++) {
            for (int lv = ReputationSC.ACQUAINTANCE; lv <= ReputationSC.HONOURED; lv++) {
                int got = ride == EmbassyRulesSC.RIDE_BACK || lv < EmbassyRulesSC.levelOf(ride) ? 0
                        : TradeRulesSC.playerSide(EmbassyRulesSC.RIDE_BASE[ride], lv, 0);
                if (got != want[ride][lv - ReputationSC.ACQUAINTANCE]) {
                    table.append(' ').append(EmbassyRulesSC.RIDE_KEYS[ride]).append('@').append(lv).append('=').append(got);
                }
            }
        }
        // the spread: within ±10% (up) over 2000 seeds; a seed of no spread for both FIRST and HOME - the round trip 24 + 20 -> 40
        boolean spread = true;
        Random r = new Random(5L);
        for (int i = 0; i < 2000; i++) {
            long seed = r.nextLong();
            int p = EmbassyRulesSC.price(EmbassyRulesSC.RIDE_DOCK, ReputationSC.PARTNER, seed);
            spread &= p >= (int) Math.ceil(32 * 0.9D - 1e-9) && p <= (int) Math.ceil(32 * 1.1D + 1e-9);
        }
        long zero = Long.MIN_VALUE;
        for (long seed = 0; seed < 400000L && zero == Long.MIN_VALUE; seed++) {
            if (TradeRulesSC.seedPct(seed, EmbassyRulesSC.priceKey(EmbassyRulesSC.RIDE_FIRST)) == 0
                    && TradeRulesSC.seedPct(seed, EmbassyRulesSC.priceKey(EmbassyRulesSC.RIDE_HOME)) == 0) {
                zero = seed;
            }
        }
        int round = zero == Long.MIN_VALUE ? -1 : EmbassyRulesSC.roundTripPrice(EmbassyRulesSC.RIDE_FIRST, ReputationSC.PARTNER, zero);
        int guarantee = zero == Long.MIN_VALUE ? -1 : EmbassyRulesSC.guaranteePrice(EmbassyRulesSC.RIDE_FIRST, ReputationSC.PARTNER, zero);
        boolean prices = round == 40 && guarantee == 36 && EmbassyRulesSC.roundTripPrice(EmbassyRulesSC.RIDE_RESCUE, ReputationSC.PARTNER, 1L) == 0
                && EmbassyRulesSC.price(EmbassyRulesSC.RIDE_BACK, ReputationSC.PARTNER, 1L) == 0 && EmbassyRulesSC.price(EmbassyRulesSC.RIDE_FIRST, ReputationSC.ENEMY, 1L) == 0
                && EmbassyRulesSC.price(99, ReputationSC.PARTNER, 1L) == 0;
        boolean kinds = EmbassyRulesSC.outbound(EmbassyRulesSC.RIDE_RESCUE) && !EmbassyRulesSC.outbound(EmbassyRulesSC.RIDE_HOME)
                && !EmbassyRulesSC.counted(EmbassyRulesSC.RIDE_RESCUE) && EmbassyRulesSC.counted(EmbassyRulesSC.RIDE_BACK) && !EmbassyRulesSC.counted(-1)
                && EmbassyRulesSC.roundTrip(EmbassyRulesSC.RIDE_INTRO) && !EmbassyRulesSC.roundTrip(EmbassyRulesSC.RIDE_OTHER)
                && EmbassyRulesSC.levelOf(-1) == ReputationSC.HONOURED + 1 && "sc.flight.ride.none".equals(EmbassyRulesSC.rideKey(7))
                && "sc.flight.refuse.notHere".equals(EmbassyRulesSC.refuseKey(-1)) && "sc.flight.refuse.channel".equals(EmbassyRulesSC.refuseKey(EmbassyRulesSC.R_CHANNEL))
                && "sc.flight.refuse.hasBack".equals(EmbassyRulesSC.refuseKey(EmbassyRulesSC.R_HAS_BACK));
        String matrix = refusals();
        long day = SettlementSC.DAY;
        boolean rescue = EmbassyRulesSC.rescuable(true, 1000L, 1000L + 2 * day, -1L) && !EmbassyRulesSC.rescuable(true, 1000L, 1001L + 2 * day, -1L)
                && !EmbassyRulesSC.rescuable(true, 1000L, 1500L, 1000L) && EmbassyRulesSC.rescuable(true, 1000L, 1500L, 400L)
                && !EmbassyRulesSC.rescuable(false, 1000L, 1500L, -1L) && !EmbassyRulesSC.rescuable(true, 1000L, 999L, -1L);
        boolean back = EmbassyRulesSC.backValid(1000L, 999L) && !EmbassyRulesSC.backValid(1000L, 1000L) && !EmbassyRulesSC.backValid(0L, 0L);
        boolean guar = EmbassyRulesSC.guaranteeBroken(100L, 100L + 3 * day) && !EmbassyRulesSC.guaranteeBroken(100L, 101L + 3 * day)
                && !EmbassyRulesSC.guaranteeBroken(100L, 99L) && EmbassyRulesSC.guaranteeOpen(ReputationSC.PARTNER, ReputationSC.STRANGER)
                && !EmbassyRulesSC.guaranteeOpen(ReputationSC.ACQUAINTANCE, ReputationSC.STRANGER) && !EmbassyRulesSC.guaranteeOpen(ReputationSC.PARTNER, ReputationSC.VIOLATOR);
        boolean soft = !EmbassyRulesSC.softWarning(true, true) && EmbassyRulesSC.softWarning(false, true) && EmbassyRulesSC.softWarning(true, false)
                && EmbassyRulesSC.confirmed(100L, 150L) && EmbassyRulesSC.confirmed(100L, 200L) && !EmbassyRulesSC.confirmed(100L, 201L)
                && !EmbassyRulesSC.confirmed(0L, 50L) && !EmbassyRulesSC.confirmed(100L, 99L);
        check(nums && phases && table.length() == 0 && spread && prices && kinds && matrix == null && rescue && back && guar && soft, "flight rules (§7.1,"
                + " ПЯ-3..ПЯ-16): the numbers (10 / 20 / 60 s, 4 passengers, 2 a day, 90%, 3 / 2 days, 5 s, 150%, -30, 8) " + nums + "; phaseAt 0 / 199 / 200"
                + " / 599 / 600 / 1799 / 1800, the ticks left, joinable " + phases + "; the proposal's prices by level at no spread"
                + (table.length() == 0 ? " ok" : " - BAD:" + table) + "; the spread within ±10% " + spread + "; a round trip FIRST + HOME at «Партнёр» "
                + round + " (40), a guarantee " + guarantee + " (36), BACK / ENEMY / a bad ride 0 " + prices + "; outbound / counted / roundTrip / keys " + kinds
                + "; the refusals' matrix and order " + (matrix == null ? "ok" : "BAD " + matrix) + "; rescuable " + rescue + ", backValid " + back
                + ", the guarantee " + guar + ", the soft admission " + soft);
    }

    /** EmbassyRulesSC.refusal: every code it returns, in the order of its checks (null: right). */
    private static String refusals() {
        int intro = EmbassyRulesSC.RIDE_INTRO, first = EmbassyRulesSC.RIDE_FIRST, rescue = EmbassyRulesSC.RIDE_RESCUE, home = EmbassyRulesSC.RIDE_HOME,
                back = EmbassyRulesSC.RIDE_BACK, other = EmbassyRulesSC.RIDE_OTHER, dock = EmbassyRulesSC.RIDE_DOCK;
        int partner = ReputationSC.PARTNER, acq = ReputationSC.ACQUAINTANCE, own = ReputationSC.OWN, enemy = ReputationSC.ENEMY, violator = ReputationSC.VIOLATOR;
        // {expected, ride, channel, level, ridesToday, otherToday, introUsed, target, signal, here, pending} (booleans as 0 / 1)
        int[][] cases = {
                {EmbassyRulesSC.R_NOT_HERE, first, 0, enemy, 9, 9, 1, 0, 0, 0, 1},             // not here first of all
                {EmbassyRulesSC.R_NOT_HERE, 99, 1, partner, 0, 0, 0, 1, 1, 1, 0},              // a bad ride
                {EmbassyRulesSC.R_OFF, first, 0, enemy, 9, 9, 1, 0, 0, 1, 1},                  // the channel before the enmity
                {EmbassyRulesSC.R_ENEMY, back, 1, enemy, 0, 0, 0, 1, 1, 1, 0},                 // the «Враг» even on a return half
                {EmbassyRulesSC.R_VIOLATOR, intro, 1, violator, 0, 0, 0, 1, 0, 1, 1},          // the «Нарушитель» before the level and the Record
                {EmbassyRulesSC.OK, back, 1, violator, 0, 0, 0, 1, 0, 1, 0},                   // ... but a paid return half takes him
                {EmbassyRulesSC.R_LEVEL, first, 1, acq, 0, 0, 0, 1, 0, 1, 1},                  // the level before the Record and the talon
                {EmbassyRulesSC.R_LEVEL, other, 1, partner, 0, 0, 0, 1, 1, 1, 0},              // OTHER needs «Свой»
                {EmbassyRulesSC.R_SIGNAL, rescue, 1, acq, 9, 9, 1, 0, 0, 1, 1},                // the Record (out) before the talon
                {EmbassyRulesSC.R_PENDING, home, 1, acq, 9, 9, 1, 0, 0, 1, 1},                 // HOME needs no Record; the talon before the rest
                {EmbassyRulesSC.R_USED, intro, 1, acq, 9, 9, 1, 0, 1, 1, 0},                   // the introductory once, before the limit
                {EmbassyRulesSC.R_LIMIT, dock, 1, partner, 2, 0, 0, 0, 1, 1, 0},               // the day's 2, before the target
                {EmbassyRulesSC.R_LIMIT, other, 1, own, 1, 1, 0, 1, 1, 1, 0},                  // OTHER's own 1 a day
                {EmbassyRulesSC.R_NO_MARK, rescue, 1, acq, 9, 9, 0, 0, 1, 1, 0},               // the rescue: outside the limit, no mark
                {EmbassyRulesSC.R_NO_BACK, back, 1, acq, 0, 0, 0, 0, 0, 1, 0},
                {EmbassyRulesSC.R_NO_TARGET, dock, 1, partner, 1, 0, 0, 0, 1, 1, 0},
                {EmbassyRulesSC.OK, intro, 1, acq, 1, 0, 0, 1, 1, 1, 0},
                {EmbassyRulesSC.OK, other, 1, own, 1, 0, 0, 1, 0, 1, 0},
                {EmbassyRulesSC.OK, rescue, 1, acq, 7, 0, 1, 1, 1, 1, 0}};
        Set<Integer> seen = new HashSet<Integer>();
        StringBuilder bad = new StringBuilder();
        for (int[] t : cases) {
            int got = EmbassyRulesSC.refusal(t[1], t[2] != 0, t[3], t[4], t[5], t[6] != 0, t[7] != 0, t[8] != 0, t[9] != 0, t[10] != 0);
            seen.add(Integer.valueOf(got));
            if (got != t[0]) {
                bad.append(" ride ").append(t[1]).append(" level ").append(t[3]).append(": ").append(got).append(" not ").append(t[0]);
            }
        }
        int[] every = {EmbassyRulesSC.OK, EmbassyRulesSC.R_NOT_HERE, EmbassyRulesSC.R_OFF, EmbassyRulesSC.R_ENEMY, EmbassyRulesSC.R_VIOLATOR, EmbassyRulesSC.R_LEVEL,
                EmbassyRulesSC.R_SIGNAL, EmbassyRulesSC.R_PENDING, EmbassyRulesSC.R_USED, EmbassyRulesSC.R_LIMIT, EmbassyRulesSC.R_NO_MARK, EmbassyRulesSC.R_NO_BACK,
                EmbassyRulesSC.R_NO_TARGET};
        for (int code : every) {
            if (!seen.contains(Integer.valueOf(code))) {
                bad.append(" never ").append(EmbassyRulesSC.REFUSE_KEYS[code]);
            }
        }
        return bad.length() == 0 ? null : bad.toString();
    }

    // ------------------------------------------------------------------ 9. EmbassyTicketSC (§2.5)

    private static void tickets() {
        SettlementRegistrySC r = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        SettlementRegistrySC.PlayerRec fresh = r.player(P2), p = r.player(P1);
        boolean empty = !EmbassyTicketSC.introUsed(fresh) && EmbassyTicketSC.from(fresh) == -1 && EmbassyTicketSC.seen(fresh).length == 0
                && EmbassyTicketSC.backEmbassy(fresh) == -1 && EmbassyTicketSC.backUntil(fresh) == 0L && EmbassyTicketSC.talon(fresh) == null
                && EmbassyTicketSC.rescuedAt(fresh) == -1L && EmbassyTicketSC.guarantee(fresh) == null && !EmbassyTicketSC.guaranteePaid(fresh)
                && !EmbassyTicketSC.introUsed(null) && EmbassyTicketSC.from(null) == -1 && EmbassyTicketSC.seen(null).length == 0 && !EmbassyTicketSC.see(null, 3);
        EmbassyTicketSC.useIntro(p);
        EmbassyTicketSC.setFrom(p, 12);
        boolean seen = EmbassyTicketSC.see(p, 5) && EmbassyTicketSC.see(p, 7) && !EmbassyTicketSC.see(p, 5) && EmbassyTicketSC.hasSeen(p, 7)
                && !EmbassyTicketSC.hasSeen(p, 8);
        EmbassyTicketSC.setBack(p, 9, 72000L);
        EmbassyTicketSC.setTalon(p, new EmbassyTicketSC.Talon(EmbassyRulesSC.RIDE_FIRST, EmbassyRulesSC.TARGET_FIRST_PURE, 9, 40, 3L, P2.toString()));
        EmbassyTicketSC.setRescued(p, 123456L);
        EmbassyTicketSC.setGuarantee(p, P2.toString(), 9, 5000L);
        EmbassyTicketSC.payGuarantee(p);
        NBTTagCompound t = new NBTTagCompound();
        r.writeToNBT(t);
        SettlementRegistrySC back = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        back.readFromNBT(t);
        SettlementRegistrySC.PlayerRec q = back.playerIfAny(P1);
        EmbassyTicketSC.Talon k = q == null ? null : EmbassyTicketSC.talon(q);
        NBTTagCompound g = q == null ? null : EmbassyTicketSC.guarantee(q);
        boolean round = q != null && EmbassyTicketSC.introUsed(q) && EmbassyTicketSC.from(q) == 12 && Arrays.equals(EmbassyTicketSC.seen(q), new int[]{5, 7})
                && EmbassyTicketSC.backEmbassy(q) == 9 && EmbassyTicketSC.backUntil(q) == 72000L && k != null && k.ride == EmbassyRulesSC.RIDE_FIRST
                && k.target == EmbassyRulesSC.TARGET_FIRST_PURE && k.settlement == 9 && k.chips == 40 && k.day == 3L && P2.toString().equals(k.payer)
                && EmbassyTicketSC.rescuedAt(q) == 123456L && g != null && P2.toString().equals(g.getString("f")) && g.getInteger("s") == 9
                && g.getLong("a") == 5000L && EmbassyTicketSC.guaranteePaid(q) && q.extra.hasKey(EmbassyTicketSC.EXTRA, 10) && "R".equals(EmbassyTicketSC.EXTRA);
        boolean stale = k != null && k.stale(4L) && !k.stale(3L) && "".equals(new EmbassyTicketSC.Talon(0, 0, 0, 0, 0L, null).payer);
        // clearing: the return half, the talon; a new guarantee is unpaid again
        EmbassyTicketSC.clearBack(p);
        EmbassyTicketSC.setTalon(p, null);
        EmbassyTicketSC.setGuarantee(p, "x", 1, 1L);
        boolean cleared = EmbassyTicketSC.backEmbassy(p) == -1 && EmbassyTicketSC.talon(p) == null && !EmbassyTicketSC.guaranteePaid(p) && EmbassyTicketSC.from(p) == 12;
        check(empty && seen && round && stale && cleared, "tickets (EmbassyTicketSC): a fresh / null record has nothing " + empty + "; see() twice - once " + seen
                + "; intro, from, seen, the return half, the talon (ride, target, settlement, chips, day, payer), rescued, the guarantee and its penalty back"
                + " from the registry's NBT under extra \"R\" " + round + "; Talon.stale by the day " + stale + "; clearBack / setTalon(null) / a new guarantee unpaid " + cleared);
    }

    // ------------------------------------------------------------------ 10. the packages' pure bodies (G, S, T)

    private static void packageBodies() throws Exception {
        String[] parts = {"G levelling", "S quarters", "T trade"};
        for (int i = 0; i < parts.length; i++) {
            try {
                switch (i) {
                    case 0: terrainBody(); break;
                    case 1: quartersBody(); break;
                    default: tradeBody(); break;
                }
            } catch (Throwable t) {
                check(false, "package bodies, " + parts[i] + " - exception " + t);
                t.printStackTrace();
            }
        }
    }

    /**
     * G (ПЕ-6): the column's levelling of a chunk's own arrays (EmbassyTerrainSC.levelColumn(Block[] blocks, byte[] metas, int base,
     * int height, int floorY, double past), index (x * 16 + z) * height + y, null - air) on a synthetic chunk: on the level ground stone
     * up to 80 with the floor at 67 becomes stone to 67 and air over it, water over stone to 60 becomes stone to 67; half the blend band
     * eases 80 to targetY's own; past the band nothing changes; levelChunk levels exactly the columns within the band's reach.
     */
    private static void terrainBody() throws Exception {
        Class<?> c = cls("com.sc.embassy.EmbassyTerrainSC");
        Method m = c == null ? null : method(c, "levelColumn", Block[].class, byte[].class, int.class, int.class, int.class, double.class);
        Method chunk = c == null ? null : method(c, "levelChunk", Block[].class, byte[].class, int.class, int.class, int.class, int.class, int.class,
                int.class, byte[].class);
        if (m == null) {
            check(false, "package G's levelling (ПЕ-6): EmbassyTerrainSC.levelColumn(Block[], byte[], int base, int height, int floorY, double past) "
                    + (c == null ? "- no class EmbassyTerrainSC" : "- no such method") + " (not written yet, or by another signature)");
            return;
        }
        int h = 256;
        Block[] b = new Block[256 * h];
        byte[] meta = new byte[256 * h];
        int stone = (3 * 16 + 5) * h, water = (7 * 16 + 9) * h, band = (1 * 16 + 1) * h, past = (2 * 16 + 2) * h;
        for (int y = 0; y <= 80; y++) {
            b[stone + y] = Blocks.stone;
            b[band + y] = Blocks.stone;
            b[past + y] = Blocks.stone;
        }
        for (int y = 0; y <= 60; y++) {
            b[water + y] = Blocks.stone;
        }
        for (int y = 61; y <= 63; y++) {
            b[water + y] = Blocks.water;
        }
        int t1 = ((Number) m.invoke(null, b, meta, stone, h, 67, 0D)).intValue();
        int t2 = ((Number) m.invoke(null, b, meta, water, h, 67, -3D)).intValue();
        int t3 = ((Number) m.invoke(null, b, meta, band, h, 67, EmbassyLayoutSC.BLEND / 2D)).intValue();
        int t4 = ((Number) m.invoke(null, b, meta, past, h, 67, (double) EmbassyLayoutSC.BLEND)).intValue();
        int eased = EmbassyLayoutSC.targetY(80, 67, EmbassyLayoutSC.BLEND / 2D);
        String col = column(b, stone, h, 67, "stone") + column(b, water, h, 67, "water") + column(b, band, h, eased, "band") + column(b, past, h, 80, "past");
        String chunks = "";
        if (chunk != null) {
            Block[] all = new Block[256 * h];
            for (int i = 0; i < 256; i++) {
                for (int y = 0; y <= 70; y++) {
                    all[i * h + y] = Blocks.stone;
                }
            }
            // the chunk (0, 0) against a core at (-100, 8), level ground 100 across: the columns with levelPast < BLEND are touched (some, not all)
            int want = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    want += EmbassyLayoutSC.levelPast(x + 100, z - 8, 100) < EmbassyLayoutSC.BLEND ? 1 : 0;
                }
            }
            int got = ((Number) chunk.invoke(null, all, null, 0, 0, -100, 8, 100, 67, new byte[256])).intValue();
            chunks = got == want && want > 0 && want < 256 ? "" : " levelChunk " + got + " columns (" + want + ")";
        } else {
            chunks = " no levelChunk";
        }
        boolean ok = t1 == 67 && t2 == 67 && t3 == eased && t4 == -1 && col.length() == 0 && chunks.length() == 0;
        check(ok, "package G's levelling (ПЕ-6): stone to 80 on the level ground at the floor 67 -> to " + t1 + ", air over it; water over stone to 60 -> "
                + t2 + "; half the band eases 80 to " + t3 + " (" + eased + "); past the band " + t4 + " (-1, untouched)" + col + chunks);
    }

    /** What is wrong with column `base` of a levelled array: solid up to `top`, air (null) over it ("" - nothing). */
    private static String column(Block[] b, int base, int h, int top, String name) {
        for (int y = 1; y < h; y++) {
            Block got = b[base + y];
            boolean solid = got != null && got != Blocks.air && got.getMaterial().isSolid();
            if (y <= top ? !solid : got != null && got != Blocks.air) {
                return " - " + name + " column y" + y + "=" + (got == null ? "air" : got.getUnlocalizedName());
            }
        }
        return "";
    }

    /**
     * S (ПЕ-7, ПГ-21): the quarters on 60 sites - every settler's post but the Dispatchers' under the dome less 2, the Dispatchers' in the
     * Vokzal's zone, the guards' inside the wall or on a tower, the lots inside the area, none overlapping another (but one lying wholly
     * inside another - the ring on the Vokzal - and the long ones: the wall, the street, a gate, a tower), the ring's lot on a = r + 24.
     */
    private static void quartersBody() {
        StringBuilder bad = new StringBuilder(), own = new StringBuilder();
        Random r = new Random(0x5F6L);
        int sites = 0, lots = 0;
        Class<?> quarters = cls("com.sc.embassy.EmbassyQuartersSC");
        Method problem = quarters == null ? null : method(quarters, "problem", EmbassyLayoutSC.Plan.class, List.class);
        for (int i = 0; i < 60; i++) {
            long seed = r.nextLong();
            EmbassySiteSC.Site site = new EmbassySiteSC.Site(r.nextInt(20000) - 10000, r.nextInt(20000) - 10000, EmbassySiteSC.radius(seed), 67, seed, false);
            EmbassyLayoutSC.Plan p = EmbassyLayoutSC.plan(SEEDS[1], site, 67);
            sites++;
            String why = null;
            int rad = p.radius;
            if (problem != null && own.length() < 300) {               // package S's own check of its quarters, by name
                try {
                    Object o = problem.invoke(null, p, EmbassyLayoutSC.roster(site));
                    if (o != null) {
                        own.append(" [").append(site.x).append(',').append(site.z).append(": ").append(o).append(']');
                    }
                } catch (Throwable t) {
                    own.append(" [").append(t).append(']');
                }
            }
            for (SettlementLayoutSC.Post q : p.posts) {
                double fromCore = Math.hypot(q.x + 0.5D - (p.coreX + 0.5D), q.z + 0.5D - (p.coreZ + 0.5D));
                double fromBeacon = Math.hypot(q.x - p.dockX, q.z - p.dockZ);
                if (q.kind == NpcKindSC.DISPATCHER) {
                    if (fromBeacon > EmbassyLayoutSC.DOCK_ZONE) {
                        why = "a Dispatcher " + Math.round(fromBeacon) + " from the beacon";
                    }
                } else if (q.kind.guard) {
                    if (fromCore > rad - EmbassyLayoutSC.WALL_IN && !onTower(p, q)) {
                        why = "a guard " + q.kind.key + " " + Math.round(fromCore) + " out";
                    }
                } else if (fromCore > rad - 2 || Math.hypot(q.homeX - p.coreX, q.homeZ - p.coreZ) > rad - 2) {
                    why = "a settler " + q.kind.key + "'s post or home " + Math.round(fromCore) + " out (dome " + rad + ")";
                }
                if (why != null) {
                    break;
                }
            }
            int ringA = rad + EmbassyLayoutSC.VOKZAL_OUT + EmbassyLayoutSC.RING_BACK;
            boolean ringLot = false;
            for (int a = 0; a < p.lots.size() && why == null; a++) {
                SettlementLayoutSC.Lot l = p.lots.get(a);
                lots++;
                if (l.x0 < p.footprint[0] || l.z0 < p.footprint[1] || l.x1 > p.footprint[2] || l.z1 > p.footprint[3]) {
                    why = "lot " + l.kind + " past the area";
                }
                if (l.kind == EmbassyLayoutSC.LOT_RING) {
                    int[] f0 = p.frame(l.x0, l.z0), f1 = p.frame(l.x1, l.z1);
                    ringLot = Math.min(f0[0], f1[0]) <= ringA && Math.max(f0[0], f1[0]) >= ringA;
                }
                for (int b = a + 1; b < p.lots.size() && why == null; b++) {
                    SettlementLayoutSC.Lot m = p.lots.get(b);
                    boolean overlap = l.x0 <= m.x1 && m.x0 <= l.x1 && l.z0 <= m.z1 && m.z0 <= l.z1;
                    if (overlap && !inside(l, m) && !inside(m, l) && !linear(l) && !linear(m)) {
                        why = "lots " + l.kind + " and " + m.kind + " overlap";
                    }
                }
            }
            if (why == null && !ringLot) {
                why = "no ring lot on a = r + 24";
            }
            if (why != null && bad.length() < 400) {
                bad.append(" [").append(site.x).append(',').append(site.z).append(" r").append(rad).append(": ").append(why).append(']');
            }
        }
        check(bad.length() == 0 && own.length() == 0 && problem != null && sites == 60, "package S's quarters (ПЕ-7, ПГ-21): " + sites + " plans, " + lots
                + " lots - the settlers' posts and homes under the dome less 2, the Dispatchers' in the zone 13, the guards' within the wall or on a tower,"
                + " the lots in the area and not overlapping (but nested and the wall's band), the ring's lot on a = r + 24" + (bad.length() == 0 ? "" : " - BAD:" + bad)
                + "; EmbassyQuartersSC.problem " + (problem == null ? "- not there" : own.length() == 0 ? "null on every plan" : "-" + own));
    }

    private static boolean onTower(EmbassyLayoutSC.Plan p, SettlementLayoutSC.Post q) {
        for (int i = 0; i < 4; i++) {
            int[] t = p.pointOf(SettlementRegistrySC.P_TOWER + i);
            if (t != null && Math.abs(t[0] - q.x) <= 3 && Math.abs(t[2] - q.z) <= 3) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(SettlementLayoutSC.Lot in, SettlementLayoutSC.Lot out) {
        return in.x0 >= out.x0 && in.x1 <= out.x1 && in.z0 >= out.z0 && in.z1 <= out.z1;
    }

    /** The long lots that cross the others by design: the wall, the street, a gate, a tower. */
    private static boolean linear(SettlementLayoutSC.Lot l) {
        return l.kind == EmbassyLayoutSC.LOT_WALL || l.kind == EmbassyLayoutSC.LOT_STREET || l.kind == EmbassyLayoutSC.LOT_GATE || l.kind == EmbassyLayoutSC.LOT_TOWER;
    }

    /**
     * T (ПО-23..ПО-27): EmbassyTradeSC.allowed at an embassy - the only things bought are the Return Capsule and the Settlement Map (no
     * tantalum, hafnium, deuterium, helium, ПЭ-1 module, chip, Crumb, Coordinator, Ruin Finder: none of them is a purchase there), the
     * sales to the Tuners open; the Quartermaster's four deals there (and no Substrate roster has a Quartermaster); ПО-7 (nothing
     * both bought and sold); the Ambassador's contracts.
     */
    private static void tradeBody() {
        SettlementRegistrySC.Settlement emb = record(SettlementSizeSC.EMBASSY, 0, 0, 0), city = record(SettlementSizeSC.CITY, SUB_DIM, 0, 0);
        StringBuilder bought = new StringBuilder(), closed = new StringBuilder();
        int open = 0, sales = 0;
        for (TradeOfferSC o : TradeTableSC.all()) {
            boolean there = EmbassyTradeSC.allowed(o, emb);
            open += there ? 1 : 0;
            if (there && o.buy() && (o.outKind == TradeOfferSC.OUT_ITEM || o.outKind == TradeOfferSC.OUT_MAP)
                    && !TradeTableSC.CART_CAPSULE.equals(o.key) && !TradeTableSC.CART_MAP.equals(o.key)) {
                bought.append(' ').append(o.key);
            }
            if (o.sell() && o.kind != NpcKindSC.QUARTERMASTER && o.minLevel < ReputationSC.OWN) {
                sales++;                                        // every sale to the Tuners (but «Свой» / «Почётный» ones: the Substrate's)
                if (!there) {
                    closed.append(' ').append(o.key);
                }
            }
            if (there && o.minLevel >= ReputationSC.OWN) {
                bought.append(' ').append(o.key).append("(level ").append(o.minLevel).append(')');
            }
            if (EmbassyTradeSC.allowed(o, city) == (o.kind == NpcKindSC.QUARTERMASTER)) {
                closed.append(" (Substrate) ").append(o.key);  // a Substrate settlement: everything but the Quartermaster's
            }
        }
        TradeOfferSC capsule = TradeTableSC.byKey(TradeTableSC.CART_CAPSULE), map = TradeTableSC.byKey(TradeTableSC.CART_MAP);
        boolean shop = capsule != null && map != null && EmbassyTradeSC.allowed(capsule, emb) && EmbassyTradeSC.allowed(map, emb);
        // the Quartermaster: 4 deals - bread 16 / logs 32 / coal 16 / iron 8 for 1 / 1 / 1 / 2 chips, 4 / 4 / 4 / 3 a day, «Чужак»
        String[] qmKeys = {"qmBread", "qmLogs", "qmCoal", "qmIron"};
        int[][] qmWant = {{16, 1, 4}, {32, 1, 4}, {16, 1, 4}, {8, 2, 3}};
        StringBuilder qm = new StringBuilder();
        for (int i = 0; i < qmKeys.length; i++) {
            TradeOfferSC o = TradeTableSC.byKey(qmKeys[i]);
            if (o == null) {
                qm.append(' ').append(qmKeys[i]).append(":missing");
                continue;
            }
            int give = o.in.length > 0 && o.in[0] != null ? o.in[0].count : -1, get = o.out == null ? -1 : o.out.count;
            if (o.kind != NpcKindSC.QUARTERMASTER || !o.sell() || give != qmWant[i][0] || get != qmWant[i][1] || o.limit != qmWant[i][2]
                    || o.minLevel > ReputationSC.STRANGER || !EmbassyTradeSC.allowed(o, emb)) {
                qm.append(' ').append(qmKeys[i]).append(':').append(give).append('/').append(get).append('/').append(o.limit).append('/').append(o.minLevel);
            }
        }
        boolean nowhereElse = true;
        for (long seed : SEEDS) {
            for (int rx = -4; rx <= 4; rx++) {
                for (int rz = -4; rz <= 4; rz++) {
                    SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, rx, rz);
                    nowhereElse &= s == null || !SettlementLayoutSC.roster(s).contains(NpcKindSC.QUARTERMASTER);
                }
            }
        }
        List<String> both = TradeTableSC.boughtAndSold();
        // the Ambassador's contracts: the supply three (6 chips, +15), the dispatch (10, +30), the tantalum capacitors kept
        StringBuilder contracts = new StringBuilder();
        String[] cKeys = {"embSupplyBread", "embSupplyLogs", "embSupplyIron", "embDispatch"};
        int[][] cWant = {{6, 15}, {6, 15}, {6, 15}, {10, 30}};
        for (int i = 0; i < cKeys.length; i++) {
            ContractTableSC.Contract k = ContractTableSC.byKey(cKeys[i]);
            if (k == null || k.chips != cWant[i][0] || k.reputation != cWant[i][1]) {
                contracts.append(' ').append(cKeys[i]).append(k == null ? ":missing" : ":" + k.chips + "/+" + k.reputation);
            }
        }
        if (ContractTableSC.byKey(ContractTableSC.DELIVER_TANTALUM_CAPS) == null) {
            contracts.append(" deliverTantalumCaps:missing");
        }
        // the boards: a Substrate Elder's never shows the Ambassador's own, the Ambassador's only his (package T's embassyBoard, by name)
        Method embBoard = method(ContractTableSC.class, "embassyBoard", UUID.class, long.class);
        if (embBoard == null) {
            contracts.append(" embassyBoard:missing");
        } else {
            Set<String> amb = new HashSet<String>(Arrays.asList(cKeys));
            amb.add(ContractTableSC.DELIVER_TANTALUM_CAPS);
            for (long day = 0; day < 20; day++) {
                for (String key : ContractTableSC.board(P1, day)) {
                    if (key.startsWith("emb")) {
                        contracts.append(" Substrate board ").append(key);
                    }
                }
                try {
                    for (Object key : (List<?>) embBoard.invoke(null, P1, Long.valueOf(day))) {
                        if (!amb.contains(key)) {
                            contracts.append(" the Ambassador's board ").append(key);
                        }
                    }
                } catch (Exception ex) {
                    contracts.append(' ').append(ex);
                    break;
                }
            }
        }
        check(bought.length() == 0 && closed.length() == 0 && shop && qm.length() == 0 && nowhereElse && both.isEmpty() && contracts.length() == 0,
                "package T's trade (ПО-23..ПО-27): at an embassy " + open + " deals open, the only purchases the Capsule and the Map " + shop
                        + (bought.length() == 0 ? "" : " - ALSO SOLD:" + bought) + ", all " + sales + " sales to the Tuners open, a Substrate city's all"
                        + (closed.length() == 0 ? "" : " - CLOSED:" + closed) + "; the Quartermaster's four (16 bread / 32 logs / 16 coal / 8 iron)"
                        + (qm.length() == 0 ? " ok" : " - BAD:" + qm) + ", no Substrate roster has one " + nowhereElse + "; ПО-7 nothing both bought and sold"
                        + (both.isEmpty() ? "" : " - BOTH: " + both) + "; the Ambassador's contracts" + (contracts.length() == 0 ? " ok" : " - BAD:" + contracts));
    }

    // ------------------------------------------------------------------ 11. the texts (§9.1)

    @SuppressWarnings("unchecked")
    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>(Arrays.asList("sc.embassy.name", "sc.embassy.seen", "sc.settle.kind.ambassador", "sc.cmd.embassy.usage",
                "tile.siliconage.embassyRing.name", "sc.tooltip.embassyRing", "sc.tooltip.embassyRing.details", "sc.tooltip.embassyRing.howto",
                "item.siliconage.embassyFinder.name", "sc.tooltip.embassyFinder", "sc.tooltip.embassyFinder.details", "sc.tooltip.embassyFinder.howto",
                "sc.nei.obtain.embassyRing", "sc.nei.obtain.embassyFinder", "sc.nei.obtain.embassy", "sc.manual.world.tuners.kind.dispatcher",
                "sc.manual.world.tuners.kind.quartermaster"));
        for (NpcKindSC k : NpcKindSC.values()) {
            keys.add(k.langKey());
        }
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            keys.add(s.langKey());
        }
        for (String n : BlockShowcase2SC.NAMES) {
            keys.add("tile.siliconage.showcase2." + n + ".name");
        }
        for (int ride = 0; ride <= EmbassyRulesSC.RIDE_KEYS.length; ride++) {
            keys.add(EmbassyRulesSC.rideKey(ride));                     // the last: «none»
        }
        for (int code = 0; code < EmbassyRulesSC.REFUSE_KEYS.length; code++) {
            keys.add(EmbassyRulesSC.refuseKey(code));
        }
        for (String id : ARTICLES) {
            keys.add("sc.manual." + id + ".title");
        }
        // the real stacks' names: what the game asks for the new block, item and metas
        for (ItemStack s : newStacks()) {
            keys.add(s.getItem().getUnlocalizedName(s) + ".name");
        }
        StringBuilder missing = new StringBuilder();
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        StringBuilder args = new StringBuilder();
        List<String[]> messages = new ArrayList<String[]>(Arrays.asList(MESSAGE_ARGS));
        messages.addAll(Arrays.asList(PACKAGE_ARGS));
        for (String[] m : messages) {
            int want = Integer.parseInt(m[1]);
            for (Map<String, String> lang : new Map[]{en, ru}) {
                String v = lang.get(m[0]);
                if (v == null) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(m[0]);
                } else if (positional(v) != want) {
                    args.append(' ').append(m[0]).append('(').append(positional(v)).append(" not ").append(want).append(')');
                }
            }
        }
        // every Ф6 key of one file in the other with as many arguments; English is English, Russian has no «» and no ”
        StringBuilder diff = new StringBuilder();
        int all = 0;
        for (Map<String, String> from : new Map[]{en, ru}) {
            Map<String, String> to = from == en ? ru : en;
            for (Map.Entry<String, String> e : from.entrySet()) {
                if (!ours(e.getKey())) {
                    continue;
                }
                all += from == en ? 1 : 0;
                if (!to.containsKey(e.getKey())) {
                    diff.append(' ').append(from == en ? "only en_US:" : "only ru_RU:").append(e.getKey());
                } else if (from == en && positional(e.getValue()) != positional(to.get(e.getKey()))) {
                    diff.append(" args differ:").append(e.getKey());
                } else if (from == en && e.getValue().matches(".*[\\u0400-\\u04FF«»„].*")) {
                    diff.append(" Cyrillic / «» / „ in en_US:").append(e.getKey());
                } else if (from == ru && e.getValue().matches(".*[«»”].*")) {
                    diff.append(" «» / ” in ru_RU:").append(e.getKey());
                }
            }
        }
        check(missing.length() == 0 && args.length() == 0 && diff.length() == 0, "texts (§9.1): " + keys.size() + " keys - the kinds and sizes, the"
                + " showcase metas, the ring, the finder, the rides and refusals, the articles - and " + MESSAGE_ARGS.length + " messages with their callers'"
                + " arguments and " + PACKAGE_ARGS.length + " of the packages' in en_US and ru_RU" + missing + args + "; " + all + " Ф6 keys with the same arguments in both, no Cyrillic in English, no « » anywhere"
                + diff);
    }

    private static boolean ours(String k) {
        for (String p : PREFIXES) {
            if (k.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /** %s / %d and %1$s... arguments of a lang value (%% isn't one). */
    static int positional(String v) {
        int n = 0;
        for (int i = 0; i + 1 < v.length(); i++) {
            if (v.charAt(i) != '%') {
                continue;
            }
            int j = i + 1;
            if (v.charAt(j) == '%') {
                i++;
                continue;
            }
            while (j < v.length() && (Character.isDigit(v.charAt(j)) || v.charAt(j) == '$')) {
                j++;
            }
            if (j < v.length() && (v.charAt(j) == 's' || v.charAt(j) == 'd')) {
                n++;
                i = j;
            }
        }
        return n;
    }

    /** The new block, item and showcase metas of Ф6 (what isn't registered is left out). */
    static List<ItemStack> newStacks() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (EmbassyBlocksSC.ring != null && Item.getItemFromBlock(EmbassyBlocksSC.ring) != null) {
            out.add(new ItemStack(EmbassyBlocksSC.ring));
        }
        if (EmbassyItemsSC.finder != null) {
            out.add(EmbassyItemsSC.finder(1));
        }
        for (int m = BlockShowcase2SC.GRAVITY_COIL; SettlementBlocksSC.showcase2 != null && m <= BlockShowcase2SC.DISPATCH_CONSOLE; m++) {
            out.add(new ItemStack(SettlementBlocksSC.showcase2, 1, m));
        }
        return out;
    }

    // ------------------------------------------------------------------ 12. the handbook (§9.2) and the cross-check of Ф1..Ф6

    private static void handbook() {
        BookContent.invalidate();
        StringBuilder bad = new StringBuilder(), text = new StringBuilder();
        for (BookEntry e : BookContent.chapter(BookChapter.WORLD)) {
            text.append(e.searchText()).append('\n');
        }
        for (String id : ARTICLES) {
            BookEntry e = BookContent.byId(id);
            if (e == null) {
                bad.append(' ').append(id).append(" missing");
                continue;
            }
            if (e.chapter != BookChapter.WORLD || e.els.size() < 5) {
                bad.append(' ').append(id).append(" chapter ").append(e.chapter).append(" / ").append(e.els.size()).append(" elements");
            }
            raw(bad, id, e.searchText());
        }
        // the extended articles read clean and have their new lines
        String[][] extended = {{"world.substrate", "sc.manual.world.rule.embassies"}, {"world.settlements", "sc.manual.world.settle.part.dispatcher"},
                {"world.tuners", "sc.settle.kind.dispatcher"}, {"world.trust", "sc.manual.world.trust.up.embassy"}, {"world.docks", "sc.manual.world.docks.vokzal"},
                {"world.trade", "sc.manual.world.trade.title"}};
        for (String[] x : extended) {
            BookEntry e = BookContent.byId(x[0]);
            String t = e == null ? "" : e.searchText();
            raw(bad, x[0], t);
            String line = Lang.tr(x[1]).toLowerCase(java.util.Locale.ROOT);
            if (e == null || !t.contains(line)) {
                bad.append(' ').append(x[0]).append(" lacks '").append(line).append('\'');
            }
        }
        // the tables: the rides out (4) and back (3), the roster (13 kinds), the Quartermaster's deals, the settlements' sizes (the Substrate's 3)
        int out = rows("world.flights", 0), back = rows("world.flightsBack", 0), roster = rows("world.embassyCity", 0), sizes = rows("world.settlements", 0);
        int qm = TradeTableSC.offers(NpcKindSC.QUARTERMASTER).size(), qmRows = qm == 0 ? 0 : rows("world.embassyTrade", 0);
        boolean tables = out == 4 && back == 3 && roster == 13 && sizes == SettlementSizeSC.SUBSTRATE_SIZES && qmRows == qm;
        // G: the finder, the ring's core, the showcase metas 7..9 (and the old crate still the settlements')
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        List<String> ids = new ArrayList<String>();
        g(stacks, ids, EmbassyItemsSC.finder(1), "world.embassies");
        g(stacks, ids, EmbassyBlocksSC.ring == null ? null : new ItemStack(EmbassyBlocksSC.ring), "world.flights");
        g(stacks, ids, showcase2(BlockShowcase2SC.GRAVITY_COIL), "world.flights");
        g(stacks, ids, showcase2(BlockShowcase2SC.CRYO_TANK), "world.embassyCity");
        g(stacks, ids, showcase2(BlockShowcase2SC.DISPATCH_CONSOLE), "world.flights");
        g(stacks, ids, showcase2(BlockShowcase2SC.CRATE), "world.settlements");
        StringBuilder noG = new StringBuilder();
        int registered = 0;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            if (s == null || s.getItem() == null) {
                noG.append(" (not registered for ").append(ids.get(i)).append(')');
                continue;
            }
            registered++;
            BookEntry got = BookContent.entryFor(s);
            if (got == null || !ids.get(i).equals(got.id)) {
                noG.append(' ').append(Item.itemRegistry.getNameForObject(s.getItem())).append(':').append(s.getItemDamage()).append(" -> ")
                        .append(got == null ? "nothing" : got.id);
            }
        }
        // the finder's recipe is there (the handbook draws it)
        boolean recipe = false;
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            ItemStack r = ((IRecipe) o).getRecipeOutput();
            recipe |= r != null && EmbassyItemsSC.finder != null && r.getItem() == EmbassyItemsSC.finder;
        }
        // the cross-check of Ф1..Ф6: every kind named in some WORLD article; every article of Ф3..Ф6 there; the chapter's count; every
        // block and item of the dimension and of the embassies G-mapped to a WORLD article
        StringBuilder unnamed = new StringBuilder();
        for (NpcKindSC k : NpcKindSC.values()) {
            String n = Lang.tr(k.langKey()).replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT);
            if (n.startsWith("sc.") || text.indexOf(n) < 0) {
                unnamed.append(' ').append(k.key);
            }
        }
        if (text.indexOf(Lang.tr("sc.settle.kind.ambassador").toLowerCase(java.util.Locale.ROOT)) < 0) {
            unnamed.append(" ambassador");
        }
        StringBuilder noArticle = new StringBuilder();
        for (String[] phase : new String[][]{BookWorldSC.F3_ARTICLES, BookWorldSC.F4_ARTICLES, BookWorldSC.F5_ARTICLES, BookWorldSC.F6_ARTICLES}) {
            for (String id : phase) {
                BookEntry e = BookContent.byId(id);
                if (e == null || e.chapter != BookChapter.WORLD) {
                    noArticle.append(' ').append(id);
                }
            }
        }
        StringBuilder unmapped = new StringBuilder();
        int checked = 0;
        List<ItemStack> every = SelfTestFinaleSC.dimensionStacks();
        every.addAll(newStacks());
        for (ItemStack s : every) {
            checked++;
            BookEntry got = BookContent.entryFor(s);
            if (got == null || got.chapter != BookChapter.WORLD) {
                unmapped.append(' ').append(Item.itemRegistry.getNameForObject(s.getItem())).append(':').append(s.getItemDamage())
                        .append(got == null ? "" : " -> " + got.id);
            }
        }
        int chapter = BookContent.chapter(BookChapter.WORLD).size(), want = 6 + 6 + 8 + BookWorldSC.F4_ARTICLES.length + BookWorldSC.F5_ARTICLES.length
                + ARTICLES.length;
        List<String> unresolved = BookWorldSC.unresolved();
        BookContent.invalidate();
        if (!unresolved.isEmpty()) {
            info("the handbook still prints the plan's numbers for (public static constants of these names in the packages' classes): " + unresolved);
        }
        check(bad.length() == 0 && tables && noG.length() == 0 && chapter == want && recipe, "handbook (§9.2): the " + ARTICLES.length + " new WORLD"
                + " articles (the chapter " + chapter + " of " + want + ") build with no raw keys, the extended ones too with their Ф6 lines"
                + (bad.length() == 0 ? "" : " -" + bad) + "; tables: the rides out " + out + " (4), back " + back + " (3), the roster " + roster + " (13), the"
                + " sizes " + sizes + " (" + SettlementSizeSC.SUBSTRATE_SIZES + "), the Quartermaster's " + qmRows + " (" + qm + ") " + tables + "; G on "
                + registered + " new stacks" + (noG.length() == 0 ? "" : " - NOT:" + noG) + "; the finder's recipe " + recipe);
        check(unnamed.length() == 0 && noArticle.length() == 0 && unmapped.length() == 0 && checked > 50, "handbook cross-check Ф1..Ф6: every one of "
                + NpcKindSC.values().length + " kinds (and the Ambassador) named in a WORLD article" + (unnamed.length() == 0 ? "" : " - NOT:" + unnamed)
                + "; the articles of Ф3..Ф6 all there" + (noArticle.length() == 0 ? "" : " - NOT:" + noArticle) + "; " + checked + " blocks and items of the"
                + " dimension and the embassies each open a WORLD article with G" + (unmapped.length() == 0 ? "" : " - NOT:" + unmapped.substring(0, Math.min(400, unmapped.length()))));
    }

    private static void raw(StringBuilder bad, String id, String t) {
        for (String raw : new String[]{"sc.manual.", "sc.book.", "sc.dim.", "sc.suit.", "sc.settle.", "sc.trade.", "sc.flight.", "sc.embassy.", "sc.contract.",
                "tile.siliconage.", "item.siliconage.", "entity.siliconage.", "generator.", "%s", "%d", "format error"}) {
            if (t.contains(raw)) {
                bad.append(' ').append(id).append(" raw '").append(raw).append('\'');
            }
        }
    }

    private static void g(List<ItemStack> stacks, List<String> ids, ItemStack s, String id) {
        stacks.add(s);
        ids.add(id);
    }

    private static int rows(String id, int index) {
        BookEntry e = BookContent.byId(id);
        int n = 0;
        for (BookEl el : e == null ? new ArrayList<BookEl>() : e.els) {
            if (el.kind == BookEl.Kind.TABLE && n++ == index) {
                return el.rows.size();
            }
        }
        return -1;
    }

    private static ItemStack showcase2(int meta) {
        return SettlementBlocksSC.showcase2 == null ? null : new ItemStack(SettlementBlocksSC.showcase2, 1, meta);
    }

    // ------------------------------------------------------------------ records and helpers

    /** A bare record of `size` in `dim` at (x, z) - floor 67, the size's smallest dome, a seed of its own. */
    static SettlementRegistrySC.Settlement record(SettlementSizeSC size, int dim, int x, int z) {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.dim = dim;
        s.size = size.ordinal();
        s.radius = size.radiusMin;
        s.x = x;
        s.y = 68;
        s.z = z;
        s.floorY = 67;
        s.seed = 0x5E7L + x * 31L + z;
        s.nameElement = 2;
        s.nameNumber = 7;
        return s;
    }

    /** A Substrate record of `size` with a dock (but an Outpost), P_GATE and a Ф2-like roster: the Elder, three trades, the Sentries (ALIVE, named). */
    private static SettlementRegistrySC.Settlement substrateRecord(SettlementSizeSC size, long seed) {
        SettlementRegistrySC.Settlement s = record(size, SUB_DIM, 1000 + (int) seed * 512, 2000);
        s.id = (int) seed + 40;
        s.seed = seed * 7919L;
        s.hasDock = size.dock;
        s.dockX = s.x + s.radius + 6;
        s.dockY = s.floorY;
        s.dockZ = s.z;
        s.points.put(SettlementRegistrySC.P_GATE, new int[]{s.x + s.radius - 4, s.floorY + 1, s.z + 2});
        List<NpcKindSC> kinds = new ArrayList<NpcKindSC>(Arrays.asList(NpcKindSC.ELDER, NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.CARTOGRAPHER));
        for (int i = 0; i < size.sentries; i++) {
            kinds.add(NpcKindSC.SENTRY);
        }
        for (int i = 0; i < kinds.size(); i++) {
            SettlementRegistrySC.Slot sl = new SettlementRegistrySC.Slot();
            sl.kind = kinds.get(i).ordinal();
            sl.nameElement = i + 3;
            sl.nameNumber = 10 + i;
            sl.postX = sl.homeX = s.x + i;
            sl.postY = sl.homeY = s.floorY + 1;
            sl.postZ = sl.homeZ = s.z - i;
            sl.state = SettlementRegistrySC.ALIVE;
            s.slots.add(sl);
        }
        return s;
    }

    private static List<String> slotsText(SettlementRegistrySC.Settlement s) {
        List<String> out = new ArrayList<String>();
        for (SettlementRegistrySC.Slot sl : s.slots) {
            out.add(sl.kind + "/" + sl.nameCode() + "/" + sl.postX + "," + sl.postY + "," + sl.postZ + "/" + sl.state);
        }
        return out;
    }

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name, true, SelfTestEmbassySC.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** A public static method of `c` by its name and exact parameters, or null. */
    private static Method method(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c.getMethod(name, params);
            return Modifier.isStatic(m.getModifiers()) ? m : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
