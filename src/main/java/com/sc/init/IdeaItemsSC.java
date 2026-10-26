package com.sc.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The proposal of 2026-10-26 (docs/plan-ideas-2026-10-26.md): its new items, registered from ModItems.init (preInit, after
 * EmbassyItemsSC). Registry names are what worlds keep - fixed here (append only):
 * <ul>
 * <li>"wantedToken" - mob.ItemWantedTokenSC (ПД-4): «Жетон розыска»;</li>
 * <li>"breachProtocol" - arena.ItemBreachProtocolSC (ПД-6): «Протокол Пробоя», meta = arena.BreachProtocolsSC protocol;</li>
 * <li>"nanitePaste" - item.ItemNanitePasteSC (ОМ-2): «Нанитовая паста», a plain component (ModItems.COMPONENTS, the creative tab's parts).</li>
 * </ul>
 * ОМ-4's processor ladder lives in SiliconMaterial (siliconMaterial's metas 18..21) and its EUV mask in SCToolType (toolEUV_MASK); ПД-7's
 * Relay, ОМ-5's Data Port and ОМ-7's Energy Bank are blocks (IdeaBlocksSC). Each item gets its TooltipSC tooltip, texture, NEI «how to
 * get» page and handbook line from its own item's implementer. Shared core: only the coordinator edits this file.
 */
public final class IdeaItemsSC {

    public static Item wantedToken, breachProtocol;
    public static com.sc.item.ItemNanitePasteSC nanitePaste;

    private IdeaItemsSC() {
    }

    public static void init() {
        wantedToken = new com.sc.mob.ItemWantedTokenSC();
        GameRegistry.registerItem(wantedToken, "wantedToken");
        breachProtocol = new com.sc.arena.ItemBreachProtocolSC();
        GameRegistry.registerItem(breachProtocol, "breachProtocol");
        nanitePaste = new com.sc.item.ItemNanitePasteSC();
        GameRegistry.registerItem(nanitePaste, com.sc.item.ItemNanitePasteSC.NAME);
        ModItems.COMPONENTS.put(com.sc.item.ItemNanitePasteSC.NAME, nanitePaste);
    }

    /** `n` Wanted Tokens (null before init). */
    public static ItemStack wantedToken(int n) {
        return wantedToken == null ? null : new ItemStack(wantedToken, n, 0);
    }

    /** Protocol `p` (arena.BreachProtocolsSC; null before init or for an unknown one). */
    public static ItemStack breachProtocol(int p) {
        return breachProtocol == null || p < 0 || p >= com.sc.arena.BreachProtocolsSC.COUNT ? null : new ItemStack(breachProtocol, 1, p);
    }

    /** `n` Nanite Pastes (null before init). */
    public static ItemStack nanitePaste(int n) {
        return nanitePaste == null ? null : new ItemStack(nanitePaste, n, 0);
    }
}
