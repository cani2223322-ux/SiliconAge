package com.sc.embassy;

import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeTableSC;

/**
 * PACKAGE T (docs/plan-substrate-tech-f6.md §8.1, rules ПО-23..ПО-25, ПД-7): an embassy's trade - what of TradeTableSC is open there.
 * TradeServerSC asks allowed() for every deal of a Tuner of an embassy (its state hides the rest, its run refuses them - R_NOT_HERE):
 * <ul>
 * <li>every sale TO the Tuners (goods for Credit Chips: the Technologist's wafers and transistors, the Metallurgist's Ge and Nb, the
 * Gas Technician's PTFE, the Cartographer's lenses, the Salvager's loot) - as in the Substrate;</li>
 * <li>the Quartermaster's supply deals (bread, logs, coal, iron - TradeTableSC's QM_* keys);</li>
 * <li>the Cartographer's Return Capsule and Settlement Map (the shop: nothing else is sold - no tantalum, hafnium, deuterium, helium,
 * no ПЭ-1 module, no chip, no Crumb, no Coordinator: those are the Substrate's reward);</li>
 * <li>the Power Engineer's charging (a service, nothing sold); the Ambassador's penance (ПД-4: under the ceiling).</li>
 * </ul>
 * «Доверие» from an embassy's deals and contracts goes through ReputationSC.give(..., ReputationSC.ceiling(s)) - 599 at most (ПД-7).
 * Fixed (the coordinator's): the class, allowed's signature. T's: the body.
 * <p>
 * The rule, in this order: a Quartermaster's deal - only at an embassy (no Substrate settlement has one); a Substrate settlement or an
 * unbound Tuner - everything else as before; at an embassy nothing from «Свой» up (those levels come from deeds in the Substrate),
 * every sale (the player gets chips), the Power Engineer's charge (OUT_CHARGE - never the Armourer's restoring: an embassy has no
 * Armourer), and of what is bought only SHOP. A deal appended later is closed at an embassy unless it is a sale - the Substrate's
 * reward stays the Substrate's by default.
 */
public final class EmbassyTradeSC {

    /** ПО-23, ПО-24, ПД-4: the only deals an embassy opens that the player pays chips for - the Capsule, the Map, the penance. */
    public static final String[] SHOP = {TradeTableSC.CART_CAPSULE, TradeTableSC.CART_MAP, TradeTableSC.ELDER_PENANCE};

    private EmbassyTradeSC() {
    }

    /** Deal `o` is open at settlement `s` (a Substrate settlement: every deal; an embassy: see the class comment). Pure. */
    public static boolean allowed(TradeOfferSC o, SettlementRegistrySC.Settlement s) {
        if (o == null) {
            return false;
        }
        boolean embassy = SettlementSC.isEmbassy(s);
        if (o.kind == NpcKindSC.QUARTERMASTER) {
            return embassy;                                     // ПО-25: the supply depots are an embassy's alone
        }
        if (!embassy) {
            return true;
        }
        if (o.minLevel >= ReputationSC.OWN) {
            return false;                                       // «Свой» / «Почётный»: the Substrate's levels, the Substrate's deals
        }
        if (o.sell() || o.outKind == TradeOfferSC.OUT_CHARGE) {
            return true;                                        // ПО-23: every sale to the Tuners; the Power Engineer's charge
        }
        for (String k : SHOP) {
            if (k.equals(o.key)) {
                return true;
            }
        }
        return false;
    }
}
