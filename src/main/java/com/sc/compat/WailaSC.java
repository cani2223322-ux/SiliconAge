package com.sc.compat;

import java.util.List;

import com.sc.conduit.ConduitKind;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityConduitBundleSC;
import com.sc.tileentity.TileEntityEnergyStorageSC;
import com.sc.tileentity.TileEntityMachineSC;
import com.sc.tileentity.TileEntityTransformerSC;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * WAILA tooltip lines for the mod's blocks: stored energy and voltage tier of everything with an
 * energy buffer (machines, generators, storages, transformers, the field generator), a machine's
 * work and upgrades, and what a conduit bundle carries. The numbers come from the server (the
 * client copy of a tile's energy is never updated outside its GUI). Only loaded when WAILA is
 * present - it finds us through the IMC message SCMod.init sends.
 */
public class WailaSC implements IWailaDataProvider {

    /** Called by WAILA (IMC "register"). */
    public static void callbackRegister(IWailaRegistrar registrar) {
        WailaSC provider = new WailaSC();
        registrar.registerBodyProvider(provider, TileEntityEnergyBase.class);
        registrar.registerNBTProvider(provider, TileEntityEnergyBase.class);
        registrar.registerBodyProvider(provider, TileEntityConduitBundleSC.class);
        registrar.registerNBTProvider(provider, TileEntityConduitBundleSC.class);
        registrar.registerBodyProvider(provider, com.sc.tileentity.TileEntityTankSC.class);
        registrar.registerNBTProvider(provider, com.sc.tileentity.TileEntityTankSC.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        NBTTagCompound t = accessor.getNBTData();
        if (t == null) {
            return tip;
        }
        if (t.hasKey("scIgnition")) {
            // an unlit fusion reactor: what counts is how far its ignition charge has got, not the buffer
            tip.add(Lang.tr("sc.waila.ignition", String.valueOf(t.getLong("scIgnition")), String.valueOf(t.getLong("scIgnitionMax"))));
        } else if (t.hasKey("scEnergy")) {
            tip.add(Lang.tr("sc.waila.energy", t.getInteger("scEnergy"), t.getInteger("scMax")));
        }
        if (t.hasKey("scEnergy")) {
            Tier in = tier(t.getInteger("scIn"));
            Tier out = tier(t.getInteger("scOut"));
            if (t.getBoolean("scTransformer")) {
                tip.add(Lang.tr(t.getBoolean("scStepUp") ? "sc.waila.stepup" : "sc.waila.stepdown"));
                tip.add(Lang.tr("sc.waila.tierinout", in.name(), in.getVoltage(), out.name(), out.getVoltage()));
            } else if (t.getBoolean("scSource") && !t.getBoolean("scSink")) {
                if (t.hasKey("scGen")) {
                    tip.add(Lang.tr("sc.waila.gen", t.getInteger("scGen")));
                }
                tip.add(Lang.tr("sc.waila.tierout", out.name(), out.getVoltage()));
                if (t.hasKey("scTokSv") && !t.getBoolean("scTokSv")) {
                    tip.add(Lang.tr("sc.waila.tok.nosv"));                // a Tokamak XV kept at XV: nothing beside it takes SV
                }
            } else {
                tip.add(Lang.tr("sc.waila.tier", in.name(), in.getVoltage()));
            }
        }
        if (t.hasKey("scFlow")) {
            int flow = t.getInteger("scFlow");
            tip.add(Lang.tr("sc.waila.flow", (flow > 0 ? "+" : "") + flow));
        }
        if (t.hasKey("scOutFaces")) {                                  // a storage with Output Splitters
            tip.add(Lang.tr("sc.waila.outputs", t.getInteger("scOutFaces"), t.getInteger("scOutTotal")));
        }
        if (t.hasKey("scStatus")) {
            tip.add(com.sc.machine.MachineStatus.byOrdinal(t.getInteger("scStatus")).localized());
            int ticks = t.getInteger("scTicks");
            if (ticks > 0) {
                tip.add(Lang.tr("sc.waila.progress", t.getInteger("scProgress") * 100 / ticks));
            }
            tip.add(Lang.tr("sc.waila.usage", t.getInteger("scUsage")));
            if (t.getInteger("scUpgrades") > 0) {
                tip.add(Lang.tr("sc.waila.upgrades", t.getInteger("scUpgrades")));
            }
        }
        if (t.hasKey("scCable")) {
            int c = t.getInteger("scCable");
            if (c >= 0 && c < com.sc.energy.CableType.values().length) {   // a newer server's cable on an older client: skip
                com.sc.energy.CableType type = com.sc.energy.CableType.values()[c];
                tip.add(Lang.tr("sc.waila.cable", type.tier.name(), type.tier.getVoltage(), type.maxThroughput(), type.maxAmps));
            }
            net.minecraftforge.fluids.Fluid fluid = t.hasKey("scFluid") ? FluidRegistry.getFluid(t.getString("scFluid")) : null;
            if (fluid != null) {
                FluidStack inPipe = new FluidStack(fluid, t.getInteger("scFluidAmount"));
                tip.add(Lang.tr("sc.waila.fluid", inPipe.getLocalizedName(), inPipe.amount, t.getInteger("scFluidCap")));
            }
        }
        if (t.hasKey("scWlKind")) {
            wireless(t, tip);
        }
        if (t.hasKey("scShowerSt")) {
            tip.add(Lang.tr("sc.shower.status." + t.getInteger("scShowerSt")));
            tip.add(Lang.tr("sc.waila.shower", t.getInteger("scShowerWater"), com.sc.tileentity.TileEntityShowerSC.TANK,
                    t.getInteger("scShowerPlayers")));
        }
        if (t.hasKey("scSingPhase")) {                                // the Singular Reactor: its hole
            int ph = t.getInteger("scSingPhase");
            tip.add(Lang.tr("sc.waila.sing.phase." + ph));
            if (ph == com.sc.tileentity.SingularReactorSC.PHASE_RUN || ph == com.sc.tileentity.SingularReactorSC.PHASE_DRAIN) {
                tip.add(Lang.tr("sc.waila.sing.hole", String.format(java.util.Locale.ROOT, "%.1f", t.getInteger("scSingMass") / 10F).replace('.', ','),
                        t.getInteger("scSingCont")));
                tip.add(Lang.tr("sc.waila.sing.power", t.getInteger("scSingOut")));
            } else if (ph == com.sc.tileentity.SingularReactorSC.PHASE_IDLE && t.getInteger("scSingEvent") > 0) {
                tip.add(Lang.tr("sc.waila.sing.event." + t.getInteger("scSingEvent")));
            } else if (ph == com.sc.tileentity.SingularReactorSC.PHASE_IDLE) {
                tip.add(Lang.tr(t.getBoolean("scSingReady") ? "sc.waila.sing.ready" : "sc.waila.sing.notready"));
            }
        }
        if (t.hasKey("scBig")) {
            tip.add(t.getInteger("scBig") == 2 ? Lang.tr("sc.waila.tok.big", String.valueOf(t.getInteger("scStab")))
                    : Lang.tr("sc.waila.tok.ready"));
        }
        if (t.hasKey("scRad")) {
            float rad = t.getFloat("scRad");
            tip.add(t.getBoolean("scRadShield") ? Lang.tr("sc.waila.rad.shielded")
                    : rad > 0 ? Lang.tr("sc.waila.rad", com.sc.radiation.RadiationSC.fmt(rad), t.getInteger("scRadR"))
                    : Lang.tr("sc.waila.rad.none"));
        }
        if (t.hasKey("scOff")) {
            tip.add(Lang.tr("sc.waila.off"));
        }
        if (t.hasKey("scTankCap")) {
            net.minecraftforge.fluids.Fluid tf = t.hasKey("scTankFluid") ? FluidRegistry.getFluid(t.getString("scTankFluid")) : null;
            if (tf == null) {
                tip.add(Lang.tr("sc.tank.tooltip.empty", t.getInteger("scTankCap")));
            } else {
                FluidStack inTank = new FluidStack(tf, t.getInteger("scTankAmount"));
                tip.add(Lang.tr(tf.isGaseous(inTank) ? "sc.tank.tooltip.gas" : "sc.tank.tooltip.fluid",
                        inTank.getLocalizedName(), inTank.amount, t.getInteger("scTankCap")));
            }
            if (t.getBoolean("scTankOut")) {
                tip.add(Lang.tr("sc.tank.output.on"));
            }
        }
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    /** Server side: just the numbers the body needs, not the whole tile. */
    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y, int z) {
        if (te instanceof TileEntityEnergyBase) {
            TileEntityEnergyBase e = (TileEntityEnergyBase) te;
            tag.setInteger("scEnergy", e.getEnergyStored());
            tag.setInteger("scMax", e.getMaxEnergyStored());
            tag.setInteger("scIn", e.inputTier().ordinal());
            tag.setInteger("scOut", e.outputTier().ordinal());
            tag.setBoolean("scSource", e.isEnergySource());
            tag.setBoolean("scSink", e.isEnergySink());
        }
        if (te instanceof com.sc.tileentity.TileEntityGeneratorSC) {
            com.sc.tileentity.TileEntityGeneratorSC g = (com.sc.tileentity.TileEntityGeneratorSC) te;
            tag.setInteger("scGen", g.getLastOutput());                  // what it makes right now - not its tier's voltage
            if (g.getGeneratorType().needsIgnition() && !g.isIgnited()) {
                tag.setLong("scIgnition", g.getIgnitionEU());
                tag.setLong("scIgnitionMax", g.ignitionNeed());
            }
        }
        if (te instanceof TileEntityTransformerSC) {
            tag.setBoolean("scTransformer", true);
            tag.setBoolean("scStepUp", ((TileEntityTransformerSC) te).isStepUp());
        }
        if (te instanceof TileEntityEnergyStorageSC) {
            TileEntityEnergyStorageSC st = (TileEntityEnergyStorageSC) te;
            tag.setInteger("scFlow", st.getFlowPerTick());
            int faces = st.outputFaces().length;
            if (faces > 1) {
                tag.setInteger("scOutFaces", faces);
                tag.setInteger("scOutTotal", st.outputTier().getVoltage() * st.packetsPerTick());
            }
        }
        if (te instanceof TileEntityMachineSC) {
            TileEntityMachineSC m = (TileEntityMachineSC) te;
            tag.setInteger("scStatus", m.getStatus().ordinal());
            tag.setInteger("scProgress", m.getProgressTicks());
            tag.setInteger("scTicks", m.getCurrentRecipeTicks());
            tag.setInteger("scUsage", m.effectiveEuPerTick());
            int upgrades = 0;
            for (int i = TileEntityMachineSC.FIRST_UPGRADE_SLOT; i < m.getSizeInventory(); i++) {
                ItemStack s = m.getStackInSlot(i);
                upgrades += s == null ? 0 : s.stackSize;
            }
            tag.setInteger("scUpgrades", upgrades);
        }
        if (te instanceof TileEntityConduitBundleSC) {
            TileEntityConduitBundleSC b = (TileEntityConduitBundleSC) te;
            tag.setInteger("scCable", b.has(ConduitKind.CABLE) ? b.getCable().ordinal() : -1);
            FluidStack fluid = b.getFluid();
            if (fluid != null && fluid.amount > 0) {
                tag.setString("scFluid", fluid.getFluid().getName());
                tag.setInteger("scFluidAmount", fluid.amount);
                tag.setInteger("scFluidCap", b.getFluidCapacity());
            }
        }
        if (te instanceof TileEntityEnergyBase && !(te instanceof com.sc.tileentity.TileEntityFieldGeneratorSC)
                && !(te instanceof com.sc.tileentity.TileEntityShowerSC) && !(te instanceof com.sc.tileentity.TileEntityWirelessSC)
                && !((TileEntityEnergyBase) te).isPowerOn()) {                // (the shower and wireless say so in their status)
            tag.setBoolean("scOff", true);
        }
        if (te instanceof com.sc.tileentity.TileEntityGeneratorSC
                && com.sc.tileentity.TileEntityGeneratorSC.radiationBase(((com.sc.tileentity.TileEntityGeneratorSC) te).getGeneratorType()) > 0) {
            com.sc.tileentity.TileEntityGeneratorSC g = (com.sc.tileentity.TileEntityGeneratorSC) te;
            tag.setFloat("scRad", com.sc.util.ConfigSC.radiation ? g.radiationLevel() * com.sc.util.ConfigSC.radiationMultiplier : 0F);
            tag.setInteger("scRadR", g.radiationRadiusNow());
            tag.setBoolean("scRadShield", g.isShielded());
        }
        if (te instanceof com.sc.tileentity.TileEntityGeneratorSC
                && ((com.sc.tileentity.TileEntityGeneratorSC) te).getGeneratorType() == com.sc.energy.GeneratorType.TOKAMAK_XV) {
            com.sc.tileentity.TileEntityGeneratorSC g = (com.sc.tileentity.TileEntityGeneratorSC) te;
            tag.setBoolean("scTokSv", g.isSvOutput());                 // its output tier is outputTier() above: SV or XV
            if (g.isBigRunning()) {
                tag.setInteger("scBig", 2);
                tag.setInteger("scStab", Math.round(g.getStability()));
            } else if (g.isBigReady()) {
                tag.setInteger("scBig", 1);
            }
        }
        if (te instanceof com.sc.tileentity.TileEntityGeneratorSC && ((com.sc.tileentity.TileEntityGeneratorSC) te).singular()) {
            com.sc.tileentity.SingularReactorSC s = ((com.sc.tileentity.TileEntityGeneratorSC) te).getSingular();
            tag.setInteger("scSingPhase", s.getPhase());
            tag.setInteger("scSingMass", (int) Math.round(s.getMass() * 1000));
            tag.setInteger("scSingCont", Math.round(s.getContainment()));
            tag.setInteger("scSingOut", s.outputNow());
            tag.setInteger("scSingEvent", s.getEvent());
            tag.setBoolean("scSingReady", s.isReady());
        }
        if (te instanceof com.sc.tileentity.TileEntityShowerSC) {
            com.sc.tileentity.TileEntityShowerSC sh = (com.sc.tileentity.TileEntityShowerSC) te;
            tag.setInteger("scShowerSt", sh.getStatus());
            tag.setInteger("scShowerWater", sh.getTank().getFluidAmount());
            tag.setInteger("scShowerPlayers", sh.getPlayers());
        }
        if (te instanceof com.sc.tileentity.TileEntityWirelessSC) {
            com.sc.tileentity.TileEntityWirelessSC w = (com.sc.tileentity.TileEntityWirelessSC) te;
            int[] p = w.partnerPos();
            tag.setInteger("scWlKind", w.getKind());
            tag.setInteger("scWlSt", w.getStatus());
            tag.setBoolean("scWlLinked", w.hasLink());
            if (player != null && w.allowed(player)) {              // where the other end is: only for those with access
                tag.setBoolean("scWlCoords", true);
                tag.setInteger("scWlX", p[0]);
                tag.setInteger("scWlY", p[1]);
                tag.setInteger("scWlZ", p[2]);
                tag.setInteger("scWlDim", p[3]);
            }
            tag.setInteger("scWlDist", w.getDistance());
            tag.setInteger("scWlLoss", w.getLossPct());
            tag.setInteger("scWlFlow", w.getFlow());
            tag.setBoolean("scWlGiving", w.isGiving());
            ItemStack crystal = w.getStackInSlot(com.sc.tileentity.TileEntityWirelessSC.SLOT_CRYSTAL);
            long pair = com.sc.item.ItemEntangledCrystalSC.pairOf(crystal);
            if (pair != 0) {
                tag.setString("scWlPair", com.sc.item.ItemEntangledCrystalSC.pairName(pair));
                tag.setInteger("scWlLife", (int) Math.ceil(com.sc.item.ItemEntangledCrystalSC.lifeOf(crystal) * 100.0
                        / com.sc.item.ItemEntangledCrystalSC.LIFE_MAX));
            }
        }
        if (te instanceof com.sc.tileentity.TileEntityTankSC) {
            com.sc.tileentity.TileEntityTankSC tank = (com.sc.tileentity.TileEntityTankSC) te;
            tag.setInteger("scTankCap", tank.getTank().getCapacity());
            tag.setBoolean("scTankOut", tank.isAutoOutput());
            FluidStack f = tank.getTank().getFluid();
            if (f != null && f.amount > 0) {
                tag.setString("scTankFluid", f.getFluid().getName());
                tag.setInteger("scTankAmount", f.amount);
            }
        }
        return tag;
    }

    /** A transmitter / receiver: status, the other end, distance and loss; a translator: pair, role, the other end, crystal. */
    private static void wireless(NBTTagCompound t, List<String> tip) {
        int kind = t.getInteger("scWlKind"), st = t.getInteger("scWlSt");
        tip.add(Lang.tr("sc.wl.status." + st));
        if (kind == com.sc.tileentity.TileEntityWirelessSC.QUANTUM) {
            if (t.hasKey("scWlPair")) {
                tip.add(Lang.tr("sc.waila.wl.pair", t.getString("scWlPair"), Lang.tr(t.getBoolean("scWlGiving") ? "sc.wl.btn.give" : "sc.wl.btn.take"),
                        t.getInteger("scWlLife")));
            }
            if (t.getBoolean("scWlCoords") && (st == com.sc.tileentity.TileEntityWirelessSC.ST_OK
                    || st == com.sc.tileentity.TileEntityWirelessSC.ST_FULL || st == com.sc.tileentity.TileEntityWirelessSC.ST_IDLE)) {
                tip.add(Lang.tr("sc.waila.wl.other", Lang.trOr("sc.wl.dim." + t.getInteger("scWlDim"), Lang.tr("sc.wl.dim.other", t.getInteger("scWlDim"))),
                        t.getInteger("scWlX"), t.getInteger("scWlY"), t.getInteger("scWlZ")));
            }
        } else if (t.getBoolean("scWlLinked")) {
            boolean tx = kind == com.sc.tileentity.TileEntityWirelessSC.TRANSMITTER;
            if (t.getBoolean("scWlCoords")) {
                tip.add(Lang.tr(tx ? "sc.waila.wl.to" : "sc.waila.wl.from", t.getInteger("scWlX"), t.getInteger("scWlY"), t.getInteger("scWlZ")));
            } else {
                tip.add(Lang.tr(tx ? "sc.waila.wl.to.hidden" : "sc.waila.wl.from.hidden"));
            }
            if (t.getInteger("scWlDist") > 0) {
                tip.add(Lang.tr("sc.waila.wl.dist", t.getInteger("scWlDist"), t.getInteger("scWlLoss")));
            }
        }
        if (t.getInteger("scWlFlow") > 0) {
            tip.add(Lang.tr("sc.waila.wl.flow", t.getInteger("scWlFlow")));
        }
    }

    private static Tier tier(int ordinal) {
        Tier[] v = Tier.values();
        return v[ordinal >= 0 && ordinal < v.length ? ordinal : 0];
    }
}
