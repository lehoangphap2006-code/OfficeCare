import { useCallback, useEffect, useRef, useState } from "react";
import { adminRequest } from "./adminApi";

const API_URL = "http://localhost:8081/api/thuong-mai";
const dateInputValue = () => {
  const date = new Date();
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
};
const dateText = (value) => value ? new Date(`${value.slice(0, 10)}T00:00:00`).toLocaleDateString("vi-VN") : "—";
const dateTimeText = (value) => value ? new Date(value).toLocaleString("vi-VN") : "—";

function openForReceipt(order) {
  return order.loaiDon === "MUA"
    && ["DA_DAT", "NHAN_MOT_PHAN", "HOAN_TAT"].includes(order.trangThai)
    && order.chiTiet.some((item) => item.soLuongDaNhan < item.soLuong);
}

export default function StockInPage({
  currentUser,
  embedded = false,
  requestedOrderId = null,
  onRequestedOrderHandled = () => {},
}) {
  const [orders, setOrders] = useState([]);
  const [receipts, setReceipts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [locations, setLocations] = useState([]);
  const [activeOrder, setActiveOrder] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [receiptDate, setReceiptDate] = useState(dateInputValue());
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [dataLoaded, setDataLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [referenceError, setReferenceError] = useState("");
  const [message, setMessage] = useState("");
  const handledRequestedOrderId = useRef(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [orderRows, receiptRows] = await Promise.all([
        adminRequest(`${API_URL}/don-hang`),
        adminRequest(`${API_URL}/nhap-kho`),
      ]);
      setOrders(orderRows);
      setReceipts(receiptRows);
      setDataLoaded(true);
    } catch (loadError) {
      setDataLoaded(false);
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  useEffect(() => {
    if (requestedOrderId == null) {
      handledRequestedOrderId.current = null;
      return;
    }
    if (loading || !dataLoaded || handledRequestedOrderId.current === requestedOrderId) return;
    handledRequestedOrderId.current = requestedOrderId;
    const order = orders.find((row) => row.id === requestedOrderId);
    if (order && openForReceipt(order)) {
      setError("");
      beginReceipt(order);
    } else {
      setError(order
        ? "Đơn mua này không còn số lượng chờ nhập kho."
        : "Không tìm thấy đơn mua vừa tạo để nhập kho. Hãy tải lại danh sách đơn.");
    }
    onRequestedOrderHandled();
  }, [requestedOrderId, orders, loading, dataLoaded, onRequestedOrderHandled]);

  useEffect(() => {
    let active = true;
    Promise.all([
      adminRequest("http://localhost:8081/api/loai-tai-san"),
      adminRequest("http://localhost:8081/api/admin/vi-tri"),
    ]).then(([categoryRows, locationRows]) => {
      if (active) {
        setCategories(categoryRows);
        setLocations(locationRows);
      }
    }).catch((loadError) => {
      if (active) setReferenceError(loadError.message);
    });
    return () => { active = false; };
  }, []);

  function beginReceipt(order) {
    setActiveOrder(order);
    setReceiptDate(dateInputValue());
    setNotes("");
    setMessage("");
    setDrafts(Object.fromEntries(order.chiTiet.map((item) => [
      item.id,
      { received: String(item.soLuong - item.soLuongDaNhan), damaged: "0", assetDetails: [], ghiChu: "" },
    ])));
  }

  function updateDraft(itemId, field, value) {
    setDrafts((current) => ({
      ...current,
      [itemId]: { ...current[itemId], [field]: value },
    }));
  }

  function updateAssetDetail(itemId, index, field, value) {
    setDrafts((current) => {
      const currentDetails = current[itemId]?.assetDetails ?? [];
      const assetDetails = [...currentDetails];
      assetDetails[index] = { ...(assetDetails[index] ?? {}), [field]: value };
      return { ...current, [itemId]: { ...current[itemId], assetDetails } };
    });
  }

  function defaultAssetDetail(item, index) {
    const saved = drafts[item.id]?.assetDetails?.[index] ?? {};
    const matchingLocation = locations.find((location) =>
      location.toaNha === item.toaNha && (location.tang ?? "") === (item.tang ?? "")
      && location.phong === item.phong);
    return {
      serialNumber: "",
      brand: "",
      model: "",
      warrantyEndDate: item.warrantyEndDate ?? "",
      locationId: matchingLocation ? String(matchingLocation.id) : "",
      ...saved,
    };
  }

  async function submitReceipt(event) {
    event.preventDefault();
    if (!activeOrder) return;
    const chiTiet = activeOrder.chiTiet.map((item) => {
      const draft = drafts[item.id] ?? {};
      return {
        chiTietId: item.id,
        soLuongThucNhan: Number(draft.received),
        soLuongHong: Number(draft.damaged),
        ghiChu: String(draft.ghiChu ?? "").trim() || null,
        assetDetails: item.loaiDong === "VAT_TU" ? [] : Array.from(
          { length: Number(draft.received) || 0 },
          (_, index) => {
            const details = defaultAssetDetail(item, index);
            return {
              serialNumber: String(details.serialNumber ?? "").trim() || null,
              brand: String(details.brand ?? "").trim() || null,
              model: String(details.model ?? "").trim() || null,
              warrantyEndDate: details.warrantyEndDate || null,
              locationId: details.locationId ? Number(details.locationId) : null,
            };
          },
        ),
      };
    });
    const invalid = chiTiet.some((line, index) => {
      const remaining = activeOrder.chiTiet[index].soLuong - activeOrder.chiTiet[index].soLuongDaNhan;
      const item = activeOrder.chiTiet[index];
      const category = categories.find((row) => row.loaiId === item.loaiTaiSanId);
      return !Number.isInteger(line.soLuongThucNhan) || line.soLuongThucNhan < 0
        || line.soLuongThucNhan > remaining || !Number.isInteger(line.soLuongHong)
        || line.soLuongHong < 0 || line.soLuongHong > line.soLuongThucNhan
        || (item.loaiDong !== "VAT_TU" && line.assetDetails.some((detail) =>
          !detail.locationId || (category?.requiresSerial && !detail.serialNumber)));
    });
    if (invalid || chiTiet.every((line) => line.soLuongThucNhan === 0)) {
      setError("Kiểm tra số lượng nhận/hỏng, vị trí của từng thiết bị và serial của loại tài sản yêu cầu serial.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const result = await adminRequest(`${API_URL}/don-hang/${activeOrder.id}/nhan-hang`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ngayNhap: receiptDate, ghiChu: notes.trim() || null, chiTiet }),
      });
      setActiveOrder(null);
      setMessage(`Đã nhập kho cho đơn ${result.maDonHang}; trạng thái đơn: ${result.trangThai === "DA_NHAN" ? "đã nhận đủ" : "nhận một phần"}.`);
      await loadData();
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      {!embedded && <section className="welcome">
        <div><p className="eyebrow">KHO &amp; THIẾT BỊ</p><h1>Nhập kho</h1>
          <p className="welcome-copy">Đối chiếu từng đợt giao với đơn mua, ghi nhận hàng hỏng/thiếu và tạo hồ sơ tài sản hoặc cập nhật tồn vật tư.</p></div>
      </section>}
      {error && <p className="message error" role="alert">{error}</p>}
      {referenceError && <p className="message error" role="alert">Không tải được danh mục loại tài sản/vị trí: {referenceError}</p>}
      {message && <p className="message" role="status">{message}</p>}

      {activeOrder && <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">PHIẾU NHẬP HÀNG</p>
          <h2>{activeOrder.maDonHang}</h2><p className="muted">{activeOrder.nhaCungCap || "Không rõ nhà cung cấp"} · Người nhập: {currentUser?.username || "—"}</p></div>
          <button className="button secondary" type="button" onClick={() => setActiveOrder(null)}>Đóng</button></div>
        <form onSubmit={submitReceipt}>
          <div className="form-grid">
            <label>Ngày nhập<input type="date" required max={dateInputValue()} value={receiptDate} onChange={(event) => setReceiptDate(event.target.value)} /></label>
            <label>Ghi chú đối chiếu<input maxLength="500" value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Hàng giao thiếu, kiện móp..." /></label>
          </div>
          <div className="table-wrap"><table>
            <thead><tr><th>SẢN PHẨM</th><th>SỐ ĐẶT</th><th>ĐÃ NHẬN</th><th>CÒN LẠI</th><th>THỰC NHẬN</th><th>HỎNG</th><th>GHI CHÚ DÒNG HÀNG</th></tr></thead>
            <tbody>{activeOrder.chiTiet.map((item) => {
              const remaining = item.soLuong - item.soLuongDaNhan;
              const consumable = item.loaiDong === "VAT_TU";
              const category = categories.find((row) => row.loaiId === item.loaiTaiSanId);
              return <tr key={item.id}>
                <td>{item.tenSanPham}<br /><span className="muted">
                  {consumable ? "Vật tư tiêu hao" : `${category?.tenLoai || "Tài sản / thiết bị"} · ${Number(item.donGia ?? 0).toLocaleString("vi-VN")} ₫/đơn vị`}
                  {!consumable && item.warrantyEndDate ? ` · Bảo hành đến ${dateText(item.warrantyEndDate)}` : ""}
                </span></td>
                <td>{item.soLuong}</td><td>{item.soLuongDaNhan}</td><td>{remaining}</td>
                <td><input aria-label={`Số lượng nhận ${item.tenSanPham}`} type="number" min="0" max={remaining} step="1"
                  value={drafts[item.id]?.received ?? ""} onChange={(event) => updateDraft(item.id, "received", event.target.value)} /></td>
                <td><input aria-label={`Số lượng hỏng ${item.tenSanPham}`} type="number" min="0"
                  max={drafts[item.id]?.received ?? 0} step="1" value={drafts[item.id]?.damaged ?? "0"}
                  onChange={(event) => updateDraft(item.id, "damaged", event.target.value)} /></td>
                <td><input aria-label={`Ghi chú đối chiếu ${item.tenSanPham}`} maxLength="500"
                  placeholder="Thiếu/hỏng, tình trạng kiện hàng..."
                  value={drafts[item.id]?.ghiChu ?? ""} onChange={(event) => updateDraft(item.id, "ghiChu", event.target.value)} />
                  {consumable && <span className="muted">Tồn kho chỉ tăng theo số lượng tốt nhận.</span>}</td>
              </tr>;
            })}</tbody>
          </table></div>
          {activeOrder.chiTiet.filter((item) => item.loaiDong !== "VAT_TU"
            && Number(drafts[item.id]?.received) > 0).map((item) => {
            const category = categories.find((row) => row.loaiId === item.loaiTaiSanId);
            const details = Array.from({ length: Number(drafts[item.id]?.received) || 0 },
              (_, index) => defaultAssetDetail(item, index));
            return <section className="panel" key={`assets-${item.id}`}>
              <div className="panel-heading"><div><h3>{item.tenSanPham}</h3>
                <p className="muted">Thông tin cho {details.length} tài sản tạo từ dòng hàng này · Giá mua: {Number(item.donGia ?? 0).toLocaleString("vi-VN")} ₫/đơn vị</p></div></div>
              <div className="form-grid">
                {details.map((detail, index) => <fieldset className="admin-resource-form" key={`${item.id}-${index}`}>
                  <legend>Thiết bị {index + 1}{category?.requiresSerial ? " · Bắt buộc serial" : ""}</legend>
                  <div className="form-grid">
                    <label>Serial {category?.requiresSerial && <span>*</span>}
                      <input maxLength="100" required={Boolean(category?.requiresSerial)} value={detail.serialNumber}
                        onChange={(event) => updateAssetDetail(item.id, index, "serialNumber", event.target.value)} />
                    </label>
                    <label>Hãng<input maxLength="100" value={detail.brand}
                      onChange={(event) => updateAssetDetail(item.id, index, "brand", event.target.value)} /></label>
                    <label>Model<input maxLength="100" value={detail.model}
                      onChange={(event) => updateAssetDetail(item.id, index, "model", event.target.value)} /></label>
                    <label>Hạn bảo hành<input type="date" min={receiptDate} value={detail.warrantyEndDate}
                      onChange={(event) => updateAssetDetail(item.id, index, "warrantyEndDate", event.target.value)} /></label>
                    <label>Vị trí kho <span>*</span>
                      <select required value={detail.locationId}
                        onChange={(event) => updateAssetDetail(item.id, index, "locationId", event.target.value)}>
                        <option value="">Chọn vị trí</option>
                        {locations.map((location) => <option key={location.id} value={location.id}>
                          {[location.toaNha, location.tang && `Tầng ${location.tang}`, `Phòng ${location.phong}`].filter(Boolean).join(" · ")}
                        </option>)}
                      </select>
                    </label>
                    <p className="muted">Mã tài sản tự sinh · Ngày mua/nhập: {dateText(receiptDate)} · Nhà cung cấp: {activeOrder.nhaCungCap || "—"}</p>
                  </div>
                </fieldset>)}
              </div>
            </section>;
          })}
          <div className="commerce-actions">
            <p className="muted">Số thiếu và ghi chú từng dòng được lưu trên phiếu bất biến và có thể tiếp tục nhận ở đợt sau. Hàng thiết bị hỏng được tạo hồ sơ ở trạng thái Bảo trì.</p>
            <button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu phiếu..." : "Lưu phiếu & nhập kho"}</button>
          </div>
        </form>
      </section>}

      <section className="panel assets-panel">
        <div className="panel-heading assets-heading"><div><h2>Đơn mua chờ nhập kho</h2>
          <p className="muted">Chọn đơn để lập phiếu. Chỉ khi lưu phiếu nhập, tồn kho và hồ sơ tài sản mới được cập nhật.</p></div>
          <button className="text-button" type="button" onClick={loadData} disabled={loading}>Làm mới</button></div>
        <div className="table-wrap" aria-busy={loading}><table>
          <thead><tr><th>ĐƠN MUA</th><th>NHÀ CUNG CẤP</th><th>NGÀY DỰ KIẾN</th><th>SẢN PHẨM</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead>
          <tbody>{loading ? <tr><td className="empty loading-state" colSpan="6">Đang tải đơn mua...</td></tr>
            : orders.filter(openForReceipt).length === 0 ? <tr><td className="empty" colSpan="6">Không có đơn mua còn số lượng chờ nhận. Đơn đã nhập đủ được xem trong Lịch sử phiếu nhập kho.</td></tr>
              : orders.filter(openForReceipt).map((order) => <tr key={order.id}>
                <td className="code">{order.maDonHang}</td><td>{order.nhaCungCap || "—"}</td><td>{dateText(order.ngayDuKienGiao)}</td>
                <td>{order.chiTiet.map((item) => `${item.tenSanPham} (còn ${item.soLuong - item.soLuongDaNhan}/${item.soLuong})`).join(", ")}</td>
                <td>{order.trangThai === "NHAN_MOT_PHAN" ? "Nhận một phần"
                  : order.trangThai === "HOAN_TAT" ? "Cần nhập kho (đơn cũ)" : "Đã đặt"}</td>
                <td><button className="link-button" type="button" onClick={() => beginReceipt(order)}>Lập phiếu nhập</button></td>
              </tr>)}
          </tbody>
        </table></div>
      </section>

      <section className="panel assets-panel">
        <div className="panel-heading assets-heading"><div><h2>Lịch sử phiếu nhập kho</h2><p className="muted">{receipts.length} phiếu gần nhất</p></div></div>
        <div className="table-wrap" aria-busy={loading}><table>
          <thead><tr><th>SỐ PHIẾU</th><th>NGÀY NHẬP</th><th>ĐƠN MUA</th><th>NGƯỜI NHẬP</th><th>ĐỐI CHIẾU</th><th>GHI CHÚ</th></tr></thead>
          <tbody>{loading ? <tr><td className="empty loading-state" colSpan="6">Đang tải phiếu nhập...</td></tr>
            : receipts.length === 0 ? <tr><td className="empty" colSpan="6">Chưa có phiếu nhập kho.</td></tr>
              : receipts.map((receipt) => <tr key={receipt.id}>
                <td className="code">{receipt.soPhieu}</td><td>{dateText(receipt.ngayNhap)}</td><td>{receipt.maDonMua}<br /><span className="muted">{receipt.nhaCungCap || "—"}</span></td>
                <td>{receipt.nguoiNhap}<br /><span className="muted">{dateTimeText(receipt.taoLuc)}</span></td>
                <td>{receipt.chiTiet.map((item) => `${item.tenSanPham}: đặt ${item.soLuongTheoDon}, đã nhận trước ${item.soLuongDaNhanTruoc}, lần này nhận ${item.soLuongThucNhan}, hỏng ${item.soLuongHong}, thiếu ${item.soLuongThieu}`).join("; ")}</td>
                <td>{[
                  receipt.ghiChu,
                  ...receipt.chiTiet.filter((item) => item.ghiChu).map((item) => `${item.tenSanPham}: ${item.ghiChu}`),
                ].filter(Boolean).join(" · ") || "—"}</td>
              </tr>)}
          </tbody>
        </table></div>
      </section>
    </>
  );
}
