package com.smartqueue.service;

import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.QueueToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueueOrderingTest {

    private static QueueToken token(String number, boolean priority, long order) {
        QueueToken t = new QueueToken();
        t.setTokenNumber(number);
        t.setPriority(priority);
        t.setQueueOrder(order);
        return t;
    }

    private static List<String> numbers(List<QueueToken> list) {
        return list.stream().map(QueueToken::getTokenNumber).toList();
    }

    @Test
    void disabledModeServesStrictlyByArrival() {
        var out = QueueOrdering.servingOrder(
                List.of(token("A1", false, 1), token("P1", true, 2), token("A2", false, 3)),
                PriorityMode.DISABLED, 3, 0);
        assertThat(numbers(out)).containsExactly("A1", "P1", "A2");
    }

    @Test
    void alwaysFirstServesPriorityBeforeAnyNormal() {
        var out = QueueOrdering.servingOrder(
                List.of(token("A1", false, 1), token("A2", false, 2), token("P1", true, 3), token("P2", true, 4)),
                PriorityMode.ALWAYS_FIRST, 3, 0);
        assertThat(numbers(out)).containsExactly("P1", "P2", "A1", "A2");
    }

    @Test
    void afterNServesOnePriorityEveryNNormals() {
        List<QueueToken> waiting = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            waiting.add(token("A" + i, false, i));
        }
        waiting.add(token("P1", true, 8));
        waiting.add(token("P2", true, 9));
        var out = QueueOrdering.servingOrder(waiting, PriorityMode.AFTER_N, 3, 0);
        assertThat(numbers(out)).containsExactly("A1", "A2", "A3", "P1", "A4", "A5", "A6", "P2", "A7");
    }

    @Test
    void afterNHonoursStreakAlreadyServed() {
        var out = QueueOrdering.servingOrder(
                List.of(token("A1", false, 1), token("P1", true, 2)), PriorityMode.AFTER_N, 3, 3);
        assertThat(numbers(out)).containsExactly("P1", "A1");
    }

    @Test
    void emptyPriorityLineAlwaysPicksNormal() {
        assertThat(QueueOrdering.choose(PriorityMode.ALWAYS_FIRST, 3, 0, null, 5L))
                .isEqualTo(QueueOrdering.Pick.NORMAL);
    }

    @Test
    void peopleAheadMatchesActualServingOrder() {
        for (PriorityMode mode : PriorityMode.values()) {
            for (int streak = 0; streak <= 3; streak++) {
                List<QueueToken> waiting = new ArrayList<>();
                long order = 1;
                // interleaved arrivals: N N P N N N P N P N
                boolean[] pattern = {false, false, true, false, false, false, true, false, true, false};
                for (boolean p : pattern) {
                    waiting.add(token((p ? "P" : "A") + order, p, order));
                    order++;
                }
                var served = QueueOrdering.servingOrder(waiting, mode, 3, streak);
                long priorityWaiting = waiting.stream().filter(QueueToken::isPriority).count();
                long normalWaiting = waiting.size() - priorityWaiting;
                for (QueueToken t : waiting) {
                    long pBefore = waiting.stream().filter(o -> o.isPriority() && o.getQueueOrder() < t.getQueueOrder()).count();
                    long nBefore = waiting.stream().filter(o -> !o.isPriority() && o.getQueueOrder() < t.getQueueOrder()).count();
                    long anyBefore = waiting.stream().filter(o -> o.getQueueOrder() < t.getQueueOrder()).count();
                    long computed = QueueOrdering.peopleAhead(mode, 3, streak, t.isPriority(),
                            priorityWaiting, normalWaiting, pBefore, nBefore, anyBefore);
                    assertThat(computed)
                            .as("mode=%s streak=%d token=%s", mode, streak, t.getTokenNumber())
                            .isEqualTo(served.indexOf(t));
                }
            }
        }
    }
}
