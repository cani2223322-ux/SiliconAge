package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.conduit.ItemFilterSC;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Item filters for pneumatic tube connectors (Ender IO style): metadata 0 = basic (5 examples,
 * whitelist / blacklist, ignore metadata), 1 = advanced (10 examples, + match NBT, + ore
 * dictionary). Right-click in hand to set it up; it goes into a tube connector's filter slot.
 */
public class ItemItemFilterSC extends Item {

    private IIcon[] icons;

    public ItemItemFilterSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".itemFilter");
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + (stack.getItemDamage() == 1 ? ".advanced" : ".basic");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            // x = the hotbar slot, so the client builds its screen for the same filter as the server
            player.openGui(SCMod.instance, GuiHandlerSC.FILTER_GUI_ID, world, player.inventory.currentItem, 0, 0);
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr(ItemFilterSC.flag(stack, ItemFilterSC.BLACKLIST) ? "sc.filter.blacklist" : "sc.filter.whitelist"));
        int n = 0;
        for (ItemStack e : ItemFilterSC.entries(stack)) {
            if (e != null) {
                n++;
                if (n <= 5) {
                    list.add("§7- " + e.getDisplayName());
                }
            }
        }
        if (n == 0) {
            list.add("§7" + Lang.tr("sc.filter.empty"));
        }
        if (com.sc.util.TooltipSC.ctrl()) {
            com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.filter.howto"), "\u00a77");
        } else {
            com.sc.util.TooltipSC.hintCtrl(list);
        }
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[]{register.registerIcon(Reference.ASSETS + ":itemFilterBasic"),
                register.registerIcon(Reference.ASSETS + ":itemFilterAdvanced")};
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[meta == 1 ? 1 : 0];
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
        list.add(new ItemStack(item, 1, 1));
    }
}
