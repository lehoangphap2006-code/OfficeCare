package com.example.quanlytaisan.service;

import com.example.quanlytaisan.entity.StocktakeExtra;
import com.example.quanlytaisan.entity.StocktakeItem;
import com.example.quanlytaisan.entity.StocktakeSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StocktakePdfGenerator {
    private static final Logger logger = LoggerFactory.getLogger(StocktakePdfGenerator.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generate(StocktakeSession session, List<StocktakeItem> items, List<StocktakeExtra> extras) {
        List<String> lines = new ArrayList<>();
        long missing = items.stream().filter(i -> "THIEU".equals(i.getConditionStatus())).count();
        long damaged = items.stream().filter(i -> "HONG".equals(i.getConditionStatus())).count();
        long misplaced = items.stream().filter(i -> "SAI_VI_TRI".equals(i.getConditionStatus())).count();
        lines.add("BIÊN BẢN KIỂM KÊ TÀI SẢN");
        lines.add("Đợt kiểm kê: " + session.getName());
        lines.add("Phạm vi: " + stocktakeScope(session));
        lines.add("Ngày bắt đầu: " + (session.getStocktakeDate() == null ? "" : DATE.format(session.getStocktakeDate())));
        lines.add("Ngày kết thúc: " + (session.getEndDate() == null ? "" : DATE.format(session.getEndDate())));
        lines.add("Người phụ trách: " + value(session.getResponsibleUsername()));
        lines.add("Người kiểm: " + value(session.getCheckerName()));
        lines.add("Người xác nhận: " + value(session.getConfirmerName()));
        lines.add("Tổng theo sổ: " + items.size() + " | Đã kiểm: " + items.stream().filter(i -> i.getCheckedAt() != null).count());
        lines.add("Thiếu: " + missing + " | Hỏng: " + damaged + " | Sai vị trí: " + misplaced + " | Thừa: " + extras.size());
        lines.add("");
        lines.add("CHI TIẾT TÀI SẢN THEO SỔ");
        for (StocktakeItem item : items) {
            lines.add(value(item.getAsset().getMaTaiSan()) + " | " + value(item.getAsset().getTenTaiSan())
                    + " | " + status(item.getConditionStatus()) + " | Theo sổ: " + value(item.getBookLocation())
                    + " | Thực tế: " + value(item.getActualLocation()) + " | " + value(item.getNotes()));
        }
        lines.add("");
        lines.add("TÀI SẢN THỪA (CÓ THỰC TẾ, KHÔNG CÓ TRONG SỔ)");
        for (StocktakeExtra extra : extras) {
            lines.add(value(extra.getAssetCode()) + " | " + value(extra.getAssetName())
                    + " | " + value(extra.getActualLocation()) + " | " + value(extra.getNotes()));
        }
        lines.add("");
        lines.add("Người kiểm: " + value(session.getCheckerName()) + "          Người xác nhận: " + value(session.getConfirmerName()));
        lines.add("");
        lines.add("Ký, ghi rõ họ tên: _________________________          Ký, ghi rõ họ tên: _________________________");
        List<String> wrappedLines = wrap(lines);
        boolean unicodeFontAvailable = hasUnicodeFontAvailable();
        try {
            return createPdf(wrappedLines, unicodeFontAvailable);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (!unicodeFontAvailable) throw exception;
            logger.warn("Stocktake report contains text unsupported by the Unicode font; retrying with ASCII text.",
                    exception);
            return createPdf(wrappedLines, false);
        }
    }

    private byte[] createPdf(List<String> lines, boolean unicodeFontAvailable) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            for (int start = 0; start < lines.size(); start += 48) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 9);
                    content.setLeading(14);
                    content.newLineAtOffset(42, 790);
                    for (String line : lines.subList(start, Math.min(start + 48, lines.size()))) {
                        content.showText(unicodeFontAvailable ? line : ascii(line));
                        content.newLine();
                    }
                    content.endText();
                }
            }
            try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo biên bản kiểm kê PDF", e);
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        InputStream bundledFont = StocktakePdfGenerator.class.getResourceAsStream(
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
        return StocktakePdfGenerator.class.getResource(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf") != null
                || windows != null && Files.isRegularFile(Path.of(windows, "Fonts", "arial.ttf"))
                || Files.isRegularFile(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
                || Files.isRegularFile(Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"));
    }

    private List<String> wrap(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            if (line.length() <= 105) {
                result.add(line);
                continue;
            }
            StringBuilder wrapped = new StringBuilder();
            for (String word : line.split("\\s+")) {
                if (!wrapped.isEmpty() && wrapped.length() + word.length() + 1 > 105) {
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
        return value == null || value.isBlank() ? "-" : value;
    }

    private String stocktakeScope(StocktakeSession session) {
        return switch (session.getScopeType()) {
            case "DEPARTMENT" -> "Phòng ban: " + value(session.getScopeValue());
            case "LOCATION" -> "Vị trí: " + value(session.getScopeValue());
            default -> "Toàn công ty";
        };
    }

    private String status(String status) {
        return switch (status == null ? "" : status) {
            case "CO_MAT" -> "Có mặt";
            case "THIEU" -> "Thiếu";
            case "HONG" -> "Hỏng";
            case "SAI_VI_TRI" -> "Sai vị trí";
            default -> "Chưa kiểm";
        };
    }
}
