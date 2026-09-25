package com.sc.block;

import java.util.List;

import com.sc.conduit.ConduitKind;
import com.sc.init.ModCreativeTab;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * The cable / pipe / tube blocks from before conduit bundles. They stay registered because their
 * items are the conduit items (recipes, OreDict, NEI, the handbook all use them) and because old
 * worlds still contain them: such a block turns itself into a conduit bundle on its first tick
 * (its tile entity does it, keeping type and fluid). New ones are never placed - the items put
 * their conduit into a bundle (ItemBlockConduitSC).
 */
public abstract class BlockLegacyConduitSC extends Block {

    private final ConduitKind kind;
    private final int variants;

    protected BlockLegacyConduitSC(ConduitKind kind, int variants, String name) {
        super(BlockConduitSC.CONDUIT);
        this.kind = kind;
        this.variants = variants;
        setBlockName(name);
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(0.8F);
        setResistance(4.0F);
        setStepSound(soundTypeMetal);
        float lo = kind.lo(), hi = kind.hi();
        setBlockBounds(lo, lo, lo, hi, hi, hi);
    }

    public ConduitKind kind() {
        return kind;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        // the conduit textures are registered by the bundle block (BlockConduitSC)
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        IIcon[] arms = kind == ConduitKind.CABLE ? BlockConduitSC.cableArm : kind == ConduitKind.PIPE ? BlockConduitSC.pipeArm : null;
        if (arms == null) {
            return BlockConduitSC.tubeArm;
        }
        return arms[meta >= 0 && meta < arms.length ? meta : 0];
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < variants; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public int getRenderType() {
        return BlockConduitSC.renderId;
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
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float lo = kind.lo(), hi = kind.hi();
        setBlockBounds(lo, lo, lo, hi, hi, hi);
    }
}
