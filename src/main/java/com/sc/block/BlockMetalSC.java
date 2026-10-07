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

    /** isBlockOf's answers: (block id << 4 | meta) -> the ore dictionary names it carries. */
    private static final java.util.Map<Integer, java.util.Set<String>> NAMES = new java.util.HashMap<Integer, java.util.Set<String>>();

    /**
     * A block of `metal` ("Lead", "Copper"...) from any mod - ours or another's ("block" + metal in the ore
     * dictionary). Every structure that needs a block of metal asks this, never one block of ours: a pack that
     * unifies metals (UniDict) crafts only one mod's block. Cached per block and meta.
     */
    public static boolean isBlockOf(String metal, Block b, int meta) {
        if (b == null) {
            return false;
        }
        int key = (Block.getIdFromBlock(b) << 4) | (meta & 15);
        java.util.Set<String> names = NAMES.get(key);
        if (names == null) {
            names = new java.util.HashSet<String>();
            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(b);
            if (item != null) {
                for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(new net.minecraft.item.ItemStack(item, 1, meta))) {
                    names.add(net.minecraftforge.oredict.OreDictionary.getOreName(id));
                }
            }
            NAMES.put(key, names);
        }
        return names.contains("block" + metal);
    }

    /** Forgets isBlockOf's answers (a new world may number its blocks differently). */
    public static void clearCache() {
        NAMES.clear();
    }

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
        return meta >= 0 && meta < count ? meta : 0;             // an unused meta (a /setblock) drops the first metal
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
            return damage >= 0 && damage < ((BlockMetalSC) field_150939_a).count() ? damage : 0;
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            return "tile." + Reference.ASSETS + ".metalBlock." + ((BlockMetalSC) field_150939_a).metalOf(stack.getItemDamage()).name()
                    .toLowerCase(java.util.Locale.ROOT);
        }

        /** Nine ingots, a beacon's base; the pickaxe it needs (and the hard metals' blast resistance). */
        @Override
        @SuppressWarnings("unchecked")
        public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, List list, boolean advanced) {
            Material m = ((BlockMetalSC) field_150939_a).metalOf(stack.getItemDamage());
            list.add("§7" + com.sc.manual.Lang.tr("sc.metal.tooltip"));
            list.add("§8" + com.sc.manual.Lang.tr("sc.manual.ores.tool",
                    com.sc.manual.Lang.tr(hard(m) ? "sc.manual.ores.tool.iron" : "sc.manual.ores.tool.stone")));
            if (hard(m)) {
                list.add("§8" + com.sc.manual.Lang.tr("sc.metal.tooltip.hard"));
            }
        }
    }
}
