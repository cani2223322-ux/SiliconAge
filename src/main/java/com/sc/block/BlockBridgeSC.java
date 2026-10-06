package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.bridge.BridgeMarksSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.IBridgePartSC;
import com.sc.tileentity.TileEntityBridgeCapacitorSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;
import com.sc.tileentity.TileEntityBridgeEnergyPortSC;
import com.sc.tileentity.TileEntityBridgeGasPortSC;

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
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The Ground / Space Bridge's own blocks (docs/plan-ground-bridge.md §2), one per metadata - the order is
 * saved in worlds, so new parts only go at the end: the controller, the Singularity Capacitor, the energy
 * port, the gas port, the Singularity Focuser, the four modules, the Receiver Beacon, the Interdimensional
 * Anchor. The ring itself is the Singular Reactor's gravity coil. The controller, capacitors and ports have
 * tile entities; the item of the controller keeps its tanks and bookmarks, the capacitor's its charge.
 */
public class BlockBridgeSC extends Block {

    public static final int CONTROLLER = 0, CAPACITOR = 1, ENERGY_PORT = 2, GAS_PORT = 3, FOCUSER = 4, NAV = 5, MASS = 6, COOLER = 7,
            SHIELD = 8, BEACON = 9, ANCHOR = 10;
    public static final String[] NAMES = {"controller", "capacitor", "energyPort", "gasPort", "focuser", "navComputer", "massCompensator",
            "ringCooler", "portalShield", "receiverBeacon", "dimAnchor"};
    /** Each part's [bottom, top, side, front] textures. */
    private static final String[][] TEX = {
        {"bridgeCasing", "bridgeControllerTop", "bridgeControllerSide", "bridgeControllerFront"},
        {"bridgeCasing", "bridgeCapacitorTop", "bridgeCapacitorSide", "bridgeCapacitorSide"},
        {"bridgeCasing", "bridgeCasing", "bridgeEnergyPort", "bridgeEnergyPort"},
        {"bridgeCasing", "bridgeCasing", "bridgeGasPort", "bridgeGasPort"},
        {"bridgeFocuser", "bridgeFocuser", "bridgeFocuser", "bridgeFocuser"},
        {"bridgeCasing", "bridgeModuleTop", "bridgeNav", "bridgeNav"},
        {"bridgeCasing", "bridgeModuleTop", "bridgeMass", "bridgeMass"},
        {"bridgeCasing", "bridgeModuleTop", "bridgeCooler", "bridgeCooler"},
        {"bridgeCasing", "bridgeModuleTop", "bridgeShield", "bridgeShield"},
        {"bridgeCasing", "bridgeBeaconTop", "bridgeBeacon", "bridgeBeacon"},
        {"bridgeCasing", "bridgeAnchorTop", "bridgeAnchor", "bridgeAnchor"},
    };
    private static final int[] LIGHT = {4, 0, 0, 0, 7, 0, 0, 0, 0, 6, 6};

    @SideOnly(Side.CLIENT)
    private IIcon[][] icons;

    public BlockBridgeSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".bridge");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(20.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
    }

    public static int parts() {
        return NAMES.length;
    }

    /** The structure check's kind of a part. */
    public static int kindOf(int meta) {
        switch (meta) {
            case CONTROLLER: return BridgeStructureSC.K_CONTROLLER;
            case CAPACITOR: return BridgeStructureSC.K_CAPACITOR;
            case ENERGY_PORT: return BridgeStructureSC.K_ENERGY_PORT;
            case GAS_PORT: return BridgeStructureSC.K_GAS_PORT;
            case FOCUSER: return BridgeStructureSC.K_FOCUSER;
            case NAV: return BridgeStructureSC.K_NAV;
            case MASS: return BridgeStructureSC.K_MASS;
            case COOLER: return BridgeStructureSC.K_COOLER;
            case SHIELD: return BridgeStructureSC.K_SHIELD;
            case BEACON: return BridgeStructureSC.K_BEACON;
            case ANCHOR: return BridgeStructureSC.K_ANCHOR;
            default: return BridgeStructureSC.K_OTHER;
        }
    }

    public static ItemStack stack(int part, int count) {
        return new ItemStack(com.sc.init.ModBlocks.bridge, count, part);
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < NAMES.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        int m = w.getBlockMetadata(x, y, z);
        return m >= 0 && m < LIGHT.length ? LIGHT[m] : 0;
    }

    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z) {
        return PickaxeOnlySC.hardness(super.getPlayerRelativeBlockHardness(player, world, x, y, z), player);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return meta == CONTROLLER || meta == CAPACITOR || meta == ENERGY_PORT || meta == GAS_PORT;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        switch (meta) {
            case CONTROLLER: return new TileEntityBridgeControllerSC();
            case CAPACITOR: return new TileEntityBridgeCapacitorSC();
            case ENERGY_PORT: return new TileEntityBridgeEnergyPortSC();
            case GAS_PORT: return new TileEntityBridgeGasPortSC();
            default: return null;
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        ItemStack held = player.getCurrentEquippedItem();
        if (meta == GAS_PORT && te instanceof TileEntityBridgeGasPortSC && com.sc.util.FluidHandSC.isContainer(held)) {
            if (!world.isRemote) {
                com.sc.util.FluidHandSC.use(world, x, y, z, player, (TileEntityBridgeGasPortSC) te, held);
            }
            return true;
        }
        if (player.isSneaking() && held != null) {
            return false;
        }
        if (held != null && held.getItem() instanceof com.sc.item.ItemBridgeRemoteSC) {
            return true;                              // the remote binds (its onItemUseFirst, on the server) - no screen
        }
        int[] c = null;
        if (te instanceof TileEntityBridgeControllerSC) {
            c = new int[]{x, y, z};
        } else if (te instanceof IBridgePartSC) {
            c = ((IBridgePartSC) te).controllerPos();
        }
        if (c == null) {
            if (!world.isRemote && (meta == CAPACITOR || meta == ENERGY_PORT || meta == GAS_PORT)) {
                player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.bridge.msg.notlinked"));
            }
            return meta == CAPACITOR || meta == ENERGY_PORT || meta == GAS_PORT || meta == CONTROLLER;
        }
        if (world.isRemote) {
            com.sc.SCMod.proxy.openBridge(c[0], c[1], c[2]);
        } else if (te instanceof TileEntityBridgeControllerSC) {
            ((TileEntityBridgeControllerSC) te).claim(player);   // М-7: an ownerless controller (automation) - the first right-click owns it
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (world.isRemote) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (te instanceof TileEntityBridgeControllerSC) {
            TileEntityBridgeControllerSC c = (TileEntityBridgeControllerSC) te;
            int quarter = net.minecraft.util.MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            ForgeDirection[] toPlacer = {ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST};
            c.setFacing(toPlacer[quarter].ordinal());
            if (tag != null) {
                c.readFromItem(tag);
            }
            if (placer instanceof EntityPlayer && !(placer instanceof net.minecraftforge.common.util.FakePlayer)) {
                c.setOwner(((EntityPlayer) placer).getCommandSenderName());   // М-7: automation (a FakePlayer) leaves it ownerless - claim
            }
            c.setPowerOn(false);                                 // placed off, as every energy block
            world.markBlockForUpdate(x, y, z);
        } else if (te instanceof TileEntityBridgeCapacitorSC && tag != null) {
            ((TileEntityBridgeCapacitorSC) te).setEnergy(tag.getLong("BridgeEU"));
        } else if (te instanceof TileEntityBridgeEnergyPortSC) {
            ((TileEntityBridgeEnergyPortSC) te).setPowerOn(false);
        }
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if (!world.isRemote && (meta == BEACON || meta == ANCHOR)) {
            BridgeMarksSC m = BridgeMarksSC.get(world);
            if (m != null) {
                m.add(meta == BEACON ? BridgeMarksSC.BEACON : BridgeMarksSC.ANCHOR, world.provider.dimensionId, x, y, z);
            }
        }
    }

    // ---- the item keeps the controller's tanks / bookmarks and the capacitor's charge: drop while the tile still exists ----

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
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack stack = new ItemStack(this, 1, meta);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityBridgeControllerSC) {
            TileEntityBridgeControllerSC c = (TileEntityBridgeControllerSC) te;
            if (!world.isRemote) {
                c.closePortal("sc.bridge.journal.removed");          // a vortex never outlives its controller
            }
            NBTTagCompound nbt = c.writeToItem();
            if (!nbt.hasNoTags()) {
                stack.setTagCompound(nbt);
            }
        } else if (te instanceof TileEntityBridgeCapacitorSC && ((TileEntityBridgeCapacitorSC) te).getEnergy() > 0) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setLong("BridgeEU", ((TileEntityBridgeCapacitorSC) te).getEnergy());
            stack.setTagCompound(nbt);
        }
        drops.add(stack);
        return drops;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote) {
            if (te instanceof TileEntityBridgeControllerSC) {
                ((TileEntityBridgeControllerSC) te).closePortal("sc.bridge.journal.removed");
            }
            if (meta == BEACON || meta == ANCHOR) {
                BridgeMarksSC m = BridgeMarksSC.get(world);
                if (m != null) {
                    m.remove(world.provider.dimensionId, x, y, z);
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        int meta = world.getBlockMetadata(x, y, z);
        if (meta == FOCUSER || meta == BEACON || meta == ANCHOR) {
            world.spawnParticle(meta == FOCUSER ? "witchMagic" : "portal", x + 0.2 + rand.nextDouble() * 0.6, y + 1.05,
                    z + 0.2 + rand.nextDouble() * 0.6, 0, meta == FOCUSER ? 0.03 : 0.3, 0);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[TEX.length][4];
        java.util.Map<String, IIcon> done = new java.util.HashMap<String, IIcon>();
        for (int m = 0; m < TEX.length; m++) {
            for (int f = 0; f < 4; f++) {
                String n = TEX[m][f];
                IIcon i = done.get(n);
                if (i == null) {
                    i = register.registerIcon(Reference.ASSETS + ":" + n);
                    done.put(n, i);
                }
                icons[m][f] = i;
            }
        }
        blockIcon = icons[0][2];
    }

    /** The item: the front faces south (3). */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        IIcon[] i = icons[meta >= 0 && meta < icons.length ? meta : 0];
        return face == 0 ? i[0] : face == 1 ? i[1] : face == 3 ? i[3] : i[2];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int face) {
        int meta = world.getBlockMetadata(x, y, z);
        if (meta == CONTROLLER && face > 1) {
            TileEntity te = world.getTileEntity(x, y, z);
            int front = te instanceof TileEntityBridgeControllerSC ? ((TileEntityBridgeControllerSC) te).getFacing() : 3;
            return face == front ? icons[0][3] : icons[0][2];
        }
        return getIcon(face, meta);
    }
}
