# 🧪 eSignet UI Automation Framework

## 🚀 Overview

This project is a UI automation testing framework for **eSignet**, built using **Cucumber**, **TestNG**, and **Selenium WebDriver**, with support for **BrowserStack** and **parallel execution**.

## 🔍 What is eSignet?

eSignet is a reference identity and authentication platform developed under the [MOSIP](https://www.mosip.io) project. It demonstrates how authentication and consent mechanisms can be implemented for foundational ID systems.

This framework enables automated testing of eSignet's UI features and flows across multiple browsers and devices to ensure consistent and reliable behavior.

---

## 🧪 Features

- ✅ Cucumber BDD support (`.feature` files)
- ✅ TestNG parallel execution
- ✅ BrowserStack integration for cross-browser/device testing
- ✅ Seamless switching between local and cloud runs
- ✅ Configurable via `config.properties`
- ✅ Generates reports in **HTML**, **JSON**, and **Extent** format
- ✅ Automatically picks up scenarios for different browsers using scenario names or tags

---

## ⚙️ Technologies Used

- Java
- Maven
- Selenium WebDriver
- Cucumber
- TestNG
- BrowserStack (optional)
- Extent Reports

---

## 📁 Project Structure

```
project-root/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── base/            # Base classes (WebDriver setup)
│   │   │   ├── constants/       # Class for constants
│   │   │   ├── pages/           # Page Object classes
│   │   │   ├── stepdefinitions/ # Cucumber step definitions
│   │   │   ├── runners/         # TestNG-Cucumber runner classes
│   │   │   ├── utils/           # Utility classes
│   │   └── resources/
│   │       ├── featurefiles/    # Cucumber feature files
│   │       ├── config.properties# Central config file
│   │       ├── config.properties# Central config file
│   │       ├── extend.properties# Extend report property file
│   └── test/
│ 
├── testNgXmlFiles/             # Optional TestNG XML suites
├── pom.xml
└── README.md
```

---

## 🔧 Configuration (`config.properties`)

```properties
baseurl=<base_environment_url> # e.g., https://healthservices-mock.es-qa1.mosip.net/
pluginToExecute=mosipid  # or mock - see "Plugin modes" below
runOnBrowserStack=true/false
runMultipleBrowsers=true/false
threadCount=1             # See "Known gaps" - parallel scenario execution (>1) is not currently safe
browser=chrome                # Used when runMultipleBrowsers is false
browsers=chrome,edge          # Used when runMultipleBrowsers is true
browserstack_username=<your_browserstack_username>
browserstack_access_key=<your_browserstack_key>
localeUrl=<locale_base_url> # e.g., https://eSignet-mock.es-qa1.mosip.net/
runLanguage=eng,khm,hin   # Languages to test, can be single value and comma-separated for more languages
eSignetbaseurl=<eSignet_base_environment_url> # e.g., https://eSignet-mock.es-qa1.mosip.net/
oidcClientId=             # Pre-existing OIDC client for the default scenarios; blank = create one per run

# Pre-existing identity overrides - when set, skip the corresponding prerequisite API call
# instead of creating a fresh identity/client. See "Login identity sourcing" below.
uin=                      # Pre-existing UIN (mosipid); comma-separated for multiple
mockUin=                  # Pre-existing mock-identity-system individualId
uinPhoneNumber=           # Local number only, no country code - overrides the login phone directly
```

---

## 🔌 Plugin modes (`pluginToExecute`)

| Mode | Identity source | Client creation |
|------|------------------|------------------|
| `mosipid` | Real ID Repository identity, created by the `AddIdentity` prerequisite | Real partner/policy chain: `CreatePolicyGroup → DefinePolicy → PublishPolicy → CreatePartner → UploadCACertificate → UploadPartnerCertificate → RequestAPIKeyForAuthPartner → ApproveAPIKey → OIDCClient` (`/v1/partnermanager/oidc/client`) |
| `mock` | MOSIP mock-identity-system, created by `AddIdentityMock` | Lighter-weight direct client creation (`OIDCClientV3MOCK`, `/v1/esignet/client-mgmt/client`) |

Prerequisites specific to one plugin are skipped for the other automatically (`EsignetUtil.isTestCaseValidForExecution`), so the same `esignetPrerequisiteSuite.xml` runs unmodified for either mode.

## 🪪 Login identity sourcing

Most scenarios don't sign up through the UI — they log in with an identity the `AddIdentity`/`AddIdentityMock` prerequisite already created:

- `EsignetUtil.getPrerequisiteRegisteredPhoneNumber()` is the single source every `"user enters Registered mobile number..."` step reads from. `uinPhoneNumber` (if set) short-circuits it entirely; otherwise it reads the prerequisite's cached identity, plugin-appropriate.
- Exactly **one** scenario (`"Verify user completes registration process"`, tagged `@registrationProcess`, in `ConsentPage.feature`) does a real signup through the UI and then logs in with that freshly-created identity. It's skipped automatically if the signup service isn't reachable in this environment (`EsignetUtil.isSignupServiceDeployed()`).
- `mosipid` and `mock` submit different values as the login identifier: `mosipid` logs in with the identity's `phone` attribute; the mock-identity-system's OTP lookup only recognizes an exact `individualId` match (its `phone` field is an independent, unrelated random value), so mock logs in with `individualId` instead.
- Phone numbers from either identity source are generated in E.164 format (country code included, required by the identity-creation API) but the login field expects the local number only — `EsignetUtil.stripCountryCode()` derives and strips the country code live from the relevant schema regex rather than hardcoding a country.

---

## 🔐 PAR & DPoP

### How the authorize URL is built

Scenarios reach the eSignet login page in one of two ways, decided in `BaseTest`:

| Flow | Used by | How the URL is built |
|------|---------|----------------------|
| **Direct (non-PAR)** | All scenarios by default | Every OIDC parameter is passed in the `/authorize` query string. No `client_assertion` is needed, since the client is not authenticated on this browser-facing leg. |
| **PAR** | Scenarios tagged `@PAR`, and *all* scenarios if the environment mandates PAR | Parameters are POSTed to `/oauth/par` (authenticated with a `client_assertion` JWT), and the returned `request_uri` is passed to `/authorize`. |

### PAR detection

PAR support is read from the eSignet discovery document
(`/v1/esignet/oidc/.well-known/openid-configuration`) — there is no config flag, since the
environment is the authoritative source:

- `pushed_authorization_request_endpoint` present → PAR is **available**
- `require_pushed_authorization_requests: true` → PAR is **mandated for every client**, so even
  clients that don't request it must use PAR (the direct flow is rejected server-side by
  `AuthorizationServiceImpl#assertPARRequiredIsFalse`). The suite falls back to PAR for all
  scenarios in this case.

`@PAR`-tagged scenarios are **skipped** when PAR is not supported. To skip them for any other
reason, filter by tag: `-Dcucumber.filter.tags="not @PAR"`.

### Client creation

A client that sets `require_pushed_authorization_requests: true` in its `additionalConfig` is
*forced* down the PAR flow — driving it through a direct `/authorize` URL fails with
`invalid_request`. The suite therefore creates two kinds of clients
(`esignetUI/CreateClientMock/CreateOIDCClient.yml`):

- **Default / purpose-type clients** — no PAR flag, usable with either flow.
- **`ESignetUI_CreateOIDCClient_par_required_Smoke_sid`** — sets
  `require_pushed_authorization_requests: true`, used by `@PAR` scenarios. It has its own template
  (`CreateOIDCClientPar.hbs`) and its own keypair, since reusing another client's public key would
  be rejected with `DUPLICATE_PUBLIC_KEY`.

### ⚠️ DPoP is intentionally not enabled

`dpop_bound_access_tokens: true` is **not** set on any client the UI suite drives a browser login
through. The token exchange is performed by the relying party (the demo health-services app), not by
the test. eSignet rejects a token request with `invalid_request` when the transaction is DPoP-bound
but no DPoP header is present (`OAuthServiceImpl`), so flagging the client would break the login flow
at the redirect back to the RP. DPoP is covered in **api-test**, which controls the token call
itself.

### Known gap

There are currently **no PAR- or DPoP-specific UI scenarios**. The plumbing above exists, but no
scenario is tagged `@PAR` yet, so the PAR-mandated client is created but never exercised. Scenarios
worth adding:

- PAR happy path (login end-to-end with the PAR-mandated client).
- PAR enforcement (negative): drive the PAR-mandated client through a direct `/authorize` URL and
  assert the request is rejected.
- `request_uri` single-use and expiry (`expires_in` is 60s) behaviour.

---

## 🧱 Pre-requisites

### Common Requirements
- JDK 21
- Maven 3.6.0 or higher
- BrowserStack account (for cross-browser testing)

### Windows
- Git Bash 2.18.0 or higher
- Ensure `settings.xml` is present in the `.m2` folder

### Linux
- Ensure `settings.xml` is in:
  - Maven's default `/conf` directory
  - `/usr/local/maven/conf/`

---

## 🚀 Getting Started

### 1. Access the Test Automation Code

#### 📥 Via Browser
1. Clone or download from [GitHub](https://github.com/mosip/esignet)
2. Unzip contents locally
3. Open terminal (Linux) or command prompt (Windows)

#### 🐙 Via Git Bash
```bash
git clone https://github.com/mosip/esignet
```

---

### 2. Build the Project

Navigate to the test directory:

```bash
cd ui-test
```

Build the project:

```bash
mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true
```

---

### 3. Execute the Test Automation Suite

#### ▶️ Using JAR
```bash
cd target/
java -jar -Denv.endpoint="$ENV_ENDPOINT" uitest-esignet-*.jar
```

#### 🧩 Using Eclipse IDE

1. **Install Eclipse**  
   [Download Eclipse](https://www.eclipse.org/downloads/)

2. **Import Maven Project**  
   - `File > Import > Maven > Existing Maven Projects`  
   - Select the `ui-test` folder

3. **Build the Project**  
   - Right-click project > `Maven > Update Project`

4. **Configure Run**  
   - `Run > Run Configurations > Java Application > New`  
   - **Main class**: `runners.Runner`  
   - **VM Arguments**:
     ```bash
     -Denv.endpoint=<base_env>
     ```

5. **Run the Configuration**  
   - Click **Run** or **Debug** (for breakpoints)

6. **View Test Results**  
   - Results will appear in `ui-test/test-output`

---

## ☁️ Run on BrowserStack

- Set `runOnBrowserStack=true` in `config.properties`
- Ensure BrowserStack credentials are correctly set

---

## 🧵 Thread Management

- Managed via `threadCount` in `config.properties` — the prerequisite suite (`esignetPrerequisiteSuite.xml`) always runs sequentially regardless of this value; it's only read by `runners.Runner`'s Cucumber `@DataProvider` for the scenario suite (`TestNg.xml`).
- ⚠️ **`threadCount > 1` is not currently safe.** `AdminTestUtil`'s dependency-report bookkeeping (`consumers`/`globalConsumersList`/`currentTestCaseName` in the `apitest-commons` dependency) is shared, non-thread-safe static state with no per-scenario isolation, and nearly every scenario hits it via `replaceIdWithAutogeneratedId()` while resolving its client ID in `BaseTest`. Running in parallel reliably produces `ArrayIndexOutOfBoundsException`s from concurrent mutation of the same shared list. Fixing it requires changes in `apitest-commons`, outside this module — keep `threadCount=1` until that's addressed upstream.

---

## 🧪 Tags Support

Run scenarios with specific tags:

```bash
mvn test -Dcucumber.filter.tags="@smoke"
```

Or configure in runner class:

```java
@CucumberOptions(tags = "@regression")
```

Tags that change how a scenario is set up (rather than just selecting it):

| Tag | Effect |
|-----|--------|
| `@PAR` | Runs the scenario through the PAR flow using the PAR-mandated client. Skipped when the environment does not support PAR. See [PAR & DPoP](#-par--dpop). |
| `@PurposeLogin`, `@PurposeLink`, `@PurposeVerify`, `@PurposeNone`, `@NoPurpose`, `@NoTitleAndSubTitle`, `@EmptyTitleAndSubTitle`, `@SingleAuthFactor` | Use a purpose-type-specific OIDC client instead of the default one. Skipped when running with the `mosipid` plugin, since these clients are only created under `mock` (`/v1/esignet/client-mgmt/client`). |
| `@registrationProcess` | Marks the one real signup-through-the-UI scenario. Skipped automatically if the signup service isn't reachable in this environment. See [Login identity sourcing](#-login-identity-sourcing). |

---

## 📊 Reports

- `target/cucumber.html` – Cucumber native HTML report  
- `target/cucumber.json` – JSON report  
- `test-output/extent-report.html` – Extent report

---

## 🌍 BrowserStack Notes

- Use scenario names like: `Login - chrome`, `Login - firefox`
- Or use tags like `@chrome`, `@firefox`
- The framework maps capabilities accordingly

---

## 💡 Tips

- If tests hang or report 0 tests:
  - Check scenario naming and browser mapping
  - Log scenario names using an overridden `scenarios()` method
  - Set thread name for debugging:
    ```java
    Thread.currentThread().setName(pickle.getPickle().getName());
    ```

---

## 🧾 References

- [MOSIP Docs – eSignet](https://docs.mosip.io/)
- [BrowserStack Documentation](https://www.browserstack.com/docs)
- [Cucumber with TestNG Guide](https://cucumber.io/docs/testng-integration/)