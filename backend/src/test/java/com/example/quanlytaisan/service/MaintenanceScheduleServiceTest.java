package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.CompleteRequest;
import com.example.quanlytaisan.dto.MaintenanceScheduleDtos.Request;
import com.example.quanlytaisan.entity.MaintenanceSchedule;
import com.example.quanlytaisan.entity.TaiSan;
import com.example.quanlytaisan.repository.MaintenanceScheduleRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MaintenanceScheduleServiceTest {
    private MaintenanceScheduleRepository scheduleRepository;
    private TaiSanRepository assetRepository;
    private TaiSanCapPhatRepository assignmentRepository;
    private YeuCauBaoTriRepository maintenanceRepository;
    private ThuongMaiOrderRepository orderRepository;
    private MaintenanceScheduleService service;
    private TaiSan asset;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(MaintenanceScheduleRepository.class);
        assetRepository = mock(TaiSanRepository.class);
        assignmentRepository = mock(TaiSanCapPhatRepository.class);
        maintenanceRepository = mock(YeuCauBaoTriRepository.class);
        orderRepository = mock(ThuongMaiOrderRepository.class);
        service = new MaintenanceScheduleService(scheduleRepository, assetRepository, assignmentRepository,
                maintenanceRepository, orderRepository, mock(ActivityLogService.class));

        asset = new TaiSan();
        asset.setTaiSanId(5);
        asset.setMaTaiSan("TS-005");
        asset.setTenTaiSan("Laptop");
        asset.setTinhTrang("Trong kho");
        when(assetRepository.findAllByIdForUpdate(List.of(5))).thenReturn(List.of(asset));
        when(assignmentRepository.findCurrentHolders(anyList())).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceRepository.existsByTaiSan_TaiSanIdAndTrangThaiIn(eq(5), anyList())).thenReturn(false);
        when(orderRepository.isAssetPendingSale(5)).thenReturn(false);
    }

    @Test
    void rejectsEmptyOrNonPositiveCycleBeforePersistence() {
        for (Integer invalidCycle : List.of(0, -1)) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.create(new Request(5, invalidCycle, LocalDate.now())));
            assertEquals(400, error.getStatusCode().value());
        }
        verify(assetRepository, never()).findAllByIdForUpdate(anyList());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void rejectsScheduleForRetiredAsset() {
        asset.setTinhTrang("Thanh lý");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new Request(5, 3, LocalDate.now())));

        assertEquals(409, error.getStatusCode().value());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void completedMaintenanceUpdatesLastServiceAndNextDueDate() {
        LocalDate completedDate = LocalDate.of(2024, 2, 1);
        MaintenanceSchedule schedule = schedule(asset);
        when(scheduleRepository.findByIdForUpdate(7)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.findByTrangThaiNotOrderByIdDesc("DELETED")).thenReturn(List.of(schedule));

        service.complete(7, new CompleteRequest("Vệ sinh thiết bị", "Kỹ thuật viên",
                new BigDecimal("125000"), completedDate));

        assertEquals(completedDate, schedule.getNgayBaoTriGanNhat());
        assertEquals(completedDate.plusMonths(3), service.schedules().get(0).ngayDenHanTiepTheo());
        verify(maintenanceRepository).save(argThat(row ->
                row.getLichBaoTri() == schedule
                        && row.getNgayHoanThanh().equals(completedDate)
                        && row.getNguoiThucHien().equals("Kỹ thuật viên")
                        && row.getChiPhi().compareTo(new BigDecimal("125000")) == 0));
    }

    @Test
    void doesNotCreateRoutineWorkWhileAssetIsUnderRepair() {
        asset.setTinhTrang("Bảo trì");
        MaintenanceSchedule schedule = schedule(asset);
        when(scheduleRepository.findByIdForUpdate(7)).thenReturn(Optional.of(schedule));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.complete(7, new CompleteRequest("Bảo dưỡng", "Kỹ thuật viên",
                        BigDecimal.ZERO, LocalDate.now())));

        assertEquals(409, error.getStatusCode().value());
        verify(maintenanceRepository, never()).save(any());
    }

    private MaintenanceSchedule schedule(TaiSan scheduledAsset) {
        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setTaiSan(scheduledAsset);
        schedule.setChuKyThang(3);
        schedule.setNgayBatDau(LocalDate.of(2024, 1, 1));
        schedule.setTrangThai("ACTIVE");
        return schedule;
    }
}
