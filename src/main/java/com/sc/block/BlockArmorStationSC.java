package com.sc.block;

import java.util.Random;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityArmorStationSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The Armour Service Station (MV): four armour slots, gases from the tanks beside it, EU from the
 * line; stand on it and the worn suit is filled and charged. The front (its screen) turns to the
 * placer; the top lights up while it works. Broken only with a pickaxe; the item keeps the energy,
 * the settings and the modules, the pieces in the slots drop.
 */
public class BlockArmorStationSC extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon top, topOn, front, side, bottom;

    public BlockArmorStationSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".armorStation");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z) {
        return PickaxeOnlySC.hardness(super.getPlayerRelativeBlockHardness(player, world, x, y, z), player);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityArmorStationSC();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int s, float hx, float hy, float hz) {
        if (!(world.getTileEntity(x, y, z) instanceof TileEntityArmorStationSC)) {
            return false;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.ARMOR_STATION_GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityArmorStationSC)) {
            return;
        }
        TileEntityArmorStationSC st = (TileEntityArmorStationSC) te;
        // the front turns to the placer, like a machine's
        int quarter = net.minecraft.util.MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        ForgeDirection[] toPlacer = {ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST};
        st.setFacing(toPlacer[quarter]);
        if (!world.isRemote && stack.hasTagCompound()) {
            st.readFromItem(stack.getTagCompound());
        }
        st.setPowerOn(false);                             // placed off, as a machine: on once the line's checked
        world.markBlockForUpdate(x, y, z);
    }

    // ---- the item keeps the energy and the settings: drop while the tile entity still exists ----

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest) {
            return true;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int meta, float chance, int fortune) {
        super.dropBlockAsItemWithChance(world, x, y, z, meta, 1.0F, fortune);
    }

    @Override
    public java.util.ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityArmorStationSC) {
            NBTTagCompound nbt = ((TileEntityArmorStationSC) te).writeToItem();
            if (!nbt.hasNoTags()) {
                stack.setTagCompound(nbt);
            }
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityArmorStationSC) {
            // the armour pieces drop; the modules too - unless getDrops just put them into the item
            for (ItemStack in : ((TileEntityArmorStationSC) te).takeLooseContents()) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, in));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityArmorStationSC && ((TileEntityArmorStationSC) te).isActive()) {
            for (int i = 0; i < 2; i++) {
                world.spawnParticle("cloud", x + 0.2 + rand.nextDouble() * 0.6, y + 1.02, z + 0.2 + rand.nextDouble() * 0.6, 0, 0.02, 0);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        top = register.registerIcon(Reference.ASSETS + ":armorStationTop");
        topOn = register.registerIcon(Reference.ASSETS + ":armorStationTopOn");
        front = register.registerIcon(Reference.ASSETS + ":armorStationFront");
        side = register.registerIcon(Reference.ASSETS + ":armorStationSide");
        bottom = register.registerIcon(Reference.ASSETS + ":armorStationBottom");
    }

    /** The item: the front faces south (3), the way a machine's item shows it. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return face == 1 ? top : face == 0 ? bottom : face == 3 ? front : side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int face) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityArmorStationSC)) {
            return getIcon(face, 0);
        }
        TileEntityArmorStationSC st = (TileEntityArmorStationSC) te;
        if (face == 1) {
            return st.isActive() ? topOn : top;
        }
        if (face == 0) {
            return bottom;
        }
        return face == st.getFacing().ordinal() ? front : side;
    }
}
