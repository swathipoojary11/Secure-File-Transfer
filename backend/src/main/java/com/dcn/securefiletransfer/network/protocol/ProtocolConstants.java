package com.dcn.securefiletransfer.network.protocol;

/**
 * Protocol Constants for the DCN Custom File Transfer Protocol.
 * 
 * Networking Viva Explanation:
 * In Layer 4 (Transport Layer), TCP provides an unstructured stream of bytes.
 * These protocol constants define the message framing format and control codes
 * so the receiver can unambiguously recognize message boundaries.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
        // Prevent instantiation
    }

    // 4-byte Magic Signature: ASCII 'D', 'C', 'N', '1' (0x44, 0x43, 0x4E, 0x31)
    // Ensures incoming connections are actually speaking our DCN protocol.
    public static final byte[] MAGIC_BYTES = new byte[] { 0x44, 0x43, 0x4E, 0x31 };

    // Protocol Version 1.0
    public static final byte PROTOCOL_VERSION = 0x01;

    // Header Size: 4 (Magic) + 1 (Version) + 1 (MsgType) + 4 (PayloadLength) = 10 Bytes
    public static final int HEADER_SIZE = 10;

    // Message Types (1 byte control codes)
    public static final byte MSG_METADATA_REQ = 0x01;          // Sender -> Receiver: Metadata offering
    public static final byte MSG_METADATA_ACK = 0x02;          // Receiver -> Sender: Metadata accepted
    public static final byte MSG_CHUNK_DATA = 0x03;            // Sender -> Receiver: Raw chunk payload
    public static final byte MSG_CHUNK_ACK = 0x04;             // Receiver -> Sender: Chunk acknowledged
    public static final byte MSG_TRANSFER_COMPLETE = 0x05;     // Sender -> Receiver: Transmission finished
    public static final byte MSG_TRANSFER_COMPLETE_ACK = 0x06; // Receiver -> Sender: Verification complete
    public static final byte MSG_ERROR = (byte) 0xFF;          // Error / abort condition

    // Default 64 KB chunk size (65,536 bytes)
    // 64 KB strikes the optimal balance between TCP throughput, buffer size,
    // and socket window scaling without exhausting Java heap RAM.
    public static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    // Maximum frame payload limit (16 MB) to prevent malicious memory exhaustion
    public static final int MAX_FRAME_PAYLOAD_SIZE = 16 * 1024 * 1024;

    // Default Connection and Socket Read Timeouts in milliseconds
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 30000;
}
