package com.sc.block;

import java.util.Random;

import com.sc.Reference;
import com.sc.handler.GuiHandlerSC;
import com.sc.tileentity.TileEntitySingularStationSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The Singular Service Station (SV): the Armour Service Station's block with its own tile entity,
 * screen and look - a dark pedestal (its rings, hologram and beams are SingularStationRendererSC's).
 * Breaking it (by hand, the wrench, an explosion) cancels a running process first: half of what it
 * drew goes back into the station, which the item then keeps.
 */
public class BlockSingularStationSC extends BlockArmorStationSC {

    @SideOnly(Side.CLIENT)
    private IIcon top, topOn, front, side, back, bottom;

    public BlockSingularStationSC() {
        super();
        setBlockName(Reference.ASSETS + ".singularStation");
        setHardness(5.0F);
        setResistance(20.0F);
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntitySingularStationSC();
    }

    @Override
    protected int guiId() {
        return GuiHandlerSC.SINGULAR_STATION_GUI_ID;
    }

    private static void cancel(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySingularStationSC && !world.isRemote) {
            ((TileEntitySingularStationSC) te).cancelProcess();
        }
    }

    @Override
    public java.util.ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        cancel(world, x, y, z);                            // the 50% back into the tanks / buffer before the item takes them
        return super.getDrops(world, x, y, z, meta, fortune);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        cancel(world, x, y, z);
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntitySingularStationSC)) {
            return;
        }
        TileEntitySingularStationSC st = (TileEntitySingularStationSC) te;
        if (st.isWorking()) {                              // particles drawn in toward the rings (portal particles fly to their spawn point)
            for (int i = 0; i < 6; i++) {
                double a = rand.nextDouble() * Math.PI * 2, r = 1.2 + rand.nextDouble() * 1.3;
                world.spawnParticle("portal", x + 0.5, y + 1.6, z + 0.5, Math.cos(a) * r, (rand.nextDouble() - 0.4) * 1.2, Math.sin(a) * r);
            }
            if (rand.nextInt(3) == 0) {
                world.spawnParticle("witchMagic", x + 0.5 + (rand.nextDouble() - 0.5) * 0.6, y + 1.3, z + 0.5 + (rand.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
            }
        } else if (st.isActive()) {
            world.spawnParticle("portal", x + 0.5, y + 1.1, z + 0.5, (rand.nextDouble() - 0.5) * 0.8, 0.2, (rand.nextDouble() - 0.5) * 0.8);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        top = register.registerIcon(Reference.ASSETS + ":singularStationTop");
        topOn = register.registerIcon(Reference.ASSETS + ":singularStationTopOn");
        front = register.registerIcon(Reference.ASSETS + ":singularStationFront");
        side = register.registerIcon(Reference.ASSETS + ":singularStationSide");
        back = register.registerIcon(Reference.ASSETS + ":singularStationBack");
        bottom = register.registerIcon(Reference.ASSETS + ":singularStationBottom");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return face == 1 ? top : face == 0 ? bottom : face == 3 ? front : face == 2 ? back : side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int face) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntitySingularStationSC)) {
            return getIcon(face, 0);
        }
        TileEntitySingularStationSC st = (TileEntitySingularStationSC) te;
        if (face == 1) {
            return st.isActive() || st.isWorking() ? topOn : top;
        }
        if (face == 0) {
            return bottom;
        }
        return face == st.getFacing().ordinal() ? front : face == st.getFacing().getOpposite().ordinal() ? back : side;
    }
}
