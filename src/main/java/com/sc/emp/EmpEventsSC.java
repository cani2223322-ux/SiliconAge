package com.sc.emp;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * ПЭ-1 server events (registered on both buses by SCMod): the state to the player on login / respawn / a dimension
 * change and to whoever starts watching them; package A's per-tick work (EmpGunLogicSC.playerTick); its state
 * forgotten on logout. (The state itself lives in the entity data: kept over a logout, gone after death.)
 */
public final class EmpEventsSC {

    public static final EmpEventsSC INSTANCE = new EmpEventsSC();

    private EmpEventsSC() {
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EmpSC.syncTo(event.player, event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        EmpSC.syncTo(event.player, event.player);
        EmpGunLogicSC.forget(event.player);
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        EmpSC.syncTo(event.player, event.player);
        EmpGunLogicSC.forget(event.player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EmpGunLogicSC.forget(event.player);
    }

    /** Forge bus: another player comes into view - their state (the lights, sparks, the stream). */
    @SubscribeEvent
    public void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.target instanceof EntityPlayer && !(event.target.worldObj.isRemote)) {
            EmpStateSC s = EmpSC.state((EntityPlayer) event.target);
            if (!s.idle(EmpSC.now(event.target))) {
                EmpSC.syncTo((EntityPlayer) event.target, event.entityPlayer);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP && !event.player.worldObj.isRemote) {
            EmpGunLogicSC.playerTick((EntityPlayerMP) event.player);
        }
    }
}
