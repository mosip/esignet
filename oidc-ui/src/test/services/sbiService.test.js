import sbiService, {
  decodeJWT,
  decodeAndValidateDeviceInfo,
  validateDeviceInfo,
} from "../../services/sbiService";

import axios from "axios";
import * as jose from "jose";
import { configurationKeys } from "../../constants/clientConstants";

// Patch global TextEncoder/TextDecoder for Node.js
import { TextEncoder, TextDecoder } from "util";
global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;

// Mocks
jest.mock("axios");
jest.mock("../../services/local-storageService", () => ({
  addDeviceInfos: jest.fn(),
  addDiscoveredDevices: jest.fn(),
  clearDeviceInfos: jest.fn(),
  clearDiscoveredDevices: jest.fn(),
}));
jest.mock("jose", () => ({
  decodeJwt: jest.fn(),
}));

describe("sbiService", () => {
  const mockOpenIDConnectService = {
    getEsignetConfiguration: jest.fn(),
  };
  const service = new sbiService(mockOpenIDConnectService);

  beforeEach(() => {
    jest.clearAllMocks();
    Object.defineProperty(window, "origin", {
      value: "http://localhost",
      writable: true,
    });
  });

  it("capture_Auth should send correct payload and return response", async () => {
    mockOpenIDConnectService.getEsignetConfiguration.mockImplementation(
      (key) => {
        const config = {
          [configurationKeys.sbiEnv]: "preprod",
          [configurationKeys.sbiCAPTURETimeoutInSeconds]: 5,
          [configurationKeys.sbiIrisBioSubtypes]: "LEFT,RIGHT",
          [configurationKeys.sbiFingerBioSubtypes]: "LEFT_INDEX,RIGHT_THUMB",
          [configurationKeys.sbiIrisCaptureCount]: 1,
          [configurationKeys.sbiIrisCaptureScore]: 80,
        };
        return config[key];
      }
    );

    axios.mockResolvedValue({ data: { success: true } });

    const result = await service.capture_Auth(
      "http://localhost",
      4501,
      "txn123",
      "1.0",
      "Iris",
      "device123"
    );

    expect(result).toEqual({ success: true });
    expect(axios).toHaveBeenCalledWith(
      expect.objectContaining({
        method: "CAPTURE",
        url: "http://localhost:4501/capture",
        headers: { "Content-Type": "application/json" },
      })
    );
  });

  it("mosipdisc_DiscoverDevicesAsync handles valid port range", async () => {
    mockOpenIDConnectService.getEsignetConfiguration.mockImplementation(
      (key) => {
        const config = {
          [configurationKeys.sbiPortRange]: "4501-4502",
          [configurationKeys.sbiDISCTimeoutInSeconds]: 1,
          [configurationKeys.sbiDINFOTimeoutInSeconds]: 1,
        };
        return config[key];
      }
    );

    // Axios for /device
    axios.mockImplementation(() =>
      Promise.resolve({ data: [{ deviceInfo: "jwt-token" }] })
    );

    // Axios.all just resolves all given promises
    axios.all = jest.fn((promises) => Promise.all(promises));

    // Mock decodeJwt for deviceInfo and digitalId
    jose.decodeJwt
      .mockResolvedValueOnce({
        certification: "L1",
        purpose: "Auth",
        deviceStatus: "Ready",
        digitalId: "digital-id-jwt",
      })
      .mockResolvedValueOnce({ digitalIdData: "mock" });

    const result = await service.mosipdisc_DiscoverDevicesAsync(
      "http://localhost"
    );

    expect(result).toBeDefined();
    expect(axios).toHaveBeenCalled();
    expect(axios.all).toHaveBeenCalled();
  });

  it("mosipdisc_DiscoverDevicesAsync falls back to default port range on invalid input", async () => {
    mockOpenIDConnectService.getEsignetConfiguration.mockImplementation(
      (key) => {
        const config = {
          [configurationKeys.sbiPortRange]: "invalid-range",
          [configurationKeys.sbiDISCTimeoutInSeconds]: 1,
          [configurationKeys.sbiDINFOTimeoutInSeconds]: 1,
        };
        return config[key];
      }
    );

    axios.mockImplementation(() =>
      Promise.resolve({ data: [{ deviceInfo: "jwt-token" }] })
    );

    axios.all = jest.fn((promises) => Promise.all(promises));

    jose.decodeJwt
      .mockResolvedValueOnce({
        certification: "L1",
        purpose: "Auth",
        deviceStatus: "Ready",
        digitalId: "token",
      })
      .mockResolvedValueOnce({ parsedDigitalId: true });

    const result = await service.mosipdisc_DiscoverDevicesAsync(
      "http://localhost"
    );

    expect(result).toBeDefined();
    expect(axios.all).toHaveBeenCalled();
  });
});
