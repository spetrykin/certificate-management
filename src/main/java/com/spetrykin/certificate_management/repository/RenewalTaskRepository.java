package com.spetrykin.certificate_management.repository;

import com.spetrykin.certificate_management.domain.RenewalTask;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RenewalTask}. No custom queries yet
 * — application-level uniqueness checking is deferred to Day 4 alongside the
 * scheduled scanner (see architecture-plan.md, Concurrency section).
 */
public interface RenewalTaskRepository extends JpaRepository<RenewalTask, Long> {
}
