import linkAuthService from "../../services/linkAuthService";
import { ApiService } from "../../services/api.service";
import localStorageService from "../../services/local-storageService";
import {
  LINK_AUTHORIZATION_CODE,
  LINK_CODE_GENERATE,
  LINK_STATUS,
} from "../../constants/routes"; // Import routes for assertion

// Mock the external dependencies
jest.mock("../../services/api.service");
jest.mock("../../services/local-storageService");

describe("linkAuthService", () => {
  let service;
  // Mock the openIDConnectService which is passed into the constructor
  // Ensure these are mock functions that return resolved promises
  const mockOpenIDConnectService = {
    getOauthDetailsHash: jest.fn(), // Will be mocked further in beforeEach
    getTransactionId: jest.fn(), // Will be mocked further in beforeEach
  };

  beforeEach(() => {
    // Reset mocks before each test
    jest.clearAllMocks();

    // Set up return values for each test to ensure isolation
    mockOpenIDConnectService.getOauthDetailsHash.mockResolvedValue(
      "mockedHash"
    );
    mockOpenIDConnectService.getTransactionId.mockResolvedValue(
      "mockedTransactionId"
    );
    localStorageService.getCookie.mockReturnValue("mockedXsrfToken");

    // Instantiate the service AFTER mocks are set up
    service = new linkAuthService(mockOpenIDConnectService);
  });

  describe("post_GenerateLinkCode", () => {
    it("should successfully generate a link code", async () => {
      const mockResponseData = { data: "linkCode123" };
      ApiService.post.mockResolvedValue({ data: mockResponseData });

      const transactionId = "mockTxId123";
      const result = await service.post_GenerateLinkCode(transactionId);

      // Verify that the async methods on openIDConnectService were called
      expect(
        mockOpenIDConnectService.getOauthDetailsHash
      ).toHaveBeenCalledTimes(1);
      expect(mockOpenIDConnectService.getTransactionId).toHaveBeenCalledTimes(
        1
      );

      // Assert that ApiService.post was called with the correct arguments
      expect(ApiService.post).toHaveBeenCalledWith(
        LINK_CODE_GENERATE, // Correct API route
        expect.objectContaining({
          request: {
            transactionId: transactionId,
          },
          requestTime: expect.any(String), // Expect any string for requestTime
        }),
        expect.objectContaining({
          headers: {
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": "mockedXsrfToken",
            // These should be the resolved values because the service awaits them before passing to ApiService.post
            "oauth-details-hash": "mockedHash",
            "oauth-details-key": "mockedTransactionId",
          },
        })
      );
      expect(result).toEqual(mockResponseData);
    });

    it("should handle errors when generating a link code", async () => {
      ApiService.post.mockRejectedValue(new Error("Generate Error"));
      await expect(service.post_GenerateLinkCode("tx")).rejects.toThrow(
        "Generate Error"
      );
      // Even on error, these should ideally be called before the API call attempt
      expect(
        mockOpenIDConnectService.getOauthDetailsHash
      ).toHaveBeenCalledTimes(1);
      expect(mockOpenIDConnectService.getTransactionId).toHaveBeenCalledTimes(
        1
      );
    });
  });

  describe("post_LinkStatus", () => {
    it("should successfully get link status", async () => {
      const mockResponseData = { status: "ACTIVE" };
      ApiService.post.mockResolvedValue({ data: mockResponseData });

      const transactionId = "mockTxId456";
      const linkCode = "mockLinkCode789";
      const result = await service.post_LinkStatus(transactionId, linkCode);

      expect(
        mockOpenIDConnectService.getOauthDetailsHash
      ).toHaveBeenCalledTimes(1);
      expect(mockOpenIDConnectService.getTransactionId).toHaveBeenCalledTimes(
        1
      );

      expect(ApiService.post).toHaveBeenCalledWith(
        LINK_STATUS, // Correct API route
        expect.objectContaining({
          request: {
            transactionId: transactionId,
            linkCode: linkCode,
          },
          requestTime: expect.any(String), // Expect any string for requestTime
        }),
        expect.objectContaining({
          headers: {
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": "mockedXsrfToken",
            "oauth-details-hash": "mockedHash",
            "oauth-details-key": "mockedTransactionId",
          },
        })
      );
      expect(result).toEqual(mockResponseData);
    });

    it("should handle errors when getting link status", async () => {
      ApiService.post.mockRejectedValue(new Error("Status Error"));
      await expect(service.post_LinkStatus("tx", "code")).rejects.toThrow(
        "Status Error"
      );
      expect(
        mockOpenIDConnectService.getOauthDetailsHash
      ).toHaveBeenCalledTimes(1);
      expect(mockOpenIDConnectService.getTransactionId).toHaveBeenCalledTimes(
        1
      );
    });
  });

  describe("post_AuthorizationCode", () => {
    it("should successfully get authorization code", async () => {
      const mockResponseData = { authCode: "authCodeABC" };
      ApiService.post.mockResolvedValue({ data: mockResponseData });

      const transactionId = "mockTxIdABC";
      const linkedCode = "mockLinkedCodeDEF";
      const result = await service.post_AuthorizationCode(
        transactionId,
        linkedCode
      );

      expect(
        mockOpenIDConnectService.getOauthDetailsHash
      ).toHaveBeenCalledTimes(1);
      expect(mockOpenIDConnectService.getTransactionId).toHaveBeenCalledTimes(
        1
      );

      expect(ApiService.post).toHaveBeenCalledWith(
        LINK_AUTHORIZATION_CODE, // Correct API route
        expect.objectContaining({
          request: {
            transactionId: transactionId,
            linkedCode: linkedCode,
          },
          requestTime: expect.any(String), // Expect any string for requestTime
        }),
        expect.objectContaining({
          headers: {
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": "mockedXsrfToken",
            "oauth-details-hash": "mockedHash",
            "oauth-details-key": "mockedTransactionId",
          },
        })
      );
      expect(result).toEqual(mockResponseData);
    });

    it("post_AuthorizationCode should handle errors", async () => {
      ApiService.post.mockRejectedValue(new Error("Auth Error"));
      await expect(
        service.post_AuthorizationCode("tx", "linkedCode")
      ).rejects.toThrow("Auth Error");
      expect(
        mockOpenIDConnectService.getOauthDetailsHash
      ).toHaveBeenCalledTimes(1);
      expect(mockOpenIDConnectService.getTransactionId).toHaveBeenCalledTimes(
        1
      );
    });
  });
});
