# Overview

In an OIDC flow, consent is typically the user’s approval for a client (application) to access certain resources or data
(scopes) on their behalf via eSignet.

eSignet will re-prompt for consent if the stored consent expires or if the client requests additional scopes later.On save
of fresh user consent existing consent entry is deleted from the consent table, and a copy of the deleted entry is inserted in
the consent_history table.

**Note:** On wallet-based login, signed consent is stored. Wallet should send the signed consent to eSignet.

## Configurable consent lifetime

Let's say 'x' is a consent lifetime and 'y' is the access/ID token lifetime.
Then, `x >= y` is always true in a user flow,

Global configuration: By default, the value is infinite. Consent is saved indefinitely.

To override global configuration in the client additional configuration use the below key:

`consent_expire_in_mins`

* What happens when `consent_expire_in_mins` is not part of the client additional configuration?

User consent is stored indefinitely. 

* What happens when `consent_expire_in_mins` is less than the default access token expire time?

The access token expire time is set equal to the consent expire time.

* How should the client configure to request consent each time?

Client should use `prompt` request query parameter with `consent` as value. When consent is received in the prompt parameter, 
consent screen is displayed irrespective of the previously saved consent.

