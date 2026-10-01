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
                        + "\n" + com.sc.manual.Lang.tr("sc.manual.ores.biomes", com.sc.manual.BookContent.biomes(ore)),
                com.sc.manual.Lang.tr("sc.ore.tooltip.howto"));
    }
}
