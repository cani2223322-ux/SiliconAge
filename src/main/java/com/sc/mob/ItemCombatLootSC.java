package com.sc.mob;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7, docs/plan-substrate.md §7): «Боевая добыча» - what the Substrate's
 * factions drop under rule ПТ-1 (born in Si-14, died there, a real player's kill). Meta = LootKind ordinal: appended
 * only, worlds keep it. The firmware and the scrap have a modest use in the Crusher (MobRecipesSC) and a settlement's
 * Salvager buys them, the nanite dust too (Ф3, TradeTableSC); the Shield Core is a part of an Exo trophy's restoring
 * (TrophyRestoreSC). Scrap never turns back into armour (ПТ-8). The Stolen Cargo (Ф2, package T) goes back to an Elder.
 */
public class ItemCombatLootSC extends Item {

    /** The kinds, in meta order (append only). `key` - the lang / texture name (loot_<key>.png). */
    public enum LootKind {
        /** «Прошивка оболочки» - the Lineman's (100%). */
        SHELL_FIRMWARE("shellFirmware"),
        /** «Лом брони Нано» - the Marauder's (40%). */
        NANO_SCRAP("nanoScrap"),
        /**
         * «Краденый груз» (Ф2, rule ПТ-10) - the Marauder's (MobDropsSC.CARGO_CHANCE, 5%): a settlement's sealed crate.
         * Only an Elder takes it back (+15 «Доверия», TradeTableSC; the contract «Вернуть 3»); never in the Crusher.
         */
        STOLEN_CARGO("stolenCargo"),
        // ---- Ф3 (the coordinator's, docs/plan-substrate-tech-f3.md §2.2): the Salvager buys them (ПТ-12); icons by the packages whose mobs drop them
        /** «Лом брони Квант» - the Trooper's (50%) and the Jammer's (40%). */
        QUANTUM_SCRAP("quantumScrap"),
        /** «Лом брони Экзо» - the Exo Executioner's (1..2, 100%). */
        EXO_SCRAP("exoScrap"),
        /** «Нанитовая пыль» - a nanite particle's (50%). */
        NANITE_DUST("naniteDust"),
        /** «Сердечник щита» - the Exo Executioner's (15%); the Armourer wants two to restore an Exo piece. */
        SHIELD_CORE("shieldCore"),
        // ---- Ф4 (the coordinator's, docs/plan-substrate-tech-f4.md §2.2): icons by the packages whose creatures drop them (M, N)
        /** «Выкипевший контур» - the Overheated's (20%); the Gas Technician buys it (3 КЧ). */
        BOILED_LOOP("boiledLoop"),
        /** «Индукционная нить» - the Induction Phantom's (30%). */
        INDUCTION_THREAD("inductionThread"),
        /** «Плазменный конденсат» - the Arc Strider's (50%). */
        PLASMA_CONDENSATE("plasmaCondensate"),
        /** «Лом брони Сингулярный» - «Затмение»'s (2..3); the Salvager buys two for 8 КЧ. */
        SINGULAR_SCRAP("singularScrap"),
        /** «Природная пластина» - the Wafer Crab's (5%); the Technologist buys four for 2 КЧ. */
        NATURAL_WAFER("naturalWafer");

        public final String key;

        LootKind(String key) {
            this.key = key;
        }

        public static LootKind of(int meta) {
            LootKind[] all = values();
            return all[meta >= 0 && meta < all.length ? meta : 0];
        }
    }

    private IIcon[] icons;

    public ItemCombatLootSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".combatLoot");
    }

    public ItemStack stackOf(LootKind kind, int count) {
        return new ItemStack(this, count, kind.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + LootKind.of(stack.getItemDamage()).key;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        LootKind[] all = LootKind.values();
        icons = new IIcon[all.length];
        for (LootKind k : all) {
            icons[k.ordinal()] = register.registerIcon(Reference.ASSETS + ":loot_" + k.key);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[LootKind.of(meta).ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (LootKind k : LootKind.values()) {
            list.add(new ItemStack(item, 1, k.ordinal()));
        }
    }

    /**
     * sc.tooltip.loot.<key>: what it is; Shift - where it comes from; Ctrl - what it's good for now: the Crusher's numbers
     * from MobRecipesSC (how many go in, the firmware's bonus chance in %); the Stolen Cargo, which the Crusher never takes:
     * the Elder's «Доверие» for one and the contract's count; the Shield Core: how many an Exo trophy's restoring takes; the
     * nanite dust: no machine takes it. Then (Ф3) the Salvager's price, if he buys it (the table's base numbers).
     */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        LootKind kind = LootKind.of(stack.getItemDamage());
        String k = "sc.tooltip.loot." + kind.key;
        TooltipSC.wrap(list, Lang.tr(k), "§7");
        String howto;
        if (kind == LootKind.STOLEN_CARGO) {
            howto = Lang.tr(k + ".howto", com.sc.settle.ReputationSC.CARGO, cargoContractTarget());
        } else if (kind == LootKind.SHIELD_CORE) {
            howto = Lang.tr(k + ".howto", shieldCoresForExo());
        } else if (MobRecipesSC.inputCount(kind) > 0) {
            howto = Lang.tr(k + ".howto", MobRecipesSC.inputCount(kind), Math.round(MobRecipesSC.FIRMWARE_TRANSISTOR * 100F));
        } else {
            howto = Lang.tr(k + ".howto");
        }
        com.sc.settle.trade.TradeOfferSC sale = com.sc.settle.trade.TradeTableSC.salvagerSale(stack);
        if (sale != null && sale.out != null) {
            howto += "\n" + Lang.tr("sc.tooltip.loot.salvager", sale.in[0].count, sale.out.count);
        }
        howto += otherBuyers(stack);
        TooltipSC.more(list, Lang.tr(k + ".details"), howto);
    }

    /**
     * Ф4 (docs/plan-substrate-tech-f4.md §9.5, package M - the tooltip only): the other settlement folk who buy it for chips (the
     * Gas Technician's Boiled Loop, the Technologist's Natural Wafers - TradeTableSC's sales, the base numbers) - a line each
     * (sc.tooltip.loot.buyer: who, how many, chips); the Salvager's has its own line above. Empty when nobody else buys it.
     */
    @SideOnly(Side.CLIENT)
    private static String otherBuyers(ItemStack stack) {
        StringBuilder out = new StringBuilder();
        for (com.sc.settle.trade.TradeOfferSC o : com.sc.settle.trade.TradeTableSC.all()) {
            if (o.kind == com.sc.settle.NpcKindSC.SALVAGER || !o.sell() || o.in.length != 1 || o.in[0] == null || !o.in[0].matches(stack)
                    || o.out == null) {
                continue;
            }
            out.append('\n').append(Lang.tr("sc.tooltip.loot.buyer", Lang.tr(o.kind.langKey()), o.in[0].count, o.out.count));
        }
        return out.toString();
    }

    /** How many Shield Cores an Exo trophy piece's restoring takes (TrophyRestoreSC's parts), for the tooltip. */
    @SideOnly(Side.CLIENT)
    private static int shieldCoresForExo() {
        com.sc.settle.trade.TradeOfferSC.Ingredient[] parts = TrophyRestoreSC.parts(TrophyRestoreSC.G_PIECE, com.sc.util.ArmorSuit.EXO.ordinal());
        ItemStack core = MobItemsSC.loot(LootKind.SHIELD_CORE, 1);
        if (parts != null && core != null) {
            for (com.sc.settle.trade.TradeOfferSC.Ingredient i : parts) {
                if (i.matches(core)) {
                    return i.count;
                }
            }
        }
        return 0;
    }

    /** How many Stolen Cargoes the contract «Вернуть» wants (ContractTableSC), for the tooltip. */
    @SideOnly(Side.CLIENT)
    private static int cargoContractTarget() {
        com.sc.settle.trade.ContractTableSC.Contract c = com.sc.settle.trade.ContractTableSC.byKey(com.sc.settle.trade.ContractTableSC.RETURN_CARGO);
        return c == null ? 0 : c.target;
    }
}
