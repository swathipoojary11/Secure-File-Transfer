package com.dcn.securefiletransfer.controller;

import com.dcn.securefiletransfer.dto.IntegrityVerifyRequest;
import com.dcn.securefiletransfer.model.IntegrityCheck;
import com.dcn.securefiletransfer.service.IntegrityService;
import com.dcn.securefiletransfer.service.SecurityEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrity Verification REST Controller.
 * 
 * Networking Viva Explanation:
 * - Provides audit reports of file integrity verification.
 * - Compares expected SHA-256 against actual computed hash.
 * - Allows explicit re-verification of files and hash comparison audits.
 */
@RestController
@RequestMapping("/api/integrity")
public class IntegrityController {

    private final IntegrityService integrityService;
    private final SecurityEventService securityEventService;

    public IntegrityController(IntegrityService integrityService, SecurityEventService securityEventService) {
        this.integrityService = integrityService;
        this.securityEventService = securityEventService;
    }

    /**
     * Retrieves the integrity audit report for a specific transfer.
     */
    @GetMapping("/{transferId}")
    public ResponseEntity<?> getIntegrityReport(@PathVariable String transferId) {
        return integrityService.getCheckByTransferId(transferId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all integrity verification records.
     */
    @GetMapping
    public ResponseEntity<List<IntegrityCheck>> listAllChecks() {
        return ResponseEntity.ok(integrityService.getAllChecks());
    }

    /**
     * Explicitly compares two hashes to verify integrity or simulate tamper verification.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyIntegrity(@Valid @RequestBody IntegrityVerifyRequest request) {
        boolean matches = integrityService.compareHashes(request.getOriginalHash(), request.getReceivedHash());
        String result = matches ? "VERIFIED" : "FAILED";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "operator";

        IntegrityCheck check = integrityService.recordCheck(
                request.getTransferId() != null ? request.getTransferId() : "manual-check",
                request.getFilename() != null ? request.getFilename() : "verification-audit",
                request.getOriginalHash(),
                request.getReceivedHash(),
                result,
                matches ? "Cryptographic match confirmed. SHA-256 bits identical." : "Hash mismatch detected! Bit tampering or transmission corruption occurred."
        );

        if (!matches) {
            securityEventService.logEvent(
                    userId, "INTEGRITY_VIOLATION", "CRITICAL",
                    "Manual integrity check FAILED for " + request.getFilename() + ". Hashes differ!",
                    "127.0.0.1"
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("checkId", check.getId());
        response.put("originalHash", check.getOriginalHash());
        response.put("receivedHash", check.getReceivedHash());
        response.put("match", matches);
        response.put("status", result);
        response.put("algorithm", "SHA-256");
        response.put("verifiedAt", check.getVerifiedAt());
        response.put("details", check.getDetails());

        return ResponseEntity.ok(response);
    }
}
