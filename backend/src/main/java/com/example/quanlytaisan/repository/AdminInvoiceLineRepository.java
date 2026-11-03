package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.AdminInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminInvoiceLineRepository extends JpaRepository<AdminInvoiceLine, Integer> {
    List<AdminInvoiceLine> findByInvoice_IdOrderByIdAsc(Integer invoiceId);
}
