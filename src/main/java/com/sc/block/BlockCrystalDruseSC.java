package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModItems;
import com.sc.manual.Lang;
import com.sc.util.SiliconMaterial;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * «Друза» (docs/plan-substrate.md §3.2): the Substrate's glowing crystals, stacked 3..9 high on the plateaus by the
 * generator (ChunkProviderSubstrateSC). A block of its own, not a meta of the rock: not opaque, drawn as crossed
 * quads (like tall grass) and thinner than a block, so a crystal is a spike you can walk past. Light 10, glass
 * sound, hardness 1; breaks into SAND_MIN..SAND_MAX Silica Sand (by hand too), with Silk Touch into itself.
 * <p>
 * Meta: META_BODY, META_TIP - the top crystal of a stack has a pointed tip. A placed or broken crystal sets its own
 * meta and the one below it (onBlockAdded / onNeighborBlockChange, flag 2: no chain of updates).
 */
public class BlockCrystalDruseSC extends Block {

    public static final int META_BODY = 0, META_TIP = 1;
    public static final int LIGHT = 10, SAND_MIN = 2, SAND_MAX = 4;

    @SideOnly(Side.CLIENT)
    private IIcon body, tip;

    public BlockCrystalDruseSC() {
        super(Material.glass);
        setBlockName(Reference.ASSETS + ".crystalDruse");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(1.0F);
        setStepSound(soundTypeGlass);
        setLightLevel((LIGHT + 0.5F) / 15F);                     // (int) (15 x level) = LIGHT, safe from float rounding
        setBlockBounds(0.2F, 0F, 0.2F, 0.8F, 1F, 0.8F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** Crossed quads (RenderBlocks.renderCrossedSquares), as tall grass and flowers. */
    @Override
    public int getRenderType() {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        body = register.registerIcon(Reference.ASSETS + ":substrate_druse");
        tip = register.registerIcon(Reference.ASSETS + ":substrate_druse_top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return meta == META_TIP ? tip : body;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemStack sand = ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND);
        sand.stackSize = SAND_MIN + world.rand.nextInt(SAND_MAX - SAND_MIN + 1);
        drops.add(sand);
        return drops;
    }

    /** Not a normal block, so vanilla would refuse Silk Touch - the crystal itself is what Silk Touch is for. */
    @Override
    protected boolean canSilkHarvest() {
        return true;
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        shape(world, x, y, z);
        if (world.getBlock(x, y - 1, z) == this) {
            shape(world, x, y - 1, z);
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        shape(world, x, y, z);
    }

    /** The tip when nothing of the druse stands on it, else the body. */
    private void shape(World world, int x, int y, int z) {
        if (world.isRemote) {
            return;
        }
        int want = world.getBlock(x, y + 1, z) == this ? META_BODY : META_TIP;
        if (world.getBlockMetadata(x, y, z) != want) {
            world.setBlockMetadataWithNotify(x, y, z, want, 2);
        }
    }

    /** The item: the pointed crystal's picture; the tooltip. */
    public static class ItemDruse extends ItemBlock {

        public ItemDruse(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIconFromDamage(int damage) {
            return field_150939_a.getIcon(1, META_TIP);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
            list.add("§7" + Lang.tr("sc.tooltip.crystalDruse"));
            TooltipSC.more(list, Lang.tr("sc.tooltip.crystalDruse.details"), Lang.tr("sc.tooltip.crystalDruse.howto"));
        }
    }
}
