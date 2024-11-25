# eSignet API Test Rig

## Overview

The **eSignet API Test Rig** is designed for the execution of module-wise automation API tests for the eSignet service. This test rig utilizes **Java REST Assured** and **TestNG** frameworks to automate testing of the eSignet API functionalities. The key focus is to validate the authentication, signature generation, and related functionalities provided by the eSignet module.

## Test Categories

- **Smoke**: Contains only positive test scenarios for quick verification.
- **Regression**: Includes all test scenarios, covering both positive and negative cases.

## Coverage

This test rig covers only **external API endpoints** exposed by the eSignet module.

## Pre-requisites

Before running the automation tests, ensure the following software is installed on the machine:

- **Java 21** (or a compatible version)
- **Maven 3.9.6** (or higher)
- **Lombok** (Refer to [Lombok Project](https://projectlombok.org/))

### For Windows

- **Git Bash 2.18.0** or higher
- Ensure the `settings.xml` file is present in the `.m2` folder.

### For Linux

- The `settings.xml` file should be present in two places:
  - In the regular Maven configuration folder (`/conf`)
  - Under `/usr/local/maven/conf/`

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

## Build Test Automation Code

Once the repository is cloned or downloaded, follow these steps to build and install the test automation code:

1. Navigate to the project directory:
   ```sh
   cd api-test
   ```

2. Build the project using Maven:
   ```sh
   mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true
   ```

This will download the required dependencies and prepare the test suite for execution.

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
   java -jar -Dmodules=esignet -Denv.user=api-internal.<env_name> -Denv.endpoint=<base_env> -Denv.testLevel=smokeAndRegression -jar apitest-esignet-1.2.1-SNAPSHOT-jar-with-dependencies.jar
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


   
### Details of Arguments Used

- **env.user**: Replace `<env_name>` with the appropriate environment name (e.g., `dev`, `qa`, etc.).
- **env.endpoint**: The environment where the application under test is deployed. Replace `<base_env>` with the correct base URL for the environment (e.g., `https://api-internal.<env_name>.mosip.net`).
- **env.testLevel**: Set this to `smoke` to run only smoke test cases, or `smokeAndRegression` to run both smoke and regression tests.
- **jar**: Specify the name of the JAR file to execute. The version will change according to the development code version. For example, the current version may look like `apitest-esignet-1.2.1-SNAPSHOT-jar-with-dependencies.jar`.

## Build and Run

To run the tests for both **Smoke** and **Regression**:

1. Ensure the correct environment and test level parameters are set.
2. Execute the tests as shown in the command above to validate eSignet's API functionalities.

## License

This project is licensed under the terms of the [Mozilla Public License 2.0]