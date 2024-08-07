import {
  validAuthFactors,
  walletConfigKeys,
  modalityIconPath
} from "../constants/clientConstants";

const wlaToAuthfactor = (wla) => {
  return {
    label: wla[walletConfigKeys.walletName],
    value: { ...wla, type: "WLA" },
    icon: wla[walletConfigKeys.walletLogoUrl],
    id: `login_with_${wla[walletConfigKeys.walletName]
      .replace(" ", "_")
      .toLowerCase()}`,
  };
};

const toAuthfactor = (authFactor) => {
  return {
    label: authFactor[0].type,
    value: authFactor[0],
    icon: modalityIconPath[authFactor[0].type],
    id: `login_with_${authFactor[0].type.toLowerCase()}`,
  };
};

const getAllAuthFactors = (authFactors, wlaList) => {
  let loginOptions = [];
  authFactors.forEach((authFactor) => {
    const authFactorType = authFactor[0].type;
    if (validAuthFactors[authFactorType]) {
      if (authFactorType === validAuthFactors.WLA) {
        wlaList.forEach((wla) => loginOptions.push(wlaToAuthfactor(wla)));
      } else {
        loginOptions.push(toAuthfactor(authFactor));
      }
    }
  });
  return loginOptions;
};

export { wlaToAuthfactor, toAuthfactor, getAllAuthFactors };
