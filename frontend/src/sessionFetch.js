const AUTH_URL = "http://localhost:8081/api/auth";
let refreshInFlight;

function reportRefreshFailure(error, url) {
  window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
    detail: {
      url: `${AUTH_URL}/refresh`,
      method: "POST",
      status: error.status,
      message: error.message,
      failedRequest: url,
    },
  }));
}

export async function refreshSession() {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    const refreshToken = sessionStorage.getItem("officecare.refreshToken");
    if (!refreshToken) {
      const error = new Error("Phiên đăng nhập cần được xác thực lại.");
      error.status = 401;
      throw error;
    }

    const response = await fetch(`${AUTH_URL}/refresh`, {
      method: "POST",
      headers: { Authorization: `Bearer ${refreshToken}` },
    });
    if (!response.ok) {
      let detail = `Không thể gia hạn phiên đăng nhập (HTTP ${response.status}).`;
      try {
        const body = await response.json();
        detail = body.detail || body.message || detail;
      } catch {
        // Keep the HTTP status when the server does not return JSON.
      }
      const error = new Error(detail);
      error.status = response.status;
      throw error;
    }

    const session = await response.json();
    if (!session.accessToken || !session.refreshToken) {
      throw new Error("Máy chủ không trả về thông tin gia hạn phiên hợp lệ.");
    }
    sessionStorage.setItem("officecare.accessToken", session.accessToken);
    sessionStorage.setItem("officecare.refreshToken", session.refreshToken);
    window.dispatchEvent(new CustomEvent("officecare:token-refreshed", {
      detail: { accessToken: session.accessToken },
    }));
    return session;
  })().finally(() => {
    refreshInFlight = undefined;
  });

  return refreshInFlight;
}

async function sendWithCurrentToken(url, options) {
  const headers = new Headers(options.headers);
  const token = sessionStorage.getItem("officecare.accessToken");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  return fetch(url, { ...options, headers });
}

export async function fetchWithSession(url, options = {}, { preserveSessionOnUnauthorized = false } = {}) {
  const sentAccessToken = sessionStorage.getItem("officecare.accessToken");
  const response = await sendWithCurrentToken(url, options);
  if (response.status !== 401
      || url.endsWith("/api/auth/login")
      || url.endsWith("/api/auth/refresh")
      || url.endsWith("/api/auth/logout")) {
    return response;
  }

  const currentAccessToken = sessionStorage.getItem("officecare.accessToken");
  if (currentAccessToken && currentAccessToken !== sentAccessToken) {
    return sendWithCurrentToken(url, options);
  }

  const refreshToken = sessionStorage.getItem("officecare.refreshToken");
  if (!refreshToken) {
    const error = new Error(preserveSessionOnUnauthorized
      ? "Không tìm thấy refresh token. Hãy đăng nhập lại trước khi tạo đơn mua."
      : "Không tìm thấy refresh token. Hãy đăng nhập lại để tiếp tục.");
    error.status = 401;
    if (preserveSessionOnUnauthorized) throw error;
    reportRefreshFailure(error, url);
    throw error;
  }

  try {
    await refreshSession();
  } catch (error) {
    if (error.status === 401 && preserveSessionOnUnauthorized) throw error;
    if (error.status === 401) {
      reportRefreshFailure(error, url);
      throw error;
    }
    throw error;
  }
  return sendWithCurrentToken(url, options);
}
