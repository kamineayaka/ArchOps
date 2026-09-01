# syntax=docker/dockerfile:1

# Default Hub prefix is DaoCloud (China). Override for official Hub:
#   docker build --build-arg DOCKER_HUB_MIRROR=docker.io/library ...
ARG DOCKER_HUB_MIRROR=docker.m.daocloud.io/library

# Stage 1: frontend static assets (React + Vite)
FROM ${DOCKER_HUB_MIRROR}/node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* frontend/.npmrc* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi \
 || (echo "===== npm debug log =====" \
     && ls -la /root/.npm/_logs/ \
     && cat /root/.npm/_logs/*-debug-*.log \
     && exit 1)
COPY frontend/ ./
RUN npm run build

# Stage 2: Spring Boot bootJar (embeds frontend into classpath:/static)
FROM ${DOCKER_HUB_MIRROR}/eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /backend
COPY backend/ ./
COPY --from=frontend-build /frontend/dist/ ./src/main/resources/static/

# Wrapper + Maven repos default to Tencent / Aliyun in the copied tree.
# Optional: GRADLE_DISTRIBUTION_URL to pin a different Gradle zip.
# Wrapper read timeout lives in gradle-wrapper.properties (120s).
ARG GRADLE_DISTRIBUTION_URL=
RUN set -eux; \
    if [ -n "${GRADLE_DISTRIBUTION_URL}" ]; then \
      escaped="$(printf '%s' "${GRADLE_DISTRIBUTION_URL}" | sed 's/:/\\:/g')"; \
      sed -i "s|^distributionUrl=.*|distributionUrl=${escaped}|" gradle/wrapper/gradle-wrapper.properties; \
      sed -i 's/^validateDistributionUrl=.*/validateDistributionUrl=false/' gradle/wrapper/gradle-wrapper.properties; \
    fi; \
    chmod +x ./gradlew; \
    ./gradlew bootJar executorBootJar --no-daemon; \
    apt-get update; \
    apt-get install -y --no-install-recommends openssl; \
    rm -rf /var/lib/apt/lists/*; \
    java -jar build/libs/archops-executor.jar --generate-mtls /mtls

# Stage 3: runtime image (control plane or 执行引擎 via APP_JAR)
FROM ${DOCKER_HUB_MIRROR}/eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
ENV JAVA_OPTS=""
ENV APP_JAR=/app/archops.jar
ENV ARCHOPS_EXECUTOR_TLS_CA_CERT=/mtls/ca.crt
ENV ARCHOPS_EXECUTOR_TLS_SERVER_CERT=/mtls/server.crt
ENV ARCHOPS_EXECUTOR_TLS_SERVER_KEY=/mtls/server.key
ENV ARCHOPS_EXECUTOR_TLS_CLIENT_CERT=/mtls/client.crt
ENV ARCHOPS_EXECUTOR_TLS_CLIENT_KEY=/mtls/client.key
COPY --from=backend-build /backend/build/libs/archops.jar /app/archops.jar
COPY --from=backend-build /backend/build/libs/archops-executor.jar /app/executor.jar
COPY --from=backend-build /mtls /mtls
EXPOSE 8080 8443
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar $APP_JAR"]
