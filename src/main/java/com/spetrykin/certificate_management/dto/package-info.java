/**
 * Data transfer objects used at the API boundary. JPA entities are never exposed
 * directly in controllers; this package holds the request/response shapes instead.
 * <p>
 * Records, not classes — no mutable state or behavior beyond mapping is
 * needed. Each response record carries its own {@code from(entity)} static
 * factory (e.g. {@link com.spetrykin.certificate_management.dto.CertificateResponse#from});
 * no separate mapper class, since each mapping is a single one-direction,
 * one-record translation with nothing shared across DTOs to justify the
 * extra indirection (no MapStruct, per CLAUDE.md).
 */
package com.spetrykin.certificate_management.dto;
