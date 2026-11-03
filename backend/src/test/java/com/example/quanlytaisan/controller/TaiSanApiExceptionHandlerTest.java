package com.example.quanlytaisan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaiSanApiExceptionHandlerTest {
    private final TaiSanApiExceptionHandler handler = new TaiSanApiExceptionHandler();

    @Test
    void includesActionableConflictReasonInAssetApiResponse() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Hãy thu hồi tài sản qua mục Bàn giao tài sản trước khi đổi tình trạng"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Hãy thu hồi tài sản qua mục Bàn giao tài sản trước khi đổi tình trạng",
                response.getBody().get("detail"));
    }

    @Test
    void retainsStatusWhenAnExceptionHasNoReason() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Yêu cầu không thể xử lý.", response.getBody().get("detail"));
    }
}
