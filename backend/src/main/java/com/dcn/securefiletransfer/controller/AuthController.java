package com.dcn.securefiletransfer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User Identity and Session Inspection REST Controller.
 * 
 * Networking Viva Explanation:
 * Endpoint: GET /api/auth/me
 * Allows the Next.js client to verify that its current Supabase JWT token
 * is recognized and validated by the Java backend service.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final com.dcn.securefiletransfer.security.jwt.JwtUtil jwtUtil;

    public AuthController(com.dcn.securefiletransfer.security.jwt.JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Generates a signed test JWT token for automated verification and academic viva demos.
     */
    @GetMapping("/demo-token")
    public ResponseEntity<Map<String, Object>> getDemoToken(
            @org.springframework.web.bind.annotation.RequestParam(value = "userId", defaultValue = "test-user-uuid-1234") String userId,
            @org.springframework.web.bind.annotation.RequestParam(value = "email", defaultValue = "operator@dcn.local") String email) {
        String token = jwtUtil.generateTestToken(userId, email, "authenticated", 3600000L);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", token);
        res.put("userId", userId);
        res.put("email", email);
        res.put("expiresInSeconds", 3600);
        res.put("type", "Bearer");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new LinkedHashMap<>();

        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "anonymous";
        String email = (auth != null && auth.getCredentials() != null) ? auth.getCredentials().toString() : "unauthenticated";

        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}

        response.put("authenticated", true);
        response.put("userId", userId);
        response.put("email", email);
        response.put("authorities", auth != null ? auth.getAuthorities() : null);
        response.put("localIp", localIp);
        response.put("service", "Secure File Transfer Auth Layer");

        return ResponseEntity.ok(response);
    }
}
