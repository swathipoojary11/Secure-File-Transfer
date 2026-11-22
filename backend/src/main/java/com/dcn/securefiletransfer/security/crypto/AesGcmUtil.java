package com.dcn.securefiletransfer.security.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM Authenticated File-Level Encryption Utility.
 * 
 * Networking Viva Explanation:
 * - Implements NIST SP 800-38D Authenticated Encryption with Associated Data (AEAD).
 * - Key Size: 256 bits (32 bytes) providing military-grade confidentiality.
 * - Nonce (Initialization Vector): 12 bytes (96 bits) freshly generated via SecureRandom
 *   for EVERY file. Nonce reuse in GCM mode is catastrophic and strictly prohibited.
 * - Authentication Tag: 128 bits (16 bytes). Verifies both confidentiality AND ciphertext integrity.
 *   If an adversary alters even a single bit of the file during transit, decryption
 *   fails immediately with an AEADBadTagException.
 * - File Format: [12-byte Nonce / IV] [Ciphertext + 16-byte GCM Tag]
 */
public final class AesGcmUtil {

    public static final String ALGORITHM = "AES";
    public static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int KEY_SIZE_BITS = 256;
    public static final int GCM_NONCE_LENGTH_BYTES = 12; // 96-bit recommended IV
    public static final int GCM_TAG_LENGTH_BITS = 128;   // 16-byte authentication tag
    private static final int BUFFER_SIZE = 64 * 1024;    // 64 KB streaming buffer

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmUtil() {
        // Utility class
    }

    /**
     * Generates a cryptographically strong 256-bit AES symmetric key.
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, SECURE_RANDOM);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES algorithm not supported by JCA", e);
        }
    }

    /**
     * Generates a fresh, unpredictable 12-byte initialization vector (nonce).
     */
    public static byte[] generateNonce() {
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }

    /**
     * Reconstructs a SecretKey from raw 32-byte key material.
     */
    public static SecretKey keyFromBytes(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 requires exactly 32 bytes of key material.");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Reconstructs a SecretKey from a Base64-encoded string.
     */
    public static SecretKey keyFromBase64(String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        return keyFromBytes(decoded);
    }

    /**
     * Encodes a SecretKey to Base64 format.
     */
    public static String keyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Encrypts a source file using AES-256-GCM and prepends the 12-byte IV to the output file.
     * 
     * @param inputFile  Plaintext file on disk.
     * @param outputFile Target ciphertext file on disk.
     * @param key        256-bit AES secret key.
     * @throws Exception If encryption or disk I/O fails.
     */
    public static void encryptFile(File inputFile, File outputFile, SecretKey key) throws Exception {
        if (!inputFile.exists()) {
            throw new FileNotFoundException("Plaintext file not found: " + inputFile.getAbsolutePath());
        }

        byte[] nonce = generateNonce();
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            // 1. Write the 12-byte random nonce at the very beginning of the output stream
            bos.write(nonce);

            // 2. Stream chunked encryption
            byte[] inBuffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(inBuffer)) != -1) {
                byte[] cipherBytes = cipher.update(inBuffer, 0, bytesRead);
                if (cipherBytes != null && cipherBytes.length > 0) {
                    bos.write(cipherBytes);
                }
            }

            // 3. Finalize encryption and append the 16-byte authentication tag
            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null && finalBytes.length > 0) {
                bos.write(finalBytes);
            }
            bos.flush();
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted file, reading the first 12 bytes as the nonce
     * and strictly validating the authentication tag.
     * 
     * @param inputFile  Ciphertext file with prepended IV.
     * @param outputFile Target decrypted file.
     * @param key        Matching 256-bit AES secret key.
     * @throws AEADBadTagException If ciphertext or authentication tag was modified / corrupted.
     * @throws Exception           If decryption fails.
     */
    public static void decryptFile(File inputFile, File outputFile, SecretKey key) throws Exception {
        if (!inputFile.exists()) {
            throw new FileNotFoundException("Encrypted file not found: " + inputFile.getAbsolutePath());
        }
        if (inputFile.length() < GCM_NONCE_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw new IllegalArgumentException("Encrypted file is too short to contain IV and GCM auth tag.");
        }

        try (FileInputStream fis = new FileInputStream(inputFile);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
             FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            // 1. Read the 12-byte IV from the start of the file
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            int readNonce = bis.read(nonce);
            if (readNonce != GCM_NONCE_LENGTH_BYTES) {
                throw new EOFException("Premature EOF while reading GCM IV nonce");
            }

            // 2. Initialize cipher in DECRYPT_MODE
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            // 3. Stream chunked decryption
            byte[] inBuffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bis.read(inBuffer)) != -1) {
                byte[] plainBytes = cipher.update(inBuffer, 0, bytesRead);
                if (plainBytes != null && plainBytes.length > 0) {
                    bos.write(plainBytes);
                }
            }

            // 4. Verify tag & finalize
            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null && finalBytes.length > 0) {
                bos.write(finalBytes);
            }
            bos.flush();
        } catch (Exception e) {
            // Delete corrupt partially decrypted file
            if (outputFile.exists()) {
                outputFile.delete();
            }
            throw e;
        }
    }
}
