package com.sc.compat;

import java.util.List;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.emp.IEmpUselessSC;
import com.sc.manual.Lang;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.EntityColossusSC;
import com.sc.mob.EntityContainmentCoilSC;
import com.sc.mob.EntityScrapperSC;
import com.sc.mob.EntityStriderSC;
import com.sc.mob.EntitySubstrateMobSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.IDisguisedSC;
import com.sc.mob.MobAnalysisSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.WardenRulesSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.util.ArmorSuit;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §7.1, rules ПИ-1, ПИ-3): WAILA's lines for the Substrate's creatures - an
 * armoured mob's (or a guard's) armour tier, its reserve (%), «уязвим к ПЭ-1» (empArmoured while dimension.empMobTargets is
 * on), the shield / the emergency mode / «села» / the personal EMP screen, an elite's star and modifier; «???» for a mimic
 * asleep (IDisguisedSC: the head's name replaced). Everything from the DataWatcher on the client - no NBT round trip. Loaded
 * only with WAILA (WailaSC.callbackRegister calls register).
 * <p>
 * Head: WAILA's own name line («§f<name>») gets a gold star for an elite, becomes «???» for a mimic asleep. Body: «Броня:
 * Квант (4/4), запас 60%», then the state lines; a reactive one (IEmpReactiveSC: drones, swarm, mimic) - what a ПЭ-1 does
 * to it. A mimic asleep shows an empty body (the health line WAILA put before ours is taken away too).
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §7.5-§7.9, package N): the new «noise» are EntitySubstrateMobSC (registered already) - their
 * reactive lines by MobAnalysisSC.reactiveKey ("kill", "burst", "collapse", "stun"); «ПЭ-1 бесполезен» for IEmpUselessSC (the
 * Colossus, ПБ-26) and its facets; the Arc Strider's collapsed arc (its telegraph); the Scrapper's bag (DW_BAG) when it holds
 * something - the things of a dead player may be in it.
 * <p>
 * Ф5 (docs/plan-substrate-tech-f5.md §5.8, package B): the Breakdown Warden (an armoured mob - its armour lines as above) shows its
 * phase and what it means now (the coils' shield: how many lit, the window's seconds, the glowing piece, overheated, the core reset's
 * seconds); a Containment Coil (registered here too) - its state and reserve, what a Point does to it (instead of WAILA's health,
 * which means nothing for a machine).
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §8.6, package T, ПЖ-14): a settlement's Tuner (registered for its body only) - one line, its name
 * and its kind («Тантал-3 · Технолог»); an embassy's Elder is «Посол» (NpcKindSC.langKey(embassy): a Tuner outside the Substrate is an
 * embassy's - the client knows the world by its provider). A caravan's master (a Tuner by class) has its own name and no such line.
 */
public class WailaEntitySC implements IWailaEntityProvider {

    /** The elite's mark after its name (U+2605, drawn from Minecraft's unicode font page). */
    private static final String STAR = " §6★";

    /** WailaSC.callbackRegister: the head and body providers for the Substrate's creature classes. */
    public static void register(IWailaRegistrar registrar) {
        WailaEntitySC p = new WailaEntitySC();
        Class<?>[] kinds = {EntityArmoredMobSC.class, EntityWardenSC.class, EntitySubstrateMobSC.class, EntityContainmentCoilSC.class};
        for (Class<?> c : kinds) {
            registrar.registerHeadProvider(p, c);
            registrar.registerBodyProvider(p, c);
        }
        registrar.registerBodyProvider(p, EntityTunerSC.class);       // Ф6: the Tuner's kind line
    }

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(Entity entity, List<String> tip, IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        if (disguised(entity)) {
            if (tip.isEmpty()) {
                tip.add("§f???");
            } else {
                tip.set(0, "§f???");                    // ПИ-3: no name for a machine that answers nothing
            }
            return tip;
        }
        if (entity instanceof EntityLivingBase && MobAnalysisSC.elite((EntityLivingBase) entity) && !tip.isEmpty()) {
            tip.set(0, tip.get(0) + STAR);              // ПС-25: an elite's star
        }
        return tip;
    }

    @Override
    public List<String> getWailaBody(Entity entity, List<String> tip, IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        if (disguised(entity)) {
            tip.clear();
            return tip;
        }
        if (!(entity instanceof EntityLivingBase)) {
            return tip;
        }
        EntityLivingBase e = (EntityLivingBase) entity;
        if (e instanceof EntityTunerSC) {
            tuner((EntityTunerSC) e, tip);                      // Ф6: a Tuner - who he is (an embassy's Elder: «Посол»)
            return tip;
        }
        if (e instanceof EntityContainmentCoilSC) {
            coil((EntityContainmentCoilSC) e, tip);             // Ф5: a machine - its own lines only
            return tip;
        }
        if (e instanceof IArmoredMobSC) {
            armour(e, tip);
        }
        if (e instanceof EntityBreakdownWardenSC) {
            warden((EntityBreakdownWardenSC) e, tip);
        }
        if (e instanceof IEmpReactiveSC && SubstrateConfigSC.empMobTargets) {
            String k = MobAnalysisSC.reactiveKey(e);
            tip.add("§e" + Lang.tr(k != null ? "sc.waila.mob.reactive." + k : "sc.waila.mob.reactive"));
        }
        if (e instanceof IEmpUselessSC) {
            tip.add("§8" + Lang.tr("sc.waila.mob.useless"));
        }
        noise(e, tip);
        return tip;
    }

    /** Ф6 (ПЖ-14): «Тантал-3 · Посол» - the Tuner's name and kind from the DataWatcher, in the reader's language. */
    private static void tuner(EntityTunerSC t, List<String> tip) {
        if (t instanceof EntityCaravanMasterSC) {
            return;
        }
        boolean embassy = t.worldObj != null && !SubstrateSC.is(t.worldObj);
        String kind = Lang.tr(t.kind().langKey(embassy));
        tip.add("§7" + (t.nameCode() != 0 ? NpcNamesSC.name(t.nameCode()).getUnformattedText() + " · " + kind : kind));
    }

    /** Ф5: the Warden's phase and its state now (the DataWatcher: DW_PHASE's flags, DW_COILS, DW_HIGHLIGHT, DW_TIMER). */
    private static void warden(EntityBreakdownWardenSC b, List<String> tip) {
        int phase = b.phase();
        tip.add("§d" + Lang.tr("sc.waila.warden.phase", Lang.tr("sc.waila.warden.phase." + phase)));
        int secs = b.timer();                                   // DW_TIMER is in seconds already
        if (b.phaseFlag(EntityBreakdownWardenSC.FLAG_SHIELDED)) {
            tip.add("§3" + Lang.tr("sc.waila.warden.shielded", Integer.bitCount(b.coilsLit()), WardenRulesSC.COILS));
        }
        if (b.phaseFlag(EntityBreakdownWardenSC.FLAG_WINDOW)) {
            tip.add("§a" + Lang.tr("sc.waila.warden.window", secs));
        }
        int h = b.highlight();
        if (h == EntityBreakdownWardenSC.SET_ALL) {
            tip.add("§b" + Lang.tr("sc.waila.warden.set"));
        } else if (h >= 0 && h <= 3) {
            tip.add("§f" + Lang.tr("sc.waila.warden.highlight", Lang.tr("sc.waila.warden.piece." + h)));
        }
        if (b.phaseFlag(EntityBreakdownWardenSC.FLAG_OVERHEAT)) {
            tip.add("§c" + Lang.tr("sc.waila.warden.overheat"));
        }
        if (phase == WardenRulesSC.PHASE_OVERLOAD) {
            tip.add((b.phaseFlag(EntityBreakdownWardenSC.FLAG_RESET_ARMED) ? "§c" : "§6") + Lang.tr("sc.waila.warden.reset", secs));
        }
    }

    /** Ф5: a Containment Coil - its state, its reserve, what a ПЭ-1 Point does to it (its Warden past «Удержание» - out for good). */
    private static void coil(EntityContainmentCoilSC c, List<String> tip) {
        tip.clear();                                            // WAILA's hearts: a coil's health means nothing
        Entity boss = c.worldObj == null ? null : c.worldObj.getEntityByID(c.bossId());
        boolean spent = boss instanceof EntityBreakdownWardenSC && ((EntityBreakdownWardenSC) boss).phase() != WardenRulesSC.PHASE_HOLD;
        int s = c.coilState();
        String state = s == EntityContainmentCoilSC.LIT ? "lit" : s == EntityContainmentCoilSC.BROKEN ? "broken" : spent ? "spent" : "dark";
        tip.add((s == EntityContainmentCoilSC.LIT ? "§b" : s == EntityContainmentCoilSC.BROKEN ? "§c" : "§7") + Lang.tr("sc.waila.coil." + state));
        tip.add("§7" + Lang.tr("sc.waila.coil.reserve", Math.round(c.reserveShown()), Math.round(WardenRulesSC.COIL_RESERVE)));
        if (s == EntityContainmentCoilSC.LIT && SubstrateConfigSC.empMobTargets) {
            tip.add("§e" + Lang.tr("sc.waila.coil.point", WardenRulesSC.COIL_OFF_TICKS / 20));
        }
    }

    /** Ф4: what the «noise» shows of itself - the Colossus's facets, a collapsed arc, the Scrapper's bag. */
    private static void noise(EntityLivingBase e, List<String> tip) {
        if (e instanceof EntityColossusSC) {
            tip.add("§7" + Lang.tr("sc.waila.mob.facets"));
        } else if (e instanceof EntityStriderSC && ((EntityStriderSC) e).collapsed()) {
            tip.add("§b" + Lang.tr("sc.waila.mob.collapsed"));
        } else if (e instanceof EntityScrapperSC && ((EntityScrapperSC) e).bagStacks() > 0) {
            tip.add("§6" + Lang.tr("sc.waila.mob.bag", ((EntityScrapperSC) e).bagStacks(), EntityScrapperSC.BAG_SLOTS));
        }
    }

    @Override
    public List<String> getWailaTail(Entity entity, List<String> tip, IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, Entity entity, NBTTagCompound tag, World world) {
        return tag;                                     // nothing from the server: the DataWatcher says it all
    }

    /** ПИ-1: the suit, its pieces and reserve, then what the client knows of its state (MobArmorSC's flags). */
    private static void armour(EntityLivingBase e, List<String> tip) {
        ArmorSuit suit = ((IArmoredMobSC) e).mobArmor().suit();
        if (suit != null) {
            String name = Lang.tr("sc.analyzer.mob.suit." + suit.textureName);
            int pct = Math.round(MobArmorSC.clientCharge(e) * 100F);
            tip.add("§7" + Lang.tr("sc.waila.mob.armor", name, MobAnalysisSC.modPieces(e), pct));
            if (e instanceof IEmpTargetSC && ((IEmpTargetSC) e).empArmoured() && SubstrateConfigSC.empMobTargets) {
                tip.add("§b" + Lang.tr("sc.waila.mob.emp"));
            }
            int sup = 0;
            for (int i = 0; i < 4; i++) {
                if (MobArmorSC.clientSuppressed(e, i)) {
                    sup++;
                }
            }
            if (sup > 0) {
                tip.add("§b" + Lang.tr("sc.waila.mob.suppressed", sup));
            }
            if (MobArmorSC.clientFlag(e, MobArmorSC.FLAG_DOWN)) {
                tip.add("§c" + Lang.tr("sc.waila.mob.down"));
            }
            if (MobArmorSC.clientFlag(e, MobArmorSC.FLAG_EMERGENCY)) {
                tip.add("§6" + Lang.tr("sc.waila.mob.emergency"));
            }
            if (MobArmorSC.clientFlag(e, MobArmorSC.FLAG_SHIELD)) {
                tip.add("§3" + Lang.tr("sc.waila.mob.shield"));
            }
            if (MobArmorSC.clientFlag(e, MobArmorSC.FLAG_SCREEN)) {
                tip.add("§9" + Lang.tr("sc.waila.mob.screen"));
            }
        }
        EliteModSC m = e instanceof EntityArmoredMobSC ? ((EntityArmoredMobSC) e).eliteMod() : null;
        if (m != null) {
            tip.add("§6" + Lang.tr("sc.waila.mob.elite", Lang.tr(m.langKey())));
        }
    }

    private static boolean disguised(Entity e) {
        return e instanceof IDisguisedSC && ((IDisguisedSC) e).disguised();
    }
}
