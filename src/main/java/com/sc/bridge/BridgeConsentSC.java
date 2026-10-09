package com.sc.bridge;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Consent (docs/plan-ground-bridge.md §10): «Забрать друга» and any end next to another player open only when
 * that player agrees. A request lives BridgeMathSC.CONSENT_TICKS (30 s); the asked player answers once
 * ([Принять] / [Отклонить] in the chat run «/scbridge accept|decline id»); an accepted request is used once -
 * the bridge then opens with that player's consent. Pure (the time is passed in), one instance per server
 * (`server()`), so the self-test runs the state machine on its own instance.
 */
public final class BridgeConsentSC {

    public static final int PENDING = 0, ACCEPTED = 1, DECLINED = 2, EXPIRED = 3, USED = 4;
    /** answer(): not found / not yours / no longer pending. */
    public static final int NOT_FOUND = -1, NOT_YOURS = -2, CLOSED = -3;
    /** МС-3: after «Отклонить», requests from the same player to the same player are refused this long (ticks). */
    public static final int DECLINE_BLOCK_TICKS = 5 * 60 * 20;

    public static final class Request {
        public final int id;
        public final String from, to;
        public final long created;
        public int state = PENDING;
        /** What to open on «Принять» (the controller's order and where the controller is). */
        public final NBTTagCompound order;

        Request(int id, String from, String to, long created, NBTTagCompound order) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.created = created;
            this.order = order == null ? new NBTTagCompound() : order;
        }
    }

    private final Map<Integer, Request> map = new LinkedHashMap<Integer, Request>();
    /** МС-3: "from>to" (lower case) - the tick until which `from` may not ask `to` again. */
    private final Map<String, Long> declined = new java.util.HashMap<String, Long>();
    private int nextId = 1;
    private final int ttl;

    public BridgeConsentSC(int ttlTicks) {
        ttl = ttlTicks;
    }

    private static BridgeConsentSC server = new BridgeConsentSC(BridgeMathSC.CONSENT_TICKS);

    /** The server's requests. */
    public static BridgeConsentSC server() {
        return server;
    }

    /** The server stopped: everything forgotten. */
    public static void reset() {
        server = new BridgeConsentSC(BridgeMathSC.CONSENT_TICKS);
    }

    /** A new request from `from` to `to` (an earlier pending one between them is replaced). */
    public Request ask(String from, String to, long now, NBTTagCompound order) {
        expire(now);
        for (Request r : map.values()) {
            if (r.state == PENDING && r.from.equalsIgnoreCase(from) && r.to.equalsIgnoreCase(to)) {
                r.state = EXPIRED;
            }
        }
        Request r = new Request(nextId++, from, to, now, order);
        map.put(r.id, r);
        return r;
    }

    /** Marks requests older than the lifetime expired, forgets old closed ones. */
    public void expire(long now) {
        for (Iterator<Request> it = map.values().iterator(); it.hasNext(); ) {
            Request r = it.next();
            if (r.state == PENDING && now - r.created > ttl) {
                r.state = EXPIRED;
            }
            if (r.state != PENDING && now - r.created > ttl * 4L) {
                it.remove();
            }
        }
        for (Iterator<Long> it = declined.values().iterator(); it.hasNext(); ) {
            long u = it.next();
            if (now >= u || now < u - DECLINE_BLOCK_TICKS) {
                it.remove();                        // over, or the world's clock went back
            }
        }
    }

    private static String pair(String from, String to) {
        return from.toLowerCase(java.util.Locale.ROOT) + ">" + to.toLowerCase(java.util.Locale.ROOT);
    }

    /** МС-3: ticks left before `from` may ask `to` again after a decline (0 - may ask now). */
    public long blockedLeft(String from, String to, long now) {
        String k = pair(from, to);
        Long until = declined.get(k);
        if (until == null) {
            return 0;
        }
        if (now >= until || now < until - DECLINE_BLOCK_TICKS) {
            declined.remove(k);
            return 0;
        }
        return until - now;
    }

    /**
     * `who` answers request `id`. @return the new state (ACCEPTED / DECLINED), or NOT_FOUND / NOT_YOURS /
     * CLOSED (expired, used, answered already)
     */
    public int answer(int id, String who, boolean accept, long now) {
        expire(now);
        Request r = map.get(id);
        if (r == null) {
            return NOT_FOUND;
        }
        if (who == null || !r.to.equalsIgnoreCase(who)) {
            return NOT_YOURS;
        }
        if (r.state != PENDING) {
            return CLOSED;
        }
        r.state = accept ? ACCEPTED : DECLINED;
        if (!accept) {
            declined.put(pair(r.from, r.to), now + DECLINE_BLOCK_TICKS);
        }
        return r.state;
    }

    /** An accepted request is taken (the bridge opens with it): USED afterwards. @return whether it was accepted and unused */
    public boolean use(int id) {
        Request r = map.get(id);
        if (r == null || r.state != ACCEPTED) {
            return false;
        }
        r.state = USED;
        return true;
    }

    public Request get(int id) {
        return map.get(id);
    }

    public int state(int id) {
        Request r = map.get(id);
        return r == null ? NOT_FOUND : r.state;
    }

    public int size() {
        return map.size();
    }
}
