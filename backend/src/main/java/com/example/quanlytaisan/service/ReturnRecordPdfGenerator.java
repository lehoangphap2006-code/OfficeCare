package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.TaiSanCapPhat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReturnRecordPdfGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generate(TaiSanCapPhat assignment, LocalDate returnDate) {
        var asset = assignment.getTaiSan();
        List<String> lines = List.of(
                "BIÊN BẢN THU HỒI TÀI SẢN",
                "Phiếu thu hồi: #" + assignment.getId(),
                "Ngày thu hồi: " + DATE.format(returnDate),
                "Mã tài sản: " + value(asset.getMaTaiSan()),
                "Tên tài sản: " + value(asset.getTenTaiSan()),
                "Serial: " + value(asset.getSerialNumber()),
                "Người giữ trước thu hồi: " + value(assignment.getNguoiNhan()),
                "Phòng ban: " + value(assignment.getPhongBan()),
                "Lý do thu hồi: " + reason(assignment.getLyDoThuHoi()),
                "Tình trạng khi nhận lại: " + value(assignment.getTinhTrangLucThuHoi()),
                "Trạng thái sau thu hồi: " + status(assignment.getTinhTrangSauThuHoi()),
                "Ghi chú: " + value(assignment.getGhiChuThuHoi()),
                "",
                "Người nhận lại: " + value(assignment.getNguoiNhanLai()),
                "Người giao lại: " + value(assignment.getNguoiGiaoLai()),
                "",
                "Người nhận lại ký, ghi rõ họ tên: _________________________",
                "",
                "Người giao lại ký, ghi rõ họ tên: _________________________");
        return createPdf(wrap(lines), hasUnicodeFontAvailable());
    }

    private byte[] createPdf(List<String> lines, boolean unicodeFontAvailable) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.setLeading(22);
                content.newLineAtOffset(48, 790);
                for (String line : lines) {
                    content.showText(unicodeFontAvailable ? line : ascii(line));
                    content.newLine();
                }
                content.endText();
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo biên bản thu hồi PDF", exception);
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        InputStream bundledFont = ReturnRecordPdfGenerator.class.getResourceAsStream(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
        if (bundledFont != null) {
            try (bundledFont) {
                return PDType0Font.load(document, bundledFont);
            }
        }
        String windows = System.getenv("WINDIR");
        List<Path> candidates = new ArrayList<>();
        if (windows != null) candidates.add(Path.of(windows, "Fonts", "arial.ttf"));
        candidates.add(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        candidates.add(Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try (InputStream font = Files.newInputStream(candidate)) {
                    return PDType0Font.load(document, font);
                }
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private boolean hasUnicodeFontAvailable() {
        String windows = System.getenv("WINDIR");
        return ReturnRecordPdfGenerator.class.getResource(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf") != null
                || windows != null && Files.isRegularFile(Path.of(windows, "Fonts", "arial.ttf"))
                || Files.isRegularFile(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
                || Files.isRegularFile(Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
    }

    private List<String> wrap(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            if (line.length() <= 100) {
                result.add(line);
                continue;
            }
            StringBuilder wrapped = new StringBuilder();
            for (String word : line.split("\\s+")) {
                if (!wrapped.isEmpty() && wrapped.length() + word.length() + 1 > 100) {
                    result.add(wrapped.toString());
                    wrapped.setLength(0);
                }
                if (!wrapped.isEmpty()) wrapped.append(' ');
                wrapped.append(word);
            }
            if (!wrapped.isEmpty()) result.add(wrapped.toString());
        }
        return result;
    }

    private String ascii(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replace("Đ", "D").replace("đ", "d").replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "-" : value
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\p{Cc}", "");
    }

    private String reason(String reason) {
        return switch (reason == null ? "" : reason) {
            case "NGHI_VIEC" -> "Nghỉ việc";
            case "CHUYEN_PHONG" -> "Chuyển phòng";
            case "HET_NHU_CAU" -> "Hết nhu cầu";
            case "KHAC" -> "Khác";
            default -> "-";
        };
    }

    private String status(String status) {
        return switch (status == null ? "" : status) {
            case "Bảo trì" -> "Đang sửa chữa";
            case "Chờ thanh lý" -> "Chờ thanh lý";
            default -> value(status);
        };
    }
}
