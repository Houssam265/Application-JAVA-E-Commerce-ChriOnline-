package com.chrionline.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientId = socket.getRemoteSocketAddress().toString();
        LOG.info("Client connecte: " + clientId);

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)
             );
             java.io.BufferedWriter out = new java.io.BufferedWriter(
                 new java.io.OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)
             )) {

            String line;
            while ((line = in.readLine()) != null) {
                String msg = line.trim();
                if (msg.isEmpty()) {
                    continue;
                }
                LOG.info("[" + clientId + "] Reçu: " + msg);
                
                // Simulation d'une réponse de succès du serveur
                org.json.JSONObject responseJson = new org.json.JSONObject();
                responseJson.put("success", true);
                responseJson.put("message", "Opération réussie (Mock Serveur)");
                responseJson.put("token", "fake-jwt-token-1234");
                
                out.write(responseJson.toString() + "\n");
                out.flush();
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "Connexion terminee avec " + clientId + ": " + e.getMessage());
        } finally {
            LOG.info("Client deconnecte: " + clientId);
        }
    }
}
