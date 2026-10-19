package com.sc.block;

import java.util.ArrayList;
import java.util.Random;

import com.sc.Reference;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.4, rule ПЯ-3): «Ядро Большого кольца» - the Great Ring's core block under its middle on
 * the Vokzal, with its TileEntityEmbassyRingSC. Unbreakable (hardness -1, blast-proof), no drop, no pick block, nothing spawns on it,
 * hidden in NEI, AE2's spatial blacklist (SettlementCompatSC). Its look is the bridge's ring's core (package R's icon
 * embassyRing / embassyRing_top; light RING_LIGHT). Fixed (the coordinator's): the class, the material, unbreakable, the tile. R's:
 * the icons, the light, randomDisplayTick (sparks while the vortex is open).
 * <p>
 * R's details: the sides - embassyRing (the dark casing, the coil's violet windings round a turquoise lens), the top and bottom -
 * embassyRing_top (a round port, the ring's segments round it); both by tools/gen_embassy_ring_tex.py, animated (a slow pulse). The
 * sparks (sparks(): the tile's client tick, by what the tile heard - a random display tick comes too seldom for a 13-block ring):
 * the countdown - turquoise sparks on the ring's coils, more as it ends; the vortex - sparks and portal motes drawn into its middle;
 * the cooling - a little smoke off the coils; idle (randomDisplayTick) - now and then a spark on the core itself.
 */
public class BlockEmbassyRingSC extends Block {

    /** The core's light. TODO(balance) */
    public static final int RING_LIGHT = 9;
    /** «Бирюза Наладчиков» (0x48F0DC) as a redstone dust particle's colour. */
    private static final double SPARK_R = 0.28D, SPARK_G = 0.94D, SPARK_B = 0.86D;

    @SideOnly(Side.CLIENT)
    private IIcon top;

    public BlockEmbassyRingSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".embassyRing");
        setBlockTextureName(Reference.ASSETS + ":embassyRing");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeMetal);
        setLightLevel(RING_LIGHT / 15F);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityEmbassyRingSC();
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
    public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess world, int x, int y, int z) {
        return false;
    }

    // ------------------------------------------------------------------ R: the look

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(getTextureName());
        top = reg.registerIcon(getTextureName() + "_top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side < 2 && top != null ? top : blockIcon;
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return RING_LIGHT;
    }

    /** Idle: now and then a spark on the core itself (the window's sparks come from its tile's client tick - sparks()). */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World w, int x, int y, int z, Random r) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileEntityEmbassyRingSC && ((TileEntityEmbassyRingSC) te).phase() == EmbassyRulesSC.PHASE_IDLE && r.nextInt(2) == 0) {
            w.spawnParticle("reddust", x + r.nextDouble(), y + 1.05D, z + r.nextDouble(), SPARK_R, SPARK_G, SPARK_B);
        }
    }

    /**
     * The window's sparks round the ring (World.spawnParticle: nothing on a server; a client draws those within 16 of its camera) -
     * the ring's tile calls it on a client every other tick while a window runs.
     */
    public static void sparks(World w, TileEntityEmbassyRingSC ring, Random r) {
        int phase = ring.phase();
        double cx = ring.xCoord + ring.ringDx() + 0.5D, cy = ring.yCoord + ring.ringDy() + 0.5D, cz = ring.zCoord + ring.ringDz() + 0.5D;
        boolean acrossX = ring.axis() == TileEntityEmbassyRingSC.AXIS_X;
        switch (phase) {
            case EmbassyRulesSC.PHASE_COUNTDOWN: {
                int len = TileEntityEmbassyRingSC.length(phase);
                int n = 1 + (len <= 0 ? 0 : 4 * ring.age() / len);    // more sparks as the countdown runs out
                for (int i = 0; i < n; i++) {
                    double[] at = onCoil(r, cx, cy, cz, acrossX);
                    w.spawnParticle("reddust", at[0], at[1], at[2], SPARK_R, SPARK_G, SPARK_B);
                }
                break;
            }
            case EmbassyRulesSC.PHASE_VORTEX:
                for (int i = 0; i < 3; i++) {
                    double[] at = onCoil(r, cx, cy, cz, acrossX);
                    w.spawnParticle("reddust", at[0], at[1], at[2], SPARK_R, SPARK_G, SPARK_B);
                    // a portal mote starts at its point + its motion and is drawn back to its point: from the coils into the middle
                    w.spawnParticle("portal", cx, cy, cz, at[0] - cx, at[1] - cy, at[2] - cz);
                }
                if (r.nextInt(3) == 0) {
                    double[] at = onCoil(r, cx, cy, cz, acrossX);
                    w.spawnParticle("fireworksSpark", at[0], at[1], at[2], (r.nextDouble() - 0.5D) * 0.1D, 0.05D, (r.nextDouble() - 0.5D) * 0.1D);
                }
                break;
            case EmbassyRulesSC.PHASE_COOL:
                if (r.nextInt(4) == 0 && ring.age() < TileEntityEmbassyRingSC.length(phase) / 2) {
                    double[] at = onCoil(r, cx, cy, cz, acrossX);
                    w.spawnParticle("smoke", at[0], at[1], at[2], 0D, 0.02D, 0D);
                }
                break;
            default:
        }
    }

    /** A random point on the ring's coils (radius RING_R round its middle, in its plane). */
    private static double[] onCoil(Random r, double cx, double cy, double cz, boolean acrossX) {
        double a = r.nextDouble() * Math.PI * 2D, rad = EmbassyLayoutSC.RING_R + (r.nextDouble() - 0.5D) * 0.8D;
        double u = Math.cos(a) * rad, v = Math.sin(a) * rad, off = (r.nextDouble() - 0.5D) * 0.8D;
        return acrossX ? new double[]{cx + u, cy + v, cz + off} : new double[]{cx + off, cy + v, cz + u};
    }
}
