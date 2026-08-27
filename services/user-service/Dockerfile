FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY services/common-lib/settings.gradle services/common-lib/build.gradle ./services/common-lib/
COPY services/common-lib/src ./services/common-lib/src
COPY services/user-service/gradle ./services/user-service/gradle
COPY services/user-service/gradlew services/user-service/gradlew.bat services/user-service/settings.gradle services/user-service/build.gradle ./services/user-service/
COPY services/user-service/src ./services/user-service/src
WORKDIR /workspace/services/user-service
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8082
COPY --from=build /workspace/services/user-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
