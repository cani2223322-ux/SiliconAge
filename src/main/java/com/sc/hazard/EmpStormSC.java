package com.sc.hazard;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.util.SoundsSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §5, rules ПФ-1..ПФ-8; proposal §9 «ЭМИ-буря»): the Substrate's own weather - the EMP
 * storm. Registered by SCMod on FML's bus (the world tick, logins, dimension changes, respawns) and Forge's (WorldEvent.Unload:
 * the schedule's memory of an unloaded Substrate). The API below is what the other
 * packages and the core ask - signatures fixed (the coordinator's); the bodies are S's.
 * <ul>
 * <li>the schedule (StormRulesSC, StormDataSC): once a game day the roll; WARNING 30 s - the siren in every loaded dome (at
 * its core, heard within 64), the Tuners go home (EntityAITunerHomeSC), the sky turns violet; STORM 3..5 min;</li>
 * <li>a pulse every 20..40 s (pulse): players (not creative) and armoured mobs (IEmpTargetSC, empArmoured) under the open sky
 * within StormRulesSC.REACH of a player - one random worn piece of the mod's suppressed 3..4 s (EmpSC.stormHit /
 * MobArmorSC.stormHit: immunity respected, a piece still suppressed not extended - ОР-1), the helium boiling; the settlements'
 * Tuners and guards, the pets - never; a Hollow
 * Shell reboots (EntityHollowShellSC.reboot); cover - coveredFrom (StormPulseSC);</li>
 * <li>the clients: HazardNetSC.STORM_STATE on every phase change and to a player arriving / joining / respawning in the
 * Substrate, STORM_PULSE on a pulse;</li>
 * <li>the two other weathers (§5.5, §5.6): the Silica Haze (a day's roll, 2..3 min - HAZE_STATE; the fog is W's, the mobs'
 * aim sightRange) and the Singular Emission (a week's roll, 10 min - EMISSION_STATE; its effects are the radiation's and M's);
 * the same rule dimension.empStorms switches all three.</li>
 * </ul>
 * The schedule is looked at every SCHEDULE_TICKS of the Substrate's world tick (END), the pulses every tick of a storm; a time
 * jump (a /time set, a night slept through - the clock is the Overworld's getWorldTime) sends the state afresh. Nothing is
 * simulated without players (ПФ-8): a window that passed while the world was unloaded is simply over. The chat lines go to the
 * players in the Substrate (sc.storm.*, sc.haze.*, sc.emission.*). Server only for every call below (a client: StormStateSC).
 */
public final class EmpStormSC {

    public static final EmpStormSC INSTANCE = new EmpStormSC();
    /** The schedule is looked at this often (ticks); the siren sounds this often in the warning, heard this far (blocks). */
    public static final int SCHEDULE_TICKS = 20, SIREN_TICKS = 100, SIREN_RANGE = 64;
    /** The siren (sounds.json, package S): "siliconage:" + this; a sound is heard 16 x its volume far. */
    public static final String SND_SIREN = "storm.siren";
    public static final float SIREN_VOLUME = SIREN_RANGE / 16F;
    /** World time and total time drifting apart by more than this between two looks: a time jump - the state is sent again. */
    private static final int JUMP = 40;

    /** Per Substrate world (by dimension id): what the schedule saw last (lost on a restart - its first look sends afresh). */
    private static final Map<Integer, Mem> MEM = new HashMap<Integer, Mem>();
    /** The pulses' spacing (the order of things, not the world's: the day's roll is by the seed). */
    private static final Random RAND = new Random();

    private static final class Mem {
        boolean seen, haze, emission;
        int phase = StormStateSC.CALM;
        /** The warning's length the clients' tint grows over: WARN_TICKS, a storm by hand's own. */
        int warnTicks = StormRulesSC.WARN_TICKS;
        long lastNow, lastTotal, sirenAt = Long.MIN_VALUE / 2;
    }

    private EmpStormSC() {
    }

    /** The phase in world `w` now (StormStateSC.CALM / WARNING / STORM): the Substrate only, the rule dimension.empStorms on. */
    public static int phase(World w) {
        if (w == null || !SubstrateSC.is(w) || !SubstrateConfigSC.empStorms) {
            return StormStateSC.CALM;
        }
        if (w.isRemote) {
            return StormStateSC.clientPhase();
        }
        StormDataSC d = StormDataSC.get(w);
        return d == null ? StormStateSC.CALM : d.phase(w.getWorldTime());
    }

    /** ПФ-5..ПФ-7: a storm runs in `w` (the solar panels, the swarms x3, the Striders, the trophies x1.5). */
    public static boolean active(World w) {
        return phase(w) == StormStateSC.STORM;
    }

    /** ПФ-9: the Silica Haze is on in `w` (the Substrate, the rule dimension.empStorms on - its weather switch). */
    public static boolean haze(World w) {
        if (w == null || !SubstrateSC.is(w) || !SubstrateConfigSC.empStorms) {
            return false;
        }
        if (w.isRemote) {
            return StormStateSC.clientHazeLeft(w.getTotalWorldTime()) > 0;
        }
        StormDataSC d = StormDataSC.get(w);
        return d != null && d.hazeAt(w.getWorldTime());
    }

    /**
     * ПФ-9: how far a mob of `w` may see to aim a beam or a ПЭ-1 - `normal`, or StormRulesSC.HAZE_SIGHT in the haze («вражеские
     * стрелки слепнут так же, как игрок»). Server.
     */
    public static double sightRange(World w, double normal) {
        return haze(w) ? Math.min(normal, StormRulesSC.HAZE_SIGHT) : normal;
    }

    /**
     * ПФ-10: the Singular Emission runs in `w` (the Rift's radiation x3 - SubstrateRadiationSC; the Crumbs' chances x2 and
     * «Затмение» x3 - package M). Server (a client: StormStateSC.clientEmission()).
     */
    public static boolean emission(World w) {
        if (w == null || !SubstrateSC.is(w) || !SubstrateConfigSC.empStorms) {
            return false;
        }
        if (w.isRemote) {
            return StormStateSC.clientEmission();
        }
        StormDataSC d = StormDataSC.get(w);
        return d != null && d.emissionAt(w.getWorldTime());
    }

    /** ПФ-4: the warning runs - the domes' siren, the Tuners going home. */
    public static boolean warning(World w) {
        return phase(w) == StormStateSC.WARNING;
    }

    /** ПФ-5: the mod's own solar panel at (x, y, z) gives nothing now (TileEntityGeneratorSC.updateSolar asks; other mods' panels work on). */
    public static boolean blocksSolar(World w, int x, int y, int z) {
        return active(w);
    }

    /**
     * ПФ-3: `e` is under cover from a pulse now - StormRulesSC.COVER_BLOCKS solid blocks over its head, or inside a field with
     * F_EMP_SCREEN (a player's pays EmpMathSC.SCREEN_EU_PULSE for it - asked outside a pulse, every ask pays; inside one, once a
     * pulse: StormPulseSC; a settlement's core always). Server.
     */
    public static boolean coveredFrom(World w, Entity e) {
        return w != null && e != null && !w.isRemote && new StormPulseSC(w).covered(e);
    }

    /** One pulse in `w` now (the world test, an operator; S's tick calls it too). @return how many players and mobs it suppressed */
    public static int pulse(WorldServer w) {
        return StormPulseSC.pulse(w);
    }

    /**
     * The world test: one pulse on `e` alone (a FakePlayer is in no world's player list, so pulse() never meets it) - the same
     * rules as pulse(): a creative player, a settlement's creature, a pet - nothing; the cover; the mob's screen (a mob needs no
     * player near here). @return it was hit
     */
    public static boolean pulseOn(WorldServer w, Entity e) {
        return StormPulseSC.pulseOn(w, e);
    }

    /** A storm by hand in `w` (the world test): the warning for `warnTicks`, then `stormTicks` of storm. */
    public static void startNow(WorldServer w, int warnTicks, int stormTicks) {
        StormDataSC d = w == null || !SubstrateSC.is(w) ? null : StormDataSC.get(w);
        if (d == null) {
            return;
        }
        long now = w.getWorldTime();
        int warn = Math.max(0, warnTicks);
        d.startAt = now + warn;
        d.endAt = d.startAt + Math.max(1, stormTicks);
        d.nextPulseAt = d.startAt + StormRulesSC.nextPulse(RAND);
        d.pulses = 0;
        d.forced = true;
        d.markDirty();
        Mem m = mem(w);
        m.warnTicks = Math.max(1, Math.min(StormRulesSC.WARN_TICKS, warn));
        schedule(w, d, m, true);
    }

    /** The storm in `w` ends now (the world test, the rule switched off). */
    public static void stopNow(WorldServer w) {
        StormDataSC d = w == null || !SubstrateSC.is(w) ? null : StormDataSC.get(w);
        if (d == null) {
            return;
        }
        d.clearStorm();
        schedule(w, d, mem(w), true);
    }

    /** Server stopped: whatever S keeps in memory (the schedule's last looks, the siren's last time). */
    public static void clearAll() {
        MEM.clear();
    }

    // ------------------------------------------------------------------ the tick

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)) {
            return;
        }
        WorldServer w = (WorldServer) e.world;
        StormDataSC d = StormDataSC.get(w);
        if (d == null) {
            return;
        }
        Mem m = mem(w);
        if (!m.seen || w.getTotalWorldTime() % SCHEDULE_TICKS == 0L) {
            schedule(w, d, m, false);
        }
        if (SubstrateConfigSC.empStorms && m.phase == StormStateSC.STORM) {
            pulses(w, d);
        }
    }

    /** The Substrate unloaded (nobody there for a while): its memory goes - the next load's first look starts afresh, silently. */
    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world != null && !e.world.isRemote && SubstrateSC.is(e.world)) {
            MEM.remove(Integer.valueOf(e.world.provider.dimensionId));
        }
    }

    /** ПФ-2: the storm's pulses - one when its time came, the next 20..40 s later. */
    private static void pulses(WorldServer w, StormDataSC d) {
        long now = w.getWorldTime();
        if (d.phase(now) != StormStateSC.STORM) {
            return;
        }
        if (d.nextPulseAt < d.startAt || d.nextPulseAt > now + StormRulesSC.PULSE_MAX) {
            d.nextPulseAt = now + StormRulesSC.nextPulse(RAND);   // a time jump (or old data): the spacing afresh
            d.markDirty();
            return;
        }
        if (now < d.nextPulseAt) {
            return;
        }
        d.nextPulseAt = now + StormRulesSC.nextPulse(RAND);
        d.pulses++;
        d.markDirty();
        pulse(w);
    }

    /**
     * The day's / the week's rolls, the phase, the haze and the emission; to the clients what changed (`force`: all of it -
     * a storm by hand). Never a roll twice: rolledDay / hazeRolledDay / emissionRolledWeek.
     */
    private static void schedule(WorldServer w, StormDataSC d, Mem m, boolean force) {
        long now = w.getWorldTime(), total = w.getTotalWorldTime();
        boolean rule = SubstrateConfigSC.empStorms;
        if (rule) {
            roll(w, d, now);
        } else {
            off(d);                                         // ПФ-1: the rule off - whatever runs ends at this look
        }
        int phase = rule ? d.phase(now) : StormStateSC.CALM;
        boolean haze = rule && d.hazeAt(now), emission = rule && d.emissionAt(now);
        boolean jump = m.seen && Math.abs((now - m.lastNow) - (total - m.lastTotal)) > JUMP;
        boolean announce = m.seen;
        if (phase != m.phase || jump || force || !m.seen) {
            int was = m.phase;
            m.phase = phase;
            if (phase == StormStateSC.WARNING && was != StormStateSC.WARNING && !d.forced) {
                m.warnTicks = StormRulesSC.WARN_TICKS;
            }
            HazardNetSC.sendToWorld(w, HazardNetSC.STORM_STATE, stormState(d, m, now, rule));
            if (announce && was != phase) {
                if (phase == StormStateSC.WARNING) {
                    tellWorld(w, EnumChatFormatting.GOLD, "sc.storm.warning", String.valueOf((Math.max(0L, d.startAt - now) + 19L) / 20L),
                            String.valueOf(StormRulesSC.COVER_BLOCKS));
                    m.sirenAt = Long.MIN_VALUE / 2;             // the siren at once
                } else if (phase == StormStateSC.STORM) {
                    tellWorld(w, EnumChatFormatting.LIGHT_PURPLE, "sc.storm.start", String.valueOf(StormRulesSC.PULSE_MIN / 20),
                            String.valueOf(StormRulesSC.PULSE_MAX / 20));
                } else if (was == StormStateSC.STORM) {
                    tellWorld(w, EnumChatFormatting.DARK_AQUA, "sc.storm.end");
                }
            }
        }
        if (haze != m.haze || jump || force || !m.seen) {
            boolean was = m.haze;
            m.haze = haze;
            HazardNetSC.sendToWorld(w, HazardNetSC.HAZE_STATE, onState(haze, d.hazeEndAt - now));
            if (announce && was != haze) {
                if (haze) {
                    tellWorld(w, EnumChatFormatting.GRAY, "sc.haze.start", String.valueOf(StormRulesSC.HAZE_SIGHT));
                } else {
                    tellWorld(w, EnumChatFormatting.GRAY, "sc.haze.end");
                }
            }
        }
        if (emission != m.emission || jump || force || !m.seen) {
            boolean was = m.emission;
            m.emission = emission;
            HazardNetSC.sendToWorld(w, HazardNetSC.EMISSION_STATE, onState(emission, d.emissionEndAt - now));
            if (announce && was != emission) {
                if (emission) {
                    tellWorld(w, EnumChatFormatting.RED, "sc.emission.start", String.valueOf((int) StormRulesSC.EMISSION_RADIATION_MUL),
                            String.valueOf((Math.max(0L, d.emissionEndAt - now) + 1199L) / 1200L));
                } else {
                    tellWorld(w, EnumChatFormatting.DARK_AQUA, "sc.emission.end");
                }
            }
        }
        if (phase == StormStateSC.WARNING && total - m.sirenAt >= SIREN_TICKS) {
            m.sirenAt = total;
            sirens(w);
        }
        m.seen = true;
        m.lastNow = now;
        m.lastTotal = total;
    }

    /** ПФ-1, ПФ-9, ПФ-10: today's storm and haze, this week's emission - each rolled once (by the seed: the same however asked). */
    private static void roll(WorldServer w, StormDataSC d, long now) {
        long seed = w.getSeed(), day = SettlementSC.day(w), week = SettlementSC.week(w);
        boolean dirty = false;
        if (d.rolledDay != day) {
            d.rolledDay = day;
            dirty = true;
            if (!(d.forced && d.endAt > now)) {             // a storm by hand runs to its end
                if (StormRulesSC.rolls(seed, day)) {
                    d.startAt = StormRulesSC.startOf(seed, day);
                    d.endAt = d.startAt + StormRulesSC.durationOf(seed, day);
                    d.nextPulseAt = d.startAt + StormRulesSC.nextPulse(RAND);
                    d.pulses = 0;
                    d.forced = false;
                } else {
                    d.clearStorm();
                }
            }
        }
        if (d.hazeRolledDay != day) {
            d.hazeRolledDay = day;
            dirty = true;
            long[] h = StormRulesSC.hazeRolls(seed, day) ? StormRulesSC.hazeWindow(seed, day) : new long[]{0L, 0L};
            d.hazeStartAt = h[0];
            d.hazeEndAt = h[1];
        }
        if (d.emissionRolledWeek != week) {
            d.emissionRolledWeek = week;
            dirty = true;
            long[] em = StormRulesSC.emissionWindow(seed, week);
            d.emissionStartAt = em[0];
            d.emissionEndAt = em[1];
        }
        if (dirty) {
            d.markDirty();
        }
    }

    /** The rule switched off: no storm, no haze, no emission (the rolled days stay - nothing comes back today). */
    private static void off(StormDataSC d) {
        if (d.hasStorm()) {
            d.clearStorm();
        }
        if (d.hazeEndAt > d.hazeStartAt || d.emissionEndAt > d.emissionStartAt) {
            d.hazeStartAt = 0L;
            d.hazeEndAt = 0L;
            d.emissionStartAt = 0L;
            d.emissionEndAt = 0L;
            d.markDirty();
        }
    }

    // ------------------------------------------------------------------ the siren (ПФ-4)

    /** The siren at every settlement core of `w` whose chunk is loaded (nobody in the world: nobody to hear it). */
    private static void sirens(WorldServer w) {
        SettlementRegistrySC r = w.playerEntities.isEmpty() ? null : SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        int dim = w.provider.dimensionId;
        for (SettlementRegistrySC.Settlement s : r.all()) {
            if (s.dim == dim) {
                siren(w, s);
            }
        }
    }

    /** One wail of the siren at settlement `s`'s core, heard within SIREN_RANGE (the storm's warning; a raid - RaidSC). */
    public static void siren(WorldServer w, SettlementRegistrySC.Settlement s) {
        if (w == null || s == null || !w.blockExists(s.x, s.y, s.z) || !(w.getTileEntity(s.x, s.y, s.z) instanceof TileEntitySettlementCoreSC)) {
            return;
        }
        SoundsSC.play(w, s.x + 0.5D, s.y + 1.5D, s.z + 0.5D, SND_SIREN, SIREN_VOLUME, 1F);
    }

    // ------------------------------------------------------------------ the clients

    /** Logged in in the Substrate: the weather now. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        sync(e.player);
    }

    /** Came into the Substrate (the bridge, a jump, a command): the weather now; went out of it - a calm sky. */
    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        sync(e.player);
    }

    /** Woke up (in the Substrate, or out of it after a death there). */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        sync(e.player);
    }

    /**
     * Server: player `p` gets the weather of its world - the storm's phase, the haze, the emission; outside the Substrate all
     * three off (the client resets itself too on leaving - StormClientSC; this is the belt to those braces).
     */
    public static void sync(EntityPlayer p) {
        if (!HazardNetSC.reachable(p) || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) p;
        World w = p.worldObj;
        StormDataSC d = SubstrateSC.is(w) ? StormDataSC.get(w) : null;
        if (d == null) {
            NBTTagCompound calm = new NBTTagCompound();
            calm.setInteger("p", StormStateSC.CALM);
            calm.setInteger("t", 0);
            calm.setInteger("w", StormRulesSC.WARN_TICKS);
            HazardNetSC.send(mp, HazardNetSC.STORM_STATE, calm);
            HazardNetSC.send(mp, HazardNetSC.HAZE_STATE, onState(false, 0L));
            HazardNetSC.send(mp, HazardNetSC.EMISSION_STATE, onState(false, 0L));
            return;
        }
        long now = w.getWorldTime();
        boolean rule = SubstrateConfigSC.empStorms;
        HazardNetSC.send(mp, HazardNetSC.STORM_STATE, stormState(d, mem(w), now, rule));
        HazardNetSC.send(mp, HazardNetSC.HAZE_STATE, onState(rule && d.hazeAt(now), d.hazeEndAt - now));
        HazardNetSC.send(mp, HazardNetSC.EMISSION_STATE, onState(rule && d.emissionAt(now), d.emissionEndAt - now));
    }

    /** STORM_STATE {p, t, w}: the phase, the ticks until it ends, the warning's length. */
    private static NBTTagCompound stormState(StormDataSC d, Mem m, long now, boolean rule) {
        int phase = rule ? d.phase(now) : StormStateSC.CALM;
        long left = phase == StormStateSC.WARNING ? d.startAt - now : phase == StormStateSC.STORM ? d.endAt - now : 0L;
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("p", phase);
        t.setInteger("t", (int) Math.max(0L, Math.min(Integer.MAX_VALUE, left)));
        t.setInteger("w", m.warnTicks);
        return t;
    }

    /** HAZE_STATE / EMISSION_STATE {on, t}: on or off, the ticks left. */
    private static NBTTagCompound onState(boolean on, long left) {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("on", on);
        t.setInteger("t", on ? (int) Math.max(0L, Math.min(Integer.MAX_VALUE, left)) : 0);
        return t;
    }

    /** A chat line to every real player in `w`. */
    static void tellWorld(World w, EnumChatFormatting colour, String key, Object... args) {
        for (Object o : w.playerEntities) {
            tell((EntityPlayer) o, colour, key, args);
        }
    }

    /** A chat line `key` in `colour` (its arguments inherit it) - to a real, connected player only. */
    static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (!HazardNetSC.reachable(p)) {
            return;
        }
        IChatComponent c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(c);
    }

    private static Mem mem(World w) {
        Integer k = Integer.valueOf(w.provider.dimensionId);
        Mem m = MEM.get(k);
        if (m == null) {
            m = new Mem();
            MEM.put(k, m);
        }
        return m;
    }
}
