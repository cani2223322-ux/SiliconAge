package com.sc.debug;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.sc.arena.ArenaBlocksSC;
import com.sc.block.BlockRuinSC;
import com.sc.arena.ArenaDataSC;
import com.sc.arena.ArenaLayoutSC;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateNetSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateSignalSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.WorldProviderSubstrateSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.finale.FinaleItemsSC;
import com.sc.finale.FinaleRulesSC;
import com.sc.handler.ArmorNetSC;
import com.sc.init.ModItems;
import com.sc.lore.ItemJournalSC;
import com.sc.lore.JournalSC;
import com.sc.lore.SignalCacheSC;
import com.sc.lore.SignalDataSC;
import com.sc.lore.SignalRulesSC;
import com.sc.lore.WreckLayoutSC;
import com.sc.lore.WreckLootSC;
import com.sc.manual.BookChapter;
import com.sc.manual.BookContent;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.BookWorldSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.mob.TrophyTableSC;
import com.sc.mob.WardenRulesSC;
import com.sc.ruin.CampBlocksSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.caravan.CaravanRulesSC;
import com.sc.settle.caravan.CaravanRunPathSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;
import com.sc.util.SingularStationMath;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * PACKAGE D (docs/plan-substrate-tech-f5.md §11.1): Ф5's self-test (-Dsc.selftest, from SelfTestSC.run after SelfTestHazardSC) - the
 * pure decisions of Ф5 without a world, each section by itself (an unfinished package body fails only its own section): the config
 * (14 rules: requireSignal, caravans; customSky not a rule), SubstrateSignalSC.visible / filterDims, ArenaLayoutSC (one arena a region
 * on its main lines, never a Vault's crossing, the spawn's region far off, two arenas 2304+ apart, arenaAtChunk / arenaNear / isArena /
 * nearest agree, the pedestals / columns / console / lamps on the pit's disk and off the canyons' mouths, the box within the Ф1
 * strip), ArenaDataSC's NBT round trip, WardenRulesSC (maxHealth 600 / 960 / 1320 / 1680, the phases at 60% / 25%, overheated's 4 s in
 * every 15, coreResetDue, resetDue, lootEligible, lootDue, trophyMeta's shares, rescaled), JournalSC (every number 1..12 in a pool, №12
 * only the Warden's), WreckLayoutSC (determinism, never a settlement's / the spawn's / a non-plateau cell, clear of the lines, the arenas
 * and the Nodes, 20 ± 4% of the free plateau cells), SignalRulesSC (the day's share 15 ± 3%, crossingFor never an arena / the spawn's
 * ring, within the distance; SignalDataSC's NBT), CaravanRulesSC, FinaleRulesSC (the upkeep's halving, the schemes' mask), SingularScheme
 * (13, L / M unlockable) and SingularStationMath.cycle (the old two-argument walk unchanged; with a mask), SingularLevel's fourth tasks
 * (3.3 = 25, 5.3 = 1, the rest 0; LevelMessage with 16 values), the pool (26 names, no egg for 23..25, the weight tables 26 long), the
 * relics (5 metas) and the items and recipes, the presets (25 colours, unique), the texts, the packages' pure bodies (J: the Burnt
 * Coil's drops, the journals' source words, a wreck's chest, the Signal's cache; K: the caravan's start and route on 1440 domes; P:
 * the ПЭ-1М trophy's restoring, the finale's four deals, R_CARAVAN_DEAL) and the handbook (the thirteen new WORLD articles; the
 * cross-check of Ф1..Ф5: every creature of the pool named in some article, every block and item of the dimension G-mapped to one);
 * the arena record's and the Signal's saved-data names never a structure start's (one perWorldStorage file each). Output
 * "[SC-TEST] PASS / FAIL / INFO finale: ...".
 * <p>
 * Built on the core alone: the packages' bodies (A / B / J / K / P) are asked for by their fixed names; a body not written yet fails
 * its own section with what is missing, never another's.
 */
final class SelfTestFinaleSC {

    /** The thirteen Ф5 WORLD articles (SelfTestSubstrateSC counts the chapter with them). */
    static final String[] ARTICLES = BookWorldSC.F5_ARTICLES;
    private static final String[] SECTIONS = {"config", "signal filter", "arena layout", "arena data", "warden rules", "journals", "wreck layout",
            "signal rules", "caravan rules", "finale rules", "singular tasks", "pool", "relics", "presets", "texts", "lore bodies", "caravan path",
            "restore and deals", "handbook"};
    /** The seeds the layouts are sampled on. */
    private static final long[] SEEDS = {1234567L, -77L, 0x5C14L};
    /** Key prefixes of Ф5's texts: each in both languages with as many arguments, no Cyrillic and no «» in English. */
    private static final String[] PREFIXES = {"sc.bridge.refuse.noSignal", "entity.SiliconAge.breakdownWarden", "entity.SiliconAge.containmentCoil",
            "entity.SiliconAge.caravan", "item.siliconage.relic.breakdown", "sc.tooltip.relic.breakdown", "sc.manual.world.relic.breakdown",
            "item.siliconage.arenaCoordinator", "sc.tooltip.arenaCoordinator", "sc.arenaCoordinator.", "item.siliconage.journalSC", "sc.tooltip.journal",
            "sc.journal.", "item.siliconage.paintScheme", "sc.tooltip.paint.", "sc.paint.", "item.siliconage.lithoModule", "sc.tooltip.lithoModule",
            "item.siliconage.navRecord", "sc.tooltip.navRecord", "sc.navrecord.", "tile.siliconage.arena", "sc.tooltip.arena.", "sc.arena.",
            "tile.siliconage.ruin.burntCoil", "sc.tooltip.ruin.burntCoil", "sc.manual.world.ruin.burntCoil", "sc.singular.scheme.l", "sc.singular.scheme.m",
            "sc.fieldzone.preset.24", "sc.levelgui.task.3.3", "sc.levelgui.task.5.3", "sc.tooltip.armor.singular.task.3.3",
            "sc.tooltip.armor.singular.task.5.3", "sc.warden.", "sc.signal.", "sc.caravan.", "sc.trade.offer.cartArenaCoordinator",
            "sc.trade.offer.elderLithoModule", "sc.trade.offer.elderTunerScheme", "sc.trade.offer.armModWide", "sc.trade.result.caravanDeal",
            "sc.trade.refuse.caravanDeal", "sc.trade.row.caravanDeal", "sc.manual.world.lithographers", "sc.manual.world.arena", "sc.manual.world.warden",
            "sc.manual.world.journals", "sc.manual.world.wrecks", "sc.manual.world.signal", "sc.manual.world.caravans", "sc.manual.world.cosmetics",
            "sc.manual.world.lithoModule", "sc.manual.world.navrecord", "sc.manual.world.progress", "sc.manual.world.sky", "sc.manual.world.rule.signal",
            "sc.manual.world.rule.caravans", "sc.manual.world.rule.sky", "sc.manual.world.trophies.warden", "sc.manual.world.trade.f5",
            "sc.manual.world.elites.coordinator", "sc.manual.world.vaults.journals", "sc.manual.world.crumbs.signal", "sc.manual.world.crumbs.warden",
            "sc.manual.world.restore.line.pe1m", "sc.manual.world.restore.pe1m", "sc.manual.world.t.players", "sc.manual.world.t.numbers",
            "sc.manual.world.t.what", "sc.manual.world.t.upkeep", "sc.manual.world.t.withModule", "sc.manual.world.t.how", "sc.nei.obtain.wreck",
            "sc.nei.obtain.arena", "sc.nei.obtain.egg.warden", "sc.nei.obtain.breakdown", "sc.nei.obtain.coordinator", "sc.nei.obtain.journal",
            "sc.nei.obtain.paint.", "sc.nei.obtain.lithoModule", "sc.nei.obtain.navRecord", "sc.waila.warden", "sc.waila.coil",
            "sc.tooltip.trophy.howto.pe1m"};
    /**
     * The messages and the arguments their callers pass (docs/plan-substrate-tech-f5.md §11.3 and the packages' reports): the
     * Fragments a Key takes, the countdown's seconds, the Coordinator's direction / distance / x / z, the cooldown's days, the Signal's
     * direction / distance, the caravan's settlement (its Trust and the deal's days when defended; the master's name and goal), the
     * scheme's name, the journal's number and «where found», the tooltips' numbers, WAILA's lines of the boss and the coils.
     */
    private static final String[][] MESSAGE_ARGS = {{"sc.bridge.refuse.noSignal", "0"}, {"sc.arena.peaceful", "0"}, {"sc.arena.nokey", "1"},
            {"sc.arena.inserted", "0"}, {"sc.arena.countdown", "1"}, {"sc.arena.busy", "0"}, {"sc.arena.blocked", "0"}, {"sc.arena.reset", "0"},
            {"sc.arena.won", "0"}, {"sc.arena.noarena", "0"}, {"sc.arenaCoordinator.arena", "4"}, {"sc.arenaCoordinator.fight", "0"},
            {"sc.arenaCoordinator.keyed", "0"}, {"sc.arenaCoordinator.none", "0"}, {"sc.arenaCoordinator.offworld", "0"}, {"sc.warden.shielded", "0"},
            {"sc.warden.window", "0"}, {"sc.warden.phase2", "0"}, {"sc.warden.phase3", "0"}, {"sc.warden.reset.warn", "0"}, {"sc.warden.loot", "0"},
            {"sc.warden.loot.cooldown", "1"}, {"sc.warden.loot.pending", "0"}, {"sc.warden.journal", "0"}, {"sc.signal.start", "2"}, {"sc.signal.end", "0"},
            {"sc.signal.cache", "0"}, {"sc.caravan.coming", "1"}, {"sc.caravan.ambush", "0"}, {"sc.caravan.defended", "2"}, {"sc.caravan.lost", "0"},
            {"sc.caravan.arrived", "1"}, {"sc.paint.unlocked", "1"}, {"sc.paint.known", "1"}, {"sc.navrecord.learned", "0"}, {"sc.navrecord.known", "0"},
            {"sc.navrecord.open", "0"}, {"sc.trade.result.caravanDeal", "0"}, {"sc.trade.refuse.caravanDeal", "0"}, {"sc.trade.row.caravanDeal", "0"},
            {"item.siliconage.journalSC.name", "1"}, {"sc.tooltip.lithoModule.details", "1"}, {"sc.tooltip.ruin.burntCoil.howto", "2"},
            {"sc.tooltip.relic.keyFragment.howto", "1"}, {"sc.manual.world.relic.keyFragment", "2"}, {"sc.manual.world.relic.breakdownKey", "2"},
            {"sc.manual.world.relic.breakdownMatrix", "2"}, {"sc.tooltip.armor.singular.task.3.3", "2"}, {"sc.tooltip.armor.singular.task.5.3", "2"},
            {"sc.nei.obtain.journal", "2"}, {"sc.caravan.master.info", "2"}, {"sc.signal.cache.quiet", "0"}, {"sc.warden.loot.kept", "0"},
            {"sc.tooltip.relic.breakdownKey.howto", "1"}, {"sc.tooltip.paint.lithographer.howto", "1"}, {"sc.tooltip.journal.where", "1"},
            {"sc.waila.warden.phase", "1"}, {"sc.waila.warden.shielded", "2"}, {"sc.waila.warden.window", "1"}, {"sc.waila.warden.highlight", "1"},
            {"sc.waila.warden.reset", "1"}, {"sc.waila.coil.reserve", "2"}, {"sc.waila.coil.point", "1"}, {"sc.tooltip.trophy.howto.pe1m", "2"}};

    private SelfTestFinaleSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "finale: " + what);
    }

    private static void info(String what) {
        System.out.println("[SC-TEST] INFO finale: " + what);
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

    private static void section(int i) throws Exception {
        switch (i) {
            case 0: config(); break;
            case 1: signalFilter(); break;
            case 2: arenaLayout(); break;
            case 3: arenaData(); break;
            case 4: wardenRules(); break;
            case 5: journals(); break;
            case 6: wreckLayout(); break;
            case 7: signalRules(); break;
            case 8: caravanRules(); break;
            case 9: finaleRules(); break;
            case 10: singularTasks(); break;
            case 11: pool(); break;
            case 12: relics(); break;
            case 13: presets(); break;
            case 14: texts(); break;
            case 15: loreBodies(); break;
            case 16: caravanPath(); break;
            case 17: restoreAndDeals(); break;
            default: handbook(); break;
        }
    }

    // ------------------------------------------------------------------ 1. the config (§2.1, ПК-21)

    private static void config() {
        Set<String> sent = new TreeSet<String>();
        for (Field f : SubstrateNetSC.ruleFields()) {
            sent.add(f.getName());
        }
        boolean rules = sent.size() == 14 && sent.contains("requireSignal") && sent.contains("caravans") && !sent.contains("customSky")
                && !SubstrateConfigSC.requireSignal && SubstrateConfigSC.caravans;
        String sky;
        try {
            Field f = SubstrateConfigSC.class.getDeclaredField("customSky");
            sky = !Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == boolean.class ? "private" : "NOT private";
        } catch (NoSuchFieldException e) {
            sky = "missing";
        }
        check(rules && "private".equals(sky) && !SubstrateConfigSC.customSky(), "config (§2.1, ПК-21): " + sent.size() + " rules the server sends (14),"
                + " requireSignal " + SubstrateConfigSC.requireSignal + " (false) / caravans " + SubstrateConfigSC.caravans + " (true) " + rules + "; customSky "
                + sky + ", off by default " + !SubstrateConfigSC.customSky() + ", not a rule" + (rules ? "" : " (the dimension section of the test config edited?)"));
    }

    // ------------------------------------------------------------------ 2. requireSignal (ПВ-13)

    private static void signalFilter() {
        StringBuilder bad = new StringBuilder();
        for (int m = 0; m < 8; m++) {
            boolean require = (m & 1) != 0, has = (m & 2) != 0, inSubstrate = (m & 4) != 0;
            if (SubstrateSignalSC.visible(require, has, inSubstrate) != (!require || has || inSubstrate)) {
                bad.append(' ').append(m);
            }
        }
        String filter;
        if (!SubstrateSC.active()) {
            filter = "skipped - the dimension is off (" + SubstrateSC.offReason() + ")";
        } else {
            NBTTagList dims = new NBTTagList();
            for (int id : new int[]{0, -1, SubstrateSC.dimId(), 1}) {
                NBTTagCompound d = new NBTTagCompound();
                d.setInteger("id", id);
                dims.appendTag(d);
            }
            boolean was = SubstrateConfigSC.requireSignal;
            try {
                SubstrateConfigSC.requireSignal = true;
                NBTTagList home = SubstrateSignalSC.filterDims(dims, null, 0), there = SubstrateSignalSC.filterDims(dims, null, SubstrateSC.dimId());
                boolean refused = SubstrateSignalSC.refuses(null, SubstrateSC.dimId(), 0) && !SubstrateSignalSC.refuses(null, 0, 0)
                        && !SubstrateSignalSC.refuses(null, SubstrateSC.dimId(), SubstrateSC.dimId());
                SubstrateConfigSC.requireSignal = false;
                NBTTagList open = SubstrateSignalSC.filterDims(dims, null, 0);
                boolean gone = home.tagCount() == 3 && !hasDim(home, SubstrateSC.dimId()) && hasDim(home, 0) && hasDim(home, -1) && hasDim(home, 1);
                filter = (gone && refused && there == dims && open == dims && dims.tagCount() == 4 ? "ok" : "BAD") + " (the rule on, no record: " + home.tagCount()
                        + " of 4 left, the Substrate gone " + !hasDim(home, SubstrateSC.dimId()) + ", an end there refused " + refused + "; a bridge in the"
                        + " Substrate keeps it " + (there == dims) + "; the rule off - the list untouched " + (open == dims) + ")";
            } finally {
                SubstrateConfigSC.requireSignal = was;
            }
        }
        boolean nobody = !SubstrateSignalSC.refuses(null, 0, 0) && !SubstrateSignalSC.knows(null) && !SubstrateSignalSC.grant(null);
        check(bad.length() == 0 && !filter.startsWith("BAD") && nobody && "scSi14Nav".equals(SubstrateSignalSC.NBT_KNOWN), "signal filter (ПВ-13):"
                + " visible = !require || record || in the Substrate over all 8 cases" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; filterDims " + filter
                + "; an end in the Overworld never refused, a null player knows nothing and is granted nothing " + nobody);
    }

    private static boolean hasDim(NBTTagList l, int id) {
        for (int i = 0; i < l.tagCount(); i++) {
            if (l.getCompoundTagAt(i).getInteger("id") == id) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 3. ArenaLayoutSC (§4.1, ПУ-8..ПУ-10)

    private static void arenaLayout() {
        int grid = SubstrateTerrainSC.GRID;
        StringBuilder bad = new StringBuilder();
        int arenas = 0, spawnArenas = 0, empty = 0;
        double nearestSpawn = Double.MAX_VALUE, closestPair = Double.MAX_VALUE;
        for (long seed : SEEDS) {
            List<int[]> all = new ArrayList<int[]>();
            for (int ri = -8; ri <= 8; ri++) {
                for (int rj = -8; rj <= 8; rj++) {
                    int[] a = ArenaLayoutSC.arenaOf(seed, ri, rj);
                    if (a == null) {
                        empty++;
                        continue;
                    }
                    arenas++;
                    all.add(a);
                    boolean spawn = ri == 0 && rj == 0;
                    int from = spawn ? ArenaLayoutSC.SPAWN_REGION_FROM : ArenaLayoutSC.MARGIN, to = ArenaLayoutSC.MARGIN + ArenaLayoutSC.SPAN - 1;
                    int li = a[0] - ri * ArenaLayoutSC.REGION, lj = a[1] - rj * ArenaLayoutSC.REGION;
                    if (li < from || li > to || lj < from || lj > to || ArenaLayoutSC.regionOf(a[0]) != ri || ArenaLayoutSC.regionOf(a[1]) != rj) {
                        bad.append(" lines ").append(a[0]).append(',').append(a[1]);
                    }
                    if (VaultLayoutSC.hasVault(seed, a[0], a[1])) {
                        bad.append(" vault ").append(a[0]).append(',').append(a[1]);
                    }
                    if (spawn) {
                        spawnArenas++;
                        double dx = ArenaLayoutSC.centre(a[0]) - WorldProviderSubstrateSC.SPAWN_X, dz = ArenaLayoutSC.centre(a[1]) - WorldProviderSubstrateSC.SPAWN_Z;
                        nearestSpawn = Math.min(nearestSpawn, Math.sqrt(dx * dx + dz * dz));
                    }
                    // the four look-ups agree (§4.1)
                    int cx = a[0] * (grid >> 4), cz = a[1] * (grid >> 4), x = ArenaLayoutSC.centre(a[0]), z = ArenaLayoutSC.centre(a[1]);
                    if (!same(ArenaLayoutSC.arenaAtChunk(seed, cx, cz), a) || ArenaLayoutSC.arenaAtChunk(seed, cx + 1, cz) != null
                            || ArenaLayoutSC.arenaAtChunk(seed, cx, cz - 1) != null) {
                        bad.append(" atChunk ").append(a[0]).append(',').append(a[1]);
                    }
                    if (!same(ArenaLayoutSC.arenaNear(seed, x + 5, z - 7, 0), a) || !same(ArenaLayoutSC.arenaNear(seed, x - ArenaLayoutSC.HALF, z + ArenaLayoutSC.HALF, 0), a)
                            || ArenaLayoutSC.arenaNear(seed, x + ArenaLayoutSC.HALF + 1, z, 0) != null
                            || !same(ArenaLayoutSC.arenaNear(seed, x + ArenaLayoutSC.HALF + 1, z, 1), a)) {
                        bad.append(" near ").append(a[0]).append(',').append(a[1]);
                    }
                    if (!ArenaLayoutSC.isArena(seed, a[0], a[1]) || ArenaLayoutSC.isArena(seed, a[0] + 1, a[1]) || ArenaLayoutSC.isArena(seed, a[0], a[1] - 1)) {
                        bad.append(" isArena ").append(a[0]).append(',').append(a[1]);
                    }
                    if (Math.abs(ri) <= 7 && Math.abs(rj) <= 7 && !same(ArenaLayoutSC.nearest(seed, x + 3, z - 9, 1), a)) {
                        bad.append(" nearest ").append(a[0]).append(',').append(a[1]);
                    }
                }
            }
            for (int p = 0; p < all.size(); p++) {
                for (int q = p + 1; q < all.size(); q++) {
                    double dx = (all.get(p)[0] - all.get(q)[0]) * (double) grid, dz = (all.get(p)[1] - all.get(q)[1]) * (double) grid;
                    closestPair = Math.min(closestPair, Math.sqrt(dx * dx + dz * dz));
                }
            }
        }
        // the spawn region's arena: no closer than its first line on both axes allows (the plan's «2 km»: from the spawn (128, 128) that is ~1991)
        double spawnMin = Math.sqrt(2D) * (ArenaLayoutSC.SPAWN_REGION_FROM * grid - WorldProviderSubstrateSC.SPAWN_X) - 1D;
        int apart = (ArenaLayoutSC.REGION - ArenaLayoutSC.SPAN + 1) * grid;
        // inside: the pedestals, the columns, the console, the lamps on the pit's disk; the pedestals and the console off the canyons' floor
        // bands (|d| > 7, half the widest canyon), a column's block never in a mouth (within a band in the last 8 blocks before the rim)
        int half = SubstrateTerrainSC.CANYON_WIDTH_MAX / 2;
        StringBuilder inside = new StringBuilder();
        Set<Long> columnBlocks = new HashSet<Long>();
        for (int k = 0; k < ArenaLayoutSC.COILS; k++) {
            int[] s = ArenaLayoutSC.coilSpot(k);
            if (!ArenaLayoutSC.inPit(s[0], s[1]) || Math.abs(s[0]) <= half || Math.abs(s[1]) <= half || ArenaLayoutSC.inColumn(s[0], s[1])) {
                inside.append(" pedestal ").append(k);
            }
        }
        for (int k = 0; k < ArenaLayoutSC.COLUMNS; k++) {
            int[] c = ArenaLayoutSC.columnSpot(k);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int bx = c[0] + dx, bz = c[1] + dz;
                    boolean band = Math.min(Math.abs(bx), Math.abs(bz)) <= half, mouth = band && Math.max(Math.abs(bx), Math.abs(bz)) >= ArenaLayoutSC.PIT_R - 8;
                    if (!ArenaLayoutSC.inPit(bx, bz) || mouth || !ArenaLayoutSC.inColumn(bx, bz) || !columnBlocks.add(((long) bx << 32) ^ (bz & 0xFFFFFFFFL))) {
                        inside.append(" column ").append(k);
                    }
                }
            }
        }
        if (!ArenaLayoutSC.inPit(ArenaLayoutSC.CONSOLE_DX, ArenaLayoutSC.CONSOLE_DZ) || Math.abs(ArenaLayoutSC.CONSOLE_DX) <= half
                || ArenaLayoutSC.inColumn(ArenaLayoutSC.CONSOLE_DX, ArenaLayoutSC.CONSOLE_DZ)) {
            inside.append(" console");
        }
        for (int k = 0; k < ArenaLayoutSC.LAMPS; k++) {
            int[] l = ArenaLayoutSC.lampSpot(k);
            if (!ArenaLayoutSC.inPit(l[0], l[1]) || l[0] * l[0] + l[1] * l[1] <= ArenaLayoutSC.COLUMN_R * ArenaLayoutSC.COLUMN_R) {
                inside.append(" lamp ").append(k);
            }
        }
        boolean box = ArenaLayoutSC.HALF <= SubstrateCellsSC.INNER_MARGIN && ArenaLayoutSC.RIM_R == ArenaLayoutSC.HALF && ArenaLayoutSC.PIT_R < ArenaLayoutSC.RIM_R
                && ArenaLayoutSC.onRim(ArenaLayoutSC.PIT_R + 1, 0) && !ArenaLayoutSC.onRim(ArenaLayoutSC.PIT_R, 0) && ArenaLayoutSC.inPit(ArenaLayoutSC.PIT_R, 0);
        boolean counts = columnBlocks.size() == 4 * ArenaLayoutSC.COLUMNS && ArenaLayoutSC.floorY(SEEDS[0], 3, 5) == SubstrateTerrainSC.nodeFloorY(SEEDS[0], 3, 5)
                && ArenaLayoutSC.key(1, 2) != ArenaLayoutSC.key(2, 1) && ArenaLayoutSC.floorDiv(-1, 16) == -1 && ArenaLayoutSC.floorDiv(16, 16) == 1;
        boolean ok = bad.length() == 0 && spawnArenas >= 1 && nearestSpawn >= spawnMin && closestPair >= apart && inside.length() == 0 && box && counts;
        check(ok, "arena layout (ArenaLayoutSC, ПУ-8..ПУ-10): " + arenas + " arenas in (-8..8)² regions of " + SEEDS.length + " seeds (" + empty + " regions none),"
                + " one a region on lines " + ArenaLayoutSC.MARGIN + ".." + (ArenaLayoutSC.MARGIN + ArenaLayoutSC.SPAN - 1) + " (the spawn's " + ArenaLayoutSC.SPAWN_REGION_FROM
                + "..), never a Vault's crossing; arenaAtChunk / arenaNear / isArena / nearest agree" + (bad.length() == 0 ? "" : " - BAD:"
                + bad.substring(0, Math.min(200, bad.length()))) + "; the spawn region's nearest " + Math.round(nearestSpawn) + " from (" + WorldProviderSubstrateSC.SPAWN_X
                + ", " + WorldProviderSubstrateSC.SPAWN_Z + ") (>= " + Math.round(spawnMin) + ", the plan's \"2 km\"), two arenas >= " + Math.round(closestPair) + " ("
                + apart + "); the pedestals, columns (" + columnBlocks.size() + " blocks, none shared), console and lamps on the pit's disk, off the canyons'"
                + " mouths" + (inside.length() == 0 ? "" : " - NOT:" + inside) + "; the box ±" + ArenaLayoutSC.HALF + " within the Ф1 strip " + SubstrateCellsSC.INNER_MARGIN
                + " " + box + " " + counts);
    }

    private static boolean same(int[] a, int[] b) {
        return a != null && b != null && a[0] == b[0] && a[1] == b[1];
    }

    // ------------------------------------------------------------------ 4. ArenaDataSC (§4.3, ПУ-11)

    private static void arenaData() {
        ArenaDataSC d = new ArenaDataSC();
        ArenaDataSC.Arena a = d.make(-3, 37, 31, true), e = d.make(100, -200, 25, false);
        a.state = ArenaDataSC.KEYED;
        a.keyIn = true;
        a.fightId = 1234567890123L;
        a.bossUuid = "00000000-0000-0000-0000-00000000abcd";
        a.fightStartedAt = 777L;
        a.lastPlayerAt = 888L;
        a.maxPlayers = 3;
        a.addCrack(-760, 32, 9480);
        a.addCrack(-770, 32, 9475);
        a.extra.setString("A", "x");
        ArenaDataSC.Arena again = d.make(-3, 37, 99, false);
        NBTTagCompound t = new NBTTagCompound();
        d.writeToNBT(t);
        ArenaDataSC back = new ArenaDataSC();
        back.readFromNBT(t);
        ArenaDataSC.Arena b = back.arena(ArenaLayoutSC.regionOf(-3), ArenaLayoutSC.regionOf(37)), eb = back.arenaAt(100, -200);
        boolean kept = again == a && again.floorY == 31 && again.state == ArenaDataSC.KEYED;
        boolean round = b != null && b.i == -3 && b.j == 37 && b.ri == ArenaLayoutSC.regionOf(-3) && b.rj == ArenaLayoutSC.regionOf(37) && b.x == -3 * SubstrateTerrainSC.GRID
                && b.z == 37 * SubstrateTerrainSC.GRID && b.floorY == 31 && b.state == ArenaDataSC.KEYED && b.keyIn && b.fightId == 1234567890123L
                && a.bossUuid.equals(b.bossUuid) && b.fightStartedAt == 777L && b.lastPlayerAt == 888L && b.maxPlayers == 3 && b.cracks().size() == 2
                && b.cracks().get(1)[0] == -770 && b.cracks().get(1)[2] == 9475 && "x".equals(b.extra.getString("A"))
                && eb != null && eb.state == ArenaDataSC.EMPTY && !eb.keyIn && back.all().size() == 2 && back.arenaAt(-3, 38) == null;
        List<int[]> taken = b == null ? new ArrayList<int[]>() : b.takeCracks();
        boolean take = taken.size() == 2 && b != null && b.cracks().isEmpty();
        boolean states = ArenaDataSC.EMPTY == 0 && ArenaDataSC.READY == 1 && ArenaDataSC.FIGHT == 2 && ArenaDataSC.KEYED == 3 && e.state == ArenaDataSC.EMPTY;
        // the Substrate's perWorldStorage holds the saved data and the vanilla structures' files side by side (MapStorage keys them by
        // name alone): a record named as a structure start would be handed the other class - a ClassCastException in chunk generation
        String clash = storageClash();
        check(kept && round && take && states && clash.length() == 0, "arena data (ArenaDataSC, ПУ-11): make keeps an existing record (floor and state) "
                + kept + "; NBT there and back - every field, the cracks, extra, the EMPTY one " + round + "; takeCracks empties them " + take + "; the states"
                + " 0..3 " + states + "; the records' names " + ArenaDataSC.NAME + " / " + SignalDataSC.NAME + " apart from every structure start of the"
                + " Substrate's storage" + (clash.length() == 0 ? "" : " - CLASH:" + clash));
    }

    /** Every WorldSavedData name of the Substrate's perWorldStorage that equals a structure start's (the same file, two classes). */
    private static String storageClash() {
        String[] data = {ArenaDataSC.NAME, SignalDataSC.NAME, SubstrateCellsSC.DATA, com.sc.hazard.StormDataSC.NAME, com.sc.mob.RareRegistrySC.NAME,
                com.sc.item.PlacedBlocksSC.NAME};
        String[] starts = {com.sc.arena.MapGenArenaSC.START_NAME, com.sc.lore.MapGenWreckSC.START_NAME, com.sc.ruin.MapGenDeadNodeSC.START_NAME,
                com.sc.ruin.MapGenLithographSC.START_NAME, com.sc.ruin.MapGenCampSC.START_NAME, com.sc.ruin.MapGenVaultSC.START_NAME,
                com.sc.settle.gen.MapGenSettlementSC.START_NAME};
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<String>();
        for (String n : starts) {
            seen.add(n);
        }
        for (String n : data) {
            if (!seen.add(n)) {
                sb.append(' ').append(n);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 5. WardenRulesSC (§5, ПС-50..ПС-58, ПТ-21..ПТ-24)

    private static void wardenRules() {
        boolean hp = WardenRulesSC.maxHealth(1) == 600F && WardenRulesSC.maxHealth(2) == 960F && Math.abs(WardenRulesSC.maxHealth(3) - 1320F) < 0.01F
                && Math.abs(WardenRulesSC.maxHealth(4) - 1680F) < 0.01F && WardenRulesSC.maxHealth(0) == 600F;
        boolean phases = WardenRulesSC.phaseOf(600.1F, 1000F) == WardenRulesSC.PHASE_HOLD && WardenRulesSC.phaseOf(600F, 1000F) == WardenRulesSC.PHASE_BREAKDOWN
                && WardenRulesSC.phaseOf(250.1F, 1000F) == WardenRulesSC.PHASE_BREAKDOWN && WardenRulesSC.phaseOf(250F, 1000F) == WardenRulesSC.PHASE_OVERLOAD
                && WardenRulesSC.phaseOf(1000F, 1000F) == WardenRulesSC.PHASE_HOLD && WardenRulesSC.PHASE_HOLD == 1 && WardenRulesSC.PHASE_OVERLOAD == 3;
        int hot = 0, first = -1;
        for (long t = 0; t < 300; t++) {
            if (WardenRulesSC.overheated(t)) {
                hot++;
                first = first < 0 ? (int) t : first;
            }
        }
        boolean heat = hot == 80 && first == 220 && WardenRulesSC.overheated(520) && !WardenRulesSC.overheated(519) && !WardenRulesSC.overheated(300)
                && !WardenRulesSC.overheated(-1);
        boolean reset = !WardenRulesSC.coreResetDue(1199) && WardenRulesSC.coreResetDue(1200) && !WardenRulesSC.resetDue(1000L, 6999L)
                && WardenRulesSC.resetDue(1000L, 7000L) && WardenRulesSC.resetDue(-6000L, 0L);
        // lootEligible keeps a 0.0001 tolerance for the summed float shares: 0.0999 counts as 10%, 0.0998 doesn't
        boolean loot = !WardenRulesSC.lootEligible(0.0998F) && WardenRulesSC.lootEligible(0.0999F) && WardenRulesSC.lootEligible(0.1F) && WardenRulesSC.lootEligible(0.6F)
                && WardenRulesSC.lootDue(false, 0L, 5L) && !WardenRulesSC.lootDue(true, 10L, 12L) && WardenRulesSC.lootDue(true, 10L, 13L);
        boolean other = WardenRulesSC.windowOpens(0) && !WardenRulesSC.windowOpens(1) && WardenRulesSC.waveSize(1) == 2 && WardenRulesSC.waveSize(3) == 4
                && WardenRulesSC.highlightTicks(80) == 120 && Math.abs(WardenRulesSC.rescaled(300F, 600F, 960F) - 480F) < 0.01F
                && WardenRulesSC.rescaled(600F, 600F, 960F) == 960F && WardenRulesSC.rescaled(5F, 0F, 960F) == 960F;
        // the trophy's shares on a sample (one roll a player): 5 / 3 / 2 %, x the multiplier; the metas 13, 19, 24
        Random r = new Random(1414L);
        int n = 200000, chest = 0, blade = 0, pe1m = 0, none = 0, chest2 = 0;
        for (int k = 0; k < n; k++) {
            float roll = r.nextFloat();
            int m = WardenRulesSC.trophyMeta(roll, 1F);
            chest += m == 13 ? 1 : 0;
            blade += m == 19 ? 1 : 0;
            pe1m += m == TrophyTableSC.META_PE1M ? 1 : 0;
            none += m == -1 ? 1 : 0;
            chest2 += WardenRulesSC.trophyMeta(roll, 2F) == 13 ? 1 : 0;
        }
        boolean shares = near(chest, n, 0.05F, 0.004F) && near(blade, n, 0.03F, 0.003F) && near(pe1m, n, 0.02F, 0.003F) && chest + blade + pe1m + none == n
                && near(chest2, n, 0.10F, 0.005F) && WardenRulesSC.trophyMeta(0.5F, 0F) == -1 && TrophyTableSC.META_PE1M == 24;
        boolean nums = WardenRulesSC.WIDTH == 1.4F && WardenRulesSC.HEIGHT == 3.5F && WardenRulesSC.SCALE_RADIUS == 48 && WardenRulesSC.COILS == 4
                && WardenRulesSC.COIL_OFF_TICKS == 300 && WardenRulesSC.WINDOW_TICKS == 200 && WardenRulesSC.COIL_RESERVE == 100F && WardenRulesSC.SILENCE_RADIUS == 16
                && WardenRulesSC.CORE_RESET_DAMAGE == 20F && WardenRulesSC.RESET_AWAY_TICKS == 6000 && WardenRulesSC.LOOT_COOLDOWN_DAYS == 3
                && WardenRulesSC.FIRST_KILL_JOURNAL == JournalSC.COUNT && WardenRulesSC.PROGRESS_POINTS == 500 && "W".equals(WardenRulesSC.EXTRA);
        check(hp && phases && heat && reset && loot && other && shares && nums, "warden rules (WardenRulesSC, ПС-50..ПС-56, ПТ-21..ПТ-23): HP 600 / 960 / 1320 /"
                + " 1680 by players " + hp + "; the phases at 60.01% / 60% / 25.01% / 25% " + phases + "; overheated " + hot + " ticks of 300 from " + first
                + " (80 from 220) " + heat + "; coreResetDue 1199 / 1200, resetDue 6000 ticks " + reset + "; lootEligible 0.0998 / 0.0999 / 0.1, lootDue (none / 2 days /"
                + " 3 days) " + loot + "; the window, the waves, x1.5, rescaled " + other + "; trophyMeta on " + n + " rolls - chestplate " + pct(chest, n) + "%, blade "
                + pct(blade, n) + "%, ПЭ-1М " + pct(pe1m, n) + "% (5 / 3 / 2), x2 - chestplate " + pct(chest2, n) + "% " + shares + "; the plan's numbers " + nums);
    }

    private static boolean near(int count, int n, float share, float tol) {
        return Math.abs(count / (float) n - share) <= tol;
    }

    private static String pct(int count, int n) {
        return String.valueOf(Math.round(count * 1000F / n) / 10F);
    }

    // ------------------------------------------------------------------ 6. JournalSC (§6.1, ПЛ-1, ПЛ-2)

    private static void journals() {
        StringBuilder bad = new StringBuilder();
        for (int number = 1; number <= JournalSC.COUNT; number++) {
            int sources = 0;
            boolean other = false;
            for (int src = 0; src < JournalSC.POOLS.length; src++) {
                for (int x : JournalSC.POOLS[src]) {
                    if (x == number) {
                        sources++;
                        other |= src != JournalSC.SRC_WARDEN;
                    }
                }
            }
            if (sources == 0 || number == JournalSC.COUNT && other) {
                bad.append(" №").append(number).append(sources == 0 ? ":nowhere" : ":not only the Warden's");
            }
            if (JournalSC.numberOf(JournalSC.metaOf(number)) != number || JournalSC.metaOf(number) != number - 1) {
                bad.append(" №").append(number).append(":meta");
            }
        }
        boolean pools = Arrays.equals(JournalSC.POOLS[JournalSC.SRC_WARDEN], new int[]{12}) && JournalSC.POOLS.length == 7 && JournalSC.PAGES.length == JournalSC.COUNT
                && JournalSC.numberOf(-1) == 1 && JournalSC.numberOf(99) == 1 && JournalSC.chance(99) == 0F && JournalSC.pick(99, new Random(1)) == 0;
        Random r = new Random(7L);
        boolean rolls = true;
        for (int k = 0; k < 200; k++) {
            int got = JournalSC.roll(JournalSC.SRC_SIGNAL, r), wreck = JournalSC.roll(JournalSC.SRC_WRECK, r), never = JournalSC.roll(99, r);
            rolls &= got == 11 && (wreck == 9 || wreck == 10) && never == 0;
        }
        boolean chances = JournalSC.chance(JournalSC.SRC_NODE) == 0.06F && JournalSC.chance(JournalSC.SRC_LITHO) == 0.10F && JournalSC.chance(JournalSC.SRC_VAULT) == 0.30F
                && JournalSC.chance(JournalSC.SRC_SENTINEL) == 0.5F && JournalSC.chance(JournalSC.SRC_WARDEN) == 1F;
        check(bad.length() == 0 && pools && rolls && chances, "journals (JournalSC, ПЛ-2): every number 1..12 in some pool, №12 only the Warden's, metaOf /"
                + " numberOf" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; the pools, an unknown source / meta " + pools + "; roll with a chance of 1 (the Signal,"
                + " the wreck) always, of 0 never " + rolls + "; the chances 6 / 10 / 30 / 50 / 100% " + chances);
    }

    // ------------------------------------------------------------------ 7. WreckLayoutSC (§6.2, ПЛ-3)

    private static void wreckLayout() {
        StringBuilder bad = new StringBuilder();
        int plateau = 0, with = 0;
        long seed = SEEDS[0];
        for (int cx = -32; cx < 32; cx++) {
            for (int cz = -32; cz < 32; cz++) {
                WreckLayoutSC.Site s = WreckLayoutSC.site(seed, cx, cz), t = WreckLayoutSC.site(seed, cx, cz);
                boolean free = !(cx == 0 && cz == 0) && SubstrateCellsSC.pureKind(seed, cx, cz) == BiomeSubstrateSC.PLATEAUS && !settlementCell(seed, cx, cz);
                plateau += free ? 1 : 0;
                if (s == null) {
                    if (t != null) {
                        bad.append(" determinism ").append(cx).append(',').append(cz);
                    }
                    continue;
                }
                with++;
                if (t == null || t.x != s.x || t.z != s.z || t.y != s.y || t.radius != s.radius || t.seed != s.seed) {
                    bad.append(" determinism ").append(cx).append(',').append(cz);
                }
                if (!free) {
                    bad.append(" cell ").append(cx).append(',').append(cz);
                }
                if (s.x >> 8 != cx || s.z >> 8 != cz || s.radius < WreckLayoutSC.RING_MIN || s.radius > WreckLayoutSC.RING_MAX || s.lean < 1
                        || s.lean > WreckLayoutSC.TILT_MAX || s.axis < 0 || s.axis > 1) {
                    bad.append(" shape ").append(cx).append(',').append(cz);
                }
                int lines = Math.min(SubstrateTerrainSC.distanceToLine(s.x), SubstrateTerrainSC.distanceToLine(s.z)) - WreckLayoutSC.HALF;
                if (lines <= 30 || lines <= RuinLayoutSC.NODE_HALF || ArenaLayoutSC.arenaNear(seed, s.x, s.z, WreckLayoutSC.HALF) != null) {
                    bad.append(" clear ").append(cx).append(',').append(cz).append('(').append(lines).append(')');
                }
                if (!same(WreckLayoutSC.siteAtChunk(seed, s.x >> 4, s.z >> 4), s) || WreckLayoutSC.siteNear(seed, s.x + WreckLayoutSC.HALF, s.z, 0) == null
                        || WreckLayoutSC.siteNear(seed, s.x + WreckLayoutSC.HALF + 1, s.z, 0) != null) {
                    bad.append(" lookups ").append(cx).append(',').append(cz);
                }
            }
        }
        float share = plateau == 0 ? 0F : with / (float) plateau;
        boolean ok = bad.length() == 0 && plateau > 300 && Math.abs(share - WreckLayoutSC.CHANCE_PCT / 100F) <= 0.04F && WreckLayoutSC.site(seed, 0, 0) == null;
        check(ok, "wreck layout (WreckLayoutSC, ПЛ-3): 4096 cells - " + with + " wrecks in " + plateau + " free plateau cells = " + Math.round(share * 1000F) / 10F
                + "% (" + WreckLayoutSC.CHANCE_PCT + " ± 4), deterministic, never the spawn's / a settlement's / a non-plateau cell, the box more than 30 from"
                + " the lines (the canyon belt, the Nodes) and off the arenas, the look-ups agree" + (bad.length() == 0 ? "" : " - BAD:" + bad.substring(0,
                Math.min(200, bad.length()))));
    }

    private static boolean same(WreckLayoutSC.Site a, WreckLayoutSC.Site b) {
        return a != null && b != null && a.x == b.x && a.z == b.z;
    }

    /** A settlement's site (its region's) has its middle in this cell (WreckLayoutSC's own rule). */
    private static boolean settlementCell(long seed, int cellX, int cellZ) {
        SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, cellX >> 1, cellZ >> 1);
        return s != null && s.x >> 8 == cellX && s.z >> 8 == cellZ;
    }

    // ------------------------------------------------------------------ 8. SignalRulesSC (§7, ПЛ-5, ПЛ-6)

    private static void signalRules() {
        int days = 2000, rolls = 0;
        StringBuilder bad = new StringBuilder();
        long seed = SEEDS[1];
        Random r = new Random(5L);
        double minD = Double.MAX_VALUE, maxD = 0;
        int found = 0;
        for (long day = 0; day < days; day++) {
            rolls += SignalRulesSC.rolls(seed, day) ? 1 : 0;
            int start = SignalRulesSC.startOf(seed, day);
            if (start < SignalRulesSC.START_MIN || start > SignalRulesSC.START_MAX || start != SignalRulesSC.startOf(seed, day)) {
                bad.append(" start ").append(day);
            }
            int px = r.nextInt(6000) - 3000, pz = r.nextInt(6000) - 3000;
            int[] c = SignalRulesSC.crossingFor(seed, day, px, pz);
            if (c == null) {
                continue;
            }
            found++;
            if (Math.abs(c[0]) <= SignalRulesSC.SPAWN_RING && Math.abs(c[1]) <= SignalRulesSC.SPAWN_RING || ArenaLayoutSC.isArena(seed, c[0], c[1])) {
                bad.append(" crossing ").append(c[0]).append(',').append(c[1]);
            }
            double dx = c[0] * (double) SubstrateTerrainSC.GRID - px, dz = c[1] * (double) SubstrateTerrainSC.GRID - pz, d = Math.sqrt(dx * dx + dz * dz);
            minD = Math.min(minD, d);
            maxD = Math.max(maxD, d);
            if (d < SignalRulesSC.DIST_MIN - 256 || d > SignalRulesSC.DIST_MAX + 256) {
                bad.append(" distance ").append(Math.round(d));
            }
            int[] again = SignalRulesSC.crossingFor(seed, day, px, pz);
            if (again == null || again[0] != c[0] || again[1] != c[1]) {
                bad.append(" determinism ").append(day);
            }
        }
        float share = rolls / (float) days;
        boolean patrol = SignalRulesSC.patrolSize(1) == 3 && SignalRulesSC.patrolSize(2) == 4 && SignalRulesSC.patrolSize(0) == 3;
        boolean nums = SignalRulesSC.DURATION_TICKS == 12000 && SignalRulesSC.BEAM_TOP == 256 && SignalRulesSC.BEAM_VIEW == 1024 && SignalRulesSC.CACHE_REACH == 24
                && "SCSi14Signal".equals(SignalDataSC.NAME);
        // the record's NBT there and back
        SignalDataSC d = new SignalDataSC(), back = new SignalDataSC(), fresh = new SignalDataSC();
        d.rolledDay = 41L;
        d.on = true;
        d.i = -2;
        d.j = 3;
        d.x = -512;
        d.y = 30;
        d.z = 768;
        d.startAt = 100L;
        d.endAt = 12100L;
        d.cacheDone = true;
        NBTTagCompound t = new NBTTagCompound();
        d.writeToNBT(t);
        back.readFromNBT(t);
        fresh.readFromNBT(new NBTTagCompound());
        boolean data = back.rolledDay == 41L && back.on && back.i == -2 && back.j == 3 && back.x == -512 && back.y == 30 && back.z == 768 && back.startAt == 100L
                && back.endAt == 12100L && back.cacheDone && fresh.rolledDay == -1L && !fresh.on;
        boolean ok = bad.length() == 0 && Math.abs(share - SignalRulesSC.CHANCE_PCT / 100F) <= 0.03F && found > days / 2 && patrol && nums && data;
        check(ok, "signal rules (SignalRulesSC, ПЛ-5..ПЛ-8): " + rolls + " of " + days + " days = " + Math.round(share * 1000F) / 10F + "% (" + SignalRulesSC.CHANCE_PCT
                + " ± 3), startOf in " + SignalRulesSC.START_MIN + ".." + SignalRulesSC.START_MAX + "; crossingFor " + found + " times - never an arena or the spawn's"
                + " ring, " + Math.round(minD) + ".." + Math.round(maxD) + " blocks (" + SignalRulesSC.DIST_MIN + ".." + SignalRulesSC.DIST_MAX + " ± 256), deterministic"
                + (bad.length() == 0 ? "" : " - BAD:" + bad.substring(0, Math.min(200, bad.length()))) + "; the patrol 3 + (n - 1) " + patrol + "; the numbers " + nums
                + "; SignalDataSC's NBT there and back " + data);
    }

    // ------------------------------------------------------------------ 9. CaravanRulesSC (§8, ПН-12..ПН-16)

    private static void caravanRules() {
        boolean days = CaravanRulesSC.caravanDay(0) && !CaravanRulesSC.caravanDay(1) && CaravanRulesSC.caravanDay(2) && CaravanRulesSC.caravanDay(40)
                && !CaravanRulesSC.caravanDay(-2);
        boolean window = !CaravanRulesSC.inWindow(999) && CaravanRulesSC.inWindow(1000) && CaravanRulesSC.inWindow(8999) && !CaravanRulesSC.inWindow(9000)
                && CaravanRulesSC.inWindow(24000L * 7 + 5000L) && !CaravanRulesSC.inWindow(18000);
        int n = 4000, ambush = 0;
        for (long day = 0; day < n; day++) {
            ambush += CaravanRulesSC.ambushRoll(SEEDS[2], day) ? 1 : 0;
        }
        float share = ambush / (float) n;
        boolean size = CaravanRulesSC.ambushSize(1) == 3 && CaravanRulesSC.ambushSize(2) == 4 && CaravanRulesSC.ambushSize(0) == 3;
        boolean defended = CaravanRulesSC.defended(true, 0) && !CaravanRulesSC.defended(false, 0) && !CaravanRulesSC.defended(true, 1);
        boolean deal = CaravanRulesSC.dealOpen(5L, false, 5L) && CaravanRulesSC.dealOpen(5L, false, 2L) && !CaravanRulesSC.dealOpen(5L, false, 6L)
                && !CaravanRulesSC.dealOpen(5L, true, 4L);
        boolean nums = CaravanRulesSC.EVERY_DAYS == 2 && CaravanRulesSC.GUARDS == 2 && CaravanRulesSC.REWARD_REP == 20 && CaravanRulesSC.RARE_DAYS == 3
                && CaravanRulesSC.TIMEOUT_TICKS == 6000 && "V".equals(CaravanRulesSC.EXTRA) && com.sc.settle.ReputationSC.SRC_CARAVAN == 10;
        check(days && window && Math.abs(share - CaravanRulesSC.AMBUSH_PCT / 100F) <= 0.04F && size && defended && deal && nums, "caravan rules (CaravanRulesSC,"
                + " ПН-12..ПН-15): a caravan day every 2nd " + days + "; the window 1000..8999 " + window + "; ambushes " + ambush + " of " + n + " days = "
                + Math.round(share * 1000F) / 10F + "% (" + CaravanRulesSC.AMBUSH_PCT + " ± 4); the ambush 3 + (n - 1) " + size + "; defended (master alive, none"
                + " left) " + defended + "; dealOpen (until the day, once) " + deal + "; the numbers, SRC_CARAVAN 10 " + nums);
    }

    // ------------------------------------------------------------------ 10. FinaleRulesSC, the schemes (§9.1, §9.4, ПО-20, ПТ-25)

    private static void finaleRules() {
        boolean upkeep = FinaleRulesSC.upkeep(0, true) == 0 && FinaleRulesSC.upkeep(1, true) == 1 && FinaleRulesSC.upkeep(7, true) == 4
                && FinaleRulesSC.upkeep(100, true) == 50 && FinaleRulesSC.upkeep(7, false) == 7 && FinaleRulesSC.upkeep(100, false) == 100;
        SingularScheme[] all = SingularScheme.values();
        StringBuilder locked = new StringBuilder();
        for (SingularScheme s : all) {
            boolean shouldLock = s == SingularScheme.L || s == SingularScheme.M;
            if (s.unlockable() != shouldLock || (s.unlockBit() != 0) != shouldLock) {
                locked.append(' ').append(s.name());
            }
        }
        boolean schemes = all.length == 13 && SingularScheme.L.ordinal() == 11 && SingularScheme.M.ordinal() == 12 && SingularScheme.L.unlockBit() == 1
                && SingularScheme.M.unlockBit() == 2 && locked.length() == 0 && SingularScheme.L.base == 0x1C1A24 && SingularScheme.M.accent == 0x3CD2C8;
        boolean open = !FinaleRulesSC.open(SingularScheme.L, 0) && FinaleRulesSC.open(SingularScheme.L, 1) && !FinaleRulesSC.open(SingularScheme.M, 1)
                && FinaleRulesSC.open(SingularScheme.M, 3) && FinaleRulesSC.open(SingularScheme.A, 0) && !FinaleRulesSC.open(null, 3)
                && FinaleRulesSC.schemeOf(FinaleRulesSC.PAINT_LITHOGRAPHER) == SingularScheme.L && FinaleRulesSC.schemeOf(FinaleRulesSC.PAINT_TUNER) == SingularScheme.M
                && FinaleRulesSC.schemeOf(2) == null && FinaleRulesSC.unlockedSchemes(null) == 0 && !FinaleRulesSC.unlock(null, SingularScheme.L);
        boolean cycle = SingularStationMath.cycle(SingularScheme.A, 1) == SingularScheme.B && SingularStationMath.cycle(SingularScheme.A, -1) == SingularScheme.K
                && SingularStationMath.cycle(SingularScheme.K, 1) == SingularScheme.A && SingularStationMath.cycle(SingularScheme.A, -1, 3) == SingularScheme.M
                && SingularStationMath.cycle(SingularScheme.K, 1, 3) == SingularScheme.L && SingularStationMath.cycle(SingularScheme.K, 1, 2) == SingularScheme.M
                && SingularStationMath.cycle(SingularScheme.L, 1, 0) == SingularScheme.A && SingularStationMath.cycle(SingularScheme.M, -1, 0) == SingularScheme.K;
        boolean nums = FinaleRulesSC.UPKEEP_SHARE == 0.5F && FinaleRulesSC.TUNER_DOME_RGB == 0x48F0DC && "scSchemes".equals(FinaleRulesSC.SCHEMES_NBT)
                && FinaleRulesSC.PAINT_NAMES.length == 2 && "lithographer".equals(FinaleRulesSC.PAINT_NAMES[0]) && "tuner".equals(FinaleRulesSC.PAINT_NAMES[1]);
        check(upkeep && schemes && open && cycle && nums, "finale rules (FinaleRulesSC, ПО-20, ПТ-25): the upkeep 0 -> 0, 1 -> 1, 7 -> 4, 100 -> 50, without the"
                + " Module unchanged " + upkeep + "; SingularScheme 13, L / M the only unlockable (bits 1 / 2)" + (locked.length() == 0 ? "" : " - NOT:" + locked) + " "
                + schemes + "; open by the mask, schemeOf, nothing for a null player " + open + "; cycle - A -> B, A <- K, K -> A as before, with mask 3 A <- M,"
                + " K -> L, a locked one stepped off " + cycle + "; the numbers " + nums);
    }

    // ------------------------------------------------------------------ 11. SingularLevel's fourth tasks (§9.2, ПК-23)

    private static void singularTasks() {
        boolean need = SingularLevel.TASKS == 4 && SingularLevel.taskNeed(3, 3) == 25 && SingularLevel.taskNeed(5, 3) == 1 && SingularLevel.taskNeed(2, 3) == 0
                && SingularLevel.taskNeed(4, 3) == 0 && SingularLevel.taskNeed(3, 4) == 0 && SingularLevel.taskNeed(6, 0) == 0;
        int[][] old = {{5000, 300, 2000}, {100, 600, 50}, {1, 300, 20}, {1, 30, 5000}};
        StringBuilder bad = new StringBuilder();
        for (int t = 2; t <= 5; t++) {
            for (int i = 0; i < 3; i++) {
                if (SingularLevel.taskNeed(t, i) != old[t - 2][i]) {
                    bad.append(' ').append(t).append('.').append(i);
                }
            }
        }
        NBTTagCompound per = new NBTTagCompound(), store = new NBTTagCompound();
        per.setTag(SingularLevel.STORE, store);
        store.setInteger(SingularLevel.C_EMP_KILLS, 24);
        store.setInteger(SingularLevel.C_WARDEN, 0);
        boolean values = SingularLevel.taskValue(per, 3, 3) == 24 && SingularLevel.taskValue(per, 5, 3) == 0 && !SingularLevel.taskDoneIn(per, 3)
                && !SingularLevel.taskDoneIn(per, 5);
        store.setInteger(SingularLevel.C_EMP_KILLS, 25);
        store.setInteger(SingularLevel.C_WARDEN, 1);
        values &= SingularLevel.taskDoneIn(per, 3) && SingularLevel.taskDoneIn(per, 5) && SingularLevel.taskValue(per, 2, 3) == 0
                && "empKills".equals(SingularLevel.C_EMP_KILLS) && "warden".equals(SingularLevel.C_WARDEN);
        int[] v = SingularLevel.taskValues(per);
        ArmorNetSC.LevelMessage m = new ArmorNetSC.LevelMessage(v, 7, 3), back = new ArmorNetSC.LevelMessage();
        ByteBuf buf = Unpooled.buffer();
        m.toBytes(buf);
        back.fromBytes(buf);
        boolean message = v.length == 16 && v[SingularLevel.TASKS + 3] == 25 && v[3 * SingularLevel.TASKS + 3] == 1 && Arrays.equals(back.tasks, v)
                && back.biomes == 7 && back.dims == 3;
        check(need && bad.length() == 0 && values && message, "singular tasks (SingularLevel, ПК-23): TASKS 4, the fourth column 3.3 = 25, 5.3 = 1, 2.3 / 4.3 = 0 "
                + need + "; the three old columns as they were" + (bad.length() == 0 ? "" : " - NOT:" + bad) + "; taskValue 33 / 53 from empKills / warden, done at"
                + " 25 / 1 " + values + "; taskValues " + v.length + " (16) through LevelMessage there and back " + message);
    }

    // ------------------------------------------------------------------ 12. the pool (§2.2)

    private static void pool() throws Exception {
        boolean names = MobEntitiesSC.NAMES.length == 26 && MobEntitiesSC.EGG_COLORS.length == 26 && "breakdownWarden".equals(MobEntitiesSC.nameOf(22))
                && "containmentCoil".equals(MobEntitiesSC.nameOf(23)) && "caravanMaster".equals(MobEntitiesSC.nameOf(24)) && "caravanGuard".equals(MobEntitiesSC.nameOf(25))
                && MobEntitiesSC.nameOf(26) == null && MobEntitiesSC.ID_BREAKDOWN_WARDEN == 22 && MobEntitiesSC.ID_CONTAINMENT_COIL == 23
                && MobEntitiesSC.ID_CARAVAN_MASTER == 24 && MobEntitiesSC.ID_CARAVAN_GUARD == 25 && MobEntitiesSC.BOSS_TRACKING == 128;
        boolean eggs = MobEntitiesSC.hasEgg(22) && !MobEntitiesSC.hasEgg(23) && !MobEntitiesSC.hasEgg(24) && !MobEntitiesSC.hasEgg(25) && MobEntitiesSC.hasEgg(0)
                && MobEntitiesSC.hasEgg(21) && !MobEntitiesSC.hasEgg(26);
        // every weight table by the pool: 26 long, nothing for 22..25 (the director never sets them)
        StringBuilder bad = new StringBuilder();
        int tables = 0;
        for (Field f : SpawnRulesSC.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int[].class || !f.getName().endsWith("WEIGHTS")) {
                continue;
            }
            int[] w = (int[]) f.get(null);
            tables++;
            if (w.length != MobEntitiesSC.NAMES.length) {
                bad.append(' ').append(f.getName()).append(':').append(w.length);
                continue;
            }
            for (int id = 22; id <= 25; id++) {
                if (w[id] != 0) {
                    bad.append(' ').append(f.getName()).append('[').append(id).append("]=").append(w[id]);
                }
            }
        }
        for (int[] g : new int[][]{SpawnRulesSC.GROUP_MIN, SpawnRulesSC.GROUP_MAX}) {
            if (g.length != MobEntitiesSC.NAMES.length) {
                bad.append(" group:").append(g.length);
            }
        }
        StringBuilder noWorld = new StringBuilder();
        Object boss = SelfTestHazardSC.make(EntityBreakdownWardenSC.class, noWorld);
        String hp = boss instanceof EntityLivingBase ? String.valueOf(((EntityLivingBase) boss).getMaxHealth()) : "not made without a world";
        if (boss == null) {
            info("the Breakdown Warden can't be made without a world:" + noWorld + " - its HP: WorldTestFinaleSC");
        }
        boolean hpOk = !(boss instanceof EntityLivingBase) || Math.abs(((EntityLivingBase) boss).getMaxHealth() - WardenRulesSC.maxHealth(1)) < 0.01F;
        check(names && eggs && tables >= 6 && bad.length() == 0 && hpOk, "pool (MobEntitiesSC, §2.2): 26 names and egg colours, 22 breakdownWarden .. 25"
                + " caravanGuard, 26 none, tracking 128 " + names + "; an egg for the boss only (none for 23..25) " + eggs + "; " + tables + " weight tables 26 long"
                + " with 0 for 22..25, the groups too" + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; the stub boss's HP " + hp + " (600)");
    }

    // ------------------------------------------------------------------ 13. the relics, the items, the recipes (§2.2, §2.3)

    private static void relics() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        boolean metas = ItemRelicSC.NAMES.length == 5 && ItemRelicSC.BREAKDOWN_KEY == 3 && ItemRelicSC.BREAKDOWN_MATRIX == 4
                && "breakdownKey".equals(ItemRelicSC.NAMES[3]) && "breakdownMatrix".equals(ItemRelicSC.NAMES[4]) && ItemRelicSC.MATRIX_STACK == 16;
        Item relic = RelicItemsSC.relic;
        StringBuilder bad = new StringBuilder();
        String stacks = "-";
        if (relic != null) {
            int[] want = {1, 4, 64, 1, 16};
            StringBuilder got = new StringBuilder();
            for (int m = 0; m < ItemRelicSC.NAMES.length && m < want.length; m++) {
                int lim = relic.getItemStackLimit(new ItemStack(relic, 1, m));
                got.append(m == 0 ? "" : " / ").append(lim);
                if (lim != want[m]) {
                    bad.append(" stack ").append(m);
                }
                String key = relic.getUnlocalizedName(new ItemStack(relic, 1, m)) + ".name";
                if (!key.equals("item.siliconage.relic." + ItemRelicSC.NAMES[m] + ".name")) {
                    bad.append(" unlocalized ").append(key);
                }
            }
            stacks = got.toString();
        } else {
            bad.append(" relicSC not registered");
        }
        for (int m = ItemRelicSC.BREAKDOWN_KEY; m <= ItemRelicSC.BREAKDOWN_MATRIX && m < ItemRelicSC.NAMES.length; m++) {
            String n = ItemRelicSC.NAMES[m];
            for (String k : new String[]{"item.siliconage.relic." + n + ".name", "sc.tooltip.relic." + n, "sc.tooltip.relic." + n + ".details",
                    "sc.tooltip.relic." + n + ".howto", "sc.manual.world.relic." + n}) {
                if (!en.containsKey(k) || !ru.containsKey(k)) {
                    bad.append(' ').append(k);
                }
            }
            if (SelfTestFinaleSC.class.getResource("/assets/siliconage/textures/items/relic_" + n + ".png") == null) {
                info("no icon relic_" + n + ".png yet (package A's)");
            }
        }
        // the five items registered, their stacks
        boolean items = FinaleItemsSC.arenaCoordinator != null && FinaleItemsSC.journal != null && FinaleItemsSC.paintScheme != null
                && FinaleItemsSC.lithoModule != null && FinaleItemsSC.navRecord != null;
        if (items) {
            items = FinaleItemsSC.arenaCoordinator(1).getMaxStackSize() == 1 && FinaleItemsSC.journal(1).getMaxStackSize() == 1 && FinaleItemsSC.journal(13) == null
                    && FinaleItemsSC.journal(12).getItemDamage() == 11 && FinaleItemsSC.lithoModule(1).getMaxStackSize() == 1
                    && FinaleItemsSC.navRecord(1).getMaxStackSize() == 16 && FinaleItemsSC.isLithoModule(FinaleItemsSC.lithoModule(1))
                    && !FinaleItemsSC.isLithoModule(new ItemStack(ModItems.empGun));
        }
        // the two recipes of the core (§2.3): the key of four Fragments, the record of a half + a chip + a controller
        int keyRecipes = 0, recordRecipes = 0;
        ItemStack key = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1), record = FinaleItemsSC.navRecord(1);
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            ItemStack out = r.getRecipeOutput();
            if (out == null || out.getItem() == null) {
                continue;
            }
            int inputs = r instanceof ShapelessRecipes ? ((ShapelessRecipes) r).recipeItems.size() : r instanceof ShapelessOreRecipe
                    ? ((ShapelessOreRecipe) r).getInput().size() : -1;
            if (key != null && out.getItem() == key.getItem() && out.getItemDamage() == ItemRelicSC.BREAKDOWN_KEY && inputs == ItemRelicSC.FRAGMENTS) {
                keyRecipes++;
            }
            if (record != null && out.getItem() == record.getItem() && inputs == 3) {
                recordRecipes++;
            }
        }
        boolean recipes = keyRecipes == 1 && recordRecipes == 1;
        check(metas && bad.length() == 0 && items && recipes, "relics and items (ItemRelicSC, FinaleItemsSC, FinaleRecipesSC): 5 metas, breakdownKey 3 /"
                + " breakdownMatrix 4 " + metas + "; stacks " + stacks + " (1 / 4 / 64 / 1 / 16), names and tooltips in both languages" + (bad.length() == 0 ? ""
                : " - NOT:" + bad) + "; the five items registered (Coordinator 1, journals 12 metas of 1, Module 1, Record 16) " + items + "; the key's shapeless"
                + " recipe of 4 Fragments " + keyRecipes + ", the Record's of 3 " + recordRecipes + " (1 each) " + recipes);
    }

    // ------------------------------------------------------------------ 14. the presets (§9.3)

    private static void presets() {
        int[] p = TileEntityFieldGeneratorSC.PRESETS;
        Set<Integer> seen = new HashSet<Integer>();
        for (int c : p) {
            seen.add(c);
        }
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < p.length; i++) {
            if (!en.containsKey("sc.fieldzone.preset." + i) || !ru.containsKey("sc.fieldzone.preset." + i)) {
                names.append(' ').append(i);
            }
        }
        boolean last = p.length > 24 && p[24] == FinaleRulesSC.TUNER_DOME_RGB;
        check(p.length == 25 && seen.size() == 25 && last && p[11] == 0x3CD2C8 && names.length() == 0, "presets (§9.3): " + p.length + " dome colours (25), "
                + seen.size() + " different, [24] = TUNER_DOME_RGB " + last + ", [11] the settlements' turquoise kept; every one named in both languages"
                + (names.length() == 0 ? "" : " - NOT:" + names));
    }

    // ------------------------------------------------------------------ 15. the texts (§11.3)

    @SuppressWarnings("unchecked")
    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>();
        for (int id = MobEntitiesSC.ID_BREAKDOWN_WARDEN; id <= MobEntitiesSC.ID_CARAVAN_GUARD; id++) {
            keys.add("entity.SiliconAge." + MobEntitiesSC.nameOf(id) + ".name");
        }
        for (String n : ArenaBlocksSC.NAMES) {
            keys.addAll(Arrays.asList("tile.siliconage.arena." + n + ".name", "sc.tooltip.arena." + n, "sc.tooltip.arena." + n + ".details",
                    "sc.tooltip.arena." + n + ".howto", "sc.manual.world.arena.block." + n));
        }
        keys.addAll(Arrays.asList("tile.siliconage.ruin.burntCoil.name", "sc.tooltip.ruin.burntCoil", "sc.tooltip.ruin.burntCoil.details",
                "sc.tooltip.ruin.burntCoil.howto", "sc.manual.world.ruin.burntCoil", "item.siliconage.arenaCoordinator.name", "sc.tooltip.arenaCoordinator",
                "sc.tooltip.arenaCoordinator.details", "sc.tooltip.arenaCoordinator.howto", "item.siliconage.journalSC.name", "sc.tooltip.journal",
                "sc.tooltip.journal.details", "sc.tooltip.journal.howto", "sc.tooltip.journal.where", "item.siliconage.lithoModule.name", "sc.tooltip.lithoModule",
                "sc.tooltip.lithoModule.details",
                "sc.tooltip.lithoModule.howto", "item.siliconage.navRecord.name", "sc.tooltip.navRecord", "sc.tooltip.navRecord.details.on",
                "sc.tooltip.navRecord.details.off", "sc.tooltip.navRecord.howto", "sc.singular.scheme.l", "sc.singular.scheme.m", "sc.fieldzone.preset.24",
                "sc.levelgui.task.3.3", "sc.levelgui.task.5.3", "sc.tooltip.armor.singular.task.3.3", "sc.tooltip.armor.singular.task.5.3"));
        for (String n : FinaleRulesSC.PAINT_NAMES) {
            keys.addAll(Arrays.asList("item.siliconage.paintScheme." + n + ".name", "sc.tooltip.paint." + n, "sc.tooltip.paint." + n + ".details",
                    "sc.tooltip.paint." + n + ".howto"));
        }
        for (SingularScheme s : SingularScheme.values()) {
            keys.add(s.langKey());
        }
        for (String src : ItemJournalSC.SRC_KEYS) {
            keys.add("sc.tooltip.journal.src." + src);
        }
        for (int i = 0; i < 4; i++) {
            keys.add("sc.waila.warden.piece." + i);
        }
        for (int phase = WardenRulesSC.PHASE_HOLD; phase <= WardenRulesSC.PHASE_OVERLOAD; phase++) {
            keys.add("sc.waila.warden.phase." + phase);
        }
        keys.addAll(Arrays.asList("sc.waila.coil.lit", "sc.waila.coil.dark", "sc.waila.coil.spent", "sc.waila.coil.broken", "sc.waila.warden.set",
                "sc.waila.warden.overheat"));
        // the journals: a title, a date and JournalSC.PAGES pages each - and no page past them
        StringBuilder pages = new StringBuilder();
        for (int n = 1; n <= JournalSC.COUNT; n++) {
            keys.add("sc.journal." + n + ".title");
            keys.add("sc.journal." + n + ".date");
            for (int p = 0; p < JournalSC.PAGES[n - 1]; p++) {
                keys.add("sc.journal." + n + ".p" + p);
                String v = ru.get("sc.journal." + n + ".p" + p);
                if (v != null && (v.indexOf('%') >= 0 || v.length() < 40)) {
                    pages.append(" №").append(n).append(".p").append(p).append(v.indexOf('%') >= 0 ? " has a %" : " too short");
                }
            }
            if (en.containsKey("sc.journal." + n + ".p" + JournalSC.PAGES[n - 1]) || ru.containsKey("sc.journal." + n + ".p" + JournalSC.PAGES[n - 1])) {
                pages.append(" №").append(n).append(" has a page past PAGES");
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
        StringBuilder args = new StringBuilder();
        for (String[] m : MESSAGE_ARGS) {
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
        // every Ф5 key of one file in the other, with as many arguments; English is English
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
                } else if (from == en && e.getValue().matches(".*[\\u0400-\\u04FF«»].*")) {
                    diff.append(" Cyrillic / «» in en_US:").append(e.getKey());
                }
            }
        }
        check(missing.length() == 0 && args.length() == 0 && diff.length() == 0 && pages.length() == 0, "texts (§11.3): " + keys.size() + " keys - the four"
                + " creatures, the arena blocks, the Burnt Coil, the items, the schemes, the preset, the tasks, the twelve journals' pages - and "
                + MESSAGE_ARGS.length + " messages with their callers' arguments in en_US and ru_RU" + missing + args + pages + "; " + all + " Ф5 keys with the same"
                + " arguments in both, no Cyrillic in English" + diff);
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
    private static int positional(String v) {
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

    // ------------------------------------------------------------------ 16. package J's pure bodies (ПЛ-1..ПЛ-4, ПЛ-7)

    private static void loreBodies() {
        boolean coil = BlockRuinSC.NAMES.length == 8 && "burntCoil".equals(BlockRuinSC.nameOf(BlockRuinSC.BURNT_COIL))
                && BlockRuinSC.BURNT_COIL == BookWorldSC.RUIN_BURNT_COIL;
        Random r = new Random(77L);
        StringBuilder bad = new StringBuilder();
        ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1), firmware = MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, 1);
        for (int k = 0; k < 200 && coil; k++) {
            for (ItemStack d : BlockRuinSC.drops(BlockRuinSC.BURNT_COIL, r)) {
                if (!like(d, scrap) && !like(d, firmware)) {
                    bad.append(" coil drop ").append(d);
                    break;
                }
            }
        }
        // the journals' «where found» word by number (ItemJournalSC: the first pool of each)
        String[] want = {"node", "node", "litho", "litho", "vault", "vault", "vault", "vault", "wreck", "wreck", "signal", "warden"};
        for (int n = 1; n <= JournalSC.COUNT; n++) {
            if (!want[n - 1].equals(ItemJournalSC.sourceKey(n))) {
                bad.append(" source ").append(n).append('=').append(ItemJournalSC.sourceKey(n));
            }
        }
        // a wreck's chest: №9 or №10 always, the scrap, the firmware; the Signal's cache: №11 and one Sealed Crumb
        int chests = 0, caches = 0;
        for (int k = 0; k < 200 && FinaleItemsSC.journal != null && MobItemsSC.combatLoot != null; k++) {
            int journal = -1, scraps = 0, firmwares = 0;
            for (ItemStack s : WreckLootSC.chest(r)) {
                journal = s.getItem() == FinaleItemsSC.journal ? s.getItemDamage() : journal;
                scraps += like(s, scrap) ? s.stackSize : 0;
                firmwares += like(s, firmware) ? s.stackSize : 0;
            }
            if ((journal == JournalSC.metaOf(9) || journal == JournalSC.metaOf(10)) && scraps >= WreckLootSC.SCRAP_MIN && scraps <= WreckLootSC.SCRAP_MAX
                    && firmwares >= WreckLootSC.FIRMWARE_MIN && firmwares <= WreckLootSC.FIRMWARE_MAX) {
                chests++;
            }
            int sealed = 0, cacheJournal = -1;
            for (ItemStack s : SignalCacheSC.contents(r)) {
                cacheJournal = s.getItem() == FinaleItemsSC.journal ? s.getItemDamage() : cacheJournal;
                sealed += s.getItem() == RelicItemsSC.relic && s.getItemDamage() == ItemRelicSC.SEALED_CRUMB ? s.stackSize : 0;
            }
            caches += cacheJournal == JournalSC.metaOf(11) && sealed == 1 ? 1 : 0;
        }
        check(coil && bad.length() == 0 && chests == 200 && caches == 200, "lore bodies (package J, ПЛ-1..ПЛ-4, ПЛ-7): ruinSC 8 metas, 7 burntCoil - its"
                + " drops Nano Scrap or Shell Firmware only " + coil + "; the journals' source words node / litho / vault / wreck / signal / warden"
                + (bad.length() == 0 ? "" : " - BAD:" + bad) + "; a wreck's chest №9 / №10 with " + WreckLootSC.SCRAP_MIN + "-" + WreckLootSC.SCRAP_MAX
                + " scrap and " + WreckLootSC.FIRMWARE_MIN + "-" + WreckLootSC.FIRMWARE_MAX + " firmware " + chests + " / 200; the Signal's cache №11 and one"
                + " Sealed Crumb " + caches + " / 200");
    }

    private static boolean like(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
    }

    // ------------------------------------------------------------------ 17. package K's route (ПН-13, ПН-16)

    private static void caravanPath() {
        int grid = SubstrateTerrainSC.GRID, cases = 0;
        StringBuilder bad = new StringBuilder();
        double longest = 0;
        int[] radii = {24, 40, 56, 64};
        for (int cell = -2; cell <= 2; cell++) {
            for (int jx = -SettlementLayoutSC.JITTER; jx <= SettlementLayoutSC.JITTER; jx += SettlementLayoutSC.JITTER) {
                for (int jz = -SettlementLayoutSC.JITTER; jz <= SettlementLayoutSC.JITTER; jz += SettlementLayoutSC.JITTER) {
                    int coreX = cell * grid + grid / 2 + jx, coreZ = -cell * grid + grid / 2 + jz;
                    int cellX = SubstrateCellsSC.cellOf(coreX), cellZ = SubstrateCellsSC.cellOf(coreZ);
                    for (int radius : radii) {
                        for (int a = 0; a < 8 && bad.length() < 400; a++) {
                            cases++;
                            List<int[]> spots = CaravanRunPathSC.spawnSpots(coreX, coreZ, radius, a * Math.PI / 4D);
                            if (spots.isEmpty()) {
                                bad.append(" nospot ").append(coreX).append(',').append(coreZ).append('/').append(radius);
                                continue;
                            }
                            int[] st = spots.get(0);
                            double out = Math.sqrt((st[0] - coreX) * (double) (st[0] - coreX) + (st[1] - coreZ) * (double) (st[1] - coreZ)) - radius;
                            if (out < CaravanRulesSC.APPROACH_FROM - 1.5D || out > CaravanRulesSC.APPROACH_TO + 1.5D) {
                                bad.append(" far ").append(Math.round(out));
                            }
                            int axis = CaravanRunPathSC.axis(st[0] - coreX, st[1] - coreZ), along = radius / 2;
                            int[][] route = CaravanRunPathSC.route(coreX, coreZ, radius, st[0], st[1], axis, along);
                            int[] at = st;
                            double length = 0;
                            for (int[] p : route) {
                                double leg = Math.sqrt((p[0] - at[0]) * (double) (p[0] - at[0]) + (p[1] - at[1]) * (double) (p[1] - at[1]));
                                length += leg;
                                if (leg > CaravanRunPathSC.STEP + 0.8D) {
                                    bad.append(" leg ").append(Math.round(leg));
                                }
                                if (!CaravanRunPathSC.plateau(cellX, cellZ, p[0], p[1])) {
                                    bad.append(" off ").append(p[0]).append(',').append(p[1]);
                                }
                                at = p;
                            }
                            longest = Math.max(longest, length);
                            int[] end = route.length == 0 ? st : route[route.length - 1];
                            if (end[0] != coreX + SettlementLayoutSC.DIR_X[axis] * along || end[1] != coreZ + SettlementLayoutSC.DIR_Z[axis] * along) {
                                bad.append(" end ").append(end[0]).append(',').append(end[1]);
                            }
                        }
                    }
                }
            }
        }
        check(bad.length() == 0 && cases > 0, "caravan path (package K, CaravanRunPathSC, ПН-13): " + cases + " starts and routes - a dome of 24..64 at its"
                + " cell's middle ± " + SettlementLayoutSC.JITTER + ", the source from 8 ways: a start always found " + CaravanRulesSC.APPROACH_FROM + ".."
                + CaravanRulesSC.APPROACH_TO + " past the edge, every waypoint on the core cell's plateau, legs <= " + CaravanRunPathSC.STEP + ", the route"
                + " ends on the street's point; the longest " + Math.round(longest) + " blocks" + (bad.length() == 0 ? "" : " - BAD:"
                + bad.substring(0, Math.min(300, bad.length()))));
    }

    // ------------------------------------------------------------------ 18. package P's restore and the finale's deals (ПТ-26, ПО-17..ПО-22)

    private static void restoreAndDeals() {
        int pe1m = TrophyTableSC.META_PE1M;
        boolean restore = TrophyRestoreSC.singular(pe1m) && TrophyRestoreSC.refusal(pe1m, TrophyRestoreSC.WHERE_ARMOURER) == TrophyRestoreSC.R_SINGULAR_ONLY
                && TrophyRestoreSC.refusal(pe1m, TrophyRestoreSC.WHERE_STATION) == TrophyRestoreSC.R_SINGULAR_ONLY
                && TrophyRestoreSC.refusal(pe1m, TrophyRestoreSC.WHERE_SINGULAR_STATION) == TrophyRestoreSC.OK
                && TrophyRestoreSC.refusal(TrophyTableSC.METAS, TrophyRestoreSC.WHERE_SINGULAR_STATION) == TrophyRestoreSC.R_UNKNOWN;
        ItemStack gun = TrophyRestoreSC.restored(pe1m);
        boolean real = gun != null && gun.getItem() == ModItems.empGun && gun.getItemDamage() == ItemEmpGunSC.META_UPGRADED && gun.getTagCompound() == null;
        StringBuilder deals = new StringBuilder();
        String[] keys = {TradeTableSC.CART_ARENA_COORDINATOR, TradeTableSC.ELDER_LITHO_MODULE, TradeTableSC.ELDER_TUNER_SCHEME, TradeTableSC.ARM_MOD_WIDE};
        NpcKindSC[] kinds = {NpcKindSC.CARTOGRAPHER, NpcKindSC.ELDER, NpcKindSC.ELDER, NpcKindSC.ARMOURER};
        for (int i = 0; i < keys.length; i++) {
            TradeOfferSC o = TradeTableSC.byKey(keys[i]);
            if (o == null || o.kind != kinds[i]) {
                deals.append(' ').append(keys[i]).append(o == null ? ":missing" : ":" + o.kind);
                continue;
            }
            ItemStack out = o.out == null ? null : o.out.stack;
            if (out == null || TradeTableSC.forbiddenOut(out)) {
                deals.append(' ').append(keys[i]).append(":out");
            }
        }
        for (String k : TradeTableSC.boughtAndSold()) {
            if (Arrays.asList(keys).contains(k)) {
                deals.append(' ').append(k).append(":boughtAndSold");
            }
        }
        boolean result = TradeServerSC.R_CARAVAN_DEAL == 19 && TradeServerSC.RESULT_KEYS.length > 19 && "caravanDeal".equals(TradeServerSC.RESULT_KEYS[19]);
        check(restore && real && deals.length() == 0 && result, "restore and deals (package P, ПТ-26, ПО-17..ПО-22): the ПЭ-1М trophy (24) - the Singular"
                + " station alone, the Armourer and the plain station refuse, an unknown meta unknown " + restore + "; it comes out a ПЭ-1М (empGun meta 1)"
                + " with no NBT " + real + "; the Cartographer's Coordinator, the Elder's Module and «Наладчик», the Armourer's «Широкий канал» - theirs, sold"
                + " only, none forbidden" + (deals.length() == 0 ? "" : " - BAD:" + deals) + "; R_CARAVAN_DEAL 19 \"caravanDeal\" " + result);
    }

    // ------------------------------------------------------------------ 16. the handbook (§11.4) and the cross-check of Ф1..Ф5

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
        // the extended Ф1..Ф4 articles read clean too
        for (String id : new String[]{"world.substrate", "world.trophies", "world.trade", "world.crumbs", "world.elites", "world.vaults", "world.restore",
                "world.ruins"}) {
            BookEntry e = BookContent.byId(id);
            raw(bad, id, e == null ? "" : e.searchText());
        }
        // the health table (4 rows), the journals' (7 sources), the Module's
        int hp = rows("world.warden", 0), sources = rows("world.journals", 0), upkeep = rows("world.lithoModule", 0);
        boolean tables = hp == 4 && sources == JournalSC.POOLS.length && upkeep >= 3;
        // G: the new blocks, items and the boss's egg open their pages
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        List<String> ids = new ArrayList<String>();
        for (int m = 0; m < ArenaBlocksSC.NAMES.length; m++) {
            g(stacks, ids, ArenaBlocksSC.stack(m, 1), "world.arena");
        }
        g(stacks, ids, FinaleItemsSC.arenaCoordinator(1), "world.arena");
        g(stacks, ids, RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1), "world.arena");
        g(stacks, ids, RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_MATRIX, 1), "world.wardenLoot");
        for (int n = 1; n <= JournalSC.COUNT; n++) {
            g(stacks, ids, FinaleItemsSC.journal(n), "world.journals");
        }
        g(stacks, ids, FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_LITHOGRAPHER, 1), "world.cosmetics");
        g(stacks, ids, FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_TUNER, 1), "world.cosmetics");
        g(stacks, ids, FinaleItemsSC.lithoModule(1), "world.lithoModule");
        g(stacks, ids, FinaleItemsSC.navRecord(1), "world.navrecord");
        g(stacks, ids, RuinBlocksSC.ruin == null ? null : new ItemStack(RuinBlocksSC.ruin, 1, BookWorldSC.RUIN_BURNT_COIL), "world.wrecks");
        g(stacks, ids, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_BREAKDOWN_WARDEN), "world.warden");
        g(stacks, ids, MobItemsSC.trophy == null ? null : new ItemStack(MobItemsSC.trophy, 1, TrophyTableSC.META_PE1M), "world.trophies");
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
        // the cross-check of Ф1..Ф5: every creature of the pool named in some WORLD article; every block and item of the dimension G-mapped to one
        StringBuilder unnamed = new StringBuilder();
        for (int id = 0; id < MobEntitiesSC.NAMES.length; id++) {
            String name = BookWorldSC.mobName(MobEntitiesSC.nameOf(id));
            if (name.startsWith("entity.") || text.indexOf(name.replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT)) < 0) {   // searchText is lower-case
                unnamed.append(' ').append(MobEntitiesSC.nameOf(id));
            }
        }
        StringBuilder unmapped = new StringBuilder();
        int checked = 0;
        for (ItemStack s : dimensionStacks()) {
            checked++;
            BookEntry got = BookContent.entryFor(s);
            if (got == null || got.chapter != BookChapter.WORLD) {
                unmapped.append(' ').append(Item.itemRegistry.getNameForObject(s.getItem())).append(':').append(s.getItemDamage())
                        .append(got == null ? "" : " -> " + got.id);
            }
        }
        int chapter = BookContent.chapter(BookChapter.WORLD).size(), want = 6 + 6 + 8 + BookWorldSC.F4_ARTICLES.length + ARTICLES.length;
        List<String> unresolved = BookWorldSC.unresolved();
        BookContent.invalidate();
        if (!unresolved.isEmpty()) {
            info("the handbook still prints the plan's numbers for (public static constants of these names in the packages' classes, or direct"
                    + " references in BookWorldSC / BookFinaleSC): " + unresolved);
        }
        check(bad.length() == 0 && tables && noG.length() == 0 && chapter == want, "handbook (§11.4): the " + ARTICLES.length + " new WORLD articles (the chapter "
                + chapter + " of " + want + ") build with no raw keys, the extended ones too" + (bad.length() == 0 ? "" : " -" + bad) + "; tables: the HP by players "
                + hp + " (4), the journals' sources " + sources + " (" + JournalSC.POOLS.length + "), the Module's " + upkeep + " " + tables + "; G on " + registered
                + " new blocks, items and the egg" + (noG.length() == 0 ? "" : " - NOT:" + noG));
        check(unnamed.length() == 0 && unmapped.length() == 0 && checked > 50, "handbook cross-check Ф1..Ф5: the " + MobEntitiesSC.NAMES.length + " creatures of"
                + " the pool each named in a WORLD article" + (unnamed.length() == 0 ? "" : " - NOT:" + unnamed) + "; " + checked + " blocks and items of the"
                + " dimension each open a WORLD article with G" + (unmapped.length() == 0 ? "" : " - NOT:" + unmapped.substring(0, Math.min(400, unmapped.length()))));
    }

    /** Every block (every meta) and item of the Substrate's phases Ф1..Ф5 - the cross-check's list (what isn't registered is skipped). */
    private static List<ItemStack> dimensionStacks() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        metas(out, com.sc.dim.SubstrateBlocksSC.rock, com.sc.dim.SubstrateBlocksSC.ROCK_METAS);
        metas(out, com.sc.dim.SubstrateBlocksSC.druse, 1);
        metas(out, com.sc.dim.SubstrateWildBlocksSC.glass, com.sc.dim.SubstrateWildBlocksSC.GLASS_NAMES.length);
        metas(out, com.sc.dim.SubstrateWildBlocksSC.flora, com.sc.dim.SubstrateWildBlocksSC.FLORA_NAMES.length);
        metas(out, com.sc.dim.SubstrateWildBlocksSC.vent, com.sc.dim.SubstrateWildBlocksSC.VENT_NAMES.length);
        metas(out, CampBlocksSC.stolenField, 1);
        metas(out, CampBlocksSC.camp, CampBlocksSC.CAMP_NAMES.length);
        metas(out, RuinBlocksSC.ruin, BlockRuinSC.NAMES.length);
        metas(out, SettlementBlocksSC.core, 1);
        metas(out, SettlementBlocksSC.dock, com.sc.block.BlockDockSC.NAMES.length);
        metas(out, ArenaBlocksSC.arena, ArenaBlocksSC.NAMES.length);
        for (Item i : new Item[]{SettlementItemsSC.creditChip, SettlementItemsSC.returnCapsule, SettlementItemsSC.settlementMap, RelicItemsSC.ruinFinder,
                FinaleItemsSC.arenaCoordinator, FinaleItemsSC.lithoModule, FinaleItemsSC.navRecord}) {
            if (i != null) {
                out.add(new ItemStack(i));
            }
        }
        for (int m = 0; RelicItemsSC.relic != null && m < ItemRelicSC.NAMES.length; m++) {
            out.add(new ItemStack(RelicItemsSC.relic, 1, m));
        }
        for (int n = 1; FinaleItemsSC.journal != null && n <= JournalSC.COUNT; n++) {
            out.add(FinaleItemsSC.journal(n));
        }
        for (int m = 0; FinaleItemsSC.paintScheme != null && m < FinaleRulesSC.PAINT_NAMES.length; m++) {
            out.add(FinaleItemsSC.paintScheme(m, 1));
        }
        for (int m : TrophyTableSC.SHOWN) {
            if (MobItemsSC.trophy != null) {
                out.add(new ItemStack(MobItemsSC.trophy, 1, m));
            }
        }
        for (int m = 0; MobItemsSC.combatLoot != null && m < BookWorldSC.LOOT_NAMES.length; m++) {
            out.add(new ItemStack(MobItemsSC.combatLoot, 1, m));
        }
        for (int id = 0; MobItemsSC.spawnEgg != null && id < MobEntitiesSC.NAMES.length; id++) {
            if (MobEntitiesSC.hasEgg(id)) {
                out.add(new ItemStack(MobItemsSC.spawnEgg, 1, id));
            }
        }
        return out;
    }

    private static void metas(List<ItemStack> out, Block b, int n) {
        if (b != null && Item.getItemFromBlock(b) != null) {
            for (int m = 0; m < Math.max(1, n); m++) {
                out.add(new ItemStack(b, 1, m));
            }
        }
    }

    private static void raw(StringBuilder bad, String id, String t) {
        for (String raw : new String[]{"sc.manual.", "sc.book.", "sc.dim.", "sc.suit.", "sc.settle.", "sc.trade.", "sc.emp.", "sc.journal.", "sc.levelgui.",
                "sc.fieldzone.", "sc.singular.", "tile.siliconage.", "item.siliconage.", "entity.siliconage.", "%s", "%d", "format error"}) {
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
}
