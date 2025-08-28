# Overview

Using **dynamic forms** instead of hardcoded forms during registration in **eSignet signup**, and also enhancing the **KBI form** capability.

The intention is to create an **independent UI library** to provide this feature. Both forms should follow the same form schema so that the same library could be used in both **oidc-ui** and **signup-ui**.

## Form JSON Specification

For reference, see the [MOSIP UI JSON specification](https://docs.mosip.io/1.2.0/id-lifecycle-management/identity-issuance/registration-client/develop/registration-client-ui-specifications#field-spec-json-template).

> **Note:** Only the **Field spec JSON template** section from the above link is applicable here.

### Supported Attributes

For **eSignet KBI** and **eSignet signup** forms, only the attributes listed below need to be supported.  
The schema is compatible with both two-letter (e.g., `en`) and three-letter (e.g., `eng`) language codes.

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

### Field Properties Section

| Property         | Type    | Description                                                                                                                                                                                                         |
| ---------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`             | string  | Unique identifier for the field. Used internally to map the field.                                                                                                                                                  |
| `required`       | boolean | Defines if the field is mandatory. `true` → must be filled by the user, `false` → optional.                                                                                                                         |
| `type`           | string  | Type of data expected. Can be `string` for a single-language input, or `simpleType` for multilingual input where each input ID renders multiple input fields, one for each language.                                |
| `label`          | object  | Multilingual field labels. Keys represent language codes (e.g., `eng`, `fra`, `ara`).                                                                                                                               |
| `placeholder`    | object  | Multilingual placeholders shown inside input fields before user enters data.                                                                                                                                        |
| `controlType`    | string  | UI control type for rendering. Options: `textbox`, `date`, `dropdown`, `password`, `checkbox`, `phone`, `photo`, `file`.                                                                                            |
| `validators`     | array   | List of validation rules. Each rule can include: <br> - `regex`: validation pattern <br> - `langCode`: specific language for error message (if `null`, applies to all) <br> - `error`: multilingual error messages. |
| `alignmentGroup` | string  | Fields with the same alignment group are placed horizontally next to each other in the UI.                                                                                                                          |

### Allowed Values Section

| Property        | Type   | Description                                                                                                                |
| --------------- | ------ | -------------------------------------------------------------------------------------------------------------------------- |
| `allowedValues` | object | Defines predefined options for dropdowns or checkboxes. Keys represent option IDs, and values provide multilingual labels. |

### Errors Section

| Property   | Type   | Description                                              |
| ---------- | ------ | -------------------------------------------------------- |
| `required` | object | Defines multilingual error messages for required fields. |

### Language Section

| Property      | Type   | Description                                                                               |
| ------------- | ------ | ----------------------------------------------------------------------------------------- |
| `mandatory`   | array  | List of mandatory language codes that must be present in the schema.                      |
| `optional`    | array  | List of optional language codes that may be included if available.                        |
| `langCodeMap` | object | Bi-directional mapping between 2-letter and 3-letter language codes (e.g., `eng` ↔ `en`). |
