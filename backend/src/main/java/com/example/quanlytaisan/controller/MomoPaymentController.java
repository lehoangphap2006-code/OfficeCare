package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.service.MomoPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/momo")
public class MomoPaymentController {
    private final MomoPaymentService paymentService;

    public MomoPaymentController(MomoPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/don-hang/{id}/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public MomoPaymentService.CheckoutResponse createCheckout(@PathVariable Integer id) {
        return paymentService.createCheckout(id);
    }

    @PostMapping("/ipn")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> receiveIpn(@RequestBody Map<String, Object> callback) {
        paymentService.processIpn(callback);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
