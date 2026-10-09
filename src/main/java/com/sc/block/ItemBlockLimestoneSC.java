package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.worldgen.OreGenSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Limestone as an item: what it's for and what mines it; under Shift where it lies, as an ore's. */
public class ItemBlockLimestoneSC extends ItemBlock {

    public ItemBlockLimestoneSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.limestone.tooltip"));
        list.add("§8" + Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool.stone")));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.manual.ores.where", OreGenSC.LIMESTONE_MIN_Y, OreGenSC.LIMESTONE_MAX_Y,
                OreGenSC.LIMESTONE_VEIN_SIZE, OreGenSC.LIMESTONE_VEINS_PER_CHUNK) + ItemBlockOreSC.dimensionsSuffix(), null);
    }
}
