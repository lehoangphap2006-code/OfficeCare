package com.example.quanlytaisan.controller;

import com.example.quanlytaisan.repository.AdminInvoiceRepository;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.LoaiTaiSanRepository;
import com.example.quanlytaisan.repository.StocktakeItemRepository;
import com.example.quanlytaisan.repository.TaiSanCapPhatRepository;
import com.example.quanlytaisan.repository.TaiSanImageRepository;
import com.example.quanlytaisan.repository.TaiSanRepository;
import com.example.quanlytaisan.repository.ThuongMaiOrderRepository;
import com.example.quanlytaisan.repository.YeuCauBaoTriRepository;
import com.example.quanlytaisan.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaiSanImageControllerTest {
    private TaiSanImageRepository imageRepository;
    private TaiSanController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        imageRepository = mock(TaiSanImageRepository.class);
        when(imageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        controller = new TaiSanController(
                mock(TaiSanRepository.class),
                imageRepository,
                mock(AdminMasterDataRepository.class),
                mock(AdminInvoiceRepository.class),
                mock(StocktakeItemRepository.class),
                mock(LoaiTaiSanRepository.class),
                mock(ThuongMaiOrderRepository.class),
                mock(TaiSanCapPhatRepository.class),
                mock(YeuCauBaoTriRepository.class),
                mock(ActivityLogService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void savesValidPngAndReturnsItsProtectedImageReference() {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile file = new MockMultipartFile("file", "device.png", "image/png", pngHeader);

        var response = controller.uploadImage(file);

        assertTrue(response.get("imageReference").matches("/api/tai-san/anh/[0-9a-f-]{36}"));
        verify(imageRepository).save(any());
    }

    @Test
    void rejectsUnsupportedContentEvenWhenFileHasData() {
        MockMultipartFile file = new MockMultipartFile("file", "not-image.txt", "text/plain", "not an image".getBytes());

        assertThrows(ResponseStatusException.class, () -> controller.uploadImage(file));
        verify(imageRepository, never()).save(any());
    }

    @Test
    void rejectsImagesLargerThanThirtyMegabytes() {
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png",
                new byte[30 * 1024 * 1024 + 1]);

        assertThrows(ResponseStatusException.class, () -> controller.uploadImage(file));
        verify(imageRepository, never()).save(any());
    }

    @Test
    void postMultipartImageEndpointIsMappedAndAcceptsPng() throws Exception {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile file = new MockMultipartFile("file", "device.png", "image/png", pngHeader);

        mockMvc.perform(multipart("/api/tai-san/anh").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageReference").value(org.hamcrest.Matchers.startsWith("/api/tai-san/anh/")));
    }
}
