package com.sc.radiation;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Forge-bus events: the lead suit's lower jump and slower digging; the dose kept when the player
 * entity is remade (leaving the End - all of it; after a death - up to the weakness step); a world unloading takes its radiation
 * sources, its wireless tiles and its Tokamak XV ports out of the registries.
 */
public class RadiationEventsSC {

    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.entityLiving instanceof EntityPlayer) {
            LeadSuitSC.jump((EntityPlayer) event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!event.wasDeath) {
            RadiationSC.copy(event.original, event.entityPlayer);
        } else {
            RadiationSC.copyAfterDeath(event.original, event.entityPlayer);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) {
            RadiationSC.forgetDimension(event.world.provider.dimensionId);
            com.sc.tileentity.TileEntityWirelessSC.forgetWorld(event.world);
            com.sc.tileentity.TileEntityGeneratorSC.forgetPorts(event.world);
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.newSpeed = LeadSuitSC.digSpeed(event.entityPlayer, event.newSpeed);
    }
}
