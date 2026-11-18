# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Stage 1 - build the application with the JDK (no external build tool needed)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

COPY src ./src

RUN mkdir -p out \
    && javac -encoding UTF-8 -d out src/main/java/com/lab/app/LoginApp.java \
    && jar --create --file app.jar \
           --main-class com.lab.app.LoginApp \
           -C out . \
           -C src/main/resources .

# ---------------------------------------------------------------------------
# Stage 2 - minimal runtime image (JRE only, non-root user)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="lab-login-app" \
      org.opencontainers.image.description="Lab External - containerized Java login application" \
      org.opencontainers.image.version="1.0.0"

WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/app.jar /app/app.jar

ENV PORT=8080 \
    APP_USER=admin \
    APP_PASSWORD=admin123 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

EXPOSE 8080

USER app

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null 2>&1 || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
