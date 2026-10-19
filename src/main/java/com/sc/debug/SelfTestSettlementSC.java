package com.sc.debug;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockSettlementDecorSC;
import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateNetSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.energy.FieldMode;
import com.sc.energy.GeneratorType;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.manual.BookChapter;
import com.sc.manual.BookContent;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.BookWorldSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.ViolationRulesSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.npc.WardenSuitSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.settle.travel.ItemSettlementMapSC;
import com.sc.settle.travel.TravelRulesSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.util.ArmorSuit;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * PACKAGE D (docs/plan-substrate-tech-f2.md §11.1): the settlements' pure logic, in the self-test (-Dsc.selftest,
 * preInit - before init: no world, no registration of the dimension; items, blocks and tile ids exist). Called from
 * SelfTestSC.run after SelfTestSubstrateSC. One line each, "[SC-TEST] PASS / FAIL settlement: ...":
 * <ol>
 * <li>the config: villages on, guardsAggroOnViolations off, both rules SubstrateNetSC sends;</li>
 * <li>SettlementRegistrySC: NBT there and back (a settlement with slots, points and a local record; a player with
 * contracts, limits and a death mark; the world's anchor), the slots' life, the day / week counters; the queries on
 * the loaded copy - the protected cylinder (its edge, floor - 6), the belt (r + 24), the dock's zone, a field's box,
 * within(40), the docks nearest (order, skipId), the region index at negative coordinates and across a seam;</li>
 * <li>SettlementSizeSC and NpcKindSC (proposal §5's table, the radius in bounds, respawnTicks 24000 / 6000), NpcNamesSC
 * (code <-> element and number, 0 - no name, every element in both lang files);</li>
 * <li>ReputationSC (the levels on their borders, the multipliers, buys / sells, give with its +20 caps and clamp,
 * afterWardenKill: 100 -> 36, 50 -> -10, 0 -> -60, killReward) and ViolationRulesSC (stages, penalties, provokes with
 * and without the rule, chargesAgain);</li>
 * <li>SettlementLayoutSC (package S's bodies): determinism, the First Dock (region 0, 0, cell 1, 0, within 300 of the
 * spawn), the share of regions with a settlement (55 +- 5% of 64 x 64), the sizes' shares, two never closer than
 * 224, every footprint on the plateau and off the spawn's cell, the roster of §4.2, posts inside the dome, the dock
 * outside it, the footprint within 5 chunks;</li>
 * <li>the trade (package T's bodies): every deal's sides exist, unique keys with lang, nothing both bought and sold
 * (ПО-7), no forbidden output, playerSide (16 -> 24 for a Stranger, 14 for the Honoured), seedPct within +-10,
 * available, chipsAllowed(60, 8) = 4; the contracts (unique keys, lang, the board deterministic, 3 of them);</li>
 * <li>TravelRulesSC (the dock's scatter 0, the death mark's >= 30, the capsule's seconds);</li>
 * <li>TileEntitySettlementCoreSC without a world: setup(7, 40, 64) - the owner, DOME, range, the six flags, pay, no
 * upkeep, no sink; its NBT there and back, a stranger's NBT overwritten by the fixed settings;</li>
 * <li>the texts of §10.1 in both languages (the same arguments, no Cyrillic in English), the WORLD chapter's six new
 * articles with no raw keys, G on every new block, item and egg.</li>
 * </ol>
 * Each section runs on its own: one that throws (a package's body unfinished) fails alone.
 */
public final class SelfTestSettlementSC {

    /** The six Ф2 articles at the end of the WORLD chapter (SelfTestSubstrateSC counts Ф1's six and these). */
    static final String[] ARTICLES = {"world.settlements", "world.tuners", "world.trade", "world.trust", "world.wardens", "world.docks"};
    /** The pool's contracts of §9.6 (the one-time «anchor» is offered apart): key, target, chips, «Доверие», module %. */
    private static final Object[][] CONTRACTS = {{"killMarauders", 10, 8, 20, 0}, {"deliverTantalumCaps", 32, 14, 25, 0}, {"suppressKill", 5, 10, 25, 20},
            {"returnCargo", 3, 10, 35, 0}};
    /** Key prefixes of Ф2's texts: each in both languages with as many arguments, no Cyrillic and no «» in English. */
    private static final String[] PREFIXES = {"sc.settle.", "sc.trade.", "sc.contract.", "sc.capsule.", "sc.map.", "sc.death.", "sc.bridge.target.",
            "sc.bridge.place.settlement", "sc.manual.world.settle", "sc.manual.world.tuners", "sc.manual.world.trade", "sc.manual.world.trust",
            "sc.manual.world.wardens", "sc.manual.world.docks", "sc.manual.world.cell.", "sc.manual.world.loot.stolenCargo", "sc.nei.obtain.creditChip",
            "sc.nei.obtain.settle", "sc.nei.obtain.cartographer", "sc.nei.obtain.egg.settle", "tile.siliconage.settlementCore", "tile.siliconage.showcase",
            "tile.siliconage.dock", "tile.siliconage.substrateRock.settlementBrick", "item.siliconage.creditChip", "item.siliconage.returnCapsule",
            "item.siliconage.settlementMap", "item.siliconage.combatLoot.stolenCargo", "sc.tooltip.settlementCore", "sc.tooltip.showcase", "sc.tooltip.dock.",
            "sc.tooltip.substrateRock.settlementBrick", "sc.tooltip.creditChip", "sc.tooltip.returnCapsule", "sc.tooltip.settlementMap",
            "sc.tooltip.loot.stolenCargo", "entity.SiliconAge.tuner", "entity.SiliconAge.warden", "tile.siliconage.settlementDecor",
            "tile.siliconage.settlementGlass", "sc.tooltip.settlementDecor", "sc.waila.settle."};
    /** The tooltips of §10.1 (each with .details and .howto). */
    private static final String[] TOOLTIPS = {"settlementCore", "showcase", "dock.plate", "dock.beacon", "substrateRock.settlementBrick", "creditChip",
            "returnCapsule", "settlementMap", "loot.stolenCargo", "settlementDecor"};
    private static final String[] SECTIONS = {"config", "registry NBT", "registry queries", "sizes and kinds", "names", "reputation", "violations",
            "layout", "trade", "contracts", "travel", "core", "texts", "wardens' suit", "handbook"};
    /** The default dimension id: the registry keeps any int, the tests need no live one. */
    private static final int DIM = -1414, OTHER_DIM = 7;
    private static final UUID P1 = UUID.fromString("5c0e7a1e-0000-4000-8000-000000000001"), P2 = UUID.fromString("5c0e7a1e-0000-4000-8000-000000000002");
    private static final long[] SEEDS = {0L, 1234567L, -98765L};

    private SelfTestSettlementSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "settlement: " + what);
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
    }

    private static void section(int i) {
        switch (i) {
            case 0: config(); break;
            case 1: registryNbt(); break;
            case 2: registryQueries(); break;
            case 3: sizesAndKinds(); break;
            case 4: names(); break;
            case 5: reputation(); break;
            case 6: violations(); break;
            case 7: layout(); break;
            case 8: trade(); break;
            case 9: contracts(); break;
            case 10: travel(); break;
            case 11: core(); break;
            case 12: texts(); break;
            case 13: wardenSuit(); break;
            default: handbook(); break;
        }
    }

    // ------------------------------------------------------------------ 1. the config (§2.1)

    private static void config() {
        Set<String> sent = new TreeSet<String>();
        for (Field f : SubstrateNetSC.ruleFields()) {
            sent.add(f.getName());
        }
        boolean defaults = SubstrateConfigSC.villages && !SubstrateConfigSC.guardsAggroOnViolations;
        boolean rules = sent.contains("villages") && sent.contains("guardsAggroOnViolations");
        check(defaults && rules, "config: dimension.villages " + SubstrateConfigSC.villages + " (true), guardsAggroOnViolations "
                + SubstrateConfigSC.guardsAggroOnViolations + " (false)" + (defaults ? "" : " (the test config edited?)") + "; both are rules SubstrateNetSC"
                + " sends a joining client " + rules);
    }

    // ------------------------------------------------------------------ 2. the registry (§3)

    /** A registry of four settlements (A with a dock, B across a region seam, C a city with a dock, D in another dimension) and two players. */
    private static SettlementRegistrySC sample() {
        SettlementRegistrySC r = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        SettlementRegistrySC.Settlement a = rec(DIM, 5, -700, 66, SettlementSizeSC.SETTLEMENT, 40, new int[]{53, 66, -700});
        a.first = true;
        a.madeAt = 12345L;
        a.points.put(SettlementRegistrySC.P_PLAZA, new int[]{5, 67, -695});
        a.points.put(SettlementRegistrySC.P_SQUARE, new int[]{5, 67, -690});
        a.points.put(SettlementRegistrySC.P_CLONE, new int[]{7, 67, -700});
        a.points.put(SettlementRegistrySC.P_PAD + "0", new int[]{0, 67, -690});
        a.points.put(SettlementRegistrySC.P_GATE, new int[]{40, 67, -700});
        a.points.put(SettlementRegistrySC.P_TOWER + "1", new int[]{-20, 75, -720});
        a.slots.add(slot(NpcKindSC.ELDER, 0, 1, SettlementRegistrySC.ALIVE, new UUID(11L, 22L), 0L, 900L));
        a.slots.add(slot(NpcKindSC.SENTRY, 5, 12, SettlementRegistrySC.DEAD, null, 500L, 400L));
        a.slots.add(slot(NpcKindSC.TECHNOLOGIST, 29, 999, SettlementRegistrySC.NEVER, null, 0L, 0L));
        SettlementRegistrySC.LocalRec l = a.local(P1);
        l.enemyUntil = 9000L;
        l.day = 4L;
        l.addCount("tech.wafers", 3);
        l.extra.setInteger("t", 1);
        a.extra.setTag("C", tag("lastClone", 7));
        r.add(a);
        r.add(rec(DIM, 511, 100, 70, SettlementSizeSC.OUTPOST, 24, null));
        r.add(rec(DIM, 2000, 2000, 71, SettlementSizeSC.CITY, 60, new int[]{2000, 71, 2068}));
        r.add(rec(OTHER_DIM, 5, -700, 64, SettlementSizeSC.OUTPOST, 24, null));
        SettlementRegistrySC.PlayerRec p = r.player(P1);
        p.reputation = 345;
        p.day = 4L;
        p.repTrades = 3;
        p.repKills = 5;
        p.chips = 17;
        p.contractsTaken = 2;
        p.week = 0L;
        p.crumbs = 1;
        p.firstPass = true;
        p.penanceDay = 3L;
        p.knownDocks.add(Integer.valueOf(3));
        p.knownDocks.add(Integer.valueOf(1));
        p.addGlobalCount("elder.crumb", 0L, 1);
        p.contracts.add(contract("killMarauders", 10, 4, 1, 4L, false));
        p.contracts.add(contract("returnCargo", 3, 3, 3, 3L, true));
        p.hasDeath = true;
        p.deathDim = DIM;
        p.deathX = 100;
        p.deathY = 65;
        p.deathZ = -200;
        p.deathAt = 7777L;
        p.extra.setTag("B", tag("y", 2));
        r.player(P2).reputation = -40;
        r.anchorBy = P1.toString();
        r.anchorPaid = true;
        r.extra.setTag("T", tag("z", 5));
        return r;
    }

    private static SettlementRegistrySC.Settlement rec(int dim, int x, int z, int floor, SettlementSizeSC size, int radius, int[] dock) {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.dim = dim;
        s.x = x;
        s.y = floor + 1;
        s.z = z;
        s.floorY = floor;
        s.size = size.ordinal();
        s.radius = radius;
        s.seed = x * 341873128712L + z * 132897987541L;
        s.nameElement = 3;
        s.nameNumber = 7;
        if (dock != null) {
            s.hasDock = true;
            s.dockX = dock[0];
            s.dockY = dock[1];
            s.dockZ = dock[2];
        }
        return s;
    }

    private static SettlementRegistrySC.Slot slot(NpcKindSC kind, int element, int number, int state, UUID id, long diedAt, long seenAt) {
        SettlementRegistrySC.Slot s = new SettlementRegistrySC.Slot();
        s.kind = kind.ordinal();
        s.nameElement = element;
        s.nameNumber = number;
        s.postX = 1 + element;
        s.postY = 67;
        s.postZ = -2 - number;
        s.homeX = -3;
        s.homeY = 68;
        s.homeZ = 4;
        s.state = state;
        if (id != null) {
            s.uuidMost = id.getMostSignificantBits();
            s.uuidLeast = id.getLeastSignificantBits();
        }
        s.diedAt = diedAt;
        s.seenAt = seenAt;
        return s;
    }

    private static SettlementRegistrySC.ContractRec contract(String key, int target, int progress, int settlement, long day, boolean done) {
        SettlementRegistrySC.ContractRec c = new SettlementRegistrySC.ContractRec();
        c.key = key;
        c.target = target;
        c.progress = progress;
        c.settlement = settlement;
        c.takenDay = day;
        c.done = done;
        return c;
    }

    private static NBTTagCompound tag(String key, int v) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger(key, v);
        return t;
    }

    /** The registry written and read into a fresh one (the index rebuilt from the NBT). */
    private static SettlementRegistrySC reloaded(SettlementRegistrySC r) {
        NBTTagCompound n = new NBTTagCompound();
        r.writeToNBT(n);
        SettlementRegistrySC back = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        back.readFromNBT(n);
        return back;
    }

    private static void registryNbt() {
        SettlementRegistrySC r = sample();
        SettlementRegistrySC b = reloaded(r);
        StringBuilder bad = new StringBuilder();
        if (b.count() != r.count()) {
            bad.append(" count ").append(b.count());
        }
        for (SettlementRegistrySC.Settlement s : r.all()) {
            SettlementRegistrySC.Settlement t = b.byId(s.id);
            String was = describe(s), now = t == null ? "missing" : describe(t);
            if (!was.equals(now)) {
                bad.append(" settlement ").append(s.id).append(": ").append(was).append(" -> ").append(now);
            }
            if (t != null && !(s.extra.equals(t.extra) && localsEqual(s, t))) {
                bad.append(" settlement ").append(s.id).append(" extra / locals");
            }
        }
        for (UUID id : new UUID[]{P1, P2}) {
            SettlementRegistrySC.PlayerRec p = r.playerIfAny(id), q = b.playerIfAny(id);
            String was = describe(p), now = q == null ? "missing" : describe(q);
            if (!was.equals(now)) {
                bad.append(" player ").append(id).append(": ").append(was).append(" -> ").append(now);
            }
        }
        boolean world = r.anchorBy.equals(b.anchorBy) && b.anchorPaid && r.extra.equals(b.extra);
        int next = b.add(rec(DIM, 9000, 9000, 66, SettlementSizeSC.OUTPOST, 24, null)).id;
        check(bad.length() == 0 && world && next == 5, "registry NBT there and back: " + r.count() + " settlements (slots, points, a local record,"
                + " extra), 2 players (contracts, daily / weekly counters, global deals, known docks, the death mark), the world's anchor " + world
                + ", the next id after loading " + next + " (5)" + (bad.length() == 0 ? "" : " - DIFFER:" + bad));

        // the slots' life, the day and the week (ПП-13, ПО-4, ПО-5)
        SettlementRegistrySC.Settlement a = b.byId(1);
        UUID born = new UUID(33L, 44L);
        b.slotAlive(1, 2, born, 900L);
        SettlementRegistrySC.Slot s2 = b.slot(1, 2);
        boolean alive = s2 != null && s2.state == SettlementRegistrySC.ALIVE && born.equals(s2.uuid()) && s2.seenAt == 900L;
        b.slotDied(1, 0, 1000L);
        SettlementRegistrySC.Slot s0 = b.slot(1, 0);
        boolean died = s0 != null && s0.state == SettlementRegistrySC.DEAD && s0.diedAt == 1000L && s0.uuid() == null;
        boolean none = b.slot(1, 99) == null && b.slot(999, 0) == null && b.slot(1, -1) == null;
        boolean slotsOf = a != null && a.slotsOf(NpcKindSC.SENTRY).size() == 1 && a.slotsOf(NpcKindSC.SENTRY).get(0).intValue() == 1
                && a.slotsOf(NpcKindSC.CARTOGRAPHER).isEmpty() && a.slots.get(2).nameCode() == NpcNamesSC.code(29, 999);
        SettlementRegistrySC.PlayerRec p = b.player(P1);
        boolean sameDay = !p.rollDay(4L) && p.repTrades == 3 && p.chips == 17;
        boolean newDay = p.rollDay(5L) && p.repTrades == 0 && p.repKills == 0 && p.chips == 0 && p.contractsTaken == 0 && p.reputation == 345;
        boolean week = !p.rollWeek(0L) && p.crumbs == 1 && p.rollWeek(1L) && p.crumbs == 0;
        boolean global = p.globalCount("elder.crumb", 0L) == 1 && p.globalCount("elder.crumb", 1L) == 0 && p.globalCount("none", 0L) == 0;
        p.addGlobalCount("elder.crumb", 1L, 1);
        global &= p.globalCount("elder.crumb", 1L) == 1 && p.globalCount("elder.crumb", 0L) == 0;
        boolean open = p.open().size() == 1 && "killMarauders".equals(p.open().get(0).key);
        p.pruneContracts(10L, 3);
        boolean pruned = p.contracts.size() == 1 && !p.contracts.get(0).done;
        SettlementRegistrySC.LocalRec l = a == null ? null : a.localIfAny(P1);
        boolean local = l != null && !l.rollDay(4L) && l.count("tech.wafers") == 3 && l.rollDay(5L) && l.count("tech.wafers") == 0 && l.enemy(8999L)
                && !l.enemy(9000L) && a.localIfAny(P2) == null && a.localIfAny(null) == null;
        boolean players = b.playerIfAny(null) == null && b.playerIfAny(UUID.randomUUID()) == null && SettlementRegistrySC.uuid("nonsense") == null
                && P1.equals(SettlementRegistrySC.uuid(P1.toString()));
        check(alive && died && none && slotsOf && sameDay && newDay && week && global && open && pruned && local && players, "registry records: slotAlive "
                + alive + ", slotDied (the UUID dropped) " + died + ", no slot out of range " + none + ", slotsOf / nameCode " + slotsOf + "; PlayerRec"
                + " rollDay same day keeps " + sameDay + ", a new day clears the daily counters (not the standing) " + newDay + ", rollWeek " + week
                + ", global deals by period " + global + ", open contracts " + open + ", prune " + pruned + "; LocalRec rollDay / enemy " + local
                + "; look-ups that create nothing " + players);
    }

    private static String describe(SettlementRegistrySC.Settlement s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.id).append('/').append(s.dim).append('/').append(s.x).append(',').append(s.y).append(',').append(s.z).append('/').append(s.floorY)
                .append('/').append(s.size).append('/').append(s.radius).append('/').append(s.seed).append('/').append(s.nameElement).append('-')
                .append(s.nameNumber).append('/').append(s.first).append('/').append(s.hasDock);
        if (s.hasDock) {
            sb.append(':').append(s.dockX).append(',').append(s.dockY).append(',').append(s.dockZ);
        }
        sb.append('/').append(s.version).append('/').append(s.madeAt).append(" points");
        for (String k : new TreeSet<String>(s.points.keySet())) {                // NBT keeps no order of a compound's keys
            sb.append(' ').append(k).append(Arrays.toString(s.points.get(k)));
        }
        sb.append(" slots");
        for (SettlementRegistrySC.Slot t : s.slots) {
            sb.append(" [").append(t.kind).append(' ').append(t.nameElement).append('-').append(t.nameNumber).append(' ').append(t.postX).append(',')
                    .append(t.postY).append(',').append(t.postZ).append(' ').append(t.homeX).append(',').append(t.homeY).append(',').append(t.homeZ)
                    .append(' ').append(t.state).append(' ').append(t.uuid()).append(' ').append(t.diedAt).append(' ').append(t.seenAt).append(']');
        }
        return sb.toString();
    }

    private static boolean localsEqual(SettlementRegistrySC.Settlement a, SettlementRegistrySC.Settlement b) {
        if (a.locals.size() != b.locals.size()) {
            return false;
        }
        for (Map.Entry<UUID, SettlementRegistrySC.LocalRec> e : a.locals.entrySet()) {
            SettlementRegistrySC.LocalRec x = e.getValue(), y = b.locals.get(e.getKey());
            if (y == null || x.enemyUntil != y.enemyUntil || x.day != y.day || !x.counts.equals(y.counts) || !x.extra.equals(y.extra)) {
                return false;
            }
        }
        return true;
    }

    private static String describe(SettlementRegistrySC.PlayerRec p) {
        if (p == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(p.reputation).append('/').append(p.day).append('/').append(p.repTrades).append(',').append(p.repKills).append(',').append(p.chips)
                .append(',').append(p.contractsTaken).append('/').append(p.week).append(',').append(p.crumbs).append('/').append(p.firstPass).append('/')
                .append(p.penanceDay).append('/').append(p.knownDocks).append('/').append(p.globalCounts).append('/').append(p.hasDeath);
        if (p.hasDeath) {
            sb.append(':').append(p.deathDim).append(',').append(p.deathX).append(',').append(p.deathY).append(',').append(p.deathZ).append('@').append(p.deathAt);
        }
        for (SettlementRegistrySC.ContractRec c : p.contracts) {
            sb.append(" [").append(c.key).append(' ').append(c.target).append(' ').append(c.progress).append(' ').append(c.settlement).append(' ')
                    .append(c.takenDay).append(' ').append(c.done).append(']');
        }
        return sb.append(' ').append(p.extra).toString();
    }

    private static void registryQueries() {
        SettlementRegistrySC r = reloaded(sample());
        SettlementRegistrySC.Settlement a = r.byId(1), b = r.byId(2), c = r.byId(3), d = r.byId(4);
        if (a == null || b == null || c == null || d == null) {
            check(false, "registry queries: the sample didn't load back (" + r.count() + " records)");
            return;
        }
        double ax = a.x + 0.5D, az = a.z + 0.5D;
        // ПП-7: the cylinder - the radius across, from floor - FOUNDATION up
        boolean cyl = r.protecting(DIM, ax + 39.9D, a.floorY - SettlementSC.FOUNDATION, az) == a
                && r.protecting(DIM, ax + 40.1D, a.floorY + 1, az) == null
                && r.protecting(DIM, ax, a.floorY - SettlementSC.FOUNDATION - 0.01D, az) == null
                && r.protecting(DIM, ax, 250D, az) == a && r.protecting(OTHER_DIM, ax, a.floorY + 1, az) == d;
        // ПЖ-2: the belt, any height
        boolean belt = r.inBelt(DIM, ax + 40 + SettlementSC.BELT - 0.1D, az) == a && r.inBelt(DIM, ax + 40 + SettlementSC.BELT + 0.1D, az) == null
                && r.inBelt(DIM, ax, az - 63.9D) == a;
        // ПР-1: the dock's zone - DOCK_ZONE across, DOCK_BELOW under to DOCK_ABOVE over the beacon
        double dx = a.dockX + 0.5D, dz = a.dockZ + 0.5D;
        boolean dock = r.dockZoneAt(DIM, dx, a.dockY + SettlementSC.DOCK_ABOVE, dz) == a && r.dockZoneAt(DIM, dx, a.dockY + SettlementSC.DOCK_ABOVE + 0.01D, dz) == null
                && r.dockZoneAt(DIM, dx, a.dockY - SettlementSC.DOCK_BELOW, dz) == a && r.dockZoneAt(DIM, dx, a.dockY - SettlementSC.DOCK_BELOW - 0.01D, dz) == null
                && r.dockZoneAt(DIM, dx + 4.9D, a.dockY, dz) == a && r.dockZoneAt(DIM, dx + 5.1D, a.dockY, dz) == null
                && r.dockZoneAt(DIM, ax, a.floorY + 1, az) == null && r.protecting(DIM, dx, a.dockY + 1, dz) == null;
        // ПП-8: a player's field box against the cylinder's box
        boolean box = r.overlapping(DIM, AxisAlignedBB.getBoundingBox(ax + 39D, 60D, az, ax + 45D, 70D, az + 3D)) == a
                && r.overlapping(DIM, AxisAlignedBB.getBoundingBox(ax + 40D, 60D, az, ax + 45D, 70D, az + 3D)) == null
                && r.overlapping(DIM, AxisAlignedBB.getBoundingBox(ax - 3D, 0D, az - 3D, ax + 3D, a.floorY - SettlementSC.FOUNDATION, az + 3D)) == null;
        // ПП-11: within the spawn margin of the edge
        boolean within = r.within(DIM, ax + 40 + SettlementSC.SPAWN_MARGIN - 0.1D, az, SettlementSC.SPAWN_MARGIN) == a
                && r.within(DIM, ax + 40 + SettlementSC.SPAWN_MARGIN + 0.1D, az, SettlementSC.SPAWN_MARGIN) == null;
        // ПР-3, ПР-5: the docks nearest, skipId
        List<SettlementRegistrySC.Settlement> n0 = r.nearestDocks(DIM, 0D, 0D, 5, -1), n1 = r.nearestDocks(DIM, 2000D, 2000D, 1, -1),
                n2 = r.nearestDocks(DIM, 0D, 0D, 5, a.id), n3 = r.nearestDocks(OTHER_DIM, 0D, 0D, 5, -1);
        boolean nearest = n0.size() == 2 && n0.get(0) == a && n0.get(1) == c && n1.size() == 1 && n1.get(0) == c && n2.size() == 1 && n2.get(0) == c
                && n3.isEmpty() && r.dockNear(DIM, a.dockX + 3, a.dockZ, TravelRulesSC.DOCK_SNAP) == a && r.dockNear(DIM, a.dockX + 9, a.dockZ, TravelRulesSC.DOCK_SNAP) == null;
        // the index: negative coordinates and a region seam (B's core at x 511 - its dome reaches region 1)
        boolean index = r.protecting(DIM, -25.5D, 70D, az) == a && r.candidates(DIM, -25, a.z).contains(a)
                && r.protecting(DIM, 530.5D, 71D, 100.5D) == b && r.candidates(DIM, 530, 100).contains(b) && r.candidates(DIM, 490, 100).contains(b)
                && r.candidates(DIM, 5000, 5000).isEmpty();
        boolean find = r.atCentre(DIM, a.x, a.z) == a && r.atCentre(DIM, a.x + 1, a.z) == null && r.atCentre(OTHER_DIM, a.x, a.z) == d && r.first(DIM) == a
                && r.first(OTHER_DIM) == null && a.owner().equals(SettlementSC.ownerName(a.id)) && SettlementSC.isSettlementOwner(a.owner())
                && !SettlementSC.isSettlementOwner("Steve") && !SettlementSC.isSettlementOwner(null);
        check(cyl && belt && dock && box && within && nearest && index && find, "registry queries (on the reloaded copy): the protected cylinder to the"
                + " radius and from floor - " + SettlementSC.FOUNDATION + " up, per dimension " + cyl + ", the belt r + " + SettlementSC.BELT + " " + belt
                + ", the dock's zone " + SettlementSC.DOCK_ZONE + " across, -" + SettlementSC.DOCK_BELOW + " .. +" + SettlementSC.DOCK_ABOVE + " (outside"
                + " the cylinder) " + dock + ", a field's box " + box + ", within(" + SettlementSC.SPAWN_MARGIN + ") " + within + ", the docks nearest"
                + " (order, n, skipId, dockNear " + TravelRulesSC.DOCK_SNAP + ") " + nearest + ", the region index at negative x and across a seam " + index
                + ", atCentre / first / the core field's owner " + find);
        lostRegistry();
    }

    /**
     * Package S / C after a lost file (§3, §5.2): reserveIds keeps add() past the ids the structure starts hold; a creature
     * bound to another core under the same id is no record's (RosterSC.ofCore); a record whose land is made again
     * (MapGenSettlementSC.renew) gets the plan's slots, all NEVER, the Clone Capsule's day gone, its name and locals kept.
     */
    private static void lostRegistry() {
        SettlementRegistrySC r = new SettlementRegistrySC(SettlementRegistrySC.NAME);
        int a = r.add(rec(DIM, 0, 0, 64, SettlementSizeSC.OUTPOST, 24, null)).id;
        r.reserveIds(7);
        int b = r.add(rec(DIM, 600, 0, 64, SettlementSizeSC.OUTPOST, 24, null)).id;
        r.reserveIds(3);
        int c = r.add(rec(DIM, 1200, 0, 64, SettlementSizeSC.OUTPOST, 24, null)).id;
        boolean ids = a == 1 && b == 8 && c == 9;
        SettlementRegistrySC.Settlement s = r.byId(b);
        boolean core = com.sc.settle.RosterSC.ofCore(member(b, new int[]{600, 0}), s) && com.sc.settle.RosterSC.ofCore(member(b, null), s)
                && !com.sc.settle.RosterSC.ofCore(member(b, new int[]{0, 0}), s) && !com.sc.settle.RosterSC.ofCore(member(a, new int[]{600, 0}), s);
        boolean renew = false;
        String why = "no First Dock for seed " + SEEDS[0];
        SettlementLayoutSC.Site site = SettlementLayoutSC.site(SEEDS[0], 0, 0);
        if (site != null) {
            SettlementLayoutSC.Plan p = SettlementLayoutSC.plan(SEEDS[0], site);
            SettlementRegistrySC.Settlement old = rec(DIM, p.coreX, p.coreZ, p.floorY, SettlementSizeSC.SETTLEMENT, p.radius, null);
            old.id = 5;
            old.slots.add(slot(NpcKindSC.SENTRY, 1, 2, SettlementRegistrySC.ALIVE, new UUID(5L, 6L), 0L, 100L));
            old.slots.add(slot(NpcKindSC.ELDER, 2, 3, SettlementRegistrySC.DEAD, null, 4000L, 100L));
            NBTTagCompound cc = new NBTTagCompound();
            cc.setLong(com.sc.settle.RosterSC.CLONE_DAY, 3L);
            old.extra.setTag(com.sc.settle.RosterSC.EXTRA, cc);
            old.local(P1).enemyUntil = 777L;
            com.sc.settle.gen.MapGenSettlementSC.renew(old, p, 9000L);
            boolean never = old.slots.size() == p.posts.size() && !old.slots.isEmpty();
            for (SettlementRegistrySC.Slot sl : old.slots) {
                never &= sl.state == SettlementRegistrySC.NEVER && sl.uuid() == null && sl.diedAt == 0L;
            }
            renew = never && old.id == 5 && old.nameElement == 3 && old.nameNumber == 7 && old.localIfAny(P1) != null
                    && old.localIfAny(P1).enemyUntil == 777L && !old.extra.getCompoundTag(com.sc.settle.RosterSC.EXTRA).hasKey(com.sc.settle.RosterSC.CLONE_DAY)
                    && old.madeAt == 9000L && old.radius == p.radius && old.hasDock == p.dock && old.points.size() == p.pointNames.size();
            why = old.slots.size() + " slots of the plan's " + p.posts.size() + " (all NEVER " + never + ")";
        }
        check(ids && core && renew, "a lost registry (§3, §5.2): ids after reserveIds(7) / (3) " + a + ", " + b + ", " + c + " (1, 8, 9) " + ids
                + "; a creature bound to another core under the same id is not the record's, one of no known core is " + core
                + "; a record on new ground (renew) - " + why + ", id, name and the local «Enemy» kept, the capsule's day gone " + renew);
    }

    /** A settlement creature of id `id` bound to the core column `core` (null: unknown) - RosterSC.ofCore's view of it. */
    private static com.sc.settle.ISettlementMemberSC member(final int id, final int[] core) {
        return new com.sc.settle.ISettlementMemberSC() {
            @Override
            public int settlementId() {
                return id;
            }

            @Override
            public int[] boundCore() {
                return core;
            }

            @Override
            public int slot() {
                return 0;
            }

            @Override
            public NpcKindSC kind() {
                return NpcKindSC.SENTRY;
            }

            @Override
            public int nameCode() {
                return 0;
            }

            @Override
            public void bindTo(SettlementRegistrySC.Settlement s, int slot) {
            }
        };
    }

    // ------------------------------------------------------------------ 3. sizes, kinds, names (§2.3, §6.1)

    private static void sizesAndKinds() {
        boolean table = size(SettlementSizeSC.OUTPOST, 24, 24, 3, 5, 2, 0, false) && size(SettlementSizeSC.SETTLEMENT, 40, 40, 8, 12, 3, 2, true)
                && size(SettlementSizeSC.CITY, 56, 64, 15, 20, 4, 3, true) && SettlementSizeSC.SUBSTRATE_SIZES == 3   // Ф6: + EMBASSY (SelfTestEmbassySC)
                && SettlementSizeSC.of(-1) == SettlementSizeSC.OUTPOST && SettlementSizeSC.of(9) == SettlementSizeSC.OUTPOST
                && SettlementSizeSC.CITY.guards() == 7 && "sc.settle.size.city".equals(SettlementSizeSC.CITY.langKey());
        Random rand = new Random(14L);
        boolean inBounds = true, cityLo = false, cityHi = false;
        for (int i = 0; i < 4000; i++) {
            long seed = rand.nextLong();
            for (SettlementSizeSC s : SettlementSizeSC.values()) {
                int r = s.radius(seed);
                inBounds &= r >= s.radiusMin && r <= s.radiusMax;
                cityLo |= s == SettlementSizeSC.CITY && r == s.radiusMin;
                cityHi |= s == SettlementSizeSC.CITY && r == s.radiusMax;
            }
        }
        String[] keys = {"elder", "technologist", "metallurgist", "gasTech", "cartographer", "sentry", "seniorWarden"};
        NpcKindSC[] kinds = NpcKindSC.values();
        boolean order = kinds.length >= keys.length;                      // Ф3 appended five (SelfTestCombatSC checks all twelve)
        for (int i = 0; order && i < keys.length; i++) {
            order = keys[i].equals(kinds[i].key) && kinds[i].guard == (i >= 5) && ("sc.settle.kind." + keys[i]).equals(kinds[i].langKey());
        }
        boolean respawn = NpcKindSC.ELDER.respawnTicks() == 24000 && NpcKindSC.CARTOGRAPHER.respawnTicks() == 24000 && NpcKindSC.SENTRY.respawnTicks() == 6000
                && NpcKindSC.SENIOR_WARDEN.respawnTicks() == 6000 && NpcKindSC.of(-1) == NpcKindSC.ELDER && NpcKindSC.of(99) == NpcKindSC.ELDER
                && NpcKindSC.of(5) == NpcKindSC.SENTRY;
        boolean consts = SettlementSC.BELT == 24 && SettlementSC.FOUNDATION == 6 && SettlementSC.DOCK_ZONE == 5 && SettlementSC.DOCK_BELOW == 3
                && SettlementSC.DOCK_ABOVE == 16 && SettlementSC.SPAWN_MARGIN == 40 && SettlementSC.RESPAWN_CLEAR == 48 && SettlementSC.KILL_REACH == 48
                && SettlementSC.DAY == 24000L && SettlementSC.WEEK_DAYS == 7 && SettlementSC.ownerName(7).equals(SettlementSC.OWNER_PREFIX + "7]");
        check(table && inBounds && cityLo && cityHi && order && respawn && consts, "sizes and kinds (proposal §5): Outpost 24 / 3-5 / 2 Sentries,"
                + " Settlement 40 / 8-12 / 3 + 2 / dock, City 56-64 / 15-20 / 4 + 3 / dock " + table + ", radius(seed) within bounds over 4000 seeds "
                + inBounds + " (the City's both ends met " + (cityLo && cityHi) + "); NpcKindSC's Ф2 order, keys and guard flags " + order + ", respawnTicks"
                + " 24000 / 6000, of() never throws " + respawn + "; SettlementSC's numbers (belt 24, foundation 6, dock zone 5 / -3 / +16, spawn margin 40,"
                + " clear 48, kill reach 48, the day) " + consts);
    }

    private static boolean size(SettlementSizeSC s, int rMin, int rMax, int tMin, int tMax, int sentries, int seniors, boolean dock) {
        return s.radiusMin == rMin && s.radiusMax == rMax && s.tunersMin == tMin && s.tunersMax == tMax && s.sentries == sentries && s.seniors == seniors
                && s.dock == dock;
    }

    private static void names() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        Set<String> unique = new HashSet<String>(Arrays.asList(NpcNamesSC.ELEMENTS));
        boolean count = NpcNamesSC.ELEMENTS.length == 30 && unique.size() == 30 && NpcNamesSC.MAX_NUMBER == 99;
        boolean code = NpcNamesSC.code(0, 1) == 1001 && NpcNamesSC.code(29, 999) == 30999 && NpcNamesSC.code(30, 1) == 0 && NpcNamesSC.code(-1, 5) == 0
                && NpcNamesSC.code(0, 0) == 0 && NpcNamesSC.code(0, 1000) == 0;
        boolean back = NpcNamesSC.element(1001) == 0 && NpcNamesSC.number(1001) == 1 && NpcNamesSC.element(0) == -1 && NpcNamesSC.number(0) == 0
                && NpcNamesSC.element(31001) == -1 && NpcNamesSC.element(999) == -1 && NpcNamesSC.number(-5) == 0;
        for (int e = 0; back && e < NpcNamesSC.ELEMENTS.length; e++) {
            for (int n = 1; n <= 999; n += 37) {
                int c = NpcNamesSC.code(e, n);
                back = NpcNamesSC.element(c) == e && NpcNamesSC.number(c) == n;
            }
        }
        Random rand = new Random(3L);
        boolean random = true;
        for (int i = 0; i < 2000; i++) {
            int c = NpcNamesSC.random(rand);
            random &= NpcNamesSC.element(c) >= 0 && NpcNamesSC.number(c) >= 1 && NpcNamesSC.number(c) <= NpcNamesSC.MAX_NUMBER;
        }
        StringBuilder missing = new StringBuilder();
        for (String e : NpcNamesSC.ELEMENTS) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey("sc.settle.element." + e)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(e);
                }
            }
        }
        IChatComponent named = NpcNamesSC.name(NpcNamesSC.code(1, 7)), none = NpcNamesSC.name(0);
        boolean chat = named instanceof ChatComponentTranslation && "sc.settle.npcName".equals(((ChatComponentTranslation) named).getKey())
                && none instanceof ChatComponentTranslation && "sc.settle.npcName.none".equals(((ChatComponentTranslation) none).getKey());
        check(count && code && back && random && missing.length() == 0 && chat, "names (ПЖ-6): " + NpcNamesSC.ELEMENTS.length + " unique elements (30),"
                + " numbers to " + NpcNamesSC.MAX_NUMBER + " " + count + ", code = (element + 1) x 1000 + number, bad pairs 0 " + code + ", element / number"
                + " back from every code, 0 - no name " + back + ", random() always valid " + random + ", a chat component the client translates "
                + "(sc.settle.npcName / .none) " + chat + (missing.length() == 0 ? "" : " - NO LANG:" + missing));
    }

    // ------------------------------------------------------------------ 4. «Доверие» and violations (§5.4, §7)

    private static void reputation() {
        int[] at = {-1, 0, 99, 100, 299, 300, 599, 600, 999, 1000, 4999};
        int[] want = {ReputationSC.VIOLATOR, ReputationSC.STRANGER, ReputationSC.STRANGER, ReputationSC.ACQUAINTANCE, ReputationSC.ACQUAINTANCE,
                ReputationSC.PARTNER, ReputationSC.PARTNER, ReputationSC.OWN, ReputationSC.OWN, ReputationSC.HONOURED, ReputationSC.HONOURED};
        boolean levels = ReputationSC.localLevel(500, true) == ReputationSC.ENEMY && ReputationSC.localLevel(500, false) == ReputationSC.PARTNER
                && ReputationSC.LEVEL_KEYS.length == 7;
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < at.length; i++) {
            if (ReputationSC.level(at[i]) != want[i]) {
                wrong.append(' ').append(at[i]).append("->").append(ReputationSC.level(at[i]));
            }
        }
        float[] mul = {0F, 2F, 1.5F, 1.25F, 1F, 0.9F, 0.85F};
        boolean prices = true;
        for (int lv = 0; lv < mul.length; lv++) {
            prices &= Math.abs(ReputationSC.priceMultiplier(lv) - mul[lv]) < 1e-5F;
        }
        boolean trade = !ReputationSC.buys(ReputationSC.ENEMY) && !ReputationSC.buys(ReputationSC.VIOLATOR) && ReputationSC.buys(ReputationSC.STRANGER)
                && !ReputationSC.sells(ReputationSC.ENEMY) && ReputationSC.sells(ReputationSC.VIOLATOR) && ReputationSC.sells(ReputationSC.HONOURED);
        // give: the caps of the day, a new day, clamp
        SettlementRegistrySC.PlayerRec p = new SettlementRegistrySC.PlayerRec();
        int deals = 0;
        for (int i = 0; i < 25; i++) {
            deals += ReputationSC.give(p, ReputationSC.TRADE, ReputationSC.SRC_TRADE, 10L);
        }
        int nextDay = ReputationSC.give(p, ReputationSC.TRADE, ReputationSC.SRC_TRADE, 11L);
        SettlementRegistrySC.PlayerRec k = new SettlementRegistrySC.PlayerRec();
        int small = 0, big = 0;
        for (int i = 0; i < 9; i++) {
            small += ReputationSC.give(k, 3, ReputationSC.SRC_KILL_SMALL, 1L);
        }
        for (int i = 0; i < 3; i++) {
            big += ReputationSC.give(k, 25, ReputationSC.SRC_KILL_BIG, 1L);
        }
        SettlementRegistrySC.PlayerRec c = new SettlementRegistrySC.PlayerRec();
        int up = ReputationSC.give(c, 10000, ReputationSC.SRC_CONTRACT, 1L), top = c.reputation;
        int down = ReputationSC.give(c, -100000, ReputationSC.SRC_PENALTY, 1L), bottom = c.reputation;
        SettlementRegistrySC.PlayerRec n = new SettlementRegistrySC.PlayerRec();
        int fine = ReputationSC.give(n, -10, ReputationSC.SRC_TRADE, 1L);
        boolean give = deals == ReputationSC.TRADE_DAY_CAP && p.reputation == 21 && nextDay == 1 && small == ReputationSC.KILL_SMALL_DAY_CAP && big == 75
                && up == ReputationSC.MAX && top == ReputationSC.MAX && down == ReputationSC.MIN - ReputationSC.MAX && bottom == ReputationSC.MIN
                && fine == -10 && n.repTrades == 0 && ReputationSC.capped(15, 10, 20) == 5 && ReputationSC.capped(25, 10, 20) == 0
                && ReputationSC.capped(0, -4, 20) == -4;
        boolean warden = ReputationSC.afterWardenKill(100) == 36 && ReputationSC.afterWardenKill(50) == -10 && ReputationSC.afterWardenKill(0) == -60
                && ReputationSC.afterWardenKill(-990) == ReputationSC.MIN;
        boolean kill = ReputationSC.killReward(null) == 1 && ReputationSC.killReward(ArmorSuit.NANO) == 1 && ReputationSC.killReward(ArmorSuit.QUANTUM) == 3
                && ReputationSC.killReward(ArmorSuit.EXO) == 8 && ReputationSC.killReward(ArmorSuit.SINGULAR) == 25 && ReputationSC.smallKill(ArmorSuit.NANO)
                && ReputationSC.smallKill(ArmorSuit.QUANTUM) && !ReputationSC.smallKill(ArmorSuit.EXO) && !ReputationSC.smallKill(ArmorSuit.SINGULAR);
        boolean nums = ReputationSC.PASS == 20 && ReputationSC.PASS_CHIPS == 8 && ReputationSC.PENANCE == 50 && ReputationSC.PENANCE_CHIPS == 16
                && ReputationSC.CARGO == 15 && ReputationSC.ENEMY_BELOW == -50 && ReputationSC.ENEMY_TICKS == 3L * SettlementSC.DAY && ReputationSC.ANCHOR == 50
                && ReputationSC.ANCHOR_CHIPS == 20;
        check(levels && wrong.length() == 0 && prices && trade && give && warden && kill && nums, "reputation (ПД-1..ПД-5): the levels on their borders"
                + " (-1, 0, 99, 100 ... 1000)" + (wrong.length() == 0 ? "" : " WRONG:" + wrong) + ", the local Enemy wins " + levels + ", the multipliers"
                + " x2 / 1.5 / 1.25 / 1 / 0.9 / 0.85 " + prices + ", a Violator only sells " + trade + "; give: 25 deals -> +" + deals + " (cap "
                + ReputationSC.TRADE_DAY_CAP + "), a new day +" + nextDay + ", small kills +" + small + " (cap " + ReputationSC.KILL_SMALL_DAY_CAP
                + "), big ones uncapped +" + big + ", clamp to " + ReputationSC.MIN + ".." + ReputationSC.MAX + " " + give + "; afterWardenKill 100 -> "
                + ReputationSC.afterWardenKill(100) + ", 50 -> " + ReputationSC.afterWardenKill(50) + ", 0 -> " + ReputationSC.afterWardenKill(0)
                + " (36, -10, -60) " + warden + "; killReward by suit 1 / 3 / 8 / 25 " + kill + "; the pass, penance, cargo, enemy, anchor numbers " + nums);
    }

    private static void violations() {
        boolean stages = ViolationRulesSC.stage(0) == 1 && ViolationRulesSC.stage(1) == 1 && ViolationRulesSC.stage(2) == 2 && ViolationRulesSC.stage(3) == 3
                && ViolationRulesSC.stage(9) == 3;
        boolean penalties = ViolationRulesSC.penalty(ViolationRulesSC.BREAK, 1) == 0 && ViolationRulesSC.penalty(ViolationRulesSC.PLACE, 2) == -5
                && ViolationRulesSC.penalty(ViolationRulesSC.USE, 3) == -10 && ViolationRulesSC.penalty(ViolationRulesSC.FIRE, 7) == -10
                && ViolationRulesSC.penalty(ViolationRulesSC.HIT_TUNER, 1) == -10 && ViolationRulesSC.penalty(ViolationRulesSC.HIT_WARDEN, 1) == -40
                && ViolationRulesSC.penalty(ViolationRulesSC.POINT_WARDEN, 3) == -40 && ViolationRulesSC.penalty(ViolationRulesSC.KILL_WARDEN, 1) == -60
                && ViolationRulesSC.penalty(99, 1) == 0;
        boolean provokes = !ViolationRulesSC.provokes(ViolationRulesSC.BREAK, 3, false) && ViolationRulesSC.provokes(ViolationRulesSC.BREAK, 3, true)
                && !ViolationRulesSC.provokes(ViolationRulesSC.PLACE, 2, true) && !ViolationRulesSC.provokes(ViolationRulesSC.FIRE, 1, true)
                && ViolationRulesSC.provokes(ViolationRulesSC.HIT_TUNER, 1, false) && ViolationRulesSC.provokes(ViolationRulesSC.HIT_WARDEN, 1, false)
                && ViolationRulesSC.provokes(ViolationRulesSC.POINT_WARDEN, 1, false) && ViolationRulesSC.provokes(ViolationRulesSC.KILL_WARDEN, 1, false);
        boolean aggro = ViolationRulesSC.aggroTicks(ViolationRulesSC.BREAK) == 1200 && ViolationRulesSC.aggroTicks(ViolationRulesSC.HIT_TUNER) == 6000
                && ViolationRulesSC.blockKind(ViolationRulesSC.FIRE) && !ViolationRulesSC.blockKind(ViolationRulesSC.HIT_TUNER);
        boolean again = ViolationRulesSC.chargesAgain(0L, 100L) && !ViolationRulesSC.chargesAgain(100L, 200L) && !ViolationRulesSC.chargesAgain(100L, 6099L)
                && ViolationRulesSC.chargesAgain(100L, 6100L) && ViolationRulesSC.chargesAgain(500L, 100L);
        boolean consts = ViolationRulesSC.KEYS.length == 8 && ViolationRulesSC.WINDOW_TICKS == 1200 && ViolationRulesSC.WARN_TICKS == 40
                && ViolationRulesSC.WALK_AWAY == 12 && ViolationRulesSC.AGGRO_TICKS == 6000 && ViolationRulesSC.VIOLATION_AGGRO_TICKS == 1200
                && ViolationRulesSC.LEASH == 48 && ViolationRulesSC.GUARD_REACH == 96;
        check(stages && penalties && provokes && aggro && again && consts, "violations (ПП-10, ПГ-6, ПГ-7): stages 1 / 2 / 3+ " + stages + ", penalties"
                + " 0 / -5 / -10, a settler -10, a guard -40, a kill -60 " + penalties + ", provokes: blows always, the 3rd block violation only with"
                + " guardsAggroOnViolations " + provokes + ", 60 s / 5 min " + aggro + ", a blow at a guard charged once per provocation " + again
                + ", the numbers (2 s warning, 12 blocks, leash 48, reach 96) " + consts);
    }

    // ------------------------------------------------------------------ 5. the layout (package S, §4.1-§4.3)

    private static void layout() {
        StringBuilder bad = new StringBuilder();
        int regions = 0, with = 0, plans = 0;
        int[] sizes = new int[3];
        double nearest = Double.MAX_VALUE;
        for (long seed : SEEDS) {
            SettlementLayoutSC.Site s0 = SettlementLayoutSC.site(seed, 0, 0);
            if (s0 == null) {
                bad.append(" seed ").append(seed).append(": no First Dock");
            } else {
                double dist = Math.hypot(s0.x + 0.5D - SettlementLayoutSC.CELL / 2D, s0.z + 0.5D - SettlementLayoutSC.CELL / 2D);
                int midX = SettlementLayoutSC.FIRST_CELL_X * SettlementLayoutSC.CELL + SettlementLayoutSC.CELL / 2;
                int midZ = SettlementLayoutSC.FIRST_CELL_Z * SettlementLayoutSC.CELL + SettlementLayoutSC.CELL / 2;
                if (!s0.first || s0.size != SettlementSizeSC.SETTLEMENT || Math.abs(s0.x - midX) > SettlementLayoutSC.JITTER
                        || Math.abs(s0.z - midZ) > SettlementLayoutSC.JITTER || dist > 300D) {
                    bad.append(" seed ").append(seed).append(": the First Dock at ").append(s0.x).append(", ").append(s0.z).append(' ').append(s0.size)
                            .append(" first ").append(s0.first).append(" ").append((int) dist).append(" from the spawn");
                }
            }
            Map<Long, SettlementLayoutSC.Site> sites = new HashMap<Long, SettlementLayoutSC.Site>();
            for (int rx = -32; rx < 32; rx++) {
                for (int rz = -32; rz < 32; rz++) {
                    SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, rx, rz);
                    regions++;
                    if (s == null) {
                        continue;
                    }
                    with++;
                    sizes[s.size.ordinal()]++;
                    sites.put(Long.valueOf((long) rx << 32 ^ rz & 0xFFFFFFFFL), s);
                    SettlementLayoutSC.Site again = SettlementLayoutSC.site(seed, rx, rz);
                    if (again == null || again.x != s.x || again.z != s.z || again.size != s.size || again.radius != s.radius || again.seed != s.seed
                            || again.first != s.first) {
                        bad.append(" not deterministic at ").append(rx).append(',').append(rz);
                    }
                    if (SettlementLayoutSC.regionOf(s.x) != rx || SettlementLayoutSC.regionOf(s.z) != rz || s.first != (rx == 0 && rz == 0)
                            || s.x >> 8 == 0 && s.z >> 8 == 0 || s.radius < s.size.radiusMin || s.radius > s.size.radiusMax) {
                        bad.append(" site ").append(rx).append(',').append(rz).append(" at ").append(s.x).append(',').append(s.z).append(" r").append(s.radius);
                    }
                    if (SettlementLayoutSC.siteAtChunk(seed, s.x >> 4, s.z >> 4) == null || SettlementLayoutSC.siteAtChunk(seed, (s.x >> 4) + 1, s.z >> 4) != null) {
                        bad.append(" siteAtChunk ").append(rx).append(',').append(rz);
                    }
                    if (rx >= -8 && rx < 8 && rz >= -8 && rz < 8) {
                        plans++;
                        String why = planProblem(seed, s);
                        if (why != null && bad.length() < 3000) {
                            bad.append(" region ").append(rx).append(',').append(rz).append(": ").append(why);
                        }
                    }
                }
            }
            for (Map.Entry<Long, SettlementLayoutSC.Site> e : sites.entrySet()) {
                int rx = (int) (e.getKey().longValue() >> 32), rz = (int) e.getKey().longValue();
                SettlementLayoutSC.Site s = e.getValue();
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        SettlementLayoutSC.Site t = (ox | oz) == 0 ? null : sites.get(Long.valueOf((long) (rx + ox) << 32 ^ rz + oz & 0xFFFFFFFFL));
                        if (t != null) {
                            nearest = Math.min(nearest, Math.hypot(s.x - t.x, s.z - t.z));
                        }
                    }
                }
            }
        }
        double share = 100D * with / Math.max(1, regions);
        double[] got = new double[3];
        int wSum = 0;
        for (int w : SettlementLayoutSC.SIZE_WEIGHTS) {
            wSum += w;
        }
        boolean sizesOk = with > 0;
        for (int i = 0; i < 3; i++) {
            got[i] = 100D * sizes[i] / Math.max(1, with);
            sizesOk &= Math.abs(got[i] - 100D * SettlementLayoutSC.SIZE_WEIGHTS[i] / wSum) <= 6D;
        }
        int minApart = SettlementLayoutSC.CELL - 2 * SettlementLayoutSC.JITTER;
        boolean shareOk = Math.abs(share - SettlementLayoutSC.CHANCE_PCT) <= 5D;
        check(bad.length() == 0 && shareOk && sizesOk && nearest >= minApart && plans > 0, "layout (package S, ПП-1..ПП-4) over 3 seeds: the First Dock"
                + " (region 0, 0 - a Settlement in cell " + SettlementLayoutSC.FIRST_CELL_X + ", " + SettlementLayoutSC.FIRST_CELL_Z + ", within 300 of the"
                + " spawn), deterministic sites and plans, " + Math.round(share * 10D) / 10D + "% of " + regions + " regions with a settlement ("
                + SettlementLayoutSC.CHANCE_PCT + " +- 5) " + shareOk + ", sizes " + Math.round(got[0]) + " / " + Math.round(got[1]) + " / "
                + Math.round(got[2]) + "% (weights " + Arrays.toString(SettlementLayoutSC.SIZE_WEIGHTS) + ", +- 6) " + sizesOk + ", the nearest two "
                + (nearest == Double.MAX_VALUE ? "-" : String.valueOf((int) nearest)) + " apart (>= " + minApart + "); " + plans + " plans: the roster of"
                + " §4.2, posts and points inside the dome, the dock outside it, the footprint on the plateau, off the spawn's cell, within 5 chunks"
                + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    /** What is wrong with a site's plan (null: nothing). SelfTestCombatSC asks it too (Ф3's roster). */
    static String planProblem(long seed, SettlementLayoutSC.Site s) {
        SettlementLayoutSC.Plan p = SettlementLayoutSC.plan(seed, s), q = SettlementLayoutSC.plan(seed, s);
        if (p == null) {
            return "no plan";
        }
        if (!planText(p).equals(planText(q))) {
            return "the plan not deterministic";
        }
        int floor = SettlementLayoutSC.floorY(seed, s);
        if (p.floorY != floor || floor < SubstrateTerrainSC.PLATEAU_MIN || floor > SubstrateTerrainSC.PLATEAU_MAX || p.coreY != floor + 1 || p.coreX != s.x
                || p.coreZ != s.z) {
            return "core " + p.coreX + "," + p.coreY + "," + p.coreZ + " floor " + p.floorY + " (" + floor + ")";
        }
        // §4.2: the roster - kinds, counts, order (Ф3: the record of version 2 - docs/plan-substrate-tech-f3.md §6.2)
        List<NpcKindSC> roster = SettlementLayoutSC.roster(s);
        String why = rosterProblem(s.size, s.seed, roster);
        if (why != null) {
            return why;
        }
        if (p.posts.size() != roster.size()) {
            return "posts " + p.posts.size() + " for a roster of " + roster.size();
        }
        double r2 = (double) s.radius * s.radius;
        for (int i = 0; i < p.posts.size(); i++) {
            SettlementLayoutSC.Post post = p.posts.get(i);
            if (post.kind != roster.get(i)) {
                return "post " + i + " is a " + post.kind + ", the roster says " + roster.get(i);
            }
            if (sq(post.x - s.x, post.z - s.z) > r2 || sq(post.homeX - s.x, post.homeZ - s.z) > r2) {
                return "post / home " + i + " outside the dome";
            }
        }
        // the named points: the plaza, the square, the Clone Capsule, the gate, 2+ pads; the towers where there are Senior Wardens
        List<String> need = new ArrayList<String>(Arrays.asList(SettlementRegistrySC.P_PLAZA, SettlementRegistrySC.P_SQUARE, SettlementRegistrySC.P_CLONE,
                SettlementRegistrySC.P_GATE, SettlementRegistrySC.P_PAD + "0", SettlementRegistrySC.P_PAD + "1"));
        if (s.size.seniors > 0) {
            need.add(SettlementRegistrySC.P_TOWER + "0");
            need.add(SettlementRegistrySC.P_TOWER + "1");
        }
        for (String n : need) {
            int i = p.pointNames.indexOf(n);
            if (i < 0) {
                return "no point " + n;
            }
            int[] pt = p.points.get(i);
            if (sq(pt[0] - s.x, pt[2] - s.z) > r2) {
                return "point " + n + " outside the dome";
            }
        }
        // the dock: Settlement and City only, wholly outside the dome, its plate inside the footprint
        if (p.dock != s.size.dock) {
            return "dock " + p.dock + " for a " + s.size;
        }
        int[] f = p.footprint;
        if (p.dock && (Math.sqrt(sq(p.dockX - s.x, p.dockZ - s.z)) < s.radius + 5 || p.dockX - 3 < f[0] || p.dockX + 3 > f[2] || p.dockZ - 3 < f[1]
                || p.dockZ + 3 > f[3])) {
            return "dock at " + p.dockX + "," + p.dockZ + " (r " + s.radius + ", footprint " + Arrays.toString(f) + ")";
        }
        // the footprint: the dome in it; on the plateau (both edges in one cell, > CANYON_BELT from the lines); off the spawn's cell; 5 chunks
        if (f[0] > s.x - s.radius || f[2] < s.x + s.radius || f[1] > s.z - s.radius || f[3] < s.z + s.radius) {
            return "the dome outside the footprint " + Arrays.toString(f);
        }
        int g = SubstrateTerrainSC.GRID, belt = SubstrateTerrainSC.CANYON_BELT;
        if (Math.floor(f[0] / (double) g) != Math.floor(f[2] / (double) g) || Math.floor(f[1] / (double) g) != Math.floor(f[3] / (double) g)
                || SubstrateTerrainSC.distanceToLine(f[0]) <= belt || SubstrateTerrainSC.distanceToLine(f[2]) <= belt
                || SubstrateTerrainSC.distanceToLine(f[1]) <= belt || SubstrateTerrainSC.distanceToLine(f[3]) <= belt) {
            return "footprint " + Arrays.toString(f) + " not on one plateau";
        }
        if (f[2] >= 0 && f[0] < SettlementLayoutSC.CELL && f[3] >= 0 && f[1] < SettlementLayoutSC.CELL) {
            return "footprint " + Arrays.toString(f) + " in the spawn's cell";
        }
        int[] c = SettlementLayoutSC.footprintChunks(p);
        int cx = s.x >> 4, cz = s.z >> 4;
        if (cx - c[0] > 5 || c[2] - cx > 5 || cz - c[1] > 5 || c[3] - cz > 5) {
            return "footprint chunks " + Arrays.toString(c) + " past 5 of " + cx + "," + cz;
        }
        return null;
    }

    private static double sq(double dx, double dz) {
        return dx * dx + dz * dz;
    }

    /**
     * §4.2: Outpost - the Elder, a Cartographer and 1..3 of the extras; Settlement - the five + more; City - the Elder and 2 of
     * each; then the guards. Ф3 (docs/plan-substrate-tech-f3.md §6.2, ПЖ-9 - the record of version 2): the Salvager among an
     * Outpost's extras, the new trades among the settlers, the Captain and the Commandant after the Senior Wardens - SettlementSizeSC
     * says how many (the Captain by the settlement's seed; without one - none or one). SelfTestCombatSC adds Ф3's own counts.
     */
    static String rosterProblem(SettlementSizeSC size, List<NpcKindSC> roster) {
        return rosterProblem(size, null, roster);
    }

    /** The same for a settlement of seed `seed` (its Captain known). */
    static String rosterProblem(SettlementSizeSC size, long seed, List<NpcKindSC> roster) {
        return rosterProblem(size, Long.valueOf(seed), roster);
    }

    private static String rosterProblem(SettlementSizeSC size, Long seed, List<NpcKindSC> roster) {
        int[] n = new int[NpcKindSC.values().length];
        int tuners = 0, last = -1;
        List<NpcKindSC> guardOrder = Arrays.asList(NpcKindSC.SENTRY, NpcKindSC.SENIOR_WARDEN, NpcKindSC.CAPTAIN, NpcKindSC.COMMANDANT);
        for (int i = 0; i < roster.size(); i++) {
            NpcKindSC k = roster.get(i);
            n[k.ordinal()]++;
            if (!k.guard) {
                tuners++;
                if (last >= 0) {
                    return "a settler after the guards";
                }
            } else {
                int rank = guardOrder.indexOf(k);
                if (rank < 0) {
                    return "a guard of kind " + k;
                }
                if (rank < last) {
                    return "a " + k + " after a " + guardOrder.get(last);
                }
                last = rank;
            }
        }
        if (roster.isEmpty() || roster.get(0) != NpcKindSC.ELDER || n[NpcKindSC.ELDER.ordinal()] != 1) {
            return "the Elder isn't the one and first (" + roster + ")";
        }
        int captains = n[NpcKindSC.CAPTAIN.ordinal()], commandants = n[NpcKindSC.COMMANDANT.ordinal()];
        boolean chiefs = (seed == null ? captains <= 1 : captains == (size.captain(seed.longValue()) ? 1 : 0)) && commandants == (size.commandant ? 1 : 0);
        if (tuners < size.tunersMin || tuners > size.tunersMax || n[NpcKindSC.SENTRY.ordinal()] != size.sentries
                || n[NpcKindSC.SENIOR_WARDEN.ordinal()] != size.seniors || !chiefs) {
            return size + ": " + tuners + " settlers, " + n[NpcKindSC.SENTRY.ordinal()] + " + " + n[NpcKindSC.SENIOR_WARDEN.ordinal()] + " guards, Captain "
                    + captains + ", Commandant " + commandants;
        }
        int need = size == SettlementSizeSC.CITY ? 2 : 1;
        int c = n[NpcKindSC.CARTOGRAPHER.ordinal()], t = n[NpcKindSC.TECHNOLOGIST.ordinal()], m = n[NpcKindSC.METALLURGIST.ordinal()],
                g = n[NpcKindSC.GAS_TECH.ordinal()], extra = t + m + g + n[NpcKindSC.SALVAGER.ordinal()];
        if (size == SettlementSizeSC.OUTPOST ? c < 1 || extra < 1 || extra > 3 : c < need || t < need || m < need || g < need) {
            return size + ": Cartographers " + c + ", Technologists " + t + ", Metallurgists " + m + ", Gas Technicians " + g + ", Salvagers "
                    + n[NpcKindSC.SALVAGER.ordinal()];
        }
        return null;
    }

    private static String planText(SettlementLayoutSC.Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.coreX).append(',').append(p.coreY).append(',').append(p.coreZ).append('/').append(p.floorY).append('/').append(p.dock).append(':')
                .append(p.dockX).append(',').append(p.dockY).append(',').append(p.dockZ).append('/').append(Arrays.toString(p.footprint)).append('/')
                .append(p.pointNames);
        for (int[] pt : p.points) {
            sb.append(Arrays.toString(pt));
        }
        for (SettlementLayoutSC.Post t : p.posts) {
            sb.append(' ').append(t.kind).append(t.x).append(',').append(t.y).append(',').append(t.z).append(';').append(t.homeX).append(',')
                    .append(t.homeY).append(',').append(t.homeZ);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 6. the trade and the contracts (package T, §9)

    private static void trade() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<TradeOfferSC> all = TradeTableSC.all();
        StringBuilder bad = new StringBuilder(), noLang = new StringBuilder(), forbidden = new StringBuilder(), both = new StringBuilder();
        Set<String> keys = new HashSet<String>();
        List<TradeOfferSC.Ingredient> given = new ArrayList<TradeOfferSC.Ingredient>(), got = new ArrayList<TradeOfferSC.Ingredient>();
        int violatorOpen = 0, services = 0;
        for (TradeOfferSC o : all) {
            if (o.key == null || o.key.isEmpty() || !keys.add(o.key)) {
                bad.append(" key '").append(o.key).append('\'');
                continue;
            }
            if (!en.containsKey("sc.trade.offer." + o.key) || !ru.containsKey("sc.trade.offer." + o.key)) {
                noLang.append(' ').append(o.key);
            }
            if (o.kind == null || o.kind.guard || !TradeTableSC.offers(o.kind).contains(o) || TradeTableSC.byKey(o.key) != o) {
                bad.append(' ').append(o.key).append(" kind ").append(o.kind);
            }
            if (o.outKind > TradeOfferSC.OUT_MAP) {
                services++;                                 // Ф3's services (the charge, the restoring): SelfTestCombatSC's, ПО-7 sets them aside
                continue;
            }
            if (o.in == null || o.in.length < 1 || o.in.length > 2) {
                bad.append(' ').append(o.key).append(" sides");
                continue;
            }
            for (TradeOfferSC.Ingredient i : o.in) {
                if (i == null || i.display() == null) {
                    bad.append(' ').append(o.key).append(" an input with nothing to show");
                } else if (!i.isChips()) {
                    given.add(i);
                }
            }
            if (o.outKind == TradeOfferSC.OUT_ITEM) {
                ItemStack out = o.out == null ? null : o.out.display();
                if (out == null) {
                    bad.append(' ').append(o.key).append(" no output");
                } else if (!o.out.isChips()) {
                    got.add(o.out);
                    String why = forbidden(out);
                    if (why != null) {
                        forbidden.append(' ').append(o.key).append(':').append(why);
                    }
                }
            } else if (o.outKind == TradeOfferSC.OUT_REPUTATION ? o.outAmount <= 0 : o.outKind != TradeOfferSC.OUT_MAP) {
                bad.append(' ').append(o.key).append(" output kind ").append(o.outKind).append(" / ").append(o.outAmount);
            }
            if (o.minLevel < ReputationSC.VIOLATOR || o.minLevel > ReputationSC.HONOURED || o.limit <= 0 || o.periodDays != 1 && o.periodDays != 7) {
                bad.append(' ').append(o.key).append(" level / limit / period ").append(o.minLevel).append('/').append(o.limit).append('/').append(o.periodDays);
            }
            violatorOpen += TradeRulesSC.available(o, ReputationSC.VIOLATOR) ? 1 : 0;
        }
        // ПО-7: no thing both bought and sold anywhere - Ф3: by its one definition, TradeTableSC.boughtAndSold (a purchase's part,
        // the Armourer's Controllers for the Defense Chip, is a price, not «bought»; Ф2 took every given thing, which Ф3's approved
        // deal against the Technologist's Controllers would fail)
        for (String n : TradeTableSC.boughtAndSold()) {
            both.append(' ').append(n);
        }
        boolean guards = TradeTableSC.offers(NpcKindSC.SENTRY).isEmpty() && TradeTableSC.offers(NpcKindSC.SENIOR_WARDEN).isEmpty();
        check(!all.isEmpty() && bad.length() == 0 && noLang.length() == 0 && forbidden.length() == 0 && both.length() == 0 && guards, "trade table"
                + " (package T, ПО-3, ПО-7): " + all.size() + " deals, unique keys, every side shown, levels and limits sane"
                + (all.isEmpty() ? " - THE TABLE IS EMPTY" : "") + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; sc.trade.offer.<key> in both"
                + " languages" + (noLang.length() == 0 ? "" : " - NO LANG:" + noLang) + "; no armour, blades, weapons, PE-1, reactors, bridge parts or"
                + " clots out" + (forbidden.length() == 0 ? "" : " - SOLD:" + forbidden) + "; nothing both bought and sold"
                + (both.length() == 0 ? "" : " - BOTH:" + both) + "; the guards trade nothing " + guards + "; " + violatorOpen + " deals open to a Violator"
                + (services > 0 ? "; " + services + " Ф3 services aside" : ""));

        // ПО-3, ПО-6: the player's side - a settlement with no spread, the level's multiplier, rounded up
        long zero = Long.MIN_VALUE;
        for (long seed = 0; seed < 20000 && zero == Long.MIN_VALUE; seed++) {
            if (TradeRulesSC.seedPct(seed, "selftest") == 0) {
                zero = seed;
            }
        }
        int stranger = zero == Long.MIN_VALUE ? -1 : TradeRulesSC.playerSide(16, ReputationSC.STRANGER, zero, "selftest");
        int honoured = zero == Long.MIN_VALUE ? -1 : TradeRulesSC.playerSide(16, ReputationSC.HONOURED, zero, "selftest");
        int one = zero == Long.MIN_VALUE ? -1 : TradeRulesSC.playerSide(1, ReputationSC.HONOURED, zero, "selftest");
        Set<Integer> spread = new TreeSet<Integer>();
        boolean within = true;
        Random rand = new Random(9L);
        List<String> someKeys = new ArrayList<String>(keys);
        someKeys.add("selftest");
        for (int i = 0; i < 2000; i++) {
            int pct = TradeRulesSC.seedPct(rand.nextLong(), someKeys.get(i % someKeys.size()));
            within &= Math.abs(pct) <= TradeRulesSC.SEED_SPREAD_PCT;
            spread.add(Integer.valueOf(pct));
        }
        boolean sides = stranger == 24 && honoured == 14 && one == 1;
        check(sides && within && spread.size() >= 3, "trade prices (ПО-3, ПО-6): base 16 at no spread - a Stranger gives " + stranger + " (24), the"
                + " Honoured " + honoured + " (14), never below 1 (" + one + "); seedPct within +-" + TradeRulesSC.SEED_SPREAD_PCT + " over 2000 seeds "
                + within + ", " + spread.size() + " different spreads (>= 3)");

        // ПД-1, ПО-4, ПО-5: what a level may do, the limits, the global chips
        ItemStack chip = SettlementItemsSC.chips(1);
        boolean rules = false;
        if (chip != null) {
            TradeOfferSC.Ingredient goods = TradeOfferSC.Ingredient.of(new ItemStack(ModItems.ingot, 1, 0), 4);
            TradeOfferSC sell = new TradeOfferSC("t.sell", NpcKindSC.METALLURGIST, new TradeOfferSC.Ingredient[]{goods}, TradeOfferSC.Ingredient.chips(2),
                    TradeOfferSC.OUT_ITEM, 0, ReputationSC.VIOLATOR, 8, false, 1);
            TradeOfferSC buy = new TradeOfferSC("t.buy", NpcKindSC.METALLURGIST, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(6)}, goods,
                    TradeOfferSC.OUT_ITEM, 0, ReputationSC.VIOLATOR, 8, false, 1);
            TradeOfferSC penance = new TradeOfferSC("t.penance", NpcKindSC.ELDER, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(16)}, null,
                    TradeOfferSC.OUT_REPUTATION, 50, ReputationSC.VIOLATOR, 1, false, 1);
            TradeOfferSC dear = new TradeOfferSC("t.dear", NpcKindSC.ELDER, new TradeOfferSC.Ingredient[]{TradeOfferSC.Ingredient.chips(64)}, goods,
                    TradeOfferSC.OUT_ITEM, 0, ReputationSC.HONOURED, 1, true, 7);
            rules = sell.sell() && !sell.buy() && buy.buy() && !buy.sell() && TradeRulesSC.available(sell, ReputationSC.VIOLATOR)
                    && !TradeRulesSC.available(buy, ReputationSC.VIOLATOR) && TradeRulesSC.available(buy, ReputationSC.STRANGER)
                    && TradeRulesSC.available(penance, ReputationSC.VIOLATOR) && !TradeRulesSC.available(sell, ReputationSC.ENEMY)
                    && !TradeRulesSC.available(penance, ReputationSC.ENEMY) && !TradeRulesSC.available(dear, ReputationSC.OWN)
                    && TradeRulesSC.available(dear, ReputationSC.HONOURED) && !TradeRulesSC.available(null, ReputationSC.HONOURED)
                    && TradeRulesSC.remaining(sell, 3) == 5 && TradeRulesSC.remaining(sell, 9) == 0 && TradeRulesSC.period(13L, 7) == 1L
                    && TradeRulesSC.period(13L, 1) == 13L && goods.matches(new ItemStack(ModItems.ingot, 5, 0)) && !goods.matches(new ItemStack(ModItems.ingot, 1, 1))
                    && TradeOfferSC.Ingredient.chips(3).isChips() && TradeOfferSC.Ingredient.chips(3).matches(SettlementItemsSC.chips(64));
        }
        boolean chips = TradeRulesSC.chipsAllowed(60, 8) == 4 && TradeRulesSC.chipsAllowed(64, 1) == 0 && TradeRulesSC.chipsAllowed(0, 8) == 8
                && TradeRulesSC.chipsAllowed(70, 5) == 0 && TradeRulesSC.CHIPS_PER_DAY == 64 && TradeRulesSC.CONTRACTS_PER_DAY == 3
                && TradeRulesSC.CRUMBS_PER_WEEK == 1 && TradeRulesSC.SEED_SPREAD_PCT == 10;
        check(rules && chips, "trade rules (ПД-1, ПО-4, ПО-5): a Violator sells and does penance but buys nothing, an Enemy does nothing, the level"
                + " gate, remaining / period, strict matching by meta, the chips " + rules + "; chipsAllowed(60, 8) = " + TradeRulesSC.chipsAllowed(60, 8)
                + " (4), 64 a day / 3 contracts / 1 crumb a week " + chips);

        // ПО-6 (Ф3): a sale of a matcher (the Salvager's trophy - one stays one) scales its chips by the level and the spread
        TradeOfferSC trophySale = TradeTableSC.byKey(TradeTableSC.SAL_TROPHY);
        boolean trophyOut = false;
        String trophyWhat = "no " + TradeTableSC.SAL_TROPHY;
        if (trophySale != null && trophySale.out != null && trophySale.out.count == 6) {
            int vio = TradeRulesSC.outCount(trophySale, ReputationSC.VIOLATOR, 0), str = TradeRulesSC.outCount(trophySale, ReputationSC.STRANGER, 0),
                    par = TradeRulesSC.outCount(trophySale, ReputationSC.PARTNER, 0), hon = TradeRulesSC.outCount(trophySale, ReputationSC.HONOURED, 0),
                    dear = TradeRulesSC.outCount(trophySale, ReputationSC.PARTNER, 10), single = TradeRulesSC.playerCounts(trophySale, ReputationSC.VIOLATOR, 10)[0];
            TradeOfferSC firmware = TradeTableSC.byKey(TradeTableSC.SAL_FIRMWARE);
            int plain = firmware == null ? -1 : TradeRulesSC.outCount(firmware, ReputationSC.VIOLATOR, 10);
            trophyOut = vio == 3 && str == 4 && par == 6 && hon == 7 && dear == 5 && single == 1 && firmware != null && plain == firmware.out.count;
            trophyWhat = "6 chips - a Violator " + vio + " (3), a Stranger " + str + " (4), a Partner " + par + " (6), the Honoured " + hon + " (7), a Partner"
                    + " at +10% " + dear + " (5); the trophy stays " + single + " (1); a plain sale's chips unscaled " + plain + " (" + (firmware == null ? "?" : firmware.out.count) + ")";
        } else if (trophySale != null) {
            trophyWhat = TradeTableSC.SAL_TROPHY + " pays " + (trophySale.out == null ? "nothing" : trophySale.out.count + " (6 expected)");
        }
        check(trophyOut, "trade prices (ПО-6): the Salvager's trophy sale (TradeRulesSC.outCount) - " + trophyWhat);
        System.out.println("[SC-TEST] INFO settlement: ПО-7's «buying costs at least x" + TradeRulesSC.BUY_OVER_SELL + " of selling» is a balance rule"
                + " with no value table to check it by (TODO(balance)); nothing in the table may be both bought and sold, so no pair to compare");
    }

    /** Why a deal may not give `s` (ПО-7: armour, blades, weapons, ПЭ-1, reactors, bridge parts, Singularity Clots), null if it may. */
    static String forbidden(ItemStack s) {
        Item i = s.getItem();
        GeneratorType g = ModBlocks.generatorTypeOf(s);
        if (i instanceof ItemArmor) {
            return "armour";
        }
        if (ModItems.BLADES.containsValue(i) || ModItems.WEAPONS.containsValue(i)) {
            return "a blade / weapon";
        }
        if (i == ModItems.empGun) {
            return "PE-1";
        }
        if (ModItems.singularClot != null && i == ModItems.singularClot) {
            return "a Singularity Clot";
        }
        if (ModBlocks.bridge != null && i == Item.getItemFromBlock(ModBlocks.bridge)) {
            return "a bridge part";
        }
        if (g != null && (g.kind == GeneratorType.Kind.FUSION || g.kind == GeneratorType.Kind.EXO || g.kind == GeneratorType.Kind.SINGULAR
                || g.kind == GeneratorType.Kind.DUAL_FLUID)) {
            return "a reactor";
        }
        return null;
    }

    private static void contracts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<ContractTableSC.Contract> pool = ContractTableSC.pool();
        StringBuilder bad = new StringBuilder();
        Set<String> keys = new HashSet<String>();
        for (ContractTableSC.Contract c : pool) {
            if (!keys.add(c.key)) {
                bad.append(" twice ").append(c.key);
            }
            boolean ambassadors = c.key.startsWith("emb");            // Ф6 (ПО-27): the Ambassador's, its «Депеша» a goal of its own - SelfTestEmbassySC's
            if (c.goal < ContractTableSC.KILL || c.goal > ContractTableSC.CARGO && !ambassadors || c.target <= 0 || c.chips <= 0 || c.reputation < ReputationSC.CONTRACT_MIN
                    || c.reputation > ReputationSC.CONTRACT_MAX || c.modulePct < 0 || c.modulePct > 100 || ContractTableSC.byKey(c.key) != c) {
                bad.append(' ').append(c.key).append(" goal ").append(c.goal).append(" target ").append(c.target).append(" reward ").append(c.chips)
                        .append(" / +").append(c.reputation);
            }
        }
        for (Object[] want : CONTRACTS) {
            ContractTableSC.Contract c = ContractTableSC.byKey((String) want[0]);
            if (c == null) {
                bad.append(" no ").append(want[0]);
            } else if (c.target != (Integer) want[1] || c.chips != (Integer) want[2] || c.reputation != (Integer) want[3] || c.modulePct != (Integer) want[4]) {
                bad.append(' ').append(c.key).append(' ').append(c.target).append('/').append(c.chips).append("/+").append(c.reputation).append('/')
                        .append(c.modulePct).append("% (plan ").append(want[1]).append('/').append(want[2]).append("/+").append(want[3]).append('/')
                        .append(want[4]).append("%)");
            }
        }
        StringBuilder noLang = new StringBuilder();
        List<String> named = new ArrayList<String>(keys);
        named.add("anchor");
        for (String k : named) {
            for (String key : new String[]{"sc.contract." + k, "sc.contract." + k + ".desc"}) {
                if (!en.containsKey(key) || !ru.containsKey(key)) {
                    noLang.append(' ').append(key);
                }
            }
        }
        boolean board = true;
        Set<String> boards = new HashSet<String>();
        for (UUID id : new UUID[]{P1, P2, UUID.nameUUIDFromBytes(new byte[]{1, 2, 3})}) {
            for (long day = 0; day < 20; day++) {
                List<String> b = ContractTableSC.board(id, day), again = ContractTableSC.board(id, day);
                board &= b.size() == ContractTableSC.BOARD && new HashSet<String>(b).size() == ContractTableSC.BOARD && keys.containsAll(b) && b.equals(again);
                boards.add(b.toString());
            }
        }
        check(!pool.isEmpty() && bad.length() == 0 && noLang.length() == 0 && board, "contracts (package T, ПО-8): " + pool.size() + " in the pool, unique"
                + " keys, the numbers of §9.6, rewards within +" + ReputationSC.CONTRACT_MIN + "..+" + ReputationSC.CONTRACT_MAX
                + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; titles and lines in both languages" + (noLang.length() == 0 ? "" : " - NO LANG:" + noLang)
                + "; the board: " + ContractTableSC.BOARD + " different ones of the pool, the same when asked again " + board + " (" + boards.size()
                + " different boards over 3 players x 20 days)");
    }

    // ------------------------------------------------------------------ 7. travel (package B, §8.3)

    private static void travel() {
        boolean scatter = TravelRulesSC.scatter(50, true, false) == 0 && TravelRulesSC.scatter(7, true, true) == 0 && TravelRulesSC.scatter(5, false, true) == 30
                && TravelRulesSC.scatter(50, false, true) == 50 && TravelRulesSC.scatter(7, false, false) == 7;
        boolean capsule = TravelRulesSC.capsuleSecondsLeft(0) == 10 && TravelRulesSC.capsuleSecondsLeft(1) == 10 && TravelRulesSC.capsuleSecondsLeft(181) == 1
                && TravelRulesSC.capsuleSecondsLeft(199) == 1 && TravelRulesSC.capsuleSecondsLeft(200) == 0 && TravelRulesSC.capsuleSecondsLeft(300) == 0;
        boolean consts = TravelRulesSC.DOCK_SNAP == 8 && TravelRulesSC.DEATH_SCATTER == 30 && TravelRulesSC.MARK_SNAP == 8 && TravelRulesSC.DEATH_CLEAR == 32
                && TravelRulesSC.CAPSULE_TICKS == 200 && TravelRulesSC.CAPSULE_SAY == 40 && TravelRulesSC.MAP_DOCKS == 3
                && "scDocks".equals(ItemSettlementMapSC.NBT_DOCKS);
        check(scatter && capsule && consts, "travel (ПР-2, ПР-4, ПР-6): a dock's end 0 scatter, the death mark's at least " + TravelRulesSC.DEATH_SCATTER
                + " " + scatter + ", the capsule's seconds 10 .. 1 .. 0 " + capsule + ", the numbers (snap 8, clear 32, 10 s, 3 docks, scDocks) " + consts);
    }

    // ------------------------------------------------------------------ 8. the core without a world (§5.2)

    private static void core() {
        TileEntitySettlementCoreSC t = new TileEntitySettlementCoreSC();
        t.setup(7, 40, 64);
        String owner = SettlementSC.ownerName(7);
        boolean set = fixedOk(t, owner, 7) && t.pay(1000000000) && t.upkeepPerTick() == 0 && !t.isEnergySink() && t.demandedEnergy() == 0
                && !t.isUseableByPlayer(null) && t.floorY() == 64;
        NBTTagCompound n = new NBTTagCompound();
        t.writeToNBT(n);
        TileEntitySettlementCoreSC u = new TileEntitySettlementCoreSC();
        u.readFromNBT(n);
        boolean nbt = n.getInteger("scSettlement") == 7 && n.getInteger("scRadius") == 40 && n.getInteger("scFloorY") == 64 && fixedOk(u, owner, 7)
                && u.floorY() == 64;
        NBTTagCompound f = (NBTTagCompound) n.copy();
        f.setInteger("Flags", 0);
        f.setString("Owner", "Steve");
        f.setInteger("Mode", FieldMode.DOME.ordinal() == 0 ? 1 : 0);
        f.setInteger("Range", 5);
        f.setInteger("Filter", TileEntityFieldGeneratorSC.FILTER_ALL);
        f.setBoolean("PowerOff", true);
        TileEntitySettlementCoreSC v = new TileEntitySettlementCoreSC();
        v.readFromNBT(f);
        boolean foreign = fixedOk(v, owner, 7);
        trim();
        check(set && nbt && foreign, "the core (ПП-6) without a world: setup(7, 40, 64) - its owner the settlement's " + owner.equals(t.getOwner()) + ", "
                + t.getMode() + " " + t.getRange() + ", F_PRIVATE / NO_MECHANISMS / NO_SPAWN / NO_ENDER / DAMAGE / EMP_SCREEN on, PUSH_PLAYERS off,"
                + " hostiles only, pays anything, no upkeep, no sink, no screen " + set + "; its NBT there and back " + nbt + "; a stranger's NBT (Flags 0,"
                + " Owner Steve, range 5, filter all) written over by the fixed settings " + foreign);
    }

    /**
     * §4.5, ПП-7: the settlements' metal, coils and lead glass are the trim - unbreakable (hardness -1: the tools that
     * break past Forge's events skip it), blast-proof, no drop, among the protected blocks; every metal of the palette
     * has its meta.
     */
    private static void trim() {
        Block d = SettlementBlocksSC.decor, g = SettlementBlocksSC.glass;
        if (d == null || g == null) {
            check(false, "the trim (§4.5): not registered");
            return;
        }
        boolean hard = d.getBlockHardness(null, 0, 0, 0) < 0F && g.getBlockHardness(null, 0, 0, 0) < 0F
                && d.getExplosionResistance(null) >= 1000000F && g.getExplosionResistance(null) >= 1000000F;
        boolean drops = d.getDrops(null, 0, 0, 0, BlockSettlementDecorSC.HAFNIUM, 0).isEmpty() && g.getDrops(null, 0, 0, 0, 0, 0).isEmpty()
                && d.getPickBlock(null, null, 0, 0, 0) == null && g.getPickBlock(null, null, 0, 0, 0) == null
                && SettlementBlocksSC.core.getPickBlock(null, null, 0, 0, 0) == null && SettlementBlocksSC.showcase.getPickBlock(null, null, 0, 0, 0) == null
                && SettlementBlocksSC.dock.getPickBlock(null, null, 0, 0, 0) == null;
        boolean guarded = SettlementBlocksSC.unbreakable(d) && SettlementBlocksSC.unbreakable(g) && !SettlementBlocksSC.unbreakable(ModBlocks.leadGlass)
                && !SettlementBlocksSC.unbreakable(ModBlocks.metalBlock);
        StringBuilder metas = new StringBuilder();
        for (com.sc.util.Material m : BlockSettlementDecorSC.METALS) {
            ItemStack s = SettlementBlocksSC.trim(m);
            if (s == null || Block.getBlockFromItem(s.getItem()) != d || BlockSettlementDecorSC.METALS[s.getItemDamage()] != m) {
                metas.append(' ').append(m);
            }
        }
        boolean names = BlockSettlementDecorSC.COIL == BlockSettlementDecorSC.METALS.length                  // Ф6's looks appended after it
                && "tokamakCoil".equals(BlockSettlementDecorSC.NAMES[BlockSettlementDecorSC.COIL])
                && BlockSettlementDecorSC.NAMES.length == BlockSettlementDecorSC.QUARTZ_PILLAR + 1
                && d.getDrops(null, 0, 0, 0, BlockSettlementDecorSC.TRACE, 0).isEmpty() && d.getDrops(null, 0, 0, 0, BlockSettlementDecorSC.QUARTZ, 0).isEmpty();
        check(hard && drops && guarded && metas.length() == 0 && names, "the trim (§4.5, ПП-7): hardness -1 and blast-proof " + hard
                + ", no drop and no pick-block (the core, showcases and dock give none either) " + drops + ", among the unbreakable - the real metal and"
                + " lead glass not " + guarded + ", a meta per metal of the palette" + (metas.length() == 0 ? "" : " - NOT:" + metas) + ", the coil last " + names);
    }

    private static boolean fixedOk(TileEntitySettlementCoreSC t, String owner, int id) {
        int[] on = {TileEntityFieldGeneratorSC.F_PRIVATE, TileEntityFieldGeneratorSC.F_NO_MECHANISMS, TileEntityFieldGeneratorSC.F_NO_SPAWN,
                TileEntityFieldGeneratorSC.F_NO_ENDER, TileEntityFieldGeneratorSC.F_DAMAGE, TileEntityFieldGeneratorSC.F_EMP_SCREEN};
        boolean flags = !t.has(TileEntityFieldGeneratorSC.F_PUSH_PLAYERS);
        for (int fl : on) {
            flags &= t.has(fl);
        }
        return flags && owner.equals(t.getOwner()) && t.getMode() == FieldMode.DOME && t.getRange() == 40 && t.settlementId() == id && t.radius() == 40
                && t.getFilter() == TileEntityFieldGeneratorSC.FILTER_HOSTILE;
    }

    // ------------------------------------------------------------------ 9. texts and the handbook (package D, §10)

    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>(Arrays.asList("tile.siliconage.settlementCore.name", "tile.siliconage.dock.plate.name",
                "tile.siliconage.dock.beacon.name", "tile.siliconage.substrateRock.settlementBrick.name", "item.siliconage.creditChip.name",
                "item.siliconage.returnCapsule.name", "item.siliconage.settlementMap.name", "item.siliconage.combatLoot.stolenCargo.name",
                "entity.SiliconAge.tuner.name", "entity.SiliconAge.warden.name", "sc.settle.npcName", "sc.settle.npcName.none", "sc.settle.name",
                "sc.nei.obtain.creditChip", "sc.nei.obtain.creditChip.limit", "sc.nei.obtain.cartographer", "sc.nei.obtain.settlement",
                "sc.nei.obtain.settlerules", "sc.nei.obtain.egg.settle", "sc.manual.world.block.settlementBrick", "sc.manual.world.loot.stolenCargo"));
        for (String n : BlockShowcaseSC.NAMES) {
            keys.add("tile.siliconage.showcase." + n + ".name");
        }
        for (NpcKindSC k : NpcKindSC.values()) {
            keys.add(k.langKey());
        }
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            keys.add(s.langKey());
        }
        for (String r : ReputationSC.LEVEL_KEYS) {
            keys.add("sc.settle.rep." + r);
        }
        for (String v : ViolationRulesSC.KEYS) {
            keys.add("sc.settle.violation." + v);
        }
        for (String t : TOOLTIPS) {
            keys.add("sc.tooltip." + t);
            keys.add("sc.tooltip." + t + ".details");
            keys.add("sc.tooltip." + t + ".howto");
        }
        // the real stacks' names: what the game asks for each new block and item
        for (ItemStack s : newStacks()) {
            if (s.getItem() != MobItemsSC.spawnEgg) {
                keys.add(s.getItem().getUnlocalizedName(s) + ".name");
            }
        }
        StringBuilder missing = new StringBuilder();
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
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
                } else if (from == en && args(e.getValue()) != args(to.get(e.getKey()))) {
                    diff.append(" args differ:").append(e.getKey());
                } else if (from == en && e.getValue().matches(".*[\\u0400-\\u04FF«»„].*")) {
                    diff.append(" Cyrillic / «» / „ in en_US:").append(e.getKey());
                }
            }
        }
        StringBuilder eggs = new StringBuilder();
        if (MobItemsSC.spawnEgg != null) {
            for (int id : new int[]{MobEntitiesSC.ID_TUNER, MobEntitiesSC.ID_WARDEN}) {
                String shown = new ItemStack(MobItemsSC.spawnEgg, 1, id).getDisplayName(), mob = BookWorldSC.mobName(MobEntitiesSC.nameOf(id));
                if (!shown.contains(mob)) {
                    eggs.append(' ').append(id).append(": ").append(shown).append(" (").append(mob).append(')');
                }
            }
        }
        check(missing.length() == 0 && diff.length() == 0 && eggs.length() == 0, "texts (§10.1): " + keys.size() + " keys - the blocks, items, creatures,"
                + " kinds, sizes, levels, elements, violations, tooltips, NEI - in en_US and ru_RU, " + all + " Ф2 keys with the same keys and arguments in"
                + " both, no Cyrillic in English, the eggs named after their creature" + missing + diff + eggs);
    }

    /** Every new block and item of Ф2 (the stacks the game names); missing registrations are skipped. */
    private static List<ItemStack> newStacks() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (SettlementBlocksSC.core != null) {
            out.add(new ItemStack(SettlementBlocksSC.core));
        }
        if (SettlementBlocksSC.showcase != null) {
            for (int m = 0; m < BlockShowcaseSC.NAMES.length; m++) {
                out.add(new ItemStack(SettlementBlocksSC.showcase, 1, m));
            }
        }
        if (SettlementBlocksSC.dock != null) {
            for (int m = 0; m < BlockDockSC.NAMES.length; m++) {
                out.add(new ItemStack(SettlementBlocksSC.dock, 1, m));
            }
        }
        if (SettlementBlocksSC.decor != null) {
            for (int m = 0; m < BlockSettlementDecorSC.NAMES.length; m++) {
                out.add(new ItemStack(SettlementBlocksSC.decor, 1, m));
            }
        }
        if (SettlementBlocksSC.glass != null) {
            out.add(new ItemStack(SettlementBlocksSC.glass));
        }
        if (SubstrateBlocksSC.rock != null && SubstrateBlocksSC.ROCK_METAS > BookWorldSC.BRICK) {
            out.add(new ItemStack(SubstrateBlocksSC.rock, 1, BookWorldSC.BRICK));
        }
        for (Item i : new Item[]{SettlementItemsSC.creditChip, SettlementItemsSC.returnCapsule, SettlementItemsSC.settlementMap}) {
            if (i != null) {
                out.add(new ItemStack(i));
            }
        }
        if (MobItemsSC.combatLoot != null && ItemCombatLootSC.LootKind.values().length > BookWorldSC.LOOT_STOLEN_CARGO) {
            out.add(new ItemStack(MobItemsSC.combatLoot, 1, BookWorldSC.LOOT_STOLEN_CARGO));
        }
        if (MobItemsSC.spawnEgg != null) {
            out.add(new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_TUNER));
            out.add(new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_WARDEN));
        }
        return out;
    }

    private static boolean ours(String k) {
        for (String p : PREFIXES) {
            if (k.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /** The number of %s / %d arguments in a lang value (%% isn't one). */
    private static int args(String v) {
        int n = 0;
        for (int i = 0; i + 1 < v.length(); i++) {
            if (v.charAt(i) == '%') {
                char c = v.charAt(i + 1);
                if (c == '%') {
                    i++;
                } else if (c == 's' || c == 'd') {
                    n++;
                }
            }
        }
        return n;
    }

    /** The six new articles build with no raw key, their tables have a row per size / level / violation / rank, G on every new thing opens one. */
    // ------------------------------------------------------------------ 13a. the wardens' suit (2026-10-18, ПГ-15..ПГ-20)

    /** A creature that deals the test blows (no world needed). */
    private static final class Hitter extends net.minecraft.entity.EntityCreature {
        Hitter() {
            super(null);
        }
    }

    private static void wardenSuit() {
        NpcKindSC[] ranks = {NpcKindSC.SENTRY, NpcKindSC.SENIOR_WARDEN, NpcKindSC.CAPTAIN, NpcKindSC.COMMANDANT};
        // ПГ-15: the reserve - a charged player set (Nano 4 x 100k / 500, Quantum 4 x 1M / 2000) for the Sentry and the Senior, the chiefs' own
        boolean defaults = com.sc.util.ConfigSC.armorCapacity == 1F && com.sc.util.ConfigSC.armorDamageCost == 1F;
        float[] want = {defaults ? 800F : WardenSuitSC.playerSetUnits(ArmorSuit.NANO), defaults ? 2000F : WardenSuitSC.playerSetUnits(ArmorSuit.QUANTUM),
                WardenSuitSC.CAPTAIN_RESERVE, WardenSuitSC.COMMANDANT_RESERVE};
        StringBuilder got = new StringBuilder();
        boolean reserves = true;
        for (int i = 0; i < ranks.length; i++) {
            float r = EntityWardenSC.reserveOf(ranks[i]);
            reserves &= Math.abs(r - want[i]) < 0.01F;
            got.append(i == 0 ? "" : " / ").append(r);
        }
        boolean exo = !defaults || Math.abs(WardenSuitSC.playerSetUnits(ArmorSuit.EXO) - 4000F) < 0.01F
                && Math.abs(WardenSuitSC.playerSetUnits(ArmorSuit.SINGULAR) - 32000F) < 0.01F;
        check(reserves && exo && WardenSuitSC.CAPTAIN_RESERVE < WardenSuitSC.playerSetUnits(ArmorSuit.EXO), "wardens' suit (ПГ-15): the reserve by rank " + got
                + " (800 / 2000 - a charged player set - / 1600 / 3000) " + reserves + "; a charged player Exo / Singular set "
                + WardenSuitSC.playerSetUnits(ArmorSuit.EXO) + " / " + WardenSuitSC.playerSetUnits(ArmorSuit.SINGULAR) + " (4000 / 32000) " + exo);
        // the plating's points and share: the player's (20 / 22 / 24 / 28 points; iron 15 under a whole-set suppression)
        int nano = WardenSuitSC.setPoints(ArmorSuit.NANO, false), quantum = WardenSuitSC.setPoints(ArmorSuit.QUANTUM, false),
                exoP = WardenSuitSC.setPoints(ArmorSuit.EXO, false), singular = WardenSuitSC.setPoints(ArmorSuit.SINGULAR, false),
                iron = WardenSuitSC.setPoints(ArmorSuit.EXO, true);
        boolean points = nano == 20 && quantum == 22 && exoP == 24 && singular == 28 && iron == 15
                && Math.abs(com.sc.mob.MobArmorMathSC.absorbShare(nano) - 0.8F) < 1e-4F && com.sc.mob.MobArmorMathSC.absorbShare(singular) == 1F;
        check(points, "wardens' suit (ПГ-15): the set's points Nano " + nano + ", Quantum " + quantum + ", Exo " + exoP + ", Singular " + singular
                + " (20 / 22 / 24 / 28 - 80..100%), iron's under a Pulse " + iron + " (15)");
        // ПГ-16: what the plating stops - a piercing blow by somebody yes; absolute, «Пробой», the world's, a blockable one no
        Hitter h = new Hitter();
        net.minecraft.util.DamageSource pierce = new net.minecraft.util.EntityDamageSource("player", h).setDamageBypassesArmor(),
                absolute = new net.minecraft.util.EntityDamageSource("player", h).setDamageBypassesArmor().setDamageIsAbsolute(),
                plain = new net.minecraft.util.EntityDamageSource("player", h);
        boolean pierced = com.sc.mob.MobArmorSC.pierce(pierce) && !com.sc.mob.MobArmorSC.pierce(absolute) && !com.sc.mob.MobArmorSC.pierce(plain)
                && !com.sc.mob.MobArmorSC.pierce(com.sc.mob.BreakdownDamageSC.of(h)) && !com.sc.mob.MobArmorSC.pierce(net.minecraft.util.DamageSource.outOfWorld)
                && !com.sc.mob.MobArmorSC.pierce(net.minecraft.util.DamageSource.fall) && !com.sc.mob.MobArmorSC.pierce(net.minecraft.util.DamageSource.magic);
        check(pierced, "wardens' suit (ПГ-16): MobArmorSC.pierce - a blade's armour-piercing blow yes; absolute, Breakdown, the void, a fall, magic, a"
                + " blockable blow no " + pierced);
        // the reference fight: a Quantum blade 20 a blow, once a second - the Sentry 15..25 s, the Senior clearly longer, the chiefs much longer;
        // a Pulse (the whole set suppressed) cuts each to well under half
        float[] t = new float[4], p = new float[4];
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            t[i] = WardenSuitSC.fightSeconds(ranks[i], WardenSuitSC.REF_BLOW, WardenSuitSC.REF_HITS_PER_SECOND, false);
            p[i] = WardenSuitSC.fightSeconds(ranks[i], WardenSuitSC.REF_BLOW, WardenSuitSC.REF_HITS_PER_SECOND, true);
            f.append(i == 0 ? "" : ", ").append(ranks[i].name().toLowerCase()).append(' ').append(Math.round(t[i])).append(" s (2 a second ")
                    .append(Math.round(WardenSuitSC.fightSeconds(ranks[i], WardenSuitSC.REF_BLOW, 2F * WardenSuitSC.REF_HITS_PER_SECOND, false)))
                    .append(" s; under a Pulse ").append(Math.round(p[i])).append(" s)");
        }
        boolean fight = WardenSuitSC.REF_BLOW == 20F && t[0] >= 15F && t[0] <= 25F && t[1] >= 2F * t[0] && t[2] >= 3F * t[0] && t[3] >= 1.5F * t[2];
        boolean pulsed = true;
        for (int i = 0; i < 4; i++) {
            pulsed &= p[i] < 0.6F * t[i];
        }
        check(fight && pulsed, "wardens' suit (ПГ-15..ПГ-17): the reference fight - a Quantum blade " + WardenSuitSC.REF_BLOW + " a blow, "
                + WardenSuitSC.REF_HITS_PER_SECOND + " a second: " + f + " - the Sentry 15..25 s, the Senior 2x, the Captain 3x, the Commandant 1.5x the"
                + " Captain " + fight + "; a Pulse cuts each under 60% " + pulsed);
        // ПГ-17 / ПГ-18: Regeneration, the pads, the heat
        boolean regen = WardenSuitSC.regenHeal(ArmorSuit.NANO) == 0.5F && WardenSuitSC.regenHeal(ArmorSuit.SINGULAR) == 3F
                && Math.abs(WardenSuitSC.regenUnits(ArmorSuit.NANO) - 0.4F) < 1e-4F
                && Math.abs(WardenSuitSC.padUnits(800F) - 40F) < 0.01F && WardenSuitSC.padUnits(10F) == EntityWardenSC.PAD_UNITS;
        boolean heat = WardenSuitSC.cooling(ArmorSuit.NANO, false) == 2 && WardenSuitSC.cooling(ArmorSuit.QUANTUM, true) == 9
                && WardenSuitSC.cooling(ArmorSuit.EXO, true) == 18 && WardenSuitSC.cooling(ArmorSuit.SINGULAR, true) == 34
                && WardenSuitSC.hotAfter(false, 100, 100) && !WardenSuitSC.hotAfter(false, 99, 100) && WardenSuitSC.hotAfter(true, 51, 100)
                && !WardenSuitSC.hotAfter(true, 50, 100);
        check(regen && heat, "wardens' suit (ПГ-17, ПГ-18): Regeneration 0.5..3 HP a second for 200 EU (Nano 0.4 units), a pad 5% a second of 800 = 40"
                + " (2 at least) " + regen + "; cooling Nano 2, Quantum / Exo / Singular with helium 9 / 18 / 34 a second, overheated at 100% until"
                + " 50% " + heat);
        // the HP (ПГ-2: 60 / 70; the chiefs' 80 / 120 as before)
        boolean hp = EntityWardenSC.hpOf(NpcKindSC.SENTRY) == 60D && EntityWardenSC.hpOf(NpcKindSC.SENIOR_WARDEN) == 70D
                && EntityWardenSC.hpOf(NpcKindSC.CAPTAIN) == 80D && EntityWardenSC.hpOf(NpcKindSC.COMMANDANT) == 120D;
        check(hp, "wardens' suit (ПГ-2): HP by rank 60 / 70 / 80 / 120 " + hp);
    }

    private static void handbook() {
        StringBuilder bad = new StringBuilder();
        for (String id : ARTICLES) {
            BookEntry e = BookContent.byId(id);
            if (e == null) {
                bad.append(' ').append(id).append(" missing");
                continue;
            }
            if (e.chapter != BookChapter.WORLD || e.els.size() < 5) {
                bad.append(' ').append(id).append(" chapter ").append(e.chapter).append(" / ").append(e.els.size()).append(" elements");
            }
            String t = e.searchText();
            for (String raw : new String[]{"sc.manual.", "sc.book.", "sc.settle.", "sc.trade.", "sc.contract.", "sc.dim.", "sc.suit.", "tile.siliconage.",
                    "item.siliconage.", "entity.siliconage.", "%s", "%d"}) {
                if (t.contains(raw)) {
                    bad.append(' ').append(id).append(" raw '").append(raw).append('\'');
                }
            }
        }
        int sizes = rowsOf("world.settlements", 0), levels = rowsOf("world.trust", 0), violations = rowsOf("world.trust", 1), ranks = rowsOf("world.wardens", 0);
        boolean tables = sizes == SettlementSizeSC.SUBSTRATE_SIZES && levels == ReputationSC.LEVEL_KEYS.length && violations == ViolationRulesSC.KEYS.length
                && ranks == 4;                                             // Ф3: + the Captain and the Commandant
        int dealTables = 0;
        BookEntry tradeEntry = BookContent.byId("world.trade");
        for (BookEl el : tradeEntry == null ? new ArrayList<BookEl>() : tradeEntry.els) {
            dealTables += el.kind == BookEl.Kind.TABLE ? 1 : 0;
        }
        int professions = 0;
        for (NpcKindSC k : NpcKindSC.values()) {
            professions += !k.guard && !TradeTableSC.offers(k).isEmpty() ? 1 : 0;
        }
        boolean deals = dealTables == professions + 2;                     // + the prices by level, + the contracts
        // G (BookEntry.about): every new block, item and egg
        StringBuilder noG = new StringBuilder();
        g(noG, SettlementBlocksSC.core == null ? null : new ItemStack(SettlementBlocksSC.core), "world.settlements");
        g(noG, SettlementBlocksSC.showcase == null ? null : new ItemStack(SettlementBlocksSC.showcase, 1, BlockShowcaseSC.ETCH_BATH), "world.settlements");
        g(noG, SubstrateBlocksSC.rock == null ? null : new ItemStack(SubstrateBlocksSC.rock, 1, BookWorldSC.BRICK), "world.settlements");
        g(noG, SettlementBlocksSC.dock == null ? null : new ItemStack(SettlementBlocksSC.dock, 1, BlockDockSC.PLATE), "world.docks");
        g(noG, SettlementBlocksSC.dock == null ? null : new ItemStack(SettlementBlocksSC.dock, 1, BlockDockSC.BEACON), "world.docks");
        g(noG, SettlementBlocksSC.trim(com.sc.util.Material.HAFNIUM), "world.settlements");
        g(noG, SettlementBlocksSC.glass == null ? null : new ItemStack(SettlementBlocksSC.glass), "world.settlements");
        g(noG, SettlementItemsSC.chips(1), "world.trade");
        g(noG, MobItemsSC.combatLoot == null ? null : new ItemStack(MobItemsSC.combatLoot, 1, BookWorldSC.LOOT_STOLEN_CARGO), "world.trade");
        g(noG, SettlementItemsSC.capsules(1), "world.docks");
        g(noG, SettlementItemsSC.settlementMap == null ? null : new ItemStack(SettlementItemsSC.settlementMap), "world.docks");
        g(noG, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_TUNER), "world.tuners");
        g(noG, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_WARDEN), "world.wardens");
        BookEntry terrain = BookContent.byId("world.terrain");
        boolean brickRow = false;
        for (BookEl el : terrain == null ? new ArrayList<BookEl>() : terrain.els) {
            brickRow |= el.kind == BookEl.Kind.ITEM && el.stack != null && SubstrateBlocksSC.rock != null
                    && el.stack.getItem() == Item.getItemFromBlock(SubstrateBlocksSC.rock) && el.stack.getItemDamage() == BookWorldSC.BRICK;
        }
        // the names BookWorldSC keeps for package S's and T's appended metas
        boolean names = "settlementBrick".equals(BookWorldSC.ROCK_NAMES[BookWorldSC.BRICK]) && "stolenCargo".equals(BookWorldSC.LOOT_NAMES[BookWorldSC.LOOT_STOLEN_CARGO]);
        String brickMeta = staticInt(SubstrateBlocksSC.class, "SETTLEMENT_BRICK");
        boolean sMetas = SubstrateBlocksSC.ROCK_METAS == BookWorldSC.ROCK_NAMES.length && SubstrateBlocksSC.ROCK_METAS == 10       // Ф4: + monocrystal
                && "monocrystal".equals(BookWorldSC.ROCK_NAMES[SubstrateBlocksSC.MONOCRYSTAL]) && String.valueOf(BookWorldSC.BRICK).equals(brickMeta);
        ItemCombatLootSC.LootKind[] loot = ItemCombatLootSC.LootKind.values();
        boolean tMetas = loot.length == BookWorldSC.LOOT_NAMES.length && "stolenCargo".equals(loot[BookWorldSC.LOOT_STOLEN_CARGO].key)
                && "naturalWafer".equals(loot[loot.length - 1].key);         // Ф3 appended four after it, Ф4 five more (docs/plan-substrate-tech-f4.md §0)
        List<String> unresolved = BookWorldSC.unresolved();
        BookContent.invalidate();
        if (!unresolved.isEmpty()) {
            System.out.println("[SC-TEST] INFO settlement: the handbook still prints the plan's numbers for (public static constants of these names in"
                    + " packages N / T, or direct references in BookWorldSC): " + unresolved);
        }
        check(bad.length() == 0 && tables && deals && noG.length() == 0 && brickRow && names && sMetas && tMetas, "handbook (§10.2): the " + ARTICLES.length
                + " new WORLD articles build with no raw keys" + (bad.length() == 0 ? "" : " -" + bad) + "; tables: sizes " + sizes + ", levels " + levels
                + ", violations " + violations + ", ranks " + ranks + " " + tables + "; the trade article's tables " + dealTables + " (a deal table per"
                + " profession with deals - " + professions + " - plus prices and contracts) " + deals + "; G on the core, showcases, brick, dock, chip,"
                + " Stolen Cargo, capsule, map, both eggs" + (noG.length() == 0 ? "" : " - NOT:" + noG) + "; the brick in the terrain's blocks " + brickRow
                + "; ROCK_NAMES / LOOT_NAMES " + names + "; package S's rock metas (ROCK_METAS " + SubstrateBlocksSC.ROCK_METAS + ", SETTLEMENT_BRICK "
                + brickMeta + ") " + sMetas + ", package T's loot kinds (" + loot.length + ") " + tMetas);
    }

    /** The rows of the `index`-th table of article `id`, -1 none. */
    private static int rowsOf(String id, int index) {
        BookEntry e = BookContent.byId(id);
        int n = 0;
        for (BookEl el : e == null ? new ArrayList<BookEl>() : e.els) {
            if (el.kind == BookEl.Kind.TABLE && n++ == index) {
                return el.rows.size();
            }
        }
        return -1;
    }

    /** A public static int of `c` as text, "-" if it has none (another package's constant not written yet). */
    private static String staticInt(Class<?> c, String name) {
        try {
            return String.valueOf(c.getField(name).getInt(null));
        } catch (Throwable t) {
            return "-";
        }
    }

    /** G on `s` opens article `id` (an item not registered - listed). */
    private static void g(StringBuilder out, ItemStack s, String id) {
        if (s == null || s.getItem() == null) {
            out.append(" (not registered for ").append(id).append(')');
            return;
        }
        BookEntry e = BookContent.entryFor(s);
        if (e == null || !id.equals(e.id)) {
            out.append(' ').append(Item.itemRegistry.getNameForObject(s.getItem())).append(':').append(s.getItemDamage()).append(" -> ")
                    .append(e == null ? "nothing" : e.id);
        }
    }
}
