FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY services/common-lib/settings.gradle services/common-lib/build.gradle ./services/common-lib/
COPY services/common-lib/src ./services/common-lib/src
COPY services/merchant-service/gradle ./services/merchant-service/gradle
COPY services/merchant-service/gradlew services/merchant-service/gradlew.bat services/merchant-service/settings.gradle services/merchant-service/build.gradle ./services/merchant-service/
COPY services/merchant-service/src ./services/merchant-service/src
WORKDIR /workspace/services/merchant-service
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8081
COPY --from=build /workspace/services/merchant-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
