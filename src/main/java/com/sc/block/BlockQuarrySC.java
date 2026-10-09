package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.energy.Tier;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityQuarrySC;

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

/**
 * Silicon Quarry LV / MV / HV / EV (metadata = tier). The front (the tier's drill panel) turns to
 * the placer, the top shows the drill shaft, the other sides the tier's casing. The placer owns
 * it. Broken or dismantled, it keeps its settings, energy, tanks and stored XP in the item (the
 * digging starts its area over when placed again).
 */
public class BlockQuarrySC extends Block {

    /** LV, MV, HV, EV quarries and (meta 4) the Exo Drilling Rig, tier XV. */
    public static final int TIERS = 5;
    private IIcon[] fronts, casings;
    private IIcon top, exoTop;

    public BlockQuarrySC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".quarry");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(4.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 0);
    }

    /**
     * Broken only with a pickaxe: by hand it doesn't break at all, nothing inside is lost (PickaxeOnlySC).
     * A stranger's doesn't break at all - the server says no (the client can't tell an op).
     */
    @Override
    public float getPlayerRelativeBlockHardness(net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world,
                                                int x, int y, int z) {
        if (refusesBreak(world, player, x, y, z)) {
            TileEntity te = world.getTileEntity(x, y, z);
            tellRefused(player, "sc.chat.break.owneronly", ((TileEntityQuarrySC) te).getOwner());
            return 0F;
        }
        return PickaxeOnlySC.hardness(super.getPlayerRelativeBlockHardness(player, world, x, y, z), player);
    }

    /**
     * КР-6: a quarry with an owner is broken or dismantled by the owner and server ops only - it
     * used to go to anyone, with its charge, tanks and settings, and the placer became the owner.
     * Server side (false on the client). Also asked by the wrench's dismantle.
     */
    public static boolean refusesBreak(World world, EntityPlayer player, int x, int y, int z) {
        if (world == null || world.isRemote || player == null) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityQuarrySC && !((TileEntityQuarrySC) te).getOwner().isEmpty()
                && !((TileEntityQuarrySC) te).allowed(player);       // allowed: the owner, or an op
    }

    /** A server op (the ops list; single player with cheats too). */
    public static boolean isOp(EntityPlayer player) {
        net.minecraft.server.MinecraftServer srv = net.minecraft.server.MinecraftServer.getServer();
        return player instanceof net.minecraft.entity.player.EntityPlayerMP && srv != null
                && srv.getConfigurationManager().func_152596_g(((net.minecraft.entity.player.EntityPlayerMP) player).getGameProfile());
    }

    /** Why a stranger's block won't break - at most once a second while they keep at it. Server side. */
    public static void tellRefused(EntityPlayer player, String key, String owner) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
        long last = player.getEntityData().getLong("scOwnedMsg");
        if (now - last >= 20 || now < last) {
            player.getEntityData().setLong("scOwnedMsg", now);
            player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(key, owner));
        }
    }

    /** The next horizontal side clockwise: north -> east -> south -> west. */
    static int clockwise(int side) {
        switch (side) {
            case 2: return 5;
            case 5: return 3;
            case 3: return 4;
            default: return 2;
        }
    }

    public static Tier tierFor(int meta) {
        if (meta == 4) {
            return Tier.XV;
        }
        return Tier.values()[meta >= 0 && meta < 4 ? meta : 0];
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityQuarrySC te = new TileEntityQuarrySC();
        te.setQuarryTier(tierFor(meta));
        return te;
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        fronts = new IIcon[TIERS];
        casings = new IIcon[TIERS];
        for (int i = 0; i < TIERS; i++) {
            fronts[i] = register.registerIcon(Reference.ASSETS + (i == 4 ? ":exoDrillFront" : ":quarryFront" + tierFor(i).name()));
            casings[i] = register.registerIcon(Reference.ASSETS + ":machineCasing" + tierFor(i).name());
        }
        top = register.registerIcon(Reference.ASSETS + ":quarryTop");
        exoTop = register.registerIcon(Reference.ASSETS + ":exoDrillTop");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        int t = Math.max(0, Math.min(TIERS - 1, meta));
        return side == 1 ? (t == 4 ? exoTop : top) : side == 3 ? fronts[t] : casings[t];
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        int front = te instanceof TileEntityQuarrySC ? ((TileEntityQuarrySC) te).getFacing() : 3;
        int t = Math.max(0, Math.min(TIERS - 1, meta));
        return side == 1 ? (t == 4 ? exoTop : top) : side == front ? fronts[t] : casings[t];
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityQuarrySC)) {
            return;
        }
        TileEntityQuarrySC q = (TileEntityQuarrySC) te;
        int quarter = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        q.setFacing(new int[]{2, 5, 3, 4}[quarter]);
        if (!world.isRemote) {
            if (stack.hasTagCompound()) {
                q.readFromItem(stack.getTagCompound());
            }
            if (placer instanceof EntityPlayer) {
                q.setOwner(placer.getCommandSenderName());     // the placer owns it - not whoever the item came from
            }
        }
        // placed switched off, as a machine: nothing can overvolt it (or flood the line) before it is checked and switched on
        q.setPowerOn(false);
        world.markBlockForUpdate(x, y, z);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        ItemStack held = player.getCurrentEquippedItem();
        TileEntity te = world.getTileEntity(x, y, z);
        if (BlockConduitSC.isWrench(held) && te instanceof TileEntityQuarrySC) {
            if (!world.isRemote) {
                TileEntityQuarrySC q = (TileEntityQuarrySC) te;
                q.setFacing(player.isSneaking() && side > 1 ? side : clockwise(q.getFacing()));
                world.markBlockForUpdate(x, y, z);
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.QUARRY_GUI_ID, world, x, y, z);
        }
        return true;
    }

    /** Other mods' wrenches (BuildCraft, Thermal, Ender IO...): a quarter turn round the vertical axis. */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, net.minecraftforge.common.util.ForgeDirection axis) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityQuarrySC)) {
            return false;
        }
        TileEntityQuarrySC q = (TileEntityQuarrySC) te;
        q.setFacing(clockwise(q.getFacing()));
        world.markBlockForUpdate(x, y, z);
        return true;
    }

    @Override
    public net.minecraftforge.common.util.ForgeDirection[] getValidRotations(World world, int x, int y, int z) {
        return new net.minecraftforge.common.util.ForgeDirection[]{net.minecraftforge.common.util.ForgeDirection.UP,
                net.minecraftforge.common.util.ForgeDirection.DOWN};
    }

    // ---- keep settings, energy, tanks and XP in the dropped item ----

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (refusesBreak(world, player, x, y, z)) {       // a creative stranger's instant break
            tellRefused(player, "sc.chat.break.owneronly", ((TileEntityQuarrySC) world.getTileEntity(x, y, z)).getOwner());
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

    /**
     * An explosion drops the block whole: its settings, charge, tanks and stored XP ride in the
     * item, and the explosion's usual 1-in-size chance would have lost them with it.
     */
    @Override
    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int meta, float chance, int fortune) {
        super.dropBlockAsItemWithChance(world, x, y, z, meta, 1.0F, fortune);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this, 1, damageDropped(meta));
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityQuarrySC) {
            stack.setTagCompound(((TileEntityQuarrySC) te).writeToItem());
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityQuarrySC) {
            TileEntityQuarrySC q = (TileEntityQuarrySC) te;
            for (int i = 0; i < q.getSizeInventory(); i++) {
                ItemStack s = q.getStackInSlot(i);
                if (s != null) {
                    world.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(world, x + 0.5, y + 0.5, z + 0.5, s));
                    q.setInventorySlotContents(i, null);
                }
            }
            q.dropOverflow();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /** With a universal transformer upgrade inside, no blast breaks the quarry (as a machine). */
    @Override
    public float getExplosionResistance(net.minecraft.entity.Entity exploder, World world, int x, int y, int z,
                                        double explosionX, double explosionY, double explosionZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityQuarrySC
                && ((TileEntityQuarrySC) te).upgradeCount(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER) > 0) {
            return 6000000.0F;
        }
        return super.getExplosionResistance(exploder, world, x, y, z, explosionX, explosionY, explosionZ);
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public int quantityDropped(Random random) {
        return 1;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < TIERS; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
