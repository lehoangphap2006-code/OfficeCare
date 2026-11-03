package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.AdminInvoiceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminInvoiceAdjustmentRepository extends JpaRepository<AdminInvoiceAdjustment, Integer> {
    List<AdminInvoiceAdjustment> findByInvoice_IdOrderByAdjustmentDateAscIdAsc(Integer invoiceId);
}
