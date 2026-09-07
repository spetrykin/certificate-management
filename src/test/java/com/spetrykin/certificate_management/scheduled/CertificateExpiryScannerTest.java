package com.spetrykin.certificate_management.scheduled;

import com.spetrykin.certificate_management.domain.CertState;
import com.spetrykin.certificate_management.domain.Certificate;
import com.spetrykin.certificate_management.repository.CertificateRepository;
import com.spetrykin.certificate_management.service.CertificateService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test, no Spring context and no database — same
 * reasoning as {@code CertificateServiceTest} (see its Javadoc): this class
 * is orchestration logic (find due certificates, call the service once per
 * certificate, isolate per-certificate failures), none of which depends on a
 * real persistence provider or the Spring scheduler actually firing.
 */
@ExtendWith(MockitoExtension.class)
class CertificateExpiryScannerTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateService certificateService;

    private CertificateExpiryScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new CertificateExpiryScanner(certificateRepository, certificateService, 30);
    }

    private static Certificate certificateWithId(Long id) {
        Certificate certificate = new Certificate(1L, "device-01.example.com", CertState.ACTIVE);
        ReflectionTestUtils.setField(certificate, "id", id);
        return certificate;
    }

    @Test
    void transitionsEveryCertificateReturnedByTheDueForRenewalQuery() {
        Certificate first = certificateWithId(1L);
        Certificate second = certificateWithId(2L);
        when(certificateRepository.findByStateAndExpiresAtBefore(eq(CertState.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        scanner.scanForExpiringCertificates();

        verify(certificateService).transitionCertificate(eq(1L), eq(CertState.EXPIRING_SOON), anyString(), anyString());
        verify(certificateService).transitionCertificate(eq(2L), eq(CertState.EXPIRING_SOON), anyString(), anyString());
    }

    @Test
    void oneCertificateFailingDoesNotAbortTheRestOfTheBatch() {
        Certificate failing = certificateWithId(10L);
        Certificate healthyBefore = certificateWithId(9L);
        Certificate healthyAfter = certificateWithId(11L);
        when(certificateRepository.findByStateAndExpiresAtBefore(eq(CertState.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(healthyBefore, failing, healthyAfter));

        // lenient(): certificates 9 and 11 hit this same mocked method with
        // different arguments and are deliberately left unstubbed (the
        // scanner's default/success path needs no stubbing on a void-ish
        // mock call). Without lenient(), Mockito's strict-stubbing check
        // flags those as a "potential stubbing problem" purely because this
        // stub exists for the same method with different arguments, which
        // would make the scanner's own catch-and-continue swallow that
        // Mockito exception instead of the intended one — defeating what
        // this test is meant to prove.
        lenient().doThrow(new RuntimeException("optimistic lock conflict"))
                .when(certificateService)
                .transitionCertificate(eq(10L), eq(CertState.EXPIRING_SOON), anyString(), anyString());

        scanner.scanForExpiringCertificates();

        verify(certificateService, times(1))
                .transitionCertificate(eq(9L), eq(CertState.EXPIRING_SOON), anyString(), anyString());
        verify(certificateService, times(1))
                .transitionCertificate(eq(10L), eq(CertState.EXPIRING_SOON), anyString(), anyString());
        verify(certificateService, times(1))
                .transitionCertificate(eq(11L), eq(CertState.EXPIRING_SOON), anyString(), anyString());
    }
}
