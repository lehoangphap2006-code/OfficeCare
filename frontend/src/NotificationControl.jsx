export default function NotificationControl({
  notifications,
  error,
  open,
  onToggle,
  onMarkRead,
  onRefresh,
}) {
  const unreadCount = notifications.filter((item) => !item.read).length;

  return (
    <div className="notification-control">
      <button className="toolbar-icon notification-button" type="button"
        onClick={onToggle}
        aria-label={`Thông báo${unreadCount ? `, ${unreadCount} chưa đọc` : ""}`}
        aria-expanded={open} title="Thông báo">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></svg>
        {unreadCount > 0 && <span className="notification-count">{unreadCount}</span>}
      </button>
      {open && <section className="notification-popover" aria-label="Thông báo gần đây">
        <div className="notification-heading">
          <strong>Thông báo</strong>
          <button className="text-button" type="button" onClick={onMarkRead} disabled={!unreadCount}>
            Đánh dấu đã xem
          </button>
        </div>
        {error
          ? <p className="notification-empty notification-error" role="alert">
            Không thể tải thông báo: {error}<br />
            <button className="text-button" type="button" onClick={onRefresh}>Thử lại</button>
          </p>
          : notifications.length === 0
            ? <p className="notification-empty">Chưa có thông báo.</p>
            : <ul>{notifications.map((item) => <li className={!item.read ? "notification-unread" : ""} key={item.id}>
              <p>{item.message}</p>
              <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString("vi-VN")}</time>
            </li>)}</ul>}
      </section>}
    </div>
  );
}
