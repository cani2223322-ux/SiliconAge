package com.sc.block;

import java.util.Random;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

/**
 * Radiation shielding: the lead block (nine ingots) lets 2% through, lead glass 5% - to see a
 * reactor through. Lead glass drops itself (it isn't cheap).
 */
public final class BlockLeadSC {

    private BlockLeadSC() {
    }

    public static class Solid extends Block {
        private IIcon icon;

        public Solid() {
            super(Material.iron);
            setBlockName(Reference.ASSETS + ".leadBlock");
            setCreativeTab(ModCreativeTab.TAB);
            setHardness(5.0F);
            setResistance(12.0F);
            setStepSound(soundTypeMetal);
            setHarvestLevel("pickaxe", 1);
        }

        @Override
        public void registerBlockIcons(IIconRegister register) {
            icon = register.registerIcon(Reference.ASSETS + ":leadBlock");
        }

        @Override
        public IIcon getIcon(int side, int meta) {
            return icon;
        }

        /** Part of a big tokamak's wall: the tokamak's screen (empty hand). */
        @Override
        public boolean onBlockActivated(net.minecraft.world.World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p,
                                        int side, float hx, float hy, float hz) {
            return com.sc.tileentity.TileEntityGeneratorSC.openFromBuild(w, x, y, z, p);
        }
    }

    public static class Glass extends BlockGlass {
        public Glass() {
            super(Material.glass, false);
            setBlockName(Reference.ASSETS + ".leadGlass");
            setCreativeTab(ModCreativeTab.TAB);
            setHardness(1.5F);
            setResistance(10.0F);
            setStepSound(soundTypeGlass);
            setBlockTextureName(Reference.ASSETS + ":leadGlass");
        }

        @Override
        public int quantityDropped(Random random) {
            return 1;
        }

        /** A window in a big tokamak's wall: the tokamak's screen (empty hand). */
        @Override
        public boolean onBlockActivated(net.minecraft.world.World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p,
                                        int side, float hx, float hy, float hz) {
            return com.sc.tileentity.TileEntityGeneratorSC.openFromBuild(w, x, y, z, p);
        }
    }
}
