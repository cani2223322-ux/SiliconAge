package com.sc.arena;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * ПД-6 (docs/plan-ideas-2026-10-26.md, «Протоколы Пробоя», ★ A): «Протокол Пробоя» - a card that makes the Breakdown Warden's fight
 * harder; up to BreachProtocolsSC.MAX of them go into the Breakdown Console with the Key (a right click with one in hand, before the
 * countdown) and are spent with the Key when the Warden falls (kept in the arena's record, never in the block). Meta = the protocol
 * (BreachProtocolsSC: DEAF_COILS «Глухие катушки», SHORT_FUSE «Короткий фитиль», SWARM «Нашествие», ECLIPSE_SHADOW «Тень Затмения»,
 * JAMMING «Помехи») - fixed, append only. Stack 1.
 * <p>
 * Fixed (the coordinator's): the class, the registry name "breachProtocol" (IdeaItemsSC), the metas, the textures
 * items/breachProtocol_<key>. ПД-6's: the tooltip (TooltipSC), the textures, the recipe, NEI, the Console's handling.
 */
public class ItemBreachProtocolSC extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemBreachProtocolSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".breachProtocol");
        setTextureName(Reference.ASSETS + ":breachProtocol_" + BreachProtocolsSC.KEYS[0]);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BreachProtocolsSC.keyOf(stack.getItemDamage());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister r) {
        icons = new IIcon[BreachProtocolsSC.COUNT];
        for (int i = 0; i < icons.length; i++) {
            icons[i] = r.registerIcon(Reference.ASSETS + ":breachProtocol_" + BreachProtocolsSC.KEYS[i]);
        }
        itemIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? itemIcon : icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < BreachProtocolsSC.COUNT; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int p = stack.getItemDamage();
        String key = "sc.tooltip.breachProtocol." + BreachProtocolsSC.keyOf(p);
        String line;
        switch (p) {
            case BreachProtocolsSC.SHORT_FUSE:
                line = com.sc.manual.Lang.tr(key, BreachProtocolsSC.SHORT_FUSE_TICKS / 20, com.sc.mob.WardenRulesSC.FINISH_TICKS / 20);
                break;
            case BreachProtocolsSC.SWARM:
                line = com.sc.manual.Lang.tr(key, String.valueOf(BreachProtocolsSC.SWARM_MUL));
                break;
            case BreachProtocolsSC.JAMMING:
                line = com.sc.manual.Lang.tr(key, BreachProtocolsSC.JAMMING_EVERY / 20);
                break;
            default:
                line = com.sc.manual.Lang.tr(key);
                break;
        }
        com.sc.util.TooltipSC.wrap(list, line, "\u00a7d");
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.breachProtocol.details", BreachProtocolsSC.MAX, BreachProtocolsSC.CRUMBS_EACH,
                Math.round(BreachProtocolsSC.TROPHY_BONUS_EACH * 100F), BreachProtocolsSC.MAX), com.sc.manual.Lang.tr("sc.tooltip.breachProtocol.howto"));
    }
}
