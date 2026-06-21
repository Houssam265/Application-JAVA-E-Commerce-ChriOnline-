package com.chrionline;

import com.chrionline.security.TlsSupport;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test TLS bootstrap du serveur (SSLServerSocket, protocoles, suites).
 * <p>
 * Exécution : {@code mvn test -Dtest=ServerTlsTest}
 */
class ServerTlsTest {

    @Test
    void serverTlsSocketProtocolsAndCiphers() throws Exception {
        String envPath = System.getenv("CHRIONLINE_TLS_KEYSTORE_PATH");
        Path keystore = Path.of(envPath != null && !envPath.isBlank() ? envPath : "config/tls/server-keystore.p12")
                .toAbsolutePath()
                .normalize();

        assumeTrue(Files.isRegularFile(keystore),
                "Keystore absent : " + keystore + " — exécutez config/tls/setup-tls.ps1 ou définissez CHRIONLINE_TLS_KEYSTORE_PATH.");

        SSLServerSocket ss = null;
        try {
            ss = TlsSupport.createServerSocket(0);

            assertInstanceOf(SSLServerSocket.class, ss);

            String[] protocols = ss.getEnabledProtocols();
            boolean hasTls13 = Arrays.asList(protocols).contains("TLSv1.3");
            assertTrue(hasTls13,
                    "TLSv1.3 doit être dans les protocoles activés. Actuels : " + Arrays.toString(protocols));

            String[] ciphers = ss.getEnabledCipherSuites();
            assertTrue(ciphers.length > 0, "Au moins une suite doit être activée.");

            for (String cs : ciphers) {
                String u = cs.toUpperCase();
                assertFalse(u.contains("RC4"), "Suite faible (RC4) ne doit pas être activée : " + cs);
                assertFalse(u.contains("MD5"), "Suite faible (MD5) ne doit pas être activée : " + cs);
                assertFalse(u.contains("_DES_") || u.contains("DES40") || u.endsWith("_DES"),
                        "Suite faible (DES) ne doit pas être activée : " + cs);
            }

            System.out.println();
            System.out.println("========== TLS ServerSocket (ephemeral port) ==========");
            System.out.println("Enabled protocols:");
            for (String p : protocols) {
                System.out.println("  - " + p);
            }
            System.out.println("Enabled cipher suites (" + ciphers.length + "):");
            for (String c : ciphers) {
                System.out.println("  - " + c);
            }
            System.out.println("==============================================================");
            System.out.println();
        } finally {
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
        }
    }
}
