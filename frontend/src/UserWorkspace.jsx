import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ChangePasswordDialog from "./ChangePasswordDialog.jsx";
import { fetchWithSession } from "./sessionFetch.js";

const API_URL = "http://localhost:8081/api";
const navigation = [
  {
    id: "overview",
    label: "TỔNG QUAN",
    links: [["overview", "▦", "Tổng quan"]],
  },
  {
    id: "work",
    label: "CÔNG VIỆC",
    links: [
      ["assets", "▤", "Tài sản của tôi"],
      ["maintenance", "⚒", "Báo hỏng / bảo trì"],
      ["consumables", "▣", "Vật tư tiêu hao"],
      ["purchases", "＋", "Mua sắm"],
    ],
  },
  {
    id: "account",
    label: "TÀI KHOẢN",
    links: [["profile", "♙", "Thông tin cá nhân"]],
  },
];
const formatDate = (value) => value ? new Date(value).toLocaleDateString("vi-VN") : "—";
const formatPurchaseReviewNote = (note, status) => note
  && !["hoàn thành", "hoan thanh", "completed", "done"].includes(note.trim().toLocaleLowerCase("vi"))
  ? note
  : status === "APPROVED" || status === "COMPLETED" ? "Đã duyệt" : status === "REJECTED" ? "Đã từ chối" : "—";
const formatPurchaseStatus = (request) => {
  if (request.status === "CANCELLED") return "Đã hủy";
  if (request.status === "REJECTED") return "Từ chối";
  if (request.status === "PENDING") return "Chờ duyệt";
  if (request.status === "COMPLETED" || request.purchaseOrderStatus === "DA_NHAN") {
    return "Hoàn tất · đã nhận đủ";
  }
  if (request.purchaseOrderStatus === "NHAN_MOT_PHAN") return "Đã thành đơn mua · đã nhận một phần";
  if (request.purchaseOrderNumber) return "Đã thành đơn mua · chờ nhận hàng";
  if (request.status === "APPROVED") return "Đã duyệt · chờ tạo đơn mua";
  return request.status;
};

async function userRequest(path, options = {}) {
  const headers = new Headers(options.headers);
  const token = sessionStorage.getItem("officecare.accessToken");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const url = `${API_URL}${path}`;
  const response = await fetchWithSession(url, { ...options, headers });
  if (!response.ok) {
    let message = `Yêu cầu thất bại (HTTP ${response.status}).`;
    try {
      const body = await response.json();
      message = body.detail || body.message || message;
    } catch {
      // Preserve the HTTP status when the server does not return JSON.
    }
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
        detail: {
          url,
          method: options.method || "GET",
          status: response.status,
          message,
        },
      }));
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

export default function UserWorkspace({ currentUser, onLogout, theme, onToggleTheme }) {
  const [activePage, setActivePage] = useState("overview");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [openNavGroup, setOpenNavGroup] = useState(null);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const navigationRef = useRef(null);
  const accountMenuRef = useRef(null);
  const accountTriggerRef = useRef(null);
  const [assets, setAssets] = useState([]);
  const [maintenance, setMaintenance] = useState([]);
  const [returnRequests, setReturnRequests] = useState([]);
  const [purchaseRequests, setPurchaseRequests] = useState([]);
  const [cancellingPurchaseRequestId, setCancellingPurchaseRequestId] = useState(null);
  const [categories, setCategories] = useState([]);
  const [consumables, setConsumables] = useState([]);
  const [consumableRequests, setConsumableRequests] = useState([]);
  const [profile, setProfile] = useState(null);
  const [selectedAsset, setSelectedAsset] = useState(null);
  const [returnTarget, setReturnTarget] = useState(null);
  const [assetSearch, setAssetSearch] = useState("");
  const [assetCategory, setAssetCategory] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [pageLoading, setPageLoading] = useState(false);
  const pageLoadingTimer = useRef(null);

  const navigateToPage = useCallback((page) => {
    if (pageLoadingTimer.current) window.clearTimeout(pageLoadingTimer.current);
    setPageLoading(true);
    setActivePage(page);
    setSelectedAsset(null);
    setMobileNavOpen(false);
    setOpenNavGroup(null);
    window.scrollTo({ top: 0 });
    pageLoadingTimer.current = window.setTimeout(() => {
      setPageLoading(false);
      pageLoadingTimer.current = null;
    }, 450);
  }, []);

  useEffect(() => () => {
    if (pageLoadingTimer.current) window.clearTimeout(pageLoadingTimer.current);
  }, []);

  useEffect(() => {
    if (!mobileNavOpen && !openNavGroup && !accountMenuOpen) return undefined;
    function closeMenus(event) {
      if (event.type === "keydown") {
        if (event.key !== "Escape") return;
        if (mobileNavOpen) setMobileNavOpen(false);
        if (openNavGroup) {
          navigationRef.current?.querySelector(`[data-employee-nav-trigger="${openNavGroup}"]`)?.focus();
          setOpenNavGroup(null);
        }
        if (accountMenuOpen) {
          accountTriggerRef.current?.focus();
          setAccountMenuOpen(false);
        }
        return;
      }
      if (!navigationRef.current?.contains(event.target)) {
        setMobileNavOpen(false);
        setOpenNavGroup(null);
      }
      if (!accountMenuRef.current?.contains(event.target)) setAccountMenuOpen(false);
    }
    document.addEventListener("pointerdown", closeMenus);
    document.addEventListener("keydown", closeMenus);
    return () => {
      document.removeEventListener("pointerdown", closeMenus);
      document.removeEventListener("keydown", closeMenus);
    };
  }, [mobileNavOpen, openNavGroup, accountMenuOpen]);

  const loadWorkspace = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [assetRows, maintenanceRows, purchaseRows, categoryRows, profileData, consumableRows, consumableRequestRows, returnRequestRows] = await Promise.all([
        userRequest("/me/assets"),
        userRequest("/me/maintenance"),
        userRequest("/me/purchase-requests"),
        userRequest("/me/categories"),
        userRequest("/auth/profile"),
        userRequest("/me/consumables"),
        userRequest("/me/consumables/requests"),
        userRequest("/me/return-requests"),
      ]);
      setAssets(assetRows);
      setMaintenance(maintenanceRows);
      setPurchaseRequests(purchaseRows);
      setCategories(categoryRows);
      setProfile(profileData);
      setConsumables(consumableRows);
      setConsumableRequests(consumableRequestRows);
      setReturnRequests(returnRequestRows);
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadWorkspace(); }, [loadWorkspace]);

  const activeMaintenance = useMemo(() =>
    maintenance.filter((item) => ["YEU_CAU", "DANG_XU_LY"].includes(item.trangThai)), [maintenance]);
  const assetCategories = useMemo(() =>
    [...new Set(assets.map((asset) => asset.tenLoaiTaiSan).filter(Boolean))].sort((a, b) => a.localeCompare(b, "vi")),
  [assets]);
  const filteredAssets = useMemo(() => {
    const query = assetSearch.trim().toLocaleLowerCase("vi");
    return assets.filter((asset) => {
      const matchesSearch = !query || [asset.maTaiSan, asset.tenTaiSan, asset.serialNumber]
        .some((value) => value?.toLocaleLowerCase("vi").includes(query));
      return matchesSearch && (!assetCategory || asset.tenLoaiTaiSan === assetCategory);
    });
  }, [assets, assetSearch, assetCategory]);
  const assetsUnderMaintenance = useMemo(() =>
    assets.filter((asset) => asset.tinhTrang === "Bảo trì"), [assets]);
  const warnings = useMemo(() => {
    const today = new Date();
    const soon = new Date(today);
    soon.setDate(today.getDate() + 30);
    const dueReturns = assets.filter((asset) => asset.ngayDuKienThuHoi
      && new Date(`${asset.ngayDuKienThuHoi}T00:00:00`) <= soon);
    return { dueReturns, activeMaintenance, assetsUnderMaintenance };
  }, [assets, activeMaintenance, assetsUnderMaintenance]);

  async function submitMaintenance(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSaving(true);
    setError("");
    setMessage("");
    try {
      await userRequest("/me/maintenance", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          taiSanId: Number(data.get("taiSanId")),
          tieuDe: String(data.get("tieuDe")).trim(),
          moTa: String(data.get("moTa") ?? "").trim(),
          anhSuCo: String(data.get("anhSuCo") ?? "").trim() || null,
          mucDo: String(data.get("mucDo")),
        }),
      });
      form.reset();
      await loadWorkspace();
      setMessage("Đã gửi yêu cầu. Bạn có thể theo dõi tiến độ tại đây.");
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }

    async function submitReturnRequest(event) {
      event.preventDefault();
      if (!returnTarget) return;
      const form = event.currentTarget;
      const data = new FormData(form);
      setSaving(true);
      setError("");
      setMessage("");
      try {
        const created = await userRequest("/me/return-requests", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            assignmentId: returnTarget.assignmentId,
            reason: String(data.get("reason")).trim(),
          }),
        });
        setReturnRequests((current) => [created, ...current]);
        setReturnTarget(null);
        setMessage("Đã gửi yêu cầu trả tài sản. Admin sẽ lập phiếu thu hồi.");
      } catch (saveError) {
        setError(saveError.message);
      } finally {
        setSaving(false);
      }
    }
  }

  async function submitPurchaseRequest(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSaving(true);
    setError("");
    setMessage("");
    try {
      await userRequest("/me/purchase-requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          assetName: String(data.get("assetName")).trim(),
          categoryId: Number(data.get("categoryId")),
          quantity: Number(data.get("quantity")),
          reason: String(data.get("reason")).trim(),
          estimatedAmount: Number(data.get("estimatedAmount")),
        }),
      });
      form.reset();
      await loadWorkspace();
      setMessage("Đã gửi yêu cầu mua sắm để quản trị viên xem xét.");
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function refreshPurchaseRequests() {
    setError("");
    try {
      setPurchaseRequests(await userRequest("/me/purchase-requests"));
    } catch (loadError) {
      setError(loadError.message);
    }
  }

  async function cancelPurchaseRequest(request) {
    if (request.status !== "PENDING" || !window.confirm("Hủy yêu cầu mua sắm này?")) return;
    setCancellingPurchaseRequestId(request.id);
    setError("");
    setMessage("");
    try {
      const updated = await userRequest(`/me/purchase-requests/${request.id}/cancel`, { method: "PUT" });
      setPurchaseRequests((current) => current.map((row) => row.id === updated.id ? updated : row));
      setMessage("Đã hủy yêu cầu mua sắm.");
    } catch (cancelError) {
      setError(cancelError.message);
    } finally {
      setCancellingPurchaseRequestId(null);
    }
  }

  async function submitConsumableRequest(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSaving(true);
    setError("");
    setMessage("");
    try {
      await userRequest("/me/consumables/requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          vatTuId: Number(data.get("vatTuId")),
          soLuong: Number(data.get("soLuong")),
          lyDo: String(data.get("lyDo")).trim(),
        }),
      });
      form.reset();
      await loadWorkspace();
      setMessage("Đã gửi yêu cầu lấy vật tư để quản trị viên xem xét.");
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function saveProfile(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setSaving(true);
    setError("");
    setMessage("");
    try {
      setProfile(await userRequest("/auth/profile", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: String(data.get("email") ?? "").trim() || null,
          phoneNumber: String(data.get("phoneNumber") ?? "").trim() || null,
        }),
      }));
      setMessage("Đã cập nhật thông tin liên hệ.");
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  const pageTitle = navigation.flatMap((group) => group.links)
    .find(([key]) => key === activePage)?.[2] ?? "Tổng quan";

  return (
    <div className={`layout employee-layout${mobileNavOpen ? " mobile-nav-open" : ""}`}>
      {mobileNavOpen && <button className="employee-nav-backdrop" type="button" aria-label="Đóng menu điều hướng" onClick={() => setMobileNavOpen(false)} />}
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="OfficeCare">
          <span className="brand-mark" aria-hidden="true">✦</span>
          <span>OfficeCare<small>Không gian nhân viên</small></span>
        </a>
        <button className="employee-menu-toggle" type="button" aria-label={mobileNavOpen ? "Đóng menu" : "Mở menu"}
          aria-expanded={mobileNavOpen} aria-controls="employee-navigation" onClick={() => setMobileNavOpen((open) => !open)}>
          <span aria-hidden="true">{mobileNavOpen ? "×" : "☰"}</span>
        </button>
        <nav id="employee-navigation" className="navigation employee-navigation" aria-label="Điều hướng nhân viên" ref={navigationRef}>
          {navigation.map((group) => {
            const groupIsActive = group.links.some(([key]) => key === activePage);
            const menuId = `employee-nav-menu-${group.id}`;
            const groupIsOpen = openNavGroup === group.id;
            if (group.links.length === 1) {
              const [key, icon, label] = group.links[0];
              return (
                <div className="employee-nav-group" key={group.id}>
                  <button type="button" className={`employee-nav-link employee-nav-direct${groupIsActive ? " active" : ""}`}
                    aria-current={activePage === key ? "page" : undefined}
                    onClick={() => navigateToPage(key)}>
                    <span className="nav-icon" aria-hidden="true">{icon}</span>{label}
                  </button>
                </div>
              );
            }
            return (
              <div className={`employee-nav-group${groupIsOpen ? " open" : ""}`} key={group.id}
                onMouseEnter={() => setOpenNavGroup(group.id)}
                onMouseLeave={() => setOpenNavGroup((current) => current === group.id ? null : current)}
                onFocus={() => setOpenNavGroup(group.id)}
                onBlur={(event) => {
                  if (!event.currentTarget.contains(event.relatedTarget)) {
                    setOpenNavGroup((current) => current === group.id ? null : current);
                  }
                }}>
                <button className={`employee-nav-trigger${groupIsActive ? " active" : ""}`} type="button"
                  data-employee-nav-trigger={group.id} aria-expanded={groupIsOpen} aria-controls={menuId}
                  onClick={() => setOpenNavGroup(group.id)}>
                  {group.label}<span aria-hidden="true">⌄</span>
                </button>
                <div className="employee-nav-menu" id={menuId} hidden={!groupIsOpen}>
                  {group.links.map(([key, icon, label]) => (
                    <button key={key} type="button"
                      className={`employee-nav-link${activePage === key ? " active" : ""}`}
                      aria-current={activePage === key ? "page" : undefined}
                      onClick={() => navigateToPage(key)}>
                      <span className="nav-icon" aria-hidden="true">{icon}</span>{label}
                    </button>
                  ))}
                </div>
              </div>
            );
          })}
        </nav>
        <div className="employee-topbar-actions">
          <button className="toolbar-icon" type="button" onClick={onToggleTheme}
            aria-label={theme === "dark" ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"}
            title={theme === "dark" ? "Giao diện sáng" : "Giao diện tối"}>{theme === "dark" ? "☀" : "☾"}</button>
          <div className="employee-account-menu-wrap" ref={accountMenuRef}>
            <button className="employee-account-trigger" type="button" ref={accountTriggerRef}
              onClick={() => setAccountMenuOpen((open) => !open)} aria-expanded={accountMenuOpen}
              aria-controls="employee-account-menu" aria-label={`Tài khoản ${currentUser.username}`}>
              <span className="avatar">{currentUser.username.slice(0, 2).toLocaleUpperCase("vi")}</span>
              <span className="employee-account-name">{currentUser.username}</span>
              <span aria-hidden="true">⌄</span>
            </button>
            <div className="employee-account-menu" id="employee-account-menu" hidden={!accountMenuOpen}>
              <div className="employee-account-identity"><strong>{currentUser.username}</strong><span>Nhân viên</span></div>
              <button type="button" onClick={() => { setAccountMenuOpen(false); setPasswordOpen(true); }}>Đổi mật khẩu</button>
              <button type="button" onClick={onLogout}>Đăng xuất</button>
            </div>
          </div>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div className="breadcrumb">OfficeCare <span>/</span> <strong>{pageTitle}</strong></div>
          {pageLoading && <span className="page-loading-indicator" role="status" aria-live="polite">
            <span className="page-loading-spinner" aria-hidden="true" />
            <span>Đang tải...</span>
          </span>}
        </header>
        <div className="dashboard employee-dashboard page-transition-content" aria-busy={pageLoading}>
          <section className="welcome">
            <div><p className="eyebrow">OFFICECARE · NHÂN VIÊN</p><h1>{pageTitle}</h1>
              <p className="welcome-copy">Xin chào {currentUser.username}. Tại đây bạn chỉ xem và gửi yêu cầu trong phạm vi của mình.</p></div>
            <button className="button secondary" type="button" onClick={loadWorkspace} disabled={loading}>{loading ? "Đang tải..." : "Làm mới"}</button>
          </section>
          {error && <p className="message error" role="alert">{error}</p>}
          {message && <p className="message" role="status">{message}</p>}

          {activePage === "overview" && <>
            <section className="stats">
              <article className="stat stat-purple"><div className="stat-top"><p>Tài sản đang giữ</p><span className="stat-icon">▤</span></div><h2>{loading ? "…" : assets.length}</h2><p className="stat-caption">Được bàn giao cho tài khoản của bạn</p></article>
              <article className="stat stat-orange"><div className="stat-top"><p>Sắp đến hạn trả</p><span className="stat-icon">↩</span></div><h2>{loading ? "…" : warnings.dueReturns.length}</h2><p className="stat-caption">Trong 30 ngày tới hoặc đã quá hạn</p></article>
              <article className="stat stat-blue"><div className="stat-top"><p>Đang bảo trì</p><span className="stat-icon">⚒</span></div><h2>{loading ? "…" : warnings.assetsUnderMaintenance.length}</h2><p className="stat-caption">Tài sản của bạn đang sửa chữa</p></article>
            </section>
            <section className="panel admin-alerts-panel">
              <div className="panel-heading"><div><h2>Cảnh báo của bạn</h2><p className="muted">Chỉ bao gồm tài sản và yêu cầu liên quan đến bạn.</p></div></div>
              {warnings.dueReturns.length === 0 && warnings.assetsUnderMaintenance.length === 0
                ? <p className="notification-empty">Hiện không có cảnh báo cần xử lý.</p>
                : <ul className="admin-alert-list">
                  {warnings.dueReturns.map((asset) => <li className="admin-alert-item medium" key={`return-${asset.taiSanId}`}>
                    <span className="admin-alert-marker">!</span><div><strong>{asset.tenTaiSan} · {asset.maTaiSan}</strong><p>Ngày dự kiến trả: {formatDate(asset.ngayDuKienThuHoi)}</p></div>
                    <button className="link-button" type="button" onClick={() => navigateToPage("assets")}>Xem tài sản</button>
                  </li>)}
                  {warnings.assetsUnderMaintenance.map((asset) => {
                    const ticket = warnings.activeMaintenance.find((item) => item.taiSanId === asset.taiSanId);
                    return <li className="admin-alert-item medium" key={`maintenance-${asset.taiSanId}`}>
                    <span className="admin-alert-marker">!</span><div><strong>Đang bảo trì: {asset.tenTaiSan}</strong><p>{ticket ? `${ticket.tieuDe} · ${ticket.trangThai === "YEU_CAU" ? "Đã gửi" : "Đang xử lý"}` : "Thiết bị đang được sửa chữa."}</p></div>
                    <button className="link-button" type="button" onClick={() => navigateToPage("maintenance")}>Theo dõi</button>
                  </li>;
                  })}
                </ul>}
            </section>
          </>}

          {activePage === "assets" && <section className="panel assets-panel">
            <div className="panel-heading assets-heading"><div><h2>Tài sản được giao cho tôi</h2><p className="muted">Chỉ có thể xem chi tiết; bạn không thể sửa hoặc thay đổi trạng thái.</p></div><span className="asset-count">{assets.length} tài sản</span></div>
            {selectedAsset ? <section className="admin-asset-history">
              <div className="panel-heading"><div><p className="eyebrow">CHI TIẾT TÀI SẢN</p><h2>{selectedAsset.maTaiSan} · {selectedAsset.tenTaiSan}</h2></div><button className="button secondary" type="button" onClick={() => setSelectedAsset(null)}>Quay lại</button></div>
              <dl className="detail-grid">
                <div><dt>Serial</dt><dd>{selectedAsset.serialNumber || "—"}</dd></div><div><dt>Hãng / Model</dt><dd>{[selectedAsset.brand, selectedAsset.model].filter(Boolean).join(" / ") || "—"}</dd></div>
                <div><dt>Vị trí</dt><dd>{[selectedAsset.toaNha, selectedAsset.tang && `Tầng ${selectedAsset.tang}`, selectedAsset.phong && `Phòng ${selectedAsset.phong}`].filter(Boolean).join(" · ") || "—"}</dd></div>
                <div><dt>Ngày nhận</dt><dd>{formatDate(selectedAsset.ngayNhan)}</dd></div><div><dt>Bảo hành đến</dt><dd>{formatDate(selectedAsset.warrantyEndDate)}</dd></div><div><dt>Trạng thái</dt><dd>{selectedAsset.tinhTrang}</dd></div>
              </dl>
              <h3>Lịch sử bảo trì</h3>
              {selectedAsset.lichSuBaoTri.length === 0 ? <p className="notification-empty">Chưa có lịch sử bảo trì.</p>
                : <div className="table-wrap"><table><thead><tr><th>YÊU CẦU</th><th>MÔ TẢ</th><th>NGÀY GỬI</th><th>TRẠNG THÁI</th><th>KẾT QUẢ</th><th>ẢNH</th></tr></thead><tbody>
                  {selectedAsset.lichSuBaoTri.map((item) => <tr key={item.id}><td>{item.tieuDe}</td><td>{item.moTa || "—"}</td><td>{formatDate(item.taoLuc)}</td><td>{item.trangThai === "YEU_CAU" ? "Chờ xử lý" : item.trangThai === "DANG_XU_LY" ? "Đang sửa" : item.trangThai === "HOAN_TAT" ? "Hoàn thành" : item.trangThai === "KHONG_SUA_DUOC" ? "Không sửa được" : "Đã hủy"}</td><td>{item.ketQua || "—"}</td><td>{item.anhSuCo ? <a href={item.anhSuCo} target="_blank" rel="noreferrer">Xem ảnh</a> : "—"}</td></tr>)}
                </tbody></table></div>}
            </section> : <>
              <div className="form-grid employee-asset-filters">
                <label className="field">Tìm theo mã, tên hoặc serial
                  <input type="search" value={assetSearch} onChange={(event) => setAssetSearch(event.target.value)} />
                </label>
                <label className="field">Loại tài sản
                  <select value={assetCategory} onChange={(event) => setAssetCategory(event.target.value)}>
                    <option value="">Tất cả loại</option>
                    {assetCategories.map((category) => <option key={category} value={category}>{category}</option>)}
                  </select>
                </label>
              </div>
              <div className="table-wrap"><table><thead><tr><th>MÃ</th><th>TÊN TÀI SẢN</th><th>LOẠI</th><th>SERIAL</th><th>VỊ TRÍ</th><th>NGÀY NHẬN</th><th>TRẠNG THÁI</th><th></th></tr></thead><tbody>
              {loading ? <tr><td className="empty" colSpan="8">Đang tải tài sản...</td></tr>
                : filteredAssets.length === 0 ? <tr><td className="empty" colSpan="8">{assets.length === 0 ? "Chưa có tài sản được bàn giao cho tài khoản này." : "Không tìm thấy tài sản phù hợp bộ lọc."}</td></tr>
                  : filteredAssets.map((asset) => <tr key={asset.taiSanId}><td className="code">{asset.maTaiSan}</td><td>{asset.tenTaiSan}</td><td>{asset.tenLoaiTaiSan || "—"}</td><td>{asset.serialNumber || "—"}</td><td>{[asset.toaNha, asset.tang && `Tầng ${asset.tang}`, asset.phong && `Phòng ${asset.phong}`].filter(Boolean).join(" · ") || "—"}</td><td>{formatDate(asset.ngayNhan)}</td><td>{asset.tinhTrang}</td><td><button className="link-button" type="button" onClick={() => setSelectedAsset(asset)}>Chi tiết</button>
                    {!returnRequests.some((request) => request.assignmentId === asset.assignmentId && request.status === "PENDING")
                      && <button className="link-button" type="button" onClick={() => setReturnTarget(asset)}>Yêu cầu trả</button>}
                  </td></tr>)}
            </tbody></table></div>
              {returnTarget && <section className="panel admin-resource-form">
                <div className="panel-heading"><div><h3>Yêu cầu trả tài sản</h3><p className="muted">{returnTarget.maTaiSan} · {returnTarget.tenTaiSan}</p></div>
                  <button className="button secondary" type="button" onClick={() => setReturnTarget(null)}>Đóng</button></div>
                <form className="form-grid" onSubmit={submitReturnRequest}>
                  <label className="field employee-wide">Lý do trả (nghỉ việc, chuyển phòng...)<textarea name="reason" required maxLength="500" rows="3" /></label>
                  <div className="form-actions employee-wide"><button className="button" type="submit" disabled={saving}>{saving ? "Đang gửi..." : "Gửi yêu cầu trả"}</button></div>
                </form>
              </section>}
              <section className="panel assets-panel">
                <div className="panel-heading"><div><h3>Yêu cầu trả tài sản của tôi</h3><p className="muted">Admin sẽ lập phiếu thu hồi sau khi tiếp nhận.</p></div></div>
                {returnRequests.length === 0 ? <p className="notification-empty">Bạn chưa gửi yêu cầu trả tài sản.</p>
                  : <div className="table-wrap"><table><thead><tr><th>NGÀY GỬI</th><th>TÀI SẢN</th><th>LÝ DO</th><th>TRẠNG THÁI</th></tr></thead><tbody>
                    {returnRequests.map((request) => <tr key={request.id}><td>{formatDate(request.createdAt)}</td><td>{request.maTaiSan} · {request.tenTaiSan}</td><td>{request.reason}</td><td>{request.status === "COMPLETED" ? "Đã thu hồi" : "Chờ admin lập phiếu thu hồi"}</td></tr>)}
                  </tbody></table></div>}
              </section>
            </>}
          </section>}

          {activePage === "maintenance" && <div className="employee-request-grid">
            <section className="panel admin-resource-form"><div className="panel-heading"><div><h2>Gửi yêu cầu sửa chữa / báo hỏng</h2><p className="muted">Chỉ chọn tài sản đang được giao cho bạn.</p></div></div>
              <form className="form-grid" onSubmit={submitMaintenance}>
                <label className="field">Tài sản<select name="taiSanId" required defaultValue=""><option value="" disabled>Chọn tài sản</option>{assets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan}</option>)}</select></label>
                <label className="field">Tiêu đề<input name="tieuDe" required maxLength="150" placeholder="Ví dụ: Máy không khởi động" /></label>
                <label className="field">Mức độ
                  <select name="mucDo" defaultValue="TRUNG_BINH">
                    <option value="NHE">Nhẹ</option>
                    <option value="TRUNG_BINH">Trung bình</option>
                    <option value="NGHIEM_TRONG">Nghiêm trọng</option>
                  </select>
                </label>
                <label className="field employee-wide">Mô tả lỗi<textarea name="moTa" rows="3" maxLength="1000" required placeholder="Mô tả dấu hiệu, thời điểm và tình trạng..." /></label>
                <label className="field employee-wide">Liên kết ảnh (nếu có)<input name="anhSuCo" type="url" maxLength="1000" placeholder="https://..." /></label>
                <div className="form-actions employee-wide"><button className="button" type="submit" disabled={saving || !assets.length}>{saving ? "Đang gửi..." : "Gửi yêu cầu"}</button></div>
              </form>
            </section>
            <section className="panel assets-panel"><div className="panel-heading"><div><h2>Yêu cầu của tôi</h2><p className="muted">Chỉ xem tiến độ, không chỉnh sửa phiếu.</p></div></div>
              {maintenance.length === 0 ? <p className="notification-empty">Bạn chưa gửi yêu cầu nào.</p> : <div className="table-wrap"><table><thead><tr><th>MÃ PHIẾU</th><th>TÀI SẢN</th><th>YÊU CẦU / MỨC ĐỘ</th><th>NGÀY BÁO</th><th>TRẠNG THÁI</th><th>KẾT QUẢ</th></tr></thead><tbody>
                {maintenance.map((item) => <tr key={item.id}><td className="code">{item.maPhieu || `SC-${String(item.id).padStart(6, "0")}`}</td><td>{item.maTaiSan} · {item.tenTaiSan}</td><td><strong>{item.tieuDe}</strong><br />{item.moTa}<br /><span className="muted">Mức độ: {item.mucDo === "NHE" ? "Nhẹ" : item.mucDo === "NGHIEM_TRONG" ? "Nghiêm trọng" : "Trung bình"}</span></td><td>{formatDate(item.ngayBaoHong || item.taoLuc)}</td><td>{item.trangThai === "YEU_CAU" ? "Chờ xử lý" : item.trangThai === "DANG_XU_LY" ? "Đang sửa" : item.trangThai === "HOAN_TAT" ? "Hoàn thành" : item.trangThai === "KHONG_SUA_DUOC" ? "Không sửa được" : "Đã hủy"}</td><td>{item.ketQua || item.noiXuLy || "—"}</td></tr>)}
              </tbody></table></div>}
            </section>
          </div>}

          {activePage === "consumables" && <div className="employee-request-grid">
            <section className="panel admin-resource-form"><div className="panel-heading"><div><h2>Gửi yêu cầu lấy vật tư</h2><p className="muted">Yêu cầu sẽ chờ admin duyệt; số tồn không thay đổi cho đến khi vật tư được xuất.</p></div></div>
              <form className="form-grid" onSubmit={submitConsumableRequest}>
                <label className="field">Vật tư<select name="vatTuId" required defaultValue=""><option value="" disabled>Chọn vật tư</option>{consumables.map((item) => <option key={item.id} value={item.id}>{item.maVatTu} · {item.tenVatTu} (còn {item.soLuongTon} {item.donVi})</option>)}</select></label>
                <label className="field">Số lượng<input name="soLuong" type="number" min="0.01" step="0.01" required /></label>
                <label className="field employee-wide">Lý do<textarea name="lyDo" required maxLength="500" rows="3" placeholder="Nêu mục đích sử dụng vật tư..." /></label>
                <div className="form-actions employee-wide"><button className="button" type="submit" disabled={saving || !consumables.length}>{saving ? "Đang gửi..." : "Gửi yêu cầu"}</button></div>
              </form>
            </section>
            <section className="panel assets-panel"><div className="panel-heading"><div><h2>Tồn kho vật tư</h2><p className="muted">Danh sách chỉ đọc; vui lòng gửi yêu cầu để nhận vật tư.</p></div></div>
              <div className="table-wrap"><table><thead><tr><th>MÃ VẬT TƯ</th><th>TÊN</th><th>ĐƠN VỊ</th><th>TỒN HIỆN TẠI</th><th>MỨC TỐI THIỂU</th><th>TRẠNG THÁI</th></tr></thead><tbody>
                {loading ? <tr><td className="empty" colSpan="6">Đang tải tồn kho...</td></tr>
                  : consumables.length === 0 ? <tr><td className="empty" colSpan="6">Hiện chưa có vật tư được sử dụng.</td></tr>
                    : consumables.map((item) => <tr key={item.id}><td className="code">{item.maVatTu}</td><td>{item.tenVatTu}</td><td>{item.donVi}</td><td>{item.soLuongTon}</td><td>{item.mucToiThieu}</td><td>{Number(item.soLuongTon) <= 0 ? "Hết" : Number(item.soLuongTon) <= Number(item.mucToiThieu) ? "Sắp hết" : "Đủ"}</td></tr>)}
              </tbody></table></div>
            </section>
            <section className="panel assets-panel"><div className="panel-heading"><div><h2>Yêu cầu vật tư của tôi</h2><p className="muted">Theo dõi trạng thái xử lý.</p></div></div>
              {consumableRequests.length === 0 ? <p className="notification-empty">Bạn chưa gửi yêu cầu vật tư nào.</p> : <div className="table-wrap"><table><thead><tr><th>NGÀY GỬI</th><th>VẬT TƯ</th><th>SỐ LƯỢNG</th><th>LÝ DO</th><th>TRẠNG THÁI</th><th>GHI CHÚ DUYỆT</th></tr></thead><tbody>
                {consumableRequests.map((item) => <tr key={item.id}><td>{formatDate(item.taoLuc)}</td><td>{item.maVatTu} · {item.tenVatTu}</td><td>{item.soLuong} {item.donVi}</td><td>{item.lyDo}</td><td>{item.trangThai === "PENDING" ? "Chờ duyệt" : item.trangThai === "ISSUED" ? "Đã xuất" : "Từ chối"}</td><td>{item.ghiChuDuyet || "—"}</td></tr>)}
              </tbody></table></div>}
            </section>
          </div>}

          {activePage === "purchases" && <div className="employee-request-grid">
            <section className="panel admin-resource-form"><div className="panel-heading"><div><h2>Mua sắm</h2><p className="muted">Gửi yêu cầu mua sắm và theo dõi quyết định của admin. Nhân viên không tự tạo đơn mua.</p></div></div>
              <form className="form-grid" onSubmit={submitPurchaseRequest}>
                <label className="field">Tên thiết bị / cấu hình<input name="assetName" required maxLength="150" placeholder="Ví dụ: Màn hình 24 inch, 120 Hz" /></label>
                <label className="field">Loại tài sản (chọn theo danh mục)<select name="categoryId" required defaultValue=""><option value="" disabled>Chọn loại tài sản</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
                <label className="field">Số lượng<input name="quantity" type="number" min="1" max="1000" required defaultValue="1" /></label>
                <label className="field">Tổng dự toán (VNĐ)<input name="estimatedAmount" type="number" min="1000" max="9999999999999.99" step="1000" required placeholder="Ví dụ: 5000000" /><span className="muted">Nhập tổng tiền bằng đồng, không nhập số triệu; tối thiểu 1.000 ₫.</span></label>
                <label className="field employee-wide">Lý do<textarea name="reason" required maxLength="1000" rows="3" placeholder="Nêu nhu cầu công việc và lý do đề xuất..." /></label>
                <div className="form-actions employee-wide"><button className="button" type="submit" disabled={saving || !categories.length}>{saving ? "Đang gửi..." : "Gửi yêu cầu"}</button></div>
              </form>
            </section>
            <section className="panel assets-panel"><div className="panel-heading assets-heading"><div><h2>Yêu cầu mua sắm của tôi</h2><p className="muted">Theo dõi duyệt, đặt hàng và tiến độ nhận hàng.</p></div><button className="text-button" type="button" onClick={refreshPurchaseRequests}>Làm mới</button></div>
              {purchaseRequests.length === 0 ? <p className="notification-empty">Bạn chưa gửi yêu cầu mua sắm nào.</p> : <div className="table-wrap"><table><thead><tr><th>NGÀY GỬI</th><th>THIẾT BỊ</th><th>SỐ LƯỢNG</th><th>DỰ TOÁN</th><th>LÝ DO</th><th>TRẠNG THÁI</th><th>ĐƠN MUA</th><th>GHI CHÚ DUYỆT</th><th>THAO TÁC</th></tr></thead><tbody>
                {purchaseRequests.map((item) => <tr key={item.id}><td>{formatDate(item.createdAt)}</td><td>{item.assetName}<br /><span className="muted">{item.categoryName || "—"}</span></td><td>{item.quantity}</td><td>{item.estimatedAmount == null ? "—" : <>{Number(item.estimatedAmount).toLocaleString("vi-VN")} ₫{Number(item.estimatedAmount) < 1000 && <><br /><span className="purchase-estimate-warning">Cần kiểm tra</span></>}</>}</td><td>{item.reason}</td><td>{formatPurchaseStatus(item)}</td><td>{item.purchaseOrderNumber || "—"}</td><td>{formatPurchaseReviewNote(item.reviewNote, item.status)}</td><td>{item.status === "PENDING" && <button className="delete" type="button" disabled={cancellingPurchaseRequestId === item.id} onClick={() => cancelPurchaseRequest(item)}>{cancellingPurchaseRequestId === item.id ? "Đang hủy..." : "Hủy yêu cầu"}</button>}</td></tr>)}
              </tbody></table></div>}
            </section>
          </div>}

          {activePage === "profile" && profile && <section className="panel admin-resource-form employee-profile-panel">
            <div className="panel-heading"><div><h2>Thông tin cá nhân</h2><p className="muted">Tên đăng nhập và vai trò được quản lý bởi hệ thống.</p></div></div>
            <form className="form-grid" onSubmit={saveProfile}>
              <label className="field">Họ tên<input value={profile.fullName || "—"} readOnly /></label>
              <label className="field">Tên đăng nhập<input value={profile.username} readOnly /></label>
              <label className="field">Phòng ban<input value={profile.departmentName || "—"} readOnly /></label>
              <label className="field">Chức vụ<input value={profile.title || "—"} readOnly /></label>
              <label className="field">Email liên hệ<input name="email" type="email" maxLength="150" defaultValue={profile.email ?? ""} /></label>
              <label className="field">Số điện thoại<input name="phoneNumber" type="tel" maxLength="30" defaultValue={profile.phoneNumber ?? ""} /></label>
              <div className="form-actions employee-wide"><button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu..." : "Lưu thông tin"}</button><button className="button secondary" type="button" onClick={() => setPasswordOpen(true)}>Đổi mật khẩu</button></div>
            </form>
          </section>}
        </div>
      </main>
      <ChangePasswordDialog open={passwordOpen} onClose={() => setPasswordOpen(false)}
        onSaved={() => setMessage("Đã đổi mật khẩu thành công.")} />
    </div>
  );
}
