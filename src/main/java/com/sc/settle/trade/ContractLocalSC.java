package com.sc.settle.trade;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * ПД-1 (docs/plan-ideas-2026-10-26.md, «Живая доска контрактов», ★ A): what lies round a settlement - its local contracts go by it (the
 * Quartzite Ridges: the Colossus, the Leeches; the Oxide Fields: the Overheated; a Dead Node - every settlement has one within a cell: the
 * sentry drones, the Hollow Shells; the Rift: nothing of its own). A mask of TAG_*: the kinds of the settlement's own cell and its eight
 * neighbours (SubstrateCellsSC.cellKind - decided once, kept), worked out once and kept in its record (Settlement.extra EXTRA {t: tags}).
 * <p>
 * Fixed (the coordinator's): the class, the tags, the key, the signatures. The bodies are ПД-1's.
 */
public final class ContractLocalSC {

    public static final int TAG_RIDGES = 1, TAG_OXIDE = 2, TAG_NODE = 4, TAG_RIFT = 8;
    /** Settlement.extra key and its field. */
    public static final String EXTRA = "L", TAGS = "t";

    private ContractLocalSC() {
    }

    /** Pure: the tags of the nine cells' kinds round a settlement (BiomeSubstrateSC kinds; TAG_NODE always - a Node at every crossing). */
    public static int tagsOf(int[] cellKinds) {
        int t = TAG_NODE;
        if (cellKinds != null) {
            for (int k : cellKinds) {
                if (k == BiomeSubstrateSC.RIDGES) {
                    t |= TAG_RIDGES;
                } else if (k == BiomeSubstrateSC.OXIDE) {
                    t |= TAG_OXIDE;
                } else if (k == BiomeSubstrateSC.RIFT) {
                    t |= TAG_RIFT;
                }
            }
        }
        return t;
    }

    /** Server: settlement `s`'s tags (kept in its record once worked out); TAG_NODE alone for a client, an embassy, no world. */
    public static int tags(World w, SettlementRegistrySC.Settlement s) {
        if (w == null || w.isRemote || s == null || SettlementSC.isEmbassy(s) || SettlementSC.isRelay(s) || !SubstrateSC.isDim(s.dim)
                || w.provider.dimensionId != s.dim) {
            return TAG_NODE;
        }
        NBTTagCompound l = s.extra.getCompoundTag(EXTRA);
        if (l.hasKey(TAGS)) {
            return l.getInteger(TAGS) | TAG_NODE;
        }
        int cx = SubstrateCellsSC.cellOf(s.x), cz = SubstrateCellsSC.cellOf(s.z);
        int[] kinds = new int[9];
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kinds[n++] = SubstrateCellsSC.cellKind(w, cx + dx, cz + dz);
            }
        }
        int t = tagsOf(kinds);
        l.setInteger(TAGS, t);
        s.extra.setTag(EXTRA, l);
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        if (reg != null) {
            reg.markDirty();
        }
        return t;
    }
}
