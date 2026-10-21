package com.sc.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateNetSC;
import com.sc.dim.SubstrateRadiationSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.dim.SubstrateWildTerrainSC;
import com.sc.dim.WorldProviderSubstrateSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.IEmpUselessSC;
import com.sc.hazard.BridgeEchoSC;
import com.sc.hazard.RaidRulesSC;
import com.sc.hazard.StormRulesSC;
import com.sc.hazard.StormStateSC;
import com.sc.manual.BookChapter;
import com.sc.manual.BookContent;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.BookWorldSC;
import com.sc.mob.EntityArmoredMobSC;
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
import com.sc.mob.EntitySubstrateMobSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.EntityWaferCrabSC;
import com.sc.mob.EntityWarlordSC;
import com.sc.mob.FactionSC;
import com.sc.mob.IFactionSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.RareRegistrySC;
import com.sc.mob.TrophyTableSC;
import com.sc.ruin.CampBlocksSC;
import com.sc.ruin.CampLayoutSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.ruin.RuinBlocksSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.VaultLayoutSC;
import com.sc.settle.CrumbLimitSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.util.BladeType;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * PACKAGE D (docs/plan-substrate-tech-f4.md §11.1): Ф4's self-test (-Dsc.selftest, from SelfTestSC.run after SelfTestCombatSC) -
 * the pure decisions of Ф4 without a world, each section by itself (an unfinished package body fails only its own section):
 * the config (12 rules, the 3 new biome ids private), SubstrateSC.pickBiomeId with the five, SubstrateCellsSC (the shares, the
 * spawn ring, never a settlement's or a lithograph's cell, columnKind, chunkHasInner), SubstrateWildTerrainSC (the ranges),
 * SubstrateRadiationSC, StormRulesSC, RaidRulesSC, BridgeEchoSC's pure rolls, CrumbLimitSC.allowed, FactionSC, RareRegistrySC
 * (NBT round trip, mayRespawn), CampLayoutSC / VaultLayoutSC (determinism, clearances, one Vault a block), SpawnRulesSC's new
 * places, the pool (22), the loot kinds (12), the trophies shown (24), the relics, the texts and the handbook.
 * <p>
 * One line each, "[SC-TEST] PASS / FAIL hazard: ...". Built on the core alone: what packages W / S / M / N / C add beyond the
 * core's fixed signatures (SpawnRulesSC's new tables, the terrain's shapes) is asked for by name or by its effect - a body not
 * written yet fails its own section with what is missing, never another's. The creatures are made without a world (World null,
 * as the Ф3 test does): a constructor that needs one is listed (INFO) and its checks are the world test's (WorldTestWildSC).
 */
final class SelfTestHazardSC {

    /** The fifteen Ф4 WORLD articles (SelfTestSubstrateSC counts the chapter: Ф1's six, Ф2's six, Ф3's eight and these). */
    static final String[] ARTICLES = BookWorldSC.F4_ARTICLES;
    private static final String[] SECTIONS = {"config", "biome ids", "cells", "terrain", "radiation", "storm rules", "raid rules", "echo", "crumbs",
            "factions", "rare registry", "camp layout", "vault layout", "spawn rules", "pool", "loot and trophies", "relics", "texts", "handbook"};
    /** The seeds the shares are sampled on. */
    private static final long[] SEEDS = {1234567L, -77L, 0x5C14L};
    /** Ф4's creatures by pool id 12..21 (MobEntitiesSC), their HP (§7.1-§7.10) and names. */
    private static final Class<?>[] MOBS = {EntityOverheatedSC.class, EntityWarlordSC.class, EntitySentinelSC.class, EntityEclipseSC.class,
            EntityPhantomSC.class, EntityLeechSC.class, EntityColossusSC.class, EntityStriderSC.class, EntityScrapperSC.class, EntityWaferCrabSC.class};
    private static final double[] MOB_HP = {30, 90, 100, 140, 20, 30, 120, 60, 60, 8};
    private static final String[] MOB_NAMES = {"overheated", "warlord", "sentinel", "eclipse", "phantom", "leech", "colossus", "strider", "scrapper",
            "waferCrab"};
    /** Key prefixes of Ф4's texts: each in both languages with as many arguments, no Cyrillic and no «» in English. */
    private static final String[] PREFIXES = {"sc.storm.", "sc.haze.", "sc.emission.", "sc.raid.", "sc.echo.", "sc.camp.", "sc.crumb.", "sc.finder.",
            "sc.tooltip.wild.", "tile.siliconage.wild", "sc.tooltip.camp.", "tile.siliconage.camp", "tile.siliconage.stolenField", "sc.tooltip.relic.",
            "item.siliconage.relic.", "sc.tooltip.ruinFinder", "item.siliconage.ruinFinder", "sc.manual.world.biomes4", "sc.manual.world.radiation",
            "sc.manual.world.resources4", "sc.manual.world.wild.", "sc.manual.world.storm", "sc.manual.world.haze", "sc.manual.world.emission",
            "sc.manual.world.raids", "sc.manual.world.echo", "sc.manual.world.camp", "sc.manual.world.warlord", "sc.manual.world.vaults",
            "sc.manual.world.sentinel", "sc.manual.world.relic.", "sc.manual.world.overheated", "sc.manual.world.eclipse", "sc.manual.world.noise4",
            "sc.manual.world.phantom", "sc.manual.world.leech", "sc.manual.world.colossus", "sc.manual.world.strider", "sc.manual.world.scrapper",
            "sc.manual.world.fauna", "sc.manual.world.crumbs", "sc.nei.obtain.camp", "sc.nei.obtain.ruin4", "sc.nei.obtain.relic", "sc.nei.obtain.finder",
            "sc.trade.result.raid", "sc.trade.result.crumbs", "sc.trade.refuse.raid", "sc.trade.refuse.crumbs", "sc.trade.row.raid", "sc.trade.row.crumbs",
            "sc.emp.hit.useless", "sc.scrapper.", "sc.waila.mob.useless", "sc.dim.biome."};
    /**
     * The messages' arguments the packages pass (docs/plan-substrate-tech-f4.md §10.1; S, C, N format them so): the HUD's time, a
     * raid's settlement name and wave, the Pelengator's direction and distance, the Crumbs' count and cap, the field's charge.
     */
    // ПМ-3 / ПМ-4 (audit 2026-10-20): the finder's X and Z; the storm's, the haze's and the Emission's numbers; the haze's HUD line
    private static final String[][] MESSAGE_ARGS = {{"sc.storm.warning", "2"}, {"sc.storm.start", "2"}, {"sc.storm.end", "0"}, {"sc.storm.hud.warn", "1"},
            {"sc.storm.hud", "1"}, {"sc.haze.start", "1"}, {"sc.haze.end", "0"}, {"sc.emission.start", "2"}, {"sc.emission.end", "0"},
            {"sc.haze.hud", "1"},
            {"sc.emission.hud", "1"}, {"sc.raid.start", "1"}, {"sc.raid.wave", "2"}, {"sc.raid.won", "0"}, {"sc.raid.lost", "0"}, {"sc.echo.squad", "0"},
            {"sc.echo.swarm", "0"}, {"sc.camp.field.info", "4"}, {"sc.camp.field.up", "0"}, {"sc.camp.field.down", "0"}, {"sc.camp.safe.empty", "0"},
            {"sc.camp.safe.locked", "0"}, {"sc.camp.safe.powered", "0"},
            {"sc.camp.safe.opened", "0"}, {"sc.crumb.opened", "2"}, {"sc.crumb.limit", "1"}, {"sc.finder.litho", "4"}, {"sc.finder.vault", "4"},
            {"sc.finder.none", "0"}, {"sc.finder.offworld", "0"}, {"sc.trade.result.raid", "0"}, {"sc.trade.result.crumbs", "0"},
            {"sc.trade.refuse.raid", "0"}, {"sc.trade.refuse.crumbs", "0"}, {"sc.emp.hit.useless", "0"}, {"sc.scrapper.took", "0"},
            {"sc.waila.mob.useless", "0"}, {"sc.analyzer.mob.useless", "0"}, {"sc.tooltip.ruinFinder.howto", "1"}};

    private SelfTestHazardSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "hazard: " + what);
    }

    private static void info(String what) {
        System.out.println("[SC-TEST] INFO hazard: " + what);
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

    /** The shortcomings of 2026-10-20, ★ (docs/plan-audit-2026-10-20-star.md): ПМ-6's multipliers, МБ-2's stale Eclipse. */
    private static void auditStar() {
        // ПМ-6: the weather's, the Signal's and the Warden's numbers through the dimension's multipliers (1 - as they were)
        float sWas = com.sc.dim.SubstrateConfigSC.stormChanceMultiplier, hWas = com.sc.dim.SubstrateConfigSC.hazeChanceMultiplier,
                gWas = com.sc.dim.SubstrateConfigSC.signalChanceMultiplier, wWas = com.sc.dim.SubstrateConfigSC.wardenHealthMultiplier;
        int lWas = com.sc.dim.SubstrateConfigSC.wardenLootCooldownDays;
        boolean plain, raised;
        try {
            com.sc.dim.SubstrateConfigSC.stormChanceMultiplier = 1F;
            com.sc.dim.SubstrateConfigSC.hazeChanceMultiplier = 1F;
            com.sc.dim.SubstrateConfigSC.signalChanceMultiplier = 1F;
            com.sc.dim.SubstrateConfigSC.wardenHealthMultiplier = 1F;
            com.sc.dim.SubstrateConfigSC.wardenLootCooldownDays = com.sc.mob.WardenRulesSC.LOOT_COOLDOWN_DAYS;
            plain = StormRulesSC.chancePct() == 20 && StormRulesSC.hazeChancePct() == 25 && com.sc.lore.SignalRulesSC.chancePct() == 15
                    && Math.abs(com.sc.mob.WardenRulesSC.maxHealth(1) - 600F) < 1e-3F && com.sc.mob.WardenRulesSC.lootCooldownDays() == 3;
            com.sc.dim.SubstrateConfigSC.stormChanceMultiplier = 10F;
            com.sc.dim.SubstrateConfigSC.wardenHealthMultiplier = 2F;
            raised = StormRulesSC.chancePct() == 100 && Math.abs(com.sc.mob.WardenRulesSC.maxHealth(1) - 1200F) < 1e-3F;
        } finally {
            com.sc.dim.SubstrateConfigSC.stormChanceMultiplier = sWas;
            com.sc.dim.SubstrateConfigSC.hazeChanceMultiplier = hWas;
            com.sc.dim.SubstrateConfigSC.signalChanceMultiplier = gWas;
            com.sc.dim.SubstrateConfigSC.wardenHealthMultiplier = wWas;
            com.sc.dim.SubstrateConfigSC.wardenLootCooldownDays = lWas;
        }
        check(plain && raised, "dimension multipliers (ПМ-6): at 1 the storm 20%, the haze 25%, the Signal 15%, the Warden 600 HP, its loot every"
                + " 3 days; the storm x10 - 100% at most, the Warden x2 - 1200");
        // МБ-2: an ALIVE «Затмение» unseen 3 game days counts as dead since its last sighting - a new one may come at once
        check(com.sc.mob.SpawnDirectorSC.stale(0L, 72000L) && !com.sc.mob.SpawnDirectorSC.stale(0L, 71999L)
                        && com.sc.mob.SpawnRulesSC.eclipseChancePct(RareRegistrySC.DEAD, 1000L, 1000L + 72000L, false, true) > 0,
                "Eclipse (МБ-2): stale after 72 000 ticks unseen, not a tick before; dead since its sighting - the chance back 3 days on");
        // МБ-2 ★: a stale copy loaded back is the live one again only under its own DEAD record (or none) - not after a newer one
        check(com.sc.mob.SpawnDirectorSC.mayRevive(RareRegistrySC.DEAD, true) && !com.sc.mob.SpawnDirectorSC.mayRevive(RareRegistrySC.DEAD, false)
                        && !com.sc.mob.SpawnDirectorSC.mayRevive(RareRegistrySC.ALIVE, false) && com.sc.mob.SpawnDirectorSC.mayRevive(RareRegistrySC.NONE, false),
                "Eclipse (МБ-2 ★): an old copy revives under its own DEAD record or none; under another's DEAD (a newer one died) or ALIVE it leaves");
    }

    private static void section(int i) throws Exception {
        switch (i) {
            case 0: config(); break;
            case 1: biomeIds(); break;
            case 2: cells(); break;
            case 3: terrain(); break;
            case 4: radiation(); break;
            case 5: stormRules(); break;
            case 6: raidRules(); break;
            case 7: echo(); break;
            case 8: crumbs(); break;
            case 9: factions(); break;
            case 10: rareRegistry(); break;
            case 11: campLayout(); break;
            case 12: vaultLayout(); break;
            case 13: spawnRules(); break;
            case 14: pool(); break;
            case 15: lootAndTrophies(); break;
            case 16: relics(); break;
            case 17: texts(); break;
            default: handbook(); break;
        }
    }

    // ------------------------------------------------------------------ 1. the config (§2.1, ПК-17, ПФ-1, ПН-1)

    private static void config() {
        Set<String> sent = new TreeSet<String>();
        for (Field f : SubstrateNetSC.ruleFields()) {
            sent.add(f.getName());
        }
        // Ф5 appended requireSignal and caravans (docs/plan-substrate-tech-f5.md §2.1), Ф6 the embassy section's six (-f6.md §2.1), the audit of
        // 2026-10-20 six more of dimension (ПМ-6) and two of embassy (ПС-8): 28 rules now
        boolean rules = sent.size() == 28 && sent.contains("empStorms") && sent.contains("raids") && sent.contains("stormChanceMultiplier")
                && SubstrateConfigSC.empStorms && SubstrateConfigSC.raids;
        StringBuilder pub = new StringBuilder();
        for (String n : new String[]{"biomeIdRidges", "biomeIdOxide", "biomeIdRift"}) {
            try {
                Field f = SubstrateConfigSC.class.getDeclaredField(n);
                if (Modifier.isPublic(f.getModifiers()) || !Modifier.isStatic(f.getModifiers())) {
                    pub.append(' ').append(n);
                }
            } catch (NoSuchFieldException e) {
                pub.append(' ').append(n).append("(missing)");
            }
        }
        boolean ids = SubstrateConfigSC.biomeIdRidges() == 242 && SubstrateConfigSC.biomeIdOxide() == 243 && SubstrateConfigSC.biomeIdRift() == 244;
        check(rules && ids && pub.length() == 0, "config (§2.1): " + sent.size() + " rules the server sends (14 with Ф5's), empStorms " + SubstrateConfigSC.empStorms
                + " / raids " + SubstrateConfigSC.raids + " (true by default) " + rules + "; the new biome ids " + SubstrateConfigSC.biomeIdRidges() + " / "
                + SubstrateConfigSC.biomeIdOxide() + " / " + SubstrateConfigSC.biomeIdRift() + " (242 / 243 / 244) " + ids + ", private statics"
                + (pub.length() == 0 ? "" : " - NOT:" + pub) + (rules && ids ? "" : " (the dimension section of the test config edited?)"));
    }

    // ------------------------------------------------------------------ 2. SubstrateSC.pickBiomeId with the five (ПК-17)

    private static void biomeIds() {
        BiomeGenBase[] empty = new BiomeGenBase[256];
        int[] fresh = pickFive(empty, new int[]{240, 241, 242, 243, 244});
        int[] same = pickFive(empty, new int[]{240, 240, 240, 240, 240});
        BiomeGenBase[] taken = new BiomeGenBase[256];
        taken[242] = BiomeGenBase.desert;                                // another mod's biome on the Ridges' slot
        int[] moved = pickFive(taken, new int[]{240, 241, 242, 243, 244});
        boolean old = SubstrateSC.pickBiomeId(empty, 240, -1) == 240 && SubstrateSC.pickBiomeId(empty, 240, 240) == 241
                && SubstrateSC.pickBiomeId(taken, 242, -1) == 240;
        boolean ok = Arrays.equals(fresh, new int[]{240, 241, 242, 243, 244}) && Arrays.equals(same, new int[]{240, 241, 242, 243, 244})
                && Arrays.equals(moved, new int[]{240, 241, 243, 244, 245});
        check(ok && old, "biome ids (SubstrateSC.pickBiomeId, ПК-17): five in a row on an empty array " + Arrays.toString(fresh) + ", all wanting 240 "
                + Arrays.toString(same) + " (240..244), 242 taken by another mod " + Arrays.toString(moved) + " (240, 241, 243, 244, 245: each past the ones"
                + " picked before) " + ok + "; the old signature as before " + old);
    }

    private static int[] pickFive(BiomeGenBase[] slots, int[] want) {
        int[] ids = new int[want.length];
        for (int k = 0; k < want.length; k++) {
            ids[k] = SubstrateSC.pickBiomeId(slots, want[k], Arrays.copyOf(ids, k));
        }
        return ids;
    }

    // ------------------------------------------------------------------ 3. SubstrateCellsSC (§3.1, ПМ-10, ПМ-11)

    private static void cells() {
        // columnKind: the belt the canyons', the band up to INNER_MARGIN the plateaus', past it the cell's
        int g = SubstrateCellsSC.CELL, m = SubstrateCellsSC.INNER_MARGIN, mid = g + g / 2;
        boolean columns = SubstrateCellsSC.columnKind(BiomeSubstrateSC.RIDGES, g, mid) == BiomeSubstrateSC.CANYONS
                && SubstrateCellsSC.columnKind(BiomeSubstrateSC.RIDGES, g + SubstrateTerrainSC.CANYON_BELT + 1, mid) == BiomeSubstrateSC.PLATEAUS
                && SubstrateCellsSC.columnKind(BiomeSubstrateSC.RIDGES, g + m, mid) == BiomeSubstrateSC.PLATEAUS
                && SubstrateCellsSC.columnKind(BiomeSubstrateSC.RIDGES, g + m + 1, mid) == BiomeSubstrateSC.RIDGES
                && SubstrateCellsSC.columnKind(BiomeSubstrateSC.RIFT, 2 * g - m - 1, mid) == BiomeSubstrateSC.RIFT
                && SubstrateCellsSC.columnKind(BiomeSubstrateSC.RIFT, 2 * g - m, mid) == BiomeSubstrateSC.PLATEAUS
                && !SubstrateCellsSC.inner(g + m, mid) && SubstrateCellsSC.inner(g + m + 1, mid) && !SubstrateCellsSC.inner(mid, g - m);
        StringBuilder chunks = new StringBuilder();
        for (int off = 0; off < 16; off++) {
            boolean want = off >= 2 && off <= 13;
            if (SubstrateCellsSC.chunkHasInner(16 + off, 16 + 7) != want || SubstrateCellsSC.chunkHasInner(-16 + off, 7) != want
                    || SubstrateCellsSC.chunkHasInner(7, off) != want) {
                chunks.append(' ').append(off);
            }
        }
        // pureKind: determinism, the spawn ring, the settlements' and lithographs' cells, the Rift only far, the shares
        StringBuilder bad = new StringBuilder();
        int all = 0, ridges = 0, oxide = 0, far = 0, rift = 0, settled = 0, litho = 0;
        for (long seed : SEEDS) {
            for (int cx = -32; cx < 32; cx++) {
                for (int cz = -32; cz < 32; cz++) {
                    int k = SubstrateCellsSC.pureKind(seed, cx, cz);
                    if (k != SubstrateCellsSC.pureKind(seed, cx, cz) || k < 0 || k >= BiomeSubstrateSC.KINDS || k == BiomeSubstrateSC.CANYONS) {
                        bad.append(" kind ").append(cx).append(',').append(cz);
                    }
                    double dx = cx * g + g / 2 - WorldProviderSubstrateSC.SPAWN_X, dz = cz * g + g / 2 - WorldProviderSubstrateSC.SPAWN_Z;
                    boolean isFar = dx * dx + dz * dz >= (double) SubstrateCellsSC.RIFT_MIN_DIST * SubstrateCellsSC.RIFT_MIN_DIST;
                    if (Math.abs(cx) <= SubstrateCellsSC.SPAWN_RING && Math.abs(cz) <= SubstrateCellsSC.SPAWN_RING && k != BiomeSubstrateSC.PLATEAUS) {
                        bad.append(" spawn ").append(cx).append(',').append(cz);
                    }
                    if (k == BiomeSubstrateSC.RIFT && !isFar) {
                        bad.append(" near-rift ").append(cx).append(',').append(cz);
                    }
                    SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, cx >> 1, cz >> 1);
                    if (s != null && s.x >> 8 == cx && s.z >> 8 == cz) {
                        settled++;
                        if (k != BiomeSubstrateSC.PLATEAUS) {
                            bad.append(" settlement ").append(cx).append(',').append(cz);
                        }
                    }
                    if (RuinLayoutSC.lithoSite(seed, cx, cz) != null) {
                        litho++;
                        if (k != BiomeSubstrateSC.PLATEAUS) {
                            bad.append(" lithograph ").append(cx).append(',').append(cz);
                        }
                    }
                    all++;
                    ridges += k == BiomeSubstrateSC.RIDGES ? 1 : 0;
                    oxide += k == BiomeSubstrateSC.OXIDE ? 1 : 0;
                    if (isFar) {
                        far++;
                        rift += k == BiomeSubstrateSC.RIFT ? 1 : 0;
                    }
                }
            }
        }
        float pr = 100F * ridges / all, po = 100F * oxide / all, pf = far == 0 ? 0F : 100F * rift / far;
        boolean shares = Math.abs(pr - 18F) <= 4F && Math.abs(po - 15F) <= 4F && Math.abs(pf - 5F) <= 2F;
        SubstrateCellsSC.clearAll();
        boolean again = SubstrateCellsSC.pureKind(SEEDS[0], 7, -9) == SubstrateCellsSC.pureKind(SEEDS[0], 7, -9);
        boolean keys = SubstrateCellsSC.cellOf(255) == 0 && SubstrateCellsSC.cellOf(256) == 1 && SubstrateCellsSC.cellOf(-1) == -1
                && SubstrateCellsSC.key(1, -1) != SubstrateCellsSC.key(-1, 1) && SubstrateCellsSC.key(3, 4) == SubstrateCellsSC.key(3, 4);
        check(columns && chunks.length() == 0 && bad.length() == 0 && shares && again && keys, "cells (SubstrateCellsSC, ПМ-10, ПМ-11): columnKind - the belt"
                + " canyons, the band to " + m + " plateaus, " + (m + 1) + " the cell's; inner() alike " + columns + "; chunkHasInner only at offsets 2..13"
                + (chunks.length() == 0 ? "" : " - NOT:" + chunks) + "; pureKind deterministic, the spawn ring, " + settled + " settlement and " + litho
                + " lithograph cells the plateaus, the Rift only " + SubstrateCellsSC.RIFT_MIN_DIST + "+ from the spawn" + (bad.length() == 0 ? "" : " - BAD:"
                + bad.substring(0, Math.min(200, bad.length()))) + "; the shares of " + all + " cells: Ridges " + n(pr) + "% (18 +-4), Oxide Fields " + n(po)
                + "% (15 +-4), the Rift " + n(pf) + "% of the " + far + " far ones (5 +-2) " + shares + "; cellOf / key " + keys);
    }

    // ------------------------------------------------------------------ 4. SubstrateWildTerrainSC (package W, §3.2, ПМ-12..ПМ-14)

    private static void terrain() {
        long seed = SEEDS[0];
        int[] ridgeCell = findCell(seed, BiomeSubstrateSC.RIDGES), oxideCell = findCell(seed, BiomeSubstrateSC.OXIDE), riftCell = findCell(seed, BiomeSubstrateSC.RIFT);
        if (ridgeCell == null || oxideCell == null || riftCell == null) {
            check(false, "terrain (package W): no Ridges / Oxide / Rift cell within 40 cells of the spawn for seed " + seed);
            return;
        }
        int g = SubstrateCellsSC.CELL, lo = SubstrateCellsSC.INNER_MARGIN + 1, hi = g - SubstrateCellsSC.INNER_MARGIN - 1;
        // the Ridges: 64..140, the plateau's terrace at the inner square's edge, crests above the plateaus somewhere
        int rMin = Integer.MAX_VALUE, rMax = Integer.MIN_VALUE;
        StringBuilder edge = new StringBuilder();
        for (int a = lo; a <= hi; a += 4) {
            for (int b = lo; b <= hi; b += 4) {
                int y = SubstrateWildTerrainSC.ridgeY(seed, ridgeCell[0] * g + a, ridgeCell[1] * g + b);
                rMin = Math.min(rMin, y);
                rMax = Math.max(rMax, y);
            }
            int x = ridgeCell[0] * g + a, z = ridgeCell[1] * g + lo;
            if (SubstrateWildTerrainSC.ridgeY(seed, x, z) != SubstrateTerrainSC.terraceY(seed, x, z)) {
                edge.append(' ').append(x).append(',').append(z);
            }
        }
        boolean ridges = rMin >= SubstrateTerrainSC.PLATEAU_MIN && rMax <= SubstrateWildTerrainSC.RIDGE_MAX && rMax > SubstrateTerrainSC.PLATEAU_MAX + 16;
        // the Oxide Fields: 64..80
        int dMin = Integer.MAX_VALUE, dMax = Integer.MIN_VALUE;
        for (int a = lo; a <= hi; a += 4) {
            for (int b = lo; b <= hi; b += 4) {
                int y = SubstrateWildTerrainSC.duneY(seed, oxideCell[0] * g + a, oxideCell[1] * g + b);
                dMin = Math.min(dMin, y);
                dMax = Math.max(dMax, y);
            }
        }
        boolean dunes = dMin >= SubstrateWildTerrainSC.DUNE_MIN && dMax <= SubstrateWildTerrainSC.DUNE_MAX;
        // the Rift: no void on the rim, void inside, islands within their heights, the pillar in the middle
        int rimVoid = 0, voidCols = 0, inside = 0, islandCols = 0;
        StringBuilder islands = new StringBuilder();
        for (int a = lo; a <= hi; a += 2) {
            for (int b = lo; b <= hi; b += 2) {
                int x = riftCell[0] * g + a, z = riftCell[1] * g + b;
                boolean rim = a < lo + SubstrateWildTerrainSC.RIFT_RIM || a > hi - SubstrateWildTerrainSC.RIFT_RIM || b < lo + SubstrateWildTerrainSC.RIFT_RIM
                        || b > hi - SubstrateWildTerrainSC.RIFT_RIM;
                boolean v = SubstrateWildTerrainSC.riftVoid(seed, x, z);
                if (rim) {
                    rimVoid += v ? 1 : 0;
                } else {
                    inside++;
                    voidCols += v ? 1 : 0;
                }
                int[] is = SubstrateWildTerrainSC.islandAt(seed, x, z);
                if (is != null) {
                    islandCols++;
                    if (is.length < 2 || is[0] < SubstrateWildTerrainSC.ISLAND_MIN_Y || is[1] > SubstrateWildTerrainSC.ISLAND_MAX_Y || is[0] > is[1]) {
                        islands.append(' ').append(x).append(',').append(z).append(':').append(Arrays.toString(is));
                    }
                }
            }
        }
        int cx = riftCell[0] * g + g / 2, cz = riftCell[1] * g + g / 2;
        boolean pillar = SubstrateWildTerrainSC.pillar(cx, cz) && SubstrateWildTerrainSC.pillar(cx + SubstrateWildTerrainSC.PILLAR_RADIUS, cz)
                && !SubstrateWildTerrainSC.pillar(cx + SubstrateWildTerrainSC.PILLAR_RADIUS + 1, cz) && !SubstrateWildTerrainSC.pillar(cx + 40, cz + 40);
        boolean rift = rimVoid == 0 && voidCols * 2 > inside && islandCols > 0 && islands.length() == 0;
        check(ridges && edge.length() == 0 && dunes && rift && pillar, "terrain (package W, ПМ-12..ПМ-14): the Ridges of cell " + ridgeCell[0] + "," + ridgeCell[1]
                + " - " + rMin + ".." + rMax + " (64..140, crests over the plateaus' " + SubstrateTerrainSC.PLATEAU_MAX + ") " + ridges + ", the plateau's terrace"
                + " at the inner square's edge" + (edge.length() == 0 ? "" : " - NOT:" + edge.substring(0, Math.min(120, edge.length()))) + "; the dunes of cell "
                + oxideCell[0] + "," + oxideCell[1] + " - " + dMin + ".." + dMax + " (64..80) " + dunes + "; the Rift of cell " + riftCell[0] + "," + riftCell[1]
                + " - void on the rim " + rimVoid + " (0), inside " + voidCols + " of " + inside + " (most), island columns " + islandCols + " (some) in Y "
                + SubstrateWildTerrainSC.ISLAND_MIN_Y + ".." + SubstrateWildTerrainSC.ISLAND_MAX_Y + (islands.length() == 0 ? "" : " - BAD:"
                + islands.substring(0, Math.min(120, islands.length()))) + " " + rift + "; the puncture pillar radius " + SubstrateWildTerrainSC.PILLAR_RADIUS
                + " " + pillar);
    }

    /** The cell of `kind` nearest the spawn (by pureKind), or null within 40 cells. */
    static int[] findCell(long seed, int kind) {
        for (int r = 0; r <= 40; r++) {
            for (int cx = -r; cx <= r; cx++) {
                for (int cz = -r; cz <= r; cz++) {
                    if ((Math.abs(cx) == r || Math.abs(cz) == r) && SubstrateCellsSC.pureKind(seed, cx, cz) == kind) {
                        return new int[]{cx, cz};
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 5. SubstrateRadiationSC (§4.1, ПА-1)

    private static void radiation() {
        float[] got = new float[BiomeSubstrateSC.KINDS];
        for (int k = 0; k < got.length; k++) {
            got[k] = SubstrateRadiationSC.backgroundLevel(k);
        }
        boolean ok = Arrays.equals(got, new float[]{0F, 0F, 0F, 1F, 2F}) && SubstrateRadiationSC.background(null) == 0F
                && StormRulesSC.EMISSION_RADIATION_MUL == 3F && SubstrateRadiationSC.RTG_RADIUS == 12 && SubstrateRadiationSC.PILLAR_RADIUS == 32;
        check(ok, "radiation (SubstrateRadiationSC, ПА-1, ПА-2): the background by kind " + Arrays.toString(got) + " (plateaus, canyons, ridges 0, oxide 1,"
                + " the Rift 2), none without a player, the Emission x" + n(StormRulesSC.EMISSION_RADIATION_MUL) + ", the RTG within " + SubstrateRadiationSC.RTG_RADIUS
                + ", the pillar within " + SubstrateRadiationSC.PILLAR_RADIUS + " " + ok);
    }

    // ------------------------------------------------------------------ 6. StormRulesSC (§5.2, ПФ-1, ПФ-3, ПФ-9, ПФ-10)

    private static void stormRules() {
        long seed = SEEDS[0], day = StormRulesSC.DAY;
        int days = 2000, storms = 0, hazes = 0;
        StringBuilder bad = new StringBuilder();
        for (long d = 0; d < days; d++) {
            if (StormRulesSC.rolls(seed, d) != StormRulesSC.rolls(seed, d)) {
                bad.append(" roll ").append(d);
            }
            boolean storm = StormRulesSC.rolls(seed, d);
            long start = StormRulesSC.startOf(seed, d);
            int dur = StormRulesSC.durationOf(seed, d);
            if (storm) {
                storms++;
                if (dur < StormRulesSC.MIN_TICKS || dur > StormRulesSC.MAX_TICKS || start - StormRulesSC.WARN_TICKS < d * day + StormRulesSC.DAY_MARGIN
                        || start + dur > (d + 1) * day) {
                    bad.append(" storm ").append(d);
                }
            }
            if (StormRulesSC.hazeRolls(seed, d)) {
                hazes++;
                long[] h = StormRulesSC.hazeWindow(seed, d);
                long len = h[1] - h[0];
                boolean overlaps = storm && h[0] < start + dur && h[1] > start - StormRulesSC.WARN_TICKS;
                if (len < StormRulesSC.HAZE_MIN || len > StormRulesSC.HAZE_MAX || h[0] < d * day || h[1] > (d + 1) * day || overlaps) {
                    bad.append(" haze ").append(d).append(overlaps ? "(over the storm)" : "");
                }
            }
        }
        for (long w = 0; w < 300; w++) {
            long[] e = StormRulesSC.emissionWindow(seed, w);
            if (e[1] - e[0] != StormRulesSC.EMISSION_TICKS || e[0] < w * StormRulesSC.WEEK_DAYS * day || e[1] > (w + 1) * StormRulesSC.WEEK_DAYS * day) {
                bad.append(" emission ").append(w);
            }
        }
        float share = 100F * storms / days, hazeShare = 100F * hazes / days;
        long s0 = 10000L, e0 = 14000L;
        boolean phases = StormRulesSC.phaseAt(s0, e0, s0 - StormRulesSC.WARN_TICKS - 1) == StormStateSC.CALM
                && StormRulesSC.phaseAt(s0, e0, s0 - StormRulesSC.WARN_TICKS) == StormStateSC.WARNING && StormRulesSC.phaseAt(s0, e0, s0 - 1) == StormStateSC.WARNING
                && StormRulesSC.phaseAt(s0, e0, s0) == StormStateSC.STORM && StormRulesSC.phaseAt(s0, e0, e0 - 1) == StormStateSC.STORM
                && StormRulesSC.phaseAt(s0, e0, e0) == StormStateSC.CALM && StormStateSC.CALM == 0 && StormStateSC.WARNING == 1 && StormStateSC.STORM == 2;
        boolean cover = !StormRulesSC.covered(2) && StormRulesSC.covered(3) && StormRulesSC.COVER_BLOCKS == 3 && StormRulesSC.COVER_SCAN == 48;
        Random r = new Random(5L);
        boolean pulses = true;
        for (int i = 0; i < 500; i++) {
            int p = StormRulesSC.nextPulse(r), sup = StormRulesSC.suppressTicks(r);
            pulses &= p >= StormRulesSC.PULSE_MIN && p <= StormRulesSC.PULSE_MAX && sup >= StormRulesSC.SUPPRESS_MIN && sup <= StormRulesSC.SUPPRESS_MAX;
        }
        boolean nums = StormRulesSC.CHANCE_PCT == 20 && StormRulesSC.WARN_TICKS == 600 && StormRulesSC.REACH == 128 && StormRulesSC.SWARM_MUL == 3
                && StormRulesSC.TROPHY_MUL == 1.5F && StormRulesSC.HAZE_SIGHT == 24 && StormRulesSC.EMISSION_TICKS == 12000 && StormRulesSC.SHELL_REBOOT_TICKS == 60;
        boolean ok = Math.abs(share - 20F) <= 3F && Math.abs(hazeShare - StormRulesSC.HAZE_CHANCE_PCT) <= 4F && bad.length() == 0 && phases && cover && pulses && nums;
        check(ok, "storm rules (StormRulesSC, ПФ-1..ПФ-3, ПФ-9, ПФ-10): storms on " + n(share) + "% of " + days + " days (20 +-3), " + StormRulesSC.MIN_TICKS + ".."
                + StormRulesSC.MAX_TICKS + " ticks, the warning and the storm inside the day; the haze on " + n(hazeShare) + "% (" + StormRulesSC.HAZE_CHANCE_PCT
                + "), " + StormRulesSC.HAZE_MIN + ".." + StormRulesSC.HAZE_MAX + " ticks, never over a storm or its warning; the Emission " + StormRulesSC.EMISSION_TICKS
                + " ticks inside its week" + (bad.length() == 0 ? "" : " - BAD:" + bad.substring(0, Math.min(200, bad.length()))) + "; phaseAt (601 before CALM,"
                + " 600 WARNING, in the window STORM, at the end CALM) " + phases + "; covered(2) no / (3) yes " + cover + "; pulses / suppressions in range "
                + pulses + "; the numbers " + nums);
    }

    // ------------------------------------------------------------------ 7. RaidRulesSC (§6.1, ПН-1..ПН-4)

    private static void raidRules() {
        boolean night = RaidRulesSC.night(13000) && !RaidRulesSC.night(12999) && RaidRulesSC.night(22999) && !RaidRulesSC.night(23000)
                && RaidRulesSC.night(5 * 24000L + 18000L) && !RaidRulesSC.night(5 * 24000L + 6000L);
        boolean reach = RaidRulesSC.reach(0) == 64 && RaidRulesSC.reach(1) == 80 && RaidRulesSC.reach(2) == 96 && RaidRulesSC.reach(7) == 96 && RaidRulesSC.reach(-1) == 64
                && SettlementSizeSC.SUBSTRATE_SIZES == RaidRulesSC.REACH.length;     // Ф6: EMBASSY (the Overworld's) is never raided
        boolean may = RaidRulesSC.mayRoll(true, true, 20, 19, -1, true) && !RaidRulesSC.mayRoll(false, true, 20, 19, -1, true)
                && !RaidRulesSC.mayRoll(true, false, 20, 19, -1, true) && !RaidRulesSC.mayRoll(true, true, 20, 20, -1, true)
                && !RaidRulesSC.mayRoll(true, true, 20, 19, 16, true) && RaidRulesSC.mayRoll(true, true, 20, 19, 15, true)
                && !RaidRulesSC.mayRoll(true, true, 20, 19, -1, false);
        boolean defender = RaidRulesSC.defender(ReputationSC.ACQUAINTANCE_FROM) && !RaidRulesSC.defender(ReputationSC.ACQUAINTANCE_FROM - 1);
        int rolls = 0, tries = 0;
        StringBuilder bad = new StringBuilder();
        for (int id = 1; id <= 50; id++) {
            for (long d = 0; d < 200; d++) {
                boolean r = RaidRulesSC.rolls(SEEDS[0], id, d);
                if (r != RaidRulesSC.rolls(SEEDS[0], id, d)) {
                    bad.append(" roll ").append(id).append('/').append(d);
                }
                rolls += r ? 1 : 0;
                tries++;
            }
        }
        float share = 100F * rolls / tries;
        // the waves by size: Nano, Quantum, then the elite (a fixed roll under 50%) or the Shells' column
        for (int s = 0; s < SettlementSizeSC.SUBSTRATE_SIZES; s++) {
            int[][] w0 = RaidRulesSC.wave(0, s, new Random(1L)), w1 = RaidRulesSC.wave(1, s, new Random(1L));
            int[][] elite = RaidRulesSC.wave(2, s, new FixedRoll(0F)), column = RaidRulesSC.wave(2, s, new FixedRoll(0.99F));
            if (count(w0, MobEntitiesSC.ID_MARAUDER) != 2 + s || count(w0, MobEntitiesSC.ID_LINEMAN) != 2 || count(w1, MobEntitiesSC.ID_TROOPER) != 1 + s
                    || count(w1, MobEntitiesSC.ID_JAMMER) != 1 || count(elite, MobEntitiesSC.ID_JAMMER) != 1 || count(elite, MobEntitiesSC.ID_MARAUDER) != 1 + s
                    || count(elite, MobEntitiesSC.ID_TROOPER) != 1 || count(column, MobEntitiesSC.ID_HOLLOW_SHELL) != 2 + s
                    || count(column, MobEntitiesSC.ID_REPAIR_DRONE) != 2 || !RaidRulesSC.eliteWave(2, elite) || RaidRulesSC.eliteWave(2, column)
                    || RaidRulesSC.eliteWave(0, w0) || RaidRulesSC.eliteWave(1, w1)) {
                bad.append(" waves of size ").append(s);
            }
        }
        boolean rewards = RaidRulesSC.rep(0) == 30 && RaidRulesSC.rep(1) == 40 && RaidRulesSC.rep(2) == 50 && RaidRulesSC.chips(0) == 6 && RaidRulesSC.chips(1) == 9
                && RaidRulesSC.chips(2) == 12 && Arrays.equals(RaidRulesSC.CHEST_ROLLS, new int[]{2, 3, 4}) && RaidRulesSC.CLOSED_TICKS == 24000L
                && RaidRulesSC.WAVES == 3 && RaidRulesSC.COOLDOWN_DAYS == 5 && ReputationSC.SRC_RAID == 9;
        boolean ok = night && reach && may && defender && Math.abs(share - 10F) <= 2F && bad.length() == 0 && rewards;
        check(ok, "raid rules (RaidRulesSC, ПН-1..ПН-4): the night 13000..22999 " + night + "; the reach 64 / 80 / 96 by size " + reach + "; mayRoll (the rule,"
                + " the night, rolled tonight, 5 days since the last raid, a defender) " + may + "; a defender from Acquaintance (" + ReputationSC.ACQUAINTANCE_FROM
                + ") " + defender + "; rolls " + n(share) + "% of " + tries + " (10 +-2); the three waves by size and eliteWave" + (bad.length() == 0 ? " ok"
                : " - BAD:" + bad) + "; the rewards 30/40/50 Trust, 6/9/12 chips, 2/3/4 rolls, the trade closed a day, SRC_RAID 9 " + rewards);
    }

    private static int count(int[][] wave, int id) {
        int n = 0;
        for (int[] g : wave) {
            n += g[0] == id ? g[1] : 0;
        }
        return n;
    }

    /** A Random whose nextFloat is fixed: the third wave of either kind. */
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

    // ------------------------------------------------------------------ 8. BridgeEchoSC's pure rolls (§6.3, ПН-9, ПН-10)

    private static void echo() {
        float in = BridgeEchoSC.HOME_CHANCE - 0.01F, out = BridgeEchoSC.HOME_CHANCE;
        boolean home = BridgeEchoSC.homeEcho(true, false, false, in) && !BridgeEchoSC.homeEcho(true, false, true, in) && !BridgeEchoSC.homeEcho(true, false, false, out)
                && !BridgeEchoSC.homeEcho(false, false, false, in) && !BridgeEchoSC.homeEcho(true, true, false, in);
        float ain = BridgeEchoSC.ARRIVAL_CHANCE - 0.01F, aout = BridgeEchoSC.ARRIVAL_CHANCE;
        boolean arrival = BridgeEchoSC.arrivalEcho(false, true, false, false, ain) && !BridgeEchoSC.arrivalEcho(false, true, true, false, ain)
                && !BridgeEchoSC.arrivalEcho(false, true, false, true, ain) && !BridgeEchoSC.arrivalEcho(false, true, false, false, aout)
                && !BridgeEchoSC.arrivalEcho(true, true, false, false, ain) && !BridgeEchoSC.arrivalEcho(false, false, false, false, ain);
        boolean nums = BridgeEchoSC.HOME_CHANCE == 0.10F && BridgeEchoSC.ARRIVAL_CHANCE == 0.10F && BridgeEchoSC.SQUAD_DELAY == 1200 && BridgeEchoSC.COOLDOWN_TICKS == 12000
                && BridgeEchoSC.ECHO_LIFE_TICKS == 1200 && "scEcho".equals(BridgeEchoSC.ECHO_NBT);
        check(home && arrival && nums, "echo (BridgeEchoSC, ПН-9, ПН-10): home - out of the Substrate, no Shield, the roll under 10% " + home + "; the squad - into"
                + " the Substrate, not at a dock / dome, no cooldown, the roll " + arrival + "; the numbers (10% / 10%, 60 s, 10 min, scEcho) " + nums);
    }

    // ------------------------------------------------------------------ 9. CrumbLimitSC (§9.4, ПТ-17)

    private static void crumbs() {
        boolean allowed = CrumbLimitSC.allowed(0, 1, 3) == 1 && CrumbLimitSC.allowed(3, 1, 3) == 0 && CrumbLimitSC.allowed(2, 5, 3) == 1
                && CrumbLimitSC.allowed(0, 5, 3) == 3 && CrumbLimitSC.allowed(-4, 2, 3) == 2 && CrumbLimitSC.allowed(1, 0, 3) == 0;
        boolean nobody = CrumbLimitSC.left(null) == 0 && CrumbLimitSC.take(null, 1) == 0 && CrumbLimitSC.take(null, 0) == 0;
        boolean nums = CrumbLimitSC.DAY_CAP == 3 && "K".equals(CrumbLimitSC.EXTRA);
        check(allowed && nobody && nums, "crumbs (CrumbLimitSC, ПТ-17): allowed 0+1 of 3 -> 1, 3 -> 0, 2+5 -> 1, 0+5 -> 3 " + allowed + "; no player - none "
                + nobody + "; DAY_CAP 3, PlayerRec.extra \"K\" " + nums + " (a real player's count: WorldTestHazardSC)");
    }

    // ------------------------------------------------------------------ 10. FactionSC (§8.3, ПЗ-3)

    private static void factions() {
        Map<Class<?>, String> want = new LinkedHashMap<Class<?>, String>();
        want.put(EntityMarauderSC.class, FactionSC.RENEGADES);
        want.put(EntityTrooperSC.class, FactionSC.RENEGADES);
        want.put(EntityJammerSC.class, FactionSC.RENEGADES);
        want.put(EntityExecutionerSC.class, FactionSC.RENEGADES);
        want.put(EntityLinemanSC.class, FactionSC.SHELLS);
        want.put(EntityHollowShellSC.class, FactionSC.SHELLS);
        want.put(EntityRepairDroneSC.class, FactionSC.SHELLS);
        want.put(EntityNaniteSC.class, FactionSC.NONE);
        want.put(EntitySentryDroneSC.class, FactionSC.NONE);
        want.put(EntityMimicSC.class, FactionSC.NONE);
        want.put(EntityOverheatedSC.class, FactionSC.SHELLS);
        want.put(EntityWarlordSC.class, FactionSC.RENEGADES);
        want.put(EntitySentinelSC.class, FactionSC.SHELLS);
        want.put(EntityEclipseSC.class, FactionSC.RENEGADES);
        want.put(EntityPhantomSC.class, FactionSC.NONE);
        want.put(EntityLeechSC.class, FactionSC.NONE);
        want.put(EntityColossusSC.class, FactionSC.NONE);
        want.put(EntityStriderSC.class, FactionSC.NONE);
        want.put(EntityScrapperSC.class, FactionSC.NONE);
        want.put(EntityWaferCrabSC.class, FactionSC.NONE);
        StringBuilder bad = new StringBuilder(), noWorld = new StringBuilder();
        int made = 0;
        for (Map.Entry<Class<?>, String> w : want.entrySet()) {
            if (!IFactionSC.class.isAssignableFrom(w.getKey())) {
                bad.append(' ').append(w.getKey().getSimpleName()).append(":not IFactionSC");
                continue;
            }
            Entity e = make(w.getKey(), noWorld);
            if (e == null) {
                continue;
            }
            made++;
            String f = FactionSC.of(e);
            if (!w.getValue().equals(f) || FactionSC.is(e, FactionSC.NONE) || FactionSC.is(e, null) || !w.getValue().isEmpty() && !FactionSC.is(e, w.getValue())) {
                bad.append(' ').append(w.getKey().getSimpleName()).append(":'").append(f).append('\'');
            }
            boolean before = !Arrays.asList(MOBS).contains(w.getKey());          // Ф1..Ф3's classes: their faction by class
            if (before && !w.getValue().equals(FactionSC.defaultOf(e))) {
                bad.append(' ').append(w.getKey().getSimpleName()).append(":defaultOf '").append(FactionSC.defaultOf(e)).append('\'');
            }
        }
        Entity item = new EntityItem((World) null);
        boolean plain = FactionSC.NONE.equals(FactionSC.of(item)) && FactionSC.NONE.equals(FactionSC.of(null)) && FactionSC.NONE.equals(FactionSC.defaultOf(item))
                && "renegades".equals(FactionSC.RENEGADES) && "shells".equals(FactionSC.SHELLS) && "".equals(FactionSC.NONE);
        if (noWorld.length() > 0) {
            info("factions - made without a world, no (WorldTestWildSC checks them):" + noWorld);
        }
        check(bad.length() == 0 && plain && made > 0, "factions (FactionSC, ПЗ-3): " + made + " of " + want.size() + " creatures made - Renegades (Marauder,"
                + " Trooper, Jammer, Executioner, Warlord, Eclipse), Shells (Lineman, Hollow Shell, its drone, Overheated, Sentinel), the rest none; Ф1..Ф3 by"
                + " defaultOf" + (bad.length() == 0 ? " ok" : " - BAD:" + bad) + "; a plain entity / null none " + plain);
    }

    // ------------------------------------------------------------------ 11. RareRegistrySC (§2.4, ПС-30)

    private static void rareRegistry() {
        RareRegistrySC a = new RareRegistrySC(RareRegistrySC.NAME);
        Entity e = new EntityItem((World) null), stranger = new EntityItem((World) null);
        e.setPosition(100.5, 64, -20.5);
        String vault = RareRegistrySC.vaultKey(3, -2), camp = RareRegistrySC.campKey(CampLayoutSC.AXIS_Z, 4, -1);
        a.alive(vault, e, 500L);
        a.alive(RareRegistrySC.ECLIPSE, e, 600L);                        // one entity for two keys: the test's own (its NBT keeps the last)
        a.died(RareRegistrySC.ECLIPSE, 900L);
        a.rec(camp).extra.setInteger("Safe", 7);
        NBTTagCompound n = new NBTTagCompound();
        a.writeToNBT(n);
        RareRegistrySC b = new RareRegistrySC(RareRegistrySC.NAME);
        b.readFromNBT(n);
        RareRegistrySC.Rec v = b.recIfAny(vault), ec = b.recIfAny(RareRegistrySC.ECLIPSE), cp = b.recIfAny(camp);
        boolean trip = v != null && v.state == RareRegistrySC.ALIVE && e.getUniqueID().equals(v.uuid()) && v.spawnedAt == 500L && v.x == 100 && v.z == -21
                && ec != null && ec.state == RareRegistrySC.DEAD && ec.diedAt == 900L && cp != null && cp.state == RareRegistrySC.NONE && cp.extra.getInteger("Safe") == 7
                && b.all().size() == 3;
        boolean tags = RareRegistrySC.ECLIPSE.equals(RareRegistrySC.keyOf(e)) && RareRegistrySC.keyOf(stranger) == null && RareRegistrySC.keyOf(null) == null
                && "vault:3,-2".equals(vault) && "camp:1,4,-1".equals(camp) && "scRareKey".equals(RareRegistrySC.KEY_NBT);
        long cool = 72000L;
        boolean respawn = RareRegistrySC.mayRespawn(RareRegistrySC.NONE, 0L, 0L, cool) && !RareRegistrySC.mayRespawn(RareRegistrySC.ALIVE, 0L, 1000000L, cool)
                && !RareRegistrySC.mayRespawn(RareRegistrySC.DEAD, 100L, 100L + cool - 1, cool) && RareRegistrySC.mayRespawn(RareRegistrySC.DEAD, 100L, 100L + cool, cool);
        boolean seen = b.seen(vault, e, 700L) && !b.seen(vault, stranger, 700L) && b.recIfAny(vault).seenAt == 700L;
        boolean missed = true;
        for (int i = 1; i < RareRegistrySC.LOST_CHECKS; i++) {
            missed &= !b.missed(vault, 800L + i);
        }
        missed &= b.missed(vault, 900L) && b.recIfAny(vault).state == RareRegistrySC.DEAD && b.recIfAny(vault).diedAt == 900L && !b.missed(vault, 901L)
                && RareRegistrySC.LOST_CHECKS == 10;
        check(trip && tags && respawn && seen && missed, "rare registry (RareRegistrySC, ПС-30): NBT there and back (ALIVE with its UUID, time and place, DEAD"
                + " with its time, a record's extra) " + trip + "; the key in the entity's NBT, the keys' form " + tags + "; mayRespawn (NONE at once, ALIVE never,"
                + " DEAD after the cooldown) " + respawn + "; seen only by its UUID " + seen + "; missed - dead after " + RareRegistrySC.LOST_CHECKS + " looks " + missed);
    }

    // ------------------------------------------------------------------ 12. CampLayoutSC (§8.1, ПЗ-1)

    private static void campLayout() {
        long seed = SEEDS[0];
        int grid = SubstrateTerrainSC.GRID, segs = 0, camps = 0;
        StringBuilder bad = new StringBuilder();
        for (int axis = CampLayoutSC.AXIS_X; axis <= CampLayoutSC.AXIS_Z; axis++) {
            for (int line = -30; line <= 30; line++) {
                for (int seg = -30; seg <= 30; seg++) {
                    int along = seg * grid + grid / 2;
                    int mx = axis == CampLayoutSC.AXIS_X ? along : line * grid, mz = axis == CampLayoutSC.AXIS_X ? line * grid : along;
                    double d = Math.hypot(mx - WorldProviderSubstrateSC.SPAWN_X, mz - WorldProviderSubstrateSC.SPAWN_Z);
                    CampLayoutSC.Site s = CampLayoutSC.site(seed, axis, line, seg), again = CampLayoutSC.site(seed, axis, line, seg);
                    if ((s == null) != (again == null) || s != null && (s.x != again.x || s.z != again.z || s.seed != again.seed || s.marauders != again.marauders)) {
                        bad.append(" determinism ").append(axis).append('/').append(line).append('/').append(seg);
                    }
                    if (d >= CampLayoutSC.MIN_SPAWN_DIST + 2 * grid) {
                        segs++;
                        camps += s != null ? 1 : 0;
                    }
                    if (s == null) {
                        continue;
                    }
                    String why = campProblem(seed, s);
                    if (why != null && bad.length() < 300) {
                        bad.append(' ').append(axis).append('/').append(line).append('/').append(seg).append(':').append(why);
                    }
                }
            }
        }
        float share = segs == 0 ? 0F : 100F * camps / segs;
        boolean ok = Math.abs(share - CampLayoutSC.CHANCE_PCT) <= 4F && bad.length() == 0 && CampLayoutSC.FIELD_RADIUS < CampLayoutSC.HALF_LEN;
        check(ok, "camp layout (CampLayoutSC, ПЗ-1): camps on " + n(share) + "% of " + segs + " far canyon segments (" + CampLayoutSC.CHANCE_PCT + " +-4),"
                + " deterministic, never within " + CampLayoutSC.MIN_SPAWN_DIST + " of the spawn, the box clear of the Nodes' zones (" + RuinLayoutSC.NODE_HALF
                + ") and of the settlements' belts, " + CampLayoutSC.MARAUDERS_MIN + ".." + CampLayoutSC.MARAUDERS_MAX + " Marauders, the Executioner only "
                + CampLayoutSC.EXECUTIONER_MIN_DIST + "+ away, siteAtChunk / siteNear agree with site" + (bad.length() == 0 ? " ok" : " - BAD:" + bad));
    }

    /** What is wrong with camp `s` (null: nothing) - clearances, the residents, the look-ups. */
    private static String campProblem(long seed, CampLayoutSC.Site s) {
        int grid = SubstrateTerrainSC.GRID;
        double d = Math.hypot(s.x - WorldProviderSubstrateSC.SPAWN_X, s.z - WorldProviderSubstrateSC.SPAWN_Z);
        if (d < CampLayoutSC.MIN_SPAWN_DIST) {
            return "near the spawn";
        }
        int along = s.axis == CampLayoutSC.AXIS_X ? s.x : s.z, across = s.axis == CampLayoutSC.AXIS_X ? s.z : s.x;
        if (across != s.line * grid) {
            return "off the centre line";
        }
        int fromCrossing = Math.min(along - s.seg * grid, (s.seg + 1) * grid - along);
        if (fromCrossing - CampLayoutSC.HALF_LEN <= RuinLayoutSC.NODE_HALF) {
            return "in a Node's zone";
        }
        if (s.marauders < CampLayoutSC.MARAUDERS_MIN || s.marauders > CampLayoutSC.MARAUDERS_MAX || s.executioner && d < CampLayoutSC.EXECUTIONER_MIN_DIST) {
            return "residents";
        }
        int[] box = s.box();
        CampLayoutSC.Site c = CampLayoutSC.siteAtChunk(seed, s.x >> 4, s.z >> 4), near = CampLayoutSC.siteNear(seed, s.x, s.z, 0);
        CampLayoutSC.Site edge = CampLayoutSC.siteNear(seed, box[0], box[1], 0), off = CampLayoutSC.siteNear(seed, box[0] - 2, box[1] - 2, 0);
        if (c == null || c.x != s.x || c.z != s.z || near == null || near.x != s.x || edge == null || edge.x != s.x || off != null && off.x == s.x && off.z == s.z) {
            return "look-ups";
        }
        // a settlement's dome and belt never reach the box (they stand on the plateaus, the camp in the canyon)
        int rx0 = SettlementLayoutSC.regionOf(box[0] - 200), rx1 = SettlementLayoutSC.regionOf(box[2] + 200), rz0 = SettlementLayoutSC.regionOf(box[1] - 200),
                rz1 = SettlementLayoutSC.regionOf(box[3] + 200);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                SettlementLayoutSC.Site st = SettlementLayoutSC.site(seed, rx, rz);
                if (st == null) {
                    continue;
                }
                double dx = Math.max(0, Math.max(box[0] - st.x, st.x - box[2])), dz = Math.max(0, Math.max(box[1] - st.z, st.z - box[3]));
                if (Math.hypot(dx, dz) <= st.radius + SettlementSC.BELT) {
                    return "a settlement's belt (" + st.x + "," + st.z + ")";
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 13. VaultLayoutSC (§8.5, ПХ-1)

    private static void vaultLayout() {
        long seed = SEEDS[0];
        int grid = SubstrateTerrainSC.GRID, blocks = 0, vaults = 0;
        StringBuilder bad = new StringBuilder();
        for (int bi = -6; bi <= 6; bi++) {
            for (int bj = -6; bj <= 6; bj++) {
                blocks++;
                int[] v = VaultLayoutSC.vaultOf(seed, bi, bj);
                int found = 0;
                for (int i = bi * VaultLayoutSC.BLOCK; i < (bi + 1) * VaultLayoutSC.BLOCK; i++) {
                    for (int j = bj * VaultLayoutSC.BLOCK; j < (bj + 1) * VaultLayoutSC.BLOCK; j++) {
                        if (VaultLayoutSC.hasVault(seed, i, j)) {
                            found++;
                            if (Math.abs(i) <= VaultLayoutSC.SPAWN_RING && Math.abs(j) <= VaultLayoutSC.SPAWN_RING) {
                                bad.append(" spawn ").append(i).append(',').append(j);
                            }
                        }
                    }
                }
                if (found > 1 || (v == null) != (found == 0)) {
                    bad.append(" block ").append(bi).append(',').append(bj).append(':').append(found);
                }
                if (v == null) {
                    continue;
                }
                vaults++;
                int x = v[0] * grid, z = v[1] * grid, f = VaultLayoutSC.floorY(seed, v[0], v[1]);
                int[] near = VaultLayoutSC.vaultNear(seed, x + VaultLayoutSC.HALF, z - VaultLayoutSC.HALF, 0);
                boolean inside = VaultLayoutSC.inVault(seed, x, f, z) && VaultLayoutSC.inVault(seed, x + 3, f + VaultLayoutSC.HEIGHT - 1, z - 3)
                        && !VaultLayoutSC.inVault(seed, x, f - 1, z) && !VaultLayoutSC.inVault(seed, x, f + VaultLayoutSC.HEIGHT, z)
                        && !VaultLayoutSC.inVault(seed, x + VaultLayoutSC.HALF + 1, f + 1, z);
                if (f < VaultLayoutSC.FLOOR_MIN || f != Math.max(VaultLayoutSC.FLOOR_MIN, SubstrateTerrainSC.nodeFloorY(seed, v[0], v[1]) - VaultLayoutSC.DEPTH)
                        || near == null || near[0] != v[0] || near[1] != v[1] || VaultLayoutSC.vaultNear(seed, x + VaultLayoutSC.HALF + 1, z, 0) != null || !inside) {
                    bad.append(" vault ").append(v[0]).append(',').append(v[1]);
                }
            }
        }
        // nearest: the closest of the blocks within 2 of the point, by brute force
        boolean nearest = true;
        Random r = new Random(9L);
        int bgrid = grid * VaultLayoutSC.BLOCK;
        for (int k = 0; k < 40 && nearest; k++) {
            int x = r.nextInt(12000) - 6000, z = r.nextInt(12000) - 6000;
            int[] got = VaultLayoutSC.nearest(seed, x, z, 2);
            double best = Double.MAX_VALUE;
            int bi0 = (int) Math.floor(x / (double) bgrid), bj0 = (int) Math.floor(z / (double) bgrid);
            for (int bi = bi0 - 2; bi <= bi0 + 2; bi++) {
                for (int bj = bj0 - 2; bj <= bj0 + 2; bj++) {
                    int[] v = VaultLayoutSC.vaultOf(seed, bi, bj);
                    if (v != null) {
                        best = Math.min(best, Math.hypot(v[0] * (double) grid - x, v[1] * (double) grid - z));
                    }
                }
            }
            nearest = got == null ? best == Double.MAX_VALUE : VaultLayoutSC.hasVault(seed, got[0], got[1])
                    && Math.abs(Math.hypot(got[0] * (double) grid - x, got[1] * (double) grid - z) - best) < 1e-6;
        }
        boolean ok = bad.length() == 0 && nearest && vaults >= blocks - 4;              // the blocks round (0, 0) may lose theirs to the spawn's ring
        check(ok, "vault layout (VaultLayoutSC, ПХ-1): " + vaults + " Vaults in " + blocks + " blocks of " + VaultLayoutSC.BLOCK + " x " + VaultLayoutSC.BLOCK
                + " crossings - at most one a block, never in the spawn's ring, the floor " + VaultLayoutSC.DEPTH + " under the Node's (not below "
                + VaultLayoutSC.FLOOR_MIN + "), inVault / vaultNear its box only" + (bad.length() == 0 ? "" : " - BAD:" + bad.substring(0, Math.min(200, bad.length())))
                + "; nearest the closest within 2 blocks " + nearest);
    }

    // ------------------------------------------------------------------ 14. SpawnRulesSC's new places (package M, §7.12, ПС-41)

    private static void spawnRules() throws Exception {
        String[] places = {"PLATEAU_WEIGHTS", "CANYON_WEIGHTS", "NODE_WEIGHTS", "RIDGES_WEIGHTS", "OXIDE_WEIGHTS", "RIFT_WEIGHTS"};
        Class<?> rules = Class.forName("com.sc.mob.SpawnRulesSC");
        StringBuilder bad = new StringBuilder(), missing = new StringBuilder();
        List<int[]> tables = new ArrayList<int[]>();
        for (String p : places) {
            int[] w = null;
            try {
                Object v = rules.getField(p).get(null);
                w = v instanceof int[] ? (int[]) v : null;
            } catch (NoSuchFieldException e) {
                missing.append(' ').append(p);
            }
            tables.add(w);
            if (w == null) {
                continue;
            }
            int sum = 0;
            for (int x : w) {
                sum += x;
            }
            if (sum <= 0 || w.length != MobEntitiesSC.NAMES.length) {
                bad.append(' ').append(p).append(":sum ").append(sum).append(" length ").append(w.length);
            }
            for (int id : new int[]{MobEntitiesSC.ID_ECLIPSE, MobEntitiesSC.ID_WARLORD, MobEntitiesSC.ID_SENTINEL, MobEntitiesSC.ID_TUNER, MobEntitiesSC.ID_WARDEN}) {
                if (id < w.length && w[id] != 0) {
                    bad.append(' ').append(p).append(':').append(MobEntitiesSC.nameOf(id)).append(' ').append(w[id]);
                }
            }
        }
        // the storm: the swarms x3, the Strider everywhere but the Node (M's stormWeights(int[], int place, boolean storm))
        String storm = "-";
        try {
            Method sw = rules.getMethod("stormWeights", int[].class, int.class, boolean.class);
            int[] canyon = tables.get(1), node = tables.get(2);
            if (canyon != null && node != null) {
                int[] c = (int[]) sw.invoke(null, canyon, 1, Boolean.TRUE), nd = (int[]) sw.invoke(null, node, 2, Boolean.TRUE);
                int[] calm = (int[]) sw.invoke(null, canyon, 1, Boolean.FALSE);
                boolean ok = c[MobEntitiesSC.ID_NANITE] == canyon[MobEntitiesSC.ID_NANITE] * StormRulesSC.SWARM_MUL && c[MobEntitiesSC.ID_STRIDER] > 0
                        && nd[MobEntitiesSC.ID_STRIDER] == 0 && Arrays.equals(calm, canyon) && canyon[MobEntitiesSC.ID_STRIDER] == 0;
                storm = String.valueOf(ok);
                if (!ok) {
                    bad.append(" storm weights");
                }
            }
        } catch (NoSuchMethodException e) {
            missing.append(" stormWeights(int[], int, boolean)");
        }
        // «Затмение» one in the world (ПС-30, ПС-34): M's eclipseChancePct(state, diedAt, now, emission, eliteRoom); the peaceful caps (ПС-41)
        String eclipse = "-", peaceful = "-";
        try {
            Method ec = rules.getMethod("eclipseChancePct", int.class, long.class, long.class, boolean.class, boolean.class);
            long cool = ((Number) rules.getField("ECLIPSE_RESPAWN_TICKS").get(null)).longValue();
            int pct = ((Number) rules.getField("ECLIPSE_PCT").get(null)).intValue();
            boolean ok = ((Integer) ec.invoke(null, RareRegistrySC.ALIVE, 0L, 10000000L, false, true)).intValue() == 0
                    && ((Integer) ec.invoke(null, RareRegistrySC.NONE, 0L, 5L, false, true)).intValue() == pct
                    && ((Integer) ec.invoke(null, RareRegistrySC.DEAD, 100L, 100L + cool - 1, false, true)).intValue() == 0
                    && ((Integer) ec.invoke(null, RareRegistrySC.DEAD, 100L, 100L + cool, false, true)).intValue() == pct
                    && ((Integer) ec.invoke(null, RareRegistrySC.NONE, 0L, 5L, true, true)).intValue() == Math.round(pct * StormRulesSC.EMISSION_ECLIPSE_MUL)
                    && ((Integer) ec.invoke(null, RareRegistrySC.NONE, 0L, 5L, false, false)).intValue() == 0 && pct == 2;
            eclipse = String.valueOf(ok);
            if (!ok) {
                bad.append(" eclipse");
            }
            Method pm = rules.getMethod("peacefulMaySpawn", int.class, int.class, int.class);
            boolean pk = ((Boolean) pm.invoke(null, 3, 0, 1)).booleanValue() && !((Boolean) pm.invoke(null, 4, 0, 1)).booleanValue()
                    && !((Boolean) pm.invoke(null, 0, 12, 1)).booleanValue() && ((Boolean) pm.invoke(null, 0, 9, 3)).booleanValue();
            peaceful = String.valueOf(pk);
            if (!pk) {
                bad.append(" peaceful caps");
            }
        } catch (NoSuchMethodException e) {
            missing.append(" eclipseChancePct / peacefulMaySpawn");
        } catch (NoSuchFieldException e) {
            missing.append(" ECLIPSE_PCT / ECLIPSE_RESPAWN_TICKS");
        }
        check(bad.length() == 0 && missing.length() == 0, "spawn rules (package M, ПС-41): the six places' weights - each sums > 0, the pool's length ("
                + MobEntitiesSC.NAMES.length + "), no Eclipse / Warlord / Sentinel / settler from the director" + (bad.length() == 0 ? "" : " - BAD:" + bad)
                + "; in a storm the swarms x" + StormRulesSC.SWARM_MUL + " and the Strider but at the Nodes " + storm + "; «Затмение» never while ALIVE, back"
                + " after its cooldown, x" + n(StormRulesSC.EMISSION_ECLIPSE_MUL) + " in the Emission " + eclipse + "; the Wafer Crabs 4 in 64 / 12 a world "
                + peaceful + (missing.length() == 0 ? "" : " - MISSING:" + missing));
    }

    // ------------------------------------------------------------------ 15. the pool (§2.2, §7)

    private static void pool() {
        // Ф5 appended its four (22..25, docs/plan-substrate-tech-f5.md §2.2): the pool is 26 now, Ф4's 12..21 unchanged
        boolean names = MobEntitiesSC.NAMES.length == 26 && MobEntitiesSC.EGG_COLORS.length == 26 && "overheated".equals(MobEntitiesSC.nameOf(12))
                && "waferCrab".equals(MobEntitiesSC.nameOf(21)) && "breakdownWarden".equals(MobEntitiesSC.nameOf(22)) && MobEntitiesSC.nameOf(26) == null
                && MobEntitiesSC.ID_OVERHEATED == 12 && MobEntitiesSC.ID_WARLORD == 13
                && MobEntitiesSC.ID_SENTINEL == 14 && MobEntitiesSC.ID_ECLIPSE == 15 && MobEntitiesSC.ID_PHANTOM == 16 && MobEntitiesSC.ID_LEECH == 17
                && MobEntitiesSC.ID_COLOSSUS == 18 && MobEntitiesSC.ID_STRIDER == 19 && MobEntitiesSC.ID_SCRAPPER == 20 && MobEntitiesSC.ID_WAFER_CRAB == 21;
        StringBuilder bad = new StringBuilder();
        double[] hp = {EntityOverheatedSC.HP, EntityWarlordSC.HP, EntitySentinelSC.HP, EntityEclipseSC.HP, EntityPhantomSC.HP, EntityLeechSC.HP, EntityColossusSC.HP,
                EntityStriderSC.HP, EntityScrapperSC.HP, EntityWaferCrabSC.HP};
        boolean hps = Arrays.equals(hp, MOB_HP);
        for (int i = 0; i < MOBS.length; i++) {
            Class<?> c = MOBS[i];
            String n = c.getSimpleName();
            if (!MOB_NAMES[i].equals(MobEntitiesSC.nameOf(12 + i))) {
                bad.append(' ').append(n).append(":name ").append(MobEntitiesSC.nameOf(12 + i));
            }
            if (!ISubstrateNativeSC.class.isAssignableFrom(c) || !IFactionSC.class.isAssignableFrom(c)) {
                bad.append(' ').append(n).append(":markers");
            }
            boolean armoured = i <= 3, noise = i >= 4 && i <= 8;
            if (armoured != EntityArmoredMobSC.class.isAssignableFrom(c) || noise != EntitySubstrateMobSC.class.isAssignableFrom(c)) {
                bad.append(' ').append(n).append(":base class");
            }
            boolean reactive = c == EntityPhantomSC.class || c == EntityLeechSC.class || c == EntityStriderSC.class || c == EntityScrapperSC.class;
            if (reactive != IEmpReactiveSC.class.isAssignableFrom(c) || (c == EntityColossusSC.class) != IEmpUselessSC.class.isAssignableFrom(c)) {
                bad.append(' ').append(n).append(":PE-1 markers");
            }
            if ((c == EntityWaferCrabSC.class) == IMob.class.isAssignableFrom(c)) {
                bad.append(' ').append(n).append(i == 9 ? ":the crab is a monster" : ":not a monster");
            }
        }
        check(names && hps && bad.length() == 0, "pool (MobEntitiesSC, §2.2): 26 names and eggs (Ф5's four after them), 12 overheated .. 21 waferCrab, 22 breakdownWarden " + names + "; HP 30 / 90 /"
                + " 100 / 140 / 20 / 30 / 120 / 60 / 60 / 8 " + hps + "; armoured 12..15, noise 16..20, the peaceful crab; IEmpReactiveSC on the Phantom, Leech,"
                + " Strider, Scrapper, IEmpUselessSC on the Colossus" + (bad.length() == 0 ? " ok" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ 16. the loot kinds (12) and the trophies shown (24) (§0, §9.1, ПТ-16, ПТ-18)

    private static void lootAndTrophies() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        ItemCombatLootSC.LootKind[] all = ItemCombatLootSC.LootKind.values();
        String[] keys = {"boiledLoop", "inductionThread", "plasmaCondensate", "singularScrap", "naturalWafer"};
        int[] eggs = {MobEntitiesSC.ID_OVERHEATED, MobEntitiesSC.ID_PHANTOM, MobEntitiesSC.ID_STRIDER, MobEntitiesSC.ID_ECLIPSE, MobEntitiesSC.ID_WAFER_CRAB};
        boolean order = all.length == 12 && "naturalWafer".equals(all[all.length - 1].key) && Arrays.equals(BookWorldSC.LOOT_NAMES, kindKeys(all));
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            int m = BookWorldSC.LOOT_BOILED_LOOP + i;
            if (m >= all.length || !keys[i].equals(all[m].key)) {
                bad.append(' ').append(keys[i]).append(":order");
                continue;
            }
            if (SelfTestHazardSC.class.getResource("/assets/siliconage/textures/items/loot_" + keys[i] + ".png") == null) {
                bad.append(' ').append(keys[i]).append(":no icon");
            }
            for (String k : new String[]{"item.siliconage.combatLoot." + keys[i] + ".name", "sc.tooltip.loot." + keys[i], "sc.tooltip.loot." + keys[i] + ".details",
                    "sc.tooltip.loot." + keys[i] + ".howto"}) {
                if (!en.containsKey(k) || !ru.containsKey(k)) {
                    bad.append(' ').append(k);
                }
            }
            String line = BookWorldSC.lootLine(m);
            if (line == null || line.contains("%s") || line.contains("%d") || line.startsWith("sc.") || line.startsWith("Format error")) {
                bad.append(' ').append(keys[i]).append(":handbook line");
            }
            ItemStack egg = BookWorldSC.lootEgg(m);
            if (MobItemsSC.spawnEgg != null && (egg == null || egg.getItemDamage() != eggs[i])) {
                bad.append(' ').append(keys[i]).append(":egg ").append(egg == null ? "none" : String.valueOf(egg.getItemDamage()));
            }
        }
        Set<Integer> shown = new TreeSet<Integer>();
        for (int m : TrophyTableSC.SHOWN) {
            shown.add(m);
        }
        StringBuilder singular = new StringBuilder();
        for (int m : new int[]{12, 13, 14, 15, 19}) {
            if (!shown.contains(m) || BookWorldSC.trophyDroppers(m) == null || BookWorldSC.trophyChancePct(m) == null || BookWorldSC.restoreLine(m) == null) {
                singular.append(' ').append(m);
            }
        }
        // Ф5 appended the Breakdown Warden's ПЭ-1М (ПТ-26, docs/plan-substrate-tech-f5.md §5.7): SHOWN is 25 now
        boolean trophies = TrophyTableSC.SHOWN.length == 25 && shown.contains(TrophyTableSC.META_PE1M) && singular.length() == 0 && TrophyTableSC.STORM_MUL == 1.5F;
        check(order && bad.length() == 0 && trophies, "loot and trophies (ПТ-16, ПТ-18): LootKind 12 in their order, the last naturalWafer, BookWorldSC.LOOT_NAMES"
                + " the same " + order + "; Ф4's five with icon, name, tooltip, handbook line and the egg of who drops it" + (bad.length() == 0 ? " ok" : " - BAD:" + bad)
                + "; SHOWN " + TrophyTableSC.SHOWN.length + " (25 with Ф5's ПЭ-1М) with the Singular pieces and blade, droppers / chance / restoring named"
                + (singular.length() == 0 ? "" : " - NOT:" + singular) + ", the storm x1.5 " + trophies);
    }

    private static String[] kindKeys(ItemCombatLootSC.LootKind[] all) {
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].key;
        }
        return out;
    }

    // ------------------------------------------------------------------ 17. the relics and the Pelengator (§8.7, ПХ-6)

    private static void relics() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        StringBuilder bad = new StringBuilder();
        // Ф5 appended the Breakdown Key and the Breakdown Matrix (metas 3, 4; docs/plan-substrate-tech-f5.md §2.2): 5 names now
        boolean metas = ItemRelicSC.CAMP_KEY == 0 && ItemRelicSC.KEY_FRAGMENT == 1 && ItemRelicSC.SEALED_CRUMB == 2 && ItemRelicSC.NAMES.length == 5
                && "scCamp".equals(ItemRelicSC.KEY_NBT) && ItemRuinFinderSC.USES == 8 && ItemRuinFinderSC.SEARCH_BLOCKS == 2;
        for (String n : ItemRelicSC.NAMES) {
            for (String k : new String[]{"item.siliconage.relic." + n + ".name", "sc.tooltip.relic." + n, "sc.tooltip.relic." + n + ".details",
                    "sc.tooltip.relic." + n + ".howto", "sc.manual.world.relic." + n}) {
                if (!en.containsKey(k) || !ru.containsKey(k)) {
                    bad.append(' ').append(k);
                }
            }
        }
        for (String k : new String[]{"item.siliconage.ruinFinder.name", "sc.tooltip.ruinFinder", "sc.tooltip.ruinFinder.details", "sc.tooltip.ruinFinder.howto"}) {
            if (!en.containsKey(k) || !ru.containsKey(k)) {
                bad.append(' ').append(k);
            }
        }
        Item relic = RelicItemsSC.relic, finder = RelicItemsSC.ruinFinder;
        boolean items = relic != null && finder != null;
        if (items) {
            items = relic.getItemStackLimit(new ItemStack(relic, 1, ItemRelicSC.CAMP_KEY)) == 1 && relic.getItemStackLimit(new ItemStack(relic, 1, ItemRelicSC.KEY_FRAGMENT)) == 4
                    && relic.getItemStackLimit(new ItemStack(relic, 1, ItemRelicSC.SEALED_CRUMB)) == 64 && finder.getMaxDamage() == ItemRuinFinderSC.USES
                    && finder.getItemStackLimit(new ItemStack(finder)) == 1;
            for (int m = 0; m < ItemRelicSC.NAMES.length; m++) {
                String key = relic.getUnlocalizedName(new ItemStack(relic, 1, m)) + ".name";
                if (!key.equals("item.siliconage.relic." + ItemRelicSC.NAMES[m] + ".name")) {
                    bad.append(" unlocalized ").append(key);
                }
            }
            String fk = finder.getUnlocalizedName(new ItemStack(finder)) + ".name";
            if (!"item.siliconage.ruinFinder.name".equals(fk)) {
                bad.append(" unlocalized ").append(fk);
            }
        }
        String line = BookWorldSC.relicLine(ItemRelicSC.SEALED_CRUMB) + BookWorldSC.relicLine(ItemRelicSC.CAMP_KEY) + BookWorldSC.finderLine();
        boolean lines = !line.contains("%s") && !line.contains("%d") && !line.contains("sc.") && !line.contains("Format error");
        check(metas && items && bad.length() == 0 && lines, "relics (ItemRelicSC, ItemRuinFinderSC, ПЗ-7, ПХ-6): the metas campKey / keyFragment / sealedCrumb,"
                + " scCamp, 8 uses within 2 blocks " + metas + "; registered, stacks 1 / 4 / 64, the finder wears out " + items + "; names and tooltips in both"
                + " languages" + (bad.length() == 0 ? "" : " - NOT:" + bad) + "; the handbook / NEI lines read clean " + lines
                + " (a Sealed Crumb opening into a Crumb: WorldTestHazardSC)");
    }

    // ------------------------------------------------------------------ 18. texts (§10.1)

    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>();
        for (int k = BiomeSubstrateSC.RIDGES; k < BiomeSubstrateSC.KINDS; k++) {
            keys.add("sc.dim.biome." + BiomeSubstrateSC.KEYS[k]);
        }
        for (int id = MobEntitiesSC.ID_OVERHEATED; id <= MobEntitiesSC.ID_WAFER_CRAB; id++) {
            keys.add("entity.SiliconAge." + MobEntitiesSC.nameOf(id) + ".name");
        }
        for (String[] names : new String[][]{SubstrateWildBlocksSC.GLASS_NAMES, SubstrateWildBlocksSC.FLORA_NAMES, SubstrateWildBlocksSC.VENT_NAMES}) {
            for (String n : names) {
                keys.add("tile.siliconage.wild." + n + ".name");
                keys.add("sc.tooltip.wild." + n);
                keys.add("sc.tooltip.wild." + n + ".details");
                keys.add("sc.tooltip.wild." + n + ".howto");
                keys.add("sc.manual.world.wild." + n);
            }
        }
        String mono = SubstrateBlocksSC.ROCK_NAMES[SubstrateBlocksSC.MONOCRYSTAL];
        keys.addAll(Arrays.asList("tile.siliconage.substrateRock." + mono + ".name", "sc.tooltip.substrateRock." + mono, "sc.tooltip.substrateRock." + mono
                + ".details", "sc.tooltip.substrateRock." + mono + ".howto", "sc.manual.world.block." + mono));
        for (String[] names : new String[][]{CampBlocksSC.FIELD_NAMES, CampBlocksSC.CAMP_NAMES}) {
            for (String n : names) {
                keys.add("tile.siliconage.camp." + n + ".name");
                keys.add("sc.tooltip.camp." + n);
                keys.add("sc.tooltip.camp." + n + ".details");
                keys.add("sc.tooltip.camp." + n + ".howto");
            }
        }
        for (String n : new String[]{"vaultPlating", "vaultLamp", "campTarp"}) {
            keys.add("tile.siliconage.ruin." + n + ".name");
            keys.add("sc.tooltip.ruin." + n);
            keys.add("sc.tooltip.ruin." + n + ".details");
            keys.add("sc.tooltip.ruin." + n + ".howto");
            keys.add("sc.manual.world.ruin." + n);
        }
        for (String d : new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}) {
            keys.add("sc.finder.dir." + d);
        }
        StringBuilder missing = new StringBuilder();
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        // the messages: present, with the arguments their packages pass
        StringBuilder args = new StringBuilder();
        for (String[] m : MESSAGE_ARGS) {
            int want = Integer.parseInt(m[1]);
            for (Map<String, String> lang : new Map[]{en, ru}) {
                String v = lang.get(m[0]);
                if (v == null) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(m[0]);
                } else if (Math.max(0, positional(v)) != want) {
                    args.append(' ').append(m[0]).append('(').append(positional(v)).append(" not ").append(want).append(')');
                }
            }
        }
        // every Ф4 key of one file in the other, with as many arguments; English is English
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
                }
            }
        }
        check(missing.length() == 0 && args.length() == 0 && diff.length() == 0, "texts (§10.1): " + keys.size() + " keys - the biomes, the creatures, the wild"
                + " and camp blocks, the monocrystal, the ruin metas, the finder's points - and " + MESSAGE_ARGS.length + " messages with the packages' arguments"
                + " in en_US and ru_RU" + missing + args + "; " + all + " Ф4 keys with the same arguments in both, no Cyrillic in English" + diff);
    }

    private static boolean ours(String k) {
        for (String p : PREFIXES) {
            if (k.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /** %s / %d and %1$s... arguments of a lang value (%% isn't one); -1 for none. */
    private static int positional(String v) {
        if (v == null) {
            return -1;
        }
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

    // ------------------------------------------------------------------ 19. the handbook (§10.2)

    private static void handbook() {
        BookContent.invalidate();
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
            raw(bad, id, e.searchText());
        }
        // the extended Ф1..Ф3 articles read clean too
        for (String id : new String[]{"world.substrate", "world.terrain", "world.armoured", "world.trophies", "world.trade", "world.ruins"}) {
            BookEntry e = BookContent.byId(id);
            raw(bad, id, e == null ? "" : e.searchText());
        }
        int radiation = rows("world.radiation", 0), waves = rows("world.raids", 0), rewards = rows("world.raids", 1);
        boolean tables = radiation == BiomeSubstrateSC.KINDS && waves == RaidRulesSC.WAVES + 1 && rewards == SettlementSizeSC.SUBSTRATE_SIZES;
        // G: the new blocks, items, eggs and loot open their pages
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        List<String> ids = new ArrayList<String>();
        g(stacks, ids, wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.GLASS), "world.biomes4");
        g(stacks, ids, wild(SubstrateWildBlocksSC.glass, SubstrateWildBlocksSC.PILLAR_CORE), "world.biomes4");
        for (int m = 0; m < SubstrateWildBlocksSC.FLORA_NAMES.length; m++) {
            g(stacks, ids, wild(SubstrateWildBlocksSC.flora, m), "world.resources4");
        }
        for (int m = 0; m < SubstrateWildBlocksSC.VENT_NAMES.length; m++) {
            g(stacks, ids, wild(SubstrateWildBlocksSC.vent, m), "world.resources4");
        }
        g(stacks, ids, wild(SubstrateBlocksSC.rock, SubstrateBlocksSC.MONOCRYSTAL), "world.resources4");
        g(stacks, ids, wild(CampBlocksSC.stolenField, 0), "world.camps");
        for (int m = 0; m < CampBlocksSC.CAMP_NAMES.length; m++) {
            g(stacks, ids, wild(CampBlocksSC.camp, m), "world.camps");
        }
        g(stacks, ids, wild(RuinBlocksSC.ruin, BookWorldSC.RUIN_VAULT_PLATING), "world.vaults");
        g(stacks, ids, wild(RuinBlocksSC.ruin, BookWorldSC.RUIN_VAULT_LAMP), "world.vaults");
        g(stacks, ids, wild(RuinBlocksSC.ruin, BookWorldSC.RUIN_CAMP_TARP), "world.camps");
        g(stacks, ids, RelicItemsSC.relic(ItemRelicSC.CAMP_KEY, 1), "world.camps");
        g(stacks, ids, RelicItemsSC.relic(ItemRelicSC.KEY_FRAGMENT, 1), "world.vaults");
        g(stacks, ids, RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1), "world.crumbs");
        g(stacks, ids, RelicItemsSC.ruinFinder == null ? null : new ItemStack(RelicItemsSC.ruinFinder), "world.vaults");
        String[] eggTo = {"world.overheated", "world.camps", "world.vaults", "world.eclipse", "world.noise4", "world.noise4", "world.noise4", "world.noise4",
                "world.scrapper", "world.fauna"};
        for (int i = 0; i < eggTo.length; i++) {
            g(stacks, ids, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_OVERHEATED + i), eggTo[i]);
        }
        String[] lootTo = {"world.overheated", "world.noise4", "world.noise4", "world.eclipse", "world.fauna"};
        for (int i = 0; i < lootTo.length; i++) {
            g(stacks, ids, MobItemsSC.combatLoot == null ? null : new ItemStack(MobItemsSC.combatLoot, 1, BookWorldSC.LOOT_BOILED_LOOP + i), lootTo[i]);
        }
        g(stacks, ids, MobItemsSC.trophy == null ? null : new ItemStack(MobItemsSC.trophy, 1, TrophyTableSC.bladeMeta(BladeType.SINGULAR)), "world.trophies");
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
        int chapter = BookContent.chapter(BookChapter.WORLD).size(), want = 6 + 6 + 8 + ARTICLES.length + BookWorldSC.F5_ARTICLES.length   // + Ф5's (SelfTestFinaleSC)
                + BookWorldSC.F6_ARTICLES.length;                                                                                       // + Ф6's (SelfTestEmbassySC)
        List<String> unresolved = BookWorldSC.unresolved();
        BookContent.invalidate();
        if (!unresolved.isEmpty()) {
            info("the handbook still prints the plan's numbers for (public static constants of these names in packages W / S / M / N / C, or direct"
                    + " references in BookWorldSC): " + unresolved);
        }
        check(bad.length() == 0 && tables && noG.length() == 0 && chapter == want, "handbook (§10.2): the " + ARTICLES.length + " new WORLD articles (the chapter "
                + chapter + " of " + want + ") build with no raw keys, the extended ones too" + (bad.length() == 0 ? "" : " -" + bad) + "; tables: the background by"
                + " biome " + radiation + " (5), the waves " + waves + " (4), the rewards " + rewards + " (3) " + tables + "; G on " + registered + " new blocks, items,"
                + " eggs and loot" + (noG.length() == 0 ? "" : " - NOT:" + noG));
    }

    private static void raw(StringBuilder bad, String id, String t) {
        for (String raw : new String[]{"sc.manual.", "sc.book.", "sc.dim.", "sc.suit.", "sc.settle.", "sc.trade.", "sc.emp.", "tile.siliconage.", "item.siliconage.",
                "entity.siliconage.", "%s", "%d", "format error"}) {
            if (t.contains(raw)) {
                bad.append(' ').append(id).append(" raw '").append(raw).append('\'');
            }
        }
    }

    private static void g(List<ItemStack> stacks, List<String> ids, ItemStack s, String id) {
        stacks.add(s);
        ids.add(id);
    }

    private static ItemStack wild(net.minecraft.block.Block b, int meta) {
        return b == null ? null : new ItemStack(b, 1, meta);
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

    // ------------------------------------------------------------------

    /** A creature made without a world (null if its constructor needs one - listed). */
    static Entity make(Class<?> cls, StringBuilder noWorld) {
        try {
            Constructor<?> c = cls.getConstructor(World.class);
            return (Entity) c.newInstance((World) null);
        } catch (Throwable t) {
            Throwable why = t.getCause() != null ? t.getCause() : t;
            noWorld.append(' ').append(cls.getSimpleName()).append(" (").append(why).append(')');
            return null;
        }
    }

    private static String n(float v) {
        return String.valueOf(Math.round(v * 100F) / 100F);
    }
}
