# Secure File Transfer — Server & Client Core Source Code
**Course:** Data Communication & Networks (DCN) Academic Mini-Project  
**Title:** Secure File Transfer Using Encryption and Integrity Verification  
**Language & Runtime:** Java 21 LTS | Java Secure Socket Extension (JSSE)  
**Security Protocols:** TLS 1.3 (`TLS_AES_256_GCM_SHA384`), AES-256-GCM, FIPS 180-4 SHA-256  

---

## Table of Contents
1. [Architectural Overview](#1-architectural-overview)
2. [Protocol Framing Specification](#2-protocol-framing-specification)
3. [Server-Side Implementation](#3-server-side-implementation)
   - `TlsFileServer.java` (TLS 1.3 Socket Receiver)
   - `TcpFileServer.java` (Baseline TCP Socket Receiver)
4. [Client-Side Implementation](#4-client-side-implementation)
   - `TlsFileClient.java` (TLS 1.3 Socket Sender)
   - `TcpFileClient.java` (Baseline TCP Socket Sender)
5. [Protocol Delimitation Utility](#5-protocol-delimitation-utility)
   - `FramingUtil.java` (10-Byte Header Message Framing)
   - `TransferMetadata.java` (Wire Metadata Object)
6. [Cryptographic Core Modules](#6-cryptographic-core-modules)
   - `SHA256Util.java` (FIPS 180-4 Streaming Hash & Verification)
   - `AesGcmUtil.java` (NIST SP 800-38D AES-256-GCM Encryption)
7. [DCN Code Review & Viva Talking Points](#7-dcn-code-review--viva-talking-points)

---

## 1. Architectural Overview

The file transfer architecture implements three independent security boundaries:

```
[ CLIENT / SENDER NODE ]                                   [ SERVER / RECEIVER NODE ]
(Laptop A: 192.168.29.125)                                (Laptop B: 192.168.29.65)
        |                                                              |
        | 1. Compute Plaintext SHA-256 Hash                            |
        | 2. (Optional) Encrypt Payload with AES-256-GCM               |
        | 3. Compute Transmission Hash                                 |
        |                                                              |
        | --------- TCP SYN (Port 8443) -----------------------------> |
        | <-------- TCP SYN-ACK -------------------------------------- |
        | --------- TCP ACK (3-Way Handshake Established) -----------> |
        |                                                              |
        | <======== TLS 1.3 ClientHello / ServerHello ===============> |
        | <======== Key Exchange (ECDHE) & Certificate Exchange =====> |
        | <======== Encrypted Handshake Complete (AES-256-GCM) ======> |
        |                                                              |
        | --------- Frame 0x01: MSG_METADATA_REQ --------------------> |
        | <-------- Frame 0x02: MSG_METADATA_ACK --------------------- |
        |                                                              |
        | === Loop: Stream 64 KB Chunks over TLS Socket ============== |
        | --------- Frame 0x03: MSG_CHUNK_DATA [Seq: 0] -------------> |
        | <-------- Frame 0x04: MSG_CHUNK_ACK  [Seq: 0] -------------- |
        | --------- Frame 0x03: MSG_CHUNK_DATA [Seq: 1] -------------> |
        | <-------- Frame 0x04: MSG_CHUNK_ACK  [Seq: 1] -------------- |
        | ============================================================ |
        |                                                              |
        | --------- Frame 0x05: MSG_TRANSFER_COMPLETE ---------------> |
        |                                                              |
        |                                        [ Server Verifies: ]  |
        |                                        1. Reconstruct Stream |
        |                                        2. Compute SHA-256    |
        |                                        3. Constant-Time Match|
        |                                        4. Atomic Move to Disk|
        |                                                              |
        | <-------- Frame 0x06: MSG_TRANSFER_COMPLETE_ACK ------------ |
```

---

## 2. Protocol Framing Specification

Because TCP is a **continuous byte-stream protocol** and does not preserve application message boundaries, consecutive packet writes can be merged by Nagle's algorithm or fragmented across IP packets. 

To eliminate packet boundary ambiguity, every message is preceded by a strict **10-byte binary header**:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                  Magic Signature ("DCN1")                     |  -> 4 Bytes
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Version (1B) | MsgType (1B)  |       Payload Length          |  -> 2 Bytes
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       (Payload Length continued)      |    Payload Bytes ...  |  -> 4 Bytes Length
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

* **Magic Bytes (`0x44 0x43 0x4E 0x31`):** ASCII `"DCN1"`. Rejects non-protocol traffic.
* **Version (`0x01`):** Protocol format revision.
* **Message Type:** Control identifiers:
  * `0x01`: `MSG_METADATA_REQ` (Transfer initialization)
  * `0x02`: `MSG_METADATA_ACK` (Receiver accepted transfer)
  * `0x03`: `MSG_CHUNK_DATA` (64 KB file payload slice)
  * `0x04`: `MSG_CHUNK_ACK` (Sequence acknowledgment)
  * `0x05`: `MSG_TRANSFER_COMPLETE` (Sender finished all chunks)
  * `0x06`: `MSG_TRANSFER_COMPLETE_ACK` (Verification passed, file accepted)
  * `0xFF`: `MSG_ERROR` (Protocol or integrity violation)
* **Payload Length (4 Bytes):** 32-bit signed big-endian integer specifying exact payload byte length.

---

## 3. Server-Side Implementation

### 3.1 `TlsFileServer.java` (Secure TLS 1.3 Server)
```java
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
 * TLS 1.3-Secured File Transfer Server with SHA-256 Integrity Verification.
 * 
 * Implements:
 * 1. Java SSLServerSocket bound to port 8443 with TLS 1.3 ciphers.
 * 2. Multi-threaded client handling via cached thread pool.
 * 3. Binary framed chunk reconstruction into isolated temporary storage.
 * 4. FIPS 180-4 streaming SHA-256 digest validation before permanent acceptance.
 * 5. Atomic file move to prevent partial or tampered file exposure.
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
     * Bootstraps the TLS 1.3 SSLContext and starts listening on the socket port.
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
            // Handshake initiation
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

                // Acknowledge metadata acceptance
                FramingUtil.writeFrame(out, ProtocolConstants.MSG_METADATA_ACK, new byte[0]);

                // 2. Stream and Reconstruct Chunks into Temp File
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

                            // Send CHUNK_ACK with sequence number
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

                // 4. Cryptographic SHA-256 Integrity Verification
                System.out.println("[TLS SERVER] Verifying SHA-256 integrity for " + metadata.getFilename() + "...");
                String calculatedHash = SHA256Util.calculateHash(tempFile);
                String expectedHash = metadata.getExpectedSha256();

                System.out.println(" -> Expected SHA-256:   " + expectedHash);
                System.out.println(" -> Calculated SHA-256: " + calculatedHash);

                boolean integrityVerified = SHA256Util.verifyIntegrity(tempFile, expectedHash);

                if (!integrityVerified) {
                    // Security alert: tamper detected, purge from disk
                    System.err.println("[SECURITY ALERT] SHA-256 MISMATCH! File has been modified or corrupted.");
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

                // 5. Atomic Move: Transfer verified file to permanent storage
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
    }

    public boolean isRunning() { return running.get(); }
    public int getPort() { return port; }
}
```

---

## 4. Client-Side Implementation

### 4.1 `TlsFileClient.java` (Secure TLS 1.3 Client)
```java
package com.dcn.securefiletransfer.network.client;

import com.dcn.securefiletransfer.network.protocol.FramingUtil;
import com.dcn.securefiletransfer.network.protocol.FramingUtil.Frame;
import com.dcn.securefiletransfer.network.protocol.ProtocolConstants;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;
import com.dcn.securefiletransfer.security.tls.TlsContextFactory;
import com.dcn.securefiletransfer.security.tls.TlsSessionDetails;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TLS 1.3-Secured File Transfer Client.
 * 
 * Implements:
 * 1. Java SSLSocket connection to target host and port.
 * 2. TLS 1.3 handshake with PKCS12 truststore certificate verification.
 * 3. Metadata frame transmission and receiver ACK validation.
 * 4. 64 KB chunk slicing and streaming with sequence tracking.
 * 5. Per-chunk ACK reception and transfer speed / ETA telemetry.
 */
public class TlsFileClient {

    public interface TlsClientListener {
        void onTlsConnected(TlsSessionDetails session);
        void onMetadataAccepted(TransferMetadata metadata);
        void onProgress(long bytesSent, long totalBytes, double percent, double speedKBps, int etaSeconds);
        void onCompleted(TransferMetadata metadata, long durationMs, TlsSessionDetails session);
        void onFailed(String reason);
    }

    private final String targetHost;
    private final int targetPort;
    private final File truststoreFile;
    private final String truststorePassword;
    private final TlsClientListener listener;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public TlsFileClient(String targetHost, int targetPort, File truststoreFile,
                         String truststorePassword, TlsClientListener listener) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.truststoreFile = truststoreFile;
        this.truststorePassword = truststorePassword;
        this.listener = listener;
    }

    /**
     * Executes the secure TLS file transfer synchronously.
     */
    public boolean sendFile(File sourceFile, TransferMetadata metadata) {
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            notifyFailed("Source file does not exist: " + sourceFile.getAbsolutePath());
            return false;
        }

        metadata.setFileSize(sourceFile.length());
        metadata.setChunkSize(ProtocolConstants.DEFAULT_CHUNK_SIZE);
        metadata.setTotalChunks((sourceFile.length() + ProtocolConstants.DEFAULT_CHUNK_SIZE - 1)
                / ProtocolConstants.DEFAULT_CHUNK_SIZE);

        long startTimeMs = System.currentTimeMillis();

        try {
            // 1. Initialize Client SSLContext with Truststore
            SSLContext sslContext = TlsContextFactory.createClientSslContext(truststoreFile, truststorePassword);

            // 2. Connect and execute TLS 1.3 handshake
            System.out.println("[TLS CLIENT] Connecting securely to " + targetHost + ":" + targetPort + " (TLS 1.3)...");
            try (SSLSocket sslSocket = TlsContextFactory.createSslClientSocket(
                    targetHost, targetPort, sslContext,
                    ProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS,
                    ProtocolConstants.DEFAULT_SOCKET_TIMEOUT_MS)) {

                TlsSessionDetails sessionDetails = TlsSessionDetails.fromSslSocket(sslSocket);
                System.out.println("[TLS CLIENT] Handshake Succeeded! Session: " + sessionDetails);

                if (listener != null) {
                    listener.onTlsConnected(sessionDetails);
                }

                try (OutputStream out = sslSocket.getOutputStream();
                     InputStream in = sslSocket.getInputStream()) {

                    // 3. Send Metadata over TLS
                    FramingUtil.writeFrame(out, ProtocolConstants.MSG_METADATA_REQ, metadata.toBytes());

                    // Read Metadata ACK
                    Frame metaAckFrame = FramingUtil.readFrame(in);
                    if (metaAckFrame.getMessageType() == ProtocolConstants.MSG_ERROR) {
                        notifyFailed("Server rejected transfer: " + new String(metaAckFrame.getPayload()));
                        return false;
                    } else if (metaAckFrame.getMessageType() != ProtocolConstants.MSG_METADATA_ACK) {
                        notifyFailed("Unexpected metadata response: " + metaAckFrame.getMessageType());
                        return false;
                    }

                    if (listener != null) {
                        listener.onMetadataAccepted(metadata);
                    }

                    // 4. Stream 64 KB Chunks over TLS
                    byte[] chunkBuffer = new byte[ProtocolConstants.DEFAULT_CHUNK_SIZE];
                    long bytesSent = 0;
                    long chunkIndex = 0;

                    try (FileInputStream fis = new FileInputStream(sourceFile);
                         BufferedInputStream bis = new BufferedInputStream(fis, ProtocolConstants.DEFAULT_CHUNK_SIZE)) {

                        int bytesRead;
                        while ((bytesRead = bis.read(chunkBuffer)) != -1) {
                            if (cancelled.get()) {
                                FramingUtil.writeFrame(out, ProtocolConstants.MSG_ERROR, "Cancelled by sender.".getBytes());
                                notifyFailed("Transfer was cancelled by sender.");
                                return false;
                            }

                            // Pack Chunk Header: [Seq (8 Bytes)] [Length (4 Bytes)] [Bytes]
                            ByteArrayOutputStream chunkBaos = new ByteArrayOutputStream(bytesRead + 12);
                            DataOutputStream chunkDos = new DataOutputStream(chunkBaos);
                            chunkDos.writeLong(chunkIndex);
                            chunkDos.writeInt(bytesRead);
                            chunkDos.write(chunkBuffer, 0, bytesRead);
                            chunkDos.flush();

                            FramingUtil.writeFrame(out, ProtocolConstants.MSG_CHUNK_DATA, chunkBaos.toByteArray());

                            // Wait for CHUNK_ACK from receiver
                            Frame ackFrame = FramingUtil.readFrame(in);
                            if (ackFrame.getMessageType() == ProtocolConstants.MSG_ERROR) {
                                notifyFailed("Server error during chunk " + chunkIndex + ": " + new String(ackFrame.getPayload()));
                                return false;
                            } else if (ackFrame.getMessageType() != ProtocolConstants.MSG_CHUNK_ACK) {
                                notifyFailed("Expected CHUNK_ACK but got " + ackFrame.getMessageType());
                                return false;
                            }

                            bytesSent += bytesRead;
                            chunkIndex++;

                            // Telemetry calculations
                            long now = System.currentTimeMillis();
                            double elapsedSec = Math.max(0.001, (now - startTimeMs) / 1000.0);
                            double speedKBps = (bytesSent / 1024.0) / elapsedSec;
                            double percent = (bytesSent * 100.0) / metadata.getFileSize();

                            long remainingBytes = metadata.getFileSize() - bytesSent;
                            double bytesPerSec = bytesSent / elapsedSec;
                            int etaSec = (bytesPerSec > 0) ? (int) (remainingBytes / bytesPerSec) : 0;

                            if (listener != null) {
                                listener.onProgress(bytesSent, metadata.getFileSize(), percent, speedKBps, etaSec);
                            }
                        }
                    }

                    // 5. Send Transfer Complete Marker
                    FramingUtil.writeFrame(out, ProtocolConstants.MSG_TRANSFER_COMPLETE, new byte[0]);

                    // Wait for Server's Integrity Verification ACK
                    Frame compAck = FramingUtil.readFrame(in);
                    if (compAck.getMessageType() != ProtocolConstants.MSG_TRANSFER_COMPLETE_ACK) {
                        notifyFailed("Server reported completion verification failure.");
                        return false;
                    }

                    long totalDurationMs = System.currentTimeMillis() - startTimeMs;
                    System.out.println("[TLS CLIENT] Secure transmission finished in " + totalDurationMs + " ms.");

                    if (listener != null) {
                        listener.onCompleted(metadata, totalDurationMs, sessionDetails);
                    }
                    return true;
                }
            }

        } catch (Exception e) {
            String errorMsg = "TLS Transfer Failure: " + e.getMessage();
            System.err.println("[TLS CLIENT] " + errorMsg);
            notifyFailed(errorMsg);
            return false;
        }
    }

    public void cancel() {
        cancelled.set(true);
    }

    private void notifyFailed(String reason) {
        if (listener != null) {
            listener.onFailed(reason);
        }
    }
}
```

---

## 5. Protocol Delimitation Utility

### 5.1 `FramingUtil.java` (Binary Length-Prefixed Framing)
```java
package com.dcn.securefiletransfer.network.protocol;

import java.io.*;
import java.util.Arrays;

/**
 * Framing Utility for Reliable Message Delimitation Over TCP Byte Streams.
 * Solves TCP stream fragmentation using strict 10-byte binary length headers.
 */
public final class FramingUtil {

    private FramingUtil() {}

    public static class Frame {
        private final byte messageType;
        private final byte[] payload;

        public Frame(byte messageType, byte[] payload) {
            this.messageType = messageType;
            this.payload = payload;
        }

        public byte getMessageType() { return messageType; }
        public byte[] getPayload() { return payload; }
    }

    /**
     * Writes an explicitly framed message to the TCP socket output stream.
     */
    public static void writeFrame(OutputStream out, byte messageType, byte[] payload) throws IOException {
        int payloadLength = (payload != null) ? payload.length : 0;

        if (payloadLength > ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload size " + payloadLength + " exceeds limit " +
                    ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE);
        }

        DataOutputStream dos = new DataOutputStream(out);
        // 1. Magic Bytes: "DCN1" (4 bytes)
        dos.write(ProtocolConstants.MAGIC_BYTES);
        // 2. Version: 0x01 (1 byte)
        dos.writeByte(ProtocolConstants.PROTOCOL_VERSION);
        // 3. Message Type (1 byte)
        dos.writeByte(messageType);
        // 4. Payload Length: Big-Endian 32-bit Integer (4 bytes)
        dos.writeInt(payloadLength);
        // 5. Payload
        if (payloadLength > 0) {
            dos.write(payload, 0, payloadLength);
        }
        dos.flush();
    }

    /**
     * Reads a complete, framed message from the TCP socket input stream.
     */
    public static Frame readFrame(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        // 1. Validate Magic Signature ("DCN1")
        byte[] magic = new byte[4];
        readFully(dis, magic, 0, 4);
        if (!Arrays.equals(magic, ProtocolConstants.MAGIC_BYTES)) {
            throw new IOException("Protocol Error: Invalid magic signature. Expected 'DCN1'");
        }

        // 2. Validate Protocol Version
        byte version = dis.readByte();
        if (version != ProtocolConstants.PROTOCOL_VERSION) {
            throw new IOException("Protocol Error: Unsupported protocol version: " + version);
        }

        // 3. Read Message Type & Payload Length
        byte messageType = dis.readByte();
        int payloadLength = dis.readInt();

        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE) {
            throw new IOException("Protocol Error: Illegal payload length: " + payloadLength);
        }

        // 4. Read Payload
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            readFully(dis, payload, 0, payloadLength);
        }

        return new Frame(messageType, payload);
    }

    public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException("Connection closed prematurely while reading frame (" + n + "/" + len + " bytes read)");
            }
            n += count;
        }
    }
}
```

---

## 6. Cryptographic Core Modules

### 6.1 `SHA256Util.java` (FIPS 180-4 Cryptographic Integrity)
```java
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
 * FIPS 180-4 SHA-256 Cryptographic Digest Utility.
 * 
 * Features:
 * - O(1) constant memory streaming using 64 KB buffers.
 * - Constant-time comparison resisting side-channel timing attacks.
 */
public final class SHA256Util {

    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB chunk buffer

    private SHA256Util() {}

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
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public static String calculateHash(File file) throws IOException {
        return calculateFileHash(file);
    }

    /**
     * Constant-time comparison between two SHA-256 digests.
     * Prevents side-channel timing analysis.
     */
    public static boolean verifyIntegrity(File file, String expectedHash) throws IOException {
        if (expectedHash == null || expectedHash.isBlank()) return false;
        String actualHash = calculateHash(file);
        return verifyHash(actualHash, expectedHash);
    }

    public static boolean verifyHash(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) return false;
        byte[] b1 = hash1.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] b2 = hash2.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(b1, b2);
    }
}
```

### 6.2 `AesGcmUtil.java` (NIST SP 800-38D Authenticated Encryption)
```java
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

/**
 * AES-256-GCM Authenticated Encryption with Associated Data (AEAD).
 * 
 * Properties:
 * - Cipher: AES/GCM/NoPadding (256-bit Key)
 * - Nonce (IV): 12 bytes (96 bits) randomly generated per transfer
 * - Authentication Tag: 128 bits (16 bytes) verifying ciphertext integrity
 * - Ciphertext Format: [12-byte IV] + [Encrypted Data] + [16-byte Tag]
 */
public final class AesGcmUtil {

    public static final String ALGORITHM = "AES";
    public static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int KEY_SIZE_BITS = 256;
    public static final int GCM_NONCE_LENGTH_BYTES = 12; // 96-bit nonce
    public static final int GCM_TAG_LENGTH_BITS = 128;   // 16-byte authentication tag
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmUtil() {}

    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, SECURE_RANDOM);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES algorithm not supported", e);
        }
    }

    public static byte[] generateNonce() {
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }

    /**
     * Encrypts a file using AES-256-GCM and prepends the 12-byte IV to the output file.
     */
    public static void encryptFile(File inputFile, File outputFile, SecretKey key) throws Exception {
        byte[] nonce = generateNonce();
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        try (FileInputStream fis = new FileInputStream(inputFile);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
             FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            // Prepend 12-byte IV so the receiver can initialize GCM decryption
            bos.write(nonce);

            byte[] inBuffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bis.read(inBuffer)) != -1) {
                byte[] outBuffer = cipher.update(inBuffer, 0, bytesRead);
                if (outBuffer != null) {
                    bos.write(outBuffer);
                }
            }
            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null) {
                bos.write(finalBytes);
            }
            bos.flush();
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted file.
     * Throws AEADBadTagException if any byte was altered.
     */
    public static void decryptFile(File inputFile, File outputFile, SecretKey key) throws Exception {
        try (FileInputStream fis = new FileInputStream(inputFile);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE)) {

            // Read 12-byte IV
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            int ivRead = bis.read(nonce);
            if (ivRead != GCM_NONCE_LENGTH_BYTES) {
                throw new IOException("Ciphertext file is missing complete 12-byte GCM nonce.");
            }

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

                byte[] inBuffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(inBuffer)) != -1) {
                    byte[] outBuffer = cipher.update(inBuffer, 0, bytesRead);
                    if (outBuffer != null) {
                        bos.write(outBuffer);
                    }
                }
                byte[] finalBytes = cipher.doFinal();
                if (finalBytes != null) {
                    bos.write(finalBytes);
                }
                bos.flush();
            }
        }
    }
}
```

---

## 7. DCN Code Review & Viva Talking Points

| Topic | Implementation Detail in Code | Academic Reason |
| :--- | :--- | :--- |
| **Why TCP instead of UDP?** | `Socket` & `ServerSocket` in `TlsFileServer` / `TlsFileClient` | Files require 100% reliable, in-order delivery. Dropped UDP packets cause corrupt binaries. |
| **Why Custom Framing?** | `FramingUtil.java` (`DCN1` + 4-byte length prefix) | TCP is a stream protocol without packet boundaries. Explicit lengths prevent Nagle concatenation. |
| **TLS 1.3 Handshake** | `SSLSocket.startHandshake()` with `TLS_AES_256_GCM_SHA384` | Establishes 1-RTT encrypted pipe with Perfect Forward Secrecy (PFS), defeating Wi-Fi sniffing. |
| **Why Constant-Time Hash Check?** | `MessageDigest.isEqual()` in `SHA256Util.java` | Standard `String.equals()` returns early on mismatch, leaking character timing information to attackers. |
| **Why Chunk Slicing?** | 64 KB buffers in `TlsFileClient` & `SHA256Util` | Keeps memory usage at $O(1)$, preventing `OutOfMemoryError` on 500 MB+ files. |
| **Tamper Defense Strategy** | Purge temp file + emit `MSG_ERROR` | Prevents corrupt files from ever being stored in `./storage/received/`. |
