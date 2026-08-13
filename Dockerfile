# syntax=docker/dockerfile:1

# Stage 1: frontend static assets (React + Vite)
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi \
 || (echo "===== npm debug log =====" \
     && ls -la /root/.npm/_logs/ \
     && cat /root/.npm/_logs/*-debug-*.log \
     && exit 1)
COPY frontend/ ./
RUN npm run build

# Stage 2: Spring Boot bootJar (embeds frontend into classpath:/static)
FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /backend
COPY backend/ ./
COPY --from=frontend-build /frontend/dist/ ./src/main/resources/static/

# Default: official services.gradle.org + Maven Central (CI / Cloud Agent).
# China Linux VMs that cannot reach those hosts: --build-arg ARCHOPS_CN_MIRRORS=1
# (see deploy/scripts/build-images.sh). Wrapper read timeout lives in
# gradle-wrapper.properties (120s); do not lower it back to 10s.
ARG ARCHOPS_CN_MIRRORS=0
ARG GRADLE_DISTRIBUTION_URL=
RUN set -eux; \
    if [ -n "${GRADLE_DISTRIBUTION_URL}" ]; then \
      escaped="$(printf '%s' "${GRADLE_DISTRIBUTION_URL}" | sed 's/:/\\:/g')"; \
      sed -i "s|^distributionUrl=.*|distributionUrl=${escaped}|" gradle/wrapper/gradle-wrapper.properties; \
      sed -i 's/^validateDistributionUrl=.*/validateDistributionUrl=false/' gradle/wrapper/gradle-wrapper.properties; \
    elif [ "${ARCHOPS_CN_MIRRORS}" = "1" ]; then \
      sed -i 's|^distributionUrl=.*|distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.12.1-bin.zip|' gradle/wrapper/gradle-wrapper.properties; \
      sed -i 's/^validateDistributionUrl=.*/validateDistributionUrl=false/' gradle/wrapper/gradle-wrapper.properties; \
    fi; \
    chmod +x ./gradlew; \
    if [ "${ARCHOPS_CN_MIRRORS}" = "1" ]; then \
      ./gradlew bootJar --no-daemon -I gradle/init-cn-mirrors.gradle; \
    else \
      ./gradlew bootJar --no-daemon; \
    fi

# Stage 3: runtime image
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
ENV JAVA_OPTS=""
COPY --from=backend-build /backend/build/libs/*.jar /app/archops.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/archops.jar"]
