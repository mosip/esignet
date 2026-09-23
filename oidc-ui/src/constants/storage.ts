// sessionStorage key the @thunderid/react SDK stores the in-progress flow's executionId under.
// The SDK owns its lifecycle (start/resume/clear); we only read it to detect a resumable flow.
// Key is `${vendor}_execution_id`; oidc-ui uses the default "thunderid" vendor. Not exported by
// the SDK, so mirrored here — keep in sync with the vendor passed to ThunderIDProvider.
export const SDK_EXECUTION_ID_KEY = "thunderid_execution_id";
