package com.sc.block;

import java.util.ArrayList;

import com.sc.Reference;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.machine.MachineType;
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
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.6, rule ПП-12): «витринные» machines of the settlements - decorative
 * copies of the mod's machines, one meta per model. Not machineSC / generatorSC: no recipes, no inventory, no energy;
 * the look is the real machine's own icons (machineSC / machineSC2 by MachineType, the tank, the bridge's focuser, the
 * armour station, the charge pad, the shower; the gas tank's side and the Clone Capsule - their own textures
 * showcase_*), its front turned the way the tile says (TileEntityShowcaseSC.facing, set by the structure); the light
 * by meta (LIGHT - the structure lights it by hand, Ф1's glow); the work's particles and sounds come from the light tile
 * once in 20 ticks (blockExists before reading a neighbour). Flags that must hold before the tile exists (hardness, the
 * drop, NEI, AE2's blacklist) are the block's - 1.7.10 asks getDrops after the tile is gone. Unbreakable; if a command
 * removes one anyway it drops Nano scrap («Лом»). Hidden in NEI, in no tab. Metas fixed - NAMES, append only (Ф3's
 * workshops go on a second block). Lang tile.siliconage.showcase.<name>.name, sc.tooltip.showcase.
 */
public class BlockShowcaseSC extends Block {

    /** Metas, by workshop: the Technologist's clean room, the Metallurgist, the Gas Technician, the Cartographer, the guard square, the gate. */
    public static final int CVD = 0, CZOCHRALSKI = 1, STEPPER = 2, ETCH_BATH = 3, ION_IMPLANTER = 4, CRUSHER = 5, ORE_WASHER = 6,
            BLAST_FURNACE = 7, AIR_SEPARATOR = 8, CENTRIFUGE = 9, GAS_TANK = 10, FOCUSER = 11, ARMOUR_STATION = 12, CHARGE_PAD = 13,
            CLONE_CAPSULE = 14, SHOWER = 15;
    public static final String[] NAMES = {"cvdChamber", "czochralski", "stepper", "etchBath", "ionImplanter", "crusher", "oreWasher",
            "blastFurnace", "airSeparator", "centrifuge", "gasTank", "focuser", "armourStation", "chargePad", "cloneCapsule", "shower"};
    /** The light by meta (the plasma, the melt, the furnace, the focuser, the capsule glow; the crusher and the tank don't). */
    public static final int[] LIGHT = {7, 9, 6, 4, 7, 0, 0, 11, 4, 3, 0, 9, 5, 6, 8, 3};
    /** The machine whose icons a meta wears (MachineType), null for the others. */
    private static final MachineType[] LOOK = {MachineType.CVD_CHAMBER, MachineType.CZOCHRALSKI_PULLER, MachineType.STEPPER,
            MachineType.ETCHING_BATH, MachineType.ION_IMPLANTER, MachineType.CRUSHER, MachineType.ORE_WASHER, MachineType.BLAST_FURNACE,
            MachineType.AIR_SEPARATOR, MachineType.CENTRIFUGE, null, null, null, null, null, null};

    @SideOnly(Side.CLIENT)
    private IIcon tankSide, capsuleSide, capsuleTop, capsuleFront;

    public BlockShowcaseSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".showcase");
        setBlockTextureName(Reference.ASSETS + ":showcase");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeMetal);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityShowcaseSC();
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    /** «Лом»: one Nano scrap (the drop of a showcase removed by a command or a creative player outside a dome). */
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

    // ------------------------------------------------------------------ the look: the real machines' icons

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        tankSide = register.registerIcon(Reference.ASSETS + ":showcase_gasTank_side");
        capsuleSide = register.registerIcon(Reference.ASSETS + ":showcase_cloneCapsule_side");
        capsuleTop = register.registerIcon(Reference.ASSETS + ":showcase_cloneCapsule_top");
        capsuleFront = register.registerIcon(Reference.ASSETS + ":showcase_cloneCapsule_front");
        blockIcon = capsuleSide;
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
        MachineType t = LOOK[meta];
        if (t != null) {
            BlockMachineSC b = t.ordinal() < 16 ? ModBlocks.machineSC : ModBlocks.machineSC2;
            return b == null ? null : b.getIcon(side, t.ordinal() - b.getTypeOffset());
        }
        switch (meta) {
            case GAS_TANK:
                return side < 2 ? (ModBlocks.tankSC == null ? null : ModBlocks.tankSC.getIcon(1, 0)) : tankSide;
            case FOCUSER:
                return ModBlocks.bridge == null ? null : ModBlocks.bridge.getIcon(side, BlockBridgeSC.FOCUSER);
            case ARMOUR_STATION:
                return ModBlocks.armorStation == null ? null : ModBlocks.armorStation.getIcon(side, 0);
            case CHARGE_PAD:                                       // a pad's top, the storage's casing round it (no terminal)
                return ModBlocks.chargePadSC == null ? null : ModBlocks.chargePadSC.getIcon(side == 1 ? 1 : 2, Tier.HV.ordinal());
            case CLONE_CAPSULE:
                return side < 2 ? capsuleTop : side == 3 ? capsuleFront : capsuleSide;
            case SHOWER:
                return ModBlocks.shower == null ? null : ModBlocks.shower.getIcon(side, 0);
            default:
                return null;
        }
    }
}
