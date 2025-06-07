# Login with Purpose

It is a page that can be loaded with any one of the following components.

  - Login with ***\<AUTH_FACTOR>***
  - Verify with ***\<AUTH_FACTOR>***
  - Link using ***\<AUTH_FACTOR>***

  These Login/Verify/Link are the purpose for the authorization, whatever your need you have to specify your purpose while creating a client. You can check the `Create OIDC Client` api in this [Postman Collection](../../postman-collection/eSignet.postman_collection.json). There is a `purpose` object inside of it where you can define all the details.  

  ```ts
  "request": {
    ...
    "additionalConfig": {
      ...
      "purpose": {
        // Type of authorization, it is case-sensitive
        // valid values `login` | `verify` | `link` | `none`
        "type": "verify",
        // Title: It will appear in the screen as the title of login page,
        // It will be multilingual language map and @none when no language 
        // map is present, @none is compulsory if your providing title object
        "title": {
          "ara": "تحقق من هويتك",
          "eng": "Verify your identity",
          "hin": "अपनी पहचान सत्यापित करें",
          "kan": "ನಿಮ್ಮ ಗುರುತನ್ನು ಪರಿಶೀಲಿಸಿ",
          "tam": "உங்கள் அடையாளத்தை சரிபார்க்கவும்",
          "@none": "Verify your identity @none"
        },
        // Subtitle: It will appear in the screen below the title of login page,
        // It will also be multilingual language map and @none when no language
        // map is present, @none is compulsory if your providing subTitle object
        "subTitle": {
          "ara": "يرجى تقديم التفاصيل التالية للتحقق من هويتك",
          "eng": "Please provide the following details to verify your identity",
          "hin": "कृपया अपनी पहचान सत्यापित करने के लिए निम्नलिखित विवरण प्रदान करें",
          "khm": "ನಿಮ್ಮ ಗುರುತನ್ನು ಪರಿಶೀಲಿಸಲು ಕೆಳಗಿನ ವಿವರಗಳನ್ನು ನೀಡಿ",
          "tam": "உங்கள் அடையாளத்தை சரிபார்க்க கீழே உள்ள விவரங்களை வழங்கவும்",
          "@none": "Please provide the following details to verify your identity @none"
        }
      },
      ...
    }
    ...
  }
  ```

  If we use the above configuration then it will look like below image:

  ![Esignet Login with purpose](../esignet-login-with-purpose.png "Esignet Login with purpose")

  Each component allows the user to authenticate in a different way. Each component is mapped to an auth-factor constant.
  For example

  - BIO: Biometrics
  - OTP: OTP
  - PIN: PIN
  - PWD: Password
  - KBI: Details (Knowledge based identification)
  - WLA: Wallet based

  The initialization of the component on this page is contingent upon the authentication factors received from the oauth-details endpoint. These authentication factors are determined based on the acr_values parameter specified in the authorize request.
