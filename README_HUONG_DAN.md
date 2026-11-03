# QUẢN LÝ TÀI SẢN VÀ THIẾT BỊ VĂN PHÒNG

## 1. Công nghệ
- Java 17+
- Backend: Spring Boot 3 REST API, Spring Data JPA, MySQL
- Frontend: React, Vite, HTML và CSS

## 2. Cách chạy
1. Cài JDK 17 trở lên, Maven, MySQL và Node.js.
2. Mở MySQL trong XAMPP (nếu dùng XAMPP). Ứng dụng sẽ tự tạo database `quan_ly_tai_san` khi khởi động nếu database chưa tồn tại; tài khoản MySQL trong cấu hình cần có quyền tạo database. Có thể chạy `database.sql` trước để tạo database thủ công.
3. Chỉnh username/password MySQL trong `backend/src/main/resources/application.properties` nếu cần.
4. Mở terminal tại thư mục `backend` và chạy `mvn spring-boot:run`.
5. Mở terminal khác tại thư mục `frontend`, chạy `npm install` một lần, sau đó chạy `npm run dev`.
6. Mở giao diện người dùng tại http://127.0.0.1:5500/; trang quản trị tại http://127.0.0.1:5500/admin. Backend API chạy tại http://localhost:8081/api/tai-san.

Nếu vừa cài Maven mà terminal chưa nhận lệnh `mvn`, hãy mở terminal mới.
Frontend dùng React và Vite; sau khi cài dependencies, có thể chạy `npm run build` để tạo bản production trong `frontend/dist`.
Nếu cổng `5500` đang được tiến trình frontend cũ sử dụng, hãy dừng tiến trình đó trước khi chạy Vite; cổng này cần khớp với cấu hình CORS của backend.
Sau khi cập nhật mã nguồn, hãy khởi động lại backend đang chạy để áp dụng cấu hình phân quyền mới. Nếu tài khoản USER vẫn gặp HTTP 403 khi tải thông báo, hãy dừng backend cũ, chạy lại `mvn spring-boot:run` trong thư mục `backend`, rồi đăng nhập lại; các thông báo chỉ yêu cầu đăng nhập và được giới hạn theo từng tài khoản.
Sau khi cập nhật backend để thêm API gia hạn phiên, hãy khởi động lại backend. Đăng nhập lại một lần để nhận refresh token mới; các lần sau phiên được gia hạn tự động, kể cả khi access token đã hết hạn.
Đường dẫn nghiệp vụ dùng URL hash để hỗ trợ mở trực tiếp và Back/Forward: `/#overview`, `/#assets`, `/#categories`, `/#assignments`, `/#returns`, `/#maintenance`, `/#repairs`, `/#purchase`, `/#sale`, `/#status-overview`. Liên kết cũ `/#commerce` vẫn mở màn hình Mua sắm. Khu vực Admin (`/admin`) có dashboard và các màn quản lý tài sản (`/admin/assets`), danh mục (`/admin/catalog`), nhân viên (`/admin/employees`), cấp phát/thu hồi (`/admin/assignments`), bảo trì/sửa chữa (`/admin/maintenance`), hóa đơn/khấu hao (`/admin/invoices`), vật tư (`/admin/consumables`), kiểm kê (`/admin/stocktakes`), báo cáo (`/admin/reports`), người dùng (`/admin/users`) và nhật ký (`/admin/activity`). Menu Admin có liên kết Mua sắm (`/#purchase`) và giữ riêng bước Nhập kho trong quy trình Công cụ dụng cụ. Chỉ tài khoản `ADMIN` truy cập được khu vực quản trị; một số quy trình vòng đời dùng lại màn hình hiện có.

## 3. Chức năng
- Bảo mật: đăng ký tài khoản USER, đăng nhập/đăng xuất bằng JWT; access token 8 giờ được tự gia hạn bằng refresh token tối đa 30 ngày, kể cả khi access token hết hạn; mật khẩu được băm BCrypt; các API quản lý yêu cầu token hợp lệ.
- Đăng nhập Google: cấu hình `GOOGLE_OAUTH_ENABLED=true`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` và `FRONTEND_URL` trong môi trường backend. Google OAuth redirect URI phải là `http://localhost:8081/login/oauth2/code/google` khi chạy local. Người dùng Google mới được tạo tài khoản USER riêng; mã callback chỉ dùng một lần, hết hạn sau 2 phút. Không tự liên kết với tài khoản hiện có chỉ dựa trên email.
- Phân quyền: người dùng đã đăng nhập có thể sử dụng các nghiệp vụ; chỉ `ADMIN` mới truy cập khu vực quản trị và API tạo, sửa, vô hiệu hóa hoặc xóa người dùng, cũng như xem nhật ký. Không thể xóa/vô hiệu hóa quản trị viên cuối cùng hoặc tự khóa tài khoản quản trị đang đăng nhập.
- Chống brute-force: khóa đăng nhập tài khoản 15 phút sau 5 lần nhập sai liên tiếp; đăng xuất thu hồi token ngay trong CSDL.
- Hai tài khoản kiểm thử (chỉ dùng cho môi trường demo): `admin` / `Admin@12345` và `user` / `User@12345`.
- Dashboard Admin: tổng tài sản, số lượng đang sử dụng/sửa chữa/đã thanh lý, tổng giá trị và giá trị còn lại; biểu đồ theo trạng thái, loại, phòng ban; cảnh báo bảo hành, bảo trì, thu hồi quá hạn và vật tư sắp hết.
- Quản lý tài sản: thêm, xem chi tiết, sửa và xóa; lưu mã, tên, loại, serial, hãng, model, ngày/giá mua, nhà cung cấp, hạn bảo hành, vị trí chọn từ danh mục, ảnh tham chiếu và ghi chú. Chi tiết có lịch sử cấp phát/thu hồi, bảo trì và bảng khấu hao theo năm.
- Quản lý loại tài sản: thêm, sửa, xóa; lưu thời gian khấu hao mặc định 1–50 năm (mặc định 5); không cho xóa loại đang được gán cho tài sản.
- Menu chính hiển thị riêng Cấp phát, Thu hồi, Bảo trì, Sửa chữa, Mua sắm và Thanh lý; mục Danh mục bao gồm loại tài sản cùng các danh mục phòng ban, vị trí và nhà cung cấp. Màn Thu hồi chỉ hiển thị phiếu đang mở, màn Sửa chữa dùng để theo dõi và cập nhật tiến độ yêu cầu.
- Mua sắm: gửi yêu cầu kèm nhà cung cấp, ngày dự kiến giao, loại tài sản và vị trí nhận lấy từ danh mục; hệ thống lưu người yêu cầu và chờ quản trị viên duyệt riêng. Khi được duyệt, đơn chuyển sang Đã đặt. Quản trị viên đối chiếu số lượng thực nhận trước khi nhập các tài sản thực nhận vào kho; số lượng thiếu được lưu trên đơn. Nếu quản trị viên tự yêu cầu và duyệt, cùng tài khoản được ghi nhận ở cả hai vai trò. Thanh toán trực tuyến chỉ là mô phỏng sandbox.
- Màn Mua sắm Admin có các tab Yêu cầu, Đơn mua và Nhận hàng. Đơn mua hiển thị mã đơn, nhà cung cấp, ngày giao dự kiến, tiến độ nhận, tổng tiền và trạng thái; yêu cầu và đơn liên kết qua lại. Form tạo đơn giữ dữ liệu khi cần đăng nhập lại, và gửi mã yêu cầu duy nhất để thao tác lặp không tạo đơn trùng.
- Thanh lý: chọn lý do hỏng/lỗi thời/mất/nhượng lại và phương thức nhận tiền chuyển khoản/nộp quỹ; giá trị còn lại được tính theo khấu hao đường thẳng tại thời điểm duyệt và điền sẵn làm giá thanh lý dự kiến. Người tạo gửi phiếu chờ duyệt, chỉ ADMIN mới phê duyệt và chuyển tài sản sang trạng thái Thanh lý. Lịch sử ghi nhận người yêu cầu/duyệt, giá trị còn lại, giá thanh lý và lãi/lỗ; phiếu đã duyệt có thể in/lưu thành PDF.
- Cấp phát và bàn giao: lập phiếu ghi người/phòng nhận, tình trạng khi giao, ngày dự kiến thu hồi và ghi chú; tài sản tự chuyển sang đang sử dụng. Có thể in biên bản bàn giao và lưu thành PDF từ hộp thoại in. Thu hồi lưu tình trạng khi nhận và trạng thái sau thu hồi; lịch sử được giữ lại.
- Bảo trì và sửa chữa: ghi nhận yêu cầu cho tài sản trong kho hoặc đang cấp phát, theo dõi các bước tiếp nhận, đang xử lý, hoàn tất hoặc hủy. Khi kết thúc yêu cầu, tài sản trở về trạng thái trước bảo trì. Hồ sơ tài sản hiển thị chu kỳ 1/3/6/12 tháng (mặc định 12), lần bảo trì gần nhất, ngày đến hạn tiếp theo và tổng chi phí của các lần đã hoàn tất.
- Tài khoản và phân quyền: đăng ký công khai chỉ tạo USER; ADMIN quản lý tài khoản và vai trò tại `/admin/users`.
- Không gian nhân viên: có 5 mục Tổng quan, Tài sản của tôi, Báo hỏng/Bảo trì, Yêu cầu mua sắm và Thông tin cá nhân. Nhân viên chỉ xem được tài sản gắn với tài khoản nhận bàn giao; có thể gửi yêu cầu bảo trì kèm mô tả và liên kết ảnh, gửi đề xuất thiết bị và theo dõi duyệt, cập nhật email/số điện thoại và đổi mật khẩu. Không có thao tác sửa/xóa tài sản, cấp phát, thu hồi, thanh lý, duyệt hay xem nhật ký.
- Khi ADMIN lập phiếu cấp phát, điền tên đăng nhập OfficeCare của người nhận để gắn tài sản với trang cá nhân; phiếu đang hiệu lực cũ có thể gắn tài khoản từ danh sách cấp phát. Các API đọc tài sản, loại tài sản, lịch sử cấp phát/bảo trì và mua bán toàn hệ thống chỉ dành cho ADMIN; dữ liệu tài sản cá nhân được cung cấp qua `/api/me/**`.
- Yêu cầu mua sắm của nhân viên lưu tên thiết bị, loại, số lượng, lý do và trạng thái chờ duyệt/đã duyệt/từ chối. ADMIN xử lý tại `/admin/purchase-requests`; quyết định và thông tin duyệt được ghi nhật ký.
- Danh mục Admin: loại tài sản, phòng ban, tòa nhà/tầng/phòng và nhà cung cấp. Phòng ban nhân viên chọn từ danh mục; khi nhân viên chuyển sang nghỉ việc, hệ thống thông báo nếu còn tài sản đang giao và đưa cảnh báo kèm liên kết đến nghiệp vụ thu hồi trên dashboard.
- Vật tư tiêu hao: quản lý tồn kho và mức tối thiểu; tạo phiếu xuất theo phòng ban, tự trừ tồn và cảnh báo khi dưới ngưỡng.
- Hóa đơn và kiểm kê: lưu hóa đơn mua hàng, xem khấu hao đường thẳng theo năm; tạo đợt kiểm kê theo phòng ban, ghi nhận có mặt/thiếu/hỏng và xuất CSV chênh lệch.
- Báo cáo Admin: tổng hợp tài sản theo phòng ban, loại hoặc trạng thái; xuất CSV tương thích Excel hoặc in/lưu thành PDF. Quản lý người dùng, vai trò và nhật ký hoạt động chỉ dành cho ADMIN; nhật ký chỉ đọc.
- Tìm kiếm theo mã, tên hoặc loại; lọc theo tình trạng, loại, phòng ban đang giữ và khoảng ngày mua; danh sách có phân trang.
- Xuất danh sách tài sản ra CSV theo từ khóa và bộ lọc hiện tại; tệp bao gồm toàn bộ kết quả qua các trang, có thể mở bằng Excel.
- Ràng buộc dữ liệu: mã tài sản và tên loại không trùng, tên/mã bắt buộc, giá mua không âm, tình trạng hợp lệ.
- Thống kê tổng số, tình trạng và tổng giá trị trên toàn bộ dữ liệu, không chỉ trang đang xem.
- Khi CSDL trống, ứng dụng tự thêm danh mục và 5 bản ghi tài sản mẫu ở lần khởi động đầu tiên.
- Thanh toán/thương mại: đặt mua thiết bị theo danh mục hoặc bán tài sản đang trong kho; hỗ trợ tiền mặt, VietQR đối chiếu thủ công và MoMo sandbox. MoMo chỉ đánh dấu đã thanh toán sau khi backend xác minh chữ ký IPN và số tiền; cần cấu hình merchant cùng URL IPN HTTPS công khai. Đơn đã hoàn tất được lưu trong lịch sử.
- Tiện ích giao diện: chuyển sáng/tối, ẩn/hiện số liệu, xem cảnh báo gần đây lấy từ máy chủ và mở nhanh biểu mẫu giao dịch mua/bán.
- Đăng nhập và tài khoản: đăng nhập, đăng ký USER, đổi mật khẩu có xác minh mật khẩu hiện tại; ADMIN có thể vô hiệu hóa/kích hoạt tài khoản. Khóa đăng nhập sau nhiều lần sai được áp dụng tự động.
- Nhật ký và cảnh báo: lưu người thao tác, hành động, thời gian cùng dữ liệu trước/sau cho các thay đổi tài sản, danh mục, cấp phát, bảo trì, giao dịch và tài khoản; ADMIN xem 100 bản ghi gần nhất. Thông báo theo tài khoản lưu trong MySQL, hiển thị trên dashboard và có thể đánh dấu đã xem.
- Khấu hao: giá trị còn lại tính theo phương pháp đường thẳng, dựa trên giá mua, ngày mua và số năm khấu hao của loại tài sản; giá trị thấp nhất là 0 VNĐ. Hồ sơ thiếu giá/ngày mua chưa tính được giá trị còn lại.

## 4. API
- `POST /api/auth/login` với JSON `{"username":"admin","password":"Admin@12345"}` — đăng nhập và nhận JWT.
- `POST /api/auth/register` với JSON `{"username":"ten-dang-nhap","password":"mat-khau-tu-8-ky-tu"}` — tạo tài khoản USER và đăng nhập; không cho phép đăng ký vai trò ADMIN.
- `GET /api/auth/me` — thông tin tài khoản hiện tại; `POST /api/auth/refresh` — xoay vòng refresh token và cấp cặp access/refresh token mới; `POST /api/auth/logout` — thu hồi cả access token và refresh token hiện tại.
- `GET/PUT /api/auth/profile` — xem/cập nhật email và số điện thoại của tài khoản hiện tại.
- `PUT /api/auth/password` nhận `currentPassword`, `newPassword` (tối thiểu 8 ký tự) để đổi mật khẩu của tài khoản hiện tại.
- `GET /api/admin/users` — danh sách người dùng (chỉ `ADMIN`); `POST /api/admin/users` — tạo người dùng với `username`, `password`, `role`, `enabled`.
- `PUT /api/admin/users/{id}` — cập nhật tài khoản, đặt mật khẩu mới tùy chọn; `DELETE /api/admin/users/{id}` — xóa người dùng. Các thao tác đều chỉ dành cho `ADMIN`; phản hồi không chứa mật khẩu hoặc hash.
- Các API `/api/admin/nhan-vien`, `/phong-ban`, `/vi-tri`, `/nha-cung-cap`, `/vat-tu`, `/hoa-don`, `/kiem-ke` hỗ trợ các màn quản lý cùng tên; toàn bộ `/api/admin/**` chỉ dành cho ADMIN.
- Các API `/api/me/assets`, `/maintenance`, `/purchase-requests`, `/categories` chỉ dành cho USER, trả dữ liệu gắn với tài khoản hiện tại; `POST /api/me/maintenance` chỉ nhận yêu cầu cho tài sản đang được giao cho người gửi. `POST /api/me/purchase-requests` tạo đề xuất mua; ADMIN xem và duyệt/từ chối qua `/api/admin/purchase-requests`.
- `GET /api/admin/dashboard`, `/api/admin/reports?groupBy=phongBan|loaiTaiSan|trangThai`, `/api/admin/khau-hao?taiSanId={id}` — số liệu dashboard, báo cáo và bảng khấu hao.
- `GET/POST /api/admin/vat-tu/phieu-xuat` — xem/tạo phiếu xuất; tạo phiếu tự kiểm tra tồn kho và trừ số lượng.
- `GET/POST /api/admin/kiem-ke/{id}/items`, `PUT /api/admin/kiem-ke/{id}/items/{itemId}`, `GET /api/admin/kiem-ke/{id}/variance.csv` — quản lý kết quả kiểm kê và xuất chênh lệch.
- `GET /api/nhat-ky` — 100 thao tác gần nhất (chỉ ADMIN), bao gồm `actor`, `action`, `entityType`, `oldValue`, `newValue`, `createdAt`.
- `GET /api/thong-bao` — tối đa 50 thông báo của tài khoản hiện tại; `PUT /api/thong-bao/{id}/da-xem` hoặc `PUT /api/thong-bao/da-xem-tat-ca` để đánh dấu đã xem.
- Gửi token trong header `Authorization: Bearer <token>` khi gọi API quản lý. Các API nghiệp vụ yêu cầu đăng nhập; mọi API `/api/admin/**` chỉ dành cho tài khoản ADMIN.
- `GET /api/tai-san?keyword=&tinhTrang=&loaiId=&phongBan=&muaTuNgay=&muaDenNgay=&page=0&size=8` — tìm kiếm, lọc theo phòng ban đang giữ, khoảng ngày mua và phân trang.
- `GET /api/tai-san/{id}` — xem chi tiết; `GET /api/tai-san/thong-ke` — thống kê.
- `POST`, `PUT /{id}`, `DELETE /{id}` trên `/api/tai-san` — CRUD tài sản. Payload hỗ trợ `serialNumber`, `brand`, `model`, `supplierId`, `warrantyEndDate`, `imageReference`, `notes`, vị trí và chu kỳ bảo trì; response có nhà cung cấp, thông tin khấu hao, `giaTriConLai`, `baoTriGanNhat`, `baoTriTiepTheo`, `chiPhiBaoTriTichLuy`.
- `GET`, `POST /api/loai-tai-san`; `PUT`, `DELETE /{id}` — CRUD loại tài sản. Request có `thoiGianKhauHaoNam` từ 1 đến 50 (mặc định 5).
- `GET /api/thuong-mai/tham-chieu` — nhà cung cấp và vị trí từ danh mục để chọn khi tạo yêu cầu mua sắm; `GET /api/thuong-mai/tai-san-ban` — danh sách tài sản trong kho chưa bị giữ bởi phiếu thanh lý chờ duyệt, kèm giá trị còn lại theo khấu hao.
- `GET`, `POST /api/thuong-mai/don-hang` — xem tối đa 100 đơn gần nhất và tạo đơn mua (`loaiDon: "MUA"`) hoặc thanh lý (`"BAN"`). Đơn mua yêu cầu `nhaCungCapId`, `ngayDuKienGiao`, loại tài sản và `viTriId` cho từng dòng; hệ thống ghi người yêu cầu theo tài khoản đăng nhập và chuyển đơn sang `CHO_DUYET`.
- `POST /api/thuong-mai/don-hang/{id}/duyet` — chỉ ADMIN; ghi người duyệt và chuyển đơn sang `DA_DAT`. `POST /api/thuong-mai/don-hang/{id}/nhan-hang` — chỉ ADMIN; nhận danh sách `chiTietId`/`soLuongThucNhan`, lưu số thực nhận và chỉ tạo tài sản trong kho theo số lượng đã nhận.
- Phiếu thanh lý dùng mã `TL-xxxxxxx`, yêu cầu `lyDoThanhLy` (`HONG`, `LOI_THOI`, `MAT`, `NHUONG_LAI`) và `phuongThucNhanTien` (`CHUYEN_KHOAN`, `NOP_QUY`); trạng thái khởi tạo `CHO_DUYET_THANH_LY`. Giá trị còn lại từng tài sản được chụp lại lúc duyệt; `POST /api/thuong-mai/don-hang/{id}/duyet-thanh-ly` chỉ ADMIN mới gọi được, ghi người duyệt và chuyển tài sản sang trạng thái Thanh lý.
- `POST /api/payments/momo/don-hang/{id}/checkout` — ADMIN tạo hoặc tiếp tục phiên thanh toán MoMo; backend trả `payUrl` để chuyển người dùng sang MoMo. `POST /api/payments/momo/ipn` nhận thông báo từ MoMo, xác minh HMAC-SHA256 và chỉ ghi nhận giao dịch thành công khi đơn hàng, mã yêu cầu và số tiền khớp.
- `POST /api/thuong-mai/don-hang/{id}/thanh-toan-mo-phong` — mô phỏng thanh toán thử nghiệm cũ, không kết nối cổng thật; `POST /api/thuong-mai/don-hang/{id}/huy` — hủy đơn chưa nhận hàng hoặc phiếu thanh lý chưa duyệt.
- `GET /api/cap-phat?activeOnly=false` — ADMIN xem tối đa 100 lịch sử cấp phát; `activeOnly=true` chỉ trả các tài sản chưa thu hồi. `POST /api/cap-phat` nhận `taiSanId`, `nguoiNhan` (tên người hoặc phòng nhận), tùy chọn `usernameNguoiNhan`, `phongBan`, `ghiChu`, `tinhTrangLucBanGiao` và tùy chọn `ngayDuKienThuHoi`; `POST /api/cap-phat/{id}/thu-hoi` nhận `tinhTrangLucThuHoi`, `tinhTrangSauThuHoi` (`Trong kho`, `Bảo trì`, `Thanh lý`) và `ghiChu`. ADMIN có thể gắn tài khoản cho phiếu đang hiệu lực bằng `PUT /api/cap-phat/{id}/tai-khoan-nguoi-nhan`. Ngày cấp/thu hồi do máy chủ ghi nhận. Một tài sản không thể có đồng thời nhiều phiếu cấp phát chưa thu hồi; thu hồi đóng phiếu hiện hành và không xóa lịch sử.
- `GET /api/bao-tri?activeOnly=false` — tối đa 100 yêu cầu; `activeOnly=true` chỉ trả các yêu cầu đang mở. `POST /api/bao-tri` nhận `taiSanId`, `tieuDe`, `moTa`, `chiPhi`; `PUT /api/bao-tri/{id}` nhận `trangThai` (`DANG_XU_LY`, `HOAN_TAT`, `DA_HUY`) và `chiPhi`. Chỉ yêu cầu hoàn tất mới cộng vào chi phí bảo trì tích lũy và cập nhật ngày bảo trì gần nhất; ngày tiếp theo được tính bằng chu kỳ của tài sản.

## 5. Lưu ý
- Backend và frontend là hai phần độc lập, giao tiếp qua REST API.
- `spring.jpa.hibernate.ddl-auto=update` tự cập nhật bảng theo entity. File `database.sql` tạo database; dữ liệu mẫu được ứng dụng tự khởi tạo, không cần tạo bảng thủ công.
- Frontend React/Vite chạy cổng `5500`; backend chạy cổng `8081` để tránh xung đột với dịch vụ khác trên cổng `8080`. Nếu đổi cổng, cập nhật địa chỉ API trong `frontend/src/App.jsx` và cổng backend trong `backend/src/main/resources/application.properties`; giữ origin frontend trong cấu hình CORS.
- Khi không cấu hình `JWT_SECRET`, ứng dụng tạo khóa ngẫu nhiên chỉ dùng trong lần chạy hiện tại; token và phiên đăng nhập sẽ hết hiệu lực sau khi backend khởi động lại. Khi triển khai thật, bắt buộc cấu hình `JWT_SECRET` ổn định, ngẫu nhiên (Base64, tối thiểu 256 bit), đặt `SEED_DEMO_USERS=false`, và đặt `DEMO_ADMIN_PASSWORD`/`DEMO_USER_PASSWORD` riêng. Đổi mật khẩu demo qua biến môi trường trước lần chạy đầu; tài khoản đã được tạo không tự đổi mật khẩu khi thay biến.
- Có thể cấu hình `LOGIN_MAX_FAILED_ATTEMPTS`, `LOGIN_LOCK_MINUTES`, `JWT_EXPIRATION_MINUTES` và `JWT_REFRESH_EXPIRATION_DAYS` qua biến môi trường. Mặc định access token có hiệu lực 480 phút (8 giờ), refresh token 30 ngày. Token bị thu hồi được lưu trong bảng `revoked_tokens`.
- MoMo mặc định bị tắt. Để dùng sandbox, cấu hình `MOMO_ENABLED=true`, `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` và `MOMO_IPN_URL` (HTTPS công khai); tùy chọn `MOMO_ENDPOINT` mặc định là endpoint sandbox, còn `MOMO_REDIRECT_URL` mặc định quay về giao diện local. Lấy credential từ MoMo Developer Portal, không lưu khóa vào source control. Chỉ chuyển sang endpoint production sau khi được MoMo cấp quyền và cấu hình URL production.
