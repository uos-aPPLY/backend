# ── 1단계: 빌드(Builder Stage) ────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

# Gradle 캐시 활용을 위해 의존성 파일 먼저 복사
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon --build-cache

# 실제 소스 복사 후 애플리케이션 빌드
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon --build-cache -Pprod

# ── 2단계: 런타임(Runtime Stage) ─────────────────────────────────────────
# Distroless(Java 17) 기반 초경량 이미지 ― shell‧패키지 관리자 없음
FROM gcr.io/distroless/java17-debian12

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar

# 컨테이너가 사용하는 포트(메타데이터용)
EXPOSE 8080

# 경량 GC + 메모리 상한(전체 RAM의 70 %) 설정
ENTRYPOINT ["java","-XX:+UseSerialGC","-XX:MaxRAMPercentage=70","-jar","app.jar"]
