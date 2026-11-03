package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.AdminInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AdminInvoiceRepository extends JpaRepository<AdminInvoice, Integer> {
    boolean existsByInvoiceNumberIgnoreCase(String number);
    boolean existsByInvoiceNumberIgnoreCaseAndIdNot(String number, Integer id);
    boolean existsByPurchaseAssetId(Integer assetId);
    boolean existsByPurchaseOrderId(Integer purchaseOrderId);
    List<AdminInvoice> findByPurchaseOrderId(Integer purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AdminInvoice i WHERE i.id = :id")
    Optional<AdminInvoice> findByIdForUpdate(@Param("id") Integer id);
}
