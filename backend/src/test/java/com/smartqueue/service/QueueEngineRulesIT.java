package com.smartqueue.service;

import com.smartqueue.IntegrationTestBase;
import com.smartqueue.TestData;
import com.smartqueue.dto.QueueDtos.JoinRequest;
import com.smartqueue.dto.QueueDtos.StaffTokenDto;
import com.smartqueue.entity.PriorityMode;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Business-rule tests: state machine, paused queues, limits, priority, counters, access control. */
class QueueEngineRulesIT extends IntegrationTestBase {

    @Autowired QueueEngine engine;
    @Autowired TestData data;

    @AfterEach
    void clearSecurity() {
        TestData.clear();
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).getCode();
    }

    @Test
    void completedTokenCannotGoBackToWaiting() {
        var fx = data.createQueue(null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        TestData.actAs(fx.staff());
        StaffTokenDto called = engine.next(fx.queue().getId(), null);
        engine.complete(called.id());

        assertThatThrownBy(() -> engine.requeue(called.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_TOKEN_STATE));
        assertThatThrownBy(() -> engine.recall(called.id(), null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_TOKEN_STATE));
        assertThatThrownBy(() -> engine.complete(called.id()))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_TOKEN_STATE));
    }

    @Test
    void waitingTokenCannotBeCompletedWithoutBeingCalled() {
        var fx = data.createQueue(null);
        var joined = engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        TestData.actAs(fx.staff());
        UUID id = tokenId(joined.token().reference());
        assertThatThrownBy(() -> engine.complete(id))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_TOKEN_STATE));
    }

    @Test
    void pausedQueueRejectsJoinsAndNextWithoutTouchingTokens() {
        var fx = data.createQueue(null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        TestData.actAs(fx.staff());
        engine.pause(fx.queue().getId());

        assertThatThrownBy(() -> engine.next(fx.queue().getId(), null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.QUEUE_PAUSED));
        TestData.clear();
        assertThatThrownBy(() -> engine.join(fx.queue().getId(), new JoinRequest(null, false), null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.QUEUE_PAUSED));

        TestData.actAs(fx.staff());
        engine.resume(fx.queue().getId());
        assertThat(engine.next(fx.queue().getId(), null).status()).isEqualTo(TokenStatus.CALLED);
    }

    @Test
    void fullQueueRejectsNewTokens() {
        var fx = data.createQueue(cfg -> cfg.setMaxQueueSize(2));
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        assertThatThrownBy(() -> engine.join(fx.queue().getId(), new JoinRequest(null, false), null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.QUEUE_FULL));
    }

    @Test
    void guestCannotIssuePriorityTokens() {
        var fx = data.createQueue(cfg -> cfg.setPriorityMode(PriorityMode.ALWAYS_FIRST));
        assertThatThrownBy(() -> engine.join(fx.queue().getId(), new JoinRequest(null, true), null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void alwaysFirstPriorityIsServedBeforeEarlierNormalTokens() {
        var fx = data.createQueue(cfg -> cfg.setPriorityMode(PriorityMode.ALWAYS_FIRST));
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        TestData.actAs(fx.staff());
        String priority = engine.join(fx.queue().getId(), new JoinRequest(null, true), null).token().tokenNumber();
        assertThat(priority).isEqualTo("P001");

        assertThat(engine.next(fx.queue().getId(), null).tokenNumber()).isEqualTo("P001");
        assertThat(engine.next(fx.queue().getId(), null).tokenNumber()).isEqualTo("A001");
    }

    @Test
    void afterNPriorityInterleavesConfiguredRatio() {
        var fx = data.createQueue(cfg -> {
            cfg.setPriorityMode(PriorityMode.AFTER_N);
            cfg.setPriorityAfterN(2);
        });
        for (int i = 0; i < 4; i++) {
            engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        }
        TestData.actAs(fx.staff());
        engine.join(fx.queue().getId(), new JoinRequest(null, true), null);

        assertThat(engine.next(fx.queue().getId(), null).tokenNumber()).isEqualTo("A001");
        assertThat(engine.next(fx.queue().getId(), null).tokenNumber()).isEqualTo("A002");
        assertThat(engine.next(fx.queue().getId(), null).tokenNumber()).isEqualTo("P001");
        assertThat(engine.next(fx.queue().getId(), null).tokenNumber()).isEqualTo("A003");
    }

    @Test
    void busyCounterCannotBeAssignedAnotherToken() {
        var fx = data.createQueue(null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        TestData.actAs(fx.staff());
        StaffTokenDto first = engine.next(fx.queue().getId(), fx.counter1().getId());
        assertThat(first.counterName()).isEqualTo("Counter 1");

        assertThatThrownBy(() -> engine.next(fx.queue().getId(), fx.counter1().getId()))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.COUNTER_BUSY));
        assertThat(engine.next(fx.queue().getId(), fx.counter2().getId()).counterName()).isEqualTo("Counter 2");
    }

    @Test
    void customerCanCancelOwnWaitingTokenButNotACompletedOne() {
        var fx = data.createQueue(null);
        var first = engine.join(fx.queue().getId(), new JoinRequest(null, false), null).token();
        assertThat(engine.customerCancel(first.reference()).status()).isEqualTo(TokenStatus.CANCELLED);
        assertThatThrownBy(() -> engine.customerCancel(first.reference()))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.INVALID_TOKEN_STATE));
    }

    @Test
    void staffOfAnotherQueueCannotOperateThisQueue() {
        var mine = data.createQueue(null);
        var other = data.createQueue(null);
        engine.join(mine.queue().getId(), new JoinRequest(null, false), null);
        TestData.actAs(other.staff());
        assertThatThrownBy(() -> engine.next(mine.queue().getId(), null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void peopleAheadAndPositionFollowTheLine() {
        var fx = data.createQueue(null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        engine.join(fx.queue().getId(), new JoinRequest(null, false), null);
        var third = engine.join(fx.queue().getId(), new JoinRequest(null, false), null).token();
        assertThat(third.peopleAhead()).isEqualTo(2);
        assertThat(third.position()).isEqualTo(3);

        TestData.actAs(fx.staff());
        engine.next(fx.queue().getId(), null);
        var after = engine.tokenByReference(third.reference());
        assertThat(after.peopleAhead()).isEqualTo(1);
        assertThat(after.currentlyServing()).isEqualTo("A001");
    }

    // helper: resolve token id from its public reference through the staff-facing state
    @Autowired com.smartqueue.repository.QueueTokenRepository tokenRepo;

    private UUID tokenId(String reference) {
        return tokenRepo.findWithDetailsByReference(reference).orElseThrow().getId();
    }

}
