# eSignet API Test Rig

## Overview

The **eSignet API Test Rig** is designed for the execution of module-wise automation API tests for the eSignet service. This test rig utilizes **Java REST Assured** and **TestNG** frameworks to automate testing of the eSignet API functionalities. The key focus is to validate the authentication, signature generation, and related functionalities provided by the eSignet module.

For more detailed information about eSignet, please refer to the [eSignet Documentation](https://docs.esignet.io/).

---

## Test Categories

- **Smoke**: Contains only positive test scenarios for quick verification.
- **Regression**: Includes all test scenarios, covering both positive and negative cases.

---

## Coverage

This test rig covers only **external API endpoints** exposed by the eSignet module.

---

### Pre-requisites
Before setting up the eSignet API Test Rig, ensure the following prerequisites are met:

1. **Java 21 (or a compatible version)**  
   - Ensure that Java 21 (or a compatible version) is installed on your machine.
   - You can download Java from the [official website](https://www.oracle.com/java/technologies/javase-jdk21-downloads.html).

2. **Maven 3.9.6 (or higher)**  
   - Download and install Maven from [here](https://maven.apache.org/download.cgi).
   - Verify the installation by running `mvn -v` in the terminal.

3. **Lombok**  
   - Lombok simplifies your code by generating boilerplate code such as getters and setters.
   - You can find installation instructions on the [Lombok project page](https://projectlombok.org/).

4. **setting.xml**  
   - Download the `setting.xml` configuration file from [this link](https://github.com/mosip/mosip-functional-tests/blob/master/settings.xml) and place it in your Maven configuration directory.

5. **IDE (e.g., Eclipse)**  
   - A code editor is needed to write and execute tests. We recommend using Eclipse for ease of use.

6. **apitest-commons Library**  
   - Clone the `apitest-commons` repository and build the JAR file by following the [README](https://github.com/mosip/mosip-functional-tests/blob/release-1.3.1/apitest-commons/README.md).
   - GitHub Repository: [apitest-commons-1.3.1](https://github.com/mosip/mosip-functional-tests/tree/release-1.3.1/apitest-commons).

7. **eSignet Repository**  
   - Ensure you have access to the eSignet API repository. The code can be cloned from [the eSignet repo](https://github.com/mosip/esignet).

### For Windows

- **Git Bash 2.18.0** or higher
- Ensure the `settings.xml` file is present in the `.m2` folder.

### For Linux

- The `settings.xml` file should be present in two places:
  - In the regular Maven configuration folder (`/conf`)
  - Under `/usr/local/maven/conf/`

---

# API Test Rig Configuration

The API Test Rig is configured to work with different core service plugins, such as MOSIP-ID, Mock-Identity-System, and Sunbird Insurance. It allows for dynamic testing and validation of workflows related to identity management and authentication.

## Integration of eSignet API Test Rig with Core Service Plugins

The Test Rig is dynamically configured based on the core service plugin being tested (either MOSIP-ID, Mock-Identity-System, or Sunbird Insurance). The configuration is as follows:

### 1. Configuration for MOSIP-ID Plugin:
- **eSignetbaseurl**: The Test Rig will use the live eSignet instance integrated with the MOSIP-ID service.
- **mosip_components_base_urls**: A string defining the base URLs for various components.
- **esignetActuatorPropertySection**: To fetch the configuration and properties from the actuator for service interactions.

### 2. Configuration for Mock-Identity-System Plugin:
- **eSignetbaseurl**: The Test Rig will use the live eSignet instance integrated with the Mock-Identity-service.
- **mosip_components_base_urls**: A string defining the base URLs for various components.
- **usePreConfiguredOtp**: A flag to use pre-configured OTPs. Set to "true" for OTP-based workflows.
- **esignetActuatorPropertySection**: To fetch the configuration and properties from the actuator for service interactions.
- **esignetSupportedLanguage**: Any 3-letter valid language code to create OIDC client (e.g., "eng", "hin", "tam").

### 3. Configuration for Sunbird Insurance Use Case:
- **eSignetbaseurl**: The Test Rig will use the live eSignet instance integrated with the Sunbird insurance service.
- **sunBirdBaseURL**: The Test Rig will use the live Sunbird registry instance integrated with the Sunbird insurance service.
- **esignetMockBaseURL**: Specifies the baseURL of Sunbird instance (e.g., "esignet-sunbird.").
- **esignetActuatorPropertySection**: To fetch the configuration and properties from the actuator for service interactions.
- **esignetSupportedLanguage**: Any 3-letter valid language code to create OIDC client (e.g., "eng", "hin", "tam").

### eSignet Deployment Configuration (Required for API Test Rig):
These configurations need to be added as part of the eSignet service deployment to support the API Test Rig:

- **MOSIP_ESIGNET_AUTHENTICATE_ATTEMPTS**: 300
- **MOSIP_ESIGNET_SEND_OTP_ATTEMPTS**: 300
- **MOSIP_ESIGNET_AUTH_CHALLENGE_BIO_MAX_LENGTH**: 200000
- **MOSIP_ESIGNET_PREAUTHENTICATION_EXPIRE_IN_SECS**: 600
- **MOSIP_ESIGNET_CAPTCHA_REQUIRED**: (empty)

These parameters must be included in the eSignet deployment YAML for the API Test Rig to function correctly, independent of which plugin is being used.

## Access Test Automation Code

You can access the test automation code using either of the following methods:

### From Browser

1. Clone or download the repository as a zip file from [GitHub](https://github.com/mosip/esignet).
2. Unzip the contents to your local machine.
3. Open a terminal (Linux) or command prompt (Windows) and continue with the following steps.

### From Git Bash

1. Copy the Git repository URL: `https://github.com/mosip/esignet`
2. Open **Git Bash** on your local machine.
3. Run the following command to clone the repository:
   ```sh
   git clone https://github.com/mosip/esignet
   ```

---

## Build Test Automation Code

Once the repository is cloned or downloaded, follow these steps to build and install the test automation code:

1. **Open Command Prompt**  
   - Before proceeding with the project setup, open the Command Prompt or terminal on your system.

2. **Navigate to the project directory**:  
   "cd api-test"

3. **Build the project using Maven**:  
   "mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true"

This will download the required dependencies and prepare the test suite for execution.

---

## Execute Test Automation Suite

You can execute the test automation code using either of the following methods:

### Using Jar

To execute the tests using Jar, use the following steps:

1. Navigate to the `target` directory where the JAR file is generated:
   ```sh
   cd target/
   ```

2. Run the automation test suite JAR file:
   ```
   java -jar -Dmodules=esignet -Denv.user=api-internal.<env_name> -Denv.endpoint=<base_env> -Denv.testLevel=smokeAndRegression -jar apitest-esignet-1.6.0-SNAPSHOT-jar-with-dependencies.jar
   ```
   
# Using Eclipse IDE

To execute the tests using Eclipse IDE, use the following steps:

## 1. **Install Eclipse (Latest Version)**
   - Download and install the latest version of Eclipse IDE from the [Eclipse Downloads](https://www.eclipse.org/downloads/).

## 2. **Import the Maven Project**

   After Eclipse is installed, follow these steps to import the Maven project:

   - Open Eclipse IDE.
   - Go to `File` > `Import`.
   - In the **Import** wizard, select `Maven` > `Existing Maven Projects`, then click **Next**.
   - Browse to the location where the `api-test` folder is saved (either from the cloned Git repository or downloaded zip).
   - Select the folder, and Eclipse will automatically detect the Maven project. Click **Finish** to import the project.

## 3. **Build the Project**

   - Right-click on the project in the **Project Explorer** and select `Maven` > `Update Project`.
   - This will download the required dependencies as defined in the `pom.xml` and ensure everything is correctly set up.

## 4. **Run the Tests**

   To execute the test automation suite, you need to configure the run parameters in Eclipse:

   - Go to `Run` > `Run Configurations`.
   - In the **Run Configurations** window, create a new configuration for your tests:
     - Right-click on **Java Application** and select **New**.
     - In the **Main** tab, select the project by browsing the location where the `api-test` folder is saved, and select the **Main class** as `io.mosip.testrig.apirig.esignet.testrunner.MosipTestRunner`.
   - In the **Arguments** tab, add the necessary **VM arguments**:
     - **VM Arguments**:
       ```
       -Dmodules=esignet -Denv.user=api-internal.<env_name> -Denv.endpoint=<base_env> -Denv.testLevel=smokeAndRegression```

## 5. **Run the Configuration**

   - Once the configuration is set up, click **Run** to execute the test suite.
   - The tests will run, and the results will be shown in the **Console** tab of Eclipse.

   **Note**: You can also run in **Debug Mode** to troubleshoot issues by setting breakpoints in your code and choosing `Debug` instead of `Run`.

## 6. **View Test Results**

   - After the tests are executed, you can view the detailed results in the `api-test\testng-report` directory.
   - The report will have two sections:
       - One section for pre-requisite APIs test cases.
       - Another section for core test cases.

---
  
## Details of Arguments Used

- **env.user**: Replace `<env_name>` with the appropriate environment name (e.g., `dev`, `qa`, etc.).
- **env.endpoint**: The environment where the application under test is deployed. Replace `<base_env>` with the correct base URL for the environment (e.g., `https://api-internal.<env_name>.mosip.net`).
- **env.testLevel**: Set this to `smoke` to run only smoke test cases, or `smokeAndRegression` to run both smoke and regression tests.
- **jar**: Specify the name of the JAR file to execute. The version will change according to the development code version. For example, the current version may look like `apitest-esignet-1.6.0-SNAPSHOT-jar-with-dependencies.jar`.

### Build and Run Info

To run the tests for both **Smoke** and **Regression**:

1. Ensure the correct environment and test level parameters are set.
2. Execute the tests as shown in the command above to validate eSignet's API functionalities.

---

## License

This project is licensed under the terms of the [Mozilla Public License 2.0](https://github.com/mosip/mosip-platform/blob/master/LICENSE)
