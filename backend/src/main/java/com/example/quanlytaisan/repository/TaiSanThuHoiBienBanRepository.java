package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.TaiSanThuHoiBienBan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TaiSanThuHoiBienBanRepository extends JpaRepository<TaiSanThuHoiBienBan, Integer> {
    Optional<TaiSanThuHoiBienBan> findByPhieuBanGiao_Id(Integer assignmentId);

    @Query("SELECT r.noiDungPdf FROM TaiSanThuHoiBienBan r WHERE r.phieuBanGiao.id = :assignmentId")
    Optional<byte[]> findPdfByAssignmentId(@Param("assignmentId") Integer assignmentId);
}
