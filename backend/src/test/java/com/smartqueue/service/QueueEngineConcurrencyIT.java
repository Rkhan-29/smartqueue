package com.smartqueue.service;

import com.smartqueue.IntegrationTestBase;
import com.smartqueue.TestData;
import com.smartqueue.dto.QueueDtos.JoinRequest;
import com.smartqueue.dto.QueueDtos.JoinResponse;
import com.smartqueue.dto.QueueDtos.StaffTokenDto;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.QueueTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The queue is concurrency-sensitive: these tests hammer the engine from many threads at once. */
class QueueEngineConcurrencyIT extends IntegrationTestBase {

    @Autowired QueueEngine engine;
    @Autowired TestData data;
    @Autowired QueueTokenRepository tokens;

    @AfterEach
    void clearSecurity() {
        TestData.clear();
    }

    /** Runs all tasks at the same instant and returns their results/exceptions. */
    private <T> List<Object> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void simultaneousJoinsReceiveDistinctGapFreeTokenNumbers() throws Exception {
        var fx = data.createQueue(null);
        int n = 25;
        List<Callable<JoinResponse>> joins = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            joins.add(() -> engine.join(fx.queue().getId(), new JoinRequest(null, false), null));
        }

        List<Object> results = runConcurrently(joins);

        List<String> numbers = results.stream()
                .peek(r -> assertThat(r).as("join must not fail: %s", r).isInstanceOf(JoinResponse.class))
                .map(r -> ((JoinResponse) r).token().tokenNumber())
                .toList();
        assertThat(numbers).doesNotHaveDuplicates().hasSize(n);
        Set<String> expected = new java.util.HashSet<>();
        for (int i = 1; i <= n; i++) {
            expected.add(String.format("A%03d", i));
        }
        assertThat(Set.copyOf(numbers)).isEqualTo(expected);
    }

    @Test
    void sameGuestSessionCannotHoldTwoActiveTokensEvenWhenRacing() throws Exception {
        var fx = data.createQueue(null);
        String session = "S".repeat(24);
        List<Callable<JoinResponse>> joins = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            joins.add(() -> engine.join(fx.queue().getId(), new JoinRequest(null, false), session));
        }

        List<Object> results = runConcurrently(joins);

        long successes = results.stream().filter(r -> r instanceof JoinResponse).count();
        long duplicates = results.stream()
                .filter(r -> r instanceof ApiException e && e.getCode() == ErrorCode.DUPLICATE_ACTIVE_TOKEN).count();
        assertThat(successes).isEqualTo(1);
        assertThat(duplicates).isEqualTo(7);
    }

    @Test
    void twoStaffPressingNextOnOneWaitingTokenServeItOnlyOnce() throws Exception {
        var fx = data.createQueue(null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);

        List<Callable<StaffTokenDto>> nexts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            nexts.add(() -> {
                TestData.actAs(fx.staff());
                try {
                    return engine.next(fx.queue().getId(), null);
                } finally {
                    TestData.clear();
                }
            });
        }

        List<Object> results = runConcurrently(nexts);

        assertThat(results.stream().filter(r -> r instanceof StaffTokenDto)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof ApiException e && e.getCode() == ErrorCode.NO_WAITING_TOKEN))
                .hasSize(1);
    }

    @Test
    void manyConcurrentNextCallsNeverCallTheSameTokenTwice() throws Exception {
        var fx = data.createQueue(null);
        int tokensInLine = 10;
        for (int i = 0; i < tokensInLine; i++) {
            engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        }

        List<Callable<StaffTokenDto>> nexts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            nexts.add(() -> {
                TestData.actAs(fx.staff());
                try {
                    return engine.next(fx.queue().getId(), null);
                } finally {
                    TestData.clear();
                }
            });
        }

        List<Object> results = runConcurrently(nexts);

        List<UUID> called = results.stream().filter(r -> r instanceof StaffTokenDto)
                .map(r -> ((StaffTokenDto) r).id()).toList();
        assertThat(called).hasSize(tokensInLine).doesNotHaveDuplicates();
        assertThat(results.stream().filter(r -> r instanceof ApiException e && e.getCode() == ErrorCode.NO_WAITING_TOKEN))
                .hasSize(20 - tokensInLine);
        assertThat(tokens.countByQueueIdAndStatus(fx.queue().getId(), TokenStatus.WAITING)).isZero();
        assertThat(tokens.countByQueueIdAndStatus(fx.queue().getId(), TokenStatus.CALLED)).isEqualTo(tokensInLine);
    }

    @Test
    void tokensAreCalledInArrivalOrder() {
        var fx = data.createQueue(null);
        for (int i = 0; i < 5; i++) {
            engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        }
        TestData.actAs(fx.staff());
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            order.add(engine.next(fx.queue().getId(), null).tokenNumber());
        }
        assertThat(order).containsExactly("A001", "A002", "A003", "A004", "A005");
    }

    @Test
    void queuesDoNotShareNumbering() {
        var one = data.createQueue(null);
        var two = data.createQueue(null);
        String a = engine.join(one.queue().getId(), new JoinRequest(null, false), null).token().tokenNumber();
        String b = engine.join(two.queue().getId(), new JoinRequest(null, false), null).token().tokenNumber();
        assertThat(a).isEqualTo("A001");
        assertThat(b).isEqualTo("A001");
    }
}
