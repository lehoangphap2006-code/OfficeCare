import { useState } from "react";
import { fetchWithSession } from "./sessionFetch.js";

export default function ChangePasswordDialog({ open, onClose, onSaved }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (!open) return null;

  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const currentPassword = String(form.get("currentPassword") ?? "");
    const newPassword = String(form.get("newPassword") ?? "");
    if (newPassword.length < 8) {
      setError("Mật khẩu mới phải có ít nhất 8 ký tự.");
      return;
    }
    if (newPassword !== form.get("confirmPassword")) {
      setError("Mật khẩu xác nhận không khớp.");
      return;
    }

    setBusy(true);
    setError("");
    try {
      const response = await fetchWithSession("http://localhost:8081/api/auth/password", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${sessionStorage.getItem("officecare.accessToken")}`,
        },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      if (!response.ok) {
        let detail = `Không thể đổi mật khẩu (HTTP ${response.status}).`;
        try {
          const result = await response.json();
          detail = result.detail || result.message || detail;
        } catch {
          // Keep the HTTP status message when the server does not return JSON.
        }
        if (response.status === 401) {
          window.dispatchEvent(new CustomEvent("officecare:unauthorized", {
            detail: {
              url: "http://localhost:8081/api/auth/password",
              method: "PUT",
              status: response.status,
              message: detail,
            },
          }));
        }
        const requestError = new Error(detail);
        requestError.status = response.status;
        throw requestError;
      }
      onClose();
      onSaved();
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog className="detail-dialog password-dialog" open aria-labelledby="password-dialog-title">
      <div className="panel-heading">
        <div><p className="eyebrow">BẢO MẬT TÀI KHOẢN</p><h2 id="password-dialog-title">Đổi mật khẩu</h2></div>
        <button className="icon-button" type="button" aria-label="Đóng" onClick={onClose}>×</button>
      </div>
      <form className="password-form" onSubmit={submit}>
        <label className="field">Mật khẩu hiện tại<input name="currentPassword" type="password" autoComplete="current-password" required /></label>
        <label className="field">Mật khẩu mới<input name="newPassword" type="password" autoComplete="new-password" minLength="8" required /></label>
        <label className="field">Nhập lại mật khẩu mới<input name="confirmPassword" type="password" autoComplete="new-password" minLength="8" required /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-actions"><button className="button secondary" type="button" onClick={onClose}>Hủy</button><button className="button" type="submit" disabled={busy}>{busy ? "Đang lưu..." : "Đổi mật khẩu"}</button></div>
      </form>
    </dialog>
  );
}
