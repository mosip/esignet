const pinFields = [
  {
    labelText: "uin_label_text", //translation key for pin namespace
    labelFor: "Mosip Uin",
    id: "mosip-uin",
    name: "uin",
    type: "text",
    autoComplete: "uin",
    isRequired: true,
    placeholder: "uin_placeholder", //translation key for pin namespace
  },
  {
    labelText: "pin_label_text",
    labelFor: "pin",
    id: "pin",
    name: "pin",
    type: "password",
    autoComplete: "",
    isRequired: true,
    placeholder: "pin_placeholder", //translation key for pin namespace
  },
];

const otpFields = [
  {
    labelText: "vid_label_text",
    labelFor: "Mosip vid",
    id: "mosip-vid",
    name: "vid",
    type: "text",
    autoComplete: "vid",
    isRequired: true,
    placeholder: "vid_placeholder",
  },
];

const bioLoginFields = {
  inputFields: [
    {
      labelText: "vid_label_text",
      labelFor: "Mosip vid",
      id: "mosip-vid",
      name: "vid",
      type: "text",
      autoComplete: "vid",
      isRequired: true,
      placeholder: "vid_placeholder", //translation key for l1biometric namespace
    },
  ],
};

const signupFields = [
  {
    labelText: "Username",
    labelFor: "username",
    id: "username",
    name: "username",
    type: "text",
    autoComplete: "username",
    isRequired: true,
    placeholder: "Username",
  },
  {
    labelText: "Vid",
    labelFor: "mosip-vid",
    id: "mosip-vid",
    name: "vid",
    type: "text",
    autoComplete: "vid",
    isRequired: true,
    placeholder: "Mosip vid",
  },
  {
    labelText: "Password",
    labelFor: "password",
    id: "password",
    name: "password",
    type: "password",
    autoComplete: "current-password",
    isRequired: true,
    placeholder: "Password",
  },
  {
    labelText: "Confirm Password",
    labelFor: "confirm-password",
    id: "confirm-password",
    name: "confirm-password",
    type: "password",
    autoComplete: "confirm-password",
    isRequired: true,
    placeholder: "Confirm Password",
  },
];

//TODO fetch tablist from oidcDetails response
const tabList = [
  {
    name: "pin_tab_name", //translation key for tabs namespace
    icon: "space-shuttle",
    comp: "PIN",
  },
  {
    name: "inji_tab_name", //translation key for tabs namespace
    icon: "cog",
    comp: "QRCode",
  },
  {
    name: "biometric_tab_name", //translation key for tabs namespace
    icon: "bio_capture",
    comp: "Biometrics",
  },
];

export { pinFields, otpFields, signupFields, tabList, bioLoginFields };
