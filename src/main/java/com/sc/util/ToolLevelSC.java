package com.sc.util;

import com.sc.item.ItemBladeSC;
import com.sc.item.ItemDrillSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * The Singular blade's and drill's levels, branches, colour scheme and cooldowns (docs/plan-singular-tools.md),
 * all kept in the tool's own NBT like the Singular armour's (SingularLevel): "SingLevel" 1..5 (none: 1),
 * "SingPts" towards the next level (capped at the threshold), "SingBranch" (chosen once at level 3 - free in
 * the K menu, re-chosen in the station; the same choice gives its level-5 perk), "SingScheme" and "SingCd"
 * (absolute world ticks per cooldown key - the client sees them through the normal item sync).
 * Tools have no tasks: full points = ready for the station. Creative mode counts as level 5 with every branch.
 */
public final class ToolLevelSC {

    public static final String LEVEL = "SingLevel", PTS = "SingPts", BRANCH = "SingBranch", SCHEME = SingularScheme.NBT, COOLDOWNS = "SingCd";
    public static final int MIN = 1, MAX = 5;
    /** Exo-era functions (Nano / Quantum / Exo) on a Singular tool spend this share of their EU. */
    public static final float LEGACY_MUL = 0.8F;
    /** Full Singular suit: the tools' cooldowns are this share of the base. */
    public static final float SET_COOLDOWN_MUL = 0.75F;

    /** Branches: none chosen yet; the blade's three, the drill's two. */
    public static final int BRANCH_NONE = 0;
    public static final int BLADE_DESTROYER = 1, BLADE_DUELIST = 2, BLADE_GUARDIAN = 3;
    public static final int DRILL_MINER = 1, DRILL_PROSPECTOR = 2;
    /** The level the branch is chosen at, and the level of its second perk. */
    public static final int BRANCH_LEVEL = 3, BRANCH_PERK_LEVEL = 5;

    /**
     * Points a tool at level N needs for N+1 (index = level; level 5: none). The blade earns 5 a mob (200 a
     * minute at most) and 500 a boss - about the armour's pace (SingularLevel: 2000 / 8000 / 25000 / 60000), a
     * little more at first since a blade only fights; the drill 1 per 16 blocks dug plus 10 a crumb fed.
     */
    private static final int[] BLADE_THRESHOLD = {0, 3000, 10000, 25000, 60000, 0};
    private static final int[] DRILL_THRESHOLD = {0, 2000, 8000, 20000, 50000, 0};

    private ToolLevelSC() {
    }

    // ------------------------------------------------------------------ what it is

    public static boolean isBlade(ItemStack s) {
        return ItemBladeSC.typeOf(s) == BladeType.SINGULAR;
    }

    public static boolean isDrill(ItemStack s) {
        return ItemDrillSC.typeOf(s) == DrillType.SINGULAR;
    }

    /** A Singular blade or drill. */
    public static boolean isSingularTool(ItemStack s) {
        return isBlade(s) || isDrill(s);
    }

    private static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        return s.getTagCompound();
    }

    // ------------------------------------------------------------------ level and points

    public static int clamp(int level) {
        return Math.max(MIN, Math.min(MAX, level));
    }

    /** 1..5 (no tag: 1). */
    public static int levelOf(ItemStack t) {
        if (t == null || !t.hasTagCompound() || !t.getTagCompound().hasKey(LEVEL)) {
            return MIN;
        }
        return clamp(t.getTagCompound().getInteger(LEVEL));
    }

    public static void setLevel(ItemStack t, int lv) {
        if (t != null) {
            tag(t).setInteger(LEVEL, clamp(lv));
        }
    }

    /** The level that counts for this player: creative - always 5. */
    public static int effectiveLevel(EntityPlayer p, ItemStack t) {
        return p != null && p.capabilities.isCreativeMode ? MAX : levelOf(t);
    }

    /** Pure: points a blade (or a drill) at `level` needs for the next one (0 at 5 / out of range). */
    public static int threshold(boolean blade, int level) {
        return level >= MIN && level < MAX ? (blade ? BLADE_THRESHOLD : DRILL_THRESHOLD)[level] : 0;
    }

    /** Points this tool at `level` needs for the next one (its kind's table; not a Singular tool: 0). */
    public static int threshold(ItemStack t, int level) {
        return isSingularTool(t) ? threshold(isBlade(t), level) : 0;
    }

    /** Points this tool needs at its own level (contract's threshold(level) with the tool for the table). */
    public static int threshold(ItemStack t) {
        return threshold(t, levelOf(t));
    }

    public static int points(ItemStack t) {
        return t == null || !t.hasTagCompound() ? 0 : Math.max(0, t.getTagCompound().getInteger(PTS));
    }

    /** Pure: what's kept of `pts` against `need` (0..need; the excess is lost). */
    public static int capPoints(int need, long pts) {
        return (int) Math.max(0, Math.min(need, pts));
    }

    public static void setPoints(ItemStack t, int p) {
        if (t != null) {
            tag(t).setInteger(PTS, capPoints(threshold(t), p));
        }
    }

    /** Adds points (a Singular tool only, capped at its threshold). @return how many were added */
    public static int addPoints(ItemStack t, int n) {
        if (!isSingularTool(t) || n <= 0) {
            return 0;
        }
        int before = points(t);
        int after = capPoints(threshold(t), (long) before + n);
        if (after != before) {
            tag(t).setInteger(PTS, after);
        }
        return after - before;
    }

    /** Points still missing for the next level (0 at the top level / full). */
    public static int pointsLeft(ItemStack t) {
        return Math.max(0, threshold(t) - points(t));
    }

    /** All the points its level asks for (the top level: never). */
    public static boolean pointsFull(ItemStack t) {
        int need = threshold(t);
        return need > 0 && points(t) >= need;
    }

    /** May go up a level in the station: Singular, under 5, points full (tools have no tasks). */
    public static boolean readyToUpgrade(ItemStack t) {
        return isSingularTool(t) && levelOf(t) < MAX && pointsFull(t);
    }

    /** The station: one level up, points back to 0. @return the new level */
    public static int applyLevelUp(ItemStack t) {
        if (!isSingularTool(t)) {
            return levelOf(t);
        }
        int lv = clamp(levelOf(t) + 1);
        setLevel(t, lv);
        tag(t).setInteger(PTS, 0);
        return lv;
    }

    /** Server: «Сингулярный клинок: уровень N!», a sound and a burst of particles (as SingularLevel.levelUpEffects). */
    public static void levelUpEffects(EntityPlayer p, ItemStack t, int lv) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        Object name = t == null ? "" : new net.minecraft.util.ChatComponentTranslation(t.getItem().getUnlocalizedName(t) + ".name");
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.tool.sing.levelup", name, String.valueOf(lv)));
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.levelup", 1F, 0.7F);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 0.8F, 0.6F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) p.worldObj;
            ws.func_147487_a("portal", p.posX, p.posY + 1, p.posZ, 120, 0.6, 1.0, 0.6, 1.2);
            ws.func_147487_a("witchMagic", p.posX, p.posY + 1, p.posZ, 60, 0.8, 1.0, 0.8, 0.2);
            ws.func_147487_a("fireworksSpark", p.posX, p.posY + 1.2, p.posZ, 40, 0.3, 0.3, 0.3, 0.25);
        }
    }

    // ------------------------------------------------------------------ branches

    /** Pure: how many branches a blade (3) / drill (2) has. */
    public static int branchCount(boolean blade) {
        return blade ? 3 : 2;
    }

    /** 3 for the blade, 2 for the drill, 0 for anything else. */
    public static int branchCount(ItemStack t) {
        return isSingularTool(t) ? branchCount(isBlade(t)) : 0;
    }

    /** Pure: `b` is a branch of a blade / drill (BRANCH_NONE is not). */
    public static boolean validBranch(boolean blade, int b) {
        return b >= 1 && b <= branchCount(blade);
    }

    /** The chosen branch, BRANCH_NONE while none (or an invalid value). */
    public static int branchOf(ItemStack t) {
        if (!isSingularTool(t) || !t.hasTagCompound()) {
            return BRANCH_NONE;
        }
        int b = t.getTagCompound().getInteger(BRANCH);
        return validBranch(isBlade(t), b) ? b : BRANCH_NONE;
    }

    /** Sets the branch (BRANCH_NONE clears it - the station's re-choice). @return whether `b` was valid for this tool */
    public static boolean setBranch(ItemStack t, int b) {
        if (!isSingularTool(t)) {
            return false;
        }
        if (b == BRANCH_NONE) {
            if (t.hasTagCompound()) {
                t.getTagCompound().removeTag(BRANCH);
            }
            return true;
        }
        if (!validBranch(isBlade(t), b)) {
            return false;
        }
        tag(t).setInteger(BRANCH, b);
        return true;
    }

    /** Level 3 reached and no branch chosen: the K menu offers the free choice. */
    public static boolean branchPending(ItemStack t) {
        return isSingularTool(t) && levelOf(t) >= BRANCH_LEVEL && branchOf(t) == BRANCH_NONE;
    }

    /** Server, the K menu (TOOL_BRANCH): the free first choice, only while pending. @return whether it was taken */
    public static boolean chooseFree(EntityPlayer p, ItemStack t, int b) {
        if (p == null || !branchPending(t) || !validBranch(isBlade(t), b)) {
            return false;
        }
        return setBranch(t, b);
    }

    /** Pure: the branch perk of `level` works (creative: every branch). */
    public static boolean hasBranch(int effectiveLevel, boolean creative, int chosen, int b, int level) {
        return effectiveLevel >= level && (creative || chosen == b);
    }

    /** The branch `b`'s perk of `level` (3 or 5) works for this player and tool. */
    public static boolean hasBranch(EntityPlayer p, ItemStack t, int b, int level) {
        boolean creative = p != null && p.capabilities.isCreativeMode;
        return isSingularTool(t) && hasBranch(effectiveLevel(p, t), creative, branchOf(t), b, level);
    }

    /** Lang key of a branch name: sc.toolbranch.blade.1 / sc.toolbranch.drill.2 ... */
    public static String branchLangKey(ItemStack t, int b) {
        return "sc.toolbranch." + (isBlade(t) ? "blade" : "drill") + "." + b;
    }

    // ------------------------------------------------------------------ colour scheme

    public static SingularScheme schemeOf(ItemStack t) {
        return SingularScheme.of(t);
    }

    public static void setScheme(ItemStack t, SingularScheme s) {
        SingularScheme.setScheme(t, s);
    }

    /** The scheme of the worn Singular chestplate copied onto the tool. @return whether the tool changed */
    public static boolean syncScheme(EntityPlayer p, ItemStack t) {
        if (p == null || !isSingularTool(t)) {
            return false;
        }
        ItemStack chest = ArmorGasSC.worn(p, ArmorGasSC.CHEST);
        if (!SingularLevel.isSingular(chest)) {
            return false;
        }
        SingularScheme s = SingularScheme.of(chest);
        boolean has = t.hasTagCompound() && t.getTagCompound().hasKey(SCHEME);
        if (has && schemeOf(t) == s || !has && s == SingularScheme.DEFAULT) {
            return false;
        }
        setScheme(t, s);
        return true;
    }

    // ------------------------------------------------------------------ cooldowns (in the tool)

    /** The world tick the cooldown `key` ends at (0: none). */
    public static long cooldownEnd(ItemStack t, String key) {
        if (t == null || key == null || !t.hasTagCompound()) {
            return 0L;
        }
        return t.getTagCompound().getCompoundTag(COOLDOWNS).getLong(key);
    }

    /** Starts the cooldown `key` for `ticks` from now (0 or less clears it). */
    public static void setCooldown(ItemStack t, String key, World w, int ticks) {
        if (t == null || key == null || w == null) {
            return;
        }
        setCooldownEnd(t, key, ticks <= 0 ? 0L : w.getTotalWorldTime() + ticks);
    }

    /** Pure-ish: the cooldown `key` ends at the world tick `end` (0 clears it; an empty store is removed). */
    public static void setCooldownEnd(ItemStack t, String key, long end) {
        NBTTagCompound root = tag(t);
        NBTTagCompound cd = root.getCompoundTag(COOLDOWNS);
        if (end <= 0) {
            cd.removeTag(key);
        } else {
            cd.setLong(key, end);
        }
        if (cd.hasNoTags()) {
            root.removeTag(COOLDOWNS);
        } else {
            root.setTag(COOLDOWNS, cd);
        }
    }

    /** Ticks left of the cooldown `key` (0: ready). */
    public static int cooldownLeft(ItemStack t, String key, World w) {
        return w == null ? 0 : cooldownLeft(t, key, w.getTotalWorldTime());
    }

    public static int cooldownLeft(ItemStack t, String key, long now) {
        return SingularCooldowns.left(cooldownEnd(t, key), now);
    }

    /** Pure: a base cooldown with or without the full Singular suit (−25%, at least 1 tick when the base isn't 0). */
    public static int cooldownTicks(int baseTicks, boolean fullSet) {
        if (baseTicks <= 0) {
            return 0;
        }
        return fullSet ? Math.max(1, Math.round(baseTicks * SET_COOLDOWN_MUL)) : baseTicks;
    }

    /** A base cooldown for this player: −25% with the full Singular suit worn (and working). */
    public static int cooldownTicks(EntityPlayer p, int baseTicks) {
        return cooldownTicks(baseTicks, p != null && com.sc.item.ArmorLogicSC.bonusSet(p) == ArmorSuit.SINGULAR);
    }

    // ------------------------------------------------------------------ the Exo-era functions' EU

    /** Pure: what an Exo-era function costing `eu` spends on a Singular tool (rounded up). */
    public static int legacyCost(int eu) {
        return eu <= 0 ? eu : (int) Math.ceil((double) eu * LEGACY_MUL - 1e-3);   // 0.8F is a hair over 0.8: 1000 -> 800, not 801
    }

    /** `eu` of an Exo-era function on this tool: LEGACY_MUL on a Singular tool, else as it is. */
    public static int legacyCost(ItemStack t, int eu) {
        return isSingularTool(t) ? legacyCost(eu) : eu;
    }
}
