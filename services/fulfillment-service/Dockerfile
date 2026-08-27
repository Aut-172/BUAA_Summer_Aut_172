FROM gradle:9.5.1-jdk21 AS build

WORKDIR /workspace
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

ENV SERVER_PORT=8085
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
