package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.machine.UpgradeType;
import com.sc.manual.Lang;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/** Machine upgrades (UpgradeType), one metadata per kind - they go into a machine's upgrade slots. */
public class ItemUpgradeSC extends Item {

    private IIcon[] icons;

    public ItemUpgradeSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".upgrade");
    }

    public static UpgradeType typeOf(ItemStack stack) {
        return UpgradeType.byMeta(stack.getItemDamage());
    }

    public ItemStack stackOf(UpgradeType type) {
        return new ItemStack(this, 1, type.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + typeOf(stack).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String name = typeOf(stack).name().toLowerCase(java.util.Locale.ROOT);
        String details = Lang.tr("sc.upgrade.tooltip." + name);
        if (typeOf(stack).storageOnly()) {
            // energy storage modules: a summary line, what it does on Shift, how to use it on Ctrl
            list.add("§7" + Lang.tr("sc.upgrade.tooltip.short." + name));
            if (typeOf(stack) == UpgradeType.OUTPUT_SPLITTER && !com.sc.tileentity.TileEntityEnergyStorageSC.overdriveWorks()) {
                details += "\n" + Lang.tr("sc.upgrade.tooltip.output_splitter.noiu");   // IC2 without IU: one packet a tile
            }
            com.sc.util.TooltipSC.more(list, details,
                    Lang.tr("sc.upgrade.tooltip.howto." + name) + "\n" + Lang.tr("sc.upgrade.tooltip.slot.storage"));
            return;
        }
        com.sc.util.TooltipSC.more(list, details, Lang.tr("sc.upgrade.tooltip.slot.where", where(stack)));
    }

    /** "machines, generators, storages, the field generator" - every block whose upgrade slots take it (their own checks). */
    private static String where(ItemStack stack) {
        UpgradeType t = typeOf(stack);
        StringBuilder sb = new StringBuilder();
        if (!t.generatorOnly() && !t.fieldOnly() && !t.storageOnly()) {
            sb.append(Lang.tr("sc.book.modules.w.machines"));
        }
        if (t.forGenerators()) {
            sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.book.modules.w.generators"));
        }
        if (com.sc.tileentity.TileEntityEnergyStorageSC.acceptsUpgrade(stack)) {
            sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.book.modules.w.storages"));
        }
        if (com.sc.tileentity.TileEntityFieldGeneratorSC.isFieldUpgrade(stack)) {
            sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.book.modules.w.field"));
        }
        return sb.toString();
    }

    @Override
    public void registerIcons(IIconRegister register) {
        UpgradeType[] types = UpgradeType.values();
        icons = new IIcon[types.length];
        for (UpgradeType type : types) {
            icons[type.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + type.textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[UpgradeType.byMeta(meta).ordinal()];
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (UpgradeType type : UpgradeType.values()) {
            list.add(new ItemStack(item, 1, type.ordinal()));
        }
    }
}
