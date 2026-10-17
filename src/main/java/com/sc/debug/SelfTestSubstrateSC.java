package com.sc.debug;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.EntryRulesSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateEntrySC;
import com.sc.dim.SubstrateNetSC;
import com.sc.dim.SubstrateOreTableSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.manual.BookChapter;
import com.sc.manual.BookContent;
import com.sc.manual.BookEl;
import com.sc.manual.BookEntry;
import com.sc.manual.BookWorldSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.mob.TrophyTableSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;
import com.sc.util.WeaponType;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.DimensionManager;

/**
 * Self-checks of the Substrate (Si-14), docs/plan-substrate-tech.md §8.1, run from SelfTestSC with -Dsc.selftest=true.
 * That is preInit: the world itself is registered only in init, so its registration is the world test's
 * (WorldTestSubstrateSC); here - the pure parts: the config holder (defaults, ids never public, the 7 rules
 * SubstrateNetSC sends, nothing of it in ConfigSC), the biome id search and the dimension id refusal, the terrain
 * maths (package W), the ore table, the mob armour maths (A), the trophy table and the spawn limits (M), the entry
 * rules (E), the trophy item's metas and names, the texts in both languages and the handbook's WORLD chapter (D).
 */
final class SelfTestSubstrateSC {

    /**
     * The rules of Ф1..Ф3, the public fields SubstrateNetSC sends a joining client (docs/plan-substrate.md §8,
     * docs/plan-substrate-tech-f2.md §2.1, docs/plan-substrate-tech-f3.md §2.1).
     */
    private static final String[] RULES = {"strictEntry", "respawnDimension", "bridgeCostMultiplier", "nativesCrossBridge", "empMobTargets",
            "siphonMobs", "gearDropMultiplier", "villages", "guardsAggroOnViolations", "mobsUseEmp"};
    /** The ids: private behind getters, never synced (ПК-4). */
    private static final String[] IDS = {"enabled", "dimensionId", "providerId", "biomeIdPlateaus", "biomeIdCanyons", "keepLoaded"};

    /** docs/plan-substrate-tech.md §7.1: the fixed keys every package uses. */
    private static final String[] KEYS = {"sc.dim.substrate.name", "sc.dim.ids.mismatch", "sc.dim.biome.plateaus", "sc.dim.biome.canyons",
            "sc.dim.ore.name", "tile.siliconage.crystalDruse.name", "sc.tooltip.substrateOre", "sc.tooltip.substrateOre.details", "sc.tooltip.crystalDruse",
            "sc.tooltip.crystalDruse.details", "sc.tooltip.crystalDruse.howto", "entity.SiliconAge.lineman.name", "entity.SiliconAge.marauder.name",
            "item.siliconage.spawnEgg.name", "sc.tooltip.trophy", "sc.tooltip.trophy.details", "sc.tooltip.trophy.howto", "sc.tooltip.spawnEgg",
            "sc.dim.entry.welcome", "sc.dim.entry.book", "sc.dim.entry.refused", "sc.dim.portal.dead", "sc.dim.bed", "sc.book.ch.world",
            "sc.book.ch.world.desc", "sc.nei.obtain.substrate", "sc.nei.obtain.subore", "sc.nei.obtain.subore.drop", "sc.nei.obtain.trophy",
            "sc.nei.obtain.trophy.later", "sc.nei.obtain.mobrules", "sc.nei.obtain.egg"};
    /** Key prefixes of the Substrate's texts: each in both languages with as many arguments. */
    private static final String[] PREFIXES = {"sc.dim.", "sc.mob.", "sc.manual.world.", "sc.book.ch.world", "sc.nei.obtain.sub", "sc.nei.obtain.trophy",
            "sc.nei.obtain.mobrules", "sc.nei.obtain.egg", "item.siliconage.trophy.", "item.siliconage.combatLoot.", "item.siliconage.spawnEgg",
            "tile.siliconage.substrate", "tile.siliconage.crystalDruse", "sc.tooltip.substrate", "sc.tooltip.crystalDruse", "sc.tooltip.trophy",
            "sc.tooltip.loot.", "sc.tooltip.spawnEgg", "entity.SiliconAge."};
    private static final String[] ARTICLES = {"world.substrate", "world.terrain", "world.armoured", "world.lineman", "world.marauder", "world.trophies"};

    private SelfTestSubstrateSC() {
    }

    static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "substrate: " + what);
    }

    static void run() {
        config();
        biomeIds();
        dimensionIds();
        terrainGrid();
        terrainShape();
        ores();
        armourMath();
        trophyTable();
        spawnRules();
        entryRules();
        items();
        texts();
        handbook();
    }

    // ------------------------------------------------------------------ the config holder (ПК-4, §2.1)

    private static void config() {
        boolean ids = SubstrateConfigSC.enabled() && SubstrateConfigSC.dimensionId() == -1414 && SubstrateConfigSC.providerId() == -1414
                && SubstrateConfigSC.biomeIdPlateaus() == 240 && SubstrateConfigSC.biomeIdCanyons() == 241 && !SubstrateConfigSC.keepLoaded();
        boolean rules = SubstrateConfigSC.strictEntry && SubstrateConfigSC.respawnDimension == 0 && SubstrateConfigSC.bridgeCostMultiplier == 0.5F
                && !SubstrateConfigSC.nativesCrossBridge && SubstrateConfigSC.empMobTargets && SubstrateConfigSC.siphonMobs
                && SubstrateConfigSC.gearDropMultiplier == 1F;
        check(ids && rules, "config defaults: dimension " + SubstrateConfigSC.dimensionId() + " / provider " + SubstrateConfigSC.providerId()
                + " (-1414), biomes " + SubstrateConfigSC.biomeIdPlateaus() + ", " + SubstrateConfigSC.biomeIdCanyons() + " (240, 241), enabled "
                + SubstrateConfigSC.enabled() + ", keepLoaded " + SubstrateConfigSC.keepLoaded() + "; strictEntry " + SubstrateConfigSC.strictEntry
                + ", respawn " + SubstrateConfigSC.respawnDimension + ", bridge x" + SubstrateConfigSC.bridgeCostMultiplier + ", natives "
                + SubstrateConfigSC.nativesCrossBridge + ", PE-1 " + SubstrateConfigSC.empMobTargets + " / siphon " + SubstrateConfigSC.siphonMobs
                + ", gear x" + SubstrateConfigSC.gearDropMultiplier + (ids && rules ? "" : " (the dimension section of the test config edited?)"));

        StringBuilder pub = new StringBuilder();
        for (String n : IDS) {
            try {
                Field f = SubstrateConfigSC.class.getDeclaredField(n);
                if (Modifier.isPublic(f.getModifiers()) || !Modifier.isStatic(f.getModifiers())) {
                    pub.append(' ').append(n);
                }
            } catch (NoSuchFieldException e) {
                pub.append(' ').append(n).append("(missing)");
            }
        }
        Set<String> sent = new TreeSet<String>();
        for (Field f : SubstrateNetSC.ruleFields()) {
            sent.add(f.getName());
        }
        Set<String> want = new TreeSet<String>(Arrays.asList(RULES));
        StringBuilder inConfig = new StringBuilder();
        for (Field f : ConfigSC.class.getDeclaredFields()) {
            String n = f.getName();
            if (n.equals("dimensionId") || n.equals("providerId") || n.startsWith("biomeId")) {
                inConfig.append(' ').append(n);
            }
        }
        check(pub.length() == 0 && sent.equals(want) && inConfig.length() == 0, "the ids are private statics of SubstrateConfigSC"
                + (pub.length() == 0 ? "" : " - NOT:" + pub) + "; SubstrateNetSC sends exactly the " + RULES.length + " rules " + sent
                + (sent.equals(want) ? "" : " (want " + want + ")") + "; no id field in ConfigSC (ConfigSyncSC would overwrite it)"
                + (inConfig.length() == 0 ? "" : " - found:" + inConfig));
    }

    // ------------------------------------------------------------------ registration's pure parts (§1)

    /** A taken slot is marked with a vanilla biome: no BiomeGenBase is made here (its constructor writes the global array). */
    private static void biomeIds() {
        BiomeGenBase taken = BiomeGenBase.plains;
        BiomeGenBase[] slots = new BiomeGenBase[256];
        boolean free = SubstrateSC.pickBiomeId(slots, 240, -1) == 240 && SubstrateSC.pickBiomeId(slots, 100, -1) == 100;
        boolean notThis = SubstrateSC.pickBiomeId(slots, 240, 240) == 241;
        boolean outside = SubstrateSC.pickBiomeId(slots, 300, -1) == 240 && SubstrateSC.pickBiomeId(slots, -5, -1) == 240;
        boolean not255 = SubstrateSC.BIOME_SEARCH_TO == 254 && SubstrateSC.pickBiomeId(slots, 255, -1) == 240;  // the chunk's «not worked out» mark
        slots[240] = taken;
        boolean next = SubstrateSC.pickBiomeId(slots, 240, -1) == 241 && SubstrateSC.pickBiomeId(slots, 240, 241) == 242;
        for (int i = SubstrateSC.BIOME_SEARCH_FROM; i <= SubstrateSC.BIOME_SEARCH_TO; i++) {
            slots[i] = taken;
        }
        int down = SubstrateSC.pickBiomeId(slots, 241, -1);
        for (int i = SubstrateSC.BIOME_SEARCH_DOWN_TO; i < SubstrateSC.BIOME_SEARCH_FROM; i++) {
            slots[i] = taken;
        }
        int none = SubstrateSC.pickBiomeId(slots, 241, -1);
        check(free && notThis && outside && not255 && next && down == 239 && none == -1, "pickBiomeId: a free wanted slot is itself " + free
                + ", notThis skipped " + notThis + ", an id off the array -> 240 " + outside + ", 255 never (empty slot 255 -> 240) " + not255
                + ", taken -> the next free " + next + ", 240..254 all taken -> " + down + " (239), 40..254 all taken (255 free) -> " + none + " (-1)");
    }

    private static void dimensionIds() {
        int free = -987654;
        while (DimensionManager.isDimensionRegistered(free)) {
            free--;
        }
        boolean vanilla = SubstrateSC.dimensionRefusal(0) != null && SubstrateSC.dimensionRefusal(1) != null && SubstrateSC.dimensionRefusal(-1) != null;
        boolean positive = SubstrateSC.dimensionRefusal(7) != null && SubstrateSC.dimensionRefusal(1414) != null;
        String freeWhy = SubstrateSC.dimensionRefusal(free);
        check(vanilla && positive && freeWhy == null, "dimensionRefusal: 0 / 1 / -1 refused " + vanilla + ", positive refused " + positive
                + ", a free negative (" + free + ") accepted " + (freeWhy == null ? "true" : "false: " + freeWhy));
    }

    // ------------------------------------------------------------------ the terrain (package W, §3.3)

    private static void terrainGrid() {
        int g = SubstrateTerrainSC.GRID, belt = SubstrateTerrainSC.CANYON_BELT;
        boolean lines = SubstrateTerrainSC.distanceToLine(0) == 0 && SubstrateTerrainSC.distanceToLine(128) == 128
                && SubstrateTerrainSC.distanceToLine(255) == 1 && SubstrateTerrainSC.distanceToLine(-1) == 1 && SubstrateTerrainSC.distanceToLine(g) == 0
                && SubstrateTerrainSC.distanceToLine(-g) == 0 && SubstrateTerrainSC.distanceToLine(-128) == 128 && SubstrateTerrainSC.distanceToLine(300) == 44;
        boolean inBelt = SubstrateTerrainSC.inCanyonBelt(0, 128) && SubstrateTerrainSC.inCanyonBelt(128, 0) && SubstrateTerrainSC.inCanyonBelt(belt, 128)
                && SubstrateTerrainSC.inCanyonBelt(-belt, 128) && !SubstrateTerrainSC.inCanyonBelt(belt + 1, 128) && !SubstrateTerrainSC.inCanyonBelt(128, 128)
                && SubstrateTerrainSC.inCanyonBelt(g + 3, 700);
        boolean kinds = SubstrateTerrainSC.biomeKind(0, 5 * 16 + 8) == BiomeSubstrateSC.CANYONS && SubstrateTerrainSC.biomeKind(128, 128) == BiomeSubstrateSC.PLATEAUS
                && SubstrateTerrainSC.biomeKind(-128, -128) == BiomeSubstrateSC.PLATEAUS;
        check(lines && inBelt && kinds, "terrain grid: Scribe lines every " + g + " (distanceToLine 0, 128, 255, -1 ...) " + lines + ", the canyon belt +-"
                + belt + " " + inBelt + ", biomeKind on a line = canyons, at (128, 128) = plateaus " + kinds);
    }

    /** By W's bodies: plateau terraces, canyon widths and depths over a grid of points, three seeds. */
    private static void terrainShape() {
        long[] seeds = {0L, 1234567L, -98765L};
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, offStep = 0, points = 0;
        int wLo = Integer.MAX_VALUE, wHi = Integer.MIN_VALUE, dLo = Integer.MAX_VALUE, dHi = Integer.MIN_VALUE, floorLo = Integer.MAX_VALUE;
        for (long seed : seeds) {
            for (int x = 40; x <= 216; x += 8) {
                for (int z = 40; z <= 216; z += 8) {
                    int y = SubstrateTerrainSC.surfaceY(seed, x, z);
                    lo = Math.min(lo, y);
                    hi = Math.max(hi, y);
                    offStep += (y - SubstrateTerrainSC.PLATEAU_MIN) % SubstrateTerrainSC.TERRACE_STEP != 0 ? 1 : 0;
                    points++;
                }
            }
            for (int line = -4; line <= 4; line++) {
                int w = 2 * SubstrateTerrainSC.canyonHalfWidth(seed, line) + 1;
                wLo = Math.min(wLo, w);
                wHi = Math.max(wHi, w);
            }
            for (int line = -2; line <= 2; line++) {
                int x0 = line * SubstrateTerrainSC.GRID, h = SubstrateTerrainSC.canyonHalfWidth(seed, line);
                for (int z : new int[]{100, 128, 160}) {
                    int floor = SubstrateTerrainSC.surfaceY(seed, x0, z);
                    int rimL = SubstrateTerrainSC.surfaceY(seed, x0 - h - 3, z), rimR = SubstrateTerrainSC.surfaceY(seed, x0 + h + 3, z);
                    dLo = Math.min(dLo, Math.min(rimL, rimR) - floor);
                    dHi = Math.max(dHi, Math.max(rimL, rimR) - floor);
                    floorLo = Math.min(floorLo, floor);
                }
            }
        }
        boolean plateau = lo >= SubstrateTerrainSC.PLATEAU_MIN && hi <= SubstrateTerrainSC.PLATEAU_MAX && hi > lo && offStep * 3 < points;
        boolean width = wLo >= SubstrateTerrainSC.CANYON_WIDTH_MIN && wHi <= SubstrateTerrainSC.CANYON_WIDTH_MAX;
        boolean depth = dLo >= SubstrateTerrainSC.CANYON_DEPTH_MIN - SubstrateTerrainSC.TERRACE_STEP
                && dHi <= SubstrateTerrainSC.CANYON_DEPTH_MAX + SubstrateTerrainSC.TERRACE_STEP && floorLo >= 1;
        check(plateau, "terrain (package W): plateau surface over " + points + " points of 3 seeds in Y " + lo + ".." + hi + " (want terraces within "
                + SubstrateTerrainSC.PLATEAU_MIN + ".." + SubstrateTerrainSC.PLATEAU_MAX + ", not all flat), " + offStep + " off the "
                + SubstrateTerrainSC.TERRACE_STEP + "-block step (the 1-block lips under the terraces' edges, about a fifth; under a third)");
        check(width && depth, "terrain (package W): canyon width " + wLo + ".." + wHi + " (" + SubstrateTerrainSC.CANYON_WIDTH_MIN + ".."
                + SubstrateTerrainSC.CANYON_WIDTH_MAX + "), floor " + dLo + ".." + dHi + " below the rims (" + SubstrateTerrainSC.CANYON_DEPTH_MIN + ".."
                + SubstrateTerrainSC.CANYON_DEPTH_MAX + ", a terrace either way), the lowest floor at Y " + floorLo);
    }

    // ------------------------------------------------------------------ the ores (package W, §3.5)

    private static void ores() {
        StringBuilder bad = new StringBuilder(), table = new StringBuilder();
        for (OreEntry ore : OreEntry.values()) {
            float want;
            switch (ore) {
                case TANTALITE:
                    want = 2F;
                    break;
                case SPERRYLITE:
                case BADDELEYITE:
                case ARGYRODITE:
                case WOLFRAMITE:
                case MONAZITE:
                    want = 1.5F;
                    break;
                case CHALCOPYRITE:
                case CASSITERITE:
                    want = 0.5F;
                    break;
                default:
                    want = 1F;
            }
            float mul = SubstrateOreTableSC.veinMultiplier(ore);
            int min = SubstrateOreTableSC.minY(ore), max = SubstrateOreTableSC.maxY(ore);
            boolean range = min >= 1 && min < max && max <= SubstrateTerrainSC.OXIDE_FROM;
            boolean layer = true;
            if (ore == OreEntry.QUARTZITE || ore == OreEntry.SPODUMENE || ore == OreEntry.ILMENITE) {
                layer = max <= SubstrateTerrainSC.BASE_TOP;                         // the silicon base
            } else if (want > 1F) {
                layer = min <= SubstrateTerrainSC.BASE_TOP && max >= SubstrateTerrainSC.METAL_TOP;   // the metallisation layer
            }
            if (mul != want || !range || !layer) {
                bad.append(' ').append(ore.name()).append(" x").append(mul).append(" Y").append(min).append('-').append(max);
            }
            table.append(' ').append(ore.oreName).append('=').append(mul).append('/').append(min).append('-').append(max);
        }
        check(bad.length() == 0, "ore table: tantalite x2, sperrylite / baddeleyite / argyrodite / wolframite / monazite x1.5, chalcopyrite / cassiterite"
                + " x0.5, the rest x1; heights within 1.." + SubstrateTerrainSC.OXIDE_FROM + ", quartzite / spodumene / ilmenite in the base, the rare ones"
                + " across the metallisation layer" + (bad.length() == 0 ? "" : " - WRONG:" + bad) + " |" + table);
    }

    // ------------------------------------------------------------------ the mob armour (package A, §4.1)

    private static void armourMath() {
        boolean shares = near(MobArmorMathSC.absorbShare(20), 0.8F) && near(MobArmorMathSC.absorbShare(22), 0.88F)
                && near(MobArmorMathSC.absorbShare(24), 0.96F) && near(MobArmorMathSC.absorbShare(28), 1F) && MobArmorMathSC.absorbShare(0) == 0F
                && MobArmorMathSC.absorbShare(40) == 1F;
        int[] suitPoints = new int[ArmorSuit.values().length];
        for (ArmorSuit s : ArmorSuit.values()) {
            for (int t = 0; t < 4; t++) {
                suitPoints[s.ordinal()] += s.material.getDamageReductionAmount(t);
            }
        }
        boolean points = Arrays.equals(suitPoints, new int[]{20, 22, 24, 28});
        boolean absorb = pair(MobArmorMathSC.absorb(10F, 20, 100F), 2F, 8F) && pair(MobArmorMathSC.absorb(10F, 20, 3F), 7F, 3F)
                && pair(MobArmorMathSC.absorb(10F, 20, 0F), 10F, 0F) && pair(MobArmorMathSC.absorb(10F, 28, 100F), 0F, 10F)
                && pair(MobArmorMathSC.absorb(0F, 20, 100F), 0F, 0F);
        boolean shield = pair(MobArmorMathSC.shield(10F, 4F), 6F, 4F) && pair(MobArmorMathSC.shield(10F, 50F), 0F, 10F)
                && pair(MobArmorMathSC.shield(10F, 0F), 10F, 0F);
        boolean iron = MobArmorMathSC.ironPoints(0) == 2 && MobArmorMathSC.ironPoints(1) == 6 && MobArmorMathSC.ironPoints(2) == 5
                && MobArmorMathSC.ironPoints(3) == 2;
        boolean emergency = !MobArmorMathSC.hasEmergencyMode(ArmorSuit.NANO) && MobArmorMathSC.hasEmergencyMode(ArmorSuit.QUANTUM)
                && MobArmorMathSC.hasEmergencyMode(ArmorSuit.EXO) && MobArmorMathSC.hasEmergencyMode(ArmorSuit.SINGULAR) && !MobArmorMathSC.hasEmergencyMode(null);
        boolean up = MobArmorMathSC.shieldUp(ArmorSuit.EXO, true, false, 5F) && MobArmorMathSC.shieldUp(ArmorSuit.SINGULAR, true, false, 0.1F)
                && !MobArmorMathSC.shieldUp(ArmorSuit.EXO, false, false, 5F) && !MobArmorMathSC.shieldUp(ArmorSuit.EXO, true, true, 5F)
                && !MobArmorMathSC.shieldUp(ArmorSuit.QUANTUM, true, false, 5F) && !MobArmorMathSC.shieldUp(ArmorSuit.NANO, true, false, 5F)
                && !MobArmorMathSC.shieldUp(ArmorSuit.EXO, true, false, 0F) && !MobArmorMathSC.shieldUp(null, true, false, 5F);
        boolean eu = MobArmorMathSC.unitsToEu(1F, ArmorSuit.NANO) == 500L && MobArmorMathSC.unitsToEu(2.5F, ArmorSuit.SINGULAR) == 20000L
                && MobArmorMathSC.unitsToEu(5F, null) == 0L && near(MobArmorMathSC.euToUnits(1000L, ArmorSuit.NANO), 2F)
                && near(MobArmorMathSC.euToUnits(6000L, ArmorSuit.QUANTUM), 3F) && MobArmorMathSC.euToUnits(-5L, ArmorSuit.NANO) == 0F;
        boolean consts = MobArmorMathSC.FULL_POINTS == 25 && MobArmorMathSC.MOB_SIPHON_STREAM_CAP == 50000 && MobArmorMathSC.TRACE_RECHARGE_PER_SECOND > 0F;
        check(shares && points && absorb && shield && iron && emergency && up && eu && consts, "mob armour maths (package A): stops 80/88/96/100% at "
                + "20/22/24/28 points " + shares + " (full sets " + Arrays.toString(suitPoints) + "), absorb with the reserve running out " + absorb
                + ", the shield's overflow " + shield + ", iron 2/6/5/2 " + iron + ", emergency only Quantum+ " + emergency + ", shieldUp " + up
                + ", EU <-> units by euPerDamage " + eu + ", 25 points / 50 000 EU a stream " + consts);

        // ПБ-5: the reserve by what is worn - every set of Nano pieces runs dry after the same blows (plasma 12, an arrow 6)
        int[] nano = new int[4];
        for (int t = 0; t < 4; t++) {
            nano[t] = ArmorSuit.NANO.material.getDamageReductionAmount(t);
        }
        int[] blows = {Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0};   // min / max: Lineman plasma, arrows; Marauder plasma
        for (int set = 1; set < 16; set++) {
            int pts = 0;
            for (int t = 0; t < 4; t++) {
                pts += (set >> t & 1) != 0 ? nano[t] : 0;
            }
            int[] got = {blowsToDrain(com.sc.mob.EntityLinemanSC.RESERVE_MIN, pts, 12F), blowsToDrain(com.sc.mob.EntityLinemanSC.RESERVE_MAX, pts, 12F),
                    blowsToDrain(com.sc.mob.EntityLinemanSC.RESERVE_MIN, pts, 6F), blowsToDrain(com.sc.mob.EntityLinemanSC.RESERVE_MAX, pts, 6F),
                    blowsToDrain(com.sc.mob.EntityMarauderSC.RESERVE_MIN, pts, 12F), blowsToDrain(com.sc.mob.EntityMarauderSC.RESERVE_MAX, pts, 12F)};
            for (int k = 0; k < 3; k++) {
                blows[2 * k] = Math.min(blows[2 * k], Math.min(got[2 * k], got[2 * k + 1]));
                blows[2 * k + 1] = Math.max(blows[2 * k + 1], Math.max(got[2 * k], got[2 * k + 1]));
            }
        }
        boolean small = near(MobArmorMathSC.reserveFor(20F, 5), 4F) && near(MobArmorMathSC.reserveFor(30F, 20), 24F)
                && MobArmorMathSC.reserveFor(20F, 0) == 0F && MobArmorMathSC.reserveFor(-5F, 20) == 0F;
        boolean same = blows[0] == 2 && blows[1] == 3 && blows[2] == 4 && blows[3] == 5 && blows[4] == 2 && blows[5] == 4;
        check(small && same, "mob reserve by what is worn (ПБ-5): reserveFor 20 dmg on 5 points 4, 30 on 20 points 24, none without pieces " + small
                + "; every set of 1..4 Nano pieces is drained by Plasma Rifle shots - Lineman " + blows[0] + ".." + blows[1] + " (2..3), arrows of 6 "
                + blows[2] + ".." + blows[3] + " (4..5), Marauder " + blows[4] + ".." + blows[5] + " (2..4)");
    }

    /** Blockable blows of `blow` until a reserve made for `damage` on `points` is empty (as MobArmorSC.take rounds it). */
    private static int blowsToDrain(float damage, int points, float blow) {
        float reserve = MobArmorMathSC.reserveFor(damage, points);
        int n = 0;
        while (reserve >= MobArmorMathSC.RESERVE_EPSILON && n < 1000) {
            reserve -= MobArmorMathSC.absorb(blow, points, reserve)[1];
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ trophies and spawning (package M, §5.5, §5.7)

    private static void trophyTable() {
        boolean metas = TrophyTableSC.METAS == 25 && TrophyTableSC.META_BLADE == 16 && TrophyTableSC.META_WEAPON == 20 && TrophyTableSC.META_PE1 == 23
                && TrophyTableSC.META_PE1M == 24 && TrophyTableSC.armorMeta(ArmorSuit.NANO, 0) == 0 && TrophyTableSC.armorMeta(ArmorSuit.NANO, 3) == 3
                && TrophyTableSC.armorMeta(ArmorSuit.EXO, 1) == 9 && TrophyTableSC.armorMeta(ArmorSuit.SINGULAR, 3) == 15
                && TrophyTableSC.bladeMeta(BladeType.NANO) == 16 && TrophyTableSC.bladeMeta(BladeType.SINGULAR) == 19
                && TrophyTableSC.weaponMeta(WeaponType.ION_CUTTER) == 20 && TrophyTableSC.weaponMeta(WeaponType.PLASMA_RIFLE) == 22;
        boolean chancesF1 = TrophyTableSC.PIECE[0] == 0.02F && TrophyTableSC.BLADE[0] == 0.015F && TrophyTableSC.WEAPON[WeaponType.ION_CUTTER.ordinal()] == 0.015F;
        boolean badLuck = TrophyTableSC.badLuckFactor(39, ArmorSuit.NANO) == 1 && TrophyTableSC.badLuckFactor(40, ArmorSuit.NANO) == 2
                && TrophyTableSC.badLuckFactor(79, ArmorSuit.NANO) == 2 && TrophyTableSC.badLuckFactor(80, ArmorSuit.NANO) == 4
                && TrophyTableSC.badLuckFactor(119, ArmorSuit.EXO) == 1 && TrophyTableSC.badLuckFactor(240, ArmorSuit.EXO) == 4;
        boolean chance = near(TrophyTableSC.chance(0.02F, 0, false, false, 1F, 1), 0.02F)
                && near(TrophyTableSC.chance(0.02F, 3, false, false, 1F, 1), 0.023F)                  // Looting III +15%
                && near(TrophyTableSC.chance(0.02F, 10, false, false, 1F, 1), 0.026F)                 // capped at x1.3
                && near(TrophyTableSC.chance(0.02F, 0, true, false, 1F, 1), 0.04F)                    // elite x2
                && near(TrophyTableSC.chance(0.02F, 0, true, true, 1F, 1), 0.05F)                     // x3 capped at x2.5
                && TrophyTableSC.chance(0.02F, 3, true, true, 0F, 4) == 0F                            // gearDropMultiplier 0: none
                && near(TrophyTableSC.chance(0.02F, 0, false, false, 2F, 1), 0.04F)
                && TrophyTableSC.chance(0.5F, 0, false, false, 1F, 4) == 1F;                         // never above 1
        check(metas && chancesF1 && badLuck && chance, "trophy table (package M): 25 metas (armour 0..15, blades 16..19, weapons 20..22, PE-1 23, "
                + "PE-1M 24) " + metas + ", Ф1 chances 2 / 1.5 / 1.5% " + chancesF1 + ", bad luck x1 / x2 / x4 at 39 / 40 / 80 Nano kills " + badLuck
                + ", chance: Looting capped x1.3, events x2.5, gearDropMultiplier 0 -> 0, at most 1 " + chance);
    }

    private static void spawnRules() {
        boolean consts = SpawnRulesSC.PERIOD == 200 && SpawnRulesSC.NEAR_RADIUS == 64 && SpawnRulesSC.NEAR_MAX == 8 && SpawnRulesSC.WORLD_MAX == 40
                && SpawnRulesSC.MIN_DIST == 24 && SpawnRulesSC.MAX_DIST == 48;
        boolean may = SpawnRulesSC.maySpawn(0, 0, 2, false) && SpawnRulesSC.maySpawn(6, 0, 2, false) && !SpawnRulesSC.maySpawn(7, 0, 2, false)
                && SpawnRulesSC.maySpawn(0, 38, 2, false) && !SpawnRulesSC.maySpawn(0, 39, 2, false) && !SpawnRulesSC.maySpawn(0, 0, 2, true)
                && !SpawnRulesSC.maySpawn(0, 0, 0, false) && SpawnRulesSC.maySpawn(4, 36, 4, false) && !SpawnRulesSC.maySpawn(5, 36, 4, false);
        check(consts && may, "spawn rules (package M): every " + SpawnRulesSC.PERIOD + " ticks, at most " + SpawnRulesSC.NEAR_MAX + " within "
                + SpawnRulesSC.NEAR_RADIUS + " and " + SpawnRulesSC.WORLD_MAX + " in the world, " + SpawnRulesSC.MIN_DIST + ".." + SpawnRulesSC.MAX_DIST
                + " blocks off " + consts + "; maySpawn by the caps, Peaceful never " + may);
    }

    // ------------------------------------------------------------------ entry (package E, §6)

    private static void entryRules() {
        boolean lawful = EntryRulesSC.lawful(false, false, false, false) && !EntryRulesSC.lawful(true, false, false, false)
                && EntryRulesSC.lawful(true, true, false, false) && EntryRulesSC.lawful(true, false, true, false) && EntryRulesSC.lawful(true, false, false, true);
        boolean cost = EntryRulesSC.costMultiplier(true, true, 0.5F) == 0.5F && EntryRulesSC.costMultiplier(false, true, 0.5F) == 1F
                && EntryRulesSC.costMultiplier(true, false, 0.5F) == 1F && EntryRulesSC.costMultiplier(true, true, 2F) == 2F;
        boolean cross = EntryRulesSC.mayCross(true, false, true, false) && EntryRulesSC.mayCross(true, true, true, false)
                && !EntryRulesSC.mayCross(false, true, true, false) && EntryRulesSC.mayCross(false, true, false, false)
                && EntryRulesSC.mayCross(false, true, true, true) && EntryRulesSC.mayCross(false, false, true, false);
        boolean consts = "bridge".equals(SubstrateEntrySC.VIA_BRIDGE) && "sva.jump".equals(SubstrateEntrySC.VIA_JUMP) && EntryRulesSC.BUFFER_TICKS == 200
                && EntryRulesSC.BUFFER_AMPLIFIER == 1 && EntryRulesSC.PORTAL_HOLD >= 20 && EntryRulesSC.PORTAL_CHECK > 0
                && EntryRulesSC.PORTAL_CHECK < EntryRulesSC.PORTAL_HOLD && EntryRulesSC.GRANT_TICKS > 0;
        boolean ways = EntryRulesSC.knownWay(SubstrateEntrySC.VIA_BRIDGE) && EntryRulesSC.knownWay(SubstrateEntrySC.VIA_JUMP)
                && !EntryRulesSC.knownWay("de.dislocator") && !EntryRulesSC.knownWay(null);
        boolean note = EntryRulesSC.safeToNote(true, false, false, false) && !EntryRulesSC.safeToNote(false, false, false, false)
                && !EntryRulesSC.safeToNote(true, true, false, false) && !EntryRulesSC.safeToNote(true, false, true, false)
                && !EntryRulesSC.safeToNote(true, false, false, true);
        boolean nbt = "scSi14Seen".equals(SubstrateEntrySC.NBT_SEEN) && "scSi14From".equals(SubstrateEntrySC.NBT_FROM);
        check(lawful && cost && cross && consts && ways && note && nbt, "entry rules (package E): strictEntry lets in only a grant / creative / op "
                + lawful + ", the price x0.5 only between dimensions with a Substrate end " + cost + ", natives never leave by a bridge unless allowed "
                + cross + ", buffer " + EntryRulesSC.BUFFER_TICKS / 20 + " s of Resistance II, the portal held >= " + EntryRulesSC.PORTAL_HOLD + " every "
                + EntryRulesSC.PORTAL_CHECK + " ticks " + consts + "; known ways: the bridge and the Jump, not another mod's or none " + ways
                + "; a way-back spot only on the ground, dry, not riding, not in bed " + note + "; NBT keys scSi14Seen / scSi14From " + nbt);
    }

    // ------------------------------------------------------------------ the items (package M, §5.7; ПТ-7)

    private static void items() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        Item trophy = MobItemsSC.trophy, loot = MobItemsSC.combatLoot, egg = MobItemsSC.spawnEgg;
        if (trophy == null || loot == null || egg == null) {
            check(false, "items: MobItemsSC.trophy / combatLoot / spawnEgg registered (package M) - " + (trophy != null) + " / " + (loot != null)
                    + " / " + (egg != null));
            return;
        }
        StringBuilder bad = new StringBuilder(), renamed = new StringBuilder();
        for (int m = 0; m < TrophyTableSC.METAS; m++) {
            ItemStack s = new ItemStack(trophy, 1, m);
            String key = trophy.getUnlocalizedName(s) + ".name";
            if (!en.containsKey(key) || !ru.containsKey(key)) {
                bad.append(' ').append(m).append(':').append(key);
            }
            if (!key.equals("item.siliconage.trophy." + BookWorldSC.TROPHY_KEYS[m] + ".name")) {
                renamed.append(' ').append(m).append(':').append(key);
            }
            ItemStack real = BookWorldSC.realOfTrophy(m);
            if (real == null || real.getItem() == null) {
                bad.append(' ').append(m).append(":no real item");
            }
        }
        for (int m = 0; m < BookWorldSC.LOOT_NAMES.length; m++) {
            String key = loot.getUnlocalizedName(new ItemStack(loot, 1, m)) + ".name";
            if (!en.containsKey(key) || !ru.containsKey(key)) {
                bad.append(" loot").append(m).append(':').append(key);
            }
        }
        String lineman = new ItemStack(egg, 1, MobEntitiesSC.ID_LINEMAN).getDisplayName(), marauder = new ItemStack(egg, 1, MobEntitiesSC.ID_MARAUDER).getDisplayName();
        boolean eggs = lineman.contains(BookWorldSC.mobName(com.sc.mob.EntityLinemanSC.NAME)) && marauder.contains(BookWorldSC.mobName(com.sc.mob.EntityMarauderSC.NAME));
        Set<Integer> shown = new TreeSet<Integer>();
        String subErr = null;
        try {
            List<ItemStack> sub = new ArrayList<ItemStack>();
            trophy.getSubItems(trophy, trophy.getCreativeTab(), sub);
            for (ItemStack s : sub) {
                shown.add(s.getItemDamage());
            }
        } catch (Throwable t) {
            subErr = t.toString();                                        // a dedicated server has no getSubItems
        }
        Set<Integer> f1 = new TreeSet<Integer>(Arrays.asList(0, 1, 2, 3, TrophyTableSC.META_BLADE, TrophyTableSC.META_WEAPON)), all = new TreeSet<Integer>();
        for (int m : TrophyTableSC.SHOWN) {
            all.add(m);                                     // Ф3 appended its trophies (SelfTestCombatSC checks the 19)
        }
        boolean tab = all.containsAll(f1) && (subErr != null || shown.equals(all));
        if (renamed.length() > 0) {
            System.out.println("[SC-TEST] INFO substrate: trophy names other than the plan's item.siliconage.trophy.<key>.name:" + renamed);
        }
        check(bad.length() == 0 && eggs && tab, "items: the 25 trophy metas each stand for a real item and have a name in en_US and ru_RU, the loot's "
                + "names, the eggs named after their mob (" + lineman + " / " + marauder + ") " + eggs + "; the creative tab / NEI show only SHOWN (Ф1's "
                + f1 + " among them) " + (subErr != null ? "(not checked: " + subErr + ")" : shown + " (want " + all + ")") + (bad.length() == 0 ? "" : " - BAD:" + bad));
    }

    // ------------------------------------------------------------------ texts and the handbook (package D, §7)

    private static void texts() {
        Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        List<String> keys = new ArrayList<String>(Arrays.asList(KEYS));
        for (String n : BookWorldSC.ROCK_NAMES) {
            keys.add("tile.siliconage.substrateRock." + n + ".name");
            keys.add("sc.tooltip.substrateRock." + n);
        }
        for (String k : BookWorldSC.TROPHY_KEYS) {
            keys.add("item.siliconage.trophy." + k + ".name");
        }
        for (String n : BookWorldSC.LOOT_NAMES) {
            keys.add("item.siliconage.combatLoot." + n + ".name");
            keys.add("sc.tooltip.loot." + n);
            keys.add("sc.tooltip.loot." + n + ".details");
            keys.add("sc.tooltip.loot." + n + ".howto");
        }
        StringBuilder missing = new StringBuilder();
        for (String k : keys) {
            for (Map<String, String> lang : new Map[]{en, ru}) {
                if (!lang.containsKey(k)) {
                    missing.append(' ').append(lang == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        // every Substrate key of one file in the other, with as many arguments; English is English
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
                } else if (from == en && e.getValue().matches(".*[\\u0400-\\u04FF«»].*")) {
                    diff.append(" Cyrillic / «» in en_US:").append(e.getKey());
                }
            }
        }
        check(missing.length() == 0 && diff.length() == 0, "texts: " + keys.size() + " keys of §7.1 in en_US and ru_RU, " + all
                + " Substrate keys with the same keys and arguments in both, no Cyrillic in English" + missing + diff);
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

    /** The WORLD chapter: its 6 Ф1 articles build with no raw key (and Ф2's 6 follow), the armour table by suit; G on the blocks, trophies, loot, eggs. */
    private static void handbook() {
        StringBuilder bad = new StringBuilder();
        for (String id : ARTICLES) {
            BookEntry e = BookContent.byId(id);
            if (e == null) {
                bad.append(' ').append(id).append(" missing");
                continue;
            }
            String t = e.searchText();
            if (e.chapter != BookChapter.WORLD || e.els.size() < 5) {
                bad.append(' ').append(id).append(" chapter ").append(e.chapter).append(" / ").append(e.els.size()).append(" elements");
            }
            for (String raw : new String[]{"sc.manual.", "sc.book.", "sc.dim.", "sc.suit.", "sc.wl.", "tile.siliconage.", "item.siliconage.",
                    "entity.siliconage.", "potion.", "%s", "%d"}) {
                if (t.contains(raw)) {
                    bad.append(' ').append(id).append(" raw '").append(raw).append('\'');
                }
            }
        }
        int chapter = BookContent.chapter(BookChapter.WORLD).size();
        boolean table = false;
        BookEntry armoured = BookContent.byId("world.armoured");
        if (armoured != null) {
            for (BookEl el : armoured.els) {
                table |= el.kind == BookEl.Kind.TABLE && el.rows.size() == ArmorSuit.values().length;
            }
        }
        StringBuilder noG = new StringBuilder();
        g(noG, SubstrateBlocksSC.rock == null ? null : new ItemStack(SubstrateBlocksSC.rock, 1, SubstrateBlocksSC.COPPER_TRACE), "world.terrain");
        g(noG, SubstrateBlocksSC.ore == null ? null : new ItemStack(SubstrateBlocksSC.ore, 1, OreEntry.TANTALITE.meta()), "world.terrain");
        g(noG, SubstrateBlocksSC.druse == null ? null : new ItemStack(SubstrateBlocksSC.druse), "world.terrain");
        g(noG, MobItemsSC.trophy == null ? null : new ItemStack(MobItemsSC.trophy, 1, TrophyTableSC.META_BLADE), "world.trophies");
        g(noG, MobItemsSC.combatLoot == null ? null : new ItemStack(MobItemsSC.combatLoot, 1, BookWorldSC.LOOT_NANO_SCRAP), "world.trophies");
        g(noG, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_LINEMAN), "world.lineman");
        g(noG, MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, MobEntitiesSC.ID_MARAUDER), "world.marauder");
        ItemStack icon = BookContent.chapterIcon(BookChapter.WORLD);
        boolean iconOk = icon != null && icon.getItem() != null && (SubstrateBlocksSC.rock == null || icon.getItem() == Item.getItemFromBlock(SubstrateBlocksSC.rock));
        List<String> unresolved = BookWorldSC.unresolved();
        BookContent.invalidate();
        if (!unresolved.isEmpty()) {
            System.out.println("[SC-TEST] INFO substrate: the bestiary still prints the plan's numbers for (package M's constants by these names, or "
                    + "direct references in BookWorldSC): " + unresolved);
        }
        int articles = ARTICLES.length + SelfTestSettlementSC.ARTICLES.length          // Ф2 appends its six (SelfTestSettlementSC checks them)
                + SelfTestCombatSC.ARTICLES.length;                                     // Ф3 its eight (SelfTestCombatSC)
        check(bad.length() == 0 && chapter == articles && table && noG.length() == 0 && iconOk, "handbook: the chapter \"" + BookChapter.WORLD.title()
                + "\" - " + chapter + " articles (" + articles + ": Ф1's " + ARTICLES.length + " with no raw keys, Ф2's and Ф3's), the armour table by suit " + table
                + ", G on the rock / ore / druse"
                + " / trophy / loot / eggs" + (noG.length() == 0 ? "" : " - NOT:" + noG) + ", the tile's icon " + iconOk + (bad.length() == 0 ? "" : " -" + bad));
    }

    /** G on `s` opens article `id` (an item not registered yet - package W / M - is listed). */
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

    // ------------------------------------------------------------------

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4F;
    }

    private static boolean pair(float[] got, float a, float b) {
        return got != null && got.length == 2 && near(got[0], a) && near(got[1], b);
    }
}
