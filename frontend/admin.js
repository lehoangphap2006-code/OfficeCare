const token = sessionStorage.getItem("officecare.accessToken");
if (!token) location.href = "index.html";

fetch("http://localhost:8081/api/auth/me", {
  headers: { Authorization: `Bearer ${token}` }
})
  .then(r => r.ok ? r.json() : Promise.reject())
  .then(me => { if (me.role !== "ADMIN") location.href = "index.html"; })
  .catch(() => location.href = "index.html");