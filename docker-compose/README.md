## Overview

This is the docker compose setup to run esignet UI and esignet-service with mock identity system. This is not for production use.

## I am a developer, how to setup dependent services to edit and test esignet-service?

1. Run `docker-compose up -f dependent-docker-compose.yml` to start all the dependent services.
2. Go to command line for the project root directory and run `mvn clean install -Dgpg.skip=true -DskipTests=true`
3. Add [esignet-mock-plugin.jar](../esignet-service/target/esignet-plugins/esignet-mock-plugin.jar) to esignet-service classpath in your IDE.
4. Start the [EsignetServiceApplication.java](../esignet-service/src/main/java/io/mosip/esignet/EsignetServiceApplication.java) from your IDE.
5. Import files under [postman-collection](../postman-collection) folder into your postman to test/validate OIDC flow.

## How to bring up the complete eSignet setup for a Demo?

1. Run [docker-compose.yml](docker-compose.yml) to start eSignet UI and backend service.
2. Access eSignet UI at http://localhost:3000
3. Access eSignet backend services at http://localhost:8088/v1/esignet/swagger-ui.html
4. Onboard relying party in eSignet, import files under [postman-collection](../postman-collection) folder into your postman. And invoke requests under `OIDC Client Mgmt/Mock` folder in postman. Copy the client ID in the `Create OIDC client` response.
5. Add a `SignIn with eSignet` button in the relying party website and embed [eSignet authorize URL](http://localhost:3000/authorize?nonce=ere973eieljznge2311&state=eree2311&client_id=client_id&redirect_uri=redirect_uri&scope=openid&response_type=code&acr_values=mosip:idp:acr:generated-code&claims_locales=en&ui_locales=en-IN) in the button. Make sure to replace the query parameter values in the url before embedding in the button.
6. Add a user in the mock-identity-system.
7. Now the setup is completely ready to start the OIDC flow. [Refer eSignet user guides](https://docs.esignet.io/end-user-guide) for more information.

`Note: To know more about the relying party onboard and query parameters used in the eSignet authorize URL `[refer eSignet docs](https://docs.esignet.io/integration/relying-party)

## How to add user identity in the mock-identity-system?

1. Import files under [postman-collection](../postman-collection) folder into your postman. And invoke requests under `User Mgmt/Mock` folder in postman.






