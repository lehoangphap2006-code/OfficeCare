package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.Consumable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface ConsumableRepository extends JpaRepository<Consumable, Integer> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
    List<Consumable> findByStockQuantityLessThanEqualOrderByNameAsc(java.math.BigDecimal amount);
    List<Consumable> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consumable c where c.id = :id")
    Optional<Consumable> findByIdForUpdate(@Param("id") Integer id);
}
