package com.dcn.securefiletransfer.network.protocol;

import java.io.*;
import java.util.Arrays;

/**
 * Framing Utility for Reliable Message Delimitation Over TCP Byte Streams.
 * 
 * Networking Viva Explanation:
 * The fundamental difference between TCP and UDP:
 * - UDP preserves datagram boundaries (message-oriented).
 * - TCP is a continuous byte stream (stream-oriented).
 * 
 * When sending multiple messages over a single TCP socket, consecutive writes
 * may be merged (coalesced) by Nagle's algorithm or fragmented across IP packets.
 * This class implements Explicit Length-Prefixed Framing:
 * 
 * +-------------------+------------------+------------------+-----------------------+---------------------+
 * | Magic (4 Bytes)   | Version (1 Byte) | MsgType (1 Byte) | PayloadLength (4 Byte)| Payload (N Bytes)   |
 * | "DCN1"            | 0x01             | e.g. 0x03        | 32-bit Integer        | Chunk / Metadata    |
 * +-------------------+------------------+------------------+-----------------------+---------------------+
 */
public final class FramingUtil {

    private FramingUtil() {
        // Prevent instantiation
    }

    /**
     * Represents a fully assembled protocol frame received from the wire.
     */
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
     * 
     * @param out         The socket output stream.
     * @param messageType One of ProtocolConstants.MSG_* control bytes.
     * @param payload     The message payload bytes (can be null or empty).
     */
    public static void writeFrame(OutputStream out, byte messageType, byte[] payload) throws IOException {
        int payloadLength = (payload != null) ? payload.length : 0;

        if (payloadLength > ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload size " + payloadLength + " exceeds maximum allowed " +
                    ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE);
        }

        DataOutputStream dos = new DataOutputStream(out);
        // 1. Write Magic Bytes ("DCN1")
        dos.write(ProtocolConstants.MAGIC_BYTES);
        // 2. Write Version (0x01)
        dos.writeByte(ProtocolConstants.PROTOCOL_VERSION);
        // 3. Write Message Type
        dos.writeByte(messageType);
        // 4. Write 4-byte Big-Endian Payload Length
        dos.writeInt(payloadLength);
        // 5. Write raw payload bytes if present
        if (payloadLength > 0) {
            dos.write(payload, 0, payloadLength);
        }
        // Flush immediately to push bytes onto the network interface
        dos.flush();
    }

    /**
     * Reads a complete, framed message from the TCP socket input stream.
     * Handles partial reads by looping until the complete header and payload
     * are fully received into memory.
     * 
     * @param in The socket input stream.
     * @return The parsed Frame object.
     * @throws EOFException If the peer terminates the connection prematurely.
     * @throws IOException  If magic bytes mismatch or data is corrupted.
     */
    public static Frame readFrame(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        // 1. Read and validate Magic Bytes (4 bytes)
        byte[] magic = new byte[4];
        readFully(dis, magic, 0, 4);
        if (!Arrays.equals(magic, ProtocolConstants.MAGIC_BYTES)) {
            throw new IOException("Protocol Error: Invalid magic signature received. Expected 'DCN1'");
        }

        // 2. Read Protocol Version (1 byte)
        byte version = dis.readByte();
        if (version != ProtocolConstants.PROTOCOL_VERSION) {
            throw new IOException("Protocol Error: Unsupported protocol version: " + version);
        }

        // 3. Read Message Type (1 byte)
        byte messageType = dis.readByte();

        // 4. Read 4-byte Payload Length (int)
        int payloadLength = dis.readInt();
        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_FRAME_PAYLOAD_SIZE) {
            throw new IOException("Protocol Error: Illegal frame payload size: " + payloadLength);
        }

        // 5. Read exactly payloadLength bytes, assembling partial packets
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            readFully(dis, payload, 0, payloadLength);
        }

        return new Frame(messageType, payload);
    }

    /**
     * Guarantees that exactly 'len' bytes are read into 'b' starting at 'off'.
     * Resolves the classic TCP "Partial Read" pitfall where socket.read() returns
     * fewer bytes than requested due to network packet fragmentation.
     */
    public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int totalRead = 0;
        while (totalRead < len) {
            int count = in.read(b, off + totalRead, len - totalRead);
            if (count < 0) {
                throw new EOFException("Connection closed by remote peer while reading frame (read " 
                        + totalRead + " of " + len + " bytes)");
            }
            totalRead += count;
        }
    }
}
