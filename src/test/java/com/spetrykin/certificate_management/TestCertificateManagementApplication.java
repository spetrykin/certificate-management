package com.spetrykin.certificate_management;

import org.springframework.boot.SpringApplication;

public class TestCertificateManagementApplication {

	public static void main(String[] args) {
		SpringApplication.from(CertificateManagementApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
