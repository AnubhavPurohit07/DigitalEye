package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.AuthResponse;
import com.digitaleye.backend.dto.LoginRequest;
import com.digitaleye.backend.dto.RegisterRequest;
import com.digitaleye.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public auth endpoints - these are the only ones that don't require a
 * JWT (see SecurityConfig: "/api/auth/**" is permitAll). Everything else
 * in the backend requires a valid Bearer token after this.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * For now this is open so you can create your first admin/user accounts.
     * Once the dashboard's User Management screen exists, registering new
     * USER accounts should move behind "/api/admin/**" (admin-only) -
     * leaving it open forever would let anyone self-register as ADMIN.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
}
