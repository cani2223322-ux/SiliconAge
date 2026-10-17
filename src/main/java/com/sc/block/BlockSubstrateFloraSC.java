package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.hazard.StormStateSC;
import com.sc.init.ModCreativeTab;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §3.3, rule ПМ-15; proposal §3 «Флора»): the Substrate's flora - «Проволочник»
 * (Wireweed, a grass of copper threads: sparks while an EMP storm runs - StormStateSC on the client, no light change) and «диодный
 * мох» (diode moss, light MOSS_LIGHT). Crossed quads, no collision, on the rock only; never ticks or spreads. Metas
 * (SubstrateWildBlocksSC): WIREWEED, DIODE_MOSS. Drops: nothing (W may give Wireweed a copper nugget's worth - TODO(balance)),
 * with shears itself. Fixed (the coordinator's): the class, the metas, the registry name; the rest W's.
 * <p>
 * W: stays only on the Substrate's rock (any meta - the turf, a cave's Crystallite, a canyon's floor): anything else under it
 * and it crumbles (nothing drops); replaceable as tall grass (a block placed into it takes its place); IShearable for the
 * shears. The sparks (client, randomDisplayTick): Wireweed in the Substrate while StormStateSC.clientPhase() is WARNING (now and
 * then) or STORM (often) - copper-blue «reddust» specks and a crit now and then; the moss's faint diode flicker always, rarely.
 */
public class BlockSubstrateFloraSC extends Block implements IShearable {

    public static final int MOSS_LIGHT = 3;
    /** Sparks: a Wireweed's random display tick sparks in 1 of this many in the storm, of WARN_ONE_IN while it is coming. */
    private static final int STORM_ONE_IN = 2, WARN_ONE_IN = 6, MOSS_ONE_IN = 12;

    private IIcon[] icons;

    public BlockSubstrateFloraSC() {
        super(Material.plants);
        setHardness(0F);
        setStepSound(soundTypeGrass);
        setBlockName(Reference.ASSETS + ".wild");
        setCreativeTab(ModCreativeTab.TAB);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 1;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    /** The selection box: Wireweed a tall tuft, the moss a low cushion. */
    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        if (world.getBlockMetadata(x, y, z) == SubstrateWildBlocksSC.DIODE_MOSS) {
            setBlockBounds(0.15F, 0F, 0.15F, 0.85F, 0.4F, 0.85F);
        } else {
            setBlockBounds(0.1F, 0F, 0.1F, 0.9F, 0.8F, 0.9F);
        }
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0.1F, 0F, 0.1F, 0.9F, 0.8F, 0.9F);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == SubstrateWildBlocksSC.DIODE_MOSS ? MOSS_LIGHT : 0;
    }

    // ------------------------------------------------------------------ where it grows

    /** Only on the Substrate's rock. */
    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        return y > 0 && world.getBlock(x, y - 1, z) == SubstrateBlocksSC.rock;
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && canBlockStay(world, x, y, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        if (!world.isRemote && !canBlockStay(world, x, y, z)) {
            world.setBlockToAir(x, y, z);                       // crumbles: nothing drops
        }
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, int x, int y, int z) {
        return true;
    }

    // ------------------------------------------------------------------ drops: shears only

    @Override
    public int damageDropped(int meta) {
        return meta >= 0 && meta < SubstrateWildBlocksSC.FLORA_NAMES.length ? meta : 0;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean isShearable(ItemStack item, IBlockAccess world, int x, int y, int z) {
        return true;
    }

    @Override
    public ArrayList<ItemStack> onSheared(ItemStack item, IBlockAccess world, int x, int y, int z, int fortune) {
        ArrayList<ItemStack> out = new ArrayList<ItemStack>();
        out.add(new ItemStack(this, 1, damageDropped(world.getBlockMetadata(x, y, z))));
        return out;
    }

    // ------------------------------------------------------------------ client

    /** ПМ-15: Wireweed sparks while an EMP storm comes and runs (the Substrate's own client copy of it); the moss flickers. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random r) {
        if (!SubstrateSC.is(world)) {
            return;
        }
        int meta = world.getBlockMetadata(x, y, z);
        if (meta == SubstrateWildBlocksSC.DIODE_MOSS) {
            if (r.nextInt(MOSS_ONE_IN) == 0) {
                world.spawnParticle("reddust", x + 0.2D + r.nextDouble() * 0.6D, y + 0.15D + r.nextDouble() * 0.2D, z + 0.2D + r.nextDouble() * 0.6D,
                        0.2D, 1.0D, 0.6D);
            }
            return;
        }
        int phase = StormStateSC.clientPhase();
        if (phase == StormStateSC.CALM || r.nextInt(phase == StormStateSC.STORM ? STORM_ONE_IN : WARN_ONE_IN) != 0) {
            return;
        }
        int n = phase == StormStateSC.STORM ? 3 : 1;
        for (int i = 0; i < n; i++) {                           // copper and blue specks running up the threads
            boolean blue = r.nextBoolean();
            world.spawnParticle("reddust", x + 0.15D + r.nextDouble() * 0.7D, y + 0.1D + r.nextDouble() * 0.7D, z + 0.15D + r.nextDouble() * 0.7D,
                    blue ? 0.35D : 1.0D, blue ? 0.75D : 0.55D, blue ? 1.0D : 0.2D);
        }
        if (phase == StormStateSC.STORM && r.nextInt(4) == 0) {
            world.spawnParticle("crit", x + 0.5D, y + 0.6D, z + 0.5D, (r.nextDouble() - 0.5D) * 0.3D, 0.2D, (r.nextDouble() - 0.5D) * 0.3D);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[SubstrateWildBlocksSC.FLORA_NAMES.length];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":substrate_" + SubstrateWildBlocksSC.FLORA_NAMES[m]);
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
        for (int m = 0; m < SubstrateWildBlocksSC.FLORA_NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }
}
