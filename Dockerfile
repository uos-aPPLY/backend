# --- 1단계: 빌드 (Builder Stage) ------------------------------------------
FROM gradle:8.6.0-jdk17 AS builder
WORKDIR /workspace
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon --build-cache
COPY src ./src
RUN gradle clean bootJar --no-daemon --build-cache

# --- 2단계: 런타임 (Runtime Stage) ----------------------------------------
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]