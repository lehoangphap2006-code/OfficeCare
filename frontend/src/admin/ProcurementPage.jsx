import { useCallback, useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { adminRequest } from "./adminApi";
import PurchaseRequestsPage from "./PurchaseRequestsPage.jsx";
import StockInPage from "./StockInPage.jsx";
import { createVietQrPayload } from "./vietQr.js";

const API_URL = "http://localhost:8081/api";
const ORDER_DRAFT_STORAGE_KEY = "officecare.purchaseOrderDraft";
const DIRECT_ORDER_DRAFT_STORAGE_KEY = "officecare.directPurchaseOrderDraft";
const moneyText = (value) => `${Number(value ?? 0).toLocaleString("vi-VN", { maximumFractionDigits: 0 })} ₫`;
const dateText = (value) => {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
};
const newRequestId = () => crypto.randomUUID();
const readOrderDraft = () => {
  const value = sessionStorage.getItem(ORDER_DRAFT_STORAGE_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    sessionStorage.removeItem(ORDER_DRAFT_STORAGE_KEY);
    return null;
  }
};
const readDirectOrderDraft = () => {
  const value = sessionStorage.getItem(DIRECT_ORDER_DRAFT_STORAGE_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    sessionStorage.removeItem(DIRECT_ORDER_DRAFT_STORAGE_KEY);
    return null;
  }
};
const estimatedUnitPrice = (request) => {
  const total = Number(request.estimatedAmount);
  const quantity = Number(request.quantity);
  return Number.isFinite(total) && total > 0 && quantity > 0
    ? (total / quantity).toFixed(2)
    : "";
};
const today = () => {
  const date = new Date();
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
};
const hasOutstandingQuantity = (order) => order.chiTiet.some(
  (item) => item.soLuongDaNhan < item.soLuong,
);
const openForReceipt = (order) => order.loaiDon === "MUA"
  && ["DA_DAT", "NHAN_MOT_PHAN", "HOAN_TAT"].includes(order.trangThai)
  && hasOutstandingQuantity(order);

function PurchaseOrdersTab({
  requestedOrder,
  onRequestHandled,
  onOpenReceipts,
  onOpenRequest,
  onCheckOrder,
  requestFilterId,
  showApprovedRequests = false,
  showOrders = true,
}) {
  const [requests, setRequests] = useState([]);
  const [orders, setOrders] = useState([]);
  const [references, setReferences] = useState({ suppliers: [], locations: [] });
  const [placingRequest, setPlacingRequest] = useState(null);
  const [unitPrice, setUnitPrice] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [locationId, setLocationId] = useState("");
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState(today());
  const [paymentMethod, setPaymentMethod] = useState("TIEN_MAT");
  const [orderRequestId, setOrderRequestId] = useState("");
  const [orderSubmission, setOrderSubmission] = useState({ status: "idle" });
  const [referencesLoading, setReferencesLoading] = useState(false);
  const [referencesError, setReferencesError] = useState("");
  const [directOrderOpen, setDirectOrderOpen] = useState(false);
  const [directOrderDraft, setDirectOrderDraft] = useState({
    supplierId: "",
    expectedDeliveryDate: today(),
    paymentMethod: "TIEN_MAT",
    productType: "TAI_SAN",
    categoryId: "",
    consumableId: "",
    productName: "",
    quantity: "1",
    unitPrice: "",
    locationId: "",
  });
  const [directOrderCategories, setDirectOrderCategories] = useState([]);
  const [directOrderError, setDirectOrderError] = useState("");
  const [directOrderTechnicalDetail, setDirectOrderTechnicalDetail] = useState("");
  const [directOrderErrorStatus, setDirectOrderErrorStatus] = useState(null);
  const [directOrderSaving, setDirectOrderSaving] = useState(false);
  const [directOrderRequestId, setDirectOrderRequestId] = useState("");
  const [cleanupSaving, setCleanupSaving] = useState(false);
  const [paymentOrder, setPaymentOrder] = useState(null);
  const [paymentBankBin, setPaymentBankBin] = useState("");
  const [paymentAccountNumber, setPaymentAccountNumber] = useState("");
  const [paymentAccountName, setPaymentAccountName] = useState("");
  const [paymentReference, setPaymentReference] = useState("");
  const [paymentQr, setPaymentQr] = useState("");
  const [paymentQrError, setPaymentQrError] = useState("");
  const [paymentError, setPaymentError] = useState("");
  const [paymentSaving, setPaymentSaving] = useState(false);
  const [momoSavingOrderId, setMomoSavingOrderId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [refreshWarning, setRefreshWarning] = useState("");
  const dialogRef = useRef(null);
  const directOrderDialogRef = useRef(null);
  const paymentDialogRef = useRef(null);
  const restoredOrderDraft = useRef(readOrderDraft());
  const restoredDirectOrderDraft = useRef(readDirectOrderDraft());

  const loadData = useCallback(async ({
    preserveSessionOnUnauthorized = false,
    suppressError = false,
  } = {}) => {
    setLoading(true);
    setError("");
    setRefreshWarning("");
    try {
      const [requestRows, orderRows] = await Promise.all([
        adminRequest(`${API_URL}/admin/purchase-requests`, {}, { preserveSessionOnUnauthorized }),
        adminRequest(`${API_URL}/thuong-mai/don-hang`, {}, { preserveSessionOnUnauthorized }),
      ]);
      setRequests(requestRows);
      setOrders(orderRows.filter((order) => order.loaiDon === "MUA"));
      setRefreshWarning("");
    } catch (loadError) {
      if (suppressError) {
        console.error("Đơn mua đã tạo nhưng danh sách chưa tải lại được.", loadError);
        setRefreshWarning("Đơn mua đã tạo thành công nhưng danh sách chưa tải lại được. Bấm “Làm mới” để cập nhật; không tạo lại đơn.");
      } else {
        setError(loadError.message);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData, showOrders, requestFilterId]);

  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get("momo_return") !== "1") return;
    url.searchParams.delete("momo_return");
    window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
    setMessage("Đã quay lại từ MoMo. Đơn chỉ được đánh dấu đã thanh toán sau khi máy chủ xác thực thông báo từ MoMo; bấm Làm mới để kiểm tra.");
  }, []);

  useEffect(() => {
    if (showOrders || placingRequest || !requests.length || !restoredOrderDraft.current) return;
    const saved = restoredOrderDraft.current;
    const request = requests.find((row) => row.id === saved.purchaseRequestId);
    if (!request) {
      sessionStorage.removeItem(ORDER_DRAFT_STORAGE_KEY);
      restoredOrderDraft.current = null;
      return;
    }
    setPlacingRequest(request);
    setOrderRequestId(saved.requestId);
    setSupplierId(saved.supplierId ?? "");
    setExpectedDeliveryDate(saved.expectedDeliveryDate ?? today());
    setUnitPrice(saved.unitPrice ?? "");
    setLocationId(saved.locationId ?? "");
    setPaymentMethod(saved.paymentMethod ?? "TIEN_MAT");
    setOrderSubmission({ status: "idle", message: "Đã đăng nhập lại. Dữ liệu vẫn được giữ nguyên; hãy xác nhận để thử lại." });
    restoredOrderDraft.current = null;
  }, [requests, showOrders, placingRequest]);

  useEffect(() => {
    if (!placingRequest || !orderRequestId) return;
    sessionStorage.setItem(ORDER_DRAFT_STORAGE_KEY, JSON.stringify({
      purchaseRequestId: placingRequest.id,
      requestId: orderRequestId,
      supplierId,
      expectedDeliveryDate,
      unitPrice,
      locationId,
      paymentMethod,
    }));
  }, [placingRequest, orderRequestId, supplierId, expectedDeliveryDate, unitPrice, locationId, paymentMethod]);

  useEffect(() => {
    if (!showOrders || directOrderOpen || !restoredDirectOrderDraft.current) return;
    const saved = restoredDirectOrderDraft.current;
    restoredDirectOrderDraft.current = null;
    beginDirectOrder(saved);
  }, [showOrders, directOrderOpen]);

  useEffect(() => {
    if (!directOrderOpen || !directOrderRequestId) return;
    sessionStorage.setItem(DIRECT_ORDER_DRAFT_STORAGE_KEY, JSON.stringify({
      requestId: directOrderRequestId,
      draft: directOrderDraft,
    }));
  }, [directOrderOpen, directOrderRequestId, directOrderDraft]);

  const loadOrderReferences = useCallback(async () => {
    setReferencesLoading(true);
    setReferencesError("");
    try {
      setReferences(await adminRequest(`${API_URL}/thuong-mai/tham-chieu`));
    } catch (loadError) {
      setReferencesError(loadError.message);
    } finally {
      setReferencesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!requestedOrder) return;
    let active = true;
    async function prepareOrder() {
      setError("");
      try {
        const options = await adminRequest(`${API_URL}/thuong-mai/tham-chieu`);
        if (!active) return;
        setReferences(options);
        setReferencesError("");
        setUnitPrice(estimatedUnitPrice(requestedOrder));
        setSupplierId(options.suppliers.length === 1 ? String(options.suppliers[0].id) : "");
        setLocationId(options.locations.length === 1 ? String(options.locations[0].id) : "");
        setExpectedDeliveryDate(today());
        setPaymentMethod("TIEN_MAT");
        setOrderRequestId(newRequestId());
        setOrderSubmission({ status: "idle" });
        setPlacingRequest(requestedOrder);
      } catch (loadError) {
        if (active) setError(`Không thể tải dữ liệu nhà cung cấp/vị trí: ${loadError.message}`);
      }
    }
    prepareOrder();
    return () => { active = false; };
  }, [requestedOrder]);

  useEffect(() => {
    if (placingRequest && dialogRef.current && !dialogRef.current.open) dialogRef.current.showModal();
  }, [placingRequest]);

  useEffect(() => {
    if (directOrderOpen && directOrderDialogRef.current && !directOrderDialogRef.current.open) {
      directOrderDialogRef.current.showModal();
    }
  }, [directOrderOpen]);

  useEffect(() => {
    if (paymentOrder && paymentDialogRef.current && !paymentDialogRef.current.open) {
      paymentDialogRef.current.showModal();
    }
  }, [paymentOrder]);

  useEffect(() => {
    if (!paymentOrder) {
      setPaymentQr("");
      setPaymentQrError("");
      return undefined;
    }
    setPaymentQr("");
    let active = true;
    try {
      const payload = createVietQrPayload({
        bankBin: paymentBankBin.trim(),
        accountNumber: paymentAccountNumber.trim(),
        amount: paymentOrder.tongTien,
        description: paymentOrder.maDonHang,
      });
      setPaymentQrError("");
      QRCode.toDataURL(payload, { errorCorrectionLevel: "M", margin: 2, width: 280 })
        .then((dataUrl) => { if (active) setPaymentQr(dataUrl); })
        .catch((error) => { if (active) setPaymentQrError(`Không tạo được mã QR: ${error.message}`); });
    } catch (error) {
      setPaymentQr("");
      setPaymentQrError(error.message);
    }
    return () => { active = false; };
  }, [paymentOrder, paymentBankBin, paymentAccountNumber]);

  async function beginDirectOrder(savedDraft = null) {
    setError("");
    setReferencesError("");
    setDirectOrderError("");
    setDirectOrderTechnicalDetail("");
    setDirectOrderErrorStatus(null);
    setReferencesLoading(true);
    try {
      const [options, categories] = await Promise.all([
        adminRequest(`${API_URL}/thuong-mai/tham-chieu`),
        adminRequest(`${API_URL}/loai-tai-san`),
      ]);
      setReferences(options);
      setDirectOrderCategories(categories);
      setDirectOrderDraft(savedDraft?.draft ?? {
        supplierId: options.suppliers.length === 1 ? String(options.suppliers[0].id) : "",
        expectedDeliveryDate: today(),
        paymentMethod: "TIEN_MAT",
        productType: "TAI_SAN",
        categoryId: "",
        consumableId: "",
        productName: "",
        quantity: "1",
        unitPrice: "",
        locationId: options.locations.length === 1 ? String(options.locations[0].id) : "",
      });
      setDirectOrderRequestId(savedDraft?.requestId ?? newRequestId());
      setDirectOrderOpen(true);
      if (savedDraft) {
        setDirectOrderError("Đã đăng nhập lại. Dữ liệu vẫn được giữ nguyên; hãy xác nhận để thử lại.");
      }
    } catch (loadError) {
      setError(`Không thể tải dữ liệu tạo đơn mua: ${loadError.message}`);
    } finally {
      setReferencesLoading(false);
    }
  }

  function closeDirectOrder() {
    if (directOrderSaving) return;
    sessionStorage.removeItem(DIRECT_ORDER_DRAFT_STORAGE_KEY);
    setDirectOrderOpen(false);
    setDirectOrderError("");
    setDirectOrderTechnicalDetail("");
    setDirectOrderErrorStatus(null);
  }

  function updateDirectOrder(field, value) {
    setDirectOrderDraft((current) => ({ ...current, [field]: value }));
    setDirectOrderError("");
    setDirectOrderTechnicalDetail("");
    setDirectOrderErrorStatus(null);
  }

  async function submitDirectOrder(event) {
    event.preventDefault();
    setDirectOrderError("");
    setDirectOrderTechnicalDetail("");
    setDirectOrderErrorStatus(null);
    const quantity = Number(directOrderDraft.quantity);
    const unitPriceValue = Number(directOrderDraft.unitPrice);
    const decimalPlaces = directOrderDraft.unitPrice.split(".")[1]?.length ?? 0;
    if (!references.suppliers.some((row) => String(row.id) === directOrderDraft.supplierId)
      || !directOrderDraft.expectedDeliveryDate || directOrderDraft.expectedDeliveryDate < today()
      || !Number.isInteger(quantity) || quantity < 1 || quantity > 1000
      || !Number.isFinite(unitPriceValue) || unitPriceValue <= 0 || decimalPlaces > 2
      || unitPriceValue > 9999999999999.99) {
      setDirectOrderError("Vui lòng kiểm tra nhà cung cấp, ngày giao, số lượng và đơn giá.");
      return;
    }

    const assetOrder = directOrderDraft.productType === "TAI_SAN";
    if (assetOrder && (!directOrderCategories.some((row) => String(row.loaiId) === directOrderDraft.categoryId)
      || !directOrderDraft.productName.trim() || directOrderDraft.productName.trim().length > 150
      || !references.locations.some((row) => String(row.id) === directOrderDraft.locationId))) {
      setDirectOrderError("Vui lòng nhập tên thiết bị, chọn loại tài sản và vị trí nhận hợp lệ.");
      return;
    }
    if (!assetOrder && !references.vatTu.some((row) => String(row.id) === directOrderDraft.consumableId)) {
      setDirectOrderError("Vui lòng chọn vật tư tiêu hao trong danh mục.");
      return;
    }

    setDirectOrderSaving(true);
    setDirectOrderError("");
    setDirectOrderTechnicalDetail("");
    try {
      const created = await adminRequest(`${API_URL}/thuong-mai/don-hang`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          loaiDon: "MUA",
          phuongThucThanhToan: directOrderDraft.paymentMethod,
          nhaCungCapId: Number(directOrderDraft.supplierId),
          ngayDuKienGiao: directOrderDraft.expectedDeliveryDate,
          chiTiet: [{
            loaiDong: assetOrder ? "TAI_SAN" : "VAT_TU",
            loaiTaiSanId: assetOrder ? Number(directOrderDraft.categoryId) : null,
            viTriId: assetOrder ? Number(directOrderDraft.locationId) : null,
            tenSanPham: assetOrder ? directOrderDraft.productName.trim() : null,
            vatTuId: assetOrder ? null : Number(directOrderDraft.consumableId),
            soLuong: quantity,
            donGia: unitPriceValue,
          }],
          requestId: directOrderRequestId,
        }),
      });
      sessionStorage.removeItem(DIRECT_ORDER_DRAFT_STORAGE_KEY);
      setDirectOrderOpen(false);
      setMessage(`Đã tạo đơn mua ${created.maDonHang || ""} thành công.`);
      setRefreshWarning("");
      await loadData({ preserveSessionOnUnauthorized: true, suppressError: true });
    } catch (submitError) {
      console.error("Không thể xác nhận kết quả tạo đơn mua trực tiếp.", submitError);
      setDirectOrderError(submitError.status === 401
        ? "Chưa tạo được đơn vì phiên đăng nhập không hợp lệ. Dữ liệu đã nhập vẫn còn; hãy đăng nhập lại rồi thử lại."
        : "Chưa xác nhận được kết quả tạo đơn. Hãy kiểm tra tab Đơn mua trước khi thử lại.");
      setDirectOrderTechnicalDetail(submitError.message);
      setDirectOrderErrorStatus(submitError.status ?? null);
    } finally {
      setDirectOrderSaving(false);
    }
  }

  async function openPayment(order) {
    if (order.phuongThucThanhToan === "MOMO") {
      if (momoSavingOrderId !== null) return;
      setMomoSavingOrderId(order.id);
      setError("");
      setMessage("");
      try {
        const checkout = await adminRequest(`${API_URL}/payments/momo/don-hang/${order.id}/checkout`, {
          method: "POST",
        });
        const paymentUrl = new URL(checkout.payUrl);
        if (paymentUrl.protocol !== "https:" || !paymentUrl.hostname.endsWith(".momo.vn")) {
          throw new Error("MoMo trả về liên kết thanh toán không hợp lệ.");
        }
        window.location.assign(paymentUrl.toString());
      } catch (checkoutError) {
        console.error("Không thể tạo phiên thanh toán MoMo.", checkoutError);
        setError(checkoutError.message || "Không thể kết nối MoMo. Vui lòng thử lại.");
      } finally {
        setMomoSavingOrderId(null);
      }
      return;
    }
    setPaymentOrder(order);
    setPaymentReference("");
    setPaymentQr("");
    setPaymentError("");
  }

  function closePayment() {
    if (paymentSaving) return;
    if (paymentDialogRef.current?.open) paymentDialogRef.current.close();
    setPaymentOrder(null);
    setPaymentError("");
  }

  async function confirmVietQrPayment(event) {
    event.preventDefault();
    if (!paymentOrder || !paymentQr) {
      setPaymentError("Vui lòng nhập thông tin tài khoản để tạo mã VietQR trước.");
      return;
    }
    const reference = paymentReference.trim();
    if (!reference || reference.length > 40) {
      setPaymentError("Vui lòng nhập mã tham chiếu giao dịch đã đối chiếu, tối đa 40 ký tự.");
      return;
    }
    setPaymentSaving(true);
    setPaymentError("");
    try {
      const paidOrder = await adminRequest(
        `${API_URL}/thuong-mai/don-hang/${paymentOrder.id}/xac-nhan-thanh-toan`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ transactionReference: reference }),
        },
      );
      setOrders((current) => current.map((order) => order.id === paidOrder.id ? paidOrder : order));
      setMessage(`Đã ghi nhận thanh toán đơn ${paidOrder.maDonHang} theo mã giao dịch ${paidOrder.maGiaoDich}.`);
      setPaymentOrder(null);
      if (paymentDialogRef.current?.open) paymentDialogRef.current.close();
    } catch (error) {
      setPaymentError(`Không thể xác nhận thanh toán: ${error.message}`);
    } finally {
      setPaymentSaving(false);
    }
  }

  async function deleteUnreceivedOrders() {
    const confirmed = window.confirm(
      "Chỉ xóa đơn mua chưa nhận hàng, chưa có phiếu nhập, hóa đơn hoặc thanh toán. "
      + "Các yêu cầu nhân viên được giữ lại để có thể tạo đơn mới. Tiếp tục?",
    );
    if (!confirmed) return;

    setCleanupSaving(true);
    setError("");
    setMessage("");
    setRefreshWarning("");
    try {
      const result = await adminRequest(`${API_URL}/admin/purchase-orders/unreceived`, {
        method: "DELETE",
      });
      const deletedCount = result.deletedOrderNumbers.length;
      const retainedCount = result.retainedOrders.length;
      const retainedSummary = retainedCount
        ? ` Giữ lại ${retainedCount} đơn đã có dữ liệu liên quan hoặc trạng thái không an toàn để xóa.`
        : "";
      setMessage(`Đã xóa ${deletedCount} đơn mua chưa nhận hàng.${retainedSummary}`);
      await loadData({ suppressError: true });
    } catch (cleanupError) {
      setError(`Không thể xóa đơn mua chưa nhận hàng: ${cleanupError.message}`);
    } finally {
      setCleanupSaving(false);
    }
  }

  async function beginOrder(request) {
    setError("");
    setReferencesError("");
    setReferencesLoading(true);
    try {
      const options = await adminRequest(`${API_URL}/thuong-mai/tham-chieu`);
      setReferences(options);
      setReferencesError("");
      setUnitPrice(estimatedUnitPrice(request));
      setSupplierId(options.suppliers.length === 1 ? String(options.suppliers[0].id) : "");
      setLocationId(options.locations.length === 1 ? String(options.locations[0].id) : "");
      setExpectedDeliveryDate(today());
      setPaymentMethod("TIEN_MAT");
      setOrderRequestId(newRequestId());
      setOrderSubmission({ status: "idle" });
      setPlacingRequest(request);
    } catch (loadError) {
      setError(`Không thể tải dữ liệu nhà cung cấp/vị trí: ${loadError.message}`);
    } finally {
      setReferencesLoading(false);
    }
  }

  function closeOrder() {
    if (saving) return;
    sessionStorage.removeItem(ORDER_DRAFT_STORAGE_KEY);
    setOrderSubmission({ status: "idle" });
    setPlacingRequest(null);
    onRequestHandled();
  }

  async function submitOrder(event) {
    event.preventDefault();
    if (!placingRequest) return;
    const missingFields = [];
    if (!references.suppliers.some((supplier) => String(supplier.id) === supplierId)) {
      missingFields.push(["Nhà cung cấp", "supplierId"]);
    }
    if (!expectedDeliveryDate) missingFields.push(["Ngày dự kiến giao", "expectedDeliveryDate"]);
    if (!unitPrice.trim()) missingFields.push(["Đơn giá mỗi thiết bị", "unitPrice"]);
    if (!references.locations.some((location) => String(location.id) === locationId)) {
      missingFields.push(["Vị trí nhận", "locationId"]);
    }
    if (missingFields.length > 0) {
      setOrderSubmission({
        status: "validation_failed",
        message: `Vui lòng điền đầy đủ thông tin bắt buộc: ${missingFields.map(([label]) => label).join(", ")}.`,
      });
      const firstMissingField = dialogRef.current?.querySelector(`[name="${missingFields[0][1]}"]`);
      firstMissingField?.focus();
      firstMissingField?.scrollIntoView({ block: "center", behavior: "smooth" });
      return;
    }
    const enteredUnitPrice = Number(unitPrice);
    const decimalPlaces = unitPrice.split(".")[1]?.length ?? 0;
    const orderTotal = enteredUnitPrice * Number(placingRequest.quantity);
    if (!expectedDeliveryDate || expectedDeliveryDate < today()) {
      setOrderSubmission({
        status: "validation_failed",
        message: "Ngày dự kiến giao đã quá hạn. Vui lòng chọn hôm nay hoặc một ngày trong tương lai.",
      });
      return;
    }
    if (!Number.isFinite(orderTotal) || enteredUnitPrice <= 0 || enteredUnitPrice > 9999999999999.99
      || decimalPlaces > 2) {
      setOrderSubmission({
        status: "validation_failed",
        message: "Đơn giá phải lớn hơn 0, tối đa 2 chữ số thập phân và không vượt quá 9.999.999.999.999 VNĐ.",
      });
      return;
    }
    setSaving(true);
    setOrderSubmission({ status: "submitting" });
    setMessage("");
    setRefreshWarning("");
    try {
      const updated = await adminRequest(
        `${API_URL}/admin/purchase-requests/${placingRequest.id}/place-order`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            supplierId: Number(supplierId),
            expectedDeliveryDate,
            paymentMethod,
            unitPrice: enteredUnitPrice,
            locationId: Number(locationId),
            requestId: orderRequestId,
          }),
        },
        { preserveSessionOnUnauthorized: true },
      );
      setRequests((current) => current.map((request) => request.id === updated.id
        ? {
          ...request,
          purchaseOrderId: updated.purchaseOrderId,
          purchaseOrderNumber: updated.purchaseOrderNumber,
          purchaseOrderStatus: updated.purchaseOrderStatus,
        }
        : request));
      sessionStorage.removeItem(ORDER_DRAFT_STORAGE_KEY);
      setOrderSubmission({ status: "success", orderNumber: updated.purchaseOrderNumber });
      setPlacingRequest(null);
      onRequestHandled(updated);
      setMessage(`Đã tạo đơn mua${updated.purchaseOrderNumber ? ` ${updated.purchaseOrderNumber}` : ""} thành công.`);
      await loadData({ preserveSessionOnUnauthorized: true, suppressError: true });
    } catch (submitError) {
      const authenticationFailed = submitError.status === 401;
      const validationFailed = submitError.status === 400 || submitError.status === 422;
      console.error("Không thể xác nhận kết quả tạo đơn mua.", submitError);
      setOrderSubmission({
        status: authenticationFailed ? "auth_failed" : validationFailed ? "validation_failed" : "server_failed",
        message: authenticationFailed
          ? "Chưa tạo được đơn vì phiên đăng nhập không hợp lệ. Đơn của bạn chưa được lưu, dữ liệu đã nhập vẫn còn. Hãy đăng nhập lại, sau đó bấm “Xác nhận” để thử lại."
          : validationFailed
            ? "Thông tin đặt hàng chưa hợp lệ. Dữ liệu vẫn được giữ lại; vui lòng kiểm tra các trường và thử lại."
            : "Chưa xác nhận được kết quả tạo đơn. Đơn có thể đã được lưu; hãy kiểm tra đơn mua trước khi gửi lại.",
        technicalDetail: submitError.message,
        statusCode: submitError.status,
      });
    } finally {
      setSaving(false);
    }
  }

  const approvedRequests = requests.filter((request) =>
    request.status === "APPROVED" && !request.purchaseOrderId);
  const requestByOrderId = new Map(requests
    .filter((request) => request.purchaseOrderId != null)
    .map((request) => [request.purchaseOrderId, request]));
  const filteredOrders = orders.filter((order) => !requestFilterId
    || requestByOrderId.get(order.id)?.id === requestFilterId);
  const enteredUnitPrice = Number(unitPrice);
  const unitPriceDecimalPlaces = unitPrice.split(".")[1]?.length ?? 0;
  const canSubmitOrder = Boolean(
    placingRequest
    && references.suppliers.some((row) => String(row.id) === supplierId)
    && expectedDeliveryDate >= today()
    && unitPrice.trim()
    && Number.isFinite(enteredUnitPrice)
    && enteredUnitPrice > 0
    && enteredUnitPrice <= 9999999999999.99
    && unitPriceDecimalPlaces <= 2
    && references.locations.some((row) => String(row.id) === locationId),
  );

  return (
    <>
      {error && <p className="message error" role="alert">{error}</p>}
      {message && <p className="message" role="status">{message}</p>}
      {refreshWarning && <p className="message error" role="status">{refreshWarning}</p>}
      {showOrders && showApprovedRequests && <section className="panel assets-panel">
        <div className="panel-heading assets-heading">
          <div><h2>Yêu cầu đã duyệt, chờ tạo đơn mua</h2><p className="muted">{approvedRequests.length} yêu cầu · Có thể tạo đơn trực tiếp nếu không xuất phát từ yêu cầu nhân viên.</p></div>
          <div className="admin-resource-actions">
            <button className="button" type="button" onClick={beginDirectOrder} disabled={referencesLoading}>
              {referencesLoading ? "Đang tải..." : "＋ Tạo đơn mua trực tiếp"}
            </button>
            <button className="text-button" type="button" onClick={loadData} disabled={loading}>Làm mới</button>
          </div>
        </div>
        <div className="table-wrap" aria-busy={loading}><table>
          <thead><tr><th>NGÀY GỬI</th><th>NGƯỜI YÊU CẦU</th><th>THIẾT BỊ / LOẠI</th><th>SỐ LƯỢNG</th><th>DỰ TOÁN</th><th>GHI CHÚ DUYỆT</th><th>THAO TÁC</th></tr></thead>
          <tbody>{loading ? <tr><td className="empty loading-state" colSpan="7">Đang tải yêu cầu...</td></tr>
            : approvedRequests.length === 0 ? <tr><td className="empty" colSpan="7">Không có yêu cầu đã duyệt chờ tạo đơn.</td></tr>
              : approvedRequests.map((request) => <tr key={request.id}>
                <td>{dateText(request.createdAt)}</td><td>{request.requester}</td>
                <td>{request.assetName}<br /><span className="muted">{request.categoryName || "—"}</span></td>
                <td>{request.quantity}</td><td>{moneyText(request.estimatedAmount)}</td>
                <td>{request.reviewNote || "Đã duyệt"}</td>
                <td><button className="button purchase-order-button" type="button" onClick={() => beginOrder(request)}>Tạo đơn mua</button></td>
              </tr>)}
          </tbody>
        </table></div>
      </section>}

      {directOrderOpen && <dialog ref={directOrderDialogRef} className="detail-dialog commerce-receipt-dialog"
        aria-labelledby="direct-order-title" onCancel={(event) => { event.preventDefault(); closeDirectOrder(); }}>
        <form noValidate onSubmit={submitDirectOrder}>
          <div className="panel-heading"><div><p className="eyebrow">ĐẶT HÀNG NHÀ CUNG CẤP</p>
            <h2 id="direct-order-title">Tạo đơn mua trực tiếp</h2>
            <p className="muted">Đơn được lưu ở trạng thái Đã đặt; chỉ nhập kho sau khi hàng thực tế được giao.</p></div>
            <button className="icon-button" type="button" aria-label="Đóng" onClick={closeDirectOrder} disabled={directOrderSaving}>×</button></div>
          {directOrderError && <div className={`message${directOrderError.startsWith("Đã đăng nhập lại") ? "" : " error"}`}
            role={directOrderError.startsWith("Đã đăng nhập lại") ? "status" : "alert"}>
            <p>{directOrderError}</p>
            {directOrderErrorStatus === 401 && <button className="retry-button" type="button" onClick={() => {
              window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
                detail: { method: "POST", url: `${API_URL}/thuong-mai/don-hang`, status: 401 },
              }));
            }}>Đăng nhập lại</button>}
            {directOrderTechnicalDetail && <details className="technical-details">
              <summary>Chi tiết kỹ thuật</summary>
              <p>{directOrderTechnicalDetail}</p>
            </details>}
          </div>}
          <label>Nhà cung cấp <span aria-hidden="true">*</span>
            <select required value={directOrderDraft.supplierId} onChange={(event) => updateDirectOrder("supplierId", event.target.value)}>
              <option value="">Chọn nhà cung cấp</option>
              {references.suppliers.map((row) => <option key={row.id} value={row.id}>{row.tenNhaCungCap}</option>)}
            </select>
            {references.suppliers.length === 0 && <small>Chưa có nhà cung cấp. Thêm trong <a href="/admin/catalog">Danh mục</a>.</small>}
          </label>
          <div className="form-grid">
            <label>Ngày dự kiến giao <span aria-hidden="true">*</span>
              <input type="date" min={today()} required value={directOrderDraft.expectedDeliveryDate}
                onChange={(event) => updateDirectOrder("expectedDeliveryDate", event.target.value)} />
            </label>
            <label>Phương thức thanh toán
              <select value={directOrderDraft.paymentMethod} onChange={(event) => updateDirectOrder("paymentMethod", event.target.value)}>
                <option value="TIEN_MAT">Thanh toán khi nhận hàng</option>
                <option value="VIETQR">Chuyển khoản VietQR (đối chiếu thủ công)</option>
                <option value="MOMO">MoMo (thanh toán trực tuyến)</option>
              </select>
            </label>
          </div>
          <label>Loại hàng
            <select value={directOrderDraft.productType} onChange={(event) => updateDirectOrder("productType", event.target.value)}>
              <option value="TAI_SAN">Thiết bị / tài sản</option>
              <option value="VAT_TU">Vật tư tiêu hao</option>
            </select>
          </label>
          {directOrderDraft.productType === "TAI_SAN" ? <>
            <label>Tên thiết bị <span aria-hidden="true">*</span>
              <input required maxLength="150" value={directOrderDraft.productName}
                onChange={(event) => updateDirectOrder("productName", event.target.value)} />
            </label>
            <div className="form-grid">
              <label>Loại tài sản <span aria-hidden="true">*</span>
                <select required value={directOrderDraft.categoryId} onChange={(event) => updateDirectOrder("categoryId", event.target.value)}>
                  <option value="">Chọn danh mục</option>
                  {directOrderCategories.map((row) => <option key={row.loaiId} value={row.loaiId}>{row.tenLoai}</option>)}
                </select>
              </label>
              <label>Vị trí nhận <span aria-hidden="true">*</span>
                <select required value={directOrderDraft.locationId} onChange={(event) => updateDirectOrder("locationId", event.target.value)}>
                  <option value="">Chọn vị trí nhận</option>
                  {references.locations.map((row) => <option key={row.id} value={row.id}>
                    {[row.toaNha, row.tang && `Tầng ${row.tang}`, `Phòng ${row.phong}`].filter(Boolean).join(" · ")}
                  </option>)}
                </select>
              </label>
            </div>
          </> : <label>Vật tư tiêu hao <span aria-hidden="true">*</span>
            <select required value={directOrderDraft.consumableId} onChange={(event) => {
              const selected = references.vatTu.find((row) => String(row.id) === event.target.value);
              setDirectOrderDraft((current) => ({
                ...current,
                consumableId: event.target.value,
                unitPrice: selected?.donGia == null ? current.unitPrice : String(selected.donGia),
              }));
              setDirectOrderError("");
            }}>
              <option value="">Chọn vật tư</option>
              {references.vatTu.map((row) => <option key={row.id} value={row.id}>{row.tenVatTu} ({row.donVi})</option>)}
            </select>
            {references.vatTu.length === 0 && <small>Chưa có vật tư tiêu hao trong danh mục.</small>}
          </label>}
          <div className="form-grid">
            <label>Số lượng <span aria-hidden="true">*</span>
              <input type="number" min="1" max="1000" step="1" required value={directOrderDraft.quantity}
                onChange={(event) => updateDirectOrder("quantity", event.target.value)} />
            </label>
            <label>Đơn giá (VNĐ) <span aria-hidden="true">*</span>
              <input type="number" min="0.01" step="0.01" required value={directOrderDraft.unitPrice}
                onChange={(event) => updateDirectOrder("unitPrice", event.target.value)} />
            </label>
          </div>
          <div className="form-actions">
            <button className="button secondary" type="button" onClick={closeDirectOrder} disabled={directOrderSaving}>Hủy</button>
            <button className="button" type="submit" disabled={directOrderSaving || referencesLoading}>
              {directOrderSaving ? "Đang tạo đơn..." : "Xác nhận tạo đơn mua"}
            </button>
          </div>
        </form>
      </dialog>}

      {paymentOrder && <dialog ref={paymentDialogRef} className="detail-dialog commerce-receipt-dialog"
        aria-labelledby="vietqr-payment-title" onCancel={(event) => { event.preventDefault(); closePayment(); }}>
        <form onSubmit={confirmVietQrPayment}>
          <div className="panel-heading"><div>
            <p className="eyebrow">THANH TOÁN CHUYỂN KHOẢN VIETQR</p>
            <h2 id="vietqr-payment-title">{paymentOrder.maDonHang}</h2>
            <p className="muted">Số tiền: <strong>{moneyText(paymentOrder.tongTien)}</strong>. Quét QR bằng ứng dụng ngân hàng bất kỳ có hỗ trợ VietQR.</p>
          </div><button className="icon-button" type="button" aria-label="Đóng" onClick={closePayment}>×</button></div>
          {paymentError && <p className="message error" role="alert">{paymentError}</p>}
          <div className="form-grid">
            <label>Mã BIN ngân hàng (6 số)
              <input inputMode="numeric" autoComplete="off" maxLength="6" value={paymentBankBin}
                onChange={(event) => setPaymentBankBin(event.target.value.replace(/\D/g, "").slice(0, 6))} />
            </label>
            <label>Số tài khoản nhận
              <input inputMode="numeric" autoComplete="off" maxLength="20" value={paymentAccountNumber}
                onChange={(event) => setPaymentAccountNumber(event.target.value.replace(/\D/g, "").slice(0, 20))} />
            </label>
          </div>
          <label>Tên chủ tài khoản
            <input autoComplete="off" maxLength="100" value={paymentAccountName}
              onChange={(event) => setPaymentAccountName(event.target.value.toLocaleUpperCase("vi"))} />
          </label>
          {paymentQrError && <p className="message error" role="status">{paymentQrError}</p>}
          {paymentQr && <div className="vietqr-preview">
            <img src={paymentQr} alt={`Mã VietQR thanh toán đơn ${paymentOrder.maDonHang}`} />
            <p><strong>{paymentAccountName.trim() || "Chủ tài khoản"}</strong></p>
            <p>{paymentBankBin} · {paymentAccountNumber}</p>
            <p>Nội dung chuyển khoản: <strong>{paymentOrder.maDonHang}</strong></p>
          </div>}
          <p className="muted">Chỉ xác nhận sau khi đã kiểm tra tiền thực nhận trên tài khoản ngân hàng. Tạo QR không tự đánh dấu đơn đã thanh toán.</p>
          <label>Mã tham chiếu giao dịch ngân hàng
            <input required maxLength="40" autoComplete="off" value={paymentReference}
              onChange={(event) => setPaymentReference(event.target.value)} />
          </label>
          <div className="form-actions">
            <button className="button secondary" type="button" onClick={closePayment} disabled={paymentSaving}>Đóng</button>
            <button className="button" type="submit" disabled={paymentSaving || !paymentQr}>
              {paymentSaving ? "Đang ghi nhận..." : "Xác nhận đã nhận tiền"}
            </button>
          </div>
        </form>
      </dialog>}

      {showOrders && <section className="panel assets-panel">
        <div className="panel-heading assets-heading">
          <div><h2>Danh sách đơn mua</h2><p className="muted">{filteredOrders.length} đơn mua · Số nhận được đối chiếu trực tiếp với số lượng đã đặt.</p></div>
          <div className="admin-resource-actions">
            {requestFilterId && <button className="text-button" type="button" onClick={() => onCheckOrder(null)}>Bỏ lọc</button>}
            <button className="text-button" type="button" onClick={loadData} disabled={loading}>Làm mới</button>
            <button className="button" type="button" onClick={beginDirectOrder} disabled={referencesLoading}>
              {referencesLoading ? "Đang tải..." : "＋ Tạo đơn mua trực tiếp"}
            </button>
            <button className="button secondary" type="button" onClick={deleteUnreceivedOrders}
              disabled={cleanupSaving || loading || orders.length === 0}>
              {cleanupSaving ? "Đang xóa..." : "Xóa đơn chưa nhận hàng"}
            </button>
          </div>
        </div>
        {requestFilterId && <p className="message" role="status">Đang lọc theo yêu cầu #{requestFilterId}.</p>}
        <div className="table-wrap" aria-busy={loading}><table>
          <thead><tr><th>MÃ ĐƠN</th><th>NGÀY GIAO DỰ KIẾN</th><th>NHÀ CUNG CẤP</th><th>YÊU CẦU / SẢN PHẨM</th><th>TIẾN ĐỘ NHẬN</th><th>TỔNG TIỀN</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead>
          <tbody>{loading ? <tr><td className="empty loading-state" colSpan="8">Đang tải danh sách đơn mua...</td></tr>
            : filteredOrders.length === 0 ? <tr><td className="empty" colSpan="8">{requestFilterId ? "Không tìm thấy đơn mua gắn với yêu cầu này." : "Chưa có đơn mua."}</td></tr>
              : filteredOrders.map((order) => <tr key={order.id}>
                <td className="code">{order.maDonHang}</td><td>{dateText(order.ngayDuKienGiao)}</td><td>{order.nhaCungCap || "—"}</td>
                <td>{requestByOrderId.get(order.id) && <><span className="muted">Từ yêu cầu của {requestByOrderId.get(order.id).requester}</span>
                  {requestByOrderId.get(order.id).categoryName ? ` · ${requestByOrderId.get(order.id).categoryName}` : ""}
                  <br /><button className="link-button" type="button" onClick={() => onOpenRequest(requestByOrderId.get(order.id).id)}>
                    Xem yêu cầu #{requestByOrderId.get(order.id).id}
                  </button><br /></>}
                  {order.chiTiet.map((item) => `${item.tenSanPham} × ${item.soLuong}`).join(", ")}</td>
                <td>{order.chiTiet.map((item) => `${item.soLuongDaNhan}/${item.soLuong}`).join(", ")}</td>
                <td>{moneyText(order.tongTien)}</td>
                <td><strong>{order.trangThai === "DA_DAT" ? "Đã đặt"
                  : order.trangThai === "NHAN_MOT_PHAN" ? "Đã nhận một phần"
                    : order.trangThai === "DA_NHAN" ? "Đã nhận đủ"
                      : order.trangThai === "DA_HUY" ? "Đã hủy"
                        : order.trangThai === "HOAN_TAT" && openForReceipt(order)
                          ? "Cần nhận hàng (đơn cũ)" : order.trangThai}</strong>
                  <br /><span className="muted">{order.trangThaiThanhToan === "DA_THANH_TOAN"
                    ? `Đã thanh toán${order.maGiaoDich ? ` · GD ${order.maGiaoDich}` : ""}`
                    : "Chưa thanh toán"}</span>
                </td>
                <td className="purchase-order-actions">
                  {openForReceipt(order) && <button className="link-button" type="button"
                    onClick={() => onOpenReceipts(order.id, `Đang mở phiếu nhận hàng cho đơn ${order.maDonHang}.`)}>
                    Nhận hàng
                  </button>}
                  {order.trangThaiThanhToan !== "DA_THANH_TOAN"
                    && ["DA_DAT", "NHAN_MOT_PHAN", "DA_NHAN", "HOAN_TAT"].includes(order.trangThai)
                    && <button className="link-button" type="button" onClick={() => openPayment(order)}
                      disabled={momoSavingOrderId === order.id}>
                      {momoSavingOrderId === order.id ? "Đang kết nối MoMo..." : order.phuongThucThanhToan === "MOMO" ? "Thanh toán MoMo" : "Thanh toán VietQR"}
                    </button>}
                  {order.trangThai === "DA_NHAN" && order.trangThaiThanhToan === "DA_THANH_TOAN" ? "Đã nhận đủ" : null}
                  {!openForReceipt(order) && order.trangThai !== "DA_NHAN"
                    && (order.trangThaiThanhToan === "DA_THANH_TOAN" || !["DA_DAT", "NHAN_MOT_PHAN", "DA_NHAN", "HOAN_TAT"].includes(order.trangThai)) ? "—" : null}
                </td>
              </tr>)}
          </tbody>
        </table></div>
      </section>}

      {placingRequest && <dialog ref={dialogRef} className="detail-dialog commerce-receipt-dialog"
        onCancel={(event) => { event.preventDefault(); closeOrder(); }}>
        <form noValidate onSubmit={submitOrder}>
          <div className="panel-heading"><div><p className="eyebrow">TẠO ĐƠN MUA TỪ YÊU CẦU ĐÃ DUYỆT</p>
            <h2>{placingRequest.assetName} × {placingRequest.quantity}</h2>
            <p className="muted">Danh mục: {placingRequest.categoryName || "Không xác định — kiểm tra lại danh mục tài sản"}</p>
            <p className="muted">Dự toán: {moneyText(placingRequest.estimatedAmount)} · Tổng đơn theo đơn giá: {orderTotalPreview(unitPrice, placingRequest.quantity) == null ? "—" : moneyText(orderTotalPreview(unitPrice, placingRequest.quantity))}</p></div>
            <button className="icon-button" type="button" aria-label="Đóng" onClick={closeOrder}>×</button></div>
          {orderSubmission.message && <div className={`message${["auth_failed", "validation_failed", "server_failed"].includes(orderSubmission.status) ? " error" : ""}`}
            role={["auth_failed", "validation_failed", "server_failed"].includes(orderSubmission.status) ? "alert" : "status"} aria-live="assertive">
            <p>{orderSubmission.message}</p>
            {orderSubmission.status === "success" && orderSubmission.orderNumber
              && <p><strong>Mã đơn: {orderSubmission.orderNumber}</strong></p>}
            {orderSubmission.status === "auth_failed" && <button className="retry-button" type="button" onClick={() => {
              window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
                detail: {
                  method: "POST",
                  url: `${API_URL}/admin/purchase-requests/${placingRequest.id}/place-order`,
                  status: 401,
                  message: orderSubmission.technicalDetail,
                },
              }));
            }}>Đăng nhập lại</button>}
            {(orderSubmission.status === "auth_failed" || orderSubmission.status === "server_failed")
              && <button className="retry-button" type="button" onClick={() => {
                setPlacingRequest(null);
                onCheckOrder(placingRequest.id);
              }}>Kiểm tra đơn mua</button>}
            {orderSubmission.technicalDetail && <details className="technical-details">
              <summary>Chi tiết kỹ thuật</summary>
              <p>HTTP {orderSubmission.statusCode ?? "—"} · {orderSubmission.technicalDetail}</p>
            </details>}
          </div>}
          {orderSubmission.status === "submitting" && <p className="message" role="status">Đang gửi yêu cầu tạo đơn…</p>}
          {referencesError && <p className="message error" role="alert">Không tải được nhà cung cấp/vị trí: {referencesError}
            <button className="retry-button" type="button" onClick={loadOrderReferences} disabled={referencesLoading}>{referencesLoading ? "Đang tải..." : "Tải lại"}</button>
          </p>}
          <label>Nhà cung cấp <span aria-hidden="true">*</span>
            <select name="supplierId" required value={supplierId} onChange={(event) => { setSupplierId(event.target.value); setOrderSubmission({ status: "idle" }); }}
              disabled={referencesLoading || references.suppliers.length === 0}>
              <option value="" disabled>{referencesLoading ? "Đang tải nhà cung cấp..." : references.suppliers.length ? "Chọn nhà cung cấp" : "Chưa có nhà cung cấp"}</option>
              {references.suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.tenNhaCungCap}</option>)}
            </select>
            {references.suppliers.length === 0 && !referencesLoading && <small>Chưa có dữ liệu. <a href="/admin/catalog" target="_blank" rel="noreferrer">Thêm nhà cung cấp trong Danh mục</a>, sau đó tải lại danh sách.</small>}
          </label>
          <label>Ngày dự kiến giao <span aria-hidden="true">*</span><input name="expectedDeliveryDate" type="date" required min={today()} value={expectedDeliveryDate}
            onChange={(event) => { setExpectedDeliveryDate(event.target.value); setOrderSubmission({ status: "idle" }); }} /></label>
          <label>Phương thức thanh toán<select name="paymentMethod" value={paymentMethod}
            onChange={(event) => setPaymentMethod(event.target.value)}><option value="TIEN_MAT">Thanh toán khi nhận hàng</option>            <option value="VIETQR">Chuyển khoản VietQR (đối chiếu thủ công)</option>
            <option value="MOMO">MoMo (thanh toán trực tuyến)</option></select></label>
          <label>Đơn giá mỗi thiết bị (VNĐ) <span aria-hidden="true">*</span><input name="unitPrice" type="number" required min="0.01" step="0.01"
            value={unitPrice} onChange={(event) => { setUnitPrice(event.target.value); setOrderSubmission({ status: "idle" }); }} />
            <small>Nhập giá thực tế theo VNĐ. Tổng dự kiến: {orderTotalPreview(unitPrice, placingRequest.quantity) == null ? "—" : moneyText(orderTotalPreview(unitPrice, placingRequest.quantity))}</small>
          </label>
          <label>Vị trí nhận <span aria-hidden="true">*</span>
            <select name="locationId" required value={locationId} onChange={(event) => { setLocationId(event.target.value); setOrderSubmission({ status: "idle" }); }}
              disabled={referencesLoading || references.locations.length === 0}>
              <option value="" disabled>{referencesLoading ? "Đang tải vị trí..." : references.locations.length ? "Chọn vị trí nhận" : "Chưa có vị trí nhận"}</option>
              {references.locations.map((location) => <option key={location.id} value={location.id}>{[location.toaNha, location.tang && `Tầng ${location.tang}`, location.phong && `Phòng ${location.phong}`].filter(Boolean).join(" · ") || `Vị trí #${location.id}`}</option>)}
            </select>
            {references.locations.length === 0 && !referencesLoading && <small>Chưa có dữ liệu. <a href="/admin/catalog" target="_blank" rel="noreferrer">Thêm vị trí trong Danh mục</a>, sau đó tải lại danh sách.</small>}
          </label>
          <div className="commerce-actions"><button className="button secondary" type="button" onClick={closeOrder} disabled={saving}>Hủy</button>
            <button className="button" type="submit" disabled={!canSubmitOrder || saving || referencesLoading}>
              {saving ? "Đang tạo đơn..." : "Xác nhận tạo đơn mua"}
            </button></div>
        </form>
      </dialog>}
    </>
  );
}

function orderTotalPreview(unitPrice, quantity) {
  if (unitPrice === "" || unitPrice == null) return null;
  const price = Number(unitPrice);
  const count = Number(quantity);
  return Number.isFinite(price) && Number.isFinite(count) ? price * count : null;
}

export default function ProcurementPage({ initialTab = "requests", currentUser }) {
  const validTabs = ["requests", "orders", "receipts"];
  const [activeTab, setActiveTab] = useState(() => {
    const requestedTab = new URLSearchParams(window.location.search).get("tab") || initialTab;
    return validTabs.includes(requestedTab) ? requestedTab : "requests";
  });
  const [pendingCount, setPendingCount] = useState(0);
  const [countError, setCountError] = useState("");
  const [requestedOrder, setRequestedOrder] = useState(null);
  const [receiptOrderId, setReceiptOrderId] = useState(null);
  const [orderRequestFilterId, setOrderRequestFilterId] = useState(null);
  const [focusedRequestId, setFocusedRequestId] = useState(null);
  const [requestRefreshKey, setRequestRefreshKey] = useState(0);
  const [workflowMessage, setWorkflowMessage] = useState("");
  const clearReceiptOrderId = useCallback(() => setReceiptOrderId(null), []);
  const updatePendingCount = useCallback((count) => setPendingCount(count), []);
  const handleRequestHandled = useCallback((createdOrder = null) => {
    setRequestedOrder(null);
    setRequestRefreshKey((current) => current + 1);
    if (createdOrder?.purchaseOrderId) {
      setReceiptOrderId(null);
      setOrderRequestFilterId(createdOrder.id);
      setActiveTab("orders");
      const url = new URL(window.location.href);
      url.searchParams.set("tab", "orders");
      window.history.replaceState(null, "", url);
      setWorkflowMessage(`Đã tạo đơn mua ${createdOrder.purchaseOrderNumber || ""}. Kiểm tra mã đơn và tiến độ tại tab Đơn mua.`);
    }
  }, []);
  const loadPendingCount = useCallback(async () => {
    try {
      setPendingCount(Number(await adminRequest(`${API_URL}/admin/purchase-requests/pending-count`)));
      setCountError("");
    } catch (loadError) {
      setCountError(`Không thể tải số yêu cầu chờ duyệt: ${loadError.message}`);
    }
  }, []);
  useEffect(() => { loadPendingCount(); }, [loadPendingCount]);
  const tabs = [
    ["requests", `01 · Yêu cầu (${pendingCount})`],
    ["orders", "02 · Đơn mua"],
    ["receipts", "03 · Nhận hàng"],
  ];
  const activeStep = {
    requests: {
      title: "Tiếp nhận và duyệt yêu cầu",
      description: "Nhân viên gửi yêu cầu; admin xem lý do, dự toán rồi duyệt hoặc từ chối. Chỉ yêu cầu đã duyệt mới được tạo đơn mua.",
    },
    orders: {
      title: "Theo dõi đơn mua",
      description: "Đối chiếu mã đơn, nhà cung cấp, ngày giao dự kiến, tiến độ nhận hàng, tổng tiền và trạng thái.",
    },
    receipts: {
      title: "Đối chiếu và nhập kho",
      description: "Khi hàng về, nhập số thực nhận và số hỏng. Có thể nhận nhiều đợt; hệ thống chặn vượt số còn lại và chỉ tạo tài sản theo số thực nhận.",
    },
  }[activeTab];

  function selectTab(tab) {
    setActiveTab(tab);
    setWorkflowMessage("");
    const url = new URL(window.location.href);
    url.searchParams.set("tab", tab);
    window.history.replaceState(null, "", url);
  }

  function openRequestReceipts(request) {
    const canReceive = ["DA_DAT", "NHAN_MOT_PHAN", "HOAN_TAT"].includes(request.purchaseOrderStatus);
    setReceiptOrderId(canReceive ? request.purchaseOrderId : null);
    selectTab("receipts");
    setWorkflowMessage(request.purchaseOrderNumber
      ? `Đang mở thông tin nhận hàng cho đơn ${request.purchaseOrderNumber}.`
      : "");
  }

  function openRequestOrder(request) {
    setOrderRequestFilterId(request.id);
    selectTab("orders");
    setWorkflowMessage(`Đang mở đơn mua từ yêu cầu #${request.id}.`);
  }

  function openRequestFromOrder(requestId) {
    setFocusedRequestId(requestId);
    setOrderRequestFilterId(null);
    selectTab("requests");
    setWorkflowMessage(`Đang mở yêu cầu #${requestId} liên kết với đơn mua.`);
  }

  function checkOrder(requestId) {
    if (requestId == null) {
      setOrderRequestFilterId(null);
      return;
    }
    setOrderRequestFilterId(requestId);
    selectTab("orders");
    setWorkflowMessage(`Đang kiểm tra đơn mua gắn với yêu cầu #${requestId}.`);
  }

  return (
    <>
      <section className="welcome"><div><p className="eyebrow">MUA SẮM NHÂN VIÊN</p><h1>Mua sắm nhân viên</h1>
        <p className="welcome-copy">Quản lý liền mạch yêu cầu mua sắm, đơn mua và nhập kho khi hàng được giao.</p></div></section>
      <nav className="admin-resource-tabs procurement-tabs" aria-label="Các bước mua sắm">
        {tabs.map(([id, label]) => <button key={id} type="button" className={activeTab === id ? "active" : ""}
          aria-current={activeTab === id ? "step" : undefined} onClick={() => selectTab(id)}>{label}</button>)}
      </nav>
      <section className="procurement-step-guide" aria-live="polite">
        <span className="procurement-step-number">{tabs.findIndex(([id]) => id === activeTab) + 1}</span>
        <div><h2>{activeStep.title}</h2><p>{activeStep.description}</p></div>
      </section>
      {countError && <p className="message error" role="alert">{countError}</p>}
      {workflowMessage && <p className="message" role="status">{workflowMessage}</p>}
      {activeTab === "requests" && <PurchaseRequestsPage onOpenReceipts={openRequestReceipts}
          onOpenOrder={openRequestOrder}
          focusRequestId={focusedRequestId}
          refreshKey={requestRefreshKey}
        onCreateOrder={(request) => {
          setRequestedOrder(request);
          setWorkflowMessage(`Đang tạo đơn mua từ yêu cầu của ${request.requester}.`);
        }}
          onPendingCountChange={updatePendingCount} />}
      {(activeTab === "requests" || activeTab === "orders") && <PurchaseOrdersTab
          requestedOrder={requestedOrder}
          onRequestHandled={handleRequestHandled}
          requestFilterId={orderRequestFilterId}
          onCheckOrder={checkOrder}
          onOpenRequest={openRequestFromOrder}
          showOrders={activeTab === "orders"}
          onOpenReceipts={(orderId, message) => {
            setReceiptOrderId(orderId);
            selectTab("receipts");
            setWorkflowMessage(message);
          }} />}
      {activeTab === "receipts" && <StockInPage currentUser={currentUser} embedded
        requestedOrderId={receiptOrderId} onRequestedOrderHandled={clearReceiptOrderId} />}
    </>
  );
}
