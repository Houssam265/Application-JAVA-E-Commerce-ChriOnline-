package com.chrionline.protocol;

import org.json.JSONObject;

/**
 * Représente un message reçu du SERVEUR vers le CLIENT via TCP.
 *
 * Format JSON attendu :
 * {"success":true,"message":"Login successful","payload":{...},"token":"abc123"}
 */
public class Response {

    private final boolean    success;
    private final String     message;
    private final JSONObject payload;
    private final String     token;   // présent uniquement après LOGIN réussi

    // ── Constructeur ─────────────────────────────────────────────────────────

    public Response(boolean success, String message, JSONObject payload, String token) {
        this.success = success;
        this.message = message;
        this.payload = payload != null ? payload : new JSONObject();
        this.token   = token;
    }

    // ── Désérialisation ──────────────────────────────────────────────────────

    /**
     * Construit une Response à partir d'une ligne JSON reçue du serveur.
     *
     * @param jsonLine ligne brute (avec ou sans '\n' final)
     * @return Response désérialisée
     * @throws RuntimeException si le JSON est malformé
     */
    public static Response fromJson(String jsonLine) {
        try {
            JSONObject obj = new JSONObject(jsonLine.trim());

            boolean    success = obj.optBoolean("success", false);
            String     message = obj.optString("message", "");
            JSONObject payload = obj.has("payload") && !obj.isNull("payload")
                                 ? obj.getJSONObject("payload")
                                 : new JSONObject();
            String     token   = obj.has("token") && !obj.isNull("token")
                                 ? obj.getString("token")
                                 : null;

            return new Response(success, message, payload, token);
        } catch (Exception e) {
            // Réponse d'erreur de parsing — ne plante pas l'UI
            return new Response(false, "Réponse invalide du serveur : " + e.getMessage(),
                                new JSONObject(), null);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean    isSuccess() { return success; }
    public String     getMessage() { return message; }
    public JSONObject getPayload() { return payload; }
    public String     getToken()   { return token;   }

    @Override
    public String toString() {
        return "Response{success=" + success + ", message='" + message + "', token=" + token + '}';
    }
}
