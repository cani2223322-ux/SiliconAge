package com.sc.block;

import com.sc.util.OreEntry;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Without this, all 16 ore metadata variants would share BlockOreSC's single unlocalized
 * name and show identical tooltips - this appends the specific ore's name per §10.
 */
public class ItemBlockOreSC extends ItemBlock {

    public ItemBlockOreSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        OreEntry ore = OreEntry.byMeta(stack.getItemDamage());
        return super.getUnlocalizedName() + "." + ore.oreName;
    }

    /** What mines it; under Shift where it lies (the current config), under Ctrl what to do with it. */
    @Override
    public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List list, boolean advanced) {
        OreEntry ore = OreEntry.byMeta(stack.getItemDamage());
        list.add("§7" + com.sc.manual.Lang.tr("sc.manual.ores.tool",
                com.sc.manual.Lang.tr("sc.manual.ores.tool." + ore.tool.name().toLowerCase(java.util.Locale.ROOT))));
        com.sc.util.ConfigSC.OreGenSettings gen = com.sc.util.ConfigSC.settingsFor(ore);
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.manual.ores.where", gen.minY, gen.maxY, gen.veinSize, gen.veinsPerChunk)
                        + dimensionsSuffix() + "\n" + com.sc.manual.Lang.tr("sc.manual.ores.biomes", com.sc.manual.BookContent.biomes(ore)),
                com.sc.manual.Lang.tr("sc.ore.tooltip.howto"));
    }

    /** " (Overworld only)" / " (dimensions: ...)" after sc.manual.ores.where - from ConfigSC.oreDimensions, as OreGenSC reads it. */
    public static String dimensionsSuffix() {
        int[] dims = com.sc.util.ConfigSC.oreDimensions;
        if (dims == null) {
            dims = new int[] {0};                   // OreGenSC.generatesIn: no list = Overworld
        }
        if (dims.length == 0) {
            return " (" + com.sc.manual.Lang.tr("sc.manual.ores.dims.none") + ")";
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < dims.length; i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(com.sc.manual.Lang.trOr("sc.wl.dim." + dims[i], com.sc.manual.Lang.tr("sc.wl.dim.other", dims[i])));
        }
        return " (" + com.sc.manual.Lang.tr(dims.length == 1 ? "sc.manual.ores.dims.only" : "sc.manual.ores.dims.list",
                names.toString()) + ")";
    }
}
