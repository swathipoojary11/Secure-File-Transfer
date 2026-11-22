package com.dcn.securefiletransfer.network.test;

import com.dcn.securefiletransfer.network.client.TlsFileClient;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;
import com.dcn.securefiletransfer.network.server.TlsFileServer;
import com.dcn.securefiletransfer.security.crypto.AesGcmUtil;
import com.dcn.securefiletransfer.security.crypto.SHA256Util;
import com.dcn.securefiletransfer.security.tls.TlsSessionDetails;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

/**
 * Standalone Test Harness for Phase 4:
 * SHA-256 Integrity Verification, AES-256-GCM File Encryption, and Tamper Detection.
 * 
 * Networking Viva Explanation:
 * Validates the core cryptographic mechanisms of the project:
 * 1. SHA-256 digest computation and constant-time integrity verification.
 * 2. AES-256-GCM authenticated encryption with 12-byte random nonces and 128-bit authentication tags.
 * 3. AEADBadTagException detection on 1-bit ciphertext modification.
 * 4. Successful end-to-end TLS transfer with matching hash -> ATOMIC MOVE to received storage.
 * 5. Corrupted/tampered transfer detection -> immediate deletion of temporary file & security alert.
 */
public class IntegrityAndCryptoTestRunner {

    private static final int TLS_PORT = 8446;
    private static final String HOST = "127.0.0.1";

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println(" DCN MINI-PROJECT - PHASE 4: CRYPTOGRAPHY & INTEGRITY TEST SUITE ");
        System.out.println("=================================================================\n");

        File tempDir = new File("./storage/temp");
        File receivedDir = new File("./storage/received");
        File certDir = new File("./storage/certs");
        File sampleDir = new File("./storage/test_samples");

        tempDir.mkdirs();
        receivedDir.mkdirs();
        sampleDir.mkdirs();

        File serverKeystore = new File(certDir, "server-keystore.p12");
        File clientTruststore = new File(certDir, "client-truststore.p12");

        if (!serverKeystore.exists() || !clientTruststore.exists()) {
            System.err.println("[SETUP ERROR] Certificates missing in ./storage/certs.");
            return;
        }

        int passCount = 0;
        int totalTests = 0;

        try {
            // -------------------------------------------------------------
            // TEST 1: SHA-256 Streaming Hash Calculation
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 1] Testing SHA-256 Streaming Digest Calculation & Constant-Time Check...");
            File hashSample = createSampleFile(sampleDir, "crypto_sample.txt", "Data Communication and Networks Cryptographic Hash Test Payload");
            String computedHash = SHA256Util.calculateHash(hashSample);
            System.out.println(" -> Sample File: " + hashSample.getName());
            System.out.println(" -> Computed SHA-256: " + computedHash);

            boolean matchSelf = SHA256Util.verifyIntegrity(hashSample, computedHash);
            boolean mismatchFake = SHA256Util.verifyIntegrity(hashSample, "0000000000000000000000000000000000000000000000000000000000000000");

            if (computedHash.length() == 64 && matchSelf && !mismatchFake) {
                System.out.println("[PASS] SHA-256 calculation and verification passed.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] SHA-256 calculation error.\n");
            }

            // -------------------------------------------------------------
            // TEST 2: AES-256-GCM File Encryption & Decryption
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 2] Testing AES-256-GCM Authenticated Encryption & Decryption...");
            File plaintextFile = createSampleFile(sampleDir, "confidential_spec.docx", "CONFIDENTIAL: Semester Mini Project Specifications for Secure DCN File Transfer");
            File encryptedFile = new File(sampleDir, "confidential_spec.docx.enc");
            File decryptedFile = new File(sampleDir, "confidential_spec.docx.dec");

            SecretKey aesKey = AesGcmUtil.generateKey();
            System.out.println(" -> Generated 256-bit AES Secret Key: " + AesGcmUtil.keyToBase64(aesKey));

            AesGcmUtil.encryptFile(plaintextFile, encryptedFile, aesKey);
            System.out.println(" -> Encrypted file created (" + encryptedFile.length() + " bytes with 12-byte IV prepended)");

            AesGcmUtil.decryptFile(encryptedFile, decryptedFile, aesKey);
            System.out.println(" -> Decrypted file created (" + decryptedFile.length() + " bytes)");

            boolean decryptedMatches = compareFilesByteByByte(plaintextFile, decryptedFile);
            System.out.println(" -> Decrypted file equals original: " + decryptedMatches);

            if (decryptedMatches) {
                System.out.println("[PASS] AES-256-GCM authenticated encryption/decryption passed.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Decrypted content mismatch.\n");
            }

            // -------------------------------------------------------------
            // TEST 3: AES-256-GCM Tamper Detection (1-Bit Modification Test)
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 3] Testing AES-256-GCM Tamper Detection (Corrupting 1 byte in ciphertext)...");
            File tamperedEncFile = new File(sampleDir, "tampered.enc");
            copyFile(encryptedFile, tamperedEncFile);

            // Corrupt 1 byte in the ciphertext payload (byte offset 20)
            try (RandomAccessFile raf = new RandomAccessFile(tamperedEncFile, "rw")) {
                raf.seek(20);
                byte originalByte = raf.readByte();
                raf.seek(20);
                raf.writeByte(originalByte ^ 0xFF); // Invert all 8 bits
            }

            boolean caughtGcmTamper = false;
            File tamperedDecOutput = new File(sampleDir, "tampered.dec");
            try {
                AesGcmUtil.decryptFile(tamperedEncFile, tamperedDecOutput, aesKey);
            } catch (AEADBadTagException e) {
                System.out.println(" -> [SECURITY EVENT] JCA AEADBadTagException caught: " + e.getMessage());
                caughtGcmTamper = true;
            } catch (Exception e) {
                if (e.getCause() instanceof AEADBadTagException || e.getMessage().contains("Tag mismatch")) {
                    caughtGcmTamper = true;
                }
            }

            if (caughtGcmTamper && !tamperedDecOutput.exists()) {
                System.out.println("[PASS] 1-bit ciphertext tampering detected by GCM authentication tag.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] GCM failed to detect ciphertext modification.\n");
            }

            // -------------------------------------------------------------
            // TEST 4: End-to-End TLS Transfer with Matching SHA-256 (Normal Transfer)
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 4] Testing End-to-End TLS Transfer with Matching Hash (Atomic Move)...");
            TlsFileServer tlsServer = new TlsFileServer(TLS_PORT, serverKeystore, "changeit", tempDir, receivedDir, null);
            tlsServer.start();
            Thread.sleep(500);

            File testPdf = createSampleFile(sampleDir, "final_project_report.pdf", "%PDF-1.4 Data Communication & Networks Mini Project Final Submission");
            String expectedSha256 = SHA256Util.calculateHash(testPdf);
            String transferId = UUID.randomUUID().toString();

            TransferMetadata normalMetadata = new TransferMetadata(
                    transferId,
                    "student-sender-laptop",
                    "faculty-evaluator-laptop",
                    "final_project_report.pdf",
                    testPdf.length(),
                    64 * 1024,
                    expectedSha256,
                    false
            );

            TlsFileClient client = new TlsFileClient(HOST, TLS_PORT, clientTruststore, "changeit", null);
            boolean normalSent = client.sendFile(testPdf, normalMetadata);

            File acceptedFile = new File(receivedDir, "final_project_report.pdf");
            boolean acceptedFileExists = acceptedFile.exists();
            boolean acceptedFileMatches = acceptedFileExists && SHA256Util.verifyIntegrity(acceptedFile, expectedSha256);

            if (normalSent && acceptedFileMatches) {
                System.out.println(" -> File accepted into: " + acceptedFile.getAbsolutePath());
                System.out.println(" -> SHA-256 verified: " + expectedSha256);
                System.out.println("[PASS] Normal transfer verified & atomically moved to received storage.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Normal transfer verification failed.\n");
            }

            // -------------------------------------------------------------
            // TEST 5: End-to-End Tampered Transfer (Hash Mismatch -> Purge File)
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 5] Testing End-to-End Tampered Transfer Simulation (Hash Mismatch Detection)...");
            String tamperedTransferId = UUID.randomUUID().toString();
            // Provide an invalid expected hash (simulating modified file payload or forged metadata)
            String forgedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Empty string hash

            TransferMetadata tamperedMetadata = new TransferMetadata(
                    tamperedTransferId,
                    "attacker-node",
                    "victim-node",
                    "tampered_network_payload.pdf",
                    testPdf.length(),
                    64 * 1024,
                    forgedHash, // Hash that will NOT match the actual bytes sent
                    false
            );

            boolean tamperClientResult = client.sendFile(testPdf, tamperedMetadata);
            File tamperedTempFile = new File(tempDir, tamperedTransferId + ".tmp");
            File tamperedReceivedFile = new File(receivedDir, "tampered_network_payload.pdf");

            // Verification: Client sendFile returns false, temp file is PURGED, and received file is NOT saved
            boolean tempFilePurged = !tamperedTempFile.exists();
            boolean neverSavedToReceived = !tamperedReceivedFile.exists();

            if (!tamperClientResult && tempFilePurged && neverSavedToReceived) {
                System.out.println(" -> Transfer was rejected by server: Integrity Mismatch.");
                System.out.println(" -> Temporary file purged: " + tempFilePurged);
                System.out.println(" -> Corrupted file NOT saved in received storage: " + neverSavedToReceived);
                System.out.println("[PASS] Tampered transfer detected, rejected, and quarantined.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Tampered transfer was not handled properly.\n");
            }

            tlsServer.stop();

        } catch (Exception e) {
            System.err.println("[CRITICAL] Test suite exception: " + e.getMessage());
            e.printStackTrace();
        }

        // Summary
        System.out.println("=================================================================");
        System.out.println(" PHASE 4 TEST SUMMARY: " + passCount + "/" + totalTests + " TESTS PASSED (" +
                (passCount == totalTests ? "100% SUCCESS" : "FAILED") + ")");
        System.out.println("=================================================================");
    }

    private static File createSampleFile(File dir, String filename, String content) throws IOException {
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
            fos.flush();
        }
        return file;
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) {
                fos.write(buf, 0, r);
            }
        }
    }

    private static boolean compareFilesByteByByte(File f1, File f2) {
        if (f1.length() != f2.length()) return false;
        try (FileInputStream fis1 = new FileInputStream(f1);
             FileInputStream fis2 = new FileInputStream(f2)) {
            byte[] b1 = new byte[8192];
            byte[] b2 = new byte[8192];
            int read1, read2;
            while ((read1 = fis1.read(b1)) != -1) {
                read2 = fis2.read(b2);
                if (read1 != read2 || !Arrays.equals(Arrays.copyOf(b1, read1), Arrays.copyOf(b2, read2))) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
