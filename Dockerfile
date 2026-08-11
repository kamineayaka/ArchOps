# syntax=docker/dockerfile:1

# Stage 1: frontend static assets (React + Vite)
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY frontend/ ./
RUN npm run build

# Stage 2: Spring Boot bootJar (embeds frontend into classpath:/static)
FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /backend
COPY backend/ ./
COPY --from=frontend-build /frontend/dist/ ./src/main/resources/static/
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

# Stage 3: runtime image
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
ENV JAVA_OPTS=""
COPY --from=backend-build /backend/build/libs/*.jar /app/archops.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/archops.jar"]
