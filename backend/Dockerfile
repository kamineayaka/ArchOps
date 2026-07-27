# syntax=docker/dockerfile:1

# Base image already ships Maven; do NOT use ./mvnw here — the wrapper
# downloads apache-maven from Central and ignores MAVEN_MIRROR.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Optional Maven mirror for dependency resolution, e.g. China:
#   --build-arg MAVEN_MIRROR=https://maven.aliyun.com/repository/public
ARG MAVEN_MIRROR=
RUN if [ -n "$MAVEN_MIRROR" ]; then \
      mkdir -p /root/.m2 \
      && printf '%s\n' \
        '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"' \
        '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
        '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">' \
        '  <mirrors>' \
        '    <mirror>' \
        '      <id>build-mirror</id>' \
        '      <mirrorOf>*</mirrorOf>' \
        '      <name>Build-time Maven mirror</name>' \
        "      <url>${MAVEN_MIRROR}</url>" \
        '    </mirror>' \
        '  </mirrors>' \
        '</settings>' > /root/.m2/settings.xml; \
    fi

# Dependency layer: source changes should not re-download the world.
COPY pom.xml .
# Cache deps separately from sources (go-offline can be flaky on plugins — resolve is enough).
RUN mvn -B -q -DskipTests dependency:resolve dependency:resolve-plugins

COPY src ./src
RUN mvn -B -q -DskipTests package

# No apt/curl: Compose healthcheck uses /dev/tcp (see deploy/compose/compose.yaml).
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
