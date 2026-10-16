package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.init.ModBlocks;
import com.sc.init.ModCreativeTab;
import com.sc.util.OreEntry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * «Руда Подложки» (docs/plan-substrate-tech.md §3.4): the 16 ores of the mod (meta = OreEntry) set in Crystallite. It
 * breaks into the ordinary ore of the same meta (ModBlocks.oreSC) - so every processing chain, NEI, the filters and
 * the quarry work unchanged; Silk Touch gives that ore too (no stacks of this block in survival). Harvest level per
 * ore as oreSC's, hardness 3.
 */
public class BlockSubstrateOreSC extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockSubstrateOreSC() {
        super(Material.rock);
        setBlockName(Reference.ASSETS + ".substrateOre");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.0F);
        setResistance(5.0F);
        setStepSound(soundTypeStone);
        for (OreEntry ore : OreEntry.values()) {
            setHarvestLevel("pickaxe", ore.tool.level, ore.meta());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[OreEntry.values().length];
        for (OreEntry ore : OreEntry.values()) {
            icons[ore.meta()] = register.registerIcon(Reference.ASSETS + ":substrate_ore_" + ore.oreName);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons == null ? null : icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (OreEntry ore : OreEntry.values()) {
            list.add(new ItemStack(item, 1, ore.meta()));
        }
    }

    @Override
    public int damageDropped(int meta) {
        return OreEntry.byMeta(meta).meta();
    }

    /** The ordinary ore of the same meta. */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        drops.add(new ItemStack(ModBlocks.oreSC, 1, damageDropped(meta)));
        return drops;
    }

    /** Silk Touch gives the ordinary ore as well (getDrops). */
    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
        return false;
    }
}
