package com.spetrykin.certificate_management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CertificateManagementApplicationTests {

	@Test
	void contextLoads() {
	}

}
