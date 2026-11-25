# ldp test data

- `ldp_vc/` and `vp/` hold sample MOSIP, Inji and Sunbird credentials and
  presentations. `did/` holds the DID and key documents their proofs
  reference; the tests serve them from an in-memory transport. `w3c/README.md`
  gives the origin of the W3C Data Integrity vectors, which the tests use only
  to cross-check canonicalization (Data Integrity proofs are not accepted).
- `did/idaController.json`, `did/httpsMockController.json` and
  `did/httpsMockController2.json` are written for these tests: the controller
  documents of the HTTPS key documents, listing each key under
  `assertionMethod`, which the resolver requires.
- `did/didMockKey.json` and `did/didEcr1Key.json` list the DID itself, not a
  verification method, under `assertionMethod`, so they authorize no key and
  their credentials fail with `ErrKeyNotAuthorized`. Tests that need those
  credentials to verify serve a copy listing the methods.
- `contexts/index.json` maps context URLs to files for `Config.ContextsDir`.
- `contexts/security-v2-standin.jsonld` is **not** the published
  `https://w3id.org/security/v2` context. It is a minimal test stand-in that
  defines only the proof terms the RsaSignature2018 and
  Ed25519Signature2018 samples use, so the suite-context fallback in
  `proof.Proof.Verify` can be tested. Deployments must pin the official
  security v1/v2 contexts instead.
