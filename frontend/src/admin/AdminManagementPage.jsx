import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { adminDownload, adminRequest } from "./adminApi";
import { fetchWithSession } from "../sessionFetch.js";
import InvoiceDepreciationPage from "./InvoiceDepreciationPage.jsx";

const API_URL = "http://localhost:8081/api";
const numberFormatter = new Intl.NumberFormat("vi-VN");
const currencyFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 });
const todayInput = () => {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
};

async function loadAllAssetRows() {
  const firstPage = await adminRequest(`${API_URL}/tai-san?page=0&size=100`);
  const laterPages = await Promise.all(Array.from(
    { length: Math.max(0, firstPage.totalPages - 1) },
    (_, index) => adminRequest(`${API_URL}/tai-san?page=${index + 1}&size=100`),
  ));
  return [firstPage, ...laterPages].flatMap((page) => page.content ?? []);
}

const resourceDefinitions = {
  categories: {
    title: "Loại tài sản",
    description: "Quản lý danh mục và số năm khấu hao mặc định của từng loại.",
    endpoint: `${API_URL}/loai-tai-san`,
    idField: "loaiId",
    fields: [
      { name: "tenLoai", label: "Tên loại tài sản", required: true },
      { name: "thoiGianKhauHaoNam", label: "Thời gian khấu hao (năm)", type: "number", min: 1, required: true },
      { name: "requiresSerial", label: "Bắt buộc nhập serial khi nhập kho", type: "checkbox" },
    ],
    columns: [
      ["Loại tài sản", (row) => row.tenLoai],
      ["Số tài sản", (row) => row.soTaiSan],
      ["Yêu cầu mua", (row) => row.soYeuCauMuaSam ?? 0],
      ["Khấu hao mặc định", (row) => `${row.thoiGianKhauHaoNam ?? 5} năm`],
      ["Serial", (row) => row.requiresSerial ? "Bắt buộc" : "Không bắt buộc"],
    ],
    removable: true,
  },
  assets: {
    title: "Tài sản & thiết bị",
    description: "Hồ sơ thiết bị, thông tin bảo hành và giá trị còn lại.",
    endpoint: `${API_URL}/tai-san`,
    collectionKey: "content",
    idField: "taiSanId",
    fields: [
      { name: "maTaiSan", label: "Mã tài sản", required: true },
      { name: "tenTaiSan", label: "Tên tài sản", required: true },
      { name: "loaiTaiSanId", label: "Loại tài sản", type: "category", required: true },
      { name: "serialNumber", label: "Serial" },
      { name: "brand", label: "Hãng" },
      { name: "model", label: "Model" },
      { name: "ngayMua", label: "Ngày mua", type: "date" },
      { name: "giaMua", label: "Giá mua (VNĐ)", type: "number", min: 0 },
      { name: "supplierId", label: "Nhà cung cấp", type: "supplier" },
      { name: "warrantyEndDate", label: "Hết hạn bảo hành", type: "date" },
      { name: "tinhTrang", label: "Trạng thái", type: "select", required: true, options: ["Trong kho", "Đang sử dụng", "Bảo trì", "Chờ thanh lý", "Thanh lý", "Đã thanh lý", "Thiếu"] },
      { name: "viTriId", label: "Vị trí", type: "location" },
      { name: "notes", label: "Ghi chú", type: "textarea" },
    ],
    columns: [
      ["Mã tài sản", (row) => row.maTaiSan],
      ["Tên", (row) => row.tenTaiSan],
      ["Loại", (row) => row.tenLoaiTaiSan || "—"],
      ["Trạng thái", (row) => row.tinhTrang],
      ["Phòng ban", (row) => row.phongBanDangGiu || "—"],
      ["Giá trị còn lại", (row) => row.giaTriConLai == null ? "—" : `${currencyFormatter.format(row.giaTriConLai)} ₫`],
    ],
    removable: true,
  },
  employees: {
    title: "Nhân viên",
    description: "Quản lý danh sách nhân sự và trạng thái làm việc để hỗ trợ thu hồi tài sản.",
    endpoint: `${API_URL}/admin/nhan-vien`,
    idField: "id",
    fields: [
      { name: "maNhanVien", label: "Mã nhân viên", required: true },
      { name: "hoTen", label: "Họ và tên", required: true },
      { name: "phongBan", label: "Phòng ban", type: "department" },
      { name: "chucVu", label: "Chức vụ" },
      { name: "trangThai", label: "Trạng thái làm việc", type: "select", required: true, options: [{ value: "ACTIVE", label: "Đang làm việc" }, { value: "INACTIVE", label: "Đã nghỉ việc" }] },
    ],
    columns: [
      ["Mã nhân viên", (row) => row.maNhanVien],
      ["Họ tên", (row) => row.hoTen],
      ["Phòng ban", (row) => row.phongBan || "—"],
      ["Chức vụ", (row) => row.chucVu || "—"],
      ["Trạng thái", (row) => row.trangThai === "ACTIVE" ? "Đang làm việc" : row.trangThai === "INACTIVE" ? "Đã nghỉ việc" : row.trangThai],
    ],
    removable: false,
  },
  departments: {
    title: "Phòng ban",
    description: "Danh mục phòng ban dùng cho nhân viên, cấp phát và báo cáo.",
    endpoint: `${API_URL}/admin/phong-ban`,
    idField: "id",
    fields: [
      { name: "maPhongBan", label: "Mã phòng ban", required: true },
      { name: "tenPhongBan", label: "Tên phòng ban", required: true },
    ],
    columns: [["Mã phòng ban", (row) => row.maPhongBan], ["Tên phòng ban", (row) => row.tenPhongBan]],
    removable: true,
  },
  locations: {
    title: "Vị trí",
    description: "Danh mục tòa nhà, tầng và phòng lưu trữ tài sản.",
    endpoint: `${API_URL}/admin/vi-tri`,
    idField: "id",
    fields: [
      { name: "toaNha", label: "Tòa nhà", required: true },
      { name: "tang", label: "Tầng" },
      { name: "phong", label: "Phòng", required: true },
    ],
    columns: [["Tòa nhà", (row) => row.toaNha], ["Tầng", (row) => row.tang || "—"], ["Phòng", (row) => row.phong]],
    removable: true,
  },
  suppliers: {
    title: "Nhà cung cấp",
    description: "Thông tin liên hệ nhà cung cấp thiết bị và hóa đơn mua hàng.",
    endpoint: `${API_URL}/admin/nha-cung-cap`,
    idField: "id",
    fields: [
      { name: "tenNhaCungCap", label: "Tên nhà cung cấp", required: true },
      { name: "nguoiLienHe", label: "Người liên hệ" },
      { name: "soDienThoai", label: "Số điện thoại" },
      { name: "email", label: "Email", type: "email" },
      { name: "diaChi", label: "Địa chỉ", type: "textarea" },
    ],
    columns: [
      ["Nhà cung cấp", (row) => row.tenNhaCungCap],
      ["Người liên hệ", (row) => row.nguoiLienHe || "—"],
      ["Điện thoại", (row) => row.soDienThoai || "—"],
      ["Email", (row) => row.email || "—"],
    ],
    removable: true,
  },
  consumables: {
    title: "Vật tư tiêu hao",
    description: "Quản lý danh mục, nhập xuất, yêu cầu cấp phát, cảnh báo tồn và báo cáo tiêu hao.",
    endpoint: `${API_URL}/admin/vat-tu`,
    idField: "id",
    fields: [
      { name: "maVatTu", label: "Mã vật tư", required: true },
      { name: "tenVatTu", label: "Tên vật tư", required: true },
      { name: "donVi", label: "Đơn vị tính", required: true, maxLength: 30, placeholder: "VD: cái, hộp, ream, chai" },
      { name: "mucToiThieu", label: "Mức tồn tối thiểu", type: "number", min: 0, required: true },
      { name: "donGia", label: "Đơn giá (VNĐ)", type: "number", min: 0 },
      { name: "dangSuDung", label: "Trạng thái sử dụng", type: "select", required: true, options: [{ value: "true", label: "Đang sử dụng" }, { value: "false", label: "Ngừng sử dụng" }] },
    ],
    columns: [
      ["Mã", (row) => row.maVatTu],
      ["Tên vật tư", (row) => row.tenVatTu],
      ["Đơn vị", (row) => row.donVi],
      ["Tồn kho", (row) => `${row.soLuongTon} ${row.donVi}`],
      ["Mức tối thiểu", (row) => row.mucToiThieu],
      ["Trạng thái tồn", (row) => Number(row.soLuongTon) <= 0 ? "Hết" : Number(row.soLuongTon) <= Number(row.mucToiThieu) ? "Sắp hết" : "Đủ"],
      ["Sử dụng", (row) => row.dangSuDung ? "Đang sử dụng" : "Ngừng sử dụng"],
    ],
    removable: false,
  },
  invoices: {
    title: "Hóa đơn & khấu hao",
    description: "Lưu chứng từ mua hàng và xem thông tin khấu hao đường thẳng của tài sản.",
    endpoint: `${API_URL}/admin/hoa-don`,
    idField: "id",
    fields: [
      { name: "soHoaDon", label: "Số hóa đơn", required: true },
      { name: "ngayMua", label: "Ngày mua", type: "date", required: true },
      { name: "tongTien", label: "Tổng tiền (VNĐ)", type: "number", min: 0, required: true },
      { name: "taiSanId", label: "Tài sản liên quan", type: "asset" },
      { name: "nhaCungCapId", label: "Nhà cung cấp", type: "supplier" },
    ],
    columns: [
      ["Số hóa đơn", (row) => row.soHoaDon],
      ["Ngày mua", (row) => row.ngayMua ? new Date(row.ngayMua).toLocaleDateString("vi-VN") : "—"],
      ["Nhà cung cấp", (row) => row.nhaCungCap || "—"],
      ["Tổng tiền", (row) => `${currencyFormatter.format(row.tongTien ?? 0)} ₫`],
    ],
    removable: true,
  },
};

const catalogTabs = [
  ["categories", "Loại tài sản"],
  ["departments", "Phòng ban"],
  ["locations", "Vị trí"],
  ["suppliers", "Nhà cung cấp"],
];

function ResourcePage({ resourceKey }) {
  const definition = resourceDefinitions[resourceKey];
  const assetFormRef = useRef(null);
  const assetDetailRef = useRef(null);
  const [rows, setRows] = useState([]);
  const [activeAssignments, setActiveAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [issueFormOpen, setIssueFormOpen] = useState(false);
  const [issueConsumableId, setIssueConsumableId] = useState("");
  const [receiptFormOpen, setReceiptFormOpen] = useState(false);
  const [receiptSaving, setReceiptSaving] = useState(false);
  const [receiptError, setReceiptError] = useState("");
  const [editingRow, setEditingRow] = useState(null);
  const [saving, setSaving] = useState(false);
  const [issueSaving, setIssueSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [categoryDeleteTarget, setCategoryDeleteTarget] = useState(null);
  const [categoryReplacementId, setCategoryReplacementId] = useState("");
  const [issueError, setIssueError] = useState("");
  const [issueFormError, setIssueFormError] = useState("");
  const [success, setSuccess] = useState("");
  const [requestRows, setRequestRows] = useState([]);
  const [movementRows, setMovementRows] = useState([]);
  const [usageRows, setUsageRows] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [consumableSuppliers, setConsumableSuppliers] = useState([]);
  const [movementFilters, setMovementFilters] = useState({ vatTuId: "", phongBan: "", tuNgay: "", denNgay: "" });
  const [usageFilters, setUsageFilters] = useState({ tuNgay: "", denNgay: "" });
  const [selectedAsset, setSelectedAsset] = useState(null);
  const [assetHistory, setAssetHistory] = useState({ assignments: [], maintenances: [], depreciation: [] });
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [assetPage, setAssetPage] = useState(0);
  const [assetTotalPages, setAssetTotalPages] = useState(0);
  const [assetTotalElements, setAssetTotalElements] = useState(0);
  const [assetFilters, setAssetFilters] = useState({ keyword: "", tinhTrang: "", loaiId: "", phongBan: "", muaTuNgay: "", muaDenNgay: "" });
  const [assetFilterDraft, setAssetFilterDraft] = useState(assetFilters);
  const [assetCategories, setAssetCategories] = useState([]);
  const [assetSuppliers, setAssetSuppliers] = useState([]);
  const [assetLocations, setAssetLocations] = useState([]);
  const [assetOptions, setAssetOptions] = useState([]);
  const [depreciationRows, setDepreciationRows] = useState([]);
  const [referenceError, setReferenceError] = useState("");
  const [depreciationError, setDepreciationError] = useState("");
  const selectedIssueItem = rows.find((row) => String(row.id) === issueConsumableId);

  const loadRows = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      if (resourceKey === "assets") {
        const params = new URLSearchParams({ page: String(assetPage), size: "20" });
        Object.entries(assetFilters).forEach(([key, value]) => { if (value) params.set(key, value); });
        const result = await adminRequest(`${API_URL}/tai-san?${params}`);
        if (!result || !Array.isArray(result.content) || !Number.isInteger(result.totalPages)) {
          throw new Error("Máy chủ trả về danh sách tài sản không hợp lệ.");
        }
        setRows(result.content);
        setAssetTotalPages(result.totalPages);
        setAssetTotalElements(result.totalElements ?? result.content.length);
      } else {
        const [result, assignmentRows] = await Promise.all([
          adminRequest(definition.endpoint),
          resourceKey === "employees" ? adminRequest(`${API_URL}/cap-phat?activeOnly=true`) : Promise.resolve([]),
        ]);
        setRows(Array.isArray(result) ? result : result?.[definition.collectionKey ?? "content"] ?? []);
        if (resourceKey === "employees") {
          if (!Array.isArray(assignmentRows)) {
            throw new Error("Máy chủ trả về danh sách tài sản đang bàn giao không hợp lệ.");
          }
          setActiveAssignments(assignmentRows);
        }
      }
      return true;
    } catch (loadError) {
      setRows([]);
      if (resourceKey === "employees") setActiveAssignments([]);
      setError(loadError.message);
      return false;
    } finally {
      setLoading(false);
    }
  }, [assetFilters, assetPage, definition, resourceKey]);

  useEffect(() => { loadRows(); }, [loadRows]);

  const loadIssueRows = useCallback(async () => {
    if (resourceKey !== "consumables") return;
    setIssueError("");
    try {
      const [movementResult, requestResult, usageResult] = await Promise.all([
        adminRequest(`${API_URL}/admin/vat-tu/lich-su`),
        adminRequest(`${API_URL}/admin/vat-tu/yeu-cau`),
        adminRequest(`${API_URL}/admin/vat-tu/bao-cao`),
      ]);
      setMovementRows(movementResult);
      setRequestRows(requestResult);
      setUsageRows(usageResult);
    } catch (loadError) {
      setIssueError(loadError.message);
    }
  }, [resourceKey]);

  useEffect(() => { loadIssueRows(); }, [loadIssueRows]);

  useEffect(() => {
    if (resourceKey !== "consumables") return undefined;
    let active = true;
    adminRequest(`${API_URL}/admin/nha-cung-cap`).then((supplierRows) => {
      if (active) {
        setConsumableSuppliers(supplierRows);
      }
    }).catch((loadError) => {
      if (active) setIssueError(loadError.message);
    });
    return () => { active = false; };
  }, [resourceKey]);

  useEffect(() => {
    if (resourceKey !== "assets" && resourceKey !== "invoices" && resourceKey !== "employees") return undefined;
    let active = true;
    setReferenceError("");
    const requests = resourceKey === "assets"
      ? [adminRequest(`${API_URL}/loai-tai-san`), adminRequest(`${API_URL}/admin/nha-cung-cap`), adminRequest(`${API_URL}/admin/vi-tri`)]
      : resourceKey === "invoices"
        ? [adminRequest(`${API_URL}/tai-san?page=0&size=100`), adminRequest(`${API_URL}/admin/nha-cung-cap`)]
        : [adminRequest(`${API_URL}/admin/phong-ban`)];
    Promise.all(requests).then(([first, suppliers, locations]) => {
      if (!active) return;
      if (resourceKey === "assets") setAssetCategories(first);
      else if (resourceKey === "invoices") setAssetOptions(first.content ?? []);
      else setDepartments(first);
      if (resourceKey !== "employees") setAssetSuppliers(suppliers);
      if (resourceKey === "assets") setAssetLocations(locations);
    }).catch((loadError) => {
      if (active) setReferenceError(loadError.message);
    });
    return () => { active = false; };
  }, [resourceKey]);

  useEffect(() => {
    if (resourceKey !== "invoices") return undefined;
    let active = true;
    setDepreciationError("");
    adminRequest(`${API_URL}/admin/khau-hao`).then((result) => {
      if (active) setDepreciationRows(result);
    }).catch((loadError) => {
      if (active) {
        setDepreciationRows([]);
        setDepreciationError(loadError.message);
      }
    });
    return () => { active = false; };
  }, [resourceKey]);

  useEffect(() => {
    if (resourceKey !== "assets" || !selectedAsset) return undefined;
    window.requestAnimationFrame(() => {
      assetDetailRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    let active = true;
    setHistoryLoading(true);
    setHistoryError("");
    setAssetHistory({ assignments: [], maintenances: [], depreciation: [] });
    Promise.allSettled([
      adminRequest(`${API_URL}/cap-phat`),
      adminRequest(`${API_URL}/bao-tri`),
      adminRequest(`${API_URL}/admin/khau-hao?taiSanId=${selectedAsset.taiSanId}`),
    ]).then((results) => {
      if (!active) return;
      const failures = results.filter((result) => result.status === "rejected").map((result) => result.reason.message);
      setHistoryError(failures.join(" "));
      const [assignmentsResult, maintenancesResult, depreciationResult] = results;
      setAssetHistory({
        assignments: assignmentsResult.status === "fulfilled"
          ? assignmentsResult.value.filter((item) => item.taiSanId === selectedAsset.taiSanId) : [],
        maintenances: maintenancesResult.status === "fulfilled"
          ? maintenancesResult.value.filter((item) => item.taiSanId === selectedAsset.taiSanId) : [],
        depreciation: depreciationResult.status === "fulfilled" ? depreciationResult.value : [],
      });
    }).finally(() => {
      if (active) setHistoryLoading(false);
    });
    return () => { active = false; };
  }, [resourceKey, selectedAsset]);

  const filteredRows = useMemo(() => {
    if (resourceKey === "assets") return rows;
    const needle = query.trim().toLocaleLowerCase("vi");
    if (!needle) return rows;
    return rows.filter((row) => Object.values(row).some((value) =>
      String(value ?? "").toLocaleLowerCase("vi").includes(needle)));
  }, [query, resourceKey, rows]);

  function applyAssetFilters(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setAssetFilterDraft(Object.fromEntries(Object.keys(assetFilters).map((key) => [key, String(data.get(key) ?? "").trim()])));
    setAssetFilters(Object.fromEntries(Object.keys(assetFilters).map((key) => [key, String(data.get(key) ?? "").trim()])));
    setAssetPage(0);
  }

  function clearAssetFilters() {
    const clearedFilters = Object.fromEntries(Object.keys(assetFilters).map((key) => [key, ""]));
    setAssetFilterDraft(clearedFilters);
    setAssetFilters(clearedFilters);
    setAssetPage(0);
  }

  function openForm(row = null) {
    setEditingRow(row);
    setFormError("");
    setFormOpen(true);
    if (resourceKey === "assets") {
      window.requestAnimationFrame(() => assetFormRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    }
  }

  async function saveRow(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    if (resourceKey === "assets") {
      const purchaseDate = String(data.get("ngayMua") ?? "");
      if (purchaseDate && purchaseDate > todayInput()) {
        setFormError("Ngày mua không được ở tương lai.");
        return;
      }
    }
    const payload = Object.fromEntries(definition.fields.filter((field) => field.type !== "location").map((field) => {
      const rawValue = data.get(field.name);
      if (field.type === "checkbox") return [field.name, rawValue === "on"];
      if (field.type === "number" || field.type === "category" || field.type === "supplier" || field.type === "asset") {
        return [field.name, rawValue === "" ? null : Number(rawValue)];
      }
      if (field.name === "dangSuDung") return [field.name, rawValue === "true"];
      return [field.name, rawValue === "" ? null : String(rawValue).trim()];
    }));
    if (resourceKey === "assets") {
      const locationId = String(data.get("viTriId") ?? "");
      const location = assetLocations.find((item) => String(item.id) === locationId);
      if (location) {
        payload.toaNha = location.toaNha;
        payload.tang = location.tang;
        payload.phong = location.phong;
      } else if (locationId === "legacy" && editingRow) {
        payload.toaNha = editingRow.toaNha ?? null;
        payload.tang = editingRow.tang ?? null;
        payload.phong = editingRow.phong ?? null;
      } else {
        payload.toaNha = null;
        payload.tang = null;
        payload.phong = null;
      }
    }
    if (resourceKey === "assets" && editingRow) {
      payload.imageReference = editingRow.imageReference ?? null;
    }
    setSaving(true);
    setFormError("");
    setSuccess("");
    try {
      const itemId = editingRow?.[definition.idField];
      const savedRow = await adminRequest(itemId == null ? definition.endpoint : `${definition.endpoint.split("?")[0]}/${itemId}`, {
        method: itemId == null ? "POST" : "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (resourceKey === "assets" && itemId != null && savedRow) {
        setSelectedAsset((current) => current?.taiSanId === itemId ? savedRow : current);
      }
      setFormOpen(false);
      setEditingRow(null);
      const refreshed = await loadRows();
      if (!refreshed) {
        setSuccess("Đã lưu dữ liệu nhưng không thể tải lại danh sách.");
      } else {
        setSuccess(itemId == null ? "Đã tạo bản ghi." : "Đã cập nhật bản ghi.");
      }
    } catch (saveError) {
      setFormError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function openAssetReference(reference, reportError = setFormError) {
    let url;
    try {
      url = new URL(reference, new URL(API_URL).origin);
    } catch {
      reportError("Liên kết ảnh không hợp lệ.");
      return;
    }
    if (!["http:", "https:"].includes(url.protocol)) {
      reportError("Chỉ có thể mở liên kết HTTP hoặc HTTPS.");
      return;
    }
    const apiOrigin = new URL(API_URL).origin;
    if (url.origin !== apiOrigin || !url.pathname.startsWith("/api/tai-san/anh/")) {
      const opened = window.open(url.href, "_blank");
      if (!opened) reportError("Trình duyệt đã chặn cửa sổ ảnh. Hãy cho phép cửa sổ bật lên rồi thử lại.");
      else opened.opener = null;
      return;
    }

    const imageWindow = window.open("about:blank", "_blank");
    if (!imageWindow) {
      reportError("Trình duyệt đã chặn cửa sổ ảnh. Hãy cho phép cửa sổ bật lên rồi thử lại.");
      return;
    }
    reportError("");
    try {
      const token = sessionStorage.getItem("officecare.accessToken");
      const response = await fetchWithSession(url.href, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!response.ok) throw new Error(`Không mở được ảnh (HTTP ${response.status}).`);
      const objectUrl = URL.createObjectURL(await response.blob());
      imageWindow.location.href = objectUrl;
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (openError) {
      imageWindow.close();
      reportError(openError.message);
    }
  }

  async function submitIssue(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const quantityInput = event.currentTarget.elements.namedItem("soLuong");
    const item = rows.find((row) => Number(row.id) === Number(data.get("vatTuId")));
    const quantity = quantityInput instanceof HTMLInputElement ? quantityInput.valueAsNumber : Number.NaN;
    const available = Number(item?.soLuongTon ?? 0);
    if (!item || !item.dangSuDung) {
      setIssueFormError("Vui lòng chọn vật tư đang sử dụng.");
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setIssueFormError("Số lượng xuất phải lớn hơn 0.");
      return;
    }
    if (quantity > available) {
      setIssueFormError(`Tồn kho "${item.tenVatTu}" chỉ còn ${numberFormatter.format(available)} ${item.donVi}; hãy nhập kho trước khi xuất số lượng này.`);
      return;
    }
    setIssueSaving(true);
    setIssueFormError("");
    setSuccess("");
    try {
      await adminRequest(`${API_URL}/admin/vat-tu/phieu-xuat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          vatTuId: Number(data.get("vatTuId")),
          soLuong: quantity,
          nguoiNhan: String(data.get("nguoiNhan")).trim(),
          ngayXuat: data.get("ngayXuat"),
          ghiChu: String(data.get("ghiChu") ?? "").trim(),
        }),
      });
      setIssueFormOpen(false);
      setSuccess("Đã xuất kho vật tư.");
      await Promise.all([loadRows(), loadIssueRows()]);
    } catch (saveError) {
      setIssueFormError(saveError.message);
    } finally {
      setIssueSaving(false);
    }
  }

  async function submitReceipt(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setReceiptSaving(true);
    setReceiptError("");
    setSuccess("");
    try {
      await adminRequest(`${API_URL}/admin/vat-tu/nhap-kho`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          vatTuId: Number(data.get("vatTuId")),
          soLuong: Number(data.get("soLuong")),
          donGia: Number(data.get("donGia")),
          nhaCungCapId: Number(data.get("nhaCungCapId")),
          ngayNhap: data.get("ngayNhap"),
          soPhieu: String(data.get("soPhieu") ?? "").trim() || null,
          donMuaLienQuan: String(data.get("donMuaLienQuan") ?? "").trim() || null,
        }),
      });
      setReceiptFormOpen(false);
      setSuccess("Đã nhập kho vật tư và cập nhật tồn kho.");
      await Promise.all([loadRows(), loadIssueRows()]);
    } catch (saveError) {
      setReceiptError(saveError.message);
    } finally {
      setReceiptSaving(false);
    }
  }

  async function updateConsumableRequest(row, action) {
    let ghiChu;
    if (action === "tu-choi") {
      ghiChu = window.prompt(`Lý do từ chối yêu cầu #${row.id}:`);
      if (ghiChu === null) return;
      if (!ghiChu.trim()) {
        setIssueError("Vui lòng nhập lý do từ chối yêu cầu.");
        return;
      }
    } else if (!window.confirm(`Xác nhận xuất ${row.soLuong} ${row.donVi} ${row.tenVatTu} cho ${row.nguoiYeuCau}?`)) {
      return;
    }
    setIssueError("");
    try {
      await adminRequest(`${API_URL}/admin/vat-tu/yeu-cau/${row.id}/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: action === "tu-choi" ? JSON.stringify({ ghiChu: ghiChu.trim() }) : undefined,
      });
      setSuccess(action === "xuat" ? "Đã duyệt yêu cầu và xuất kho vật tư." : "Đã từ chối yêu cầu vật tư.");
      await Promise.all([loadRows(), loadIssueRows()]);
    } catch (actionError) {
      setIssueError(actionError.message);
    }
  }

  async function toggleConsumable(row) {
    const nextActive = !row.dangSuDung;
    if (!window.confirm(`${nextActive ? "Tiếp tục sử dụng" : "Ngừng sử dụng"} vật tư "${row.tenVatTu}"?`)) return;
    setError("");
    try {
      await adminRequest(`${definition.endpoint}/${row.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          maVatTu: row.maVatTu,
          tenVatTu: row.tenVatTu,
          donVi: row.donVi,
          mucToiThieu: row.mucToiThieu,
          donGia: row.donGia,
          dangSuDung: nextActive,
        }),
      });
      await loadRows();
      setSuccess(nextActive ? "Đã kích hoạt lại vật tư." : "Đã ngừng sử dụng vật tư; lịch sử được giữ nguyên.");
    } catch (actionError) {
      setError(actionError.message);
    }
  }

  async function loadConsumableMovements(event) {
    event.preventDefault();
    const params = new URLSearchParams();
    Object.entries(movementFilters).forEach(([key, value]) => { if (value) params.set(key, value); });
    try {
      setMovementRows(await adminRequest(`${API_URL}/admin/vat-tu/lich-su?${params}`));
    } catch (loadError) {
      setIssueError(loadError.message);
    }
  }

  async function loadConsumableUsage(event) {
    event.preventDefault();
    const params = new URLSearchParams();
    Object.entries(usageFilters).forEach(([key, value]) => { if (value) params.set(key, value); });
    try {
      setUsageRows(await adminRequest(`${API_URL}/admin/vat-tu/bao-cao?${params}`));
    } catch (loadError) {
      setIssueError(loadError.message);
    }
  }

  async function deleteRow(row) {
    const title = row.maTaiSan || row.tenTaiSan || row.tenLoai || row.hoTen || row.tenVatTu || row.soHoaDon || row.tenNhaCungCap || row.tenPhongBan || row.phong || "bản ghi này";
    if (resourceKey === "categories") {
      const assetCount = Number(row.soTaiSan ?? 0);
      const requestCount = Number(row.soYeuCauMuaSam ?? 0);
      if (requestCount > 0) {
        setCategoryDeleteTarget(row);
        setCategoryReplacementId("");
        setError("");
        setSuccess("");
        return;
      }
      const impact = assetCount > 0 ? ` ${assetCount} tài sản sẽ được giữ lại nhưng bỏ phân loại.` : "";
      if (!window.confirm(`Xóa loại "${row.tenLoai}"?${impact}`)) return;
      await deleteCategoryRow(row);
      return;
    }
    if (!window.confirm(`Xóa "${title}"?`)) return;
    setError("");
    setSuccess("");
    try {
      await adminRequest(`${definition.endpoint.split("?")[0]}/${row[definition.idField]}`, { method: "DELETE" });
      if (resourceKey === "assets" && row.imageReference?.startsWith("/api/tai-san/anh/")) {
        try {
          await adminRequest(`${API_URL}${row.imageReference}`, { method: "DELETE" });
        } catch (cleanupError) {
          await loadRows();
          setError(`Đã xóa tài sản nhưng chưa thể xóa ảnh cũ: ${cleanupError.message}`);
          return;
        }
      }
      await loadRows();
      setSuccess("Đã xóa bản ghi.");
    } catch (deleteError) {
      setError(deleteError.message);
    }
  }

  async function deleteCategoryRow(row, replacementId) {
    setError("");
    setSuccess("");
    try {
      const endpoint = `${definition.endpoint.split("?")[0]}/${row[definition.idField]}`;
      const url = replacementId ? `${endpoint}?replacementId=${encodeURIComponent(replacementId)}` : endpoint;
      await adminRequest(url, { method: "DELETE" });
      setCategoryDeleteTarget(null);
      setCategoryReplacementId("");
      await loadRows();
      setSuccess(`Đã xóa loại "${row.tenLoai}"${row.soTaiSan > 0 && !replacementId ? "; tài sản liên quan đã được bỏ phân loại" : replacementId ? "; các yêu cầu mua sắm đã chuyển sang loại thay thế" : ""}.`);
    } catch (deleteError) {
      setError(deleteError.message);
    }
  }

  const matchedEditingLocation = assetLocations.find((location) =>
    location.toaNha === editingRow?.toaNha && (location.tang ?? "") === (editingRow?.tang ?? "")
      && location.phong === editingRow?.phong);
  const hasUnlistedEditingLocation = resourceKey === "assets" && editingRow
    && [editingRow.toaNha, editingRow.tang, editingRow.phong].some(Boolean) && !matchedEditingLocation;

  return (
    <>
      <section className="welcome">
        <div><p className="eyebrow">QUẢN LÝ TÀI SẢN</p><h1>{definition.title}</h1><p className="welcome-copy">{definition.description}</p></div>
        <div className="admin-resource-actions">
          {resourceKey === "consumables" && <>
            <button className="button secondary" type="button" onClick={() => { setReceiptError(""); setReceiptFormOpen(true); }}>Nhập kho vật tư</button>
            <button className="button secondary" type="button" onClick={() => { setIssueFormError(""); setIssueConsumableId(""); setIssueFormOpen(true); }}>Xuất kho vật tư</button>
          </>}
          <button className="button" type="button" onClick={() => openForm()}>
            {resourceKey === "consumables" ? "＋ Thêm vật tư" : "＋ Thêm mới"}
          </button>
        </div>
      </section>
      {error && <p className="message error" role="alert">{error} <button className="retry-button" type="button" onClick={loadRows}>Thử lại</button></p>}
      {referenceError && (resourceKey === "assets" || resourceKey === "invoices" || resourceKey === "employees") && <p className="message error" role="alert">Không tải được dữ liệu tham chiếu: {referenceError}</p>}
      {success && <p className="message" role="status">{success}</p>}
      {resourceKey === "categories" && categoryDeleteTarget && <div className="category-delete-backdrop">
        <section className="panel category-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="category-delete-title">
          <div className="panel-heading"><div><p className="eyebrow">XÁC NHẬN XÓA</p><h2 id="category-delete-title">Xóa loại “{categoryDeleteTarget.tenLoai}”</h2></div>
            <button className="icon-button" type="button" aria-label="Đóng" onClick={() => setCategoryDeleteTarget(null)}>×</button></div>
          <p>Loại này đang được dùng bởi {categoryDeleteTarget.soYeuCauMuaSam} yêu cầu mua sắm. Chọn loại thay thế để giữ nguyên khả năng xử lý các yêu cầu.</p>
          <label className="field" htmlFor="category-delete-replacement">Chuyển yêu cầu sang loại</label>
          <select id="category-delete-replacement" value={categoryReplacementId} onChange={(event) => setCategoryReplacementId(event.target.value)}>
            <option value="">Chọn loại thay thế</option>
            {rows.filter((row) => row[definition.idField] !== categoryDeleteTarget[definition.idField]).map((row) =>
              <option key={row[definition.idField]} value={row[definition.idField]}>{row.tenLoai}</option>)}
          </select>
          <div className="form-actions">
            <button className="button secondary" type="button" onClick={() => setCategoryDeleteTarget(null)}>Hủy</button>
            <button className="button category-delete-submit" type="button" disabled={!categoryReplacementId}
              onClick={() => deleteCategoryRow(categoryDeleteTarget, categoryReplacementId)}>Chuyển liên kết và xóa</button>
          </div>
        </section>
      </div>}
      {formOpen && <section className="panel admin-resource-form" ref={assetFormRef}>
        <div className="panel-heading"><div><p className="eyebrow">{editingRow ? "CẬP NHẬT" : "TẠO MỚI"}</p><h2>{editingRow ? "Chỉnh sửa thông tin" : resourceKey === "consumables" ? "Thêm vật tư tiêu hao" : `Thêm ${definition.title.toLocaleLowerCase("vi")}`}</h2>
          {resourceKey === "consumables" && !editingRow && <p className="muted">Vật tư mới bắt đầu với tồn kho 0. Sau khi lưu, chọn “Nhập kho vật tư” để ghi nhận số lượng ban đầu.</p>}</div>
          <button className="icon-button" type="button" aria-label="Đóng biểu mẫu" onClick={() => setFormOpen(false)}>×</button></div>
        <form onSubmit={saveRow}>
          <div className="form-grid admin-resource-grid">
            {definition.fields.map((field) => (
              <div className={`field${field.type === "textarea" ? " admin-resource-wide" : ""}`} key={field.name}>
                <label htmlFor={`resource-${field.name}`}>{field.label}{field.required && <span> *</span>}</label>
                {field.type === "category" || field.type === "supplier" || field.type === "asset"
                  ? <select id={`resource-${field.name}`} name={field.name} defaultValue={editingRow?.[field.name] ?? ""} required={field.required}>
                    <option value="">Chọn {field.type === "category" ? "loại tài sản" : field.type === "supplier" ? "nhà cung cấp" : "tài sản"}</option>
                    {(field.type === "category" ? assetCategories : field.type === "supplier" ? assetSuppliers : assetOptions).map((item) => (
                      <option key={item.loaiId ?? item.id ?? item.taiSanId} value={item.loaiId ?? item.id ?? item.taiSanId}>
                        {item.tenLoai ?? item.tenNhaCungCap ?? `${item.maTaiSan} · ${item.tenTaiSan}`}
                      </option>
                    ))}
                  </select>
                  : field.type === "select"
                  ? resourceKey === "assets" && field.name === "tinhTrang" && editingRow?.nguoiDangGiu
                    ? <><select id={`resource-${field.name}`} value={editingRow.tinhTrang} disabled
                        aria-describedby="asset-status-assignment-note">
                        <option value={editingRow.tinhTrang}>{editingRow.tinhTrang}</option>
                      </select>
                      <input type="hidden" name={field.name} value={editingRow.tinhTrang} />
                      <small className="admin-asset-status-note" id="asset-status-assignment-note">
                        Tài sản đang được bàn giao cho {editingRow.nguoiDangGiu}. Hãy thu hồi tại mục Bàn giao &amp; Thu hồi trước khi đổi trạng thái.
                      </small></>
                    : <select id={`resource-${field.name}`} name={field.name} defaultValue={editingRow?.[field.name] == null ? (typeof field.options[0] === "string" ? field.options[0] : field.options[0].value) : String(editingRow[field.name])} required={field.required}>
                    {field.options.map((option) => {
                      const value = typeof option === "string" ? option : option.value;
                      const label = typeof option === "string" ? option : option.label;
                      return <option key={value} value={value}>{label}</option>;
                    })}
                  </select>
                  : field.type === "location"
                    ? <select id={`resource-${field.name}`} name={field.name} defaultValue={matchedEditingLocation?.id ?? (hasUnlistedEditingLocation ? "legacy" : "")}>
                      <option value="">Chọn vị trí</option>
                      {hasUnlistedEditingLocation && <option value="legacy">Giữ vị trí hiện tại (chưa có trong danh mục)</option>}
                      {assetLocations.map((location) => <option key={location.id} value={location.id}>
                        {[location.toaNha, location.tang && `Tầng ${location.tang}`, `Phòng ${location.phong}`].filter(Boolean).join(" · ")}
                      </option>)}
                    </select>
                    : field.type === "department"
                      ? <select id={`resource-${field.name}`} name={field.name} defaultValue={editingRow?.[field.name] ?? ""}>
                        <option value="">Chọn phòng ban</option>
                        {departments.map((department) => <option key={department.id} value={department.tenPhongBan}>{department.tenPhongBan}</option>)}
                      </select>
                  : field.type === "checkbox"
                    ? <input id={`resource-${field.name}`} name={field.name} type="checkbox" defaultChecked={Boolean(editingRow?.[field.name])} />
                  : field.type === "textarea"
                    ? <textarea id={`resource-${field.name}`} name={field.name} defaultValue={editingRow?.[field.name] ?? ""} required={field.required} maxLength="2000" rows="3" />
                    : <input id={`resource-${field.name}`} name={field.name} type={field.type ?? "text"} defaultValue={editingRow?.[field.name] ?? ""} required={field.required} min={field.min}
                      max={field.name === "ngayMua" ? todayInput() : undefined}
                      maxLength={field.type === "number" ? undefined : field.maxLength ?? "300"} placeholder={field.placeholder} />}
              </div>
            ))}
          </div>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <div className="form-actions"><button className="button secondary" type="button" onClick={() => setFormOpen(false)}>Hủy</button><button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu..." : "Lưu thông tin"}</button></div>
        </form>
      </section>}
      {issueFormOpen && resourceKey === "consumables" && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><p className="eyebrow">PHIẾU XUẤT KHO</p><h2>Xuất vật tư</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng phiếu xuất kho" onClick={() => setIssueFormOpen(false)}>×</button></div>
        <form className="admin-stocktake-form" onSubmit={submitIssue}>
          <div className="field"><label htmlFor="issue-material">Vật tư</label><select id="issue-material" name="vatTuId" required value={issueConsumableId} onChange={(event) => { setIssueConsumableId(event.target.value); setIssueFormError(""); }}><option value="" disabled>Chọn vật tư</option>{rows.filter((row) => row.dangSuDung).map((row) => <option key={row.id} value={row.id}>{row.maVatTu} · {row.tenVatTu} (tồn {numberFormatter.format(Number(row.soLuongTon))} {row.donVi})</option>)}</select></div>
          <div className="field"><label htmlFor="issue-quantity">Số lượng</label><input id="issue-quantity" name="soLuong" type="number" min="0.01" step="0.01" required /><p className="muted">{selectedIssueItem ? `Tồn khả dụng: ${numberFormatter.format(Number(selectedIssueItem.soLuongTon))} ${selectedIssueItem.donVi}` : "Chọn vật tư để xem số lượng tồn."}</p></div>
          <div className="field"><label htmlFor="issue-recipient">Người nhận</label><input id="issue-recipient" name="nguoiNhan" required maxLength="150" /></div>
          <div className="field"><label htmlFor="issue-date">Ngày xuất</label><input id="issue-date" name="ngayXuat" type="date" required max={todayInput()} defaultValue={todayInput()} /></div>
          <div className="field"><label htmlFor="issue-note">Ghi chú</label><input id="issue-note" name="ghiChu" maxLength="500" /></div>
          {issueFormError && <p className="form-error" role="alert">{issueFormError}</p>}
          <div className="form-actions"><button className="button secondary" type="button" onClick={() => setIssueFormOpen(false)}>Hủy</button><button className="button" type="submit" disabled={issueSaving}>{issueSaving ? "Đang xuất..." : "Ghi phiếu xuất"}</button></div>
        </form>
      </section>}
      {receiptFormOpen && resourceKey === "consumables" && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><p className="eyebrow">PHIẾU NHẬP KHO</p><h2>Nhập vật tư tiêu hao</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng phiếu nhập kho" onClick={() => setReceiptFormOpen(false)}>×</button></div>
        <form className="admin-stocktake-form" onSubmit={submitReceipt}>
          <div className="field"><label htmlFor="receipt-material">Vật tư</label><select id="receipt-material" name="vatTuId" required defaultValue=""><option value="" disabled>Chọn vật tư</option>{rows.filter((row) => row.dangSuDung).map((row) => <option key={row.id} value={row.id}>{row.maVatTu} · {row.tenVatTu}</option>)}</select></div>
          <div className="field"><label htmlFor="receipt-quantity">Số lượng nhập</label><input id="receipt-quantity" name="soLuong" type="number" min="0.01" step="0.01" required /></div>
          <div className="field"><label htmlFor="receipt-price">Đơn giá (VNĐ)</label><input id="receipt-price" name="donGia" type="number" min="0" step="0.01" required /></div>
          <div className="field"><label htmlFor="receipt-supplier">Nhà cung cấp</label><select id="receipt-supplier" name="nhaCungCapId" required defaultValue=""><option value="" disabled>Chọn nhà cung cấp</option>{consumableSuppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.tenNhaCungCap}</option>)}</select></div>
          <div className="field"><label htmlFor="receipt-date">Ngày nhập</label><input id="receipt-date" name="ngayNhap" type="date" required max={todayInput()} defaultValue={todayInput()} /></div>
          <div className="field"><label htmlFor="receipt-number">Số phiếu</label><input id="receipt-number" name="soPhieu" maxLength="50" /></div>
          <div className="field"><label htmlFor="receipt-purchase">Đơn mua liên quan</label><input id="receipt-purchase" name="donMuaLienQuan" maxLength="50" /></div>
          {receiptError && <p className="form-error" role="alert">{receiptError}</p>}
          <div className="form-actions"><button className="button secondary" type="button" onClick={() => setReceiptFormOpen(false)}>Hủy</button><button className="button" type="submit" disabled={receiptSaving}>{receiptSaving ? "Đang nhập..." : "Ghi phiếu nhập"}</button></div>
        </form>
      </section>}
      <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading">
          <div><h2>{definition.title}</h2><p className="muted">{numberFormatter.format(resourceKey === "assets" ? assetTotalElements : rows.length)} bản ghi</p></div>
          {resourceKey !== "assets" && <label className="admin-resource-search"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm trong danh sách..." aria-label={`Tìm ${definition.title.toLocaleLowerCase("vi")}`} /></label>}
        </div>
        {resourceKey === "assets" && <form className="admin-asset-filters" onSubmit={applyAssetFilters}>
          <label>Tìm kiếm<input name="keyword" value={assetFilterDraft.keyword} onChange={(event) => setAssetFilterDraft({ ...assetFilterDraft, keyword: event.target.value })} placeholder="Mã, tên, serial..." /></label>
          <label>Loại tài sản<select name="loaiId" value={assetFilterDraft.loaiId} onChange={(event) => setAssetFilterDraft({ ...assetFilterDraft, loaiId: event.target.value })}><option value="">Tất cả loại</option>{assetCategories.map((item) => <option key={item.loaiId} value={item.loaiId}>{item.tenLoai}</option>)}</select></label>
          <label>Trạng thái<select name="tinhTrang" value={assetFilterDraft.tinhTrang} onChange={(event) => setAssetFilterDraft({ ...assetFilterDraft, tinhTrang: event.target.value })}><option value="">Tất cả trạng thái</option><option>Trong kho</option><option>Đang sử dụng</option><option>Bảo trì</option><option>Chờ thanh lý</option><option>Thanh lý</option><option>Đã thanh lý</option></select></label>
          <label>Phòng ban<input name="phongBan" value={assetFilterDraft.phongBan} onChange={(event) => setAssetFilterDraft({ ...assetFilterDraft, phongBan: event.target.value })} placeholder="Phòng ban" /></label>
          <label>Mua từ ngày<input type="date" name="muaTuNgay" value={assetFilterDraft.muaTuNgay} onChange={(event) => setAssetFilterDraft({ ...assetFilterDraft, muaTuNgay: event.target.value })} /></label>
          <label>Đến ngày<input type="date" name="muaDenNgay" value={assetFilterDraft.muaDenNgay} onChange={(event) => setAssetFilterDraft({ ...assetFilterDraft, muaDenNgay: event.target.value })} /></label>
          <div className="admin-asset-filter-actions">
            <button className="button" type="submit" disabled={loading}>Lọc</button>
            <button className="button secondary" type="button" onClick={clearAssetFilters} disabled={loading}>Xóa bộ lọc</button>
          </div>
        </form>}
        <div className="table-wrap" aria-busy={loading}><table><thead><tr>{definition.columns.map(([label]) => <th key={label}>{label.toLocaleUpperCase("vi")}</th>)}<th>THAO TÁC</th></tr></thead><tbody>
          {loading ? <tr><td className="empty loading-state" colSpan={definition.columns.length + 1}>Đang tải dữ liệu...</td></tr>
            : filteredRows.length === 0 ? <tr><td className="empty" colSpan={definition.columns.length + 1}>{error ? "Không thể tải dữ liệu." : query || (resourceKey === "assets" && Object.values(assetFilters).some(Boolean)) ? "Không tìm thấy kết quả." : "Chưa có dữ liệu."}</td></tr>
              : filteredRows.map((row) => <tr key={row[definition.idField]}>
                {definition.columns.map(([label, render]) => <td key={label}>{render(row) ?? "—"}</td>)}
                <td className="actions">{resourceKey === "employees" && row.trangThai === "INACTIVE"
                  && activeAssignments.some((assignment) => {
                    const recipient = (assignment.nguoiNhan ?? "").trim().toLocaleLowerCase("vi");
                    return recipient === row.hoTen.trim().toLocaleLowerCase("vi")
                      || recipient === row.maNhanVien.trim().toLocaleLowerCase("vi");
                  })
                  && <a className="link-button" href={`/admin/assignments?employeeName=${encodeURIComponent(row.hoTen)}&employeeCode=${encodeURIComponent(row.maNhanVien)}#returns`}>Thu hồi tài sản</a>}
                  {resourceKey === "assets" && <button className="link-button" type="button" onClick={() => setSelectedAsset(row)}>Chi tiết</button>}
                  <button className="link-button" type="button" onClick={() => openForm(row)}>Sửa</button>
                  {resourceKey === "consumables" && <button className={row.dangSuDung ? "delete" : "link-button"} type="button" onClick={() => toggleConsumable(row)}>{row.dangSuDung ? "Ngừng sử dụng" : "Kích hoạt lại"}</button>}
                  {definition.removable && <button className="delete" type="button" onClick={() => deleteRow(row)}>Xóa</button>}</td>
              </tr>)}
        </tbody></table></div>
        {resourceKey === "assets" && <div className="admin-resource-pagination"><span>Trang {assetTotalPages === 0 ? 0 : assetPage + 1} / {assetTotalPages}</span><div><button className="button secondary" type="button" onClick={() => setAssetPage((current) => Math.max(0, current - 1))} disabled={assetPage === 0 || loading}>Trước</button><button className="button secondary" type="button" onClick={() => setAssetPage((current) => Math.min(assetTotalPages - 1, current + 1))} disabled={assetTotalPages === 0 || assetPage + 1 >= assetTotalPages || loading}>Sau</button></div></div>}
      </section>
      {resourceKey === "consumables" && <>
        {issueError && <p className="message error" role="alert">{issueError}</p>}
        <section className="panel assets-panel">
          <div className="panel-heading"><div><h2>Yêu cầu cấp vật tư</h2><p className="muted">Duyệt để xuất kho, hoặc từ chối kèm lý do. Danh sách tối đa 100 yêu cầu gần nhất.</p></div></div>
          <div className="table-wrap"><table><thead><tr><th>NGÀY GỬI</th><th>VẬT TƯ</th><th>SỐ LƯỢNG</th><th>NGƯỜI YÊU CẦU / PHÒNG BAN</th><th>LÝ DO</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
            {requestRows.length === 0 ? <tr><td className="empty" colSpan="7">Chưa có yêu cầu cấp vật tư.</td></tr>
              : requestRows.map((row) => <tr key={row.id}><td>{row.taoLuc ? new Date(row.taoLuc).toLocaleString("vi-VN") : "—"}</td><td>{row.maVatTu} · {row.tenVatTu}</td><td>{row.soLuong} {row.donVi}</td><td>{row.nguoiYeuCau}<br /><span className="muted">{row.phongBan || "—"}</span></td><td>{row.lyDo}</td><td>{row.trangThai === "PENDING" ? "Chờ duyệt" : row.trangThai === "ISSUED" ? "Đã xuất" : "Từ chối"}{row.ghiChuDuyet && <><br /><span className="muted">{row.ghiChuDuyet}</span></>}</td><td className="actions">{row.trangThai === "PENDING" ? <><button className="link-button" type="button" onClick={() => updateConsumableRequest(row, "xuat")}>Duyệt & xuất</button><button className="delete" type="button" onClick={() => updateConsumableRequest(row, "tu-choi")}>Từ chối</button></> : "—"}</td></tr>)}
          </tbody></table></div>
        </section>
        <section className="panel assets-panel">
          <div className="panel-heading"><div><h2>Lịch sử nhập xuất</h2><p className="muted">Phiếu đã ghi nhận chỉ xem, không thể sửa hoặc xóa.</p></div></div>
          <form className="admin-asset-filters" onSubmit={loadConsumableMovements}>
            <label>Vật tư<select value={movementFilters.vatTuId} onChange={(event) => setMovementFilters({ ...movementFilters, vatTuId: event.target.value })}><option value="">Tất cả vật tư</option>{rows.map((row) => <option key={row.id} value={row.id}>{row.maVatTu} · {row.tenVatTu}</option>)}</select></label>
            <label>Phòng ban<input value={movementFilters.phongBan} onChange={(event) => setMovementFilters({ ...movementFilters, phongBan: event.target.value })} placeholder="Lọc phòng ban xuất" /></label>
            <label>Từ ngày<input type="date" value={movementFilters.tuNgay} onChange={(event) => setMovementFilters({ ...movementFilters, tuNgay: event.target.value })} /></label>
            <label>Đến ngày<input type="date" value={movementFilters.denNgay} onChange={(event) => setMovementFilters({ ...movementFilters, denNgay: event.target.value })} /></label>
            <button className="button" type="submit">Lọc lịch sử</button>
          </form>
          <div className="table-wrap"><table><thead><tr><th>NGÀY</th><th>LOẠI</th><th>VẬT TƯ</th><th>SỐ LƯỢNG</th><th>PHÒNG BAN / NHÀ CUNG CẤP</th><th>NGƯỜI NHẬN / CHỨNG TỪ</th><th>NGƯỜI GHI NHẬN / GHI CHÚ</th></tr></thead><tbody>
            {movementRows.length === 0 ? <tr><td className="empty" colSpan="7">Không có giao dịch phù hợp.</td></tr>
              : movementRows.map((row) => <tr key={`${row.loai}-${row.id}`}><td>{row.ngay ? new Date(`${row.ngay}T00:00:00`).toLocaleDateString("vi-VN") : "—"}</td><td>{row.loai === "NHAP" ? "Nhập kho" : "Xuất kho"}</td><td>{row.maVatTu} · {row.tenVatTu}</td><td>{row.loai === "NHAP" ? "+" : "−"}{row.soLuong}</td><td>{row.phongBan || row.nhaCungCap || "—"}</td><td>{row.nguoiNhan || [row.soPhieu, row.donMuaLienQuan].filter(Boolean).join(" / ") || "—"}</td><td>{row.nguoiThucHien || "—"}{row.ghiChu && <><br /><span className="muted">{row.ghiChu}</span></>}</td></tr>)}
          </tbody></table></div>
        </section>
        <section className="panel assets-panel">
          <div className="panel-heading"><div><h2>Báo cáo tiêu hao</h2><p className="muted">Tổng lượng vật tư đã xuất theo phòng ban và tháng.</p></div></div>
          <form className="admin-asset-filters" onSubmit={loadConsumableUsage}>
            <label>Từ ngày<input type="date" value={usageFilters.tuNgay} onChange={(event) => setUsageFilters({ ...usageFilters, tuNgay: event.target.value })} /></label>
            <label>Đến ngày<input type="date" value={usageFilters.denNgay} onChange={(event) => setUsageFilters({ ...usageFilters, denNgay: event.target.value })} /></label>
            <button className="button" type="submit">Lọc báo cáo</button>
          </form>
          <div className="table-wrap"><table><thead><tr><th>THÁNG</th><th>PHÒNG BAN</th><th>VẬT TƯ</th><th>TỔNG ĐÃ XUẤT</th></tr></thead><tbody>
            {usageRows.length === 0 ? <tr><td className="empty" colSpan="4">Chưa có dữ liệu xuất kho để báo cáo.</td></tr>
              : usageRows.map((row, index) => <tr key={`${row.thang}-${row.phongBan}-${row.vatTuId}-${index}`}><td>{row.thang}</td><td>{row.phongBan}</td><td>{row.maVatTu} · {row.tenVatTu}</td><td>{row.soLuong} {row.donVi}</td></tr>)}
          </tbody></table></div>
        </section>
      </>}
      {selectedAsset && resourceKey === "assets" && <section className="panel admin-asset-history" ref={assetDetailRef}>
        <div className="panel-heading">
          <div><p className="eyebrow">HỒ SƠ TÀI SẢN</p><h2>{selectedAsset.maTaiSan} · {selectedAsset.tenTaiSan}</h2></div>
          <div className="admin-asset-detail-actions">
            <button className="button secondary" type="button" onClick={() => openForm(selectedAsset)}>Sửa thông tin</button>
            <button className="icon-button" type="button" aria-label="Đóng chi tiết tài sản" onClick={() => setSelectedAsset(null)}>×</button>
          </div>
        </div>
        <dl className="detail-grid">
          <div><dt>Loại tài sản</dt><dd>{selectedAsset.tenLoaiTaiSan || "—"}</dd></div>
          <div><dt>Trạng thái</dt><dd>{selectedAsset.tinhTrang || "—"}</dd></div>
          <div><dt>Serial</dt><dd>{selectedAsset.serialNumber || "—"}</dd></div>
          <div><dt>Hãng / Model</dt><dd>{[selectedAsset.brand, selectedAsset.model].filter(Boolean).join(" / ") || "—"}</dd></div>
          <div><dt>Ngày mua / Giá mua</dt><dd>{selectedAsset.ngayMua ? new Date(selectedAsset.ngayMua).toLocaleDateString("vi-VN") : "—"} · {selectedAsset.giaMua == null ? "—" : `${currencyFormatter.format(selectedAsset.giaMua)} ₫`}</dd></div>
          <div><dt>Nhà cung cấp</dt><dd>{selectedAsset.supplierName || "—"}</dd></div>
          <div><dt>Bảo hành đến</dt><dd>{selectedAsset.warrantyEndDate ? new Date(selectedAsset.warrantyEndDate).toLocaleDateString("vi-VN") : "—"}</dd></div>
          <div><dt>Vị trí</dt><dd>{[selectedAsset.toaNha, selectedAsset.tang && `Tầng ${selectedAsset.tang}`, selectedAsset.phong && `Phòng ${selectedAsset.phong}`].filter(Boolean).join(" · ") || "—"}</dd></div>
          <div><dt>Người đang giữ</dt><dd>{selectedAsset.nguoiDangGiu || "—"}{selectedAsset.phongBanDangGiu ? ` · ${selectedAsset.phongBanDangGiu}` : ""}</dd></div>
          <div><dt>Khấu hao</dt><dd>{selectedAsset.thoiGianKhauHaoNam ?? 5} năm</dd></div>
          <div><dt>Chu kỳ bảo trì</dt><dd>{selectedAsset.chuKyBaoTriThang ?? 12} tháng</dd></div>
          <div><dt>Bảo trì gần nhất</dt><dd>{selectedAsset.baoTriGanNhat ? new Date(selectedAsset.baoTriGanNhat).toLocaleDateString("vi-VN") : "—"}</dd></div>
          <div><dt>Bảo trì tiếp theo</dt><dd>{selectedAsset.baoTriTiepTheo ? new Date(selectedAsset.baoTriTiepTheo).toLocaleDateString("vi-VN") : "—"}</dd></div>
          <div><dt>Chi phí bảo trì tích lũy</dt><dd>{currencyFormatter.format(selectedAsset.chiPhiBaoTriTichLuy ?? 0)} ₫</dd></div>
          <div><dt>Giá trị còn lại</dt><dd>{selectedAsset.giaTriConLai == null ? "Chưa đủ dữ liệu" : `${currencyFormatter.format(selectedAsset.giaTriConLai)} ₫`}</dd></div>
          <div><dt>Ghi chú</dt><dd>{selectedAsset.notes || "—"}</dd></div>
        </dl>
        {selectedAsset.imageReference && <p className="admin-asset-image-link"><button type="button" onClick={() => openAssetReference(selectedAsset.imageReference, setHistoryError)}>Mở ảnh tài sản ↗</button></p>}
        {historyError && <p className="message error" role="alert">{historyError}</p>}
        {historyLoading ? <p className="notification-empty">Đang tải lịch sử...</p> : <>
          <div className="admin-history-grid">
            <section><h3>Lịch sử điều chuyển</h3>
              {assetHistory.assignments.length === 0 ? <p className="notification-empty">Chưa có phiếu bàn giao.</p>
                : <div className="table-wrap"><table><thead><tr><th>NGƯỜI NHẬN</th><th>PHÒNG BAN</th><th>BÀN GIAO</th><th>DỰ KIẾN THU HỒI</th><th>THU HỒI</th></tr></thead><tbody>
                  {assetHistory.assignments.map((item) => <tr key={item.id}><td>{item.nguoiNhan}</td><td>{item.phongBan || "—"}</td><td>{item.banGiaoLuc ? new Date(item.banGiaoLuc).toLocaleDateString("vi-VN") : "—"}</td><td>{item.ngayDuKienThuHoi ? new Date(item.ngayDuKienThuHoi).toLocaleDateString("vi-VN") : "—"}</td><td>{item.thuHoiLuc ? new Date(item.thuHoiLuc).toLocaleDateString("vi-VN") : "Đang sử dụng"}</td></tr>)}
                </tbody></table></div>}
            </section>
            <section><h3>Lịch sử sửa chữa / bảo trì</h3>
              {assetHistory.maintenances.length === 0 ? <p className="notification-empty">Chưa có phiếu sửa chữa hoặc bảo trì.</p>
                : <div className="table-wrap"><table><thead><tr><th>NGÀY</th><th>LỖI / NỘI DUNG</th><th>KẾT QUẢ</th><th>TRẠNG THÁI</th><th>NGƯỜI THỰC HIỆN</th><th>CHI PHÍ</th></tr></thead><tbody>
                  {assetHistory.maintenances.map((item) => <tr key={item.id}><td>{item.ngayBaoHong ? new Date(`${item.ngayBaoHong}T00:00:00`).toLocaleDateString("vi-VN") : item.taoLuc ? new Date(item.taoLuc).toLocaleDateString("vi-VN") : "—"}</td><td>{item.tieuDe}</td><td>{item.ketQua || "—"}</td><td>{item.trangThai === "KHONG_SUA_DUOC" ? "Không sửa được" : item.trangThai === "HOAN_TAT" ? "Hoàn thành" : item.trangThai === "YEU_CAU" ? "Chờ xử lý" : item.trangThai === "DANG_XU_LY" ? "Đang sửa" : item.trangThai}</td><td>{item.nguoiThucHien || "—"}</td><td>{currencyFormatter.format(item.chiPhi ?? 0)} ₫</td></tr>)}
                </tbody></table></div>}
            </section>
          </div>
          <section className="admin-asset-depreciation">
            <h3>Lịch khấu hao đường thẳng</h3>
            {assetHistory.depreciation.length === 0 ? <p className="notification-empty">Chưa đủ dữ liệu giá mua, ngày mua hoặc thời gian khấu hao.</p>
              : <div className="table-wrap"><table><thead><tr><th>NĂM</th><th>ĐẦU NĂM</th><th>KHẤU HAO</th><th>CUỐI NĂM</th></tr></thead><tbody>
                {assetHistory.depreciation.map((row) => <tr key={row.nam}><td>{row.nam}</td><td>{currencyFormatter.format(row.giaTriConLaiDauNam)} ₫</td><td>{currencyFormatter.format(row.khauHaoNam)} ₫</td><td>{currencyFormatter.format(row.giaTriConLaiCuoiNam)} ₫</td></tr>)}
              </tbody></table></div>}
          </section>
        </>}
      </section>}
      {resourceKey === "invoices" && <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading"><div><h2>Bảng khấu hao đường thẳng</h2><p className="muted">Giá trị đầu năm, khấu hao năm và giá trị cuối năm theo từng tài sản.</p></div></div>
        {depreciationError && <p className="message error" role="alert">{depreciationError}</p>}
        <div className="table-wrap"><table><thead><tr><th>MÃ TÀI SẢN</th><th>TÊN TÀI SẢN</th><th>NĂM</th><th>ĐẦU NĂM</th><th>KHẤU HAO</th><th>CUỐI NĂM</th></tr></thead><tbody>
          {depreciationRows.length === 0 ? <tr><td className="empty" colSpan="6">Chưa có dữ liệu khấu hao đầy đủ.</td></tr>
            : depreciationRows.map((row, index) => <tr key={`${row.taiSanId}-${row.nam}-${index}`}><td>{row.maTaiSan}</td><td>{row.tenTaiSan}</td><td>{row.nam}</td><td>{currencyFormatter.format(row.giaTriConLaiDauNam)} ₫</td><td>{currencyFormatter.format(row.khauHaoNam)} ₫</td><td>{currencyFormatter.format(row.giaTriConLaiCuoiNam)} ₫</td></tr>)}
        </tbody></table></div>
      </section>}
    </>
  );
}

function CategoryPage() {
  const [activeTab, setActiveTab] = useState("categories");
  return (
    <>
      <div className="admin-resource-tabs" role="tablist" aria-label="Nhóm danh mục">
        {catalogTabs.map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={activeTab === key} className={activeTab === key ? "active" : ""} onClick={() => setActiveTab(key)}>{label}</button>)}
      </div>
      <ResourcePage key={activeTab} resourceKey={activeTab} />
    </>
  );
}

function LifecyclePage({ page }) {
  const maintenance = page === "maintenance";
  const links = maintenance
    ? [["Bảo trì định kỳ", "/#maintenance"], ["Sửa chữa", "/#repairs"]]
    : [["Bàn giao tài sản", "/admin/assignments"], ["Thu hồi", "/admin/assignments#returns"]];
  const title = maintenance ? "Bảo trì / Sửa chữa" : "Bàn giao & Thu hồi";
  return (
    <>
      <section className="welcome"><div><p className="eyebrow">VÒNG ĐỜI TÀI SẢN</p><h1>{title}</h1><p className="welcome-copy">Theo dõi bàn giao, thu hồi và tình trạng vận hành thiết bị.</p></div></section>
      <section className="panel admin-workflow-launcher">
        <div className="panel-heading"><div><h2>Chọn nghiệp vụ</h2><p className="muted">Mở màn hình nghiệp vụ để tạo phiếu và cập nhật trạng thái tài sản.</p></div></div>
        <div className="admin-workflow-links">{links.map(([label, href]) => <a className="button" key={label} href={href}>{label}<span aria-hidden="true"> →</span></a>)}</div>
      </section>
    </>
  );
}

function MaintenanceSchedulesPage() {
  const [schedules, setSchedules] = useState([]);
  const [assets, setAssets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [editing, setEditing] = useState(null);
  const [cyclePreset, setCyclePreset] = useState("12");
  const [historySchedule, setHistorySchedule] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [workTarget, setWorkTarget] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [scheduleRows, assetPage] = await Promise.all([
        adminRequest(`${API_URL}/admin/maintenance-schedules`),
        adminRequest(`${API_URL}/tai-san?page=0&size=100`),
      ]);
      setSchedules(Array.isArray(scheduleRows) ? scheduleRows : []);
      let rows = Array.isArray(assetPage) ? assetPage : assetPage?.content ?? [];
      if (!Array.isArray(assetPage) && assetPage?.totalPages > 1) {
        const remainingPages = await Promise.all(Array.from({ length: assetPage.totalPages - 1 }, (_, index) =>
          adminRequest(`${API_URL}/tai-san?page=${index + 1}&size=100`)));
        rows = [...rows, ...remainingPages.flatMap((page) => page?.content ?? [])];
      }
      setAssets(rows.filter((asset) => !["Thanh lý", "Đã thanh lý"].includes(asset.tinhTrang)));
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  useEffect(() => {
    if (loading || !window.location.hash.startsWith("#schedule-")) return;
    document.getElementById(decodeURIComponent(window.location.hash.slice(1)))?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [loading, schedules]);

  async function loadHistory(schedule) {
    setHistorySchedule(schedule);
    setHistoryLoading(true);
    setError("");
    try {
      setHistory(await adminRequest(`${API_URL}/admin/maintenance-schedules/${schedule.id}/history`));
    } catch (loadError) {
      setError(loadError.message);
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  }

  function startCreate() {
    setEditing(null);
    setCyclePreset("12");
    setError("");
    setMessage("");
  }

  function startEdit(schedule) {
    setEditing(schedule);
    setCyclePreset([1, 3, 6, 12].includes(schedule.chuKyThang) ? String(schedule.chuKyThang) : "custom");
    setError("");
    setMessage("");
  }

  async function saveSchedule(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const cycle = cyclePreset === "custom" ? Number(data.get("customCycle")) : Number(cyclePreset);
    if (!Number.isInteger(cycle) || cycle < 1) {
      setError("Chu kỳ khác phải là số nguyên dương tính theo tháng.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      await adminRequest(`${API_URL}/admin/maintenance-schedules${editing ? `/${editing.id}` : ""}`, {
        method: editing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          taiSanId: editing ? editing.taiSanId : Number(data.get("taiSanId")),
          chuKyThang: cycle,
          ngayBatDau: data.get("ngayBatDau"),
        }),
      });
      setEditing(null);
      setCyclePreset("12");
      setMessage(editing ? "Đã cập nhật lịch bảo trì." : "Đã tạo lịch bảo trì.");
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function updateSchedule(schedule, action) {
    const labels = { pause: "tạm dừng", resume: "tiếp tục", delete: "ngừng và xóa khỏi danh sách" };
    if (action === "delete" && !window.confirm(`Ngừng lịch bảo trì của ${schedule.maTaiSan}? Lịch sử các phiếu vẫn được giữ.`)) return;
    setActionId(schedule.id);
    setError("");
    setMessage("");
    try {
      await adminRequest(`${API_URL}/admin/maintenance-schedules/${schedule.id}${action === "delete" ? "" : `/${action}`}`, {
        method: action === "delete" ? "DELETE" : "POST",
      });
      setMessage(`Đã ${labels[action]} lịch bảo trì ${schedule.maTaiSan}.`);
      await loadData();
    } catch (actionError) {
      setError(actionError.message);
    } finally {
      setActionId(null);
    }
  }

  async function completeMaintenance(event) {
    event.preventDefault();
    if (!workTarget) return;
    const data = new FormData(event.currentTarget);
    setActionId(workTarget.id);
    setError("");
    setMessage("");
    try {
      await adminRequest(`${API_URL}/admin/maintenance-schedules/${workTarget.id}/maintenance`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          noiDung: String(data.get("noiDung")).trim(),
          nguoiThucHien: String(data.get("nguoiThucHien")).trim(),
          chiPhi: Number(data.get("chiPhi") || 0),
          ngayHoanThanh: data.get("ngayHoanThanh"),
        }),
      });
      setWorkTarget(null);
      setMessage(`Đã ghi nhận bảo trì ${workTarget.maTaiSan}; ngày đến hạn được cập nhật.`);
      await loadData();
      if (historySchedule?.id === workTarget.id) await loadHistory(workTarget);
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setActionId(null);
    }
  }

  const dueCount = schedules.filter((item) => ["Sắp đến hạn", "Đến hạn"].includes(item.trangThai)).length;
  const overdueCount = schedules.filter((item) => item.trangThai === "Quá hạn").length;

  return <>
    <section className="welcome">
      <div><p className="eyebrow">QUẢN LÝ VẬN HÀNH</p><h1>Lịch bảo trì định kỳ</h1>
        <p className="welcome-copy">Theo dõi chu kỳ bảo dưỡng, lần gần nhất và ngày đến hạn tiếp theo của từng tài sản.</p></div>
      <div className="admin-resource-actions">
        <a className="button secondary" href="/#repairs">Mở danh sách sửa chữa</a>
        <button className="button" type="button" onClick={startCreate}>＋ Thêm lịch bảo trì</button>
      </div>
    </section>
    {error && <p className="message error" role="alert">{error} <button className="retry-button" type="button" onClick={loadData}>Thử lại</button></p>}
    {message && <p className="message" role="status">{message}</p>}
    <section className="stats admin-maintenance-stats" aria-label="Thống kê lịch bảo trì" aria-busy={loading}>
      <article className="stat stat-blue"><div className="stat-top"><p>Tổng lịch đang theo dõi</p><span className="stat-icon">◷</span></div><h2>{loading ? "…" : numberFormatter.format(schedules.length)}</h2></article>
      <article className="stat stat-orange"><div className="stat-top"><p>Sắp đến hạn / đến hạn</p><span className="stat-icon">!</span></div><h2>{loading ? "…" : numberFormatter.format(dueCount)}</h2></article>
      <article className="stat stat-purple"><div className="stat-top"><p>Quá hạn</p><span className="stat-icon">↗</span></div><h2>{loading ? "…" : numberFormatter.format(overdueCount)}</h2></article>
    </section>
    <section className="panel admin-resource-form">
      <div className="panel-heading"><div><p className="eyebrow">{editing ? "CHỈNH SỬA" : "THIẾT LẬP"}</p>
        <h2>{editing ? `Cập nhật lịch · ${editing.maTaiSan}` : "Thêm lịch bảo trì"}</h2>
        <p className="muted">Tài sản thanh lý không thể lập lịch; chu kỳ được tính theo tháng.</p></div></div>
      <form key={editing?.id ?? "new"} className="admin-maintenance-form" onSubmit={saveSchedule}>
        <label>Tài sản<select name="taiSanId" required defaultValue={editing?.taiSanId ?? ""} disabled={Boolean(editing)}>
          <option value="">Chọn tài sản</option>
          {assets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>
            {asset.maTaiSan} · {asset.tenTaiSan} · {asset.tinhTrang}
          </option>)}
        </select></label>
        <label>Chu kỳ<select value={cyclePreset} onChange={(event) => setCyclePreset(event.target.value)}>
          <option value="1">Hằng tháng</option><option value="3">Mỗi 3 tháng</option>
          <option value="6">Mỗi 6 tháng</option><option value="12">Hằng năm</option>
          <option value="custom">Chu kỳ khác</option>
        </select></label>
        {cyclePreset === "custom" && <label>Số tháng<input key={editing?.chuKyThang ?? "custom"} name="customCycle" type="number" min="1" step="1"
          required defaultValue={editing && ![1, 3, 6, 12].includes(editing.chuKyThang) ? editing.chuKyThang : ""} placeholder="Nhập số nguyên dương" /></label>}
        <label>Ngày bắt đầu<input name="ngayBatDau" type="date" required defaultValue={editing?.ngayBatDau ?? ""} /></label>
        <div className="admin-maintenance-form-actions">
          {editing && <button className="button secondary" type="button" onClick={startCreate}>Hủy sửa</button>}
          <button className="button" type="submit" disabled={saving || loading || (!editing && assets.length === 0)}>
            {saving ? "Đang lưu..." : editing ? "Lưu thay đổi" : "Tạo lịch"}
          </button>
        </div>
      </form>
    </section>
    {workTarget && <section className="panel admin-resource-form">
      <div className="panel-heading"><div><p className="eyebrow">PHIẾU BẢO TRÌ</p>
        <h2>{workTarget.maTaiSan} · {workTarget.tenTaiSan}</h2></div>
        <button className="icon-button" type="button" aria-label="Đóng phiếu" onClick={() => setWorkTarget(null)}>×</button></div>
      <form className="admin-maintenance-form" onSubmit={completeMaintenance}>
        <label className="admin-maintenance-wide">Nội dung công việc<textarea name="noiDung" required maxLength="1000" rows="3" placeholder="Nội dung bảo dưỡng đã thực hiện" /></label>
        <label>Người thực hiện<input name="nguoiThucHien" required maxLength="100" placeholder="Họ tên người thực hiện" /></label>
        <label>Chi phí (VNĐ)<input name="chiPhi" type="number" min="0" step="1" defaultValue="0" /></label>
        <label>Ngày hoàn thành<input name="ngayHoanThanh" type="date" required max={localDateValue()} defaultValue={localDateValue()} /></label>
        <div className="admin-maintenance-form-actions">
          <button className="button secondary" type="button" onClick={() => setWorkTarget(null)}>Hủy</button>
          <button className="button" type="submit" disabled={actionId === workTarget.id}>{actionId === workTarget.id ? "Đang lưu..." : "Hoàn thành bảo trì"}</button>
        </div>
      </form>
    </section>}
    <section className="panel assets-panel admin-resource-panel">
      <div className="panel-heading assets-heading"><div><h2>Danh sách lịch bảo trì</h2>
        <p className="muted">{numberFormatter.format(schedules.length)} lịch · Có thể tạm dừng hoặc ngừng theo dõi, lịch sử đã ghi nhận không bị xóa.</p></div>
        <button className="button secondary" type="button" onClick={loadData} disabled={loading}>{loading ? "Đang tải..." : "Làm mới"}</button></div>
      <div className="table-wrap" aria-busy={loading}><table className="admin-maintenance-table"><thead><tr>
        <th>TÀI SẢN / LOẠI</th><th>PHÒNG BAN / NGƯỜI GIỮ</th><th>CHU KỲ</th><th>NGÀY GẦN NHẤT</th>
        <th>ĐẾN HẠN TIẾP</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>
        {loading ? <tr><td className="empty loading-state" colSpan="7">Đang tải lịch bảo trì...</td></tr>
          : schedules.length === 0 ? <tr><td className="empty" colSpan="7">Chưa có lịch bảo trì. Hãy tạo lịch để bắt đầu theo dõi.</td></tr>
            : schedules.map((schedule) => {
              const isDeferred = ["Tạm dừng", "Tạm hoãn", "Đã thanh lý"].includes(schedule.trangThai);
              const canComplete = !isDeferred && ["Sắp đến hạn", "Đến hạn", "Quá hạn"].includes(schedule.trangThai);
              return <tr id={`schedule-${schedule.id}`} key={schedule.id}>
                <td><strong>{schedule.maTaiSan} · {schedule.tenTaiSan}</strong><br /><span className="muted">{schedule.tenLoaiTaiSan || "Chưa phân loại"}</span></td>
                <td>{schedule.phongBan || "—"}<br /><span className="muted">{schedule.nguoiDangGiu || "Chưa gán người giữ"}</span></td>
                <td>{schedule.chuKyThang === 1 ? "Hằng tháng" : schedule.chuKyThang === 3 ? "3 tháng" : schedule.chuKyThang === 6 ? "6 tháng" : schedule.chuKyThang === 12 ? "Hằng năm" : `${schedule.chuKyThang} tháng`}</td>
                <td>{schedule.ngayBaoTriGanNhat ? new Date(`${schedule.ngayBaoTriGanNhat}T00:00:00`).toLocaleDateString("vi-VN") : "Chưa thực hiện"}</td>
                <td>{new Date(`${schedule.ngayDenHanTiepTheo}T00:00:00`).toLocaleDateString("vi-VN")}</td>
                <td><span className={`status ${schedule.trangThai === "Quá hạn" ? "retired" : schedule.trangThai === "Đến hạn" ? "warning" : schedule.trangThai === "Đã hoàn thành" ? "in-use" : ""}`}>{schedule.trangThai}</span></td>
                <td className="actions admin-maintenance-actions">
                  {canComplete && <button className="link-button" type="button" onClick={() => setWorkTarget(schedule)}>Tạo phiếu bảo trì</button>}
                  {!["Tạm hoãn", "Đã thanh lý", "Chờ thanh lý"].includes(schedule.trangThai)
                    && <a className="link-button" href={`/?repairAssetId=${schedule.taiSanId}&repairSource=BAO_TRI_DINH_KY#repairs`}>Báo hỏng</a>}
                  <button className="link-button" type="button" onClick={() => loadHistory(schedule)}>Lịch sử</button>
                  {!["Đã thanh lý", "Chờ thanh lý"].includes(schedule.trangThai) && <button className="link-button" type="button" onClick={() => startEdit(schedule)}>Sửa</button>}
                  {!["Đã thanh lý", "Chờ thanh lý"].includes(schedule.trangThai) && <button className="link-button" type="button" disabled={actionId === schedule.id}
                    onClick={() => updateSchedule(schedule, schedule.trangThai === "Tạm dừng" ? "resume" : "pause")}>
                    {schedule.trangThai === "Tạm dừng" ? "Tiếp tục" : "Tạm dừng"}
                  </button>}
                  <button className="delete" type="button" disabled={actionId === schedule.id} onClick={() => updateSchedule(schedule, "delete")}>Xóa lịch</button>
                </td>
              </tr>;
            })}
      </tbody></table></div>
    </section>
    {historySchedule && <section className="panel assets-panel admin-resource-panel">
      <div className="panel-heading assets-heading"><div><p className="eyebrow">LỊCH SỬ CHỈ ĐỌC</p>
        <h2>{historySchedule.maTaiSan} · {historySchedule.tenTaiSan}</h2></div>
        <button className="icon-button" type="button" aria-label="Đóng lịch sử bảo trì" onClick={() => setHistorySchedule(null)}>×</button></div>
      <div className="table-wrap" aria-busy={historyLoading}><table><thead><tr>
        <th>NGÀY HOÀN THÀNH</th><th>NỘI DUNG</th><th>NGƯỜI THỰC HIỆN</th><th>CHI PHÍ</th></tr></thead><tbody>
        {historyLoading ? <tr><td className="empty loading-state" colSpan="4">Đang tải lịch sử...</td></tr>
          : history.length === 0 ? <tr><td className="empty" colSpan="4">Tài sản chưa có lịch sử bảo trì.</td></tr>
            : history.map((item) => <tr key={item.id}><td>{item.ngayHoanThanh ? new Date(`${item.ngayHoanThanh}T00:00:00`).toLocaleDateString("vi-VN") : item.hoanTatLuc ? new Date(item.hoanTatLuc).toLocaleDateString("vi-VN") : "Chưa hoàn thành"}</td>
              <td><strong>{item.tieuDe}</strong>{item.moTa && <><br /><span className="muted">{item.moTa}</span></>}</td>
              <td>{item.nguoiThucHien || item.nguoiYeuCau || "—"}</td><td>{currencyFormatter.format(item.chiPhi ?? 0)} ₫</td></tr>)}
      </tbody></table></div>
    </section>}
  </>;
}

function StocktakePage({ currentUser }) {
  const [rows, setRows] = useState([]);
  const [selectedStocktake, setSelectedStocktake] = useState(null);
  const [items, setItems] = useState([]);
  const [extras, setExtras] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [locations, setLocations] = useState([]);
  const [scopeType, setScopeType] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [itemSavingId, setItemSavingId] = useState(null);
  const isAdmin = currentUser?.role === "ADMIN";

  const loadStocktakes = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const endpoint = isAdmin ? "/admin/kiem-ke" : "/admin/kiem-ke/my";
      setRows(await adminRequest(`${API_URL}${endpoint}`));
      return true;
    } catch (loadError) {
      setError(loadError.message);
      setRows([]);
      return false;
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => { loadStocktakes(); }, [loadStocktakes]);

  useEffect(() => {
    if (!isAdmin) return;
    Promise.all([
      adminRequest(`${API_URL}/admin/phong-ban`),
      adminRequest(`${API_URL}/admin/vi-tri`),
    ])
      .then(([departmentRows, locationRows]) => {
        setDepartments(departmentRows);
        setLocations(locationRows);
      })
      .catch((loadError) => setError(loadError.message));
  }, [isAdmin]);

  const loadItems = useCallback(async () => {
    if (!selectedStocktake) return;
    setItemsLoading(true);
    setError("");
    try {
      const [itemRows, extraRows] = await Promise.all([
        adminRequest(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/items`),
        adminRequest(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/extras`),
      ]);
      setItems(itemRows);
      setExtras(extraRows);
    } catch (loadError) {
      setError(loadError.message);
      setItems([]);
      setExtras([]);
    } finally {
      setItemsLoading(false);
    }
  }, [selectedStocktake]);

  useEffect(() => { loadItems(); }, [loadItems]);

  async function createStocktake(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSaving(true);
    setError("");
    try {
      const created = await adminRequest(`${API_URL}/admin/kiem-ke`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tenDot: String(data.get("tenDot")).trim(),
          phongBan: scopeType === "DEPARTMENT" ? String(data.get("phamVi")).trim() : null,
          batDauLuc: data.get("batDauLuc"),
          ketThucLuc: data.get("ketThucLuc") || null,
          trangThai: "DRAFT",
          nguoiPhuTrach: String(data.get("nguoiPhuTrach")).trim(),
          nguoiKiem: String(data.get("nguoiKiem")).trim(),
          nguoiXacNhan: String(data.get("nguoiXacNhan")).trim(),
          loaiPhamVi: scopeType,
          phamVi: scopeType === "ALL" ? null : String(data.get("phamVi")).trim(),
        }),
      });
      form.reset();
      setScopeType("ALL");
      await loadStocktakes();
      setSelectedStocktake(created);
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function saveStocktakeItem(event, item) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setItemSavingId(item.id);
    setError("");
    try {
      const updated = await adminRequest(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/items/${item.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tinhTrangThucTe: data.get("tinhTrangThucTe"),
            viTriThucTe: String(data.get("viTriThucTe") ?? "").trim(),
            ghiChu: String(data.get("ghiChu") ?? "").trim(),
          }),
        });
        setItems((current) => current.map((row) => row.id === item.id ? updated : row));
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setItemSavingId(null);
    }
  }

  async function addStocktakeExtra(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setSaving(true);
    setError("");
    try {
      const created = await adminRequest(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/extras`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          maTaiSan: String(data.get("maTaiSan")).trim(),
          tenTaiSan: String(data.get("tenTaiSan")).trim(),
          viTriThucTe: String(data.get("viTriThucTe") ?? "").trim(),
          ghiChu: String(data.get("ghiChu") ?? "").trim(),
        }),
      });
      form.reset();
      setExtras((current) => [...current, created]);
      await loadStocktakes();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function transitionStocktake(action, confirmation) {
    if (!selectedStocktake || !window.confirm(confirmation)) return;
    setSaving(true);
    setError("");
    try {
      const updated = await adminRequest(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/${action}`, { method: "POST" });
      setSelectedStocktake(updated);
      await loadStocktakes();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  function exportVariance() {
    const columns = ["Nhóm", "Mã tài sản", "Tên tài sản", "Vị trí theo sổ", "Người giữ theo sổ", "Vị trí thực tế", "Hiện trạng", "Ghi chú"];
    const rowsToExport = [
      ...items.filter((item) => item.daKiemLuc && item.tinhTrangThucTe !== "CO_MAT").map((item) => [
        item.tinhTrangThucTe, item.maTaiSan, item.tenTaiSan, item.viTriTheoSo,
        item.nguoiGiuTheoSo, item.viTriThucTe, item.tinhTrangThucTe, item.ghiChu,
      ]),
      ...extras.map((item) => ["THUA", item.maTaiSan, item.tenTaiSan, "", "", item.viTriThucTe, "THUA", item.ghiChu]),
    ];
    const safeCell = (value) => {
      let text = String(value ?? "");
      if (/^[\s]*[=+\-@]/.test(text)) text = `'${text}`;
      return `"${text.replaceAll('"', '""')}"`;
    };
    const csv = [
      columns.map(safeCell).join(","),
      ...rowsToExport.map((row) => row.map(safeCell).join(",")),
    ].join("\r\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `chenh-lech-kiem-ke-${selectedStocktake.id}.csv`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function createMaintenance(item) {
    setError("");
    try {
      const result = await adminRequest(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/items/${item.id}/maintenance`, { method: "POST" });
      setItems((current) => current.map((row) => row.id === item.id
        ? { ...row, maintenanceRequestId: result.maintenanceRequestId }
        : row));
    } catch (actionError) {
      setError(actionError.message);
    }
  }

  async function downloadReport() {
    setError("");
    try {
      const headers = new Headers();
      const token = sessionStorage.getItem("officecare.accessToken");
      if (token) headers.set("Authorization", `Bearer ${token}`);
      const response = await fetchWithSession(`${API_URL}/admin/kiem-ke/${selectedStocktake.id}/report.pdf`, { headers });
      if (!response.ok) throw new Error(`Không tải được biên bản PDF (HTTP ${response.status}).`);
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = `bien-ban-kiem-ke-${selectedStocktake.id}.pdf`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (downloadError) {
      setError(downloadError.message);
    }
  }

  const checkedCount = items.filter((item) => item.daKiemLuc).length;
  const uncheckedCount = Math.max(items.length - checkedCount, 0);
  const progress = items.length ? Math.round(checkedCount / items.length * 100) : 0;
  const varianceGroups = [
    { status: "THIEU", label: "Thiếu", items: items.filter((item) => item.daKiemLuc && item.tinhTrangThucTe === "THIEU") },
    { status: "HONG", label: "Hỏng", items: items.filter((item) => item.daKiemLuc && item.tinhTrangThucTe === "HONG") },
    { status: "SAI_VI_TRI", label: "Sai vị trí", items: items.filter((item) => item.daKiemLuc && item.tinhTrangThucTe === "SAI_VI_TRI") },
    { status: "THUA", label: "Thừa", items: extras },
  ];
  const statusLabels = { DRAFT: "Nháp", OPEN: "Đang kiểm", COMPLETED: "Hoàn thành", CANCELLED: "Đã hủy" };

  return (
    <>
      <section className="welcome"><div><p className="eyebrow">ĐỐI CHIẾU THỰC TẾ</p><h1>Kiểm kê tài sản</h1><p className="welcome-copy">Theo dõi tiến độ kiểm kê, chênh lệch và biên bản theo từng đợt.</p></div></section>
      {error && <p className="message error" role="alert">{error}</p>}
      {isAdmin && <section className="panel admin-resource-form">
        <div className="panel-heading"><div><h2>Tạo đợt kiểm kê</h2><p className="muted">Bắt đầu một đợt kiểm kê mới.</p></div></div>
        <form className="admin-stocktake-form" onSubmit={createStocktake}>
          <div className="field"><label htmlFor="stocktake-name">Tên đợt</label><input id="stocktake-name" name="tenDot" required maxLength="100" /></div>
          <div className="field"><label htmlFor="stocktake-scope-type">Phạm vi</label><select id="stocktake-scope-type" value={scopeType} onChange={(event) => setScopeType(event.target.value)}><option value="ALL">Toàn công ty</option><option value="DEPARTMENT">Phòng ban</option><option value="LOCATION">Vị trí</option></select></div>
          {scopeType !== "ALL" && <div className="field"><label htmlFor="stocktake-scope-value">{scopeType === "DEPARTMENT" ? "Phòng ban" : "Vị trí"}</label><select id="stocktake-scope-value" name="phamVi" required defaultValue="">
            <option value="">Chọn {scopeType === "DEPARTMENT" ? "phòng ban" : "vị trí"}</option>
            {scopeType === "DEPARTMENT"
              ? departments.map((department) => <option key={department.id} value={department.tenPhongBan}>{department.tenPhongBan}</option>)
              : locations.map((location) => <option key={location.id} value={[location.toaNha, location.tang && `Tầng ${location.tang}`, location.phong && `Phòng ${location.phong}`].filter(Boolean).join(" · ")}>{[location.toaNha, location.tang && `Tầng ${location.tang}`, location.phong && `Phòng ${location.phong}`].filter(Boolean).join(" · ")}</option>)}
          </select></div>}
          <div className="field"><label htmlFor="stocktake-start">Ngày bắt đầu</label><input id="stocktake-start" name="batDauLuc" type="date" max={localDateValue()} defaultValue={localDateValue()} required /></div>
          <div className="field"><label htmlFor="stocktake-end">Ngày kết thúc dự kiến</label><input id="stocktake-end" name="ketThucLuc" type="date" min={localDateValue()} /></div>
          <div className="field"><label htmlFor="stocktake-responsible">Tài khoản phụ trách</label><input id="stocktake-responsible" name="nguoiPhuTrach" defaultValue={currentUser?.username ?? ""} required maxLength="50" /></div>
          <div className="field"><label htmlFor="stocktake-checker">Người kiểm</label><input id="stocktake-checker" name="nguoiKiem" defaultValue={currentUser?.username ?? ""} required maxLength="150" /></div>
          <div className="field"><label htmlFor="stocktake-confirmer">Người xác nhận</label><input id="stocktake-confirmer" name="nguoiXacNhan" required maxLength="150" /></div>
          <button className="button" type="submit" disabled={saving}>{saving ? "Đang tạo..." : "Tạo bản nháp"}</button>
        </form>
      </section>}
      <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading"><div><h2>{isAdmin ? "Các đợt kiểm kê" : "Đợt được giao"}</h2><p className="muted">{numberFormatter.format(rows.length)} đợt</p></div><button className="text-button" type="button" onClick={loadStocktakes} disabled={loading}>Làm mới</button></div>
        <div className="table-wrap" aria-busy={loading}><table><thead><tr><th>TÊN ĐỢT</th><th>PHẠM VI</th><th>NGƯỜI PHỤ TRÁCH</th><th>TRẠNG THÁI</th><th>TIẾN ĐỘ</th><th>BẮT ĐẦU</th><th>KẾT THÚC</th><th>CHI TIẾT</th></tr></thead><tbody>
          {loading ? <tr><td className="empty loading-state" colSpan="8">Đang tải danh sách...</td></tr>
            : rows.length === 0 ? <tr><td className="empty" colSpan="8">Chưa có đợt kiểm kê.</td></tr>
              : rows.map((row) => <tr key={row.id}><td>{row.tenDot}</td><td>{row.loaiPhamVi === "LOCATION" ? `Vị trí: ${row.phamVi}` : row.loaiPhamVi === "DEPARTMENT" ? `Phòng ban: ${row.phamVi || row.phongBan}` : "Toàn công ty"}</td><td>{row.nguoiPhuTrach || "—"}</td><td>{statusLabels[row.trangThai] ?? row.trangThai}</td><td>{row.soDaKiem}/{row.soLuongItems}</td><td>{row.batDauLuc ? new Date(`${row.batDauLuc}T00:00:00`).toLocaleDateString("vi-VN") : "—"}</td><td>{row.ketThucLuc ? new Date(`${row.ketThucLuc}T00:00:00`).toLocaleDateString("vi-VN") : "—"}</td><td><button className="link-button" type="button" onClick={() => setSelectedStocktake(row)}>Mở</button></td></tr>)}
        </tbody></table></div>
      </section>
      {selectedStocktake && <section className="panel assets-panel admin-resource-panel">
        <div className="panel-heading assets-heading">
          <div><h2>{selectedStocktake.tenDot} · {statusLabels[selectedStocktake.trangThai] ?? selectedStocktake.trangThai}</h2><p className="muted">{selectedStocktake.loaiPhamVi === "LOCATION" ? `Vị trí: ${selectedStocktake.phamVi}` : selectedStocktake.loaiPhamVi === "DEPARTMENT" ? `Phòng ban: ${selectedStocktake.phamVi || selectedStocktake.phongBan}` : "Toàn công ty"} · Phụ trách: {selectedStocktake.nguoiPhuTrach || "—"} · Người kiểm: {selectedStocktake.nguoiKiem || "—"} · Xác nhận: {selectedStocktake.nguoiXacNhan || "—"}</p></div>
          <div className="admin-resource-actions">
            {selectedStocktake.trangThai === "DRAFT" && <button className="button" type="button" onClick={() => transitionStocktake("start", "Bắt đầu kiểm kê theo snapshot tài sản hiện tại?")} disabled={saving}>Bắt đầu kiểm</button>}
            {selectedStocktake.trangThai === "OPEN" && <><button className="button" type="button" onClick={() => transitionStocktake("complete", "Hoàn thành và khóa đợt kiểm kê?")} disabled={saving || itemsLoading || uncheckedCount > 0} title={uncheckedCount > 0 ? `Còn ${uncheckedCount} tài sản chưa kiểm hoặc chưa lưu` : undefined}>Hoàn thành</button><button className="button secondary" type="button" onClick={() => transitionStocktake("cancel", "Hủy đợt kiểm kê này?")} disabled={saving}>Hủy đợt</button></>}
            {selectedStocktake.trangThai === "DRAFT" && <button className="button secondary" type="button" onClick={() => transitionStocktake("cancel", "Hủy bản nháp kiểm kê này?")} disabled={saving}>Hủy bản nháp</button>}
            <button className="button secondary" type="button" onClick={exportVariance} disabled={itemsLoading}>Xuất Excel (.csv)</button>
            {selectedStocktake.trangThai === "COMPLETED" && <button className="button secondary" type="button" onClick={downloadReport}>Tải biên bản PDF</button>}
            <button className="icon-button" type="button" aria-label="Đóng chi tiết kiểm kê" onClick={() => setSelectedStocktake(null)}>×</button>
          </div>
        </div>
        {selectedStocktake.trangThai === "OPEN" && <section className="admin-stocktake-progress" aria-label="Tiến độ kiểm kê">
          <div><strong>Đã kiểm {checkedCount}/{items.length}</strong><span>{progress}%</span></div>
          <progress value={checkedCount} max={Math.max(items.length, 1)} />
          {uncheckedCount > 0 && <p className="muted" role="status">Còn {uncheckedCount} tài sản chưa kiểm hoặc chưa lưu. Hãy chọn hiện trạng và bấm “Lưu” ở từng dòng trước khi hoàn thành đợt.</p>}
        </section>}
        {itemsLoading ? <p className="empty loading-state">Đang tải tài sản trong đợt kiểm kê...</p>
          : <div className="table-wrap"><table><thead><tr><th>MÃ / TÊN TÀI SẢN</th><th>VỊ TRÍ THEO SỔ</th><th>NGƯỜI GIỮ THEO SỔ</th><th>KIỂM KÊ THỰC TẾ</th></tr></thead><tbody>
            {items.length === 0 ? <tr><td className="empty" colSpan="4">{selectedStocktake.trangThai === "DRAFT" ? "Bắt đầu đợt để chụp danh sách tài sản theo sổ." : "Đợt kiểm kê không có tài sản trong phạm vi."}</td></tr>
              : items.map((item) => <tr key={`${item.id}-${item.daKiemLuc ?? "unchecked"}`}><td><strong>{item.maTaiSan}</strong><br />{item.tenTaiSan}</td><td>{item.viTriTheoSo || "—"}</td><td>{item.nguoiGiuTheoSo || "—"}</td>{selectedStocktake.trangThai !== "OPEN" ? <td>{item.daKiemLuc ? ({"CO_MAT":"Có mặt","THIEU":"Thiếu","HONG":"Hỏng","SAI_VI_TRI":"Sai vị trí"}[item.tinhTrangThucTe] ?? item.tinhTrangThucTe) : "Chưa kiểm"}{item.viTriThucTe && <><br /><span className="muted">Thực tế: {item.viTriThucTe}</span></>}{item.ghiChu && <><br /><span className="muted">{item.ghiChu}</span></>}</td> : <td>
                <form className="admin-stocktake-item-form" onSubmit={(event) => saveStocktakeItem(event, item)}>
                  <select name="tinhTrangThucTe" defaultValue={item.daKiemLuc ? item.tinhTrangThucTe : ""} aria-label={`Hiện trạng ${item.maTaiSan}`} required><option value="" disabled>Chọn trạng thái</option><option value="CO_MAT">Có mặt</option><option value="THIEU">Thiếu</option><option value="HONG">Hỏng</option><option value="SAI_VI_TRI">Sai vị trí</option></select>
                  <input name="viTriThucTe" defaultValue={item.viTriThucTe ?? ""} placeholder="Vị trí thực tế (bắt buộc nếu sai vị trí)" aria-label={`Vị trí thực tế ${item.maTaiSan}`} maxLength="250" />
                  <input name="ghiChu" defaultValue={item.ghiChu ?? ""} placeholder="Ghi chú..." aria-label={`Ghi chú ${item.maTaiSan}`} maxLength="500" />
                  <button className="link-button" type="submit" disabled={itemSavingId === item.id}>{itemSavingId === item.id ? "Đang lưu..." : "Lưu"}</button>
                  {item.tinhTrangThucTe === "HONG" && <button className="link-button" type="button" onClick={() => createMaintenance(item)} disabled={Boolean(item.maintenanceRequestId)}>{item.maintenanceRequestId ? `Đã tạo phiếu #${item.maintenanceRequestId}` : "Tạo phiếu bảo trì"}</button>}
                </form>
              </td>}</tr>)}
          </tbody></table></div>}
        {selectedStocktake.trangThai === "OPEN" && <form className="admin-stocktake-extra-form" onSubmit={addStocktakeExtra}>
          <h3>Ghi nhận tài sản thừa</h3>
          <input name="maTaiSan" placeholder="Mã tài sản / mã tạm" required maxLength="30" aria-label="Mã tài sản thừa" />
          <input name="tenTaiSan" placeholder="Tên tài sản" required maxLength="150" aria-label="Tên tài sản thừa" />
          <input name="viTriThucTe" placeholder="Vị trí thực tế" maxLength="250" aria-label="Vị trí tài sản thừa" />
          <input name="ghiChu" placeholder="Ghi chú" maxLength="500" aria-label="Ghi chú tài sản thừa" />
          <button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu..." : "Thêm tài sản thừa"}</button>
        </form>}
        {selectedStocktake.trangThai === "OPEN" && extras.length > 0 && <p className="muted admin-stocktake-extra-count">Đã ghi nhận {extras.length} tài sản thừa.</p>}
        {selectedStocktake.trangThai !== "DRAFT" && <section className="admin-stocktake-variances" aria-label="Tổng hợp chênh lệch">
          {varianceGroups.map((group) => <article className="panel admin-stocktake-variance" key={group.status}>
            <h3>{group.label} <span>{group.items.length}</span></h3>
            {group.items.length === 0 ? <p className="muted">Không có chênh lệch.</p> : <ul>
              {group.items.map((item) => <li key={item.id}><strong>{item.maTaiSan} · {item.tenTaiSan}</strong>
                <span>{item.viTriTheoSo || item.viTriThucTe || "Chưa có vị trí"}{item.nguoiGiuTheoSo ? ` · ${item.nguoiGiuTheoSo}` : ""}</span>
                {item.ghiChu && <span>{item.ghiChu}</span>}
              </li>)}
            </ul>}
          </article>)}
        </section>}
      </section>}
    </>
  );
}

function ReportsPage({ currentUser }) {
  const [report, setReport] = useState(null);
  const [groupBy, setGroupBy] = useState("phongBan");
  const [repairGroupBy, setRepairGroupBy] = useState("taiSan");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  function reportTitle(kind, repairGrouping) {
    if (kind === "suaChua") {
      const groupingLabel = repairGrouping === "taiSan" ? "tài sản"
        : repairGrouping === "phongBan" ? "phòng ban" : "loại tài sản";
      return `Báo cáo chi phí sửa chữa theo ${groupingLabel}`;
    }
    if (kind === "giaTriConLai") return "Báo cáo giá trị tài sản theo phòng ban";
    const groupingLabel = kind === "phongBan" ? "phòng ban"
      : kind === "loaiTaiSan" ? "loại tài sản" : "trạng thái";
    return `Báo cáo tài sản theo ${groupingLabel}`;
  }

  function reportGroupLabel(asset, kind, repairGrouping) {
    if (kind === "suaChua") {
      if (repairGrouping === "taiSan") return `${asset.maTaiSan} · ${asset.tenTaiSan}`;
      if (repairGrouping === "phongBan") return asset.phongBanDangGiu || "Chưa phân công";
      return asset.tenLoaiTaiSan || "Chưa phân loại";
    }
    if (kind === "phongBan" || kind === "giaTriConLai") return asset.phongBanDangGiu || "Chưa phân công";
    if (kind === "loaiTaiSan") return asset.tenLoaiTaiSan || "Chưa phân loại";
    return asset.tinhTrang || "Chưa xác định";
  }

  function dateInRange(value, from, to) {
    if (!value) return false;
    const date = String(value).slice(0, 10);
    return (!from || date >= from) && (!to || date <= to);
  }

  async function loadReport(event) {
    event?.preventDefault();
    if (dateFrom && dateTo && dateFrom > dateTo) {
      setError("Ngày bắt đầu không được sau ngày kết thúc.");
      return;
    }
    setLoading(true);
    setError("");
    setReport(null);
    try {
      const [assets, repairTickets] = await Promise.all([
        loadAllAssetRows(),
        adminRequest(`${API_URL}/bao-tri`),
      ]);
      const assetById = new Map(assets.map((asset) => [asset.taiSanId, asset]));
      const effectiveGroup = groupBy === "giaTriConLai" ? "phongBan" : groupBy;
      const grouped = new Map();
      assets.forEach((asset) => {
        const label = reportGroupLabel(asset, groupBy, repairGroupBy);
        const row = grouped.get(label) ?? {
          nhan: label,
          assetIds: new Set(),
          soLuong: 0,
          giaTriBanDau: 0,
          giaTriConLai: 0,
          chiPhi: 0,
          soLanSuaChua: 0,
        };
        row.assetIds.add(asset.taiSanId);
        row.soLuong += 1;
        row.giaTriBanDau += Number(asset.giaMua ?? 0);
        row.giaTriConLai += Number(asset.giaTriConLai ?? 0);
        grouped.set(label, row);
      });

      repairTickets
        .filter((item) => (item.loaiYeuCau === "SUA_CHUA" || (!item.loaiYeuCau && !item.lichBaoTriId))
          && ["HOAN_TAT", "KHONG_SUA_DUOC"].includes(item.trangThai))
        .filter((item) => {
          if (!dateFrom && !dateTo) return true;
          const completedDate = item.ngayHoanThanh || item.hoanTatLuc;
          return dateInRange(completedDate, dateFrom, dateTo);
        })
        .forEach((item) => {
          const asset = assetById.get(item.taiSanId);
          const repairAsset = asset ?? {
            taiSanId: item.taiSanId,
            maTaiSan: item.maTaiSan,
            tenTaiSan: item.tenTaiSan,
            phongBanDangGiu: item.phongBan,
            tenLoaiTaiSan: item.tenLoaiTaiSan,
            tinhTrang: "Chưa xác định",
          };
          const label = reportGroupLabel(repairAsset, groupBy, repairGroupBy);
          const row = grouped.get(label) ?? {
            nhan: label,
            assetIds: new Set(),
            soLuong: 0,
            giaTriBanDau: 0,
            giaTriConLai: 0,
            chiPhi: 0,
            soLanSuaChua: 0,
          };
          if (item.taiSanId != null) row.assetIds.add(item.taiSanId);
          row.soLanSuaChua += 1;
          row.chiPhi += Number(item.chiPhi ?? 0);
          grouped.set(label, row);
        });

      const rows = [...grouped.values()]
        .filter((row) => groupBy !== "suaChua" || row.soLanSuaChua > 0)
        .map(({ assetIds, ...row }) => ({ ...row, soLuong: assetIds.size }))
        .sort((first, second) => (groupBy === "suaChua" ? second.chiPhi - first.chiPhi
          : second.giaTriBanDau - first.giaTriBanDau)
          || first.nhan.localeCompare(second.nhan, "vi"));
      setReport({
        rows,
        groupBy,
        repairGroupBy,
        dateFrom,
        dateTo,
        title: reportTitle(groupBy, repairGroupBy),
        generatedAt: new Date(),
        generatedBy: currentUser?.username || "Quản trị viên",
        chart: effectiveGroup === "phongBan",
      });
    } catch (loadError) {
      setReport(null);
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }

  function exportCsv() {
    if (!report?.rows.length) return;
    const rows = [
      [report.title],
      [`Ngày lập: ${report.generatedAt.toLocaleString("vi-VN")}`],
      [`Người lập: ${report.generatedBy}`],
      [`Kỳ chi phí sửa chữa: ${report.dateFrom || "Không giới hạn"} - ${report.dateTo || "Không giới hạn"}`],
      [],
      ["Nhóm", "Số tài sản", "Giá trị ban đầu (VNĐ)", "Giá trị còn lại (VNĐ)",
        "Chi phí sửa chữa (VNĐ)", ...(report.groupBy === "suaChua" ? ["Số phiếu hoàn thành"] : [])],
      ...report.rows.map((row) => [row.nhan, row.soLuong, row.giaTriBanDau, row.giaTriConLai,
        row.chiPhi, ...(report.groupBy === "suaChua" ? [row.soLanSuaChua] : [])]),
      ["Tổng cộng",
        report.rows.reduce((total, row) => total + row.soLuong, 0),
        report.rows.reduce((total, row) => total + row.giaTriBanDau, 0),
        report.rows.reduce((total, row) => total + row.giaTriConLai, 0),
        report.rows.reduce((total, row) => total + row.chiPhi, 0),
        ...(report.groupBy === "suaChua"
          ? [report.rows.reduce((total, row) => total + row.soLanSuaChua, 0)] : [])],
    ];
    const csv = rows.map((row) => row.map((cell) => {
      let text = String(cell ?? "");
      if (/^[\s]*[=+\-@]/.test(text)) text = `'${text}`;
      return `"${text.replaceAll('"', '""')}"`;
    }).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `bao-cao-${report.groupBy}-${localDateValue(report.generatedAt)}.csv`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const reportRows = report?.rows ?? [];
  const reportTotal = reportRows.reduce((total, row) => ({
    soLuong: total.soLuong + row.soLuong,
    giaTriBanDau: total.giaTriBanDau + row.giaTriBanDau,
    giaTriConLai: total.giaTriConLai + row.giaTriConLai,
    chiPhi: total.chiPhi + row.chiPhi,
    soLanSuaChua: total.soLanSuaChua + row.soLanSuaChua,
  }), { soLuong: 0, giaTriBanDau: 0, giaTriConLai: 0, chiPhi: 0, soLanSuaChua: 0 });
  const chartMaximum = Math.max(1, ...reportRows.map((row) => row.giaTriBanDau));

  return (
    <>
      <section className="welcome"><div><p className="eyebrow">PHÂN TÍCH DỮ LIỆU</p><h1>Báo cáo tài sản</h1><p className="welcome-copy">Tổng hợp tài sản, giá trị còn lại và chi phí sửa chữa theo tài sản, phòng ban hoặc loại.</p></div></section>
      {error && <p className="message error" role="alert">{error}</p>}
      <section className="panel admin-resource-form">
        <form className="admin-report-controls" onSubmit={loadReport}>
          <div className="field"><label htmlFor="report-group">Nhóm báo cáo</label><select id="report-group" value={groupBy} onChange={(event) => setGroupBy(event.target.value)}><option value="phongBan">Theo phòng ban</option><option value="loaiTaiSan">Theo loại tài sản</option><option value="trangThai">Theo trạng thái</option><option value="giaTriConLai">Giá trị còn lại theo phòng ban</option><option value="suaChua">Chi phí sửa chữa</option></select></div>
          {groupBy === "suaChua" && <div className="field"><label htmlFor="report-repair-group">Phân nhóm sửa chữa</label><select id="report-repair-group" value={repairGroupBy} onChange={(event) => setRepairGroupBy(event.target.value)}><option value="taiSan">Theo tài sản</option><option value="phongBan">Theo phòng ban</option><option value="loaiTaiSan">Theo loại tài sản</option></select></div>}
          <div className="field"><label htmlFor="report-date-from">Từ ngày (chi phí sửa chữa)</label><input id="report-date-from" type="date" value={dateFrom} max={dateTo || undefined} onChange={(event) => setDateFrom(event.target.value)} /></div>
          <div className="field"><label htmlFor="report-date-to">Đến ngày (chi phí sửa chữa)</label><input id="report-date-to" type="date" value={dateTo} min={dateFrom || undefined} onChange={(event) => setDateTo(event.target.value)} /></div>
          <div className="admin-report-actions">
            <button className="button" type="submit" disabled={loading}>{loading ? "Đang tải..." : "Xem báo cáo"}</button>
            <button className="button secondary" type="button" onClick={exportCsv} disabled={!reportRows.length}>Xuất CSV / Excel</button>
            <button className="button secondary" type="button" onClick={() => window.print()} disabled={!report}>In / Lưu PDF</button>
          </div>
        </form>
        <p className="muted admin-report-filter-note">Khoảng ngày áp dụng cho chi phí sửa chữa theo ngày hoàn thành; giá trị tài sản là số liệu hiện tại.</p>
        {!reportRows.length && !loading && <p className="muted admin-report-export-hint">{report ? "Không có dữ liệu để xuất." : "Hãy chọn điều kiện và bấm “Xem báo cáo” để tạo kết quả."}</p>}
      </section>
      {loading && <section className="panel admin-report-loading" role="status">Đang tải dữ liệu báo cáo...</section>}
      {report && <section id="report-print-area" className="panel assets-panel admin-resource-panel admin-report-results">
        <header className="admin-report-print-header">
          <p className="eyebrow">BÁO CÁO QUẢN LÝ TÀI SẢN VĂN PHÒNG</p>
          <h2>{report.title}</h2>
          <p>Ngày lập: {report.generatedAt.toLocaleString("vi-VN")} · Người lập: {report.generatedBy}</p>
          {(report.dateFrom || report.dateTo) && <p>Kỳ chi phí sửa chữa: {report.dateFrom || "Đầu kỳ"} – {report.dateTo || "Hiện tại"}</p>}
        </header>
        {report.chart && reportRows.length > 0 && <section className="admin-report-chart" aria-label="Biểu đồ giá trị tài sản ban đầu theo phòng ban">
          <h3>Giá trị ban đầu theo phòng ban</h3>
          {reportRows.map((row) => <div className="admin-report-chart-row" key={row.nhan}>
            <span title={row.nhan}>{row.nhan}</span>
            <div className="admin-report-chart-track"><div style={{ width: `${Math.max(1, row.giaTriBanDau / chartMaximum * 100)}%` }} /></div>
            <strong>{currencyFormatter.format(row.giaTriBanDau)} ₫</strong>
          </div>)}
        </section>}
        <div className="table-wrap"><table className="admin-report-table"><thead><tr>
          <th>{report.groupBy === "phongBan" || report.groupBy === "giaTriConLai" ? "PHÒNG BAN" : report.groupBy === "loaiTaiSan" ? "LOẠI TÀI SẢN" : report.groupBy === "trangThai" ? "TRẠNG THÁI" : "NHÓM"}</th>
          <th>SỐ TÀI SẢN</th><th>GIÁ TRỊ BAN ĐẦU</th><th>GIÁ TRỊ CÒN LẠI</th><th>CHI PHÍ SỬA CHỮA</th>
          {report.groupBy === "suaChua" && <th>SỐ PHIẾU HOÀN THÀNH</th>}
        </tr></thead><tbody>
          {reportRows.map((row) => <tr key={row.nhan}>
            <td>{row.nhan}</td><td>{numberFormatter.format(row.soLuong)}</td>
            <td>{currencyFormatter.format(row.giaTriBanDau)} ₫</td>
            <td>{currencyFormatter.format(row.giaTriConLai)} ₫</td>
            <td>{currencyFormatter.format(row.chiPhi)} ₫</td>
            {report.groupBy === "suaChua" && <td>{numberFormatter.format(row.soLanSuaChua)}</td>}
          </tr>)}
          {reportRows.length > 0 && <tr className="admin-report-total">
            <th>TỔNG CỘNG</th><th>{numberFormatter.format(reportTotal.soLuong)}</th>
            <th>{currencyFormatter.format(reportTotal.giaTriBanDau)} ₫</th>
            <th>{currencyFormatter.format(reportTotal.giaTriConLai)} ₫</th>
            <th>{currencyFormatter.format(reportTotal.chiPhi)} ₫</th>
            {report.groupBy === "suaChua" && <th>{numberFormatter.format(reportTotal.soLanSuaChua)}</th>}
          </tr>}
          {reportRows.length === 0 && <tr><td className="empty" colSpan={report.groupBy === "suaChua" ? 6 : 5}>Không có dữ liệu trong kỳ báo cáo đã chọn.</td></tr>}
        </tbody></table></div>
      </section>}
    </>
  );
}

function escapePrintableText(value) {
  return String(value ?? "—").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;",
  })[character]);
}

function localDateValue(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function AssignmentManagementPage({ currentUser }) {
  const [assignments, setAssignments] = useState([]);
  const [stockAssets, setStockAssets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState(null);
  const [tab, setTab] = useState(window.location.hash === "#returns" ? "returns" : "assignments");
  const [returnTarget, setReturnTarget] = useState(null);
  const [returnDueFilter, setReturnDueFilter] = useState("");
  const [repairSuggestion, setRepairSuggestion] = useState(null);
  const [returnPdfId, setReturnPdfId] = useState(null);
  const [employeeReturnFilter, setEmployeeReturnFilter] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    return {
      name: params.get("employeeName") ?? "",
      code: params.get("employeeCode") ?? "",
    };
  });
  const [recipientFilter, setRecipientFilter] = useState(() => employeeReturnFilter.name || employeeReturnFilter.code);
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const [assignmentRows, activeRows, assetPage] = await Promise.all([
        adminRequest(`${API_URL}/cap-phat?activeOnly=false`),
        adminRequest(`${API_URL}/cap-phat?activeOnly=true`),
        adminRequest(`${API_URL}/tai-san?page=0&size=100`),
      ]);
      const rowsById = new Map((Array.isArray(assignmentRows) ? assignmentRows : []).map((item) => [item.id, item]));
      (Array.isArray(activeRows) ? activeRows : []).forEach((item) => rowsById.set(item.id, item));
      setAssignments([...rowsById.values()].sort((first, second) => second.id - first.id));
      const assets = Array.isArray(assetPage) ? assetPage : assetPage?.content ?? [];
      setStockAssets(assets.filter((asset) => asset.tinhTrang === "Trong kho"));
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadData(); }, []);

  function selectTab(nextTab, clearReturnTarget = true) {
    setTab(nextTab);
    if (clearReturnTarget) setReturnTarget(null);
    window.history.replaceState(null, "", nextTab === "returns" ? "#returns" : "#assignments");
  }

  function clearAssignmentFilters() {
    setRecipientFilter("");
    setEmployeeReturnFilter({ name: "", code: "" });
    setDepartmentFilter("");
    setReturnDueFilter("");
    setDateFrom("");
    setDateTo("");
    const url = new URL(window.location.href);
    url.searchParams.delete("employeeName");
    url.searchParams.delete("employeeCode");
    window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
  }

  function filteredRows(rows) {
    const recipient = recipientFilter.trim().toLocaleLowerCase("vi");
    const department = departmentFilter.trim().toLocaleLowerCase("vi");
    return rows.filter((item) => {
      const day = item.banGiaoLuc ? new Date(item.banGiaoLuc) : null;
      const localDay = day && `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
      const currentRecipient = (item.nguoiNhan ?? "").toLocaleLowerCase("vi");
      const employeeMatch = employeeReturnFilter.name || employeeReturnFilter.code
        ? [employeeReturnFilter.name, employeeReturnFilter.code]
          .some((value) => value && currentRecipient === value.trim().toLocaleLowerCase("vi"))
        : false;
      return (!recipient || currentRecipient.includes(recipient) || employeeMatch)
        && (!department || (item.phongBan ?? "").toLocaleLowerCase("vi").includes(department))
        && (!dateFrom || (localDay && localDay >= dateFrom))
        && (!dateTo || (localDay && localDay <= dateTo));
    });
  }

  function openHandoverRecord(item) {
    const printWindow = window.open("", "_blank");
    if (!printWindow) {
      setError("Trình duyệt đã chặn cửa sổ biên bản. Hãy cho phép cửa sổ bật lên để in hoặc lưu PDF.");
      return;
    }
    printWindow.opener = null;
    const date = item.banGiaoLuc ? new Date(item.banGiaoLuc).toLocaleDateString("vi-VN") : "—";
    const fields = [
      ["Mã tài sản", item.maTaiSan],
      ["Tên tài sản", item.tenTaiSan],
      ["Serial", item.serialNumber],
      ["Loại tài sản", item.tenLoaiTaiSan],
      ["Vị trí", [item.toaNha, item.tang && `Tầng ${item.tang}`, item.phong && `Phòng ${item.phong}`].filter(Boolean).join(" · ")],
      ["Người nhận / phòng ban", [...new Set([item.nguoiNhan, item.phongBan].filter(Boolean))].join(" · ")],
      ["Ngày bàn giao", date],
      ["Tình trạng lúc bàn giao", item.tinhTrangLucBanGiao],
      ["Ghi chú", item.ghiChu],
    ];
    printWindow.document.open();
    printWindow.document.write(`<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Biên bản bàn giao ${escapePrintableText(item.maTaiSan)}</title><style>
      body{font:15px Arial,sans-serif;color:#172033;max-width:760px;margin:48px auto;padding:0 28px}
      h1{text-align:center;font-size:22px;margin-bottom:8px}p{text-align:center;color:#536078}
      table{border-collapse:collapse;width:100%;margin:32px 0}td{border:1px solid #cbd2df;padding:12px}
      td:first-child{width:34%;font-weight:700;background:#f5f7fa}.signatures{display:grid;grid-template-columns:1fr 1fr;gap:40px;margin-top:64px;text-align:center}
      .signature{height:100px;border-bottom:1px solid #8791a3;margin-bottom:10px}
      @media print{body{margin:20mm auto}}
    </style></head><body><h1>BIÊN BẢN BÀN GIAO TÀI SẢN</h1><p>Phiếu bàn giao #${escapePrintableText(item.id)}</p>
      <table>${fields.map(([label, value]) => `<tr><td>${escapePrintableText(label)}</td><td>${escapePrintableText(value)}</td></tr>`).join("")}</table>
      <div class="signatures"><div><div class="signature"></div><strong>${escapePrintableText(item.nguoiBanGiao)}</strong><br>Người bàn giao</div>
      <div><div class="signature"></div><strong>${escapePrintableText(item.nguoiKyNhan)}</strong><br>Người ký nhận</div></div></body></html>`);
    printWindow.document.close();
    window.setTimeout(() => printWindow.print(), 300);
  }

  async function submitHandover(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const recipient = String(data.get("nguoiNhan") ?? "").trim();
    const department = String(data.get("phongBan") ?? "").trim();
    if (!recipient && !department) {
      setError("Vui lòng nhập người nhận hoặc phòng ban.");
      return;
    }
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const result = await adminRequest(`${API_URL}/cap-phat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          taiSanId: Number(data.get("taiSanId")),
          nguoiNhan: recipient || null,
          usernameNguoiNhan: String(data.get("usernameNguoiNhan") ?? "").trim() || null,
          phongBan: department || null,
          ngayBanGiao: data.get("ngayBanGiao"),
          ngayDuKienThuHoi: data.get("ngayDuKienThuHoi") || null,
          nguoiBanGiao: String(data.get("nguoiBanGiao")).trim(),
          nguoiKyNhan: String(data.get("nguoiKyNhan")).trim(),
          tinhTrangLucBanGiao: String(data.get("tinhTrangLucBanGiao")).trim(),
          ghiChu: String(data.get("ghiChu") ?? "").trim(),
        }),
      });
      form.reset();
      await loadData();
      setSuccess(`Đã tạo phiếu bàn giao tài sản ${result.maTaiSan}.`);
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function submitReturn(event) {
    event.preventDefault();
    if (!returnTarget) return;
    const data = new FormData(event.currentTarget);
    setActionId(returnTarget.id);
    setError("");
    setSuccess("");
    try {
      const result = await adminRequest(`${API_URL}/cap-phat/${returnTarget.id}/thu-hoi`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ngayThuHoi: data.get("ngayThuHoi"),
          lyDoThuHoi: String(data.get("lyDoThuHoi")),
          tinhTrangLucThuHoi: String(data.get("tinhTrangLucThuHoi")).trim(),
          tinhTrangSauThuHoi: String(data.get("tinhTrangSauThuHoi")),
          ghiChu: String(data.get("ghiChu") ?? "").trim(),
          nguoiNhanLai: String(data.get("nguoiNhanLai")).trim(),
          nguoiGiaoLai: String(data.get("nguoiGiaoLai")).trim(),
        }),
      });
      setReturnTarget(null);
      setSuccess(`Đã thu hồi tài sản ${returnTarget.maTaiSan}.`);
      setRepairSuggestion(result.tinhTrangSauThuHoi === "Bảo trì" ? result : null);
      await loadData();
    } catch (saveError) {
      setError(saveError.message);
    } finally {
      setActionId(null);
    }
  }

  async function downloadReturnRecord(item) {
    setReturnPdfId(item.id);
    setError("");
    try {
      await adminDownload(`${API_URL}/cap-phat/${item.id}/bien-ban-thu-hoi.pdf`,
        `bien-ban-thu-hoi-${item.id}.pdf`);
    } catch (downloadError) {
      setError(downloadError.message);
    } finally {
      setReturnPdfId(null);
    }
  }

  const activeAssignments = assignments.filter((item) => !item.thuHoiLuc);
  const activeFiltered = filteredRows(activeAssignments);
  const returnsFiltered = activeFiltered.filter((item) => {
    const expectedDate = item.ngayDuKienThuHoi;
    const today = localDateValue();
    const overdue = expectedDate && expectedDate < today;
    const upcomingLimit = new Date();
    upcomingLimit.setDate(upcomingLimit.getDate() + 30);
    const upcomingDate = localDateValue(upcomingLimit);
    const dueSoon = expectedDate && expectedDate >= today && expectedDate <= upcomingDate;
    return returnDueFilter === "overdue" ? overdue
      : returnDueFilter === "upcoming" ? dueSoon : true;
  });
  const historyFiltered = filteredRows(assignments);

  return <>
    <section className="welcome">
      <div><p className="eyebrow">QUẢN LÝ TÀI SẢN</p><h1>Bàn giao & Thu hồi</h1>
        <p className="welcome-copy">Lập phiếu bàn giao, lưu biên bản và theo dõi lịch sử người giữ tài sản.</p></div>
      <span className="asset-count">{activeAssignments.length} đang sử dụng</span>
    </section>
    {error && <p className="message error" role="alert">{error} <button className="retry-button" type="button" onClick={loadData}>Thử lại</button></p>}
    {success && <p className="message" role="status">{success}</p>}
    {repairSuggestion && <p className="message" role="status">
      Tài sản đã chuyển sang trạng thái sửa chữa. <a className="link-button" href={`/?repairAssetId=${repairSuggestion.taiSanId}&repairSource=RETURN#repairs`}>Tạo phiếu sửa chữa ngay</a>
    </p>}
    <section className="panel assets-panel workflow-panel">
      <div className="panel-heading assets-heading">
        <div><h2>{tab === "assignments" ? "Danh sách bàn giao" : "Thu hồi tài sản"}</h2>
          <p className="muted">Lịch sử được lưu để tra cứu; không thể xóa phiếu bàn giao.</p></div>
        <div className="admin-resource-actions">
          <button className={`button ${tab === "assignments" ? "" : "secondary"}`} type="button" onClick={() => selectTab("assignments")}>Bàn giao tài sản</button>
          <button className={`button ${tab === "returns" ? "" : "secondary"}`} type="button" onClick={() => selectTab("returns")}>Thu hồi</button>
        </div>
      </div>
      {tab === "assignments" ? <>
        <form className="workflow-form" onSubmit={submitHandover}>
          <label className="field">Tài sản trong kho<select name="taiSanId" required disabled={loading || stockAssets.length === 0}>
            <option value="">{stockAssets.length ? "Chọn tài sản trong kho" : "Không có tài sản trong kho"}</option>
            {stockAssets.map((asset) => <option key={asset.taiSanId} value={asset.taiSanId}>{asset.maTaiSan} · {asset.tenTaiSan}</option>)}
          </select></label>
          <label className="field">Người nhận<input name="nguoiNhan" maxLength="100" placeholder="Tên người nhận (nếu bàn giao cho cá nhân)" /></label>
          <label className="field">Phòng ban<input name="phongBan" maxLength="100" placeholder="Phòng ban (nếu bàn giao cho đơn vị)" /></label>
          <label className="field">Tài khoản OfficeCare (nếu có)<input name="usernameNguoiNhan" maxLength="50" placeholder="Tên đăng nhập" /></label>
          <label className="field">Ngày bàn giao<input name="ngayBanGiao" type="date" required defaultValue={localDateValue()} max={localDateValue()} /></label>
          <label className="field">Ngày dự kiến thu hồi<input name="ngayDuKienThuHoi" type="date" min={localDateValue()} /></label>
          <label className="field">Người bàn giao<input name="nguoiBanGiao" required maxLength="100" defaultValue={currentUser.username} /></label>
          <label className="field">Người ký nhận<input name="nguoiKyNhan" required maxLength="100" placeholder="Họ tên người ký nhận" /></label>
          <label className="field">Tình trạng lúc bàn giao<input name="tinhTrangLucBanGiao" required maxLength="300" placeholder="VD: Hoạt động tốt, kèm sạc" /></label>
          <label className="field">Ghi chú<input name="ghiChu" maxLength="500" placeholder="Ví dụ: trầy xước nhẹ, kèm phụ kiện" /></label>
          <button className="button" type="submit" disabled={saving || loading || stockAssets.length === 0}>{saving ? "Đang lưu..." : "＋ Tạo phiếu bàn giao"}</button>
        </form>
        <div className="admin-asset-filters">
          <label>Người nhận<input value={recipientFilter} onChange={(event) => { setRecipientFilter(event.target.value); setEmployeeReturnFilter({ name: "", code: "" }); }} placeholder="Lọc người nhận" /></label>
          <label>Phòng ban<input value={departmentFilter} onChange={(event) => setDepartmentFilter(event.target.value)} placeholder="Lọc phòng ban" /></label>
          <label>Từ ngày<input type="date" value={dateFrom} max={dateTo || undefined} onChange={(event) => setDateFrom(event.target.value)} /></label>
          <label>Đến ngày<input type="date" value={dateTo} min={dateFrom || undefined} onChange={(event) => setDateTo(event.target.value)} /></label>
          <button className="text-button" type="button" onClick={clearAssignmentFilters}>Xóa bộ lọc</button>
        </div>
        <h3>Tài sản đang được bàn giao</h3>
        <AssignmentTable rows={activeFiltered} loading={loading} onPrint={openHandoverRecord}
          onReturn={(item) => { setReturnTarget(item); selectTab("returns", false); }} active />
        <h3>Lịch sử điều chuyển</h3>
        <AssignmentTable rows={historyFiltered} loading={loading} onPrint={openHandoverRecord}
          onDownloadReturnRecord={downloadReturnRecord} returnPdfId={returnPdfId} />
      </> : <>
        <div className="admin-asset-filters admin-return-filters">
          <label>Người giữ<input value={recipientFilter} onChange={(event) => { setRecipientFilter(event.target.value); setEmployeeReturnFilter({ name: "", code: "" }); }} placeholder="Lọc người giữ" /></label>
          <label>Phòng ban<input value={departmentFilter} onChange={(event) => setDepartmentFilter(event.target.value)} placeholder="Lọc phòng ban" /></label>
          <label>Thời hạn trả<select value={returnDueFilter} onChange={(event) => setReturnDueFilter(event.target.value)}>
            <option value="">Tất cả</option><option value="upcoming">Sắp đến hạn (30 ngày)</option><option value="overdue">Quá hạn</option>
          </select></label>
          <label>Từ ngày bàn giao<input type="date" value={dateFrom} max={dateTo || undefined} onChange={(event) => setDateFrom(event.target.value)} /></label>
          <label>Đến ngày bàn giao<input type="date" value={dateTo} min={dateFrom || undefined} onChange={(event) => setDateTo(event.target.value)} /></label>
          <button className="text-button" type="button" onClick={clearAssignmentFilters}>Xóa bộ lọc</button>
        </div>
        {returnTarget && <form key={returnTarget.id} className="workflow-form assignment-return-form" onSubmit={submitReturn}>
          <div className="assignment-return-heading"><strong>Thu hồi: {returnTarget.maTaiSan} · {returnTarget.tenTaiSan}</strong><span>Người giữ: {returnTarget.nguoiNhan}</span></div>
          <label className="field">Ngày thu hồi<input name="ngayThuHoi" type="date" required defaultValue={localDateValue()} max={localDateValue()} /></label>
          <label className="field">Lý do thu hồi<select name="lyDoThuHoi" required defaultValue="NGHI_VIEC">
            <option value="NGHI_VIEC">Nghỉ việc</option><option value="CHUYEN_PHONG">Chuyển phòng</option>
            <option value="HET_NHU_CAU">Hết nhu cầu</option><option value="KHAC">Khác</option>
          </select></label>
          <label className="field">Tình trạng khi nhận lại<input name="tinhTrangLucThuHoi" required maxLength="300" placeholder="VD: Có trầy xước, thiếu sạc" /></label>
          <label className="field">Trạng thái sau thu hồi<select name="tinhTrangSauThuHoi" defaultValue="Trong kho">
            <option value="Trong kho">Trong kho</option><option value="Bảo trì">Đang sửa chữa / cần kiểm tra</option><option value="Chờ thanh lý">Hỏng hoàn toàn · chờ thanh lý</option>
          </select></label>
          <label className="field">Người nhận lại<input name="nguoiNhanLai" required maxLength="100" defaultValue={currentUser.username} /></label>
          <label className="field">Người giao lại<input name="nguoiGiaoLai" required maxLength="100" placeholder="Người đang giữ tài sản" defaultValue={returnTarget.nguoiNhan} /></label>
          <label className="field">Ghi chú / hư hỏng<input name="ghiChu" maxLength="500" placeholder="Mô tả hư hỏng hoặc ghi chú" /></label>
          <div className="form-actions"><button className="button secondary" type="button" onClick={() => setReturnTarget(null)}>Hủy</button>
            <button className="button" type="submit" disabled={actionId === returnTarget.id}>{actionId === returnTarget.id ? "Đang lưu..." : "Xác nhận thu hồi"}</button></div>
        </form>}
        <p className="muted admin-return-count">{returnsFiltered.length} tài sản phù hợp bộ lọc thu hồi.</p>
        <AssignmentTable rows={returnsFiltered} loading={loading} onPrint={openHandoverRecord}
          onReturn={setReturnTarget} onDownloadReturnRecord={downloadReturnRecord}
          returnPdfId={returnPdfId} active showReturnDue />
        <h3>Lịch sử phiếu thu hồi</h3>
        <AssignmentTable rows={historyFiltered.filter((item) => item.thuHoiLuc)} loading={loading}
          onPrint={openHandoverRecord} onDownloadReturnRecord={downloadReturnRecord} returnPdfId={returnPdfId} />
      </>}
    </section>
  </>;
}

function AssignmentTable({ rows, loading, onPrint, onReturn, onDownloadReturnRecord, returnPdfId, active = false, showReturnDue = false }) {
  const columns = active ? (showReturnDue ? 9 : 8) : 12;
  return <div className="table-wrap"><table><thead><tr>
    <th>MÃ TÀI SẢN</th><th>TÀI SẢN</th><th>NGƯỜI / PHÒNG NHẬN</th><th>PHÒNG BAN</th>
    <th>NGÀY BÀN GIAO</th>{showReturnDue && <th>HẠN TRẢ / TRẠNG THÁI</th>}
    <th>TÌNH TRẠNG LÚC GIAO</th><th>NGƯỜI GIAO / KÝ NHẬN</th>
    {!active && <><th>NGÀY KẾT THÚC</th><th>TÌNH TRẠNG KHI THU HỒI</th>
      <th>LÝ DO THU HỒI</th><th>NGƯỜI NHẬN / GIAO LẠI</th></>}
    <th>THAO TÁC</th></tr></thead><tbody>
    {loading ? <tr><td className="empty loading-state" colSpan={columns}>Đang tải dữ liệu...</td></tr>
      : rows.length === 0 ? <tr><td className="empty" colSpan={columns}>{active ? "Không có tài sản đang được bàn giao." : "Chưa có lịch sử điều chuyển phù hợp."}</td></tr>
        : rows.map((item) => <tr key={item.id}>
          <td className="code">{item.maTaiSan}</td><td>{item.tenTaiSan}</td>
          <td>{item.nguoiNhan === item.phongBan ? "—" : item.nguoiNhan || "—"}</td><td>{item.phongBan || "—"}</td>
          <td>{item.banGiaoLuc ? new Date(item.banGiaoLuc).toLocaleDateString("vi-VN") : "—"}</td>
          {showReturnDue && <td>{item.ngayDuKienThuHoi ? <>{new Date(`${item.ngayDuKienThuHoi}T00:00:00`).toLocaleDateString("vi-VN")}<br />
            {item.ngayDuKienThuHoi < localDateValue()
              ? <span className="status retired">Quá hạn</span>
              : item.ngayDuKienThuHoi <= localDateValue(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000))
                ? <span className="status warning">Sắp đến hạn</span>
                : <span className="status in-use">Đang giữ</span>}</>
            : <span className="muted">Chưa đặt hạn</span>}</td>}
          <td>{item.tinhTrangLucBanGiao || "—"}</td>
          <td>{[item.nguoiBanGiao, item.nguoiKyNhan].filter(Boolean).join(" / ") || "—"}</td>
          {!active && <><td>{item.thuHoiLuc ? new Date(item.thuHoiLuc).toLocaleDateString("vi-VN") : "Đang giữ"}</td>
            <td>{item.tinhTrangLucThuHoi || "—"}{item.ghiChuThuHoi && <><br /><span className="muted">{item.ghiChuThuHoi}</span></>}</td>
            <td>{{ NGHI_VIEC: "Nghỉ việc", CHUYEN_PHONG: "Chuyển phòng", HET_NHU_CAU: "Hết nhu cầu", KHAC: "Khác" }[item.lyDoThuHoi] || "—"}</td>
            <td>{[item.nguoiNhanLai, item.nguoiGiaoLai].filter(Boolean).join(" / ") || "—"}</td></>}
          <td className="actions">{active && <button className="link-button" type="button" onClick={() => onReturn(item)}>Thu hồi</button>}
            <button className="link-button" type="button" onClick={() => onPrint(item)}>Biên bản bàn giao</button>
            {!active && item.thuHoiLuc && item.coBienBanThuHoi && <button className="link-button" type="button"
              disabled={returnPdfId === item.id} onClick={() => onDownloadReturnRecord(item)}>
              {returnPdfId === item.id ? "Đang tải..." : "Biên bản thu hồi PDF"}
            </button>}</td>
        </tr>)}
  </tbody></table></div>;
}

export default function AdminManagementPage({ page, currentUser }) {
  if (page === "catalog") return <CategoryPage />;
  if (page === "invoices") return <InvoiceDepreciationPage />;
  if (page === "employees" || page === "assets" || page === "consumables") {
    return <ResourcePage resourceKey={page} />;
  }
  if (page === "assignments") return <AssignmentManagementPage currentUser={currentUser} />;
  if (page === "maintenance") return <MaintenanceSchedulesPage />;
  if (page === "stocktakes") return <StocktakePage currentUser={currentUser} />;
  if (page === "reports") return <ReportsPage currentUser={currentUser} />;
  return <section className="panel"><h1>Không tìm thấy trang quản trị</h1><p className="muted">Mục này không tồn tại hoặc chưa được cấu hình.</p></section>;
}
