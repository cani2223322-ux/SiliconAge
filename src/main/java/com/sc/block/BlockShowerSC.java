package com.sc.block;

import java.util.Random;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityShowerSC;
import com.sc.util.FluidHandSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * The decontamination shower: stand on its grate. A bucket of water pours in; otherwise the screen.
 * While it washes, water sprays up round the player.
 */
public class BlockShowerSC extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon top, side, bottom;

    public BlockShowerSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".shower");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityShowerSC();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int s, float hx, float hy, float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityShowerSC)) {
            return false;
        }
        ItemStack held = player.getCurrentEquippedItem();
        if (FluidHandSC.isContainer(held)) {
            if (!world.isRemote) {
                FluidHandSC.use(world, x, y, z, player, (TileEntityShowerSC) te, held);
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.SHOWER_GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileEntityShowerSC && stack.hasTagCompound()) {
            ((TileEntityShowerSC) te).readFromItem(stack.getTagCompound());
        }
    }

    // ---- the item keeps the energy and the water: drop while the tile entity still exists ----

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
        if (te instanceof TileEntityShowerSC) {
            NBTTagCompound nbt = ((TileEntityShowerSC) te).writeToItem();
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
        if (te instanceof TileEntityShowerSC) {
            ItemStack in = ((TileEntityShowerSC) te).getStackInSlot(TileEntityShowerSC.SLOT_BATTERY);
            if (in != null) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, in));
                ((TileEntityShowerSC) te).setInventorySlotContents(TileEntityShowerSC.SLOT_BATTERY, null);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityShowerSC) || !((TileEntityShowerSC) te).isWashing()) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double px = x + 0.1 + rand.nextDouble() * 0.8, pz = z + 0.1 + rand.nextDouble() * 0.8;
            world.spawnParticle("splash", px, y + 1.05 + rand.nextDouble() * 1.6, pz, 0, 0.2, 0);
        }
        for (int i = 0; i < 3; i++) {
            world.spawnParticle("bubble", x + 0.2 + rand.nextDouble() * 0.6, y + 1.02, z + 0.2 + rand.nextDouble() * 0.6, 0, 0.05, 0);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        top = register.registerIcon(Reference.ASSETS + ":showerTop");
        side = register.registerIcon(Reference.ASSETS + ":showerSide");
        bottom = register.registerIcon(Reference.ASSETS + ":showerBottom");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return face == 1 ? top : face == 0 ? bottom : side;
    }
}
