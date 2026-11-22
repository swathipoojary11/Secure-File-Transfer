# Two-Laptop Wi-Fi Deployment & Live Demo Guide
## Data Communication and Networks (DCN) Mini-Project

This guide provides step-by-step instructions for deploying and running a live peer-to-peer file transfer demonstration between **two separate laptops** over a local Wi-Fi router or Mobile Hotspot.

---

### 1. Network Topology & Prerequisites

```
                      [ Wi-Fi Router / Mobile Hotspot ]
                               (Subnet: 192.168.1.0/24)
                                    /           \
                                   /             \
                       192.168.1.15               192.168.1.22
                     [ LAPTOP 1: SENDER ]       [ LAPTOP 2: RECEIVER ]
                     - Frontend: Port 3000      - Frontend: Port 3000
                     - Backend:  Port 8085      - Backend:  Port 8085
                                                - TLS Socket: Port 8443 (LISTENING)
```

#### Prerequisites
- Both laptops connected to the **same Wi-Fi network** or a **Mobile Hotspot**.
  > [!IMPORTANT]
  > College or public campus Wi-Fi networks often enable **AP Isolation (Client Isolation)**, which blocks peer-to-peer device communication. If `ping` or connection fails, use a smartphone **Mobile Hotspot**.
- Java Development Kit (JDK) 21 installed on both machines.
- Node.js (v18+) and npm installed on both machines.
- Git or USB drive to copy the project repository to both laptops.

---

### 2. Step 1: Find Local IP Addresses (Both Laptops)

On **both Laptop 1 and Laptop 2**, open PowerShell and run:

```powershell
ipconfig
```

Look for the active Wi-Fi adapter:
- **IPv4 Address**: e.g., `192.168.1.15` (Laptop 1) and `192.168.1.22` (Laptop 2).
- Write down Laptop 2's IP address (the **Receiver IP**).

Test basic network layer (ICMP) connectivity from Laptop 1 to Laptop 2:
```powershell
ping 192.168.1.22
```

---

### 3. Step 2: Configure Windows Defender Firewall (Laptop 2 - Receiver)

By default, Windows Defender Firewall blocks unsolicited incoming TCP connections on non-standard ports. 

On **Laptop 2 (Receiver)**, open **PowerShell as Administrator** and execute:

```powershell
# Open inbound TCP port 8443 for TLS 1.3 socket transfers
New-NetFirewallRule -DisplayName "DCN Secure File Transfer TLS Port 8443" `
    -Direction Inbound `
    -LocalPort 8443 `
    -Protocol TCP `
    -Action Allow

# (Optional) Open inbound TCP port 8085 for REST API access across LAN
New-NetFirewallRule -DisplayName "DCN Secure File Transfer REST Port 8085" `
    -Direction Inbound `
    -LocalPort 8085 `
    -Protocol TCP `
    -Action Allow
```

---

### 4. Step 3: Start the System on Laptop 2 (Receiver)

On **Laptop 2**, start both the Spring Boot backend and Next.js frontend:

#### Terminal 1: Backend (Spring Boot + TLS 1.3 Socket Server)
```powershell
cd d:\DCN\secure-file-transfer\backend
mvn spring-boot:run
```
*Verification*: Ensure console logs show:
```
[TLS SERVER] Initializing TLS 1.3 File Receive Server on port 8443...
[TLS SERVER] Listening on port 8443 with TLS 1.3 enabled.
Tomcat started on port 8085 (http)
```

#### Terminal 2: Frontend (Next.js)
```powershell
cd d:\DCN\secure-file-transfer\frontend
npm run start
# or 'npm run dev' if in development mode
```
Open browser on Laptop 2 at: `http://localhost:3000`

---

### 5. Step 4: Start the System on Laptop 1 (Sender)

On **Laptop 1**, start the application:

#### Terminal 1: Backend
```powershell
cd d:\DCN\secure-file-transfer\backend
mvn spring-boot:run
```

#### Terminal 2: Frontend
```powershell
cd d:\DCN\secure-file-transfer\frontend
npm run start
```
Open browser on Laptop 1 at: `http://localhost:3000`

---

### 6. Step 5: Perform the Live Wi-Fi File Transfer

1. **Register Target Peer**:
   - On Laptop 1, navigate to **Dashboard** or **Send Files** (`/send`).
   - Under **Target Peer Configuration**, enter Laptop 2's LAN IP:
     - **IP Address**: `192.168.1.22`
     - **TLS Port**: `8443`
     - **Peer Name**: `Laptop 2 (Receiver)`
   - Click **Save & Ping Target**.
   - The Layer 4 TCP SYN probe verifies connectivity and displays a green **ONLINE (Reachable)** badge with measured round-trip latency.

2. **Upload and Send File**:
   - Drag and drop or browse to select a file (e.g., `DCN_Research_Paper.pdf`).
   - Notice the UI immediately computes the local client SHA-256 digest in the browser.
   - (Optional) Toggle **AES-256-GCM Envelope Encryption** for secondary payload security.
   - Click **Transfer File over TLS 1.3**.

3. **Observe Real-Time Transfer**:
   - The transfer progress bar tracks chunk transmission in real time.
   - On Laptop 2's terminal, observe:
     ```
     [TLS SERVER] Secure Handshake Established: TLSv1.3 | TLS_AES_256_GCM_SHA384
     [TLS SERVER] Receiving metadata for: DCN_Research_Paper.pdf (1,248,512 bytes)
     [TLS SERVER] Streamed 19 chunks (64 KB each)
     [TLS SERVER] File hash calculated: a3f8... Match: VERIFIED
     [TLS SERVER] Atomic commit -> storage/received/DCN_Research_Paper.pdf
     ```

4. **Verify and Download on Receiver**:
   - On Laptop 2, navigate to **Receive Files** (`/receive`).
   - The received file appears in the table with status `VERIFIED` and a green checkmark.
   - Click **Download** to save and open the file on Laptop 2.

---

### 7. Step 6: Live Viva Demonstration of Tampering Attack

To demonstrate how the system catches tampering in real-time before external examiners:

1. On Laptop 1, navigate to **Tampering Lab** (`/tampering-lab`).
2. Upload any test file (e.g., `sample.txt`).
3. Click **Simulate Case A (Valid Transfer)**:
   - Status: **VERIFIED** (Sender and Receiver hashes match 100%).
4. Click **Simulate Case B (1-Bit Tamper Attack)**:
   - System alters exactly 1 bit in transit.
   - Receiver recalculates SHA-256.
   - Status: **FAILED - TAMPERED**.
   - Note the **50.0% Avalanche Effect** bit divergence displayed on screen.
   - Navigate to **Security Center** (`/security`) to show the real-time **CRITICAL Security Event** logged with timestamp, source IP, and posture change.

---

### 8. Troubleshooting Checklist

| Issue | Root Cause | Solution |
| :--- | :--- | :--- |
| **Ping fails / Peer OFFLINE** | Wi-Fi AP Isolation is enabled on the college router. | Connect both laptops to a smartphone **Mobile Hotspot**. |
| **Ping succeeds but transfer hangs** | Windows Defender Firewall blocking port 8443 on Receiver. | Run the `New-NetFirewallRule` command in Step 2 on Laptop 2 as Administrator. |
| **SSLHandshakeException** | Truststore does not contain the server certificate. | Ensure both laptops are using the project's included `client-truststore.p12` containing `localhost` / SAN certificate. |
| **Connection Refused** | Backend is not running on Laptop 2 or crashed. | Check Laptop 2 terminal; ensure `[TLS SERVER] Listening on port 8443` is printed. |
| **IP Address changed** | DHCP lease expired or Wi-Fi reconnected. | Re-run `ipconfig` on Laptop 2 to verify the latest assigned IPv4 address. |
