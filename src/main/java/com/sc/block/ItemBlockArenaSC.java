package com.sc.block;

import java.util.List;

import com.sc.arena.ArenaBlocksSC;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.2): the arena blocks' item - a name per meta (tile.siliconage.arena.<name>.name) and
 * the TooltipSC tooltip (sc.tooltip.arena.<name> + .details + .howto: «только в Эпицентре Пробоя»). Creative only (hidden in NEI).
 */
public class ItemBlockArenaSC extends ItemBlock {

    public ItemBlockArenaSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + ArenaBlocksSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String k = "sc.tooltip.arena." + ArenaBlocksSC.nameOf(stack.getItemDamage());
        TooltipSC.wrap(list, Lang.tr(k), "§7");
        TooltipSC.more(list, Lang.tr(k + ".details"), Lang.tr(k + ".howto"));
    }
}
