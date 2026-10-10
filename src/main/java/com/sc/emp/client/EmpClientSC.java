package com.sc.emp.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * ПЭ-1, client side (ClientProxy.preInit calls register): wires up the packages' client classes - D's model, HUD and
 * effects, C's keys - and empties the synced state on leaving a server. Coordinator-owned: packages edit their own classes.
 */
public final class EmpClientSC {

    public static final EmpClientSC INSTANCE = new EmpClientSC();

    private EmpClientSC() {
    }

    public static void register() {
        FMLCommonHandler.instance().bus().register(INSTANCE);
        RenderEmpGunSC.register();          // package D: IItemRenderer «Сифон-капсула»
        EmpHudSC.register();                // package D: HUD overlays
        EmpFxSC.register();                 // package D: beams, stream, sparks (EmpNetSC.FX)
        EmpKeysSC.register();               // package C: key bindings, Shift + wheel
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        com.sc.emp.EmpSC.clientClear();
        com.sc.emp.EmpNetSC.FX.clear();
        com.sc.emp.EmpNetSC.lastAim = null;
    }
}
