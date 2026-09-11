package com.spetrykin.certificate_management.controller;

import com.spetrykin.certificate_management.domain.CertificateNotFoundException;
import com.spetrykin.certificate_management.domain.DeviceNotFoundException;
import com.spetrykin.certificate_management.domain.IllegalStateTransitionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain and validation exceptions to RFC 7807 {@link ProblemDetail}
 * responses for every {@code @RestController} in this application.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CertificateNotFoundException.class)
    public ProblemDetail handleCertificateNotFound(CertificateNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ProblemDetail handleDeviceNotFound(DeviceNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ProblemDetail handleIllegalStateTransition(IllegalStateTransitionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Reachable from POST /api/certificates/{id}/transitions: two concurrent
     * requests transitioning the same certificate race on
     * {@code Certificate.version} exactly as the two scheduled-scan
     * invocations do in Day 3's CertificateServiceConcurrencyTest (see
     * architecture-plan.md, Concurrency section) — the difference here is
     * the race is driven by two HTTP requests instead of two scan
     * invocations, but it's the same underlying mechanism and the same
     * exception. The losing request's {@code transitionCertificate()} call
     * throws this, uncaught, straight out of the service method to here.
     * Treated as a 409, same family as {@link IllegalStateTransitionException}
     * — both mean "the certificate's state moved out from under this
     * request," just from a different cause (a version conflict rather
     * than an invalid target state).
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The certificate was modified concurrently by another request; reload and retry.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    /**
     * Generic fallback for anything else: never leaks the exception's own
     * message or stack trace into the response body (it may contain
     * internals an API consumer has no business seeing) — logged
     * server-side instead, with a generic detail message in the response.
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleUnexpected(RuntimeException e) {
        log.error("Unhandled exception while processing request", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
