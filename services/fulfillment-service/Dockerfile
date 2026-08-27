FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY services/common-lib/settings.gradle services/common-lib/build.gradle ./services/common-lib/
COPY services/common-lib/src ./services/common-lib/src
COPY services/fulfillment-service/gradle ./services/fulfillment-service/gradle
COPY services/fulfillment-service/gradlew services/fulfillment-service/gradlew.bat services/fulfillment-service/settings.gradle services/fulfillment-service/build.gradle ./services/fulfillment-service/
COPY services/fulfillment-service/src ./services/fulfillment-service/src
WORKDIR /workspace/services/fulfillment-service
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8085
COPY --from=build /workspace/services/fulfillment-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
