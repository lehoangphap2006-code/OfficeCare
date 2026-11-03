CREATE DATABASE IF NOT EXISTS quan_ly_tai_san
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE quan_ly_tai_san;

-- Các bảng sẽ được Spring Data JPA tạo từ entity khi chạy ứng dụng.
-- Khi CSDL còn trống, ứng dụng tự tạo danh mục và 5 tài sản mẫu khi khởi động lần đầu.
-- Bảng tai_san có khóa ngoại loai_id trỏ đến loai_tai_san.loai_id.

-- Nâng cột lưu biên bản thanh lý từ TINYBLOB lên LONGBLOB nếu bảng đã được tạo.
-- Có thể chạy lại script này an toàn sau khi backend đã khởi tạo các bảng.
SET @disposal_content_type = (
  SELECT DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'thuong_mai_bien_ban_thanh_ly'
    AND COLUMN_NAME = 'content'
);
SET @disposal_content_migration = IF(
  @disposal_content_type IS NOT NULL AND @disposal_content_type <> 'longblob',
  'ALTER TABLE thuong_mai_bien_ban_thanh_ly MODIFY COLUMN content LONGBLOB NOT NULL',
  'SELECT 1'
);
PREPARE disposal_content_statement FROM @disposal_content_migration;
EXECUTE disposal_content_statement;
DEALLOCATE PREPARE disposal_content_statement;
