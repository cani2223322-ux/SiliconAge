package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.energy.Tier;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityWirelessSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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
 * Wireless energy blocks: the transmitter and the receiver (damage = tier LV..XV) and the quantum
 * translator (XV only). The front turns to the placer; a wrench turns it to the clicked face
 * (the output of a receiver / a taking translator). The item keeps the energy and an A link.
 */
public class BlockWirelessSC extends Block {

    private final int kind;
    @SideOnly(Side.CLIENT)
    private IIcon[] fronts, sides;

    public BlockWirelessSC(int kind) {
        super(Material.iron);
        this.kind = kind;
        setBlockName(Reference.ASSETS + (kind == TileEntityWirelessSC.TRANSMITTER ? ".wirelessTx"
                : kind == TileEntityWirelessSC.RECEIVER ? ".wirelessRx" : ".quantumTranslator"));
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.5F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    public int getKind() {
        return kind;
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
        TileEntityWirelessSC te = new TileEntityWirelessSC();
        te.setup(kind, tierFor(meta));
        return te;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityWirelessSC)) {
            return;
        }
        TileEntityWirelessSC w = (TileEntityWirelessSC) te;
        int quarter = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        ForgeDirection[] faces = {ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST};
        w.setFacing(faces[quarter]);
        if (placer instanceof EntityPlayer) {
            w.setOwner(((EntityPlayer) placer).getCommandSenderName());
        }
        if (stack.hasTagCompound()) {
            w.readFromItem(stack.getTagCompound());
        }
        world.markBlockForUpdate(x, y, z);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityWirelessSC)) {
            return false;
        }
        if (BlockConduitSC.isWrench(player.getCurrentEquippedItem())) {
            if (!world.isRemote && ((TileEntityWirelessSC) te).allowed(player)) {
                ((TileEntityWirelessSC) te).setFacing(ForgeDirection.getOrientation(side));
            }
            return true;
        }
        if (player.getCurrentEquippedItem() != null && player.getCurrentEquippedItem().getItem() instanceof com.sc.item.ItemLinkCardSC) {
            return true;                       // the card links (server side), no screen
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.WIRELESS_GUI_ID, world, x, y, z);
        }
        return true;
    }

    // ---- the item keeps the energy and the link: drop while the tile entity still exists ----

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
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this, 1, damageDropped(meta));
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityWirelessSC) {
            NBTTagCompound nbt = ((TileEntityWirelessSC) te).writeToItem();
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
        if (te instanceof TileEntityWirelessSC) {
            TileEntityWirelessSC w = (TileEntityWirelessSC) te;
            for (int i = 0; i < w.getSizeInventory(); i++) {
                ItemStack in = w.getStackInSlot(i);
                if (in != null) {
                    world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, in));
                    w.setInventorySlotContents(i, null);
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public int damageDropped(int meta) {
        return kind == TileEntityWirelessSC.QUANTUM ? 0 : meta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        if (kind == TileEntityWirelessSC.QUANTUM) {
            list.add(new ItemStack(item, 1, 0));
            return;
        }
        for (Tier tier : Tier.values()) {
            list.add(new ItemStack(item, 1, tier.ordinal()));
        }
    }

    // ---- textures ----

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        Tier[] tiers = Tier.values();
        fronts = new IIcon[tiers.length];
        sides = new IIcon[tiers.length];
        for (Tier t : tiers) {
            if (kind == TileEntityWirelessSC.QUANTUM) {
                fronts[t.ordinal()] = register.registerIcon(Reference.ASSETS + ":quantumTranslatorFront");
                sides[t.ordinal()] = register.registerIcon(Reference.ASSETS + ":quantumTranslatorSide");
            } else {
                fronts[t.ordinal()] = register.registerIcon(Reference.ASSETS + ":wireless"
                        + (kind == TileEntityWirelessSC.TRANSMITTER ? "Tx" : "Rx") + t.name() + "Front");
                sides[t.ordinal()] = register.registerIcon(Reference.ASSETS + ":wireless" + t.name() + "Side");
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        int t = kind == TileEntityWirelessSC.QUANTUM ? 0 : tierFor(meta).ordinal();
        return side == 3 ? fronts[t] : sides[t];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        int t = kind == TileEntityWirelessSC.QUANTUM ? 0 : tierFor(meta).ordinal();
        if (te instanceof TileEntityWirelessSC) {
            return side == ((TileEntityWirelessSC) te).getFacing().ordinal() ? fronts[t] : sides[t];
        }
        return getIcon(side, meta);
    }
}
