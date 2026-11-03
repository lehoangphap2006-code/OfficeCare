package com.example.quanlytaisan.config;

import com.example.quanlytaisan.entity.LoaiTaiSan;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DemoDataInitializer {
    @Bean
    CommandLineRunner initializeDemoData(LoaiTaiSanRepository loaiRepository, TaiSanRepository taiSanRepository) {
        return args -> {
            List<String> defaultCategories = List.of(
                    "Máy tính & laptop",
                    "Máy in & máy scan",
                    "Thiết bị mạng",
                    "Nội thất văn phòng",
                    "Thiết bị trình chiếu");
            defaultCategories.forEach(name -> {
                if (!loaiRepository.existsByTenLoaiIgnoreCase(name)) {
                    LoaiTaiSan loai = new LoaiTaiSan();
                    loai.setTenLoai(name);
                    loaiRepository.save(loai);
                }
            });

            if (taiSanRepository.count() > 0) {
                return;
            }

            addSampleAsset(taiSanRepository, loaiRepository, "TS001", "Laptop Dell Latitude", "Máy tính & laptop",
                    "15800000", "Đang sử dụng");
            addSampleAsset(taiSanRepository, loaiRepository, "TS002", "Máy in HP LaserJet", "Máy in & máy scan",
                    "5200000", "Trong kho");
            addSampleAsset(taiSanRepository, loaiRepository, "TS003", "Bàn làm việc 1m2", "Nội thất văn phòng",
                    "2800000", "Đang sử dụng");
            addSampleAsset(taiSanRepository, loaiRepository, "TS004", "Bộ phát Wi-Fi TP-Link", "Thiết bị mạng",
                    "1250000", "Bảo trì");
            addSampleAsset(taiSanRepository, loaiRepository, "TS005", "Máy chiếu Epson", "Thiết bị trình chiếu",
                    "11600000", "Trong kho");
        };
    }

    private void addSampleAsset(
            TaiSanRepository taiSanRepository,
            LoaiTaiSanRepository loaiRepository,
            String code,
            String name,
            String categoryName,
            String price,
            String status) {
        LoaiTaiSan loai = loaiRepository.findAll().stream()
                .filter(item -> item.getTenLoai().equals(categoryName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Thiếu danh mục mẫu: " + categoryName));

        TaiSan taiSan = new TaiSan();
        taiSan.setMaTaiSan(code);
        taiSan.setTenTaiSan(name);
        taiSan.setGiaMua(new BigDecimal(price));
        taiSan.setTinhTrang(status);
        taiSan.setLoaiTaiSan(loai);
        taiSanRepository.save(taiSan);
    }
}
