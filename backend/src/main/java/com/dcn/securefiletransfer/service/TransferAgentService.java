package com.dcn.securefiletransfer.service;

import com.dcn.securefiletransfer.dto.TransferInitiateRequest;
import com.dcn.securefiletransfer.model.Transfer;
import com.dcn.securefiletransfer.network.client.TlsFileClient;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;
import com.dcn.securefiletransfer.network.server.TlsFileServer;
import com.dcn.securefiletransfer.security.crypto.AesGcmUtil;
import com.dcn.securefiletransfer.security.crypto.SHA256Util;
import com.dcn.securefiletransfer.security.tls.TlsSessionDetails;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-Level Transfer Agent Service bridging REST APIs with the TLS 1.3 socket engine.
 * 
 * Networking Viva Explanation:
 * - Operates as an autonomous peer node:
 *   1. Spawns an inbound TLS 1.3 server daemon on startup (default port 8443).
 *   2. Reconstructs and verifies incoming files in real time.
 *   3. Outbound: accepts REST client requests, stages files, calculates SHA-256 digests,
 *      optionally applies AES-256-GCM authenticated encryption, and executes
 *      streaming chunk transmissions via TlsFileClient.
 */
@Service
public class TransferAgentService implements TlsFileServer.TlsServerListener {

    private final TransferService transferService;
    private final IntegrityService integrityService;
    private final SecurityEventService securityEventService;

    @Value("${tcp.transfer.port:8443}")
    private int transferPort;

    @Value("${app.storage.temp-dir:./storage/temp}")
    private String tempDirPath;

    @Value("${app.storage.received-dir:./storage/received}")
    private String receivedDirPath;

    private File keystoreFile;
    private File truststoreFile;
    @Value("${tcp.transfer.keystore-password:changeit}")
    private String storePassword;

    private TlsFileServer tlsServer;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public TransferAgentService(TransferService transferService,
                                IntegrityService integrityService,
                                SecurityEventService securityEventService) {
        this.transferService = transferService;
        this.integrityService = integrityService;
        this.securityEventService = securityEventService;
    }

    @PostConstruct
    public void init() {
        // Resolve keystores
        File baseCerts = new File("src/main/resources/certs");
        File storageCerts = new File("storage/certs");

        if (new File(storageCerts, "server-keystore.p12").exists()) {
            keystoreFile = new File(storageCerts, "server-keystore.p12");
            truststoreFile = new File(storageCerts, "client-truststore.p12");
        } else if (new File(baseCerts, "server-keystore.p12").exists()) {
            keystoreFile = new File(baseCerts, "server-keystore.p12");
            truststoreFile = new File(baseCerts, "client-truststore.p12");
        } else {
            keystoreFile = new File("server-keystore.p12");
            truststoreFile = new File("client-truststore.p12");
        }

        // Initialize directories
        new File(tempDirPath).mkdirs();
        new File(receivedDirPath).mkdirs();

        // Launch inbound TLS socket listener daemon
        try {
            if (keystoreFile.exists()) {
                tlsServer = new TlsFileServer(
                        transferPort, keystoreFile, storePassword,
                        new File(tempDirPath), new File(receivedDirPath), this
                );
                executorService.submit(() -> {
                    try {
                        tlsServer.start();
                    } catch (Exception e) {
                        System.err.println("[TRANSFER AGENT] TLS server listener error: " + e.getMessage());
                    }
                });
                System.out.println("[TRANSFER AGENT] Inbound TLS 1.3 socket daemon spawned on port " + transferPort);
            } else {
                System.err.println("[TRANSFER AGENT] Keystore not found at " + keystoreFile.getAbsolutePath() + "; TLS server listener deferred.");
            }
        } catch (Exception e) {
            System.err.println("[TRANSFER AGENT] Could not start TLS server on port " + transferPort + ": " + e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (tlsServer != null) {
            tlsServer.stop();
        }
        executorService.shutdownNow();
    }

    /**
     * Staging and launching outbound secure file transfer.
     */
    public Transfer initiateOutboundTransfer(String userId, String userEmail, MultipartFile multipartFile,
                                             TransferInitiateRequest request) throws IOException {
        String originalFilename = Paths.get(multipartFile.getOriginalFilename()).getFileName().toString();
        // Sanitize filename
        String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        String transferId = UUID.randomUUID().toString();
        File tempDir = new File(tempDirPath).getAbsoluteFile();
        if (!tempDir.exists()) tempDir.mkdirs();
        File stagedFile = new File(tempDir, transferId + "_" + safeFilename).getAbsoluteFile();
        multipartFile.transferTo(stagedFile);

        File fileToSend = stagedFile;
        boolean encrypted = request.isEncryptWithAes();

        // 1. Optional AES-256-GCM encryption
        if (encrypted) {
            try {
                SecretKey aesKey = AesGcmUtil.generateKey();
                File encFile = new File(tempDir, transferId + "_" + safeFilename + ".enc").getAbsoluteFile();
                AesGcmUtil.encryptFile(stagedFile, encFile, aesKey);
                fileToSend = encFile;
                System.out.println("[AES-GCM] Pre-encrypted file before TLS streaming: " + encFile.getName());
            } catch (Exception e) {
                throw new IOException("AES-256-GCM encryption failed: " + e.getMessage(), e);
            }
        }

        // 2. Calculate cryptographic SHA-256 for the exact transmitted byte-stream
        String transmissionHash = SHA256Util.calculateFileHash(fileToSend);

        // 3. Register Transfer in pending state
        Transfer transfer = new Transfer(
                userId != null ? userId : "anonymous-user",
                userEmail != null ? userEmail : "Operator",
                request.getRecipientId(),
                request.getRecipientName() != null ? request.getRecipientName() : request.getRecipientIp(),
                request.getRecipientIp(),
                request.getRecipientPort(),
                safeFilename,
                fileToSend.length(),
                transmissionHash,
                encrypted
        );
        transfer.setId(transferId);
        transfer.setTransferStatus("in_progress");
        transferService.saveTransfer(transfer);

        // 4. Asynchronous dispatch via TLS Client
        File finalFileToSend = fileToSend;
        executorService.submit(() -> {
            try {
                TransferMetadata metadata = new TransferMetadata(
                        transfer.getId(),
                        userId != null ? userId : "local-user",
                        request.getRecipientId() != null ? request.getRecipientId() : "remote-peer",
                        safeFilename,
                        finalFileToSend.length(),
                        com.dcn.securefiletransfer.network.protocol.ProtocolConstants.DEFAULT_CHUNK_SIZE,
                        transmissionHash,
                        encrypted
                );

                TlsFileClient client = new TlsFileClient(
                        request.getRecipientIp(), request.getRecipientPort(),
                        truststoreFile, storePassword,
                        new TlsFileClient.TlsClientListener() {
                            @Override
                            public void onTlsConnected(TlsSessionDetails session) {
                                System.out.println("[TRANSFER AGENT OUT] TLS 1.3 connected. Cipher: " + session.getCipherSuite());
                            }

                            @Override
                            public void onMetadataAccepted(TransferMetadata meta) {
                                transfer.setProgressPercent(5.0);
                                transferService.saveTransfer(transfer);
                            }

                            @Override
                            public void onProgress(long bytesSent, long totalBytes, double percent, double speedKBps, int eta) {
                                transfer.setProgressPercent(percent);
                                transfer.setTransferSpeed(speedKBps);
                                transferService.saveTransfer(transfer);
                            }

                            @Override
                            public void onCompleted(TransferMetadata meta, long durationMs, TlsSessionDetails session) {
                                transfer.setTransferStatus("completed");
                                transfer.setIntegrityStatus("verified");
                                transfer.setReceivedHash(transmissionHash);
                                transfer.setProgressPercent(100.0);
                                transfer.setTransferDurationMs(durationMs);
                                transfer.setCompletedAt(Instant.now());
                                transferService.saveTransfer(transfer);

                                integrityService.recordCheck(
                                        transfer.getId(), safeFilename, transmissionHash, transmissionHash,
                                        "VERIFIED", "Client transmission complete. TLS 1.3 AEAD MAC verified."
                                );

                                securityEventService.logEvent(
                                        userId, "TRANSFER_COMPLETED", "LOW",
                                        "Outbound transfer completed securely: " + safeFilename + " (" + transfer.getFileSize() + " bytes)",
                                        request.getRecipientIp()
                                );
                            }

                            @Override
                            public void onFailed(String reason) {
                                transfer.setTransferStatus("failed");
                                transfer.setIntegrityStatus("failed");
                                transfer.setErrorMessage(reason);
                                transferService.saveTransfer(transfer);

                                securityEventService.logEvent(
                                        userId, "TRANSFER_FAILED", "HIGH",
                                        "Outbound transfer to " + request.getRecipientIp() + " failed: " + reason,
                                        request.getRecipientIp()
                                );
                            }
                        }
                );

                client.sendFile(finalFileToSend, metadata);

            } catch (Exception e) {
                transfer.setTransferStatus("failed");
                transfer.setErrorMessage(e.getMessage());
                transferService.saveTransfer(transfer);
            }
        });

        return transfer;
    }

    /**
     * Resolves a file for download.
     */
    public Resource getFileAsResource(String filename) {
        Path path = Paths.get(receivedDirPath).resolve(filename).normalize();
        File file = path.toFile();
        if (file.exists() && file.isFile()) {
            return new FileSystemResource(file);
        }
        // Fallback to temp
        Path tempPath = Paths.get(tempDirPath).resolve(filename).normalize();
        File tempFile = tempPath.toFile();
        if (tempFile.exists() && tempFile.isFile()) {
            return new FileSystemResource(tempFile);
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // TlsFileServer Listener Callbacks (Inbound Transmissions)
    // ------------------------------------------------------------------------

    @Override
    public void onServerStarted(int port, String protocol, String[] cipherSuites) {
        System.out.println("[TLS SERVER CALLBACK] Server listening on port " + port);
    }

    @Override
    public void onServerStopped() {
        System.out.println("[TLS SERVER CALLBACK] Server stopped.");
    }

    @Override
    public void onTlsSessionEstablished(TlsSessionDetails session) {
        securityEventService.logEvent(
                "system", "TLS_HANDSHAKE_SUCCESS", "LOW",
                "TLS 1.3 Handshake completed with peer: " + session.getPeerHost() + " using " + session.getCipherSuite(),
                session.getPeerHost()
        );
    }

    @Override
    public void onTransferStarted(TransferMetadata metadata, TlsSessionDetails session) {
        Transfer t = new Transfer(
                "remote-sender", session.getPeerHost(),
                "local-receiver", "This Computer (Host)",
                session.getPeerHost(), transferPort,
                metadata.getFilename(), metadata.getFileSize(),
                metadata.getExpectedSha256(), false
        );
        t.setId(metadata.getTransferId());
        t.setTransferStatus("in_progress");
        transferService.saveTransfer(t);
    }

    @Override
    public void onChunkReceived(TransferMetadata metadata, long chunkIndex, long totalChunks, long bytesReceived) {
        transferService.getTransferById(metadata.getTransferId()).ifPresent(t -> {
            double pct = totalChunks > 0 ? (double) chunkIndex / totalChunks * 100.0 : 0.0;
            t.setProgressPercent(pct);
            transferService.saveTransfer(t);
        });
    }

    @Override
    public void onTransferCompleted(TransferMetadata metadata, File receivedFile, String verifiedHash, TlsSessionDetails session) {
        transferService.getTransferById(metadata.getTransferId()).ifPresent(t -> {
            t.setTransferStatus("completed");
            t.setIntegrityStatus("verified");
            t.setReceivedHash(verifiedHash);
            t.setProgressPercent(100.0);
            t.setDownloadPath(receivedFile.getName());
            t.setCompletedAt(Instant.now());
            transferService.saveTransfer(t);
        });

        integrityService.recordCheck(
                metadata.getTransferId(), metadata.getFilename(),
                metadata.getExpectedSha256(), verifiedHash,
                "VERIFIED", "Inbound file verified via SHA-256. Cryptographic digest match confirmed."
        );

        securityEventService.logEvent(
                "system", "FILE_RECEIVED_VERIFIED", "LOW",
                "File " + metadata.getFilename() + " received & verified with SHA-256: " + verifiedHash.substring(0, 16) + "...",
                session.getPeerHost()
        );
    }

    @Override
    public void onIntegrityMismatch(TransferMetadata metadata, String expectedHash, String actualHash, TlsSessionDetails session) {
        transferService.getTransferById(metadata.getTransferId()).ifPresent(t -> {
            t.setTransferStatus("failed");
            t.setIntegrityStatus("failed");
            t.setReceivedHash(actualHash);
            t.setErrorMessage("SHA-256 Integrity Verification Failed! File rejected and deleted.");
            transferService.saveTransfer(t);
        });

        integrityService.recordCheck(
                metadata.getTransferId(), metadata.getFilename(),
                expectedHash, actualHash,
                "FAILED", "SECURITY ALERT: Hash mismatch detected! Expected: " + expectedHash + " but received: " + actualHash
        );

        securityEventService.logEvent(
                "system", "INTEGRITY_VIOLATION", "CRITICAL",
                "TAMPERING DETECTED for " + metadata.getFilename() + "! Expected: " + expectedHash.substring(0, 12) + "..., Got: " + actualHash.substring(0, 12) + "...",
                session.getPeerHost()
        );
    }

    @Override
    public void onTransferFailed(TransferMetadata metadata, String error) {
        if (metadata != null && metadata.getTransferId() != null) {
            transferService.getTransferById(metadata.getTransferId()).ifPresent(t -> {
                t.setTransferStatus("failed");
                t.setErrorMessage(error);
                transferService.saveTransfer(t);
            });
        }
        securityEventService.logEvent(
                "system", "TRANSFER_FAILED", "HIGH",
                "Transfer failed: " + error, "127.0.0.1"
        );
    }
}
