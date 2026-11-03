function tlv(tag, value) {
  const length = new TextEncoder().encode(value).length;
  if (length > 99) throw new Error("Nội dung VietQR vượt quá độ dài cho phép.");
  return `${tag}${String(length).padStart(2, "0")}${value}`;
}

function crc16(input) {
  let crc = 0xffff;
  for (const byte of new TextEncoder().encode(input)) {
    crc ^= byte << 8;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = crc & 0x8000 ? (crc << 1) ^ 0x1021 : crc << 1;
      crc &= 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, "0");
}

export function createVietQrPayload({ bankBin, accountNumber, amount, description }) {
  if (!/^\d{6}$/.test(bankBin)) throw new Error("Mã BIN ngân hàng phải có đúng 6 chữ số.");
  if (!/^\d{6,20}$/.test(accountNumber)) throw new Error("Số tài khoản phải có từ 6 đến 20 chữ số.");
  if (!Number.isFinite(Number(amount)) || Number(amount) <= 0) throw new Error("Số tiền thanh toán không hợp lệ.");
  if (!description?.trim()) throw new Error("Nội dung chuyển khoản không được để trống.");

  const bankAccount = [
    tlv("00", "A000000727"),
    tlv("01", `${tlv("00", bankBin)}${tlv("01", accountNumber)}`),
    tlv("02", "QRIBFTTA"),
  ].join("");
  const additionalData = tlv("08", description.trim());
  const amountText = Number(amount).toFixed(2).replace(/\.?0+$/, "");
  const payload = [
    "000201",
    "010212",
    tlv("38", bankAccount),
    "5303704",
    tlv("54", amountText),
    "5802VN",
    tlv("62", additionalData),
    "6304",
  ].join("");
  return `${payload}${crc16(payload)}`;
}
