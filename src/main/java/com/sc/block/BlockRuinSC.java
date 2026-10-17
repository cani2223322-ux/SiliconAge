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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
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
 * <p>
 * Ф4 (package C, docs/plan-substrate-tech-f4.md §2.2, §8.2, §8.6, rules ПЗ-6, ПХ-2; appended): the Vaults' and the camps' -
 * <ul>
 * <li>VAULT_PLATING «Плита Хранилища» - the sealed bunker's shell: a pickaxe of level PLATING_LEVEL (diamond), hardness
 * PLATING_HARDNESS, blast resistance PLATING_RESISTANCE (obsidian's); Nano Scrap PLATING_SCRAP_PCT %, never itself (no Silk
 * Touch - an endless world's free blast wall);</li>
 * <li>VAULT_LAMP «Лампа Хранилища» - a dim lamp in the bunker's ceiling (light LAMP_LIGHT), as hard as the plating (the shell
 * has no soft spot); glowstone dust 1..2, never itself (no Silk Touch either);</li>
 * <li>CAMP_TARP «Навес Отступников» - the Renegades' tarps over their camp: soft, no tool needed; string 1..2.</li>
 * </ul>
 */
public class BlockRuinSC extends Block {

    /** Metas: the ruin metal, a burnt panel, a tank's husk, a machine's husk; Ф4 - the vault plating, the vault lamp, the camp's tarp. */
    public static final int RUIN_METAL = 0, BURNT_PANEL = 1, TANK_HUSK = 2, MACHINE_HUSK = 3, VAULT_PLATING = 4, VAULT_LAMP = 5, CAMP_TARP = 6;
    public static final String[] NAMES = {"ruinMetal", "burntPanel", "tankHusk", "machineHusk", "vaultPlating", "vaultLamp", "campTarp"};
    /** The drops' chances (%) - see the class comment. TODO(balance) */
    public static final int METAL_SCRAP_PCT = 30, PANEL_FIRMWARE_PCT = 20, HUSK_TRANSISTOR_PCT = 10, PLATING_SCRAP_PCT = 50;
    /** ПХ-2: the vault plating (and its lamps) - pickaxe level, hardness, blast resistance (setResistance's scale); the lamp's light. TODO(balance) */
    public static final int PLATING_LEVEL = 3, LAMP_LIGHT = 10;
    public static final float PLATING_HARDNESS = 30F, PLATING_RESISTANCE = 2000F, TARP_HARDNESS = 0.8F;

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
        setHarvestLevel("pickaxe", PLATING_LEVEL, VAULT_PLATING);    // Ф4: the Vault's shell - diamond
        setHarvestLevel("pickaxe", PLATING_LEVEL, VAULT_LAMP);
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

    // ------------------------------------------------------------------ Ф4: the metas' own hardness, resistance, light

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        int m = world.getBlockMetadata(x, y, z);
        return m == VAULT_PLATING || m == VAULT_LAMP ? PLATING_HARDNESS : m == CAMP_TARP ? TARP_HARDNESS : super.getBlockHardness(world, x, y, z);
    }

    @Override
    public float getExplosionResistance(Entity exploder, World world, int x, int y, int z, double ex, double ey, double ez) {
        int m = world.getBlockMetadata(x, y, z);
        if (m == VAULT_PLATING || m == VAULT_LAMP) {
            return PLATING_RESISTANCE * 3F / 5F;                // as setResistance(PLATING_RESISTANCE) would (obsidian's)
        }
        return super.getExplosionResistance(exploder, world, x, y, z, ex, ey, ez);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        if (b != this) {
            return b.getLightValue(world, x, y, z);
        }
        return world.getBlockMetadata(x, y, z) == VAULT_LAMP ? LAMP_LIGHT : 0;
    }

    /** The tarp is cloth: any hand takes it (the rest - a pickaxe, as Material.iron wants). */
    @Override
    public boolean canHarvestBlock(EntityPlayer player, int meta) {
        return meta == CAMP_TARP || super.canHarvestBlock(player, meta);
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
            case VAULT_PLATING:                                 // Ф4 (ПХ-2)
                if (rand.nextInt(100) < PLATING_SCRAP_PCT) {
                    add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1));
                }
                break;
            case VAULT_LAMP:
                out.add(new ItemStack(Items.glowstone_dust, 1 + rand.nextInt(2)));
                break;
            case CAMP_TARP:
                out.add(new ItemStack(Items.string, 1 + rand.nextInt(2)));
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

    /**
     * Ф4: but never the vault plating nor its lamp (an endless world would hand out free blast walls, lit ones too) - scrap,
     * glowstone dust.
     */
    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
        return meta != VAULT_PLATING && meta != VAULT_LAMP && super.canSilkHarvest(world, player, x, y, z, meta);
    }
}
