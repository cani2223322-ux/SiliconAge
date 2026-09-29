package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/** Tokamak Coil: eight of them in a ring round the Tokamak (on its level) make it work. */
public class BlockTokamakCoilSC extends Block {

    public BlockTokamakCoilSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".tokamakCoil");
        setBlockTextureName(Reference.ASSETS + ":tokamakCoil");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(4.0F);
        setResistance(12.0F);
        setStepSound(soundTypeMetal);
    }

    /** A coil round a tokamak: its screen (empty hand). */
    @Override
    public boolean onBlockActivated(net.minecraft.world.World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p,
                                    int side, float hx, float hy, float hz) {
        return com.sc.tileentity.TileEntityGeneratorSC.openFromBuild(w, x, y, z, p);
    }
}
