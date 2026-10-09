# Keycloak init audience mappers

`keycloak_init.py` is the eSignet Keycloak init script with support for audience protocol mappers. Issue [#2667](https://github.com/mosip/esignet/issues/2667). This change stays in eSignet. `keycloak-init-values.yaml` is unchanged.

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
