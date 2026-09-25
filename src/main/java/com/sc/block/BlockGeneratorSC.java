package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** All 7 generators (§15) on one block, one metadata per GeneratorType - well under the 16-value cap (unlike machines, §10/step 6). */
public class BlockGeneratorSC extends Block {

    private IIcon[] frontIcons;
    private IIcon[] casingIcons;

    public BlockGeneratorSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".generatorSC");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(8.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityGeneratorSC te = new TileEntityGeneratorSC();
        te.setGeneratorType(typeFor(meta));
        return te;
    }

    private static GeneratorType typeFor(int meta) {
        GeneratorType[] values = GeneratorType.values();
        return values[meta >= 0 && meta < values.length ? meta : 0];
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        GeneratorType[] types = GeneratorType.values();
        frontIcons = new IIcon[types.length];
        for (GeneratorType type : types) {
            frontIcons[type.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + type.frontTexture());
        }
        Tier[] tiers = Tier.values();
        casingIcons = new IIcon[tiers.length];
        for (Tier tier : tiers) {
            casingIcons[tier.ordinal()] = register.registerIcon(Reference.ASSETS + ":machineCasing" + tier.name());
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return iconFor(side, typeFor(meta), 3);
    }

    /** In the world the front is on the side the generator was turned to when placed. */
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        GeneratorType type = typeFor(world.getBlockMetadata(x, y, z));
        TileEntity te = world.getTileEntity(x, y, z);
        int front = te instanceof TileEntityGeneratorSC ? ((TileEntityGeneratorSC) te).getFacing().ordinal() : 3;
        return iconFor(side, type, front);
    }

    private IIcon iconFor(int side, GeneratorType type, int front) {
        // Solar panels face the sky: their cell texture goes on top, not on the front.
        int face = type.kind == GeneratorType.Kind.PASSIVE ? 1 : front;
        if (side == face) {
            return frontIcons != null ? frontIcons[type.ordinal()] : null;
        }
        return casingIcons != null ? casingIcons[type.tier.ordinal()] : null;
    }

    /** The front turns to the placer; the item's buffer, fuel and ignition come back (see getDrops). */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGeneratorSC)) {
            return;
        }
        TileEntityGeneratorSC generator = (TileEntityGeneratorSC) te;
        int quarter = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        ForgeDirection[] toPlacer = {ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST};
        generator.setFacing(toPlacer[quarter]);
        if (!world.isRemote && stack.hasTagCompound()) {
            generator.readFromItem(stack.getTagCompound());
        }
        world.markBlockForUpdate(x, y, z);
    }

    // ---- keep the buffer, fuel and ignition in the dropped item: drop while the tile entity still exists ----

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest) {
            return true;              // harvestBlock drops (reading the TE) and then removes the block
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this, 1, damageDropped(meta));
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityGeneratorSC) {
            NBTTagCompound nbt = ((TileEntityGeneratorSC) te).writeToItem();
            if (nbt != null) {
                stack.setTagCompound(nbt);
            }
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (GeneratorType type : GeneratorType.values()) {
            list.add(new ItemStack(item, 1, type.ordinal()));
        }
    }

    @Override
    public int quantityDropped(Random random) {
        return 1;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.GENERATOR_GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityGeneratorSC) {
            TileEntityGeneratorSC generator = (TileEntityGeneratorSC) te;
            for (int slot = 0; slot < generator.getSizeInventory(); slot++) {
                ItemStack stack = generator.getStackInSlot(slot);
                if (stack != null) {
                    world.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack));
                    generator.setInventorySlotContents(slot, null);    // no second copy for a GUI still open
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
