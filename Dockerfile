# --- 1단계: 빌드 ----------------------------------------------------
FROM gradle:8.6.0-jdk17 AS builder
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN gradle clean bootJar --no-daemon

# --- 2단계: 런타임 --------------------------------------------------
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
