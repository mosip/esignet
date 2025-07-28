import openIDConnectService from "../../services/openIDConnectService";
import { Buffer } from "buffer";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";
import {
  walletConfigKeys,
  configurationKeys,
  modalityIconPath,
  purposeTypeObj,
} from "../../constants/clientConstants";

const mockOAuthDetails = {
  redirectUri: "https://example.com",
  transactionId: "transaction123",
  configs: {
    someConfigKey: "someConfigValue",
    [configurationKeys.walletConfig]: [
      {
        walletName: "TestWallet",
        walletLogoUrl: "logo.png",
        "deep-link-uri": "app://test",
        "download-uri": "https://download.com",
      },
    ],
    [configurationKeys.additionalConfig]: {
      purpose: {
        type: "login",
        title: "Test Title",
        subTitle: "Test Subtitle",
      },
    },
  },
  authFactors: [
    [{ type: "WLA" }],
    [{ type: "PWD", subtypes: [], count: 1 }],
    [{ type: "INVALID", subtypes: [], count: 0 }],
  ],
};

describe("openIDConnectService", () => {
  let service;

  beforeEach(() => {
    service = new openIDConnectService(
      { ...mockOAuthDetails },
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
    expect(service.getOAuthDetails()).toEqual(mockOAuthDetails);
  });

  test("getTransactionId returns the transactionId", () => {
    expect(service.getTransactionId()).toBe(mockOAuthDetails.transactionId);
  });

  test("getEsignetConfiguration returns the configuration value for the given key", () => {
    expect(service.getEsignetConfiguration("someConfigKey")).toBe(
      "someConfigValue"
    );
  });

  test("getEsignetConfiguration returns undefined for invalid configKey", () => {
    expect(service.getEsignetConfiguration("invalidConfig")).toBeUndefined();
  });

  test("encodeBase64 encodes JSON object into base64 string", () => {
    const jsonObject = { key: "value" };
    const expectedBase64 = Buffer.from(JSON.stringify(jsonObject)).toString(
      "base64"
    );
    expect(service.encodeBase64(jsonObject)).toBe(expectedBase64);
  });

  test("encodeBase64 returns base64 for empty object", () => {
    const emptyJsonObject = {};
    const expectedB64 = Buffer.from(JSON.stringify(emptyJsonObject)).toString(
      "base64"
    );
    expect(service.encodeBase64(emptyJsonObject)).toBe(expectedB64);
  });

  test("wlaToAuthfactor returns correct object", () => {
    const wla = {
      [walletConfigKeys.walletName]: "Wallet1",
      [walletConfigKeys.walletLogoUrl]: "logo1.png",
      "deep-link-uri": "app://wallet1",
      "download-uri": "https://wallet1.com",
    };
    const result = service.wlaToAuthfactor(wla);
    expect(result.label).toBe("Wallet1");
    expect(result.value.type).toBe("WLA");
    expect(result.icon).toBe("logo1.png");
    expect(result.id).toBe("login_with_wallet1");
  });

  test("toAuthfactor returns correct object for PWD", () => {
    const authFactor = [{ type: "PWD", subtypes: [], count: 1 }];
    const result = service.toAuthfactor(authFactor);
    expect(result.label).toBe("PWD");
    expect(result.value).toBe(authFactor[0]);
    expect(result.icon).toBe(modalityIconPath["PSWD"]);
    expect(result.id).toBe("login_with_pwd");
  });

  test("toAuthfactor returns correct object for unknown type", () => {
    const authFactor = [{ type: "UNKNOWN", subtypes: [], count: 1 }];
    const result = service.toAuthfactor(authFactor);
    expect(result.label).toBe("UNKNOWN");
    expect(result.icon).toBeUndefined();
    expect(result.id).toBe("login_with_unknown");
  });

  test("getAuthFactorList returns empty list if no authFactors", () => {
    service.oAuthDetails.authFactors = [];
    expect(service.getAuthFactorList()).toEqual([]);
  });

  test("getAuthFactorList returns empty list for all invalid types", () => {
    service.oAuthDetails.authFactors = [
      [{ type: "INVALID", subtypes: [], count: 1 }],
    ];
    expect(service.getAuthFactorList()).toEqual([]);
  });

  test("getPurpose returns default object if no additionalConfig", () => {
    service.oAuthDetails.configs = {};
    expect(service.getPurpose()).toEqual({
      type: purposeTypeObj.login,
      title: null,
      subTitle: null,
    });
  });

  test("getPurpose returns correct object if additionalConfig.purpose is present", () => {
    const result = service.getPurpose();
    expect(result.type).toBe("login");
    expect(result.title).toBe("Test Title");
    expect(result.subTitle).toBe("Test Subtitle");
  });

  test("getPurpose returns default if purpose type is 'none'", () => {
    service.oAuthDetails.configs[configurationKeys.additionalConfig] = {
      purpose: { type: purposeTypeObj.none, title: "X", subTitle: "Y" },
    };
    expect(service.getPurpose()).toEqual({
      type: purposeTypeObj.none,
      title: null,
      subTitle: null,
    });
  });

  test("checkPurposeObjIsNotEmpty returns true for valid object", () => {
    expect(
      service.checkPurposeObjIsNotEmpty({ title: "A" })
    ).toBe(true);
    expect(
      service.checkPurposeObjIsNotEmpty({ subTitle: "B" })
    ).toBe(true);
    expect(
      service.checkPurposeObjIsNotEmpty({ type: "C" })
    ).toBe(true);
  });

  test("checkPurposeObjIsNotEmpty returns false for empty object", () => {
    expect(service.checkPurposeObjIsNotEmpty({})).toBe(false);
    expect(service.checkPurposeObjIsNotEmpty(null)).toBe(false);
    expect(service.checkPurposeObjIsNotEmpty(undefined)).toBe(false);
  });

  test("checkTitleAndSubTitle returns null for null/undefined", () => {
    expect(service.checkTitleAndSubTitle(null)).toBeNull();
    expect(service.checkTitleAndSubTitle(undefined)).toBeNull();
  });

  test("checkTitleAndSubTitle returns value for non-null", () => {
    expect(service.checkTitleAndSubTitle("abc")).toBe("abc");
    expect(service.checkTitleAndSubTitle({ a: 1 })).toEqual({ a: 1 });
  });
});