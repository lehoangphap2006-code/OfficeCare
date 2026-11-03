import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { adminRequest } from "./adminApi";

function displayValue(value) {
  if (!value) return "—";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export default function ActivityLogPage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedValue, setSelectedValue] = useState(null);
  const [actorFilter, setActorFilter] = useState("");
  const [actionFilter, setActionFilter] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const valueDialogRef = useRef(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setRows(await adminRequest("http://localhost:8081/api/nhat-ky"));
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadLogs(); }, [loadLogs]);

  useEffect(() => {
    const dialog = valueDialogRef.current;
    if (selectedValue && dialog && !dialog.open) dialog.showModal();
  }, [selectedValue]);

  const actors = useMemo(() => [...new Set(rows.map((row) => row.actor).filter(Boolean))].sort(), [rows]);
  const actions = useMemo(() => [...new Set(rows.map((row) => row.action).filter(Boolean))].sort(), [rows]);
  const filteredRows = useMemo(() => {
    const from = fromDate ? new Date(`${fromDate}T00:00:00`) : null;
    const to = toDate ? new Date(`${toDate}T23:59:59.999`) : null;
    return rows.filter((row) => {
      const createdAt = new Date(row.createdAt);
      return (!actorFilter || row.actor === actorFilter)
        && (!actionFilter || row.action === actionFilter)
        && (!from || createdAt >= from)
        && (!to || createdAt <= to);
    });
  }, [rows, actorFilter, actionFilter, fromDate, toDate]);

  return (
    <>
      <section className="welcome">
        <div><p className="eyebrow">QUẢN TRỊ HỆ THỐNG</p><h1>Nhật ký hoạt động</h1><p className="welcome-copy">Tối đa 100 thao tác gần nhất, gồm người thực hiện và dữ liệu trước/sau.</p></div>
        <button className="button secondary" type="button" onClick={loadLogs} disabled={loading}>Làm mới</button>
      </section>
      {error && <p className="message error" role="alert">{error}</p>}
      <section className="panel assets-panel admin-activity-panel">
        <form className="admin-log-filters" onSubmit={(event) => event.preventDefault()}>
          <div className="field"><label htmlFor="activity-actor-filter">Người thao tác</label><select id="activity-actor-filter" value={actorFilter} onChange={(event) => setActorFilter(event.target.value)}><option value="">Tất cả</option>{actors.map((actor) => <option key={actor} value={actor}>{actor}</option>)}</select></div>
          <div className="field"><label htmlFor="activity-action-filter">Loại hành động</label><select id="activity-action-filter" value={actionFilter} onChange={(event) => setActionFilter(event.target.value)}><option value="">Tất cả</option>{actions.map((action) => <option key={action} value={action}>{action}</option>)}</select></div>
          <div className="field"><label htmlFor="activity-from-date">Từ ngày</label><input id="activity-from-date" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} /></div>
          <div className="field"><label htmlFor="activity-to-date">Đến ngày</label><input id="activity-to-date" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} min={fromDate || undefined} /></div>
          <button className="text-button" type="button" onClick={() => { setActorFilter(""); setActionFilter(""); setFromDate(""); setToDate(""); }}>Xóa bộ lọc</button>
          <span className="muted admin-log-count">{filteredRows.length}/{rows.length} bản ghi</span>
        </form>
        <div className="table-wrap" aria-busy={loading}><table><colgroup>
          <col className="activity-time-column" /><col className="activity-actor-column" />
          <col className="activity-action-column" /><col className="activity-entity-column" />
          <col className="activity-value-column" /><col className="activity-value-column" />
        </colgroup><thead><tr><th>THỜI GIAN</th><th>NGƯỜI THAO TÁC</th><th>HÀNH ĐỘNG</th><th>ĐỐI TƯỢNG</th><th>NỘI DUNG CŨ</th><th>NỘI DUNG MỚI</th></tr></thead><tbody>
          {loading ? <tr><td className="empty loading-state" colSpan="6">Đang tải nhật ký...</td></tr>
            : error ? <tr><td className="empty" colSpan="6">Không thể tải nhật ký.</td></tr>
              : rows.length === 0 ? <tr><td className="empty" colSpan="6">Chưa có hoạt động được ghi nhận.</td></tr>
                : filteredRows.length === 0 ? <tr><td className="empty" colSpan="6">Không có bản ghi khớp với bộ lọc.</td></tr>
                : filteredRows.map((row) => <tr key={row.id}>
                  <td>{new Date(row.createdAt).toLocaleString("vi-VN")}</td>
                  <td className="code">{row.actor}</td>
                  <td>{row.action}</td>
                  <td>{row.entityType} #{row.entityId ?? "—"}</td>
                  {[["Nội dung cũ", row.oldValue], ["Nội dung mới", row.newValue]].map(([label, value]) => (
                    <td key={label}>
                      {value
                        ? <button
                          className="activity-view-button"
                          type="button"
                          onClick={() => setSelectedValue({
                            label,
                            value: displayValue(value),
                            entity: `${row.entityType} #${row.entityId ?? "—"}`,
                            action: row.action,
                          })}
                        >Xem dữ liệu</button>
                        : "—"}
                    </td>
                  ))}
                </tr>)}
        </tbody></table></div>
      </section>
      <dialog
        ref={valueDialogRef}
        className="detail-dialog activity-data-dialog"
        aria-labelledby="activity-data-title"
        onClose={() => setSelectedValue(null)}
      >
        {selectedValue && <>
          <div className="panel-heading">
            <div>
              <p className="eyebrow">{selectedValue.entity} · {selectedValue.action}</p>
              <h2 id="activity-data-title">{selectedValue.label}</h2>
            </div>
            <button className="icon-button" type="button" aria-label="Đóng dữ liệu nhật ký" onClick={() => valueDialogRef.current?.close()}>×</button>
          </div>
          <pre className="activity-value">{selectedValue.value}</pre>
        </>}
      </dialog>
    </>
  );
}
