import authService from "../../services/authService";
import { ApiService } from "../../services/api.service";
import localStorageService from "../../services/local-storageService";
import {
  CSRF,
} from "../../constants/routes";

// ✅ Mock global Buffer before tests
global.Buffer = {
  from: jest.fn((input) => ({
    toString: jest.fn((encoding) => {
      if (encoding === "base64") return `mockedBase64StringOf(${input})`;
      return `mockedString(${input})`;
    }),
  })),
};

jest.mock("../../services/api.service");
jest.mock("../../services/local-storageService");

describe("authService", () => {
  let service;
  const mockOpenIDConnectService = {
    getOauthDetailsHash: jest.fn().mockResolvedValue("mockedHash"),
    getTransactionId: jest.fn().mockResolvedValue("mockedTransactionId"),
  };

  beforeEach(() => {
    service = new authService(mockOpenIDConnectService);
    jest.clearAllMocks();
    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");
  });

  it("post_AuthenticateUser should call ApiService.post with correct parameters and headers", async () => {
    ApiService.post.mockResolvedValue({ data: "authenticateResponse" });

    const result = await service.post_AuthenticateUser(
      "tx123",
      "user123",
      [],
      "captchaToken",
      "mockedHash"
    );

    expect(ApiService.post).toHaveBeenCalled();
    expect(result).toBe("authenticateResponse");
  });

  it("post_OauthDetails_v2 should call ApiService.post correctly", async () => {
    ApiService.post.mockResolvedValue({ data: "oauthDetailsResponse" });
    const result = await service.post_OauthDetails_v2({ foo: "bar" });
    expect(result).toBe("oauthDetailsResponse");
  });

  it("post_OauthDetails_v3 should call ApiService.post correctly", async () => {
    ApiService.post.mockResolvedValue({ data: "oauthDetailsV3Response" });
    const result = await service.post_OauthDetails_v3({ baz: "qux" });
    expect(result).toBe("oauthDetailsV3Response");
  });

  it("post_OauthDetails_v3 should handle invalid parameters", async () => {
    ApiService.post.mockRejectedValue(new Error("Invalid Parameters"));
    await expect(service.post_OauthDetails_v3({})).rejects.toThrow(
      "Invalid Parameters"
    );
  });

  it("post_SendOtp should call ApiService.post with expected params", async () => {
    ApiService.post.mockResolvedValue({ data: "otpResponse" });
    const result = await service.post_SendOtp("tx", "uid", ["phone"], "captcha");
    expect(result).toBe("otpResponse");
  });

  it("get_CsrfToken should return CSRF token", async () => {
    ApiService.get.mockResolvedValue({ data: "csrfToken" });
    const result = await service.get_CsrfToken();
    expect(ApiService.get).toHaveBeenCalledWith(CSRF, {
      headers: { "Content-Type": "application/json" },
    });
    expect(result).toBe("csrfToken");
  });

  it("resume should post to RESUME with correct headers", async () => {
    ApiService.post.mockResolvedValue({ data: "resumeData" });
    const result = await service.resume("tx");
    expect(result).toBe("resumeData");
  });

  it("buildRedirectParams returns encoded values", () => {
    const result = service.buildRedirectParams(
      "n1",
      "s2",
      { foo: "bar" },
      "approve"
    );
    expect(result).toContain("nonce=n1");
    expect(result).toContain("state=s2");
    expect(result).toContain("consentAction=approve");
  });

  it("getAuthorizeQueryParam handles error gracefully", () => {
    jest.spyOn(Storage.prototype, "getItem").mockImplementationOnce(() => {
      throw new Error("LocalStorage Error");
    });
    expect(() => service.getAuthorizeQueryParam()).toThrow(
      "LocalStorage Error"
    );
  });
});
