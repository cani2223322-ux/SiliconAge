package com.sc.compat;

import com.sc.item.ArmorLogicSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import micdoodle8.mods.galacticraft.api.event.oxygen.GCCoreOxygenSuffocationEvent;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Galacticraft (modid "GalacticraftCore", soft dependency): on its airless worlds a player without
 * their oxygen gear gets GCCoreOxygenSuffocationEvent.Pre (Forge bus, cancelable) before every
 * suffocation hit. A helmet with the breathing function on and oxygen in its tank cancels it and
 * spends ArmorGasSC.OXYGEN_PER_SECOND a second (ArmorLogicSC.breatheInSpace).
 *
 * Registered by ShieldEventHandler only when Galacticraft is loaded - without it this class (and
 * the Galacticraft one it names) is never loaded. Compiled against libs/GalacticraftCore-*.jar,
 * which is not packed into our jar.
 */
public class GalacticraftSC {

    @SubscribeEvent
    public void onSuffocate(GCCoreOxygenSuffocationEvent.Pre event) {
        if (event.entityLiving instanceof EntityPlayer && ArmorLogicSC.breatheInSpace((EntityPlayer) event.entityLiving)) {
            event.setCanceled(true);
        }
    }
}
