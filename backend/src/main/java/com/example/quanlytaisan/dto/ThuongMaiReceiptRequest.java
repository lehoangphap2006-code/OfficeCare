package com.example.quanlytaisan.dto;

import java.time.LocalDate;
import java.util.List;

public record ThuongMaiReceiptRequest(LocalDate ngayNhap, String ghiChu, List<Item> chiTiet) {
    public record Item(Integer chiTietId, Integer soLuongThucNhan, Integer soLuongHong,
            List<String> serialNumbers, List<AssetDetails> assetDetails, String ghiChu) {
        public Item(Integer chiTietId, Integer soLuongThucNhan, Integer soLuongHong, List<String> serialNumbers) {
            this(chiTietId, soLuongThucNhan, soLuongHong, serialNumbers, null, null);
        }
    }

    public record AssetDetails(String serialNumber, String brand, String model,
            LocalDate warrantyEndDate, Integer locationId) {}
}
