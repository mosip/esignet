[![Maven Package upon a push](https://github.com/mosip/idp/actions/workflows/push_trigger.yml/badge.svg?branch=develop)](https://github.com/mosip/idp/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_idp&id=mosip_idp&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_idp)


# Identity Provider Project

## Overview

This repository contains the implementation of Authorization Code flow of OAuth 2.0. Supports all the mandatory features of OIDC (Open ID Connect) specification.

IdP repository contains following:

1. idp-core
2. idp-service
3. db_scripts
4. authentication-wrapper
5. audits

## Databases
Refer to [SQL scripts](db_scripts).

## Build & run (for developers)
The project requires JDK 11. 
1. Build and install:
    ```
    $ mvn clean install -Dgpg.skip=true
    ```
1. Build Docker for a service:
    ```
    $ docker build -f Dockerfile
    ```

## APIs
API documentation is available [here](https://mosip.stoplight.io/docs/identity-provider/branches/main/6f1syzijynu40-identity-provider).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

