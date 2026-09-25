package com.sc.block;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.energy.CableType;
import com.sc.tileentity.TileEntityCableSC;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * The 5 cables (§9.1/§12.1), one metadata per CableType. Their items place the cable into a
 * conduit bundle (BlockConduitSC); a cable block left in an old world converts itself.
 */
public class BlockCableSC extends BlockLegacyConduitSC {

    public BlockCableSC() {
        super(ConduitKind.CABLE, CableType.values().length, Reference.ASSETS + ".cableSC");
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityCableSC te = new TileEntityCableSC();
        CableType[] values = CableType.values();
        te.setCableType(values[meta >= 0 && meta < values.length ? meta : 0]);
        return te;
    }
}
