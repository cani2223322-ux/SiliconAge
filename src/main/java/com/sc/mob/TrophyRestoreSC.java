package com.sc.mob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sc.bridge.BridgeMathSC;
import com.sc.init.ModItems;
import com.sc.settle.ReputationSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SiliconMaterial;
import com.sc.util.WeaponType;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE T (docs/plan-substrate-tech-f3.md §6.6, rules ПТ-11, ПТ-12): «восстановление трофея» - a «Трофейный …» item made
 * into the real one again: a FRESH stack of the real item (ItemTrophySC.realStack: charge 0, no gases, chips or modules; a
 * Singular piece or blade at level 1) - the trophy's own NBT is never copied, so no NBT trick makes a charged or upgraded
 * piece of it, and the player armour code is never asked. Where:
 * <ul>
 * <li>WHERE_ARMOURER - a settlement's Armourer (a service deal of TradeTableSC: Credit Chips + components, by its level);</li>
 * <li>WHERE_STATION - one's own Armour Service Station (TileEntityArmorStationSC): a right-click with the trophy in hand
 * (sneaking too - ItemTrophySC.doesSneakBypassUse lets that click reach the block) - the components from the inventory (the
 * Armourer's, without the chips) and STATION_EU from the station's buffer; the first click says the price, a second within
 * CONFIRM_TICKS does it;</li>
 * <li>WHERE_SINGULAR_STATION - the Singular Service Station: a Singular trophy only there, for one Singularity Clot (and
 * SINGULAR_STATION_EU); any other trophy as at the plain station.</li>
 * </ul>
 * A Singular trophy anywhere else: refused (R_SINGULAR_ONLY). Scrap never turns back into armour (ПТ-8). Server for the
 * actions; refusal / restored / tier are pure (the self-test calls them). Fixed signatures (the core's); the bodies are T's.
 * <p>
 * The prices (ПТ-12, about 35% of the craft, TODO(balance)) go by a price group (an armour piece, a blade or a gun, the
 * ПЭ-1) and a tier (tier()): the Armourer's chips (PIECE_CHIPS, ARMS_CHIPS, PE1_CHIPS), his level (LEVELS) and the parts
 * (parts()) - the same parts at a station, without the chips. TradeTableSC builds the Armourer's services from these, the
 * trophy's tooltip and the handbook print them: one table for all.
 * <p>
 * The station's confirmation is per player UUID in memory (a server restart forgets it - the next click says the price
 * again): the same station, the same hotbar slot, the same trophy, the second click at least REPEAT_TICKS after the last
 * one (holding the button repeats a click every 4 ticks - a stream of clicks never confirms) and within CONFIRM_TICKS of
 * the first. Two players at one station: each has his own. A FakePlayer (a mod's machine) never restores
 * (onStationClick); the world test calls click() itself.
 * <p>
 * Ф5 (package P, docs/plan-substrate-tech-f5.md §9.6, rule ПТ-26): the ПЭ-1М trophy (TrophyTableSC.META_PE1M, 24 - the Breakdown
 * Warden's 2%) is of the Singular tier (tier() said so already): singular() answers it too, so only the Singular station restores
 * it - for one Singularity Clot and SINGULAR_STATION_EU, as a Singular piece; the Armourer and the plain station refuse it
 * (R_SINGULAR_ONLY, sc.restore.singularOnly). It becomes a fresh ПЭ-1М (ModItems.empGun meta 1): no modules, charge 0, the default
 * settings - the trophy's NBT never copied. Its price group stays G_NONE and metas() never lists it: no Armourer's service takes it.
 * What metas 0..23 answered before is unchanged; 25 and on stay unknown.
 */
public final class TrophyRestoreSC {

    public static final int WHERE_ARMOURER = 0, WHERE_STATION = 1, WHERE_SINGULAR_STATION = 2;
    /** refusal's answers. */
    public static final int OK = 0, R_UNKNOWN = 1, R_SINGULAR_ONLY = 2;
    /** ПТ-12: EU off the station's buffer (an MV buffer holds 12 800), the Singular station's; the confirming click's window. TODO(balance) */
    public static final int STATION_EU = 10000, SINGULAR_STATION_EU = 1000000, CONFIRM_TICKS = 100;
    /** A click sooner than this after the last one is the button held down (vanilla repeats it every 4 ticks), never a confirmation. */
    public static final int REPEAT_TICKS = 8;
    /** ПТ-12's price groups: an armour piece (0..15), a blade or a gun (16..22), the ПЭ-1 (23); NONE - the ПЭ-1М and unknown metas. */
    public static final int G_PIECE = 0, G_ARMS = 1, G_PE1 = 2, G_NONE = -1;
    /** The Armourer's Credit Chips by tier (Nano, Quantum, Exo): a piece, a blade or a gun; the ПЭ-1's. TODO(balance) */
    public static final int[] PIECE_CHIPS = {6, 14, 30}, ARMS_CHIPS = {4, 10, 24};
    public static final int PE1_CHIPS = 32;
    /** The Armourer's «Доверие» level by tier: «Знакомый», «Партнёр», «Свой» (the ПЭ-1: «Свой»). */
    public static final int[] LEVELS = {ReputationSC.ACQUAINTANCE, ReputationSC.PARTNER, ReputationSC.OWN};
    /** The tiers the Armourer restores (Nano..Exo); the Singular one is the Singular station's alone. */
    public static final int ARMOURER_TIERS = 3;
    /** click()'s answers: not a trophy at a station; the price said; restored; refused (Singular, unknown); parts or EU short; a private field; a held button. */
    public static final int C_NONE = 0, C_PRICE = 1, C_DONE = 2, C_REFUSED = 3, C_MISSING = 4, C_NO_EU = 5, C_PRIVATE = 6, C_HELD = 7;

    /** A station's restoring waiting for its second click, by player UUID (the server thread only). */
    private static final Map<UUID, Pending> PENDING = new HashMap<UUID, Pending>();

    private TrophyRestoreSC() {
    }

    /**
     * ПТ-11 (pure): may trophy meta `meta` be restored `where` - OK, R_UNKNOWN (no such trophy), R_SINGULAR_ONLY (a Singular piece or
     * blade, Ф5: the ПЭ-1М too - the Singular station's alone).
     */
    public static int refusal(int meta, int where) {
        if (meta < 0 || meta >= TrophyTableSC.METAS || where < WHERE_ARMOURER || where > WHERE_SINGULAR_STATION) {
            return R_UNKNOWN;
        }
        if (singular(meta)) {
            return where == WHERE_SINGULAR_STATION ? OK : R_SINGULAR_ONLY;
        }
        return OK;
    }

    /**
     * ПТ-11: what trophy `meta` becomes - a fresh real stack (null for an unknown meta); Ф5 (ПТ-26): the ПЭ-1М's - ModItems.empGun
     * meta 1 with no NBT at all: no modules, charge 0, the default settings.
     */
    public static ItemStack restored(int meta) {
        return ItemTrophySC.realStack(meta);
    }

    /**
     * ПТ-12 (pure): the level a trophy is priced at - the ArmorSuit ordinal of its armour / blade; the guns: the Ion Cutter
     * Nano's, the Pulse Emitter and the Plasma Rifle Quantum's; the ПЭ-1 Exo's, the ПЭ-1М Singular's. -1 unknown.
     */
    public static int tier(int meta) {
        if (meta >= 0 && meta < TrophyTableSC.META_WEAPON) {
            ArmorSuit s = TrophyTableSC.level(meta);
            return s == null ? -1 : s.ordinal();
        }
        if (meta >= TrophyTableSC.META_WEAPON && meta < TrophyTableSC.META_PE1) {
            return meta - TrophyTableSC.META_WEAPON == WeaponType.ION_CUTTER.ordinal() ? ArmorSuit.NANO.ordinal() : ArmorSuit.QUANTUM.ordinal();
        }
        if (meta == TrophyTableSC.META_PE1) {
            return ArmorSuit.EXO.ordinal();
        }
        return meta == TrophyTableSC.META_PE1M ? ArmorSuit.SINGULAR.ordinal() : -1;
    }

    /** A Singular piece or blade (metas 12..15, 19), Ф5 (ПТ-26) the ПЭ-1М (24): the Singular station's alone (pure). */
    public static boolean singular(int meta) {
        if (meta == TrophyTableSC.META_PE1M) {
            return true;
        }
        return meta >= 0 && meta < TrophyTableSC.META_WEAPON && TrophyTableSC.level(meta) == ArmorSuit.SINGULAR;
    }

    /** The trophy's price group (pure): G_PIECE, G_ARMS, G_PE1; G_NONE for the ПЭ-1М and unknown metas. */
    public static int group(int meta) {
        if (meta >= 0 && meta < TrophyTableSC.META_BLADE) {
            return G_PIECE;
        }
        if (meta >= TrophyTableSC.META_BLADE && meta < TrophyTableSC.META_PE1) {
            return G_ARMS;
        }
        return meta == TrophyTableSC.META_PE1 ? G_PE1 : G_NONE;
    }

    /**
     * The trophies priced alike (pure): every meta of `group` at `tier`, Singular ones aside (in meta order) - the ПЭ-1М (Ф5) too: its
     * group is G_NONE and it is singular(), so no Armourer's service ever lists it.
     */
    public static int[] metas(int group, int tier) {
        int[] buf = new int[TrophyTableSC.METAS];
        int n = 0;
        for (int m = 0; m < TrophyTableSC.METAS; m++) {
            if (group(m) == group && tier(m) == tier && !singular(m)) {
                buf[n++] = m;
            }
        }
        return Arrays.copyOf(buf, n);
    }

    /** The Armourer's Credit Chips for `group` at `tier` (pure; -1 - he doesn't restore it). */
    public static int armourerChips(int group, int tier) {
        if (tier < 0 || tier >= ARMOURER_TIERS) {
            return -1;
        }
        switch (group) {
            case G_PIECE:
                return PIECE_CHIPS[tier];
            case G_ARMS:
                return ARMS_CHIPS[tier];
            case G_PE1:
                return tier == ArmorSuit.EXO.ordinal() ? PE1_CHIPS : -1;
            default:
                return -1;
        }
    }

    /** The Armourer's level for `group` at `tier` (pure; the ПЭ-1 «Свой»; -1 - he doesn't restore it). */
    public static int armourerLevel(int group, int tier) {
        if (armourerChips(group, tier) < 0) {
            return -1;
        }
        return group == G_PE1 ? ReputationSC.OWN : LEVELS[tier];
    }

    /**
     * ПТ-12: the parts of `group` at `tier` - the Armourer's and the station's alike (the Armourer adds his chips): a Nano
     * piece 2 Controllers; a Quantum one 2 Memory Chips + 2 Controllers; an Exo one 2 Shield Cores + 2 tantalum ingots (by
     * the ore name: UniDict's are as good); a blade or a gun 1 Controller; the ПЭ-1 2 Controllers + 2 Focus Lenses. Null
     * when there are none (a group he doesn't restore) or an item isn't registered yet.
     */
    public static TradeOfferSC.Ingredient[] parts(int group, int tier) {
        if (armourerChips(group, tier) < 0 || ModItems.siliconMaterial == null) {
            return null;
        }
        List<TradeOfferSC.Ingredient> l = new ArrayList<TradeOfferSC.Ingredient>();
        ItemStack controller = ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER);
        if (group == G_ARMS) {
            l.add(TradeOfferSC.Ingredient.of(controller, 1));
        } else if (group == G_PE1) {
            l.add(TradeOfferSC.Ingredient.of(controller, 2));
            l.add(TradeOfferSC.Ingredient.of(ModItems.component("focusLens") == null ? null : new ItemStack(ModItems.component("focusLens")), 2));
        } else if (tier == ArmorSuit.NANO.ordinal()) {
            l.add(TradeOfferSC.Ingredient.of(controller, 2));
        } else if (tier == ArmorSuit.QUANTUM.ordinal()) {
            l.add(TradeOfferSC.Ingredient.of(ModItems.siliconMaterial.stackOf(SiliconMaterial.MEMORY_CHIP), 2));
            l.add(TradeOfferSC.Ingredient.of(controller, 2));
        } else {
            l.add(TradeOfferSC.Ingredient.of(MobItemsSC.loot(ItemCombatLootSC.LootKind.SHIELD_CORE, 1), 2));
            l.add(TradeOfferSC.Ingredient.ore("ingotTantalum", 2));
        }
        return complete(l);
    }

    /** The Singular station's part for a Singular trophy: one Singularity Clot (null before init). */
    public static TradeOfferSC.Ingredient[] singularParts() {
        List<TradeOfferSC.Ingredient> l = new ArrayList<TradeOfferSC.Ingredient>();
        l.add(TradeOfferSC.Ingredient.of(ModItems.singularClot == null ? null : new ItemStack(ModItems.singularClot), 1));
        return complete(l);
    }

    private static TradeOfferSC.Ingredient[] complete(List<TradeOfferSC.Ingredient> l) {
        for (TradeOfferSC.Ingredient i : l) {
            if (i.stack == null && i.ore == null || i.stack != null && i.stack.getItem() == null) {
                return null;
            }
        }
        return l.toArray(new TradeOfferSC.Ingredient[l.size()]);
    }

    /** What restoring a trophy costs at one place. */
    public static final class Price {
        /** The Armourer's Credit Chips (0 at a station). */
        public final int chips;
        /** The parts out of the inventory. */
        public final TradeOfferSC.Ingredient[] parts;
        /** EU off the station's buffer (0 at the Armourer). */
        public final int eu;
        /** The Armourer's «Доверие» level (-1 at a station). */
        public final int level;

        private Price(int chips, TradeOfferSC.Ingredient[] parts, int eu, int level) {
            this.chips = chips;
            this.parts = parts;
            this.eu = eu;
            this.level = level;
        }
    }

    /** Trophy `meta`'s price `where` - null when refused there (refusal) or an item isn't registered yet. */
    public static Price price(int meta, int where) {
        if (refusal(meta, where) != OK) {
            return null;
        }
        if (singular(meta)) {
            TradeOfferSC.Ingredient[] p = singularParts();
            return p == null ? null : new Price(0, p, SINGULAR_STATION_EU, -1);
        }
        int g = group(meta), t = tier(meta);
        TradeOfferSC.Ingredient[] p = parts(g, t);
        if (p == null) {
            return null;
        }
        return where == WHERE_ARMOURER ? new Price(armourerChips(g, t), p, 0, armourerLevel(g, t)) : new Price(0, p, STATION_EU, -1);
    }

    // ================================================================== the station

    /**
     * BlockArmorStationSC.onBlockActivated (both stations), first thing: a right-click with a trophy in hand is a restoring,
     * not the screen. @return true when it was one (handled - the screen doesn't open). Both sides (the client answers the
     * same, from the held stack, so it doesn't open the screen either); the work is the server's.
     */
    public static boolean onStationClick(World world, int x, int y, int z, EntityPlayer player) {
        ItemStack held = player == null ? null : player.getCurrentEquippedItem();
        if (world == null || !isTrophy(held) || !(world.getTileEntity(x, y, z) instanceof TileEntityArmorStationSC)) {
            return false;
        }
        if (!world.isRemote && !(player instanceof FakePlayer)) {    // a machine's player: the click is eaten, nothing restored
            click(world, x, y, z, player, world.getTotalWorldTime());
        }
        return true;
    }

    /**
     * Server: one click of `player` with a trophy in hand at the station at (x, y, z), at world tick `now` - the price,
     * or (the second one) the restoring. Messages go to the chat (a FakePlayer's are dropped). The world test calls it
     * itself with its own ticks (a FakePlayer works: no packets). @return C_*
     */
    public static int click(World world, int x, int y, int z, EntityPlayer player, long now) {
        TileEntity te = world == null || player == null || world.isRemote ? null : world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityArmorStationSC)) {
            return C_NONE;
        }
        int slot = player.inventory.currentItem;
        ItemStack held = player.inventory.getStackInSlot(slot);
        if (!isTrophy(held)) {
            return C_NONE;
        }
        UUID id = player.getUniqueID();
        TileEntityArmorStationSC station = (TileEntityArmorStationSC) te;
        if (!station.canItemCharge(player)) {
            PENDING.remove(id);
            return C_PRIVATE;                                   // someone else's private field: the chat said whose
        }
        int meta = held.getItemDamage();
        int where = te instanceof TileEntitySingularStationSC ? WHERE_SINGULAR_STATION : WHERE_STATION;
        int why = refusal(meta, where);
        Price price = why == OK ? price(meta, where) : null;
        ItemStack out = price == null ? null : restored(meta);
        if (out == null) {
            PENDING.remove(id);
            say(player, EnumChatFormatting.RED, why == R_SINGULAR_ONLY ? "sc.restore.singularOnly" : "sc.restore.unknown");
            return C_REFUSED;
        }
        int dim = world.provider.dimensionId;
        Pending p = PENDING.get(id);
        boolean same = p != null && p.at(dim, x, y, z) && p.slot == slot && p.meta == meta;
        if (same && now >= p.last && now - p.last < REPEAT_TICKS) {
            p.last = now;                                       // the button held down: not a second click
            return C_HELD;
        }
        if (!same || !confirms(p.first, now)) {
            PENDING.put(id, new Pending(dim, x, y, z, slot, meta, now));
            say(player, EnumChatFormatting.AQUA, "sc.restore.price", name(held), partsText(price.parts), BridgeMathSC.group(price.eu),
                    String.valueOf(CONFIRM_TICKS / 20));
            return C_PRICE;
        }
        PENDING.remove(id);
        ItemStack[] inv = player.inventory.mainInventory;
        List<TradeOfferSC.Ingredient> lacking = new ArrayList<TradeOfferSC.Ingredient>();
        for (TradeOfferSC.Ingredient i : price.parts) {
            if (i.countIn(inv) < i.count) {
                lacking.add(i);
            }
        }
        if (!lacking.isEmpty()) {
            say(player, EnumChatFormatting.RED, "sc.restore.missing", partsText(lacking.toArray(new TradeOfferSC.Ingredient[lacking.size()])));
            return C_MISSING;
        }
        if (station.getEnergyStored() < price.eu) {
            say(player, EnumChatFormatting.RED, "sc.restore.noEu", BridgeMathSC.group(station.getEnergyStored()), BridgeMathSC.group(price.eu));
            return C_NO_EU;
        }
        for (TradeOfferSC.Ingredient i : price.parts) {
            i.takeFrom(inv, i.count);
        }
        station.extractForItemCharging(price.eu);               // all of it: the buffer was checked just now
        player.inventory.setInventorySlotContents(slot, out);
        player.inventory.markDirty();
        if (!(player instanceof FakePlayer)) {
            player.inventoryContainer.detectAndSendChanges();
        }
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.anvil_use", 0.5F, 1.3F);
        say(player, EnumChatFormatting.GREEN, "sc.restore.done", name(out));
        return C_DONE;
    }

    /** A second click at tick `now` confirms the first one at `first` (pure): within CONFIRM_TICKS, not before it. */
    public static boolean confirms(long first, long now) {
        return now >= first && now - first <= CONFIRM_TICKS;
    }

    /** Server stopped: the pending confirmations forgotten. */
    public static void clearAll() {
        PENDING.clear();
    }

    private static boolean isTrophy(ItemStack s) {
        return s != null && s.getItem() != null && MobItemsSC.trophy != null && s.getItem() == MobItemsSC.trophy;
    }

    /** One player's first click: where, with what, when (and the last click, for a held button). */
    private static final class Pending {
        final int dim, x, y, z, slot, meta;
        final long first;
        long last;

        Pending(int dim, int x, int y, int z, int slot, int meta, long now) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.slot = slot;
            this.meta = meta;
            this.first = now;
            this.last = now;
        }

        boolean at(int d, int px, int py, int pz) {
            return d == dim && px == x && py == y && pz == z;
        }
    }

    // ------------------------------------------------------------------ the chat (the client translates: its own language)

    private static void say(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (p instanceof FakePlayer) {
            return;
        }
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    /** An item's name for the chat: its lang key when there is one (translated on the client), else the server's text. */
    private static IChatComponent name(ItemStack s) {
        String key = s.getUnlocalizedName() + ".name";
        return StatCollector.canTranslate(key) ? new ChatComponentTranslation(key) : new ChatComponentText(s.getDisplayName());
    }

    /** «2 × Контроллер, 2 × Чип памяти» for the chat. */
    private static IChatComponent partsText(TradeOfferSC.Ingredient[] parts) {
        IChatComponent all = new ChatComponentText("");
        for (int k = 0; k < parts.length; k++) {
            ItemStack s = parts[k].display();
            IChatComponent n = s != null ? name(s) : new ChatComponentText(parts[k].ore == null ? "?" : parts[k].ore);
            if (k > 0) {
                all.appendText(", ");
            }
            all.appendSibling(new ChatComponentTranslation("sc.restore.part", String.valueOf(parts[k].count), n));
        }
        return all;
    }
}
