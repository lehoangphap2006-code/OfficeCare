import { describe, expect, it } from "vitest";
import { createVietQrPayload } from "./vietQr.js";

describe("createVietQrPayload", () => {
  it("creates a local VietQR payload with order reference and exact amount", () => {
    const payload = createVietQrPayload({
      bankBin: "970436",
      accountNumber: "123456789012",
      amount: 1250000,
      description: "DH-0000042",
    });

    expect(payload).toContain("38560010A0000007270126000697043601121234567890120208QRIBFTTA");
    expect(payload).toContain("DH-0000042");
    expect(payload).toContain("54071250000");
    expect(payload).toMatch(/6304[0-9A-F]{4}$/);
  });

  it("rejects invalid bank and account details", () => {
    expect(() => createVietQrPayload({
      bankBin: "9704",
      accountNumber: "123456",
      amount: 2000,
      description: "DH-0000042",
    })).toThrow("Mã BIN ngân hàng phải có đúng 6 chữ số.");

    expect(() => createVietQrPayload({
      bankBin: "970436",
      accountNumber: "abc",
      amount: 2000,
      description: "DH-0000042",
    })).toThrow("Số tài khoản phải có từ 6 đến 20 chữ số.");
  });
});
