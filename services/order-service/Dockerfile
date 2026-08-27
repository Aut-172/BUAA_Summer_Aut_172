FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY services/common-lib/settings.gradle services/common-lib/build.gradle ./services/common-lib/
COPY services/common-lib/src ./services/common-lib/src
COPY services/order-service/gradle ./services/order-service/gradle
COPY services/order-service/gradlew services/order-service/gradlew.bat services/order-service/settings.gradle services/order-service/build.gradle ./services/order-service/
COPY services/order-service/src ./services/order-service/src
WORKDIR /workspace/services/order-service
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
COPY --from=build /workspace/services/order-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
