package com.spetrykin.certificate_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} activates the Day 4
 * {@code CertificateExpiryScanner}'s {@code @Scheduled} method.
 */
@SpringBootApplication
@EnableScheduling
public class CertificateManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(CertificateManagementApplication.class, args);
	}

}
