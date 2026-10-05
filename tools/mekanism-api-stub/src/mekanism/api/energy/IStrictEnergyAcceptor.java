package mekanism.api.energy;

import net.minecraftforge.common.util.ForgeDirection;

/** Compile-only stub (see IStrictEnergyStorage). */
public interface IStrictEnergyAcceptor extends IStrictEnergyStorage {
    /** @return the energy used */
    double transferEnergyToAcceptor(ForgeDirection side, double amount);

    boolean canReceiveEnergy(ForgeDirection side);
}
