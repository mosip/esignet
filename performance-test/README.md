
### Contains
* This folder contains performance test script of below API endpoint categories.

  *esignet_mockida_test_script.jmx
    1. Create OIDC Client in Mock Authentication System (Setup)
    2. Create Identities in Mock Identity System (Setup)
    3. S01 OTP Authentication (Execution)
  
  *esignet_mosipida_test_script.jmx
	1. Create Identities in MOSIP Identity System (Setup)
	2. Create OIDC Client in MOSIP Authentication System (Setup)
	3. S01 OTP Authentication (Execution)
	4. S02 Password Authentication (Execution) 

* Open source Tools used,
    1. [Apache JMeter](https://jmeter.apache.org/)

### How to run performance scripts using Apache JMeter tool
* Download Apache JMeter from https://jmeter.apache.org/download_jmeter.cgi
* Download scripts for the required module.
* Start JMeter by running the jmeter.bat file for Windows or jmeter file for Unix. 
* Validate the scripts for one user.
* Execute a dry run for 10 min.
* Execute performance run with various loads in order to achieve targeted NFR's.

### Setup points for Execution

* We need some jar files which needs to be added in lib folder of jmeter, PFA dependency links for your reference : 

   * bcprov-jdk15on-1.66.jar
      * <!-- https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk15on -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk15on</artifactId>
    <version>1.66</version>
</dependency>

   * jjwt-api-0.11.2.jar
      * <!-- https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.2</version>
</dependency>

   * jjwt-impl-0.11.2.jar
       * <!-- https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-impl -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.2</version>
    <scope>runtime</scope>
</dependency>

   * jjwt-jackson-0.11.2.jar
       * <!-- https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-jackson -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.2</version>
    <scope>runtime</scope>
</dependency>

   * nimbus-jose-jwt-9.25.6.jar  
       * <!-- https://mvnrepository.com/artifact/com.nimbusds/nimbus-jose-jwt -->
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.25.6</version>
</dependency>

### Mosip Config changes for Mock IDA

* eSignet-default properties: eSignet will be pointing to MockIDA services after performing following changes
		*mosip.esignet.integration.authenticator=MockAuthenticationService
		*mosip.esignet.integration.key-binder=MockKeyBindingWrapperService
		
   *Removing Auth token dependency for the eSignet client ID generation
		*mosip.esignet.security.auth.post-urls={}
		*mosip.esignet.security.auth.put-urls={}
		*mosip.esignet.security.auth.get-urls={}
		*spring.security.oauth2.resourceserver.jwt.issuer-uri=
		
### Mosip Config changes for Mosip IDA

* eSignet-default properties: eSignet will be pointing to Mosip IDA services after performing following changes
		*mosip.esignet.integration.authenticator=IdaAuthenticatorImpl
		*mosip.esignet.integration.key-binder=IdaKeyBinderImpl
				
   *Adding Auth token dependency for the eSignet client ID generation
		*mosip.esignet.security.auth.post-urls={'${server.servlet.path}/client-mgmt/**' : {'SCOPE_add_oidc_client'} , \
			* \ '${server.servlet.path}/system-info/**' : { 'SCOPE_upload_certificate'},\
            * \ '${server.servlet.path}/binding/wallet-binding' : { 'SCOPE_wallet_binding'}, \
			* \ '${server.servlet.path}/binding/binding-otp' : { 'SCOPE_send_binding_otp'}}
		*mosip.esignet.security.auth.put-urls={'${server.servlet.path}/client-mgmt/**' : { 'SCOPE_update_oidc_client'} }
		*mosip.esignet.security.auth.get-urls={'${server.servlet.path}/system-info/**' : { 'SCOPE_get_certificate'}
		*spring.security.oauth2.resourceserver.jwt.issuer-uri=${keycloak.external.url}/auth/realms/mosip

### Schema update for the Password authentication.

* We have created new schema with version 0.4 in cellbox1 environment to support password authentication where default schema does not password authentication.

### Execution points for eSignet Authentication API's

*esignet_mockida_test_script.jmx
	
	* Create OIDC Client in Mock Authentication System (Setup) : This threadgroup generates client id and encoded private key pair and stored in csv file. 
	* Create Identities in Mock Identity System (Setup) : This threadgroup generates mock identities and stores in mock identity database. These identities are used for authentication in eSignet portal.
    * S01 OTP authentication (Execution)
		*S01 T01 GetCsrf: This API endpoint generates CSRF token.
		*S01 T02 Oauthdetails : This API endpoint hits Oauthdetails endpoint of eSignet.
		*S01 T03 Send OTP : This API endpoint sends OTP request for authentication.
		*S01 T04 Authentication : This API endpoint performs authentication in eSignet portal
		*S01 T05 Authorization : This API endpoint performs authorization in eSignet portal
		*S01 T06 Token: Code created in the preparation will be used only once and a signed JWT key value is also required for which we are using a JSR223 Pre-processor. The Pre-processor(Generate Client Assertion) will generate a signed JWT token value using the client id and its private key from the file created in Create OIDC Client in Mock Authentication System (Setup). An access token will be generated in the response body.
		*S01 T07 Userinfo: For execution the generated access token from the token end point api is used. Till the token is not expired it can be used for multiple samples.
		
*esignet_mosipida_test_script.jmx
	
	* Create Identities in MOSIP Identity System (Setup) : This threadgroup generates VIDs and passwords for eSignet authentication and stored in csv file. 
	* Create OIDC Client in MOSIP Authentication System (Setup): This threadgroup generates client Id and encoded private key for eSignet authentication.
    * S01 OTP authentication (Execution)
		*S01 T01 Get Csrf Token: This API endpoint generates CSRF token.
		*S01 T02 Oauth Details : This API endpoint hits Oauthdetails endpoint of eSignet.
		*S01 T03 Send OTP : This API endpoint sends OTP request for authentication.
		*S01 T04 Authentication : This API endpoint performs OTP authentication in eSignet portal
		*S01 T05 Authorization Code : This API endpoint performs authorization in eSignet portal
		*S01 T06 Token: Code created in the preparation will be used only once and a signed JWT key value is also required for which we are using a JSR223 Pre-processor. The Pre-processor(Generate Client Assertion) will generate a signed JWT token value using the client id and its private key from the file created in Create OIDC Client in MOSIP Authentication System (Setup). An access token will be generated in the response body.
		*S01 T07 Userinfo: For execution the generated access token from the token end point api is used. Till the token is not expired it can be used for multiple samples.
	* S02 Password Authentication (Execution)
		*S02 T01 Get Csrf Token: This API endpoint generates CSRF token.
		*S02 T02 Oauth Details : This API endpoint hits Oauthdetails endpoint of eSignet.
		*S02 T03 Authentication : This API endpoint performs Password authentication in eSignet portal
		*S02 T04 Authorization Code : This API endpoint performs authorization in eSignet portal
		*S02 T05 Token: Code created in the preparation will be used only once and a signed JWT key value is also required for which we are using a JSR223 Pre-processor. The Pre-processor(Generate Client Assertion) will generate a signed JWT token value using the client id and its private key from the file created in Create OIDC Client in MOSIP Authentication System (Setup). An access token will be generated in the response body.
		*S02 T06 Userinfo: For execution the generated access token from the token end point api is used. Till the token is not expired it can be used for multiple samples.
	
### Downloading Plugin manager jar file for the purpose of installing other JMeter specific plugins

* Download JMeter plugin manager from below url links.
	*https://jmeter-plugins.org/get/

* After downloading the jar file place it in below folder path.
	*lib/ext

* Please refer to following link to download JMeter jars.
	https://mosip.atlassian.net/wiki/spaces/PT/pages/1227751491/Steps+to+set+up+the+local+system#PluginManager
		
### Designing the workload model for performance test execution
* Calculation of number of users depending on Transactions per second (TPS) provided by client

* Applying little's law
	* Users = TPS * (SLA of transaction + think time + pacing)
	* TPS --> Transaction per second.
	
* For the realistic approach we can keep (Think time + Pacing) = 1 second for API testing
	* Calculating number of users for 10 TPS
		* Users= 10 X (SLA of transaction + 1)
		       = 10 X (1 + 1)
			   = 20
			   
### Usage of Constant Throughput timer to control Hits/sec from JMeter
* In order to control hits/ minute in JMeter, it is better to use Timer called Constant Throughput Timer.

* If we are performing load test with 10TPS as hits / sec in one thread group. Then we need to provide value hits / minute as in Constant Throughput Timer
	* Value = 10 X 60
			= 600

* Dropdown option in Constant Throughput Timer
	* Calculate Throughput based on as = All active threads in current thread group
		* If we are performing load test with 10TPS as hits / sec in one thread group. Then we need to provide value hits / minute as in Constant Throughput Timer
	 			Value = 10 X 60
					  = 600
		  
	* Calculate Throughput based on as = this thread
		* If we are performing scalability testing we need to calculate throughput for 10 TPS as 
          Value = (10 * 60 )/(Number of users)
