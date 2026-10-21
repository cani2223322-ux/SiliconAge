package com.sc.block;

import java.util.List;

import com.sc.conduit.ConduitKind;
import com.sc.manual.Lang;
import com.sc.util.PipeType;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class ItemBlockPipeSC extends ItemBlockConduitSC {

    public ItemBlockPipeSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    protected ConduitKind kind() {
        return ConduitKind.PIPE;
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        PipeType[] values = PipeType.values();
        int meta = stack.getItemDamage();
        PipeType type = values[meta >= 0 && meta < values.length ? meta : 0];
        return super.getUnlocalizedName() + "." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** СТ-1 (audit 2026-10-20): the rate, what corrosive fluids do to it (PTFE: nothing), how its connectors are set. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        PipeType[] values = PipeType.values();
        int meta = stack.getItemDamage();
        PipeType type = values[meta >= 0 && meta < values.length ? meta : 0];
        list.add("\u00a77" + Lang.tr("sc.pipe.tooltip.rate", type.throughput));
        list.add(type.chemicallyResistant ? "\u00a7a" + Lang.tr("sc.pipe.tooltip.ptfe")
                : "\u00a7c" + Lang.tr("sc.pipe.tooltip.corrosive", com.sc.tileentity.TileEntityConduitBundleSC.CORROSION_TICKS / 20));
        com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.pipe.tooltip.howto"));
    }
}
