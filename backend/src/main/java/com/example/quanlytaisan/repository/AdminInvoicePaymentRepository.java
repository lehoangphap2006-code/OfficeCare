package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.AdminInvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminInvoicePaymentRepository extends JpaRepository<AdminInvoicePayment, Integer> {
    List<AdminInvoicePayment> findByInvoice_IdOrderByPaymentDateAscIdAsc(Integer invoiceId);
}
