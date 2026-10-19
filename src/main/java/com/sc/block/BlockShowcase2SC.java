package com.sc.block;

import java.util.ArrayList;

import com.sc.Reference;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityShowcaseSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * PACKAGE S (docs/plan-substrate-tech-f3.md §6.3, rule ПП-12): the second block of the settlements' «витринные» machines -
 * the Ф3 workshops (BlockShowcaseSC's sixteen metas are all taken): the Power Engineer's energy storage, transformer, wind
 * generator and solar panel (the roof), the Salvager's warehouse - crates, a scrap pile, a rack. The same rules as
 * BlockShowcaseSC: unbreakable (SettlementBlocksSC.unbreakable), no recipe, inventory or energy, the look of the real thing,
 * light and particles by its light tile, a command's removal drops Nano scrap, hidden in NEI, in no tab, in AE2's spatial
 * blacklist (SettlementCompatSC). Metas fixed - NAMES, append only. Lang tile.siliconage.showcase2.<name>.name.
 * <p>
 * Fixed (the coordinator's): the class, NAMES, the registry name "showcase2SC". Package S's: the look - the real machines'
 * own icons (the HV Energy Storage, the MV-HV Transformer, the Wind Turbine, the GaAs Solar Panel; the front where the tile
 * faces), the warehouse's own textures showcase2_* (tools/gen_settlement3_tex.py); the light by meta (LIGHT, lit by hand
 * by the structure as the first block's); the tile - the first block's TileEntityShowcaseSC (its facing, its particles
 * once in 20 ticks), none for the crate and the solar panel (no front, nothing moving).
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §2.2, package S's look): the Tuners' Embassies' metas appended - GRAVITY_COIL (the Great Ring's
 * segment, «витрина-гравикатушка»: the bridge's Gravity Coil's look, lit), CRYO_TANK (the Vokzal's gas tanks, «витрина-бак»), and
 * DISPATCH_CONSOLE (the Dispatchers' office desk). The same rules: unbreakable, no drop but scrap, hidden in NEI, AE2-blacklisted.
 */
public class BlockShowcase2SC extends Block {

    /** Metas: the Power Engineer's workshop and roof, the Salvager's warehouse. */
    public static final int ENERGY_STORAGE = 0, TRANSFORMER = 1, WIND_GENERATOR = 2, SOLAR_PANEL = 3, CRATE = 4, SCRAP_PILE = 5, RACK = 6;
    /** Ф6 (appended): the Great Ring's segment, the Vokzal's tank, the Dispatchers' desk. */
    public static final int GRAVITY_COIL = 7, CRYO_TANK = 8, DISPATCH_CONSOLE = 9;
    public static final String[] NAMES = {"energyStorage", "transformer", "windGenerator", "solarPanel", "crate", "scrapPile", "rack",
            "gravityCoil", "cryoTank", "dispatchConsole"};
    /** The light by meta (the storage's terminal, the transformer's coils; Ф6: the ring's coils, the tank's gauge, the desk's screens; the rest is dark). */
    public static final int[] LIGHT = {5, 4, 0, 0, 0, 0, 0, 8, 3, 6};
    /** The real machines the metas wear: the energy storage's and the transformer's tier (their blocks' meta), the two generators. */
    public static final Tier STORAGE_TIER = Tier.HV, TRANSFORMER_TIER = Tier.MV;
    public static final GeneratorType WIND = GeneratorType.WIND_TURBINE, SOLAR = GeneratorType.SOLAR_GAAS;

    @SideOnly(Side.CLIENT)
    private IIcon crateSide, crateTop, scrapSide, scrapTop, rackSide, rackFront, rackTop;
    /** Ф6 (package S): the ring's coil (its windings, their ends), the tank (side, gauge, lid), the desk (screens, side, top). */
    @SideOnly(Side.CLIENT)
    private IIcon coilSide, coilEnd, tankSide, tankFront, tankTop, consoleFront, consoleSide, consoleTop;

    public BlockShowcase2SC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".showcase2");
        setBlockTextureName(Reference.ASSETS + ":showcase");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeMetal);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    /** A light tile for everything with a front or a show: not the crate, not the solar panel. */
    @Override
    public boolean hasTileEntity(int meta) {
        return meta != CRATE && meta != SOLAR_PANEL;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return hasTileEntity(meta) ? new TileEntityShowcaseSC() : null;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    /** «Лом»: one Nano scrap (a showcase removed by a command or a creative player outside a dome). */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> out = new ArrayList<ItemStack>();
        ItemStack scrap = com.sc.mob.MobItemsSC.loot(com.sc.mob.ItemCombatLootSC.LootKind.NANO_SCRAP, 1);
        if (scrap != null) {
            out.add(scrap);
        }
        return out;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    /** Creative's middle click: nothing - an unbreakable copy would stand wherever it was put. */
    @Override
    public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    /** LIGHT by meta (the structure lights a new one by hand where setBlock's update gave up). */
    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        if (b != this) {
            return b.getLightValue(world, x, y, z);
        }
        int meta = world.getBlockMetadata(x, y, z);
        return meta >= 0 && meta < LIGHT.length ? LIGHT[meta] : 0;
    }

    // ------------------------------------------------------------------ the look: the real machines' icons, the warehouse's own
    // Ф6 (package S, tools/gen_embassy_tex.py): the Great Ring's segment - the bridge's Gravity Coil lit in the Tuners' turquoise
    // (showcase2_gravityCoil_side / _top), the Vokzal's cryo tank - frosted steel, its level gauge in front (showcase2_cryoTank_*), the
    // Dispatchers' desk - the ring and the flights on its screens (showcase2_dispatchConsole_*).

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        crateSide = register.registerIcon(Reference.ASSETS + ":showcase2_crate_side");
        crateTop = register.registerIcon(Reference.ASSETS + ":showcase2_crate_top");
        scrapSide = register.registerIcon(Reference.ASSETS + ":showcase2_scrapPile_side");
        scrapTop = register.registerIcon(Reference.ASSETS + ":showcase2_scrapPile_top");
        rackSide = register.registerIcon(Reference.ASSETS + ":showcase2_rack_side");
        rackFront = register.registerIcon(Reference.ASSETS + ":showcase2_rack_front");
        rackTop = register.registerIcon(Reference.ASSETS + ":showcase2_rack_top");
        coilSide = register.registerIcon(Reference.ASSETS + ":showcase2_gravityCoil_side");
        coilEnd = register.registerIcon(Reference.ASSETS + ":showcase2_gravityCoil_top");
        tankSide = register.registerIcon(Reference.ASSETS + ":showcase2_cryoTank_side");
        tankFront = register.registerIcon(Reference.ASSETS + ":showcase2_cryoTank_front");
        tankTop = register.registerIcon(Reference.ASSETS + ":showcase2_cryoTank_top");
        consoleFront = register.registerIcon(Reference.ASSETS + ":showcase2_dispatchConsole_front");
        consoleSide = register.registerIcon(Reference.ASSETS + ":showcase2_dispatchConsole_side");
        consoleTop = register.registerIcon(Reference.ASSETS + ":showcase2_dispatchConsole_top");
        blockIcon = crateSide;
    }

    /** As an item (and a tile-less block): the front faces south (3), the way a machine's item shows it. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        IIcon i = look(side, meta >= 0 && meta < NAMES.length ? meta : 0);
        return i != null ? i : blockIcon;
    }

    /** In the world: the front where the tile faces (the casing / the sides elsewhere). */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        int front = te instanceof TileEntityShowcaseSC ? ((TileEntityShowcaseSC) te).getFacing() : 3;
        int as = side < 2 ? side : side == front ? 3 : side == (front ^ 1) ? 2 : 4;   // ForgeDirection: 2/3, 4/5 are opposites
        return getIcon(as, world.getBlockMetadata(x, y, z));
    }

    @SideOnly(Side.CLIENT)
    private IIcon look(int side, int meta) {
        switch (meta) {
            case ENERGY_STORAGE:
                return ModBlocks.energyStorageSC == null ? null : ModBlocks.energyStorageSC.getIcon(side, STORAGE_TIER.ordinal());
            case TRANSFORMER:
                return ModBlocks.transformerSC == null ? null : ModBlocks.transformerSC.getIcon(side, TRANSFORMER_TIER.ordinal());
            case WIND_GENERATOR:
                return generator(side, WIND);
            case SOLAR_PANEL:
                return generator(side, SOLAR);
            case CRATE:
                return side < 2 ? crateTop : crateSide;
            case SCRAP_PILE:
                return side == 1 ? scrapTop : scrapSide;
            case RACK:
                return side < 2 ? rackTop : side == 3 ? rackFront : rackSide;
            case GRAVITY_COIL:                                  // Ф6: the bridge's coil lit turquoise, the same all round
                return side < 2 ? coilEnd : coilSide;
            case CRYO_TANK:                                     // the gauge where the tile faces
                return side < 2 ? tankTop : side == 3 ? tankFront : tankSide;
            case DISPATCH_CONSOLE:                              // the screens face the Dispatcher
                return side == 1 ? consoleTop : side == 3 ? consoleFront : consoleSide;
            default:
                return null;
        }
    }

    /** A generator's icons (its block and meta as ModBlocks.generatorStack): the front on side 3, a solar cell on top. */
    @SideOnly(Side.CLIENT)
    private static IIcon generator(int side, GeneratorType t) {
        BlockGeneratorSC b = t.ordinal() < 16 ? ModBlocks.generatorSC : ModBlocks.generatorSC2;
        return b == null ? null : b.getIcon(side, t.ordinal() - b.getTypeOffset());
    }
}
