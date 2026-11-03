// Runtime validation coverage for: JWT session expiration parsing.
import { describe, expect, it } from "vitest";
import { getTokenExpiration } from "./authToken.js";

function createToken(claims) {
  const payload = btoa(JSON.stringify(claims)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `header.${payload}.signature`;
}

describe("getTokenExpiration", () => {
  it("returns the JWT expiration in milliseconds", () => {
    expect(getTokenExpiration(createToken({ exp: 1_800_000_000 }))).toBe(1_800_000_000_000);
  });

  it("returns null when the expiration claim is missing or invalid", () => {
    expect(getTokenExpiration(createToken({ sub: "admin" }))).toBeNull();
    expect(getTokenExpiration(createToken({ exp: "later" }))).toBeNull();
  });
});
