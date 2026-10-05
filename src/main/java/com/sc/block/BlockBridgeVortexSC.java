package com.sc.block;

import java.util.Random;

import com.sc.Reference;
import com.sc.tileentity.TileEntityBridgeVortexSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * One cell of an open bridge's vortex (meta 0 Ground - green, 1 Space - blue-white with stars): no item,
 * nothing solid, it glows. It is only ever put into air and taken away when the portal closes (or when it
 * finds its portal gone). Whatever walks into it goes to the other end (TileEntityBridgeControllerSC.enter).
 * The cells draw nothing as blocks: the opening's centre cell draws the whole portal as one disc
 * (client/BridgeVortexRendererSC); every cell still lights the place, takes whoever walks in and is what WAILA shows.
 */
public class BlockBridgeVortexSC extends Block {

    /** Only for the odd particle the game asks a block for (the disc itself is drawn from textures/fx). */
    @SideOnly(Side.CLIENT)
    private IIcon ground, space;

    public BlockBridgeVortexSC() {
        super(Material.portal);
        setBlockName(Reference.ASSETS + ".bridgeVortex");
        setBlockTextureName(Reference.ASSETS + ":bridgeVortexG_4");
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightLevel(1.0F);
        setLightOpacity(0);
        setStepSound(soundTypeGlass);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityBridgeVortexSC();
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
        return null;
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
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canCollideCheck(int meta, boolean hitIfLiquid) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
    }

    /** ВП1: no block model - the master cell's renderer draws the whole opening as one disc. */
    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return null;
    }

    @Override
    public int quantityDropped(Random rand) {
        return 0;
    }

    @Override
    public boolean canDropFromExplosion(net.minecraft.world.Explosion e) {
        return false;
    }

    @Override
    public boolean isReplaceable(IBlockAccess w, int x, int y, int z) {
        return false;
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity e) {
        if (world.isRemote) {
            return;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityBridgeVortexSC) {
            ((TileEntityBridgeVortexSC) te).entered(e);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        ground = register.registerIcon(Reference.ASSETS + ":bridgeVortexG_4");
        space = register.registerIcon(Reference.ASSETS + ":bridgeVortexS_12");
        blockIcon = ground;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return meta == 1 ? space : ground;
    }
}
