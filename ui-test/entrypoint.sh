#!/bin/bash
echo "Listing files in /home/mosip/:"
ls -l /home/mosip/

ls -l /home/mosip/
echo "Listing files in /home/mosip/:"

echo "Listing feature files in /home/mosip/featurefiles/:"
ls -l /home/mosip/featurefiles/

echo "/home/mosip/src/test/resources/"
ls -l /home/mosip/src/test/resources/

echo "/home/mosip/test-output/SparkReport/"
ls -l /home/mosip/test-output/SparkReport/

echo "/home/mosip/src/test/resources/extent.properties"
ls -l /home/mosip/src/test/resources/extent.properties

echo "/home/mosip/src/"
ls -l /home/mosip/src/

echo "/home/mosip/src/main/"
ls -l /home/mosip/src/main/

echo "/home/mosip/src/main/java/"
ls -l /home/mosip/src/main/java/

echo "/home/mosip/src/main/java/utils/"
ls -l /home/mosip/src/main/java/utils/

java --version
java -jar -Denv.endpoint="$ENV_ENDPOINT" uitest-esignet-*.jar