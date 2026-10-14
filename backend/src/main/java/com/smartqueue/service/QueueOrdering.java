package com.smartqueue.service;

import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.QueueToken;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure (database-free) ordering rules, shared by NEXT, the "people ahead" calculation and the staff
 * waiting list so that they can never disagree.
 */
public final class QueueOrdering {

    public enum Pick {PRIORITY, NORMAL}

    private QueueOrdering() {
    }

    /**
     * Decides whether the next served token comes from the priority or the normal line.
     *
     * @param streak       normal tokens served since the last priority token
     * @param priorityHead queue order of the first waiting priority token, or null if none
     * @param normalHead   queue order of the first waiting normal token, or null if none
     */
    public static Pick choose(PriorityMode mode, int afterN, int streak, Long priorityHead, Long normalHead) {
        if (priorityHead == null) {
            return Pick.NORMAL;
        }
        if (normalHead == null) {
            return Pick.PRIORITY;
        }
        return switch (mode) {
            case DISABLED -> priorityHead < normalHead ? Pick.PRIORITY : Pick.NORMAL;
            case ALWAYS_FIRST -> Pick.PRIORITY;
            case AFTER_N -> streak >= afterN ? Pick.PRIORITY : Pick.NORMAL;
        };
    }

    public static int nextStreak(Pick picked, int afterN, int streak) {
        return picked == Pick.PRIORITY ? 0 : Math.min(streak + 1, afterN);
    }

    /** Orders waiting tokens exactly as NEXT will serve them. Input must be sorted by queueOrder. */
    public static List<QueueToken> servingOrder(List<QueueToken> waitingByOrder, PriorityMode mode,
                                                int afterN, int streak) {
        List<QueueToken> priority = new ArrayList<>();
        List<QueueToken> normal = new ArrayList<>();
        for (QueueToken t : waitingByOrder) {
            (t.isPriority() ? priority : normal).add(t);
        }
        List<QueueToken> out = new ArrayList<>(waitingByOrder.size());
        int pi = 0;
        int ni = 0;
        int s = streak;
        while (pi < priority.size() || ni < normal.size()) {
            Long pHead = pi < priority.size() ? priority.get(pi).getQueueOrder() : null;
            Long nHead = ni < normal.size() ? normal.get(ni).getQueueOrder() : null;
            Pick pick = choose(mode, afterN, s, pHead, nHead);
            out.add(pick == Pick.PRIORITY ? priority.get(pi++) : normal.get(ni++));
            s = nextStreak(pick, afterN, s);
        }
        return out;
    }

    /**
     * Number of waiting tokens that will be served before the given waiting token.
     *
     * @param priorityWaiting total waiting priority tokens
     * @param normalWaiting   total waiting normal tokens
     * @param priorityBefore  waiting priority tokens with a lower queue order than this token
     * @param normalBefore    waiting normal tokens with a lower queue order than this token
     * @param anyBefore       all waiting tokens (any class) with a lower queue order (DISABLED mode)
     */
    public static long peopleAhead(PriorityMode mode, int afterN, int streak, boolean tokenIsPriority,
                                   long priorityWaiting, long normalWaiting,
                                   long priorityBefore, long normalBefore, long anyBefore) {
        return switch (mode) {
            case DISABLED -> anyBefore;
            case ALWAYS_FIRST -> tokenIsPriority ? priorityBefore : priorityWaiting + normalBefore;
            case AFTER_N -> {
                if (tokenIsPriority) {
                    long normalsServedFirst = Math.min(normalWaiting,
                            Math.max(0, afterN - streak) + priorityBefore * afterN);
                    yield priorityBefore + normalsServedFirst;
                }
                long priorityServedFirst = Math.min(priorityWaiting, (streak + normalBefore) / afterN);
                yield normalBefore + priorityServedFirst;
            }
        };
    }
}
