package com.sc.dim;

/**
 * A world provider whose name is translated in the Space Bridge's dimension list (docs/plan-substrate-tech.md §6,
 * rule ПВ-5). getDimensionName() stays plain Latin («Si-14 Substrate») for logs and JourneyMap; the bridge's
 * dimension list (TileEntityBridgeControllerSC.buildDimList) sends this key as well, and the screens show
 * Lang.tr(key) - «Подложка (Si-14)» / «Substrate (Si-14)». Called on a provider made by
 * DimensionManager.createProviderFor without a world: it must not touch worldObj.
 */
public interface INamedDimensionSC {

    /** The lang key of the dimension's name, e.g. "sc.dim.substrate.name". */
    String nameKey();
}
