import { useCallback, useEffect, useRef, useState } from "react";
import { adminRequest } from "./adminApi";

const API_URL = "http://localhost:8081/api/admin/purchase-requests";
const dateText = (value) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
};
const currencyText = (value) => value == null
  ? "—"
  : `${Number(value).toLocaleString("vi-VN", { maximumFractionDigits: 0 })} ₫`;
const hasLowEstimate = (value) => value != null && Number(value) < 1000;

export default function PurchaseRequestsPage({
  onOpenReceipts,
  onOpenOrder,
  onCreateOrder,
  onPendingCountChange,
  refreshKey,
  focusRequestId,
}) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState(null);
  const [reviewTarget, setReviewTarget] = useState(null);
  const [reviewNote, setReviewNote] = useState("");
  const [error, setError] = useState("");
  const [reviewError, setReviewError] = useState("");
  const [message, setMessage] = useState("");
  const reviewDialogRef = useRef(null);

  const loadRequests = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const requests = await adminRequest(API_URL);
      setRows(requests);
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, [onPendingCountChange]);

  useEffect(() => { loadRequests(); }, [loadRequests, refreshKey]);

  useEffect(() => {
    if (!focusRequestId || !rows.some((row) => row.id === focusRequestId)) return;
    document.getElementById(`purchase-request-${focusRequestId}`)
      ?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [focusRequestId, rows]);

  useEffect(() => {
    if (reviewTarget && reviewDialogRef.current && !reviewDialogRef.current.open) {
      reviewDialogRef.current.showModal();
    }
  }, [reviewTarget]);

  function beginReview(row, status) {
    setError("");
    setMessage("");
    setReviewError("");
    setReviewNote("");
    setReviewTarget({ row, status });
  }

  function closeReview() {
    if (savingId != null) return;
    setReviewTarget(null);
    setReviewError("");
    setReviewNote("");
  }

  async function review(event) {
    event.preventDefault();
    if (!reviewTarget) return;
    const { row, status } = reviewTarget;
    const note = reviewNote.trim();
    if (status === "REJECTED" && !note) {
      setReviewError("Vui lòng nhập lý do từ chối để người yêu cầu biết kết quả.");
      return;
    }
    if (status === "REJECTED" && !window.confirm(`Xác nhận từ chối yêu cầu của ${row.requester}?`)) return;
    setSavingId(row.id);
    setReviewError("");
    setError("");
    setMessage("");
    try {
      const updated = await adminRequest(`${API_URL}/${row.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status, reviewNote: note || null }),
      });
      setRows((current) => current.map((item) => item.id === updated.id ? updated : item));
      if (row.status === "PENDING" && updated.status !== "PENDING") {
        onPendingCountChange((count) => Math.max(0, count - 1));
      }
      setReviewTarget(null);
      setReviewNote("");
      setMessage(status === "APPROVED"
        ? `Đã duyệt yêu cầu của ${row.requester}. Có thể tạo đơn mua từ yêu cầu này.`
        : `Đã từ chối yêu cầu của ${row.requester}.`);
    } catch (reviewError) {
      setReviewError(reviewError.message);
    } finally {
      setSavingId(null);
    }
  }

  return (
    <>
      {error && <p className="message error" role="alert">{error}</p>}
      {message && <p className="message" role="status">{message}</p>}
      <section className="panel assets-panel">
        <div className="panel-heading assets-heading"><div><h2>Yêu cầu mua sắm từ nhân viên</h2><p className="muted">{rows.length} yêu cầu gần nhất · Nhân viên gửi yêu cầu, admin quyết định</p></div><button className="text-button" type="button" onClick={loadRequests} disabled={loading}>Làm mới</button></div>
        <div className="table-wrap" aria-busy={loading}><table>
          <thead><tr><th>NGÀY GỬI</th><th>NGƯỜI YÊU CẦU</th><th>THIẾT BỊ / LOẠI</th><th>SỐ LƯỢNG</th><th>LÝ DO</th><th>DỰ TOÁN</th><th>TRẠNG THÁI / ĐƠN MUA</th><th>GHI CHÚ DUYỆT</th><th>THAO TÁC</th></tr></thead>
          <tbody>{loading ? <tr><td className="empty loading-state" colSpan="9">Đang tải yêu cầu...</td></tr>
            : rows.length === 0 ? <tr><td className="empty" colSpan="9">Chưa có yêu cầu mua sắm.</td></tr>
              : rows.map((row) => <tr key={row.id} id={`purchase-request-${row.id}`}
                className={focusRequestId === row.id ? "purchase-request-focused" : undefined}>
                <td>{dateText(row.createdAt)}</td>
                <td>{row.requester}</td>
                <td>{row.assetName}<br /><span className="muted">{row.categoryName || "—"}</span></td>
                <td>{row.quantity}</td>
                <td>{row.reason}</td>
                <td>{row.estimatedAmount == null ? "—" : <>{currencyText(row.estimatedAmount)}{hasLowEstimate(row.estimatedAmount) && <><br /><span className="purchase-estimate-warning">Dự toán thấp · xác nhận đơn giá thực tế khi tạo đơn</span></>}</>}</td>
                <td>{row.purchaseOrderNumber ? <><span className="purchase-order-status">{row.status === "COMPLETED" || row.purchaseOrderStatus === "DA_NHAN"
                  ? "Đã nhận đủ"
                  : row.purchaseOrderStatus === "NHAN_MOT_PHAN"
                    ? "Đã nhận một phần"
                    : row.purchaseOrderStatus === "DA_HUY" ? "Đã hủy" : "Đã đặt"}</span><br />
                  <button className="link-button" type="button" onClick={() => onOpenOrder(row)}>{row.purchaseOrderNumber} · Xem đơn</button>
                  {row.purchaseOrderStatus !== "DA_NHAN" && row.purchaseOrderStatus !== "DA_HUY"
                    && <button className="link-button" type="button" onClick={() => onOpenReceipts(row)}>Nhận hàng</button>}</>
                  : row.status === "PENDING" ? "Chờ duyệt"
                    : row.status === "APPROVED" ? "Đã duyệt · chờ tạo đơn mua"
                      : row.status === "COMPLETED" ? "Hoàn tất · đã nhận đủ" : "Đã từ chối"}</td>
                <td>{row.reviewNote && !["hoàn thành", "hoan thanh", "completed", "done"].includes(row.reviewNote.trim().toLocaleLowerCase("vi"))
                  ? row.reviewNote
                  : "—"}
                  {row.reviewer && <><br /><span className="muted">Duyệt bởi {row.reviewer}</span></>}</td>
                <td className="actions">{row.status === "PENDING" ? <div className="purchase-request-actions">
                  <button className="button purchase-approve-button" type="button"
                    disabled={savingId === row.id}
                    onClick={() => beginReview(row, "APPROVED")}>Duyệt</button>
                  <button className="button purchase-reject-button" type="button" disabled={savingId === row.id} onClick={() => beginReview(row, "REJECTED")}>Từ chối</button>
                </div> : row.status === "APPROVED" && !row.purchaseOrderId
                  ? <button className="button purchase-order-button" type="button" onClick={() => onCreateOrder(row)}>Tạo đơn mua</button> : "—"}</td>
              </tr>)}
          </tbody>
        </table></div>
      </section>
      {reviewTarget && <dialog ref={reviewDialogRef} className="detail-dialog commerce-receipt-dialog purchase-review-dialog"
        aria-labelledby="purchase-review-title" onCancel={(event) => { event.preventDefault(); closeReview(); }}>
        <form onSubmit={review}>
          <div className="panel-heading">
            <div>
              <p className="eyebrow">{reviewTarget.status === "APPROVED" ? "DUYỆT YÊU CẦU" : "TỪ CHỐI YÊU CẦU"}</p>
              <h2 id="purchase-review-title">{reviewTarget.row.assetName} × {reviewTarget.row.quantity}</h2>
              <p className="muted">Người yêu cầu: {reviewTarget.row.requester} · Dự toán: {currencyText(reviewTarget.row.estimatedAmount)}</p>
            </div>
            <button className="icon-button" type="button" aria-label="Đóng" onClick={closeReview} disabled={savingId != null}>×</button>
          </div>
          <label>{reviewTarget.status === "APPROVED" ? "Ghi chú duyệt (không bắt buộc)" : "Lý do từ chối"}
            <textarea autoFocus value={reviewNote} onChange={(event) => setReviewNote(event.target.value)}
              maxLength={500} required={reviewTarget.status === "REJECTED"} rows={4}
              placeholder={reviewTarget.status === "APPROVED"
                ? "Ví dụ: Đã duyệt, mua trong tháng 11."
                : "Nêu rõ lý do để nhân viên biết và có thể gửi yêu cầu phù hợp hơn."} />
            <small>{reviewNote.length}/500 ký tự</small>
          </label>
          {reviewError && <p className="message error" role="alert">{reviewError}</p>}
          <div className="commerce-actions">
            <button className="button secondary" type="button" onClick={closeReview} disabled={savingId != null}>Hủy</button>
            <button className={`button ${reviewTarget.status === "REJECTED" ? "purchase-reject-button" : "purchase-approve-button"}`}
              type="submit" disabled={savingId != null}>
              {savingId != null ? "Đang lưu..." : reviewTarget.status === "APPROVED" ? "Xác nhận duyệt" : "Xác nhận từ chối"}
            </button>
          </div>
        </form>
      </dialog>}
    </>
  );
}
