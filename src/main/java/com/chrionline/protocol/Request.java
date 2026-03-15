package com.chrionline.protocol;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * DTO for incoming TCP request (JSON).
 * Matches protocol: action, payload, token.
 */
public class Request {

    @SerializedName(MessageProtocol.KEY_ACTION)
    private String action;

    @SerializedName(MessageProtocol.KEY_PAYLOAD)
    private Map<String, Object> payload;

    @SerializedName(MessageProtocol.KEY_TOKEN)
    private String token;

    public Request() {}

    public Request(String action, Map<String, Object> payload, String token) {
        this.action = action;
        this.payload = payload;
        this.token = token;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

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
}
