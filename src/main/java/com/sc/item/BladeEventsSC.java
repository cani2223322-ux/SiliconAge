package com.sc.item;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * The Singular blade's events (BladeSingularSC), registered on both Forge's bus and FML's (common side):
 * the server tick (queued keys, collapses, tethers), the perfect parry, kill points («Жатва душ»), the
 * Guardian's last chance, logging out.
 */
public final class BladeEventsSC {

    public static final BladeEventsSC INSTANCE = new BladeEventsSC();

    private BladeEventsSC() {
    }

    /** FML bus: the queued key presses / form changes, the collapses and the tethers. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BladeSingularSC.serverTick();
        }
    }

    /** FML bus: the player's transient blade state goes with them. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BladeSingularSC.forget(event.player);
    }

    /** Perfect parry: after the armour's own stops (a cancelled attack costs no argon). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAttacked(LivingAttackEvent event) {
        if (event.entityLiving instanceof EntityPlayer && !event.entityLiving.worldObj.isRemote
                && BladeSingularSC.parry((EntityPlayer) event.entityLiving, event.source)) {
            event.setCanceled(true);
        }
    }

    /** Guardian 5 «Последний шанс»: first, so nothing else treats the player as dead. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeathFirst(LivingDeathEvent event) {
        if (event.entityLiving instanceof EntityPlayer && !event.entityLiving.worldObj.isRemote
                && BladeSingularSC.lastChance((EntityPlayer) event.entityLiving, event.source)) {
            event.setCanceled(true);
        }
    }

    /** Kill points for the Singular blade in the killer's hand. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        BladeSingularSC.killed(event.entityLiving, event.source);
    }
}
