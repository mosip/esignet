import axios from "axios";
import configService from "../../services/configService";
import { CONFIG } from "../../constants/routes";

jest.mock("axios");

describe("configService", () => {
  it("should fetch config and return data", async () => {
    const mockData = { env: "production" };
    axios.get.mockResolvedValue({ data: mockData });

    const result = await configService();

    expect(axios.get).toHaveBeenCalledWith(CONFIG);
    expect(result).toBe(mockData);
  });

  it("should throw error if axios.get fails", async () => {
    const error = new Error("Network error");
    axios.get.mockRejectedValue(error);

    await expect(configService()).rejects.toThrow("Network error");
  });
});
