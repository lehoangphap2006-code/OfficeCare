package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.TaiSanCapPhatRequest;
import com.example.quanlytaisan.dto.TaiSanCapPhatResponse;
import com.example.quanlytaisan.dto.ThuHoiTaiSanRequest;
import com.example.quanlytaisan.dto.CapNhatBaoTriRequest;
import com.example.quanlytaisan.dto.YeuCauBaoTriRequest;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.entity.TaiSanCapPhat;
import com.example.quanlytaisan.entity.TaiSanThuHoiBienBan;
import com.example.quanlytaisan.entity.YeuCauBaoTri;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.TaiSanThuHoiBienBanRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;

class TaiSanVongDoiServiceAssignmentTest {
    private TaiSanRepository assetRepository;
    private TaiSanCapPhatRepository assignmentRepository;
    private TaiSanThuHoiBienBanRepository returnRecordRepository;
    private YeuCauBaoTriRepository maintenanceRepository;
    private ThuongMaiOrderRepository orderRepository;
    private ReturnRecordPdfGenerator returnRecordPdfGenerator;
    private TaiSanVongDoiService service;
    private TaiSan asset;

    @BeforeEach
    void setUp() {
        assetRepository = mock(TaiSanRepository.class);
        assignmentRepository = mock(TaiSanCapPhatRepository.class);
        returnRecordRepository = mock(TaiSanThuHoiBienBanRepository.class);
        maintenanceRepository = mock(YeuCauBaoTriRepository.class);
        orderRepository = mock(ThuongMaiOrderRepository.class);
        returnRecordPdfGenerator = mock(ReturnRecordPdfGenerator.class);
        service = new TaiSanVongDoiService(assetRepository, assignmentRepository, maintenanceRepository,
                orderRepository, mock(ActivityLogService.class), mock(AppUserRepository.class),
                returnRecordPdfGenerator, returnRecordRepository);
        asset = new TaiSan();
        asset.setTaiSanId(5);
        asset.setMaTaiSan("TS-005");
        asset.setTenTaiSan("Laptop");
        when(assetRepository.findAllByIdForUpdate(List.of(5))).thenReturn(List.of(asset));
        when(assetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.existsByTaiSan_TaiSanIdAndThuHoiLucIsNull(5)).thenReturn(false);
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(eq(5), anyList())).thenReturn(false);
        when(orderRepository.isAssetPendingSale(5)).thenReturn(false);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToHandoverAssetsThatAreNotInStock() {
        asset.setTinhTrang("Đang sử dụng");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assign(request()));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void refusesToHandoverAssetWithAnOpenRepairTicket() {
        when(maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(eq(5), anyList())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.assign(request()));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void handsOverStockAssetAndPersistsDateAndSignatures() {
        LocalDate handoverDate = LocalDate.now();

        TaiSanCapPhatResponse response = service.assign(request(
                null, "Phòng IT", handoverDate, "Admin", "Người nhận"));

        assertEquals("Phòng IT", response.nguoiNhan());
        assertEquals("Admin", response.nguoiBanGiao());
        assertEquals("Người nhận", response.nguoiKyNhan());
        assertEquals("Đang sử dụng", asset.getTinhTrang());
        verify(assignmentRepository).save(argThat(assignment ->
                assignment.getNguoiNhan().equals("Phòng IT")
                        && assignment.getPhongBan().equals("Phòng IT")
                        && assignment.getNguoiBanGiao().equals("Admin")
                        && assignment.getNguoiKyNhan().equals("Người nhận")
                        && assignment.getBanGiaoLuc().equals(
                        handoverDate.atStartOfDay(ZoneId.systemDefault()).toInstant())));
    }

    @Test
    void returnClosesAssignmentAndSavesReturnDetailsAndPdf() {
        asset.setTinhTrang("Đang sử dụng");
        TaiSanCapPhat assignment = new TaiSanCapPhat();
        assignment.setTaiSan(asset);
        assignment.setNguoiNhan("Người giữ");
        assignment.setPhongBan("Phòng IT");
        ReflectionTestUtils.setField(assignment, "id", 10);
        assignment.setBanGiaoLuc(LocalDate.now().minusMonths(2).atStartOfDay(ZoneId.systemDefault()).toInstant());
        when(assignmentRepository.findByIdWithTaiSan(10)).thenReturn(Optional.of(assignment));
        LocalDate returnDate = LocalDate.now();
        byte[] pdf = {1, 2, 3};
        when(returnRecordPdfGenerator.generate(any(), eq(returnDate))).thenReturn(pdf);
        when(returnRecordRepository.findPdfByAssignmentId(10)).thenReturn(Optional.of(pdf));

        TaiSanCapPhatResponse response = service.returnAsset(10, new ThuHoiTaiSanRequest(
                returnDate, "CHUYEN_PHONG", "Hoạt động tốt", "Trong kho",
                "Đã chuyển phòng", "Admin", "Người giữ"));

        assertEquals("Trong kho", asset.getTinhTrang());
        assertEquals("CHUYEN_PHONG", response.lyDoThuHoi());
        assertEquals("Admin", response.nguoiNhanLai());
        assertEquals("Người giữ", response.nguoiGiaoLai());
        assertEquals(true, response.coBienBanThuHoi());
        assertArrayEquals(pdf, service.returnRecordPdf(10));
        verify(assignmentRepository).save(argThat(saved ->
                saved.getThuHoiLuc().equals(returnDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                        && saved.getLyDoThuHoi().equals("CHUYEN_PHONG")
                        && saved.isCoBienBanThuHoi()));
        verify(returnRecordRepository).save(argThat(record ->
                record.getPhieuBanGiao() == assignment && record.getNoiDungPdf().length == 3));
        verify(returnRecordRepository).findByPhieuBanGiao_Id(10);
    }

    @Test
    void returnUpdatesExistingReturnRecordInsteadOfInsertingADuplicate() {
        asset.setTinhTrang("Đang sử dụng");
        TaiSanCapPhat assignment = new TaiSanCapPhat();
        assignment.setTaiSan(asset);
        ReflectionTestUtils.setField(assignment, "id", 10);
        when(assignmentRepository.findByIdWithTaiSan(10)).thenReturn(Optional.of(assignment));
        TaiSanThuHoiBienBan existingRecord = new TaiSanThuHoiBienBan();
        existingRecord.setPhieuBanGiao(assignment);
        existingRecord.setNoiDungPdf(new byte[] {1});
        when(returnRecordRepository.findByPhieuBanGiao_Id(10)).thenReturn(Optional.of(existingRecord));
        byte[] replacementPdf = {2, 3};
        when(returnRecordPdfGenerator.generate(any(), any())).thenReturn(replacementPdf);

        service.returnAsset(10, returnRequest());

        verify(returnRecordRepository).save(argThat(record ->
                record == existingRecord
                        && record.getPhieuBanGiao() == assignment
                        && Arrays.equals(record.getNoiDungPdf(), replacementPdf)));
    }

    @Test
    void returnRejectsAssetsThatAreNotInUse() {
        asset.setTinhTrang("Bảo trì");
        TaiSanCapPhat assignment = new TaiSanCapPhat();
        assignment.setTaiSan(asset);
        when(assignmentRepository.findByIdWithTaiSan(11)).thenReturn(Optional.of(assignment));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.returnAsset(11, returnRequest()));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void returnRejectsAssetsWithAnOpenRepairTicket() {
        asset.setTinhTrang("Đang sử dụng");
        TaiSanCapPhat assignment = new TaiSanCapPhat();
        assignment.setTaiSan(asset);
        when(assignmentRepository.findByIdWithTaiSan(12)).thenReturn(Optional.of(assignment));
        when(maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(eq(5), anyList())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.returnAsset(12, returnRequest()));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void returnPdfGeneratorCreatesAReadablePdfDocument() {
        TaiSanCapPhat assignment = new TaiSanCapPhat();
        assignment.setTaiSan(asset);
        assignment.setNguoiNhan("Người giữ");
        assignment.setPhongBan("Phòng IT");
        assignment.setLyDoThuHoi("NGHI_VIEC");
        assignment.setTinhTrangLucThuHoi("Thiết bị hoạt động tốt");
        assignment.setTinhTrangSauThuHoi("Trong kho");
        assignment.setNguoiNhanLai("Quản trị viên");
        assignment.setNguoiGiaoLai("Người giữ");

        byte[] pdf = new ReturnRecordPdfGenerator().generate(assignment, LocalDate.now());

        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(pdf, 4));
    }

    @Test
    void creatingRepairForReturnedAssetRestoresItToStockWhenRepairIsFinished() {
        asset.setTinhTrang("Bảo trì");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        service.createMaintenance(new YeuCauBaoTriRequest(5, "Kiểm tra thiết bị",
                "Thiết bị cần kiểm tra sau thu hồi", null, BigDecimal.ZERO, null));

        verify(maintenanceRepository).save(argThat(row -> "Trong kho".equals(row.getTrangThaiTruocBaoTri())));
    }

    @Test
    void createsRepairTicketAndMovesAssignedAssetIntoRepair() {
        asset.setTinhTrang("Đang sử dụng");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        service.createMaintenance(new YeuCauBaoTriRequest(5, "Màn hình bị lỗi",
                "Thiết bị hiển thị sọc", null, BigDecimal.ZERO, null, "Người báo",
                LocalDate.now(), "NGHIEM_TRONG", "NHA_CUNG_CAP", "SUA_CHUA", "HANDOVER"));

        assertEquals("Bảo trì", asset.getTinhTrang());
        verify(maintenanceRepository).save(argThat(row ->
                "Người báo".equals(row.getNguoiYeuCau())
                        && "NGHIEM_TRONG".equals(row.getMucDo())
                        && "NHA_CUNG_CAP".equals(row.getNoiXuLy())
                        && "HANDOVER".equals(row.getNguonTao())
                        && "Đang sử dụng".equals(row.getTrangThaiTruocBaoTri())));
    }

    @Test
    void completingRepairRestoresPreviousAssetStatusAndRecordsResult() {
        asset.setTinhTrang("Bảo trì");
        YeuCauBaoTri repair = openRepair("Đang sử dụng");
        when(maintenanceRepository.findByIdWithTaiSan(20)).thenReturn(Optional.of(repair));

        service.updateMaintenance(20, new CapNhatBaoTriRequest("HOAN_TAT", BigDecimal.valueOf(250000),
                "Đã thay màn hình", "Kỹ thuật viên", LocalDate.now()));

        assertEquals("Đang sử dụng", asset.getTinhTrang());
        assertEquals("Đã thay màn hình", repair.getKetQua());
        assertEquals("Kỹ thuật viên", repair.getNguoiThucHien());
        assertEquals(LocalDate.now(), repair.getNgayHoanThanh());
        assertEquals(BigDecimal.valueOf(250000), repair.getChiPhi());
    }

    @Test
    void unrepairableAssetMovesToWaitingDisposal() {
        asset.setTinhTrang("Bảo trì");
        YeuCauBaoTri repair = openRepair("Trong kho");
        when(maintenanceRepository.findByIdWithTaiSan(21)).thenReturn(Optional.of(repair));

        service.updateMaintenance(21, new CapNhatBaoTriRequest("KHONG_SUA_DUOC", BigDecimal.ZERO,
                "Hư bo mạch, không còn linh kiện", "Nhà cung cấp", LocalDate.now()));

        assertEquals("Chờ thanh lý", asset.getTinhTrang());
        assertEquals("KHONG_SUA_DUOC", repair.getTrangThai());
    }

    @Test
    void rejectsRepairForDisposedAsset() {
        asset.setTinhTrang("Thanh lý");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createMaintenance(new YeuCauBaoTriRequest(5, "Thiết bị hỏng",
                        null, null, BigDecimal.ZERO, null)));

        assertEquals(409, error.getStatusCode().value());
        verify(maintenanceRepository, never()).save(any());
    }

    private YeuCauBaoTri openRepair(String previousStatus) {
        YeuCauBaoTri repair = new YeuCauBaoTri();
        repair.setTaiSan(asset);
        repair.setTrangThai("DANG_XU_LY");
        repair.setTrangThaiTruocBaoTri(previousStatus);
        repair.setNgayBaoHong(LocalDate.now().minusDays(1));
        return repair;
    }

    private ThuHoiTaiSanRequest returnRequest() {
        return new ThuHoiTaiSanRequest(LocalDate.now(), "KHAC", "Tốt", "Trong kho",
                null, "Admin", "Người giữ");
    }

    private TaiSanCapPhatRequest request() {
        return request("Người nhận", null, LocalDate.now(), "Admin", "Người nhận");
    }

    private TaiSanCapPhatRequest request(
            String recipient, String department, LocalDate date, String handoverBy, String receiverSignature) {
        return new TaiSanCapPhatRequest(5, recipient, department, null, "Hoạt động tốt",
                null, null, date, handoverBy, receiverSignature);
    }
}
