import { useCallback, useEffect, useState } from "react";
import { adminRequest } from "./adminApi";

const numberFormatter = new Intl.NumberFormat("vi-VN");
const currencyFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 });
const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
});
const todayFormatter = new Intl.DateTimeFormat("vi-VN", {
  weekday: "long", day: "2-digit", month: "2-digit", year: "numeric",
});
const emptyFilters = { tinhTrang: "", loaiTaiSan: "", phongBan: "", muaTuNgay: "", muaDenNgay: "" };
const metrics = [
  ["Tổng tài sản", "tongTaiSan", "▦", "stat-purple", false],
  ["Đang sử dụng", "dangSuDung", "✓", "stat-green", false],
  ["Trong kho", "trongKho", "▣", "stat-blue", false],
  ["Đang sửa chữa", "dangSuaChua", "⚒", "stat-orange", false],
  ["Tổng chi phí sửa chữa", "tongChiPhiSuaChua", "₫", "stat-purple", true],
  ["Đã thanh lý", "daThanhLy", "↗", "stat-orange", false],
  ["Chờ thanh lý", "choThanhLy", "♲", "stat-orange", false],
  ["Đang thiếu", "thieu", "!", "stat-orange", false],
  ["Bảo trì sắp/đến hạn", "baoTriSapDenHan", "◷", "stat-orange", false],
  ["Bảo trì quá hạn", "baoTriQuaHan", "!", "stat-red", false],
  ["Tổng giá trị", "tongGiaTri", "₫", "stat-purple", true],
  ["Giá trị còn lại", "giaTriConLai", "₫", "stat-green", true],
];
const primaryMetrics = new Set(["tongTaiSan", "dangSuDung", "trongKho", "dangSuaChua"]);
const chartColors = ["#2563eb", "#10b981", "#f59e0b", "#8b5cf6", "#6470a8", "#ec4899", "#06b6d4", "#84cc16"];

function formatAlertDescription(value) {
  return String(value ?? "").replace(/\b(\d{4})-(\d{2})-(\d{2})\b/g, (_, year, month, day) => `${day}/${month}/${year}`);
}

function DistributionPanel({ title, description, rows }) {
  const [expanded, setExpanded] = useState(false);
  const rankedRows = [...rows].sort((first, second) => second.soLuong - first.soLuong || first.nhan.localeCompare(second.nhan, "vi"));
  const visibleRows = expanded ? rankedRows : rankedRows.slice(0, 7);
  const maximum = Math.max(1, ...rankedRows.map((row) => row.soLuong));

  return (
    <section className="panel admin-distribution-panel">
      <div className="panel-heading">
        <div><h2>{title}</h2><p className="muted">{description}</p></div>
      </div>
      {rows.length === 0
        ? <p className="notification-empty">Chưa có dữ liệu để thống kê.</p>
        : <div className={`admin-distribution-list${expanded ? " expanded" : ""}`}>
          {visibleRows.map((row) => (
            <div className="admin-distribution-row" key={row.nhan}>
              <span title={row.nhan}>{row.nhan}</span>
              <div className="admin-distribution-track" aria-hidden="true">
                <span style={{ width: `${Math.max(3, row.soLuong / maximum * 100)}%` }} />
              </div>
              <strong>{numberFormatter.format(row.soLuong)}</strong>
            </div>
          ))}
          {rankedRows.length > 7 && <button className="text-button admin-distribution-toggle" type="button"
            aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>
            {expanded ? "Thu gọn" : `Xem thêm ${numberFormatter.format(rankedRows.length - 7)} nhóm`}
          </button>}
        </div>}
    </section>
  );
}

function CategoryPanel({ rows, total }) {
  const categories = [...rows]
    .sort((first, second) => second.soLuong - first.soLuong || first.nhan.localeCompare(second.nhan, "vi"));
  const categoryTotal = categories.reduce((sum, row) => sum + row.soLuong, 0);
  let position = 0;
  const gradient = categories.map((row, index) => {
    const start = position;
    position += categoryTotal ? row.soLuong / categoryTotal * 100 : 0;
    return `${chartColors[index % chartColors.length]} ${start}% ${position}%`;
  }).join(", ");

  return (
    <section className="panel admin-category-panel">
      <div className="panel-heading">
        <div><h2>Thống kê tài sản theo danh mục</h2><p className="muted">Phân bổ theo loại tài sản</p></div>
      </div>
      {categories.length === 0
        ? <p className="notification-empty">Chưa có dữ liệu để thống kê.</p>
        : <div className="admin-category-content">
          <div className="admin-category-donut"
            role="img"
            aria-label={`Phân bổ ${numberFormatter.format(categoryTotal)} tài sản theo danh mục`}
            style={{ background: `conic-gradient(${gradient})` }}>
            <div><strong>{numberFormatter.format(total)}</strong><span>Tài sản</span></div>
          </div>
          <ul className="admin-category-legend">
            {categories.map((row, index) => (
              <li key={row.nhan}>
                <span className="admin-category-label">
                  <i style={{ backgroundColor: chartColors[index % chartColors.length] }} aria-hidden="true" />
                  <span title={row.nhan}>{row.nhan}</span>
                </span>
                <strong>{categoryTotal ? `${Math.round(row.soLuong / categoryTotal * 100)}%` : "0%"}</strong>
              </li>
            ))}
          </ul>
        </div>}
    </section>
  );
}

function activityIcon(action = "") {
  const value = action.toLocaleLowerCase("vi");
  if (value.includes("tạo") || value.includes("thêm") || value.includes("create")) return "＋";
  if (value.includes("bảo trì") || value.includes("sửa chữa") || value.includes("maintenance")) return "⚒";
  if (value.includes("trả") || value.includes("thu hồi") || value.includes("return")) return "↔";
  return "→";
}

function alertIcon(type = "") {
  if (type === "MAINTENANCE") return "⚒";
  if (type === "WARRANTY") return "◷";
  if (type === "LOW_STOCK") return "▣";
  return "!";
}

export default function DashboardPage({ currentUser }) {
  const [dashboard, setDashboard] = useState(null);
  const [referenceDashboard, setReferenceDashboard] = useState(null);
  const [filters, setFilters] = useState(emptyFilters);
  const [appliedFilters, setAppliedFilters] = useState(emptyFilters);
  const [error, setError] = useState("");
  const [validationError, setValidationError] = useState("");
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [activities, setActivities] = useState([]);
  const [activityError, setActivityError] = useState("");
  const [activityLoading, setActivityLoading] = useState(true);
  const [showAllAlerts, setShowAllAlerts] = useState(false);

  const loadDashboard = useCallback(async (selectedFilters = emptyFilters) => {
    setLoading(true);
    setError("");
    setValidationError("");
    try {
      const params = new URLSearchParams();
      Object.entries(selectedFilters).forEach(([key, value]) => {
        if (value) params.set(key, value);
      });
      const query = params.toString();
      const data = await adminRequest(`http://localhost:8081/api/admin/dashboard${query ? `?${query}` : ""}`);
      setReferenceDashboard((current) => current ?? data);
      setDashboard(data);
      setAppliedFilters(selectedFilters);
      setLastUpdated(new Date());
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadRecentActivity = useCallback(async () => {
    setActivityLoading(true);
    setActivityError("");
    try {
      const data = await adminRequest("http://localhost:8081/api/nhat-ky");
      if (!Array.isArray(data)) throw new Error("Dữ liệu nhật ký hoạt động không hợp lệ.");
      setActivities(data.slice(0, 5));
    } catch (loadError) {
      setActivityError(loadError.message);
    } finally {
      setActivityLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDashboard();
    loadRecentActivity();
  }, [loadDashboard, loadRecentActivity]);

  const alerts = dashboard?.canhBao ?? [];
  const availableCategories = referenceDashboard?.theoLoai ?? [];
  const availableDepartments = referenceDashboard?.theoPhongBan ?? [];

  async function applyFilters(event) {
    event.preventDefault();
    if (filters.muaTuNgay && filters.muaDenNgay && filters.muaTuNgay > filters.muaDenNgay) {
      setValidationError("Ngày mua bắt đầu không được sau ngày kết thúc.");
      return;
    }
    setValidationError("");
    await loadDashboard(filters);
  }

  async function refreshOverview() {
    await Promise.all([loadDashboard(appliedFilters), loadRecentActivity()]);
  }

  function retryDashboard() {
    loadDashboard(appliedFilters);
  }

  async function clearFilters() {
    setFilters(emptyFilters);
    await loadDashboard(emptyFilters);
  }

  const categoryTotal = dashboard?.theoLoai?.reduce((sum, row) => sum + row.soLuong, 0) ?? 0;

  return (
    <>
      <section className="welcome admin-overview-header">
        <div>
          <p className="eyebrow">TRUNG TÂM ĐIỀU HÀNH</p>
          <h1>Tổng quan hệ thống</h1>
          <p className="welcome-copy">
            Chào mừng trở lại, {currentUser?.username || "Admin"}! Đây là tình hình quản lý tài sản hôm nay.
          </p>
          <p className="admin-dashboard-updated" role="status">
            {lastUpdated ? `Cập nhật lần cuối: ${dateTimeFormatter.format(lastUpdated)}` : "Chưa có dữ liệu cập nhật"}
          </p>
        </div>
        <div className="admin-overview-header-actions">
          <span className="date-chip"><span aria-hidden="true">▦</span>{todayFormatter.format(new Date())}</span>
          <button className="button secondary" type="button" onClick={refreshOverview} disabled={loading || activityLoading}>
            {loading || activityLoading ? "Đang cập nhật..." : "Làm mới"}
          </button>
        </div>
      </section>

      <details className="panel admin-overview-filters">
        <summary>Bộ lọc số liệu tổng quan</summary>
        <div className="admin-overview-filter-content">
          <p className="muted">Áp dụng cho chỉ số và biểu đồ; cảnh báo vẫn hiển thị trên toàn hệ thống.</p>
          <form className="admin-dashboard-filters" onSubmit={applyFilters}>
            <label htmlFor="dashboard-status">Trạng thái<select id="dashboard-status" value={filters.tinhTrang}
              onChange={(event) => setFilters({ ...filters, tinhTrang: event.target.value })}>
              <option value="">Tất cả trạng thái</option>
              {["Trong kho", "Đang sử dụng", "Bảo trì", "Chờ thanh lý", "Thanh lý", "Đã thanh lý", "Thiếu"].map((status) => <option key={status}>{status}</option>)}
            </select></label>
            <label htmlFor="dashboard-category">Loại tài sản<select id="dashboard-category" value={filters.loaiTaiSan}
              onChange={(event) => setFilters({ ...filters, loaiTaiSan: event.target.value })}>
              <option value="">Tất cả loại</option>
              {availableCategories.map((row) => <option key={row.nhan} value={row.nhan}>{row.nhan}</option>)}
            </select></label>
            <label htmlFor="dashboard-department">Phòng ban<select id="dashboard-department" value={filters.phongBan}
              onChange={(event) => setFilters({ ...filters, phongBan: event.target.value })}>
              <option value="">Tất cả phòng ban</option>
              {availableDepartments.map((row) => <option key={row.nhan} value={row.nhan}>{row.nhan}</option>)}
            </select></label>
            <label htmlFor="dashboard-from">Mua từ ngày<input id="dashboard-from" type="date" value={filters.muaTuNgay}
              max={filters.muaDenNgay || undefined} onChange={(event) => setFilters({ ...filters, muaTuNgay: event.target.value })} /></label>
            <label htmlFor="dashboard-to">Đến ngày<input id="dashboard-to" type="date" value={filters.muaDenNgay}
              min={filters.muaTuNgay || undefined} onChange={(event) => setFilters({ ...filters, muaDenNgay: event.target.value })} /></label>
            <div className="admin-dashboard-filter-actions">
              <button className="button" type="submit" disabled={loading}>{loading ? "Đang lọc..." : "Áp dụng bộ lọc"}</button>
              <button className="button secondary" type="button" onClick={clearFilters} disabled={loading}>Xóa bộ lọc</button>
            </div>
          </form>
          {validationError && <p className="message error admin-dashboard-error" role="alert">{validationError}</p>}
          <p className="muted admin-dashboard-result-count" role="status" aria-live="polite">
            {loading ? "Đang cập nhật số liệu..." : dashboard
              ? `Đang thống kê ${numberFormatter.format(dashboard.tongTaiSan)} tài sản phù hợp.`
              : "Chưa có số liệu để hiển thị."}
          </p>
        </div>
      </details>

      <section className="stats admin-stats admin-primary-stats" aria-label="Chỉ số tài sản chính" aria-busy={loading}>
        {error && <div className="admin-dashboard-data-error" role="alert">
          Không thể cập nhật số liệu: {error}{" "}
          <button className="retry-button" type="button" onClick={retryDashboard}>Thử lại</button>
        </div>}
        {metrics.filter(([, key]) => primaryMetrics.has(key)).map(([label, key, icon, color]) => (
          <article className={`stat admin-overview-stat ${color}`} key={key}>
            <div className="stat-top"><p>{label}</p><span className="stat-icon" aria-hidden="true">{icon}</span></div>
            {loading && !dashboard
              ? <div className="admin-stat-skeleton" aria-hidden="true"><span /><span /></div>
              : <h2>{dashboard ? numberFormatter.format(dashboard[key] ?? 0) : "—"}</h2>}
          </article>
        ))}
      </section>

      <details className="admin-extra-metrics">
        <summary>Chỉ số tài sản và tài chính khác</summary>
        <section className="stats admin-stats admin-secondary-stats" aria-label="Chỉ số tài sản bổ sung" aria-busy={loading}>
          {metrics.filter(([, key]) => !primaryMetrics.has(key)).map(([label, key, icon, color, currency]) => (
            <article className={`stat ${color}`} key={key}>
              <div className="stat-top"><p>{label}</p><span className="stat-icon" aria-hidden="true">{icon}</span></div>
              {loading && !dashboard
                ? <div className="admin-stat-skeleton" aria-hidden="true"><span /><span /></div>
                : <h2>{dashboard
                  ? currency ? currencyFormatter.format(dashboard[key] ?? 0) : numberFormatter.format(dashboard[key] ?? 0)
                  : "—"}{currency && <small> ₫</small>}</h2>}
            </article>
          ))}
        </section>
      </details>

      <section className="admin-dashboard-charts" aria-label="Phân tích tài sản" aria-busy={loading}>
        {dashboard ? <>
          <CategoryPanel rows={dashboard.theoLoai ?? []} total={categoryTotal} />
          <DistributionPanel title="Tình trạng tài sản" description="Số lượng theo tình trạng hiện tại" rows={dashboard.theoTrangThai ?? []} />
        </> : loading ? [0, 1].map((item) => (
          <section className="panel admin-distribution-panel admin-chart-skeleton" key={item} aria-label="Đang tải biểu đồ">
            <div className="admin-skeleton-line" />
            <div className="admin-skeleton-line short" />
            {[0, 1, 2, 3].map((row) => <div className="admin-skeleton-chart-row" key={row}><span /><i /></div>)}
          </section>
        )) : <section className="panel admin-dashboard-chart-error" role="alert">
          <p>{error ? `Không thể tải biểu đồ: ${error}` : "Chưa có dữ liệu biểu đồ."}</p>
          <button className="retry-button" type="button" onClick={retryDashboard}>Thử lại</button>
        </section>}
      </section>

      {dashboard && <details className="admin-department-details">
        <summary>Xem phân bổ tài sản theo phòng ban</summary>
        <DistributionPanel title="Tài sản theo phòng ban" description="Tài sản đang được các phòng ban quản lý" rows={dashboard.theoPhongBan ?? []} />
      </details>}

      <section className="admin-overview-bottom">
        <section className="panel admin-activity-panel">
          <div className="panel-heading">
            <div><h2>Hoạt động gần đây</h2><p className="muted">Các thao tác mới nhất trên hệ thống</p></div>
            <a className="admin-overview-link" href="/admin/activity">Xem tất cả <span aria-hidden="true">→</span></a>
          </div>
          {activityLoading ? <p className="notification-empty" role="status">Đang tải hoạt động...</p>
            : activityError ? <p className="notification-empty" role="alert">
              Không thể tải hoạt động: {activityError}{" "}
              <button className="retry-button" type="button" onClick={loadRecentActivity}>Thử lại</button>
            </p>
              : activities.length === 0 ? <p className="notification-empty">Chưa có hoạt động được ghi nhận.</p>
                : <ul className="admin-overview-activity-list">{activities.map((activity) => (
                  <li key={activity.id}>
                    <span className="admin-overview-activity-icon" aria-hidden="true">{activityIcon(activity.action)}</span>
                    <div className="admin-overview-activity-copy">
                      <strong>{activity.action} · {activity.entityType}{activity.entityId ? ` #${activity.entityId}` : ""}</strong>
                      <span>{activity.actor || "Người dùng"}</span>
                    </div>
                    <time dateTime={activity.createdAt}>{activity.createdAt ? dateTimeFormatter.format(new Date(activity.createdAt)) : "—"}</time>
                  </li>
                ))}</ul>}
        </section>

        <section className="panel admin-alerts-panel">
          <div className="panel-heading">
            <div><h2>Tài sản cần chú ý</h2><p className="muted">Cảnh báo bảo trì, bảo hành, thu hồi và tồn kho</p></div>
            <span className="asset-count">{numberFormatter.format(alerts.length)} cảnh báo</span>
          </div>
          {loading && !dashboard ? <p className="admin-alert-skeleton" role="status">Đang tải cảnh báo...</p>
            : error && !dashboard ? <p className="notification-empty" role="alert">
              Không thể tải cảnh báo: {error} <button className="retry-button" type="button" onClick={retryDashboard}>Thử lại</button>
            </p>
              : alerts.length === 0 ? <p className="notification-empty">Không có cảnh báo cần xử lý.</p>
                : <ul className="admin-alert-list">{(showAllAlerts ? alerts : alerts.slice(0, 4)).map((alert, index) => (
                  <li className={`admin-alert-item ${alert.mucDo?.toLowerCase() ?? ""}`} key={`${alert.loai}-${index}`}>
                    <span className="admin-alert-marker" aria-hidden="true">{alertIcon(alert.loai)}</span>
                    <div><strong>{alert.tieuDe}</strong><p>{formatAlertDescription(alert.moTa)}</p>
                      {alert.actionUrl && <a className="link-button" href={alert.actionUrl}>Mở xử lý →</a>}
                    </div>
                    <span className="status">{alert.loai}</span>
                  </li>
                ))}</ul>}
          {alerts.length > 4 && <button className="text-button admin-overview-alert-toggle" type="button"
            aria-expanded={showAllAlerts} onClick={() => setShowAllAlerts((value) => !value)}>
            {showAllAlerts ? "Thu gọn" : `Xem thêm ${numberFormatter.format(alerts.length - 4)} cảnh báo`}
          </button>}
        </section>
      </section>
    </>
  );
}
