package com.sc.block;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.tileentity.TileEntityPipeSC;
import com.sc.util.PipeType;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * The 4 fluid pipes (§12.2), one metadata per PipeType. Their items place the pipe into a
 * conduit bundle (BlockConduitSC); a pipe block left in an old world converts itself.
 */
public class BlockPipeSC extends BlockLegacyConduitSC {

    public BlockPipeSC() {
        super(ConduitKind.PIPE, PipeType.values().length, Reference.ASSETS + ".pipeSC");
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityPipeSC te = new TileEntityPipeSC();
        PipeType[] values = PipeType.values();
        te.setPipeType(values[meta >= 0 && meta < values.length ? meta : 0]);
        return te;
    }
}
