package com.chrionline.protocol;

import com.google.gson.annotations.SerializedName;

/**
 * DTO for outgoing TCP response (JSON).
 * success, message, data (optional).
 */
public class Response {

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private Object data;

    public Response() {}

    public Response(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static Response ok(Object data) {
        return new Response(true, null, data);
    }

    public static Response ok(String message, Object data) {
        return new Response(true, message, data);
    }

    public static Response error(String message) {
        return new Response(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
