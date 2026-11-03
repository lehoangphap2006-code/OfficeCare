package com.example.quanlytaisan.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_employees")
public class AdminEmployee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 30)
    private String employeeCode;
    @Column(nullable = false, length = 150)
    private String fullName;
    private Integer departmentId;
    @Column(length = 100)
    private String title;
    @Column(nullable = false, length = 30)
    private String employmentStatus = "ACTIVE";

    public Integer getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
}
