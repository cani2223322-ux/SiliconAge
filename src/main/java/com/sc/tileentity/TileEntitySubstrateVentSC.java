package com.sc.tileentity;

import java.util.List;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.dim.SubstrateRadiationSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.init.ModFluids;
import com.sc.item.ItemArmorSC;
import com.sc.mob.BreakdownDamageSC;
import com.sc.radiation.RadiationSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §4.2, §4.4, §4.5, rules ПА-4, ПА-6, ПА-7): the tile of a fumarole, an ion geyser or a
 * cracked RTG (BlockSubstrateVentSC by meta; the Breakdown crack has its own, TileEntityArcCrackSC). Once a second (by its
 * position's phase, blockExists before every neighbour - it never loads a chunk):
 * <ul>
 * <li>FUMAROLE: HELIUM_MB_MIN..MAX mB of liquid helium a second into the mod's pipe or tank beside it (TileEntityPipeSC /
 * TileEntityTankSC only - «гелий забирают только трубой мода в бак»); nothing kept, nothing to drain from outside;</li>
 * <li>ION_GEYSER: a discharge every GEYSER_MIN..GEYSER_MAX ticks within GEYSER_RADIUS - GEYSER_DAMAGE of «Пробой»
 * (BreakdownDamageSC.WORLD) and GEYSER_DRAIN_PCT of every worn armour piece's charge (ItemArmorSC.discharge), sparks for all;</li>
 * <li>CRACKED_RTG: RadiationSC.report(SubstrateRadiationSC.RTG_LEVEL, RTG_RADIUS) - blocks in the way count.</li>
 * </ul>
 * The nanite swarm never feeds on it (no energy). Fixed (the coordinator's): the class, the registry id "SiliconAge.substrateVent",
 * the numbers; the body W's.
 * <p>
 * W: the server only (the client's tile does nothing - the block draws its particles). The mod's pipe is a conduit bundle holding
 * a pipe now (a TileEntityPipeSC of an old world turns into one on its first tick): the helium goes, one face after another, into a
 * bundle with a pipe or a Fluid Tank beside it, through IFluidHandler.fill from the face towards the fumarole - 1 mB and 2 mB a
 * second by turns (1.5 on average). The geyser's clock is not saved: a loaded geyser waits GEYSER_MIN..MAX afresh; its last
 * WARN_TICKS before a discharge the server sends sparks (WorldServer.func_147487_a - every player near sees them) and a charge
 * sound. A discharge hits every living thing within GEYSER_RADIUS of the vent's top (creative players excepted - vanilla's
 * attackEntityFrom spares them anyway, the drain skips them too).
 */
public class TileEntitySubstrateVentSC extends TileEntity {

    /** ПА-4: the fumarole's helium (mB a second, «около 1–2 мБ/с»). */
    public static final int HELIUM_MB_MIN = 1, HELIUM_MB_MAX = 2;
    /** ПА-6: the geyser - every 6..10 s, radius 3, 3 «Пробой», -2% charge. */
    public static final int GEYSER_MIN = 120, GEYSER_MAX = 200, GEYSER_RADIUS = 3, GEYSER_DAMAGE = 3, GEYSER_DRAIN_PCT = 2;
    /** ПА-6: the sparks before a discharge («перед разрядом 1 с искр»), a burst every SPARK_EVERY ticks of it. */
    public static final int WARN_TICKS = 20, SPARK_EVERY = 5;

    /** The geyser: ticks to its next discharge (0: not set yet). */
    private int countdown;
    /** The fumarole's turn: 1 mB, then 2. */
    private boolean twoNext;

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        int meta = getBlockMetadata();
        if (meta == SubstrateWildBlocksSC.ION_GEYSER) {
            geyserTick();
            return;
        }
        if ((worldObj.getTotalWorldTime() + (xCoord * 7 + yCoord * 3 + zCoord * 13 & 15)) % 20 != 0) {
            return;                                         // once a second, staggered by position
        }
        if (meta == SubstrateWildBlocksSC.FUMAROLE) {
            fumarole();
        } else if (meta == SubstrateWildBlocksSC.CRACKED_RTG) {
            RadiationSC.report(worldObj, xCoord, yCoord, zCoord, SubstrateRadiationSC.rtgLevel(), SubstrateRadiationSC.RTG_RADIUS);   // ПМ-6: with the multiplier
        }
    }

    // ------------------------------------------------------------------ ПА-4: the fumarole

    /** One second's helium into the neighbouring pipes / tanks of the mod (none: it is simply lost). */
    private void fumarole() {
        if (ModFluids.liquidHelium == null) {
            return;
        }
        int left = twoNext ? HELIUM_MB_MAX : HELIUM_MB_MIN;
        twoNext = !twoNext;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (left <= 0) {
                break;
            }
            IFluidHandler h = takerAt(d);
            if (h != null) {
                left -= h.fill(d.getOpposite(), new FluidStack(ModFluids.liquidHelium, left), true);
            }
        }
    }

    /** The mod's pipe (a conduit bundle holding one) or tank beside face `d`, or null (never loads a chunk). */
    private IFluidHandler takerAt(ForgeDirection d) {
        int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
        if (y < 0 || y > 255 || !worldObj.blockExists(x, y, z)) {
            return null;
        }
        TileEntity te = worldObj.getTileEntity(x, y, z);
        if (te instanceof TileEntityConduitBundleSC && ((TileEntityConduitBundleSC) te).has(ConduitKind.PIPE) || te instanceof TileEntityTankSC) {
            return (IFluidHandler) te;
        }
        return null;
    }

    // ------------------------------------------------------------------ ПА-6: the ion geyser

    private void geyserTick() {
        if (countdown <= 0) {
            countdown = GEYSER_MIN + worldObj.rand.nextInt(GEYSER_MAX - GEYSER_MIN + 1);
            return;
        }
        countdown--;
        double cx = xCoord + 0.5D, top = yCoord + 1.1D, cz = zCoord + 0.5D;
        if (countdown == WARN_TICKS) {
            worldObj.playSoundEffect(cx, top, cz, Reference.ASSETS + ":emp.charge", 0.8F, 1.4F);
        }
        if (countdown > 0 && countdown <= WARN_TICKS && countdown % SPARK_EVERY == 0 && worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("magicCrit", cx, top, cz, 10, 0.3D, 0.4D, 0.3D, 0.15D);
        }
        if (countdown == 0) {
            discharge(cx, top, cz);
        }
    }

    /** The discharge: GEYSER_DAMAGE of «Пробой» and GEYSER_DRAIN_PCT of every worn piece's charge to all within GEYSER_RADIUS. */
    @SuppressWarnings("unchecked")
    private void discharge(double cx, double top, double cz) {
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", cx, top + 0.5D, cz, 30, 0.6D, 1.2D, 0.6D, 0.2D);
            ((WorldServer) worldObj).func_147487_a("witchMagic", cx, top + 0.3D, cz, 20, 1.5D, 0.6D, 1.5D, 0.1D);
        }
        worldObj.playSoundEffect(cx, top, cz, Reference.ASSETS + ":field.zap", 1.2F, 0.8F);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cx - GEYSER_RADIUS, top - 1D - GEYSER_RADIUS, cz - GEYSER_RADIUS, cx + GEYSER_RADIUS,
                top + GEYSER_RADIUS, cz + GEYSER_RADIUS);
        for (EntityLivingBase e : (List<EntityLivingBase>) worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (!e.isEntityAlive() || e.getDistanceSq(cx, top - 0.1D, cz) > (GEYSER_RADIUS + 0.5D) * (GEYSER_RADIUS + 0.5D)) {
                continue;
            }
            if (e instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) e;
                if (p.capabilities.isCreativeMode) {
                    continue;
                }
                drainArmour(p, GEYSER_DRAIN_PCT * 10, 1000);
            }
            e.attackEntityFrom(BreakdownDamageSC.WORLD, GEYSER_DAMAGE);
        }
    }

    // ------------------------------------------------------------------ shared with the crack

    /**
     * Takes `parts` / `per` of the capacity (at least 1 EU) from every worn piece of the mod's armour of `p`
     * (ItemArmorSC.discharge - the pieces' NBT, synced by the inventory). @return some charge was taken
     */
    public static boolean drainArmour(EntityPlayer p, int parts, int per) {
        boolean took = false;
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.inventory.armorInventory[i];
            if (s != null && s.getItem() instanceof ItemArmorSC) {
                int amount = (int) Math.max(1L, (long) ItemArmorSC.capacityOf(s) * parts / per);
                took |= ItemArmorSC.discharge(s, amount) > 0;
            }
        }
        return took;
    }

    /** The world test: the geyser's next discharge in `ticks` (1 or more). */
    public void dischargeIn(int ticks) {
        countdown = Math.max(1, ticks);
    }

    /** The world test / WAILA: ticks to the geyser's next discharge (0 before its first tick). */
    public int ticksToDischarge() {
        return countdown;
    }

    /** `w` at (x, y, z) holds a vent of meta `meta` (the world test). */
    public static boolean isVent(World w, int x, int y, int z, int meta) {
        return w.getBlock(x, y, z) == SubstrateWildBlocksSC.vent && w.getBlockMetadata(x, y, z) == meta;
    }
}
