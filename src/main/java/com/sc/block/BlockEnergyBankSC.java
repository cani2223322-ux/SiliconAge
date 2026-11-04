package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityEnergyBankPortSC;
import com.sc.tileentity.TileEntityEnergyBankSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md, «Сингулярный энергобанк», ★ A): the Energy Bank's blocks on one block id. Metas (fixed, append
 * only): CASING «Корпус энергобанка», GLASS «Стекло энергобанка», CONTROLLER «Контроллер энергобанка» (the bank's tile, its screen -
 * GuiHandlerSC.ENERGY_BANK_GUI_ID), CELL_4 / CELL_16 «Ячейка энергобанка 4 / 16 млрд EU» (inside the cube), PORT_FIRST + Tier ordinal
 * «Порт энергобанка LV..SV» (each with TileEntityEnergyBankPortSC: the net's in / out). EnergyBankMathSC has the numbers.
 * <p>
 * Fixed (the coordinator's): the class, the registry name "energyBank" (IdeaBlocksSC), the metas, the tiles, the controller's screen id.
 * ОМ-7's: the icons (energyBank_<name>; tools/gen_energybank_tex.py), the glass (a cut-out texture: not an opaque cube, no light kept
 * by the glass meta, the faces between bank blocks not drawn), the port's wrench click, the controller's drop with its charge (NBT
 * TileEntityEnergyBankSC.NBT {eu}), the comparator at the controller.
 */
public class BlockEnergyBankSC extends Block {

    public static final int CASING = 0, GLASS = 1, CONTROLLER = 2, CELL_4 = 3, CELL_16 = 4, PORT_FIRST = 5;
    /** The ports: PORT_FIRST + Tier.ordinal() (LV..SV, 8 of them). */
    public static final int PORTS = com.sc.energy.Tier.values().length, METAS = PORT_FIRST + PORTS;
    public static final String[] NAMES = {"casing", "glass", "controller", "cell4", "cell16", "portLV", "portMV", "portHV", "portEV", "portIV",
            "portQV", "portXV", "portSV"};

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockEnergyBankSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".energyBank");
        setBlockTextureName(Reference.ASSETS + ":energyBank");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(30F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setLightOpacity(255);                                   // the glass meta lets light through (getLightOpacity by place)
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    /** The meta is a port (PORT_FIRST..METAS-1). */
    public static boolean isPort(int meta) {
        return meta >= PORT_FIRST && meta < METAS;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return meta == CONTROLLER || isPort(meta);
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        if (meta == CONTROLLER) {
            return new TileEntityEnergyBankSC();
        }
        return isPort(meta) ? new TileEntityEnergyBankPortSC(com.sc.energy.Tier.byOrdinal(meta - PORT_FIRST)) : null;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < METAS; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }

    // ------------------------------------------------------------------ the looks

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[METAS];
        for (int m = 0; m < METAS; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":energyBank_" + NAMES[m]);
        }
        blockIcon = icons[CASING];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[meta >= 0 && meta < METAS ? meta : CASING];
    }

    /** The glass is cut out (its texture's clear pixels): the block as a whole isn't an opaque cube. */
    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public int getLightOpacity(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == GLASS ? 0 : 255;
    }

    /** (x, y, z) is the neighbour: between two bank blocks a face is drawn only where a solid one shows through the glass. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        Block other = world.getBlock(x, y, z);
        if (other == this) {
            int selfMeta = world.getBlockMetadata(x - Facing.offsetsXForSide[side], y - Facing.offsetsYForSide[side], z - Facing.offsetsZForSide[side]);
            return world.getBlockMetadata(x, y, z) == GLASS && selfMeta != GLASS;
        }
        return !other.isOpaqueCube();
    }

    // ------------------------------------------------------------------ the clicks

    /** The controller: its screen. A port with a wrench: IN <-> OUT (a line says which). */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        int meta = world.getBlockMetadata(x, y, z);
        if (isPort(meta)) {
            if (!BlockConduitSC.isWrench(player.getCurrentEquippedItem())) {
                return false;
            }
            TileEntity te = world.getTileEntity(x, y, z);
            if (!world.isRemote && te instanceof TileEntityEnergyBankPortSC) {
                boolean out = ((TileEntityEnergyBankPortSC) te).toggleOut();
                player.addChatComponentMessage(new ChatComponentTranslation(out ? "sc.bank.port.out" : "sc.bank.port.in")
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
            }
            return true;
        }
        if (meta != CONTROLLER || player.isSneaking()) {
            return false;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.ENERGY_BANK_GUI_ID, world, x, y, z);
        }
        return true;
    }

    /** A port: a bank block put or taken away next to it - its shut faces (TileEntityEnergyBankPortSC) looked at again. */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        TileEntity te = world.isRemote || !isPort(world.getBlockMetadata(x, y, z)) ? null : world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyBankPortSC) {
            ((TileEntityEnergyBankPortSC) te).neighbourChanged();
        }
    }

    // ------------------------------------------------------------------ the controller's charge rides in its item

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyBankSC && stack.hasTagCompound() && stack.getTagCompound().hasKey(TileEntityEnergyBankSC.NBT)) {
            ((TileEntityEnergyBankSC) te).setEnergyFromItem(stack.getTagCompound().getCompoundTag(TileEntityEnergyBankSC.NBT).getLong("eu"));
        }
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest && world.getBlockMetadata(x, y, z) == CONTROLLER) {
            return true;              // harvestBlock drops (reading the tile) and then removes the block
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        if (meta == CONTROLLER) {
            world.setBlockToAir(x, y, z);
        }
    }

    /** An explosion drops the controller whole - its charge rides in the item. */
    @Override
    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int meta, float chance, int fortune) {
        super.dropBlockAsItemWithChance(world, x, y, z, meta, meta == CONTROLLER ? 1.0F : chance, fortune);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this, 1, meta);
        TileEntity te = meta == CONTROLLER ? world.getTileEntity(x, y, z) : null;
        if (te instanceof TileEntityEnergyBankSC && ((TileEntityEnergyBankSC) te).energy() > 0) {
            NBTTagCompound root = new NBTTagCompound(), t = new NBTTagCompound();
            t.setLong("eu", ((TileEntityEnergyBankSC) te).energy());
            root.setTag(TileEntityEnergyBankSC.NBT, t);
            stack.setTagCompound(root);
        }
        drops.add(stack);
        return drops;
    }

    // ------------------------------------------------------------------ the comparator

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityEnergyBankSC ? ((TileEntityEnergyBankSC) te).comparatorLevel() : 0;
    }
}
