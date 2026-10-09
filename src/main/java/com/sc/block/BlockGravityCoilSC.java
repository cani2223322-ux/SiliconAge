package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Gravity Coil: 16 of them - two rings of 8 round the axis, above and below the Singular
 * Reactor (levels 2 and 4 of its 7x7x5 build) - hold its black hole. The Ground / Space Bridge's ring is
 * made of them too (16 / 24 in a square standing on its edge): while its portal is open its coils glow
 * (metadata 1, set by the bridge controller) and show the ring's heat - 1 cold blue, 2 warm orange, 3 hot red (a
 * tint; a hot ring keeps its glow while it cools). A coil taken out of a bridge ring loses that bridge its calibration (every
 * controller whose ring it was in hears of it - М-5; the wear stays, only «Ремонт» takes it off - М-4).
 */
public class BlockGravityCoilSC extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon lit;

    public BlockGravityCoilSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".gravityCoil");
        setBlockTextureName(Reference.ASSETS + ":gravityCoil");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(15.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
    }

    /** Broken only with a pickaxe: a stray punch doesn't lose it (PickaxeOnlySC). */
    @Override
    public float getPlayerRelativeBlockHardness(net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world, int x, int y, int z) {
        return PickaxeOnlySC.hardness(super.getPlayerRelativeBlockHardness(player, world, x, y, z), player);
    }

    /** A coil of a Singular Reactor's build: the reactor's screen (empty hand); of a bridge ring: the bridge controller's. */
    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p,
                                    int side, float hx, float hy, float hz) {
        if (com.sc.tileentity.TileEntityGeneratorSC.openFromBuild(w, x, y, z, p)) {
            return true;
        }
        if (p.getCurrentEquippedItem() != null || p.isSneaking()) {
            return false;
        }
        com.sc.tileentity.TileEntityBridgeControllerSC c = bridgeBelow(w, x, y, z);
        if (c == null) {
            return false;
        }
        if (w.isRemote) {
            com.sc.SCMod.proxy.openBridge(c.xCoord, c.yCoord, c.zCoord);
        }
        return true;
    }

    /** A bridge controller whose ring this coil may be part of (under it, within the biggest ring's reach). */
    public static com.sc.tileentity.TileEntityBridgeControllerSC bridgeBelow(World w, int x, int y, int z) {
        for (int dy = 1; dy <= 8; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx != 0 && dz != 0) {
                        continue;                          // the ring stands along X or along Z through its controller
                    }
                    if (!w.blockExists(x + dx, y - dy, z + dz)) {
                        continue;
                    }
                    TileEntity te = w.getTileEntity(x + dx, y - dy, z + dz);
                    if (te instanceof com.sc.tileentity.TileEntityBridgeControllerSC) {
                        return (com.sc.tileentity.TileEntityBridgeControllerSC) te;
                    }
                }
            }
        }
        return null;
    }

    /** М-5: every bridge controller whose ring this coil may be part of (each checks itself whether it is one of its coils). */
    public static java.util.List<com.sc.tileentity.TileEntityBridgeControllerSC> bridgesBelow(World w, int x, int y, int z) {
        java.util.List<com.sc.tileentity.TileEntityBridgeControllerSC> l = new java.util.ArrayList<com.sc.tileentity.TileEntityBridgeControllerSC>();
        for (int dy = 1; dy <= 8; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx != 0 && dz != 0) {
                        continue;
                    }
                    if (!w.blockExists(x + dx, y - dy, z + dz)) {
                        continue;
                    }
                    TileEntity te = w.getTileEntity(x + dx, y - dy, z + dz);
                    if (te instanceof com.sc.tileentity.TileEntityBridgeControllerSC) {
                        l.add((com.sc.tileentity.TileEntityBridgeControllerSC) te);
                    }
                }
            }
        }
        return l;
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        if (!w.isRemote) {
            for (com.sc.tileentity.TileEntityBridgeControllerSC c : bridgesBelow(w, x, y, z)) {
                c.ringChanged(x, y, z);                    // М-5: all of them, not the first one found
            }
        }
        super.breakBlock(w, x, y, z, block, meta);
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        int m = w.getBlockMetadata(x, y, z);
        return m == 1 ? 9 : m == 2 ? 10 : m == 3 ? 12 : 0;
    }

    /** The bridge ring's heat (§11): cold blue, warm orange, hot red. */
    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess w, int x, int y, int z) {
        switch (w.getBlockMetadata(x, y, z)) {
            case 1: return 0xB4D2FF;
            case 2: return 0xFFB45A;
            case 3: return 0xFF5A40;
            default: return 0xFFFFFF;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(Reference.ASSETS + ":gravityCoil");
        lit = register.registerIcon(Reference.ASSETS + ":gravityCoilOn");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return meta >= 1 && meta <= 3 ? lit : blockIcon;
    }
}
