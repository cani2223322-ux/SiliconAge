package com.sc.mob;

/**
 * Marker (docs/plan-substrate-tech.md §6, rule ПВ-8): one of the Substrate's own creatures - its hostiles now, its
 * villagers and guards later. With dimension.nativesCrossBridge = false the bridge never lets it through, even with
 * the Mass Compensator (TileEntityBridgeControllerSC.enter / BridgeTeleportSC). Being native is the class; being
 * «born in Si-14» (trophies, drops, reputation) is a flag of the single entity - BornInSubstrateSC.
 */
public interface ISubstrateNativeSC {
}
