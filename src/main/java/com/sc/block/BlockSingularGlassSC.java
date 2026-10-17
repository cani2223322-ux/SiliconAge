package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.init.ModCreativeTab;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §3.3, rule ПМ-14): «Сингулярное стекло» - the Rift's floor and its puncture pillar;
 * see-through (a block of its own: isOpaqueCube doesn't depend on the meta in 1.7.10). Metas (SubstrateWildBlocksSC): GLASS,
 * PILLAR_CORE (light CORE_LIGHT - few: only the pillar's core column, laid by populate with glow()). Decoration, drops itself.
 * Fixed (the coordinator's): the class, the metas' names, the registry name; the rest W's.
 * <p>
 * W: a stone pickaxe mines the glass faster (any hand gets it - glass needs no tool); the core can't be mined, blown up or
 * taken by a boss (hardness -1, CORE_RESISTANCE, canEntityDestroy - the pillar's light stays). The faces between two blocks of
 * one meta are not drawn (a floor or a pillar is one see-through mass), between the glass and the core they are - the core
 * shows through the pillar's glass. The glass is in the creative tab, the core is not (NEI hides it too - package D). Light
 * opacity 1 (as ice's 3, not glass's 0): the chunks' height map stops at it, so World.getHeightValue over the void gives the
 * floor's top (Y 3), not the bedrock under it - the spawn director and populate see the floor as the ground it is.
 */
public class BlockSingularGlassSC extends Block {

    public static final int CORE_LIGHT = 12;
    /** The core: as bedrock against explosions. */
    public static final float CORE_RESISTANCE = 6000000F;

    private IIcon[] icons;

    public BlockSingularGlassSC() {
        super(Material.glass);
        setHardness(3.0F);
        setResistance(30F);
        setStepSound(soundTypeGlass);
        setBlockName(Reference.ASSETS + ".wild");
        setCreativeTab(ModCreativeTab.TAB);
        setHarvestLevel("pickaxe", 1);
        setLightOpacity(1);                                     // the height map counts it (the Rift's floor is the top there)
    }

    /** (x, y, z) is the neighbour's place: no face against a block of this one with the same meta. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        ForgeDirection d = ForgeDirection.getOrientation(side);
        int ox = x - d.offsetX, oy = y - d.offsetY, oz = z - d.offsetZ;
        if (world.getBlock(x, y, z) == this && world.getBlock(ox, oy, oz) == this
                && world.getBlockMetadata(x, y, z) == world.getBlockMetadata(ox, oy, oz)) {
            return false;
        }
        return super.shouldSideBeRendered(world, x, y, z, side);
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
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == SubstrateWildBlocksSC.PILLAR_CORE ? CORE_LIGHT : 0;
    }

    /** The core can't be mined (the glass round it can). */
    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) == this && world.getBlockMetadata(x, y, z) == SubstrateWildBlocksSC.PILLAR_CORE ? -1F
                : super.getBlockHardness(world, x, y, z);
    }

    @Override
    public float getExplosionResistance(Entity exploder, World world, int x, int y, int z, double ex, double ey, double ez) {
        return world.getBlockMetadata(x, y, z) == SubstrateWildBlocksSC.PILLAR_CORE ? CORE_RESISTANCE / 5F
                : super.getExplosionResistance(exploder, world, x, y, z, ex, ey, ez);
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return world.getBlockMetadata(x, y, z) != SubstrateWildBlocksSC.PILLAR_CORE && super.canEntityDestroy(world, x, y, z, entity);
    }

    /** The glass drops itself; the core nothing (it is never mined - a creative player's break leaves no item). */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        if (meta == SubstrateWildBlocksSC.PILLAR_CORE) {
            return new ArrayList<ItemStack>();
        }
        return super.getDrops(world, x, y, z, meta, fortune);
    }

    @Override
    public int damageDropped(int meta) {
        return meta >= 0 && meta < SubstrateWildBlocksSC.GLASS_NAMES.length ? meta : 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[SubstrateWildBlocksSC.GLASS_NAMES.length];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":substrate_" + SubstrateWildBlocksSC.GLASS_NAMES[m]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons == null ? null : icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    /** The tab lists the glass only: the core is the world's (mined, it drops itself - a light the Rift gave). */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, SubstrateWildBlocksSC.GLASS));
    }
}
