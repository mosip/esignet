
### Contains
* This folder contains performance Test script and test script of below API endpoint categories.
    1. Create Identities in MOSIP Authentication System (Setup)
    2. Create Identities in MOSIP Authentication System - Password Based Auth (Setup)
    3. Auth Token Generation (Setup)
	4. Create OIDC Client in MOSIP Authentication System (Setup)
	5. OIDC - UserInfo (Preparation)
	6. Scenario - 01 : OTP Authentication
	7. Scenario - 02 : Password Authentication

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


### Execution points for eSignet Authentication API's

*Esignet_Test_Script.jmx
	
	* Create Identities in MOSIP Authentication System (Setup) : This thread contains the authorization api's for regproc and idrepo from which the auth token will be generated. There is set of 4 api's generate RID, generate UIN, add identity and add VID. From here we will get the VID which can be further used as individual id. These 4 api's are present in the loop controller where we can define the number of samples for creating identities in which "addIdentitySetup" is used as a variable. 
	* Create Identities in MOSIP Authentication System - Password Based Auth (Setup) : This thread contains the authorization api's for regproc and idrepo from which the auth token will be generated. There is set of 3 api's generate UIN, generate hash for password and add identity. From here we will get the identifier value which can be further used as an individual id or we can say as phone number in pwd based authentication. These 3 api's are present in the loop controller where we can define the number of samples for creating identities in which "addIdentitySetup" is used as a variable.
	* Auth Token Generation (Setup) : This thread conatins Auth manager authentication API which will generate auth token value for PMS and mobile client. 
	* Create OIDC Client in MOSIP Authentication System (Setup) : This thread contains a JSR223 sampler(Generate Key Pair) from which will get a public-private key pair. The public key generated will be used in the OIDC client api to generate client id's  which will be registered for both IDA and eSignet. The private key generated from the sampler will be used in another JSR223 sampler(Generate Client Assertion) present in the OIDC Token (Execution). Generated client id's and there respective private key will be stored in a file which will be used further in the required api's.
	* OIDC - UserInfo (Preparation) : For the preparation we need 5 api's OAuth Details, Send OTP, Authentication, Authorization Code and Token Endpoint api from which a access token will be generated. Access token will be used for the execution.
	
	*Scenario 01: OTP Authentication:
		*S01 T01 GetCsrf: This thread generated CSRF token
		*S01 T02 Oauthdetails , S01 T03 SendOTP, S01 T04 Authentication, S01 T04 Authentication, S01 T06 Token: Code created in the preparation will be used only once and a signed JWT key value is also required for which we are using a JSR223 Sampler. The sampler(Generate Client Assertion) will generate a signed JWT token value using the client id and its private key from the file created in Create OIDC Client in MOSIP Authentication System (Setup). An access token will be generated in the response body.
		*S01 T07 Userinfo: For execution the generated access token from the token end point api is used. Till the token is not expired it can be used for multiple samples.

	*Scenario 02: Password Authentication:
		*S02 T01 GetCsrftoken: This thread generated CSRF token
		*S02 T02 Oauthdetails , S02 T03 Authenticate, S02 T04 Auth-code, S02 T05 Token: Code created in the preparation will be used only once and a signed JWT key value is also required for which we are using a JSR223 Sampler. The sampler(Generate Client Assertion) will generate a signed JWT token value using the client id and its private key from the file created in Create OIDC Client in MOSIP Authentication System (Setup). An access token will be generated in the response body.
		*S02 T06 Userinfo: For execution the generated access token from the token end point api is used. Till the token is not expired it can be used for multiple samples.	
	
		
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
