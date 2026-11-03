import { useCallback, useEffect, useMemo, useState } from "react";
import { adminDownload, adminRequest } from "./adminApi";

const API_URL = "http://localhost:8081/api/thuong-mai";
const reasons = [
  ["HONG", "Hỏng"],
  ["HONG_KHONG_SUA_DUOC", "Hỏng không sửa được"],
  ["LOI_THOI", "Lỗi thời"],
  ["MAT", "Mất"],
  ["HET_NIEN_HAN", "Hết niên hạn"],
  ["NHUONG_LAI", "Nhượng lại"],
  ["KHAC", "Khác"],
];
const currency = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 });
const today = () => {
  const date = new Date();
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
};
const dateText = (value) => value
  ? new Date(`${String(value).slice(0, 10)}T00:00:00`).toLocaleDateString("vi-VN")
  : "—";
const moneyText = (value) => `${currency.format(Number(value) || 0)} ₫`;
const reasonText = (value) => reasons.find(([key]) => key === value)?.[1] || value || "—";
const statusText = (value) => ({
  CHO_DUYET_THANH_LY: "Chờ duyệt",
  DA_THANH_LY: "Đã thanh lý",
  TU_CHOI_THANH_LY: "Từ chối",
})[value] || value || "—";

function emptyLine() {
  return { taiSanId: "", donGia: "" };
}

export default function DisposalPage({ currentUser }) {
  const [assets, setAssets] = useState([]);
  const [orders, setOrders] = useState([]);
  const [lines, setLines] = useState([emptyLine()]);
  const [reason, setReason] = useState("");
  const [disposalDate, setDisposalDate] = useState(today());
  const [notes, setNotes] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [reasonFilter, setReasonFilter] = useState("");
  const [search, setSearch] = useState("");
  const [rejectingId, setRejectingId] = useState(null);
  const [rejectNote, setRejectNote] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [assetRows, orderRows] = await Promise.all([
        adminRequest(`${API_URL}/tai-san-ban`),
        adminRequest(`${API_URL}/don-hang`),
      ]);
      setAssets(assetRows);
      setOrders(orderRows.filter((order) => order.loaiDon === "BAN"));
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const selectedIds = useMemo(
    () => new Set(lines.map((line) => String(line.taiSanId)).filter(Boolean)),
    [lines],
  );
  const totalSaleValue = useMemo(
    () => lines.reduce((total, line) => total + (Number(line.donGia) || 0), 0),
    [lines],
  );
  const totalBookValue = useMemo(() => lines.reduce((total, line) => {
    const asset = assets.find((row) => String(row.taiSanId) === String(line.taiSanId));
    return total + (Number(asset?.giaTriConLai) || 0);
  }, 0), [assets, lines]);

  const filteredOrders = useMemo(() => {
    const needle = search.trim().toLocaleLowerCase("vi");
    return orders.filter((order) => {
      const date = order.ngayThanhLy || order.taoLuc;
      const day = date ? String(date).slice(0, 10) : "";
      if (fromDate && (!day || day < fromDate)) return false;
      if (toDate && (!day || day > toDate)) return false;
      if (reasonFilter && order.lyDoThanhLy !== reasonFilter) return false;
      if (!needle) return true;
      const text = [
        order.maDonHang,
        order.nguoiYeuCau,
        order.nguoiDuyet,
        order.lyDoThanhLy,
        order.ghiChuThanhLy,
        order.ghiChuDuyetThanhLy,
        ...order.chiTiet.flatMap((item) => [item.maTaiSan, item.tenSanPham]),
      ].filter(Boolean).join(" ").toLocaleLowerCase("vi");
      return text.includes(needle);
    });
  }, [fromDate, orders, reasonFilter, search, toDate]);

  function changeLine(index, field, value) {
    setLines((current) => current.map((line, lineIndex) => {
      if (lineIndex !== index) return line;
      if (field !== "taiSanId") return { ...line, [field]: value };
      const asset = assets.find((row) => String(row.taiSanId) === value);
      return { ...line, taiSanId: value, donGia: asset ? String(asset.giaTriConLai ?? 0) : "" };
    }));
  }

  function addLine() {
    if (lines.length >= 30) {
      setError("Mỗi phiếu thanh lý tối đa 30 tài sản.");
      return;
    }
    setLines((current) => [...current, emptyLine()]);
  }

  async function createDisposal(event) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (!reason) {
      setError("Vui lòng chọn lý do thanh lý.");
      return;
    }
    if (reason === "KHAC" && !notes.trim()) {
      setError("Vui lòng mô tả lý do khác khi thanh lý.");
      return;
    }
    if (lines.some((line) => !line.taiSanId || line.donGia === "" || Number(line.donGia) < 0)) {
      setError("Chọn tài sản và nhập giá thanh lý hợp lệ cho từng dòng.");
      return;
    }
    setSaving(true);
    try {
      const result = await adminRequest(`${API_URL}/don-hang`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          loaiDon: "BAN",
          phuongThucThanhToan: "TIEN_MAT",
          lyDoThanhLy: reason,
          ngayThanhLy: disposalDate,
          ghiChuThanhLy: notes.trim() || null,
          chiTiet: lines.map((line) => ({
            taiSanId: Number(line.taiSanId),
            soLuong: 1,
            donGia: Number(line.donGia),
          })),
        }),
      });
      setMessage(`Đã tạo phiếu ${result.maDonHang}; tài sản chỉ được cập nhật sau khi được duyệt.`);
      setLines([emptyLine()]);
      setReason("");
      setNotes("");
      setDisposalDate(today());
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function approve(order) {
    if (!window.confirm(`Duyệt thanh lý phiếu ${order.maDonHang}? Thao tác sẽ đổi trạng thái tài sản và dừng khấu hao.`)) return;
    setActionId(order.id);
    setError("");
    setMessage("");
    try {
      await adminRequest(`${API_URL}/don-hang/${order.id}/duyet-thanh-ly`, { method: "POST" });
      setMessage(`Đã duyệt phiếu ${order.maDonHang} và tạo biên bản PDF.`);
      await loadData();
    } catch (actionError) {
      setError(actionError.message);
    } finally {
      setActionId(null);
    }
  }

  async function reject(order) {
    if (!rejectNote.trim()) {
      setError("Vui lòng nhập lý do từ chối.");
      return;
    }
    setActionId(order.id);
    setError("");
    setMessage("");
    try {
      await adminRequest(`${API_URL}/don-hang/${order.id}/tu-choi-thanh-ly`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ghiChu: rejectNote.trim() }),
      });
      setMessage(`Đã từ chối phiếu ${order.maDonHang}.`);
      setRejectingId(null);
      setRejectNote("");
      await loadData();
    } catch (actionError) {
      setError(actionError.message);
    } finally {
      setActionId(null);
    }
  }

  async function downloadRecord(order) {
    setError("");
    try {
      await adminDownload(`${API_URL}/don-hang/${order.id}/bien-ban-thanh-ly`,
        `${order.maDonHang}-bien-ban-thanh-ly.pdf`);
    } catch (downloadError) {
      setError(downloadError.message);
    }
  }

  return (
    <div className="admin-resource-page">
      <section className="panel form-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">VÒNG ĐỜI TÀI SẢN</p>
            <h2>Thanh lý tài sản</h2>
            <p className="muted">Lập phiếu, xét duyệt và lưu biên bản thanh lý. Người lập: {currentUser.username}.</p>
          </div>
          <button className="button secondary" type="button" onClick={loadData} disabled={loading}>Tải lại</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {message && <p className="form-success" role="status">{message}</p>}
        <form onSubmit={createDisposal}>
          <div className="form-grid">
            <label>Lý do thanh lý
              <select value={reason} onChange={(event) => setReason(event.target.value)} required>
                <option value="">Chọn lý do</option>
                {reasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <label>Ngày thanh lý dự kiến
              <input type="date" value={disposalDate} max={today()} onChange={(event) => setDisposalDate(event.target.value)} required />
            </label>
            <label className="full-width">Chi tiết / lý do đề xuất
              <textarea value={notes} onChange={(event) => setNotes(event.target.value)} maxLength={1000} rows={2} placeholder="Mô tả tình trạng, biên bản kiểm tra hoặc lý do khác" />
            </label>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>TÀI SẢN</th><th>NGƯỜI GIỮ / VỊ TRÍ</th><th>GIÁ TRỊ CÒN LẠI</th><th>GIÁ THANH LÝ (VNĐ)</th><th></th></tr></thead>
              <tbody>
                {lines.map((line, index) => {
                  const asset = assets.find((row) => String(row.taiSanId) === String(line.taiSanId));
                  return <tr key={index}>
                    <td><select aria-label={`Tài sản thanh lý ${index + 1}`} value={line.taiSanId}
                      onChange={(event) => changeLine(index, "taiSanId", event.target.value)} required>
                      <option value="">{loading ? "Đang tải tài sản..." : "Chọn tài sản"}</option>
                      {assets.filter((row) => !selectedIds.has(String(row.taiSanId)) || String(row.taiSanId) === String(line.taiSanId))
                        .map((row) => <option key={row.taiSanId} value={row.taiSanId} disabled={!row.coTheThanhLy}>
                          {row.maTaiSan} · {row.tenTaiSan}{row.nguoiDangGiu ? ` · ${row.nguoiDangGiu}` : ""} ({row.coTheThanhLy ? row.tinhTrang : row.lyDoKhongTheThanhLy})
                        </option>)}
                    </select>{asset && <small className="muted">{asset.tenLoaiTaiSan || "Chưa phân loại"}</small>}</td>
                    <td>{asset?.nguoiDangGiu || "Không có người giữ"}<br /><span className="muted">{[asset?.phongBanDangGiu, asset?.toaNha, asset?.tang, asset?.phong].filter(Boolean).join(" · ") || "—"}</span></td>
                    <td>{asset ? moneyText(asset.giaTriConLai) : "—"}</td>
                    <td><input aria-label={`Giá thanh lý ${index + 1}`} type="number" min="0" step="0.01"
                      value={line.donGia} onChange={(event) => changeLine(index, "donGia", event.target.value)} required /></td>
                    <td>{lines.length > 1 && <button className="delete" type="button" onClick={() => setLines((current) => current.filter((_, row) => row !== index))}>Xóa</button>}</td>
                  </tr>;
                })}
              </tbody>
            </table>
          </div>
          <div className="commerce-actions">
            <button className="button secondary" type="button" onClick={addLine}>＋ Thêm tài sản</button>
            <strong>Giá trị còn lại: {moneyText(totalBookValue)}<br /><small>Tổng giá thanh lý: {moneyText(totalSaleValue)}</small></strong>
            <button className="button" type="submit" disabled={saving || loading || assets.length === 0}>{saving ? "Đang tạo..." : "Tạo phiếu chờ duyệt"}</button>
          </div>
        </form>
      </section>

      <section className="panel admin-disposal-history">
        <div className="panel-heading">
          <div><h2>Lịch sử và duyệt thanh lý</h2><p className="muted">Phiếu đã duyệt hoặc từ chối chỉ xem; phiếu chờ duyệt có thể xử lý tại đây.</p></div>
          <span className="status">{orders.filter((order) => order.trangThai === "CHO_DUYET_THANH_LY").length} chờ duyệt</span>
        </div>
        <div className="table-filters">
          <label>Từ ngày<input type="date" value={fromDate} max={toDate || undefined} onChange={(event) => setFromDate(event.target.value)} /></label>
          <label>Đến ngày<input type="date" value={toDate} min={fromDate || undefined} onChange={(event) => setToDate(event.target.value)} /></label>
          <label>Lý do<select value={reasonFilter} onChange={(event) => setReasonFilter(event.target.value)}>
            <option value="">Tất cả lý do</option>{reasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          <label>Tìm kiếm<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Mã phiếu, tài sản, người đề xuất" /></label>
          <button className="text-button" type="button" onClick={() => { setFromDate(""); setToDate(""); setReasonFilter(""); setSearch(""); }}>Xóa bộ lọc</button>
        </div>
        <div className="table-wrap" aria-busy={loading}>
          <table>
            <thead><tr><th>MÃ / NGÀY</th><th>TÀI SẢN / NGƯỜI GIỮ</th><th>LÝ DO / GIÁ TRỊ CÒN LẠI</th><th>GIÁ THANH LÝ</th><th>NGƯỜI ĐỀ XUẤT / DUYỆT</th><th>TRẠNG THÁI / GHI CHÚ</th><th>THAO TÁC</th></tr></thead>
            <tbody>
              {loading ? <tr><td className="empty" colSpan="7">Đang tải dữ liệu thanh lý...</td></tr>
                : error && orders.length === 0 ? <tr><td className="empty" colSpan="7">{error}</td></tr>
                  : filteredOrders.length === 0 ? <tr><td className="empty" colSpan="7">Không có phiếu thanh lý phù hợp.</td></tr>
                    : filteredOrders.map((order) => <tr key={order.id}>
                      <td><strong>{order.maDonHang}</strong><br /><span className="muted">{dateText(order.ngayThanhLy || order.taoLuc)}</span></td>
                      <td>{order.chiTiet.map((item) => <div key={item.id || item.taiSanId}>
                        <strong>{item.maTaiSan} · {item.tenSanPham}</strong><br />
                        <span className="muted">{[item.toaNha, item.tang, item.phong].filter(Boolean).join(" · ") || "Không có vị trí"}</span>
                      </div>)}</td>
                      <td>{reasonText(order.lyDoThanhLy)}<br />{moneyText(order.chiTiet.reduce((sum, item) => sum + (Number(item.giaTriConLai) || 0), 0))}</td>
                      <td>{moneyText(order.tongTien)}</td>
                      <td>{order.nguoiYeuCau || "—"}<br /><span className="muted">{order.nguoiDuyet
                        ? `${order.trangThai === "TU_CHOI_THANH_LY" ? "Từ chối" : "Duyệt"}: ${order.nguoiDuyet}`
                        : "Chưa xử lý"}</span></td>
                      <td><span className={`status${order.trangThai === "DA_THANH_LY" ? " retired" : order.trangThai === "TU_CHOI_THANH_LY" ? " rejected" : ""}`}>{statusText(order.trangThai)}</span>
                        {order.ghiChuThanhLy && <><br /><span className="muted">{order.ghiChuThanhLy}</span></>}
                        {order.ghiChuDuyetThanhLy && <><br /><strong>Ghi chú duyệt:</strong> {order.ghiChuDuyetThanhLy}</>}
                      </td>
                      <td className="actions">
                        {order.trangThai === "CHO_DUYET_THANH_LY" ? <>
                          <button className="button" type="button" disabled={actionId === order.id} onClick={() => approve(order)}>Duyệt</button>
                          {rejectingId === order.id ? <div className="disposal-reject-form">
                            <input aria-label={`Lý do từ chối ${order.maDonHang}`} value={rejectNote} maxLength="1000"
                              onChange={(event) => setRejectNote(event.target.value)} placeholder="Lý do từ chối" />
                            <button className="delete" type="button" disabled={actionId === order.id} onClick={() => reject(order)}>Xác nhận</button>
                            <button className="text-button" type="button" onClick={() => { setRejectingId(null); setRejectNote(""); }}>Hủy</button>
                          </div> : <button className="delete" type="button" disabled={actionId === order.id}
                            onClick={() => { setRejectingId(order.id); setRejectNote(""); }}>Từ chối</button>}
                        </> : order.trangThai === "DA_THANH_LY" && order.coBienBanThanhLy
                          ? <button className="text-button" type="button" onClick={() => downloadRecord(order)}>Tải biên bản PDF</button>
                          : "—"}
                      </td>
                    </tr>)}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
