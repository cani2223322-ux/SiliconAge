package com.sc.compat;

import java.util.List;

import com.sc.dim.SubstrateConfigSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.manual.Lang;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntitySubstrateMobSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.IDisguisedSC;
import com.sc.mob.MobAnalysisSC;
import com.sc.mob.MobArmorSC;
import com.sc.settle.npc.EntityWardenSC;
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
 */
public class WailaEntitySC implements IWailaEntityProvider {

    /** The elite's mark after its name (U+2605, drawn from Minecraft's unicode font page). */
    private static final String STAR = " §6★";

    /** WailaSC.callbackRegister: the head and body providers for the Substrate's creature classes. */
    public static void register(IWailaRegistrar registrar) {
        WailaEntitySC p = new WailaEntitySC();
        Class<?>[] kinds = {EntityArmoredMobSC.class, EntityWardenSC.class, EntitySubstrateMobSC.class};
        for (Class<?> c : kinds) {
            registrar.registerHeadProvider(p, c);
            registrar.registerBodyProvider(p, c);
        }
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
        if (e instanceof IArmoredMobSC) {
            armour(e, tip);
        }
        if (e instanceof IEmpReactiveSC && SubstrateConfigSC.empMobTargets) {
            String k = MobAnalysisSC.reactiveKey(e);
            tip.add("§e" + Lang.tr(k != null ? "sc.waila.mob.reactive." + k : "sc.waila.mob.reactive"));
        }
        return tip;
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
