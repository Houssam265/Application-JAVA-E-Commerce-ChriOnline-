package com.chrionline.client;

import com.chrionline.protocol.Request;
import com.chrionline.protocol.Response;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

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
    private static final int    TIMEOUT_MS      = 10_000; // 10 secondes

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

        socket = new Socket(HOST, PORT);
        socket.setSoTimeout(TIMEOUT_MS);

        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),  "UTF-8"));
    }

    /**
     * Ferme proprement la connexion TCP.
     */
    public void disconnect() {
        sessionToken = null;
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
        if (!isConnected()) {
            throw new IOException("Non connecté au serveur. Appelez connect() d'abord.");
        }

        // Envoi de la requête JSON (newline-delimited)
        writer.write(request.toJson()); // toJson() inclut déjà '\n'
        writer.flush();

        // Lecture de la réponse ligne par ligne
        String responseLine;
        try {
            responseLine = reader.readLine();
        } catch (SocketTimeoutException e) {
            throw new IOException("Délai d'attente dépassé — le serveur ne répond pas.", e);
        }

        if (responseLine == null) {
            throw new IOException("Le serveur a fermé la connexion.");
        }

        Response response = Response.fromJson(responseLine);

        // Mise à jour du token de session après un LOGIN réussi
        if (response.isSuccess() && response.getToken() != null) {
            this.sessionToken = response.getToken();
        }

        return response;
    }

    // ── Token de session ─────────────────────────────────────────────────────

    public String getSessionToken()              { return sessionToken; }
    public void   setSessionToken(String token)  { this.sessionToken = token; }
    public boolean hasSession()                  { return sessionToken != null && !sessionToken.isEmpty(); }
}
