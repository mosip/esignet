# Keycloak init audience mappers

`keycloak_init.py` here is the MOSIP Keycloak init script with support for audience protocol mappers. Issue [#2667](https://github.com/mosip/esignet/issues/2667).

`deploy/keycloak/keycloak-init.sh` still installs chart `mosip/keycloak-init` `12.0.2`. This directory is not read by that install. Existing realm YAML in `keycloak-init-values.yaml` is unchanged, so current eSignet installs keep the same mappers.

## Mapper YAML

A client `mappers` entry creates a protocol mapper. `protocol_mapper` selects the type. When it is omitted, the mapper stays `oidc-usermodel-attribute-mapper` and uses `mapper_user_attribute` plus `token_claim_name`.

An `oidc-audience-mapper` entry adds an audience to the access token `aud` claim. Set `included_client_audience` (another client's client id in the same realm), `included_custom_audience` (a literal audience), or both. The script sets `access.token.claim=true`.

```yaml
clients:
  - name: mosip-pms-client
    mappers:
      - mapper_name: partnerType
        mapper_user_attribute: partnerType
        token_claim_name: partnerType
      - mapper_name: esignet-audience
        protocol_mapper: oidc-audience-mapper
        included_client_audience: mosip-esignet-client
      - mapper_name: custom-audience
        protocol_mapper: oidc-audience-mapper
        included_custom_audience: https://api.example.org
    saroles: []
```

The same script change belongs in [mosip/keycloak](https://github.com/mosip/keycloak) `keycloak-init/keycloak_init.py`, which is the copy the published chart runs.
