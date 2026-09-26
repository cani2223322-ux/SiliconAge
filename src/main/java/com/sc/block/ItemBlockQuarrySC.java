package com.sc.block;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityQuarrySC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockQuarrySC extends ItemBlock {

    public ItemBlockQuarrySC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockQuarrySC.tierFor(stack.getItemDamage()).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = Math.max(0, Math.min(3, stack.getItemDamage()));
        list.add(Lang.tr("sc.quarry.tooltip.size", TileEntityQuarrySC.BASE_SIZE[t], TileEntityQuarrySC.BASE_SIZE[t],
                String.valueOf(TileEntityQuarrySC.TIER_SPEED[t])));
        if (stack.hasTagCompound() && stack.getTagCompound().getLong("Mined") > 0) {
            list.add(Lang.tr("sc.quarry.tooltip.mined", String.valueOf(stack.getTagCompound().getLong("Mined"))));
        }
        list.add("§7" + Lang.tr("sc.quarry.tooltip.hint"));
    }
}
