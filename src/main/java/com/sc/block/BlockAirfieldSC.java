package com.sc.block;

import java.util.ArrayList;

import com.sc.Reference;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §2.4, rule ЛП-4): «Покрытие лётного поля» - the embassies' airfield
 * surface, its markings and its lights, one meta each (registry name "embassyAirfieldSC" - for good; metas fixed, NAMES append only).
 * Built only by the airfield's pieces (package P); the trim's rules (BlockSettlementDecorSC): unbreakable (hardness -1, nobody breaks
 * it - SettlementBlocksSC.unbreakable), blast-proof, no mob destroys it, it drops nothing, creative's pick-block gives nothing, AE2's
 * Spatial IO blacklist has it (SettlementCompatSC), no tab, hidden in NEI.
 * <ul>
 * <li><b>Not an opaque cube</b> (isOpaqueCube false - BlockSnow.canPlaceBlockAt asks exactly that of the block under it, checked in
 * Forge 1614's bytecode): no snow layer ever settles on the runway (the add-on's RunwayMath reads the column's top block - snow would
 * hide it). It still is a full, solid, normal cube to everything else (Material.rock: isNormalCube, a solid top - walking, mobs'
 * pathing), its light opacity 255 (the heightmap and the light below as under stone); faces against its own kind are culled
 * (shouldSideBeRendered) - a runway is thousands of them;</li>
 * <li>the add-on recognises its runway by isRunwaySurface (EmbassyAirApiSC.isRunwaySurface: RUNWAY, MARK_WHITE, LIGHT_EDGE,
 * LIGHT_THRESHOLD, LIGHT_END - the paved 9 of the runway, its edge lights and end lights in its edge rows; the shoulders, the apron,
 * the yellow markings and the taxi lights are not runway: RunwayMath's flood never joins the apron to it);</li>
 * <li>the lights give light by meta (LIGHT - the pieces light them by hand where setBlock gave up: ComponentSettlementSC.put).</li>
 * </ul>
 * Fixed (the coordinator's): the class, NAMES, LIGHT, RUNWAY_METAS, the rules above. Package P's: the look (registerBlockIcons /
 * getIcon - the asphalt, the concrete, the markings, the lamps; the textures embassyAirfield_*.png and their generator).
 */
public class BlockAirfieldSC extends Block {

    /** Metas: the runway's paving, its shoulder, the apron; white and yellow markings; the edge, threshold (green), end (red) and taxi (blue) lights. */
    public static final int RUNWAY = 0, SHOULDER = 1, APRON = 2, MARK_WHITE = 3, MARK_YELLOW = 4, LIGHT_EDGE = 5, LIGHT_THRESHOLD = 6,
            LIGHT_END = 7, LIGHT_TAXI = 8;
    public static final String[] NAMES = {"runway", "shoulder", "apron", "markWhite", "markYellow", "lightEdge", "lightThreshold", "lightEnd",
            "lightTaxi"};
    /** The light by meta (the lamps; the paving and the paint are dark). TODO(balance) */
    public static final int[] LIGHT = {0, 0, 0, 0, 0, 10, 10, 8, 6};
    /** The metas the add-on takes for its runway (EmbassyAirApiSC.isRunwaySurface). */
    private static final boolean[] RUNWAY_METAS = {true, false, false, true, false, true, true, true, false};

    public BlockAirfieldSC() {
        super(net.minecraft.block.material.Material.rock);
        setBlockName(Reference.ASSETS + ".embassyAirfield");
        setBlockTextureName(Reference.ASSETS + ":embassyAirfield_runway");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeStone);
        setLightOpacity(255);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    /** Meta `meta` is a part of the runway itself (the add-on's runway flag - EmbassyAirApiSC.isRunwaySurface). Pure. */
    public static boolean runway(int meta) {
        return meta >= 0 && meta < RUNWAY_METAS.length && RUNWAY_METAS[meta];
    }

    /** No snow layer settles on it (see the class comment); everything else still takes it for a full solid cube. */
    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    /** A face against another block of the airfield is never drawn (isOpaqueCube false would draw them all). */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return world.getBlock(x, y, z) != this && super.shouldSideBeRendered(world, x, y, z, side);
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

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    /** Creative's middle click: nothing - an unbreakable copy would stand wherever it was put. */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    /** LIGHT by meta (the pieces light a new one by hand where setBlock's update gave up). */
    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        if (b != this) {
            return b.getLightValue(world, x, y, z);
        }
        int meta = world.getBlockMetadata(x, y, z);
        return meta >= 0 && meta < LIGHT.length ? LIGHT[meta] : 0;
    }

    // ------------------------------------------------------------------ the look (package P: replaces the two methods below)
    // tools/gen_airfield_tex.py: the runway's dark asphalt, the shoulder's worn lighter one, the apron's concrete slabs, white and yellow
    // paint, the lamps flush in their paving (a steel ring, the lens - white edge, green threshold, red end, blue taxi). A meta's top is
    // its own; its sides and bottom the paving it lies in (paint and lamps are only on top): the runway's asphalt under the white paint
    // and the runway's lamps, the concrete under the yellow paint and the taxi lamp.

    /** The paving a meta's sides show (by meta). */
    private static final int[] SIDE_OF = {RUNWAY, SHOULDER, APRON, RUNWAY, APRON, RUNWAY, RUNWAY, RUNWAY, APRON};

    @SideOnly(Side.CLIENT)
    private IIcon[] tops;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        IIcon[] t = new IIcon[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            t[i] = register.registerIcon(Reference.ASSETS + ":embassyAirfield_" + NAMES[i]);
        }
        tops = t;
        blockIcon = t[RUNWAY];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        IIcon[] t = tops;
        if (t == null) {
            return blockIcon;
        }
        int m = meta >= 0 && meta < t.length ? meta : RUNWAY;
        return t[side == 1 ? m : SIDE_OF[m]];
    }
}
