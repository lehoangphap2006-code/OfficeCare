package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.ThuongMaiOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThuongMaiOrderRepository extends JpaRepository<ThuongMaiOrder, Integer> {
    List<ThuongMaiOrder> findTop100ByOrderByIdDesc();
    boolean existsByMaGiaoDichIgnoreCase(String maGiaoDich);
    java.util.Optional<ThuongMaiOrder> findByClientRequestId(String clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ThuongMaiOrder o WHERE o.id = :id")
    java.util.Optional<ThuongMaiOrder> findByIdForUpdate(@Param("id") Integer id);

    @Query("""
            SELECT COUNT(i) > 0 FROM ThuongMaiOrder o JOIN o.chiTiet i
            WHERE i.taiSanId = :taiSanId
              AND o.trangThai IN ('CHO_THANH_TOAN', 'CHO_DUYET_THANH_LY')
              AND o.loaiDon = 'BAN'
            """)
    boolean isAssetPendingSale(@Param("taiSanId") Integer taiSanId);
}
