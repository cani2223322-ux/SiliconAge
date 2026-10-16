package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * «Порода Подложки» (docs/plan-substrate.md §3.2): the Substrate's rock, one block of opaque full cubes by meta
 * (SubstrateBlocksSC: Crystallite, the four Oxide Crusts, Copper Trace, Trace Node, Quartz Turf). A stone pickaxe,
 * hardness 2, blast resistance as stone's.
 * <ul>
 * <li>isReplaceableOreGen: only Crystallite and only for a generator that asks for this block - vanilla's
 * WorldGenMinable doesn't look at metas, without this the Substrate's ores would eat the crust, the turf and the
 * traces; anyone asking for minecraft:stone (other mods' ores, OreGenSC) gets no.</li>
 * <li>the drops: the turf crumbles to Crystallite, a trace (and its node) gives 1..2 Crushed Copper Ore (+ fortune),
 * the rest itself; Silk Touch keeps any of them.</li>
 * <li>a Trace Node glows (light 6); nothing else does. The turf never ticks or spreads.</li>
 * </ul>
 */
public class BlockSubstrateRockSC extends Block {

    public static final int NODE_LIGHT = 6;
    /** A trace's drop: 1..TRACE_DROP_MAX Crushed Copper Ore, + 0..fortune. */
    public static final int TRACE_DROP_MAX = 2;

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    @SideOnly(Side.CLIENT)
    private IIcon turfTop, turfSide;

    public BlockSubstrateRockSC() {
        super(Material.rock);
        setBlockName(Reference.ASSETS + ".substrateRock");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(2.0F);
        setResistance(10.0F);
        setStepSound(soundTypeStone);
        setHarvestLevel("pickaxe", 1);                          // a stone pickaxe, every meta
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        String[] tex = {"crystallite", "oxide_violet", "oxide_blue", "oxide_gold", "oxide_green", "copper_trace", "trace_node", "crystallite"};
        icons = new IIcon[SubstrateBlocksSC.ROCK_METAS];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":substrate_" + tex[m]);
        }
        turfTop = register.registerIcon(Reference.ASSETS + ":substrate_turf_top");
        turfSide = register.registerIcon(Reference.ASSETS + ":substrate_turf_side");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (icons == null) {
            return null;
        }
        if (meta == SubstrateBlocksSC.QUARTZ_TURF) {
            return side == 1 ? turfTop : side == 0 ? icons[SubstrateBlocksSC.CRYSTALLITE] : turfSide;
        }
        return icons[meta >= 0 && meta < icons.length ? meta : SubstrateBlocksSC.CRYSTALLITE];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < SubstrateBlocksSC.ROCK_METAS; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }

    @Override
    public int damageDropped(int meta) {
        return meta >= 0 && meta < SubstrateBlocksSC.ROCK_METAS ? meta : SubstrateBlocksSC.CRYSTALLITE;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        if (meta == SubstrateBlocksSC.COPPER_TRACE || meta == SubstrateBlocksSC.TRACE_NODE) {
            ItemStack copper = ModItems.crushedOre.stackOf(com.sc.util.Material.COPPER);
            if (copper != null) {
                copper.stackSize = 1 + world.rand.nextInt(TRACE_DROP_MAX) + (fortune > 0 ? world.rand.nextInt(fortune + 1) : 0);
                drops.add(copper);
            }
            return drops;
        }
        drops.add(new ItemStack(this, 1, meta == SubstrateBlocksSC.QUARTZ_TURF ? SubstrateBlocksSC.CRYSTALLITE : damageDropped(meta)));
        return drops;
    }

    /** Silk Touch keeps every meta (the turf and the traces too) - ItemBlockSubstrateRockSC has subtypes. */
    @Override
    protected boolean canSilkHarvest() {
        return true;
    }

    /**
     * Only Crystallite is ore ground, and only for a generator that asks for this very block (SubstrateOreGenSC): the
     * crust, the turf and the traces stay; minecraft:stone generators (other mods, OreGenSC) find nothing here.
     */
    @Override
    public boolean isReplaceableOreGen(World world, int x, int y, int z, Block target) {
        return target == this && world.getBlockMetadata(x, y, z) == SubstrateBlocksSC.CRYSTALLITE;
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        if (b != this) {
            return b.getLightValue(world, x, y, z);
        }
        return world.getBlockMetadata(x, y, z) == SubstrateBlocksSC.TRACE_NODE ? NODE_LIGHT : 0;
    }
}
