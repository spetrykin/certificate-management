package com.spetrykin.certificate_management.repository;

import com.spetrykin.certificate_management.domain.Device;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Device}. No custom queries — Day 6's
 * needs (create, find by id) are fully covered by {@link JpaRepository}.
 */
public interface DeviceRepository extends JpaRepository<Device, Long> {
}
