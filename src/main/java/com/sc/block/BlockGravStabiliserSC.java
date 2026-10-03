package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityGravStabiliserSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Gravitational Stabiliser: a passive dark pillar; up to 4 of them within
 * TileEntitySingularStationSC.STAB_RADIUS blocks of a Singular station (its Y +-1) speed its
 * modernisation up by 25% each. The orb on top is GravStabiliserRendererSC's - it glows while a
 * station near it works.
 */
public class BlockGravStabiliserSC extends Block {

    /** The pillar's footprint (of a block) and its height (the orb floats above it). */
    public static final float MIN = 5 / 16F, MAX = 11 / 16F, HEIGHT = 12 / 16F;

    @SideOnly(Side.CLIENT)
    private IIcon top;

    public BlockGravStabiliserSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".gravStabiliser");
        setBlockTextureName(Reference.ASSETS + ":gravStabiliser");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(4.0F);
        setResistance(15.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setBlockBounds(MIN, 0F, MIN, MAX, HEIGHT, MAX);
        setLightOpacity(0);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityGravStabiliserSC();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(Reference.ASSETS + ":gravStabiliser");
        top = register.registerIcon(Reference.ASSETS + ":gravStabiliserTop");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return face <= 1 ? top : blockIcon;
    }
}
