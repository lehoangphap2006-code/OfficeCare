package com.example.quanlytaisan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}
    public record DepartmentRequest(String maPhongBan, String tenPhongBan) {}
    public record DepartmentResponse(Integer id, String maPhongBan, String tenPhongBan) {}
    public record LocationRequest(String toaNha, String tang, String phong) {}
    public record LocationResponse(Integer id, String toaNha, String tang, String phong) {}
    public record SupplierRequest(String tenNhaCungCap, String nguoiLienHe, String soDienThoai, String email, String diaChi) {}
    public record SupplierResponse(Integer id, String tenNhaCungCap, String nguoiLienHe, String soDienThoai, String email, String diaChi) {}
    public record EmployeeRequest(String maNhanVien, String hoTen, String phongBan, String chucVu, String trangThai) {}
    public record EmployeeResponse(Integer id, String maNhanVien, String hoTen, String phongBan, String chucVu, String trangThai) {}
    public record ConsumableRequest(String maVatTu, String tenVatTu, String donVi, BigDecimal soLuongTon, BigDecimal mucToiThieu, BigDecimal donGia, Boolean dangSuDung) {}
    public record ConsumableResponse(Integer id, String maVatTu, String tenVatTu, String donVi, BigDecimal soLuongTon, BigDecimal mucToiThieu, BigDecimal donGia, boolean dangSuDung) {}
    public record ConsumableStockResponse(Integer id, String maVatTu, String tenVatTu, String donVi,
            BigDecimal soLuongTon, BigDecimal mucToiThieu) {}
    public record IssueRequest(Integer vatTuId, Integer phongBanId, String phongBan, BigDecimal soLuong, String ghiChu,
            String nguoiNhan, LocalDate ngayXuat) {}
    public record IssueResponse(Integer id, Integer vatTuId, String tenVatTu, Integer phongBanId, String phongBan,
            BigDecimal soLuong, String ghiChu, String nguoiXuat, Instant ngayXuat, String nguoiNhan,
            LocalDate ngayThucTeXuat) {}
    public record ConsumableReceiptRequest(Integer vatTuId, BigDecimal soLuong, BigDecimal donGia,
            Integer nhaCungCapId, LocalDate ngayNhap, String soPhieu, String donMuaLienQuan) {}
    public record ConsumableReceiptResponse(Integer id, Integer vatTuId, String maVatTu, String tenVatTu,
            BigDecimal soLuong, BigDecimal donGia, Integer nhaCungCapId, String nhaCungCap,
            LocalDate ngayNhap, String soPhieu, String donMuaLienQuan, String nguoiNhap) {}
    public record ConsumableMovementResponse(String loai, Integer id, Integer vatTuId, String maVatTu,
            String tenVatTu, BigDecimal soLuong, LocalDate ngay, String phongBan, String nguoiNhan,
            String nhaCungCap, String soPhieu, String donMuaLienQuan, BigDecimal donGia,
            String nguoiThucHien, String ghiChu) {}
    public record ConsumableUsageResponse(String thang, String phongBan, Integer vatTuId,
            String maVatTu, String tenVatTu, String donVi, BigDecimal soLuong) {}
    public record ConsumableRequestCreate(Integer vatTuId, BigDecimal soLuong, String lyDo) {}
    public record ConsumableRequestReview(String ghiChu) {}
    public record ConsumableRequestResponse(Integer id, Integer vatTuId, String maVatTu, String tenVatTu,
            String donVi, BigDecimal soLuong, String lyDo, String nguoiYeuCau, String phongBan,
            String trangThai, String nguoiDuyet, String ghiChuDuyet, Instant taoLuc, Instant duyetLuc) {}
    public record InvoiceLineRequest(String loai, String ten, BigDecimal soLuong, BigDecimal donGia,
            Integer taiSanId, Integer vatTuId) {}
    public record InvoiceRequest(String soHoaDon, LocalDate ngayMua, BigDecimal tongTien, Integer taiSanId,
            Integer nhaCungCapId, String nhaCungCap, String moTa, List<InvoiceLineRequest> chiTiet) {}
    public record OrderInvoiceRequest(String soHoaDon, LocalDate ngayLap, String moTa) {}
    public record InvoicePaymentRequest(LocalDate ngayThanhToan, BigDecimal soTien, String phuongThuc) {}
    public record InvoiceAdjustmentRequest(LocalDate ngayDieuChinh, BigDecimal thayDoi, String lyDo) {}
    public record DepreciationLifeRequest(Integer thoiGianKhauHaoNam) {}
    public record InvoiceLineResponse(Integer id, String loai, String ten, BigDecimal soLuong,
            BigDecimal donGia, BigDecimal thanhTien, Integer taiSanId, Integer vatTuId, Integer dongDonMuaId) {}
    public record InvoicePaymentResponse(Integer id, LocalDate ngayThanhToan, BigDecimal soTien,
            String phuongThuc, String nguoiGhi, Instant taoLuc) {}
    public record InvoiceAdjustmentResponse(Integer id, LocalDate ngayDieuChinh, BigDecimal thayDoi,
            String lyDo, String nguoiGhi, Instant taoLuc) {}
    public record InvoiceResponse(Integer id, String soHoaDon, LocalDate ngayMua, BigDecimal tongTien,
            Integer taiSanId, Integer nhaCungCapId, String nhaCungCap, Integer donMuaId,
            String trangThaiThanhToan, BigDecimal daThanhToan, BigDecimal conNo,
            String tenTepDinhKem, String moTa, List<InvoiceLineResponse> chiTiet,
            List<InvoicePaymentResponse> thanhToan, List<InvoiceAdjustmentResponse> dieuChinh) {}
    public record SupplierDebtResponse(Integer nhaCungCapId, String nhaCungCap,
            BigDecimal tongHoaDon, BigDecimal daThanhToan, BigDecimal conNo) {}
    public record StocktakeRequest(String tenDot, String phongBan, LocalDate batDauLuc, LocalDate ketThucLuc,
            String trangThai, String nguoiPhuTrach, String nguoiKiem, String nguoiXacNhan,
            String loaiPhamVi, String phamVi) {}
    public record StocktakeResponse(Integer id, String tenDot, String phongBan, LocalDate batDauLuc,
            LocalDate ketThucLuc, String trangThai, long soLuongItems, long soDaKiem, long soLuongThua,
            String nguoiPhuTrach, String nguoiKiem, String nguoiXacNhan, String loaiPhamVi, String phamVi) {}
    public record StocktakeItemRequest(Integer taiSanId, String tinhTrangThucTe, String ghiChu, String viTriThucTe) {}
    public record StocktakeItemResponse(Integer id, Integer taiSanId, String maTaiSan, String tenTaiSan,
            String viTriTheoSo, String nguoiGiuTheoSo, String viTriThucTe, String tinhTrangThucTe,
            String ghiChu, String nguoiKiem, Instant daKiemLuc, Integer maintenanceRequestId) {}
    public record StocktakeExtraRequest(String maTaiSan, String tenTaiSan, String viTriThucTe, String ghiChu) {}
    public record StocktakeExtraResponse(Integer id, String maTaiSan, String tenTaiSan,
            String viTriThucTe, String ghiChu, String nguoiKiem, Instant daKiemLuc) {}
    public record Metric(String nhan, long soLuong) {}
    public record Alert(String loai, String tieuDe, String moTa, String mucDo, String actionUrl) {}
    public record Dashboard(long tongTaiSan, long dangSuDung, long trongKho, long dangSuaChua,
            long daThanhLy, long choThanhLy, long thieu, long baoTriSapDenHan, long baoTriQuaHan,
            BigDecimal tongGiaTri, BigDecimal giaTriConLai, BigDecimal tongChiPhiSuaChua,
            List<Metric> theoTrangThai,
            List<Metric> theoLoai, List<Metric> theoPhongBan, List<Alert> canhBao) {}
    public record DepreciationRow(Integer taiSanId, String maTaiSan, String tenTaiSan, int nam,
            BigDecimal khauHaoNam, BigDecimal giaTriConLaiDauNam, BigDecimal giaTriConLaiCuoiNam) {}
}
