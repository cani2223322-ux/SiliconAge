package com.sc.block;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.tileentity.TileEntityItemTubeSC;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Item Pneumatic Tube (§1/§12.2). Its item places the tube into a conduit bundle
 * (BlockConduitSC); a tube block left in an old world converts itself.
 */
public class BlockItemTubeSC extends BlockLegacyConduitSC {

    public BlockItemTubeSC() {
        super(ConduitKind.TUBE, 1, Reference.ASSETS + ".tubeItemPneumatic");
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityItemTubeSC();
    }
}
