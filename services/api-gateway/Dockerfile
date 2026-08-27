FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/services/api-gateway
COPY services/api-gateway/gradle ./gradle
COPY services/api-gateway/gradlew services/api-gateway/gradlew.bat services/api-gateway/settings.gradle services/api-gateway/build.gradle ./
COPY services/api-gateway/src ./src
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080
COPY --from=build /workspace/services/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
