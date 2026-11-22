# Top 25 DCN Viva Questions & Comprehensive Answers
## Secure File Transfer Using Encryption and Integrity Verification

---

### Q1: What is the primary problem with traditional file transfer protocols like FTP (RFC 959) and standard HTTP?
**Answer:**
Traditional File Transfer Protocol (FTP) and cleartext HTTP transmit all control information (usernames, passwords) and payload data across the network in plaintext. Any node on the local broadcast domain or along the routing path running packet analysis tools like Wireshark can capture the raw TCP payload, compromising confidentiality. Furthermore, legacy FTP lacks built-in cryptographic integrity checks; an attacker in the middle (MitM) can modify packets in transit (e.g., bit flips, payload injection) without the receiver detecting the corruption.

---

### Q2: Why did you choose TCP over UDP for this file transfer project?
**Answer:**
TCP (Transmission Control Protocol, Layer 4) is connection-oriented, reliable, and byte-stream-based. File transfer requires:
1. **Guaranteed Delivery**: TCP utilizes Positive Acknowledgements (ACKs) and Automatic Repeat reQuest (ARQ) retransmissions to ensure zero packet loss.
2. **In-Order Delivery**: Sequence numbers guarantee that packets arriving out of order are reassembled correctly before reaching the application layer.
3. **Flow Control & Congestion Control**: TCP's sliding window mechanism prevents a fast sender from overwhelming a slow receiver or congesting intermediate network routers. UDP is unreliable and datagram-based; lost chunks would corrupt the file unless an entire transport-layer reliability stack was reinvented at the application layer.

---

### Q3: Why is a custom binary framing protocol necessary over a TCP socket? Why can't you just read until EOF?
**Answer:**
TCP is a **continuous, unstructured byte stream**, not a message-based protocol. TCP does not preserve application-layer message boundaries (a phenomenon known as stream fragmentation and concatenation). If a sender transmits a 100-byte metadata object followed immediately by file chunks, the underlying IP layer may split them across multiple MTUs or merge them into a single TCP segment.
Without framing:
- The receiver cannot determine where metadata ends and file content begins.
- Reading until EOF (`-1`) closes the socket, preventing bidirectional control signaling (such as sending back an integrity acknowledgement or error code).
Our custom 10-byte binary header (`[Magic: 4B][Version: 1B][Type: 1B][Length: 4B]`) tells the receiver exactly how many bytes to read for each frame.

---

### Q4: Explain the structure of your custom 10-byte binary framing header.
**Answer:**
The header consists of exactly 10 bytes:
1. **Bytes 0–3 (Magic Bytes: `0x44 0x43 0x4E 0x31` = "DCN1")**: Identifies our specific application protocol. Any connection sending malformed or non-DCN traffic is immediately rejected before allocating memory buffers.
2. **Byte 4 (Version: `0x01`)**: Protocol major version, enabling backward compatibility and schema evolution.
3. **Byte 5 (Message Type: 1 byte)**: Control opcode identifying the frame payload (e.g., `0x01` Metadata Request, `0x02` Metadata Ack, `0x03` Chunk Data, `0x04` Chunk Ack, `0x05` Transfer Complete, `0x06` Integrity Verified, `0x07` Integrity Failed).
4. **Bytes 6–9 (Payload Length: 4 bytes)**: A big-endian 32-bit unsigned integer specifying the exact byte length of the trailing payload (up to 64 KB).

---

### Q5: How does TLS 1.3 differ from TLS 1.2, and why did you enforce TLS 1.3?
**Answer:**
TLS 1.3 (RFC 8446) introduces major cryptographic improvements over TLS 1.2:
1. **1-RTT Handshake**: Reduces handshake latency from 2 round-trip times (RTT) to 1 RTT by sending key share parameters along with the `ClientHello`.
2. **Elimination of Legacy Ciphers**: Deprecated weak algorithms including static RSA key exchange (which lacks forward secrecy), CBC-mode ciphers (vulnerable to POODLE and Lucky13 attacks), RC4, MD5, and SHA-1.
3. **Mandatory AEAD Ciphers**: Enforces Authenticated Encryption with Associated Data (`TLS_AES_256_GCM_SHA384` and `TLS_CHACHA20_POLY1305_SHA256`).
4. **Encrypted Handshake**: Certificate exchange is encrypted, concealing server and client identities from passive eavesdroppers.

---

### Q6: What is Perfect Forward Secrecy (PFS), and how is it achieved in your system?
**Answer:**
Perfect Forward Secrecy ensures that even if a server's long-term private key is compromised in the future, past recorded encrypted communications cannot be decrypted.
In our system, TLS 1.3 accomplishes this by exclusively using **Ephemeral Elliptic Curve Diffie-Hellman (ECDHE)** for key exchange. Unique, temporary session keys are negotiated per connection and destroyed from memory immediately upon socket closure. Because the private key is never used to encrypt session keys directly, the recorded ciphertext remains permanently unbreakable.

---

### Q7: What is the difference between Symmetric and Asymmetric Cryptography in your architecture?
**Answer:**
- **Asymmetric Cryptography (Public Key)**: Uses key pairs (public and private). Used during the **TLS 1.3 handshake** to verify server authenticity (via X.509 certificates) and negotiate a shared secret via ECDHE without transmitting the secret over the wire.
- **Symmetric Cryptography (Secret Key)**: Uses a single shared key for both encryption and decryption. Used for **bulk data transmission** (`TLS_AES_256_GCM_SHA384`) and optional file encryption (`AES-256-GCM`) because symmetric encryption is thousands of times computationally faster than asymmetric operations.

---

### Q8: What is SHA-256, and why is it used instead of MD5 or CRC32 for integrity verification?
**Answer:**
SHA-256 (FIPS PUB 180-4) is a cryptographic hash function producing a deterministic 256-bit (32-byte) digest.
- **CRC32**: A non-cryptographic cyclic redundancy check designed purely for accidental burst transmission errors. It provides zero security against malicious tampering because an adversary can easily craft collision payloads with identical CRC32 values.
- **MD5**: Cryptographically broken; practical collision attacks can be generated within seconds on modern hardware ($2^{16}$ complexity).
- **SHA-256**: Offers pre-image resistance, second pre-image resistance, and strong collision resistance requiring $2^{128}$ operations, making deliberate forgery practically impossible.

---

### Q9: What is the "Avalanche Effect" in cryptographic hash functions?
**Answer:**
The Avalanche Effect is a fundamental property of secure cryptographic hashing and block ciphers where a minor change in the input (such as flipping a single bit) produces a drastically different and unpredictable output digest.
Mathematically, for a good cryptographic hash function, each output bit has a 50% probability of changing when any single input bit is flipped:
$$\text{Expected Divergence} \approx 50.0\% \quad (128 \text{ out of } 256 \text{ bits changed})$$
In our Tampering Simulation Lab, flipping 1 bit in transit produces an exact ~50% bit Hamming distance divergence, completely breaking the hash match.

---

### Q10: Why did you use `MessageDigest.isEqual()` instead of standard `String.equals()` for hash verification?
**Answer:**
Standard `String.equals()` or `Arrays.equals()` compares characters iteratively from left to right and **terminates immediately at the first non-matching byte**. This creates measurable variations in execution time depending on where the mismatch occurs.
An attacker running high-resolution timing measurements (a **Timing Attack**) can exploit this side channel to guess correct digest bytes one by one.
`MessageDigest.isEqual()` executes a **constant-time byte comparison** that always examines all 32 bytes regardless of where mismatches occur, preventing timing analysis.

---

### Q11: Explain how AES-256-GCM works and why Galois/Counter Mode (GCM) is preferred over CBC mode.
**Answer:**
AES-256-GCM is an **Authenticated Encryption with Associated Data (AEAD)** mode:
1. **Counter Mode (CTR) Encryption**: Transforms AES into a stream cipher by encrypting sequential counter blocks XORed with plaintext, allowing parallel processing.
2. **Galois Field (GHASH) Authentication**: Computes a 128-bit Message Authentication Code (MAC tag) over ciphertext.
- **CBC Mode Weakness**: Requires PKCS#7 padding, making it historically vulnerable to **Padding Oracle Attacks** (like Lucky13). It also requires an independent HMAC calculation (Encrypt-then-MAC).
- **GCM Advantage**: Provides both confidentiality and ciphertext integrity in a single authenticated pass, without padding vulnerabilities.

---

### Q12: Why is the Initialization Vector (IV) critical in AES-256-GCM, and what happens if an IV is reused?
**Answer:**
In GCM mode, the IV acts as the nonce for the counter. If the **same key and IV pair are ever reused** to encrypt two different files:
1. The XOR of the two ciphertexts equals the XOR of the two plaintexts ($C_1 \oplus C_2 = P_1 \oplus P_2$), destroying confidentiality.
2. An attacker can recover the internal GHASH authentication subkey $H$, allowing them to forge valid authentication tags for arbitrary packets.
Our implementation generates a cryptographically secure, random 12-byte (96-bit) IV via `SecureRandom` for every individual transfer session.

---

### Q13: How does your implementation achieve $O(1)$ memory consumption when transferring multi-gigabyte files?
**Answer:**
Rather than loading the entire file into a Java heap byte array (`byte[]`), the system utilizes **streaming I/O pipelines**:
- Sender reads from `FileInputStream` into a reusable 64 KB byte buffer and feeds `MessageDigest.update(buffer, 0, bytesRead)`.
- Chunks are framed and written directly to the `SSLSocket` output stream.
- Receiver reads 64 KB frames from the socket directly into a temporary file on disk via `BufferedOutputStream`.
The resident memory footprint remains bounded at $\le 64 \text{ KB}$ regardless of whether transferring 10 megabytes or 100 gigabytes.

---

### Q14: What is the purpose of the 64 KB chunk size? Why not 1 KB or 10 MB?
**Answer:**
- **Too Small (e.g., 1 KB)**: Incurs excessive protocol overhead (10 bytes of framing header + TLS record headers for every 1024 bytes) and triggers excessive system call / context switching overhead.
- **Too Large (e.g., 10 MB)**: Increases latency before the first chunk is sent, monopolizes socket buffers, and if a packet drop forces retransmission at the application layer, large amounts of data must be resent.
- **64 KB**: Matches standard TCP Receive Window (`RWIN`) sizes, aligns with modern OS page buffer caches, and optimizes network interface card (NIC) throughput.

---

### Q15: How does the system prevent Directory Traversal attacks (e.g., `../../etc/passwd`)?
**Answer:**
If an untrusted sender transmits a malicious filename containing path separators (e.g., `../../windows/system32/cmd.exe`), a naive receiver might save the file outside the designated sandbox.
Our server enforces strict sanitization in `TcpFileServer.sanitizeFilename()`:
1. `Paths.get(rawFilename).getFileName().toString()` isolates the bare filename, discarding all directory paths and traversal dots.
2. A regex replaces illegal filesystem characters (`[\\/:*?"<>|]`) with underscores (`_`).
3. The sanitized file is strictly written inside `./storage/temp/` and atomically moved to `./storage/received/`.

---

### Q16: What happens if an attacker tampers with a single byte of the file while in transit? Walk through the failure pipeline.
**Answer:**
1. Sender calculates `Hash_Sender = SHA256(Original_File)` and sends it inside `MSG_METADATA_REQ`.
2. The altered byte is received and written into `./storage/temp/<uuid>.tmp`.
3. Upon receiving `MSG_TRANSFER_COMPLETE`, the receiver streams the received temp file and computes `Hash_Receiver = SHA256(Temp_File)`.
4. `MessageDigest.isEqual(Hash_Sender, Hash_Receiver)` evaluates to `false`.
5. Receiver triggers an immediate **quarantine action**: deletes `./storage/temp/<uuid>.tmp` from the filesystem so the corrupt file is never accessible.
6. Receiver transmits `MSG_INTEGRITY_FAILED (0x07)` back to the sender.
7. Receiver logs a **CRITICAL** event in `security_events` table and flags system security posture as `ELEVATED_RISK`.

---

### Q17: How does the system handle an untrusted or rogue X.509 certificate?
**Answer:**
JSSE uses a `TrustManager` backed by a strict PKCS12 truststore (`client-truststore.p12`). During the TLS 1.3 handshake:
1. The server presents its X.509 certificate containing its public key and digital signature.
2. The client checks if the certificate's issuer CA exists in its trusted keystore.
3. If an attacker attempts an SSL-stripping or proxy MitM attack using an untrusted self-signed certificate, the `TrustManager` throws `SSLHandshakeException: PKIX path building failed`.
4. The socket is immediately closed before any application data or metadata is exchanged.

---

### Q18: What is Layer 4 TCP SYN Port Probing, and how is it used in Device Discovery?
**Answer:**
To verify whether a target receiver peer is online without attempting a full file transfer, the sender initiates a Layer 4 TCP connection probe (`DeviceService.probeTcpPort`):
1. Sender sends a TCP `SYN` packet to `Target_IP:8443` with a short timeout (e.g., 500 ms).
2. If the peer is active and listening, it replies with `SYN-ACK`. The probe establishes the socket, calculates round-trip latency, closes the socket with `FIN`, and marks the device **ONLINE**.
3. If the peer is powered off or unreachable, no response or a `RST` packet is returned, and the device is safely flagged as **OFFLINE**.

---

### Q19: What is AP Isolation (Client Isolation), and how does it impact peer-to-peer Wi-Fi file transfer?
**Answer:**
AP Isolation is a security setting on wireless routers (common in university or corporate guest Wi-Fi) where the access point prevents connected wireless stations from communicating directly with each other at Layer 2 (MAC layer). Even though both laptops are on the same subnet (e.g., `192.168.1.0/24`), ARP requests and unicast IP packets between them are dropped by the router firmware.
*Resolution*: Connect both laptops to a private smartphone Mobile Hotspot, which does not enforce AP isolation.

---

### Q20: Explain the role of ARP (Address Resolution Protocol) in this transfer.
**Answer:**
When Laptop 1 (`192.168.1.15`) wants to open a TCP socket to Laptop 2 (`192.168.1.22`):
1. Laptop 1 inspects its subnet mask and recognizes Laptop 2 is on the same local subnet.
2. Laptop 1 broadcasts an **ARP Request** frame at Layer 2: *"Who has 192.168.1.22? Tell 192.168.1.15"*.
3. Laptop 2 receives the broadcast and replies with a unicast **ARP Reply** containing its 48-bit hardware MAC address (`xx:xx:xx:xx:xx:xx`).
4. Laptop 1 caches this mapping in its ARP table and encapsulates subsequent IP/TCP packets into Ethernet/802.11 frames addressed to Laptop 2's MAC address.

---

### Q21: What is RFC 7519 JSON Web Token (JWT), and how does your system validate it?
**Answer:**
A JWT is a compact, URL-safe means of representing claims between two parties consisting of three parts: `Header.Payload.Signature`.
1. **Header**: Specifies the hashing algorithm (`HS256`).
2. **Payload**: JSON claims containing `sub` (User UUID), `email`, and `exp` (expiration timestamp).
3. **Signature**: Cryptographic HMAC-SHA256 signature calculated over `Base64(Header) + "." + Base64(Payload)` using the Supabase JWT secret.
When Next.js sends `Authorization: Bearer <token>`, `JwtUtil.java` verifies the HMAC signature and ensures `System.currentTimeMillis() < exp * 1000`.

---

### Q22: What is Row Level Security (RLS) in PostgreSQL, and why is it important in a distributed transfer app?
**Answer:**
Standard relational databases grant global `SELECT`/`INSERT` permissions per table based on database roles.
Supabase PostgreSQL **Row Level Security (RLS)** enforces security policies at the database engine level for individual rows:
```sql
CREATE POLICY "Users view their transfers" ON transfers
FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);
```
Even if an unauthorized user crafts a custom SQL or REST query, PostgreSQL automatically filters the result set to only records where the user's authenticated UUID matches `sender_id` or `receiver_id`, preventing data leakage between students.

---

### Q23: Why do we perform an "atomic commit" (rename) of the received file only after integrity verification?
**Answer:**
Writing directly to the target destination filename (`/storage/received/final_report.pdf`) during streaming creates a **Time-of-Check to Time-of-Use (TOCTOU)** vulnerability:
- A local background process or user might open a partially downloaded or corrupt file before transfer completion.
- If the network disconnects midway, a broken file remains in the directory.
By writing chunks to a temporary quarantine directory (`/storage/temp/<uuid>.tmp`) and performing an atomic OS rename (`Files.move()`) only after SHA-256 verification returns `true`, we guarantee that every file in `/storage/received/` is 100% complete, authentic, and verified.

---

### Q24: What happens if the network cable is unplugged or Wi-Fi drops mid-transfer?
**Answer:**
1. The TCP layer attempts retransmissions until `TCP_TIMEOUT` expires.
2. The Java `SSLSocket` detects socket termination and throws `SocketException: Connection reset` or `EOFException`.
3. The server catches the exception in `TlsFileServer`, closes all stream handles, and executes a cleanup routine deleting the orphaned `.tmp` file from disk.
4. The transfer record status in the database is updated to `FAILED` or `CANCELLED`, preventing orphaned or zombie locks.

---

### Q25: How does your system prevent Replay Attacks?
**Answer:**
A Replay Attack occurs when an eavesdropper records valid ciphertext packets and re-transmits them later to deceive the receiver.
1. **At Layer 5/6 (TLS 1.3)**: Every TLS record has an implicit 64-bit sequence number incremented for each record. TLS AEAD decryption fails if packets arrive duplicated, modified, or out of sequence. Handshake nonces prevent handshake replay.
2. **At Layer 7 (Application Protocol)**: Every chunk frame carries a sequential 64-bit sequence number (`seqNumber`). The receiver enforces monotonic incrementation (`expectedSeq == seqNumber`), rejecting duplicate or replayed chunk frames.
