package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.LoginResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoogleOAuthCodeStoreTest {
    @Test
    void consumesLoginCodeOnlyOnce() {
        GoogleOAuthCodeStore store = new GoogleOAuthCodeStore();
        LoginResponse expected = new LoginResponse("access", "refresh", "Bearer", 3600, "employee", "USER");

        String code = store.issue(expected);

        assertEquals(expected, store.consume(code));
        assertNull(store.consume(code));
        assertNull(store.consume("unknown"));
    }
}
