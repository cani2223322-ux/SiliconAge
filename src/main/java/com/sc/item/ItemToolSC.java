package com.sc.item;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.SCToolType;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

/** One instance per SCToolType (§13.5) - see SCToolType's javadoc for why these aren't metadata variants. */
public class ItemToolSC extends Item {

    private final SCToolType type;
    private IIcon icon;

    public ItemToolSC(SCToolType type) {
        this.type = type;
        setMaxDamage(type.durability);
        setNoRepair();          // worn moulds/photomasks can't be merged back to full by the vanilla repair recipe
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    public SCToolType getType() {
        return type;
    }

    /**
     * СТ-1 (audit 2026-10-20): what the tool / mould is for, the machines it works in (their recipes - MadeInSC), how many
     * operations it lasts; under Ctrl where it is made (a lang ".howto" overrides it).
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List list,
                               boolean advanced) {
        String key = "sc.tool.tooltip." + type.name().toLowerCase(java.util.Locale.ROOT);
        list.add("\u00a77" + com.sc.manual.Lang.tr(key));
        String works = MadeInSC.worksIn(stack);
        if (!works.isEmpty()) {
            list.add("\u00a77" + com.sc.manual.Lang.tr("sc.tool.tooltip.worksin", works));
        }
        list.add("\u00a77" + com.sc.manual.Lang.tr("sc.tool.tooltip.wear", type.durability));
        String made = MadeInSC.madeIn(stack);
        com.sc.util.TooltipSC.more(list, null, com.sc.manual.Lang.trOr(key + ".howto",
                made.isEmpty() ? null : com.sc.manual.Lang.tr("sc.tooltip.madein", made)));
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":" + type.textureName);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }
}
