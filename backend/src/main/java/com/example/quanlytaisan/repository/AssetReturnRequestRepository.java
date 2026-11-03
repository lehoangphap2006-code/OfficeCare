package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.AssetReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetReturnRequestRepository extends JpaRepository<AssetReturnRequest, Integer> {
    List<AssetReturnRequest> findByRequesterIgnoreCaseOrderByCreatedAtDesc(String requester);
    List<AssetReturnRequest> findAllByOrderByCreatedAtDesc();
    boolean existsByAssignment_IdAndRequesterIgnoreCaseAndStatus(Integer assignmentId, String requester, String status);
}
