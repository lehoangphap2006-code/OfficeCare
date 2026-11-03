package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.ConsumableRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConsumableRequestRepository extends JpaRepository<ConsumableRequest, Integer> {
    List<ConsumableRequest> findTop100ByRequesterIgnoreCaseOrderByCreatedAtDesc(String requester);
    List<ConsumableRequest> findTop100ByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ConsumableRequest request where request.id = :id")
    Optional<ConsumableRequest> findByIdForUpdate(@Param("id") Integer id);
}
