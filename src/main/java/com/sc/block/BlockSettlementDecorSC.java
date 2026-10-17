package com.sc.block;

import java.util.ArrayList;
import java.util.Random;

import com.sc.Reference;
import com.sc.init.ModBlocks;
import com.sc.util.Material;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §4.5, ПП-7): «Отделка поселения» - what the settlement generator builds
 * with where the plan wants the mod's valuables: the metal blocks of the walls, posts, roofs and the Metallurgist's stock,
 * the Heart's Tokamak Coils and pedestal (this block, one meta per look), the lead glass of the caps, windows and the
 * clean room (Glass). The real blocks are guarded only by Forge's events, and AE2's Mass Cannon, Thaumic Tinkerer's
 * Dislocation focus, the Ender Quarry and IC2's mining laser break blocks without a BreakEvent - so the settlements hold
 * none of them. The trim wears the real block's own icons, but is unbreakable (hardness -1: every such tool skips it),
 * blast-proof, no mob destroys it, it drops nothing (a command that removes it leaves nothing either) and creative's
 * pick-block gives nothing; AE2's Spatial IO blacklist has it (SettlementCompatSC). No tab, hidden in NEI. Metas fixed -
 * NAMES, append only. Lang tile.siliconage.settlementDecor.<name>.name / tile.siliconage.settlementGlass.name,
 * sc.tooltip.settlementDecor.
 */
public class BlockSettlementDecorSC extends Block {

    /** Metas: the metals of the plan's palette, then the Tokamak Coil. */
    public static final int STEEL = 0, TITANIUM = 1, ALUMINIUM = 2, NIOBIUM = 3, ZIRCONIUM = 4, TANTALUM = 5, HAFNIUM = 6, TUNGSTEN = 7,
            COIL = 8;
    public static final String[] NAMES = {"steel", "titanium", "aluminium", "niobium", "zirconium", "tantalum", "hafnium", "tungsten",
            "tokamakCoil"};
    /** The metal whose block a meta wears (by meta, COIL past the end). */
    public static final Material[] METALS = {Material.STEEL, Material.TITANIUM, Material.ALUMINIUM, Material.NIOBIUM, Material.ZIRCONIUM,
            Material.TANTALUM, Material.HAFNIUM, Material.TUNGSTEN};

    public BlockSettlementDecorSC() {
        super(net.minecraft.block.material.Material.iron);
        setBlockName(Reference.ASSETS + ".settlementDecor");
        setBlockTextureName(Reference.ASSETS + ":tokamakCoil");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeMetal);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    /** The meta that looks like a block of `m`, -1 none in the palette. */
    public static int metaOf(Material m) {
        for (int i = 0; i < METALS.length; i++) {
            if (METALS[i] == m) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    /** Creative's middle click: nothing - an unbreakable copy would stand wherever it was put. */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    // ------------------------------------------------------------------ the look: the real blocks' icons

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        IIcon i = null;
        if (meta == COIL) {
            i = ModBlocks.tokamakCoil == null ? null : ModBlocks.tokamakCoil.getIcon(side, 0);
        } else if (meta >= 0 && meta < METALS.length) {
            ItemStack s = ModBlocks.metalBlock == null ? null : BlockMetalSC.stackOf(METALS[meta], 1);
            Block b = s == null ? null : Block.getBlockFromItem(s.getItem());
            i = b == null ? null : b.getIcon(side, s.getItemDamage());
        }
        return i != null ? i : blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(getTextureName());      // the fallback; the metas wear the real blocks' icons
    }

    /** The lead glass of a settlement: the real one's look, the trim's rules. */
    public static class Glass extends BlockGlass {

        public Glass() {
            super(net.minecraft.block.material.Material.glass, false);
            setBlockName(Reference.ASSETS + ".settlementGlass");
            setBlockTextureName(Reference.ASSETS + ":leadGlass");
            setBlockUnbreakable();
            setResistance(6000000F);
            setStepSound(soundTypeGlass);
        }

        @Override
        public int quantityDropped(Random random) {
            return 0;
        }

        @Override
        public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
            return new ArrayList<ItemStack>();
        }

        @Override
        public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
            return false;
        }

        @Override
        public boolean canDropFromExplosion(Explosion explosion) {
            return false;
        }

        @Override
        public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
            return null;
        }
    }
}
