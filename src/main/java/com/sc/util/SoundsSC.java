package com.sc.util;

import com.sc.Reference;
import com.sc.energy.GeneratorType;
import com.sc.machine.MachineType;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * The mod's sounds (assets/siliconage/sounds.json, synthesised - no borrowed samples). Working
 * machines and generators repeat a 2-second loop from the server, each block a little out of
 * step with its neighbours; events (power switch, lightning on a shield, a battery's mode) play
 * once. All of it can be turned down or off in the config (sounds.volume / sounds.machines).
 */
public final class SoundsSC {

    /** A loop is 2 s long; played every 38 ticks, its faded ends overlap and it never gaps. */
    private static final int PERIOD = 38;

    private SoundsSC() {
    }

    /** What a working block sounds like: the sound, its volume and pitch. */
    public static final class Loop {
        public final String name;
        public final float volume, pitch;

        public Loop(String name, float volume, float pitch) {
            this.name = name;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static Loop of(MachineType type) {
        switch (type) {
            case CRUSHER:
            case ROLLING_MACHINE: return new Loop("machine.grind", 0.5F, 1F);
            case BLAST_FURNACE:
            case KILN:
            case OXIDATION_FURNACE:
            case CZOCHRALSKI_PULLER:
            case CZOCHRALSKI_PULLER_EV:
            case BOILER_LV:
            case BOILER_MV:
            case ELECTRIC_FURNACE:
            case REFINERY: return new Loop("machine.furnace", 0.45F, 1F);
            case INDUCTION_FURNACE: return new Loop("machine.hum", 0.4F, 1.5F);
            case ORE_WASHER:
            case CHEM_REACTOR:
            case ETCHING_BATH:
            case CHLOR_ALKALI_ELECTROLYZER:
            case FLUID_CELL_FILLER: return new Loop("machine.fluid", 0.45F, 1F);
            case CVD_CHAMBER:
            case STEPPER:
            case STEPPER_EV:
            case ION_IMPLANTER:
            case SPUTTERER: return new Loop("machine.laser", 0.35F, 1F);
            case WIRE_SAW:
            case DICING_SAW: return new Loop("machine.saw", 0.4F, 1F);
            case CENTRIFUGE:
            case PHOTORESIST_COATER: return new Loop("machine.spin", 0.4F, 1F);
            default: return new Loop("machine.hum", 0.35F, 1F);   // air separator, packager, upgrade stations
        }
    }

    /** Null: a silent generator (the solar panels, the creative one). */
    public static Loop of(GeneratorType type) {
        switch (type) {
            case COMBUSTION: return new Loop("gen.engine", 0.55F, 1F);
            case SOLID_FUEL: return new Loop("gen.fire", 0.45F, 1F);
            case STEAM_TURBINE: return new Loop("gen.turbine", 0.5F, 1F);
            case GAS_TURBINE: return new Loop("gen.turbine", 0.5F, 1.15F);
            case WIND_TURBINE: return new Loop("gen.turbine", 0.3F, 0.7F);
            case WATER_WHEEL: return new Loop("gen.water", 0.45F, 1F);
            case GEOTHERMAL: return new Loop("machine.fluid", 0.4F, 0.7F);
            case FUEL_CELL: return new Loop("machine.hum", 0.3F, 1.3F);
            case THERMOELECTRIC: return new Loop("machine.hum", 0.2F, 0.8F);
            case RTG: return new Loop("gen.geiger", 0.3F, 1F);
            case PLASMA_GENERATOR:
            case PLASMA_REACTOR: return new Loop("gen.reactor", 0.6F, 1.2F);
            case FUSION_REACTOR: return new Loop("gen.reactor", 0.7F, 1F);
            case TOKAMAK: return new Loop("gen.reactor", 0.75F, 0.9F);
            case TOKAMAK_XV: return new Loop("gen.reactor", 0.85F, 0.8F);
            case EXO_REACTOR: return new Loop("gen.reactor", 0.8F, 0.8F);
            case SINGULAR_REACTOR: return new Loop("gen.reactor", 0.9F, 0.6F);
            default: return null;
        }
    }

    /**
     * One tick of a working block: every PERIOD ticks (offset by its position) its loop plays again.
     * The server sends the base volume; each client applies its own settings ({@link ClientFilter}).
     */
    public static void loop(TileEntity te, Loop loop) {
        World w = te.getWorldObj();
        if (loop == null || w == null || w.isRemote) {
            return;
        }
        long offset = (te.xCoord * 31L + te.yCoord * 7L + te.zCoord * 17L) & 0x3F;
        if ((w.getTotalWorldTime() + offset) % PERIOD == 0) {
            w.playSoundEffect(te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5, Reference.ASSETS + ":" + loop.name,
                    loop.volume, loop.pitch);
        }
    }

    /** A one-off sound at a place (server side: everyone near hears it, at their own volume setting). */
    public static void play(World w, double x, double y, double z, String name, float volume, float pitch) {
        if (w == null || w.isRemote) {
            return;
        }
        w.playSoundEffect(x, y, z, Reference.ASSETS + ":" + name, volume, pitch);
    }

    /**
     * Client: the player's own sounds.machines / sounds.volume, applied to every Silicon Age sound as it
     * starts (a dedicated server's config never decides what a player hears). Registered by the ClientProxy.
     */
    public static final class ClientFilter {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onSound(net.minecraftforge.client.event.sound.PlaySoundEvent17 e) {
            net.minecraft.client.audio.ISound s = e.result;
            if (s == null || s.getPositionedSoundLocation() == null
                    || !Reference.ASSETS.equals(s.getPositionedSoundLocation().getResourceDomain())) {
                return;
            }
            String p = s.getPositionedSoundLocation().getResourcePath();
            boolean working = p.startsWith("machine.") || p.startsWith("gen.") || p.startsWith("quarry.");
            if (ConfigSC.soundVolume <= 0F || (working && !ConfigSC.machineSounds)) {
                e.result = null;
                return;
            }
            if (ConfigSC.soundVolume < 1F && s instanceof net.minecraft.client.audio.PositionedSoundRecord) {
                e.result = new net.minecraft.client.audio.PositionedSoundRecord(s.getPositionedSoundLocation(),
                        s.getVolume() * ConfigSC.soundVolume, s.getPitch(), s.getXPosF(), s.getYPosF(), s.getZPosF());
            }
        }
    }

    /** The power switch's click, on or off, at a block. */
    public static void powerClick(TileEntity te, boolean on) {
        play(te.getWorldObj(), te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5, on ? "power.on" : "power.off", 0.6F, 1F);
    }
}
