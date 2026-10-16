package com.sc.mob;

import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7): the trophies' numbers, pure - the metas of the trophy item, the
 * chances by level (rules ПТ-4, ПТ-5, ПТ-9 of docs/plan-substrate.md), the multipliers with their caps, the bad-luck
 * protection. MobDropsSC rolls by these, the self-test and the handbook read them. Signatures fixed; the bodies are
 * M's (the stub has the plan's numbers).
 */
public final class TrophyTableSC {

    /** Trophy metas (ПТ-7): 0..15 armour = suit ordinal x 4 + armorType, 16..19 blades, 20..22 weapons, 23 ПЭ-1, 24 ПЭ-1М. */
    public static final int META_BLADE = 16, META_WEAPON = 20, META_PE1 = 23, META_PE1M = 24, METAS = 25;
    /** Chances by category and level (ПТ-9; Quantum..Singular - their phases). TODO(balance) */
    public static final float[] PIECE = {0.02F, 0.01F, 0.005F, 0.002F}, BLADE = {0.015F, 0.007F, 0.003F, 0.001F};
    /** Weapons by WeaponType ordinal: Ion Cutter 1.5%, Pulse Emitter 1%, Plasma Rifle 0.6%. */
    public static final float[] WEAPON = {0.015F, 0.01F, 0.006F};
    /** ПТ-4: Looting +5% a level up to x1.3; elite x2, storm x1.5, the two together at most x2.5. */
    public static final float LOOTING_STEP = 0.05F, LOOTING_CAP = 1.3F, ELITE_MUL = 2F, STORM_MUL = 1.5F, EVENT_CAP = 2.5F;
    /** ПТ-5: kills of that level without a trophy before x2 (2N: x4), by ArmorSuit ordinal. */
    public static final int[] BAD_LUCK_N = {40, 80, 120, 40};
    /**
     * ПТ-7: the metas that already drop - the creative tab and NEI show only these (Ф1: the four Nano pieces, the Nano
     * Blade, the Ion Cutter). Each phase appends the trophies of its mobs.
     */
    public static final int[] SHOWN = {0, 1, 2, 3, META_BLADE, META_WEAPON};
    /** The key in the bad-luck counters' tag (EntityPlayer.PERSISTED_NBT_TAG: survives death): int[4] by level. */
    public static final String DRY_NBT = "scTrophyDry";

    private static final String[] PIECE_KEYS = {"Helmet", "Chestplate", "Leggings", "Boots"};

    private TrophyTableSC() {
    }

    /**
     * The meta's key - item.siliconage.trophy.<key>.name (each its own, for the Russian genders: «Трофейные Нано-поножи»,
     * «Трофейная Плазменная винтовка»): nanoHelmet .. singularBoots, bladeNano .. bladeSingular, ionCutter, pulseEmitter,
     * plasmaRifle, pe1, pe1m. An unknown meta: "unknown".
     */
    public static String key(int meta) {
        if (meta >= 0 && meta < META_BLADE) {
            return ArmorSuit.values()[meta / 4].textureName + PIECE_KEYS[meta % 4];
        }
        if (meta >= META_BLADE && meta < META_WEAPON) {
            String b = BladeType.values()[meta - META_BLADE].key();
            return "blade" + Character.toUpperCase(b.charAt(0)) + b.substring(1);
        }
        if (meta >= META_WEAPON && meta < META_PE1) {
            return WeaponType.values()[meta - META_WEAPON].textureName;
        }
        return meta == META_PE1 ? "pe1" : meta == META_PE1M ? "pe1m" : "unknown";
    }

    /** The meta is one of the trophies that drop by now (SHOWN). */
    public static boolean shown(int meta) {
        for (int m : SHOWN) {
            if (m == meta) {
                return true;
            }
        }
        return false;
    }

    /** The trophy's level - the suit it belongs to (armour, blades), null for the guns and ПЭ-1 (no level of their own). */
    public static ArmorSuit level(int meta) {
        if (meta >= 0 && meta < META_BLADE) {
            return ArmorSuit.values()[meta / 4];
        }
        if (meta >= META_BLADE && meta < META_WEAPON) {
            return BladeType.values()[meta - META_BLADE].suit;
        }
        return null;
    }

    /** ПТ-4: a weighted pick among the categories' chances (`roll` in 0..1). @return the index into `chances`, -1 if all are 0 */
    public static int pickCategory(float[] chances, float roll) {
        float sum = 0F;
        for (float c : chances) {
            sum += Math.max(0F, c);
        }
        if (sum <= 0F) {
            return -1;
        }
        float at = roll * sum;
        int last = -1;
        for (int i = 0; i < chances.length; i++) {
            if (chances[i] <= 0F) {
                continue;
            }
            last = i;
            at -= chances[i];
            if (at < 0F) {
                return i;
            }
        }
        return last;
    }

    public static int armorMeta(ArmorSuit suit, int armorType) {
        return suit.ordinal() * 4 + armorType;
    }

    public static int bladeMeta(BladeType t) {
        return META_BLADE + t.ordinal();
    }

    public static int weaponMeta(WeaponType t) {
        return META_WEAPON + t.ordinal();
    }

    /** ПТ-5: x1 / x2 / x4 after `dryKills` kills of that level with no trophy. */
    public static int badLuckFactor(int dryKills, ArmorSuit tier) {
        int n = BAD_LUCK_N[tier.ordinal()];
        return dryKills >= 2 * n ? 4 : dryKills >= n ? 2 : 1;
    }

    /**
     * The chance of one roll: the sum of the categories' `base` times Looting (capped), the events (capped), the
     * config's gearDropMultiplier and the bad-luck factor - never above 1.
     */
    public static float chance(float base, int looting, boolean elite, boolean storm, float configMul, int badLuck) {
        float loot = Math.min(LOOTING_CAP, 1F + LOOTING_STEP * Math.max(0, looting));
        float ev = Math.min(EVENT_CAP, (elite ? ELITE_MUL : 1F) * (storm ? STORM_MUL : 1F));
        return Math.max(0F, Math.min(1F, base * loot * ev * Math.max(0F, configMul) * Math.max(1, badLuck)));
    }
}
