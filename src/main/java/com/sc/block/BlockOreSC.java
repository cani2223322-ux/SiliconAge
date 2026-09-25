package com.sc.block;

import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.OreEntry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * The single ore block covering all 16 ores from 01_recipes.md §10, one per metadata value
 * (0-15 - the full range a block's metadata nibble allows, which is exactly why the design
 * doc capped the ore list at 16 instead of the originally-requested 19: see §10's "4-я
 * проверка" note).
 */
public class BlockOreSC extends Block {

    private IIcon[] icons;

    public BlockOreSC() {
        super(Material.rock);
        setBlockName(Reference.ASSETS + ".oreSC");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.0F);
        setResistance(5.0F);
        setStepSound(soundTypeStone);
        for (OreEntry ore : OreEntry.values()) {
            setHarvestLevel("pickaxe", ore.tool.level, ore.meta());
        }
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[OreEntry.values().length];
        for (OreEntry ore : OreEntry.values()) {
            icons[ore.meta()] = register.registerIcon(Reference.ASSETS + ":ore_" + ore.oreName);
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (icons == null || meta < 0 || meta >= icons.length) {
            return icons != null ? icons[0] : null;
        }
        return icons[meta];
    }

    @Override
    public int damageDropped(int meta) {
        // Preserve the ore type when broken (drops the same metadata variant), matching how
        // OreEntry.byMeta is used everywhere else to recover which ore a stack represents.
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (OreEntry ore : OreEntry.values()) {
            list.add(new ItemStack(item, 1, ore.meta()));
        }
    }

    @Override
    public int quantityDropped(Random random) {
        return 1;
    }
}
