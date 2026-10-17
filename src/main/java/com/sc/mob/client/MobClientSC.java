package com.sc.mob.client;

import com.sc.mob.EntityEclipseSC;
import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityJammerSC;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityOverheatedSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.EntitySentinelSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.EntityWarlordSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.9): the Substrate mobs' renderers (RenderBiped on ModelBiped - the worn
 * mod armour draws itself through ItemArmorSC.getArmorModel), called from ClientProxy.preInit.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.1): package M's mobs - the Trooper, the Jammer (its antenna horns), the Hollow Shell,
 * the Executioner (larger, the helium tank) on the biped with their telegraph poses (RenderSubstrateMobSC.lean), the Repair
 * Drone on its own model with the repair beam. Package X's «noise» is ruin.client.RuinClientSC's.
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §7.0..§7.4): the Overheated (its red-hot undersuit glowing through the gaps), the camp's
 * Warlord (a crest and a crimson cape), the Singular Sentinel (taller), «Затмение» (its flicker in a blink); package N's are
 * NoiseClientSC's. And «Затмение»'s hush (ПС-34, «когда оно рядом, звуки глохнут»): while this client's player is within
 * EntityEclipseSC.MUFFLE_RADIUS of one (looked for every HUSH_CHECK_TICKS of the client tick), every one-shot sound starting is
 * played at EntityEclipseSC.HUSH_VOLUME of its volume - only a positioned one-shot (PositionedSoundRecord with the linear
 * attenuation): not the music or a record, not a repeating or a ticking sound (their owners stop them by the very ISound they
 * started: a copy would never be stopped), not the GUI's clicks.
 */
@SideOnly(Side.CLIENT)
public final class MobClientSC {

    /** Ticks between two looks for a «Затмение» near the player. */
    private static final int HUSH_CHECK_TICKS = 10;

    private static boolean hushed;
    private static int hushCheck;

    private MobClientSC() {
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityLinemanSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, EntityLinemanSC.NAME));
        RenderingRegistry.registerEntityRenderingHandler(EntityMarauderSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, EntityMarauderSC.NAME));
        // Подложка Ф3
        RenderingRegistry.registerEntityRenderingHandler(EntityTrooperSC.class, new RenderTrooperSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityJammerSC.class, new RenderJammerSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityHollowShellSC.class, new RenderHollowShellSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityRepairDroneSC.class, new RenderRepairDroneSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityExecutionerSC.class, new RenderExecutionerSC());
        // Подложка Ф4
        RenderingRegistry.registerEntityRenderingHandler(EntityOverheatedSC.class, new RenderOverheatedSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityWarlordSC.class, new RenderWarlordSC());
        RenderingRegistry.registerEntityRenderingHandler(EntitySentinelSC.class, new RenderSentinelSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityEclipseSC.class, new RenderEclipseSC());
        Hush hush = new Hush();
        MinecraftForge.EVENT_BUS.register(hush);
        FMLCommonHandler.instance().bus().register(hush);
    }

    /** This client's player is within a «Затмение»'s hush now. */
    public static boolean hushed() {
        return hushed;
    }

    /** «Затмение»'s hush: the look for one near (the client tick) and the quieter sounds (PlaySoundEvent17). */
    public static final class Hush {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || ++hushCheck < HUSH_CHECK_TICKS) {
                return;
            }
            hushCheck = 0;
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer p = mc.thePlayer;
            boolean near = false;
            if (p != null && mc.theWorld != null) {
                double r2 = EntityEclipseSC.MUFFLE_RADIUS * EntityEclipseSC.MUFFLE_RADIUS;
                for (Object o : mc.theWorld.loadedEntityList) {
                    if (o instanceof EntityEclipseSC && ((Entity) o).isEntityAlive() && ((Entity) o).getDistanceSqToEntity(p) <= r2) {
                        near = true;
                        break;
                    }
                }
            }
            hushed = near;
        }

        @SubscribeEvent
        public void onSound(PlaySoundEvent17 e) {
            ISound s = e.result;
            if (!hushed || s == null || s instanceof ITickableSound || !(s instanceof PositionedSoundRecord) || s.canRepeat()
                    || e.category == SoundCategory.MUSIC || e.category == SoundCategory.RECORDS || s.getPositionedSoundLocation() == null
                    || s.getAttenuationType() != ISound.AttenuationType.LINEAR) {
                return;                                          // a GUI click (no attenuation) would land at 0, 0, 0
            }
            e.result = new PositionedSoundRecord(s.getPositionedSoundLocation(), s.getVolume() * EntityEclipseSC.HUSH_VOLUME, s.getPitch(),
                    s.getXPosF(), s.getYPosF(), s.getZPosF());
        }
    }
}
