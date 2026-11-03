package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.ConsumableReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConsumableReceiptRepository extends JpaRepository<ConsumableReceipt, Integer> {
    List<ConsumableReceipt> findAllByOrderByReceivedOnDescIdDesc();
}
