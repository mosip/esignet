# Login ID configuration

Login IDs are not a `deployment.yaml` property — they are YAML anchors and `PROMPT` nodes in
`esignet-service/data/flows/flow-esignet.yaml`.

## Shipped login-ID types

| Type | Anchor | Component | `postfix` | Validation |
|---|---|---|---|---|
| UIN/VID | `prompt_uin` | `TEXT_INPUT` | _(none)_ | regex `^[0-9]+$`; length 10–16 |
| Mobile number | `prompt_mobile` | `PHONE_INPUT` | `@phone` | per-prefix regex (`+91` → 10 digits, `+855` → 9 digits) plus `^[0-9]+$`, length 9–11 |
| Email | `prompt_email` | `EMAIL_INPUT` | `@email` | regex `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$` |
| NRC ID | `prompt_nrc` | `TEXT_INPUT` | `@nrc` | none |

A fifth identifier, `policyNumber` (regex `^[0-9]+$`, length 8–50), is used only on the KBI login
branch (`prompt_kbi_details`) — not one of the four switchable types above.

Validation `message` keys (`validation.uin.numeric`, `validation.mobile.length`,
`validation.email.format`, ...) resolve through `data/i18n/<lang>.yaml`.

No env var or `deployment.yaml` key controls any of this — it is edited directly in the flow YAML
under `<DATA_DIR>/flows/`.
