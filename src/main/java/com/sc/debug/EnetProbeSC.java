package com.sc.debug;

import java.util.ArrayList;
import java.util.List;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.NodeStats;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/** The IC2 side of /scenergy - only ever loaded with IC2 present (CommandEnergySC checks). */
final class EnetProbeSC {

    private EnetProbeSC() {
    }

    static boolean isEnergyTile(TileEntity te) {
        return te instanceof IEnergyTile;
    }

    static String netName() {
        return EnergyNet.instance == null ? "null" : EnergyNet.instance.getClass().getName();
    }

    static List<String> describe(TileEntity te) {
        List<String> lines = new ArrayList<String>();
        StringBuilder b = new StringBuilder("  IC2:");
        try {
            if (te instanceof IEnergySink) {
                IEnergySink s = (IEnergySink) te;
                b.append(" sinkTier=").append(s.getSinkTier()).append(" demanded=").append(s.getDemandedEnergy());
            }
            if (te instanceof IEnergySource) {
                IEnergySource s = (IEnergySource) te;
                b.append(" sourceTier=").append(s.getSourceTier()).append(" offered=").append(s.getOfferedEnergy());
            }
            if (te instanceof IEnergyConductor) {
                IEnergyConductor c = (IEnergyConductor) te;
                b.append(" loss=").append(c.getConductionLoss()).append(" breakdown=").append(c.getConductorBreakdownEnergy())
                        .append(" insulation=").append(c.getInsulationEnergyAbsorption()).append("/").append(c.getInsulationBreakdownEnergy());
            }
            NodeStats n = EnergyNet.instance.getNodeStats(te);
            if (n != null) {
                b.append(" node in=").append(n.getEnergyIn()).append(" out=").append(n.getEnergyOut()).append(" V=").append(n.getVoltage());
            }
            b.append(" emitted=").append(EnergyNet.instance.getTotalEnergyEmitted(te)).append(" sunk=").append(EnergyNet.instance.getTotalEnergySunken(te));
        } catch (Throwable t) {
            b.append(" !").append(t);
        }
        lines.add(b.toString());
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity n = te.getWorldObj().getTileEntity(te.xCoord + d.offsetX, te.yCoord + d.offsetY, te.zCoord + d.offsetZ);
            if (!(n instanceof IEnergyTile)) {
                continue;
            }
            StringBuilder s = new StringBuilder("    " + d + " -> " + n.getClass().getSimpleName() + ":");
            try {
                if (te instanceof IEnergyEmitter) {
                    s.append(" emitsTo=").append(((IEnergyEmitter) te).emitsEnergyTo(n, d));
                }
                if (te instanceof IEnergyAcceptor) {
                    s.append(" acceptsFrom=").append(((IEnergyAcceptor) te).acceptsEnergyFrom(n, d));
                }
                if (n instanceof IEnergyAcceptor) {
                    s.append(" | it acceptsFrom=").append(((IEnergyAcceptor) n).acceptsEnergyFrom(te, d.getOpposite()));
                }
                if (n instanceof IEnergyEmitter) {
                    s.append(" it emitsTo=").append(((IEnergyEmitter) n).emitsEnergyTo(te, d.getOpposite()));
                }
                TileEntity viaNet = EnergyNet.instance.getNeighbor(te, d);
                s.append(" net neighbour=").append(viaNet == null ? "none" : viaNet.getClass().getSimpleName());
            } catch (Throwable t) {
                s.append(" !").append(t);
            }
            lines.add(s.toString());
        }
        return lines;
    }
}
