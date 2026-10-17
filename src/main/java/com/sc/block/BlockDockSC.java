package com.sc.block;

import java.util.ArrayList;
import java.util.Random;

import com.sc.Reference;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * PACKAGE B (docs/plan-substrate-tech-f2.md §8.1, rule ПР-1): «Причал» - the 7 x 7 landing of a Settlement / City
 * outside its gate. PLATE - «Причальная плита», the floor; BEACON - «Причальный маяк» in the middle: the bridge lands on it
 * exactly, with no scatter (the registry knows the dock: SettlementRegistrySC.dockNear - no mark of BridgeMarksSC, no
 * discount for the Space Bridge: -40% is the Ground Bridge's beacon only). Both unbreakable, no drop, blast-proof,
 * hidden in NEI, AE2-blacklisted; the zone round it (SettlementSC.DOCK_ZONE) takes no block from anyone (package C).
 * Placed by package S. Metas fixed, append only. Lang tile.siliconage.dock.<name>.name, sc.tooltip.dock.<name>.
 * <p>
 * The beacon is flush with the plate (the registry's dock y is the floor's): a bridge end aimed at the dock stands on
 * it, at beacon y + 1 (TileEntityBridgeControllerSC.si14Targets). Looks (tools/gen_travel_tex.py): the plate - dark
 * settlement metal with the dome's turquoise landing chevrons; the beacon - a turquoise lens on top, lit strips on the
 * sides, light BEACON_LIGHT and the Receiver Beacon's portal sparks rising from it. Nothing spawns on either (a «cold»
 * world's dock too, before package C's events ever see it).
 */
public class BlockDockSC extends Block {

    public static final int PLATE = 0, BEACON = 1;
    public static final String[] NAMES = {"plate", "beacon"};
    /** The beacon's light. */
    public static final int BEACON_LIGHT = 6;

    @SideOnly(Side.CLIENT)
    private IIcon plateTop, plateSide, beaconTop, beaconSide;

    public BlockDockSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".dock");
        setBlockTextureName(Reference.ASSETS + ":dock");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeMetal);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        return w.getBlockMetadata(x, y, z) == BEACON ? BEACON_LIGHT : 0;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
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

    /** ПР-1: no creature spawns on the landing (the zone's CheckSpawn is package C's; this holds in a cold world too). */
    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess world, int x, int y, int z) {
        return false;
    }

    // ------------------------------------------------------------------ client

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        plateTop = r.registerIcon(Reference.ASSETS + ":dockPlate");
        plateSide = r.registerIcon(Reference.ASSETS + ":dockPlateSide");
        beaconTop = r.registerIcon(Reference.ASSETS + ":dockBeaconTop");
        beaconSide = r.registerIcon(Reference.ASSETS + ":dockBeaconSide");
        blockIcon = plateTop;
    }

    /** Top (1): the plate's chevrons / the beacon's lens; the beacon's bottom is the plain plate side. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta == BEACON) {
            return side == 1 ? beaconTop : side == 0 ? plateSide : beaconSide;
        }
        return side == 1 ? plateTop : plateSide;
    }

    /** The Receiver Beacon's sparks (BlockBridgeSC): portal particles rising off the beacon's top. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (world.getBlockMetadata(x, y, z) == BEACON && !world.getBlock(x, y + 1, z).isOpaqueCube()) {
            world.spawnParticle("portal", x + 0.2 + rand.nextDouble() * 0.6, y + 1.05, z + 0.2 + rand.nextDouble() * 0.6, 0, 0.3, 0);
        }
    }
}
