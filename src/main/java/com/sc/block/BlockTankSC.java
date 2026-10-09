package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityTankSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

/**
 * Portable tanks (Thermal Expansion style), metadata = tier: steel 8 000 mB, titanium 32 000,
 * tungsten 128 000, superconducting 512 000 - any fluid or gas. Right-click with a full bucket /
 * cell pours it in, with an empty one fills it; sneak + right-click empty-handed switches
 * auto-output (down). Broken, the tank keeps its contents in the item. A glowing fluid lights the
 * tank up; a comparator reads how full it is. The fluid inside is drawn by client.TankRendererSC.
 */
public class BlockTankSC extends Block {

    /** An explosion's drop chance (1 / size) would lose the tank and all its fluid: always dropped. */
    @Override
    public void dropBlockAsItemWithChance(net.minecraft.world.World world, int x, int y, int z, int meta, float chance, int fortune) {
        super.dropBlockAsItemWithChance(world, x, y, z, meta, 1.0F, fortune);
    }

    public static final String[] TIER_NAMES = {"steel", "titanium", "tungsten", "superconductor"};

    private IIcon[] sides;
    private IIcon[] tops;

    public BlockTankSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".tank");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeGlass);
        setHarvestLevel("pickaxe", 0);
    }

    /** Broken only with a pickaxe: by hand it doesn't break at all, nothing inside is lost (PickaxeOnlySC). */
    @Override
    public float getPlayerRelativeBlockHardness(net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world,
                                                int x, int y, int z) {
        return PickaxeOnlySC.hardness(super.getPlayerRelativeBlockHardness(player, world, x, y, z), player);
    }

    static int tierOf(int meta) {
        return meta >= 0 && meta < TIER_NAMES.length ? meta : 0;
    }

    private static TileEntityTankSC tank(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityTankSC ? (TileEntityTankSC) te : null;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityTankSC te = new TileEntityTankSC();
        te.setTier(tierOf(meta));
        return te;
    }

    // ---- item <-> block: the contents travel along ----

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntityTankSC te = tank(world, x, y, z);
        if (te != null && stack.hasTagCompound()) {
            te.setContents(FluidStack.loadFluidStackFromNBT(stack.getTagCompound().getCompoundTag("Fluid")));
            te.setAutoOutput(stack.getTagCompound().getBoolean("AutoOutput"));
        }
    }

    /** The item for a tank as it stands (its fluid and auto-output in NBT). */
    public static ItemStack itemOf(TileEntityTankSC te, Block block, int meta) {
        ItemStack stack = new ItemStack(block, 1, meta);
        FluidStack fluid = te == null ? null : te.getTank().getFluid();
        if (fluid != null && fluid.amount > 0 || te != null && te.isAutoOutput()) {
            NBTTagCompound nbt = new NBTTagCompound();
            if (fluid != null && fluid.amount > 0) {
                nbt.setTag("Fluid", fluid.writeToNBT(new NBTTagCompound()));
            }
            nbt.setBoolean("AutoOutput", te.isAutoOutput());
            stack.setTagCompound(nbt);
        }
        return stack;
    }

    /**
     * Always harvestable: Material.iron otherwise wants a pickaxe, and broken by hand (or with the
     * wrong tool) getDrops was never called - the tank vanished with up to 512 000 mB inside.
     */
    @Override
    public boolean canHarvestBlock(EntityPlayer player, int meta) {
        return true;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest) {
            return true;                  // harvestBlock drops (reading the tile) and then removes the block
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        drops.add(itemOf(tank(world, x, y, z), this, meta));
        return drops;
    }

    @Override
    public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
        return new ItemStack(this, 1, world.getBlockMetadata(x, y, z));
    }

    // ---- buckets, cells, auto-output ----

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        TileEntityTankSC te = tank(world, x, y, z);
        if (te == null) {
            return false;
        }
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null) {
            if (!player.isSneaking()) {
                return false;
            }
            if (!world.isRemote) {
                te.setAutoOutput(!te.isAutoOutput());
                player.addChatComponentMessage(new ChatComponentTranslation(te.isAutoOutput() ? "sc.tank.output.on" : "sc.tank.output.off"));
            }
            return true;
        }
        if (!com.sc.util.FluidHandSC.isContainer(held)) {
            return false;
        }
        if (!world.isRemote) {
            // the machines' bucket / cell logic: part-filled cells topped up, a chat line for every outcome,
            // creative hands unchanged
            com.sc.util.FluidHandSC.use(world, x, y, z, player, te, held);
        }
        return true;
    }

    // ---- light, comparator ----

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        TileEntityTankSC te = tank(world, x, y, z);
        FluidStack f = te == null ? null : te.getTank().getFluid();
        return f == null || f.amount <= 0 ? 0 : Math.max(0, Math.min(15, f.getFluid().getLuminosity(f)));
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntityTankSC te = tank(world, x, y, z);
        return te == null ? 0 : te.comparatorLevel();
    }

    // ---- look: a metal frame with glass sides (the fluid is drawn by the tile renderer) ----

    @Override
    public void registerBlockIcons(IIconRegister register) {
        sides = new IIcon[TIER_NAMES.length];
        tops = new IIcon[TIER_NAMES.length];
        for (int i = 0; i < TIER_NAMES.length; i++) {
            String base = Reference.ASSETS + ":tank/" + TIER_NAMES[i];
            sides[i] = register.registerIcon(base + "Side");
            tops[i] = register.registerIcon(base + "Top");
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        int t = tierOf(meta);
        return side < 2 ? tops[t] : sides[t];
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
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < TIER_NAMES.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
