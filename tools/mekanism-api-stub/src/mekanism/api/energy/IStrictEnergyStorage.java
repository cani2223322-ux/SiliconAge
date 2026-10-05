package mekanism.api.energy;

/**
 * Compile-only stub of Mekanism's API (Mekanism 1.7.10): only the methods Silicon Age calls or
 * implements, same package, names and signatures. Built into libs/mekanism-api-stub-compileonly.jar
 * (tools/mekanism-api-stub/build.sh) and never packed into the mod's jar - at runtime the real
 * Mekanism API is used, or the interfaces are stripped by @Optional.Interface.
 */
public interface IStrictEnergyStorage {
    double getEnergy();

    void setEnergy(double energy);

    double getMaxEnergy();
}
