package com.sc.radiation;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Forge-bus events of the lead suit: the lower jump, the slower digging. */
public class RadiationEventsSC {

    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.entityLiving instanceof EntityPlayer) {
            LeadSuitSC.jump((EntityPlayer) event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.newSpeed = LeadSuitSC.digSpeed(event.entityPlayer, event.newSpeed);
    }
}
