// Runtime validation coverage for: retrying authenticated requests after access-token expiry.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchWithSession } from "./sessionFetch.js";

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

describe("fetchWithSession", () => {
  it("refreshes the session and retries a request after an expired access token", async () => {
    const dispatchEvent = vi.fn();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accessToken: "new-access",
        refreshToken: "new-refresh",
      }), { status: 200, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ result: "confirmed" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }));
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("window", { dispatchEvent });
    vi.stubGlobal("CustomEvent", class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    });
    sessionStorage.setItem("officecare.accessToken", "expired-access");
    sessionStorage.setItem("officecare.refreshToken", "valid-refresh");

    const response = await fetchWithSession("http://localhost:8081/api/thuong-mai/confirm", {
      method: "POST",
      body: JSON.stringify({ orderId: 7 }),
    });

    expect(await response.json()).toEqual({ result: "confirmed" });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("Authorization")).toBe("Bearer expired-access");
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get("Authorization")).toBe("Bearer valid-refresh");
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get("Authorization")).toBe("Bearer new-access");
    expect(sessionStorage.getItem("officecare.accessToken")).toBe("new-access");
    expect(sessionStorage.getItem("officecare.refreshToken")).toBe("new-refresh");
    expect(dispatchEvent).toHaveBeenCalledTimes(1);
  });

  it("retries with a token another request has already refreshed", async () => {
    sessionStorage.setItem("officecare.accessToken", "expired-access");
    const fetchMock = vi.fn()
      .mockImplementationOnce(async () => {
        sessionStorage.setItem("officecare.accessToken", "new-access");
        sessionStorage.setItem("officecare.refreshToken", "new-refresh");
        return new Response(null, { status: 401 });
      })
      .mockResolvedValueOnce(new Response(JSON.stringify({ result: "ok" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await fetchWithSession("http://localhost:8081/api/thuong-mai/don-hang");

    expect(await response.json()).toEqual({ result: "ok" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("Authorization")).toBe("Bearer expired-access");
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get("Authorization")).toBe("Bearer new-access");
  });

  it("preserves the refresh failure for a purchase request", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        detail: "Refresh token hết hạn.",
      }), { status: 401, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("window", { dispatchEvent: vi.fn() });
    vi.stubGlobal("CustomEvent", class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    });
    sessionStorage.setItem("officecare.accessToken", "expired-access");
    sessionStorage.setItem("officecare.refreshToken", "expired-refresh");

    await expect(fetchWithSession(
      "http://localhost:8081/api/admin/purchase-requests/12/place-order",
      { method: "POST" },
      { preserveSessionOnUnauthorized: true },
    )).rejects.toMatchObject({
      message: "Refresh token hết hạn.",
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("reports a missing refresh token for a protected purchase request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);
    sessionStorage.setItem("officecare.accessToken", "expired-access");

    await expect(fetchWithSession(
      "http://localhost:8081/api/admin/purchase-requests/12/place-order",
      { method: "POST" },
      { preserveSessionOnUnauthorized: true },
    )).rejects.toMatchObject({
      message: "Không tìm thấy refresh token. Hãy đăng nhập lại trước khi tạo đơn mua.",
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
