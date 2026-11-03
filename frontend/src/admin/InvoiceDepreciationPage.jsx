import { useCallback, useEffect, useMemo, useState } from "react";
import { adminDownload, adminRequest } from "./adminApi";

const API_URL = "http://localhost:8081/api";
const money = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 });
const today = () => {
  const date = new Date();
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};
const emptyLine = () => ({ loai: "TAI_SAN", ten: "", soLuong: "1", donGia: "", taiSanId: "", vatTuId: "" });

async function loadAllAssets() {
  const firstPage = await adminRequest(`${API_URL}/tai-san?page=0&size=100`);
  const remainingPages = await Promise.all(Array.from(
    { length: Math.max(0, firstPage.totalPages - 1) },
    (_, index) => adminRequest(`${API_URL}/tai-san?page=${index + 1}&size=100`),
  ));
  return [firstPage, ...remainingPages].flatMap((page) => page.content ?? []);
}

function invoiceStatus(status) {
  return ({ CHUA_TRA: "Chưa trả", TRA_MOT_PHAN: "Trả một phần", DA_TRA: "Đã trả" })[status] || status;
}

export default function InvoiceDepreciationPage() {
  const [invoices, setInvoices] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [assets, setAssets] = useState([]);
  const [consumables, setConsumables] = useState([]);
  const [orders, setOrders] = useState([]);
  const [debts, setDebts] = useState([]);
  const [depreciation, setDepreciation] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [orderFormOpen, setOrderFormOpen] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [paymentInvoice, setPaymentInvoice] = useState(null);
  const [adjustmentInvoice, setAdjustmentInvoice] = useState(null);
  const [usefulLife, setUsefulLife] = useState({});
  const [invoiceForm, setInvoiceForm] = useState({
    soHoaDon: "", ngayMua: today(), nhaCungCapId: "", moTa: "", chiTiet: [emptyLine()],
  });
  const [orderForm, setOrderForm] = useState({ orderId: "", soHoaDon: "", ngayLap: today(), moTa: "" });
  const [paymentForm, setPaymentForm] = useState({ ngayThanhToan: today(), soTien: "", phuongThuc: "Chuyển khoản" });
  const [adjustmentForm, setAdjustmentForm] = useState({ ngayDieuChinh: today(), thayDoi: "", lyDo: "" });

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [invoiceRows, supplierRows, assetRows, consumableRows, orderRows, debtRows, depreciationRows] =
        await Promise.all([
          adminRequest(`${API_URL}/admin/hoa-don`),
          adminRequest(`${API_URL}/admin/nha-cung-cap`),
          loadAllAssets(),
          adminRequest(`${API_URL}/admin/vat-tu`),
          adminRequest(`${API_URL}/thuong-mai/don-hang`),
          adminRequest(`${API_URL}/admin/cong-no-nha-cung-cap`),
          adminRequest(`${API_URL}/admin/khau-hao`),
        ]);
      setInvoices(invoiceRows);
      setSuppliers(supplierRows);
      setAssets(assetRows);
      setConsumables(consumableRows);
      setOrders(orderRows);
      setDebts(debtRows);
      setDepreciation(depreciationRows);
      setUsefulLife((current) => Object.fromEntries(assetRows.map((asset) => [
        asset.taiSanId, current[asset.taiSanId] ?? String(asset.thoiGianKhauHaoNam ?? 5),
      ])));
      setSelectedInvoice((current) => current ? invoiceRows.find((row) => row.id === current.id) ?? null : null);
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const invoiceTotal = useMemo(() => invoiceForm.chiTiet.reduce((sum, line) => (
    sum + (Number(line.soLuong) || 0) * (Number(line.donGia) || 0)
  ), 0), [invoiceForm.chiTiet]);

  function setInvoiceField(name, value) {
    setInvoiceForm((current) => ({ ...current, [name]: value }));
  }

  function updateLine(index, name, value) {
    setInvoiceForm((current) => ({
      ...current,
      chiTiet: current.chiTiet.map((line, lineIndex) => {
        if (lineIndex !== index) return line;
        if (name === "loai") return { ...line, loai: value, ten: "", taiSanId: "", vatTuId: "" };
        if (name === "taiSanId") {
          const asset = assets.find((item) => item.taiSanId === Number(value));
          return {
            ...line,
            taiSanId: value,
            ten: asset?.tenTaiSan ?? "",
            donGia: asset?.giaMua ?? line.donGia,
            soLuong: value ? "1" : line.soLuong,
          };
        }
        if (name === "vatTuId") {
          const item = consumables.find((entry) => entry.id === Number(value));
          return { ...line, vatTuId: value, ten: item?.tenVatTu ?? "", donGia: item?.donGia ?? line.donGia };
        }
        return { ...line, [name]: value };
      }),
    }));
  }

  async function createInvoice(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    const selectedFile = document.getElementById("invoice-attachment")?.files?.[0];
    try {
      const payload = {
        ...invoiceForm,
        nhaCungCapId: invoiceForm.nhaCungCapId ? Number(invoiceForm.nhaCungCapId) : null,
        tongTien: invoiceTotal,
        chiTiet: invoiceForm.chiTiet.map((line) => ({
          loai: line.loai,
          ten: line.ten,
          soLuong: Number(line.soLuong),
          donGia: Number(line.donGia),
          taiSanId: line.taiSanId ? Number(line.taiSanId) : null,
          vatTuId: line.vatTuId ? Number(line.vatTuId) : null,
        })),
      };
      const created = await adminRequest(`${API_URL}/admin/hoa-don`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      setFormOpen(false);
      setInvoiceForm({ soHoaDon: "", ngayMua: today(), nhaCungCapId: "", moTa: "", chiTiet: [emptyLine()] });
      setSelectedInvoice(created);
      let attachmentWarning = "";
      if (selectedFile) {
        const formData = new FormData();
        formData.append("file", selectedFile);
        try {
          await adminRequest(`${API_URL}/admin/hoa-don/${created.id}/tep-dinh-kem`, {
            method: "POST",
            body: formData,
          });
        } catch (uploadError) {
          attachmentWarning = ` Hóa đơn đã được lập, nhưng tệp chưa tải lên được: ${uploadError.message}`;
        }
      }
      setSuccess(`Đã lập hóa đơn. Chứng từ không thể sửa hoặc xóa; hãy dùng phiếu điều chỉnh nếu cần.${attachmentWarning}`);
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function uploadInvoiceAttachment(invoice, file) {
    if (!file) return;
    setSaving(true);
    setError("");
    try {
      const formData = new FormData();
      formData.append("file", file);
      const updated = await adminRequest(`${API_URL}/admin/hoa-don/${invoice.id}/tep-dinh-kem`, {
        method: "POST",
        body: formData,
      });
      setSelectedInvoice(updated);
      setSuccess("Đã lưu tệp đính kèm hóa đơn.");
      await loadData();
    } catch (uploadError) {
      setError(uploadError.message);
    } finally {
      setSaving(false);
    }
  }

  async function createFromOrder(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const created = await adminRequest(`${API_URL}/admin/hoa-don/tu-don-mua/${orderForm.orderId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          soHoaDon: orderForm.soHoaDon,
          ngayLap: orderForm.ngayLap,
          moTa: orderForm.moTa,
        }),
      });
      setOrderForm({ orderId: "", soHoaDon: "", ngayLap: today(), moTa: "" });
      setOrderFormOpen(false);
      setSuccess("Đã lập hóa đơn từ đơn mua đã nhận.");
      setSelectedInvoice(created);
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function recordPayment(event) {
    event.preventDefault();
    if (!paymentInvoice) return;
    setSaving(true);
    setError("");
    try {
      const updated = await adminRequest(`${API_URL}/admin/hoa-don/${paymentInvoice.id}/thanh-toan`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...paymentForm, soTien: Number(paymentForm.soTien) }),
      });
      setPaymentInvoice(null);
      setPaymentForm({ ngayThanhToan: today(), soTien: "", phuongThuc: "Chuyển khoản" });
      setSelectedInvoice(updated);
      setSuccess("Đã ghi nhận thanh toán.");
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function recordAdjustment(event) {
    event.preventDefault();
    if (!adjustmentInvoice) return;
    setSaving(true);
    setError("");
    try {
      const updated = await adminRequest(`${API_URL}/admin/hoa-don/${adjustmentInvoice.id}/dieu-chinh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...adjustmentForm, thayDoi: Number(adjustmentForm.thayDoi) }),
      });
      setAdjustmentInvoice(null);
      setAdjustmentForm({ ngayDieuChinh: today(), thayDoi: "", lyDo: "" });
      setSelectedInvoice(updated);
      setSuccess("Đã lưu phiếu điều chỉnh hóa đơn.");
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function saveUsefulLife(assetId) {
    const years = Number(usefulLife[assetId]);
    setError("");
    setSuccess("");
    try {
      await adminRequest(`${API_URL}/admin/khau-hao/${assetId}/thoi-gian`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ thoiGianKhauHaoNam: years }),
      });
      setSuccess("Đã cập nhật thời gian khấu hao riêng cho tài sản.");
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    }
  }

  const invoicedQuantityByItem = new Map();
  for (const invoice of invoices) {
    for (const line of invoice.chiTiet ?? []) {
      if (invoice.donMuaId && line.dongDonMuaId) {
        const key = `${invoice.donMuaId}-${line.dongDonMuaId}`;
        invoicedQuantityByItem.set(key, (invoicedQuantityByItem.get(key) ?? 0) + Number(line.soLuong));
      }
    }
  }
  const receivedOrders = orders.filter((order) => order.loaiDon === "MUA"
    && ["DA_NHAN", "NHAN_MOT_PHAN"].includes(order.trangThai)
    && order.chiTiet.some((item) => item.soLuongDaNhan > (invoicedQuantityByItem.get(`${order.id}-${item.id}`) ?? 0)));
  const assetById = new Map(assets.map((asset) => [asset.taiSanId, asset]));

  return (
    <>
      <section className="welcome">
        <div><p className="eyebrow">QUẢN LÝ TÀI CHÍNH TÀI SẢN</p><h1>Hóa đơn & khấu hao</h1>
          <p className="welcome-copy">Theo dõi chứng từ mua hàng, công nợ nhà cung cấp và giá trị tài sản theo phương pháp đường thẳng.</p></div>
        <div className="admin-resource-actions">
          <button className="button secondary" type="button" onClick={() => { setError(""); setOrderFormOpen(true); }}>＋ Từ đơn mua</button>
          <button className="button" type="button" onClick={() => { setError(""); setFormOpen(true); }}>＋ Lập hóa đơn</button>
        </div>
      </section>
      {error && <p className="message error" role="alert">{error} <button className="retry-button" type="button" onClick={loadData}>Tải lại</button></p>}
      {success && <p className="message" role="status">{success}</p>}

      {formOpen && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><p className="eyebrow">CHỨNG TỪ MỚI</p><h2>Lập hóa đơn thủ công</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng" onClick={() => setFormOpen(false)}>×</button></div>
        <form onSubmit={createInvoice}>
          <div className="form-grid admin-resource-grid">
            <div className="field"><label htmlFor="invoice-number">Số hóa đơn *</label><input id="invoice-number" required maxLength="50" value={invoiceForm.soHoaDon} onChange={(event) => setInvoiceField("soHoaDon", event.target.value)} /></div>
            <div className="field"><label htmlFor="invoice-date">Ngày lập *</label><input id="invoice-date" type="date" required value={invoiceForm.ngayMua} onChange={(event) => setInvoiceField("ngayMua", event.target.value)} /></div>
            <div className="field"><label htmlFor="invoice-supplier">Nhà cung cấp</label><select id="invoice-supplier" value={invoiceForm.nhaCungCapId} onChange={(event) => setInvoiceField("nhaCungCapId", event.target.value)}>
              <option value="">Chọn nhà cung cấp</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.tenNhaCungCap}</option>)}
            </select></div>
            <div className="field admin-resource-wide"><label htmlFor="invoice-note">Ghi chú</label><textarea id="invoice-note" value={invoiceForm.moTa} onChange={(event) => setInvoiceField("moTa", event.target.value)} maxLength="500" /></div>
            <div className="field admin-resource-wide"><label htmlFor="invoice-attachment">Tệp hóa đơn (PDF, PNG, JPEG; tối đa 10 MB)</label><input id="invoice-attachment" type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" /></div>
          </div>
          <div className="panel-heading"><div><h3>Chi tiết hóa đơn</h3><p className="muted">Tổng tiền được tính từ số lượng × đơn giá.</p></div>
            <button className="button secondary" type="button" onClick={() => setInvoiceForm((current) => ({ ...current, chiTiet: [...current.chiTiet, emptyLine()] }))}>＋ Thêm dòng</button></div>
          <div className="table-wrap"><table><thead><tr><th>LOẠI</th><th>MẶT HÀNG / TÀI SẢN</th><th>SỐ LƯỢNG</th><th>ĐƠN GIÁ</th><th>THÀNH TIỀN</th><th></th></tr></thead><tbody>
            {invoiceForm.chiTiet.map((line, index) => <tr key={index}>
              <td><select aria-label="Loại dòng" value={line.loai} onChange={(event) => updateLine(index, "loai", event.target.value)}>
                <option value="TAI_SAN">Tài sản</option><option value="VAT_TU">Vật tư</option><option value="KHAC">Khác</option></select></td>
              <td>{line.loai === "TAI_SAN" ? <select required value={line.taiSanId} onChange={(event) => updateLine(index, "taiSanId", event.target.value)}>
                <option value="">Chọn tài sản liên kết</option>{assets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan}</option>)}
              </select> : line.loai === "VAT_TU" ? <select required value={line.vatTuId} onChange={(event) => updateLine(index, "vatTuId", event.target.value)}>
                <option value="">Chọn vật tư</option>{consumables.map((item) => <option key={item.id} value={item.id}>{item.maVatTu} · {item.tenVatTu}</option>)}
              </select> : <input required aria-label="Tên mặt hàng" value={line.ten} onChange={(event) => updateLine(index, "ten", event.target.value)} maxLength="200" />}</td>
              <td><input aria-label="Số lượng" required type="number" min="0.001" step="0.001" value={line.soLuong} disabled={line.loai === "TAI_SAN" && Boolean(line.taiSanId)} onChange={(event) => updateLine(index, "soLuong", event.target.value)} /></td>
              <td><input aria-label="Đơn giá" required type="number" min="0" step="0.01" value={line.donGia} onChange={(event) => updateLine(index, "donGia", event.target.value)} /></td>
              <td>{money.format((Number(line.soLuong) || 0) * (Number(line.donGia) || 0))} ₫</td>
              <td><button className="link-button" type="button" disabled={invoiceForm.chiTiet.length === 1} onClick={() => setInvoiceForm((current) => ({ ...current, chiTiet: current.chiTiet.filter((_, lineIndex) => lineIndex !== index) }))}>Bỏ dòng</button></td>
            </tr>)}
          </tbody><tfoot><tr><th colSpan="4">TỔNG CỘNG</th><th>{money.format(invoiceTotal)} ₫</th><th></th></tr></tfoot></table></div>
          <div className="form-actions"><button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu..." : "Lập hóa đơn"}</button><button className="button secondary" type="button" onClick={() => setFormOpen(false)}>Hủy</button></div>
        </form>
      </section>}

      {orderFormOpen && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><p className="eyebrow">LIÊN KẾT MUA SẮM</p><h2>Lập hóa đơn từ đơn mua</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng" onClick={() => setOrderFormOpen(false)}>×</button></div>
        {receivedOrders.length === 0 ? <p className="notification-empty">Không có đơn mua đã nhận hàng chưa lập hóa đơn.</p> : <form onSubmit={createFromOrder}>
          <div className="form-grid admin-resource-grid">
            <div className="field admin-resource-wide"><label htmlFor="invoice-order">Đơn mua đã nhận *</label><select id="invoice-order" required value={orderForm.orderId} onChange={(event) => setOrderForm((current) => ({ ...current, orderId: event.target.value }))}>
              <option value="">Chọn đơn mua</option>{receivedOrders.map((order) => <option key={order.id} value={order.id}>{order.maDonHang} · {order.nhaCungCap || "Không có nhà cung cấp"} · {order.trangThai}</option>)}
            </select></div>
            <div className="field"><label htmlFor="order-invoice-number">Số hóa đơn *</label><input id="order-invoice-number" required maxLength="50" value={orderForm.soHoaDon} onChange={(event) => setOrderForm((current) => ({ ...current, soHoaDon: event.target.value }))} /></div>
            <div className="field"><label htmlFor="order-invoice-date">Ngày lập *</label><input id="order-invoice-date" type="date" required value={orderForm.ngayLap} onChange={(event) => setOrderForm((current) => ({ ...current, ngayLap: event.target.value }))} /></div>
            <div className="field admin-resource-wide"><label htmlFor="order-invoice-note">Ghi chú</label><textarea id="order-invoice-note" maxLength="500" value={orderForm.moTa} onChange={(event) => setOrderForm((current) => ({ ...current, moTa: event.target.value }))} /></div>
          </div>
          <div className="form-actions"><button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu..." : "Tạo hóa đơn từ đơn mua"}</button></div>
        </form>}
      </section>}

      {paymentInvoice && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><p className="eyebrow">GHI NHẬN THANH TOÁN</p><h2>{paymentInvoice.soHoaDon}</h2><p className="muted">Còn nợ {money.format(paymentInvoice.conNo)} ₫</p></div>
          <button className="icon-button" type="button" aria-label="Đóng" onClick={() => setPaymentInvoice(null)}>×</button></div>
        <form onSubmit={recordPayment}><div className="form-grid admin-resource-grid">
          <div className="field"><label htmlFor="payment-date">Ngày thanh toán *</label><input id="payment-date" type="date" required value={paymentForm.ngayThanhToan} onChange={(event) => setPaymentForm((current) => ({ ...current, ngayThanhToan: event.target.value }))} /></div>
          <div className="field"><label htmlFor="payment-amount">Số tiền *</label><input id="payment-amount" type="number" min="0.01" step="0.01" max={paymentInvoice.conNo} required value={paymentForm.soTien} onChange={(event) => setPaymentForm((current) => ({ ...current, soTien: event.target.value }))} /></div>
          <div className="field"><label htmlFor="payment-method">Phương thức *</label><select id="payment-method" value={paymentForm.phuongThuc} onChange={(event) => setPaymentForm((current) => ({ ...current, phuongThuc: event.target.value }))}><option>Chuyển khoản</option><option>Tiền mặt</option><option>Khác</option></select></div>
        </div><div className="form-actions"><button className="button" type="submit" disabled={saving}>Ghi thanh toán</button></div></form>
      </section>}

      {adjustmentInvoice && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><p className="eyebrow">PHIẾU ĐIỀU CHỈNH BẤT BIẾN</p><h2>{adjustmentInvoice.soHoaDon}</h2><p className="muted">Dùng số dương để tăng, số âm để giảm tổng hóa đơn.</p></div>
          <button className="icon-button" type="button" aria-label="Đóng" onClick={() => setAdjustmentInvoice(null)}>×</button></div>
        <form onSubmit={recordAdjustment}><div className="form-grid admin-resource-grid">
          <div className="field"><label htmlFor="adjustment-date">Ngày điều chỉnh *</label><input id="adjustment-date" type="date" required value={adjustmentForm.ngayDieuChinh} onChange={(event) => setAdjustmentForm((current) => ({ ...current, ngayDieuChinh: event.target.value }))} /></div>
          <div className="field"><label htmlFor="adjustment-amount">Số tiền tăng / giảm *</label><input id="adjustment-amount" type="number" step="0.01" required value={adjustmentForm.thayDoi} onChange={(event) => setAdjustmentForm((current) => ({ ...current, thayDoi: event.target.value }))} /></div>
          <div className="field admin-resource-wide"><label htmlFor="adjustment-reason">Lý do *</label><textarea id="adjustment-reason" required maxLength="500" value={adjustmentForm.lyDo} onChange={(event) => setAdjustmentForm((current) => ({ ...current, lyDo: event.target.value }))} /></div>
        </div><div className="form-actions"><button className="button" type="submit" disabled={saving}>Lưu phiếu điều chỉnh</button></div></form>
      </section>}

      <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading"><div><h2>Danh sách hóa đơn</h2><p className="muted">Hóa đơn và lịch sử thanh toán được lưu; không thể sửa hoặc xóa.</p></div></div>
        <div className="table-wrap"><table><thead><tr><th>SỐ HÓA ĐƠN</th><th>NGÀY LẬP</th><th>NHÀ CUNG CẤP</th><th>ĐƠN MUA</th><th>TỔNG TIỀN</th><th>ĐÃ TRẢ</th><th>CÒN NỢ</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
          {loading ? <tr><td className="empty" colSpan="9">Đang tải...</td></tr>
            : invoices.length === 0 ? <tr><td className="empty" colSpan="9">Chưa có hóa đơn.</td></tr>
              : invoices.map((invoice) => <tr key={invoice.id}>
                <td><button className="link-button" type="button" onClick={() => setSelectedInvoice(invoice)}>{invoice.soHoaDon}</button></td>
                <td>{invoice.ngayMua}</td><td>{invoice.nhaCungCap || "—"}</td><td>{invoice.donMuaId ? orders.find((order) => order.id === invoice.donMuaId)?.maDonHang ?? `#${invoice.donMuaId}` : "Nhập tay"}</td>
                <td>{money.format(invoice.tongTien)} ₫</td><td>{money.format(invoice.daThanhToan)} ₫</td><td>{money.format(invoice.conNo)} ₫</td><td>{invoiceStatus(invoice.trangThaiThanhToan)}</td>
                <td className="actions"><button className="link-button" type="button" onClick={() => setPaymentInvoice(invoice)} disabled={invoice.conNo <= 0}>Thanh toán</button>
                  <button className="link-button" type="button" onClick={() => setAdjustmentInvoice(invoice)}>Điều chỉnh</button></td>
              </tr>)}
        </tbody></table></div>
      </section>

      {selectedInvoice && <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading"><div><h2>Chi tiết hóa đơn {selectedInvoice.soHoaDon}</h2><p className="muted">{selectedInvoice.moTa || "Không có ghi chú"}</p></div>
          <button className="icon-button" type="button" aria-label="Đóng chi tiết" onClick={() => setSelectedInvoice(null)}>×</button></div>
        <div className="table-wrap"><table><thead><tr><th>LOẠI</th><th>MẶT HÀNG</th><th>SỐ LƯỢNG</th><th>ĐƠN GIÁ</th><th>THÀNH TIỀN</th></tr></thead><tbody>
          {selectedInvoice.chiTiet?.length ? selectedInvoice.chiTiet.map((line) => <tr key={line.id}><td>{line.loai}</td><td>{line.ten}</td><td>{line.soLuong}</td><td>{money.format(line.donGia)} ₫</td><td>{money.format(line.thanhTien)} ₫</td></tr>)
            : <tr><td className="empty" colSpan="5">Hóa đơn cũ chưa có chi tiết dòng hàng.</td></tr>}
        </tbody></table></div>
        <div className="panel-heading"><div><h3>Lịch sử thanh toán</h3></div>
          {selectedInvoice.tenTepDinhKem
            ? <button className="button secondary" type="button" onClick={() => adminDownload(`${API_URL}/admin/hoa-don/${selectedInvoice.id}/tep-dinh-kem`, selectedInvoice.tenTepDinhKem).catch((downloadError) => setError(downloadError.message))}>Tải {selectedInvoice.tenTepDinhKem}</button>
            : <label className="button secondary">Đính kèm hóa đơn<input type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" disabled={saving} onChange={(event) => uploadInvoiceAttachment(selectedInvoice, event.target.files?.[0])} hidden /></label>}
        </div>
        <div className="table-wrap"><table><thead><tr><th>NGÀY</th><th>SỐ TIỀN</th><th>PHƯƠNG THỨC</th><th>NGƯỜI GHI</th></tr></thead><tbody>
          {selectedInvoice.thanhToan?.length ? selectedInvoice.thanhToan.map((payment) => <tr key={payment.id}><td>{payment.ngayThanhToan}</td><td>{money.format(payment.soTien)} ₫</td><td>{payment.phuongThuc}</td><td>{payment.nguoiGhi}</td></tr>)
            : <tr><td className="empty" colSpan="4">Chưa ghi nhận thanh toán.</td></tr>}
        </tbody></table></div>
        {selectedInvoice.dieuChinh?.length > 0 && <><h3>Phiếu điều chỉnh</h3><div className="table-wrap"><table><thead><tr><th>NGÀY</th><th>THAY ĐỔI</th><th>LÝ DO</th><th>NGƯỜI GHI</th></tr></thead><tbody>{selectedInvoice.dieuChinh.map((adjustment) => <tr key={adjustment.id}><td>{adjustment.ngayDieuChinh}</td><td>{money.format(adjustment.thayDoi)} ₫</td><td>{adjustment.lyDo}</td><td>{adjustment.nguoiGhi}</td></tr>)}</tbody></table></div></>}
      </section>}

      <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading"><div><h2>Công nợ nhà cung cấp</h2><p className="muted">Tổng hợp tổng hóa đơn, tiền đã thanh toán và số còn phải trả.</p></div></div>
        <div className="table-wrap"><table><thead><tr><th>NHÀ CUNG CẤP</th><th>TỔNG HÓA ĐƠN</th><th>ĐÃ THANH TOÁN</th><th>CÒN NỢ</th></tr></thead><tbody>
          {debts.length === 0 ? <tr><td className="empty" colSpan="4">Chưa phát sinh công nợ.</td></tr>
            : debts.map((debt) => <tr key={debt.nhaCungCapId ?? debt.nhaCungCap}><td>{debt.nhaCungCap}</td><td>{money.format(debt.tongHoaDon)} ₫</td><td>{money.format(debt.daThanhToan)} ₫</td><td>{money.format(debt.conNo)} ₫</td></tr>)}
        </tbody></table></div>
      </section>

      <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading"><div><h2>Khấu hao đường thẳng</h2><p className="muted">Số năm mặc định lấy từ loại tài sản; có thể nhập thời gian riêng cho từng tài sản. Tài sản đã thanh lý dừng khấu hao tại ngày thanh lý.</p></div></div>
        <div className="table-wrap"><table><thead><tr><th>MÃ TÀI SẢN</th><th>TÊN TÀI SẢN</th><th>SỐ NĂM</th><th></th></tr></thead><tbody>
          {assets.filter((asset) => asset.giaMua != null && asset.ngayMua).map((asset) => <tr key={asset.taiSanId}>
            <td>{asset.maTaiSan}</td><td>{asset.tenTaiSan}</td>
            <td><input aria-label={`Số năm khấu hao ${asset.maTaiSan}`} type="number" min="1" max="100" step="1" value={usefulLife[asset.taiSanId] ?? ""} onChange={(event) => setUsefulLife((current) => ({ ...current, [asset.taiSanId]: event.target.value }))} /></td>
            <td><button className="link-button" type="button" onClick={() => saveUsefulLife(asset.taiSanId)}>Lưu số năm</button></td>
          </tr>)}
          {assets.filter((asset) => asset.giaMua != null && asset.ngayMua).length === 0 && <tr><td className="empty" colSpan="4">Chưa có tài sản đủ dữ liệu giá mua và ngày mua.</td></tr>}
        </tbody></table></div>
        <h3>Bảng giá trị theo năm</h3>
        <div className="table-wrap"><table><thead><tr><th>MÃ</th><th>TÀI SẢN</th><th>NĂM</th><th>ĐẦU NĂM</th><th>KHẤU HAO</th><th>CUỐI NĂM</th></tr></thead><tbody>
          {depreciation.length === 0 ? <tr><td className="empty" colSpan="6">Chưa có dữ liệu khấu hao.</td></tr>
            : depreciation.map((row, index) => <tr key={`${row.taiSanId}-${row.nam}-${index}`}><td>{row.maTaiSan}</td><td>{row.tenTaiSan}</td><td>{row.nam}</td><td>{money.format(row.giaTriConLaiDauNam)} ₫</td><td>{money.format(row.khauHaoNam)} ₫</td><td>{money.format(row.giaTriConLaiCuoiNam)} ₫</td></tr>)}
        </tbody></table></div>
      </section>
    </>
  );
}
