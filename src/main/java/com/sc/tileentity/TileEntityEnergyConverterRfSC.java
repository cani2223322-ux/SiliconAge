package com.sc.tileentity;

/**
 * The Energy Converter speaking Redstone Flux: the same tile with CoFH's IEnergyHandler declared (the
 * methods are TileEntityEnergyConverterSC's - they use only primitives). Loaded and registered only when
 * the RF API is on the class path (TileEntityEnergyConverterSC.tileClass()), under the same tile id, so a
 * world keeps its converters with or without it. Not @Optional: Industrial Upgrade ships the API classes
 * without declaring the API to FML, so an @Optional.Interface("CoFHAPI|energy") would be stripped there.
 */
public class TileEntityEnergyConverterRfSC extends TileEntityEnergyConverterSC implements cofh.api.energy.IEnergyHandler {

    @Override
    public boolean speaksRf() {
        return true;
    }
}
