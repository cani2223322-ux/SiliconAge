package com.sc.manual;

import java.util.List;

/**
 * The proposal of 2026-10-26 (docs/plan-ideas-2026-10-26.md): the WORLD chapter's new articles (BookWorldSC calls add at the chapter's
 * end, after the embassies' - the coordinator's), one method an item, each implementer only his own; an item that only changes an
 * article that exists (the contracts, the raids, the Warden, the docks...) edits that article where it is instead. The main mod's items
 * (ОМ-1..ОМ-7) add their lines to their own chapters (BookContent: the quarry, materials, machines, energy, armour) where they belong.
 */
final class BookIdeasSC {

    private BookIdeasSC() {
    }

    static void add(List<BookEntry> list, BookChapter c) {
        squads(list, c);
        alarms(list, c);
        wanted(list, c);
        gatherers(list, c);
        protocols(list, c);
        relayPosts(list, c);
        tunnels(list, c);
    }

    /** ПД-2: «Звено» (world.squads). */
    private static void squads(List<BookEntry> list, BookChapter c) {
        BookEntry e = new BookEntry("world.squads", c, com.sc.settle.SettlementItemsSC.chips(1), Lang.tr("sc.manual.world.squads.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.squads.title")));
        if (!com.sc.dim.SubstrateConfigSC.squads) {
            e.add(BookEl.warn(Lang.tr("sc.squad.off")));
        }
        e.addAll(BookContent.paras("sc.manual.world.squads", new Object[]{com.sc.settle.SquadSC.MAX},
                new Object[]{Math.round(com.sc.settle.SquadSC.SHARE * 100F)}, null));
        e.add(BookEl.dim(Lang.tr("sc.cmd.squad.usage")));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        list.add(e);
    }

    /** ПД-3: «Тревога по вызову» (world.alarms) - or a part of the raids' article. */
    private static void alarms(List<BookEntry> list, BookChapter c) {
    }

    /** ПД-4: «Розыск» (world.wanted). */
    private static void wanted(List<BookEntry> list, BookChapter c) {
        net.minecraft.item.ItemStack token = com.sc.init.IdeaItemsSC.wantedToken == null ? null : com.sc.init.IdeaItemsSC.wantedToken(1);
        BookEntry e = new BookEntry("world.wanted", c, token, Lang.tr("sc.manual.world.wanted.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.wanted.title")));
        if (!com.sc.dim.SubstrateConfigSC.wanted) {
            e.add(BookEl.warn(Lang.tr("sc.wanted.quiet")));
        }
        int share = Math.round(com.sc.mob.WantedRulesSC.REWARD_SHARE * 100F);
        e.addAll(BookContent.paras("sc.manual.world.wanted", new Object[]{com.sc.mob.WantedRulesSC.ESCORT, com.sc.mob.WantedRulesSC.RANGE_MIN,
                com.sc.mob.WantedRulesSC.RANGE_MAX}, new Object[]{com.sc.mob.WantedRulesSC.HINT_CHIPS, com.sc.mob.WantedRulesSC.HINT_SPREAD},
                new Object[]{share, com.sc.mob.WantedRulesSC.REWARD_CHIPS, com.sc.mob.WantedRulesSC.REWARD_REP, com.sc.mob.WantedRulesSC.TOKENS_PER_RESTORE}));
        if (token != null) {
            e.add(BookEl.item(token, token.getDisplayName(), Lang.tr("sc.tooltip.wantedToken.howto", com.sc.mob.WantedRulesSC.TOKENS_PER_RESTORE)));
            e.about.add(token);
        }
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        list.add(e);
    }

    /** ПД-5: «Собиратели» (world.gatherers). */
    private static void gatherers(List<BookEntry> list, BookChapter c) {
        net.minecraft.item.ItemStack druse = com.sc.dim.SubstrateBlocksSC.druse == null ? null : new net.minecraft.item.ItemStack(com.sc.dim.SubstrateBlocksSC.druse);
        BookEntry e = new BookEntry("world.gatherers", c, druse, Lang.tr("sc.manual.world.gatherers.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.gatherers.title")));
        int[] per = com.sc.settle.GathererSC.PER_SIZE;
        e.addAll(BookContent.paras("sc.manual.world.gatherers", new Object[]{per[com.sc.settle.SettlementSizeSC.SETTLEMENT.ordinal()],
                per[com.sc.settle.SettlementSizeSC.CITY.ordinal()], com.sc.settle.GathererSC.REACH, com.sc.settle.GathererSC.NEAR},
                new Object[]{com.sc.settle.GathererSC.RESCUE_REP}, null));
        if (!com.sc.dim.SubstrateConfigSC.gatherers) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.off")));
        }
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        list.add(e);
    }

    /** ПД-6: «Протоколы Пробоя» (world.protocols). */
    private static void protocols(List<BookEntry> list, BookChapter c) {
        net.minecraft.item.ItemStack first = com.sc.init.IdeaItemsSC.breachProtocol(0);
        BookEntry e = new BookEntry("world.protocols", c, first, Lang.tr("sc.manual.world.protocols.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.protocols.title")));
        if (!com.sc.dim.SubstrateConfigSC.breachProtocols) {
            e.add(BookEl.warn(Lang.tr("sc.protocol.refuse.off")));
        }
        int max = com.sc.arena.BreachProtocolsSC.MAX;
        e.addAll(BookContent.paras("sc.manual.world.protocols", new Object[]{max}, new Object[]{com.sc.arena.BreachProtocolsSC.CRUMBS_EACH,
                Math.round(com.sc.arena.BreachProtocolsSC.TROPHY_BONUS_EACH * 100F), max}));
        for (int p = 0; p < com.sc.arena.BreachProtocolsSC.COUNT; p++) {
            net.minecraft.item.ItemStack s = com.sc.init.IdeaItemsSC.breachProtocol(p);
            if (s == null) {
                continue;
            }
            String key = "sc.tooltip.breachProtocol." + com.sc.arena.BreachProtocolsSC.keyOf(p);
            String line = p == com.sc.arena.BreachProtocolsSC.SHORT_FUSE ? Lang.tr(key, com.sc.arena.BreachProtocolsSC.SHORT_FUSE_TICKS / 20,
                    com.sc.mob.WardenRulesSC.FINISH_TICKS / 20) : p == com.sc.arena.BreachProtocolsSC.SWARM
                    ? Lang.tr(key, String.valueOf(com.sc.arena.BreachProtocolsSC.SWARM_MUL)) : p == com.sc.arena.BreachProtocolsSC.JAMMING
                    ? Lang.tr(key, com.sc.arena.BreachProtocolsSC.JAMMING_EVERY / 20) : Lang.tr(key);
            e.add(BookEl.item(s, s.getDisplayName(), line));
            e.about.add(s);
        }
        e.add(BookEl.dim(Lang.tr("sc.tooltip.breachProtocol.howto")));
        e.add(BookEl.link("world.cosmetics", Lang.tr("sc.manual.world.cosmetics.title")));
        list.add(e);
    }

    /** ПД-7: «Посты-ретрансляторы» (world.relayPosts). */
    private static void relayPosts(List<BookEntry> list, BookChapter c) {
        net.minecraft.item.ItemStack relay = com.sc.init.IdeaBlocksSC.relay == null ? null : com.sc.init.IdeaBlocksSC.relay(1);
        BookEntry e = new BookEntry("world.relayPosts", c, relay, Lang.tr("sc.manual.world.relayPosts.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.relayPosts.title")));
        if (!com.sc.dim.SubstrateConfigSC.relayPosts) {
            e.add(BookEl.warn(Lang.tr("sc.relay.refuse.off")));
        }
        String own = Lang.tr("sc.settle.rep." + com.sc.settle.ReputationSC.LEVEL_KEYS[com.sc.settle.RelayRulesSC.BUY_LEVEL]);
        int g = com.sc.settle.RelayRulesSC.REGION;
        e.addAll(BookContent.paras("sc.manual.world.relayPosts", new Object[]{own, com.sc.settle.RelayRulesSC.PRICE_CHIPS,
                com.sc.settle.RelayRulesSC.HOLD_TICKS / 1200, com.sc.settle.RelayRulesSC.WAVES}, null,
                new Object[]{g, g, com.sc.settle.RelayRulesSC.SETTLEMENT_GAP}));
        if (relay != null) {
            e.add(BookEl.item(relay, relay.getDisplayName(), Lang.tr("sc.nei.obtain.relay", own, com.sc.settle.RelayRulesSC.PRICE_CHIPS)));
            e.about.add(relay);
        }
        e.add(BookEl.link("world.raids", Lang.tr("sc.manual.world.raids.title")));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        list.add(e);
    }

    /** ПД-8: «Тоннели под плато» (world.tunnels). */
    private static void tunnels(List<BookEntry> list, BookChapter c) {
        net.minecraft.item.ItemStack trace = com.sc.dim.SubstrateBlocksSC.rock == null ? null
                : new net.minecraft.item.ItemStack(com.sc.dim.SubstrateBlocksSC.rock, 1, com.sc.dim.SubstrateBlocksSC.COPPER_TRACE);
        BookEntry e = new BookEntry("world.tunnels", c, trace, Lang.tr("sc.manual.world.tunnels.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.tunnels.title")));
        e.addAll(BookContent.paras("sc.manual.world.tunnels", new Object[]{com.sc.ruin.TunnelLayoutSC.WIDTH, com.sc.ruin.TunnelLayoutSC.HEIGHT,
                com.sc.ruin.TunnelLayoutSC.FLOOR_MIN, com.sc.ruin.TunnelLayoutSC.FLOOR_MAX}, null, null));
        if (!com.sc.dim.SubstrateConfigSC.tunnels) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.off")));
        }
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        list.add(e);
    }
}
