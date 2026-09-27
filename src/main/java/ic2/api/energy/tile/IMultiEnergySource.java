package ic2.api.energy.tile;

/**
 * Compile-time stand-in for Industrial Upgrade's interface of the same name (its energy net sends
 * getMultibleEnergyPacketAmount() packets a tick from such a source). Not packed into the jar.
 */
public interface IMultiEnergySource extends IEnergySource {

    boolean sendMultibleEnergyPackets();

    double getMultibleEnergyPacketAmount();
}
