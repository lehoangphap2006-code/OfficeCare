export function getTokenExpiration(token) {
  const encodedPayload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  const payload = encodedPayload.padEnd(Math.ceil(encodedPayload.length / 4) * 4, "=");
  const claims = JSON.parse(globalThis.atob(payload));
  return Number.isFinite(claims.exp) ? claims.exp * 1000 : null;
}
