package com.dcn.securefiletransfer.security.tls;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * JSSE TLS 1.3 Context & Socket Factory.
 * 
 * Networking Viva Explanation:
 * - Bootstraps the Java Secure Socket Extension (JSSE) SSLContext.
 * - Enforces TLS 1.3 exclusively (RFC 8446), removing obsolete, vulnerable
 *   ciphers (RC4, 3DES, CBC mode) and enforcing Ephemeral Diffie-Hellman (ECDHE)
 *   to ensure Perfect Forward Secrecy (PFS).
 * - Implements strict certificate path validation using PKCS12 truststores
 *   (Strictly avoids blind Trust-All TrustManagers).
 */
public final class TlsContextFactory {

    public static final String TLS_PROTOCOL_VERSION = "TLSv1.3";

    // Modern Authenticated Encryption with Associated Data (AEAD) Cipher Suites
    public static final String[] SUPPORTED_CIPHER_SUITES = new String[] {
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256"
    };

    private TlsContextFactory() {
        // Utility class
    }

    /**
     * Creates a server-side SSLContext using a PKCS12 keystore.
     */
    public static SSLContext createServerSslContext(File keystoreFile, String password) throws Exception {
        return createSslContext(keystoreFile, password, null, null);
    }

    /**
     * Creates a client-side SSLContext using a PKCS12 truststore.
     */
    public static SSLContext createClientSslContext(File truststoreFile, String password) throws Exception {
        return createSslContext(null, null, truststoreFile, password);
    }

    /**
     * Initializes a custom SSLContext configured with optional KeyManager and TrustManager.
     */
    public static SSLContext createSslContext(File keystoreFile, String keystorePassword,
                                             File truststoreFile, String truststorePassword) throws Exception {
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        // 1. Initialize KeyManager if keystore is provided (Server Identity)
        if (keystoreFile != null && keystoreFile.exists()) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(keystoreFile)) {
                keyStore.load(is, keystorePassword != null ? keystorePassword.toCharArray() : new char[0]);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword != null ? keystorePassword.toCharArray() : new char[0]);
            keyManagers = kmf.getKeyManagers();
        }

        // 2. Initialize TrustManager if truststore is provided (Peer Certificate Validation)
        if (truststoreFile != null && truststoreFile.exists()) {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(truststoreFile)) {
                trustStore.load(is, truststorePassword != null ? truststorePassword.toCharArray() : new char[0]);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }

        // 3. Create SSLContext enforcing TLS 1.3
        SSLContext sslContext = SSLContext.getInstance(TLS_PROTOCOL_VERSION);
        sslContext.init(keyManagers, trustManagers, new SecureRandom());

        return sslContext;
    }

    /**
     * Creates and configures an SSLServerSocket enforcing TLS 1.3.
     */
    public static SSLServerSocket createSslServerSocket(int port, SSLContext sslContext) throws IOException {
        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket();

        // Enforce TLS 1.3 exclusively
        serverSocket.setEnabledProtocols(new String[] { TLS_PROTOCOL_VERSION });

        // Enforce modern AEAD cipher suites
        serverSocket.setEnabledCipherSuites(SUPPORTED_CIPHER_SUITES);

        // Allow immediate socket address reuse upon reboot
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));

        return serverSocket;
    }

    /**
     * Connects an SSLSocket to a remote peer and performs the TLS 1.3 handshake.
     */
    public static SSLSocket createSslClientSocket(String host, int port, SSLContext sslContext,
                                                 int connectTimeoutMs, int socketTimeoutMs) throws IOException {
        SSLSocketFactory sf = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) sf.createSocket();

        // Socket options
        sslSocket.setTcpNoDelay(true); // Disable Nagle's algorithm
        sslSocket.setSoTimeout(socketTimeoutMs);

        // Enforce TLS 1.3
        sslSocket.setEnabledProtocols(new String[] { TLS_PROTOCOL_VERSION });
        sslSocket.setEnabledCipherSuites(SUPPORTED_CIPHER_SUITES);

        // Connect with timeout
        sslSocket.connect(new InetSocketAddress(host, port), connectTimeoutMs);

        // Explicitly trigger the TLS 1.3 cryptographic handshake
        sslSocket.startHandshake();

        return sslSocket;
    }
}
