package com.dcn.securefiletransfer.service;

import com.dcn.securefiletransfer.model.IntegrityCheck;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing SHA-256 Integrity Verification Audit Records.
 */
@Service
public class IntegrityService {

    private final Map<String, IntegrityCheck> checkStore = new ConcurrentHashMap<>();

    public IntegrityCheck recordCheck(String transferId, String filename, String originalHash,
                                      String receivedHash, String result, String details) {
        IntegrityCheck check = new IntegrityCheck(transferId, filename, originalHash, receivedHash, result, details);
        checkStore.put(check.getId(), check);
        return check;
    }

    public Optional<IntegrityCheck> getCheckByTransferId(String transferId) {
        return checkStore.values().stream()
                .filter(c -> transferId.equalsIgnoreCase(c.getTransferId()))
                .findFirst();
    }

    public List<IntegrityCheck> getAllChecks() {
        List<IntegrityCheck> list = new ArrayList<>(checkStore.values());
        list.sort(Comparator.comparing(IntegrityCheck::getVerifiedAt).reversed());
        return list;
    }

    public boolean compareHashes(String originalHash, String receivedHash) {
        if (originalHash == null || receivedHash == null) return false;
        return originalHash.trim().equalsIgnoreCase(receivedHash.trim());
    }

    public int getFailureCount() {
        return (int) checkStore.values().stream()
                .filter(c -> "FAILED".equalsIgnoreCase(c.getVerificationResult()))
                .count();
    }
}
