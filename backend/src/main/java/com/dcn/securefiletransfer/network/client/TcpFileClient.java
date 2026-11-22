package com.dcn.securefiletransfer.network.client;

import com.dcn.securefiletransfer.network.protocol.FramingUtil;
import com.dcn.securefiletransfer.network.protocol.FramingUtil.Frame;
import com.dcn.securefiletransfer.network.protocol.ProtocolConstants;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP File Client for Chunked File Streaming.
 * 
 * Networking Viva Explanation:
 * - Establishes a direct Layer 4 point-to-point TCP socket connection to the receiver.
 * - Disables Nagle's algorithm (TCP_NODELAY = true) so small acknowledgment packets
 *   are not buffered unnecessarily by the OS kernel.
 * - Streams file data in 64 KB chunks directly from disk to socket without
 *   reading large files completely into memory (preventing OutOfMemoryError).
 * - Dynamically computes real-time transfer metrics: throughput (KB/s, MB/s) and ETA.
 */
public class TcpFileClient {

    public interface TransferClientListener {
        void onConnected(String host, int port);
        void onMetadataAccepted(TransferMetadata metadata);
        void onProgress(long bytesSent, long totalBytes, double percent, double speedKBps, int etaSeconds);
        void onCompleted(TransferMetadata metadata, long durationMs);
        void onFailed(String reason);
    }

    private final String targetHost;
    private final int targetPort;
    private final TransferClientListener listener;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public TcpFileClient(String targetHost, int targetPort, TransferClientListener listener) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.listener = listener;
    }

    /**
     * Executes the file transfer synchronously on the calling thread.
     * 
     * @param sourceFile File on the local disk to transmit.
     * @param metadata   Metadata describing the transfer (IDs, expected hash, encryption).
     * @return true if verified and completed by remote peer, false otherwise.
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

        try (Socket socket = new Socket()) {
            // Configure TCP socket
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm for responsive streaming
            socket.setSoTimeout(ProtocolConstants.DEFAULT_SOCKET_TIMEOUT_MS);

            // Establish connection with timeout
            System.out.println("[CLIENT] Connecting to " + targetHost + ":" + targetPort + "...");
            socket.connect(new InetSocketAddress(targetHost, targetPort), ProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS);
            System.out.println("[CLIENT] Connected successfully to peer.");

            if (listener != null) {
                listener.onConnected(targetHost, targetPort);
            }

            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {

                // 1. Send MSG_METADATA_REQ
                System.out.println("[CLIENT] Sending transfer metadata: " + metadata);
                FramingUtil.writeFrame(out, ProtocolConstants.MSG_METADATA_REQ, metadata.toBytes());

                // Read MSG_METADATA_ACK
                Frame metaAckFrame = FramingUtil.readFrame(in);
                if (metaAckFrame.getMessageType() == ProtocolConstants.MSG_ERROR) {
                    String err = new String(metaAckFrame.getPayload());
                    notifyFailed("Receiver rejected metadata: " + err);
                    return false;
                } else if (metaAckFrame.getMessageType() != ProtocolConstants.MSG_METADATA_ACK) {
                    notifyFailed("Unexpected response to metadata: " + metaAckFrame.getMessageType());
                    return false;
                }

                if (listener != null) {
                    listener.onMetadataAccepted(metadata);
                }

                // 2. Stream Chunks Loop
                byte[] chunkBuffer = new byte[ProtocolConstants.DEFAULT_CHUNK_SIZE];
                long bytesSent = 0;
                long chunkIndex = 0;

                try (FileInputStream fis = new FileInputStream(sourceFile);
                     BufferedInputStream bis = new BufferedInputStream(fis, ProtocolConstants.DEFAULT_CHUNK_SIZE)) {

                    int bytesRead;
                    while ((bytesRead = bis.read(chunkBuffer)) != -1) {
                        if (cancelled.get()) {
                            FramingUtil.writeFrame(out, ProtocolConstants.MSG_ERROR, "Transfer cancelled by sender.".getBytes());
                            notifyFailed("Transfer was cancelled by sender.");
                            return false;
                        }

                        // Prepare Chunk Payload: [8 bytes seq] [4 bytes length] [raw data]
                        ByteArrayOutputStream chunkBaos = new ByteArrayOutputStream(bytesRead + 12);
                        DataOutputStream chunkDos = new DataOutputStream(chunkBaos);
                        chunkDos.writeLong(chunkIndex);
                        chunkDos.writeInt(bytesRead);
                        chunkDos.write(chunkBuffer, 0, bytesRead);
                        chunkDos.flush();

                        // Write MSG_CHUNK_DATA frame
                        FramingUtil.writeFrame(out, ProtocolConstants.MSG_CHUNK_DATA, chunkBaos.toByteArray());

                        // Wait for MSG_CHUNK_ACK
                        Frame ackFrame = FramingUtil.readFrame(in);
                        if (ackFrame.getMessageType() == ProtocolConstants.MSG_ERROR) {
                            String err = new String(ackFrame.getPayload());
                            notifyFailed("Receiver failed during chunk " + chunkIndex + ": " + err);
                            return false;
                        } else if (ackFrame.getMessageType() != ProtocolConstants.MSG_CHUNK_ACK) {
                            notifyFailed("Expected CHUNK_ACK but got " + ackFrame.getMessageType());
                            return false;
                        }

                        bytesSent += bytesRead;
                        chunkIndex++;

                        // Calculate progress, speed and ETA
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

                // 3. Send MSG_TRANSFER_COMPLETE
                System.out.println("[CLIENT] All chunks sent. Sending MSG_TRANSFER_COMPLETE...");
                FramingUtil.writeFrame(out, ProtocolConstants.MSG_TRANSFER_COMPLETE, new byte[0]);

                // Read MSG_TRANSFER_COMPLETE_ACK
                Frame compAck = FramingUtil.readFrame(in);
                if (compAck.getMessageType() != ProtocolConstants.MSG_TRANSFER_COMPLETE_ACK) {
                    if (compAck.getMessageType() == ProtocolConstants.MSG_ERROR) {
                        notifyFailed("Receiver verification failed: " + new String(compAck.getPayload()));
                    } else {
                        notifyFailed("Receiver returned unexpected completion frame: " + compAck.getMessageType());
                    }
                    return false;
                }

                long totalDurationMs = System.currentTimeMillis() - startTimeMs;
                System.out.println("[CLIENT] Transfer completed successfully in " + totalDurationMs + " ms.");

                if (listener != null) {
                    listener.onCompleted(metadata, totalDurationMs);
                }
                return true;
            }

        } catch (Exception e) {
            String errorMsg = "TCP Transfer Error: " + e.getMessage();
            System.err.println("[CLIENT] " + errorMsg);
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
