## Overview

This is the docker compose setup to run esignet UI and esignet-service with mock identity system. This is not for production use.

## I am a developer, how to setup dependent services to edit and test esignet-service?

1. Open terminal and go to "docker-compose" folder.
2. Run `docker compose --file dependent-docker-compose.yaml up` to start all the dependent services.
3. Go to [esignet-service](../esignet-service) folder
4. Make a copy of `.env.example` and rename it as `.env`.
5. To start esignet-service, run `./make.sh run` from the command line or git bash terminal.
6. Access `http://localhost:8080/health` to check if the server is up and running.
7. Import files under [postman-collection](../postman-collection) folder into your postman to test/validate OIDC flow.

## How to bring up the complete eSignet setup for a Demo?

1. Open terminal and go to "docker-compose" folder.
2. Run `docker compose --file docker-compose.yaml up` to start eSignet UI and backend service.
3. Access eSignet UI at `http://localhost:3000`
4. Access eSignet backend services at `http://localhost:8080/health`

If you donot have relying party portal of your own, Try setting up with our [Mock relying party portal](https://github.com/mosip/esignet-mock-services/blob/release-0.14.x/docker-compose/README.md).


Now the setup is completely ready to start the OIDC flow. [Refer eSignet user guides](https://docs.esignet.io/test/end-user-guide) for more information.

`Note: To know more about the relying party onboard and query parameters used in the eSignet authorize URL `[refer eSignet docs](https://docs.esignet.io/integration/relying-party)

## How to create an OIDC client?

1. Import files under [postman-collection](../postman-collection) folder into your postman.
2. Run the **Client Management → Create client** request once. Its pre-request script generates a fresh RSA key pair and `client_id` for you and stores them in the `client_private_key` / `client_public_key` / `client_id` collection variables; every later request in the flow folder signs with that same private key.
3. See the [postman-collection README](../postman-collection/README.md#client-management) for the request body fields to set (e.g. `additionalConfig.require_pushed_authorization_requests`, `dpop_bound_access_tokens`, `authContextRefs`) before creating the client.

## How to add user identity in the mock-identity-system?

1. Import files under [postman-collection](../postman-collection) folder into your postman. And invoke requests under `User Mgmt/Mock` folder in postman.