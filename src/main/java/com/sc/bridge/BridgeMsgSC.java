package com.sc.bridge;

import java.util.ArrayList;
import java.util.List;

import com.sc.manual.Lang;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * A bridge message the server builds and every client reads in its own language: a lang key with %s
 * arguments and, optionally, a list of parts (what is missing...) shown after it, comma-separated. Goes
 * to the chat (nested translations), into the journal (NBT) and onto the screen.
 */
public final class BridgeMsgSC {

    /** An argument "@x" is itself translated: the lang key RES + "x" (a resource name, a reason). */
    public static final String RES = "sc.bridge.res.";

    public final String key;
    public final String[] args;
    public final List<BridgeMsgSC> parts = new ArrayList<BridgeMsgSC>();

    public BridgeMsgSC(String key, Object... args) {
        this.key = key;
        this.args = new String[args == null ? 0 : args.length];
        for (int i = 0; i < this.args.length; i++) {
            this.args[i] = String.valueOf(args[i]);
        }
    }

    public BridgeMsgSC part(BridgeMsgSC p) {
        if (p != null) {
            parts.add(p);
        }
        return this;
    }

    public BridgeMsgSC part(String key, Object... args) {
        return part(new BridgeMsgSC(key, args));
    }

    /** The text in this client's language. */
    public String text() {
        Object[] a = new Object[args.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = args[i].startsWith("@") ? Lang.tr(RES + args[i].substring(1)) : args[i];
        }
        String s = a.length == 0 ? Lang.tr(key) : Lang.tr(key, a);
        if (!parts.isEmpty()) {
            StringBuilder b = new StringBuilder(s).append(": ");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(parts.get(i).text());
            }
            s = b.toString();
        }
        return s;
    }

    /** For the chat: translated by the receiving client. */
    public IChatComponent chat() {
        Object[] a = new Object[args.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = args[i].startsWith("@") ? new ChatComponentTranslation(RES + args[i].substring(1)) : args[i];
        }
        ChatComponentTranslation main = new ChatComponentTranslation(key, a);
        if (parts.isEmpty()) {
            return main;
        }
        IChatComponent all = new ChatComponentText("");
        all.appendSibling(main);
        all.appendSibling(new ChatComponentText(": "));
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                all.appendSibling(new ChatComponentText(", "));
            }
            all.appendSibling(parts.get(i).chat());
        }
        return all;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("k", key);
        NBTTagList a = new NBTTagList();
        for (String s : args) {
            a.appendTag(new NBTTagString(s));
        }
        t.setTag("a", a);
        if (!parts.isEmpty()) {
            NBTTagList p = new NBTTagList();
            for (BridgeMsgSC m : parts) {
                p.appendTag(m.write());
            }
            t.setTag("p", p);
        }
        return t;
    }

    public static BridgeMsgSC read(NBTTagCompound t) {
        NBTTagList a = t.getTagList("a", 8);
        Object[] args = new Object[a.tagCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = a.getStringTagAt(i);
        }
        BridgeMsgSC m = new BridgeMsgSC(t.getString("k"), args);
        NBTTagList p = t.getTagList("p", 10);
        for (int i = 0; i < p.tagCount(); i++) {
            m.parts.add(read(p.getCompoundTagAt(i)));
        }
        return m;
    }
}
