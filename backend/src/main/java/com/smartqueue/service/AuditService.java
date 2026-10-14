package com.smartqueue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.AuditLog;
import com.smartqueue.repository.AuditLogRepository;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Writes audit entries inside the caller's transaction, so an audit row exists if and only if the
 * audited change was committed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Object entityId, Map<String, ?> metadata) {
        AuthenticatedUser actor = CurrentUser.get().orElse(null);
        String label = actor == null ? "guest" : actor.username();
        write(actor == null ? null : actor.id(), label, action, entityType, entityId, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAs(AppUser actor, String action, String entityType, Object entityId, Map<String, ?> metadata) {
        write(actor.getId(), actor.getUsername(), action, entityType, entityId, metadata);
    }

    /** For events with no authenticated user (e.g. failed login), identified by the attempted login. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAnonymous(String label, String action, String entityType, Object entityId,
                                Map<String, ?> metadata) {
        write(null, label, action, entityType, entityId, metadata);
    }

    private void write(UUID actorId, String label, String action, String entityType, Object entityId,
                       Map<String, ?> metadata) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorLabel(truncate(label, 190));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : truncate(entityId.toString(), 64));
        log.setIpAddress(currentIp());
        log.setMetadata(toJson(metadata));
        repository.save(log);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return truncate(req.getRemoteAddr(), 64);
        }
        return null;
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise audit metadata", e);
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
