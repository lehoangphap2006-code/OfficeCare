import { useCallback, useEffect, useState } from "react";
import { adminRequest, ADMIN_USERS_URL } from "./adminApi";

const EMPLOYEES_URL = "http://localhost:8081/api/admin/nhan-vien";

export default function UsersPage({ currentUser }) {
  const [users, setUsers] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [employeeError, setEmployeeError] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError("");
    setSuccessMessage("");
    try {
      setUsers(await adminRequest());
      return true;
    } catch (loadError) {
      setError(loadError.message);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    let active = true;
    adminRequest(EMPLOYEES_URL)
      .then((rows) => { if (active) setEmployees(rows); })
      .catch((loadError) => { if (active) setEmployeeError(loadError.message); });
    return () => { active = false; };
  }, []);

  function openForm(user = null) {
    setEditingUser(user);
    setFormError("");
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditingUser(null);
    setFormError("");
  }

  async function saveUser(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const username = String(data.get("username")).trim();
    const password = String(data.get("password") ?? "");
    const role = String(data.get("role"));
    if (!username || (!editingUser && password.length < 8) || (password && password.length < 8)
      || (role === "USER" && !data.get("employeeId"))) {
      setFormError(!username ? "Tên đăng nhập không được để trống."
        : role === "USER" && !data.get("employeeId") ? "Tài khoản nhân viên phải liên kết với hồ sơ nhân viên."
          : "Mật khẩu phải có ít nhất 8 ký tự.");
      return;
    }
    setSaving(true);
    setFormError("");
    const payload = {
      username,
      fullName: String(data.get("fullName")).trim(),
      employeeId: data.get("employeeId") ? Number(data.get("employeeId")) : null,
      role,
      enabled: data.get("enabled") === "on",
      ...(password ? { password } : {}),
    };
    try {
      await adminRequest(editingUser ? `${ADMIN_USERS_URL}/${editingUser.userId}` : ADMIN_USERS_URL, {
        method: editingUser ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const isEditing = Boolean(editingUser);
      closeForm();
      const refreshed = await loadUsers();
      setSuccessMessage(refreshed
        ? isEditing ? "Đã cập nhật tài khoản." : "Đã tạo tài khoản."
        : "Đã lưu tài khoản nhưng không tải lại được danh sách.");
    } catch (saveError) {
      setFormError(saveError.message);
    } finally {
      setSaving(false);
    }
  }

  async function deleteUser(user) {
    if (!window.confirm(`Xóa tài khoản "${user.username}"? Hành động này không thể hoàn tác.`)) return;
    try {
      await adminRequest(`${ADMIN_USERS_URL}/${user.userId}`, { method: "DELETE" });
      await loadUsers();
    } catch (deleteError) {
      setError(deleteError.message);
    }
  }

  async function setUserEnabled(user, enabled) {
    if (!enabled && !window.confirm(`Khóa tài khoản "${user.username}"?`)) return;
    try {
      await adminRequest(`${ADMIN_USERS_URL}/${user.userId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: user.username,
          fullName: user.fullName ?? "",
          employeeId: user.employeeId ?? null,
          role: user.role,
          enabled,
        }),
      });
      await loadUsers();
      setSuccessMessage(enabled ? "Đã mở khóa tài khoản." : "Đã khóa tài khoản.");
    } catch (updateError) {
      setError(updateError.message);
    }
  }

  async function unlockLogin(user) {
    try {
      await adminRequest(`${ADMIN_USERS_URL}/${user.userId}/unlock`, { method: "POST" });
      await loadUsers();
      setSuccessMessage("Đã mở khóa đăng nhập và đặt lại số lần đăng nhập sai.");
    } catch (unlockError) {
      setError(unlockError.message);
    }
  }

  return (
    <>
      <section className="welcome">
        <div><p className="eyebrow">QUẢN TRỊ HỆ THỐNG</p><h1>Quản lý người dùng</h1><p className="welcome-copy">Tạo tài khoản, cập nhật vai trò và kiểm soát quyền truy cập.</p></div>
        <button className="button" type="button" onClick={() => openForm()}>＋ Thêm người dùng</button>
      </section>
      {error && <p className="message error" role="alert">{error} <button className="retry-button" type="button" onClick={loadUsers}>Thử lại</button></p>}
      {employeeError && <p className="message error" role="alert">Không thể tải hồ sơ nhân viên: {employeeError}</p>}
      {successMessage && <p className="message" role="status">{successMessage}</p>}
      {formOpen && <section className="panel admin-user-form">
        <div className="panel-heading"><div><p className="eyebrow">TÀI KHOẢN</p><h2>{editingUser ? `Cập nhật ${editingUser.username}` : "Tạo người dùng mới"}</h2></div><button className="icon-button" type="button" aria-label="Đóng biểu mẫu" onClick={closeForm}>×</button></div>
        <form onSubmit={saveUser}>
          <div className="form-grid">
            <div className="field"><label htmlFor="admin-username">Tên đăng nhập <span>*</span></label><input id="admin-username" name="username" defaultValue={editingUser?.username ?? ""} required maxLength="50" autoComplete="off" /></div>
            <div className="field"><label htmlFor="admin-password">{editingUser ? "Mật khẩu mới (để trống nếu không đổi)" : "Mật khẩu *"}</label><input id="admin-password" name="password" type="password" required={!editingUser} minLength="8" autoComplete="new-password" /></div>
            <div className="field"><label htmlFor="admin-full-name">Họ và tên <span>*</span></label><input id="admin-full-name" name="fullName" defaultValue={editingUser?.fullName ?? ""} required maxLength="150" /></div>
            <div className="field"><label htmlFor="admin-employee">Hồ sơ nhân viên</label><select id="admin-employee" name="employeeId" defaultValue={editingUser?.employeeId ?? ""}><option value="">Không liên kết</option>{employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.hoTen} · {employee.maNhanVien}</option>)}</select></div>
            <div className="field"><label htmlFor="admin-role">Vai trò</label><select id="admin-role" name="role" defaultValue={editingUser?.role ?? "USER"}><option value="USER">Nhân viên</option><option value="ADMIN">Quản trị viên</option></select></div>
            <label className="admin-enabled-field"><input type="checkbox" name="enabled" defaultChecked={editingUser?.enabled ?? true} /> Tài khoản được phép đăng nhập</label>
          </div>
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <div className="form-actions"><button className="button secondary" type="button" onClick={closeForm}>Hủy</button><button className="button" type="submit" disabled={saving}>{saving ? "Đang lưu..." : editingUser ? "Lưu thay đổi" : "Tạo tài khoản"}</button></div>
        </form>
      </section>}
      <section className="panel assets-panel admin-users-panel">
        <div className="panel-heading assets-heading"><div><h2>Danh sách tài khoản</h2><p className="muted">{users.length} tài khoản</p></div><button className="text-button" type="button" onClick={loadUsers} disabled={loading}>Làm mới</button></div>
        <div className="table-wrap" aria-busy={loading}><table><thead><tr><th>HỌ TÊN</th><th>TÊN ĐĂNG NHẬP</th><th>VAI TRÒ</th><th>HỒ SƠ NHÂN VIÊN</th><th>TRẠNG THÁI</th><th>BẢO MẬT ĐĂNG NHẬP</th><th>THAO TÁC</th></tr></thead><tbody>
          {loading ? <tr><td className="empty loading-state" colSpan="7">Đang tải danh sách người dùng...</td></tr>
            : error ? <tr><td className="empty" colSpan="7">Không thể tải danh sách.</td></tr>
              : users.length === 0 ? <tr><td className="empty" colSpan="7">Chưa có tài khoản.</td></tr>
                : users.map((user) => {
                  const locked = user.lockedUntil && new Date(user.lockedUntil) > new Date();
                  const isSelf = user.username.toLowerCase() === currentUser.username.toLowerCase();
                  return <tr key={user.userId}>
                    <td>{user.fullName || "—"}</td>
                    <td className="code">{user.username}{isSelf && <span className="admin-self-tag">Bạn</span>}</td>
                    <td>{user.role === "ADMIN" ? "Quản trị viên" : "Người dùng"}</td>
                    <td>{user.employeeName ? `${user.employeeName} · #${user.employeeId}` : "Chưa liên kết"}</td>
                    <td><span className={`status ${user.enabled ? "in-use" : "retired"}`}>{user.enabled ? "Đang hoạt động" : "Đã vô hiệu hóa"}</span></td>
                    <td>{locked ? `Đang khóa đến ${new Date(user.lockedUntil).toLocaleString("vi-VN")}` : user.failedLoginAttempts ? `${user.failedLoginAttempts} lần đăng nhập sai` : "Bình thường"}</td>
                    <td className="actions">
                      <button className="link-button" type="button" onClick={() => openForm(user)}>Sửa / đặt lại mật khẩu</button>
                      <button className="link-button" type="button" disabled={isSelf} onClick={() => setUserEnabled(user, !user.enabled)}>{user.enabled ? "Khóa" : "Mở khóa"}</button>
                      {locked && <button className="link-button" type="button" onClick={() => unlockLogin(user)}>Mở khóa đăng nhập</button>}
                      <button className="delete" type="button" disabled={isSelf} onClick={() => deleteUser(user)}>Xóa</button>
                    </td>
                  </tr>;
                })}
        </tbody></table></div>
      </section>
    </>
  );
}
