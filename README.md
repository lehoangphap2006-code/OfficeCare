# Hệ thống Quản lý Tài sản (OfficeCare)

> Đồ án môn **Lập trình Java** — Khoa Công nghệ Thông tin, Trường Cao đẳng Công Thương TP. Hồ Chí Minh.

Ứng dụng web quản lý toàn bộ **vòng đời tài sản** của một tổ chức: nhập kho → cấp phát → bảo trì → kiểm kê → thanh lý. Backend là REST API xây dựng bằng Spring Boot, frontend là ứng dụng React (Vite), dữ liệu lưu trong MySQL.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)
![React](https://img.shields.io/badge/React-19-61dafb)
![Vite](https://img.shields.io/badge/Vite-6-646cff)

---

## Mục lục

- [Giới thiệu](#giới-thiệu)
- [Tính năng](#tính-năng)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Cài đặt và chạy](#cài-đặt-và-chạy)
- [Cấu hình biến môi trường](#cấu-hình-biến-môi-trường)
- [Tài khoản thử nghiệm](#tài-khoản-thử-nghiệm)
- [Tổng quan REST API](#tổng-quan-rest-api)
- [Thiết kế cơ sở dữ liệu](#thiết-kế-cơ-sở-dữ-liệu)
- [Bảo mật](#bảo-mật)
- [Kiểm thử](#kiểm-thử)
- [Hạn chế và hướng phát triển](#hạn-chế-và-hướng-phát-triển)
- [Thông tin nhóm thực hiện](#thông-tin-nhóm-thực-hiện)

---

## Giới thiệu

Quản lý tài sản cố định (thiết bị văn phòng, máy tính, máy móc…) bằng sổ sách hoặc Excel thường dẫn đến dữ liệu không đồng bộ, khó theo dõi trạng thái theo thời gian thực và thiếu cơ chế kiểm soát việc cấp phát, bảo trì, thanh lý.

Dự án giải quyết bài toán này bằng một hệ thống tập trung, phân quyền rõ ràng cho hai nhóm người dùng:

| Vai trò | Quyền hạn |
| --- | --- |
| **ADMIN** (Quản trị viên) | Toàn quyền: quản lý tài sản, danh mục, cấp phát/thu hồi, bảo trì, kiểm kê, mua sắm, thanh lý, người dùng, xem nhật ký. |
| **USER** (Nhân viên) | Xem tài sản được giao, báo hỏng/bảo trì, gửi yêu cầu mua sắm, yêu cầu trả tài sản, xem thông báo, cập nhật thông tin cá nhân. |

## Tính năng

**Quản lý tài sản**
- CRUD tài sản, tìm kiếm và lọc đa tiêu chí (từ khóa, tình trạng, loại, phòng ban, khoảng ngày mua), có phân trang.
- Lưu mã, tên, loại, serial, hãng, model, giá/ngày mua, nhà cung cấp, hạn bảo hành, vị trí (tòa nhà/tầng/phòng), ảnh và ghi chú.
- Tự động tính **khấu hao đường thẳng** và hiển thị giá trị còn lại.
- Thống kê tổng số, tình trạng và tổng giá trị trên toàn bộ dữ liệu.
- Quản lý loại tài sản kèm thời gian khấu hao mặc định (1–50 năm).

**Vòng đời tài sản**
- **Cấp phát / thu hồi** tài sản cho nhân viên hoặc phòng ban; xuất **biên bản thu hồi PDF**.
- **Bảo trì / sửa chữa**: tạo phiếu, theo dõi tiến độ, ghi nhận chi phí, tự tính ngày bảo trì kế tiếp theo chu kỳ; lịch bảo trì định kỳ có thông báo.
- **Kiểm kê**: tạo phiên kiểm kê theo phòng ban, ghi nhận có mặt/thiếu/hỏng, xuất CSV chênh lệch và **biên bản PDF**.
- **Thanh lý**: lập phiếu, ADMIN phê duyệt, tính lãi/lỗ theo giá trị còn lại, xuất **biên bản thanh lý PDF**.

**Mua sắm và tài chính**
- Quy trình: yêu cầu → phê duyệt → đặt hàng → nhận hàng → nhập kho; đối chiếu số lượng thực nhận.
- Quản lý nhà cung cấp, hóa đơn, công nợ nhà cung cấp.
- Thanh toán tiền mặt, VietQR (đối chiếu thủ công) và **MoMo** (sandbox, xác minh chữ ký IPN).
- Quản lý **vật tư tiêu hao**: tồn kho, mức tối thiểu, phiếu xuất theo phòng ban, cảnh báo sắp hết.

**Hệ thống**
- Đăng nhập **JWT** (access + refresh token) và **Google OAuth2** (tùy chọn).
- Dashboard cho Admin với biểu đồ và cảnh báo (bảo hành, bảo trì, thu hồi quá hạn, vật tư sắp hết).
- Nhật ký hoạt động (CREATE/UPDATE/DELETE kèm dữ liệu trước/sau) và thông báo trong hệ thống.
- Báo cáo tổng hợp theo phòng ban / loại / trạng thái, xuất CSV (mở được bằng Excel) hoặc in/lưu PDF.
- Giao diện sáng/tối.

## Công nghệ sử dụng

| Nhóm | Công nghệ | Vai trò |
| --- | --- | --- |
| Ngôn ngữ / Backend | Java 17, Spring Boot 3.5 (Spring Web) | REST API, xử lý nghiệp vụ |
| ORM | Spring Data JPA, Hibernate | Ánh xạ entity – bảng, truy vấn JPQL |
| Cơ sở dữ liệu | MySQL 8.x | Lưu trữ dữ liệu |
| Bảo mật | Spring Security, jjwt 0.12.6 | Xác thực JWT, phân quyền ROLE_ADMIN / ROLE_USER |
| OAuth2 | Spring OAuth2 Client | Đăng nhập Google |
| Tạo PDF | Apache PDFBox 3.0.5 | Biên bản thu hồi, kiểm kê, thanh lý |
| Thanh toán | MoMo Payment API | Thanh toán đơn mua sắm |
| Build | Apache Maven (có sẵn Maven Wrapper) | Quản lý phụ thuộc, đóng gói JAR |
| Frontend | React 19, Vite 6, QRCode | Giao diện người dùng và quản trị |
| Kiểm thử | JUnit 5, Spring Boot Test, Vitest | Unit test và integration test |

## Cấu trúc thư mục

```text
quanlytaisan/
├── database.sql                  # Script tạo database (tùy chọn)
├── README_HUONG_DAN.md           # Tài liệu hướng dẫn chi tiết nghiệp vụ và API
├── backend/                      # Spring Boot backend
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd            # Maven Wrapper
│   └── src/
│       ├── main/
│       │   ├── java/com/example/quanlytaisan/
│       │   │   ├── QuanLyTaiSanApplication.java   # Điểm khởi động
│       │   │   ├── config/       # Security, JWT filter, rate limit, Google OAuth, dữ liệu mẫu
│       │   │   ├── controller/   # REST controllers
│       │   │   ├── dto/          # Data Transfer Objects (Java records)
│       │   │   ├── entity/       # JPA entities
│       │   │   ├── repository/   # Spring Data JPA repositories
│       │   │   └── service/      # Logic nghiệp vụ, MoMo, PDF generators
│       │   └── resources/
│       │       └── application.properties
│       └── test/                 # JUnit tests
└── frontend/                     # React + Vite
    ├── index.html
    ├── style.css
    ├── vite.config.js
    ├── package.json
    └── src/
        ├── main.jsx, App.jsx     # Giao diện chính
        ├── UserWorkspace.jsx     # Không gian làm việc của nhân viên
        ├── authToken.js          # Quản lý token
        ├── sessionFetch.js       # fetch tự động đính kèm và gia hạn token
        └── admin/                # Các trang quản trị (Dashboard, Procurement, Disposal, ...)
```

### Các controller chính

| Controller | Endpoint gốc | Chức năng |
| --- | --- | --- |
| `AuthController` | `/api/auth` | Đăng nhập, đăng ký, refresh token, hồ sơ, đổi mật khẩu, Google OAuth |
| `TaiSanController` | `/api/tai-san` | CRUD tài sản, ảnh, thống kê |
| `LoaiTaiSanController` | `/api/loai-tai-san` | CRUD loại tài sản |
| `TaiSanVongDoiController` | `/api/cap-phat`, `/api/bao-tri` | Cấp phát, thu hồi, bảo trì |
| `ThuongMaiController` | `/api/thuong-mai` | Đơn mua sắm, thanh lý, nhập kho |
| `MomoPaymentController` | `/api/payments/momo` | Thanh toán MoMo và IPN |
| `AdminFoundationController` | `/api/admin/**` | Nhân viên, phòng ban, vị trí, nhà cung cấp, vật tư, hóa đơn, khấu hao, kiểm kê, báo cáo, dashboard |
| `AdminPurchaseRequestController` | `/api/admin/purchase-requests` | Duyệt yêu cầu mua sắm của nhân viên |
| `AdminReturnRequestController` | `/api/admin/return-requests` | Xử lý yêu cầu trả tài sản |
| `AdminUserController` | `/api/admin/users` | Quản lý tài khoản |
| `MaintenanceScheduleController` | `/api/admin/maintenance-schedules` | Lịch bảo trì định kỳ |
| `PersonalWorkspaceController` và các controller `/api/me/**` | `/api/me` | Không gian cá nhân của nhân viên |
| `ActivityController` | `/api/nhat-ky`, `/api/thong-bao` | Nhật ký hoạt động và thông báo |

## Cài đặt và chạy

### Yêu cầu

- **JDK 17** trở lên
- **MySQL 8.x** (có thể dùng XAMPP)
- **Node.js** (kèm npm) cho frontend
- **Maven 3.x** (hoặc dùng `mvnw` có sẵn trong thư mục `backend`)

### 1. Lấy mã nguồn

```bash
git clone <đường-dẫn-repository>
cd quanlytaisan
```

### 2. Chuẩn bị cơ sở dữ liệu

Khởi động MySQL. Ứng dụng tự tạo database `quan_ly_tai_san` (nhờ `createDatabaseIfNotExist=true`) và tự tạo bảng từ entity (`ddl-auto=update`), nên **không cần tạo bảng thủ công**. Tài khoản MySQL cần có quyền tạo database.

Nếu muốn tạo database trước, chạy:

```bash
mysql -u root -p < database.sql
```

Mặc định backend kết nối bằng `root` với mật khẩu rỗng. Nếu MySQL của bạn khác, sửa trong `backend/src/main/resources/application.properties`:

```properties
spring.datasource.username=root
spring.datasource.password=
```

### 3. Chạy backend

```bash
cd backend
mvn spring-boot:run
# hoặc: ./mvnw spring-boot:run   (Windows: mvnw.cmd spring-boot:run)
```

Backend chạy tại **http://localhost:8081**. Khi CSDL còn trống, ứng dụng tự tạo danh mục, 5 tài sản mẫu và hai tài khoản demo.

> Nếu không đặt `JWT_SECRET`, hệ thống dùng khóa ngẫu nhiên tạm thời; token sẽ mất hiệu lực khi khởi động lại backend. Với môi trường thật, hãy đặt khóa cố định (xem phần [Cấu hình](#cấu-hình-biến-môi-trường)).

### 4. Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

| Địa chỉ | Mô tả |
| --- | --- |
| http://127.0.0.1:5500/ | Giao diện người dùng |
| http://127.0.0.1:5500/admin | Khu vực quản trị (chỉ ADMIN) |

Cổng `5500` được cố định (`--strictPort`) và cần khớp với cấu hình CORS của backend (chỉ cho phép `http://localhost:5500` và `http://127.0.0.1:5500`). Nếu cổng đang bị chiếm, hãy dừng tiến trình cũ trước khi chạy.

Build bản production:

```bash
npm run build      # kết quả trong frontend/dist
npm run preview
```

### Đóng gói backend thành JAR

```bash
cd backend
mvn clean package
java -jar target/quan-ly-tai-san-0.0.1-SNAPSHOT.jar
```

## Cấu hình biến môi trường

Các biến được đọc trong `application.properties`. Đặt biến môi trường trước khi chạy backend.

Ví dụ (Linux/macOS):

```bash
export JWT_SECRET="<chuỗi Base64 tối thiểu 256-bit>"
```

Ví dụ (Windows PowerShell):

```powershell
$env:JWT_SECRET = "<chuỗi Base64 tối thiểu 256-bit>"
```

| Biến | Mặc định | Mô tả |
| --- | --- | --- |
| `JWT_SECRET` | *(sinh ngẫu nhiên mỗi lần chạy)* | Khóa ký JWT (Base64). **Bắt buộc đặt cố định khi triển khai thật.** |
| `JWT_EXPIRATION_MINUTES` | `480` | Thời hạn access token (phút) |
| `JWT_REFRESH_EXPIRATION_DAYS` | `30` | Thời hạn refresh token (ngày) |
| `LOGIN_MAX_FAILED_ATTEMPTS` | `5` | Số lần đăng nhập sai tối đa trước khi khóa tài khoản |
| `LOGIN_LOCK_MINUTES` | `15` | Thời gian khóa tài khoản (phút) |
| `LOGIN_IP_MAX_ATTEMPTS` | `20` | Số lần đăng nhập tối đa theo IP trong một khung thời gian |
| `LOGIN_IP_WINDOW_SECONDS` | `60` | Độ dài khung thời gian giới hạn theo IP (giây) |
| `SEED_DEMO_USERS` | `true` | Tạo tài khoản và dữ liệu mẫu khi khởi động |
| `DEMO_ADMIN_PASSWORD` | `Admin@12345` | Mật khẩu tài khoản `admin` demo |
| `DEMO_USER_PASSWORD` | `User@12345` | Mật khẩu tài khoản `user` demo |
| `GOOGLE_OAUTH_ENABLED` | `false` | Bật/tắt đăng nhập Google |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | *(rỗng)* | Thông tin OAuth2 từ Google Cloud Console |
| `FRONTEND_URL` | `http://localhost:5500` | Địa chỉ frontend để chuyển hướng sau khi đăng nhập Google |
| `MOMO_ENABLED` | `false` | Bật/tắt thanh toán MoMo |
| `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` | *(rỗng)* | Thông tin merchant MoMo |
| `MOMO_ENDPOINT` | `https://test-payment.momo.vn/v2/gateway/api/create` | Endpoint MoMo (mặc định là sandbox) |
| `MOMO_REDIRECT_URL` | `http://localhost:5500/admin/procurement?momo_return=1` | URL quay về sau thanh toán |
| `MOMO_IPN_URL` | *(rỗng)* | URL IPN công khai (HTTPS) để MoMo gọi lại |

**Đăng nhập Google:** đặt `GOOGLE_OAUTH_ENABLED=true` cùng `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FRONTEND_URL`. Khi chạy local, Redirect URI cần khai báo trên Google là `http://localhost:8081/login/oauth2/code/google`.

**MoMo:** cần đặt `MOMO_ENABLED=true`, các khóa merchant và một `MOMO_IPN_URL` HTTPS công khai. Không lưu khóa vào source control.

## Tài khoản thử nghiệm

Được tạo tự động khi `SEED_DEMO_USERS=true`:

| Tài khoản | Mật khẩu | Vai trò |
| --- | --- | --- |
| `admin` | `Admin@12345` | ADMIN |
| `user` | `User@12345` | USER |

> ⚠️ Chỉ dùng cho môi trường demo. Khi triển khai thật: đặt `SEED_DEMO_USERS=false`, hoặc đổi mật khẩu bằng `DEMO_ADMIN_PASSWORD` / `DEMO_USER_PASSWORD` **trước lần chạy đầu tiên** (tài khoản đã tạo sẽ không tự đổi mật khẩu khi thay biến).

## Tổng quan REST API

Gửi token trong header `Authorization: Bearer <accessToken>`. Các API công khai chỉ gồm đăng nhập, đăng ký, refresh token, trạng thái/đổi mã Google và IPN của MoMo.

**Xác thực**

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| POST | `/api/auth/login` | Đăng nhập, trả `accessToken` và `refreshToken` |
| POST | `/api/auth/register` | Đăng ký tài khoản USER |
| POST | `/api/auth/refresh` | Làm mới token (xoay vòng refresh token) |
| POST | `/api/auth/logout` | Đăng xuất, thu hồi token |
| GET | `/api/auth/me` | Thông tin tài khoản hiện tại |
| GET / PUT | `/api/auth/profile` | Xem / cập nhật email, số điện thoại |
| PUT | `/api/auth/password` | Đổi mật khẩu |

**Tài sản và danh mục** *(ADMIN)*

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET | `/api/tai-san` | Danh sách, tìm kiếm, lọc, phân trang |
| GET | `/api/tai-san/{id}` | Chi tiết tài sản |
| POST | `/api/tai-san` | Thêm tài sản (201) |
| PUT | `/api/tai-san/{id}` | Cập nhật tài sản |
| DELETE | `/api/tai-san/{id}` | Xóa tài sản (có kiểm tra ràng buộc) |
| GET | `/api/tai-san/thong-ke` | Thống kê |
| GET / POST | `/api/loai-tai-san` | Danh sách / thêm loại tài sản |
| PUT / DELETE | `/api/loai-tai-san/{id}` | Sửa / xóa loại tài sản |

Ví dụ tìm kiếm:

```http
GET /api/tai-san?keyword=laptop&tinhTrang=Trong%20kho&page=0&size=8
```

**Cấp phát và bảo trì** *(ADMIN)*

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET / POST | `/api/cap-phat` | Danh sách / cấp phát tài sản |
| POST | `/api/cap-phat/{id}/thu-hoi` | Thu hồi tài sản |
| GET | `/api/cap-phat/{id}/bien-ban-thu-hoi.pdf` | Xuất biên bản thu hồi PDF |
| GET / POST | `/api/bao-tri` | Danh sách / tạo yêu cầu bảo trì |
| PUT | `/api/bao-tri/{id}` | Cập nhật trạng thái bảo trì |

**Mua sắm, thanh lý, thanh toán**

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET / POST | `/api/thuong-mai/don-hang` | Danh sách / tạo đơn mua hoặc phiếu thanh lý |
| POST | `/api/thuong-mai/don-hang/{id}/duyet` | Duyệt đơn mua (ADMIN) |
| POST | `/api/thuong-mai/don-hang/{id}/nhan-hang` | Nhận hàng và nhập kho (ADMIN) |
| POST | `/api/thuong-mai/don-hang/{id}/duyet-thanh-ly` | Duyệt thanh lý (ADMIN) |
| GET | `/api/thuong-mai/don-hang/{id}/bien-ban-thanh-ly` | Biên bản thanh lý PDF |
| POST | `/api/payments/momo/don-hang/{id}/checkout` | Tạo phiên thanh toán MoMo |
| POST | `/api/payments/momo/ipn` | Nhận IPN từ MoMo (xác minh HMAC-SHA256) |

**Quản trị** *(chỉ ADMIN, `/api/admin/**`)*

`/dashboard`, `/reports`, `/nhan-vien`, `/phong-ban`, `/vi-tri`, `/nha-cung-cap`, `/vat-tu`, `/hoa-don`, `/khau-hao`, `/kiem-ke` (kèm `/{id}/report.pdf`, `/{id}/variance.csv`), `/users`, `/purchase-requests`, `/return-requests`, `/maintenance-schedules`.

**Nhân viên** *(`/api/me/**`)*

`/assets`, `/categories`, `/maintenance`, `/purchase-requests`, `/return-requests`, `/consumables`.

**Khác:** `GET /api/nhat-ky` (nhật ký, ADMIN), `GET /api/thong-bao` và `PUT /api/thong-bao/{id}/da-xem` (thông báo của tài khoản hiện tại).

> Danh sách đầy đủ và mô tả chi tiết payload xem trong [`README_HUONG_DAN.md`](README_HUONG_DAN.md).

## Thiết kế cơ sở dữ liệu

Các bảng chính (được Hibernate sinh tự động từ entity):

| Bảng | Vai trò |
| --- | --- |
| `tai_san` | Thông tin tài sản cố định (N–1 với `loai_tai_san`) |
| `loai_tai_san` | Danh mục loại tài sản, thời gian khấu hao |
| `tai_san_cap_phat` | Lịch sử cấp phát / thu hồi |
| `tai_san_thu_hoi_bien_ban` | Biên bản thu hồi |
| `tai_san_image` | Ảnh tài sản |
| `yeu_cau_bao_tri`, `maintenance_schedule` | Phiếu bảo trì và lịch bảo trì định kỳ |
| `app_users` | Tài khoản người dùng |
| `revoked_token` | Danh sách JWT đã thu hồi |
| `admin_employee`, `admin_master_data` | Nhân viên; danh mục phòng ban, vị trí, nhà cung cấp |
| `thuong_mai_order`, `thuong_mai_order_item` | Đơn mua sắm / thanh lý và các dòng hàng |
| `purchase_request` | Yêu cầu mua sắm từ nhân viên |
| `asset_return_request` | Yêu cầu trả tài sản của nhân viên |
| `stocktake_session`, `stocktake_item`, `stocktake_extra` | Phiên kiểm kê, kết quả và tài sản phát sinh |
| `consumable`, `consumable_*` | Vật tư tiêu hao, phiếu nhập/xuất, yêu cầu |
| `admin_invoice`, `admin_invoice_*` | Hóa đơn, dòng hóa đơn, thanh toán, điều chỉnh, tệp đính kèm |
| `stock_receipt`, `stock_receipt_item` | Phiếu nhập kho |
| `activity_log` | Nhật ký hoạt động |
| `app_notification` | Thông báo trong hệ thống |

Công thức khấu hao đường thẳng:

```text
Giá trị còn lại = Giá mua − (Giá mua × Số ngày đã sử dụng / Tổng số ngày sử dụng hữu ích)
```

Thời gian sử dụng lấy từ tài sản, hoặc từ loại tài sản nếu tài sản chưa khai báo; giá trị thấp nhất là 0.

## Bảo mật

- **JWT stateless**: access token 480 phút, refresh token 30 ngày, có xoay vòng refresh token.
- **Thu hồi token** khi đăng xuất (lưu trong CSDL).
- **Phân quyền** theo vai trò: `/api/admin/**` chỉ dành cho `ROLE_ADMIN`, các API còn lại yêu cầu đăng nhập; mật khẩu băm BCrypt.
- **Chống brute-force**: khóa tài khoản sau 5 lần sai (15 phút), giới hạn đăng nhập theo IP (mặc định 20 lần/60 giây).
- **HTTP Security headers**: CSP, HSTS, Referrer-Policy, X-Frame-Options.
- **CORS** giới hạn theo origin frontend; tắt CSRF vì không dùng session.
- Khóa bi quan (`PESSIMISTIC_WRITE`) cho các thao tác đồng thời; kiểm tra magic bytes khi tải ảnh lên.
- Đăng nhập Google không tự liên kết với tài khoản có sẵn chỉ dựa trên email; mã callback dùng một lần, hết hạn sau 2 phút.

## Kiểm thử

**Backend** (JUnit 5, Spring Boot Test, MockMvc) — gồm 21 lớp kiểm thử, bao phủ JWT, đăng nhập/refresh/Google, CRUD tài sản và loại tài sản, cấp phát, bảo trì định kỳ, thanh lý, nhập kho, MoMo, tạo PDF kiểm kê và các luồng nghiệp vụ (vật tư, khấu hao hóa đơn, yêu cầu trả, yêu cầu mua sắm, phạm vi kiểm kê).

```bash
cd backend
mvn test
```

**Frontend** (Vitest):

```bash
cd frontend
npm test
```

## Hạn chế và hướng phát triển

**Hạn chế hiện tại**
- Ảnh tài sản lưu dạng binary trong MySQL, có thể ảnh hưởng hiệu năng khi dữ liệu lớn.
- Một số danh sách (ví dụ cấp phát) mới giới hạn 100 bản ghi gần nhất, chưa phân trang đầy đủ.
- Chưa đóng gói Docker.
- Thanh toán MoMo mới ở mức sandbox.

**Hướng phát triển**
- Đóng gói Docker và triển khai lên đám mây.
- Lưu ảnh trên dịch vụ lưu trữ đám mây (S3, Firebase Storage…).
- Dashboard thống kê nâng cao (khấu hao, chi phí bảo trì theo thời gian).
- Ứng dụng di động (React Native / Flutter) dùng chung REST API.
- Phân trang cho mọi danh sách; xác thực hai yếu tố (2FA).
- Tích hợp hệ thống ERP/kế toán.

## Thông tin nhóm thực hiện

- **Nhóm:** XI
- **Giảng viên hướng dẫn:** Thầy Nguyễn Minh Tuấn

| Thành viên | MSSV | Phụ trách chính |
| --- | --- | --- |
| Lê Hoàng Pháp | 2124110229 | Thiết kế CSDL (entity, repository), API quản lý tài sản / loại tài sản / cấp phát – thu hồi / bảo trì, bảo mật (JWT, Google OAuth2, phân quyền), báo cáo Chương 1–3 và phụ lục |
| Lê Thanh Dinh | 2124110222 | Dữ liệu mẫu và tối ưu truy vấn JPQL, API mua sắm / thanh lý / kiểm kê / MoMo / xuất PDF, giao diện frontend, kiểm thử và báo cáo Chương 4–6 |

---

*TP. Hồ Chí Minh, tháng 11 năm 2026.*
