# Stage 1: Build the application
FROM bellsoft/liberica-runtime-container:jdk-all-21-slim-stream-musl AS builder
WORKDIR /app

# Copy entire project structure for multi-module build
COPY gradlew .
COPY gradle/ ./gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy all module build files first (for better caching)
COPY api/build.gradle.kts ./api/
COPY spark/build.gradle.kts ./spark/
COPY stream/build.gradle.kts ./stream/

# Copy source code
COPY stream/src/ ./stream/src/

RUN chmod +x ./gradlew && ./gradlew :stream:bootJar --no-daemon

# Stage 2: Create the runtime image
FROM bellsoft/liberica-runtime-container:jre-21-crac-glibc
WORKDIR /app

# Install wget for health checks
RUN apk update && apk add --no-cache wget

COPY --from=builder /app/stream/build/libs/stream.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
