package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3, rule ПУ-2): «руинные» blocks of the Dead Nodes and the lithographs - only
 * opaque full cubes, one meta each: the ruin metal, a burnt-out panel, the husk of a tank, the husk of a machine. They are
 * not the mod's real blocks (an endless world would hand those out free): no recipe, no inventory, no energy; broken with a
 * pickaxe they give scrap - Nano Scrap, Shell Firmware, a little dust (getDrops, TODO(balance)) - never a machine, a
 * panel or a metal block. Metas fixed - NAMES, append only. Lang tile.siliconage.ruin.<name>.name; a TooltipSC tooltip
 * (ItemBlockRuinSC); NEI «how to get»: the Dead Nodes, the lithographs (package D). Not in the creative tab (found, not
 * made), shown in NEI (getSubBlocks lists them all, the tab asked or not).
 * <p>
 * The drops (TODO(balance)): Ruin Metal - Nano Scrap METAL_SCRAP_PCT %; a Burnt Panel - Shell Firmware PANEL_FIRMWARE_PCT %,
 * else a Silica Sand; a Tank Husk - a Nano Scrap; a Machine Husk - a Nano Scrap and a Transistor HUSK_TRANSISTOR_PCT %.
 * Silk Touch takes the block itself (a ruin's look for a base - it makes nothing). Icons ruin_<name>.png
 * (tools/gen_ruins_tex.py).
 * <p>
 * Fixed (the coordinator's): the class, NAMES and the metas, the registry name "ruinSC" (RuinBlocksSC).
 */
public class BlockRuinSC extends Block {

    /** Metas: the ruin metal, a burnt panel, a tank's husk, a machine's husk. */
    public static final int RUIN_METAL = 0, BURNT_PANEL = 1, TANK_HUSK = 2, MACHINE_HUSK = 3;
    public static final String[] NAMES = {"ruinMetal", "burntPanel", "tankHusk", "machineHusk"};
    /** The drops' chances (%) - see the class comment. TODO(balance) */
    public static final int METAL_SCRAP_PCT = 30, PANEL_FIRMWARE_PCT = 20, HUSK_TRANSISTOR_PCT = 10;

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockRuinSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".ruin");
        setBlockTextureName(Reference.ASSETS + ":ruin_ruinMetal");
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[NAMES.length];
        for (int m = 0; m < NAMES.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":ruin_" + NAMES[m]);
        }
        blockIcon = icons[RUIN_METAL];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons == null ? null : icons[meta >= 0 && meta < icons.length ? meta : RUIN_METAL];
    }

    /** All the metas - NEI lists them (no creative tab: found, not made). */
    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }

    @Override
    public int damageDropped(int meta) {
        return meta >= 0 && meta < NAMES.length ? meta : RUIN_METAL;
    }

    /** Scrap, never the block (Silk Touch aside) - the class comment. */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return drops(meta, world.rand);
    }

    /** The scrap one block of `meta` gives (public for the self-test and the handbook). */
    public static ArrayList<ItemStack> drops(int meta, Random rand) {
        ArrayList<ItemStack> out = new ArrayList<ItemStack>();
        switch (meta) {
            case BURNT_PANEL:
                if (rand.nextInt(100) < PANEL_FIRMWARE_PCT) {
                    add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, 1));
                } else if (ModItems.siliconMaterial != null) {
                    add(out, ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND));
                }
                break;
            case TANK_HUSK:
                add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1));
                break;
            case MACHINE_HUSK:
                add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1));
                if (rand.nextInt(100) < HUSK_TRANSISTOR_PCT && ModItems.siliconMaterial != null) {
                    add(out, ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR));
                }
                break;
            default:                                            // RUIN_METAL
                if (rand.nextInt(100) < METAL_SCRAP_PCT) {
                    add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1));
                }
                break;
        }
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack s) {
        if (s != null) {
            out.add(s);
        }
    }

    /** Silk Touch keeps every meta (ItemBlockRuinSC has subtypes). */
    @Override
    protected boolean canSilkHarvest() {
        return true;
    }
}
