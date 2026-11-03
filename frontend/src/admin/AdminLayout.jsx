import { useEffect, useRef, useState } from "react";
import ChangePasswordDialog from "../ChangePasswordDialog.jsx";
import NotificationControl from "../NotificationControl.jsx";

export default function AdminLayout({
  activePage,
  currentUser,
  onLogout,
  onNavigate,
  pageLoading = false,
  theme,
  onToggleTheme,
  stocktakeOnly = false,
  notifications,
  notificationsError,
  notificationsOpen,
  onToggleNotifications,
  onMarkNotificationsRead,
  onRefreshNotifications,
  children,
}) {
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [openNavGroup, setOpenNavGroup] = useState(null);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const navigationRef = useRef(null);
  const accountMenuRef = useRef(null);
  const accountTriggerRef = useRef(null);
  const navigationGroups = [
    {
      id: "overview",
      label: "TỔNG QUAN",
      links: [["dashboard", "▦", "Tổng quan", "/admin"]],
    },
    {
      id: "assets",
      label: "TÀI SẢN",
      links: [
        ["assets", "▤", "Tài sản & thiết bị", "/admin/assets"],
        ["catalog", "▧", "Danh mục", "/admin/catalog"],
        ["employees", "♙", "Nhân viên", "/admin/employees"],
      ],
    },
    {
      id: "tools",
      label: "CÔNG CỤ DỤNG CỤ",
      links: [
        ["assignments", "♙", "Bàn giao & Thu hồi", "/admin/assignments"],
        ["maintenance", "⚒", "Bảo trì", "/admin/maintenance"],
        ["disposal", "♲", "Thanh lý", "/admin/disposal"],
      ],
    },
    {
      id: "warehouse",
      label: "KHO & BÁO CÁO",
      links: [
        ["consumables", "▣", "Vật tư tiêu hao", "/admin/consumables"],
        ["invoices", "₫", "Hóa đơn & khấu hao", "/admin/invoices"],
        ["stocktakes", "☷", "Kiểm kê", "/admin/stocktakes"],
        ["reports", "◷", "Báo cáo", "/admin/reports"],
      ],
    },
    {
      id: "system",
      label: "QUẢN TRỊ HỆ THỐNG",
      links: [
        ["users", "♙", "Người dùng & phân quyền", "/admin/users"],
        ["activity", "◴", "Nhật ký hoạt động", "/admin/activity"],
      ],
    },
  ];
  const visibleNavigationGroups = stocktakeOnly
    ? [{ label: "KIỂM KÊ", links: [["stocktakes", "☷", "Đợt kiểm kê", "/admin/stocktakes"]] }]
    : navigationGroups;
  const activeLink = visibleNavigationGroups.flatMap((group) => group.links).find(([id]) => id === activePage);
  const activeLabel = activeLink?.[2] ?? "Quản trị";

  useEffect(() => {
    setMobileNavOpen(false);
  }, [activePage]);

  useEffect(() => {
    if (!mobileNavOpen) return undefined;
    function closeOnEscape(event) {
      if (event.key === "Escape") setMobileNavOpen(false);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [mobileNavOpen]);

  useEffect(() => {
    if (!openNavGroup && !accountMenuOpen) return undefined;
    function closeMenus(event) {
      if (event.type === "keydown") {
        if (event.key !== "Escape") return;
        if (openNavGroup) {
          document.querySelector(`[data-nav-trigger="${openNavGroup}"]`)?.focus();
          setOpenNavGroup(null);
        }
        if (accountMenuOpen) {
          accountTriggerRef.current?.focus();
          setAccountMenuOpen(false);
        }
        return;
      }
      if (!navigationRef.current?.contains(event.target)) setOpenNavGroup(null);
      if (!accountMenuRef.current?.contains(event.target)) setAccountMenuOpen(false);
    }
    document.addEventListener("pointerdown", closeMenus);
    document.addEventListener("keydown", closeMenus);
    return () => {
      document.removeEventListener("pointerdown", closeMenus);
      document.removeEventListener("keydown", closeMenus);
    };
  }, [openNavGroup, accountMenuOpen]);

  return (
    <div className={`layout admin-layout${mobileNavOpen ? " mobile-nav-open" : ""}`}>
      {mobileNavOpen && <button className="admin-sidebar-backdrop" type="button" aria-label="Đóng menu điều hướng" onClick={() => setMobileNavOpen(false)} />}
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="OfficeCare">
          <span className="brand-mark" aria-hidden="true">✦</span>
          <span>OfficeCare<small>{stocktakeOnly ? "Đợt kiểm kê được giao" : "Quản trị hệ thống"}</small></span>
        </a>
        <button className="admin-menu-toggle" type="button" aria-label={mobileNavOpen ? "Đóng menu" : "Mở menu"}
          aria-expanded={mobileNavOpen} aria-controls="admin-navigation" onClick={() => setMobileNavOpen((open) => !open)}>
          <span aria-hidden="true">{mobileNavOpen ? "×" : "☰"}</span>
        </button>
        <nav id="admin-navigation" className="navigation admin-navigation" aria-label="Điều hướng quản trị" ref={navigationRef}>
          {visibleNavigationGroups.map((group) => {
            const groupIsActive = group.links.some(([id]) => id === activePage);
            const menuId = `admin-nav-menu-${group.id}`;
            const groupIsOpen = openNavGroup === group.id;
            if (group.links.length === 1) {
              const [id, , , href] = group.links[0];
              return (
                <div className="admin-nav-group" key={group.id}>
                  <a className={`admin-nav-trigger admin-nav-direct${groupIsActive ? " active" : ""}`}
                    href={href} aria-current={activePage === id ? "page" : undefined}
                    onClick={(event) => {
                      setMobileNavOpen(false);
                      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey
                        || event.shiftKey || event.altKey) return;
                      const target = new URL(href, window.location.href);
                      if (target.origin !== window.location.origin || !target.pathname.startsWith("/admin")) return;
                      event.preventDefault();
                      onNavigate(`${target.pathname}${target.search}${target.hash}`);
                    }}>
                    {group.label}
                  </a>
                </div>
              );
            }
            return (
              <div className={`admin-nav-group${groupIsOpen ? " open" : ""}`} key={group.id}
                onMouseEnter={() => setOpenNavGroup(group.id)}
                onMouseLeave={() => setOpenNavGroup((current) => current === group.id ? null : current)}
                onFocus={() => setOpenNavGroup(group.id)}
                onBlur={(event) => {
                  if (!event.currentTarget.contains(event.relatedTarget)) {
                    setOpenNavGroup((current) => current === group.id ? null : current);
                  }
                }}>
                <button className={`admin-nav-trigger${groupIsActive ? " active" : ""}`} type="button"
                  data-nav-trigger={group.id} aria-expanded={groupIsOpen} aria-controls={menuId}
                  onClick={() => setOpenNavGroup(group.id)}>
                  {group.label}<span aria-hidden="true">⌄</span>
                </button>
                <div className="admin-nav-menu" id={menuId} hidden={!groupIsOpen}>
                  {group.links.map(([id, icon, label, href]) => (
                    <a key={id} className={activePage === id ? "active" : ""} href={href}
                      onClick={(event) => {
                        setMobileNavOpen(false);
                        setOpenNavGroup(null);
                        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey
                          || event.shiftKey || event.altKey) return;
                        const target = new URL(href, window.location.href);
                        if (target.origin !== window.location.origin || !target.pathname.startsWith("/admin")) return;
                        event.preventDefault();
                        onNavigate(`${target.pathname}${target.search}${target.hash}`);
                      }}
                      aria-current={activePage === id ? "page" : undefined} title={label}>
                      <span className="nav-icon" aria-hidden="true">{icon}</span>{label}
                    </a>
                  ))}
                </div>
              </div>
            );
          })}
        </nav>
        <div className="admin-topbar-actions">
          <button className="toolbar-icon" type="button" onClick={onToggleTheme}
            aria-label={theme === "dark" ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"}
            title={theme === "dark" ? "Giao diện sáng" : "Giao diện tối"}>{theme === "dark" ? "☀" : "☾"}</button>
          <NotificationControl notifications={notifications} error={notificationsError} open={notificationsOpen}
            onToggle={onToggleNotifications} onMarkRead={onMarkNotificationsRead} onRefresh={onRefreshNotifications} />
          <div className="admin-account-menu-wrap" ref={accountMenuRef}>
            <button className="admin-account-trigger" type="button" ref={accountTriggerRef}
              onClick={() => setAccountMenuOpen((open) => !open)} aria-expanded={accountMenuOpen}
              aria-controls="admin-account-menu" aria-label={`Tài khoản ${currentUser.username}`}>
              <span className="avatar">{(currentUser.username || "OC").slice(0, 2).toLocaleUpperCase("vi")}</span>
              <span className="admin-account-name">{currentUser.username}</span>
              <span aria-hidden="true">⌄</span>
            </button>
            <div className="admin-account-menu" id="admin-account-menu" hidden={!accountMenuOpen}>
              <div className="admin-account-identity">
                <strong>{currentUser.username}</strong>
                <span>{stocktakeOnly ? "Người phụ trách" : "Quản trị viên"}</span>
              </div>
              <button type="button" onClick={() => { setAccountMenuOpen(false); setPasswordDialogOpen(true); }}>Đổi mật khẩu</button>
              <button type="button" onClick={onLogout}>Đăng xuất</button>
            </div>
          </div>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div className="breadcrumb">OfficeCare <span>/</span> <strong>{activeLabel}</strong></div>
          {pageLoading && <span className="page-loading-indicator" role="status" aria-live="polite">
            <span className="page-loading-spinner" aria-hidden="true" />
            <span>Đang tải...</span>
          </span>}
        </header>
        <div className="dashboard admin-dashboard page-transition-content" aria-busy={pageLoading}>
          {children}
        </div>
      </main>
      <ChangePasswordDialog open={passwordDialogOpen} onClose={() => setPasswordDialogOpen(false)}
        onSaved={() => window.dispatchEvent(new CustomEvent("officecare:message", { detail: "Đã đổi mật khẩu thành công." }))} />
    </div>
  );
}
