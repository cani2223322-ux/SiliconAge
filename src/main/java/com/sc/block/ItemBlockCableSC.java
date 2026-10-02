package com.sc.block;

import com.sc.conduit.ConduitKind;
import com.sc.energy.CableType;

import java.util.List;

import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class ItemBlockCableSC extends ItemBlockConduitSC {

    public ItemBlockCableSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    protected ConduitKind kind() {
        return ConduitKind.CABLE;
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        CableType[] values = CableType.values();
        int meta = stack.getItemDamage();
        CableType type = values[meta >= 0 && meta < values.length ? meta : 0];
        return super.getUnlocalizedName() + "." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Summary: tier, amps, EU/t, loss (the handbook's cable line). Shift details only where the
     * lang has them (sc.cable.tooltip.<type>, e.g. the SV Singular cable).
     */
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        CableType[] values = CableType.values();
        int meta = stack.getItemDamage();
        CableType type = values[meta >= 0 && meta < values.length ? meta : 0];
        list.add("§7" + Lang.tr("sc.manual.energy.cableline", type.tier.name(), type.tier.getVoltage(), type.maxThroughput(), type.maxAmps, type.lossPerBlock));
        com.sc.util.TooltipSC.more(list, Lang.trOr("sc.cable.tooltip." + type.name().toLowerCase(java.util.Locale.ROOT), null), null);
    }
}
