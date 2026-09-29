package com.sc.manual;

import java.util.ArrayList;
import java.util.List;

import com.sc.util.OreEntry;

import net.minecraft.item.ItemStack;

/**
 * One element of a handbook page - plain data (safe on a dedicated server, the self-test builds
 * every page there); GuiBook lays it out and draws it. Which fields mean something depends on
 * the kind (see the factories).
 */
public final class BookEl {

    public enum Kind { TITLE, HEAD, PARA, ITEM, ITEMS, CRAFT, MACHINE, CHAIN, ORE, TABLE, LAYERS, IMAGE, CHECK, LINK, GAP }

    public final Kind kind;
    /** Heading / paragraph / label text; ITEM's and CHECK's line. */
    public String text;
    /** ITEM: the dim line under the name. */
    public String sub;
    /** ITEM / CHECK / HEAD: the icon. */
    public ItemStack stack;
    /** ITEMS: the icons; CHAIN: the items along the route. */
    public ItemStack[] stacks;
    /** CHAIN: the machine above each arrow (index i: between stacks[i] and stacks[i + 1]). */
    public ItemStack[] between;
    /** CRAFT: an IRecipe; MACHINE: a MachineRecipe. */
    public Object recipe;
    public OreEntry ore;
    /** TABLE: the header, the rows, an icon per row (or null). */
    public String[] header;
    public final List<String[]> rows = new ArrayList<String[]>();
    public final List<ItemStack> rowIcons = new ArrayList<ItemStack>();
    /** LAYERS: layers[layer][z][x] (null - air), a caption per layer. */
    public ItemStack[][][] layers;
    public String[] layerNames;
    /** IMAGE: the texture under textures/gui/book/, its size in texture pixels. */
    public String image;
    public int imgW, imgH;
    /** LINK: the target entry; CHECK: the step's id. */
    public String target;
    /** A colour for TITLE / HEAD / PARA text (0 - the default). */
    public int color;

    private BookEl(Kind kind) {
        this.kind = kind;
    }

    public static BookEl title(String text) {
        BookEl e = new BookEl(Kind.TITLE);
        e.text = text;
        return e;
    }

    public static BookEl head(String text) {
        BookEl e = new BookEl(Kind.HEAD);
        e.text = text;
        return e;
    }

    public static BookEl head(String text, ItemStack icon) {
        BookEl e = head(text);
        e.stack = icon;
        return e;
    }

    public static BookEl warn(String text) {
        BookEl e = head(text);
        e.color = 0xA01818;
        return e;
    }

    public static BookEl para(String text) {
        BookEl e = new BookEl(Kind.PARA);
        e.text = text;
        return e;
    }

    public static BookEl dim(String text) {
        BookEl e = para(text);
        e.color = 0x6A6258;
        return e;
    }

    /** An icon, a name and a dim line - clicking it opens the item's page. */
    public static BookEl item(ItemStack stack, String name, String sub) {
        BookEl e = new BookEl(Kind.ITEM);
        e.stack = stack;
        e.text = name;
        e.sub = sub;
        return e;
    }

    public static BookEl items(List<ItemStack> stacks) {
        BookEl e = new BookEl(Kind.ITEMS);
        e.stacks = stacks.toArray(new ItemStack[stacks.size()]);
        return e;
    }

    public static BookEl craft(Object recipe) {
        BookEl e = new BookEl(Kind.CRAFT);
        e.recipe = recipe;
        return e;
    }

    public static BookEl machine(com.sc.machine.MachineRecipe recipe) {
        BookEl e = new BookEl(Kind.MACHINE);
        e.recipe = recipe;
        return e;
    }

    public static BookEl chain(List<ItemStack> items, List<ItemStack> machines) {
        BookEl e = new BookEl(Kind.CHAIN);
        e.stacks = items.toArray(new ItemStack[items.size()]);
        e.between = machines.toArray(new ItemStack[machines.size()]);
        return e;
    }

    public static BookEl ore(OreEntry ore, ItemStack block) {
        BookEl e = new BookEl(Kind.ORE);
        e.ore = ore;
        e.stack = block;
        return e;
    }

    public static BookEl table(String... header) {
        BookEl e = new BookEl(Kind.TABLE);
        e.header = header;
        return e;
    }

    public BookEl row(ItemStack icon, String... cells) {
        rows.add(cells);
        rowIcons.add(icon);
        return this;
    }

    public static BookEl layers(ItemStack[][][] layers, String[] names) {
        BookEl e = new BookEl(Kind.LAYERS);
        e.layers = layers;
        e.layerNames = names;
        return e;
    }

    public static BookEl image(String name, int w, int h) {
        BookEl e = new BookEl(Kind.IMAGE);
        e.image = name;
        e.imgW = w;
        e.imgH = h;
        return e;
    }

    public static BookEl check(String step, ItemStack icon, String text) {
        BookEl e = new BookEl(Kind.CHECK);
        e.target = step;
        e.stack = icon;
        e.text = text;
        return e;
    }

    public static BookEl link(String target, String text) {
        BookEl e = new BookEl(Kind.LINK);
        e.target = target;
        e.text = text;
        return e;
    }

    public static BookEl gap() {
        return new BookEl(Kind.GAP);
    }

    /** Every word on the element, lower-case - what the search looks through. */
    public void collectText(StringBuilder sb) {
        if (text != null) {
            sb.append(text).append('\n');
        }
        if (sub != null) {
            sb.append(sub).append('\n');
        }
        if (header != null) {
            for (String h : header) {
                sb.append(h).append(' ');
            }
        }
        for (String[] r : rows) {
            for (String c : r) {
                sb.append(c).append(' ');
            }
            sb.append('\n');
        }
        appendNames(sb, stack);
        if (stacks != null) {
            for (ItemStack s : stacks) {
                appendNames(sb, s);
            }
        }
        for (ItemStack s : rowIcons) {
            appendNames(sb, s);
        }
    }

    private static void appendNames(StringBuilder sb, ItemStack s) {
        if (s != null && s.getItem() != null) {
            try {
                sb.append(s.getDisplayName()).append('\n');
            } catch (Throwable t) {
                // an item whose name needs the client - no matter for the search
            }
        }
    }
}
