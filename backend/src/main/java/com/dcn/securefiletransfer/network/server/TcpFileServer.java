package com.dcn.securefiletransfer.network.server;

import com.dcn.securefiletransfer.network.protocol.FramingUtil;
import com.dcn.securefiletransfer.network.protocol.FramingUtil.Frame;
import com.dcn.securefiletransfer.network.protocol.ProtocolConstants;
import com.dcn.securefiletransfer.network.protocol.TransferMetadata;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multithreaded TCP File Receiver Server.
 * 
 * Networking Viva Explanation:
 * - Listens for incoming Layer 4 TCP connections using ServerSocket.
 * - Spawns a dedicated thread per connection from a thread pool to allow concurrent transfers.
 * - Enforces path sanitization to prevent Directory Traversal Attacks (e.g. "../../etc/passwd").
 * - Streams incoming chunk frames directly into a temporary file on disk to guarantee O(1) memory usage.
 */
public class TcpFileServer {

    public interface TransferServerListener {
        void onServerStarted(int port);
        void onServerStopped();
        void onTransferStarted(TransferMetadata metadata);
        void onChunkReceived(TransferMetadata metadata, long chunkIndex, long totalChunks, long bytesReceived);
        void onTransferCompleted(TransferMetadata metadata, File receivedTempFile);
        void onTransferFailed(TransferMetadata metadata, String error);
    }

    private final int port;
    private final File tempStorageDir;
    private final TransferServerListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService threadPool;

    public TcpFileServer(int port, File tempStorageDir, TransferServerListener listener) {
        this.port = port;
        this.tempStorageDir = tempStorageDir;
        this.listener = listener;

        if (!tempStorageDir.exists()) {
            boolean created = tempStorageDir.mkdirs();
            if (created) {
                System.out.println("[SERVER] Created temp directory: " + tempStorageDir.getAbsolutePath());
            }
        }
    }

    /**
     * Starts the ServerSocket listener loop on a background daemon thread.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        serverSocket = new ServerSocket(port);
        // Allows immediate reuse of the local socket address if in TIME_WAIT state
        serverSocket.setReuseAddress(true);
        threadPool = Executors.newCachedThreadPool();
        running.set(true);

        if (listener != null) {
            listener.onServerStarted(port);
        }

        Thread acceptorThread = new Thread(this::listenLoop, "TcpFileServer-Acceptor-" + port);
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    /**
     * ServerSocket accept loop listening for incoming peer socket connections.
     */
    private void listenLoop() {
        System.out.println("[SERVER] TCP File Server listening on port " + port + "...");
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Configure socket parameters
                clientSocket.setSoTimeout(ProtocolConstants.DEFAULT_SOCKET_TIMEOUT_MS);
                clientSocket.setTcpNoDelay(true); // Disable Nagle algorithm for lower latency

                // Dispatch connection handling to thread pool
                threadPool.execute(() -> handleConnection(clientSocket));
            } catch (IOException e) {
                if (!running.get()) {
                    break; // Normal shutdown
                }
                System.err.println("[SERVER] Socket accept error: " + e.getMessage());
            }
        }
    }

    /**
     * Handles an individual file transfer connection lifecycle.
     */
    private void handleConnection(Socket socket) {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        System.out.println("[SERVER] Accepted incoming connection from: " + clientAddress);

        TransferMetadata metadata = null;
        File tempFile = null;

        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            // 1. Read Metadata Frame
            Frame metaFrame = FramingUtil.readFrame(in);
            if (metaFrame.getMessageType() != ProtocolConstants.MSG_METADATA_REQ) {
                sendError(out, "Expected MSG_METADATA_REQ (0x01) but got " + metaFrame.getMessageType());
                return;
            }

            metadata = TransferMetadata.fromBytes(metaFrame.getPayload());
            System.out.println("[SERVER] Transfer Offered: " + metadata);

            // Path Traversal Security Protection:
            // Strip any directory path separators from the filename.
            String safeFilename = sanitizeFilename(metadata.getFilename());
            metadata.setFilename(safeFilename);

            if (listener != null) {
                listener.onTransferStarted(metadata);
            }

            // Create temporary staging file using transferId
            tempFile = new File(tempStorageDir, metadata.getTransferId() + ".tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }

            // Acknowledge metadata acceptance
            FramingUtil.writeFrame(out, ProtocolConstants.MSG_METADATA_ACK, new byte[0]);

            // 2. Receive Chunks Loop
            long totalBytesReceived = 0;
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, ProtocolConstants.DEFAULT_CHUNK_SIZE)) {

                long expectedChunkSeq = 0;

                while (totalBytesReceived < metadata.getFileSize()) {
                    Frame frame = FramingUtil.readFrame(in);

                    if (frame.getMessageType() == ProtocolConstants.MSG_CHUNK_DATA) {
                        // Parse chunk structure: [8 bytes seq] [4 bytes length] [raw data]
                        DataInputStream chunkDis = new DataInputStream(new ByteArrayInputStream(frame.getPayload()));
                        long seq = chunkDis.readLong();
                        int dataLength = chunkDis.readInt();

                        if (seq != expectedChunkSeq) {
                            sendError(out, "Chunk sequence mismatch. Expected " + expectedChunkSeq + ", got " + seq);
                            throw new IOException("Out of order chunk received: " + seq);
                        }

                        byte[] chunkBytes = new byte[dataLength];
                        chunkDis.readFully(chunkBytes);

                        // Write to disk immediately
                        bos.write(chunkBytes);
                        totalBytesReceived += dataLength;
                        expectedChunkSeq++;

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
                        String errMsg = new String(frame.getPayload());
                        throw new IOException("Client sent error: " + errMsg);
                    } else {
                        throw new IOException("Unexpected frame received while awaiting chunks: " + frame.getMessageType());
                    }
                }
                bos.flush();
            }

            // 3. Read TRANSFER_COMPLETE Frame
            Frame finishFrame = FramingUtil.readFrame(in);
            if (finishFrame.getMessageType() != ProtocolConstants.MSG_TRANSFER_COMPLETE) {
                sendError(out, "Expected MSG_TRANSFER_COMPLETE but got " + finishFrame.getMessageType());
                throw new IOException("Incomplete transmission protocol termination.");
            }

            // Verify file size on disk matches expected size
            if (tempFile.length() != metadata.getFileSize()) {
                sendError(out, "Size mismatch: expected " + metadata.getFileSize() + " bytes, got " + tempFile.length());
                throw new IOException("Assembled file size mismatch on disk.");
            }

            // Send completion acknowledgment
            FramingUtil.writeFrame(out, ProtocolConstants.MSG_TRANSFER_COMPLETE_ACK, new byte[0]);
            System.out.println("[SERVER] Transfer " + metadata.getTransferId() + " successfully reconstructed: " + tempFile.length() + " bytes.");

            if (listener != null) {
                listener.onTransferCompleted(metadata, tempFile);
            }

        } catch (Exception e) {
            System.err.println("[SERVER] Transfer failed for client " + clientAddress + ": " + e.getMessage());
            if (tempFile != null && tempFile.exists()) {
                // Delete partial/corrupted temporary file
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

    private void sendError(OutputStream out, String message) {
        try {
            FramingUtil.writeFrame(out, ProtocolConstants.MSG_ERROR, message.getBytes());
        } catch (IOException ignored) {}
    }

    /**
     * Sanitizes filename against directory traversal attacks.
     * Extracts basename only and removes dangerous path characters.
     */
    public static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            return "unnamed_transfer.bin";
        }
        Path path = Paths.get(rawFilename);
        String basename = path.getFileName().toString();
        // Remove null bytes and replace dangerous path traversal sequences
        return basename.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("^\\.+", "");
    }

    /**
     * Shuts down the ServerSocket and worker thread pool cleanly.
     */
    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        if (listener != null) {
            listener.onServerStopped();
        }
        System.out.println("[SERVER] TCP File Server stopped.");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }
}
