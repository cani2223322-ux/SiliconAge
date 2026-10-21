package com.sc.hazard;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sc.dim.SubstrateSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpSC;
import com.sc.emp.IEmpTargetSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.MobArmorSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §5.3, rules ПФ-2, ПФ-3): one pulse of the EMP storm - EmpStormSC's tick and its
 * pulse() run it. One pass over the world's players, one over its loaded entities (§13.12):
 * <ul>
 * <li>a player (alive, not creative) wearing some of the mod's armour, not under cover: one random worn piece of the mod's -
 * EmpSC.stormHit (suppressed StormRulesSC.suppressTicks, the helium boiling BOIL_MUL times for BOIL_TICKS; an immune piece -
 * nothing). Without the mod's armour nothing at all: the storm hits electronics, not legs (no Slowness);</li>
 * <li>an armoured mob (IEmpTargetSC with a reserve, IArmoredMobSC) within StormRulesSC.REACH of some player, not a settlement's
 * creature (ISettlementMemberSC) nor somebody's pet: under cover - nothing; its personal EMP screen up - the screen takes the
 * pulse and is down (as after a ПЭ-1 shot); else one random worn piece - MobArmorSC.stormHit (a suppressed Exo / Singular
 * chestplate drops its shield, MobArmorSC's). A Hollow Shell reboots SHELL_REBOOT_TICKS (ПБ-21) unless covered or screened;</li>
 * <li>the cover (ПФ-3, covered): COVER_BLOCKS movement-blocking blocks over the head - looked for from the head up to the
 * column's top (getPrecipitationHeight: the highest movement-blocking or liquid block), COVER_SCAN at most - or an up field
 * with F_EMP_SCREEN round it that covers it (shieldsFromStorm: a player's field - its owner's side and the mobs it lets stay)
 * and pays EmpMathSC.SCREEN_EU_PULSE: each field once a pulse, however many it covers (a player's from its buffer - none
 * left, no cover; a settlement's core always, its pay() is free; a camp's Stolen Field by its own).</li>
 * </ul>
 * ConfigSC.empEnabled off: a pulse suppresses nothing (EmpSC.stormHit's own rule, kept for the mobs too). Server only.
 */
final class StormPulseSC {

    private static final Random RAND = new Random();

    private final World w;
    /** The up fields with an EMP screen in the world, looked up once a pulse; what each paid this pulse. */
    private final List<TileEntityFieldGeneratorSC> screens = new ArrayList<TileEntityFieldGeneratorSC>();
    private final Map<TileEntityFieldGeneratorSC, Boolean> paid = new IdentityHashMap<TileEntityFieldGeneratorSC, Boolean>();

    StormPulseSC(World w) {
        this.w = w;
        for (TileEntityFieldGeneratorSC f : TileEntityFieldGeneratorSC.activeFieldsIn(w)) {
            if (f.has(TileEntityFieldGeneratorSC.F_EMP_SCREEN)) {
                screens.add(f);
            }
        }
    }

    /** One pulse in `w` (EmpStormSC.pulse). @return the players and mobs it suppressed (or rebooted) */
    static int pulse(WorldServer w) {
        if (w == null || !SubstrateSC.is(w)) {
            return 0;
        }
        StormPulseSC sp = new StormPulseSC(w);
        List<EntityPlayer> watchers = new ArrayList<EntityPlayer>();
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayer && ((EntityPlayer) o).isEntityAlive()) {
                watchers.add((EntityPlayer) o);
            }
        }
        int hit = 0;
        for (EntityPlayer p : watchers) {
            if (sp.player(p)) {
                hit++;
            }
        }
        if (!watchers.isEmpty()) {
            List<?> all = w.loadedEntityList;
            for (int i = 0; i < all.size(); i++) {
                Object o = all.get(i);
                if (o instanceof EntityLivingBase && !(o instanceof EntityPlayer) && near((Entity) o, watchers)
                        && sp.mob((EntityLivingBase) o)) {
                    hit++;
                }
            }
        }
        HazardNetSC.sendToWorld(w, HazardNetSC.STORM_PULSE, new NBTTagCompound());
        return hit;
    }

    /** EmpStormSC.pulseOn: one entity alone (the world test). */
    static boolean pulseOn(WorldServer w, Entity e) {
        if (w == null || e == null || e.worldObj != w || !SubstrateSC.is(w)) {
            return false;
        }
        StormPulseSC sp = new StormPulseSC(w);
        if (e instanceof EntityPlayer) {
            return sp.player((EntityPlayer) e);
        }
        return e instanceof EntityLivingBase && sp.mob((EntityLivingBase) e);
    }

    /** ПФ-2: a player - one random worn piece of the mod's, if not under cover. */
    boolean player(EntityPlayer p) {
        if (!p.isEntityAlive() || p.capabilities.isCreativeMode || !ConfigSC.empEnabled) {
            return false;
        }
        List<Integer> worn = new ArrayList<Integer>(4);
        for (int i = 0; i < 4; i++) {
            if (EmpSC.modPiece(p, i) != null) {
                worn.add(Integer.valueOf(i));
            }
        }
        if (worn.isEmpty() || covered(p)) {
            return false;                                   // no armour of the mod's - nothing (no Slowness)
        }
        int slot = worn.get(RAND.nextInt(worn.size())).intValue();
        if (EmpSC.stormHit(p, slot, StormRulesSC.suppressTicks(RAND), StormRulesSC.BOIL_MUL, StormRulesSC.BOIL_TICKS) <= 0) {
            return false;                                   // that piece is immune or still suppressed (ОР-1)
        }
        hitFx(p, 1F);
        return true;
    }

    /** ПФ-2: an armoured mob (or a Hollow Shell) - not a settlement's creature, not a pet, not under cover. */
    boolean mob(EntityLivingBase e) {
        if (!e.isEntityAlive() || e instanceof ISettlementMemberSC || owned(e) || !ConfigSC.empEnabled) {
            return false;
        }
        boolean shell = e instanceof EntityHollowShellSC;
        boolean armoured = e instanceof IEmpTargetSC && e instanceof IArmoredMobSC && ((IEmpTargetSC) e).empArmoured();
        List<Integer> worn = new ArrayList<Integer>(4);
        if (armoured) {
            for (int i = 0; i < 4; i++) {
                if (MobArmorSC.pieceSuit(e, i) != null) {
                    worn.add(Integer.valueOf(i));
                }
            }
        }
        if (!shell && worn.isEmpty() || covered(e)) {
            return false;
        }
        boolean took = false;
        if (armoured) {
            MobArmorSC a = ((IArmoredMobSC) e).mobArmor();
            if (a.screenTakes()) {                          // ПБ-19: the personal screen takes it, nothing else happens
                screenFx(e);
                return false;
            }
            if (!worn.isEmpty()) {
                took = a.stormHit(worn.get(RAND.nextInt(worn.size())).intValue(), StormRulesSC.suppressTicks(RAND));
            }
        }
        if (shell) {
            ((EntityHollowShellSC) e).reboot(StormRulesSC.SHELL_REBOOT_TICKS);
            took = true;
        }
        if (took) {
            hitFx(e, 0.6F);
        }
        return took;
    }

    /** ПФ-3: `e` is under cover - solid blocks over its head, or a field's EMP screen that paid for this pulse. */
    boolean covered(Entity e) {
        if (StormRulesSC.covered(solidAbove(w, e))) {
            return true;
        }
        double x = e.posX, y = e.posY + e.height / 2D, z = e.posZ;
        for (TileEntityFieldGeneratorSC f : screens) {
            if (f.isInvalid() || !f.fieldContains(x, y, z) || !f.shieldsFromStorm(e)) {
                continue;                                   // not its side: no cover, nothing paid for it
            }
            Boolean ok = paid.get(f);
            if (ok == null) {
                ok = Boolean.valueOf(f.pay(EmpMathSC.SCREEN_EU_PULSE));
                if (ok.booleanValue()) {
                    f.markDirty();
                }
                paid.put(f, ok);
            }
            if (ok.booleanValue()) {
                if (e instanceof EntityPlayer) {
                    f.flash(x, e.posY + e.height + 0.4D, z);    // the shell lights up over the player's head
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Movement-blocking blocks over `e`'s head in its column, counted up to StormRulesSC.COVER_BLOCKS: from the block its head
     * is in up to the column's top (getPrecipitationHeight - nothing above it blocks movement), COVER_SCAN blocks at most.
     */
    static int solidAbove(World w, Entity e) {
        int x = MathHelper.floor_double(e.posX), z = MathHelper.floor_double(e.posZ);
        int from = MathHelper.floor_double(e.boundingBox.maxY - 0.01D);
        int top = Math.min(Math.min(w.getPrecipitationHeight(x, z), from + StormRulesSC.COVER_SCAN), 256);
        int n = 0;
        for (int y = Math.max(0, from); y < top && n < StormRulesSC.COVER_BLOCKS; y++) {
            if (w.getBlock(x, y, z).getMaterial().blocksMovement()) {
                n++;
            }
        }
        return n;
    }

    /** Within REACH of some player (a mob nobody is near feels nothing: nothing to see there). */
    static boolean near(Entity e, List<EntityPlayer> watchers) {
        double r2 = (double) StormRulesSC.REACH * StormRulesSC.REACH;
        for (EntityPlayer p : watchers) {
            if (e.getDistanceSqToEntity(p) <= r2) {
                return true;
            }
        }
        return false;
    }

    /** A tamed thing: an owner's UUID (or the owner entity) is set (as EmpMobSC's ПБ-10 filter). */
    static boolean owned(Entity e) {
        if (!(e instanceof IEntityOwnable)) {
            return false;
        }
        IEntityOwnable o = (IEntityOwnable) e;
        String id = o.func_152113_b();
        return id != null && id.length() > 0 || o.getOwner() != null;
    }

    /** The pulse's sound at the one it hit (EmpSC.SND_PULSE - everyone near hears it) and a few sparks. */
    private void hitFx(Entity e, float volume) {
        SoundsSC.play(w, e.posX, e.posY + e.height / 2D, e.posZ, EmpSC.SND_PULSE, volume, 0.8F + RAND.nextFloat() * 0.3F);
        if (w instanceof WorldServer) {
            ((WorldServer) w).func_147487_a("fireworksSpark", e.posX, e.posY + e.height * 0.6D, e.posZ, 6, 0.25D, 0.4D, 0.25D, 0.04D);
        }
    }

    /** A mob's screen taking the pulse: a crackle on its bubble. */
    private void screenFx(Entity e) {
        if (w instanceof WorldServer) {
            ((WorldServer) w).func_147487_a("magicCrit", e.posX, e.posY + e.height * 0.5D, e.posZ, 10, 0.5D, 0.6D, 0.5D, 0.1D);
        }
    }
}
