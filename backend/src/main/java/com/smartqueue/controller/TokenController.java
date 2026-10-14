package com.smartqueue.controller;

import com.smartqueue.dto.QueueDtos.CounterAssignRequest;
import com.smartqueue.dto.QueueDtos.JoinRequest;
import com.smartqueue.dto.QueueDtos.JoinResponse;
import com.smartqueue.dto.QueueDtos.MoveRequest;
import com.smartqueue.dto.QueueDtos.NextRequest;
import com.smartqueue.dto.QueueDtos.QueueSnapshot;
import com.smartqueue.dto.QueueDtos.StaffTokenDto;
import com.smartqueue.dto.QueueDtos.TokenView;
import com.smartqueue.service.QueueEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Token and queue operations. Customer-facing calls (join, view) are open to guests;
 * everything else is object-level authorised inside {@link QueueEngine}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TokenController {

    private final QueueEngine engine;

    // ---- Customer ----

    @PostMapping("/queues/{queueId}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResponse join(@PathVariable UUID queueId,
                             @Valid @RequestBody(required = false) JoinRequest request,
                             @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return engine.join(queueId, request, sessionId);
    }

    @GetMapping("/tokens/{reference}")
    public TokenView byReference(@PathVariable String reference) {
        return engine.tokenByReference(reference);
    }

    @GetMapping("/me/tokens")
    public List<TokenView> myTokens(@RequestParam(defaultValue = "20") int limit) {
        return engine.myTokens(limit);
    }

    // ---- Staff: queue level ----

    @PostMapping("/queues/{queueId}/next")
    public StaffTokenDto next(@PathVariable UUID queueId, @RequestBody(required = false) NextRequest request) {
        return engine.next(queueId, request == null ? null : request.counterId());
    }

    @PostMapping("/queues/{queueId}/pause")
    public QueueSnapshot pause(@PathVariable UUID queueId) {
        return engine.pause(queueId);
    }

    @PostMapping("/queues/{queueId}/resume")
    public QueueSnapshot resume(@PathVariable UUID queueId) {
        return engine.resume(queueId);
    }

    // ---- Staff: token level ----

    @PostMapping("/tokens/{tokenId}/recall")
    public StaffTokenDto recall(@PathVariable UUID tokenId,
                                @RequestBody(required = false) CounterAssignRequest request) {
        return engine.recall(tokenId, request == null ? null : request.counterId());
    }

    @PostMapping("/tokens/{tokenId}/serving")
    public StaffTokenDto serving(@PathVariable UUID tokenId) {
        return engine.startServing(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/complete")
    public StaffTokenDto complete(@PathVariable UUID tokenId) {
        return engine.complete(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/skip")
    public StaffTokenDto skip(@PathVariable UUID tokenId) {
        return engine.skip(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/no-show")
    public StaffTokenDto noShow(@PathVariable UUID tokenId) {
        return engine.markNoShow(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/cancel")
    public StaffTokenDto cancel(@PathVariable UUID tokenId) {
        return engine.cancel(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/requeue")
    public StaffTokenDto requeue(@PathVariable UUID tokenId) {
        return engine.requeue(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/move")
    public StaffTokenDto move(@PathVariable UUID tokenId, @Valid @RequestBody MoveRequest request) {
        return engine.move(tokenId, request.position());
    }
}
