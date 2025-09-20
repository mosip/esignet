# Overview

eSignet offers QR code based login using Wallet apps. Wallet apps store verifiable credentials(VC). Both demographic and biometric data 
are available in the VC. Biometric data in the VC could be leveraged to perform local 1:1 biometric match to authenticate into eSignet and access any relying party services.

## Binding Process

VC issued by an identity system against an individual ID. Issued VC should be mapped to a key pair, private key resides in the wallet app's secure storage. 
The corresponding public key is mapped to the same individual ID in the identity system (VC issuer).

![esignet-wallet-binding.png](../images/esignet-wallet-binding.png)

## Wallet based authentication using QR code

Below diagram depicts the QR code based authorization code flow in eSignet.

![esignet-authorize-qr-code-flow.png](../images/esignet-authorize-qr-code-flow.png)



