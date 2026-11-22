package com.dcn.securefiletransfer.network.server;

import com.dcn.securefiletransfer.network.protocol.FramingUtil;
import com.dcn.securefiletransfer.network.protocol.FramingUtil.Frame;
import com.dcn.securefiletransfer.network.protocol.ProtocolConstants;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;
import com.dcn.securefiletransfer.security.crypto.SHA256Util;
import com.dcn.securefiletransfer.security.tls.TlsContextFactory;
import com.dcn.securefiletransfer.security.tls.TlsSessionDetails;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TLS 1.3-Secured File Transfer Server with SHA-256 Integrity Verification & Atomic File Move.
 * 
 * Networking Viva Explanation:
 * - Implements the full Phase 4 secure receiver workflow:
 *   1. Receives chunk frames over the authenticated TLS 1.3 socket stream.
 *   2. Reconstructs chunks into an isolated temporary file (./storage/temp/{transferId}.tmp).
 *   3. Computes the SHA-256 cryptographic digest of the assembled file using streaming O(1) hashing.
 *   4. Performs constant-time comparison against metadata.getExpectedSha256().
 *   5. ATOMIC MOVE: If digests match, the file is atomically moved to ./storage/received/{filename}
 *      and marked VERIFIED.
 *   6. PURGE ON TAMPER: If digests mismatch, the temporary file is immediately deleted from disk,
 *      an MSG_ERROR frame is sent, and an integrity security alert is emitted.
 */
public class TlsFileServer {

    public interface TlsServerListener {
        void onServerStarted(int port, String protocol, String[] cipherSuites);
        void onServerStopped();
        void onTlsSessionEstablished(TlsSessionDetails session);
        void onTransferStarted(TransferMetadata metadata, TlsSessionDetails session);
        void onChunkReceived(TransferMetadata metadata, long chunkIndex, long totalChunks, long bytesReceived);
        void onTransferCompleted(TransferMetadata metadata, File receivedFile, String verifiedHash, TlsSessionDetails session);
        void onIntegrityMismatch(TransferMetadata metadata, String expectedHash, String actualHash, TlsSessionDetails session);
        void onTransferFailed(TransferMetadata metadata, String error);
    }

    private final int port;
    private final File keystoreFile;
    private final String keystorePassword;
    private final File tempStorageDir;
    private final File receivedStorageDir;
    private final TlsServerListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private SSLServerSocket sslServerSocket;
    private ExecutorService threadPool;

    public TlsFileServer(int port, File keystoreFile, String keystorePassword,
                         File tempStorageDir, TlsServerListener listener) {
        this(port, keystoreFile, keystorePassword, tempStorageDir, new File(tempStorageDir.getParentFile(), "received"), listener);
    }

    public TlsFileServer(int port, File keystoreFile, String keystorePassword,
                         File tempStorageDir, File receivedStorageDir, TlsServerListener listener) {
        this.port = port;
        this.keystoreFile = keystoreFile;
        this.keystorePassword = keystorePassword;
        this.tempStorageDir = tempStorageDir;
        this.receivedStorageDir = receivedStorageDir;
        this.listener = listener;

        if (!tempStorageDir.exists()) tempStorageDir.mkdirs();
        if (!receivedStorageDir.exists()) receivedStorageDir.mkdirs();
    }

    /**
     * Bootstraps the TLS 1.3 SSLContext and binds the SSLServerSocket.
     */
    public synchronized void start() throws Exception {
        if (running.get()) return;

        SSLContext sslContext = TlsContextFactory.createServerSslContext(keystoreFile, keystorePassword);
        sslServerSocket = TlsContextFactory.createSslServerSocket(port, sslContext);
        threadPool = Executors.newCachedThreadPool();
        running.set(true);

        if (listener != null) {
            listener.onServerStarted(port, TlsContextFactory.TLS_PROTOCOL_VERSION, TlsContextFactory.SUPPORTED_CIPHER_SUITES);
        }

        Thread acceptorThread = new Thread(this::listenLoop, "TlsFileServer-Acceptor-" + port);
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    private void listenLoop() {
        System.out.println("[TLS SERVER] Listening securely on port " + port + " (TLS 1.3)...");
        while (running.get() && !sslServerSocket.isClosed()) {
            try {
                SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
                sslSocket.setSoTimeout(ProtocolConstants.DEFAULT_SOCKET_TIMEOUT_MS);
                sslSocket.setTcpNoDelay(true);

                threadPool.execute(() -> handleSecureConnection(sslSocket));
            } catch (IOException e) {
                if (!running.get()) break;
                System.err.println("[TLS SERVER] Socket accept error: " + e.getMessage());
            }
        }
    }

    private void handleSecureConnection(SSLSocket socket) {
        TlsSessionDetails sessionDetails = null;
        TransferMetadata metadata = null;
        File tempFile = null;

        try {
            socket.startHandshake();
            sessionDetails = TlsSessionDetails.fromSslSocket(socket);
            System.out.println("[TLS SERVER] Secure Handshake Established: " + sessionDetails);

            if (listener != null) {
                listener.onTlsSessionEstablished(sessionDetails);
            }

            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                // 1. Read Metadata Frame
                Frame metaFrame = FramingUtil.readFrame(in);
                if (metaFrame.getMessageType() != ProtocolConstants.MSG_METADATA_REQ) {
                    sendError(out, "Expected MSG_METADATA_REQ (0x01) but got " + metaFrame.getMessageType());
                    return;
                }

                metadata = TransferMetadata.fromBytes(metaFrame.getPayload());
                metadata.setFilename(TcpFileServer.sanitizeFilename(metadata.getFilename()));

                if (listener != null) {
                    listener.onTransferStarted(metadata, sessionDetails);
                }

                tempFile = new File(tempStorageDir, metadata.getTransferId() + ".tmp");
                if (tempFile.exists()) tempFile.delete();

                // Acknowledge metadata acceptance over TLS
                FramingUtil.writeFrame(out, ProtocolConstants.MSG_METADATA_ACK, new byte[0]);

                // 2. Receive Chunks Loop over TLS Stream
                long totalBytesReceived = 0;
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos, ProtocolConstants.DEFAULT_CHUNK_SIZE)) {

                    long expectedSeq = 0;
                    while (totalBytesReceived < metadata.getFileSize()) {
                        Frame frame = FramingUtil.readFrame(in);

                        if (frame.getMessageType() == ProtocolConstants.MSG_CHUNK_DATA) {
                            DataInputStream chunkDis = new DataInputStream(new ByteArrayInputStream(frame.getPayload()));
                            long seq = chunkDis.readLong();
                            int dataLength = chunkDis.readInt();

                            if (seq != expectedSeq) {
                                sendError(out, "Chunk sequence error. Expected " + expectedSeq + ", got " + seq);
                                throw new IOException("Out-of-order chunk received on TLS session.");
                            }

                            byte[] chunkBytes = new byte[dataLength];
                            chunkDis.readFully(chunkBytes);

                            bos.write(chunkBytes);
                            totalBytesReceived += dataLength;
                            expectedSeq++;

                            if (listener != null) {
                                listener.onChunkReceived(metadata, seq, metadata.getTotalChunks(), totalBytesReceived);
                            }

                            // Send CHUNK_ACK
                            ByteArrayOutputStream ackBaos = new ByteArrayOutputStream();
                            DataOutputStream ackDos = new DataOutputStream(ackBaos);
                            ackDos.writeLong(seq);
                            ackDos.flush();
                            FramingUtil.writeFrame(out, ProtocolConstants.MSG_CHUNK_ACK, ackBaos.toByteArray());

                        } else if (frame.getMessageType() == ProtocolConstants.MSG_ERROR) {
                            throw new IOException("Remote client reported error: " + new String(frame.getPayload()));
                        } else {
                            throw new IOException("Unexpected frame received: " + frame.getMessageType());
                        }
                    }
                    bos.flush();
                }

                // 3. Complete Transmission Marker
                Frame finishFrame = FramingUtil.readFrame(in);
                if (finishFrame.getMessageType() != ProtocolConstants.MSG_TRANSFER_COMPLETE) {
                    throw new IOException("Unexpected frame while awaiting completion marker.");
                }

                // 4. MODULE 7: SHA-256 Integrity Verification on Reconstructed Temp File
                System.out.println("[TLS SERVER] Verifying SHA-256 integrity for " + metadata.getFilename() + "...");
                String calculatedHash = SHA256Util.calculateHash(tempFile);
                String expectedHash = metadata.getExpectedSha256();

                System.out.println(" -> Expected SHA-256:   " + expectedHash);
                System.out.println(" -> Calculated SHA-256: " + calculatedHash);

                boolean integrityVerified = SHA256Util.verifyIntegrity(tempFile, expectedHash);

                if (!integrityVerified) {
                    // SECURITY ALERT: Hash Mismatch / File Tampered in Transit!
                    System.err.println("[SECURITY ALERT] SHA-256 MISMATCH! File has been modified or corrupted.");
                    // Immediately delete the tampered temporary file from disk
                    if (tempFile.exists()) {
                        tempFile.delete();
                        System.err.println("[SECURITY ACTION] Tampered temporary file deleted from disk.");
                    }

                    if (listener != null) {
                        listener.onIntegrityMismatch(metadata, expectedHash, calculatedHash, sessionDetails);
                    }

                    sendError(out, "INTEGRITY_VERIFICATION_FAILED: Calculated hash " + calculatedHash +
                            " does not match expected " + expectedHash);
                    throw new SecurityException("Cryptographic SHA-256 integrity verification failed for transfer " + metadata.getTransferId());
                }

                // 5. ATOMIC FILE ACCEPTANCE: Move from temp to received storage
                File destinationFile = new File(receivedStorageDir, metadata.getFilename());
                if (destinationFile.exists()) {
                    String base = metadata.getFilename();
                    String ext = "";
                    int dot = base.lastIndexOf('.');
                    if (dot > 0) {
                        ext = base.substring(dot);
                        base = base.substring(0, dot);
                    }
                    destinationFile = new File(receivedStorageDir, base + "_" + System.currentTimeMillis() + ext);
                }

                Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[TLS SERVER] Integrity VERIFIED! File atomically accepted: " + destinationFile.getAbsolutePath());

                // Send completion acknowledgment
                FramingUtil.writeFrame(out, ProtocolConstants.MSG_TRANSFER_COMPLETE_ACK, calculatedHash.getBytes());

                if (listener != null) {
                    listener.onTransferCompleted(metadata, destinationFile, calculatedHash, sessionDetails);
                }
            }

        } catch (Exception e) {
            System.err.println("[TLS SERVER] Error: " + e.getMessage());
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (listener != null && metadata != null) {
                listener.onTransferFailed(metadata, e.getMessage());
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void sendError(OutputStream out, String msg) {
        try {
            FramingUtil.writeFrame(out, ProtocolConstants.MSG_ERROR, msg.getBytes());
        } catch (IOException ignored) {}
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);

        try {
            if (sslServerSocket != null && !sslServerSocket.isClosed()) {
                sslServerSocket.close();
            }
        } catch (IOException ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        if (listener != null) {
            listener.onServerStopped();
        }
        System.out.println("[TLS SERVER] Server stopped.");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }
}
