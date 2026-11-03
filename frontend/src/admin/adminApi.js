import { fetchWithSession } from "../sessionFetch.js";

const ADMIN_USERS_URL = "http://localhost:8081/api/admin/users";

export async function adminRequest(url = ADMIN_USERS_URL, options = {}, { preserveSessionOnUnauthorized = false } = {}) {
  const token = sessionStorage.getItem("officecare.accessToken");
  if (!token) {
    const error = new Error("Chưa đăng nhập. Hãy đăng nhập lại để tiếp tục.");
    error.status = 401;
    throw error;
  }
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let response;
  try {
    response = await fetchWithSession(url, { ...options, headers }, { preserveSessionOnUnauthorized });
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error("Không kết nối được máy chủ API. Hãy kiểm tra backend đang chạy ở cổng 8081.");
    }
    throw error;
  }

  if (!response.ok) {
    let detail = `Yêu cầu thất bại (HTTP ${response.status}).`;
    let hasServerDetail = false;
    const isStocktakeEndpoint = url.includes("/api/admin/kiem-ke");
    const isAdminEndpoint = (!isStocktakeEndpoint && url.includes("/api/admin/")) || url.endsWith("/api/nhat-ky");
    const isPlaceOrderSubmission = options.method?.toUpperCase() === "POST" && url.includes("/place-order");
    if (response.status === 403 && isAdminEndpoint) {
      detail = "Tài khoản hiện tại không có quyền quản trị. Hãy đăng nhập bằng tài khoản có vai trò ADMIN.";
    }
    const responseBody = await response.text();
    try {
      const error = JSON.parse(responseBody);
      if (response.status !== 403 || !isAdminEndpoint) {
        const serverDetail = typeof error === "string"
          ? error
          : error?.detail || error?.message || error?.title || error?.error;
        if (serverDetail) {
          detail = serverDetail;
          hasServerDetail = true;
        }
      }
    } catch {
      if (responseBody.trim()) {
        detail = responseBody.trim();
        hasServerDetail = true;
      }
    }
    if (response.status === 409 && options.method?.toUpperCase() === "DELETE"
        && url.includes("/api/loai-tai-san/") && !hasServerDetail) {
      detail = "Backend đang chạy phiên bản cũ nên chưa hỗ trợ xóa danh mục có liên kết. Hãy khởi động lại backend từ mã nguồn mới rồi thử lại.";
    }
    if (response.status === 401 && preserveSessionOnUnauthorized) {
      if (detail === `Yêu cầu thất bại (HTTP ${response.status}).`) {
        detail = "Máy chủ không xác thực được phiên đăng nhập sau khi gia hạn. Hãy đăng nhập lại.";
      }
      if (isPlaceOrderSubmission) {
        detail = `${detail} Yêu cầu tạo đơn chưa được xử lý; hãy kiểm tra Yêu cầu và Nhận hàng trước khi gửi lại.`;
      }
    }
    if (response.status === 401 && !preserveSessionOnUnauthorized) {
      sessionStorage.removeItem("officecare.accessToken");
      window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
        detail: {
          url,
          method: options.method || "GET",
          status: response.status,
          message: detail,
        },
      }));
    }
    const requestError = new Error(
      response.status === 401 && preserveSessionOnUnauthorized
        ? detail
        : `${detail} (${url.replace("http://localhost:8081", "")})`,
    );
    requestError.status = response.status;
    throw requestError;
  }
  return response.status === 204 ? null : response.json();
}

export async function adminDownload(url, filename) {
  const token = sessionStorage.getItem("officecare.accessToken");
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let response;
  try {
    response = await fetchWithSession(url, { headers });
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error("Không kết nối được máy chủ API. Hãy kiểm tra backend đang chạy ở cổng 8081.");
    }
    throw error;
  }
  if (response.status === 401) {
    sessionStorage.removeItem("officecare.accessToken");
    window.dispatchEvent(new Event("officecare:unauthorized"));
  }
  if (!response.ok) {
    let detail = `Không tải được tệp (HTTP ${response.status}).`;
    try {
      const error = await response.json();
      detail = error.detail || error.message || detail;
    } catch {
      // Keep the HTTP status message when the server does not return JSON.
    }
    throw new Error(`${detail} (${url.replace("http://localhost:8081", "")})`);
  }
  const objectUrl = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

export { ADMIN_USERS_URL };
