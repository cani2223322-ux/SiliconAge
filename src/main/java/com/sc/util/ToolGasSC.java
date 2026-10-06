package com.sc.util;

import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Gases for the Singular blade's and drill's own functions (docs/plan-singular-tools.md, variant A): the
 * tools have no tanks - everything comes from the tanks of the worn armour. Creative mode: free. Without
 * the gas a Singular function doesn't work (noGasMessage); the Exo-era ones never need it.
 */
public final class ToolGasSC {

    /** Tool NBT: the part of a mB a per-tick cost has spent but not yet taken. */
    public static final String FRAC_PREFIX = "SingGasFrac_";
    /** Player data: when the no-gas message was last shown (per gas). */
    private static final String WARN_PREFIX = "scToolNoGas_";
    /** Ticks between two no-gas messages for one gas. */
    public static final int WARN_TICKS = 40;

    private ToolGasSC() {
    }

    private static boolean creative(EntityPlayer p) {
        return p != null && p.capabilities.isCreativeMode;
    }

    /** The worn armour holds `mb` of `g` (creative: always). */
    public static boolean has(EntityPlayer p, Gas g, int mb) {
        return creative(p) || p != null && g != null && ArmorGasSC.suitAmount(p, g) >= mb;
    }

    /** Takes all of `mb` from the worn armour or nothing (creative: free, true). */
    public static boolean drainExact(EntityPlayer p, Gas g, int mb) {
        if (creative(p) || mb <= 0) {
            return true;
        }
        return p != null && g != null && ArmorGasSC.drainExact(ArmorGasSC.wornSet(p), g, mb);
    }

    /** Pure: the carried fraction after spending `mb` on top of `frac`: {whole mB to take now, new fraction}. */
    public static float[] split(float frac, float mb) {
        float sum = Math.max(0F, frac) + Math.max(0F, mb);
        int whole = (int) sum;
        return new float[]{whole, sum - whole};
    }

    /**
     * Per-tick costs: `mb` (may be under 1) of `g` from the worn armour, the part under a whole mB kept in
     * the tool's NBT until it adds up. Creative: nothing is taken. @return whole mB taken now (check has() first)
     */
    public static int drainFraction(EntityPlayer p, ItemStack tool, Gas g, float mb) {
        if (creative(p) || p == null || tool == null || g == null || mb <= 0) {
            return 0;
        }
        if (!tool.hasTagCompound()) {
            tool.setTagCompound(new NBTTagCompound());
        }
        String key = FRAC_PREFIX + g.key();
        float[] s = split(tool.getTagCompound().getFloat(key), mb);
        int whole = (int) s[0];
        int took = whole > 0 ? ArmorGasSC.drainOf(ArmorGasSC.wornSet(p), g, whole, false) : 0;   // counted as the suit's spending (ОЧ1)
        if (took < whole || ArmorGasSC.suitAmount(p, g) <= 0) {
            tool.getTagCompound().removeTag(key);          // ran dry: nothing owed
        } else {
            tool.getTagCompound().setFloat(key, s[1]);
        }
        return took;
    }

    /** Server: «<gas>: нет в баках надетой брони», at most once per WARN_TICKS per gas, with a soft sound. */
    public static void noGasMessage(EntityPlayer p, Gas g) {
        warn(p, g, "sc.tool.sing.nogas", new net.minecraft.util.ChatComponentTranslation("sc.gas." + g.key()));
    }

    /** As noGasMessage, with the amount needed: «<gas>: нужно N мБ в баках надетой брони». */
    public static void noGasMessage(EntityPlayer p, Gas g, int mb) {
        warn(p, g, "sc.tool.sing.nogas.mb", new net.minecraft.util.ChatComponentTranslation("sc.gas." + g.key()), String.valueOf(mb));
    }

    private static void warn(EntityPlayer p, Gas g, String key, Object... args) {
        if (p == null || g == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        NBTTagCompound data = p.getEntityData();
        String k = WARN_PREFIX + g.key();
        long now = p.worldObj.getTotalWorldTime();
        long at = data.getLong(k);
        if (data.hasKey(k) && now - at < WARN_TICKS && now >= at) {
            return;
        }
        data.setLong(k, now);
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(key, args));
        p.worldObj.playSoundAtEntity(p, "note.bass", 0.5F, 0.6F);
    }
}
