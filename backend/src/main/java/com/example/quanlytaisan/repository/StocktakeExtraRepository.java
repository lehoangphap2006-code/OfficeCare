package com.example.quanlytaisan.repository;

import com.example.quanlytaisan.entity.StocktakeExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StocktakeExtraRepository extends JpaRepository<StocktakeExtra, Integer> {
    List<StocktakeExtra> findBySession_IdOrderByIdAsc(Integer sessionId);
    boolean existsBySession_IdAndAssetCodeIgnoreCase(Integer sessionId, String assetCode);
    long countBySession_Id(Integer sessionId);
}
