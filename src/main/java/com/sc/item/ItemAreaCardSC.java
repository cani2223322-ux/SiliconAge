package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * Area card: right-click a block for the first corner, another for the second (sneak + right-click
 * in the air clears it). In a quarry's card slot it sets the quarry's area to the box between them.
 */
public class ItemAreaCardSC extends Item {

    public ItemAreaCardSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".areaCard");
        setTextureName(Reference.ASSETS + ":areaCard");
    }

    /** {x0, y0, z0, x1, y1, z1} with x0 <= x1 etc., or null if the card has no complete area. */
    public static int[] area(ItemStack s) {
        if (s == null || !s.hasTagCompound() || !s.getTagCompound().hasKey("B")) {
            return null;
        }
        int[] a = s.getTagCompound().getIntArray("A"), b = s.getTagCompound().getIntArray("B");
        if (a.length != 3 || b.length != 3) {
            return null;
        }
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
    }

    /** Does the card's area lie in that dimension? A card written before "Dim" was kept: any. */
    public static boolean inDimension(ItemStack s, int dim) {
        return s == null || !s.hasTagCompound() || !s.getTagCompound().hasKey("Dim") || s.getTagCompound().getInteger("Dim") == dim;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hx, float hy, float hz) {
        if (world.isRemote) {
            return true;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound nbt = stack.getTagCompound();
        int dim = world.provider.dimensionId;
        if (!nbt.hasKey("A") || nbt.hasKey("B") || nbt.hasKey("Dim") && nbt.getInteger("Dim") != dim) {
            nbt.setIntArray("A", new int[]{x, y, z});      // a corner in another dimension starts the box over
            nbt.removeTag("B");
            nbt.setInteger("Dim", dim);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.areacard.first", x, y, z));
        } else {
            nbt.setIntArray("B", new int[]{x, y, z});
            nbt.setInteger("Dim", dim);
            int[] a = area(stack);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.areacard.second", x, y, z,
                    a[3] - a[0] + 1, a[5] - a[2] + 1, a[4] - a[1] + 1));
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking() && stack.hasTagCompound()) {
            stack.setTagCompound(null);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.areacard.cleared"));
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int[] a = area(stack);
        if (a != null) {
            list.add(Lang.tr("sc.areacard.area", a[3] - a[0] + 1, a[5] - a[2] + 1, a[4] - a[1] + 1));
            list.add("§7" + a[0] + " " + a[1] + " " + a[2] + "  ->  " + a[3] + " " + a[4] + " " + a[5]);
            if (stack.getTagCompound().hasKey("Dim")) {
                int dim = stack.getTagCompound().getInteger("Dim");
                boolean here = player != null && player.worldObj != null && player.worldObj.provider.dimensionId == dim;
                String name = here ? player.worldObj.provider.getDimensionName() + " (" + dim + ")" : String.valueOf(dim);
                list.add("§7" + Lang.tr("sc.areacard.dim", name));
            }
        } else if (stack.hasTagCompound() && stack.getTagCompound().hasKey("A")) {
            list.add(Lang.tr("sc.areacard.half"));
        } else {
            list.add(Lang.tr("sc.areacard.empty"));
        }
        com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.areacard.hint"));
    }
}
