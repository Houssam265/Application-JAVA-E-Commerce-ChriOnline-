package com.chrionline.protocol;

public final class MessageProtocol {
    private MessageProtocol() {}

    // Common JSON keys
    public static final String KEY_ACTION  = "action";
    public static final String KEY_PAYLOAD = "payload";
    public static final String KEY_TOKEN   = "token";

    // Auth
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_REGISTER = "REGISTER";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_VERIFY_EMAIL = "VERIFY_EMAIL";
    public static final String ACTION_RESEND_VERIFICATION_EMAIL = "RESEND_VERIFICATION_EMAIL";

    // Catalogue
    public static final String ACTION_GET_PRODUCTS   = "GET_PRODUCTS";
    public static final String ACTION_GET_PRODUCT    = "GET_PRODUCT";
    public static final String ACTION_GET_CATEGORIES = "GET_CATEGORIES";

    // Cart
    public static final String ACTION_GET_CART          = "GET_CART";
    public static final String ACTION_ADD_TO_CART       = "ADD_TO_CART";
    public static final String ACTION_UPDATE_CART_ITEM  = "UPDATE_CART_ITEM";
    public static final String ACTION_REMOVE_FROM_CART  = "REMOVE_FROM_CART";
    public static final String ACTION_CLEAR_CART        = "CLEAR_CART";

    // Orders
    public static final String ACTION_PLACE_ORDER        = "PLACE_ORDER";
    public static final String ACTION_GET_ORDERS         = "GET_ORDERS";
    public static final String ACTION_GET_ORDER_DETAILS  = "GET_ORDER_DETAILS";
    public static final String ACTION_UPDATE_ORDER_STATUS = "UPDATE_ORDER_STATUS";

    // Payment (KAN-7) — payload: order_id, card_number, expiry (MM/YY), cvv
    public static final String ACTION_PAYMENT = "PAYMENT";

    // Profile
    public static final String ACTION_UPDATE_PROFILE  = "UPDATE_PROFILE";
    public static final String ACTION_CHANGE_PASSWORD = "CHANGE_PASSWORD";

    // Notifications
    public static final String ACTION_GET_NOTIFICATIONS       = "GET_NOTIFICATIONS";
    public static final String ACTION_MARK_NOTIFICATION_READ  = "MARK_NOTIFICATION_READ";

    // Admin
    public static final String ACTION_ADMIN_CREATE_PRODUCT     = "ADMIN_CREATE_PRODUCT";
    public static final String ACTION_ADMIN_UPDATE_PRODUCT     = "ADMIN_UPDATE_PRODUCT";
    public static final String ACTION_ADMIN_DELETE_PRODUCT     = "ADMIN_DELETE_PRODUCT";
    public static final String ACTION_ADMIN_LIST_USERS         = "ADMIN_LIST_USERS";
    public static final String ACTION_ADMIN_UPDATE_USER_ROLE   = "ADMIN_UPDATE_USER_ROLE";
    public static final String ACTION_ADMIN_SET_USER_SUSPENDED = "ADMIN_SET_USER_SUSPENDED";
    public static final String ACTION_ADMIN_LIST_ORDERS        = "ADMIN_LIST_ORDERS";

    // Admin — Category CRUD (KAN-18)
    public static final String ACTION_ADMIN_ADD_CATEGORY    = "ADD_CATEGORY";
    public static final String ACTION_ADMIN_UPDATE_CATEGORY = "UPDATE_CATEGORY";
    public static final String ACTION_ADMIN_DELETE_CATEGORY = "DELETE_CATEGORY";

    // Logs (KAN-41)
    public static final String GET_LOGS = "GET_LOGS";
    public static final String GET_LOGS_BY_USER = "GET_LOGS_BY_USER";
}
