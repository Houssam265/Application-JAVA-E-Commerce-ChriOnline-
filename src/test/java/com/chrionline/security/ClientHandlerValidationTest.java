package com.chrionline.security;

import com.chrionline.dao.UserDAO;
import com.chrionline.protocol.MessageProtocol;
import com.chrionline.protocol.Response;
import com.chrionline.server.ClientHandler;
import com.chrionline.server.SessionManager;
import com.chrionline.server.UDPNotificationService;
import com.chrionline.service.AdminAuthService;
import com.chrionline.service.AdminService;
import com.chrionline.service.AuthService;
import com.chrionline.service.CartService;
import com.chrionline.service.OrderService;
import com.chrionline.service.PaymentService;
import com.chrionline.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// [SECURITY TEST]
class ClientHandlerValidationTest {

    private ClientHandler handler;
    private Method processRequestMethod;
    private KeyPair serverKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        Socket socket = new Socket() {
            @Override
            public InetAddress getInetAddress() {
                return InetAddress.getLoopbackAddress();
            }
        };

        serverKeyPair = com.chrionline.security.RSAUtil.generateKeyPair();

        handler = new ClientHandler(
                socket,
                mock(UserDAO.class),
                mock(AuthService.class),
                mock(SessionManager.class),
                mock(ProductService.class),
                mock(CartService.class),
                mock(OrderService.class),
                mock(PaymentService.class),
                mock(AdminService.class),
                mock(UDPNotificationService.class),
                mock(AdminAuthService.class),
                serverKeyPair
        );

        processRequestMethod = ClientHandler.class.getDeclaredMethod("processRequest", String.class);
        processRequestMethod.setAccessible(true);
    }

    private Response invoke(String json) throws Exception {
        return (Response) processRequestMethod.invoke(handler, json);
    }

    private String encryptedRequestJson(String action, JSONObject plainPayload, String token) throws Exception {
        SecretKey aesKey = AESUtil.generateKey();
        AESUtil.Sealed sealed = AESUtil.encrypt(aesKey, plainPayload.toString());
        Map<String, Object> encryptedPayload = new HashMap<>();
        encryptedPayload.put(MessageProtocol.KEY_ENCRYPTED_AES_KEY,
                HybridCryptoUtil.wrapAesKey(aesKey, serverKeyPair.getPublic(), HybridCryptoUtil.RSA_TRANSFORMATION_PKCS1));
        encryptedPayload.put(MessageProtocol.KEY_AES_IV, sealed.ivBase64());
        encryptedPayload.put(MessageProtocol.KEY_ENCRYPTED_PAYLOAD, sealed.cipherTextBase64());
        encryptedPayload.put("hybrid", true);
        return new com.chrionline.protocol.Request(action, encryptedPayload, token).toJson();
    }

    @Nested
    class UnknownActionTests {
        @Test
        void unknownAction_returnsInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"UNKNOWN_ACTION\",\"payload\":{}}");
            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void maliciousActionStrings_returnInvalidInput() throws Exception {
            Response drop = invoke("{\"action\":\"DROP TABLE users\",\"payload\":{}}");
            Response shell = invoke("{\"action\":\"; rm -rf /\",\"payload\":{}}");
            assertTrue(drop.getMessage().contains("INVALID_INPUT"));
            assertTrue(shell.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void emptyOrNullAction_returnsInvalidInput() throws Exception {
            Response empty = invoke("{\"action\":\"\",\"payload\":{}}");
            Response nil = invoke("{\"payload\":{}}");
            assertTrue(empty.getMessage().contains("INVALID_INPUT"));
            assertTrue(nil.getMessage().contains("INVALID_INPUT"));
        }
    }

    @Nested
    class KnownActionBadPayloadTests {
        @Test
        void placeOrder_withInjectedOrderId_returnsInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("PLACE_ORDER", new JSONObject().put("order_id", "001; cat /etc/passwd"), "t"));
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void getProduct_withNonNumericProductId_returnsInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("GET_PRODUCT", new JSONObject().put("product_id", "abc"), "t"));
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void addToCart_withNegativeQuantity_returnsInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("ADD_TO_CART", new JSONObject().put("product_id", 1).put("quantity", -1), "t"));
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void updateProfile_withShortUsername_returnsInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("UPDATE_PROFILE", new JSONObject().put("username", "ab").put("email", "user@example.com"), "t"));
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void updateProfile_withInvalidEmail_returnsInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("UPDATE_PROFILE", new JSONObject().put("username", "alice").put("email", "notanemail"), "t"));
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void paymentWithPlainPayload_returnsInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"PAYMENT\",\"payload\":{\"order_id\":1,\"card_number\":\"4111111111111111\",\"expiry\":\"12/30\",\"cvv\":\"123\"},\"token\":\"t\"}");
            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
            assertTrue(r.getMessage().contains("canal TLS paiement"));
        }

        @Test
        void encryptedLoginPayload_isDecryptedBeforeValidation() throws Exception {
            Response r = invoke(encryptedRequestJson(
                    "LOGIN",
                    new JSONObject().put("email", "notanemail").put("password", "Password1!"),
                    null));
            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
            assertTrue(r.getMessage().contains("email format"));
        }

        @Test
        void malformedHybridPayload_returnsInvalidInput() throws Exception {
            SecretKey aesKey = AESUtil.generateKey();
            Map<String, Object> encryptedPayload = new HashMap<>();
            encryptedPayload.put(MessageProtocol.KEY_ENCRYPTED_AES_KEY,
                    HybridCryptoUtil.wrapAesKey(aesKey, serverKeyPair.getPublic(), HybridCryptoUtil.RSA_TRANSFORMATION_PKCS1));
            encryptedPayload.put(MessageProtocol.KEY_ENCRYPTED_PAYLOAD, "abc");
            Response r = invoke(new com.chrionline.protocol.Request("LOGIN", encryptedPayload, null).toJson());
            assertFalse(r.isSuccess());
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
            assertTrue(r.getMessage().contains("aesIv"));
        }

        @Test
        void replayedEncryptedApplicationRequest_returnsError() throws Exception {
            String json = encryptedRequestJson("GET_LOGIN_CAPTCHA", new JSONObject(), null);
            Response first = invoke(json);
            Response replay = invoke(json);
            assertFalse(first.getMessage().contains("dupliquee"));
            assertFalse(replay.isSuccess());
            assertTrue(replay.getMessage().contains("dupliquee"));
        }
    }

    @Nested
    class KnownActionValidPayloadTests {
        @Test
        void placeOrder_withValidOrderId_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("PLACE_ORDER", new JSONObject().put("order_id", "12345"), "t"));
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void getProduct_withValidProductId_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("GET_PRODUCT", new JSONObject().put("product_id", 1), "t"));
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void addToCart_withValidQuantity_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("ADD_TO_CART", new JSONObject().put("product_id", 1).put("quantity", 2), "t"));
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void getLoginCaptcha_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("GET_LOGIN_CAPTCHA", new JSONObject(), null));
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void updateOrderStatus_withPendingStatus_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke(encryptedRequestJson("UPDATE_ORDER_STATUS", new JSONObject().put("order_id", 1).put("status", "PENDING"), "t"));
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }
    }
}
