# Overview

User consent to share claims with a Relying Party is stored in eSignet. If a Relying Party modifies the requested claims, any previously stored consent is disregarded, and eSignet requires the user to provide fresh consent, regardless of earlier approvals.

**Note:** During the QR code-based login, signed consent is stored. Wallet should send the signed consent to eSignet.

On save of fresh user consent for a specific Relying Party, old entry is deleted from the consent table and an audit entry is added in the consent_history table with all the details.

## 1.6.0 Changelist

Time bound the captured user consent. Time to consider the given consent is configurable at every Relying Party level. Fallback to default expires time(global configuration) if nothing is configured for a Relying Party.

Accepted minimum consent expire time is configurable. 

TODO



