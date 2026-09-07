package com.spetrykin.certificate_management.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only stand-in for Day 6's certificate endpoints, mounted under
 * {@code /api/**} — used solely by {@link AuthControllerSecurityTest} to
 * exercise {@link SecurityConfig}'s GET-vs-mutating authorization rule for
 * that prefix without inventing a throwaway production route. Lives under
 * src/test; never shipped in main, never referenced from
 * controller/AuthController.java.
 */
@RestController
@RequestMapping("/api/dummy")
public class DummyApiController {

    @GetMapping
    public String read() {
        return "ok";
    }

    @PostMapping
    public String write() {
        return "ok";
    }
}
