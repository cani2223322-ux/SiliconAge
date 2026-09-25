package com.sc.block;

import com.sc.conduit.ConduitKind;
import com.sc.energy.CableType;

import net.minecraft.block.Block;
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
}
