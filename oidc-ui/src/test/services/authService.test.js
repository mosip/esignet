import authService from "../../services/authService";
import { ApiService } from "../../services/api.service";
import localStorageService from "../../services/local-storageService";
import { Buffer } from "buffer";
import {
  AUTHENTICATE,
  OAUTH_DETAIL_V2,
  CLAIM_DETAILS,
  PREPARE_SIGNUP_REDIRECT,
  AUTHCODE
} from "../../constants/routes";

jest.mock("../../services/api.service");
jest.mock("../../services/local-storageService");
jest.mock("buffer", () => ({
  Buffer: {
    from: jest.fn().mockImplementation(() => ({
      toString: jest.fn().mockReturnValue("mockedBase64String"),
    })),
  },
}));
jest.spyOn(Buffer, "from").mockImplementation((str) => ({
  toString: jest.fn(() => "mockedBase64String"),
}));

describe("authService", () => {
  let service;
  const mockOpenIDConnectService = {
    getOauthDetailsHash: jest.fn().mockResolvedValue("mockedHash"),
    getTransactionId: jest.fn().mockResolvedValue("mockedTransactionId"),
  };

  beforeEach(() => {
    service = new authService(mockOpenIDConnectService);

    // Mock getCookie to return the expected XSRF token
    jest
      .spyOn(localStorageService, "getCookie")
      .mockReturnValue("mockedXsrfToken");

    // Mock OpenIDConnectService methods
    mockOpenIDConnectService.getOauthDetailsHash = jest
      .fn()
      .mockResolvedValue("mockedHash");
    mockOpenIDConnectService.getTransactionId = jest
      .fn()
      .mockResolvedValue("mockedTransactionId");

    jest.clearAllMocks();
  });

  it("post_AuthenticateUser should call ApiService.post with correct parameters and headers", async () => {
    const transactionId = "transactionId";
    const individualId = "individualId";
    const challengeList = [];
    const captchaToken = "captchaToken";
    const oAuthDetailsHash = "mockedHash";

    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      oAuthDetailsHash
    );
    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");

    ApiService.post.mockResolvedValue({ data: "authenticateResponse" });

    const response = await service.post_AuthenticateUser(
      transactionId,
      individualId,
      challengeList,
      captchaToken,
      oAuthDetailsHash
    );

    expect(ApiService.post).toHaveBeenCalledWith(
      AUTHENTICATE,
      {
        requestTime: expect.any(String),
        request: { transactionId, individualId, challengeList, captchaToken },
      },
      {
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "mockedXsrfToken",
          "oauth-details-hash": oAuthDetailsHash,
          "oauth-details-key": transactionId,
        }),
      }
    );

    expect(response).toBe("authenticateResponse");
  });

  it("post_AuthenticateUser should handle errors from ApiService.post", async () => {
    const transactionId = "transactionId";
    const individualId = "individualId";
    const challengeList = [];
    const captchaToken = "captchaToken";
    const oAuthDetailsHash = "mockedHash";

    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      oAuthDetailsHash
    );
    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");

    ApiService.post.mockRejectedValue(new Error("API Error"));

    await expect(
      service.post_AuthenticateUser(
        transactionId,
        individualId,
        challengeList,
        captchaToken,
        oAuthDetailsHash
      )
    ).rejects.toThrow("API Error");
  });

  it("post_OauthDetails_v2 should call ApiService.post with correct parameters and headers", async () => {
    const params = { key: "value" };

    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");

    ApiService.post.mockResolvedValue({ data: "oauthDetailsResponse" });

    const response = await service.post_OauthDetails_v2(params);

    expect(ApiService.post).toHaveBeenCalledWith(
      OAUTH_DETAIL_V2,
      {
        requestTime: expect.any(String),
        request: params,
      },
      {
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "mockedXsrfToken",
        },
      }
    );

    expect(response).toBe("oauthDetailsResponse");
  });

  it("post_OauthDetails_v2 should handle errors from ApiService.post", async () => {
    const params = { key: "value" };

    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");

    ApiService.post.mockRejectedValue(new Error("API Error"));

    await expect(service.post_OauthDetails_v2(params)).rejects.toThrow(
      "API Error"
    );
  });

  it("storeQueryParam should store the encoded query param in localStorage", () => {
    const queryParam = "testQueryParam";
    const encodedBase64 = "mockedBase64String";

    // Mock the Buffer.from implementation
    jest.spyOn(Buffer, "from").mockReturnValueOnce({
      toString: () => encodedBase64,
    });

    service.storeQueryParam(queryParam);

    expect(localStorage.getItem("authorize_query_param")).toBe(encodedBase64);

    jest.spyOn(Buffer, "from").mockRestore();
  });

  it("storeQueryParam should handle encoding errors gracefully", () => {
    jest.spyOn(Buffer, "from").mockImplementation(() => {
      throw new Error("Encoding Error");
    });

    expect(() => service.storeQueryParam("queryParam")).toThrow(
      "Encoding Error"
    );

    jest.spyOn(Buffer, "from").mockRestore();
  });

  it("getAuthorizeQueryParam should handle localStorage errors gracefully", () => {
    // Mock localStorage to throw an error
    jest.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("LocalStorage Error");
    });

    expect(() => service.getAuthorizeQueryParam()).toThrow(
      "LocalStorage Error"
    );
  });

  it("getClaimDetails should call ApiService.get with correct headers", async () => {
    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");
    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      "mockedHash"
    );

    ApiService.get.mockResolvedValue({ data: "claimDetailsResponse" });

    const response = await service.getClaimDetails();

    expect(ApiService.get).toHaveBeenCalledWith(CLAIM_DETAILS, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": "mockedXsrfToken",
        "oauth-details-hash": "mockedHash",
        "oauth-details-key": "mockedTransactionId",
      },
    });

    expect(response).toBe("claimDetailsResponse");
  });

  it("getClaimDetails should handle errors from ApiService.get", async () => {
    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");
    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      "mockedHash"
    );

    ApiService.get.mockRejectedValue(new Error("API Error"));

    await expect(service.getClaimDetails()).rejects.toThrow("API Error");
  });

  it("prepareSignupRedirect should call ApiService.post with correct parameters and headers", async () => {
    const transactionId = "transactionId";
    const pathFragment = "pathFragment";

    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");
    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      "mockedHash"
    );

    ApiService.post.mockResolvedValue({ data: "signupRedirectResponse" });

    const response = await service.prepareSignupRedirect(
      transactionId,
      pathFragment
    );

    expect(ApiService.post).toHaveBeenCalledWith(
      PREPARE_SIGNUP_REDIRECT,
      {
        requestTime: expect.any(String),
        request: { transactionId, pathFragment },
      },
      {
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "mockedXsrfToken",
          "oauth-details-hash": "mockedHash",
          "oauth-details-key": "mockedTransactionId",
        },
      }
    );

    expect(response).toBe("signupRedirectResponse");
  });

  it("prepareSignupRedirect should handle errors from ApiService.post", async () => {
    const transactionId = "transactionId";
    const pathFragment = "pathFragment";

    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");
    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      "mockedHash"
    );

    ApiService.post.mockRejectedValue(new Error("API Error"));

    await expect(
      service.prepareSignupRedirect(transactionId, pathFragment)
    ).rejects.toThrow("API Error");
  });

  it("post_OauthDetails_v2 should call ApiService.post with correct parameters", async () => {
    const params = { key: "value" };
  
    ApiService.post.mockResolvedValue({ data: "oauthDetailsResponse" });
  
    const response = await service.post_OauthDetails_v2(params);
  
    expect(ApiService.post).toHaveBeenCalledWith(
      OAUTH_DETAIL_V2,
      {
        requestTime: expect.any(String),
        request: params,
      },
      {
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "mockedXsrfToken",
        },
      }
    );
  
    expect(response).toBe("oauthDetailsResponse");
  });
  
  it("post_AuthCode should call ApiService.post with correct parameters", async () => {
    const transactionId = "transactionId";
    const acceptedClaims = ["claim1"];
    const permittedAuthorizeScopes = ["scope1"];
    const oAuthDetailsHash = "mockedHash";
  
    ApiService.post.mockResolvedValue({ data: "authCodeResponse" });
  
    const response = await service.post_AuthCode(
      transactionId,
      acceptedClaims,
      permittedAuthorizeScopes,
      oAuthDetailsHash
    );
  
    expect(ApiService.post).toHaveBeenCalledWith(
      AUTHCODE,
      {
        requestTime: expect.any(String),
        request: { transactionId, acceptedClaims, permittedAuthorizeScopes },
      },
      {
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "mockedXsrfToken",
          "oauth-details-hash": oAuthDetailsHash,
          "oauth-details-key": transactionId,
        }),
      }
    );
  
    expect(response).toBe("authCodeResponse");
  });

  it("buildRedirectParams should return correct formatted query params", () => {
    jest.spyOn(Buffer, "from").mockImplementation((str) => ({
      toString: jest.fn(() => "mockedBase64String"),
    }));
  
    const nonce = "nonce123";
    const state = "state456";
    const oauthResponse = { key: "value" };
    const consentAction = "approve";
  
    const result = service.buildRedirectParams(nonce, state, oauthResponse, consentAction);
  
    expect(Buffer.from).toHaveBeenCalledWith(JSON.stringify(oauthResponse));
    expect(result).toContain("nonce=nonce123");
    expect(result).toContain("state=state456");
    expect(result).toContain("consentAction=approve");
    expect(result).toContain("mockedBase64String"); // Mocked buffer result
  });  

  it("should handle cases where oAuthDetailsHash is not provided in post_AuthenticateUser", async () => {
    const transactionId = "transactionId";
    const individualId = "individualId";
    const challengeList = [];
    const captchaToken = "captchaToken";

    // Mock getOauthDetailsHash to return undefined or null
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(undefined);

    // Expect ApiService.post to be called with undefined for the 'oauth-details-hash' header
    ApiService.post.mockResolvedValue({ data: "authenticateResponse" });

    const response = await service.post_AuthenticateUser(
      transactionId,
      individualId,
      challengeList,
      captchaToken
    );

    expect(ApiService.post).toHaveBeenCalledWith(
      AUTHENTICATE,
      {
        requestTime: expect.any(String),
        request: { transactionId, individualId, challengeList, captchaToken },
      },
      {
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "mockedXsrfToken",
          "oauth-details-hash": undefined,
          "oauth-details-key": transactionId,
        }),
      }
    );

    expect(response).toBe("authenticateResponse");
  });

  it("post_OauthDetails_v2 should handle API failure due to server error", async () => {
    const params = { key: "value" };

    // Mock ApiService.post to simulate a server error
    ApiService.post.mockRejectedValue(new Error("Server Error"));

    await expect(service.post_OauthDetails_v2(params)).rejects.toThrow(
      "Server Error"
    );
  });

  it("post_OauthDetails_v3 should handle unexpected or invalid parameters gracefully", async () => {
    const invalidParams = {}; // Simulate invalid parameters

    // Mock ApiService.post to return an error or unexpected result
    ApiService.post.mockRejectedValue(new Error("Invalid Parameters"));

    await expect(service.post_OauthDetails_v3(invalidParams)).rejects.toThrow(
      "Invalid Parameters"
    );
  });
});
