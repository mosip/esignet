import { describe, it, expect } from "vitest";
import {
  encodeBase64Url,
  encodeBase64,
  decodeBase64,
  decodeBase64Url,
} from "../../utils/encoding";

describe("encodeBase64", () => {
  it("encodes a string to standard base64", () => {
    expect(encodeBase64("hello world")).toBe(btoa("hello world"));
  });

  it("encodes an empty string", () => {
    expect(encodeBase64("")).toBe("");
  });

  it("round-trips with decodeBase64", () => {
    const original = "test payload 123";
    expect(decodeBase64(encodeBase64(original))).toBe(original);
  });

  it("handles unicode characters without throwing", () => {
    const result = encodeBase64("hello 🌍");
    expect(typeof result).toBe("string");
    expect(result.length).toBeGreaterThan(0);
  });
});

describe("encoding utilities", () => {
  describe("encodeBase64Url", () => {
    it("encodes a simple string to base64url", () => {
      const result = encodeBase64Url("hello world");
      expect(result).toBe("aGVsbG8gd29ybGQ");
    });

    it("removes padding characters", () => {
      const result = encodeBase64Url("a");
      expect(result).not.toContain("=");
    });

    it("replaces + with - and / with _", () => {
      // A string that produces + and / in base64
      const result = encodeBase64Url("subjects?_d");
      expect(result).not.toContain("+");
      expect(result).not.toContain("/");
    });

    it("handles empty string", () => {
      expect(encodeBase64Url("")).toBe("");
    });

    it("handles unicode characters", () => {
      const result = encodeBase64Url("hello 🌍");
      expect(typeof result).toBe("string");
      expect(result.length).toBeGreaterThan(0);
    });
  });

  describe("decodeBase64", () => {
    it("decodes a base64 string", () => {
      const encoded = btoa("hello world");
      expect(decodeBase64(encoded)).toBe("hello world");
    });

    it("handles empty string", () => {
      expect(decodeBase64(btoa(""))).toBe("");
    });
  });

  describe("decodeBase64Url", () => {
    it("decodes a base64url-encoded string", () => {
      // "hello world" base64url encoded
      const result = decodeBase64Url("aGVsbG8gd29ybGQ");
      expect(result).toBe("hello world");
    });

    it("correctly handles base64url characters (- and _)", () => {
      // Encode then decode roundtrip
      const original = "test+data/here=now";
      const encoded = encodeBase64Url(original);
      const decoded = decodeBase64Url(encoded);
      expect(decoded).toBe(original);
    });
  });
});
