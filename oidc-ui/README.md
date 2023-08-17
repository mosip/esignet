# Esignet UI

## Overview

This repository contains the source code for the UI of MOSIP Esignet Services.
Esignet Services are based on [OpenID specs](https://openid.net/specs/openid-connect-core-1_0.html).

Esignet UI contains the following pages:

1. Authorize
2. Login
3. Consent

- /authorize:
  This is a redirect URI that needs to be called from the relying party portal. This is the endpoint that handles the oidc authorization. For API details, refer to this [document](https://mosip.stoplight.io/docs/identity-provider/85f761d237115-authorization-endpoint).

- /login:
  It is a page that can be loaded with any one of the following components.

  - Sign in with Biometrics
  - Sign in with OTP
  - Sign in with PIN(MOCK)

  Each component allows the user to authenticate in a different way. Each component is mapped to an auth-factor constant.
  For example

  - BIO: Sign in with Biometrics
  - OTP: Sign in with OTP
  - PIN: Sign in with PIN

  Loading of the component in this page depends on the auth-factors returned from the oauth-details endpoint (auth-factors are derived based on the acr_values parameter in authorize request).

- /consent: is a page that prompts the user to provide consent to share one's details from the MOSIP to the relying party. It shows authorize scope that needs to be permitted and, essential and voluntary claims that need to be accepted or rejected.

## Build & run (for developers)

The application runs on PORT=3000 by default.

- Env variables

  - REACT_APP_SBI_CAPTURE_TIMEOUT: timeout for sbi capture endpoint
  - REACT_APP_SBI_DINFO_TIMEOUT: timeout for sbi device info endpoint
  - REACT_APP_SBI_DISC_TIMEOUT: timeout for sbi discovery endpoint
  - REACT_APP_SBI_FACE_CAPTURE_COUNT: face count for capture endpoint
  - REACT_APP_SBI_FINGER_CAPTURE_COUNT: finger count for capture endpoint
  - REACT_APP_SBI_IRIS_CAPTURE_COUNT: iris count for capture endpoint
  - REACT_APP_SBI_FACE_CAPTURE_SCORE: score for sbi face capture
  - REACT_APP_SBI_FINGER_CAPTURE_SCORE: score for sbi finger capture
  - REACT_APP_SBI_IRIS_CAPTURE_SCORE: score for sbi iris capture
  - REACT_APP_SBI_IRIS_BIO_SUBTYPES: sbi subtypes for iris
  - REACT_APP_SBI_FINGER_BIO_SUBTYPES: sbi subtypes for finger
  - REACT_APP_SBI_ENV: Value that needs to be passed into sbi /capture request's evn parameter.
  - REACT_APP_ESIGNET_API_URL: This will be internally resolved to Esignet services URL (`/v1/esignet`).
- Build and run Docker for a service:

  ```
  $ docker build -t <dockerImageName>:<tag> .
  $ docker run -it -d -p 3000:3000 <dockerImageName>:<tag>
  ```
  To host oidc ui on a context path: 
  1. Remove the location path with `/` in the nignx file and add the location with context path as below.
    ```
    location /oidc-ui {
       alias /usr/share/nginx/oidc-ui;
       try_files $uri $uri/ /oidc-ui/index.html;
    }
    ```
  2. Provide the context path in the evn variable `OIDC_UI_PUBLIC_URL` during docker run.
  ```
  $ docker build -t <dockerImageName>:<tag> .
  $ docker run -it -d -p 3000:3000 -e OIDC_UI_PUBLIC_URL='oidc-ui' <dockerImageName>:<tag>

  # The UI will be hosted on http://<domain>/oidc-ui
  ```
  

- Build and run on the local system:
  - Update ".env.development" file, add REACT_APP_ESIGNET_API_URL=<'Complete URL of Esignet Services'>
  - Start oidc-ui
    ```
    $ npm start
    ```
  - Run the browser with web-security disabled. For Google chrome the command is
    ```
    chrome.exe --user-data-dir="C://Chrome dev session" --disable-web-security
    ```
  - Open URL http://localhost:3000