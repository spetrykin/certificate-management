package com.spetrykin.certificate_management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

// jwt.signing-key: Day 5 introduced a required (no-default) property that
// JwtService needs to construct; this full Spring context now includes it,
// so a throwaway, test-only value is supplied here — never the real
// JWT_SIGNING_KEY environment variable / production mechanism.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jwt.signing-key=test-only-signing-key-for-context-load-test-not-for-real-use-0")
class CertificateManagementApplicationTests {

	@Test
	void contextLoads() {
	}

}
