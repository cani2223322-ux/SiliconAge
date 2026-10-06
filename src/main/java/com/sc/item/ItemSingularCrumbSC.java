package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.ToolLevelSC;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * «Крупица сингулярности» (docs/plan-singular-tools.md §3.2): only the Singular drill's black hole mode gives them -
 * one per CRUMB_BLOCKS natural blocks it destroys (ores x ORE_MUL, placed blocks never; DrillLogicSC.addCrumbs). Sneak + right-click in the air with crumbs in hand and
 * a Singular drill in the hotbar feeds them to the drill - CRUMB_POINTS level points each, only as many as
 * its level still needs. Nine make a clot (ItemSingularClotSC). Uncommon - the Matter Compressor never eats them.
 */
public class ItemSingularCrumbSC extends Item {

    /** Natural blocks per crumb dug, ores count this many times, level points per crumb fed. */
    public static final int CRUMB_BLOCKS = 64, ORE_MUL = 4, CRUMB_POINTS = 10;

    public ItemSingularCrumbSC() {
        setMaxStackSize(64);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".singularCrumb");
    }

    @Override
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(Reference.ASSETS + ":singularCrumb");
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.uncommon;
    }

    /** Pure: crumbs to feed out of `have` to give `left` points at `per` a crumb (the last may be partly lost). */
    public static int crumbsToFeed(int have, int left, int per) {
        if (have <= 0 || left <= 0 || per <= 0) {
            return 0;
        }
        return Math.min(have, (left + per - 1) / per);
    }

    /** The first Singular drill in the hotbar (the held one first), or null. */
    public static ItemStack hotbarDrill(EntityPlayer p) {
        ItemStack held = p.getCurrentEquippedItem();
        if (ToolLevelSC.isDrill(held)) {
            return held;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (ToolLevelSC.isDrill(s)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!player.isSneaking() || world.isRemote) {
            return stack;
        }
        ItemStack drill = hotbarDrill(player);
        if (drill == null) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.singcrumb.nodrill"));
            return stack;
        }
        int n = crumbsToFeed(stack.stackSize, ToolLevelSC.pointsLeft(drill), CRUMB_POINTS);
        if (n <= 0) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.singcrumb.full"));
            return stack;
        }
        int got = ToolLevelSC.addPoints(drill, n * CRUMB_POINTS);
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize -= n;
        }
        player.addChatComponentMessage(new ChatComponentTranslation("sc.singcrumb.fed", String.valueOf(got), String.valueOf(n),
                String.valueOf(ToolLevelSC.points(drill)), String.valueOf(ToolLevelSC.threshold(drill))));
        world.playSoundAtEntity(player, "mob.endermen.portal", 0.5F, 1.6F);
        player.inventoryContainer.detectAndSendChanges();
        return stack;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.tooltip.singcrumb"));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.tooltip.singcrumb.details", CRUMB_BLOCKS, ORE_MUL),
                Lang.tr("sc.tooltip.singcrumb.howto", CRUMB_POINTS));
    }
}
