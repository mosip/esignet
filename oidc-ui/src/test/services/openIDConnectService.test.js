import openIDConnectService from "../../services/openIDConnectService";
import { Buffer } from "buffer";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";
import { sortKeysDeep } from "../../helpers/utils";

const mockOAuthDetails = {
  redirectUri: "https://example.com",
  transactionId: "transaction123",
  configs: {
    someConfigKey: "someConfigValue",
  },
  authFactors: [[{ type: "WLA" }], [{ type: "OtherFactor" }]],
};

describe("openIDConnectService", () => {
  let service;

  beforeEach(() => {
    service = new openIDConnectService(
      mockOAuthDetails,
      "nonce123",
      "state123"
    );
  });

  test("getRedirectUri returns the redirectUri", () => {
    expect(service.getRedirectUri()).toBe(mockOAuthDetails.redirectUri);
  });

  test("getNonce returns the nonce", () => {
    expect(service.getNonce()).toBe("nonce123");
  });

  test("getState returns the state", () => {
    expect(service.getState()).toBe("state123");
  });

  test("getOAuthDetails returns the OAuth details", () => {
    expect(service.getOAuthDetails()).toBe(mockOAuthDetails);
  });

  test("getTransactionId returns the transactionId", () => {
    expect(service.getTransactionId()).toBe(mockOAuthDetails.transactionId);
  });

  test("getEsignetConfiguration returns the configuration value for the given key", () => {
    expect(service.getEsignetConfiguration("someConfigKey")).toBe(
      "someConfigValue"
    );
  });

  test("encodeBase64 encodes JSON object into base64 string", () => {
    const jsonObject = { key: "value" };
    const expectedBase64 = Buffer.from(JSON.stringify(jsonObject)).toString(
      "base64"
    );
    expect(service.encodeBase64(jsonObject)).toBe(expectedBase64);
  });

  test("getOauthDetailsHash returns base64 URL encoded SHA-256 hash", async () => {
    const sha256Hash = sha256(JSON.stringify(sortKeysDeep(mockOAuthDetails)));
    let hashB64 = Base64.stringify(sha256Hash).split('=')[0];
    hashB64 = hashB64.replace(/\+/g, "-").replace(/\//g, "_");

    const response = await service.getOauthDetailsHash();
    expect(response).toBe(hashB64);
  });

  test("getEsignetConfiguration returns undefined for invalid configKey", () => {
    expect(service.getEsignetConfiguration("invalidConfig")).toBeUndefined();
  });

  test("encodeBase64 returns empty string when JSON object is empty", () => {
    const emptyJsonObject = {};
    const expectedB64 = Buffer.from(JSON.stringify(emptyJsonObject)).toString(
      "base64"
    );

    expect(service.encodeBase64(emptyJsonObject)).toBe(expectedB64);
  });

  test("getAuthFactorList returns empty list if no authFactors are present", () => {
    service.oAuthDetails.authFactors = [];
    expect(service.getAuthFactorList()).toEqual([]);
  });

  test("getAuthFactorList handles invalid authFactor type", () => {
    const invalidAuthFactor = [
      { type: "INVALID", subtypes: ["fingerprint"], count: 1 },
    ];
    service.oAuthDetails.authFactors = [invalidAuthFactor];

    expect(service.getAuthFactorList()).toEqual([]);
  });

  test("toAuthfactor returns object with undefined properties for invalid authFactor", () => {
    const invalidAuthFactor = [{ type: "INVALID", subtypes: [], count: 0 }];
    const result = service.toAuthfactor(invalidAuthFactor);

    expect(result.label).toBe("INVALID");
    expect(result.icon).toBeUndefined();
    expect(result.id).toBe("login_with_invalid");
  });
});
