# Debian (not Ubuntu) base: Ubuntu ships Chromium only as a snap, which does not run in Docker.
# The Temurin JRE is copied in so Java stays on 21.
FROM eclipse-temurin:21-jre AS jre

FROM debian:trixie-slim

COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=${JAVA_HOME}/bin:${PATH}

# Set automatically by BuildKit (amd64 / arm64).
ARG TARGETARCH

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
ENV CHROME_BIN=/usr/bin/chromium
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver
ENV MODULES=esignet
ENV ENV_USER=
ENV ENV_ENDPOINT=
ENV ENV_TESTLEVEL=smokeAndRegression
# Do not set DISPLAY. Headless Chrome talks to X when DISPLAY is set. :99 has no
# X server here, so the GPU process crash-loops and ChromeDriver reports
# "session deleted ... Unable to receive message from renderer".

WORKDIR ${work_dir}

# Debian Chromium + matching chromium-driver, native on amd64 and arm64. Google Chrome has no
# linux/arm64 build, and running the amd64 one under emulation crashes the renderer.
# Alpine apk Chromium / Ubuntu snaps cannot start a ChromeDriver session in testriq pods.
# kubectl is used by MOSIP testriq jobs on Rancher to pull env config / push reports.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        chromium chromium-driver curl ca-certificates bash fonts-liberation \
    && curl -fsSLO "https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_VERSION}/bin/linux/${TARGETARCH:-amd64}/kubectl" \
    && chmod +x kubectl \
    && mv kubectl /usr/local/bin/ \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/bash -m ${container_user} -d ${work_dir} \
    && mkdir -p ${work_dir}/test-output ${work_dir}/screenshots ${work_dir}/reports \
        ${work_dir}/testng-report ${work_dir}/target ${work_dir}/src/test/resources \
        ${work_dir}/test-output/SparkReport \
    && rm -rf /var/lib/apt/lists/*

COPY --chown=${container_user}:${container_user} ./entrypoint.sh ${work_dir}/entrypoint.sh
COPY --chown=${container_user}:${container_user} ./target/uitest-esignet-*-jar-with-dependencies.jar ${work_dir}/
COPY --chown=${container_user}:${container_user} ./src/main/resources/ ${work_dir}/src/main/resources/
COPY --chown=${container_user}:${container_user} ./src/main/resources/featurefiles/ ${work_dir}/featurefiles/
COPY --chown=${container_user}:${container_user} ./src/main/resources/extent.properties ${work_dir}/src/test/resources/extent.properties
COPY --chown=${container_user}:${container_user} ./testNgXmlFiles/ ${work_dir}/testNgXmlFiles/
COPY --chown=${container_user}:${container_user} ./application.properties ${work_dir}/application.properties
COPY --chown=${container_user}:${container_user} ./browserstack.yml ${work_dir}/browserstack.yml
COPY --chown=${container_user}:${container_user} ["./Biometric Devices", "${work_dir}/Biometric Devices"]
COPY --chown=${container_user}:${container_user} ./certs/ ${work_dir}/certs/

RUN chmod +x ${work_dir}/entrypoint.sh \
    && chown -R ${container_user}:${container_user} ${work_dir}

USER ${container_user_uid}:${container_user_gid}

WORKDIR ${work_dir}

ENTRYPOINT ["./entrypoint.sh"]
