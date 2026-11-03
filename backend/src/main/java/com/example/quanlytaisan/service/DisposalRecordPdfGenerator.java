package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.ThuongMaiOrder;
import com.example.quanlytaisan.entity.ThuongMaiOrderItem;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DisposalRecordPdfGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float LEFT = 48;
    private static final float TOP = 790;
    private static final float BOTTOM = 48;
    private static final float LEADING = 17;

    public byte[] generate(ThuongMaiOrder order) {
        List<String> lines = new ArrayList<>(List.of(
                "BIEN BAN THANH LY TAI SAN",
                "Ma phieu: " + value(order.getMaDonHang()),
                "Ngay thanh ly: " + (order.getNgayThanhLy() == null ? "-" : DATE.format(order.getNgayThanhLy())),
                "Ly do: " + reason(order.getLyDoThanhLy()),
                "Chi tiet: " + value(order.getGhiChuThanhLy()),
                "Nguoi yeu cau: " + value(order.getNguoiYeuCau()),
                "Nguoi duyet: " + value(order.getNguoiDuyet()),
                "",
                "TAI SAN | GIA TRI CON LAI | GIA THANH LY"));
        for (ThuongMaiOrderItem item : order.getChiTiet()) {
            lines.add(value(item.getMaTaiSan()) + " | " + value(item.getTenSanPham())
                    + " | " + money(item.getGiaTriConLai()) + " | " + money(item.getDonGia()));
        }
        lines.add("");
        lines.add("Tong gia tri con lai: " + money(order.getChiTiet().stream()
                .map(item -> (item.getGiaTriConLai() == null ? java.math.BigDecimal.ZERO : item.getGiaTriConLai())
                        .multiply(java.math.BigDecimal.valueOf(item.getSoLuong())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)));
        lines.add("Tong gia thanh ly: " + money(order.getTongTien()));
        lines.add("");
        lines.add("Nguoi lap phieu ky, ghi ro ho ten: _________________________");
        lines.add("");
        lines.add("Nguoi duyet thanh ly ky, ghi ro ho ten: _________________________");
        return createPdf(wrap(lines), hasUnicodeFontAvailable());
    }

    private byte[] createPdf(List<String> lines, boolean unicodeFontAvailable) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            int linesPerPage = (int) ((TOP - BOTTOM) / LEADING);
            for (int start = 0; start < lines.size(); start += linesPerPage) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 10);
                    content.setLeading(LEADING);
                    content.newLineAtOffset(LEFT, TOP);
                    int end = Math.min(start + linesPerPage, lines.size());
                    for (int index = start; index < end; index++) {
                        String line = lines.get(index);
                        content.showText(unicodeFontAvailable ? line : ascii(line));
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo biên bản thanh lý PDF", exception);
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        InputStream bundledFont = DisposalRecordPdfGenerator.class.getResourceAsStream(
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
        return DisposalRecordPdfGenerator.class.getResource(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf") != null
                || windows != null && Files.isRegularFile(Path.of(windows, "Fonts", "arial.ttf"))
                || Files.isRegularFile(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
                || Files.isRegularFile(Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
    }

    private List<String> wrap(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            StringBuilder wrapped = new StringBuilder();
            for (String word : line.split("\\s+")) {
                if (word.length() > 95) {
                    if (!wrapped.isEmpty()) {
                        result.add(wrapped.toString());
                        wrapped.setLength(0);
                    }
                    for (int index = 0; index < word.length(); index += 95) {
                        result.add(word.substring(index, Math.min(index + 95, word.length())));
                    }
                    continue;
                }
                if (!wrapped.isEmpty() && wrapped.length() + word.length() + 1 > 95) {
                    result.add(wrapped.toString());
                    wrapped.setLength(0);
                }
                if (!wrapped.isEmpty()) wrapped.append(' ');
                wrapped.append(word);
            }
            result.add(wrapped.toString());
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
            case "HONG", "HONG_KHONG_SUA_DUOC" -> "Hong khong sua duoc";
            case "LOI_THOI" -> "Loi thoi";
            case "MAT" -> "Mat";
            case "NHUONG_LAI" -> "Nhuong lai";
            case "HET_NIEN_HAN" -> "Het nien han su dung";
            case "KHAC" -> "Ly do khac";
            default -> "-";
        };
    }

    private String money(java.math.BigDecimal amount) {
        return (amount == null ? java.math.BigDecimal.ZERO : amount).toPlainString() + " VND";
    }
}
