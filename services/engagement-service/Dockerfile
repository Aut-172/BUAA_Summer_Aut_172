FROM gradle:9.5.1-jdk21 AS build

WORKDIR /workspace
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN mkdir -p /app/uploads/reviews
COPY --from=build /workspace/build/libs/*.jar app.jar

ENV SERVER_PORT=8086
ENV APP_UPLOAD_ROOT_DIR=/app/uploads
ENV APP_UPLOAD_REVIEW_DIR=/app/uploads/reviews
EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]
