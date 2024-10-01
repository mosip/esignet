
# [eSignet](https://docs.esignet.io/overview) Collection

This folder contains Postman collection with requests for creating and updating OIDC clients, performing authentication using oidc flow or wallet flow.

## Usage

One can [import](https://learning.postman.com/docs/getting-started/importing-and-exporting/importing-and-exporting-overview/ "Postman Docs") the following collection and the corresponding environment files in postman

[eSignet.postman_collection.json](eSignet.postman_collection.json)

To test eSignet with Mock identity system:

[esignet-with-mock.postman_environment.json](eSignet-with-mock.postman_environment.json)

To test eSignet integrated with MOSIP IDA:

[esignet-with-MOSIP.postman_environment.json](eSignet-with-MOSIP.postman_environment.json)

## Prerequisites for testing with MOSIP IDA Flow
For the client creation in the above flow the user is expected to have the following details handy

* relayingPartyId
* policyId

## Crypto Operations

This collection utilizes the [postman util lib](https://joolfe.github.io/postman-util-lib/ "Postman Util Library") for performing crypto operations like

* Key Pair Generation
* Signing
* Thumbprint Computation x5t#s256
* Client Assertion


## Overview

Overview regarding esignet is [here](../README.md "readme")

## Contributing

Pull requests are welcome. For major changes, please open an issue first
to discuss what you would like to change.