package com.sc.block;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.Material;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Blocks of the mod's metals (9 ingots, as a block of iron): every metal with an ingot but lead,
 * which has its own block (the radiation shield). Split over two blocks of 16 (metadata); the
 * order is saved in worlds - append only. A beacon's base, like iron and gold.
 */
public class BlockMetalSC extends Block {

    /** Every metal block, in metadata order across both blocks. Append only. */
    public static final Material[] METALS = {Material.COPPER, Material.TIN, Material.ZINC, Material.GERMANIUM, Material.TITANIUM,
            Material.TUNGSTEN, Material.TANTALUM, Material.ZIRCONIUM, Material.PLATINUM, Material.NEODYMIUM, Material.LITHIUM,
            Material.ALUMINIUM, Material.SILVER, Material.GALLIUM, Material.INDIUM, Material.NIOBIUM, Material.HAFNIUM,
            Material.PALLADIUM, Material.CERIUM, Material.LANTHANUM, Material.STEEL, Material.MAGNESIUM};

    private final int offset, count;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockMetalSC(int offset) {
        super(net.minecraft.block.material.Material.iron);
        this.offset = offset;
        this.count = Math.min(16, METALS.length - offset);
        setBlockName(Reference.ASSETS + ".metalBlock" + (offset == 0 ? "" : "2"));
        setCreativeTab(ModCreativeTab.TAB);
        setStepSound(soundTypeMetal);
        setHardness(5.0F);
        setResistance(10.0F);
        for (int m = 0; m < count; m++) {
            setHarvestLevel("pickaxe", hard(metalOf(m)) ? 2 : 1, m);
        }
    }

    public Material metalOf(int meta) {
        return METALS[offset + (meta >= 0 && meta < count ? meta : 0)];
    }

    public int count() {
        return count;
    }

    /** The block and metadata of a metal's block, or null (lead: its own block; no ingot: none). */
    public static ItemStack stackOf(Material m, int n) {
        for (int i = 0; i < METALS.length; i++) {
            if (METALS[i] == m) {
                return new ItemStack(i < 16 ? com.sc.init.ModBlocks.metalBlock : com.sc.init.ModBlocks.metalBlock2, n, i % 16);
            }
        }
        return null;
    }

    /** The hard refractory metals: an iron pickaxe, and they shrug off blasts. */
    private static boolean hard(Material m) {
        return m == Material.TUNGSTEN || m == Material.TANTALUM || m == Material.HAFNIUM || m == Material.NIOBIUM || m == Material.TITANIUM;
    }

    /** The soft ones break easily. */
    private static boolean soft(Material m) {
        return m == Material.LITHIUM || m == Material.INDIUM || m == Material.GALLIUM || m == Material.MAGNESIUM || m == Material.TIN;
    }

    @Override
    public float getBlockHardness(World w, int x, int y, int z) {
        Material m = metalOf(w.getBlockMetadata(x, y, z));
        return hard(m) ? 8.0F : soft(m) ? 3.0F : 5.0F;
    }

    @Override
    public float getExplosionResistance(net.minecraft.entity.Entity e, World w, int x, int y, int z, double ex, double ey, double ez) {
        Material m = metalOf(w.getBlockMetadata(x, y, z));
        return (hard(m) ? 60.0F : soft(m) ? 6.0F : 10.0F) * 3.0F / 5.0F;   // as setResistance(r) would: r x 3, / 5 on use
    }

    @Override
    public boolean isBeaconBase(IBlockAccess world, int x, int y, int z, int bx, int by, int bz) {
        return true;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < count; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[count];
        for (int m = 0; m < count; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":metalBlock" + metalOf(m).oreDictName);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons == null ? null : icons[meta >= 0 && meta < count ? meta : 0];
    }

    /** The item: a name per metal. */
    public static class ItemMetalBlock extends ItemBlock {
        public ItemMetalBlock(Block block) {
            super(block);
            setHasSubtypes(true);
        }

        @Override
        public int getMetadata(int damage) {
            return damage;
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            return "tile." + Reference.ASSETS + ".metalBlock." + ((BlockMetalSC) field_150939_a).metalOf(stack.getItemDamage()).name()
                    .toLowerCase(java.util.Locale.ROOT);
        }
    }
}
