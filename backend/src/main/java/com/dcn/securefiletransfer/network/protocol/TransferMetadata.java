package com.dcn.securefiletransfer.network.protocol;

import java.io.*;

/**
 * Transfer Metadata Model & Binary Serializer.
 * 
 * Networking Viva Explanation:
 * Before any raw file bytes are streamed across the TCP socket, the sender
 * and receiver must agree on the contract (Layer 5/6 session handshake):
 * - What file is being sent?
 * - How large is it?
 * - What is the expected SHA-256 hash for integrity verification?
 * - Is optional AES-256-GCM file-level encryption enabled?
 */
public class TransferMetadata {

    private String transferId;
    private String senderId;
    private String receiverId;
    private String filename;
    private long fileSize;
    private int chunkSize;
    private long totalChunks;
    private String expectedSha256;
    private boolean encrypted;

    public TransferMetadata() {
    }

    public TransferMetadata(String transferId, String senderId, String receiverId,
                            String filename, long fileSize, int chunkSize,
                            String expectedSha256, boolean encrypted) {
        this.transferId = transferId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = (fileSize + chunkSize - 1) / chunkSize;
        this.expectedSha256 = expectedSha256;
        this.encrypted = encrypted;
    }

    /**
     * Serializes this metadata object into a compact binary byte array
     * for network transmission using Java DataOutputStream.
     */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(transferId != null ? transferId : "");
            dos.writeUTF(senderId != null ? senderId : "");
            dos.writeUTF(receiverId != null ? receiverId : "");
            dos.writeUTF(filename != null ? filename : "unnamed_file");
            dos.writeLong(fileSize);
            dos.writeInt(chunkSize);
            dos.writeLong(totalChunks);
            dos.writeUTF(expectedSha256 != null ? expectedSha256 : "");
            dos.writeBoolean(encrypted);
            dos.flush();
        }
        return baos.toByteArray();
    }

    /**
     * Deserializes a binary byte array back into a TransferMetadata object.
     */
    public static TransferMetadata fromBytes(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (DataInputStream dis = new DataInputStream(bais)) {
            TransferMetadata meta = new TransferMetadata();
            meta.transferId = dis.readUTF();
            meta.senderId = dis.readUTF();
            meta.receiverId = dis.readUTF();
            meta.filename = dis.readUTF();
            meta.fileSize = dis.readLong();
            meta.chunkSize = dis.readInt();
            meta.totalChunks = dis.readLong();
            meta.expectedSha256 = dis.readUTF();
            meta.encrypted = dis.readBoolean();
            return meta;
        }
    }

    // Getters and Setters
    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public long getTotalChunks() { return totalChunks; }
    public void setTotalChunks(long totalChunks) { this.totalChunks = totalChunks; }

    public String getExpectedSha256() { return expectedSha256; }
    public void setExpectedSha256(String expectedSha256) { this.expectedSha256 = expectedSha256; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    @Override
    public String toString() {
        return "TransferMetadata{" +
                "transferId='" + transferId + '\'' +
                ", filename='" + filename + '\'' +
                ", fileSize=" + fileSize + " bytes" +
                ", totalChunks=" + totalChunks +
                ", encrypted=" + encrypted +
                ", sha256='" + expectedSha256 + '\'' +
                '}';
    }
}
