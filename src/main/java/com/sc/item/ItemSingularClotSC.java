package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * «Сгусток сингулярности» (docs/plan-singular-tools.md §3.2): nine crumbs pressed together. The Matter
 * Compressor in its liquid-matter mode turns one into SM_PER_CLOT mB of singular matter at once
 * (TileEntityMachineSC.absorbMatter); in the capsule mode it waits in the input slot. Epic - never
 * counted as plain mass.
 */
public class ItemSingularClotSC extends Item {

    /** mB of singular matter a clot gives in the compressor's liquid mode. */
    public static final int SM_PER_CLOT = 100;

    public ItemSingularClotSC() {
        setMaxStackSize(64);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".singularClot");
    }

    public static boolean isClot(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSingularClotSC;
    }

    @Override
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(Reference.ASSETS + ":singularClot");
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.singclot"));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.tooltip.singclot.details", SM_PER_CLOT), Lang.tr("sc.tooltip.singclot.howto"));
    }
}
