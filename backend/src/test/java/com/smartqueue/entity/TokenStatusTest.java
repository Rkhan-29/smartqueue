package com.smartqueue.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenStatusTest {

    @ParameterizedTest
    @EnumSource(value = TokenStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void terminalStatesCannotTransitionAnywhere(TokenStatus terminal) {
        for (TokenStatus next : TokenStatus.values()) {
            assertThat(terminal.canTransitionTo(next)).as("%s -> %s", terminal, next).isFalse();
        }
    }

    @Test
    void completedCannotReturnToWaiting() {
        assertThat(TokenStatus.COMPLETED.canTransitionTo(TokenStatus.WAITING)).isFalse();
    }

    @Test
    void happyPathIsAllowed() {
        assertThat(TokenStatus.WAITING.canTransitionTo(TokenStatus.CALLED)).isTrue();
        assertThat(TokenStatus.CALLED.canTransitionTo(TokenStatus.SERVING)).isTrue();
        assertThat(TokenStatus.SERVING.canTransitionTo(TokenStatus.COMPLETED)).isTrue();
    }

    @Test
    void servingCannotBeSkippedOrReturnedToLine() {
        assertThat(TokenStatus.SERVING.canTransitionTo(TokenStatus.SKIPPED)).isFalse();
        assertThat(TokenStatus.SERVING.canTransitionTo(TokenStatus.WAITING)).isFalse();
    }

    @Test
    void waitingCannotBeCompletedDirectly() {
        assertThat(TokenStatus.WAITING.canTransitionTo(TokenStatus.COMPLETED)).isFalse();
    }

    @Test
    void activeStatusesAreWaitingCalledServing() {
        assertThat(TokenStatus.activeStatuses())
                .containsExactlyInAnyOrder(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING);
    }
}
