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
 * The swirl is one big animated picture cut into 3x3 (5x5) tiles - each cell shows its own.
 */
public class BlockBridgeVortexSC extends Block {

    public static final int GROUND_TILES = 3, SPACE_TILES = 5;

    @SideOnly(Side.CLIENT)
    private IIcon[] ground, space;

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
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess w, int x, int y, int z, int side) {
        return w.getBlock(x, y, z) != this && super.shouldSideBeRendered(w, x, y, z, side);
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
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        boolean sp = world.getBlockMetadata(x, y, z) == 1;
        for (int i = 0; i < 2; i++) {
            world.spawnParticle("portal", x + rand.nextDouble(), y + rand.nextDouble(), z + rand.nextDouble(),
                    (rand.nextDouble() - 0.5) * 0.6, (rand.nextDouble() - 0.5) * 0.6, (rand.nextDouble() - 0.5) * 0.6);
        }
        if (sp && rand.nextInt(4) == 0) {
            world.spawnParticle("fireworksSpark", x + rand.nextDouble(), y + rand.nextDouble(), z + rand.nextDouble(), 0, 0.01, 0);
        }
        if (rand.nextInt(120) == 0) {
            world.playSound(x + 0.5, y + 0.5, z + 0.5, "portal.portal", 0.35F, rand.nextFloat() * 0.4F + (sp ? 1.2F : 0.8F), false);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        ground = new IIcon[GROUND_TILES * GROUND_TILES];
        space = new IIcon[SPACE_TILES * SPACE_TILES];
        for (int i = 0; i < ground.length; i++) {
            ground[i] = register.registerIcon(Reference.ASSETS + ":bridgeVortexG_" + i);
        }
        for (int i = 0; i < space.length; i++) {
            space[i] = register.registerIcon(Reference.ASSETS + ":bridgeVortexS_" + i);
        }
        blockIcon = ground[ground.length / 2];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return meta == 1 ? space[space.length / 2] : ground[ground.length / 2];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int face) {
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityBridgeVortexSC)) {
            return getIcon(face, meta);
        }
        TileEntityBridgeVortexSC v = (TileEntityBridgeVortexSC) te;
        IIcon[] set = meta == 1 ? space : ground;
        int n = meta == 1 ? SPACE_TILES : GROUND_TILES;
        int col = Math.max(0, Math.min(n - 1, v.getTileU())), row = Math.max(0, Math.min(n - 1, v.getTileV()));
        return set[row * n + col];
    }
}
