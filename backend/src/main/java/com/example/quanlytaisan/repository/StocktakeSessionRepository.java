package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.StocktakeSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface StocktakeSessionRepository extends JpaRepository<StocktakeSession, Integer> {
    List<StocktakeSession> findByResponsibleUsernameIgnoreCaseOrderByStocktakeDateDesc(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StocktakeSession s WHERE s.id = :id")
    Optional<StocktakeSession> findByIdForUpdate(@Param("id") Integer id);
}
