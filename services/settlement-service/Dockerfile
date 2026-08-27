FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY services/common-lib/settings.gradle services/common-lib/build.gradle ./services/common-lib/
COPY services/common-lib/src ./services/common-lib/src
COPY services/settlement-service/gradle ./services/settlement-service/gradle
COPY services/settlement-service/gradlew services/settlement-service/gradlew.bat services/settlement-service/settings.gradle services/settlement-service/build.gradle ./services/settlement-service/
COPY services/settlement-service/src ./services/settlement-service/src
WORKDIR /workspace/services/settlement-service
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8084
COPY --from=build /workspace/services/settlement-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
