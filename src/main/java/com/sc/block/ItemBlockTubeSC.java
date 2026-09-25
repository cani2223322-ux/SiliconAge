package com.sc.block;

import com.sc.conduit.ConduitKind;

import net.minecraft.block.Block;

public class ItemBlockTubeSC extends ItemBlockConduitSC {

    public ItemBlockTubeSC(Block block) {
        super(block);
    }

    @Override
    protected ConduitKind kind() {
        return ConduitKind.TUBE;
    }
}
