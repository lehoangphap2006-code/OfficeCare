import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminRequest } from "./adminApi.js";

const sessionValues = new Map();
const sessionStore = {
  getItem: (key) => sessionValues.get(key) ?? null,
  setItem: (key, value) => sessionValues.set(key, String(value)),
  removeItem: (key) => sessionValues.delete(key),
  clear: () => sessionValues.clear(),
};

beforeEach(() => {
  sessionValues.clear();
  vi.stubGlobal("sessionStorage", sessionStore);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("adminRequest", () => {
  it("does not send an admin request without an access token", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(adminRequest("http://localhost:8081/api/admin/purchase-requests"))
      .rejects.toMatchObject({
        message: "Chưa đăng nhập. Hãy đăng nhập lại để tiếp tục.",
        status: 401,
      });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a useful server error when the response uses the standard error field", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({
      status: 400,
      error: "Bad Request",
    }), { status: 400, headers: { "Content-Type": "application/json" } })));
    sessionStorage.setItem("officecare.accessToken", "test-access");

    await expect(adminRequest("http://localhost:8081/api/admin/vat-tu/phieu-xuat", { method: "POST" }))
      .rejects.toMatchObject({
        message: expect.stringContaining("Bad Request"),
        status: 400,
      });
  });

  it("reports the rejected refresh token and original request in the unauthorized event", async () => {
    const dispatchEvent = vi.fn();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        detail: "Access token hết hạn hoặc không hợp lệ.",
      }), { status: 401, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        detail: "Phiên đăng nhập đã hết hạn hoặc không hợp lệ",
      }), { status: 401, headers: { "Content-Type": "application/json" } })));
    vi.stubGlobal("window", { dispatchEvent });
    vi.stubGlobal("CustomEvent", class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    });
    sessionStorage.setItem("officecare.accessToken", "expired-access");
    sessionStorage.setItem("officecare.refreshToken", "expired-refresh");

    await expect(adminRequest("http://localhost:8081/api/thuong-mai/don-hang"))
      .rejects.toMatchObject({ status: 401 });

    expect(dispatchEvent).toHaveBeenCalledWith(expect.objectContaining({
      type: "officecare:unauthorized",
      detail: expect.objectContaining({
        url: "http://localhost:8081/api/auth/refresh",
        failedRequest: "http://localhost:8081/api/thuong-mai/don-hang",
        message: "Phiên đăng nhập đã hết hạn hoặc không hợp lệ",
        status: 401,
      }),
    }));
  });

  it("preserves the backend error detail for a protected order request", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accessToken: "renewed-access",
        refreshToken: "renewed-refresh",
      }), { status: 200, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        detail: "Tài khoản không có quyền tạo đơn mua.",
      }), { status: 401, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const dispatchEvent = vi.fn();
    vi.stubGlobal("window", { dispatchEvent });
    vi.stubGlobal("CustomEvent", class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    });
    sessionStorage.setItem("officecare.accessToken", "test-access");
    sessionStorage.setItem("officecare.refreshToken", "test-refresh");

    await expect(adminRequest(
      "http://localhost:8081/api/admin/purchase-requests/12/place-order",
      { method: "POST" },
      { preserveSessionOnUnauthorized: true },
    )).rejects.toMatchObject({
      message: expect.stringContaining("Tài khoản không có quyền tạo đơn mua. Yêu cầu tạo đơn chưa được xử lý; hãy kiểm tra Yêu cầu và Nhận hàng trước khi gửi lại."),
      status: 401,
    });
    expect(dispatchEvent).not.toHaveBeenCalledWith(expect.objectContaining({
      type: "officecare:unauthorized",
    }));
  });

  it("does not claim an order failed when a protected list refresh returns 401", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accessToken: "renewed-access",
        refreshToken: "renewed-refresh",
      }), { status: 200, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        detail: "Thiếu access token. Hãy đăng nhập lại.",
      }), { status: 401, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("window", { dispatchEvent: vi.fn() });
    vi.stubGlobal("CustomEvent", class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    });
    sessionStorage.setItem("officecare.accessToken", "test-access");
    sessionStorage.setItem("officecare.refreshToken", "test-refresh");

    await expect(adminRequest(
      "http://localhost:8081/api/thuong-mai/don-hang",
      {},
      { preserveSessionOnUnauthorized: true },
    )).rejects.toMatchObject({
      message: "Thiếu access token. Hãy đăng nhập lại.",
      status: 401,
    });
  });

  it("does not claim an order failed when a protected list request has no backend detail", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accessToken: "renewed-access",
        refreshToken: "renewed-refresh",
      }), { status: 200, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("window", { dispatchEvent: vi.fn() });
    vi.stubGlobal("CustomEvent", class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    });
    sessionStorage.setItem("officecare.accessToken", "test-access");
    sessionStorage.setItem("officecare.refreshToken", "test-refresh");

    await expect(adminRequest(
      "http://localhost:8081/api/thuong-mai/don-hang",
      {},
      { preserveSessionOnUnauthorized: true },
    )).rejects.toMatchObject({
      message: "Máy chủ không xác thực được phiên đăng nhập sau khi gia hạn. Hãy đăng nhập lại.",
      status: 401,
    });
  });
});
