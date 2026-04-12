package com.chrionline.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Centralizes TLS configuration for the TCP client/server channel.
 */
public final class TlsSupport {

    private static final Logger LOG = LogManager.getLogger(TlsSupport.class);

    public static final String DEFAULT_PROTOCOL = "TLS";
    public static final String DEFAULT_KEYSTORE_PATH = "config/tls/server-keystore.p12";
    public static final String DEFAULT_TRUSTSTORE_PATH = "config/tls/client-truststore.p12";
    public static final String DEFAULT_STORE_TYPE = "PKCS12";
    public static final String DEFAULT_STORE_PASSWORD = "changeit";

    private TlsSupport() {}

    public static SSLServerSocket createServerSocket(int port) throws IOException, GeneralSecurityException {
        SSLContext context = buildServerContext();
        SSLServerSocketFactory factory = context.getServerSocketFactory();
        SSLServerSocket socket = (SSLServerSocket) factory.createServerSocket(port);
        socket.setEnabledProtocols(selectProtocols(socket.getSupportedProtocols()));
        socket.setNeedClientAuth(false);
        return socket;
    }

    public static Socket createClientSocket(String host, int port, int timeoutMs) throws IOException, GeneralSecurityException {
        SSLContext context = buildClientContext();
        SSLSocketFactory factory = context.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        socket.setEnabledProtocols(selectProtocols(socket.getSupportedProtocols()));

        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
        socket.startHandshake();
        return socket;
    }

    public static String describeServerConfiguration() {
        return "TLS actif (keystore=" + resolvePath("CHRIONLINE_TLS_KEYSTORE_PATH", DEFAULT_KEYSTORE_PATH).toAbsolutePath().normalize() + ")";
    }

    public static String describeClientConfiguration() {
        return "TLS actif (truststore=" + resolvePath("CHRIONLINE_TLS_TRUSTSTORE_PATH", DEFAULT_TRUSTSTORE_PATH).toAbsolutePath().normalize() + ")";
    }

    private static SSLContext buildServerContext() throws IOException, GeneralSecurityException {
        KeyStore keyStore = loadStore(
            resolvePath("CHRIONLINE_TLS_KEYSTORE_PATH", DEFAULT_KEYSTORE_PATH),
            readSetting("CHRIONLINE_TLS_KEYSTORE_TYPE", DEFAULT_STORE_TYPE),
            readPassword("CHRIONLINE_TLS_KEYSTORE_PASSWORD", DEFAULT_STORE_PASSWORD)
        );

        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        char[] password = readPassword("CHRIONLINE_TLS_KEYSTORE_PASSWORD", DEFAULT_STORE_PASSWORD).toCharArray();
        keyManagers.init(keyStore, password);

        SSLContext context = SSLContext.getInstance(readSetting("CHRIONLINE_TLS_PROTOCOL", DEFAULT_PROTOCOL));
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    private static SSLContext buildClientContext() throws IOException, GeneralSecurityException {
        KeyStore trustStore = loadStore(
            resolvePath("CHRIONLINE_TLS_TRUSTSTORE_PATH", DEFAULT_TRUSTSTORE_PATH),
            readSetting("CHRIONLINE_TLS_TRUSTSTORE_TYPE", DEFAULT_STORE_TYPE),
            readPassword("CHRIONLINE_TLS_TRUSTSTORE_PASSWORD", DEFAULT_STORE_PASSWORD)
        );

        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);

        SSLContext context = SSLContext.getInstance(readSetting("CHRIONLINE_TLS_PROTOCOL", DEFAULT_PROTOCOL));
        context.init(null, trustManagers.getTrustManagers(), null);
        return context;
    }

    private static KeyStore loadStore(Path path, String type, String password) throws IOException, GeneralSecurityException {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("Fichier TLS introuvable: " + path.toAbsolutePath().normalize());
        }

        KeyStore store = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(path)) {
            store.load(in, password.toCharArray());
        }
        LOG.debug("TLS store charge: {}", path.toAbsolutePath().normalize());
        return store;
    }

    private static Path resolvePath(String key, String fallback) {
        return Path.of(readSetting(key, fallback));
    }

    private static String readPassword(String key, String fallback) {
        return readSetting(key, fallback);
    }

    private static String readSetting(String key, String fallback) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }

        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        return fallback;
    }

    private static String[] selectProtocols(String[] supportedProtocols) {
        boolean hasTls13 = false;
        boolean hasTls12 = false;
        for (String protocol : supportedProtocols) {
            if ("TLSv1.3".equals(protocol)) {
                hasTls13 = true;
            } else if ("TLSv1.2".equals(protocol)) {
                hasTls12 = true;
            }
        }

        if (hasTls13 && hasTls12) {
            return new String[]{"TLSv1.3", "TLSv1.2"};
        }
        if (hasTls13) {
            return new String[]{"TLSv1.3"};
        }
        if (hasTls12) {
            return new String[]{"TLSv1.2"};
        }
        return supportedProtocols;
    }
}
