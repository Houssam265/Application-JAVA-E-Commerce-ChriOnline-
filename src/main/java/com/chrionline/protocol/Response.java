package com.chrionline.protocol;

import org.json.JSONObject;

/**
 * Représente un message reçu du SERVEUR vers le CLIENT via TCP.
 *
 * Format JSON attendu :
 * {"success":true,"message":"Login successful","payload":{...},"token":"abc123"}
 */
public class Response {

    private boolean success;
    private String message;
    /**
     * Payload of the response (server sends lists/maps here).
     * Named "payload" to match the documented TCP protocol and the JavaFX client parser.
     */
    private Object payload;
    /** present only after successful LOGIN (client stores it). */
    private String token;

    // ── Constructeur ─────────────────────────────────────────────────────────

    public Response() {}

    public Response(boolean success, String message, Object payload, String token) {
        this.success = success;
        this.message = message;
        this.payload = payload;
        this.token   = token;
    }

    public static Response ok(Object payload) {
        return new Response(true, "", payload, null);
    }

    public static Response ok(String message, Object payload) {
        return new Response(true, message != null ? message : "", payload, null);
    }

    public static Response error(String message) {
        return new Response(false, message != null ? message : "Erreur", null, null);
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
            Object payload;
            if (obj.has("payload") && !obj.isNull("payload")) {
                payload = obj.get("payload");
            } else if (obj.has("data") && !obj.isNull("data")) {
                // backward compatibility (older server responses)
                payload = obj.get("data");
            } else {
                payload = new JSONObject();
            }
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

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getPayload() { return payload; }
    public String getToken()   { return token;   }

    // Convenience for JavaFX client code that expects a JSONObject payload
    public JSONObject getPayloadAsJsonObject() {
        if (payload instanceof JSONObject) {
            return (JSONObject) payload;
        }
        if (payload == null) {
            return new JSONObject();
        }
        if (payload instanceof java.util.Map) {
            return new JSONObject((java.util.Map<?, ?>) payload);
        }
        return new JSONObject(payload.toString());
    }

    @Override
    public String toString() {
        return "Response{success=" + success + ", message='" + message + "', token=" + token + '}';
    }
}
