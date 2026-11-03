package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Integer> {
    List<StocktakeItem> findBySession_IdOrderByIdAsc(Integer id);
    boolean existsBySession_IdAndAsset_TaiSanId(Integer sessionId, Integer assetId);
    long countBySession_Id(Integer id);
    long countBySession_IdAndCheckedAtIsNotNull(Integer id);
    boolean existsBySession_Id(Integer id);
    boolean existsByAsset_TaiSanId(Integer id);

    @Query("SELECT DISTINCT i.asset.taiSanId FROM StocktakeItem i WHERE i.session.status = 'OPEN' AND i.asset.taiSanId IN :assetIds")
    List<Integer> findAssetIdsInOpenSessions(@Param("assetIds") List<Integer> assetIds);
}
