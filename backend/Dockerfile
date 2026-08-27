FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appuser /app

COPY --from=build /workspace/build/libs/*.jar app.jar

ENV SERVER_PORT=8081
ENV APP_UPLOAD_ROOT_DIR=/app/uploads
ENV APP_UPLOAD_REVIEW_DIR=/app/uploads/reviews

EXPOSE 8081
USER appuser

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8081/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
