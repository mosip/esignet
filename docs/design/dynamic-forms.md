# Overview

Using **dynamic forms** instead of hardcoded forms during registration in **eSignet signup**, and also enhancing the **KBI form** capability.

The intention is to create an **independent UI library** to provide this feature. Both forms should follow the same form schema so that the same library could be used in both **oidc-ui** and **signup-ui**.

For more details on how to use the `json-form-builder` library, please refer to the [official documentation](https://github.com/mosip/mosip-sdk/blob/release-0.12.x/json-form-builder/README.md).

## Form JSON Specification

For reference, see the [MOSIP UI JSON specification](https://docs.mosip.io/1.2.0/id-lifecycle-management/identity-issuance/registration-client/develop/registration-client-ui-specifications#field-spec-json-template).

> **Note:** Only the **Field spec JSON template** section from the above link is applicable here.

### Supported Attributes

For **eSignet KBI** and **eSignet signup** forms, only the attributes listed below need to be supported.  
The schema is compatible with both two-letter (e.g., `en`) and three-letter (e.g., `eng`) language codes.

### Configuring KBI (Knowledge-Based Input)

KBI can be configured in **eSignet** using the following properties:

1. MOSIP_ESIGNET_AUTHENTICATOR_DEFAULT_AUTH_FACTOR_KBI_FIELD_DETAILS_URL

   - Description: URL pointing to the raw JSON schema defining the KBI field details.
   - The schema must include the fields, their types, validation rules, and multilingual labels used for KBI authentication.
   - Example:
     `https://example.com/path/to/kbi_schema.json`

2. MOSIP_ESIGNET_AUTHENTICATOR_DEFAULT_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD
   - Description: The `id` of one of the fields defined in the KBI form schema.
   - This ensures the system correctly identifies the individual for whom the KBI fields are being captured.
   - Example:
     `sampleInputId`

## 📄 Schema Structure

```json
{
  "schema": [
    {
      "id": "sampleInputId",
      "required": true,
      "type": "string",
      "labelName": {
        "eng": "Sample Field",
        "ara": "حقل تجريبي",
        "fra": "Champ d'exemple"
      },
      "placeholder": {
        "eng": "Enter value",
        "ara": "أدخل القيمة",
        "fra": "Entrez la valeur"
      },
      "info": {
        "eng": "You have to input some text in this field",
        "ara": "عليك إدخال بعض النصوص في هذا الحقل",
        "fra": "Vous devez saisir du texte dans ce champ"
      },
      "capsLockCheck": {
        "eng": "Caps lock is on",
        "ara": "زر Caps lock قيد التشغيل",
        "fra": "La touche Verr Maj est activée"
      },
      "cssClasses": "sample-input-field",
      "controlType": "textbox",
      "validators": [
        {
          "regex": "^[a-zA-Z0-9]+$",
          "langCode": null,
          "error": {
            "eng": "Special characters are not allowed",
            "ara": "لا يُسمح باستخدام الأحرف الخاصة",
            "fra": "Les caractères spéciaux ne sont pas autorisés"
          }
        }
      ],
      "alignmentGroup": "group1"
    },
    {
      "id": "gender",
      "controlType": "dropdown",
      "labelName": {
        "eng": "Gender",
        "fra": "Genre",
        "ara": "جنس"
      },
      "required": false,
      "alignmentGroup": "group2"
    },
    {
      "id": "samplePhone",
      "controlType": "phone",
      "disabled": true,
      "required": false,
      "prefix": ["+91"],
      "labelName": {
        "eng": "Phone Number",
        "ara": "رقم الهاتف",
        "fra": "Numéro de téléphone"
      },
      "placeholder": {
        "eng": "Enter you phone number",
        "ara": "أدخل رقم هاتفك",
        "fra": "Entrez votre numéro de téléphone"
      }
    }
  ],
  "allowedValues": {
    "gender": {
      "male": {
        "eng": "Male",
        "fra": "Masculin",
        "ara": "ذكر"
      },
      "female": {
        "eng": "Female",
        "fra": "Féminin",
        "ara": "أنثى"
      },
      "other": {
        "eng": "Other",
        "fra": "Autre",
        "ara": "آخر"
      }
    }
  },
  "i18nValues": {
    "errors": {
      "required": {
        "eng": "This field is required",
        "fra": "Ce champ est obligatoire",
        "ara": "هذه الخانة مطلوبه"
      },
      "capsLock": {
        "eng": "Caps Lock is enabled",
        "fra": "Verr Maj est activé",
        "ara": "تم تفعيل مفتاح الحروف الكبيرة"
      }
    }
  },
  "language": {
    "mandatory": ["eng"],
    "optional": ["fra", "ara"],
    "langCodeMap": {
      "eng": "en",
      "fra": "fr",
      "ara": "ar"
    }
  }
}
```

## 📘 Schema Properties

The schema consists of the following properties:

### Field Properties Section (mandatory)

| Property            | Type     | Requirement   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ------------------- | -------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `alignmentGroup`    | string   | Optional      | Fields with the same alignment group are placed horizontally next to each other in the UI.                                                                                                                                                                                                                                                                                                                                                                                                  |
| `capsLockCheck`     | boolean  | Optional      | It enable a caps lock indication in top right corner(or top left corner if in rtl direction).                                                                                                                                                                                                                                                                                                                                                                                               |
| `controlType`       | string   | **Mandatory** | UI control type for rendering. Options: `textbox`, `date`, `dropdown`, `password`, `checkbox`, `phone`, `photo`.                                                                                                                                                                                                                                                                                                                                                              |
| `cssClasses`        | string   | Optional      | External css class which can be added to the component.                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `disabled`          | boolean  | Optional      | By enabling this, it will disable that field. By default it will be `false`.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `id`                | string   | **Mandatory** | Unique identifier for the field. Used internally to map the field.                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `info`              | object   | Optional      | It will create an info icon beside the label of the component, to show some info in the tooltip. It will be a multilingual fields and keys represent with language codes.                                                                                                                                                                                                                                                                                                                   |
| `labelName`             | object   | **Mandatory** | Multilingual field labels. Keys represent language codes (e.g., `eng`, `fra`, `ara`).                                                                                                                                                                                                                                                                                                                                                                                                       |
| `placeholder`       | object   | Optional      | Multilingual placeholders shown inside input fields before user enters data.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `prefix`            | string[] | Optional      | Multiple or single prefix for phone component, so that it can be selected as per the needs, it will work only when controlType is `phone`                                                                                                                                                                                                                                                                                                                                                   |
| `required`          | boolean  | Optional      | Specifies whether the field is required. If set to `true`, the user must provide a value. If set to `false`, the field can be left empty.                                                                                                                                                                                                                                                                                                                                                   |
| `type`              | string   | Optional      | Type of data expected. Can be `string` for a single-language input, or `simpleType` for multilingual input where each input ID renders multiple input fields, one for each language.                                                                                                                                                                                                                                                                                                        |
| `validators`        | array    | Optional      | List of validation rules. Each validator object has the following structure:<br><br> <table><tr><th>Property</th><th>Type</th><th>Requirement</th><th>Description</th></tr><tr><td>`regex`</td><td>string</td><td>**Mandatory**</td><td>Validation pattern</td></tr><tr><td>`error`</td><td>object</td><td>**Mandatory**</td><td>Multilingual error messages</td></tr><tr><td>`langCode`</td><td>string</td><td>Optional</td><td>Language code; if `null`, applies to all</td></tr></table> |

### Allowed Values Section (optional)

| Property        | Type   | Description                                                                                                                |
| --------------- | ------ | -------------------------------------------------------------------------------------------------------------------------- |
| `allowedValues` | object | Defines predefined options for dropdowns or checkboxes. Keys represent option IDs, and values provide multilingual labels. |

### i18nValues Section (optional)
#### It contains errors, additional labels & placeholders
Errors Section

| Property           | Type   | Description                                                       |
| ------------------ | ------ | ----------------------------------------------------------------- |
| `required`         | object | Defines multilingual error messages for required fields.          |
| `passwordMismatch` | object | Defines multilingual error messages for password mismatch.        |
| `capsLock` | object | Defines multilingual error messages for caps lock enabled.       |



### Language Section (mandatory)

| Property      | Type   | Description                                                                               |
| ------------- | ------ | ----------------------------------------------------------------------------------------- |
| `mandatory`   | array  | List of mandatory language codes that must be present in the schema.                      |
| `optional`    | array  | List of optional language codes that may be included if available.                        |
| `langCodeMap` | object | Bi-directional mapping between 2-letter and 3-letter language codes (e.g., `eng` ↔ `en`). |
