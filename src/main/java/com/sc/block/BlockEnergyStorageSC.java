package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.energy.Tier;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
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

/**
 * Energy storage LV / MV / HV / EV (metadata = Tier ordinal). The front (output) face turns to
 * the player on placement; the item keeps the stored charge ("EnergySC" in its NBT) when the
 * block is broken and restores it when placed again.
 */
public class BlockEnergyStorageSC extends Block {

    protected IIcon[] frontIcons;
    protected IIcon[] sideIcons;

    public BlockEnergyStorageSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".energyStorage");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    public static Tier tierFor(int meta) {
        Tier[] tiers = Tier.values();
        return tiers[meta >= 0 && meta < tiers.length ? meta : 0];
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityEnergyStorageSC te = new TileEntityEnergyStorageSC();
        te.setStorageTier(tierFor(meta));
        return te;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyStorageSC)) {
            return;
        }
        TileEntityEnergyStorageSC storage = (TileEntityEnergyStorageSC) te;
        storage.setFacing(facingToward(placer));
        storage.setPowerOn(false);                             // placed off, as a machine: on once the line's checked
        if (stack.hasTagCompound()) {
            if (stack.getTagCompound().hasKey("UpgradesSC")) {     // first: a capacity upgrade makes room for the charge
                storage.loadUpgradesFromItem(stack.getTagCompound().getCompoundTag("UpgradesSC"));
            }
            storage.setStoredFromItem(stack.getTagCompound().getInteger("EnergySC"));
        }
        world.markBlockForUpdate(x, y, z);
        if (!(this instanceof BlockChargePadSC)) {            // the pad warns after turning its face
            com.sc.energy.CableWarningSC.sourcePlaced(world, x, y, z, placer);
        }
    }

    /** The horizontal face (or top/bottom when looking steeply) that points back at the placer. */
    private static ForgeDirection facingToward(EntityLivingBase placer) {
        if (placer.rotationPitch > 60) {
            return ForgeDirection.UP;
        }
        if (placer.rotationPitch < -60) {
            return ForgeDirection.DOWN;
        }
        int quarter = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        switch (quarter) {
            case 0: return ForgeDirection.NORTH;
            case 1: return ForgeDirection.EAST;
            case 2: return ForgeDirection.SOUTH;
            default: return ForgeDirection.WEST;
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (com.sc.block.BlockConduitSC.isWrench(player.getCurrentEquippedItem())) {      // a wrench: the output to the clicked face
            TileEntity te = world.getTileEntity(x, y, z);
            if (!world.isRemote && te instanceof TileEntityEnergyStorageSC) {
                ((TileEntityEnergyStorageSC) te).setFacing(ForgeDirection.getOrientation(side));
                te.markDirty();
                world.markBlockForUpdate(x, y, z);
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.ENERGY_STORAGE_GUI_ID, world, x, y, z);
        }
        return true;
    }

    /** Other mods' wrenches: the output a quarter turn round the vertical axis. */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, ForgeDirection axis) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyStorageSC)) {
            return false;
        }
        TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
        ForgeDirection f = s.getFacing();
        s.setFacing(f.offsetY != 0 ? ForgeDirection.NORTH : f.getRotation(ForgeDirection.UP));
        te.markDirty();
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    /** A comparator reads how full it is (0 empty .. 15 full). */
    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityEnergyStorageSC ? ((TileEntityEnergyStorageSC) te).comparatorLevel() : 0;
    }

    // ---- keep the charge in the dropped item: drop while the tile entity still exists ----

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
        ItemStack stack = new ItemStack(this, 1, meta);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyStorageSC) {
            TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
            NBTTagCompound nbt = new NBTTagCompound();
            if (s.getEnergyStored() > 0) {
                nbt.setInteger("EnergySC", s.getEnergyStored());
            }
            NBTTagCompound ups = s.upgradesForItem();        // the upgrades go with the block, as a machine's
            if (ups != null) {
                nbt.setTag("UpgradesSC", ups);
            }
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
        if (te instanceof TileEntityEnergyStorageSC) {
            TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) te;
            int last = s.upgradesInItem() ? TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT : TileEntityEnergyStorageSC.SLOT_COUNT;
            for (int i = 0; i < last; i++) {
                ItemStack in = s.getStackInSlot(i);
                if (in != null) {
                    world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, in));
                    s.setInventorySlotContents(i, null);   // no second copy for a GUI still open
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (Tier tier : Tier.values()) {
            list.add(new ItemStack(item, 1, tier.ordinal()));
        }
    }

    // ---- textures: tier-coloured casing, a terminal on the output face ----

    @Override
    public void registerBlockIcons(IIconRegister register) {
        Tier[] tiers = Tier.values();
        frontIcons = new IIcon[tiers.length];
        sideIcons = new IIcon[tiers.length];
        for (Tier tier : tiers) {
            frontIcons[tier.ordinal()] = register.registerIcon(Reference.ASSETS + ":energyStorage" + tier.name() + "Front");
            sideIcons[tier.ordinal()] = register.registerIcon(Reference.ASSETS + ":energyStorage" + tier.name() + "Side");
        }
    }

    /** Inventory / item form: the output terminal on the south face, like a machine's front. */
    @Override
    public IIcon getIcon(int side, int meta) {
        int t = tierFor(meta).ordinal();
        if (frontIcons == null) {
            return null;
        }
        return side == 3 ? frontIcons[t] : sideIcons[t];
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyStorageSC && frontIcons != null) {
            int t = tierFor(meta).ordinal();
            return side == ((TileEntityEnergyStorageSC) te).getFacing().ordinal() ? frontIcons[t] : sideIcons[t];
        }
        return getIcon(side, meta);
    }
}
