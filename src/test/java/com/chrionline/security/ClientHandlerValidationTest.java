package com.chrionline.security;

import com.chrionline.dao.UserDAO;
import com.chrionline.protocol.Response;
import com.chrionline.server.ClientHandler;
import com.chrionline.server.SessionManager;
import com.chrionline.server.UDPNotificationService;
import com.chrionline.service.AdminService;
import com.chrionline.service.AuthService;
import com.chrionline.service.CartService;
import com.chrionline.service.OrderService;
import com.chrionline.service.PaymentService;
import com.chrionline.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// [SECURITY TEST]
class ClientHandlerValidationTest {

    private ClientHandler handler;
    private Method processRequestMethod;

    @BeforeEach
    void setUp() throws Exception {
        Socket socket = new Socket() {
            @Override
            public InetAddress getInetAddress() {
                return InetAddress.getLoopbackAddress();
            }
        };

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
                mock(UDPNotificationService.class)
        );

        processRequestMethod = ClientHandler.class.getDeclaredMethod("processRequest", String.class);
        processRequestMethod.setAccessible(true);
    }

    private Response invoke(String json) throws Exception {
        return (Response) processRequestMethod.invoke(handler, json);
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
            Response r = invoke("{\"action\":\"PLACE_ORDER\",\"payload\":{\"order_id\":\"001; cat /etc/passwd\"},\"token\":\"t\"}");
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void getProduct_withNonNumericProductId_returnsInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"GET_PRODUCT\",\"payload\":{\"product_id\":\"abc\"},\"token\":\"t\"}");
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void addToCart_withNegativeQuantity_returnsInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"ADD_TO_CART\",\"payload\":{\"product_id\":1,\"quantity\":-1},\"token\":\"t\"}");
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void updateProfile_withShortUsername_returnsInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"UPDATE_PROFILE\",\"payload\":{\"username\":\"ab\",\"email\":\"user@example.com\"},\"token\":\"t\"}");
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void updateProfile_withInvalidEmail_returnsInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"UPDATE_PROFILE\",\"payload\":{\"username\":\"alice\",\"email\":\"notanemail\"},\"token\":\"t\"}");
            assertTrue(r.getMessage().contains("INVALID_INPUT"));
        }
    }

    @Nested
    class KnownActionValidPayloadTests {
        @Test
        void placeOrder_withValidOrderId_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"PLACE_ORDER\",\"payload\":{\"order_id\":\"12345\"},\"token\":\"t\"}");
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void getProduct_withValidProductId_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"GET_PRODUCT\",\"payload\":{\"product_id\":1},\"token\":\"t\"}");
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void addToCart_withValidQuantity_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"ADD_TO_CART\",\"payload\":{\"product_id\":1,\"quantity\":2},\"token\":\"t\"}");
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void getLoginCaptcha_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"GET_LOGIN_CAPTCHA\",\"payload\":{}}");
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }

        @Test
        void updateOrderStatus_withPendingStatus_doesNotReturnInvalidInput() throws Exception {
            Response r = invoke("{\"action\":\"UPDATE_ORDER_STATUS\",\"payload\":{\"order_id\":1,\"status\":\"PENDING\"},\"token\":\"t\"}");
            assertFalse(r.getMessage().contains("INVALID_INPUT"));
        }
    }
}
