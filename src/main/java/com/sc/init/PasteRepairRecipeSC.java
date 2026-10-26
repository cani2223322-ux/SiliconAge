package com.sc.init;

import com.sc.item.ItemDrillHeadSC;
import com.sc.item.ItemNanitePasteSC;
import com.sc.item.ItemToolSC;
import com.sc.util.SCToolType;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * ОМ-2 (docs/plan-ideas-2026-10-26.md, «Добыча Подложки в домашних машинах», ★ A): the Nanite Paste's repair - a shapeless crafting recipe
 * (RecipeSorter "siliconage:pasteRepair"): exactly one worn tool of the mod (a photomask, a sputter target, a Diamond Wire / Blade, an EUV
 * mask, a drill head with a life) and k pastes, k = (1 + its earlier paste repairs (the tool's NBT scPasteRepairs)) x its weight; out - the
 * same tool REPAIR_SHARE of its maximum less worn (never below new), one repair more. After MAX_REPAIRS it isn't mended any more. Over a
 * tool's life at most 3 x 25% = 75% of a new one for (1 + 2 + 3) x weight pastes (24 Nanite Dust a weight).
 * <p>
 * The weight (the review's fix: one flat price made the dear tools' repair far cheaper than a new one): 1 - the tools of plain metal and
 * vanilla things (a photomask, the sputter targets, the Diamond Wire and Blade, the steel drill head); a tool made of another mended tool
 * weighs more than it - the EUV mask (a photomask + tungsten + quartz chambers) and the tungsten head (the steel one + tungsten) 2, the
 * diamond head (the tungsten one + 5 Diamond Blades) 3. SelfTestIdeasSC checks that against the tools' own recipes. TODO(balance)
 */
public class PasteRepairRecipeSC implements IRecipe {

    /** The tool's NBT key: how many paste repairs it had. */
    public static final String NBT = "scPasteRepairs";
    /** A repair gives back this share of the maximum; at most this many repairs a tool. TODO(balance) */
    public static final float REPAIR_SHARE = 0.25F;
    public static final int MAX_REPAIRS = 3;
    /** The tools the paste mends (ItemToolSC types; the drill heads with a life besides). */
    public static final SCToolType[] TOOLS = {SCToolType.PHOTOMASK, SCToolType.SPUTTER_TARGET_COPPER, SCToolType.SPUTTER_TARGET_ALUMINIUM,
            SCToolType.SPUTTER_TARGET_TUNGSTEN, SCToolType.DIAMOND_WIRE, SCToolType.DIAMOND_BLADE, SCToolType.EUV_MASK};

    // ================================================================== pure

    /** Pure: the pastes the next repair of a weight-1 tool takes after `repairs` (1, 2, 3); 0 - none any more. */
    public static int pastesFor(int repairs) {
        return repairs < 0 || repairs >= MAX_REPAIRS ? 0 : repairs + 1;
    }

    /** The pastes the next repair of `tool` takes (pastesFor x weight); 0 - none any more (or not mendable). */
    public static int pastesFor(ItemStack tool) {
        return mendable(tool) ? pastesFor(repairs(tool)) * weight(tool) : 0;
    }

    /** How dear a mendable tool is to mend (see the class): 1, 2 or 3; 0 - not one the paste mends. */
    public static int weight(ItemStack s) {
        if (!mendable(s)) {
            return 0;
        }
        if (s.getItem() instanceof ItemDrillHeadSC) {
            ItemDrillHeadSC.Kind k = ((ItemDrillHeadSC) s.getItem()).kind;
            return k == ItemDrillHeadSC.Kind.DIAMOND ? 3 : k == ItemDrillHeadSC.Kind.TUNGSTEN ? 2 : 1;
        }
        return ((ItemToolSC) s.getItem()).getType() == SCToolType.EUV_MASK ? 2 : 1;
    }

    /** Pure: the wear after a repair - `damage` less REPAIR_SHARE of `max` (rounded up), never below 0. */
    public static int repaired(int damage, int max) {
        return Math.max(0, damage - (int) Math.ceil(max * REPAIR_SHARE));
    }

    /** The paste repairs `s` had. */
    public static int repairs(ItemStack s) {
        return s == null || !s.hasTagCompound() ? 0 : s.getTagCompound().getInteger(NBT);
    }

    /** `s` is a tool the paste mends (worn or not). */
    public static boolean mendable(ItemStack s) {
        if (s == null || s.getItem() == null || !s.getItem().isDamageable()) {
            return false;
        }
        if (s.getItem() instanceof ItemDrillHeadSC) {
            return ((ItemDrillHeadSC) s.getItem()).kind.life > 0;
        }
        if (s.getItem() instanceof ItemToolSC) {
            SCToolType t = ((ItemToolSC) s.getItem()).getType();
            for (SCToolType m : TOOLS) {
                if (m == t) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================================================================== the recipe

    @Override
    public boolean matches(InventoryCrafting inv, World w) {
        return result(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        return result(inv);
    }

    /** The repaired tool for the grid, or null (not this recipe: another thing in it, no worn tool, the wrong number of pastes). */
    static ItemStack result(InventoryCrafting inv) {
        ItemStack tool = null;
        int pastes = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null) {
                continue;
            }
            if (s.getItem() instanceof ItemNanitePasteSC) {
                pastes++;
            } else if (tool == null && mendable(s)) {
                tool = s;
            } else {
                return null;
            }
        }
        return repair(tool, pastes);
    }

    /** Pure but for the stacks: `tool` mended with `pastes` pastes, or null (not worn, mended MAX_REPAIRS times, the wrong count). */
    public static ItemStack repair(ItemStack tool, int pastes) {
        if (tool == null || tool.getItemDamage() <= 0) {
            return null;
        }
        int done = repairs(tool), need = pastesFor(done) * Math.max(1, weight(tool));
        if (need <= 0 || pastes != need) {
            return null;
        }
        ItemStack out = tool.copy();
        out.stackSize = 1;
        out.setItemDamage(repaired(tool.getItemDamage(), tool.getMaxDamage()));
        if (!out.hasTagCompound()) {
            out.setTagCompound(new NBTTagCompound());
        }
        out.getTagCompound().setInteger(NBT, done + 1);
        return out;
    }

    @Override
    public int getRecipeSize() {
        return 9;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }
}
