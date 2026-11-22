package com.dcn.securefiletransfer.network.test;

import com.dcn.securefiletransfer.network.client.TlsFileClient;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;
import com.dcn.securefiletransfer.network.server.TlsFileServer;
import com.dcn.securefiletransfer.security.tls.TlsSessionDetails;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

/**
 * Standalone Test Harness for Phase 3: TLS 1.3 Socket Communication & Certificate Validation.
 * 
 * Networking Viva Explanation:
 * Tests Layer 6 Presentation & Security Protocol guarantees:
 * 1. Strict TLS 1.3 cryptographic handshake negotiation.
 * 2. Authenticated AEAD cipher suite enforcement (e.g. TLS_AES_256_GCM_SHA384).
 * 3. Extraction of session diagnostics: Protocol, Cipher Suite, Peer Principal, Fingerprint.
 * 4. Successful chunked file streaming over the encrypted channel.
 * 5. Negative security test: Untrusted / rogue certificates are strictly rejected with SSLHandshakeException.
 */
public class TlsTransferTestRunner {

    private static final int TLS_TEST_PORT = 8445;
    private static final String HOST = "127.0.0.1";

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println(" DCN MINI-PROJECT - PHASE 3: TLS 1.3 SECURE TRANSFER TEST SUITE  ");
        System.out.println("=================================================================\n");

        File tempDir = new File("./storage/temp");
        File certDir = new File("./storage/certs");
        File sampleDir = new File("./storage/test_samples");
        tempDir.mkdirs();
        sampleDir.mkdirs();

        File serverKeystore = new File(certDir, "server-keystore.p12");
        File clientTruststore = new File(certDir, "client-truststore.p12");
        File untrustedTruststore = new File(certDir, "untrusted-truststore.p12");

        if (!serverKeystore.exists() || !clientTruststore.exists()) {
            System.err.println("[SETUP ERROR] Certificates missing in ./storage/certs. Please run keytool setup first.");
            return;
        }

        TlsFileServer tlsServer = null;
        int passCount = 0;
        int totalTests = 0;

        try {
            // -------------------------------------------------------------
            // TEST 1: TLS 1.3 Server Startup & Port Binding
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 1] Bootstrapping TlsFileServer on port " + TLS_TEST_PORT + " with TLS 1.3...");
            tlsServer = new TlsFileServer(TLS_TEST_PORT, serverKeystore, "changeit", tempDir, new TlsFileServer.TlsServerListener() {
                @Override
                public void onServerStarted(int port, String protocol, String[] cipherSuites) {
                    System.out.println(" -> [TLS SERVER EVENT] Bound on port " + port + " (" + protocol + ")");
                }

                @Override
                public void onServerStopped() {
                    System.out.println(" -> [TLS SERVER EVENT] Server stopped cleanly.");
                }

                @Override
                public void onTlsSessionEstablished(TlsSessionDetails session) {
                    System.out.println(" -> [TLS SERVER EVENT] Session Established: " + session.getProtocol() + " | " + session.getCipherSuite());
                }

                @Override
                public void onTransferStarted(TransferMetadata metadata, TlsSessionDetails session) {
                    System.out.println(" -> [TLS SERVER EVENT] Receiving file: " + metadata.getFilename());
                }

                @Override
                public void onChunkReceived(TransferMetadata metadata, long chunkIndex, long totalChunks, long bytesReceived) {
                    if (chunkIndex % 5 == 0 || chunkIndex == totalChunks - 1) {
                        System.out.println(" -> [TLS SERVER EVENT] Received encrypted chunk " + (chunkIndex + 1) + "/" + totalChunks);
                    }
                }

                @Override
                public void onTransferCompleted(TransferMetadata metadata, File receivedFile, String verifiedHash, TlsSessionDetails session) {
                    System.out.println(" -> [TLS SERVER EVENT] Completed & verified on disk: " + receivedFile.getName() + " (Hash: " + verifiedHash + ")");
                }

                @Override
                public void onIntegrityMismatch(TransferMetadata metadata, String expectedHash, String actualHash, TlsSessionDetails session) {
                    System.err.println(" -> [TLS SERVER EVENT] Integrity mismatch detected: expected=" + expectedHash + ", actual=" + actualHash);
                }

                @Override
                public void onTransferFailed(TransferMetadata metadata, String error) {
                    System.err.println(" -> [TLS SERVER EVENT] Transfer failed: " + error);
                }
            });

            tlsServer.start();
            Thread.sleep(600); // Allow acceptor thread to bind
            if (tlsServer.isRunning()) {
                System.out.println("[PASS] TlsFileServer running with TLS 1.3 on port " + TLS_TEST_PORT + "\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Server failed to bind.\n");
            }

            // -------------------------------------------------------------
            // TEST 2: Successful TLS 1.3 Transfer with Trusted Certificate
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 2] Testing End-to-End TLS 1.3 Encrypted Transfer with Trusted Truststore...");
            File testPdf = createTestFile(sampleDir, "tls_secure_document.pdf", 384 * 1024, "%PDF-1.7 confidential academic research");
            String transferId = UUID.randomUUID().toString();
            String testPdfHash = com.dcn.securefiletransfer.security.crypto.SHA256Util.calculateHash(testPdf);
            TransferMetadata meta = new TransferMetadata(
                    transferId,
                    "alice-node",
                    "bob-node",
                    "tls_secure_document.pdf",
                    testPdf.length(),
                    64 * 1024,
                    testPdfHash,
                    false
            );

            final TlsSessionDetails[] capturedSession = {null};
            final boolean[] transferSuccess = {false};

            TlsFileClient trustedClient = new TlsFileClient(HOST, TLS_TEST_PORT, clientTruststore, "changeit", new TlsFileClient.TlsClientListener() {
                @Override
                public void onTlsConnected(TlsSessionDetails session) {
                    System.out.println(" -> [TLS CLIENT EVENT] Handshake OK: Protocol=" + session.getProtocol() + " | Cipher=" + session.getCipherSuite());
                    System.out.println(" -> [TLS CLIENT EVENT] Server Certificate Fingerprint (SHA-256): " + session.getPeerCertificateFingerprint());
                    capturedSession[0] = session;
                }

                @Override
                public void onMetadataAccepted(TransferMetadata metadata) {
                    System.out.println(" -> [TLS CLIENT EVENT] Metadata acknowledged over TLS tunnel.");
                }

                @Override
                public void onProgress(long bytesSent, long totalBytes, double percent, double speedKBps, int etaSeconds) {
                    System.out.printf(" -> [TLS CLIENT EVENT] Encrypted Progress: %6.1f%% | Speed: %8.2f KB/s%n", percent, speedKBps);
                }

                @Override
                public void onCompleted(TransferMetadata metadata, long durationMs, TlsSessionDetails session) {
                    System.out.println(" -> [TLS CLIENT EVENT] TLS Transfer completed in " + durationMs + " ms.");
                    transferSuccess[0] = true;
                }

                @Override
                public void onFailed(String reason) {
                    System.err.println(" -> [TLS CLIENT EVENT] Client failed: " + reason);
                }
            });

            boolean sent = trustedClient.sendFile(testPdf, meta);
            File acceptedFile = new File("./storage/received", "tls_secure_document.pdf");
            boolean byteMatch = acceptedFile.exists() && compareFilesByteByByte(testPdf, acceptedFile);
            acceptedFile.delete();

            if (sent && transferSuccess[0] && byteMatch && capturedSession[0] != null &&
                    "TLSv1.3".equals(capturedSession[0].getProtocol())) {
                System.out.println(" -> Byte-for-byte binary equality confirmed across TLS encrypted stream.");
                System.out.println("[PASS] TLS 1.3 transfer and session diagnostics successfully validated.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] TLS transfer or verification failed.\n");
            }

            // -------------------------------------------------------------
            // TEST 3: Negative Test - Reject Untrusted / Rogue Certificate
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 3] Testing Negative Security Gate: Connection with Untrusted Truststore...");
            final boolean[] handshakeFailed = {false};

            TlsFileClient rogueClient = new TlsFileClient(HOST, TLS_TEST_PORT, untrustedTruststore, "changeit", new TlsFileClient.TlsClientListener() {
                @Override public void onTlsConnected(TlsSessionDetails session) {}
                @Override public void onMetadataAccepted(TransferMetadata metadata) {}
                @Override public void onProgress(long b, long t, double p, double s, int eta) {}
                @Override public void onCompleted(TransferMetadata m, long d, TlsSessionDetails s) {}
                @Override
                public void onFailed(String reason) {
                    System.out.println(" -> [TLS CLIENT EVENT] Handshake rejected as expected: " + reason);
                    if (reason.contains("PKIX path building failed") || reason.contains("validatorException") 
                            || reason.contains("Handshake") || reason.contains("Certificate")) {
                        handshakeFailed[0] = true;
                    }
                }
            });

            boolean rogueResult = rogueClient.sendFile(testPdf, meta);
            if (!rogueResult && handshakeFailed[0]) {
                System.out.println("[PASS] Untrusted certificate was strictly rejected by JSSE truststore validation.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Rogue client was not rejected as expected.\n");
            }

            // -------------------------------------------------------------
            // TEST 4: Protocol & Cipher Suite Enforcement
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 4] Verifying TLS 1.3 & AEAD Cipher Suite Suite Enforcement...");
            if (capturedSession[0] != null && capturedSession[0].getCipherSuite().contains("GCM") || 
                    capturedSession[0].getCipherSuite().contains("CHACHA20")) {
                System.out.println(" -> Negotiated Cipher: " + capturedSession[0].getCipherSuite() + " (AEAD Authenticated Encryption)");
                System.out.println("[PASS] Cryptographic strength requirement verified.\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Insecure or non-AEAD cipher negotiated.\n");
            }

        } catch (Exception e) {
            System.err.println("[CRITICAL] Test crashed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tlsServer != null) {
                tlsServer.stop();
            }
        }

        // Summary
        System.out.println("=================================================================");
        System.out.println(" PHASE 3 TEST SUMMARY: " + passCount + "/" + totalTests + " TESTS PASSED (" +
                (passCount == totalTests ? "100% SUCCESS" : "FAILED") + ")");
        System.out.println("=================================================================");
    }

    private static File createTestFile(File dir, String filename, int sizeBytes, String header) throws IOException {
        File file = new File(dir, filename);
        byte[] headerBytes = header.getBytes();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(headerBytes);
            int remaining = sizeBytes - headerBytes.length;
            if (remaining > 0) {
                byte[] buffer = new byte[Math.min(remaining, 16384)];
                Random random = new Random(1337);
                int written = 0;
                while (written < remaining) {
                    int toWrite = Math.min(buffer.length, remaining - written);
                    random.nextBytes(buffer);
                    fos.write(buffer, 0, toWrite);
                    written += toWrite;
                }
            }
            fos.flush();
        }
        return file;
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
