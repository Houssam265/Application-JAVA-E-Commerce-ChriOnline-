package com.chrionline.protocol;

import org.json.JSONObject;

/**
 * Représente un message envoyé du CLIENT vers le SERVEUR via TCP.
 *
 * Format JSON (newline-delimited) :
 * {"action":"LOGIN","payload":{"email":"...","password":"..."},"token":null}
 */
public class Request {

    private final String     action;
    private final JSONObject payload;
    private final String     token;   // null pour LOGIN et REGISTER

    // ── Constructeurs ────────────────────────────────────────────────────────

    public Request(String action, JSONObject payload, String token) {
        this.action  = action;
        this.payload = payload != null ? payload : new JSONObject();
        this.token   = token;
    }

    /** Variante sans token (LOGIN / REGISTER). */
    public Request(String action, JSONObject payload) {
        this(action, payload, null);
    }

    // ── Sérialisation ────────────────────────────────────────────────────────

    /**
     * Convertit la requête en ligne JSON prête à être envoyée sur le socket.
     * Le '\n' final sert de délimiteur de message (newline-delimited protocol).
     */
    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put(MessageProtocol.KEY_ACTION,  action);
        obj.put(MessageProtocol.KEY_PAYLOAD, payload);
        obj.put(MessageProtocol.KEY_TOKEN,   token == null ? JSONObject.NULL : token);
        return obj.toString() + "\n";
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String     getAction()  { return action;  }
    public JSONObject getPayload() { return payload; }
    public String     getToken()   { return token;   }

    @Override
    public String toString() {
        return toJson().trim();
    }
}
