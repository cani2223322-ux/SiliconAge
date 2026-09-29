package com.sc.energy;

/**
 * The arithmetic of one cable network's tick, kept free of the world so the self-test can check
 * it. Energy is shared out evenly between consumers (Ender IO style - not "nearest first"): each
 * gets an equal slice of what the network can carry this tick, a consumer that needs less than
 * its slice is filled and the rest is shared again among the others. Delivering to a consumer
 * costs its line loss on top (lossPerBlock x cable length from the supply), so a slice that
 * would lose more than half of itself on the way goes to the others instead.
 */
public final class EnergySplitSC {

    private EnergySplitSC() {
    }

    /**
     * @param budget EU the network can move this tick (supply, capped by the cable rating), loss included
     * @param demand EU each consumer can take
     * @param loss   EU lost on the way to each consumer (paid once per tick if it gets anything)
     * @return EU delivered to each consumer; the network spends delivered + loss for each nonzero one
     */
    public static int[] split(int budget, int[] demand, int[] loss) {
        int n = demand.length;
        int[] out = new int[n];
        boolean[] active = new boolean[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (demand[i] > 0) {
                active[i] = true;
                count++;
            }
        }
        int remaining = Math.max(0, budget);
        while (count > 0 && remaining > 0) {
            int share = remaining / count;
            boolean changed = false;
            // consumers whose whole need fits in a slice are filled first
            for (int i = 0; i < n; i++) {
                if (active[i] && (long) demand[i] + loss[i] <= share) {
                    out[i] = demand[i];
                    remaining -= demand[i] + loss[i];
                    active[i] = false;
                    count--;
                    changed = true;
                }
            }
            if (changed) {
                continue;
            }
            if (share == 0) {
                // fewer EU than consumers: what there is goes to the nearest one rather than to nobody
                int nearest = -1;
                for (int i = 0; i < n; i++) {
                    if (active[i] && (nearest < 0 || loss[i] < loss[nearest])) {
                        nearest = i;
                    }
                }
                if (remaining > loss[nearest]) {
                    out[nearest] = Math.min(demand[nearest], remaining - loss[nearest]);
                    remaining -= out[nearest] + loss[nearest];
                }
                break;
            }
            // a slice that would lose more than half of itself on the way is better spent elsewhere:
            // the farthest such consumer steps out and the slices are recut for the rest
            int farthest = -1;
            for (int i = 0; i < n; i++) {
                if (active[i] && 2L * loss[i] > share && (farthest < 0 || loss[i] > loss[farthest])) {
                    farthest = i;
                }
            }
            if (farthest >= 0 && count > 1) {     // the last one still gets what gets through
                active[farthest] = false;
                count--;
                continue;
            }
            for (int i = 0; i < n; i++) {
                if (active[i] && share > loss[i]) {
                    out[i] = share - loss[i];
                    remaining -= share;
                }
            }
            // the few EU the division left over, one each
            for (int i = 0; i < n && remaining > 0; i++) {
                if (active[i] && out[i] > 0 && out[i] < demand[i]) {
                    out[i]++;
                    remaining--;
                }
            }
            break;
        }
        return out;
    }

    /** Takes `total` EU from suppliers in proportion to what each offers. @return EU taken from each */
    public static int[] draw(int total, int[] offer) {
        int n = offer.length;
        int[] taken = new int[n];
        long supply = 0;
        for (int o : offer) {
            supply += Math.max(0, o);
        }
        if (supply <= 0 || total <= 0) {
            return taken;
        }
        int left = (int) Math.min(total, supply);
        int want = left;
        for (int i = 0; i < n; i++) {
            taken[i] = (int) ((long) want * Math.max(0, offer[i]) / supply);
            left -= taken[i];
        }
        for (int i = 0; i < n && left > 0; i++) {
            int more = Math.min(left, Math.max(0, offer[i]) - taken[i]);
            taken[i] += more;
            left -= more;
        }
        return taken;
    }
}
