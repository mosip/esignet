@flow-execute
Feature: /health and /system-info — liveness and the certificate endpoints
  The last two HTTP surfaces of esignet-service that no other feature file
  touches: the liveness probe, and the pair of /system-info endpoints that read
  and replace the service's signing certificates.

  Tagged @flow-execute rather than a tag of its own on purpose. These endpoints
  need neither admin credentials nor a registered client, which is exactly the
  precondition @flow-execute already stands for — and it is the tag the harness
  always runs. A dedicated @system-info tag would need adding to the default set
  in engine_test.go before it ever executed.

  # Unlike client-mgmt, these endpoints answer with errorCode "invalid_request"
  # (not "invalid_input") and "invalid_certificate". Both still come back as
  # HTTP 200 with an "errors" array, per the MOSIP convention used throughout.

  # ------------------------------------------------------------------ health --

  # Plain text "ok", not JSON — so status is the only thing to assert. The probe
  # is what compose and every deployment gate on, so a change that breaks it
  # strands the whole stack in "starting" with no other symptom.
  Scenario: The health endpoint reports the service is up
    When I send a "GET" request to "/health"
    Then the response status should be 200

  # ------------------------------------------------- certificate — positive --

  # ROOT is the top of the key hierarchy and the one application id whose
  # referenceId may be blank; every other id requires one. Asking for it returns
  # the certificate the service provisioned at boot, so this also proves the key
  # hierarchy came up rather than the server merely accepting connections.
  Scenario: Read the ROOT certificate
    When I send a "GET" request to "/system-info/certificate?applicationId=ROOT"
    Then the response status should be 200
    And the JSON path "response.certificate" should exist

  # ------------------------------------------------- certificate — negative --

  Scenario: Reading a certificate without an applicationId is rejected
    When I send a "GET" request to "/system-info/certificate"
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_request"

  # The length ceiling is checked before the allow-list, so an over-long id is
  # rejected for its length even when it would also have failed as not permitted.
  Scenario: A referenceId beyond the length ceiling is rejected
    When I send a "GET" request to "/system-info/certificate?applicationId=ROOT&referenceId=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_request"

  # The allow-list has no silent default: GetCertificate mints a fresh key pair
  # for any (applicationId, referenceId) pair it has not seen, so an unlisted
  # encryption-key reference must be refused rather than quietly generating one.
  # Only the fixed hierarchy tiers (blank, RSA_2048, the three signing refs) are
  # exempt without configuration.
  Scenario: A referenceId outside the configured allow-list is refused
    When I send a "GET" request to "/system-info/certificate?applicationId=ROOT&referenceId=ANY_ENCRYPTION_KEY"
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_request"

  # ---------------------------------------------- uploadCertificate — negative --

  # The positive upload is deliberately absent: replacing a certificate requires
  # one signed against the service's own CSR for that alias, which cannot be
  # committed as a fixture without going stale when the key is regenerated. The
  # rejection paths below are where the handler's own logic lives; the accepted
  # path is covered by the package's unit tests.

  Scenario: Uploading a certificate with a malformed body is rejected
    When I send a "POST" request to "/system-info/uploadCertificate" with body:
      """
      { "requestTime": "{{now}}", "request": { "applicationId": }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_request"

  Scenario: Uploading a certificate without an applicationId is rejected
    Given a fresh request timestamp
    When I send a "POST" request to "/system-info/uploadCertificate" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "certificateData": "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_request"

  Scenario: Uploading a certificate without certificateData is rejected
    Given a fresh request timestamp
    When I send a "POST" request to "/system-info/uploadCertificate" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": { "applicationId": "ROOT" }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_request"

  # KNOWN ISSUE — tracked as mosip/esignet#2527. Real defect in esignet-service,
  # reported here as KNOWN_ISSUE (not FAILED) rather than muted or weakened to
  # match the current response, so it stays visible in the report without
  # blocking the rest of the suite. Revert to plain FAILED (drop @known_issue
  # below and the apiKnownIssues entry in api/steps.go) only once #2527 is
  # closed and this scenario goes green on its own — or, if the service team
  # rules the 500 intentional, invert the assertion to 500 / "server_error" and
  # record that decision here.
  #
  # Expected: HTTP 200 + errorCode "invalid_certificate".
  # Actual:   HTTP 500 + errorCode "server_error" ("an unexpected error occurred").
  #
  # Both required fields are present, so this gets past the handler's own checks
  # and fails inside the certificate parser. Unparseable client input is then
  # reported as a server fault, while every other bad-input case in the service
  # answers HTTP 200 with a specific errorCode.
  #
  # Cause: UploadCertificate wraps the parse failure with a bare
  #   fmt.Errorf("parse uploaded certificate: %w", err)
  # (keymanager/service.go:823) — an anonymous error with no sentinel.
  # handleServiceError (keymanager/handler.go:194-204) picks the response by
  # errors.Is against named sentinels and only maps ErrThumbprintMismatch and
  # ErrCertificateAlreadyExists to invalid_certificate, so a parse failure
  # matches no arm and falls into the default branch, which is the 500.
  #
  # The endpoint already declares errCodeInvalidCertificate for exactly this
  # shape of error, and the package already defines ~10 sentinels alongside it,
  # so the fix is to add one more (e.g. ErrInvalidCertificate), return it here,
  # and add it to that case — roughly three lines. See #2527 for the full
  # write-up. That is an esignet-service change, which this branch does not
  # make for any plugin.
  @known_issue
  Scenario: Uploading certificateData that is not a certificate is rejected as an invalid certificate
    Given a fresh request timestamp
    When I send a "POST" request to "/system-info/uploadCertificate" with body:
      """
      {
        "requestTime": "{{now}}",
        "request": {
          "applicationId": "ROOT",
          "certificateData": "not-a-certificate"
        }
      }
      """
    Then the response status should be 200
    And the JSON value at "errors.0.errorCode" should be "invalid_certificate"
