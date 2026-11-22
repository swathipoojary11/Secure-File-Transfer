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
 * Networking Viva Explanation:
 * - Connects to the receiver and initiates a cryptographic TLS 1.3 handshake.
 * - Validates the receiver's X.509 certificate against a trusted PKCS12 truststore.
 *   If the peer certificate is self-signed, expired, or not trusted, the JSSE
 *   framework terminates the connection with an SSLHandshakeException.
 * - Streams chunks encrypted in-flight using authenticated AES-256-GCM / ChaCha20.
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

            // 2. Connect and perform TLS 1.3 handshake
            System.out.println("[TLS CLIENT] Connecting securely to " + targetHost + ":" + targetPort + " (TLS 1.3)...");
            try (SSLSocket sslSocket = TlsContextFactory.createSslClientSocket(
                    targetHost, targetPort, sslContext,
                    ProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS,
                    ProtocolConstants.DEFAULT_SOCKET_TIMEOUT_MS)) {

                // Handshake completed successfully
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

                    // 4. Stream Chunks over TLS
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

                            // Pack Chunk Frame
                            ByteArrayOutputStream chunkBaos = new ByteArrayOutputStream(bytesRead + 12);
                            DataOutputStream chunkDos = new DataOutputStream(chunkBaos);
                            chunkDos.writeLong(chunkIndex);
                            chunkDos.writeInt(bytesRead);
                            chunkDos.write(chunkBuffer, 0, bytesRead);
                            chunkDos.flush();

                            FramingUtil.writeFrame(out, ProtocolConstants.MSG_CHUNK_DATA, chunkBaos.toByteArray());

                            // Wait for CHUNK_ACK
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

                    // 5. Send Completion Marker
                    FramingUtil.writeFrame(out, ProtocolConstants.MSG_TRANSFER_COMPLETE, new byte[0]);

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
