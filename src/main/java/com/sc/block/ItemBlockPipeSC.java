package com.sc.block;

import com.sc.conduit.ConduitKind;
import com.sc.util.PipeType;

import net.minecraft.block.Block;
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
}
