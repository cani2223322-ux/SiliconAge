package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.energy.ForeignEnergySC;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityEnergyConverterSC;

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
 * The Energy Converter (TileEntityEnergyConverterSC). Always registered; with no other energy in the
 * game (no RF API, no Mekanism, no Galacticraft) it has no recipe, no creative / NEI entry, and one
 * already in a world works as an EU buffer. Placed switched off; broken or dismantled, the item keeps
 * both buffers, the settings and the modules. The front (two windows: EU and the pair's energy in its
 * colour) turns to the placer; the wrench turns it to the clicked face.
 */
public class BlockEnergyConverterSC extends Block {

    private IIcon side, top;
    /** The front: no pair, RF, J, gJ. */
    private final IIcon[] fronts = new IIcon[4];

    public BlockEnergyConverterSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".energyConverter");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z) {
        if (refusesBreak(world, player, x, y, z)) {
            BlockQuarrySC.tellRefused(player, "sc.chat.break.owneronly", ((TileEntityEnergyConverterSC) world.getTileEntity(x, y, z)).getOwner());
            return 0F;
        }
        return PickaxeOnlySC.hardness(super.getPlayerRelativeBlockHardness(player, world, x, y, z), player);
    }

    /**
     * БП-3: a converter with an owner is broken or dismantled by the owner (anyone in creative) and
     * server ops only, as BlockWirelessSC. Server side (false on the client).
     */
    public static boolean refusesBreak(World world, EntityPlayer player, int x, int y, int z) {
        if (world == null || world.isRemote || player == null) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityEnergyConverterSC && !((TileEntityEnergyConverterSC) te).getOwner().isEmpty()
                && !((TileEntityEnergyConverterSC) te).allowed(player) && !BlockQuarrySC.isOp(player);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return TileEntityEnergyConverterSC.create();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyConverterSC)) {
            return;
        }
        TileEntityEnergyConverterSC c = (TileEntityEnergyConverterSC) te;
        c.setFacing(facingToward(placer));
        c.setPowerOn(false);                                   // placed off, as every energy block
        if (stack.hasTagCompound()) {
            c.readFromItem(stack.getTagCompound());
        }
        if (placer instanceof EntityPlayer) {
            c.setOwner(((EntityPlayer) placer).getCommandSenderName());   // БП-3: the placer owns it
        }
        world.markBlockForUpdate(x, y, z);
    }

    private static ForgeDirection facingToward(EntityLivingBase placer) {
        if (placer.rotationPitch > 60) {
            return ForgeDirection.UP;
        }
        if (placer.rotationPitch < -60) {
            return ForgeDirection.DOWN;
        }
        switch (MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3) {
            case 0: return ForgeDirection.NORTH;
            case 1: return ForgeDirection.EAST;
            case 2: return ForgeDirection.SOUTH;
            default: return ForgeDirection.WEST;
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (BlockConduitSC.isWrench(player.getCurrentEquippedItem())) {         // a wrench: the front to the clicked face
            if (!world.isRemote && te instanceof TileEntityEnergyConverterSC && ((TileEntityEnergyConverterSC) te).allowed(player)) {
                ((TileEntityEnergyConverterSC) te).setFacing(ForgeDirection.getOrientation(side));
                world.markBlockForUpdate(x, y, z);
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.ENERGY_CONVERTER_GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileEntityEnergyConverterSC) {
            ((TileEntityEnergyConverterSC) te).neighbourChanged();
        }
    }

    @Override
    public void onNeighborChange(IBlockAccess world, int x, int y, int z, int tileX, int tileY, int tileZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyConverterSC) {
            ((TileEntityEnergyConverterSC) te).neighbourChanged();
        }
    }

    @Override
    public boolean rotateBlock(World world, int x, int y, int z, ForgeDirection axis) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyConverterSC)) {
            return false;
        }
        TileEntityEnergyConverterSC c = (TileEntityEnergyConverterSC) te;
        if (!c.getOwner().isEmpty()) {
            return false;                              // БП-3: no player here - an owned one is turned by its owner's wrench only
        }
        ForgeDirection f = c.getFacing();
        c.setFacing(f.offsetY != 0 ? ForgeDirection.NORTH : f.getRotation(ForgeDirection.UP));
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityEnergyConverterSC ? ((TileEntityEnergyConverterSC) te).comparatorLevel() : 0;
    }

    // ---- the item keeps the buffers, the settings and the modules: drop while the tile still exists ----

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (refusesBreak(world, player, x, y, z)) {
            BlockQuarrySC.tellRefused(player, "sc.chat.break.owneronly", ((TileEntityEnergyConverterSC) world.getTileEntity(x, y, z)).getOwner());
            world.markBlockForUpdate(x, y, z);
            return false;
        }
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
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyConverterSC) {
            NBTTagCompound nbt = ((TileEntityEnergyConverterSC) te).writeToItem();
            if (!nbt.hasNoTags()) {                    // БП-5: a blank one stacks with new ones
                stack.setTagCompound(nbt);
            }
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyConverterSC) {
            TileEntityEnergyConverterSC c = (TileEntityEnergyConverterSC) te;
            boolean inItem = c.modulesInItem();
            for (int i = 0; i < TileEntityEnergyConverterSC.SLOT_COUNT; i++) {
                ItemStack in = c.getStackInSlot(i);
                if (in != null && !(inItem && i >= TileEntityEnergyConverterSC.FIRST_MODULE)) {
                    world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, in));
                }
            }
            // emptied without the module rules (they'd refuse a module holding energy): no copy for a screen still open
            c.clearForBreak();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        if (ForeignEnergySC.anyPresent()) {
            list.add(new ItemStack(item, 1, 0));
        }
    }

    // ---- textures ----

    @Override
    public void registerBlockIcons(IIconRegister register) {
        side = register.registerIcon(Reference.ASSETS + ":energyConverterSide");
        top = register.registerIcon(Reference.ASSETS + ":energyConverterTop");
        String[] f = {"", "RF", "J", "GJ"};
        for (int i = 0; i < 4; i++) {
            fronts[i] = register.registerIcon(Reference.ASSETS + ":energyConverterFront" + f[i]);
        }
    }

    /** The item: the front (RF windows) to the south, like a machine's. */
    @Override
    public IIcon getIcon(int s, int meta) {
        if (s == 3) {
            return fronts[ForeignEnergySC.rfApi() ? 1 : 0];
        }
        return s < 2 ? top : side;
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int s) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyConverterSC) {
            TileEntityEnergyConverterSC c = (TileEntityEnergyConverterSC) te;
            if (c.getFacing().ordinal() == s) {
                ForeignEnergySC.Kind k = c.pairKind();
                return fronts[k == null ? 0 : k.ordinal() + 1];
            }
            return s < 2 && c.getFacing().offsetY == 0 ? top : side;
        }
        return getIcon(s, 0);
    }
}
