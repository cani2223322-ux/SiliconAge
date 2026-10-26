package com.sc.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.dim.SubstrateConfigSC;
import com.sc.emp.EmpSC;
import com.sc.finale.FinaleNetSC;
import com.sc.hazard.StormRulesSC;
import com.sc.init.IdeaItemsSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.EntityEclipseSC;
import com.sc.mob.WardenFightSC;
import com.sc.mob.WardenRulesSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ПД-6 (docs/plan-ideas-2026-10-26.md, «Протоколы Пробоя», ★ A): the protocols in a fight - inserting them into the Console (ArenaWorldSC
 * .start asks insert before the Key's countdown), the arena record's mask (ArenaDataSC.Arena.extra BreachProtocolsSC.EXTRA), the running
 * effects (JAMMING's pulses, ECLIPSE_SHADOW's copy of «Затмение» - from WardenFightSC's tick), spending them with the Key (ArenaWorldSC
 * .bossDied), the reset keeping them (ArenaWorldSC.reset). The loot's bonus - WardenLootSC asks BreachProtocolsSC.crumbs / trophyMul /
 * scheme with mask(a).
 * <p>
 * insert: a protocol in hand - into the mask while the arena is READY / KEYED and no countdown runs (the rule dimension.breachProtocols
 * on, not in yet, the new mask allowed - else the refusal names the pair or the limit), the card taken (not in creative); Sneak + right
 * click with an empty hand (no fight) - every protocol back into the inventory. The shadow: SHADOW_SHOT (the arena record's extra P {s:
 * the fight id it came for}) - once a fight. JAMMING: every JAMMING_EVERY of the fight's clock - a storm's pulse on every player within
 * WardenRulesSC.SCALE_RADIUS of the pit's middle (a piece of the mod's armour suppressed, its immunity kept; no cover counts in the pit).
 * The protocols' short names are lang sc.protocol.<key>.
 * <p>
 * Fixed (the coordinator's): the class, the signatures. The bodies - ПД-6's.
 */
public final class BreachFightSC {

    /** The record's extra P field: the fight id the shadow came for (once a fight). */
    public static final String SHADOW_SHOT = "s";
    /** BOSS_FX of a jamming pulse (BossClientSC.FX_JAMMING - the server never names the client's class). */
    public static final int FX_JAMMING = 6;

    private static final Random RAND = new Random();

    private BreachFightSC() {
    }

    /** The protocols' mask of arena `a` (0 none, a client, no record). */
    public static int mask(ArenaDataSC.Arena a) {
        return a == null ? 0 : a.extra.getCompoundTag(BreachProtocolsSC.EXTRA).getInteger(BreachProtocolsSC.MASK);
    }

    /** Server: arena `a`'s mask is now `m` (0 - the extra goes). */
    static void setMask(ArenaDataSC.Arena a, int m) {
        NBTTagCompound x = a.extra.getCompoundTag(BreachProtocolsSC.EXTRA);
        if (m == 0) {
            a.extra.removeTag(BreachProtocolsSC.EXTRA);
            return;
        }
        x.setInteger(BreachProtocolsSC.MASK, m);
        a.extra.setTag(BreachProtocolsSC.EXTRA, x);
    }

    /** ПД-6: «Перегрузка»'s time to finish in arena `a` - SHORT_FUSE's SHORT_FUSE_TICKS, else WardenRulesSC.FINISH_TICKS. */
    public static int finishTicks(ArenaDataSC.Arena a) {
        return BreachProtocolsSC.has(mask(a), BreachProtocolsSC.SHORT_FUSE) ? BreachProtocolsSC.SHORT_FUSE_TICKS : WardenRulesSC.FINISH_TICKS;
    }

    /** ПД-6: the Shell waves' multiplier in arena `a` (SWARM's SWARM_MUL, else 1). */
    public static float swarmMul(ArenaDataSC.Arena a) {
        return BreachProtocolsSC.has(mask(a), BreachProtocolsSC.SWARM) ? BreachProtocolsSC.SWARM_MUL : 1F;
    }

    /** ПД-6: arena `a`'s coils don't hear a ПЭ-1 Point (DEAF_COILS). */
    public static boolean deafCoils(ArenaDataSC.Arena a) {
        return BreachProtocolsSC.has(mask(a), BreachProtocolsSC.DEAF_COILS);
    }

    /**
     * Server (ArenaWorldSC.start, before the Key): `p` holds a Breach Protocol at arena `a`'s Console - it goes in (the mask grows) if the
     * fight hasn't begun, the rule is on, BreachProtocolsSC.allowed holds for the new mask. @return the click was a protocol's (used)
     */
    public static boolean insert(WorldServer w, ArenaDataSC.Arena a, EntityPlayer p) {
        if (w == null || a == null || p == null) {
            return false;
        }
        ItemStack held = p.getCurrentEquippedItem();
        boolean protocol = held != null && IdeaItemsSC.breachProtocol != null && held.getItem() == IdeaItemsSC.breachProtocol;
        if (!protocol) {
            if (held == null && p.isSneaking() && mask(a) != 0 && a.state != ArenaDataSC.FIGHT && !ArenaWorldSC.counting(a)) {
                takeBack(w, a, p);
                return true;
            }
            return false;
        }
        if (!SubstrateConfigSC.breachProtocols) {
            tell(p, EnumChatFormatting.GRAY, "sc.protocol.refuse.off");
            return true;
        }
        if (a.state == ArenaDataSC.FIGHT || ArenaWorldSC.counting(a) || a.state == ArenaDataSC.EMPTY) {
            tell(p, EnumChatFormatting.YELLOW, "sc.protocol.refuse.busy");
            return true;
        }
        int pr = held.getItemDamage(), m = mask(a), bit = BreachProtocolsSC.bit(pr);
        if (bit == 0) {
            return true;
        }
        if ((m & bit) != 0) {
            tell(p, EnumChatFormatting.GRAY, "sc.protocol.refuse.same");
            return true;
        }
        int next = m | bit;
        if (BreachProtocolsSC.count(next) > BreachProtocolsSC.MAX) {
            tell(p, EnumChatFormatting.RED, "sc.protocol.refuse.max", String.valueOf(BreachProtocolsSC.MAX));
            return true;
        }
        if (!BreachProtocolsSC.allowed(next)) {
            int[] pair = BreachProtocolsSC.forbiddenPair(next);
            int other = pair == null ? firstOf(m) : pair[0] == pr ? pair[1] : pair[0];
            tell(p, EnumChatFormatting.RED, "sc.protocol.refuse.pair", name(pr), name(other));
            return true;
        }
        if (!p.capabilities.isCreativeMode) {
            held.stackSize--;
            if (held.stackSize <= 0) {
                p.inventory.setInventorySlotContents(p.inventory.currentItem, null);
            }
            if (!(p instanceof FakePlayer)) {
                p.inventoryContainer.detectAndSendChanges();
            }
        }
        setMask(a, next);
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d != null) {
            d.markDirty();
        }
        tell(p, EnumChatFormatting.LIGHT_PURPLE, "sc.protocol.inserted", name(pr), String.valueOf(BreachProtocolsSC.count(next)),
                String.valueOf(BreachProtocolsSC.MAX));
        int x = a.x + ArenaLayoutSC.CONSOLE_DX, y = a.floorY + 1, z = a.z + ArenaLayoutSC.CONSOLE_DZ;
        w.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.click", 0.6F, 0.6F);
        console(w, a);
        return true;
    }

    /** Every protocol of `a` back into `p`'s inventory (what doesn't fit at his feet), the mask empty. */
    private static void takeBack(WorldServer w, ArenaDataSC.Arena a, EntityPlayer p) {
        int m = mask(a);
        IChatComponent names = new ChatComponentText("");
        boolean first = true;
        for (int pr = 0; pr < BreachProtocolsSC.COUNT; pr++) {
            if (!BreachProtocolsSC.has(m, pr)) {
                continue;
            }
            ItemStack s = IdeaItemsSC.breachProtocol(pr);
            if (s != null && !p.inventory.addItemStackToInventory(s)) {
                p.dropPlayerItemWithRandomChoice(s, false);
            }
            if (!first) {
                names.appendText(", ");
            }
            names.appendSibling(name(pr));
            first = false;
        }
        setMask(a, 0);
        ArenaDataSC d = ArenaDataSC.get(w);
        if (d != null) {
            d.markDirty();
        }
        if (!(p instanceof FakePlayer)) {
            p.inventoryContainer.detectAndSendChanges();
        }
        tell(p, EnumChatFormatting.GRAY, "sc.protocol.removed", names);
        console(w, a);
    }

    /** The Console's protocol lights to the clients (its description packet carries the mask). */
    private static void console(WorldServer w, ArenaDataSC.Arena a) {
        int x = a.x + ArenaLayoutSC.CONSOLE_DX, y = a.floorY + 1, z = a.z + ArenaLayoutSC.CONSOLE_DZ;
        if (w.blockExists(x, y, z)) {
            w.markBlockForUpdate(x, y, z);
        }
    }

    /** ArenaWorldSC.bossDied: the protocols are spent with the Key. */
    public static void spent(ArenaDataSC.Arena a) {
        if (a != null) {
            setMask(a, 0);
        }
    }

    /** Server (WardenFightSC's tick, the fight of arena `a` running): the protocols' own effects at world time `now`. */
    public static void tick(WorldServer w, ArenaDataSC.Arena a, long now) {
        int m = mask(a);
        if (m == 0 || w == null || a == null || a.state != ArenaDataSC.FIGHT || !SubstrateConfigSC.breachProtocols) {
            return;
        }
        long t = now - a.fightStartedAt;
        if (BreachProtocolsSC.has(m, BreachProtocolsSC.JAMMING) && t > 0 && t % BreachProtocolsSC.JAMMING_EVERY == 0) {
            jam(w, a);
        }
        if (BreachProtocolsSC.has(m, BreachProtocolsSC.ECLIPSE_SHADOW)) {
            EntityBreakdownWardenSC boss = ArenaWorldSC.warden(w, a);
            NBTTagCompound x = a.extra.getCompoundTag(BreachProtocolsSC.EXTRA);
            if (boss != null && x.getLong(SHADOW_SHOT) != a.fightId && boss.getHealth() <= boss.getMaxHealth() * BreachProtocolsSC.SHADOW_AT) {
                x.setLong(SHADOW_SHOT, a.fightId);
                a.extra.setTag(BreachProtocolsSC.EXTRA, x);
                ArenaDataSC d = ArenaDataSC.get(w);
                if (d != null) {
                    d.markDirty();
                }
                shadow(w, a);
            }
        }
    }

    /** JAMMING: a storm's pulse on every player within SCALE_RADIUS of the middle (no cover in the pit); the ring for the clients. */
    static int jam(WorldServer w, ArenaDataSC.Arena a) {
        double cx = a.x + 0.5D, cz = a.z + 0.5D, r = WardenRulesSC.SCALE_RADIUS;
        int hit = 0;
        for (Object o : new ArrayList<Object>(w.playerEntities)) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.isCreativeMode || (p.posX - cx) * (p.posX - cx) + (p.posZ - cz) * (p.posZ - cz) > r * r) {
                continue;
            }
            List<Integer> worn = new ArrayList<Integer>(4);
            for (int i = 0; i < 4; i++) {
                if (EmpSC.modPiece(p, i) != null) {
                    worn.add(Integer.valueOf(i));
                }
            }
            if (!worn.isEmpty() && EmpSC.stormHit(p, worn.get(RAND.nextInt(worn.size())).intValue(), StormRulesSC.suppressTicks(RAND),
                    StormRulesSC.BOIL_MUL, StormRulesSC.BOIL_TICKS) > 0) {
                hit++;
            }
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("k", FX_JAMMING);
        t.setDouble("x", cx);
        t.setDouble("y", a.floorY + 1D);
        t.setDouble("z", cz);
        FinaleNetSC.sendAround(w, cx, a.floorY + 1D, cz, WardenFightSC.FX_RANGE, FinaleNetSC.BOSS_FX, t);
        return hit;
    }

    /** ECLIPSE_SHADOW: a copy of «Затмение» at the pit's rim (no record of its own, no loot - EntityEclipseSC.makeShadow). */
    private static void shadow(WorldServer w, ArenaDataSC.Arena a) {
        double ang = RAND.nextDouble() * Math.PI * 2D, rr = ArenaLayoutSC.PIT_R - 3;
        double x = a.x + 0.5D + Math.cos(ang) * rr, z = a.z + 0.5D + Math.sin(ang) * rr;
        EntityEclipseSC e = new EntityEclipseSC(w);
        e.setLocationAndAngles(x, a.floorY + 1, z, RAND.nextFloat() * 360F, 0F);
        e.onSpawnWithEgg(null);
        e.makeShadow(a.ri, a.rj, a.fightId);
        com.sc.mob.BornInSubstrateSC.mark(e);
        if (w.spawnEntityInWorld(e)) {
            for (Object o : w.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (p.getDistanceSq(a.x, a.floorY, a.z) <= (double) ArenaWorldSC.TELL_RADIUS * ArenaWorldSC.TELL_RADIUS) {
                    tell(p, EnumChatFormatting.DARK_PURPLE, "sc.tooltip.breachProtocol.eclipseShadow");
                }
            }
        }
    }

    /** Server stopped: the effects' timers (memory only). */
    public static void clearAll() {
    }

    // ------------------------------------------------------------------ helpers

    private static int firstOf(int mask) {
        for (int p = 0; p < BreachProtocolsSC.COUNT; p++) {
            if (BreachProtocolsSC.has(mask, p)) {
                return p;
            }
        }
        return 0;
    }

    /** The protocol's short name in the reader's language (sc.protocol.<key>: «Глухие катушки»). */
    static IChatComponent name(int p) {
        return new ChatComponentTranslation("sc.protocol." + BreachProtocolsSC.keyOf(p));
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (p == null || p instanceof FakePlayer) {
            return;
        }
        p.addChatComponentMessage(new ChatComponentTranslation(key, args).setChatStyle(new ChatStyle().setColor(colour)));
    }
}
