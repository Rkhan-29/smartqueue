package com.smartqueue.service;

import com.smartqueue.dto.QueueDtos.QueueStatsDto;
import com.smartqueue.dto.QueueDtos.StaffQueueState;
import com.smartqueue.dto.QueueDtos.StaffQueueSummary;
import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.QueueConfiguration;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.entity.TokenStatus;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.QueueTokenRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.security.AuthenticatedUser;
import com.smartqueue.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Read-only staff dashboard queries. Mutations live in {@link QueueEngine}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffQueueService {

    private final ServiceQueueRepository queues;
    private final QueueConfigurationRepository configs;
    private final QueueTokenRepository tokens;
    private final AppUserRepository users;
    private final QueueAccessService access;
    private final QueueViewService views;

    /** Queues the caller may operate: all active queues for admins, assigned ones for staff. */
    public List<StaffQueueSummary> myQueues() {
        AuthenticatedUser user = CurrentUser.require();
        List<ServiceQueue> list;
        if (user.role() == Role.ADMIN) {
            list = queues.findByActiveTrueOrderByNameAsc();
        } else if (user.role() == Role.STAFF) {
            AppUser account = users.findById(user.id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
            if (account.getStatus() != com.smartqueue.entity.UserStatus.ACTIVE) {
                throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
            }
            List<UUID> ids = users.findAssignedQueueIds(user.id());
            list = ids.isEmpty() ? List.of() : queues.findByIdInOrderByNameAsc(ids);
        } else {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return list.stream()
                .filter(ServiceQueue::isActive)
                .map(q -> new StaffQueueSummary(q.getId(), q.getPublicRef(), q.getName(), q.getStatus(),
                        q.getService().getName(),
                        q.getService().getDepartment().getBranch().getName(),
                        tokens.countByQueueIdAndStatus(q.getId(), TokenStatus.WAITING)))
                .toList();
    }

    public StaffQueueState state(UUID queueId) {
        access.requireStaffAccess(queueId);
        ServiceQueue q = queues.findWithDetailsById(queueId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        return views.staffState(q, requireConfig(queueId));
    }

    public QueueStatsDto stats(UUID queueId) {
        access.requireStaffAccess(queueId);
        ServiceQueue q = queues.findWithDetailsById(queueId)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        return views.stats(q, requireConfig(queueId));
    }

    private QueueConfiguration requireConfig(UUID queueId) {
        return configs.findByQueueId(queueId).orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
    }
}
