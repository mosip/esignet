## Overview

This is the docker compose setup to run esignet UI and esignet-service with mock identity system. This is not for production use.

## I am a developer, how to setup dependent services to edit and test esignet-service?

1. Open terminal and go to "docker-compose" folder.
2. Run `docker compose --file dependent-docker-compose.yml up` to start all the dependent services.
3. Go to [esignet-with-plugins](../esignet-with-plugins) folder and run `mvn clean install -Dgpg.skip=true` from the command line.
4. Add [esignet-mock-plugin.jar](../esignet-with-plugins/target/esignet-mock-plugin.jar) to esignet-service classpath in your IDE.
5. Start the [EsignetServiceApplication.java](../esignet-service/src/main/java/io/mosip/esignet/EsignetServiceApplication.java) from your IDE.
6. Import files under [postman-collection](../postman-collection) folder into your postman to test/validate OIDC flow.

## How to bring up the complete eSignet setup for a Demo?

1. Open terminal and go to "docker-compose" folder.
2. Run `docker compose --file docker-compose.yml up` to start eSignet UI and backend service.
3. Access eSignet UI at http://localhost:3000
4. Access eSignet backend services at http://localhost:8088/v1/esignet/swagger-ui.html
5. Onboard relying party in eSignet, import all files under [postman-collection](../postman-collection) folder into your postman. Choose `eSignet-with-mock` environment in the postman and invoke below requests under `OIDC Client Mgmt` -> `Mock` folder in postman.
   
    a. `Get CSRF token`

    b. `Create OIDC client` -> Make sure to update redirect Urls and logo URL as per your requirement in the request body.

6. Copy the client ID in the `Create OIDC client` response.
7. Add a `SignIn with eSignet` button in the relying party website and embed [eSignet authorize URL](http://localhost:3000/authorize?nonce=ere973eieljznge2311&state=eree2311&client_id=client_id&redirect_uri=redirect_uri&scope=openid&response_type=code&acr_values=mosip:idp:acr:generated-code&claims_locales=en&ui_locales=en-IN) in the button. Update the below query parameter in the eSignet authorize URL before embedding in the button.

   a. `client_id` -> value should be replace with the value copied in the step 6

   b. `redirect_uri` -> As updated in step 5

8. Add a user in the mock-identity-system. Invoke `Creat User` request under `User Mgmt` -> `Mock` folder in the postman. 
9. Now the setup is completely ready to start the OIDC flow. [Refer eSignet user guides](https://docs.esignet.io/test/end-user-guide) for more information.

`Note: To know more about the relying party onboard and query parameters used in the eSignet authorize URL `[refer eSignet docs](https://docs.esignet.io/integration/relying-party)

## How to add user identity in the mock-identity-system?

1. Import files under [postman-collection](../postman-collection) folder into your postman. And invoke requests under `User Mgmt/Mock` folder in postman.
