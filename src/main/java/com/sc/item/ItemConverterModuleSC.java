package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.ForeignEnergySC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * The Energy Converter's own expansion modules (one metadata per Kind): the Channel Amplifier, the
 * Efficiency module and the two energy cards. They go into the converter's expansion slots only.
 */
public class ItemConverterModuleSC extends Item {

    public enum Kind {
        /** Packets a tick x2 each (at most 3 count: x8). */
        AMPLIFIER("converterAmplifier", ForeignEnergySC.MAX_AMPLIFIERS),
        /** Loss 2 % lower each (5 -> 3 -> 1; at most 2 count). */
        EFFICIENCY("converterEfficiency", ForeignEnergySC.MAX_EFFICIENCY),
        /** The EU-J pair (Mekanism joules) - only with Mekanism installed. */
        CARD_MEKANISM("converterCardMekanism", 1),
        /** The EU-gJ pair (Galacticraft) - only with Galacticraft installed. */
        CARD_GALACTICRAFT("converterCardGalacticraft", 1);

        public final String textureName;
        /** How many count (the slot takes no more). */
        public final int max;

        Kind(String textureName, int max) {
            this.textureName = textureName;
            this.max = max;
        }

        public static Kind byMeta(int meta) {
            Kind[] v = values();
            return v[meta >= 0 && meta < v.length ? meta : 0];
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The energy a card opens, or null. */
        public ForeignEnergySC.Kind energy() {
            return this == CARD_MEKANISM ? ForeignEnergySC.Kind.J : this == CARD_GALACTICRAFT ? ForeignEnergySC.Kind.GJ : null;
        }

        /** A card whose mod isn't installed: it does nothing (red in the converter's screen). */
        public boolean inert() {
            return energy() != null && !energy().modPresent();
        }
    }

    private IIcon[] icons;

    public ItemConverterModuleSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".converterModule");
    }

    public static Kind kindOf(ItemStack s) {
        return Kind.byMeta(s.getItemDamage());
    }

    public ItemStack stackOf(Kind kind) {
        return new ItemStack(this, 1, kind.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + kindOf(stack).key();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        Kind k = kindOf(stack);
        list.add("§7" + Lang.tr("sc.conv.module.short." + k.key()));
        if (k.inert()) {
            list.add("§c" + Lang.tr("sc.conv.module.nomod", k == Kind.CARD_MEKANISM ? "Mekanism" : "Galacticraft"));
        }
        if (!ForeignEnergySC.anyPresent()) {
            list.add("§c" + Lang.tr("sc.conv.tooltip.noforeign"));
        }
        String details = Lang.tr("sc.conv.module.details." + k.key());
        if (k.max > 1) {
            details += "\n" + Lang.tr("sc.conv.module.max", k.max);
        }
        com.sc.util.TooltipSC.more(list, details, Lang.tr("sc.conv.module.howto"));
    }

    @Override
    public void registerIcons(IIconRegister register) {
        Kind[] kinds = Kind.values();
        icons = new IIcon[kinds.length];
        for (Kind k : kinds) {
            icons[k.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + k.textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[Kind.byMeta(meta).ordinal()];
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        if (!ForeignEnergySC.anyPresent()) {
            return;                                   // no other energy in this game: the converter has no use
        }
        for (Kind k : Kind.values()) {
            list.add(new ItemStack(item, 1, k.ordinal()));
        }
    }
}
