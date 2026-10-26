package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityDataPortSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md, «Редстоун для машин и реакторов», ★ A): «Порт данных» - a block in the wall of a reactor's
 * multiblock (the Tokamak XV's 7 x 7 x 3, the Singular Reactor's 7 x 7 x 5 - it counts as their lead wall): a comparator next to it reads
 * the reactor (TileEntityDataPortSC.comparatorLevel - the plasma's stability / the black hole's mass); a redstone pulse into it is an
 * emergency soft stop (TileEntityGeneratorSC.portStop); in PERMIT mode lighting is allowed only while it is powered - the lighting is
 * still started from the screen. One block, no metas.
 * <p>
 * Fixed (the coordinator's): the class, the registry name "dataPort" (IdeaBlocksSC), the tile. ОМ-5's: everything it does, the icons
 * (dataPort - the top and bottom, dataPort_side - the panel with the socket and the STOP / PERMIT lamps; tools/gen_dataport_tex.py),
 * the recipe (ModRecipesIdeasSC.om5), the tooltip (client.DataPortTooltipSC - the block has the default ItemBlock).
 */
public class BlockDataPortSC extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon side;

    public BlockDataPortSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".dataPort");
        setBlockTextureName(Reference.ASSETS + ":dataPort");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(30F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        if (cpw.mods.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            com.sc.client.DataPortTooltipSC.register();          // the tooltip (ОМ-5): the default ItemBlock has none
        }
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityDataPortSC();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(Reference.ASSETS + ":dataPort");
        side = register.registerIcon(Reference.ASSETS + ":dataPort_side");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        return s < 2 ? blockIcon : side;
    }

    /** ОМ-5: the empty hand or a wrench - STOP <-> PERMIT (a line says which, and that the port is in no reactor's wall). */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int s, float hx, float hy, float hz) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && !BlockConduitSC.isWrench(held)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityDataPortSC)) {
            return true;
        }
        TileEntityDataPortSC port = (TileEntityDataPortSC) te;
        int mode = port.toggleMode();
        player.addChatComponentMessage(new ChatComponentTranslation(mode == TileEntityDataPortSC.PERMIT ? "sc.dataport.mode.permit"
                : "sc.dataport.mode.stop").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
        if (port.boundAt() == null) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.dataport.unbound")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        }
        return true;
    }

    /** ОМ-5: a comparator reads the reactor through the port. */
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int s) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityDataPortSC ? ((TileEntityDataPortSC) te).comparatorLevel() : 0;
    }

    /** Redstone dust turns to the port (its pulse is the point). */
    @Override
    public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int s) {
        return s != -1;
    }
}
