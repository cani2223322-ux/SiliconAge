package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

/**
 * Plain, common building-stone-like block - NOT an ore (per 01_recipes.md §17.1: it doesn't
 * occupy a BlockOreSC metadata slot). Flux for the very first machine step of the whole
 * silicon chain (Blast Furnace, §3 step 2), so it deliberately generates common and shallow
 * rather than rare like the metal ores.
 */
public class BlockLimestoneSC extends Block {

    private IIcon icon;

    public BlockLimestoneSC() {
        super(Material.rock);
        setBlockName(Reference.ASSETS + ".limestone");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(1.5F);
        setResistance(5.0F);
        setStepSound(soundTypeStone);
        setHarvestLevel("pickaxe", 1); // Stone Pickaxe, per §17.1
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":limestone");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return icon;
    }
}
