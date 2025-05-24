# Stage 1: Build the application
FROM bellsoft/liberica-runtime-container:jdk-all-24-slim-stream-musl AS builder
WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

COPY build.gradle.kts ./api/build.gradle.kts
COPY api/src ./api/src

RUN chmod +x ./gradlew && ./gradlew :api:bootJar --no-daemon

# Stage 2: Create the runtime image
FROM bellsoft/liberica-runtime-container:jdk-all-24-slim-stream-musl
WORKDIR /app

ARG JAR_FILE_PATH=api/build/libs/*.jar
COPY --from=builder /app/${JAR_FILE_PATH} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]