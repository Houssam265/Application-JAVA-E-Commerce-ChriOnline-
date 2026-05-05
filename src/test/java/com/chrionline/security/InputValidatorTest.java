package com.chrionline.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// [SECURITY TEST]
class InputValidatorTest {

    @Nested
    class ValidateOrderIdTests {
        @Test
        void validateOrderId_withValidDigits_returnsInteger() {
            assertEquals(12345, InputValidator.validateOrderId("12345"));
            assertEquals(1, InputValidator.validateOrderId("001"));
        }

        @Test
        void validateOrderId_withTenDigits_shouldBeValidButCurrentlyFails() {
            assertEquals(2147483647, InputValidator.validateOrderId("2147483647"));
        }

        @Test
        void validateOrderId_withInvalidInputs_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId("12345678901"));
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId("001; cat /etc/passwd"));
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId("001 | rm -rf /"));
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId("001&& whoami"));
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId(""));
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId(null));
            assertThrows(ValidationException.class, () -> InputValidator.validateOrderId("abc"));
        }
    }

    @Nested
    class ValidateUsernameTests {
        @Test
        void validateUsername_withValidInputs_returnsUsername() {
            assertEquals("alice", InputValidator.validateUsername("alice"));
            assertEquals("user_123", InputValidator.validateUsername("user_123"));
            assertEquals("my-name", InputValidator.validateUsername("my-name"));
        }

        @Test
        void validateUsername_withInvalidInputs_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername("ab"));
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername("a".repeat(31)));
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername("admin' OR '1'='1"));
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername("user; drop table users"));
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername("user<script>"));
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername(""));
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername(null));
        }
    }

    @Nested
    class ValidateEmailTests {
        @Test
        void validateEmail_withValidInputs_returnsNormalizedEmail() {
            assertEquals("user@example.com", InputValidator.validateEmail("user@example.com"));
            assertEquals("user.name+tag@domain.co.uk", InputValidator.validateEmail("user.name+tag@domain.co.uk"));
        }

        @Test
        void validateEmail_withInvalidInputs_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail("notanemail"));
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail("missing@"));
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail("@nodomain.com"));
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail("user@domain; cat /etc/passwd"));
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail(""));
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail(null));
        }
    }

    @Nested
    class NumericValidationTests {
        @Test
        void validateNumericFields_withValidValues_returnsValidatedValue() {
            assertEquals(1, InputValidator.validateProductId(1));
            assertEquals(2, InputValidator.validateQuantity(2));
            assertEquals(1999, InputValidator.validatePrice(19.99));
        }

        @Test
        void validateNumericFields_withZeroOrNegative_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.validateProductId(0));
            assertThrows(ValidationException.class, () -> InputValidator.validateProductId(-1));
            assertThrows(ValidationException.class, () -> InputValidator.validateQuantity(0));
            assertThrows(ValidationException.class, () -> InputValidator.validateQuantity(-1));
            assertThrows(ValidationException.class, () -> InputValidator.validatePrice(-1.0));
        }

        @Test
        void validateNumericFields_withNull_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.validateProductId(null));
            assertThrows(ValidationException.class, () -> InputValidator.validateQuantity(null));
            assertThrows(ValidationException.class, () -> InputValidator.validatePrice(null));
        }

        @Test
        void validateNumericFields_withOverflowAttempt_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.validateProductId((int) ((long) Integer.MAX_VALUE + 1)));
        }
    }

    @Nested
    class ValidateFilenameTests {
        @Test
        void sanitizeFileName_withSafeNames_returnsSafeName() {
            assertEquals("invoice_123.pdf", InputValidator.sanitizeFileName("invoice_123.pdf"));
            assertEquals("product_image.jpg", InputValidator.sanitizeFileName("product_image.jpg"));
        }

        @Test
        void validateFilename_withTraversalOrShellMeta_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName("../../etc/passwd"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName("..\\..\\ windows\\system32"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName("file; rm -rf /"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName("file|pipe.txt"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName("/absolute/path.pdf"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName(""));
            assertThrows(ValidationException.class, () -> InputValidator.sanitizeFileName(null));
        }
    }

    @Nested
    class SanitizeTests {
        @Test
        void sanitize_withNormalInput_returnsTrimmedString() {
            assertEquals("hello world", InputValidator.sanitize("hello world", "field"));
            assertEquals("normal_input-123", InputValidator.sanitize("normal_input-123", "field"));
        }

        @Test
        void sanitize_withShellMeta_throwsValidationException() {
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input; ls", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input | cat", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input && whoami", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input`id`", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input$(whoami)", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input > /tmp/x", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input < /etc/passwd", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input!bang", "field"));
            assertThrows(ValidationException.class, () -> InputValidator.sanitize("input#hash", "field"));
        }
    }
}
