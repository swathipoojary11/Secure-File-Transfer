package com.dcn.securefiletransfer.security.tls;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.MessageDigest;
import java.security.cert.Certificate;

/**
 * Encapsulates Active TLS 1.3 Session Diagnostic Metadata.
 * 
 * Networking Viva Explanation:
 * In OSI Layer 6 (Presentation/Security Layer), after the TLS 1.3 cryptographic
 * handshake completes, this class queries the established SSLSession to expose:
 * 1. Protocol Version (e.g. TLSv1.3)
 * 2. Negotiated Cipher Suite (e.g. TLS_AES_256_GCM_SHA384)
 * 3. Peer Certificate Subject & Issuer (X.500 Distinguished Name)
 * 4. SHA-256 Public Certificate Fingerprint for strict identity pinning
 */
public class TlsSessionDetails {

    private String protocol;
    private String cipherSuite;
    private String peerHost;
    private int peerPort;
    private String peerPrincipal;
    private String peerCertificateFingerprint;
    private boolean certificateVerified;
    private boolean established;

    public TlsSessionDetails() {
    }

    /**
     * Extracts TLS session properties from an active SSLSocket.
     */
    public static TlsSessionDetails fromSslSocket(SSLSocket socket) {
        TlsSessionDetails details = new TlsSessionDetails();
        SSLSession session = socket.getSession();

        details.setProtocol(session.getProtocol());
        details.setCipherSuite(session.getCipherSuite());
        details.setPeerHost(socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "unknown");
        details.setPeerPort(socket.getPort());
        details.setEstablished(session.isValid());

        try {
            Certificate[] peerCerts = session.getPeerCertificates();
            if (peerCerts != null && peerCerts.length > 0) {
                details.setPeerPrincipal(session.getPeerPrincipal().getName());
                details.setPeerCertificateFingerprint(computeSha256Fingerprint(peerCerts[0]));
                details.setCertificateVerified(true);
            } else {
                details.setPeerPrincipal("Anonymous (No Peer Certificate)");
                details.setPeerCertificateFingerprint("N/A");
                details.setCertificateVerified(false);
            }
        } catch (SSLPeerUnverifiedException e) {
            details.setPeerPrincipal("Unverified Peer");
            details.setPeerCertificateFingerprint("N/A");
            details.setCertificateVerified(false);
        }

        return details;
    }

    /**
     * Computes the colon-separated SHA-256 fingerprint of an X.509 certificate.
     */
    public static String computeSha256Fingerprint(Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                sb.append(String.format("%02X", digest[i]));
                if (i < digest.length - 1) {
                    sb.append(":");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "FINGERPRINT_ERROR";
        }
    }

    // Getters and Setters
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getCipherSuite() { return cipherSuite; }
    public void setCipherSuite(String cipherSuite) { this.cipherSuite = cipherSuite; }

    public String getPeerHost() { return peerHost; }
    public void setPeerHost(String peerHost) { this.peerHost = peerHost; }

    public int getPeerPort() { return peerPort; }
    public void setPeerPort(int peerPort) { this.peerPort = peerPort; }

    public String getPeerPrincipal() { return peerPrincipal; }
    public void setPeerPrincipal(String peerPrincipal) { this.peerPrincipal = peerPrincipal; }

    public String getPeerCertificateFingerprint() { return peerCertificateFingerprint; }
    public void setPeerCertificateFingerprint(String peerCertificateFingerprint) { this.peerCertificateFingerprint = peerCertificateFingerprint; }

    public boolean isCertificateVerified() { return certificateVerified; }
    public void setCertificateVerified(boolean certificateVerified) { this.certificateVerified = certificateVerified; }

    public boolean isEstablished() { return established; }
    public void setEstablished(boolean established) { this.established = established; }

    @Override
    public String toString() {
        return "TlsSessionDetails{" +
                "protocol='" + protocol + '\'' +
                ", cipherSuite='" + cipherSuite + '\'' +
                ", peer=" + peerHost + ":" + peerPort +
                ", principal='" + peerPrincipal + '\'' +
                ", fingerprint='" + peerCertificateFingerprint + '\'' +
                ", verified=" + certificateVerified +
                '}';
    }
}
