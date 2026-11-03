package com.example.quanlytaisan.repository;
import com.example.quanlytaisan.entity.AdminEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdminEmployeeRepository extends JpaRepository<AdminEmployee, Integer> {
    boolean existsByEmployeeCodeIgnoreCase(String code);
    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String code, Integer id);
}
