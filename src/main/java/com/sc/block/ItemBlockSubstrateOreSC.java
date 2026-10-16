package com.sc.block;

import java.util.List;
import java.util.Locale;

import com.sc.dim.SubstrateOreTableSC;
import com.sc.init.ModBlocks;
import com.sc.manual.Lang;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * A Substrate ore as an item (creative, NEI): named «<ore> в кристаллите» (sc.dim.ore.name with the ordinary ore's
 * name); the tooltip below.
 */
public class ItemBlockSubstrateOreSC extends ItemBlock {

    public ItemBlockSubstrateOreSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return OreEntry.byMeta(damage).meta();
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + OreEntry.byMeta(stack.getItemDamage()).oreName;
    }

    /** «Халькопиритовая руда в кристаллите»: the ordinary ore's name in sc.dim.ore.name. */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        ItemStack plain = new ItemStack(ModBlocks.oreSC, 1, OreEntry.byMeta(stack.getItemDamage()).meta());
        return Lang.tr("sc.dim.ore.name", plain.getDisplayName());
    }

    /**
     * What it is, the pickaxe; under Shift where it lies here - the ore's Y range, vein size and veins a chunk in the
     * Substrate (sc.manual.ores.where with SubstrateOreTableSC's numbers; the rare ores' deeper share); under Ctrl the
     * ordinary ore's «what to do with it».
     */
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        OreEntry ore = OreEntry.byMeta(stack.getItemDamage());
        list.add("§7" + Lang.tr("sc.tooltip.substrateOre"));
        list.add("§8" + Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool." + ore.tool.name().toLowerCase(Locale.ROOT))));
        ConfigSC.OreGenSettings gen = ConfigSC.settingsFor(ore);
        String where = Lang.tr("sc.tooltip.substrateOre.details") + "\n" + Lang.tr("sc.manual.ores.where", SubstrateOreTableSC.minY(ore),
                SubstrateOreTableSC.maxY(ore), gen.veinSize, Math.round(gen.veinsPerChunk * SubstrateOreTableSC.veinMultiplier(ore)));
        if (SubstrateOreTableSC.deepShare(ore) > 0F) {
            where += "\n" + Lang.tr("sc.tooltip.substrateOre.deep", Math.round(SubstrateOreTableSC.deepShare(ore) * 100F),
                    SubstrateOreTableSC.DEEP_MIN_Y, SubstrateOreTableSC.DEEP_MAX_Y);
        }
        TooltipSC.more(list, where, Lang.tr("sc.ore.tooltip.howto"));
    }
}
