# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

UI automation suite for **MOSIP eSignet** (identity/authentication platform). Cucumber BDD + TestNG + Selenium WebDriver, layered on top of MOSIP's `apitest-commons` library (`io.mosip.testrig.apitest.commons`), which supplies the API-driven prerequisite orchestration (identity/partner/policy/OIDC-client creation, keycloak, S3, config templating).

`README.md` is unusually detailed and is the authoritative reference for domain behavior — read it for plugin modes, login-identity sourcing, and the PAR/DPoP flow. This file covers the mechanics of building, running, and where code lives.

## Build & run

JDK 21 and Maven 3.6+ required.

```bash
mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true
```

**Tests do NOT run via `mvn test`.** Surefire is configured with `skipTests=true`. The suite is driven by a `main()` method:

- Entry point: `runners.Runner.main()` (also the shaded-JAR `Main-Class`).
- Run from the built JAR: `cd target && java -Denv.endpoint="$ENV_ENDPOINT" -jar uitest-esignet-*.jar` (JVM `-D` flags must precede `-jar`; anything after the JAR name is passed to `main()` as an argument)
- Run from IDE: run `runners.Runner` as a Java Application with VM arg `-Denv.endpoint=<base_env>`.

Run type ("IDE" vs "JAR") is auto-detected from whether `Runner.class` sits inside a `.jar`, which changes resource-extraction paths and where `testNgXmlFiles` is located.

### Selecting scenarios

- **By tag:** `-Dcucumber.filter.tags="@smoke"` or `"not @PAR"`. Tags are also settable in `Runner`'s `@CucumberOptions`.
- **Single feature:** in `runners/Runner.java` the `@CucumberOptions.features` points at `classpath:featurefiles`; there's a commented line for pinning a single `.feature` — uncomment and rebuild to narrow the run.
- There is no per-scenario CLI selector beyond tags; the framework expands each scenario across configured browsers/languages itself (see below).

## Execution architecture (the non-obvious part)

`Runner.main()` orchestrates everything; it does not just delegate to Cucumber:

1. Bootstraps `apitest-commons` (`AdminTestUtil.init()`, `BaseTestCase.initialize()`, resource extraction, config load, OTP listener).
2. Resolves the **plugin** (`mosipid` | `mock`) via `EsignetUtil.getPluginName()` — read from `pluginToExecute` in config when set to `mosipid`/`mock`, otherwise auto-detected from the eSignet actuator. Under `mosipid` it additionally provisions Keycloak users, policies, and biometric test data up front. `isSunbirdAuthenticatorActive()` gives finer detail (a Sunbird RC-backed server still reports `mock` here) and always queries the actuator directly, regardless of `pluginToExecute`.
3. **Loops over each language** in `runLanguage` (comma-separated). Per language it sets `currentRunLanguage`, resets counters, inits a fresh Extent report, runs the suite, flushes, and pushes the report to S3.
4. Each run invokes `startTestRunner()`, which finds `testNgXmlFiles/MasterTestSuite.xml` and runs it via a programmatic `TestNG` instance.

The TestNG suite files form a three-phase pipeline (`MasterTestSuite.xml` aggregates them, in order):

- **`esignetPrerequisiteSuite.xml`** — API prerequisites run **sequentially, regardless of `threadCount`**. Creates the login identity (`AddIdentity` for mosipid / `AddIdentityMock` for mock) and the full partner→policy→certificate→OIDC-client chain, plus `CreatePolicySunBirdR` (a Sunbird RC registry policy used as KBI credentials — self-skips unless the server's authenticator is actually Sunbird RC). Each `<test>` maps a YAML request file (under `src/main/resources/esignetUI/...`) to a generic testscript class in `io.mosip.testrig.apirig.esignetUI.testscripts`. Plugin-inappropriate steps self-skip via `EsignetUtil.isTestCaseValidForExecution`, so the same suite runs unmodified regardless of plugin.
- **`TestNg.xml`** — a single `<class name="runners.Runner"/>`. Here `Runner` acts as an `AbstractTestNGCucumberTests` whose `@DataProvider("scenarios")` expands every Cucumber scenario across the supported browsers (× the current language) and runs them.
- **`esignetPostrequisiteSuite.xml`** — cleanup that runs after the scenario suite. Currently just `DeletePolicySunBirdR`, undoing `CreatePolicySunBirdR` (via `DeleteWithParam`, this repo's only DELETE-capable generic testscript).

### Layers within a UI scenario

- **Feature files:** `src/main/resources/featurefiles/*.feature`.
- **Step definitions:** `src/main/java/stepdefinitions/*` — glue packages are `stepdefinitions` and `base`. Step-def classes receive `BaseTest` via cucumber-picocontainer constructor injection and build their page objects from `baseTest.getDriver()`.
- **Page objects:** `src/main/java/pages/*`, all extending `base.BasePage` (PageFactory `@FindBy`, wait/click/report helpers). Every interaction helper logs to the Extent report.
- **`base.BaseTest`** (extends `apitest-commons` `AdminTestUtil`): Cucumber lifecycle hooks. Holds the `WebDriver` in a `ThreadLocal`, sets up local or BrowserStack drivers, and in `@Before` builds the eSignet `/authorize` URL — choosing the **direct vs PAR** flow and the tag-appropriate OIDC client via `CLIENT_CONFIG_MAP`. `@After` handles pass/fail/skip accounting, screenshots on failure, and report flushing.

## Configuration

`src/main/resources/config.properties` is the central config, read through `utils.EsignetConfigManager`. Key knobs: `baseurl`/`eSignetbaseurl`/`localeUrl`/`signupUrl`, `runLanguage`, `runMultipleBrowsers`+`browsers`, `runOnBrowserStack`, and identity overrides (`oidcClientId`, `uin`, `mockUin`, `uinPhoneNumber`). `application.properties` and `browserstack.yml` at the repo root also feed the run. See README "Configuration" and "Login identity sourcing" for the meaning of the identity overrides.

**Keep `threadCount=1`.** Parallel scenario execution is not safe — `apitest-commons`' `AdminTestUtil` dependency-report bookkeeping is shared non-thread-safe static state that nearly every scenario touches while resolving its client ID. `>1` reliably throws `ArrayIndexOutOfBoundsException`. Fix is upstream in `apitest-commons`. (The prerequisite suite always runs sequentially anyway.)

## Reports & outputs

- `test-output/ExtentReport.html` — Extent report; renamed per-language to `EsignetUi-<env>-<lang>-<timestamp>-T-P-F-S-KI.html` and optionally pushed to S3.
- `target/cucumber.html`, `target/cucumber.json`, `reports/` — Cucumber native output.
- `screenshots/` — failure screenshots. `testng-report/` — TestNG output.

## Conventions & gotchas

- **Known issues:** `src/main/resources/config/Known_Issues.txt` (format `BUGID------Scenario Name`) is loaded at startup; matching scenarios are auto-skipped and reported as known issues linked to Jira, not failures.
- **Skips are deliberate and layered.** A scenario may skip because: it's a known issue; it's a purpose-type/`@PAR`/mock-only client tag running under `mosipid`; PAR isn't supported in the environment; or the signup service isn't deployed (`@registrationProcess`). `BaseTest.skipWithReason()` logs the reason to the report before throwing `SkipException`. `@Before(order = 0)` always creates the per-scenario ExtentTest first so skip reasons are attributed correctly.
- **PAR vs direct `/authorize`:** decided per scenario in `BaseTest`, driven by the environment's discovery document, not a config flag. DPoP is intentionally not enabled here (covered in api-test). See README "PAR & DPoP".
- **Tags that change setup** (not just selection): `@PAR`, the purpose-type client tags (`@PurposeLogin`, `@NoPurpose`, etc.), `@registrationProcess`, `@mobile`, `@NeedsUIN`, `@NeedsVID`. See README "Tags Support".
- OTP for logins is mocked as `111111` (`BasePage.getOtp()`); the `OTPListener` from apitest-commons handles real-OTP environments.
- **KBI (Knowledge-Based Identity) login**: unsupported only under mosipid (`EsignetUtil.isKbiSupportedPlugin()`). The KBI field schema is read live from the current transaction (`EsignetUtil.getKbiFieldSchema()`, sourced via `ClaimsUtil.getConfigs()` off the decoded `/login#<payload>` URL) — not from the eSignet actuator, which has been observed to read back stale/empty even when a real transaction carries a populated schema. When testing against a Sunbird RC-backed server, `CreatePolicySunBirdR`'s fixture values (`EsignetUtil.getSunBirdRFullName()`/`getSunBirdRDob()`/`getSunBirdRPolicyNumber()`) are the one known-valid KBI credential set for a real login attempt.
- **`pluginToExecute` config property** (`mosipid`/`mock`) short-circuits `getPluginName()` to skip the actuator call; leave it blank or set it to anything else to fall back to eSignet's own `api-test`-style actuator auto-detection. Sunbird RC is a finer server-side detail on top (`isSunbirdAuthenticatorActive()`, which always hits the actuator), still reported as `mock` by `getPluginName()`.
- **`idTokenExpirySeconds` config property** short-circuits `EsignetUtil.getIdTokenExpirySeconds()` (used by `signJWKKey()` to set the client_assertion JWT's expiry) to skip the actuator call for `mosip.esignet.id-token-expire-seconds`; leave it blank to fall back to the actuator. Needed for deployments (e.g. the Thunder/eSignet-go build) with no Spring actuator at all - `actuator/env` 404s there, and every other actuator-derived lookup (plugin auto-detection, Sunbird detection) silently degrades to a safe default in that case, but this one throws `IllegalStateException` instead of crashing on `Integer.parseInt(null)`.
- **The default OIDC client has two mutually-exclusive variants**, both writing to the same cache key (`CreateOIDCClient_all_Valid_Smoke_sid_clientId`) so `BaseTest` needs no changes: `CreateOIDCClient.yml` (V3, `/v1/esignet/client-mgmt/client`, with `additionalConfig`) for plain mock/mosipid, and `CreateOIDCClientV2SunBird.yml` (V2, `/v1/esignet/client-mgmt/oauth-client`, no `additionalConfig`) for a Sunbird RC-backed server — the V3 endpoint 500s there. `SimplePostForAutoGenId` routes between them via `isSunbirdAuthenticatorActive()`. The purpose-type clients in `CreateOIDCClient.yml` (`@PurposeLogin` etc.) don't have V2 counterparts yet, so they self-skip under Sunbird rather than 500ing.
- When adding any new prerequisite/postrequisite YAML, remember `EsignetUtil.isTestCaseValidForExecution`'s `mock` branch only lets an endpoint through if it contains `/esignet/` or `/mock-identity-system/` — anything else (e.g. an external registry endpoint behind a `$SUNBIRDBASEURL$`-style placeholder) needs an explicit carve-out there, checked *before* your testscript's own base-URL-swap logic ever runs, or it silently self-skips with "feature not supported" and never reaches your code at all.
