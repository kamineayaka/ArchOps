# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Optional Maven mirror for slow networks, e.g. China:
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

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./
COPY src ./src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
