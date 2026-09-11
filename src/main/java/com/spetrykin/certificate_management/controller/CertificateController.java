package com.spetrykin.certificate_management.controller;

import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.dto.CertificateCreateRequest;
import com.spetrykin.certificate_management.dto.CertificateResponse;
import com.spetrykin.certificate_management.dto.TransitionRequest;
import com.spetrykin.certificate_management.service.CertificateService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mounted under {@code /api/**} per SecurityConfig's convention: GET
 * requires ADMIN or VIEWER, POST (create, transitions) requires ADMIN.
 * <p>
 * All access here — mutating and read alike — goes through {@link
 * CertificateService} consistently; the service layer is the only layer
 * that talks to repositories.
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    public ResponseEntity<CertificateResponse> createCertificate(@Valid @RequestBody CertificateCreateRequest request) {
        Certificate certificate = certificateService.createCertificate(request.deviceId(), request.commonName());
        return ResponseEntity.status(HttpStatus.CREATED).body(CertificateResponse.from(certificate));
    }

    @GetMapping("/{id}")
    public CertificateResponse getCertificate(@PathVariable Long id) {
        Certificate certificate = certificateService.getCertificateById(id);
        return CertificateResponse.from(certificate);
    }

    @GetMapping
    public List<CertificateResponse> listCertificates() {
        return certificateService.listCertificates().stream()
                .map(CertificateResponse::from)
                .toList();
    }

    @PostMapping("/{id}/transitions")
    public CertificateResponse transitionCertificate(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        Certificate certificate = certificateService.transitionCertificate(
                id, request.target(), request.actor(), request.reason());
        return CertificateResponse.from(certificate);
    }
}
