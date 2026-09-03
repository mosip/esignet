FROM eclipse-temurin:21-jre-jammy

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001
ARG KUBECTL_VERSION=1.22.9

ENV work_dir=/home/${container_user}
ENV RUN_DOCKER=yes
ENV MODULES=esignet
ENV ENV_USER=
ENV ENV_ENDPOINT=
ENV ENV_TESTLEVEL=smokeAndRegression
ENV DISPLAY=:99

WORKDIR ${work_dir}

# Google Chrome + matching ChromeDriver (amd64). Chromium from Ubuntu snaps is unusable in containers.
# Also kubectl, used by MOSIP testriq jobs on Rancher to pull env config / push reports.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        wget gnupg unzip curl ca-certificates bash \
        fonts-liberation libasound2 libatk-bridge2.0-0 libatk1.0-0 \
        libcups2 libdrm2 libgbm1 libgtk-3-0 libnspr4 libnss3 \
        libxcomposite1 libxdamage1 libxrandr2 xdg-utils \
    && wget -qO /tmp/google-chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y --no-install-recommends /tmp/google-chrome.deb \
    && rm -f /tmp/google-chrome.deb \
    && CHROME_MAJOR=$(google-chrome --version | grep -oE '[0-9]+' | head -1) \
    && DRIVER_VERSION=$(curl -fsSL "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_${CHROME_MAJOR}") \
    && wget -qO /tmp/chromedriver.zip "https://storage.googleapis.com/chrome-for-testing-public/${DRIVER_VERSION}/linux64/chromedriver-linux64.zip" \
    && unzip -q /tmp/chromedriver.zip -d /tmp \
    && mv /tmp/chromedriver-linux64/chromedriver /usr/bin/chromedriver \
    && chmod +x /usr/bin/chromedriver \
    && rm -rf /tmp/chromedriver.zip /tmp/chromedriver-linux64 \
    && curl -fsSLO "https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
    && chmod +x kubectl \
    && mv kubectl /usr/local/bin/ \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/bash -m ${container_user} -d ${work_dir} \
    && mkdir -p ${work_dir}/test-output ${work_dir}/screenshots ${work_dir}/reports ${work_dir}/testng-report \
    && rm -rf /var/lib/apt/lists/*

COPY --chown=${container_user}:${container_user} ./entrypoint.sh ${work_dir}/entrypoint.sh
COPY --chown=${container_user}:${container_user} ./target ${work_dir}/target
COPY --chown=${container_user}:${container_user} ./src/main/resources/ ${work_dir}/src/main/resources/
COPY --chown=${container_user}:${container_user} ./testNgXmlFiles/ ${work_dir}/testNgXmlFiles/
COPY --chown=${container_user}:${container_user} ./application.properties ${work_dir}/application.properties
COPY --chown=${container_user}:${container_user} ./browserstack.yml ${work_dir}/browserstack.yml
COPY --chown=${container_user}:${container_user} ["./Biometric Devices", "/home/mosip/Biometric Devices"]
COPY --chown=${container_user}:${container_user} ./certs/ ${work_dir}/certs/

# GitHub unzip lands jars under target/; local `mvn package` does the same. Flatten the shaded jar to $HOME
# so entrypoint can `java -jar uitest-esignet-*-jar-with-dependencies.jar`.
RUN find ${work_dir}/target -maxdepth 1 -type f -name 'uitest-esignet-*-jar-with-dependencies.jar' -exec cp {} ${work_dir}/ \; \
    && chmod +x ${work_dir}/entrypoint.sh \
    && chown -R ${container_user}:${container_user} ${work_dir}

USER ${container_user_uid}:${container_user_gid}

WORKDIR ${work_dir}

ENTRYPOINT ["./entrypoint.sh"]
