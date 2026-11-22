package com.dcn.securefiletransfer.network.test;

import com.dcn.securefiletransfer.network.client.TcpFileClient;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;
import com.dcn.securefiletransfer.network.server.TcpFileServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

/**
 * Standalone Test Harness for Phase 2 TCP Socket File Transfer.
 * 
 * Networking Viva Explanation:
 * Tests the foundational Layer 4 transport guarantees:
 * 1. ServerSocket listening and multi-threaded connection acceptance.
 * 2. Reliable length-prefixed framing over TCP byte streams.
 * 3. 64 KB chunk-based transmission with per-chunk acknowledgment (stop-and-wait ARQ layer).
 * 4. Exact byte-for-byte file reconstruction for binary formats (PDF, PNG, ZIP).
 */
public class TcpTransferTestRunner {

    private static final int TEST_PORT = 8444;
    private static final String TEST_HOST = "127.0.0.1";

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println(" DCN MINI-PROJECT - PHASE 2: TCP SOCKET FILE TRANSFER TEST SUITE ");
        System.out.println("=================================================================\n");

        File tempDir = new File("./storage/temp");
        File sampleDir = new File("./storage/test_samples");
        tempDir.mkdirs();
        sampleDir.mkdirs();

        TcpFileServer server = null;
        int passCount = 0;
        int totalTests = 0;

        try {
            // -------------------------------------------------------------
            // TEST 1: Server Startup & Port Binding
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 1] Initializing TcpFileServer on port " + TEST_PORT + "...");
            server = new TcpFileServer(TEST_PORT, tempDir, new TcpFileServer.TransferServerListener() {
                @Override
                public void onServerStarted(int port) {
                    System.out.println(" -> [SERVER EVENT] Server bound and listening on port " + port);
                }

                @Override
                public void onServerStopped() {
                    System.out.println(" -> [SERVER EVENT] Server stopped cleanly.");
                }

                @Override
                public void onTransferStarted(TransferMetadata metadata) {
                    System.out.println(" -> [SERVER EVENT] Started receiving: " + metadata.getFilename() + 
                            " (" + metadata.getFileSize() + " bytes)");
                }

                @Override
                public void onChunkReceived(TransferMetadata metadata, long chunkIndex, long totalChunks, long bytesReceived) {
                    // Log occasionally to avoid spamming console
                    if (chunkIndex % 5 == 0 || chunkIndex == totalChunks - 1) {
                        System.out.println(" -> [SERVER EVENT] Received chunk " + (chunkIndex + 1) + "/" + totalChunks + 
                                " (" + bytesReceived + " bytes)");
                    }
                }

                @Override
                public void onTransferCompleted(TransferMetadata metadata, File receivedTempFile) {
                    System.out.println(" -> [SERVER EVENT] Transfer completed. Temp file saved: " + receivedTempFile.getName());
                }

                @Override
                public void onTransferFailed(TransferMetadata metadata, String error) {
                    System.err.println(" -> [SERVER EVENT] Transfer failed: " + error);
                }
            });

            server.start();
            Thread.sleep(500); // Give server a moment to bind
            if (server.isRunning()) {
                System.out.println("[PASS] Server successfully running on port " + TEST_PORT + "\n");
                passCount++;
            } else {
                System.err.println("[FAIL] Server failed to start.\n");
            }

            // -------------------------------------------------------------
            // TEST 2: Transfer Simulated Binary PDF File (256 KB)
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 2] Testing PDF File Transfer (256 KB chunked)...");
            File pdfFile = createTestFile(sampleDir, "academic_dcn_report.pdf", 256 * 1024, "%PDF-1.4 simulated pdf header");
            boolean pdfSuccess = executeClientTransfer(pdfFile, "academic_dcn_report.pdf", tempDir);
            if (pdfSuccess) {
                passCount++;
                System.out.println("[PASS] PDF transfer & byte-for-byte verification passed.\n");
            } else {
                System.err.println("[FAIL] PDF transfer verification failed.\n");
            }

            // -------------------------------------------------------------
            // TEST 3: Transfer Simulated Image File (PNG - 512 KB)
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 3] Testing PNG Image Transfer (512 KB chunked)...");
            File pngFile = createTestFile(sampleDir, "network_topology.png", 512 * 1024, "\u0089PNG\r\n\u001a\n simulated png");
            boolean pngSuccess = executeClientTransfer(pngFile, "network_topology.png", tempDir);
            if (pngSuccess) {
                passCount++;
                System.out.println("[PASS] PNG image transfer & byte-for-byte verification passed.\n");
            } else {
                System.err.println("[FAIL] PNG transfer verification failed.\n");
            }

            // -------------------------------------------------------------
            // TEST 4: Transfer Multi-Megabyte ZIP Archive (1.5 MB)
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 4] Testing Multi-MB ZIP Archive Transfer (1.5 MB across ~24 chunks)...");
            File zipFile = createTestFile(sampleDir, "project_source.zip", (int)(1.5 * 1024 * 1024), "PK\u0003\u0004 simulated zip archive");
            boolean zipSuccess = executeClientTransfer(zipFile, "project_source.zip", tempDir);
            if (zipSuccess) {
                passCount++;
                System.out.println("[PASS] Large ZIP transfer & byte-for-byte verification passed.\n");
            } else {
                System.err.println("[FAIL] ZIP transfer verification failed.\n");
            }

            // -------------------------------------------------------------
            // TEST 5: Negative Test - Connection Refused on Inactive Port
            // -------------------------------------------------------------
            totalTests++;
            System.out.println("[TEST 5] Testing Error Handling: Connection to closed port 59999...");
            final boolean[] connectionFailedTriggered = {false};
            TcpFileClient badClient = new TcpFileClient(TEST_HOST, 59999, new TcpFileClient.TransferClientListener() {
                @Override public void onConnected(String h, int p) {}
                @Override public void onMetadataAccepted(TransferMetadata m) {}
                @Override public void onProgress(long b, long t, double p, double s, int eta) {}
                @Override public void onCompleted(TransferMetadata m, long d) {}
                @Override
                public void onFailed(String reason) {
                    System.out.println(" -> [CLIENT EVENT] Correctly caught network error: " + reason);
                    connectionFailedTriggered[0] = true;
                }
            });
            TransferMetadata dummyMeta = new TransferMetadata(UUID.randomUUID().toString(), "sender-1", "rec-1", "test.bin", 100, 65536, "hash", false);
            boolean badResult = badClient.sendFile(pdfFile, dummyMeta);
            if (!badResult && connectionFailedTriggered[0]) {
                passCount++;
                System.out.println("[PASS] Connection failure cleanly caught without uncaught exception.\n");
            } else {
                System.err.println("[FAIL] Error handling failed to trigger on closed port.\n");
            }

        } catch (Exception e) {
            System.err.println("[CRITICAL ERROR] Test suite crashed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (server != null) {
                server.stop();
            }
        }

        // Summary
        System.out.println("=================================================================");
        System.out.println(" PHASE 2 TEST SUMMARY: " + passCount + "/" + totalTests + " TESTS PASSED (" + 
                (passCount == totalTests ? "100% SUCCESS" : "FAILED") + ")");
        System.out.println("=================================================================");
    }

    /**
     * Executes client send operation and verifies byte-for-byte equality with reconstructed file.
     */
    private static boolean executeClientTransfer(File sourceFile, String targetFilename, File tempDir) {
        String transferId = UUID.randomUUID().toString();
        TransferMetadata metadata = new TransferMetadata(
                transferId,
                "academic-sender-node",
                "academic-receiver-node",
                targetFilename,
                sourceFile.length(),
                64 * 1024,
                "dummy-pre-phase4-hash",
                false
        );

        final boolean[] success = {false};

        TcpFileClient client = new TcpFileClient(TEST_HOST, TEST_PORT, new TcpFileClient.TransferClientListener() {
            @Override
            public void onConnected(String host, int port) {
                System.out.println(" -> [CLIENT EVENT] Connected to " + host + ":" + port);
            }

            @Override
            public void onMetadataAccepted(TransferMetadata meta) {
                System.out.println(" -> [CLIENT EVENT] Metadata acknowledged by receiver.");
            }

            @Override
            public void onProgress(long bytesSent, long totalBytes, double percent, double speedKBps, int etaSeconds) {
                System.out.printf(" -> [CLIENT EVENT] Progress: %6.1f%% (%d/%d bytes) | Speed: %8.2f KB/s | ETA: %d s%n",
                        percent, bytesSent, totalBytes, speedKBps, etaSeconds);
            }

            @Override
            public void onCompleted(TransferMetadata meta, long durationMs) {
                System.out.println(" -> [CLIENT EVENT] Transfer finished in " + durationMs + " ms.");
                success[0] = true;
            }

            @Override
            public void onFailed(String reason) {
                System.err.println(" -> [CLIENT EVENT] Client transfer error: " + reason);
            }
        });

        boolean transferCompleted = client.sendFile(sourceFile, metadata);
        if (!transferCompleted || !success[0]) {
            return false;
        }

        // Reconstructed file in temp dir is named {transferId}.tmp
        File reconstructedFile = new File(tempDir, transferId + ".tmp");
        if (!reconstructedFile.exists()) {
            System.err.println("Reconstructed temp file not found: " + reconstructedFile.getAbsolutePath());
            return false;
        }

        // Verify exact byte-by-byte equality
        boolean matches = compareFilesByteByByte(sourceFile, reconstructedFile);
        System.out.println(" -> Byte-by-byte comparison: " + (matches ? "EXACT MATCH (100% Identical)" : "MISMATCH"));

        // Clean up temp file
        reconstructedFile.delete();
        return matches;
    }

    /**
     * Creates a synthetic test file with custom header and pseudo-random deterministic bytes.
     */
    private static File createTestFile(File dir, String filename, int sizeBytes, String header) throws IOException {
        File file = new File(dir, filename);
        byte[] headerBytes = header.getBytes();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(headerBytes);
            int remaining = sizeBytes - headerBytes.length;
            if (remaining > 0) {
                byte[] buffer = new byte[Math.min(remaining, 16384)];
                Random random = new Random(42); // Deterministic seed
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

    /**
     * Compares two files byte-by-byte using buffered streams.
     */
    private static boolean compareFilesByteByByte(File f1, File f2) {
        if (f1.length() != f2.length()) {
            System.err.println("Size difference: f1=" + f1.length() + ", f2=" + f2.length());
            return false;
        }

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
            System.err.println("Error comparing files: " + e.getMessage());
            return false;
        }
    }
}
