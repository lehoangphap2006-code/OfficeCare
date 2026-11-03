package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.TaiSanCapPhat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaiSanCapPhatRepository extends JpaRepository<TaiSanCapPhat, Integer> {
    boolean existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(Integer taiSanId);
    boolean existsByTaiSan_TaiSanId(Integer taiSanId);
    boolean existsByUsernameNguoiNhanIgnoreCaseAndTaiSan_TaiSanIdAndThuHoiLucIsNull(String username, Integer taiSanId);
    boolean existsByNguoiNhanIgnoreCaseAndTaiSan_TaiSanIdAndThuHoiLucIsNull(String username, Integer taiSanId);

    @EntityGraph(attributePaths = "taiSan")
    List<TaiSanCapPhat> findTop100ByOrderByIdDesc();

    @Query("SELECT c FROM TaiSanCapPhat c JOIN FETCH c.taiSan WHERE c.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaiSanCapPhat> findByIdWithTaiSan(@Param("id") Integer id);

    @EntityGraph(attributePaths = "taiSan")
    List<TaiSanCapPhat> findByThuHoiLucIsNullOrderByIdDesc();

    @Query("""
            SELECT c.taiSan.taiSanId AS taiSanId,
                   c.nguoiNhan AS nguoiNhan,
                   c.phongBan AS phongBan
            FROM TaiSanCapPhat c
            WHERE c.thuHoiLuc IS NULL AND c.taiSan.taiSanId IN :taiSanIds
            """)
    List<TaiSanHolderInfo> findCurrentHolders(@Param("taiSanIds") List<Integer> taiSanIds);
}
