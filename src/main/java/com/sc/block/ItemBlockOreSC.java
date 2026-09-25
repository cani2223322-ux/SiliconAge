package com.sc.block;

import com.sc.util.OreEntry;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Without this, all 16 ore metadata variants would share BlockOreSC's single unlocalized
 * name and show identical tooltips - this appends the specific ore's name per §10.
 */
public class ItemBlockOreSC extends ItemBlock {

    public ItemBlockOreSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        OreEntry ore = OreEntry.byMeta(stack.getItemDamage());
        return super.getUnlocalizedName() + "." + ore.oreName;
    }
}
