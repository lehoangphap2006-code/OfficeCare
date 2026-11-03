package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.MaintenanceSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Integer> {
    boolean existsByTaiSan_TaiSanIdAndTrangThaiIn(Integer taiSanId, List<String> statuses);

    @EntityGraph(attributePaths = {"taiSan", "taiSan.loaiTaiSan"})
    List<MaintenanceSchedule> findByTrangThaiNotOrderByIdDesc(String status);

    @Query("SELECT s FROM MaintenanceSchedule s JOIN FETCH s.taiSan t LEFT JOIN FETCH t.loaiTaiSan WHERE s.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MaintenanceSchedule> findByIdForUpdate(@Param("id") Integer id);
}
