This module describes how to conduct load test of the eSignet OIDC(FAPI2.0) flow (OTP-based and Biometric-based) using the provided JMeter script. (`ESignet_MockIDA_Test_script.jmx`).

# Contains
* This directory contains Performance Test script of below API endpoint categories grouped inside "Thread Groups".
	01. A00 Auth Token Generation (Setup)
	02. A01 Create Mock Identities (Setup)
	03. A02 Create OIDC Client (Setup)
	04. S01 OTP Authentication (Execution)
	05. S02 Biometric Authentication (Execution)

* Open source tools used,
	01. [Java 21](https://www.oracle.com/java/technologies/downloads/#java21)
	02. [Apache JMeter 5.6.3](https://jmeter.apache.org/download_jmeter.cgi)
	03. Libraries
		* [nimbus-jose-jwt](https://mvnrepository.com/artifact/com.nimbusds/nimbus-jose-jwt)
		* [BouncyCastle - bcprov](https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk18on)
		* [BouncyCastle - bcpkix](https://mvnrepository.com/artifact/org.bouncycastle/bcpkix-jdk18on)


# How to run performance scripts using Apache JMeter tool
* Download tools and components
  * Download Apache JMeter from https://jmeter.apache.org/download_jmeter.cgi
  * Download JMeter Plugin Manager jar file from https://jmeter-plugins.org/get/ , and install by placing the it in "Jmeter/apache-jmeter-X.X.X/lib/ext"
  * Download following JARs and place them in the same "Jmeter/apache-jmeter-X.X.X/lib/ext" folder.
    * `nimbus-jose-jwt` and 
    * BouncyCastle (`bcprov-jdk18on`, `bcpkix-jdk18on`) 
  * Download script for the required module from the [script](script/) folder of this repo.
* Setup Jmeter
  * Start JMeter by running the jmeter.bat/jmeter.sh as per your OS. 
  * Load downloaded *.jmx script onto JMeter. 
    * If script requires additional plugins, a prompt will appear. Install the required plugins.
    * If plugins were installed, restart JMeter.
  * Update "User Defined Variables - ____" within the JMeter scripts. This list holds environment endpoint URL, protocols, users, secret keys, passwords, runtime file path, support file path etc.
* Validate the script is working functionally. 
    * Disable all "Thread Group(s)" within the test plan by clicking 'Disable'/'Toggle'. 
    * Select the first "Thread Group"and enable it. 
    * In Thread Properties, set "Number of Threads (users)" and "Loop Count" to 1.
    * Execute 1 iteration and validate it ran without errors.  
    * Sequentially, execute each "Thread Group" for 1 iteration, one at a time to validate script is fully functional.
    * Go to [script execution steps](#script-execution-steps) for further detail.
* Setup workload for Performance Test.
  * Execute all scenarios (i.e. S01, S02 labeled thread groups) for multiple iterations (10 or 100 iterations should suffice).
  * Take note of average scenario response time obtained from above test for each scenario. 
  * Open the [MOSIP_TPS_Thread_setting_calculator](MOSIP_TPS_Thread_setting_calculator-ESignet.xlsx) provided for this module and update "Total Target TPS" and "Scenario Response time" column.
  * The excel applies Little's law to recommend required "No. of Threads" and "Constant Throughput Timer" value for each scenario. Apply these values to each scenario in jMeter.
  * Execute a dry run for 10 min. The execution duration is controlled by "testDuration" variable.
  * Use "Scenario level report" within Jmeter GUI, to validate the actual throughput attained during test is as expected. 
    * The throughput can change as per environment performance. "No. of Threads" value may need to be tweaked if throughput is not attained.
* Execute performance run with various loads in order to achieve targeted NFR's. 
  * For a performance run, only Execution scenarios (S01, S02) should be enabled and executed concurrently.


# Setup points before execution
* `A01 Create Mock Identities` creates identities within [mock-identity-system](https://github.com/mosip/esignet-mock-services/tree/master/mock-identity-system). mock-identity-system's mock delay should be configured as per requirement. This release simulated 5 seconds delay as the worst case production scenario. 
  * set "MOSIP_MOCKIDENTITYSYSTEM_RESPONSE_DELAY" = 5000 in YAML.
* Update the "User Defined Variables - Loadgenerator", "User Defined Variables - Server" and "User Defined Variables - Others" groups with your environment's host names (`serverIP`, `serverIP_Internal`, `serverIP_IAM`), `runTimeFilePath`, and `supportFilePath` before running any Thread Group.
* Execution order matters — each Setup group produces a file consumed by later groups:
  * `A00 Auth Token Generation` obtains an auth token used by `A02 Create OIDC Client`.
  * `A01 Create Mock Identities` writes `{runTimeFilePath}/A01_credential_password_auth.txt` (VID, password, full name), which `S01`/`S02` read via the "Load A01 User Credentials From File" CSV Data Set. Disable 'Load A01 User Credentials From File' until A01 execution is complete.
  * `A02 Create OIDC Client` generates an RSA key pair per client and writes `{runTimeFilePath}/A02_client_id_esignet.csv` (client ID + keys), which `S01`/`S02` read via the "Load A02 ClientID From File" CSV Data Set. Disable 'Load A02 ClientID From File' until A02 execution is complete.
  * Run A00 → A01 → A02 to completion before enabling S01 or S02.
  * Keep 'Load A01 User Credentials From File' and 'Load A02 ClientID From File' enabled when enabling S01 or S02..
* Delete runtime files created during previous setup from `{runTimeFilePath}` folder before a fresh setup in a new/updated environment, so stale identities/clients aren't reused.
* Note: as shipped, this script has only "S01 OTP Authentication" and "S02 Biometric Authentication" are enabled by default; "A00", "A01" and "A02" are disabled. Enable each Thread Group deliberately per the validation steps above rather than assuming all groups are active.


# Script execution steps:

  01. A00 Auth Token Generation (Preparation) - In this thread group we are creating the authorization token - Using User Id which will be saved to a file within user defined path - "runTimeFilePath". The authorization token has expiration time which is controlled by MOSIP settings. Ensure the tokens remain valid throughout the duration of the test execution.
    
  02. A01 Create Mock Identities (Setup) - Creates list of unique mock identities and store the details in a file named - A01_credential_password_auth.txt. The VID (individualId), password and first name is stored that will be later used during test scenario execution.  

  03. A02 Create OIDC Client (Setup) - Creates list of OIDC clients. Uses token generated by A00. Also generates a pair of unique private and public RSA keys that will be used for authentication. All data is stored in A02_client_id_esignet.csv.

  04. S01 OTP Authentication (Execution) - Authentication flow scenario that uses OTP for validation.
      * S01 T01 Initiate PAR — POSTs to `/oauth2/par` with PKCE, DPoP proof, and client assertion; server returns a `request_uri`.
      * S01 T02 Send Authorize — GETs `/oauth2/authorize` with the `request_uri`; server redirects to sign-in and returns `authId`/`executionId`.
      * S01 T03 Flow Meta — would fetch flow metadata via `/flow/meta` before starting the login UI flow.
      * S01 T04 1 Authentication Flow - Start — POSTs `executionId` to `/flow/execute`, kicking off the flow and getting back the ACR (login mode) choices.
      * S01 T04 2 Authentication Flow - ACR — POSTs `action=acr_otp` to `/flow/execute`, selecting OTP as the login mode.
      * S01 T04 3 Authentication Flow - Individual ID — POSTs the UIN/VID and captcha token to `/flow/execute`, triggering OTP dispatch.
      * S01 T04 4 Authentication Flow - OTP — POSTs the OTP value to `/flow/execute` to verify the user.
      * S01 T04 5 Authentication Flow - Consent — POSTs the approved consent decisions to `/flow/execute`, completing the flow and returning a signed assertion.
      * S01 T05 Obtain Authorization Code — POSTs the `authId` + assertion to `/oauth2/auth/callback`, getting back the redirect URI with the auth `code`.
      * S01 T06 Obtain Access Token — POSTs the auth code (+ PKCE verifier, DPoP proof, client assertion) to `/oauth2/token`, getting back the access/ID tokens.
      * S02 T07 User Info — GETs `/oauth2/userinfo` with the DPoP-bound access token to fetch the authenticated user's claims.


  05. S02 Biometric Authentication (Execution) - Authentication flow scenario that uses Biometric data for validation.
      * S02 T01 Initiate PAR — POSTs to `/oauth2/par` with PKCE, DPoP proof, and client assertion; server returns a `request_uri`.
      * S02 T02 Send Authorize — GETs `/oauth2/authorize` with the `request_uri`; server redirects to sign-in and returns `authId`/`executionId`.
      * S02 T03 Flow Meta — POSTs `executionId` to `/flow/meta` to fetch flow metadata before starting the login UI flow.
      * S02 T04 1 Authentication Flow - Start — POSTs to `/flow/execute`, kicking off the flow and getting back the ACR (login mode) choices.
      * S02 T04 2 Authentication Flow - Select acr — POSTs `action=acr_bio` to `/flow/execute`, selecting biometrics as the login mode.
      * S02 T04 3 Authentication Flow - Individual ID — POSTs the UIN/VID to `/flow/execute`, advancing to the biometric capture step.
      * S02 T04 4 Authentication Flow - Bio — generates the transaction ID hash, digital ID JWT, and biometric challenge, then POSTs the biometric assertion to `/flow/execute` to verify the user.
      * S02 T04 5 Authentication Flow - Consent — POSTs the approved consent decisions to `/flow/execute`, completing the flow and returning a signed assertion.
      * S02 T05 Obtain Authorization Code — POSTs the `authId` + assertion to `/oauth2/auth/callback`, getting back the redirect URI with the auth `code`.
      * S02 T06 Obtain Access Token — POSTs the auth code (+ PKCE verifier, DPoP proof, client assertion) to `/oauth2/token`, getting back the access/ID tokens.
      * S02 T07 User Info — GETs `/oauth2/userinfo` with the DPoP-bound access token to fetch the authenticated user's claims. 


## Support files required for this test execution:

1. [add_identity_request_details.csv](support-files/add_identity_request_details.csv) - Contain list of basic identity detail that is used to create unique mockIds.
2. [encoded_photo_data.txt](support-files/encoded_photo_data.txt) - This support file contains sample encrypted biometric data. 
