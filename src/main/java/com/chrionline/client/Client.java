package com.chrionline.client;

import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

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

    // ── Configuration ────────────────────────────────────────────────────────
    private static final String HOST            = "localhost";
    private static final int    PORT            = 8080;
    private static final int    TIMEOUT_MS      = 5_000; // 5 secondes
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
        socket = new Socket();
        socket.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);

        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        startUdpListener();
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
                System.err.println("[UDP] Notifications desactivees: " + e.getMessage());
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
        IOException last = null;
        boolean timeoutOccurred = false;

        for (int attempt = 1; attempt <= RECONNECT_ATTEMPTS; attempt++) {
            try {
                if (!isConnected()) {
                    connectOnce();
                }
                return sendOnce(request);
            } catch (SocketTimeoutException e) {
                last = new IOException("Delai d'attente depasse - le serveur ne repond pas.", e);
                timeoutOccurred = true;
            } catch (IOException e) {
                last = e;
            }

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

        if (last != null) {
            if (timeoutOccurred) {
                throw last;
            }
            if (!isConnected()) {
                throw new IOException("Serveur indisponible. Verifiez qu'il est demarre.", last);
            }
            throw last;
        }
        throw new IOException("Erreur reseau.");
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

    public String getSessionToken()              { return sessionToken; }
    public void   setSessionToken(String token)  { this.sessionToken = token; }
    public boolean hasSession()                  { return sessionToken != null && !sessionToken.isEmpty(); }
}
