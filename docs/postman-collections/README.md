
# [eSignet](https://docs.esignet.io/overview) Collection

This folder contains Postman collection with requests for creating and updating OIDC clients, performing authentication using oidc flow or wallet flow using esignet.

## Usage

One can [import](https://learning.postman.com/docs/getting-started/importing-and-exporting/importing-and-exporting-overview/ "Postman Docs") the following collections and the corresponding environment files in postman

Mock Ida flow:

* [Esignet Collection](./esignet-OIDC-flow-with-mock.postman_collection.json "Postman Collection")
* [Esignet Environment](./esignet-OIDC-flow-with-mock.postman_environment.json "Environment")

Real Ida Flow

* [Esignet Collection](./esignet-OIDC-flow-with-real-ida.postman_collection.json "Postman Collection")
* [Esignet Environment](./esignet-OIDC-flow-with-real-ida.postman_environment.json "Environment")


## Prerequisites for Real Ida Flow
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

Overview regarding esignet is [here](../../README.md "readme")

## Contributing

Pull requests are welcome. For major changes, please open an issue first
to discuss what you would like to change.