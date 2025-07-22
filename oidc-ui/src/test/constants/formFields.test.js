import { generateFieldData, initFormConfig } from "../../constants/formFields";
import {
  validAuthFactors,
  configurationKeys,
} from "../../constants/clientConstants";
import configService from "../../services/configService"; // Import the actual configService

// Mock configService
jest.mock("../../services/configService", () =>
  jest.fn(() => ({
    pin_info_icon: "pin_info_icon_url",
    username_info_icon: "username_info_icon_url",
    otp_info_icon: "otp_info_icon_url",
    biometrics_info_icon: "biometrics_info_icon_url",
  }))
);

describe("Auth field constants and generator", () => {
  // Use a deep clone utility to prevent mutation of test data
  const cloneDeep = (obj) => JSON.parse(JSON.stringify(obj));

  // Before all tests, initialize the config
  beforeAll(async () => {
    await initFormConfig();
  });

  // Mock config values for OpenIDConnectService
  let mockValues;
  let mockOpenIDConnectService;

  beforeEach(() => {
    // Reset mock values for each test to ensure isolation
    mockValues = {
      [configurationKeys.authFactorKnowledgeFieldDetails]: [
        { id: "policyNumber" },
        { id: "fullName" },
        { id: "dob" },
      ],
      [configurationKeys.usernamePrefix]: "+91",
      [configurationKeys.usernamePostfix]: "id",
      [configurationKeys.usernameInputType]: "text",
      [configurationKeys.usernameMaxLength]: 12,
      [configurationKeys.pswdMaxLength]: 8,
      [configurationKeys.pswdRegex]: "^.*$",
      [configurationKeys.usernameRegex]: "^[a-zA-Z0-9]+$",
    };

    mockOpenIDConnectService = {
      getEsignetConfiguration: jest.fn((key) => mockValues[key]),
    };
  });

  it("should generate knowledge-based input fields properly", () => {
    const result = generateFieldData(
      validAuthFactors.KBI,
      mockOpenIDConnectService
    );
    expect(result.map((f) => f.id)).toEqual([
      "policyNumber",
      "fullName",
      "dob",
    ]);
    expect(result).toHaveLength(3);
    expect(result[0].labelText).toBe("policyNumber_label_text");
    expect(result[1].labelText).toBe("fullName_label_text");
    expect(result[2].labelText).toBe("dob_label_text");
  });

  it("should return an empty array for an unsupported auth factor", () => {
    const result = generateFieldData(
      "UNKNOWN_FACTOR",
      mockOpenIDConnectService
    );
    expect(result).toEqual([]);
  });

  // Test cases for default values when config is undefined/null
  describe("when configuration values are undefined or null", () => {
    beforeEach(() => {
      // Set all relevant config keys to undefined to test fallbacks
      mockValues = {
        [configurationKeys.authFactorKnowledgeFieldDetails]: undefined,
        [configurationKeys.usernamePrefix]: undefined,
        [configurationKeys.usernamePostfix]: null, // Test with null too
        [configurationKeys.usernameInputType]: undefined,
        [configurationKeys.usernameMaxLength]: undefined,
        [configurationKeys.pswdMaxLength]: undefined,
        [configurationKeys.pswdRegex]: undefined,
        [configurationKeys.usernameRegex]: undefined,
      };

      // Mock process.env for regex fallbacks
      process.env.REACT_APP_PSWD_REGEX = "default_pswd_regex";
      process.env.REACT_APP_USERNAME_REGEX = "default_username_regex";

      mockOpenIDConnectService = {
        getEsignetConfiguration: jest.fn((key) => mockValues[key]),
      };
    });

    afterEach(() => {
      // Clean up mock process.env
      delete process.env.REACT_APP_PSWD_REGEX;
      delete process.env.REACT_APP_USERNAME_REGEX;
    });

    it("should use default values for password fields when config is undefined/null", () => {
      const result = generateFieldData(
        validAuthFactors.PSWD,
        mockOpenIDConnectService
      );

      expect(result[0].prefix).toBe("");
      expect(result[0].postfix).toBe("");
      expect(result[0].type).toBe("text");
      expect(result[0].maxLength).toBe("");
      expect(result[0].regex).toBe("default_username_regex");

      expect(result[1].maxLength).toBe("");
      expect(result[1].regex).toBe("default_pswd_regex"); // Fallback to process.env
    });

    it("should use default values for otp fields when config is undefined/null", () => {
      const result = generateFieldData(
        validAuthFactors.OTP,
        mockOpenIDConnectService
      );

      expect(result[0].prefix).toBe("");
      expect(result[0].postfix).toBe("");
      expect(result[0].type).toBe("text");
      expect(result[0].maxLength).toBe("");
      expect(result[0].regex).toBe("default_username_regex");
    });

    it("should use default values for biometric fields when config is undefined/null", () => {
      const result = generateFieldData(
        validAuthFactors.BIO,
        mockOpenIDConnectService
      );

      expect(result.inputFields[0].prefix).toBe("");
      expect(result.inputFields[0].postfix).toBe("");
      expect(result.inputFields[0].type).toBe("text");
      expect(result.inputFields[0].maxLength).toBe("");
      expect(result.inputFields[0].regex).toBe("default_username_regex");
    });
  });
});
