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
   cd apitest
   ```

2. Build the project using Maven:
   ```sh
   mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true
   ```

This will download the required dependencies and prepare the test suite for execution.

## Execute Test Automation Suite

To execute the tests, use the following steps:

1. Navigate to the `target` directory where the JAR file is generated:
   ```sh
   cd target/
   ```

2. Run the automation test suite JAR file:
   ```sh
   java -jar -Dmodules=esignet -Denv.user=api-internal.<env_name> -Denv.endpoint=<base_env> -Denv.testLevel=smokeAndRegression -jar apitest-esignet-1.5.0-SNAPSHOT-jar-with-dependencies.jar
   ```
   
### Details of Arguments Used

- **env.user**: The user of the environment where the tests will be executed.
- **env.endpoint**: The environment where the application under test is deployed. Replace `<base_env>` with the actual environment hostname.
- **env.testLevel**: Set this to `smoke` to run only smoke test cases, or `smokeAndRegression` to run both smoke and regression tests.
- **jar**: Specify the name of the JAR file to execute. The version will change according to the development code version. For example, the current version may look like `apitest-esignet-1.5.0-SNAPSHOT-jar-with-dependencies.jar`.

## Build and Run

To run the tests for both **Smoke** and **Regression**:

1. Ensure the correct environment and test level parameters are set.
2. Execute the tests as shown in the command above to validate eSignet's API functionalities.

## License

This project is licensed under the terms of the [Mozilla Public License 2.0]