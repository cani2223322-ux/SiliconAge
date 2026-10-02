package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Gravity Coil: 16 of them - two rings of 8 round the axis, above and below the Singular
 * Reactor (levels 2 and 4 of its 7x7x5 build) - hold its black hole.
 */
public class BlockGravityCoilSC extends Block {

    public BlockGravityCoilSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".gravityCoil");
        setBlockTextureName(Reference.ASSETS + ":gravityCoil");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(15.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 2);
    }

    /** A coil of a Singular Reactor's build: the reactor's screen (empty hand). */
    @Override
    public boolean onBlockActivated(net.minecraft.world.World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p,
                                    int side, float hx, float hy, float hz) {
        return com.sc.tileentity.TileEntityGeneratorSC.openFromBuild(w, x, y, z, p);
    }
}
