package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.YeuCauBaoTri;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface YeuCauBaoTriRepository extends JpaRepository<YeuCauBaoTri, Integer> {
    boolean existsByTaiSan_TaiSanIdAndTrangThaiIn(Integer taiSanId, List<String> statuses);
    boolean existsByTaiSan_TaiSanId(Integer taiSanId);

    @EntityGraph(attributePaths = "taiSan")
    List<YeuCauBaoTri> findTop100ByOrderByIdDesc();

    @EntityGraph(attributePaths = {"taiSan", "taiSan.loaiTaiSan"})
    List<YeuCauBaoTri> findAllByOrderByIdDesc();

    @Query("SELECT y FROM YeuCauBaoTri y JOIN FETCH y.taiSan WHERE y.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<YeuCauBaoTri> findByIdWithTaiSan(@Param("id") Integer id);

    @EntityGraph(attributePaths = "taiSan")
    List<YeuCauBaoTri> findByTrangThaiInOrderByIdDesc(List<String> statuses);
    List<YeuCauBaoTri> findByNguoiYeuCauIgnoreCaseOrderByIdDesc(String username);
    List<YeuCauBaoTri> findByTaiSan_TaiSanIdOrderByIdDesc(Integer taiSanId);
    @EntityGraph(attributePaths = "taiSan")
    List<YeuCauBaoTri> findByLichBaoTri_IdOrderByIdDesc(Integer scheduleId);

    @Query("""
            SELECT y.taiSan.taiSanId AS taiSanId,
                   MAX(y.hoanTatLuc) AS baoTriGanNhat,
                   SUM(y.chiPhi) AS chiPhiBaoTriTichLuy
            FROM YeuCauBaoTri y
            WHERE y.trangThai IN ('HOAN_TAT', 'KHONG_SUA_DUOC')
              AND y.taiSan.taiSanId IN :taiSanIds
            GROUP BY y.taiSan.taiSanId
            """)
    List<BaoTriTongHop> summarizeCompletedByTaiSanIds(@Param("taiSanIds") List<Integer> taiSanIds);

    @Query("""
            SELECT COALESCE(SUM(y.chiPhi), 0)
            FROM YeuCauBaoTri y
            WHERE y.trangThai IN ('HOAN_TAT', 'KHONG_SUA_DUOC')
              AND (y.loaiYeuCau = 'SUA_CHUA'
                   OR (y.loaiYeuCau IS NULL AND y.lichBaoTri IS NULL))
            """)
    java.math.BigDecimal totalRepairCost();

    @Query("""
            SELECT COALESCE(SUM(y.chiPhi), 0)
            FROM YeuCauBaoTri y
            WHERE y.trangThai IN ('HOAN_TAT', 'KHONG_SUA_DUOC')
              AND (y.loaiYeuCau = 'SUA_CHUA'
                   OR (y.loaiYeuCau IS NULL AND y.lichBaoTri IS NULL))
              AND y.taiSan.taiSanId IN :taiSanIds
            """)
    java.math.BigDecimal repairCostByAssetIds(@Param("taiSanIds") List<Integer> taiSanIds);
}
