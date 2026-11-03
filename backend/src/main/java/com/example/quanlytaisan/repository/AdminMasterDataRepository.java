package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.AdminMasterData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AdminMasterDataRepository extends JpaRepository<AdminMasterData, Integer> {
    List<AdminMasterData> findByKindOrderByNameAsc(String kind);
    boolean existsByKindAndCodeIgnoreCase(String kind, String code);
    boolean existsByKindAndCodeIgnoreCaseAndIdNot(String kind, String code, Integer id);
    boolean existsByKindAndId(String kind, Integer id);
}
