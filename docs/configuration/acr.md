# ACR (Authentication Context Class Reference)

ACR values use a fixed namespace, `mosip:idp:acr:<method>`, rather than free-form values.

## 1. Registerable ACR values

A client's `authContextRefs` field must contain at least one value from the allow-list
(`esignet-service/internal/clientmgmt/validate.go`). `/client-mgmt/client` — the current endpoint, for both create and update — always uses the broader `allowedACRAll`.

```go
var allowedACRAll = map[string]struct{}{
	"mosip:idp:acr:static-code":    {},
	"mosip:idp:acr:generated-code": {},
	"mosip:idp:acr:linked-wallet":  {},
	"mosip:idp:acr:biometrics":     {},
	"mosip:idp:acr:id-token":       {},
	"mosip:idp:acr:password":       {},
	"mosip:idp:acr:knowledge":      {},
}
```

Empty `authContextRefs` is rejected (`invalid_acr`). API field name: `authContextRefs`; stored as a
JSON string array in `client_detail.acr_values`.

`acr_values_supported` in the OpenID discovery document (see
[`well-known/openid-configuration.md`](well-known/openid-configuration.md)) is a separate,
narrower set — the fixed ACR→AMR map below, not this allow-list.

## 2. ACR → AMR mapping (fixed, not configurable)

Set unconditionally in `esignet-service/internal/config/app.go`'s `applyDefaults` — there is no
`deployment.yaml` key for this:

```go
cfg.OAuth.AuthClass.AcrAMR = map[string][]string{
	"mosip:idp:acr:generated-code": {},
	"mosip:idp:acr:biometrics":     {},
	"mosip:idp:acr:knowledge":      {},
	"mosip:idp:acr:password":       {},
}
cfg.OAuth.AuthClass.Amrs = []string{}
```

`static-code`, `linked-wallet`, `id-token` are registerable (§1) but absent from this map.

## 3. ACR → login-screen mapping

`esignet-service/data/flows/flow-esignet.yaml`, `prompt_acr` node's `authMethodMapping`:

```yaml
properties:
  authMethodMapping:
    "mosip:idp:acr:generated-code": acr_otp
    "mosip:idp:acr:password": acr_password
    "mosip:idp:acr:biometrics": acr_bio
    "mosip:idp:acr:knowledge": acr_kbi
```

No env var or `deployment.yaml` key controls this — it is edited directly in the flow YAML under
`<DATA_DIR>/flows/`.
