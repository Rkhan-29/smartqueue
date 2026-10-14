package com.smartqueue.controller;

import com.smartqueue.dto.AdminDtos.BranchDto;
import com.smartqueue.dto.AdminDtos.OrganizationDto;
import com.smartqueue.dto.QueueDtos.QueueSnapshot;
import com.smartqueue.dto.QueueDtos.TokenView;
import com.smartqueue.service.PublicCatalogService;
import com.smartqueue.service.QrCodeService;
import com.smartqueue.service.QueueEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Anonymous endpoints used by the customer flow and QR joining. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicCatalogService catalog;
    private final QrCodeService qr;
    private final QueueEngine engine;

    @GetMapping("/organizations")
    public List<OrganizationDto> organizations() {
        return catalog.organizations();
    }

    @GetMapping("/organizations/{organizationId}/branches")
    public List<BranchDto> branches(@PathVariable UUID organizationId) {
        return catalog.branches(organizationId);
    }

    @GetMapping("/branches/{branchId}/queues")
    public List<QueueSnapshot> queues(@PathVariable UUID branchId) {
        return catalog.queuesForBranch(branchId);
    }

    @GetMapping("/queues/{publicRef}")
    public QueueSnapshot queue(@PathVariable String publicRef) {
        return catalog.queueByRef(publicRef);
    }

    @GetMapping(value = "/queues/{publicRef}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable String publicRef,
                                         @RequestParam(defaultValue = "512") int size,
                                         @RequestParam(defaultValue = "false") boolean download) {
        catalog.queueByRef(publicRef); // 404 for unknown references
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.IMAGE_PNG);
        if (download) {
            response.header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename("smartqueue-" + publicRef + ".png").build().toString());
        }
        return response.body(qr.png(publicRef, size));
    }

    @PostMapping("/tokens/{reference}/cancel")
    public TokenView cancel(@PathVariable String reference) {
        return engine.customerCancel(reference);
    }
}
