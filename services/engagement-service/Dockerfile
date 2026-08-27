FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY services/common-lib/settings.gradle services/common-lib/build.gradle ./services/common-lib/
COPY services/common-lib/src ./services/common-lib/src
COPY services/engagement-service/gradle ./services/engagement-service/gradle
COPY services/engagement-service/gradlew services/engagement-service/gradlew.bat services/engagement-service/settings.gradle services/engagement-service/build.gradle ./services/engagement-service/
COPY services/engagement-service/src ./services/engagement-service/src
WORKDIR /workspace/services/engagement-service
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/uploads/reviews
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8086
ENV APP_UPLOAD_ROOT_DIR=/app/uploads
ENV APP_UPLOAD_REVIEW_DIR=/app/uploads/reviews
COPY --from=build /workspace/services/engagement-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
