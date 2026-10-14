package com.smartqueue.service;

import com.smartqueue.dto.AdminDtos.BranchDto;
import com.smartqueue.dto.AdminDtos.OrganizationDto;
import com.smartqueue.dto.QueueDtos.QueueSnapshot;
import com.smartqueue.entity.Branch;
import com.smartqueue.entity.ServiceQueue;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.BranchRepository;
import com.smartqueue.repository.OrganizationRepository;
import com.smartqueue.repository.QueueConfigurationRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Anonymous, read-only browsing: organization -> branch -> queues. Exposes no personal data. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCatalogService {

    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final ServiceQueueRepository queues;
    private final QueueConfigurationRepository configs;
    private final QueueViewService views;

    public List<OrganizationDto> organizations() {
        return organizations.findByActiveTrueOrderByNameAsc().stream()
                .map(o -> new OrganizationDto(o.getId(), o.getName(), o.getSlug(), o.getDescription(), o.isActive()))
                .toList();
    }

    public List<BranchDto> branches(UUID organizationId) {
        return branches.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId).stream()
                .map(PublicCatalogService::toDto)
                .toList();
    }

    public List<QueueSnapshot> queuesForBranch(UUID branchId) {
        List<ServiceQueue> list = queues.findByServiceDepartmentBranchIdAndActiveTrueOrderByNameAsc(branchId);
        Map<UUID, com.smartqueue.entity.QueueConfiguration> cfgs = configs
                .findByQueueIdIn(list.stream().map(ServiceQueue::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getQueue().getId(), Function.identity()));
        return list.stream()
                .filter(q -> cfgs.containsKey(q.getId()))
                .map(q -> views.snapshot(q, cfgs.get(q.getId())))
                .toList();
    }

    public QueueSnapshot queueByRef(String publicRef) {
        ServiceQueue q = queues.findWithDetailsByPublicRef(publicRef)
                .filter(ServiceQueue::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND));
        return views.snapshot(q, configs.findByQueueId(q.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.QUEUE_NOT_FOUND)));
    }

    static BranchDto toDto(Branch b) {
        return new BranchDto(b.getId(), b.getOrganization().getId(), b.getOrganization().getName(), b.getName(),
                b.getAddress(), b.getCity(), b.getTimeZone(), b.isActive());
    }
}
