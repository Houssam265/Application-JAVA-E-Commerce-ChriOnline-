package com.chrionline.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

// [SECURITY TEST]
class ValidationExceptionTest {

    @Test
    void constructor_withMessage_preservesMessage() {
        ValidationException ex = new ValidationException("invalid input");
        assertEquals("invalid input", ex.getMessage());
    }

    @Test
    void validationException_isRuntimeExceptionAsDesigned() {
        ValidationException ex = new ValidationException("x");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void validationException_messageRoundtripThroughCatch_preserved() {
        ValidationException thrown = assertThrows(ValidationException.class, () -> {
            throw new ValidationException("roundtrip");
        });
        assertEquals("roundtrip", thrown.getMessage());
    }
}
