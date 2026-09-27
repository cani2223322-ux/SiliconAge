package com.sc.block;

import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.machine.MachineType;
import com.sc.tileentity.TileEntityMachineSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Every machine (§13 + §14) on metadata-driven blocks - same pattern as BlockOreSC/
 * BlockCableSC/BlockPipeSC, EXCEPT machines don't fit on a single block: MachineType now has
 * 29 values, and 1.7.10 block metadata is a 4-bit nibble (max 16, 0-15) - the exact same wall
 * BlockOreSC hit in §10. So this class takes a `typeOffset` (0 or 16) and covers at most 16
 * consecutive MachineType ordinals per instance; ModBlocks registers two instances
 * (machineSC = ordinals 0-15, machineSC2 = ordinals 16-28) to cover all of them.
 *
 * The front texture shows on the machine's facing (TileEntityMachineSC.getFacing): it turns to the
 * player on placement, a wrench turns it (click: a quarter turn; sneak + click: to the clicked
 * side), and so do other mods' wrenches through rotateBlock. Every other side shows the tier's
 * casing. Only a look - all sides take ingredients and give products alike. In the inventory
 * (no tile) the front is side 3, as before.
 */
public class BlockMachineSC extends Block {

    private final int typeOffset;
    private final int typeCount;
    private IIcon[] frontIcons;
    private IIcon[] casingIcons;

    public BlockMachineSC(int typeOffset) {
        super(Material.iron);
        this.typeOffset = typeOffset;
        this.typeCount = Math.min(16, MachineType.values().length - typeOffset);
        setBlockName(Reference.ASSETS + ".machineSC" + (typeOffset == 0 ? "" : String.valueOf(typeOffset / 16 + 1)));
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(8.0F);
        setStepSound(soundTypeMetal);
    }

    public int getTypeOffset() {
        return typeOffset;
    }

    public int getTypeCount() {
        return typeCount;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityMachineSC te = new TileEntityMachineSC();
        te.setMachineType(typeFor(meta));
        return te;
    }

    private MachineType typeFor(int meta) {
        MachineType[] values = MachineType.values();
        int index = typeOffset + (meta >= 0 && meta < typeCount ? meta : 0);
        return values[Math.min(index, values.length - 1)];
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        frontIcons = new IIcon[typeCount];
        for (int i = 0; i < typeCount; i++) {
            MachineType type = MachineType.values()[typeOffset + i];
            frontIcons[i] = register.registerIcon(Reference.ASSETS + ":" + type.frontTexture());
        }
        com.sc.energy.Tier[] tiers = com.sc.energy.Tier.values();
        casingIcons = new IIcon[tiers.length];
        for (com.sc.energy.Tier tier : tiers) {
            casingIcons[tier.ordinal()] = register.registerIcon(Reference.ASSETS + ":machineCasing" + tier.name());
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        MachineType type = typeFor(meta);
        if (side == 3) { // south - the fixed "front" (§13.3), see TileEntityMachineSC javadoc
            return frontIcons != null ? frontIcons[type.ordinal() - typeOffset] : null;
        }
        return casingIcons != null ? casingIcons[type.tier.ordinal()] : null;
    }

    @Override
    public IIcon getIcon(net.minecraft.world.IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if (te instanceof TileEntityMachineSC) {
            int front = ((TileEntityMachineSC) te).getFacing().ordinal();
            if (side == front) {
                return getIcon(3, meta);
            }
            return getIcon(side == 3 ? 2 : side, meta);     // the casing everywhere else, south included
        }
        return getIcon(side, meta);
    }

    /** With a universal transformer upgrade inside, no blast breaks the machine. */
    @Override
    public float getExplosionResistance(net.minecraft.entity.Entity exploder, World world, int x, int y, int z,
                                        double explosionX, double explosionY, double explosionZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityMachineSC
                && ((TileEntityMachineSC) te).upgradeCount(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER) > 0) {
            return 6000000.0F;
        }
        return super.getExplosionResistance(exploder, world, x, y, z, explosionX, explosionY, explosionZ);
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < typeCount; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public int quantityDropped(Random random) {
        return 1;
    }

    // ---- item <-> block: the tank contents travel along (like "EnergySC" on energy storage) ----

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, net.minecraft.entity.EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityMachineSC) {
            // the front turns to the placer, like a generator's or a furnace's
            int quarter = net.minecraft.util.MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            net.minecraftforge.common.util.ForgeDirection[] toPlacer = {net.minecraftforge.common.util.ForgeDirection.NORTH,
                    net.minecraftforge.common.util.ForgeDirection.EAST, net.minecraftforge.common.util.ForgeDirection.SOUTH,
                    net.minecraftforge.common.util.ForgeDirection.WEST};
            ((TileEntityMachineSC) te).setFacing(toPlacer[quarter]);
            world.markBlockForUpdate(x, y, z);
        }
        // placed switched off: nothing can overvolt it before its upgrades are in and it's switched on
        if (te instanceof TileEntityMachineSC) {
            ((TileEntityMachineSC) te).setPowerOn(false);
            if (stack.hasTagCompound()) {
                ((TileEntityMachineSC) te).setRedstoneMode(stack.getTagCompound().getInteger(TileEntityMachineSC.ITEM_REDSTONE_KEY));
            }
        }
        // the upgrades first: a transformer must be in before the machine's first energy tick
        if (te instanceof TileEntityMachineSC && stack.hasTagCompound()
                && stack.getTagCompound().hasKey(TileEntityMachineSC.ITEM_UPGRADES_KEY)) {
            ((TileEntityMachineSC) te).loadUpgradesFromItem(stack.getTagCompound().getCompoundTag(TileEntityMachineSC.ITEM_UPGRADES_KEY));
            world.markBlockForUpdate(x, y, z);
        }
        if (te instanceof TileEntityMachineSC && stack.hasTagCompound()
                && stack.getTagCompound().hasKey(TileEntityMachineSC.ITEM_TANKS_KEY)) {
            ((TileEntityMachineSC) te).loadTanksFromItem(stack.getTagCompound().getCompoundTag(TileEntityMachineSC.ITEM_TANKS_KEY));
            world.markBlockForUpdate(x, y, z);
        }
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest) {
            return true;              // harvestBlock drops (reading the tile) and then removes the block
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    /**
     * An explosion drops the block whole: its upgrades and charge ride in the item, and the
     * explosion's usual 1-in-size chance would have lost them with it.
     */
    @Override
    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int meta, float chance, int fortune) {
        super.dropBlockAsItemWithChance(world, x, y, z, meta, 1.0F, fortune);
    }

    @Override
    public java.util.ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this, 1, damageDropped(meta));
        TileEntity te = world.getTileEntity(x, y, z);
        net.minecraft.nbt.NBTTagCompound tanks = te instanceof TileEntityMachineSC ? ((TileEntityMachineSC) te).tanksForItem() : null;
        net.minecraft.nbt.NBTTagCompound ups = te instanceof TileEntityMachineSC ? ((TileEntityMachineSC) te).upgradesForItem() : null;
        int redstone = te instanceof TileEntityMachineSC ? ((TileEntityMachineSC) te).getRedstoneMode() : 0;
        if (tanks != null || ups != null || redstone != 0) {
            net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
            if (redstone != 0) {
                nbt.setInteger(TileEntityMachineSC.ITEM_REDSTONE_KEY, redstone);
            }
            if (tanks != null) {
                nbt.setTag(TileEntityMachineSC.ITEM_TANKS_KEY, tanks);
            }
            if (ups != null) {
                nbt.setTag(TileEntityMachineSC.ITEM_UPGRADES_KEY, ups);
            }
            stack.setTagCompound(nbt);
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
        return new ItemStack(this, 1, world.getBlockMetadata(x, y, z));
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (com.sc.block.BlockConduitSC.isWrench(player.getCurrentEquippedItem())) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (!world.isRemote && te instanceof TileEntityMachineSC) {
                TileEntityMachineSC machine = (TileEntityMachineSC) te;
                net.minecraftforge.common.util.ForgeDirection clicked = net.minecraftforge.common.util.ForgeDirection.getOrientation(side);
                machine.setFacing(player.isSneaking() && clicked.offsetY == 0 ? clicked
                        : machine.getFacing().getRotation(net.minecraftforge.common.util.ForgeDirection.UP));
            }
            return true;
        }
        if (com.sc.util.FluidHandSC.isContainer(player.getCurrentEquippedItem())) {    // a bucket / cell: pour in or take out
            TileEntity te = world.getTileEntity(x, y, z);
            if (!world.isRemote && te instanceof net.minecraftforge.fluids.IFluidHandler) {
                com.sc.util.FluidHandSC.use(world, x, y, z, player, (net.minecraftforge.fluids.IFluidHandler) te, player.getCurrentEquippedItem());
            }
            return true;
        }
        if (player.isSneaking() && player.getCurrentEquippedItem() == null) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (!world.isRemote && te instanceof TileEntityMachineSC) {
                int vented = ((TileEntityMachineSC) te).ventInputTanks();
                player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.chat.machine.vented", vented));
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.MACHINE_GUI_ID, world, x, y, z);
        }
        return true;
    }

    /** Other mods' wrenches (BuildCraft, Thermal, Ender IO...): a quarter turn round the vertical axis. */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, net.minecraftforge.common.util.ForgeDirection axis) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityMachineSC)) {
            return false;
        }
        TileEntityMachineSC machine = (TileEntityMachineSC) te;
        machine.setFacing(machine.getFacing().getRotation(net.minecraftforge.common.util.ForgeDirection.UP));
        return true;
    }

    @Override
    public net.minecraftforge.common.util.ForgeDirection[] getValidRotations(World world, int x, int y, int z) {
        return new net.minecraftforge.common.util.ForgeDirection[]{net.minecraftforge.common.util.ForgeDirection.UP,
                net.minecraftforge.common.util.ForgeDirection.DOWN};
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityMachineSC) {
            TileEntityMachineSC machine = (TileEntityMachineSC) te;
            for (int i = 0; i < machine.getSizeInventory(); i++) {
                if (machine.upgradesInItem() && i >= TileEntityMachineSC.FIRST_UPGRADE_SLOT
                        && i < TileEntityMachineSC.FIRST_UPGRADE_SLOT + TileEntityMachineSC.UPGRADE_SLOTS) {
                    continue;                                        // they left inside the machine's item
                }
                ItemStack stack = machine.getStackInSlot(i);
                if (stack != null) {
                    float x0 = x + 0.5F, y0 = y + 0.5F, z0 = z + 0.5F;
                    net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(world, x0, y0, z0, stack);
                    world.spawnEntityInWorld(entityItem);
                    machine.setInventorySlotContents(i, null);      // no second copy for a GUI still open
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
