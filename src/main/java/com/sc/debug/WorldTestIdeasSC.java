package com.sc.debug;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * The world tests of the proposal of 2026-10-26 (docs/plan-ideas-2026-10-26.md), -Dsc.worldtest=true: SCMod.serverStarting calls
 * register() after the others (the coordinator's). Each item that has a world test registers its own class here - one line an item, in
 * its slot below - and keeps to its own place in the world (docs/plan-ideas-2026-10-26.md, «Проверки»: the X band of each item).
 */
public final class WorldTestIdeasSC {

    private WorldTestIdeasSC() {
    }

    public static void register() {
        cpw.mods.fml.common.eventhandler.EventBus bus = FMLCommonHandler.instance().bus();
        // ПД-1: bus.register(new WorldTestContractsSC());
        // ПД-2: bus.register(new WorldTestSquadSC());
        // ПД-3: bus.register(new WorldTestAlarmSC());
        // ПД-4: bus.register(new WorldTestWantedSC());
        // ПД-5: bus.register(new WorldTestGatherersSC());
        // ПД-6: bus.register(new WorldTestProtocolsSC());
        // ПД-7: bus.register(new WorldTestRelaySC());
        // ПД-8: bus.register(new WorldTestTunnelsSC());
        // ОМ-1: bus.register(new WorldTestRigSubstrateSC());
        bus.register(new WorldTestComparatorSC());         // ОМ-5
        bus.register(new WorldTestSalvageSC());            // ОМ-6
        bus.register(new WorldTestEnergyBankSC());         // ОМ-7
    }
}
