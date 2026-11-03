package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.PurchaseRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Integer> {
    List<PurchaseRequest> findTop100ByRequesterIgnoreCaseOrderByCreatedAtDesc(String requester);
    List<PurchaseRequest> findTop100ByOrderByCreatedAtDesc();
    List<PurchaseRequest> findByPurchaseOrder_Id(Integer purchaseOrderId);
    java.util.Optional<PurchaseRequest> findByOrderRequestId(String orderRequestId);
    long countByStatus(String status);
    long countByCategoryId(Integer categoryId);
    List<PurchaseRequest> findByCategoryId(Integer categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PurchaseRequest request where request.id = :id")
    java.util.Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Integer id);
}
