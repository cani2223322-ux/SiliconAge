package com.sc.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public class ModCreativeTab extends CreativeTabs {

    public static final CreativeTabs TAB = new ModCreativeTab("siliconage");

    private ModCreativeTab(String label) {
        super(label);
    }

    @Override
    public Item getTabIconItem() {
        return Item.getItemFromBlock(ModBlocks.oreSC);
    }
}
