package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.LoaiTaiSan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoaiTaiSanRepository extends JpaRepository<LoaiTaiSan, Integer> {
    boolean existsByTenLoaiIgnoreCase(String tenLoai);
    boolean existsByTenLoaiIgnoreCaseAndLoaiIdNot(String tenLoai, Integer loaiId);
}
