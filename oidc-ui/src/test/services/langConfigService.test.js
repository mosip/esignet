import axios from "axios";
import langConfigService from "../../services/langConfigService";
import { DEFAULT_CONFIG, ENG_CONFIG } from "../../constants/routes";

jest.mock("axios");

describe("langConfigService", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("should fetch default locale configuration", async () => {
    const mockData = { langCodeMapping: {} };
    axios.get.mockResolvedValue({ data: mockData });

    const result = await langConfigService.getLocaleConfiguration();

    expect(axios.get).toHaveBeenCalledWith(DEFAULT_CONFIG);
    expect(result).toEqual(mockData);
  });

  it("should fetch English locale configuration", async () => {
    const mockData = { lang: "en" };
    axios.get.mockResolvedValue({ data: mockData });

    const result = await langConfigService.getEnLocaleConfiguration();

    expect(axios.get).toHaveBeenCalledWith(ENG_CONFIG);
    expect(result).toEqual(mockData);
  });

  it("should return reverse language code mapping", async () => {
    const mockLangMapping = {
      langCodeMapping: {
        eng: "en",
        hin: "hi",
        tel: "te",
      },
    };
    axios.get.mockResolvedValue({ data: mockLangMapping });

    const result = await langConfigService.getLangCodeMapping();

    expect(result).toEqual({
      en: "eng",
      hi: "hin",
      te: "tel",
    });

    expect(axios.get).toHaveBeenCalledWith(DEFAULT_CONFIG);
  });

  it("should throw error if getLocaleConfiguration fails in getLangCodeMapping", async () => {
    axios.get.mockRejectedValue(new Error("Failed to load locale config"));

    await expect(langConfigService.getLangCodeMapping()).rejects.toThrow(
      "Failed to load locale config"
    );
  });
});
