package com.dcn.securefiletransfer.security.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Dedicated SHA-256 Cryptographic Hash Utility.
 * 
 * Networking Viva Explanation:
 * - Implements FIPS 180-4 Secure Hash Standard (SHA-256).
 * - Computes a deterministic 256-bit (32-byte / 64-character hex) digest.
 * - Streams files in 64 KB buffers using MessageDigest.update() to guarantee
 *   constant O(1) memory consumption regardless of file size (prevents Java Heap OOM).
 * - Uses constant-time equality check (MessageDigest.isEqual) to defend against
 *   side-channel timing attacks during hash verification.
 */
public final class SHA256Util {

    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB chunk buffer

    private SHA256Util() {
        // Utility class
    }

    /**
     * Calculates the SHA-256 hash of a file on disk by streaming chunks.
     * 
     * @param file The file to hash.
     * @return 64-character lowercase hexadecimal SHA-256 digest.
     * @throws IOException If file reading fails.
     */
    public static String calculateHash(File file) throws IOException {
        return calculateFileHash(file);
    }

    /**
     * Alias for calculateHash.
     */
    public static String calculateFileHash(File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Target file does not exist: " + file.getAbsolutePath());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in Java runtime", e);
        }
    }

    /**
     * Calculates the SHA-256 hash of an in-memory byte array.
     */
    public static String calculateHash(byte[] data) {
        if (data == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in Java runtime", e);
        }
    }

    /**
     * Calculates the SHA-256 hash of a text string using UTF-8 encoding.
     */
    public static String calculateHash(String text) {
        if (text == null) return "";
        return calculateHash(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Constant-time comparison between two SHA-256 hash strings.
     */
    public static boolean verifyHash(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) return false;
        byte[] b1 = hash1.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] b2 = hash2.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(b1, b2);
    }

    /**
     * Verifies whether the calculated file hash matches an expected hash.
     * Implements constant-time byte comparison to eliminate timing leakages.
     * 
     * @param file         The received file to verify.
     * @param expectedHash The authenticated hash from metadata.
     * @return true if digests match exactly, false otherwise.
     */
    public static boolean verifyIntegrity(File file, String expectedHash) {
        if (expectedHash == null || expectedHash.trim().isEmpty()) {
            return false;
        }
        try {
            String actualHash = calculateHash(file);
            byte[] expectedBytes = expectedHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = actualHash.toLowerCase().getBytes(StandardCharsets.UTF_8);
            // Constant-time comparison
            return MessageDigest.isEqual(expectedBytes, actualBytes);
        } catch (Exception e) {
            System.err.println("[INTEGRITY ERROR] Failed to calculate hash: " + e.getMessage());
            return false;
        }
    }
}
