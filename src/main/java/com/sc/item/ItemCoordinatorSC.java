package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.bridge.BridgeItemDataSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeSpaceSC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.tileentity.IBridgePartSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * The Coordinator (docs/plan-ground-bridge.md §2, §8): keeps one point - x, y, z, the dimension, a name and
 * «безопасно» (a 3 x 3 x 2 vortex fits there when it was recorded). A right-click on a block records the cell
 * on the clicked face; sneak + right-click opens its name screen; a right-click in the air says the point.
 * The bridge controller's screen reads it from the inventory («Из коорд.», «В коорд.», a right-click on «В коорд.»
 * copies one coordinator into an empty one), the remotes take one into their slot, the armour's «Мост» tab writes
 * points into it. A right-click on a bridge block opens the controller as usual.
 */
public class ItemCoordinatorSC extends Item {

    public ItemCoordinatorSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".coordinator");
        setTextureName(Reference.ASSETS + ":coordinator");
    }

    public static boolean isCoordinator(ItemStack s) {
        return s != null && s.getItem() instanceof ItemCoordinatorSC;
    }

    /** A filled coordinator is never used up in a craft (the copy recipe, CoordinatorCopyRecipeSC): it stays in the grid. */
    @Override
    public boolean hasContainerItem(ItemStack s) {
        return BridgeItemDataSC.point(s) != null;
    }

    @Override
    public ItemStack getContainerItem(ItemStack s) {
        if (BridgeItemDataSC.point(s) == null) {
            return null;
        }
        ItemStack back = s.copy();
        back.stackSize = 1;
        return back;
    }

    @Override
    public boolean doesContainerItemLeaveCraftingGrid(ItemStack s) {
        return false;
    }

    /** "Name (x y z)" or "x y z" - tooltips and screens. */
    public static String label(ItemStack s) {
        int[] p = BridgeItemDataSC.point(s);
        if (p == null) {
            return Lang.tr("sc.coordinator.empty");
        }
        String n = BridgeItemDataSC.pointName(s);
        String at = p[0] + " " + p[1] + " " + p[2];
        return n.length() > 0 ? n + " (" + at + ")" : at;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy, float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!player.isSneaking() && (te instanceof TileEntityBridgeControllerSC || te instanceof IBridgePartSC)) {
            return false;                                  // the controller's screen opens (it reads the coordinator from the inventory)
        }
        if (player.isSneaking()) {
            if (world.isRemote) {
                com.sc.SCMod.proxy.openCoordinator();
            }
            return true;
        }
        if (world.isRemote) {
            return false;
        }
        int[] d = {0, 0, 0};
        switch (side) {
            case 0: d[1] = -1; break;
            case 1: d[1] = 1; break;
            case 2: d[2] = -1; break;
            case 3: d[2] = 1; break;
            case 4: d[0] = -1; break;
            default: d[0] = 1; break;
        }
        int px = x + d[0], py = y + d[1], pz = z + d[2];
        record(stack, player, world, px, py, pz);
        return true;
    }

    /** Records (x, y, z) of `world` with the safe flag; says it. */
    public static void record(ItemStack stack, EntityPlayer player, World world, int x, int y, int z) {
        int f = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        boolean safe = BridgeSpaceSC.check(BridgeSpaceSC.of(world), x, y, z, BridgeMathSC.vortexSize(BridgeMathSC.GROUND), f % 2 == 0 ? 0 : 1).free;
        BridgeItemDataSC.setPoint(stack, x, y, z, world.provider.dimensionId, safe);
        com.sc.bridge.BridgeFamiliarSC fam = com.sc.bridge.BridgeFamiliarSC.get(world);
        if (fam != null && !world.isRemote) {
            fam.markBlock(player.getCommandSenderName(), world.provider.dimensionId, x, z, 0);      // С5: a recorded point is a familiar place
        }
        player.addChatComponentMessage(new ChatComponentTranslation(safe ? "sc.coordinator.recorded.safe" : "sc.coordinator.recorded.unsafe",
                String.valueOf(x), String.valueOf(y), String.valueOf(z)));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (world.isRemote) {
                com.sc.SCMod.proxy.openCoordinator();
            }
            return stack;
        }
        if (!world.isRemote) {
            int[] p = BridgeItemDataSC.point(stack);
            if (p == null) {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.coordinator.none"));
            } else {
                player.addChatComponentMessage(new ChatComponentTranslation("sc.coordinator.is", label(stack), String.valueOf(p[3])));
            }
        }
        return stack;
    }

    /** С5: the chunk a player stands in while holding a Coordinator becomes familiar (once a second). */
    @Override
    public void onUpdate(ItemStack stack, World world, net.minecraft.entity.Entity e, int slot, boolean held) {
        if (held && !world.isRemote && e instanceof EntityPlayer && world.getTotalWorldTime() % 20 == 0) {
            com.sc.bridge.BridgeFamiliarSC.visit((EntityPlayer) e, 0);
        }
    }

    @Override
    public boolean hasEffect(ItemStack s, int pass) {
        return BridgeItemDataSC.point(s) != null;
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        String n = BridgeItemDataSC.pointName(s);
        String base = super.getItemStackDisplayName(s);
        return n.length() > 0 ? base + ": " + n : base;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack s, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.coordinator"));
        int[] p = BridgeItemDataSC.point(s);
        if (p != null) {
            list.add("§b" + Lang.tr("sc.tooltip.coordinator.point", p[0], p[1], p[2], p[3]));
            list.add(BridgeItemDataSC.safe(s) ? "§a" + Lang.tr("sc.tooltip.coordinator.safe") : "§e" + Lang.tr("sc.tooltip.coordinator.unsafe"));
        } else {
            list.add("§8" + Lang.tr("sc.coordinator.empty"));
        }
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.tooltip.coordinator.details"), Lang.tr("sc.tooltip.coordinator.howto"));
    }
}
