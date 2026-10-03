package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.bridge.BridgeItemDataSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.energy.Tier;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.tileentity.IBridgePartSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * The Bridge Remote (damage 0) and the Space Remote (damage 1) - docs/plan-ground-bridge.md §8: a right-click on
 * a Bridge Controller (or any part linked to it) binds the remote (owner and friends only; the Ground remote to a
 * Ground bridge, the Space one to a Space bridge); a right-click anywhere else opens its screen (modes ДР1-ДР5,
 * bookmarks, own coordinates, history, the coordinator slot, the cost and the bridge's state). Every command
 * that opens takes REMOTE_SIGNAL_EU from its own charge (С9) - charged in storages, charge pads and portable
 * batteries like the mod's suits. The Space remote also needs a Singular Matter cell inside as its key.
 */
public class ItemBridgeRemoteSC extends Item {

    public static final int GROUND = 0, SPACE = 1;
    public static final Tier[] TIER = {Tier.IV, Tier.SV};

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemBridgeRemoteSC() {
        setMaxStackSize(1);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".bridgeRemote");
    }

    public static boolean isRemote(ItemStack s) {
        return s != null && s.getItem() instanceof ItemBridgeRemoteSC;
    }

    public static boolean isSpace(ItemStack s) {
        return isRemote(s) && s.getItemDamage() == SPACE;
    }

    public static long capacityOf(ItemStack s) {
        return isSpace(s) ? BridgeMathSC.SPACE_REMOTE_CAPACITY : BridgeMathSC.REMOTE_CAPACITY;
    }

    public static Tier tierOf(ItemStack s) {
        return TIER[isSpace(s) ? SPACE : GROUND];
    }

    public static long chargeOf(ItemStack s) {
        return isRemote(s) ? BridgeItemDataSC.charge(s) : 0;
    }

    /** Charges up to `max` EU (storages, pads, batteries). @return EU taken */
    public static int charge(ItemStack s, int max) {
        if (!isRemote(s) || max <= 0 || s.stackSize != 1) {
            return 0;
        }
        long room = capacityOf(s) - chargeOf(s);
        int took = (int) Math.max(0, Math.min(room, max));
        if (took > 0) {
            BridgeItemDataSC.setCharge(s, chargeOf(s) + took);
        }
        return took;
    }

    /** The Space remote's key: a Singular Matter cell with matter in it. */
    public static boolean hasKey(ItemStack s) {
        return ItemSingularCellSC.amountOf(BridgeItemDataSC.inside(s, "Key")) > 0;
    }

    // ------------------------------------------------------------------ binding

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy, float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        int[] c = te instanceof TileEntityBridgeControllerSC ? new int[]{x, y, z} : te instanceof IBridgePartSC ? ((IBridgePartSC) te).controllerPos() : null;
        if (c == null) {
            return false;
        }
        if (world.isRemote) {
            return false;                       // the server binds; the block itself doesn't open its screen for a remote
        }
        TileEntity ct = world.getTileEntity(c[0], c[1], c[2]);
        if (!(ct instanceof TileEntityBridgeControllerSC)) {
            return true;
        }
        bind(stack, player, (TileEntityBridgeControllerSC) ct);
        return true;
    }

    /** Binds the remote to a controller (the server): owner / friends, the right kind. @return the chat key said */
    public static String bind(ItemStack stack, EntityPlayer player, TileEntityBridgeControllerSC c) {
        String key;
        if (!c.trusted(player)) {
            key = "sc.bridge.remote.noaccess";
            say(player, key, c.getOwner());
            return key;
        }
        if (c.getScan() == null || !c.getScan().found) {
            key = "sc.bridge.remote.nobuild";
            say(player, key);
            return key;
        }
        int kind = c.bridgeKind();
        if ((kind == BridgeMathSC.SPACE) != isSpace(stack)) {
            key = isSpace(stack) ? "sc.bridge.remote.needspace" : "sc.bridge.remote.needground";
            say(player, key);
            return key;
        }
        BridgeItemDataSC.bindRemote(stack, c.xCoord, c.yCoord, c.zCoord, c.getWorldObj().provider.dimensionId, c.ensureBridgeId(), c.getBridgeName(), kind);
        c.noteLink(player, BridgeMathSC.SRC_REMOTE);
        key = "sc.bridge.remote.bound";
        player.addChatComponentMessage(new ChatComponentTranslation(key, c.nameArgChat(), String.valueOf(c.xCoord), String.valueOf(c.yCoord),
                String.valueOf(c.zCoord)));
        return key;
    }

    private static void say(EntityPlayer p, String key, Object... args) {
        if (p != null) {
            p.addChatComponentMessage(new ChatComponentTranslation(key, args));
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            com.sc.SCMod.proxy.openRemote();
        }
        return stack;
    }

    // ------------------------------------------------------------------ looks

    @Override
    public String getUnlocalizedName(ItemStack s) {
        return isSpace(s) ? "item." + Reference.ASSETS + ".spaceRemote" : "item." + Reference.ASSETS + ".bridgeRemote";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[]{r.registerIcon(Reference.ASSETS + ":bridgeRemote"), r.registerIcon(Reference.ASSETS + ":spaceRemote")};
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons[meta == SPACE ? SPACE : GROUND];
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, GROUND));
        ItemStack full = new ItemStack(item, 1, GROUND);
        BridgeItemDataSC.setCharge(full, BridgeMathSC.REMOTE_CAPACITY);
        list.add(full);
        list.add(new ItemStack(item, 1, SPACE));
        ItemStack sfull = new ItemStack(item, 1, SPACE);
        BridgeItemDataSC.setCharge(sfull, BridgeMathSC.SPACE_REMOTE_CAPACITY);
        list.add(sfull);
    }

    @Override
    public EnumRarity getRarity(ItemStack s) {
        return isSpace(s) ? EnumRarity.epic : EnumRarity.rare;
    }

    @Override
    public boolean showDurabilityBar(ItemStack s) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack s) {
        return 1.0 - chargeOf(s) / (double) capacityOf(s);
    }

    @Override
    public boolean hasEffect(ItemStack s, int pass) {
        return BridgeItemDataSC.remoteLink(s) != null;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack s, EntityPlayer player, List list, boolean advanced) {
        boolean space = isSpace(s);
        list.add("§7" + Lang.tr(space ? "sc.tooltip.spaceRemote" : "sc.tooltip.bridgeRemote"));
        list.add("§b" + Lang.tr("sc.tooltip.remote.charge", BridgeMathSC.shortEu(chargeOf(s), Lang.tr("sc.bridge.unit.m"), Lang.tr("sc.bridge.unit.g")),
                BridgeMathSC.shortEu(capacityOf(s), Lang.tr("sc.bridge.unit.m"), Lang.tr("sc.bridge.unit.g")), tierOf(s).name()));
        int[] b = BridgeItemDataSC.remoteLink(s);
        if (b != null) {
            String n = BridgeItemDataSC.remoteBridgeName(s);
            list.add("§a" + Lang.tr("sc.tooltip.remote.bound", n.length() == 0 ? Lang.tr("sc.bridge.res.noname") : n, b[0], b[1], b[2]));
        } else {
            list.add("§e" + Lang.tr("sc.tooltip.remote.unbound"));
        }
        ItemStack c = BridgeItemDataSC.inside(s, "Coord");
        if (c != null) {
            list.add("§7" + Lang.tr("sc.tooltip.remote.coord", ItemCoordinatorSC.label(c)));
        }
        if (space) {
            list.add(hasKey(s) ? "§d" + Lang.tr("sc.tooltip.remote.key") : "§c" + Lang.tr("sc.tooltip.remote.nokey"));
        }
        com.sc.util.TooltipSC.more(list, Lang.tr(space ? "sc.tooltip.spaceRemote.details" : "sc.tooltip.bridgeRemote.details",
                BridgeMathSC.shortEu(BridgeMathSC.REMOTE_SIGNAL_EU, Lang.tr("sc.bridge.unit.m"), Lang.tr("sc.bridge.unit.g"))),
                Lang.tr("sc.tooltip.bridgeRemote.howto"));
    }
}
