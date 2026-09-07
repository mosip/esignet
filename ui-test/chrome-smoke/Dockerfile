FROM eclipse-temurin:21-jre-alpine
USER root
RUN apk add --no-cache chromium chromium-chromedriver curl
WORKDIR /smoke
COPY chrome-session-smoke.sh /smoke/chrome-session-smoke.sh
RUN chmod +x /smoke/chrome-session-smoke.sh
ENTRYPOINT ["/smoke/chrome-session-smoke.sh"]
