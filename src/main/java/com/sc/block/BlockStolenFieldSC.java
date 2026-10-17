package com.sc.block;

import java.util.ArrayList;
import java.util.Random;

import com.sc.Reference;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityStolenFieldSC;
import com.sc.util.SiliconMaterial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.3, rules ПЗ-2, ПЗ-5): «Краденый генератор поля» - the camp's field master
 * (TileEntityStolenFieldSC). While its field is up a stranger can't break it (F_PRIVATE - ShieldEventHandler); down, it breaks
 * (hardness HARDNESS) and gives scrap and 1..2 components (C: Nano Scrap, a Controller / Transistor - TODO(balance)), never a
 * working Field Generator. No screen (a right click: a chat line with its charge - C). Not in a tab; NEI «how to get» (D).
 * Fixed (the coordinator's): the class, the tile; the rest C's.
 * <p>
 * The drop (ПЗ-5): Nano Scrap SCRAP, COMPONENTS_MIN..MAX components - a Controller or a Transistor each, half and half. A creative
 * stranger's instant break is refused while the dome stands (as the player's own Field Generator's). A right click (empty hand or
 * not, sneaking or not): sc.camp.field.info - the charge in % (%1$s), up / down (%2$s: sc.camp.field.up / .down), the charge in
 * EU (%3$s), the dome's radius (%4$s).
 */
public class BlockStolenFieldSC extends BlockContainer {

    public static final float HARDNESS = 5F;
    /** ПЗ-5: what it gives broken - Nano Scrap, components (a Controller or a Transistor each). TODO(balance) */
    public static final int SCRAP = 2, COMPONENTS_MIN = 1, COMPONENTS_MAX = 2;

    public BlockStolenFieldSC() {
        super(Material.iron);
        setHardness(HARDNESS);
        setResistance(30F);
        setStepSound(soundTypeMetal);
        setBlockName(Reference.ASSETS + ".stolenField");
        setBlockTextureName(Reference.ASSETS + ":camp_stolenField");
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityStolenFieldSC();
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return drops(world.rand);
    }

    /** ПЗ-5: the scrap and the components (public for the self-test and the handbook). */
    public static ArrayList<ItemStack> drops(Random rand) {
        ArrayList<ItemStack> out = new ArrayList<ItemStack>();
        ItemStack scrap = MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, SCRAP);
        if (scrap != null) {
            out.add(scrap);
        }
        if (ModItems.siliconMaterial != null) {
            int n = COMPONENTS_MIN + rand.nextInt(COMPONENTS_MAX - COMPONENTS_MIN + 1);
            for (int i = 0; i < n; i++) {
                out.add(ModItems.siliconMaterial.stackOf(rand.nextBoolean() ? SiliconMaterial.CONTROLLER : SiliconMaterial.TRANSISTOR));
            }
        }
        return out;
    }

    /** A creative stranger's instant break under its own dome: refused (the survival break is ShieldEventHandler's). */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote && TileEntityFieldGeneratorSC.privateRefusing(world, player, x + 0.5D, y + 0.5D, z + 0.5D) != null) {
            world.markBlockForUpdate(x, y, z);
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntityFieldGeneratorSC.unlink(world, x, y, z);
        super.breakBlock(world, x, y, z, block, meta);
    }

    /** sc.camp.field.info: the charge (%), up / down, the charge (EU), the dome's radius. */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityStolenFieldSC) || player instanceof FakePlayer) {
            return true;
        }
        TileEntityStolenFieldSC f = (TileEntityStolenFieldSC) te;
        int max = Math.max(1, f.getMaxEnergyStored());
        ChatComponentTranslation m = new ChatComponentTranslation("sc.camp.field.info", String.valueOf(f.getEnergyStored() * 100L / max),
                new ChatComponentTranslation(f.isActive() ? "sc.camp.field.up" : "sc.camp.field.down"), String.valueOf(f.getEnergyStored()),
                String.valueOf(f.radius()));
        m.setChatStyle(new ChatStyle().setColor(f.isActive() ? EnumChatFormatting.RED : EnumChatFormatting.GRAY));
        player.addChatComponentMessage(m);
        return true;
    }
}
