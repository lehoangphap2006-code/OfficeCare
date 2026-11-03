import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import AdminLayout from "./admin/AdminLayout.jsx";
import DashboardPage from "./admin/DashboardPage.jsx";
import UsersPage from "./admin/UsersPage.jsx";
import ActivityLogPage from "./admin/ActivityLogPage.jsx";
import ProcurementPage from "./admin/ProcurementPage.jsx";
import DisposalPage from "./admin/DisposalPage.jsx";
import UserWorkspace from "./UserWorkspace.jsx";
import AdminManagementPage from "./admin/AdminManagementPage.jsx";
import ChangePasswordDialog from "./ChangePasswordDialog.jsx";
import { adminDownload } from "./admin/adminApi.js";
import { getTokenExpiration } from "./authToken.js";
import NotificationControl from "./NotificationControl.jsx";
import { fetchWithSession, refreshSession } from "./sessionFetch.js";

const BACKEND_URL = "http://localhost:8081";
const API_URL = `${BACKEND_URL}/api/tai-san`;
const CATEGORY_URL = "http://localhost:8081/api/loai-tai-san";
const AUTH_URL = "http://localhost:8081/api/auth";
const COMMERCE_URL = "http://localhost:8081/api/thuong-mai";
const MOMO_PAYMENT_URL = "http://localhost:8081/api/payments/momo";
const NOTIFICATION_URL = "http://localhost:8081/api/thong-bao";
const PAGE_SIZE = 8;
const numberFormatter = new Intl.NumberFormat("vi-VN");
const assetStatuses = [
  { name: "Đang sử dụng", key: "dangSuDung", color: "#7541d8", className: "in-use" },
  { name: "Trong kho", key: "trongKho", color: "#2da58a", className: "stock" },
  { name: "Bảo trì", key: "baoTri", color: "#538dc4", className: "maintenance" },
  { name: "Chờ thanh lý", key: "choThanhLy", color: "#e7a052", className: "retired" },
  { name: "Đã thanh lý", key: "thanhLy", color: "#e7a052", className: "retired" },
];
const navigation = [
  ["overview", "▦", "Tổng quan", "overview", "#overview"],
  ["assets", "▤", "Tài sản & thiết bị", "assets", "#assets"],
  ["categories", "▧", "Danh mục", "catalog", "#categories"],
  ["assignments", "♙", "Bàn giao & Thu hồi", "assignments", "#assignments"],
  ["returns", "↩", "Bàn giao & Thu hồi", "returns", "#returns"],
  ["maintenance", "⚒", "Bảo trì", "maintenance", "#maintenance"],
  ["repairs", "🔧", "Sửa chữa", "maintenance", "#repairs"],
  ["purchase", "₫", "Mua sắm", "assets", "#purchase"],
  ["sale", "₫", "Thanh lý", "assets", "#sale"],
  ["status-overview", "◷", "Kiểm kê & báo cáo", "reports", "#status-overview"],
];
const authSlides = [
  {
    kicker: "QUẢN LÝ TÀI SẢN VĂN PHÒNG",
    title: ["Mọi thiết bị.", "Một không gian quản lý."],
    description: "Theo dõi tài sản, bàn giao và bảo trì dễ dàng trên cùng một nền tảng.",
    footer: ["Quản lý thông minh", "Vận hành hiệu quả"],
  },
  {
    kicker: "BÀN GIAO RÕ RÀNG",
    title: ["Đúng người.", "Đúng thiết bị."],
    description: "Theo dõi lịch sử bàn giao và tình trạng tài sản của từng nhân viên.",
    footer: ["Minh bạch trách nhiệm", "An tâm sử dụng"],
  },
  {
    kicker: "CHỦ ĐỘNG BẢO TRÌ",
    title: ["Chăm sóc thiết bị.", "Liền mạch mỗi ngày."],
    description: "Nắm lịch bảo trì, sửa chữa và hạn chế gián đoạn công việc.",
    footer: ["Nhắc lịch kịp thời", "Vận hành bền lâu"],
  },
];
const legacySectionIds = { commerce: "purchase" };
const today = new Intl.DateTimeFormat("vi-VN", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
}).format(new Date());

async function request(url, options = {}) {
  const token = sessionStorage.getItem("officecare.accessToken");
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let response;
  try {
    response = await fetchWithSession(url, { ...options, headers });
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error("Không kết nối được máy chủ API. Hãy kiểm tra backend đang chạy ở cổng 8081 rồi tải lại trang.");
    }
    throw error;
  }

  if (!response.ok) {
    let detail = `Yêu cầu thất bại (HTTP ${response.status}).`;
    let hasServerDetail = false;
    if (response.status === 403) {
      detail = url.includes("/api/cap-phat") || url.includes("/api/bao-tri")
        ? "Backend chưa có API cấp phát/bảo trì mới. Hãy khởi động lại backend từ mã nguồn mới rồi tải lại trang."
        : "Tài khoản không được phép thực hiện thao tác này.";
    }
    try {
      const error = await response.json();
      const serverDetail = error.detail || error.message;
      if (serverDetail) {
        detail = serverDetail;
        hasServerDetail = true;
      }
    } catch {
      // Keep the HTTP status message when the server does not return JSON.
    }
    if (response.status === 409 && options.method?.toUpperCase() === "DELETE"
        && url.includes("/api/loai-tai-san/") && !hasServerDetail) {
      detail = "Backend đang chạy phiên bản cũ nên chưa hỗ trợ xóa danh mục có liên kết. Hãy khởi động lại backend từ mã nguồn mới rồi thử lại.";
    }
    if (response.status === 401 && !url.endsWith("/api/auth/login")) {
      sessionStorage.removeItem("officecare.accessToken");
      sessionStorage.removeItem("officecare.refreshToken");
      window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
        detail: {
          url,
          method: options.method || "GET",
          status: response.status,
          message: detail,
        },
      }));
    }
    const requestError = new Error(detail);
    requestError.status = response.status;
    throw requestError;
  }
  return response.status === 204 ? null : response.json();
}

function storeLoginSession(session) {
  if (!session.accessToken || !session.refreshToken) {
    throw new Error("Backend chưa hỗ trợ gia hạn phiên. Hãy khởi động lại backend rồi đăng nhập lại.");
  }
  sessionStorage.setItem("officecare.accessToken", session.accessToken);
  sessionStorage.setItem("officecare.refreshToken", session.refreshToken);
}

function csvCell(value) {
  let text = String(value ?? "");
  if (/^[\s]*[=+\-@]/.test(text)) text = `'${text}`;
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadCsv(rows) {
  const columns = [
    ["Mã tài sản", (asset) => asset.maTaiSan],
    ["Tên tài sản", (asset) => asset.tenTaiSan],
    ["Loại tài sản", (asset) => asset.tenLoaiTaiSan],
    ["Giá mua (VNĐ)", (asset) => asset.giaMua ?? ""],
    ["Ngày mua", (asset) => asset.ngayMua ?? ""],
    ["Tình trạng", (asset) => asset.tinhTrang],
    ["Tòa nhà", (asset) => asset.toaNha ?? ""],
    ["Tầng", (asset) => asset.tang ?? ""],
    ["Phòng", (asset) => asset.phong ?? ""],
    ["Người đang giữ", (asset) => asset.nguoiDangGiu ?? ""],
    ["Phòng ban đang giữ", (asset) => asset.phongBanDangGiu ?? ""],
    ["Thời gian khấu hao (năm)", (asset) => asset.thoiGianKhauHaoNam ?? ""],
    ["Giá trị còn lại (VNĐ)", (asset) => asset.giaTriConLai ?? ""],
    ["Chu kỳ bảo trì (tháng)", (asset) => asset.chuKyBaoTriThang ?? 12],
    ["Ngày bảo trì gần nhất", (asset) => formatDate(asset.baoTriGanNhat)],
    ["Ngày bảo trì tiếp theo", (asset) => formatDate(asset.baoTriTiepTheo)],
    ["Chi phí bảo trì tích lũy (VNĐ)", (asset) => asset.chiPhiBaoTriTichLuy ?? 0],
  ];
  const csv = [
    columns.map(([heading]) => csvCell(heading)).join(","),
    ...rows.map((asset) => columns.map(([, value]) => csvCell(value(asset))).join(",")),
  ].join("\r\n");
  const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `tai-san-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString("vi-VN") : "—";
}

function localDateInputValue(date = new Date()) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;",
  })[character]);
}

function TableMessage({ children, columns, loading = false, onRetry }) {
  return (
    <tr>
      <td colSpan={columns} className={`empty${loading ? " loading-state" : ""}`}>
        {children}
        {onRetry && <><br /><button className="retry-button" type="button" onClick={onRetry}>Thử lại</button></>}
      </td>
    </tr>
  );
}

function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname);
  const [pageLoading, setPageLoading] = useState(false);
  const pageLoadingTimer = useRef(null);
  const [authSlide, setAuthSlide] = useState(0);
  const [accessToken, setAccessToken] = useState(() => sessionStorage.getItem("officecare.accessToken"));
  const [currentUser, setCurrentUser] = useState(null);
  const [loginError, setLoginError] = useState("");
  const [loginBusy, setLoginBusy] = useState(false);
  const [authMode, setAuthMode] = useState("login");
  const [message, setMessage] = useState({ text: "", error: false });
  const manualLogoutRef = useRef(false);
  const [returnRepairSuggestion, setReturnRepairSuggestion] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem("officecare.theme") === "dark" ? "dark" : "light");
  const [sensitiveValuesVisible, setSensitiveValuesVisible] = useState(
    () => localStorage.getItem("officecare.showSensitiveValues") !== "false",
  );
  const [notifications, setNotifications] = useState([]);
  const [notificationsError, setNotificationsError] = useState("");
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);

  const [assets, setAssets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [stats, setStats] = useState(null);
  const [saleableAssets, setSaleableAssets] = useState([]);
  const [orders, setOrders] = useState([]);
  const [commerceReferences, setCommerceReferences] = useState({ suppliers: [], locations: [], vatTu: [] });
  const [assetsLoading, setAssetsLoading] = useState(false);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [commerceLoading, setCommerceLoading] = useState(false);
  const [assetError, setAssetError] = useState("");
  const [categoryError, setCategoryError] = useState("");
  const [commerceError, setCommerceError] = useState("");

  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [departmentSearchInput, setDepartmentSearchInput] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [purchaseFromFilter, setPurchaseFromFilter] = useState("");
  const [purchaseToFilter, setPurchaseToFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [assetFormOpen, setAssetFormOpen] = useState(false);
  const [editingAsset, setEditingAsset] = useState(null);
  const [assetFormError, setAssetFormError] = useState("");
  const [assetBusy, setAssetBusy] = useState(false);
  const [categoryFormOpen, setCategoryFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [categoryBusy, setCategoryBusy] = useState(false);
  const [categoryDeleteTarget, setCategoryDeleteTarget] = useState(null);
  const [categoryReplacementId, setCategoryReplacementId] = useState("");
  const [categoryDeleting, setCategoryDeleting] = useState(false);
  const [selectedAsset, setSelectedAsset] = useState(null);

  const [commercePayment, setCommercePayment] = useState("TIEN_MAT");
  const [commerceItems, setCommerceItems] = useState([{ soLuong: "1", loaiDong: "TAI_SAN" }]);
  const [commerceSupplierId, setCommerceSupplierId] = useState("");
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState("");
  const [disposalReason, setDisposalReason] = useState("");
  const [disposalReceiptMethod, setDisposalReceiptMethod] = useState("");
  const [disposalSearch, setDisposalSearch] = useState("");
  const [commercePurchaseFrom, setCommercePurchaseFrom] = useState("");
  const [commercePurchaseTo, setCommercePurchaseTo] = useState("");
  const [commerceLocationFilter, setCommerceLocationFilter] = useState("");
  const [commerceBusy, setCommerceBusy] = useState(false);
  const [commerceActionId, setCommerceActionId] = useState(null);
  const [receiptOrder, setReceiptOrder] = useState(null);
  const [receiptQuantities, setReceiptQuantities] = useState({});
  const [receiptBusy, setReceiptBusy] = useState(false);
  const [assignableAssets, setAssignableAssets] = useState([]);
  const [maintenanceAssets, setMaintenanceAssets] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [assignmentBusy, setAssignmentBusy] = useState(false);
  const [assignmentActionId, setAssignmentActionId] = useState(null);
  const [assignmentReturnTarget, setAssignmentReturnTarget] = useState(null);
  const [returnRequestTargetId, setReturnRequestTargetId] = useState(null);
  const [returnRequests, setReturnRequests] = useState([]);
  const [returnRequestError, setReturnRequestError] = useState("");
  const [assignmentError, setAssignmentError] = useState("");
  const [maintenanceRequests, setMaintenanceRequests] = useState([]);
  const [maintenanceBusy, setMaintenanceBusy] = useState(false);
  const [maintenanceActionId, setMaintenanceActionId] = useState(null);
  const [maintenanceDrafts, setMaintenanceDrafts] = useState({});
  const [maintenanceDetails, setMaintenanceDetails] = useState({});
  const [repairFilters, setRepairFilters] = useState({ status: "", department: "", fromDate: "", toDate: "" });
  const [repairAssetId, setRepairAssetId] = useState(() => new URLSearchParams(window.location.search).get("repairAssetId") ?? "");
  const [maintenanceError, setMaintenanceError] = useState("");
  const [exporting, setExporting] = useState(false);
  const [activeSection, setActiveSection] = useState(() => {
    const hashSection = window.location.hash.slice(1);
    const section = legacySectionIds[hashSection] ?? hashSection;
    return navigation.some(([id, , , , href]) => id === section && href.startsWith("#"))
      ? section
      : "overview";
  });
  const commerceType = activeSection === "sale" ? "BAN" : "MUA";
  const searchInputRef = useRef(null);
  const detailDialogRef = useRef(null);
  const receiptDialogRef = useRef(null);

  useEffect(() => {
    if (currentUser) return undefined;
    const timer = window.setInterval(() => {
      if (!document.hidden) {
        setAuthSlide((current) => (current + 1) % authSlides.length);
      }
    }, 5000);
    return () => window.clearInterval(timer);
  }, [currentUser]);

  const hasWriteAccess = currentUser !== null;
  const hasPermission = (permission) =>
    permission !== "users" && permission !== "activity" || currentUser?.role === "ADMIN";

  const endSession = useCallback(({ clearLoginError = false } = {}) => {
    sessionStorage.removeItem("officecare.accessToken");
    sessionStorage.removeItem("officecare.refreshToken");
    setAccessToken(null);
    setCurrentUser(null);
    setMessage({ text: "", error: false });
    if (clearLoginError) setLoginError("");
    setAssets([]);
    setCategories([]);
    setStats(null);
    setOrders([]);
    setCommerceReferences({ suppliers: [], locations: [], vatTu: [] });
    setSaleableAssets([]);
    setReceiptOrder(null);
    setReceiptQuantities({});
    setNotifications([]);
    setNotificationsError("");
  }, []);

  const showPageLoading = useCallback(() => {
    if (pageLoadingTimer.current) window.clearTimeout(pageLoadingTimer.current);
    setPageLoading(true);
    pageLoadingTimer.current = window.setTimeout(() => {
      setPageLoading(false);
      pageLoadingTimer.current = null;
    }, 450);
  }, []);

  useEffect(() => () => {
    if (pageLoadingTimer.current) window.clearTimeout(pageLoadingTimer.current);
  }, []);

  function navigateToPath(path) {
    showPageLoading();
    window.history.pushState(null, "", path);
    setPathname(window.location.pathname);
    window.scrollTo({ top: 0 });
  }

  const refreshNotifications = useCallback(async () => {
    setNotificationsError("");
    try {
      setNotifications(await request(NOTIFICATION_URL));
    } catch (error) {
      setNotifications([]);
      setNotificationsError(error.message);
    }
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("officecare.theme", theme);
  }, [theme]);

  useEffect(() => {
    localStorage.setItem("officecare.showSensitiveValues", String(sensitiveValuesVisible));
  }, [sensitiveValuesVisible]);

  useEffect(() => {
    if (!currentUser) {
      setNotifications([]);
      setNotificationsError("");
      return;
    }
    let active = true;
    request(NOTIFICATION_URL)
      .then((result) => {
        if (active) {
          setNotifications(result);
          setNotificationsError("");
        }
      })
      .catch((error) => {
        if (active) {
          setNotifications([]);
          setNotificationsError(error.message);
        }
      });
    return () => { active = false; };
  }, [currentUser]);

  useEffect(() => {
    const handleUnauthorized = (event) => {
      if (manualLogoutRef.current) {
        endSession({ clearLoginError: true });
        return;
      }
      const isPlaceOrder = event.detail?.method === "POST"
        && (event.detail?.url.includes("/place-order")
          || event.detail?.url.endsWith("/api/thuong-mai/don-hang"));
      const authenticationDetail = event.detail?.message
        ? ` Chi tiết từ máy chủ: ${event.detail.message}`
        : "";
      const requestDetail = event.detail?.url
        ? ` API bị từ chối: ${event.detail.method || "GET"} ${event.detail.url.replace("http://localhost:8081", "")}.`
        : "";
      const failedRequestDetail = event.detail?.failedRequest
        ? ` Yêu cầu ban đầu: ${event.detail.failedRequest}.`
        : "";
      setLoginError(isPlaceOrder
        ? "Chưa tạo được đơn vì phiên đăng nhập không hợp lệ. Đơn của bạn chưa được lưu, dữ liệu đã nhập vẫn còn. Hãy đăng nhập lại, sau đó bấm “Xác nhận” để thử lại."
        : `Máy chủ không chấp nhận phiên đăng nhập. Vui lòng đăng nhập lại.${requestDetail}${failedRequestDetail}${authenticationDetail}`);
      endSession();
    };
    const handleMessage = (event) => setMessage({ text: event.detail, error: false });
    window.addEventListener("officecare:unauthorized", handleUnauthorized);
    window.addEventListener("officecare:message", handleMessage);
    return () => {
      window.removeEventListener("officecare:unauthorized", handleUnauthorized);
      window.removeEventListener("officecare:message", handleMessage);
    };
  }, [endSession]);

  useEffect(() => {
    const handleTokenRefreshed = (event) => {
      if (event.detail?.accessToken) setAccessToken(event.detail.accessToken);
    };
    window.addEventListener("officecare:token-refreshed", handleTokenRefreshed);
    return () => window.removeEventListener("officecare:token-refreshed", handleTokenRefreshed);
  }, []);

  useEffect(() => {
    const updatePath = () => {
      showPageLoading();
      setPathname(window.location.pathname);
    };
    window.addEventListener("popstate", updatePath);
    return () => window.removeEventListener("popstate", updatePath);
  }, [showPageLoading]);

  useEffect(() => {
    if (!accessToken) {
      setCurrentUser(null);
      return undefined;
    }
    let active = true;
    request(`${AUTH_URL}/me`)
      .then((user) => { if (active) setCurrentUser(user); })
      .catch(() => { if (active) endSession(); });
    return () => { active = false; };
  }, [accessToken, endSession]);

  useEffect(() => {
    if (!accessToken) return undefined;
    let expiresAt;
    try {
      expiresAt = getTokenExpiration(accessToken);
    } catch (error) {
      setLoginError(`Không thể đọc thời hạn phiên đăng nhập: ${error.message}`);
      endSession();
      return undefined;
    }
    if (!expiresAt) {
      setLoginError("Token đăng nhập không có thời hạn hợp lệ. Vui lòng đăng nhập lại.");
      endSession();
      return undefined;
    }
    const refreshDelay = Math.max(0, expiresAt - Date.now() - 60_000);
    const timer = window.setTimeout(async () => {
      try {
        await refreshSession();
      } catch (error) {
        if (error.status === 401) {
          window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
            detail: {
              url: "http://localhost:8081/api/auth/refresh",
              method: "POST",
              status: error.status,
              message: error.message,
            },
          }));
        } else {
          setMessage({ text: `Không thể gia hạn phiên đăng nhập: ${error.message}`, error: true });
        }
      }
    }, refreshDelay);
    const refreshWhenVisible = () => {
      if (document.visibilityState !== "visible" || expiresAt - Date.now() > 60_000) return;
      refreshSession().catch((error) => {
        if (error.status === 401) {
          window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
            detail: {
              url: "http://localhost:8081/api/auth/refresh",
              method: "POST",
              status: error.status,
              message: error.message,
            },
          }));
        } else setMessage({ text: `Không thể gia hạn phiên đăng nhập: ${error.message}`, error: true });
      });
    };
    document.addEventListener("visibilitychange", refreshWhenVisible);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, [accessToken, endSession]);

  const loadAssets = useCallback(async () => {
    if (!currentUser || currentUser.role !== "ADMIN") return false;
    setAssetsLoading(true);
    setAssetError("");
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (keyword) params.set("keyword", keyword);
    if (statusFilter) params.set("tinhTrang", statusFilter);
    if (categoryFilter) params.set("loaiId", categoryFilter);
    if (departmentFilter.trim()) params.set("phongBan", departmentFilter.trim());
    if (purchaseFromFilter) params.set("muaTuNgay", purchaseFromFilter);
    if (purchaseToFilter) params.set("muaDenNgay", purchaseToFilter);

    try {
      const [result, summary] = await Promise.all([
        request(`${API_URL}?${params}`),
        request(`${API_URL}/thong-ke`),
      ]);
      if (result.totalPages > 0 && page >= result.totalPages) {
        setPage(result.totalPages - 1);
        return true;
      }
      setAssets(result.content);
      setSelectedAsset((current) => current
        ? result.content.find((asset) => asset.taiSanId === current.taiSanId) ?? current
        : current);
      setStats(summary);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      if (result.totalPages === 0) setPage(0);
      return true;
    } catch (error) {
      setAssets([]);
      setTotalElements(0);
      setTotalPages(0);
      setAssetError(error.message);
      if (error.status !== 401 && !manualLogoutRef.current
        && sessionStorage.getItem("officecare.accessToken")) {
        setMessage({ text: `Không thể tải dữ liệu: ${error.message}`, error: true });
      }
      return false;
    } finally {
      setAssetsLoading(false);
    }
  }, [currentUser, page, keyword, statusFilter, categoryFilter, departmentFilter, purchaseFromFilter, purchaseToFilter]);

  const loadCategories = useCallback(async () => {
    if (!currentUser || currentUser.role !== "ADMIN") return false;
    setCategoriesLoading(true);
    setCategoryError("");
    try {
      setCategories(await request(CATEGORY_URL));
      return true;
    } catch (error) {
      setCategoryError(error.message);
      return false;
    } finally {
      setCategoriesLoading(false);
    }
  }, [currentUser]);

  const loadCommerce = useCallback(async () => {
    if (!currentUser || currentUser.role !== "ADMIN") return false;
    setCommerceLoading(true);
    setCommerceError("");
    try {
      const [availableAssets, orderList, references] = await Promise.all([
        request(`${COMMERCE_URL}/tai-san-ban`),
        request(`${COMMERCE_URL}/don-hang`),
        request(`${COMMERCE_URL}/tham-chieu`),
      ]);
      setSaleableAssets(availableAssets);
      setOrders(orderList);
      setCommerceReferences(references);
      return true;
    } catch (error) {
      setCommerceError(error.message);
      return false;
    } finally {
      setCommerceLoading(false);
    }
  }, [currentUser]);

  const loadAssetWorkflows = useCallback(async () => {
    if (!currentUser || currentUser.role !== "ADMIN") return false;
    setAssignmentError("");
    setMaintenanceError("");
    setReturnRequestError("");
    const results = await Promise.allSettled([
      request("http://localhost:8081/api/cap-phat"),
      request("http://localhost:8081/api/bao-tri"),
      request(`${API_URL}?page=0&size=100&tinhTrang=${encodeURIComponent("Trong kho")}`),
      request(`${API_URL}?page=0&size=100&tinhTrang=${encodeURIComponent("Đang sử dụng")}`),
      request(`${API_URL}?page=0&size=100&tinhTrang=${encodeURIComponent("Bảo trì")}`),
      request("http://localhost:8081/api/admin/return-requests"),
    ]);
    const [assignmentResult, maintenanceResult, stockResult, inUseResult, maintenanceAssetsResult, returnRequestResult] = results;
    let assignmentLoadError = "";
    let maintenanceLoadError = "";
    if (assignmentResult.status === "fulfilled") {
      setAssignments(assignmentResult.value);
      const assignedIds = new Set(assignmentResult.value.filter((item) => !item.thuHoiLuc).map((item) => item.taiSanId));
      if (stockResult.status === "fulfilled") {
        setAssignableAssets(stockResult.value.content.filter((asset) => !assignedIds.has(asset.taiSanId)));
      } else {
        setAssignableAssets([]);
        assignmentLoadError = stockResult.reason.message;
      }
    } else {
      setAssignments([]);
      assignmentLoadError = assignmentResult.reason.message;
      if (stockResult.status === "fulfilled") {
        setAssignableAssets(stockResult.value.content);
      }
    }
    if (maintenanceResult.status === "fulfilled") {
      setMaintenanceRequests(maintenanceResult.value);
      setMaintenanceDrafts(Object.fromEntries(maintenanceResult.value.map((item) => [item.id, String(item.chiPhi ?? 0)])));
    } else {
      setMaintenanceRequests([]);
      maintenanceLoadError = maintenanceResult.reason.message;
    }
    if (returnRequestResult.status === "fulfilled") {
      setReturnRequests(returnRequestResult.value);
    } else {
      setReturnRequests([]);
      setReturnRequestError(returnRequestResult.reason.message);
    }
    if (stockResult.status === "fulfilled" && inUseResult.status === "fulfilled"
        && maintenanceAssetsResult.status === "fulfilled") {
      const assetsWithOpenMaintenance = new Set(maintenanceResult.status === "fulfilled"
        ? maintenanceResult.value.filter((item) => ["YEU_CAU", "DANG_XU_LY"].includes(item.trangThai))
          .map((item) => item.taiSanId)
        : []);
      setMaintenanceAssets([
        ...stockResult.value.content,
        ...inUseResult.value.content,
        ...maintenanceAssetsResult.value.content.filter((asset) => !assetsWithOpenMaintenance.has(asset.taiSanId)),
      ]);
    } else {
      setMaintenanceAssets([]);
      if (!maintenanceLoadError) {
        maintenanceLoadError = [stockResult, inUseResult, maintenanceAssetsResult]
          .find((result) => result.status === "rejected").reason.message;
      }
    }
    setAssignmentError(assignmentLoadError);
    setMaintenanceError(maintenanceLoadError);
    return results.every((result) => result.status === "fulfilled");
  }, [currentUser]);

  const refreshData = useCallback(async () => {
    const results = await Promise.all([loadCategories(), loadAssets(), loadCommerce(), loadAssetWorkflows()]);
    const succeeded = results.every(Boolean);
    if (!succeeded) setMessage({ text: "Một số dữ liệu chưa tải được. Hãy thử lại tại bảng tương ứng.", error: true });
    return succeeded;
  }, [loadAssets, loadAssetWorkflows, loadCategories, loadCommerce]);

  useEffect(() => {
    if (!currentUser || currentUser.role !== "ADMIN") return;
    loadAssets();
  }, [currentUser, loadAssets]);

  useEffect(() => {
    if (!currentUser || currentUser.role !== "ADMIN") return;
    loadCategories();
    loadCommerce();
    loadAssetWorkflows();
  }, [currentUser, loadCategories, loadCommerce, loadAssetWorkflows]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setPage(0);
      setKeyword(searchInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setPage(0);
      setDepartmentFilter(departmentSearchInput.trim());
    }, 250);
    return () => window.clearTimeout(timer);
  }, [departmentSearchInput]);

  useEffect(() => {
    setPage(0);
  }, [statusFilter, categoryFilter, departmentFilter, purchaseFromFilter, purchaseToFilter]);

  useEffect(() => {
    if (!currentUser || currentUser.role !== "ADMIN") return;
    if (pathname.startsWith("/admin")) return;
    const updateSection = () => {
      const hashSection = window.location.hash.slice(1);
      const section = legacySectionIds[hashSection] ?? hashSection;
      const requested = navigation.find(([id, , , permission, href]) =>
        id === section && href.startsWith("#") && hasPermission(permission));
      const fallback = navigation.find(([, , , permission, href]) =>
        href.startsWith("#") && hasPermission(permission));
      if (!requested && !fallback) {
        const adminPage = navigation.find(([, , , permission, href]) =>
          !href.startsWith("#") && hasPermission(permission));
        if (adminPage) {
          window.location.assign(adminPage[4]);
          return;
        }
      }
      const nextSection = requested?.[0] ?? fallback?.[0] ?? "overview";
      if (window.location.hash !== `#${nextSection}`) {
        window.location.hash = nextSection;
      }
      setActiveSection(nextSection);
      window.scrollTo({ top: 0 });
    };
    updateSection();
    window.addEventListener("hashchange", updateSection);
    window.addEventListener("popstate", updateSection);
    return () => {
      window.removeEventListener("hashchange", updateSection);
      window.removeEventListener("popstate", updateSection);
    };
  }, [currentUser, pathname]);

  useEffect(() => {
    if (activeSection === "purchase" || activeSection === "sale") {
      setCommerceItems([{ soLuong: "1", loaiDong: "TAI_SAN" }]);
    }
  }, [activeSection]);

  useEffect(() => {
    const handleShortcut = (event) => {
      if (activeSection !== "assets" || event.key !== "/" || event.altKey || event.ctrlKey || event.metaKey) return;
      const target = event.target;
      if (target instanceof HTMLElement && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)) return;
      event.preventDefault();
      searchInputRef.current?.focus();
    };
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, [activeSection]);

  useEffect(() => {
    if (selectedAsset && detailDialogRef.current && !detailDialogRef.current.open) {
      detailDialogRef.current.showModal();
    }
  }, [selectedAsset]);

  useEffect(() => {
    if (receiptOrder && receiptDialogRef.current && !receiptDialogRef.current.open) {
      receiptDialogRef.current.showModal();
    }
  }, [receiptOrder]);

  const showMessage = (text, error = false) => {
    setMessage({ text, error });
    if (!currentUser) return;
    refreshNotifications();
  };

  async function markNotificationsRead() {
    try {
      await request(`${NOTIFICATION_URL}/da-xem-tat-ca`, { method: "PUT" });
      setNotifications(await request(NOTIFICATION_URL));
      setNotificationsError("");
    } catch (error) {
      setNotificationsError(`Không thể cập nhật trạng thái thông báo: ${error.message}`);
    }
  }

  function privateValue(value) {
    return sensitiveValuesVisible ? value : "••••";
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoginBusy(true);
    setLoginError("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      const login = await request(`${AUTH_URL}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: form.get("username"), password: form.get("password") }),
      });
      manualLogoutRef.current = false;
      storeLoginSession(login);
      setAccessToken(login.accessToken);
      setCurrentUser({ username: login.username, role: login.role });
      if (login.role === "ADMIN") {
        const hasProcurementDraft = window.location.pathname === "/admin/procurement"
          && (sessionStorage.getItem("officecare.purchaseOrderDraft")
            || sessionStorage.getItem("officecare.directPurchaseOrderDraft"));
        if (!hasProcurementDraft) navigateToPath("/admin");
      }
      formElement.reset();
    } catch (error) {
      setLoginError(error.message);
    } finally {
      setLoginBusy(false);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();
    setLoginBusy(true);
    setLoginError("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    if (form.get("password") !== form.get("passwordConfirm")) {
      setLoginBusy(false);
      setLoginError("Mật khẩu nhập lại không khớp.");
      return;
    }
    try {
      const login = await request(`${AUTH_URL}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: form.get("username"), password: form.get("password") }),
      });
      manualLogoutRef.current = false;
      storeLoginSession(login);
      setAccessToken(login.accessToken);
      setCurrentUser({ username: login.username, role: login.role });
      setMessage({ text: "Đăng ký thành công. Tài khoản USER đã được đăng nhập.", error: false });
      formElement.reset();
    } catch (error) {
      setLoginError(error.message);
    } finally {
      setLoginBusy(false);
    }
  }

  async function handleLogout() {
    manualLogoutRef.current = true;
    setLoginError("");
    setMessage({ text: "", error: false });
    try {
      if (sessionStorage.getItem("officecare.refreshToken")) await refreshSession();
      await request(`${AUTH_URL}/logout`, {
        method: "POST",
        headers: { "X-Refresh-Token": sessionStorage.getItem("officecare.refreshToken") ?? "" },
      });
    } catch (error) {
      console.warn("Không thể thu hồi phiên đăng nhập trên máy chủ; phiên trên trình duyệt đã được xóa.", error);
    } finally {
      endSession({ clearLoginError: true });
    }
  }

  function openAssetForm(asset = null) {
    setEditingAsset(asset);
    setAssetFormError("");
    setAssetFormOpen(true);
    if (activeSection !== "assets") {
      window.location.hash = "assets";
      setActiveSection("assets");
    }
    window.setTimeout(() => document.getElementById("asset-form-panel")?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
  }

  function navigateToSection(section) {
    const item = navigation.find(([id]) => id === section);
    if (!item || !hasPermission(item[3])) return;
    showPageLoading();
    if (window.location.hash !== `#${section}`) window.location.hash = section;
    setActiveSection(section);
    window.scrollTo({ top: 0 });
  }

  function closeAssetForm() {
    setAssetFormOpen(false);
    setEditingAsset(null);
    setAssetFormError("");
  }

  async function handleAssetSubmit(event) {
    event.preventDefault();
    setAssetFormError("");
    const form = new FormData(event.currentTarget);
    const code = String(form.get("maTaiSan")).trim();
    const name = String(form.get("tenTaiSan")).trim();
    if (!code || !name) {
      setAssetFormError("Mã tài sản và tên tài sản không được để trống.");
      return;
    }
    const payload = {
      maTaiSan: code,
      tenTaiSan: name,
      loaiTaiSanId: Number(form.get("loaiTaiSanId")),
      giaMua: form.get("giaMua") === "" ? null : Number(form.get("giaMua")),
      ngayMua: form.get("ngayMua") || null,
      tinhTrang: form.get("tinhTrang"),
      chuKyBaoTriThang: Number(form.get("chuKyBaoTriThang") || 12),
      toaNha: String(form.get("toaNha") ?? "").trim(),
      tang: String(form.get("tang") ?? "").trim(),
      phong: String(form.get("phong") ?? "").trim(),
    };
    setAssetBusy(true);
    try {
      await request(editingAsset ? `${API_URL}/${editingAsset.taiSanId}` : API_URL, {
        method: editingAsset ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!editingAsset) setPage(0);
      const wasEditing = Boolean(editingAsset);
      closeAssetForm();
      const [assetsLoaded, categoriesLoaded] = await Promise.all([loadAssets(), loadCategories()]);
      showMessage(
        assetsLoaded && categoriesLoaded
          ? wasEditing ? "Đã cập nhật tài sản." : "Đã thêm tài sản."
          : "Đã lưu tài sản nhưng không tải lại được dữ liệu.",
        !(assetsLoaded && categoriesLoaded),
      );
    } catch (error) {
      setAssetFormError(`Không thể lưu tài sản: ${error.message}`);
    } finally {
      setAssetBusy(false);
    }
  }

  async function deleteAsset(asset) {
    if (!window.confirm(`Xóa tài sản "${asset.tenTaiSan}" (${asset.maTaiSan})?`)) return;
    try {
      await request(`${API_URL}/${asset.taiSanId}`, { method: "DELETE" });
      const refreshed = await loadAssets();
      showMessage(refreshed ? `Đã xóa tài sản ${asset.maTaiSan}.` : `Đã xóa tài sản ${asset.maTaiSan}, nhưng không tải lại được danh sách.`, !refreshed);
    } catch (error) {
      showMessage(`Không thể xóa tài sản: ${error.message}`, true);
    }
  }

  function openCategoryForm(category = null) {
    setEditingCategory(category);
    setCategoryFormOpen(true);
    window.setTimeout(() => document.getElementById("category-name")?.focus(), 0);
  }

  function closeCategoryForm() {
    setCategoryFormOpen(false);
    setEditingCategory(null);
  }

  async function handleCategorySubmit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const name = String(form.get("tenLoai")).trim();
    const nameInput = event.currentTarget.elements.namedItem("tenLoai");
    if (!name) {
      nameInput.setCustomValidity("Tên loại tài sản không được để trống.");
      nameInput.reportValidity();
      return;
    }
    nameInput.setCustomValidity("");
    setCategoryBusy(true);
    try {
      await request(editingCategory ? `${CATEGORY_URL}/${editingCategory.loaiId}` : CATEGORY_URL, {
        method: editingCategory ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tenLoai: name,
          thoiGianKhauHaoNam: Number(form.get("thoiGianKhauHaoNam") || 5),
        }),
      });
      const wasEditing = Boolean(editingCategory);
      closeCategoryForm();
      const refreshed = await refreshData();
      showMessage(!refreshed
        ? "Đã lưu loại tài sản nhưng không tải lại được dữ liệu."
        : wasEditing ? "Đã cập nhật loại tài sản." : "Đã thêm loại tài sản.", !refreshed);
    } catch (error) {
      showMessage(`Không thể lưu loại tài sản: ${error.message}`, true);
    } finally {
      setCategoryBusy(false);
    }
  }

  async function deleteCategory(category) {
    const assetCount = Number(category.soTaiSan ?? 0);
    const requestCount = Number(category.soYeuCauMuaSam ?? 0);
    if (requestCount > 0) {
      setCategoryDeleteTarget(category);
      setCategoryReplacementId("");
      showMessage("");
      return;
    }
    const impact = assetCount > 0 ? ` ${assetCount} tài sản sẽ được giữ lại nhưng bỏ phân loại.` : "";
    if (!window.confirm(`Xóa loại tài sản "${category.tenLoai}"?${impact}`)) return;
    await executeCategoryDelete(category);
  }

  async function executeCategoryDelete(category, replacementId) {
    showMessage("");
    setCategoryDeleting(true);
    try {
      const replacementQuery = replacementId ? `?replacementId=${encodeURIComponent(replacementId)}` : "";
      await request(`${CATEGORY_URL}/${category.loaiId}${replacementQuery}`, { method: "DELETE" });
      setCategoryDeleteTarget(null);
      setCategoryReplacementId("");
      const refreshed = await refreshData();
      showMessage(refreshed
        ? `Đã xóa loại tài sản "${category.tenLoai}"${category.soTaiSan > 0 && !replacementId ? "; tài sản liên quan đã được bỏ phân loại" : replacementId ? "; yêu cầu mua sắm đã chuyển sang loại thay thế" : ""}.`
        : `Đã xóa loại tài sản "${category.tenLoai}", nhưng không tải lại được dữ liệu.`, !refreshed);
    } catch (error) {
      showMessage(`Không thể xóa loại tài sản: ${error.message}`, true);
    } finally {
      setCategoryDeleting(false);
    }
  }

  async function exportAssets() {
    setExporting(true);
    showMessage("Đang chuẩn bị tệp CSV theo điều kiện tìm kiếm và bộ lọc hiện tại.");
    try {
      const params = new URLSearchParams({ page: "0", size: "100" });
      if (keyword) params.set("keyword", keyword);
      if (statusFilter) params.set("tinhTrang", statusFilter);
      if (categoryFilter) params.set("loaiId", categoryFilter);
      if (departmentFilter.trim()) params.set("phongBan", departmentFilter.trim());
      if (purchaseFromFilter) params.set("muaTuNgay", purchaseFromFilter);
      if (purchaseToFilter) params.set("muaDenNgay", purchaseToFilter);
      const firstPage = await request(`${API_URL}?${params}`);
      if (!firstPage || !Array.isArray(firstPage.content) || !Number.isInteger(firstPage.totalPages) || firstPage.totalPages < 0) {
        throw new Error("Máy chủ trả về dữ liệu phân trang không hợp lệ.");
      }
      const rows = [...firstPage.content];
      for (let pageNumber = 1; pageNumber < firstPage.totalPages; pageNumber += 1) {
        params.set("page", String(pageNumber));
        const result = await request(`${API_URL}?${params}`);
        if (!result || !Array.isArray(result.content)) throw new Error(`Không đọc được trang dữ liệu số ${pageNumber + 1}.`);
        rows.push(...result.content);
      }
      downloadCsv(rows);
      showMessage(`Đã xuất ${numberFormatter.format(rows.length)} tài sản phù hợp với bộ lọc hiện tại.`);
    } catch (error) {
      showMessage(`Không thể xuất danh sách tài sản: ${error.message}`, true);
    } finally {
      setExporting(false);
    }
  }

  async function handleCommerceSubmit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const invalidItems = commerceItems.flatMap((item, index) => {
      const missingFields = [];
      if (commerceType === "MUA") {
        const price = Number(item.donGia);
        const quantity = Number(item.soLuong);
        const isConsumable = item.loaiDong === "VAT_TU";
        if (isConsumable) {
          if (!commerceReferences.vatTu.some((consumable) => String(consumable.id) === String(item.vatTuId))) {
            missingFields.push("vật tư tiêu hao");
          }
        } else {
          if (!String(item.tenSanPham ?? "").trim()) missingFields.push("tên thiết bị");
          if (!categories.some((category) => String(category.loaiId) === String(item.loaiTaiSanId))) {
            missingFields.push("loại tài sản");
          }
          if (!commerceReferences.locations.some((location) => String(location.id) === String(item.viTriId))) {
            missingFields.push("vị trí nhận");
          }
        }
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > 1000) {
          missingFields.push("số lượng từ 1 đến 1.000");
        }
        if (!Number.isFinite(price) || price <= 0 || price > 9999999999999.99 || !Number.isInteger(price)) {
          missingFields.push("đơn giá hợp lệ");
        }
        if (!commerceReferences.suppliers.some((supplier) => String(supplier.id) === String(commerceSupplierId))) {
          missingFields.push("nhà cung cấp");
        }
      } else if (!saleableAssets.some((asset) => String(asset.taiSanId) === String(item.taiSanId))) {
        missingFields.push("tài sản thanh lý");
      } else {
        const price = Number(item.donGia);
        if (!Number.isFinite(price) || price < 0 || price > 9999999999999.99 || !Number.isInteger(price)) {
          missingFields.push("giá thanh lý hợp lệ");
        }
      }
      return missingFields.length ? [`Dòng ${index + 1}: ${missingFields.join(", ")}`] : [];
    });
    if (invalidItems.length) {
      showMessage(`Vui lòng điền đầy đủ và kiểm tra thông tin ${commerceType === "MUA" ? "đơn mua sắm" : "phiếu thanh lý"}. ${invalidItems.join("; ")}.`, true);
      return;
    }
    if (commerceType === "MUA" && (!expectedDeliveryDate || expectedDeliveryDate < localDateInputValue())) {
      showMessage("Vui lòng chọn ngày dự kiến giao là hôm nay hoặc một ngày trong tương lai.", true);
      return;
    }
    const items = commerceItems.map((item) => commerceType === "MUA"
      ? {
        loaiDong: item.loaiDong === "VAT_TU" ? "VAT_TU" : "TAI_SAN",
        tenSanPham: String(item.tenSanPham ?? "").trim(),
        loaiTaiSanId: item.loaiDong === "VAT_TU" ? null : Number(item.loaiTaiSanId),
        vatTuId: item.loaiDong === "VAT_TU" ? Number(item.vatTuId) : null,
        viTriId: item.loaiDong === "VAT_TU" ? null : Number(item.viTriId),
        warrantyEndDate: item.loaiDong === "VAT_TU" ? null : item.warrantyEndDate || null,
        donGia: Number(item.donGia),
        soLuong: Number(item.soLuong),
      }
      : { taiSanId: Number(item.taiSanId), donGia: Number(item.donGia) });
    setCommerceBusy(true);
    try {
      const order = await request(`${COMMERCE_URL}/don-hang`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          loaiDon: commerceType,
          phuongThucThanhToan: commercePayment,
          nhaCungCapId: commerceType === "MUA" ? Number(commerceSupplierId) : null,
          ngayDuKienGiao: commerceType === "MUA" ? expectedDeliveryDate : null,
          lyDoThanhLy: commerceType === "BAN" ? disposalReason : null,
          ghiChuThanhLy: commerceType === "BAN" ? String(form.get("ghiChuThanhLy") ?? "").trim() : null,
          phuongThucNhanTien: commerceType === "BAN" && disposalReceiptMethod ? disposalReceiptMethod : null,
          chiTiet: items,
        }),
      });
      setOrders((current) => [order, ...current.filter((item) => item.id !== order.id)].slice(0, 100));
      setCommercePurchaseFrom("");
      setCommercePurchaseTo("");
      setCommerceLocationFilter("");
      setCommerceItems([{ soLuong: "1", loaiDong: "TAI_SAN" }]);
      setCommerceSupplierId("");
      setExpectedDeliveryDate("");
      setDisposalReason("");
      setDisposalReceiptMethod("");
      const refreshed = await refreshData();
      showMessage(!refreshed
        ? `Đơn ${order.maDonHang} đã được tạo nhưng dữ liệu hiển thị chưa tải lại được.`
        : commerceType === "MUA"
          ? `Đơn mua ${order.maDonHang} đã được đặt; có thể xác nhận nhận hàng khi hàng về.`
          : `Đã gửi phiếu thanh lý ${order.maDonHang}; phiếu đang chờ ADMIN duyệt.`, !refreshed);
    } catch (error) {
      showMessage(`Không thể ${commerceType === "MUA" ? "tạo đơn mua" : "tạo phiếu thanh lý"}: ${error.message}`, true);
    } finally {
      setCommerceBusy(false);
    }
  }

  async function handleAssignmentSubmit(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setAssignmentBusy(true);
    try {
      const assignment = await request("http://localhost:8081/api/cap-phat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          taiSanId: Number(form.get("taiSanId")),
          nguoiNhan: String(form.get("nguoiNhan")).trim(),
          usernameNguoiNhan: String(form.get("usernameNguoiNhan") ?? "").trim() || null,
          phongBan: String(form.get("phongBan") ?? "").trim(),
          ngayBanGiao: form.get("ngayBanGiao") || null,
          nguoiBanGiao: String(form.get("nguoiBanGiao")).trim(),
          nguoiKyNhan: String(form.get("nguoiKyNhan")).trim(),
          ghiChu: String(form.get("ghiChu") ?? "").trim(),
          tinhTrangLucBanGiao: String(form.get("tinhTrangLucBanGiao")).trim(),
          ngayDuKienThuHoi: form.get("ngayDuKienThuHoi") || null,
        }),
      });
      formElement.reset();
      await Promise.all([loadAssetWorkflows(), loadAssets()]);
      showMessage(`Đã bàn giao tài sản ${assignment.maTaiSan} cho ${assignment.nguoiNhan}.`);
    } catch (error) {
      showMessage(`Không thể bàn giao tài sản: ${error.message}`, true);
    } finally {
      setAssignmentBusy(false);
    }
  }

  async function handleAssignmentReturnSubmit(event) {
    event.preventDefault();
    if (!assignmentReturnTarget) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setAssignmentActionId(assignmentReturnTarget.id);
    try {
      await request(`http://localhost:8081/api/cap-phat/${assignmentReturnTarget.id}/thu-hoi`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ngayThuHoi: form.get("ngayThuHoi"),
          lyDoThuHoi: String(form.get("lyDoThuHoi")),
          ghiChu: String(form.get("ghiChuThuHoi") ?? "").trim(),
          tinhTrangLucThuHoi: String(form.get("tinhTrangLucThuHoi")).trim(),
          tinhTrangSauThuHoi: String(form.get("tinhTrangSauThuHoi")),
          nguoiNhanLai: String(form.get("nguoiNhanLai")).trim(),
          nguoiGiaoLai: String(form.get("nguoiGiaoLai")).trim(),
        }),
      });
      let returnRequestNotice = "";
      const relatedReturnRequest = returnRequests.find((item) =>
        item.assignmentId === assignmentReturnTarget.id && item.status !== "COMPLETED");
      const requestIdToComplete = returnRequestTargetId ?? relatedReturnRequest?.id;
      if (requestIdToComplete != null) {
        try {
          await request(`http://localhost:8081/api/admin/return-requests/${requestIdToComplete}/complete`, {
            method: "PUT",
          });
          setReturnRequestTargetId(null);
        } catch (requestError) {
          returnRequestNotice = ` Phiếu thu hồi đã lưu nhưng trạng thái yêu cầu trả chưa cập nhật: ${requestError.message}`;
        }
      }
      await Promise.all([loadAssetWorkflows(), loadAssets()]);
      setReturnRepairSuggestion(String(form.get("tinhTrangSauThuHoi")) === "Bảo trì");
      showMessage(`Đã thu hồi tài sản ${assignmentReturnTarget.maTaiSan}.${returnRequestNotice}`);
      setAssignmentReturnTarget(null);
    } catch (error) {
      showMessage(`Không thể thu hồi tài sản ${assignmentReturnTarget.maTaiSan}: ${error.message}`, true);
    } finally {
      setAssignmentActionId(null);
    }
  }

  async function downloadAssignmentReturnRecord(item) {
    try {
      const headers = new Headers();
      const token = sessionStorage.getItem("officecare.accessToken");
      if (token) headers.set("Authorization", `Bearer ${token}`);
      const response = await fetchWithSession(`http://localhost:8081/api/cap-phat/${item.id}/bien-ban-thu-hoi.pdf`, { headers });
      if (!response.ok) throw new Error(`Không tải được biên bản thu hồi (HTTP ${response.status}).`);
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = `bien-ban-thu-hoi-${item.id}.pdf`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      showMessage(`Không thể tải biên bản thu hồi: ${error.message}`, true);
    }
  }

  async function downloadDisposalRecord(order) {
    try {
      await adminDownload(
        `${COMMERCE_URL}/don-hang/${order.id}/bien-ban-thanh-ly`,
        `${order.maDonHang}-bien-ban-thanh-ly.pdf`,
      );
    } catch (error) {
      showMessage(`Không thể tải biên bản thanh lý: ${error.message}`, true);
    }
  }

  async function linkAssignmentAccount(assignment) {
    const username = window.prompt(`Tên đăng nhập OfficeCare của ${assignment.nguoiNhan}:`, assignment.usernameNguoiNhan ?? "");
    if (username === null || !username.trim()) return;
    setAssignmentActionId(assignment.id);
    setAssignmentError("");
    try {
      const updated = await request(`http://localhost:8081/api/cap-phat/${assignment.id}/tai-khoan-nguoi-nhan`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username.trim() }),
      });
      setAssignments((current) => current.map((item) => item.id === updated.id ? updated : item));
      showMessage(`Đã liên kết phiếu bàn giao với tài khoản ${updated.usernameNguoiNhan}.`);
    } catch (error) {
      showMessage(`Không thể liên kết tài khoản người nhận: ${error.message}`, true);
    } finally {
      setAssignmentActionId(null);
    }
  }

  function printHandoverRecord(assignment) {
    const printWindow = window.open("", "_blank");
    if (!printWindow) {
      showMessage("Trình duyệt đã chặn cửa sổ in. Hãy cho phép cửa sổ bật lên rồi thử lại.", true);
      return;
    }
    printWindow.opener = null;
    const escape = escapeHtml;
    printWindow.document.write(`<!doctype html>
      <html lang="vi"><head><meta charset="utf-8"><title>Biên bản bàn giao ${escape(assignment.maTaiSan)}</title>
      <style>
        body{font:14px Arial,sans-serif;color:#222;max-width:800px;margin:40px auto;line-height:1.55}
        h1{text-align:center;font-size:20px;margin-bottom:4px}p{text-align:center;margin:0 0 28px}
        table{width:100%;border-collapse:collapse;margin:20px 0}
        th,td{border:1px solid #777;padding:9px;text-align:left;vertical-align:top}
        th{width:32%;background:#f4f4f4}
        .signatures{display:grid;grid-template-columns:1fr 1fr;gap:40px;margin-top:55px;text-align:center}
        .signatures div{min-height:110px}
        @media print{body{margin:12mm auto}}
      </style></head><body>
      <h1>BIÊN BẢN BÀN GIAO TÀI SẢN</h1><p>OfficeCare · Ngày bàn giao ${escape(formatDate(assignment.banGiaoLuc))}</p>
      <table>
        <tr><th>Mã tài sản</th><td>${escape(assignment.maTaiSan)}</td></tr>
        <tr><th>Tên tài sản</th><td>${escape(assignment.tenTaiSan)}</td></tr>
        <tr><th>Serial</th><td>${escape(assignment.serialNumber || "—")}</td></tr>
        <tr><th>Loại tài sản</th><td>${escape(assignment.tenLoaiTaiSan || "—")}</td></tr>
        <tr><th>Vị trí</th><td>${escape([assignment.toaNha, assignment.tang && `Tầng ${assignment.tang}`, assignment.phong && `Phòng ${assignment.phong}`].filter(Boolean).join(" · ") || "—")}</td></tr>
        <tr><th>Người/phòng nhận</th><td>${escape(assignment.nguoiNhan)}</td></tr>
        <tr><th>Phòng ban</th><td>${escape(assignment.phongBan || "—")}</td></tr>
        <tr><th>Ngày dự kiến thu hồi</th><td>${escape(formatDate(assignment.ngayDuKienThuHoi))}</td></tr>
        <tr><th>Tình trạng lúc bàn giao</th><td>${escape(assignment.tinhTrangLucBanGiao || "—")}</td></tr>
        <tr><th>Ghi chú</th><td>${escape(assignment.ghiChu || "—")}</td></tr>
      </table>
      <div class="signatures"><div>Người bàn giao<br><br><br>${escape(assignment.nguoiBanGiao || "________________")}<br>(Ký, ghi rõ họ tên)</div><div>Người nhận<br><br><br>${escape(assignment.nguoiKyNhan || "________________")}<br>(Ký, ghi rõ họ tên)</div></div>
      <script>window.onload=()=>window.print();</script></body></html>`);
    printWindow.document.close();
  }

  async function handleMaintenanceSubmit(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setMaintenanceBusy(true);
    try {
      const requestItem = await request("http://localhost:8081/api/bao-tri", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          taiSanId: Number(form.get("taiSanId")),
          tieuDe: String(form.get("tieuDe")).trim(),
          moTa: String(form.get("moTa") ?? "").trim(),
          chiPhi: Number(form.get("chiPhi") || 0),
          nguoiYeuCau: String(form.get("nguoiYeuCau") ?? "").trim(),
          ngayBaoHong: form.get("ngayBaoHong") || null,
          mucDo: String(form.get("mucDo") || "TRUNG_BINH"),
          noiXuLy: String(form.get("noiXuLy") || "NOI_BO"),
          loaiYeuCau: "SUA_CHUA",
          nguonTao: String(form.get("nguonTao") || "MANUAL"),
        }),
      });
      formElement.reset();
      setRepairAssetId("");
      window.history.replaceState(null, "", "/#repairs");
      await Promise.all([loadAssetWorkflows(), loadAssets()]);
      showMessage(`Đã tạo phiếu ${requestItem.maPhieu} cho tài sản ${requestItem.maTaiSan}.`);
    } catch (error) {
      showMessage(`Không thể tạo yêu cầu bảo trì: ${error.message}`, true);
    } finally {
      setMaintenanceBusy(false);
    }
  }

  async function updateMaintenanceStatus(item, nextStatus) {
    const cost = Number(maintenanceDrafts[item.id] ?? item.chiPhi ?? 0);
    if (!Number.isFinite(cost) || cost < 0) {
      showMessage("Chi phí phải là số không âm.", true);
      return;
    }
    const details = maintenanceDetails[item.id] ?? {};
    if (["HOAN_TAT", "KHONG_SUA_DUOC"].includes(nextStatus)
        && (!details.ketQua?.trim() || !details.nguoiThucHien?.trim() || !details.ngayHoanThanh)) {
      showMessage("Vui lòng nhập kết quả, người thực hiện và ngày hoàn thành.", true);
      return;
    }
    const actionLabel = nextStatus === "DANG_XU_LY" ? "bắt đầu sửa chữa" :
      nextStatus === "HOAN_TAT" ? "hoàn tất sửa chữa" :
        nextStatus === "KHONG_SUA_DUOC" ? "đóng phiếu vì không sửa được" : "hủy phiếu";
    if (!window.confirm(`Xác nhận ${actionLabel} yêu cầu cho tài sản ${item.maTaiSan}?`)) return;
    setMaintenanceActionId(item.id);
    try {
      await request(`http://localhost:8081/api/bao-tri/${item.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          trangThai: nextStatus,
          chiPhi: cost,
          ketQua: details.ketQua?.trim() || null,
          nguoiThucHien: details.nguoiThucHien?.trim() || null,
          ngayHoanThanh: details.ngayHoanThanh || null,
        }),
      });
      await Promise.all([loadAssetWorkflows(), loadAssets()]);
      showMessage(`Đã cập nhật yêu cầu bảo trì cho tài sản ${item.maTaiSan}.`);
    } catch (error) {
      showMessage(`Không thể cập nhật yêu cầu bảo trì: ${error.message}`, true);
    } finally {
      setMaintenanceActionId(null);
    }
  }

  const filteredRepairRequests = maintenanceRequests.filter((item) => {
    const reportedDate = item.ngayBaoHong || item.taoLuc?.slice(0, 10) || "";
    return (!repairFilters.status || item.trangThai === repairFilters.status)
      && (!repairFilters.department || (item.phongBan ?? "").toLocaleLowerCase("vi")
        .includes(repairFilters.department.trim().toLocaleLowerCase("vi")))
      && (!repairFilters.fromDate || reportedDate >= repairFilters.fromDate)
      && (!repairFilters.toDate || reportedDate <= repairFilters.toDate);
  });

  async function updateOrder(order, action) {
    const isMomoPayment = action === "momo-checkout";
    const isPayment = action === "thanh-toan-mo-phong" || isMomoPayment;
    const isApproval = action === "duyet";
    const isDisposalApproval = action === "duyet-thanh-ly";
    if (!window.confirm(isPayment
      ? `Tiếp tục thanh toán${isMomoPayment ? " MoMo" : " mô phỏng"} cho đơn ${order.maDonHang} (${privateValue(numberFormatter.format(order.tongTien))} ₫)?`
      : isApproval || isDisposalApproval
        ? `Xác nhận duyệt ${isDisposalApproval ? "phiếu thanh lý" : "và đặt đơn mua"} ${order.maDonHang}? Hành động sẽ lưu tài khoản người duyệt.`
        : `Hủy đơn ${order.maDonHang}?`)) return;
    setCommerceActionId(order.id);
    try {
      if (isMomoPayment) {
        const checkout = await request(
          `${MOMO_PAYMENT_URL}/don-hang/${order.id}/checkout`,
          { method: "POST" },
        );
        const paymentUrl = new URL(checkout.payUrl);
        if (paymentUrl.protocol !== "https:" || !paymentUrl.hostname.endsWith(".momo.vn")) {
          throw new Error("MoMo trả về liên kết thanh toán không hợp lệ.");
        }
        window.location.assign(paymentUrl.toString());
        return;
      }
      const endpoint = isApproval ? "duyet" : action;
      const result = await request(`${COMMERCE_URL}/don-hang/${order.id}/${endpoint}`, { method: "POST" });
      const refreshed = await refreshData();
      showMessage(refreshed
        ? isPayment
          ? `Đơn ${order.maDonHang} đã thanh toán thành công.`
          : isApproval
            ? `Đơn mua ${result.maDonHang} đã được duyệt và đặt.`
            : isDisposalApproval
              ? `Phiếu thanh lý ${result.maDonHang} đã được duyệt; tài sản đã chuyển sang trạng thái thanh lý.`
            : `Đơn ${result.maDonHang} đã được hủy.`
        : `Đơn ${order.maDonHang} đã cập nhật nhưng dữ liệu hiển thị chưa tải lại được.`, !refreshed);
    } catch (error) {
      showMessage(`Không thể ${isMomoPayment ? "kết nối MoMo" : isPayment ? "thanh toán" : isApproval || isDisposalApproval ? "duyệt" : "hủy"} đơn hàng: ${error.message}`, true);
    } finally {
      setCommerceActionId(null);
    }
  }

  function openReceiptDialog(order) {
    setReceiptOrder(order);
    setReceiptQuantities(Object.fromEntries(order.chiTiet.map((item) => [item.id, String(item.soLuong)])));
  }

  function closeReceiptDialog() {
    if (receiptDialogRef.current?.open) receiptDialogRef.current.close();
    setReceiptOrder(null);
  }

  async function handleReceiptSubmit(event) {
    event.preventDefault();
    if (!receiptOrder) return;
    const receiptItems = receiptOrder.chiTiet.map((item) => ({
      chiTietId: item.id,
      soLuongThucNhan: Number(receiptQuantities[item.id]),
    }));
    if (receiptItems.some((item, index) =>
      !Number.isInteger(item.soLuongThucNhan) || item.soLuongThucNhan < 0
      || item.soLuongThucNhan > receiptOrder.chiTiet[index].soLuong)
      || receiptItems.every((item) => item.soLuongThucNhan === 0)) {
      showMessage("Số lượng thực nhận phải là số nguyên từ 0 đến số lượng đã đặt và tổng phải lớn hơn 0.", true);
      return;
    }
    setReceiptBusy(true);
    try {
      const result = await request(`${COMMERCE_URL}/don-hang/${receiptOrder.id}/nhan-hang`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ chiTiet: receiptItems }),
      });
      closeReceiptDialog();
      const refreshed = await refreshData();
      showMessage(refreshed
        ? `Đã ghi nhận hàng thực nhận của đơn ${result.maDonHang} và nhập tài sản vào kho.`
        : `Đã nhận hàng đơn ${result.maDonHang}, nhưng không tải lại được dữ liệu hiển thị.`, !refreshed);
    } catch (error) {
      showMessage(`Không thể xác nhận nhận hàng: ${error.message}`, true);
    } finally {
      setReceiptBusy(false);
    }
  }

  function updateCommerceItem(index, field, value) {
    setCommerceItems((current) => current.map((item, itemIndex) => {
      if (itemIndex !== index) return item;
      if (field === "loaiDong") {
        return { soLuong: item.soLuong || "1", loaiDong: value, donGia: "", tenSanPham: "", loaiTaiSanId: "", vatTuId: "", viTriId: "", warrantyEndDate: "" };
      }
      if (field === "vatTuId") {
        const consumable = commerceReferences.vatTu.find((candidate) => String(candidate.id) === String(value));
        return { ...item, vatTuId: value, tenSanPham: consumable?.tenVatTu ?? "", donGia: String(consumable?.donGia ?? "") };
      }
      if (field === "taiSanId" && commerceType === "BAN") {
        const asset = saleableAssets.find((candidate) => String(candidate.taiSanId) === String(value));
        return { ...item, taiSanId: value, donGia: String(asset?.giaTriConLai ?? 0) };
      }
      return { ...item, [field]: value };
    }));
  }

  function addCommerceItem() {
    if (commerceItems.length >= 30) {
      showMessage("Mỗi phiếu tối đa 30 tài sản hoặc dòng sản phẩm.", true);
      return;
    }
    setCommerceItems((current) => [...current, { soLuong: "1", loaiDong: "TAI_SAN" }]);
  }

  function clearFilters() {
    setSearchInput("");
    setKeyword("");
    setStatusFilter("");
    setCategoryFilter("");
    setDepartmentSearchInput("");
    setDepartmentFilter("");
    setPurchaseFromFilter("");
    setPurchaseToFilter("");
    setPage(0);
  }

  const commerceTotal = useMemo(() => commerceItems.reduce((sum, item) => {
    if (commerceType === "MUA") {
      const price = Number(item.donGia);
      const quantity = Number(item.soLuong);
      return sum + (price > 0 && quantity > 0 ? price * quantity : 0);
    }
    return sum + (Number(item.donGia) || 0);
  }, 0), [commerceItems, commerceType, saleableAssets]);

  const disposalBookValueTotal = useMemo(() => commerceItems.reduce((sum, item) => {
    const asset = saleableAssets.find((candidate) => String(candidate.taiSanId) === String(item.taiSanId));
    return sum + (asset ? Number(asset.giaTriConLai ?? 0) : 0);
  }, 0), [commerceItems, saleableAssets]);

  const commerceLocationNeedle = commerceLocationFilter.trim().toLocaleLowerCase("vi");
  const filteredSaleableAssets = useMemo(() => saleableAssets.filter((asset) => {
    if (commercePurchaseFrom && (!asset.ngayMua || asset.ngayMua < commercePurchaseFrom)) return false;
    if (commercePurchaseTo && (!asset.ngayMua || asset.ngayMua > commercePurchaseTo)) return false;
    const location = [asset.toaNha, asset.tang, asset.phong].filter(Boolean).join(" ").toLocaleLowerCase("vi");
    return !commerceLocationNeedle || location.includes(commerceLocationNeedle);
  }), [commerceLocationNeedle, commercePurchaseFrom, commercePurchaseTo, saleableAssets]);
  const filteredOrders = useMemo(() => orders.filter((order) => {
    const orderDate = order.ngayThanhLy || (order.taoLuc ? localDateInputValue(new Date(order.taoLuc)) : "");
    if (commercePurchaseFrom && (!orderDate || orderDate < commercePurchaseFrom)) return false;
    if (commercePurchaseTo && (!orderDate || orderDate > commercePurchaseTo)) return false;
    const location = order.chiTiet.map((item) =>
      [item.toaNha, item.tang, item.phong].filter(Boolean).join(" "),
    ).join(" ").toLocaleLowerCase("vi");
    return !commerceLocationNeedle || location.includes(commerceLocationNeedle);
  }), [commerceLocationNeedle, commercePurchaseFrom, commercePurchaseTo, orders]);
  const disposalNeedle = disposalSearch.trim().toLocaleLowerCase("vi");
  const filteredCommerceOrders = filteredOrders.filter((order) => {
    if (order.loaiDon !== commerceType || commerceType !== "BAN" || !disposalNeedle) {
      return order.loaiDon === commerceType;
    }
    const searchable = [
      order.maDonHang,
      order.lyDoThanhLy,
      order.ghiChuThanhLy,
      order.nguoiYeuCau,
      order.nguoiDuyet,
      order.trangThai,
      ...order.chiTiet.flatMap((item) => [item.maTaiSan, item.tenSanPham]),
    ].filter(Boolean).join(" ").toLocaleLowerCase("vi");
    return searchable.includes(disposalNeedle);
  });

  if (!currentUser) {
    return (
      <main className="login-screen">
        <div className={`auth-shell${authMode === "register" ? " register-mode" : ""}`}>
          <section className="auth-cover" aria-label="Quản lý tài sản văn phòng OfficeCare">
            <button className="auth-theme-toggle" type="button" onClick={() => setTheme((value) => value === "dark" ? "light" : "dark")}
              aria-label={theme === "dark" ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"}>
              {theme === "dark" ? "☀" : "☾"}
            </button>
            <div className="auth-slide" key={authSlide} aria-live="off">
              <div className="auth-cover-copy">
                <span className="auth-cover-brand"><span aria-hidden="true">✦</span> OfficeCare</span>
                <p className="auth-cover-kicker">{authSlides[authSlide].kicker}</p>
                <h2>{authSlides[authSlide].title.map((line, index) => (
                  <span key={line}>{index > 0 && <br />}{line}</span>
                ))}</h2>
                <p>{authSlides[authSlide].description}</p>
              </div>
              <div className="auth-illustration" aria-hidden="true">
              {authSlide === 0 ? (
              <svg viewBox="0 0 640 410" role="presentation">
                <defs>
                  <linearGradient id="authDesk" x1="0" x2="1" y1="0" y2="1">
                    <stop offset="0" stopColor="#ffffff" stopOpacity=".96" />
                    <stop offset="1" stopColor="#e9e2ff" stopOpacity=".9" />
                  </linearGradient>
                  <linearGradient id="authScreen" x1="0" x2="1" y1="0" y2="1">
                    <stop offset="0" stopColor="#f8f6ff" />
                    <stop offset="1" stopColor="#e9e2ff" />
                  </linearGradient>
                </defs>
                <circle cx="455" cy="93" r="118" fill="#ffffff" opacity=".08" />
                <circle cx="139" cy="287" r="151" fill="#9de7d2" opacity=".12" />
                <path d="M66 348h510" stroke="#fff" strokeOpacity=".35" strokeWidth="2" />
                <rect x="130" y="58" width="374" height="244" rx="22" fill="#252041" opacity=".25" />
                <rect x="118" y="45" width="374" height="244" rx="22" fill="url(#authDesk)" />
                <rect x="137" y="64" width="336" height="205" rx="12" fill="url(#authScreen)" />
                <path d="M137 98h336" stroke="#d8d1e8" strokeWidth="1.5" />
                <circle cx="155" cy="81" r="4" fill="#fd7979" />
                <circle cx="169" cy="81" r="4" fill="#ffc76c" />
                <circle cx="183" cy="81" r="4" fill="#5ed5ae" />
                <rect x="155" y="116" width="106" height="137" rx="10" fill="#fff" />
                <text x="169" y="137" fill="#77718d" fontSize="10" fontFamily="Arial" fontWeight="700">TÀI SẢN</text>
                <rect x="169" y="151" width="78" height="36" rx="7" fill="#f3efff" />
                <rect x="178" y="159" width="22" height="18" rx="3" fill="#8759dc" />
                <path d="M182 164h14m-14 5h10" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
                <rect x="207" y="160" width="31" height="4" rx="2" fill="#c7bfdb" />
                <rect x="207" y="169" width="23" height="4" rx="2" fill="#e1dceb" />
                <rect x="169" y="196" width="78" height="36" rx="7" fill="#ecfaf6" />
                <circle cx="189" cy="214" r="10" fill="#42b99a" />
                <path d="m185 214 3 3 6-7" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                <rect x="207" y="207" width="31" height="4" rx="2" fill="#c7bfdb" />
                <rect x="207" y="216" width="23" height="4" rx="2" fill="#e1dceb" />
                <rect x="275" y="116" width="178" height="62" rx="10" fill="#fff" />
                <text x="291" y="137" fill="#77718d" fontSize="9" fontFamily="Arial" fontWeight="700">TỔNG TÀI SẢN</text>
                <text x="291" y="164" fill="#332c4d" fontSize="24" fontFamily="Arial" fontWeight="700">1.248</text>
                <rect x="382" y="145" width="51" height="21" rx="10" fill="#e8f8f2" />
                <text x="391" y="159" fill="#32a782" fontSize="8" fontFamily="Arial" fontWeight="700">+12,8%</text>
                <rect x="275" y="190" width="178" height="63" rx="10" fill="#fff" />
                <text x="291" y="209" fill="#77718d" fontSize="9" fontFamily="Arial" fontWeight="700">TÌNH TRẠNG THIẾT BỊ</text>
                <path d="M292 237c17-17 27-4 42-15s24 4 38-7 25 0 41-9" fill="none" stroke="#8455d6" strokeWidth="4" strokeLinecap="round" />
                <path d="M292 241h141" stroke="#e9e5f1" strokeWidth="1" />
                <rect x="264" y="289" width="84" height="18" rx="5" fill="#ddd7e9" />
                <path d="M302 307v13m-20 0h40" stroke="#d5cee3" strokeWidth="7" strokeLinecap="round" />
                <rect x="42" y="193" width="72" height="112" rx="12" fill="#fff" opacity=".95" />
                <rect x="53" y="207" width="50" height="34" rx="7" fill="#f0ebff" />
                <path d="M61 231h34M65 225h26M70 219h16" stroke="#8e63dd" strokeWidth="3" strokeLinecap="round" />
                <circle cx="78" cy="272" r="17" fill="#eaf9f4" />
                <path d="m70 272 5 5 11-12" fill="none" stroke="#39ad8c" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                <rect x="507" y="202" width="65" height="106" rx="11" fill="#fff" opacity=".96" />
                <path d="M522 220h35M522 231h25" stroke="#d6d0e4" strokeWidth="4" strokeLinecap="round" />
                <rect x="520" y="246" width="39" height="43" rx="8" fill="#f0ebff" />
                <path d="M529 278v-17l10-7 11 7v17m-16 0v-10h10v10" fill="none" stroke="#895cda" strokeWidth="3" strokeLinejoin="round" />
                <path d="M92 344c8-19 6-40-3-58m18 58c-2-22 5-41 18-56m-36 37c-18-6-27-18-30-34 18 2 31 12 38 29m7-8c3-18 15-30 33-36 0 19-9 33-28 42" fill="#60c5a5" />
                <rect x="69" y="340" width="47" height="12" rx="5" fill="#f3a56e" />
                <path d="M82 352h21l-4 18H86z" fill="#f3a56e" />
              </svg>
              ) : authSlide === 1 ? (
                <svg viewBox="0 0 640 410" role="presentation">
                  <circle cx="478" cy="98" r="115" fill="#ffffff" opacity=".08" />
                  <circle cx="166" cy="288" r="142" fill="#9de7d2" opacity=".12" />
                  <path d="M70 350h500" stroke="#fff" strokeOpacity=".35" strokeWidth="2" />
                  <rect x="134" y="68" width="370" height="244" rx="22" fill="#201b42" opacity=".28" />
                  <rect x="120" y="54" width="370" height="244" rx="22" fill="#f3efff" />
                  <rect x="139" y="73" width="332" height="205" rx="13" fill="#fff" />
                  <path d="M139 108h332" stroke="#ddd7e9" strokeWidth="2" />
                  <circle cx="158" cy="90" r="4" fill="#fd7979" />
                  <circle cx="172" cy="90" r="4" fill="#ffc76c" />
                  <circle cx="186" cy="90" r="4" fill="#5ed5ae" />
                  <rect x="160" y="131" width="130" height="126" rx="12" fill="#f4f0ff" />
                  <circle cx="225" cy="174" r="26" fill="#d9cdf6" />
                  <path d="M190 239c4-26 17-39 35-39s31 13 35 39" fill="#8155d5" />
                  <path d="M215 174h20m-10-10v20" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
                  <text x="179" y="223" fill="#665a82" fontSize="10" fontFamily="Arial" fontWeight="700">NHÂN VIÊN</text>
                  <rect x="310" y="131" width="137" height="55" rx="10" fill="#f7f5fc" />
                  <text x="325" y="151" fill="#79728d" fontSize="9" fontFamily="Arial" fontWeight="700">TÀI SẢN BÀN GIAO</text>
                  <text x="325" y="172" fill="#3c3454" fontSize="14" fontFamily="Arial" fontWeight="700">Laptop Dell</text>
                  <rect x="310" y="197" width="137" height="60" rx="10" fill="#eaf9f4" />
                  <circle cx="332" cy="227" r="11" fill="#42b99a" />
                  <path d="m327 227 4 4 7-8" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  <text x="350" y="223" fill="#398a76" fontSize="9" fontFamily="Arial" fontWeight="700">ĐÃ BÀN GIAO</text>
                  <text x="350" y="238" fill="#77718d" fontSize="8" fontFamily="Arial">Ngày 12/06/2026</text>
                  <rect x="273" y="298" width="78" height="16" rx="5" fill="#d8d1e8" />
                  <path d="M308 314v14m-20 0h40" stroke="#d5cee3" strokeWidth="7" strokeLinecap="round" />
                  <rect x="48" y="197" width="66" height="108" rx="12" fill="#fff" opacity=".96" />
                  <rect x="61" y="211" width="40" height="35" rx="8" fill="#f0ebff" />
                  <path d="M69 238v-15l12-8 12 8v15m-17 0v-9h10v9" fill="none" stroke="#895cda" strokeWidth="3" strokeLinejoin="round" />
                  <circle cx="81" cy="275" r="13" fill="#eaf9f4" />
                  <path d="m75 275 4 4 8-9" fill="none" stroke="#39ad8c" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
                  <rect x="518" y="195" width="68" height="108" rx="12" fill="#fff" opacity=".96" />
                  <circle cx="552" cy="228" r="15" fill="#f0ebff" />
                  <path d="m546 228 4 4 8-9" fill="none" stroke="#895cda" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M537 260h30m-25 9h20m-25 9h25" stroke="#cfc8df" strokeWidth="4" strokeLinecap="round" />
                </svg>
              ) : (
                <svg viewBox="0 0 640 410" role="presentation">
                  <circle cx="470" cy="112" r="116" fill="#ffffff" opacity=".08" />
                  <circle cx="170" cy="283" r="142" fill="#9de7d2" opacity=".12" />
                  <path d="M70 350h500" stroke="#fff" strokeOpacity=".35" strokeWidth="2" />
                  <circle cx="326" cy="190" r="112" fill="#ffffff" opacity=".08" />
                  <path d="m324 93 17 2 4 17 14 7 15-8 13 12-6 16 8 14 17 5 2 17-15 9-4 16 10 14-10 15-17-2-12 11-2 17-17 5-11-13-16-2-13 12-16-8-1-17-13-10-17 4-10-14 8-15-6-15-16-7 2-17 17-6 7-14-6-16 13-12 15 8 15-5 5-16z" fill="#d8cdf7" opacity=".9" />
                  <circle cx="326" cy="191" r="59" fill="#52458f" />
                  <circle cx="326" cy="191" r="38" fill="#f3efff" />
                  <path d="m329 165-24 34h18l-5 19 29-37h-19l7-16z" fill="#8055d4" />
                  <rect x="139" y="232" width="351" height="89" rx="16" fill="#241f48" opacity=".25" />
                  <rect x="126" y="218" width="351" height="89" rx="16" fill="#fff" />
                  <rect x="145" y="237" width="52" height="52" rx="12" fill="#f0ebff" />
                  <path d="m158 277 11-22 8 13 6-7 8 16" fill="none" stroke="#8055d4" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
                  <text x="214" y="255" fill="#77718d" fontSize="9" fontFamily="Arial" fontWeight="700">LỊCH BẢO TRÌ TIẾP THEO</text>
                  <text x="214" y="278" fill="#3c3454" fontSize="15" fontFamily="Arial" fontWeight="700">Kiểm tra thiết bị văn phòng</text>
                  <rect x="392" y="249" width="66" height="26" rx="13" fill="#eaf9f4" />
                  <circle cx="406" cy="262" r="4" fill="#42b99a" />
                  <text x="415" y="266" fill="#398a76" fontSize="8" fontFamily="Arial" fontWeight="700">ĐÚNG HẠN</text>
                  <rect x="54" y="158" width="66" height="108" rx="12" fill="#fff" opacity=".96" />
                  <circle cx="87" cy="192" r="17" fill="#eaf9f4" />
                  <path d="m79 192 6 6 11-13" fill="none" stroke="#39ad8c" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M71 225h32m-26 9h20m-26 9h26" stroke="#d0c9df" strokeWidth="4" strokeLinecap="round" />
                  <rect x="511" y="163" width="72" height="108" rx="12" fill="#fff" opacity=".96" />
                  <rect x="526" y="178" width="42" height="39" rx="8" fill="#f0ebff" />
                  <path d="M536 204v-15h22v15m-28 0h34m-25 9h16" fill="none" stroke="#895cda" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                  <circle cx="547" cy="244" r="8" fill="#f3a56e" />
                  <path d="M544 244h6" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
                </svg>
              )}
              </div>
            </div>
            <div className="auth-cover-foot">
              <span>{authSlides[authSlide].footer[0]}</span><span>·</span><span>{authSlides[authSlide].footer[1]}</span>
              <div className="auth-slide-controls" role="group" aria-label="Chọn hình giới thiệu">
                {authSlides.map((slide, index) => (
                  <button key={slide.kicker} className={`auth-slide-dot${authSlide === index ? " active" : ""}`}
                    type="button" onClick={() => setAuthSlide(index)}
                    aria-label={`Hiển thị hình ${index + 1}: ${slide.kicker}`}
                    aria-pressed={authSlide === index} />
                ))}
              </div>
            </div>
          </section>
          <form className="login-card" onSubmit={authMode === "login" ? handleLogin : handleRegister}>
            <div className="auth-form-heading">
              <span className="brand-mark" aria-hidden="true">✦</span>
              <p className="eyebrow">CHÀO MỪNG ĐẾN VỚI OFFICECARE</p>
              <h1>{authMode === "login" ? "Đăng nhập" : "Tạo tài khoản"}</h1>
              <p className="muted">{authMode === "login" ? "Đăng nhập để tiếp tục quản lý tài sản của bạn." : "Tạo tài khoản nhân viên để bắt đầu sử dụng."}</p>
            </div>
            <div className="auth-mode-switch" role="group" aria-label="Chọn đăng nhập hoặc đăng ký">
              <button className={authMode === "login" ? "active" : ""} type="button" onClick={() => setAuthMode("login")} aria-pressed={authMode === "login"}>Đăng nhập</button>
              <button className={authMode === "register" ? "active" : ""} type="button" onClick={() => { setAuthMode("register"); setLoginError(""); }} aria-pressed={authMode === "register"}>Đăng ký</button>
            </div>
            <label htmlFor="login-username">Tên đăng nhập</label>
            <input id="login-username" name="username" autoComplete="username" required maxLength="50" />
            <label htmlFor="login-password">Mật khẩu</label>
            <input id="login-password" name="password" type="password" autoComplete={authMode === "login" ? "current-password" : "new-password"} required minLength={authMode === "register" ? 8 : undefined} />
            {message.text && <p className={`message${message.error ? " error" : ""}`} role="status" aria-live="polite">{message.text}</p>}
            {authMode === "register" && <>
              <label htmlFor="register-password-confirm">Nhập lại mật khẩu</label>
              <input id="register-password-confirm" name="passwordConfirm" type="password" autoComplete="new-password" required minLength="8" />
              <small className="auth-register-note">Tài khoản đăng ký mới chỉ có quyền Nhân viên.</small>
            </>}
            {loginError && <p className="login-error" role="alert">{loginError}</p>}
            <button className="button auth-submit" type="submit" disabled={loginBusy}>
              {loginBusy ? "Đang xử lý..." : authMode === "login" ? "Đăng nhập" : "Tạo tài khoản"}
            </button>
            <p className="auth-form-foot">Bảo mật và quản lý tài sản trong một nơi.</p>
          </form>
        </div>
      </main>
    );
  }

  const route = pathname.replace(/\/+$/, "") || "/";
  const adminPage = (route === "/" && currentUser.role === "ADMIN" ? "dashboard" : {
    "/admin": "dashboard",
    "/admin/assets": "assets",
    "/admin/catalog": "catalog",
    "/admin/employees": "employees",
    "/admin/assignments": "assignments",
    "/admin/stock-in": "procurement",
    "/admin/maintenance": "maintenance",
    "/admin/disposal": "disposal",
    "/admin/invoices": "invoices",
    "/admin/consumables": "consumables",
    "/admin/stocktakes": "stocktakes",
    "/admin/reports": "reports",
    "/admin/users": "users",
    "/admin/activity": "activity",
    "/admin/purchase-requests": "procurement",
    "/admin/procurement": "procurement",
  }[route]);
  if (adminPage) {
    if (currentUser.role !== "ADMIN" && adminPage !== "stocktakes") {
      return (
        <main className="login-screen">
          <section className="login-card access-denied-card" role="alert">
            <span className="brand-mark" aria-hidden="true">!</span>
            <p className="eyebrow">TRUY CẬP BỊ TỪ CHỐI</p>
            <h1>Bạn không có quyền truy cập vào trang này</h1>
            <p className="muted">Trang quản trị chỉ dành cho tài khoản có vai trò ADMIN.</p>
            <a className="button access-denied-link" href="/">Về trang người dùng</a>
          </section>
        </main>
      );
    }
    return (
      <AdminLayout activePage={adminPage} currentUser={currentUser} onLogout={handleLogout}
        onNavigate={navigateToPath} pageLoading={pageLoading}
        theme={theme} onToggleTheme={() => setTheme((value) => value === "dark" ? "light" : "dark")}
        notifications={notifications} notificationsError={notificationsError} notificationsOpen={notificationsOpen}
        onToggleNotifications={() => {
          const opening = !notificationsOpen;
          setNotificationsOpen(opening);
          if (opening) markNotificationsRead();
        }}
        onMarkNotificationsRead={markNotificationsRead} onRefreshNotifications={refreshNotifications}
        stocktakeOnly={currentUser.role !== "ADMIN"}>
        {adminPage === "users"
          ? <UsersPage currentUser={currentUser} />
          : adminPage === "procurement"
            ? <ProcurementPage currentUser={currentUser} initialTab={route === "/admin/stock-in"
              ? "receipts" : new URLSearchParams(window.location.search).get("tab") || "requests"} />
          : adminPage === "activity"
            ? <ActivityLogPage />
            : adminPage === "disposal"
              ? <DisposalPage currentUser={currentUser} />
            : adminPage === "dashboard"
                ? <DashboardPage currentUser={currentUser} />
                : <AdminManagementPage page={adminPage} currentUser={currentUser} />}
      </AdminLayout>
    );
  }

  if (currentUser.role === "USER") {
    return <UserWorkspace currentUser={currentUser} onLogout={handleLogout}
      theme={theme} onToggleTheme={() => setTheme((value) => value === "dark" ? "light" : "dark")} />;
  }

  const categoriesOptions = categories.map((category) =>
    <option key={category.loaiId} value={category.loaiId}>{category.tenLoai}</option>);
  return (
    <div className="layout">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="OfficeCare - Tổng quan">
          <span className="brand-mark" aria-hidden="true">✦</span>
          <span>OfficeCare<small>Quản lý tài sản văn phòng</small></span>
        </a>
        <p className="muted menu-label">MENU CHÍNH</p>
        <nav className="navigation" aria-label="Điều hướng chính">
          {navigation.filter(([id, , , permission]) => id !== "returns" && hasPermission(permission)).map(([id, icon, label, , href]) => (
            <a key={id} className={activeSection === id || (id === "assignments" && activeSection === "returns") ? "active" : ""} href={href}
              aria-current={activeSection === id || (id === "assignments" && activeSection === "returns") ? "page" : undefined} aria-label={label} title={label}
              onClick={href.startsWith("#") ? (event) => {
                event.preventDefault();
                navigateToSection(id);
              } : undefined}>
              <span className="nav-icon" aria-hidden="true">{icon}</span>{label}
            </a>
          ))}
          {currentUser.role === "ADMIN" && <a className={route === "/admin" ? "active" : ""} href="/admin"
            onClick={(event) => {
              event.preventDefault();
              navigateToPath("/admin");
            }}>
            <span className="nav-icon" aria-hidden="true">⚙</span>Trang quản trị
          </a>}
        </nav>
        <div className="sidebar-footer">
          <span className="avatar">OC</span>
          <span>{currentUser.username}<small>{currentUser.role === "ADMIN" ? "Quản trị viên" : "Người dùng"}</small></span>
          <span className="footer-dots" aria-hidden="true">•••</span>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div className="breadcrumb">Không gian làm việc <span>/</span> <strong>{route === "/admin"
            ? "Tổng quan quản trị"
            : navigation.find(([id]) => id === activeSection)?.[2] || "Tổng quan"}</strong></div>
          <div className="topbar-actions">
            {activeSection === "assets" && <label className="search-box" htmlFor="asset-search">
              <span aria-hidden="true">⌕</span>
              <input ref={searchInputRef} id="asset-search" type="search" placeholder="Tìm tài sản..." autoComplete="off"
                value={searchInput} onChange={(event) => setSearchInput(event.target.value)}
                onKeyDown={(event) => { if (event.key === "Escape") setSearchInput(""); }} />
              <kbd>/</kbd>
            </label>}
            <span className="today-label">{today}</span>
            <button className="toolbar-icon" type="button" onClick={() => setTheme((value) => value === "dark" ? "light" : "dark")}
              aria-label={theme === "dark" ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"} title={theme === "dark" ? "Giao diện sáng" : "Giao diện tối"}>
              {theme === "dark" ? "☀" : "☾"}
            </button>
            <button className="toolbar-icon" type="button" onClick={() => setSensitiveValuesVisible((value) => !value)}
              aria-label={sensitiveValuesVisible ? "Ẩn số liệu" : "Hiện số liệu"} aria-pressed={!sensitiveValuesVisible}
              title={sensitiveValuesVisible ? "Ẩn số liệu" : "Hiện số liệu"}>
              {sensitiveValuesVisible ? "◉" : "◎"}
            </button>
            <NotificationControl notifications={notifications} error={notificationsError} open={notificationsOpen}
              onToggle={() => {
                const opening = !notificationsOpen;
                setNotificationsOpen(opening);
                if (opening) markNotificationsRead();
              }}
              onMarkRead={markNotificationsRead} onRefresh={refreshNotifications} />
            {!route.startsWith("/admin") && activeSection === "assets" && !assetFormOpen
              && <button className="button" type="button" onClick={() => openAssetForm()}><span aria-hidden="true">＋</span> Thêm tài sản</button>}
            <button className="button secondary" type="button" onClick={() => setPasswordDialogOpen(true)}>Đổi mật khẩu</button>
            <button className="button secondary" type="button" onClick={handleLogout} aria-label="Đăng xuất"><span aria-hidden="true">↪</span><span className="button-label">Đăng xuất</span></button>
            {pageLoading && <span className="page-loading-indicator" role="status" aria-live="polite">
              <span className="page-loading-spinner" aria-hidden="true" />
              <span>Đang tải...</span>
            </span>}
          </div>
        </header>

        <div className="dashboard page-transition-content" aria-busy={pageLoading}>
          <>
          {activeSection === "overview" && <section className="welcome" id="overview">
            <div><p className="eyebrow">TỔNG QUAN</p><h1>Chào bạn! <span aria-hidden="true">👋</span></h1><p className="welcome-copy">Đây là tình hình tài sản và thiết bị văn phòng của bạn.</p></div>
            <span className="date-chip">Hôm nay, {today}</span>
          </section>}
          {activeSection !== "overview" && <section className="welcome">
            <div><p className="eyebrow">MENU CHÍNH</p><h1>{navigation.find(([id]) => id === activeSection)?.[2]}</h1><p className="welcome-copy">Quản lý {navigation.find(([id]) => id === activeSection)?.[2].toLowerCase()} trong không gian OfficeCare.</p></div>
            <span className="date-chip">Hôm nay, {today}</span>
          </section>}
          {message.text && <p className={`message${message.error ? " error" : ""}`} role="status" aria-live="polite">{message.text}</p>}
          {returnRepairSuggestion && activeSection === "returns" && <p className="message" role="status">
            Tài sản cần kiểm tra/sửa chữa. <a className="link-button" href="/#repairs">Tạo phiếu sửa chữa ngay</a>
          </p>}

          {activeSection === "overview" && <section className="stats" aria-label="Thống kê tài sản">
            <article className="stat stat-purple"><div className="stat-top"><p>Tổng tài sản</p><span className="stat-icon" aria-hidden="true">▦</span></div><h2>{privateValue(numberFormatter.format(stats?.tongTaiSan ?? 0))}</h2><p className="stat-caption">Thiết bị đang được quản lý</p></article>
            <article className="stat stat-green"><div className="stat-top"><p>Đang sử dụng</p><span className="stat-icon" aria-hidden="true">↗</span></div><h2>{privateValue(numberFormatter.format(stats?.dangSuDung ?? 0))}</h2><p className="stat-caption">Được cấp phát cho nhân viên</p></article>
            <article className="stat stat-blue"><div className="stat-top"><p>Trong kho</p><span className="stat-icon" aria-hidden="true">▣</span></div><h2>{privateValue(numberFormatter.format(stats?.trongKho ?? 0))}</h2><p className="stat-caption">Sẵn sàng đưa vào sử dụng</p></article>
            <article className="stat stat-orange"><div className="stat-top"><p>Tổng giá trị tài sản</p><span className="stat-icon" aria-hidden="true">₫</span></div><h2>{privateValue(numberFormatter.format(stats?.tongGiaTri ?? 0))} <small>₫</small></h2><p className="stat-caption">Theo giá mua được ghi nhận</p></article>
          </section>}
          {activeSection === "overview" && <section className="panel recent-alerts-panel">
            <div className="panel-heading"><div><h2>Recent Alerts</h2><p className="muted">Cảnh báo và hoạt động mới nhất dành cho tài khoản của bạn</p></div></div>
            {notificationsError
              ? <p className="notification-empty notification-error" role="alert">Không thể tải cảnh báo: {notificationsError} <button className="text-button" type="button" onClick={refreshNotifications}>Thử lại</button></p>
              : notifications.length === 0
              ? <p className="notification-empty">Chưa có cảnh báo mới.</p>
              : <ul className="recent-alerts-list">{notifications.slice(0, 5).map((item) =>
                <li key={item.id} className={item.read ? "" : "unread"}><span>{item.message}</span><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString("vi-VN")}</time></li>)}</ul>}
          </section>}

          {activeSection === "status-overview" && <section className="insights-grid" id="status-overview">
            <article className="panel chart-panel">
              <div className="panel-heading"><div><h2>Phân bổ tài sản</h2><p className="muted">Số lượng theo tình trạng hiện tại</p></div><span className="period-label">Tất cả tài sản</span></div>
              <div className="bar-chart" aria-label="Biểu đồ số lượng tài sản theo tình trạng">
                {assetStatuses.map((status) => {
                  const count = stats?.[status.key] ?? 0;
                  const percent = stats?.tongTaiSan ? count / stats.tongTaiSan * 100 : 0;
                  return <div className="bar-group" key={status.key}><span className="bar-value">{privateValue(numberFormatter.format(count))}</span><div className="bar-track"><div className="bar-fill" style={{ height: `${stats?.tongTaiSan ? Math.max(percent, count > 0 ? 5 : 0) : 0}%` }} /></div><span className="bar-label">{status.name}</span></div>;
                })}
              </div>
            </article>
            <article className="panel chart-panel distribution-panel">
              <div className="panel-heading"><div><h2>Tình trạng sử dụng</h2><p className="muted">Tỷ trọng thiết bị theo trạng thái</p></div></div>
              <div className="donut-layout">
                <div className="donut" role="img" aria-label="Biểu đồ tình trạng tài sản"
                  style={{ background: stats?.tongTaiSan ? `conic-gradient(${assetStatuses.reduce((acc, status) => { const count = stats[status.key] ?? 0; const end = acc.offset + count / stats.tongTaiSan * 100; acc.parts.push(`${status.color} ${acc.offset}% ${end}%`); acc.offset = end; return acc; }, { parts: [], offset: 0 }).parts.join(", ")})` : "conic-gradient(#eeecf3 0 100%)" }}>
                  <div className="donut-center"><strong>{privateValue(numberFormatter.format(stats?.tongTaiSan ?? 0))}</strong><span>Tài sản</span></div>
                </div>
                <div className="legend">{assetStatuses.map((status) => <div className="legend-item" key={status.key}><span className="legend-swatch" style={{ backgroundColor: status.color }} /><span>{status.name}</span><strong>{privateValue(numberFormatter.format(stats?.[status.key] ?? 0))}</strong></div>)}</div>
              </div>
            </article>
          </section>}

          {activeSection === "assets" && <section className="panel assets-panel" id="assets">
            <div className="panel-heading assets-heading"><div><h2>Danh sách tài sản</h2><p className="muted">Theo dõi và quản lý thiết bị văn phòng</p></div><span className="asset-count">{privateValue(numberFormatter.format(totalElements))} kết quả</span></div>
            <div className="table-filters asset-advanced-filters">
              <label htmlFor="status-filter">Tình trạng<select id="status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">Tất cả tình trạng</option>{assetStatuses.map((status) => <option key={status.key}>{status.name}</option>)}</select></label>
              <label htmlFor="category-filter">Loại tài sản<select id="category-filter" value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="">Tất cả loại</option>{categoriesOptions}</select></label>
              <label htmlFor="department-filter">Phòng ban đang giữ<input id="department-filter" value={departmentSearchInput} onChange={(event) => setDepartmentSearchInput(event.target.value)} placeholder="Tên phòng ban" /></label>
              <label htmlFor="purchase-from-filter">Mua từ ngày<input id="purchase-from-filter" type="date" value={purchaseFromFilter} onChange={(event) => setPurchaseFromFilter(event.target.value)} /></label>
              <label htmlFor="purchase-to-filter">Mua đến ngày<input id="purchase-to-filter" type="date" value={purchaseToFilter} onChange={(event) => setPurchaseToFilter(event.target.value)} /></label>
              <button className="text-button" type="button" disabled={exporting} onClick={exportAssets}>{exporting ? "Đang xuất..." : "Xuất CSV"}</button>
              <button className="text-button" type="button" onClick={clearFilters}>Xóa bộ lọc</button>
            </div>
            <div className="table-wrap" aria-busy={assetsLoading}>
              <table className="asset-list-table"><thead><tr><th>MÃ TÀI SẢN</th><th>TÊN TÀI SẢN</th><th>LOẠI</th><th>GIÁ MUA</th><th>NGÀY MUA</th><th>TÌNH TRẠNG</th><th>NGƯỜI GIỮ</th><th>VỊ TRÍ HIỆN TẠI</th><th>KHẤU HAO</th><th>GIÁ TRỊ CÒN LẠI</th><th>BẢO TRÌ GẦN NHẤT</th><th>BẢO TRÌ TIẾP THEO</th><th>CHU KỲ</th><th>CHI PHÍ TÍCH LŨY</th><th>THAO TÁC</th></tr></thead>
                <tbody aria-live="polite">
                  {assetsLoading ? <TableMessage columns={15} loading>Đang tải danh sách tài sản...</TableMessage>
                    : assetError ? <TableMessage columns={15} onRetry={loadAssets}>Không thể tải dữ liệu tài sản: {assetError}</TableMessage>
                      : assets.length === 0 ? <TableMessage columns={15}>Không tìm thấy tài sản phù hợp với điều kiện lọc.</TableMessage>
                        : assets.map((asset) => <tr key={asset.taiSanId}>
                          <td className="code">{asset.maTaiSan}</td><td>{asset.tenTaiSan}</td><td>{asset.tenLoaiTaiSan}</td>
                          <td>{asset.giaMua == null ? "—" : `${privateValue(numberFormatter.format(asset.giaMua))} ₫`}</td>
                          <td>{formatDate(asset.ngayMua)}</td>
                          <td><span className={`status ${assetStatuses.find((item) => item.name === asset.tinhTrang)?.className || "in-use"}`}>{asset.tinhTrang === "Bảo trì" ? "Đang sửa chữa" : asset.tinhTrang}</span></td>
                          <td>{asset.nguoiDangGiu || "—"}{asset.phongBanDangGiu && <><br /><span className="muted">{asset.phongBanDangGiu}</span></>}</td>
                          <td>{[asset.toaNha, asset.tang && `Tầng ${asset.tang}`, asset.phong && `Phòng ${asset.phong}`].filter(Boolean).join(" · ") || "—"}</td>
                          <td>{asset.thoiGianKhauHaoNam ?? "—"} năm</td><td>{asset.giaTriConLai == null ? "—" : `${privateValue(numberFormatter.format(asset.giaTriConLai))} ₫`}</td>
                          <td>{formatDate(asset.baoTriGanNhat)}</td><td>{formatDate(asset.baoTriTiepTheo)}</td>
                          <td>{asset.chuKyBaoTriThang ?? 12} tháng</td>
                          <td>{privateValue(numberFormatter.format(asset.chiPhiBaoTriTichLuy ?? 0))} ₫</td>
                          <td className="actions"><button className="link-button detail-button" type="button" onClick={() => setSelectedAsset(asset)}>Chi tiết</button>
                            {hasWriteAccess && <><button className="link-button" type="button" onClick={() => openAssetForm(asset)}>Sửa</button><button className="delete" type="button" onClick={() => deleteAsset(asset)}>Xóa</button></>}</td>
                        </tr>)}
                </tbody>
              </table>
            </div>
            <div className="pagination"><span>{assetsLoading ? "Đang tải dữ liệu..." : totalElements === 0 ? "Không có kết quả" : `Trang ${page + 1} / ${totalPages} · ${privateValue(numberFormatter.format(totalElements))} kết quả`}</span>
              <div><button className="page-button" type="button" disabled={page <= 0 || assetsLoading} onClick={() => setPage((current) => Math.max(current - 1, 0))}>‹ Trước</button><button className="page-button" type="button" disabled={page + 1 >= totalPages || assetsLoading} onClick={() => setPage((current) => current + 1)}>Sau ›</button></div>
            </div>
          </section>}

          {activeSection === "assets" && assetFormOpen && <section className="panel form-panel" id="asset-form-panel">
            <div className="panel-heading"><div><p className="eyebrow">QUẢN LÝ TÀI SẢN</p><h2>{editingAsset ? "Cập nhật tài sản" : "Thêm tài sản mới"}</h2></div><button className="icon-button" type="button" aria-label="Đóng biểu mẫu" onClick={closeAssetForm}>×</button></div>
            <form onSubmit={handleAssetSubmit}>
              <div className="form-grid">
                <div className="field"><label htmlFor="asset-code">Mã tài sản <span>*</span></label><input id="asset-code" name="maTaiSan" defaultValue={editingAsset?.maTaiSan ?? ""} placeholder="VD: TS001" required maxLength="30" /></div>
                <div className="field"><label htmlFor="asset-name">Tên tài sản <span>*</span></label><input id="asset-name" name="tenTaiSan" defaultValue={editingAsset?.tenTaiSan ?? ""} placeholder="VD: Laptop Dell" required maxLength="150" /></div>
                <div className="field"><label htmlFor="asset-category">Loại tài sản <span>*</span></label><select id="asset-category" name="loaiTaiSanId" defaultValue={editingAsset?.loaiTaiSanId ?? ""} required><option value="">Chọn loại tài sản</option>{categoriesOptions}</select></div>
                <div className="field"><label htmlFor="asset-price">Giá mua (VNĐ)</label><input id="asset-price" name="giaMua" type="number" min="0" step="1000" defaultValue={editingAsset?.giaMua ?? ""} placeholder="VD: 15000000" /></div>
                <div className="field"><label htmlFor="asset-purchase-date">Ngày mua</label><input id="asset-purchase-date" name="ngayMua" type="date" max={localDateInputValue()} defaultValue={editingAsset?.ngayMua ?? localDateInputValue()} /></div>
                <div className="field"><label htmlFor="asset-status">Tình trạng</label><select id="asset-status" name="tinhTrang" defaultValue={editingAsset?.tinhTrang ?? "Trong kho"}>{assetStatuses.map((item) => <option key={item.key}>{item.name}</option>)}</select></div>
                <div className="field"><label htmlFor="asset-maintenance-cycle">Chu kỳ bảo trì</label><select id="asset-maintenance-cycle" name="chuKyBaoTriThang" defaultValue={editingAsset?.chuKyBaoTriThang ?? 12}><option value="1">1 tháng</option><option value="3">3 tháng</option><option value="6">6 tháng</option><option value="12">12 tháng</option></select></div>
                <div className="field"><label htmlFor="asset-building">Tòa nhà</label><input id="asset-building" name="toaNha" maxLength="100" defaultValue={editingAsset?.toaNha ?? ""} placeholder="VD: Tòa A" /></div>
                <div className="field"><label htmlFor="asset-floor">Tầng</label><input id="asset-floor" name="tang" maxLength="30" defaultValue={editingAsset?.tang ?? ""} placeholder="VD: 3" /></div>
                <div className="field"><label htmlFor="asset-room">Phòng</label><input id="asset-room" name="phong" maxLength="100" defaultValue={editingAsset?.phong ?? ""} placeholder="VD: 302" /></div>
              </div>
              {assetFormError && <p className="form-error" role="alert">{assetFormError}</p>}
              <div className="form-actions"><button className="button secondary" type="button" onClick={closeAssetForm}>Hủy</button><button className="button" type="submit" disabled={assetBusy}>{assetBusy ? "Đang lưu..." : "Lưu tài sản"}</button></div>
            </form>
          </section>}

          {activeSection === "categories" && <section className="panel assets-panel" id="categories">
            <div className="panel-heading assets-heading"><div><h2>Danh mục loại tài sản</h2><p className="muted">Quản lý nhóm thiết bị dùng trong hồ sơ tài sản</p></div><button className="button" type="button" onClick={() => openCategoryForm()}><span aria-hidden="true">＋</span> Thêm loại</button></div>
            {categoryDeleteTarget && <div className="category-delete-backdrop">
              <section className="panel category-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="legacy-category-delete-title">
                <div className="panel-heading"><div><p className="eyebrow">XÁC NHẬN XÓA</p><h2 id="legacy-category-delete-title">Xóa loại “{categoryDeleteTarget.tenLoai}”</h2></div>
                  <button className="icon-button" type="button" aria-label="Đóng" onClick={() => setCategoryDeleteTarget(null)}>×</button></div>
                <p>Loại này đang được dùng bởi {categoryDeleteTarget.soYeuCauMuaSam} yêu cầu mua sắm. Chọn loại thay thế để các yêu cầu vẫn xử lý được.</p>
                <label className="field" htmlFor="legacy-category-replacement">Chuyển yêu cầu sang loại</label>
                <select id="legacy-category-replacement" value={categoryReplacementId} onChange={(event) => setCategoryReplacementId(event.target.value)}>
                  <option value="">Chọn loại thay thế</option>
                  {categories.filter((item) => item.loaiId !== categoryDeleteTarget.loaiId).map((item) =>
                    <option key={item.loaiId} value={item.loaiId}>{item.tenLoai}</option>)}
                </select>
                <div className="form-actions">
                  <button className="button secondary" type="button" onClick={() => setCategoryDeleteTarget(null)}>Hủy</button>
                  <button className="button category-delete-submit" type="button" disabled={!categoryReplacementId || categoryDeleting}
                    onClick={() => executeCategoryDelete(categoryDeleteTarget, categoryReplacementId)}>{categoryDeleting ? "Đang xóa..." : "Chuyển liên kết và xóa"}</button>
                </div>
              </section>
            </div>}
            {categoryFormOpen && <form className="category-form" onSubmit={handleCategorySubmit}>
              <label htmlFor="category-name">Tên loại tài sản</label><input key={editingCategory?.loaiId ?? "new"} id="category-name" name="tenLoai" defaultValue={editingCategory?.tenLoai ?? ""} maxLength="100" required placeholder="VD: Máy tính" onInput={(event) => event.currentTarget.setCustomValidity("")} />
              <label htmlFor="category-depreciation">Khấu hao (năm)</label><input id="category-depreciation" name="thoiGianKhauHaoNam" type="number" min="1" max="50" defaultValue={editingCategory?.thoiGianKhauHaoNam ?? 5} required />
              <button className="button" type="submit" disabled={categoryBusy}>{categoryBusy ? "Đang lưu..." : editingCategory ? "Cập nhật loại" : "Lưu loại"}</button><button className="button secondary" type="button" onClick={closeCategoryForm}>Hủy</button>
            </form>}
            <div className="table-wrap" aria-busy={categoriesLoading}><table><thead><tr><th>TÊN LOẠI TÀI SẢN</th><th>KHẤU HAO MẶC ĐỊNH</th><th>SỐ TÀI SẢN</th><th>THAO TÁC</th></tr></thead><tbody aria-live="polite">
              {categoriesLoading ? <TableMessage columns={4} loading>Đang tải danh mục...</TableMessage>
                : categoryError ? <TableMessage columns={4} onRetry={loadCategories}>Không thể tải danh mục: {categoryError}</TableMessage>
                  : categories.length === 0 ? <TableMessage columns={4}>Chưa có loại tài sản. Hãy thêm danh mục trước khi tạo hồ sơ.</TableMessage>
                    : categories.map((category) => <tr key={category.loaiId}><td className="code">{category.tenLoai}</td><td>{category.thoiGianKhauHaoNam ?? 5} năm</td><td>{privateValue(numberFormatter.format(category.soTaiSan))}</td><td className="actions"><button className="link-button" type="button" onClick={() => openCategoryForm(category)}>Sửa</button><button className="delete" type="button" onClick={() => deleteCategory(category)}>Xóa</button></td></tr>)}
            </tbody></table></div>
          </section>}

          {activeSection === "assignments" && <section className="panel assets-panel workflow-panel" id="assignments">
            <div className="panel-heading assets-heading">
              <div><h2>Bàn giao tài sản</h2><p className="muted">Ghi nhận người nhận, phòng ban và tình trạng thiết bị lúc bàn giao.</p></div>
              <div className="admin-resource-actions">
                <span className="asset-count">{assignments.filter((item) => !item.thuHoiLuc).length} đang sử dụng</span>
                <div className="admin-resource-tabs" role="tablist" aria-label="Nghiệp vụ tài sản">
                  <button className="active" type="button" role="tab" aria-selected="true" onClick={() => navigateToSection("assignments")}>Bàn giao</button>
                  <button type="button" role="tab" aria-selected="false" onClick={() => navigateToSection("returns")}>Thu hồi</button>
                </div>
              </div>
            </div>
            <form className="workflow-form" onSubmit={handleAssignmentSubmit}>
              <label className="field">Tài sản cần bàn giao<select name="taiSanId" required disabled={!assignableAssets.length}>
                <option value="">{assignableAssets.length ? "Chọn tài sản trong kho" : "Không có tài sản trong kho"}</option>
                {assignableAssets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan}</option>)}
              </select></label>
              <label className="field">Người nhận<input name="nguoiNhan" maxLength="100" placeholder="Tên nhân viên (nếu bàn giao cá nhân)" /></label>
              <label className="field">Tài khoản OfficeCare (nếu có)<input name="usernameNguoiNhan" maxLength="50" placeholder="Tên đăng nhập của nhân viên" /></label>
              <label className="field">Phòng ban<input name="phongBan" maxLength="100" placeholder="Phòng ban (nếu bàn giao đơn vị)" /></label>
              <label className="field">Ngày bàn giao<input name="ngayBanGiao" type="date" required defaultValue={localDateInputValue()} max={localDateInputValue()} /></label>
              <label className="field">Ngày dự kiến thu hồi<input name="ngayDuKienThuHoi" type="date" min={localDateInputValue()} /></label>
              <label className="field">Người bàn giao<input name="nguoiBanGiao" required maxLength="100" defaultValue={currentUser.username} /></label>
              <label className="field">Người ký nhận<input name="nguoiKyNhan" required maxLength="100" placeholder="Họ tên người ký nhận" /></label>
              <label className="field">Tình trạng lúc bàn giao<input name="tinhTrangLucBanGiao" required maxLength="300" placeholder="VD: Hoạt động tốt, đủ phụ kiện" /></label>
              <label className="field">Ghi chú<input name="ghiChu" maxLength="500" placeholder="Ghi chú thêm (không bắt buộc)" /></label>
              <button className="button" type="submit" disabled={assignmentBusy || !assignableAssets.length}>
                {assignmentBusy ? "Đang bàn giao..." : "＋ Bàn giao tài sản"}
              </button>
            </form>
            {assignmentError
              ? <div className="workflow-error" role="alert">Không thể tải lịch sử cấp phát: {assignmentError} <button className="retry-button" type="button" onClick={loadAssetWorkflows}>Thử lại</button></div>
              : <div className="table-wrap"><table><thead><tr><th>MÃ TÀI SẢN</th><th>TÀI SẢN</th><th>NGƯỜI/PHÒNG NHẬN</th><th>PHÒNG BAN</th><th>NGÀY CẤP</th><th>DỰ KIẾN THU HỒI</th><th>TÌNH TRẠNG LÚC GIAO</th><th>NGÀY THU HỒI / TRẠNG THÁI</th><th>TÌNH TRẠNG LÚC NHẬN</th><th>GHI CHÚ</th><th>THAO TÁC</th></tr></thead><tbody>
                {assignments.length === 0 ? <TableMessage columns={11}>Chưa có lịch sử bàn giao.</TableMessage>
                  : assignments.map((item) => <tr key={item.id}>
                    <td className="code">{item.maTaiSan}</td><td>{item.tenTaiSan}</td><td>{item.nguoiNhan}</td><td>{item.phongBan || "—"}</td>
                    <td>{formatDate(item.banGiaoLuc)}</td><td>{formatDate(item.ngayDuKienThuHoi)}</td><td>{item.tinhTrangLucBanGiao || "—"}</td>
                    <td>{item.thuHoiLuc
                      ? <>{formatDate(item.thuHoiLuc)}<br /><span className="muted">{item.tinhTrangSauThuHoi === "Bảo trì" ? "Đang sửa chữa" : item.tinhTrangSauThuHoi}</span></>
                      : item.ngayDuKienThuHoi && item.ngayDuKienThuHoi < localDateInputValue()
                        ? <span className="status retired">Quá hạn thu hồi</span>
                        : <span className="status in-use">Đang sử dụng</span>}</td>
                    <td>{item.tinhTrangLucThuHoi || "—"}</td>
                    <td>{item.ghiChuThuHoi || item.ghiChu || "—"}</td>
                    <td className="actions">{!item.thuHoiLuc && <><button className="link-button" type="button" disabled={assignmentActionId === item.id} onClick={() => linkAssignmentAccount(item)}>{item.usernameNguoiNhan ? "Đổi tài khoản" : "Gắn tài khoản"}</button><a className="link-button" href={`/?repairAssetId=${item.taiSanId}&repairSource=HANDOVER#repairs`}>Báo hỏng</a></>}<button className="link-button" type="button" onClick={() => printHandoverRecord(item)}>Biên bản bàn giao</button>{item.thuHoiLuc && item.coBienBanThuHoi && <button className="link-button" type="button" onClick={() => downloadAssignmentReturnRecord(item)}>Biên bản thu hồi PDF</button>}</td>
                  </tr>)}
              </tbody></table></div>}
          </section>}

          {activeSection === "returns" && <section className="panel assets-panel workflow-panel" id="returns">
            <div className="panel-heading assets-heading">
              <div><h2>Thu hồi tài sản</h2><p className="muted">Ghi nhận tình trạng khi nhận lại và trạng thái tài sản sau thu hồi.</p></div>
              <div className="admin-resource-actions">
                <span className="asset-count">{assignments.filter((item) => !item.thuHoiLuc).length} cần thu hồi</span>
                <div className="admin-resource-tabs" role="tablist" aria-label="Nghiệp vụ tài sản">
                  <button type="button" role="tab" aria-selected="false" onClick={() => navigateToSection("assignments")}>Bàn giao</button>
                  <button className="active" type="button" role="tab" aria-selected="true" onClick={() => navigateToSection("returns")}>Thu hồi</button>
                </div>
              </div>
            </div>
            {assignmentReturnTarget && <form className="workflow-form assignment-return-form" onSubmit={handleAssignmentReturnSubmit}>
              <div className="assignment-return-heading"><strong>Thu hồi: {assignmentReturnTarget.maTaiSan} · {assignmentReturnTarget.tenTaiSan}</strong><span>Người giữ: {assignmentReturnTarget.nguoiNhan}</span></div>
              <label className="field">Ngày thu hồi<input name="ngayThuHoi" type="date" required defaultValue={localDateInputValue()} max={localDateInputValue()} /></label>
              <label className="field">Lý do thu hồi<select name="lyDoThuHoi" required defaultValue="NGHI_VIEC">
                <option value="NGHI_VIEC">Nghỉ việc</option><option value="CHUYEN_PHONG">Chuyển phòng</option>
                <option value="HET_NHU_CAU">Hết nhu cầu</option><option value="KHAC">Khác</option>
              </select></label>
              <label className="field">Tình trạng khi nhận lại<input name="tinhTrangLucThuHoi" required maxLength="300" placeholder="VD: Có trầy xước, thiếu sạc" /></label>
              <label className="field">Trạng thái sau thu hồi<select name="tinhTrangSauThuHoi" defaultValue="Trong kho">
                <option value="Trong kho">Trong kho</option><option value="Bảo trì">Đang sửa chữa / cần kiểm tra</option><option value="Chờ thanh lý">Hỏng hoàn toàn · chờ thanh lý</option>
              </select></label>
              <label className="field">Người nhận lại<input name="nguoiNhanLai" required maxLength="100" defaultValue={currentUser.username} /></label>
              <label className="field">Người giao lại<input name="nguoiGiaoLai" required maxLength="100" defaultValue={assignmentReturnTarget.nguoiNhan} /></label>
              <label className="field">Ghi chú thu hồi<input name="ghiChuThuHoi" maxLength="500" placeholder="Ghi chú thêm (không bắt buộc)" /></label>
              <div className="assignment-return-actions"><button className="button secondary" type="button" onClick={() => {
                setAssignmentReturnTarget(null);
                setReturnRequestTargetId(null);
              }}>Hủy</button><button className="button" type="submit" disabled={assignmentActionId === assignmentReturnTarget.id}>{assignmentActionId === assignmentReturnTarget.id ? "Đang lưu..." : "Xác nhận thu hồi"}</button></div>
            </form>}
            <section className="panel assets-panel">
              <div className="panel-heading"><div><h3>Yêu cầu trả tài sản từ nhân viên</h3><p className="muted">Lập phiếu thu hồi từ yêu cầu được gửi; trạng thái yêu cầu sẽ hoàn tất sau khi phiếu thu hồi được lưu.</p></div></div>
              {returnRequestError && <p className="message error" role="alert">Không tải được yêu cầu trả tài sản: {returnRequestError}</p>}
              {!returnRequestError && returnRequests.length === 0
                ? <p className="notification-empty">Chưa có yêu cầu trả tài sản.</p>
                : !returnRequestError && <div className="table-wrap"><table><thead><tr><th>NGÀY GỬI</th><th>NHÂN VIÊN</th><th>TÀI SẢN</th><th>LÝ DO</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
                  {returnRequests.map((item) => {
                    const assignment = assignments.find((row) => row.id === item.assignmentId);
                    const isCompleted = item.status === "COMPLETED" || item.returned;
                    return <tr key={item.id}>
                      <td>{formatDate(item.createdAt)}</td><td>{item.requester}</td><td>{item.maTaiSan} · {item.tenTaiSan}</td><td>{item.reason}</td>
                      <td>{isCompleted ? "Đã thu hồi" : "Chờ lập phiếu"}</td>
                      <td>{isCompleted ? "—" : assignment
                        ? <button className="link-button" type="button" onClick={() => {
                          setAssignmentReturnTarget(assignment);
                          setReturnRequestTargetId(item.id);
                        }}>Lập phiếu thu hồi</button>
                        : "Không còn tài sản đang bàn giao"}</td>
                    </tr>;
                  })}
                </tbody></table></div>}
            </section>
            {assignmentError
              ? <div className="workflow-error" role="alert">Không thể tải danh sách tài sản cần thu hồi: {assignmentError} <button className="retry-button" type="button" onClick={loadAssetWorkflows}>Thử lại</button></div>
              : <div className="table-wrap"><table><thead><tr><th>MÃ TÀI SẢN</th><th>TÀI SẢN</th><th>NGƯỜI/PHÒNG NHẬN</th><th>PHÒNG BAN</th><th>NGÀY CẤP</th><th>TÌNH TRẠNG LÚC GIAO</th><th>THAO TÁC</th></tr></thead><tbody>
                {assignments.filter((item) => !item.thuHoiLuc).length === 0
                  ? <TableMessage columns={7}>Không có tài sản đang chờ thu hồi.</TableMessage>
                  : assignments.filter((item) => !item.thuHoiLuc).map((item) => <tr key={item.id}>
                    <td className="code">{item.maTaiSan}</td><td>{item.tenTaiSan}</td><td>{item.nguoiNhan}</td><td>{item.phongBan || "—"}</td>
                    <td>{formatDate(item.banGiaoLuc)}</td><td>{item.tinhTrangLucBanGiao || "—"}</td>
                    <td className="actions"><button className="link-button" type="button" disabled={assignmentActionId === item.id} onClick={() => {
                      setAssignmentReturnTarget(item);
                      setReturnRequestTargetId(null);
                    }}>
                      {assignmentActionId === item.id ? "Đang thu hồi..." : "Thu hồi"}
                    </button><a className="link-button" href={`/?repairAssetId=${item.taiSanId}&repairSource=RETURN#repairs`}>Tạo phiếu sửa</a></td>
                  </tr>)}
              </tbody></table></div>}
          </section>}

          {activeSection === "maintenance" && <section className="panel assets-panel workflow-panel" id="maintenance">
            <div className="panel-heading assets-heading">
              <div><h2>Tạo yêu cầu bảo trì</h2><p className="muted">Ghi nhận thiết bị cần kiểm tra, bảo dưỡng định kỳ hoặc xử lý sự cố.</p></div>
            </div>
            <form className="workflow-form maintenance-form" onSubmit={handleMaintenanceSubmit}>
              <label className="field">Tài sản cần bảo trì<select name="taiSanId" required disabled={!maintenanceAssets.length}>
                <option value="">{maintenanceAssets.length ? "Chọn tài sản" : "Không có tài sản phù hợp"}</option>
                {maintenanceAssets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan}{asset.tinhTrang === "Đang sử dụng" ? " · Đang cấp phát" : ""}</option>)}
              </select></label>
              <label className="field">Nội dung yêu cầu<input name="tieuDe" required maxLength="150" placeholder="VD: Kiểm tra máy tính không khởi động" /></label>
              <label className="field">Mô tả<input name="moTa" maxLength="1000" placeholder="Triệu chứng hoặc ghi chú" /></label>
              <label className="field">Chi phí dự kiến (VNĐ)<input name="chiPhi" type="number" min="0" step="1" defaultValue="0" /></label>
              <button className="button" type="submit" disabled={maintenanceBusy || !maintenanceAssets.length}>
                {maintenanceBusy ? "Đang ghi nhận..." : "＋ Ghi nhận yêu cầu"}
              </button>
            </form>
          </section>}

          {activeSection === "repairs" && <section className="panel assets-panel workflow-panel" id="repairs">
            <div className="panel-heading assets-heading">
              <div><h2>Danh sách phiếu sửa chữa</h2><p className="muted">Theo dõi tiếp nhận, xử lý, chi phí và lịch sử sửa chữa của tài sản.</p></div>
              <span className="asset-count">{maintenanceRequests.filter((item) => ["YEU_CAU", "DANG_XU_LY"].includes(item.trangThai)).length} đang sửa</span>
            </div>
            <form className="workflow-form maintenance-form" onSubmit={handleMaintenanceSubmit}>
              <label className="field">Tài sản<select name="taiSanId" required disabled={!maintenanceAssets.length}
                value={repairAssetId} onChange={(event) => setRepairAssetId(event.target.value)}>
                <option value="">{maintenanceAssets.length ? "Chọn tài sản" : "Không có tài sản phù hợp"}</option>
                {maintenanceAssets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan}</option>)}
              </select></label>
              <label className="field">Người báo hỏng<input name="nguoiYeuCau" required maxLength="100" defaultValue={currentUser.username} /></label>
              <label className="field">Ngày báo<input name="ngayBaoHong" type="date" required defaultValue={localDateInputValue()} max={localDateInputValue()} /></label>
              <label className="field">Mức độ<select name="mucDo" defaultValue="TRUNG_BINH">
                <option value="NHE">Nhẹ</option><option value="TRUNG_BINH">Trung bình</option><option value="NGHIEM_TRONG">Nghiêm trọng</option>
              </select></label>
              <label className="field">Nơi xử lý<select name="noiXuLy" defaultValue="NOI_BO">
                <option value="NOI_BO">Nội bộ</option><option value="NHA_CUNG_CAP">Nhà cung cấp</option>
              </select></label>
              <label className="field">Mô tả lỗi<input name="tieuDe" required maxLength="150" placeholder="Mô tả ngắn lỗi cần xử lý" /></label>
              <label className="field">Chi tiết<textarea name="moTa" maxLength="1000" rows="2" placeholder="Triệu chứng, phụ kiện liên quan..." /></label>
              <input type="hidden" name="nguonTao" value={new URLSearchParams(window.location.search).get("repairSource") || "MANUAL"} />
              <button className="button" type="submit" disabled={maintenanceBusy || !maintenanceAssets.length}>
                {maintenanceBusy ? "Đang tạo phiếu..." : "＋ Tạo phiếu sửa chữa"}
              </button>
            </form>
            {maintenanceError
              ? <div className="workflow-error" role="alert">Không thể tải danh sách sửa chữa: {maintenanceError} <button className="retry-button" type="button" onClick={loadAssetWorkflows}>Thử lại</button></div>
              : <>
                <div className="admin-asset-filters">
                  <label>Trạng thái<select value={repairFilters.status} onChange={(event) => setRepairFilters({ ...repairFilters, status: event.target.value })}>
                    <option value="">Tất cả trạng thái</option><option value="YEU_CAU">Chờ xử lý</option><option value="DANG_XU_LY">Đang sửa</option>
                    <option value="HOAN_TAT">Hoàn thành</option><option value="KHONG_SUA_DUOC">Không sửa được</option>
                  </select></label>
                  <label>Phòng ban<input value={repairFilters.department} onChange={(event) => setRepairFilters({ ...repairFilters, department: event.target.value })} placeholder="Lọc phòng ban" /></label>
                  <label>Từ ngày<input type="date" value={repairFilters.fromDate} max={repairFilters.toDate || undefined} onChange={(event) => setRepairFilters({ ...repairFilters, fromDate: event.target.value })} /></label>
                  <label>Đến ngày<input type="date" value={repairFilters.toDate} min={repairFilters.fromDate || undefined} onChange={(event) => setRepairFilters({ ...repairFilters, toDate: event.target.value })} /></label>
                  <button className="text-button" type="button" onClick={() => setRepairFilters({ status: "", department: "", fromDate: "", toDate: "" })}>Xóa bộ lọc</button>
                </div>
                <div className="table-wrap"><table><thead><tr><th>MÃ PHIẾU / TÀI SẢN</th><th>NGƯỜI BÁO / NGÀY</th><th>LỖI / MỨC ĐỘ</th><th>NƠI XỬ LÝ / PHÒNG BAN</th><th>TRẠNG THÁI</th><th>KẾT QUẢ / NGƯỜI THỰC HIỆN</th><th>NGÀY HOÀN THÀNH</th><th>CHI PHÍ (VNĐ)</th><th>THAO TÁC</th></tr></thead><tbody>
                {filteredRepairRequests.length === 0 ? <TableMessage columns={9}>Không có phiếu sửa chữa phù hợp bộ lọc.</TableMessage>
                  : filteredRepairRequests.map((item) => {
                    const active = ["YEU_CAU", "DANG_XU_LY"].includes(item.trangThai);
                    const details = maintenanceDetails[item.id] ?? {
                      ketQua: item.ketQua ?? "",
                      nguoiThucHien: item.nguoiThucHien ?? currentUser.username,
                      ngayHoanThanh: item.ngayHoanThanh ?? localDateInputValue(),
                    };
                    const statusLabel = {
                      YEU_CAU: "Chờ xử lý", DANG_XU_LY: "Đang sửa",
                      HOAN_TAT: "Hoàn thành", KHONG_SUA_DUOC: "Không sửa được", DA_HUY: "Đã hủy",
                    }[item.trangThai] ?? item.trangThai;
                    return <tr key={item.id}>
                      <td><span className="code">{item.maPhieu || `SC-${String(item.id).padStart(6, "0")}`}</span><br /><strong>{item.maTaiSan} · {item.tenTaiSan}</strong></td>
                      <td>{item.nguoiYeuCau || "—"}<br /><span className="muted">{formatDate(item.ngayBaoHong || item.taoLuc)}</span></td>
                      <td><strong>{item.tieuDe}</strong>{item.moTa && <><br /><span className="muted">{item.moTa}</span></>}<br /><span className="status">{({ NHE: "Nhẹ", TRUNG_BINH: "Trung bình", NGHIEM_TRONG: "Nghiêm trọng" })[item.mucDo] || "—"}</span></td>
                      <td>{item.noiXuLy === "NHA_CUNG_CAP" ? "Nhà cung cấp" : "Nội bộ"}<br /><span className="muted">{item.phongBan || "—"}</span></td>
                      <td>{statusLabel}</td>
                      <td>{active ? <input aria-label={`Kết quả ${item.maPhieu}`} value={details.ketQua}
                        onChange={(event) => setMaintenanceDetails((current) => ({ ...current, [item.id]: { ...details, ketQua: event.target.value } }))}
                        placeholder="Kết quả xử lý" maxLength="1000" /> : <>{item.ketQua || "—"}<br /><span className="muted">{item.nguoiThucHien || ""}</span></>}</td>
                      <td>{active ? <><input aria-label={`Người thực hiện ${item.maPhieu}`} value={details.nguoiThucHien}
                        onChange={(event) => setMaintenanceDetails((current) => ({ ...current, [item.id]: { ...details, nguoiThucHien: event.target.value } }))}
                        placeholder="Người thực hiện" maxLength="100" /><input aria-label={`Ngày hoàn thành ${item.maPhieu}`} type="date" value={details.ngayHoanThanh}
                        onChange={(event) => setMaintenanceDetails((current) => ({ ...current, [item.id]: { ...details, ngayHoanThanh: event.target.value } }))} max={localDateInputValue()} /></>
                        : formatDate(item.ngayHoanThanh || item.hoanTatLuc)}</td>
                      <td>{active
                        ? <input className="workflow-cost" aria-label={`Chi phí ${item.maTaiSan}`} type="number" min="0" step="1"
                          value={maintenanceDrafts[item.id] ?? item.chiPhi}
                          onChange={(event) => setMaintenanceDrafts((drafts) => ({ ...drafts, [item.id]: event.target.value }))} />
                        : `${privateValue(numberFormatter.format(item.chiPhi))} ₫`}</td>
                      <td className="actions">{active ? <>
                        {item.trangThai === "YEU_CAU" && <button className="link-button" type="button" disabled={maintenanceActionId === item.id} onClick={() => updateMaintenanceStatus(item, "DANG_XU_LY")}>Bắt đầu sửa</button>}
                        {item.trangThai === "DANG_XU_LY" && <>
                          <button className="link-button" type="button" disabled={maintenanceActionId === item.id} onClick={() => updateMaintenanceStatus(item, "HOAN_TAT")}>Hoàn thành</button>
                          <button className="delete" type="button" disabled={maintenanceActionId === item.id} onClick={() => updateMaintenanceStatus(item, "KHONG_SUA_DUOC")}>Không sửa được</button>
                        </>}
                      </> : item.hoanTatLuc ? new Date(item.hoanTatLuc).toLocaleDateString("vi-VN") : "—"}</td>
                    </tr>;
                  })}
                </tbody></table></div>
              </>}
          </section>}

          {(activeSection === "purchase" || activeSection === "sale") && <section className="panel commerce-panel" id={activeSection}>
            <div className="panel-heading"><div><p className="eyebrow">{commerceType === "MUA" ? "MUA SẮM" : "THANH LÝ"}</p><h2>{commerceType === "MUA" ? "Đặt hàng mua sắm" : "Thanh lý"}</h2><p className="muted">{commerceType === "MUA"
              ? "Tạo đơn từ yêu cầu đã được duyệt; tài sản chỉ được tạo khi xác nhận nhận hàng."
              : "Tạo phiếu thanh lý để trình duyệt; tài sản chỉ được thanh lý sau khi ADMIN phê duyệt."}</p></div></div>
            <div className="commerce-filter-section">
              <strong>{commerceType === "MUA" ? "Lọc lịch sử mua sắm" : "Lọc tài sản và lịch sử thanh lý"}</strong>
              <div className="table-filters commerce-filters">
                <label htmlFor="commerce-purchase-from">{commerceType === "MUA" ? "Đơn mua từ ngày" : "Phiếu từ ngày"}<input id="commerce-purchase-from" type="date" max={commercePurchaseTo || undefined} value={commercePurchaseFrom} onChange={(event) => setCommercePurchaseFrom(event.target.value)} /></label>
                <label htmlFor="commerce-purchase-to">{commerceType === "MUA" ? "Đơn mua đến ngày" : "Phiếu đến ngày"}<input id="commerce-purchase-to" type="date" min={commercePurchaseFrom || undefined} value={commercePurchaseTo} onChange={(event) => setCommercePurchaseTo(event.target.value)} /></label>
                <label htmlFor="commerce-location-filter">{commerceType === "MUA" ? "Vị trí nhận" : "Vị trí"}<input id="commerce-location-filter" value={commerceLocationFilter} onChange={(event) => setCommerceLocationFilter(event.target.value)} placeholder={commerceType === "MUA" ? "Lọc theo vị trí nhận" : "Tòa nhà, tầng hoặc phòng"} /></label>
                {commerceType === "BAN" && <label htmlFor="disposal-search">Tìm phiếu / tài sản<input id="disposal-search" value={disposalSearch} onChange={(event) => setDisposalSearch(event.target.value)} placeholder="Mã phiếu, mã tài sản, lý do, người duyệt" /></label>}
                <button className="text-button" type="button" onClick={() => {
                  setCommercePurchaseFrom("");
                  setCommercePurchaseTo("");
                  setCommerceLocationFilter("");
                  setDisposalSearch("");
                }}>Xóa bộ lọc</button>
                <span className="commerce-filter-hint">{commerceType === "MUA"
                  ? "Bộ lọc chỉ áp dụng cho lịch sử đơn mua sắm, không ảnh hưởng thông tin tạo đơn."
                  : "Bộ lọc áp dụng cho tài sản trong kho và lịch sử thanh lý."}</span>
              </div>
            </div>
            <form noValidate onSubmit={handleCommerceSubmit}>
              {commerceType === "MUA" && <div className="commerce-purchase-details">
                <label>Nhà cung cấp<select required value={commerceSupplierId} onChange={(event) => setCommerceSupplierId(event.target.value)}><option value="">Chọn nhà cung cấp</option>{commerceReferences.suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.tenNhaCungCap}</option>)}</select></label>
                <label>Ngày dự kiến giao<input type="date" required min={localDateInputValue()} value={expectedDeliveryDate} onChange={(event) => setExpectedDeliveryDate(event.target.value)} /></label>
                <label>Phương thức thanh toán<select value={commercePayment} onChange={(event) => setCommercePayment(event.target.value)}><option value="TIEN_MAT">Thanh toán khi nhận hàng</option><option value="VIETQR">Chuyển khoản VietQR (đối chiếu thủ công)</option><option value="MOMO">MoMo (thanh toán trực tuyến)</option></select></label>
              </div>}
              {commerceType === "BAN" && <div className="commerce-purchase-details">
                <label>Lý do thanh lý<select required value={disposalReason} onChange={(event) => setDisposalReason(event.target.value)}>
                  <option value="">Chọn lý do thanh lý</option>
                  <option value="HONG_KHONG_SUA_DUOC">Hỏng không sửa được</option>
                  <option value="LOI_THOI">Lỗi thời</option>
                  <option value="MAT">Mất</option>
                  <option value="HET_NIEN_HAN">Hết niên hạn sử dụng</option>
                  <option value="NHUONG_LAI">Nhượng lại</option>
                </select></label>
                <label>Chi tiết / căn cứ thanh lý<textarea name="ghiChuThanhLy" maxLength="1000" rows="2" placeholder="Mô tả tình trạng, biên bản kiểm tra hoặc căn cứ đề xuất (không bắt buộc)" /></label>
                <label>Phương thức nhận tiền (nếu có)<select value={disposalReceiptMethod} onChange={(event) => setDisposalReceiptMethod(event.target.value)}>
                  <option value="">Không phát sinh tiền</option>
                  <option value="CHUYEN_KHOAN">Chuyển khoản</option>
                  <option value="NOP_QUY">Nộp quỹ</option>
                </select></label>
                <label>Người yêu cầu thanh lý<input value={currentUser.username} readOnly /></label>
                <div className="commerce-approval-note">
                  <strong>Người duyệt thanh lý</strong>
                  <span>{currentUser.role === "ADMIN"
                    ? "Cần thao tác duyệt riêng; khi ADMIN tự yêu cầu, chính tài khoản đó sẽ được ghi nhận là người duyệt."
                    : "Chỉ ADMIN mới có thể duyệt phiếu và cập nhật trạng thái tài sản."}</span>
                </div>
              </div>}
              <div className="commerce-items">
                {commerceItems.map((item, index) => <div className={`commerce-item ${commerceType === "MUA" ? "commerce-buy-row" : "commerce-sale-row"}`} key={index}>
                  {commerceType === "MUA" ? <>
                    <label className="field">Loại mặt hàng<select value={item.loaiDong ?? "TAI_SAN"} onChange={(event) => updateCommerceItem(index, "loaiDong", event.target.value)}>
                      <option value="TAI_SAN">Tài sản / thiết bị</option><option value="VAT_TU">Vật tư tiêu hao</option>
                    </select></label>
                    {item.loaiDong === "VAT_TU"
                      ? <label className="field">Vật tư tiêu hao<select required value={item.vatTuId ?? ""} onChange={(event) => updateCommerceItem(index, "vatTuId", event.target.value)}>
                        <option value="">Chọn vật tư</option>{commerceReferences.vatTu.map((consumable) => <option key={consumable.id} value={consumable.id}>{consumable.tenVatTu} · {consumable.donVi}</option>)}
                      </select></label>
                      : <>
                        <label className="field">Tên thiết bị<input name="tenSanPham" maxLength="150" required placeholder="VD: Laptop văn phòng" value={item.tenSanPham ?? ""} onChange={(event) => updateCommerceItem(index, "tenSanPham", event.target.value)} /></label>
                        <label className="field">Loại tài sản<select name="loaiTaiSanId" required value={item.loaiTaiSanId ?? ""} onChange={(event) => updateCommerceItem(index, "loaiTaiSanId", event.target.value)}><option value="">Chọn loại tài sản</option>{categoriesOptions}</select></label>
                      </>}
                    <label className="field">Số lượng<input name="soLuong" type="number" min="1" max="1000" required value={item.soLuong ?? "1"} onChange={(event) => updateCommerceItem(index, "soLuong", event.target.value)} /></label>
                    {item.loaiDong === "VAT_TU" && <span className="muted">{commerceReferences.vatTu.find((row) => String(row.id) === String(item.vatTuId))?.donVi || "Đơn vị tính"}</span>}
                    <label className="field">Đơn giá (VNĐ)<input type="number" min="1" step="1" required value={item.donGia ?? ""} onChange={(event) => updateCommerceItem(index, "donGia", event.target.value)} /></label>
                    {item.loaiDong !== "VAT_TU" && <>
                      <label className="field">Vị trí nhận<select required value={item.viTriId ?? ""} onChange={(event) => updateCommerceItem(index, "viTriId", event.target.value)}><option value="">Chọn vị trí nhận</option>{commerceReferences.locations.map((location) => <option key={location.id} value={location.id}>{[location.toaNha, location.tang && `Tầng ${location.tang}`, location.phong && `Phòng ${location.phong}`].filter(Boolean).join(" · ") || `Vị trí #${location.id}`}</option>)}</select></label>
                      <label className="field">Hạn bảo hành<input type="date" value={item.warrantyEndDate ?? ""} onChange={(event) => updateCommerceItem(index, "warrantyEndDate", event.target.value)} /></label>
                    </>}
                  </> : <>
                    <label className="field">Tài sản đề xuất thanh lý<select name="taiSanId" required value={item.taiSanId ?? ""} onChange={(event) => updateCommerceItem(index, "taiSanId", event.target.value)}><option value="">{filteredSaleableAssets.length ? "Chọn tài sản" : "Không có tài sản phù hợp bộ lọc"}</option>{filteredSaleableAssets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan} · Giá trị còn lại: {privateValue(numberFormatter.format(asset.giaTriConLai ?? 0))} ₫</option>)}</select></label>
                    <div className="commerce-book-value">Giá trị còn lại: <strong>{privateValue(numberFormatter.format(saleableAssets.find((asset) => String(asset.taiSanId) === String(item.taiSanId))?.giaTriConLai ?? 0))} ₫</strong></div>
                    <label className="field">Giá thanh lý (nếu bán/nhượng)<input aria-label="Giá thanh lý nếu có" type="number" min="0" step="1" value={item.donGia ?? ""} onChange={(event) => updateCommerceItem(index, "donGia", event.target.value)} /><small>Để trống hoặc nhập 0 nếu không phát sinh tiền.</small></label>
                    <span className="commerce-line-total">Lãi / (lỗ): {privateValue(numberFormatter.format((Number(item.donGia) || 0) - Number(saleableAssets.find((asset) => String(asset.taiSanId) === String(item.taiSanId))?.giaTriConLai ?? 0)))} ₫</span>
                  </>}
                  <button className="icon-button commerce-remove" type="button" aria-label="Xóa dòng sản phẩm" onClick={() => setCommerceItems((current) => current.length > 1 ? current.filter((_, itemIndex) => itemIndex !== index) : [{ soLuong: "1", loaiDong: "TAI_SAN" }])}>×</button>
                </div>)}
              </div>
              <div className="commerce-actions"><button className="button secondary" type="button" onClick={addCommerceItem}>＋ {commerceType === "MUA" ? "Thêm sản phẩm" : "Thêm tài sản thanh lý"}</button><strong>{commerceType === "MUA" ? "Tổng đơn" : "Tổng giá trị thanh lý"}: <span>{privateValue(numberFormatter.format(commerceTotal))} ₫</span>{commerceType === "BAN" && <small className="commerce-total-book">Giá trị còn lại: {privateValue(numberFormatter.format(disposalBookValueTotal))} ₫ · Lãi / (lỗ): {privateValue(numberFormatter.format(commerceTotal - disposalBookValueTotal))} ₫</small>}</strong><button className="button" type="submit" disabled={commerceBusy}>{commerceBusy ? "Đang gửi..." : commerceType === "MUA" ? "Đặt hàng" : "Gửi duyệt thanh lý"}</button></div>
            </form>
            <div className="commerce-history"><div className="panel-heading"><div><h2>{commerceType === "MUA" ? "Lịch sử mua sắm" : "Lịch sử thanh lý"}</h2><p className="muted">Hiển thị tối đa 100 phiếu gần nhất.</p></div></div>
              <div className="table-wrap" aria-busy={commerceLoading}><table><thead><tr><th>MÃ PHIẾU</th><th>{commerceType === "MUA" ? "NGÀY YÊU CẦU" : "NGÀY THANH LÝ / TẠO PHIẾU"}</th><th>{commerceType === "MUA" ? "NHÀ CUNG CẤP / LÝ DO" : "LÝ DO THANH LÝ"}</th><th>{commerceType === "MUA" ? "SẢN PHẨM / SỐ LƯỢNG" : "TÀI SẢN / GIÁ TRỊ CÒN LẠI"}</th><th>{commerceType === "MUA" ? "VỊ TRÍ NHẬN / DỰ TOÁN" : "GIÁ THANH LÝ"}</th><th>{commerceType === "MUA" ? "DỰ KIẾN GIAO" : "PHƯƠNG THỨC NHẬN TIỀN"}</th><th>NGƯỜI YÊU CẦU / DUYỆT</th><th>{commerceType === "MUA" ? "DỰ TOÁN / ĐƠN GIÁ" : "LÃI / (LỖ)"}</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody aria-live="polite">
                {commerceLoading ? <TableMessage columns={10} loading>Đang tải đơn hàng...</TableMessage>
                  : commerceError ? <TableMessage columns={10} onRetry={loadCommerce}>Không thể tải lịch sử đơn hàng: {commerceError}</TableMessage>
                    : !orders.some((order) => order.loaiDon === commerceType) ? <TableMessage columns={10}>Chưa có đơn {commerceType === "MUA" ? "mua sắm" : "thanh lý"}.</TableMessage>
                      : filteredCommerceOrders.length === 0 ? <TableMessage columns={10}>Không có đơn hàng phù hợp với bộ lọc.</TableMessage>
                      : filteredCommerceOrders.map((order) => {
                        const overdue = order.loaiDon === "MUA" && order.ngayDuKienGiao
                          && order.ngayDuKienGiao < localDateInputValue()
                          && !["DA_NHAN", "DA_HUY"].includes(order.trangThai);
                        const orderStatus = ({
                          CHO_DUYET: "Chờ duyệt",
                          CHO_DUYET_THANH_LY: "Chờ duyệt thanh lý",
                          DA_DAT: "Đã đặt",
                          DA_NHAN: "Đã nhận đủ",
                          NHAN_MOT_PHAN: "Nhận một phần",
                          DA_THANH_LY: "Đã thanh lý",
                          DA_HUY: "Đã hủy",
                          CHO_THANH_TOAN: "Chờ thanh toán",
                          HOAN_TAT: "Hoàn tất",
                        })[order.trangThai] ?? order.trangThai;
                        const isOrderOwner = !order.nguoiYeuCau || order.nguoiYeuCau.toLocaleLowerCase("vi") === currentUser.username.toLocaleLowerCase("vi");
                        const isAdmin = currentUser.role === "ADMIN";
                        const canCancel = ["CHO_DUYET_THANH_LY", "DA_DAT", "CHO_THANH_TOAN"].includes(order.trangThai);
                        const canCancelForOrder = canCancel
                          && (isAdmin || isOrderOwner);
                        const canPay = isAdmin
                          && ["MOCK_ONLINE", "MOMO"].includes(order.phuongThucThanhToan)
                          && (order.loaiDon === "MUA"
                            ? order.trangThai === "DA_NHAN" && order.trangThaiThanhToan !== "DA_THANH_TOAN"
                            : order.trangThai === "CHO_THANH_TOAN");
                        return <tr key={order.id}>
                          <td className="code">{order.maDonHang}</td>
                          <td>{formatDate(order.ngayThanhLy || order.taoLuc)}</td>
                          <td>{order.loaiDon === "MUA" ? order.nhaCungCap || "—" : <>{({
                            HONG: "Hỏng", HONG_KHONG_SUA_DUOC: "Hỏng không sửa được",
                            LOI_THOI: "Lỗi thời", MAT: "Mất", NHUONG_LAI: "Nhượng lại",
                            HET_NIEN_HAN: "Hết niên hạn sử dụng",
                          })[order.lyDoThanhLy] || "—"}{order.ghiChuThanhLy && <><br /><span className="muted">{order.ghiChuThanhLy}</span></>}</>}</td>
                          <td>{order.chiTiet.map((item) => order.loaiDon === "MUA"
                            ? `${item.tenSanPham} × ${privateValue(String(item.soLuong))}${["DA_DAT", "NHAN_MOT_PHAN", "DA_NHAN"].includes(order.trangThai) ? ` (nhận ${privateValue(String(item.soLuongDaNhan ?? 0))})` : ""}`
                            : `${item.maTaiSan || item.tenSanPham}: còn lại ${privateValue(numberFormatter.format(item.giaTriConLai ?? 0))} ₫`).join(", ")}</td>
                          <td>{order.loaiDon === "MUA"
                            ? [...new Set(order.chiTiet.map((item) => [item.toaNha, item.tang && `Tầng ${item.tang}`, item.phong && `Phòng ${item.phong}`].filter(Boolean).join(" · ")).filter(Boolean))].join(", ") || "—"
                            : `${privateValue(numberFormatter.format(order.tongTien))} ₫`}</td>
                          <td>{order.loaiDon === "MUA"
                            ? order.ngayDuKienGiao ? <>{formatDate(order.ngayDuKienGiao)}{overdue && <span className="commerce-overdue">Quá hạn</span>}</> : "Chưa đặt hàng"
                            : order.phuongThucNhanTien === "CHUYEN_KHOAN" ? "Chuyển khoản" : order.phuongThucNhanTien === "NOP_QUY" ? "Nộp quỹ" : "—"}</td>
                          <td>Yêu cầu: {order.nguoiYeuCau || "—"}<br />Duyệt: {order.nguoiDuyet || "—"}</td>
                          <td>{order.loaiDon === "MUA" ? `${privateValue(numberFormatter.format(order.tongTien))} ₫` : `${privateValue(numberFormatter.format(order.laiLo ?? 0))} ₫`}</td>
                          <td><strong>{orderStatus}</strong>{order.loaiDon === "MUA" && <><br /><span className="muted">{order.trangThaiThanhToan === "DA_THANH_TOAN" ? "Đã thanh toán" : "Chưa thanh toán"}</span></>}</td>
                          <td className="actions">
                            {order.trangThai === "CHO_DUYET" && currentUser.role === "ADMIN" && <button className="link-button" type="button" disabled={commerceActionId === order.id} onClick={() => updateOrder(order, "duyet")}>Duyệt & đặt</button>}
                            {order.trangThai === "CHO_DUYET_THANH_LY" && isAdmin && <button className="link-button" type="button" disabled={commerceActionId === order.id} onClick={() => updateOrder(order, "duyet-thanh-ly")}>Duyệt thanh lý</button>}
                            {["DA_DAT", "NHAN_MOT_PHAN"].includes(order.trangThai) && currentUser.role === "ADMIN"
                              && <button className="link-button" type="button" onClick={() => window.location.assign("/admin/stock-in")}>Nhập kho</button>}
                            {canPay && <button className="link-button" type="button" disabled={commerceActionId === order.id}
                              onClick={() => updateOrder(order, order.phuongThucThanhToan === "MOMO" ? "momo-checkout" : "thanh-toan-mo-phong")}>
                              {order.phuongThucThanhToan === "MOMO" ? "Thanh toán MoMo" : "Thanh toán mô phỏng"}
                            </button>}
                            {canCancelForOrder && <button className="delete" type="button" disabled={commerceActionId === order.id} onClick={() => updateOrder(order, "huy")}>Hủy đơn</button>}
                            {order.trangThai === "DA_THANH_LY" && order.coBienBanThanhLy && <button className="link-button" type="button" onClick={() => downloadDisposalRecord(order)}>Tải biên bản PDF</button>}
                            {order.loaiDon === "MUA" && !canCancel && !canPay && order.trangThai !== "DA_DAT" && order.maGiaoDich && <span>GD: {order.maGiaoDich}</span>}
                          </td>
                        </tr>;
                      })}
              </tbody></table></div>
            </div>
            {receiptOrder && <dialog ref={receiptDialogRef} className="detail-dialog commerce-receipt-dialog"
              onCancel={() => setReceiptOrder(null)}>
              <form onSubmit={handleReceiptSubmit}>
                <div className="panel-heading"><div><p className="eyebrow">ĐỐI CHIẾU NHẬN HÀNG</p><h2>{receiptOrder.maDonHang}</h2><p className="muted">{receiptOrder.nhaCungCap} · Dự kiến {formatDate(receiptOrder.ngayDuKienGiao)}</p></div>
                  <button className="icon-button" type="button" aria-label="Đóng nhận hàng" onClick={closeReceiptDialog}>×</button></div>
                <p className="muted">Nhập số lượng thực nhận cho từng dòng. Chỉ số lượng đã nhận mới được tạo thành tài sản trong kho.</p>
                <div className="commerce-receipt-lines">{receiptOrder.chiTiet.map((item) => <label key={item.id}>
                  <span>{item.tenSanPham} · Đã đặt {item.soLuong}</span>
                  <input aria-label={`Số lượng thực nhận ${item.tenSanPham}`} type="number" min="0" max={item.soLuong} step="1" required
                    value={receiptQuantities[item.id] ?? ""}
                    onChange={(event) => setReceiptQuantities((values) => ({ ...values, [item.id]: event.target.value }))} />
                </label>)}</div>
                <div className="commerce-actions"><button className="button secondary" type="button" onClick={closeReceiptDialog}>Đóng</button>
                  <button className="button" type="submit" disabled={receiptBusy}>{receiptBusy ? "Đang ghi nhận..." : "Xác nhận nhận hàng & nhập kho"}</button></div>
              </form>
            </dialog>}
          </section>}

          {selectedAsset && <dialog ref={detailDialogRef} className="detail-dialog" onCancel={() => setSelectedAsset(null)}>
            <div className="panel-heading"><div><p className="eyebrow">CHI TIẾT HỒ SƠ</p><h2>{selectedAsset.tenTaiSan}</h2></div><button className="icon-button" type="button" aria-label="Đóng chi tiết" onClick={() => setSelectedAsset(null)}>×</button></div>
            <dl className="detail-grid"><div><dt>Mã tài sản</dt><dd>{selectedAsset.maTaiSan}</dd></div><div><dt>Loại tài sản</dt><dd>{selectedAsset.tenLoaiTaiSan || "Chưa phân loại"}</dd></div><div><dt>Giá mua</dt><dd>{selectedAsset.giaMua == null ? "Chưa cập nhật" : `${privateValue(numberFormatter.format(selectedAsset.giaMua))} ₫`}</dd></div><div><dt>Ngày mua</dt><dd>{formatDate(selectedAsset.ngayMua)}</dd></div><div><dt>Tình trạng</dt><dd>{selectedAsset.tinhTrang === "Bảo trì" ? "Đang sửa chữa" : selectedAsset.tinhTrang}</dd></div><div><dt>Người giữ</dt><dd>{selectedAsset.nguoiDangGiu || "—"}{selectedAsset.phongBanDangGiu ? ` · ${selectedAsset.phongBanDangGiu}` : ""}</dd></div><div><dt>Vị trí</dt><dd>{[selectedAsset.toaNha, selectedAsset.tang && `Tầng ${selectedAsset.tang}`, selectedAsset.phong && `Phòng ${selectedAsset.phong}`].filter(Boolean).join(" · ") || "Chưa cập nhật"}</dd></div><div><dt>Khấu hao mặc định</dt><dd>{selectedAsset.thoiGianKhauHaoNam ?? 5} năm</dd></div><div><dt>Giá trị còn lại</dt><dd>{selectedAsset.giaTriConLai == null ? "Chưa đủ dữ liệu ngày mua/giá mua" : `${privateValue(numberFormatter.format(selectedAsset.giaTriConLai))} ₫`}</dd></div><div><dt>Chu kỳ bảo trì</dt><dd>{selectedAsset.chuKyBaoTriThang ?? 12} tháng</dd></div><div><dt>Bảo trì gần nhất</dt><dd>{formatDate(selectedAsset.baoTriGanNhat)}</dd></div><div><dt>Bảo trì tiếp theo</dt><dd>{formatDate(selectedAsset.baoTriTiepTheo)}</dd></div><div><dt>Chi phí bảo trì tích lũy</dt><dd>{privateValue(numberFormatter.format(selectedAsset.chiPhiBaoTriTichLuy ?? 0))} ₫</dd></div></dl>
          </dialog>}
          </>
          <footer className="footnote">OfficeCare <span>•</span> Quản lý tài sản và thiết bị văn phòng</footer>
          <ChangePasswordDialog open={passwordDialogOpen} onClose={() => setPasswordDialogOpen(false)}
            onSaved={() => showMessage("Đã đổi mật khẩu thành công.")} />
        </div>
      </main>
    </div>
  );
}

export default App;
