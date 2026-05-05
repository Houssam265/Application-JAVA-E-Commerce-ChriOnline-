package com.chrionline.client;

import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;
import com.chrionline.security.AESUtil;
import com.chrionline.security.HybridCryptoUtil;
import com.chrionline.security.RSAUtil;
import com.chrionline.security.TlsSupport;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import javax.crypto.SecretKey;

/**
 * Couche réseau TCP du client ChriOnline.
 *
 * Responsabilités :
 *  - Ouvrir / fermer la connexion TCP vers le serveur (port 8080)
 *  - Envoyer des {@link Request} JSON et recevoir des {@link Response} JSON
 *  - Stocker le token de session après un LOGIN réussi
 *
 * Usage (dans un Controller JavaFX) :
 * <pre>
 *   Client client = Client.getInstance();
 *   client.connect();
 *   Response r = client.send(new Request(MessageProtocol.ACTION_LOGIN, payload));
 * </pre>
 *
 * Pattern Singleton : une seule connexion TCP partagée entre tous les écrans.
 */
public class Client {

    private static final Logger LOG = LogManager.getLogger(Client.class);

    // ── Configuration ────────────────────────────────────────────────────────
    private static final String HOST            = "localhost";
    private static final int    PORT            = 8080;
    private static final int    TIMEOUT_MS      = 15_000; // 15 secondes
    private static final int    RECONNECT_ATTEMPTS = 3;
    private static final int    RECONNECT_BACKOFF_MS = 300;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static Client instance;

    public static Client getInstance() {
        if (instance == null) {
            instance = new Client();
        }
        return instance;
    }

    // ── État de la connexion ──────────────────────────────────────────────────
    private Socket         socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private UDPNotificationListener udpListener;

    /** Token de session récupéré après LOGIN — null si non connecté. */
    private String sessionToken;

    /** Cle publique RSA du serveur recue lors du handshake HELLO (Task 2). */
    private PublicKey serverRsaPublicKey;

    private Client() {}

    // ── Connexion ─────────────────────────────────────────────────────────────

    /**
     * Ouvre la connexion TCP vers le serveur.
     *
     * @throws IOException si le serveur est injoignable
     */
    public void connect() throws IOException {
        if (isConnected()) return;
        connectWithRetry();
    }

    /**
     * Ferme proprement la connexion TCP.
     */
    public void disconnect() {
        sessionToken = null;
        serverRsaPublicKey = null;
        if (udpListener != null) {
            udpListener.close();
            udpListener = null;
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        writer = null;
        reader = null;
    }

    /**
     * @return true si le socket est ouvert et connecté.
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    private void connectOnce() throws IOException {
        try {
            socket = TlsSupport.createClientSocket(HOST, PORT, TIMEOUT_MS);
        } catch (GeneralSecurityException e) {
            throw new IOException("Impossible d'etablir la connexion TLS avec le serveur.", e);
        }

        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        LOG.info("Connexion client etablie vers {}:{} avec {}", HOST, PORT, TlsSupport.describeClientConfiguration());

        readHelloFrame();

        startUdpListener();
    }

    /**
     * Lit la premiere ligne envoyee par le serveur juste apres le handshake TLS.
     * Cette ligne est un message {@code HELLO} contenant la cle publique RSA du serveur,
     * utilisee plus tard pour le handshake hybride RSA -> AES.
     */
    private void readHelloFrame() throws IOException {
        String helloLine = reader.readLine();
        if (helloLine == null || helloLine.isBlank()) {
            throw new IOException("Le serveur n'a pas envoye de message HELLO.");
        }
        try {
            Response hello = Response.fromJson(helloLine);
            if (!hello.isSuccess() || !MessageProtocol.MESSAGE_HELLO.equals(hello.getMessage())) {
                throw new IOException("Message HELLO invalide: " + hello.getMessage());
            }
            JSONObject payload = hello.getPayloadAsJsonObject();
            String publicKeyB64 = payload.optString("serverPublicKey", null);
            if (publicKeyB64 == null || publicKeyB64.isBlank()) {
                throw new IOException("Cle publique RSA absente du HELLO.");
            }
            this.serverRsaPublicKey = RSAUtil.decodePublicKey(publicKeyB64);
            LOG.info("[HELLO] Cle publique RSA serveur recue ({} bits)", payload.optInt("keySize", 2048));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Impossible de decoder le HELLO du serveur: " + e.getMessage(), e);
        }
    }

    private void connectWithRetry() throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= RECONNECT_ATTEMPTS; attempt++) {
            try {
                connectOnce();
                return;
            } catch (IOException e) {
                last = e;
                disconnect();
                if (attempt < RECONNECT_ATTEMPTS) {
                    try {
                        Thread.sleep(RECONNECT_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new IOException("Serveur indisponible. Verifiez qu'il est demarre.", last);
    }

    private void startUdpListener() {
        if (udpListener == null) {
            udpListener = new UDPNotificationListener();
        }
        if (!udpListener.isRunning()) {
            try {
                udpListener.start();
            } catch (IOException e) {
                LOG.warn("[UDP] Notifications desactivees: {}", e.getMessage(), e);
            }
        }
    }

    // ── Envoi / Réception ────────────────────────────────────────────────────

    /**
     * Envoie une requête et attend la réponse du serveur (bloquant).
     *
     * Doit être appelé depuis un thread non-UI (Task / Thread JavaFX).
     *
     * @param request la requête à envoyer
     * @return la réponse désérialisée du serveur
     * @throws IOException en cas de problème réseau ou timeout
     */
    public Response send(Request request) throws IOException {
        try {
            if (!isConnected()) {
                connectWithRetry();
            }
            return sendOnce(request);
        } catch (SocketTimeoutException e) {
            disconnect();
            throw new IOException("Delai d'attente depasse - le serveur ne repond pas.", e);
        } catch (IOException e) {
            disconnect();
            if (e instanceof java.net.ConnectException
                    || e instanceof java.net.NoRouteToHostException
                    || e instanceof java.net.UnknownHostException) {
                throw new IOException("Serveur indisponible. Verifiez qu'il est demarre.", e);
            }
            throw e;
        }
    }

    private Response sendOnce(Request request) throws IOException {
        if (!isConnected()) {
            throw new IOException("Non connecte au serveur.");
        }

        // Envoi de la requete JSON (newline-delimited)
        writer.write(request.toJson()); // toJson() inclut deja '\n'
        writer.flush();

        // Lecture de la reponse ligne par ligne
        String responseLine = reader.readLine();

        if (responseLine == null) {
            throw new IOException("Le serveur a ferme la connexion.");
        }

        Response response = Response.fromJson(responseLine);

        // Mise a jour du token de session apres un LOGIN reussi
        if (response.isSuccess() && response.getToken() != null) {
            this.sessionToken = response.getToken();
        }
        return response;
    }

    // ── Hybride RSA -> AES (Task 2) ───────────────────────────────────────────

    /** @return la cle publique RSA recue lors du HELLO, ou {@code null} si non connecte. */
    public PublicKey getServerRsaPublicKey() {
        return serverRsaPublicKey;
    }

    /**
     * Envoi hybride RSA -> AES :
     * <ol>
     *   <li>Genere une cle AES-256 fraiche (module AES "Achraf").</li>
     *   <li>Chiffre cette cle avec la cle publique RSA du serveur (recue via HELLO).</li>
     *   <li>Ajoute {@code encryptedAesKey} au payload, puis envoie la requete via SSLSocket.</li>
     *   <li>Si le serveur repond avec {@code encryptedPayload + aesIv}, dechiffre le payload
     *       en AES-GCM et retourne une nouvelle {@link Response} avec le payload en clair.</li>
     * </ol>
     *
     * <p>Sans cle RSA disponible, retombe automatiquement sur {@link #send(Request)}.
     */
    public Response sendHybrid(Request request) throws IOException {
        if (serverRsaPublicKey == null) {
            LOG.warn("[HYBRID] Cle publique RSA serveur indisponible - bascule sur envoi clair.");
            return send(request);
        }
        try {
            SecretKey aesKey = AESUtil.generateKey();
            String wrappedAesKey = HybridCryptoUtil.wrapAesKey(aesKey, serverRsaPublicKey);
            if (request.getPayload() != null) {
                request.getPayload().put(MessageProtocol.KEY_ENCRYPTED_AES_KEY, wrappedAesKey);
            }
            Response response = send(request);
            if (response == null || !response.isSuccess()) return response;

            JSONObject payload = response.getPayloadAsJsonObject();
            String encrypted = payload.optString(MessageProtocol.KEY_ENCRYPTED_PAYLOAD, null);
            String iv        = payload.optString(MessageProtocol.KEY_AES_IV, null);
            if (encrypted == null || encrypted.isBlank() || iv == null || iv.isBlank()) {
                return response;
            }

            String plainJson = AESUtil.decrypt(aesKey, encrypted, iv);
            Object decryptedPayload;
            String trimmed = plainJson.trim();
            if (trimmed.startsWith("{")) {
                decryptedPayload = new JSONObject(trimmed);
            } else if (trimmed.startsWith("[")) {
                decryptedPayload = new org.json.JSONArray(trimmed);
            } else {
                decryptedPayload = trimmed;
            }
            LOG.info("[HYBRID] Payload AES-GCM dechiffre avec succes ({} octets clair)", plainJson.length());
            return new Response(true, response.getMessage(), decryptedPayload, response.getToken());
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException("Echec du chiffrement hybride RSA->AES: " + e.getMessage(), e);
        }
    }

    /**
     * Request a one-time server nonce for a sensitive operation.
     */
    public Response requestOperationNonce(String operation, JSONObject scopePayload) throws IOException {
        JSONObject payload = scopePayload != null ? new JSONObject(scopePayload.toString()) : new JSONObject();
        payload.put("operation", operation);
        return send(new Request(MessageProtocol.ACTION_GET_OPERATION_NONCE, payload, getSessionToken()));
    }

    public String getSessionToken()              { return sessionToken; }
    public void   setSessionToken(String token)  { this.sessionToken = token; }
    public boolean hasSession()                  { return sessionToken != null && !sessionToken.isEmpty(); }
}
