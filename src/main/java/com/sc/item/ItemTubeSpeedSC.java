package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Speed upgrade for a pneumatic tube's extract connector (Ender IO style): +4 items per move each, up to 64. */
public class ItemTubeSpeedSC extends Item {

    public static final int ITEMS_PER_UPGRADE = 4;
    public static final int MAX_ITEMS = 64;

    public ItemTubeSpeedSC() {
        setMaxStackSize(15);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".tubeSpeedUpgrade");
        setTextureName(Reference.ASSETS + ":tubeSpeedUpgrade");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.tubespeed.tooltip"));
    }
}
