package com.sc.block;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.Tier;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityTransformerSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Transformers LV-MV / MV-HV / HV-EV (metadata = the low tier's ordinal). The front (high-voltage)
 * face turns to the player on placement. Right-click with an empty hand or a wrench switches
 * step-down / step-up (with anything else in hand the click places it as usual); sneak +
 * right-click with an empty hand, or a wrench's rotate, turns the front to the clicked side.
 */
public class BlockTransformerSC extends Block {

    public static final int VARIANTS = Tier.values().length - 1;

    private IIcon[] sideIcons;
    private IIcon[] downIcons;
    private IIcon[] upIcons;

    public BlockTransformerSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".transformer");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    static Tier lowTierFor(int meta) {
        return Tier.values()[meta >= 0 && meta < VARIANTS ? meta : 0];
    }

    static Tier highTierFor(int meta) {
        return Tier.values()[lowTierFor(meta).ordinal() + 1];
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityTransformerSC te = new TileEntityTransformerSC();
        te.setLowTier(lowTierFor(meta));
        return te;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityTransformerSC) {
            ((TileEntityTransformerSC) te).setFacing(facingToward(placer));
            world.markBlockForUpdate(x, y, z);
        }
    }

    /** The face that points back at the placer (top/bottom when looking steeply). */
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
        TileEntity te = world.getTileEntity(x, y, z);
        ItemStack held = player.getCurrentEquippedItem();
        if (!(te instanceof TileEntityTransformerSC) || (held != null && !BlockConduitSC.isWrench(held))) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntityTransformerSC transformer = (TileEntityTransformerSC) te;
        if (player.isSneaking()) {
            transformer.setFacing(ForgeDirection.getOrientation(side));
            player.addChatComponentMessage(new ChatComponentTranslation("sc.transformer.chat.rotated"));
        } else {
            transformer.setStepUp(!transformer.isStepUp());
            player.addChatComponentMessage(modeMessage(transformer));
        }
        transformer.markDirty();
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    /** Wrenches (BuildCraft, Ender IO, Thermal...) rotate blocks through this: the front goes to the clicked side. */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, ForgeDirection axis) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityTransformerSC) || axis == ForgeDirection.UNKNOWN) {
            return false;
        }
        TileEntityTransformerSC t = (TileEntityTransformerSC) te;
        t.setFacing(t.getFacing() == axis ? axis.getOpposite() : axis);
        t.markDirty();
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    @Override
    public ForgeDirection[] getValidRotations(World world, int x, int y, int z) {
        return ForgeDirection.VALID_DIRECTIONS;
    }

    private static ChatComponentTranslation modeMessage(TileEntityTransformerSC t) {
        String low = t.getLowTier().name() + " (" + t.getLowTier().getVoltage() + " EU/t)";
        String high = t.getHighTier().name() + " (" + t.getHighTier().getVoltage() + " EU/t)";
        return t.isStepUp()
                ? new ChatComponentTranslation("sc.transformer.chat.up", low, high)
                : new ChatComponentTranslation("sc.transformer.chat.down", high, low);
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int meta = 0; meta < VARIANTS; meta++) {
            list.add(new ItemStack(item, 1, meta));
        }
    }

    // ---- textures: low-tier casing with the core on the sides, the high-tier terminal in front ----

    static String textureKey(int meta) {
        return lowTierFor(meta).name() + highTierFor(meta).name();
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        sideIcons = new IIcon[VARIANTS];
        downIcons = new IIcon[VARIANTS];
        upIcons = new IIcon[VARIANTS];
        for (int meta = 0; meta < VARIANTS; meta++) {
            String base = Reference.ASSETS + ":transformer" + textureKey(meta);
            sideIcons[meta] = register.registerIcon(base + "Side");
            downIcons[meta] = register.registerIcon(base + "FrontDown");
            upIcons[meta] = register.registerIcon(base + "FrontUp");
        }
    }

    /** Inventory form: the front (step-down) on the south face, like a machine's. */
    @Override
    public IIcon getIcon(int side, int meta) {
        if (sideIcons == null) {
            return null;
        }
        int m = lowTierFor(meta).ordinal();
        return side == 3 ? downIcons[m] : sideIcons[m];
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityTransformerSC && sideIcons != null) {
            TileEntityTransformerSC t = (TileEntityTransformerSC) te;
            int m = lowTierFor(meta).ordinal();
            if (side == t.getFacing().ordinal()) {
                return t.isStepUp() ? upIcons[m] : downIcons[m];
            }
            return sideIcons[m];
        }
        return getIcon(side, meta);
    }
}
