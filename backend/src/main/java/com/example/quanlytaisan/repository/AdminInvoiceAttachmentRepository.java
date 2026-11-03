package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.AdminInvoiceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminInvoiceAttachmentRepository extends JpaRepository<AdminInvoiceAttachment, Integer> {
    boolean existsByInvoice_Id(Integer invoiceId);
    Optional<AdminInvoiceAttachment> findByInvoice_Id(Integer invoiceId);
}
