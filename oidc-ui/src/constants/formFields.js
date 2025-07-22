import configService from "../services/configService";
import { validAuthFactors, configurationKeys } from "./clientConstants";

let config = {};

export const initFormConfig = async () => {
  config = await configService();
};

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
    infoIcon: config["pin_info_icon"],
    errorCode: "IDA-MLC-002",
    prefix: "",
    postfix: "",
    maxLength: "",
    regex: "",
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
    errorCode: "invalid_pin",
    maxLength: "",
  },
];

const passwordFields = [
  {
    labelText: "uin_label_text", //translation key for password namespace
    labelFor: "Mosip Uin",
    id: "mosip-uin",
    name: "uin",
    type: "text",
    autoComplete: "uin",
    isRequired: true,
    placeholder: "uin_placeholder", //translation key for password namespace
    infoIcon: config["username_info_icon"],
    errorCode: "username_not_valid",
    prefix: "",
    postfix: "",
    maxLength: "",
    regex: "",
  },
  {
    labelText: "password_label_text",
    labelFor: "password",
    id: "password",
    name: "password",
    type: "password",
    autoComplete: "",
    isRequired: true,
    placeholder: "password_placeholder", //translation key for password namespace
    errorCode: "password_not_valid",
    maxLength: "",
    regex: "",
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
    infoIcon: config["otp_info_icon"],
    errorCode: "IDA-MLC-004",
    prefix: "",
    postfix: "",
    maxLength: "",
    regex: "",
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
      infoIcon: config["biometrics_info_icon"],
      errorCode: "IDA-MLC-004",
      prefix: "",
      postfix: "",
      maxLength: "",
      regex: "",
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

const knowledgeFields = {
  policyNumber: {
    labelText: "policyNumber_label_text",
    labelFor: "Mosip policy",
    id: "policyNumber",
    name: "policyNumber",
    type: "text",
    format: "",
    isRequired: true,
    placeholder: "policyNumber_placeholder",
    infoIcon: "",
    errorCode: "invalid_policyNumber",
    prefix: "",
    postfix: "",
    maxLength: 50,
    regex: "",
  },
  fullName: {
    labelText: "fullName_label_text",
    labelFor: "Mosip fullname",
    id: "fullName",
    name: "fullName",
    type: "text",
    format: "",
    isRequired: true,
    placeholder: "fullName_placeholder",
    infoIcon: "",
    errorCode: "invalid_fullName",
    prefix: "",
    postfix: "",
    maxLength: 25,
    regex: "",
  },
  dob: {
    labelText: "dob_label_text",
    labelFor: "Mosip dob",
    id: "dob",
    name: "dob",
    type: "text",
    format: "",
    isRequired: true,
    placeholder: "dob_placeholder",
    infoIcon: "",
    errorCode: "invalid_dob",
    prefix: "",
    postfix: "",
    maxLength: "",
    regex: "",
  },
};

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
  {
    name: "password_tab_name", //translation key for tabs namespace
    icon: "space-shuttle",
    comp: "Password",
  },
];

const generateFieldData = (fieldName, openIDConnectService) => {
  let fieldData = [];

  const knowledgeFieldList = openIDConnectService.getEsignetConfiguration(
    configurationKeys.authFactorKnowledgeFieldDetails
  );

  const prefix =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernamePrefix
    ) ?? "";

  const postfix =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernamePostfix
    ) ?? "";

  const inputType =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameInputType
    ) ?? "text";

  const userMaxLength =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameMaxLength
    ) ?? "";

  const passwordMaxLength =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.pswdMaxLength
    ) ?? "";

  const passwordRegexValue =
    openIDConnectService.getEsignetConfiguration(configurationKeys.pswdRegex) ??
    process.env.REACT_APP_PSWD_REGEX;

  const usernameRegexValue =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameRegex
    ) ?? process.env.REACT_APP_USERNAME_REGEX;

  const individualFields = {
    prefix,
    postfix,
    type: inputType,
    maxLength: userMaxLength,
    regex: usernameRegexValue,
  };

  switch (fieldName) {
    case validAuthFactors.PIN:
      fieldData = pinFields;
      Object.assign(fieldData[0], individualFields);
      fieldData[1].maxLength = passwordMaxLength;
      break;
    case validAuthFactors.OTP:
      fieldData = otpFields;
      Object.assign(fieldData[0], individualFields);
      break;
    case validAuthFactors.BIO:
      fieldData = bioLoginFields;
      Object.assign(fieldData.inputFields[0], individualFields);
      break;
    case validAuthFactors.PSWD:
      fieldData = passwordFields;
      Object.assign(fieldData[0], individualFields);
      fieldData[1].maxLength = passwordMaxLength;
      fieldData[1].regex = passwordRegexValue;
      break;
    case validAuthFactors.KBI:
      fieldData = knowledgeFieldList.map((field) => {
        return { ...knowledgeFields[field.id], ...field };
      });
      break;
  }

  return fieldData;
};

export {
  pinFields,
  otpFields,
  signupFields,
  tabList,
  bioLoginFields,
  passwordFields,
  generateFieldData,
};
