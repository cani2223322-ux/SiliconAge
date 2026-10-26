package com.sc.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sc.mob.ItemTrophySC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.TrophyRestoreSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.util.ArmorSuit;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * ОМ-6 (docs/plan-ideas-2026-10-26.md, «Разборка лишних трофеев», ★ A): what a thing breaks into at the Armour Service Station - a trophy
 * (any but the Singular ones and the ПЭ-1М: those the Singular station restores, nothing breaks them), and one's own Nano / Quantum / Exo
 * piece, blade, drill and gun (ItemArmorSC, ItemBladeSC, ItemDrillSC, ItemWeaponSC, the ПЭ-1): SHARE_MIN..SHARE_MAX of its crafting
 * components (the recipe's own, rounded down, at least one of the dearest), the armour chips and the ПЭ-1 modules of one's own whole;
 * charge, gases and the Singular progress are lost. Never worth more than restoring the trophy (TrophyRestoreSC's price) - the self-test
 * checks every trophy meta. The station's salvage slot (TileEntityArmorStationSC.SALVAGE_SLOT) and its button
 * (ContainerArmorStationSC.BTN_SALVAGE, two clicks) do it; STATION_EU from its buffer.
 * <p>
 * The recipe: the real item's first crafting recipe (ShapedOreRecipe / ShapelessOreRecipe / the vanilla ones - an ore list gives its
 * first entry), else its first machine recipe (the Upgrade Stations: Quantum / Exo); a salvageable thing among the inputs (the piece,
 * blade, drill or gun of the level before) is not given back. "The dearest" - the side whose own recipe is the biggest (worth()).
 * A trophy's sides that are also its restoring's parts never come out at more than the restoring takes (restoreBound).
 * <p>
 * Fixed (the coordinator's): the class, the names, the signatures. Bodies and TODO(balance) - ОМ-6's.
 */
public final class SalvageSC {

    /** The share of the components a thing gives back. TODO(balance) */
    public static final float SHARE_MIN = 0.25F, SHARE_MAX = 0.35F;
    /** EU off the station's buffer for one salvage. TODO(balance) */
    public static final int STATION_EU = 5000;
    /** refusal()'s answers. */
    public static final int OK = 0, R_NOTHING = 1, R_SINGULAR = 2;
    /** The refusal's line by refusal() (OK - none). */
    public static final String[] REFUSE_KEYS = {null, "sc.salvage.refuse.nothing", "sc.salvage.refuse.singular"};
    /** The armour chips' tiers (ItemArmorChipSC's I..III). */
    private static final int CHIP_TIERS = 3;

    private SalvageSC() {
    }

    /** Pure: may `s` be salvaged - OK, R_NOTHING (not a thing of the list), R_SINGULAR (a Singular trophy / piece, the ПЭ-1М). */
    public static int refusal(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return R_NOTHING;
        }
        Item it = s.getItem();
        if (MobItemsSC.trophy != null && it == MobItemsSC.trophy) {
            int why = TrophyRestoreSC.refusal(s.getItemDamage(), TrophyRestoreSC.WHERE_STATION);
            return why == TrophyRestoreSC.OK ? OK : why == TrophyRestoreSC.R_SINGULAR_ONLY ? R_SINGULAR : R_NOTHING;
        }
        if (it instanceof ItemArmorSC) {
            ArmorSuit suit = ((ItemArmorSC) it).getSuit();
            return suit == ArmorSuit.SINGULAR ? R_SINGULAR : suit == null ? R_NOTHING : OK;
        }
        if (it instanceof ItemBladeSC) {
            return ((ItemBladeSC) it).getType() == com.sc.util.BladeType.SINGULAR ? R_SINGULAR : OK;
        }
        if (it instanceof ItemDrillSC) {
            return ((ItemDrillSC) it).getType() == com.sc.util.DrillType.SINGULAR ? R_SINGULAR : OK;
        }
        if (it instanceof ItemWeaponSC) {
            return OK;
        }
        if (com.sc.emp.ItemEmpGunSC.isGun(s)) {
            return s.getItemDamage() == com.sc.emp.ItemEmpGunSC.META_BASIC ? OK : R_SINGULAR;   // the ПЭ-1М: the Singular station's
        }
        return R_NOTHING;
    }

    /** Pure: what `s` gives back (new stacks; empty when refused) - the components, then its own chips and modules. */
    public static List<ItemStack> result(ItemStack s) {
        if (refusal(s) != OK) {
            return Collections.emptyList();
        }
        boolean trophy = s.getItem() == MobItemsSC.trophy;
        ItemStack real = trophy ? ItemTrophySC.realStack(s.getItemDamage()) : new ItemStack(s.getItem(), 1, s.getItemDamage());
        if (real == null) {
            return Collections.emptyList();
        }
        List<ItemStack> out = parts(real);
        if (trophy) {
            restoreCap(s.getItemDamage(), out);
        } else {
            out.addAll(installed(s));
        }
        return out;
    }

    /** Pure: the share `item` gives back - SHARE_MIN..SHARE_MAX by the item's name (the same thing always the same). */
    public static float share(ItemStack item) {
        int h = item == null || item.getItem() == null ? 0 : (item.getItem().getUnlocalizedName() + "#" + item.getItemDamage()).hashCode();
        int k = (h & 0x7FFFFFFF) % 1001;
        return SHARE_MIN + (SHARE_MAX - SHARE_MIN) * k / 1000F;
    }

    /** Pure: `count` of a side at `share`, rounded down. */
    public static int shareOf(int count, float share) {
        return Math.max(0, (int) Math.floor(count * share + 1e-6));
    }

    /**
     * The crafting components `real` gives back (new stacks, merged by item): its recipe's sides x share(real), rounded down, at least
     * one of the dearest; the salvageable things among them left out. Empty - no recipe.
     */
    public static List<ItemStack> parts(ItemStack real) {
        List<ItemStack> sides = merged(inputsOf(real));
        List<ItemStack> out = new ArrayList<ItemStack>();
        float sh = share(real);
        ItemStack dearest = null;
        int best = -1;
        for (ItemStack side : sides) {
            if (refusal(side) != R_NOTHING) {
                continue;                                       // the piece / blade / drill / gun before it: not given back
            }
            int n = shareOf(side.stackSize, sh);
            if (n > 0) {
                ItemStack c = side.copy();
                c.stackSize = n;
                out.add(c);
            }
            int w = worth(side);
            if (w > best) {
                best = w;
                dearest = side;
            }
        }
        if (dearest != null && countOf(out, dearest) == 0) {
            ItemStack c = dearest.copy();
            c.stackSize = 1;
            out.add(c);
        }
        return out;
    }

    /** "How dear": 1 + the item count of its own first recipe (a raw thing - 1). */
    public static int worth(ItemStack s) {
        int n = 1;
        for (ItemStack i : inputsOf(s)) {
            n += i.stackSize;
        }
        return n;
    }

    /** The armour chips (ChipsSC) and the ПЭ-1 modules installed in one's own `s` - whole (read only). */
    public static List<ItemStack> installed(ItemStack s) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        NBTTagCompound root = s == null ? null : s.getTagCompound();
        if (root == null) {
            return out;
        }
        if (s.getItem() instanceof ItemArmorSC && com.sc.init.ModItems.armorChip != null && root.hasKey("ChipsSC")) {
            NBTTagCompound chips = root.getCompoundTag("ChipsSC");
            for (com.sc.util.ChipType t : com.sc.util.ChipType.values()) {
                if (chips.hasKey(t.name())) {
                    int tier = Math.max(1, Math.min(CHIP_TIERS, chips.getInteger(t.name())));
                    out.add(com.sc.init.ModItems.armorChip.stackOf(t, tier));
                }
            }
        }
        if (com.sc.emp.ItemEmpGunSC.isGun(s) && com.sc.init.ModItems.empModule != null) {
            for (com.sc.emp.EmpModule m : com.sc.emp.EmpModule.values()) {
                if (com.sc.emp.ItemEmpGunSC.hasModule(s, m)) {
                    out.add(new ItemStack(com.sc.init.ModItems.empModule, 1, m.ordinal()));
                }
            }
        }
        return out;
    }

    /** ОМ-6: a trophy's sides that its restoring takes (TrophyRestoreSC.parts) cut to what the restoring takes. */
    private static void restoreCap(int meta, List<ItemStack> out) {
        TradeOfferSC.Ingredient[] parts = TrophyRestoreSC.parts(TrophyRestoreSC.group(meta), TrophyRestoreSC.tier(meta));
        if (parts == null) {
            return;
        }
        for (TradeOfferSC.Ingredient p : parts) {
            int room = p.count;
            for (ItemStack o : out) {
                if (p.matches(o)) {
                    int n = Math.min(o.stackSize, room);
                    room -= n;
                    o.stackSize = n;
                }
            }
        }
        for (int i = out.size() - 1; i >= 0; i--) {
            if (out.get(i).stackSize <= 0) {
                out.remove(i);
            }
        }
    }

    /**
     * Pure (the self-test, every trophy meta): `out` is no better than restoring trophy `meta` - each restoring part's side at most what
     * restoring takes, and all of it at most the parts and the Armourer's chips together. True for a meta that isn't restored.
     */
    public static boolean restoreBound(int meta, List<ItemStack> out) {
        int g = TrophyRestoreSC.group(meta), t = TrophyRestoreSC.tier(meta);
        TradeOfferSC.Ingredient[] parts = TrophyRestoreSC.parts(g, t);
        if (parts == null) {
            return true;
        }
        int total = 0, price = Math.max(0, TrophyRestoreSC.armourerChips(g, t));
        for (ItemStack o : out) {
            total += o.stackSize;
        }
        for (TradeOfferSC.Ingredient p : parts) {
            int have = 0;
            for (ItemStack o : out) {
                have += p.matches(o) ? o.stackSize : 0;
            }
            if (have > p.count) {
                return false;
            }
            price += p.count;
        }
        return total <= price;
    }

    // ------------------------------------------------------------------ the recipe

    /** The inputs of `out`'s first recipe (crafting, else a machine's) - new stacks, one per input (the machines' with their counts). */
    @SuppressWarnings("rawtypes")
    public static List<ItemStack> inputsOf(ItemStack out) {
        List<ItemStack> l = new ArrayList<ItemStack>();
        if (out == null || out.getItem() == null) {
            return l;
        }
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            if (!makes(r.getRecipeOutput(), out)) {
                continue;
            }
            Object[] in = null;
            if (r instanceof ShapedOreRecipe) {
                in = ((ShapedOreRecipe) r).getInput();
            } else if (r instanceof ShapelessOreRecipe) {
                in = ((ShapelessOreRecipe) r).getInput().toArray();
            } else if (r instanceof ShapedRecipes) {
                in = ((ShapedRecipes) r).recipeItems;
            } else if (r instanceof ShapelessRecipes) {
                in = ((ShapelessRecipes) r).recipeItems.toArray();
            }
            if (in == null) {
                continue;
            }
            for (Object x : in) {
                ItemStack s = x instanceof ItemStack ? (ItemStack) x
                        : x instanceof List && !((List) x).isEmpty() && ((List) x).get(0) instanceof ItemStack ? (ItemStack) ((List) x).get(0) : null;
                if (s != null && s.getItem() != null) {
                    ItemStack c = new ItemStack(s.getItem(), 1, s.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0 : s.getItemDamage());
                    l.add(c);
                }
            }
            if (!l.isEmpty()) {
                return l;
            }
        }
        for (com.sc.machine.MachineType type : com.sc.machine.MachineType.values()) {
            for (com.sc.machine.MachineRecipe r : com.sc.machine.RecipeRegistry.recipesFor(type)) {
                if (r.outputs == null || r.outputs.length == 0 || !makes(r.outputs[0], out) || r.inputs == null) {
                    continue;
                }
                for (ItemStack s : r.inputs) {
                    if (s != null && s.getItem() != null) {
                        l.add(new ItemStack(s.getItem(), Math.max(1, s.stackSize), s.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0 : s.getItemDamage()));
                    }
                }
                if (!l.isEmpty()) {
                    return l;
                }
            }
        }
        return l;
    }

    private static boolean makes(ItemStack made, ItemStack want) {
        return made != null && made.getItem() == want.getItem()
                && (made.getItemDamage() == want.getItemDamage() || made.getItemDamage() == OreDictionary.WILDCARD_VALUE);
    }

    /** The same items merged into one stack each (counts added), in their first order. */
    private static List<ItemStack> merged(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ItemStack s : in) {
            ItemStack same = null;
            for (ItemStack o : out) {
                if (o.getItem() == s.getItem() && o.getItemDamage() == s.getItemDamage()) {
                    same = o;
                    break;
                }
            }
            if (same == null) {
                out.add(s.copy());
            } else {
                same.stackSize += s.stackSize;
            }
        }
        return out;
    }

    private static int countOf(List<ItemStack> l, ItemStack like) {
        int n = 0;
        for (ItemStack o : l) {
            n += o.getItem() == like.getItem() && o.getItemDamage() == like.getItemDamage() ? o.stackSize : 0;
        }
        return n;
    }
}
