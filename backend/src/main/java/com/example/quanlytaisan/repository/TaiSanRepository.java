package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.TaiSan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TaiSanRepository extends JpaRepository<TaiSan, Integer> {
    boolean existsByMaTaiSanIgnoreCase(String maTaiSan);
    boolean existsBySerialNumberIgnoreCase(String serialNumber);
    boolean existsByMaTaiSanIgnoreCaseAndTaiSanIdNot(String maTaiSan, Integer taiSanId);
    long countByLoaiTaiSan_LoaiId(Integer loaiId);
    List<TaiSan> findByLoaiTaiSan_LoaiId(Integer loaiId);
    long countByTinhTrang(String tinhTrang);
    List<TaiSan> findByTinhTrangOrderByTaiSanIdAsc(String tinhTrang);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TaiSan t WHERE t.taiSanId IN :ids ORDER BY t.taiSanId")
    List<TaiSan> findAllByIdForUpdate(@Param("ids") List<Integer> ids);

    @Query("SELECT COALESCE(SUM(t.giaMua), 0) FROM TaiSan t")
    BigDecimal sumGiaMua();

    @Query("""
            SELECT t FROM TaiSan t LEFT JOIN t.loaiTaiSan loai
            WHERE (:keyword IS NULL
                OR LOWER(t.maTaiSan) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(t.tenTaiSan) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(loai.tenLoai) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:tinhTrang IS NULL OR t.tinhTrang = :tinhTrang)
              AND (:loaiId IS NULL OR loai.loaiId = :loaiId)
              AND (:muaTuNgay IS NULL OR t.ngayMua >= :muaTuNgay)
              AND (:muaDenNgay IS NULL OR t.ngayMua <= :muaDenNgay)
              AND (:phongBan IS NULL OR EXISTS (
                    SELECT c.id FROM TaiSanCapPhat c
                    WHERE c.taiSan = t AND c.thuHoiLuc IS NULL
                      AND LOWER(c.phongBan) LIKE LOWER(CONCAT('%', :phongBan, '%'))))
            """)
    Page<TaiSan> search(
            @Param("keyword") String keyword,
            @Param("tinhTrang") String tinhTrang,
            @Param("loaiId") Integer loaiId,
            @Param("phongBan") String phongBan,
            @Param("muaTuNgay") LocalDate muaTuNgay,
            @Param("muaDenNgay") LocalDate muaDenNgay,
            Pageable pageable);
}
