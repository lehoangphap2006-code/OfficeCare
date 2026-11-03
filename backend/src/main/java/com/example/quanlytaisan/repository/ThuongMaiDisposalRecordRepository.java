package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.ThuongMaiDisposalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThuongMaiDisposalRecordRepository extends JpaRepository<ThuongMaiDisposalRecord, Integer> {
    Optional<ThuongMaiDisposalRecord> findByOrderId(Integer orderId);
    boolean existsByOrderId(Integer orderId);
}
