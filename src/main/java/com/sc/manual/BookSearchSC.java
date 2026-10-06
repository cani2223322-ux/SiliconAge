package com.sc.manual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

/**
 * The handbook's search and bookmark logic - plain data, no client classes (the self-test runs it
 * on a dedicated server). Text is compared folded: no §-codes, lower case, «ё» as «е», whitespace
 * runs as one space; folding keeps the length, so a match's offsets point into the shown text too.
 */
public final class BookSearchSC {

    public static final int MIN_QUERY = 2, MAX_RESULTS = 50, MAX_MARKS = 64;
    /** Between an article's elements in its searchable body. */
    static final String SEP = " · ";

    private BookSearchSC() {
    }

    // ------------------------------------------------------------------ normalisation

    /** Drops §x formatting codes and collapses whitespace runs (newlines too) into one space. */
    public static String plain(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;                                            // the code's letter
                continue;
            }
            if (Character.isWhitespace(c) || c == ' ') {
                space = true;
                continue;
            }
            if (space && sb.length() > 0) {
                sb.append(' ');
            }
            space = false;
            sb.append(c);
        }
        return sb.toString();
    }

    /** Lower case, «ё» as «е» - char by char, so the length never changes. */
    public static String fold(String s) {
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            char c = Character.toLowerCase(a[i]);
            a[i] = c == 'ё' ? 'е' : c;
        }
        return new String(a);
    }

    /** A query as it is compared: plain, folded, trimmed. */
    public static String norm(String s) {
        return fold(plain(s)).trim();
    }

    /** Non-overlapping occurrences of `needle` in `hay`. */
    public static int count(String hay, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ documents

    /** One article as the search sees it. */
    public static final class Doc {
        public final String id, title, chapter;
        /** Plain body (elements joined by SEP) and its folded twin of the same length. */
        public final String body;
        final String titleF, bodyF;
        /** Where each element starts in the body. */
        final int[] elStart;
        final int order;

        /** parts[i] - element i's text (null: an element with no words). */
        public Doc(String id, String title, String chapter, String[] parts, int order) {
            this.id = id;
            this.title = plain(title);
            this.chapter = chapter == null ? "" : plain(chapter);
            this.order = order;
            elStart = new int[parts.length];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String p = plain(parts[i]);
                elStart[i] = sb.length();
                if (!p.isEmpty()) {
                    sb.append(p).append(SEP);
                }
            }
            body = sb.length() >= SEP.length() ? sb.substring(0, sb.length() - SEP.length()) : sb.toString();
            titleF = fold(this.title);
            bodyF = fold(body);
        }

        /** The element holding body offset `at` (-1: none). */
        public int elementAt(int at) {
            if (at < 0) {
                return -1;
            }
            int found = -1;
            for (int i = 0; i < elStart.length; i++) {
                if (elStart[i] <= at) {
                    found = i;
                } else {
                    break;
                }
            }
            return found;
        }
    }

    /** One result: its rank class, the number of matches, the first body match (offset, length) and its element. */
    public static final class Hit {
        public final Doc doc;
        /** 0 - the title starts with the query, 1 - holds it, 2 - the body holds it, 3 - every word somewhere. */
        public final int rank;
        public final int count, at, len, el;

        Hit(Doc doc, int rank, int count, int at, int len) {
            this.doc = doc;
            this.rank = rank;
            this.count = count;
            this.at = at;
            this.len = len;
            this.el = doc.elementAt(at);
        }
    }

    /** Title matches first, then by the number of matches, then in book order. */
    static final Comparator<Hit> RANKING = new Comparator<Hit>() {
        @Override
        public int compare(Hit a, Hit b) {
            if (a.rank != b.rank) {
                return a.rank < b.rank ? -1 : 1;
            }
            if (a.count != b.count) {
                return a.count > b.count ? -1 : 1;
            }
            return a.doc.order < b.doc.order ? -1 : a.doc.order == b.doc.order ? 0 : 1;
        }
    };

    /** The hit for one document, or null. */
    public static Hit match(Doc d, String nq) {
        if (nq.length() < MIN_QUERY) {
            return null;
        }
        int tc = count(d.titleF, nq), bc = count(d.bodyF, nq);
        if (tc + bc > 0) {
            int rank = d.titleF.startsWith(nq) ? 0 : tc > 0 ? 1 : 2;
            return new Hit(d, rank, tc + bc, d.bodyF.indexOf(nq), nq.length());
        }
        String[] words = nq.split(" ");
        if (words.length < 2) {
            return null;
        }
        int sum = 0, first = -1, flen = 0;
        boolean allTitle = true;
        for (String w : words) {
            int t = count(d.titleF, w), b = count(d.bodyF, w);
            if (t + b == 0) {
                return null;
            }
            allTitle &= t > 0;
            sum += t + b;
            int i = d.bodyF.indexOf(w);
            if (i >= 0 && (first < 0 || i < first)) {
                first = i;
                flen = w.length();
            }
        }
        return new Hit(d, allTitle ? 1 : 3, sum, first, flen);
    }

    /** Every hit for `query`, ranked; `total[0]` (if given) gets the count before the limit. */
    public static List<Hit> search(List<Doc> docs, String query, int limit, int[] total) {
        String nq = norm(query);
        List<Hit> hits = new ArrayList<Hit>();
        for (Doc d : docs) {
            Hit h = match(d, nq);
            if (h != null) {
                hits.add(h);
            }
        }
        Collections.sort(hits, RANKING);
        if (total != null && total.length > 0) {
            total[0] = hits.size();
        }
        return hits.size() > limit ? new ArrayList<Hit>(hits.subList(0, limit)) : hits;
    }

    // ------------------------------------------------------------------ snippets

    /** A piece of text around a match: the text ("..." where cut) and the match inside it. */
    public static final class Snip {
        public final String text;
        public final int hl, hlLen;

        Snip(String text, int hl, int hlLen) {
            this.text = text;
            this.hl = hl;
            this.hlLen = hlLen;
        }
    }

    /**
     * Up to `before` chars before the match and `after` after it, cut at a space where one is near;
     * no match (at < 0): the text's start, nothing highlighted.
     */
    public static Snip snippet(String text, int at, int len, int before, int after) {
        if (text == null || text.isEmpty()) {
            return new Snip("", 0, 0);
        }
        if (at < 0 || at >= text.length()) {
            at = 0;
            len = 0;
        }
        len = Math.max(0, Math.min(len, text.length() - at));
        int s = Math.max(0, at - Math.max(0, before)), e = Math.min(text.length(), at + len + Math.max(0, after));
        if (s > 0) {
            int sp = text.indexOf(' ', s);
            if (sp >= 0 && sp < at) {
                s = sp + 1;
            }
        }
        if (e < text.length()) {
            int sp = text.lastIndexOf(' ', e);
            if (sp > at + len) {
                e = sp;
            }
        }
        String pre = s > 0 ? "..." : "", post = e < text.length() ? "..." : "";
        return new Snip(pre + text.substring(s, e) + post, pre.length() + at - s, len);
    }

    // ------------------------------------------------------------------ the book's index (lazy, per BookContent build)

    private static List<BookEntry> indexedFrom;
    private static List<Doc> index = Collections.emptyList();
    private static List<BookEntry> indexEntries = Collections.emptyList();

    /** The index of the current book (built again whenever BookContent builds the book again). */
    public static synchronized List<Doc> index() {
        List<BookEntry> all = BookContent.all();
        if (all != indexedFrom) {
            List<Doc> docs = new ArrayList<Doc>();
            List<BookEntry> entries = new ArrayList<BookEntry>();
            for (BookEntry e : all) {
                if (e.chapter == BookChapter.RECIPES) {
                    continue;                                   // the Recipes chapter has its own filter
                }
                docs.add(doc(e, docs.size()));
                entries.add(e);
            }
            index = docs;
            indexEntries = entries;
            indexedFrom = all;
        }
        return index;
    }

    /** The article of a hit. */
    public static synchronized BookEntry entry(Hit h) {
        index();
        int i = index.indexOf(h.doc);
        return i >= 0 ? indexEntries.get(i) : BookContent.byId(h.doc.id);
    }

    /** Searches the book. */
    public static List<Hit> searchBook(String query, int[] total) {
        return search(index(), query, MAX_RESULTS, total);
    }

    static Doc doc(BookEntry e, int order) {
        String[] parts = new String[e.els.size()];
        for (int i = 0; i < parts.length; i++) {
            BookEl el = e.els.get(i);
            if (el.kind == BookEl.Kind.TITLE && el.text != null && plain(el.text).equals(plain(e.title))) {
                continue;                                       // the heading repeating the title
            }
            StringBuilder sb = new StringBuilder();
            el.collectText(sb);
            extraNames(el, sb);
            parts[i] = sb.toString();
        }
        return new Doc(e.id, e.title, e.chapter.title(), parts, order);
    }

    /** Item names a card shows that collectText leaves out (recipes, routes). */
    private static void extraNames(BookEl el, StringBuilder sb) {
        try {
            if (el.between != null) {
                for (ItemStack s : el.between) {
                    name(s, sb);
                }
            }
            if (el.recipe instanceof IRecipe) {
                name(((IRecipe) el.recipe).getRecipeOutput(), sb);
            } else if (el.recipe instanceof com.sc.machine.MachineRecipe) {
                com.sc.machine.MachineRecipe r = (com.sc.machine.MachineRecipe) el.recipe;
                for (ItemStack s : r.inputs) {
                    name(s, sb);
                }
                for (ItemStack s : r.outputs) {
                    name(s, sb);
                }
            }
        } catch (Throwable t) {
            // an odd recipe: its names just aren't searchable
        }
    }

    private static void name(ItemStack s, StringBuilder sb) {
        if (s != null && s.getItem() != null) {
            try {
                sb.append(s.getDisplayName()).append('\n');
            } catch (Throwable t) {
                // a name that needs the client
            }
        }
    }

    // ------------------------------------------------------------------ bookmarks (a list of article ids, in the order added)

    /** Adds `id` at the end; false when it's already there, blank, or the list is full. */
    public static boolean addMark(List<String> marks, String id, int max) {
        if (!validId(id) || marks.contains(id) || marks.size() >= max) {
            return false;
        }
        marks.add(id);
        return true;
    }

    public static boolean removeMark(List<String> marks, String id) {
        return marks.remove(id);
    }

    /** Lines for the file: "bm=<id>". */
    public static List<String> marksToLines(List<String> marks) {
        List<String> out = new ArrayList<String>();
        for (String m : marks) {
            if (validId(m)) {
                out.add("bm=" + m);
            }
        }
        return out;
    }

    /** The bookmarks among a file's lines: once each, in order, at most `max`. */
    public static List<String> marksFromLines(List<String> lines, int max) {
        Set<String> seen = new LinkedHashSet<String>();
        for (String l : lines) {
            if (l != null && l.startsWith("bm=") && seen.size() < max) {
                String id = l.substring(3).trim();
                if (validId(id)) {
                    seen.add(id);
                }
            }
        }
        return new ArrayList<String>(seen);
    }

    private static boolean validId(String id) {
        return id != null && !id.trim().isEmpty() && id.indexOf('\n') < 0 && id.indexOf('\r') < 0 && id.equals(id.trim());
    }
}
