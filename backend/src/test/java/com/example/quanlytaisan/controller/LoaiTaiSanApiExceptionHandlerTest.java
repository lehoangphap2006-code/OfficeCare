package com.example.quanlytaisan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoaiTaiSanApiExceptionHandlerTest {
    private final LoaiTaiSanApiExceptionHandler handler = new LoaiTaiSanApiExceptionHandler();

    @Test
    void includesReasonWhenCategoryCannotBeDeleted() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Không thể xóa loại đang được gán cho tài sản"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Không thể xóa loại đang được gán cho tài sản",
                response.getBody().get("detail"));
    }
}
