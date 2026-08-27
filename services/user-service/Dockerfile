FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
WORKDIR /workspace/services/user-service
RUN GRADLE_ZIP="$(tr -d '\r' < gradle/wrapper/gradle-wrapper.properties | sed -n 's#^distributionUrl=.*/\([^/]*\.zip\)$#\1#p')" \
    && if [ -n "$GRADLE_ZIP" ] && [ -f "/workspace/$GRADLE_ZIP" ]; then \
        sed -i "s#^distributionUrl=.*#distributionUrl=file:/workspace/$GRADLE_ZIP#" gradle/wrapper/gradle-wrapper.properties; \
    fi \
    && chmod +x ./gradlew \
    && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8082
COPY --from=build /workspace/services/user-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
