import {
  wlaToAuthfactor,
  toAuthfactor,
  getAllAuthFactors,
} from "../../services/walletService";

import {
  validAuthFactors,
  walletConfigKeys,
  modalityIconPath,
} from "../../constants/clientConstants";

describe("Auth Factor Utilities", () => {
  describe("wlaToAuthfactor", () => {
    it("should return correctly formatted WLA object", () => {
      const wla = {
        [walletConfigKeys.walletName]: "MOSIP Wallet",
        [walletConfigKeys.walletLogoUrl]: "https://logo.png",
        extraProp: "foo",
      };

      const result = wlaToAuthfactor(wla);

      expect(result).toEqual({
        label: "MOSIP Wallet",
        value: { ...wla, type: "WLA" },
        icon: "https://logo.png",
        id: "login_with_mosip_wallet",
      });
    });
  });

  describe("toAuthfactor", () => {
    it("should return object for PWD type with correct icon", () => {
      const authFactor = [{ type: "PWD", data: "abc" }];

      const result = toAuthfactor(authFactor);

      expect(result).toEqual({
        label: "PWD",
        value: authFactor[0],
        icon: modalityIconPath["PSWD"],
        id: "login_with_pwd",
      });
    });

    it("should return object for non-PWD type with correct icon", () => {
      const authFactor = [{ type: "BIO", data: "xyz" }];

      const result = toAuthfactor(authFactor);

      expect(result).toEqual({
        label: "BIO",
        value: authFactor[0],
        icon: modalityIconPath["BIO"],
        id: "login_with_bio",
      });
    });
  });

  describe("getAllAuthFactors", () => {
    const wlaList = [
      {
        [walletConfigKeys.walletName]: "MOSIP Wallet",
        [walletConfigKeys.walletLogoUrl]: "https://logo.png",
      },
    ];

    it("should return correct options for WLA type", () => {
      const authFactors = [[{ type: validAuthFactors.WLA }]];
      const result = getAllAuthFactors(authFactors, wlaList);

      expect(result).toHaveLength(1);
      expect(result[0].label).toBe("MOSIP Wallet");
    });

    it("should return correct options for PWD type", () => {
      const authFactors = [[{ type: "PWD" }]];
      const result = getAllAuthFactors(authFactors, wlaList);

      expect(result).toHaveLength(1);
      expect(result[0].label).toBe("PWD");
    });

    it("should return correct options for validAuthFactors types", () => {
      const authFactors = [[{ type: validAuthFactors.BIO }]];
      const result = getAllAuthFactors(authFactors, wlaList);

      expect(result).toHaveLength(1);
      expect(result[0].label).toBe("BIO");
    });

    it("should skip unsupported types", () => {
      const authFactors = [[{ type: "UNSUPPORTED" }]];
      const result = getAllAuthFactors(authFactors, wlaList);

      expect(result).toEqual([]);
    });
  });
});
