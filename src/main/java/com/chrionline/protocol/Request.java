package com.chrionline.protocol;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Représente un message envoyé du CLIENT vers le SERVEUR via TCP.
 *
 * Format JSON (newline-delimited) :
 * {"action":"LOGIN","payload":{"email":"...","password":"..."},"token":null}
 */
public class Request {

    /** Action string (ex: LOGIN, GET_PRODUCTS). */
    private String action;

    /**
     * Payload as a generic map for server-side processing (Gson friendly).
     * When built from JSONObject (client side), it is filled via {@link JSONObject#toMap()}.
     */
    private Map<String, Object> payload;

    /** Session token (null for LOGIN / REGISTER). */
    private String token;

    /** Unique request identifier used for anti-replay protection. */
    private String requestId;

    /** Client-side timestamp in epoch milliseconds. */
    private Long timestamp;

    /** Server-issued one-time nonce for sensitive operations. */
    private String operationNonce;

    // ── Constructeurs ────────────────────────────────────────────────────────

    /** No-arg constructor required for Gson. */
    public Request() {}

    public Request(String action, JSONObject payload, String token) {
        this.action  = action;
        this.payload = payload != null ? payload.toMap() : Collections.emptyMap();
        this.token   = token;
        ensureMetadata();
    }

    /** Variante sans token (LOGIN / REGISTER). */
    public Request(String action, JSONObject payload) {
        this(action, payload, null);
    }

    /** Server-side friendly constructor. */
    public Request(String action, Map<String, Object> payload, String token) {
        this.action = action;
        this.payload = payload;
        this.token = token;
        ensureMetadata();
    }

    // ── Sérialisation ────────────────────────────────────────────────────────

    /**
     * Convertit la requête en ligne JSON prête à être envoyée sur le socket.
     * Le '\n' final sert de délimiteur de message (newline-delimited protocol).
     */
    public String toJson() {
        ensureMetadata();
        JSONObject obj = new JSONObject();
        obj.put(MessageProtocol.KEY_ACTION,  action);
        obj.put(MessageProtocol.KEY_PAYLOAD, payload != null ? new JSONObject(payload) : new JSONObject());
        obj.put(MessageProtocol.KEY_TOKEN,   token == null ? JSONObject.NULL : token);
        obj.put(MessageProtocol.KEY_OPERATION_NONCE, operationNonce == null ? JSONObject.NULL : operationNonce);
        obj.put("requestId", requestId);
        obj.put("timestamp", timestamp);
        return obj.toString() + "\n";
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getAction()  { return action;  }
    public void   setAction(String action) { this.action = action; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getToken()   { return token;   }
    public void   setToken(String token) { this.token = token; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getOperationNonce() { return operationNonce; }
    public void setOperationNonce(String operationNonce) { this.operationNonce = operationNonce; }

    /** Safe get of payload value as Integer (e.g. product_id, category_id). */
    public Integer getPayloadInt(String key) {
        if (payload == null) return null;
        Object v = payload.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return toJson().trim();
    }

    private void ensureMetadata() {
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        if (timestamp == null || timestamp <= 0L) {
            timestamp = System.currentTimeMillis();
        }
    }
}
