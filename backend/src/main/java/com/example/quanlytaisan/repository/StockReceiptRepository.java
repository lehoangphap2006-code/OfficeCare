package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Integer> {
    List<StockReceipt> findTop100ByOrderByIdDesc();
    long countByPurchaseOrderId(Integer purchaseOrderId);
}
