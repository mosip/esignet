# Overview

Using dynamic forms instead of hardcoded forms during registration in eSignet signup and also enhancing the KBI form capability.
Intention is to create an independent UI library to provide this feature. Both forms should follow the same form schema, so that
the same library could be used in both oidc-ui and signup-ui.

## KBI form in eSignet

As of version 1.5.1, a KBI form can be configured in the properties file. The same is returned in the oauth-details endpoint
under uiConfigs field.

The same will be continued. In the future releases, when eSignet starts supporting `Multiple ID systems` in single deployment then every ID system
is expected to have its own KBI form. Based on the ID system mapped to the OIDC client, the respective ID systems KBI form will 
be returned in the oauth-details endpoint response.

Note: Assumption with `Multiple ID systems` in single deployment is every ID system will register its metadata in eSignet DB. One such
metadata is supported auth factors. If KBI is one of them, it is expected to have a KBI form mapped to the respective ID system.

## Registration form in eSignet signup

As of version 1.1.1, eSignet signup Registration form is hardcoded to capture only fullName, phoneNumber and password along with the user
consent.

We are enhancing this existing feature to dynamically load the form. Form capable of:

1. Capture text in multiple languages.
2. Render predefined values as dropdown / radio-button / checkbox.
3. Capture a photo using webcam.
4. Ability to compress the file if exceeds the acceptable size on photo capture.
5. File uploads with file size, type validations.
6. Validation of other form fields based on configured regexes.

We can define form based on the existing MOSIP's UI JSON specification. Also define extra attributes as required.

### Endpoint to give out the UI spec to render a registration form

`GET https://signup.mosip.net/v1/signup/ui-spec?version=latest`

## Structure

Below is the default MOSIP UI JSON specification, We will NOT be supporting all the field attributes.

`{
    "id": "<Unique identifier for the field>",
    "inputRequired": <true/false -> if false, its not required to take any input from end user>,
    "type": "<string/simpleType/documentType/biometricsType>",
    "minimum": <applicable for date control type only in days>,
    "maximum": <applicable for date control type only in days>,
    "description": "<Field description>",
    "label": {
            "ara": "حقل العينة",
            "fra": "Exemple de champ",
            "eng": "Sample Field"
            },
    "controlType": "textbox/fileupload/dropdown/checkbox/button/date/ageDate/html/biometrics",
    "fieldType": "<default/dynamic>",
    "format": "none",
    "validators": [{
            "type": "regex",
            "validator": "^([0-9]{10,30})$",
            "arguments": [],
            "langCode": <if null, its applicable for all languages, else validator expression can be provided for each langCode>,
            "errorCode" : "UI_100001"
            }],
    "fieldCategory": "<pvt/evidence/kyc -> determines sharing and longevity policies applicable>",
    "alignmentGroup": "<fields belonging to same alignment group are placed horizontally next to eachother>",
    "visible": {
            "engine": "MVEL",
            "expr": "identity.get('ageGroup') == 'INFANT' && (identity.get('introducerRID') == nil || identity.get('introducerRID') == empty)"
            }, //if its set to null, then the field is always displayed
    "contactType": null,
    "group": "<grouping used in update UIN process>",
    "groupLabel": {
            "eng" : "Sample group",
            "ara" : "مجموعة العينة",
            "fra" : "Groupe d'échantillons"
            },
    "changeAction": null,
    "transliterate": false,
    "templateName": "<applicable only for html fields>",
    "fieldLayout": null,
    "locationHierarchy": null,
    "conditionalBioAttributes": [{
            "ageGroup": "INFANT",
            "process": "ALL",
            "validationExpr": "face || leftEye",
            "bioAttributes": [
            "face",
            "leftEye"
            ]
            }],                
    "bioAttributes": [ //below are the supported biometric attributes
            "leftEye",
            "rightEye",
            "rightIndex",
            "rightLittle",
            "rightRing",
            "rightMiddle",
            "leftIndex",
            "leftLittle",
            "leftRing",
            "leftMiddle",
            "leftThumb",
            "rightThumb",
            "face"
            ],
    //if requiredOn is defined, its result will take the priority over "required" attribute
    "required": true,
    "requiredOn": [{
            "engine": "MVEL",
            "expr": "identity.get('ageGroup') == 'INFANT' && (identity.get('introducerRID') == nil || identity.get('introducerRID') == empty)"
            }],
    "subType": "<document types / applicant / heirarchy level names>",
    //If true mandates capture of exception photo on exception marking
    "exceptionPhotoRequired" : true
}`

Only the below attributes are supported in eSignet signup:
 
`{
    "id": "<Unique identifier for the field>",
    "inputRequired": <true/false -> if false, its not required to take any input from end user>,
    "type": "<string/simpleType/documentType/biometricsType>",
    "minimum": <applicable for date control type only in days>,
    "maximum": <applicable for date control type only in days>,
    "description": "<Field description>",
    "label": {
            "ara": "حقل العينة",
            "fra": "Exemple de champ",
            "eng": "Sample Field"
            },
    "controlType": "textbox/fileupload/dropdown/checkbox/button/date/ageDate/html/biometrics"
}`

TODO