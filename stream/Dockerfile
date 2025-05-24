# Stage 1: Build the application
FROM bellsoft/liberica-runtime-container:jdk-all-21-slim-stream-musl AS builder
WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

COPY stream/build.gradle.kts ./stream/build.gradle.kts
COPY stream/src ./stream/src

RUN chmod +x ./gradlew && ./gradlew :stream:bootJar --no-daemon

# Stage 2: Create the runtime image
FROM bellsoft/liberica-runtime-container:jdk-all-21-slim-stream-musl
WORKDIR /app

COPY --from=builder /app/stream/build/libs/stream.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]