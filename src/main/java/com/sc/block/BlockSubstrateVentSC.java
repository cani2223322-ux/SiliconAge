package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.tileentity.TileEntityArcCrackSC;
import com.sc.tileentity.TileEntitySubstrateVentSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §4.2-§4.5, rules ПА-4..ПА-8): the Substrate's vents - unbreakable (hardness -1,
 * resistance 6 000 000, canEntityDestroy false), no drop, never moved by a quarry / drill / Spatial IO (W asks the coordinator
 * for the AE2 IMC if needed). Metas (SubstrateWildBlocksSC): FUMAROLE (helium, only into the mod's pipe or tank beside it),
 * ARC_CRACK (32 EU/t free, only into a cable beside it - its own tile TileEntityArcCrackSC; 1 «Пробой» a second or -0.5% of every
 * armour piece's charge within 2), ION_GEYSER (the Rift: a discharge every 6..10 s within 3 - 3 «Пробой» and -2%), CRACKED_RTG
 * (the Oxide Fields: a radiation source). Fixed (the coordinator's): the class, the metas, the tiles by meta, the registry name;
 * the rest W's.
 * <p>
 * W: the quarry and the drill skip a block of hardness below 0 (DrillLogicSC, TileEntityQuarrySC); AE2's Spatial IO is told by
 * IMC (SubstrateCompatSC.sendImc). A neighbour's change tells the crack's tile to look for cables again. The particles are the
 * client's own (randomDisplayTick): the fumarole's helium steam, the crack's sparks, the geyser's ion haze, the RTG's greenish
 * glow out of its cracks; the geyser's sparks before a discharge come from the server (its tile) - every player sees the
 * same warning. Not in the creative tab (NEI hides them - package D): they are the world's, not a player's.
 */
public class BlockSubstrateVentSC extends Block {

    private IIcon[] icons;

    public BlockSubstrateVentSC() {
        super(Material.rock);
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeStone);
        setBlockName(Reference.ASSETS + ".wild");
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return meta >= 0 && meta < SubstrateWildBlocksSC.VENT_NAMES.length;
    }

    /** ARC_CRACK - its energy tile; the others the plain vent tile. */
    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return meta == SubstrateWildBlocksSC.ARC_CRACK ? new TileEntityArcCrackSC() : new TileEntitySubstrateVentSC();
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    /** A cable put beside a crack (or taken away): its tile looks at its faces again. */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityArcCrackSC) {
                ((TileEntityArcCrackSC) te).neighbourChanged();
            }
        }
    }

    @Override
    public void onNeighborChange(IBlockAccess world, int x, int y, int z, int tileX, int tileY, int tileZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityArcCrackSC) {
            ((TileEntityArcCrackSC) te).neighbourChanged();
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random r) {
        if (!world.isAirBlock(x, y + 1, z)) {
            return;
        }
        double cx = x + 0.5D, top = y + 1.02D, cz = z + 0.5D;
        switch (world.getBlockMetadata(x, y, z)) {
            case SubstrateWildBlocksSC.FUMAROLE:                     // a cold white plume, a hiss now and then
                for (int i = 0; i < 3; i++) {
                    world.spawnParticle(i == 0 ? "cloud" : "explode", cx + (r.nextDouble() - 0.5D) * 0.4D, top, cz + (r.nextDouble() - 0.5D) * 0.4D,
                            0D, 0.06D + r.nextDouble() * 0.05D, 0D);
                }
                if (r.nextInt(8) == 0) {
                    world.playSound(cx, top, cz, "random.fizz", 0.15F, 0.6F + r.nextFloat() * 0.2F, false);
                }
                break;
            case SubstrateWildBlocksSC.ARC_CRACK:                    // arcs along the crack
                for (int i = 0; i < 2; i++) {
                    world.spawnParticle("reddust", x + r.nextDouble(), top, z + r.nextDouble(), 0.45D, 0.8D, 1.0D);
                }
                if (r.nextInt(3) == 0) {
                    world.spawnParticle("magicCrit", x + r.nextDouble(), top, z + r.nextDouble(), 0D, 0.1D, 0D);
                }
                break;
            case SubstrateWildBlocksSC.ION_GEYSER:                   // a violet ion haze over the vent
                world.spawnParticle("witchMagic", cx + (r.nextDouble() - 0.5D) * 0.6D, top + r.nextDouble() * 0.3D,
                        cz + (r.nextDouble() - 0.5D) * 0.6D, 0D, 0.05D, 0D);
                break;
            case SubstrateWildBlocksSC.CRACKED_RTG:                  // a greenish glow out of the cracks
                if (r.nextInt(2) == 0) {
                    world.spawnParticle("reddust", x + 0.1D + r.nextDouble() * 0.8D, y + 0.2D + r.nextDouble() * 0.8D, z + 0.1D + r.nextDouble() * 0.8D,
                            0.45D, 1.0D, 0.25D);
                }
                break;
            default:
                break;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[SubstrateWildBlocksSC.VENT_NAMES.length];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":substrate_" + SubstrateWildBlocksSC.VENT_NAMES[m]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons == null ? null : icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < SubstrateWildBlocksSC.VENT_NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }
}
