# Overview

Using **dynamic forms** instead of hardcoded forms during registration in **eSignet signup**, and also enhancing the **KBI form** capability.

The intention is to create an **independent UI library** to provide this feature. Both forms should follow the same form schema so that the same library could be used in both **oidc-ui** and **signup-ui**.

For more details on how to use the `json-form-builder` library, please refer to the [official documentation](https://github.com/mosip/mosip-sdk/blob/develop/json-form-builder/README.md).

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
      "label": {
        "eng": "Sample Field",
        "ara": "حقل تجريبي",
        "fra": "Champ d'exemple"
      },
      "placeholder": {
        "eng": "Enter value",
        "ara": "أدخل القيمة",
        "fra": "Entrez la valeur"
      },
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
      "id": "sampleDropdownId",
      "controlType": "dropdown",
      "label": {
        "eng": "Gender",
        "fra": "Genre",
        "ara": "جنس"
      },
      "required": false,
      "alignmentGroup": "group2"
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
  "errors": {
    "required": {
      "eng": "This field is required",
      "fra": "Ce champ est obligatoire",
      "ara": "هذه الخانة مطلوبه"
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

| Property         | Type    | Requirement   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ---------------- | ------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `alignmentGroup` | string  | Optional      | Fields with the same alignment group are placed horizontally next to each other in the UI.                                                                                                                                                                                                                                                                                                                                                                                                  |
| `controlType`    | string  | **Mandatory** | UI control type for rendering. Options: `textbox`, `date`, `dropdown`, `password`, `checkbox`, `phone`, `photo`, `file`.                                                                                                                                                                                                                                                                                                                                                                    |
| `id`             | string  | **Mandatory** | Unique identifier for the field. Used internally to map the field.                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `label`          | object  | Optional      | Multilingual field labels. Keys represent language codes (e.g., `eng`, `fra`, `ara`).                                                                                                                                                                                                                                                                                                                                                                                                       |
| `placeholder`    | object  | Optional      | Multilingual placeholders shown inside input fields before user enters data.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `required`       | boolean | Optional      | Specifies whether the field is required. If set to `true`, the user must provide a value. If set to `false`, the field can be left empty.                                                                                                                                                                                                                                                                                                                                                   |
| `type`           | string  | **Mandatory** | Type of data expected. Can be `string` for a single-language input, or `simpleType` for multilingual input where each input ID renders multiple input fields, one for each language.                                                                                                                                                                                                                                                                                                        |
| `validators`     | array   | Optional      | List of validation rules. Each validator object has the following structure:<br><br> <table><tr><th>Property</th><th>Type</th><th>Requirement</th><th>Description</th></tr><tr><td>`regex`</td><td>string</td><td>**Mandatory**</td><td>Validation pattern</td></tr><tr><td>`error`</td><td>object</td><td>**Mandatory**</td><td>Multilingual error messages</td></tr><tr><td>`langCode`</td><td>string</td><td>Optional</td><td>Language code; if `null`, applies to all</td></tr></table> |

### Allowed Values Section (optional)

| Property        | Type   | Description                                                                                                                |
| --------------- | ------ | -------------------------------------------------------------------------------------------------------------------------- |
| `allowedValues` | object | Defines predefined options for dropdowns or checkboxes. Keys represent option IDs, and values provide multilingual labels. |

### Errors Section (optional)

| Property   | Type   | Description                                              |
| ---------- | ------ | -------------------------------------------------------- |
| `required` | object | Defines multilingual error messages for required fields. |

### Language Section (mandatory)

| Property      | Type   | Description                                                                               |
| ------------- | ------ | ----------------------------------------------------------------------------------------- |
| `mandatory`   | array  | List of mandatory language codes that must be present in the schema.                      |
| `optional`    | array  | List of optional language codes that may be included if available.                        |
| `langCodeMap` | object | Bi-directional mapping between 2-letter and 3-letter language codes (e.g., `eng` ↔ `en`). |
