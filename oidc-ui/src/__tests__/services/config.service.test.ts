import { describe, it, expect, vi, beforeEach } from "vitest";
import axios from "axios";
import { fetchThemeConfig } from "../../services/config.service";

vi.mock("axios");

describe("config.service", () => {
  const mockConfig = {
    background_logo: false,
    footer: true,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches theme config from the correct URL", async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: mockConfig });

    const result = await fetchThemeConfig();

    expect(axios.get).toHaveBeenCalledTimes(1);
    expect(result).toEqual(mockConfig);
  });

  it("throws when the request fails", async () => {
    vi.mocked(axios.get).mockRejectedValue(new Error("Network error"));

    await expect(fetchThemeConfig()).rejects.toThrow("Network error");
  });
});
